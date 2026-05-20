package org.openmw.ui.page.mod

import android.app.Application
import android.content.Context
import android.os.Environment
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmw.Constants
import org.openmw.ui.view.addCustomLog
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.OpenMWConfigUtils
import org.openmw.utils.findFilesWithExtensions
import java.io.File
import java.util.regex.Pattern
import javax.inject.Inject
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@HiltViewModel
class ModAssistantViewModel @Inject constructor(
    private val app: Application
) : ViewModel() {

    val selectedTabIndex = mutableIntStateOf(0)
    val categories = OpenMWConfigUtils.ConfigKeyType.entryList

    var showFileBrowser = mutableStateOf(false)

    var showMods = mutableStateOf(true)
    var showNav = mutableStateOf(false)
    var showDelta = mutableStateOf(false)
    var autoMods = mutableStateOf(false)
    var downloader = mutableStateOf(false)
    var s3LightsDialog = mutableStateOf(false)
    var terminal = mutableStateOf(false)
    var kram = mutableStateOf(false)
    var showDialogSettings = mutableStateOf(false)
    var showSearchDialog = mutableStateOf(false)
    var launchFloatingActionButton = mutableStateOf(false)

    val newFeatureEnabledChecked = GameFilesPreferences.loadNewFeatureEnabledState(app)


    suspend fun pathCorrector(): Map<String, String> {
        val configFilePath = Constants.USER_OPENMW_CFG
        val configFile = File(configFilePath)
        val results = mutableMapOf<String, String>()

        withContext(Dispatchers.IO) {
            configFile.readLines().filter { it.startsWith("data=") }.forEach { line ->
                val dataPath = line.substringAfter("data=")
                val dataDirectory = File(dataPath)

                // Check if the path is valid
                if (!dataDirectory.exists() || !dataDirectory.isDirectory) {
                    val fileName = dataDirectory.name
                    val searchResult = searchFile(Environment.getExternalStorageDirectory(), fileName)

                    if (searchResult != null) {
                        val correctedLine = "data=$searchResult"
                        results[line] = correctedLine
                    }
                }
            }
        }

        return results
    }

    fun applyChanges(changes: Map<String, String>) {
        val configFilePath = Constants.USER_OPENMW_CFG
        val configFile = File(configFilePath)
        val existingLines = configFile.readLines().toMutableList()

        val updatedLines = existingLines.map { line ->
            changes[line] ?: line
        }

        configFile.writeText(updatedLines.joinToString("\n"))
    }

    fun searchFile(directory: File, fileName: String): String? {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory) {
                if (file.name.equals(fileName, ignoreCase = true)) {
                    return file.absolutePath
                } else {
                    val result = searchFile(file, fileName)
                    if (result != null) {
                        return result
                    }
                }
            }
        }
        return null
    }

    suspend fun startSearch(updateStatus: (String) -> Unit): List<String> {
        val basePath = Environment.getExternalStorageDirectory().toString()
        val baseDirectory = File(basePath)
        val extensions = arrayOf("bsa", "esm", "esp", "esl", "omwaddon", "omwgame", "omwscripts")
        val filterList = listOf("Morrowind.bsa", "Bloodmoon.bsa", "Tribunal.bsa") // Add your filters here
        val results = mutableListOf<String>()

        withContext(Dispatchers.IO) {
            searchFiles(baseDirectory, extensions, results, updateStatus, filterList)
        }
        return results
    }

    suspend fun searchFiles(directory: File, extensions: Array<String>, results: MutableList<String>, updateStatus: (String) -> Unit, filterList: List<String>) {
        directory.listFiles()?.forEach { file ->
            if (file.isDirectory && filterList.none { file.name.contains(it, ignoreCase = true) }) {
                searchFiles(file, extensions, results, updateStatus, filterList)
            } else if (!file.isDirectory && filterList.none { file.name.contains(it, ignoreCase = true) }) {
                val fileName = file.name.lowercase()
                if (extensions.any { fileName.endsWith(it) }) {
                    results.add(file.absolutePath)

                    withContext(Dispatchers.Main) {
                        updateStatus(file.absolutePath)
                    }
                }
            }
        }
    }

    fun updateConfigFile(files: List<String>) {
        val configFilePath = Constants.USER_OPENMW_CFG
        val configFile = File(configFilePath)
        val existingLines = configFile.readLines().toMutableList()
        val newLines = mutableListOf<String>()

        val dataLines = existingLines.filter { it.startsWith("data=") }.toMutableList()
        val contentLines = existingLines.filter { it.startsWith("content=") }.toMutableList()
        val groundcoverLines = existingLines.filter { it.startsWith("groundcover=") }.toMutableList()

        val groundcoverPattern = Pattern.compile("Rem_.*", Pattern.CASE_INSENSITIVE) // Modify this regex to match your pattern

        files.forEach { filePath ->
            val file = File(filePath)
            val directoryPath = file.parent
            val fileName = file.name

            val dataLine = "data=$directoryPath"
            val categoryLine = if (groundcoverPattern.matcher(fileName).matches()) {
                "groundcover=$fileName"
            } else {
                "content=$fileName"
            }

            if (!dataLines.contains(dataLine)) {
                dataLines.add(dataLine)
            }

            if (categoryLine.startsWith("groundcover=") && !groundcoverLines.contains(categoryLine)) {
                groundcoverLines.add(categoryLine)
            } else if (categoryLine.startsWith("content=") && !contentLines.contains(categoryLine)) {
                contentLines.add(categoryLine)
            }
        }

        newLines.addAll(dataLines)
        newLines.addAll(contentLines)
        newLines.addAll(groundcoverLines)

        configFile.writeText(newLines.joinToString("\n"))
    }

    fun modPathSelection(context: Context, directory: File, onPathPersisted: (String?) -> Unit) {
        try {
            CoroutineScope(Dispatchers.IO).launch {
                val ignoreList = listOf("Morrowind.bsa", "Tribunal.bsa", "Bloodmoon.bsa")
                val extensions = arrayOf("bsa", "esm", "esp", "esl", "omwaddon", "omwgame", "omwscripts")
                val selectedDirectory = DocumentFile.fromFile(directory)
                val files = findFilesWithExtensions(selectedDirectory, extensions)
                val modPath = directory.absolutePath

                // Read existing content from the file as raw lines
                val file = File(Constants.USER_OPENMW_CFG)
                val existingLines = if (file.exists()) {
                    file.readLines().toMutableList()
                } else {
                    mutableListOf()
                }

                // Create new mod values
                val newModValues = files.mapIndexed { index, file ->
                    val fileName = file.name ?: ""
                    val nameWithoutExtension = fileName.substringBeforeLast(".")
                    val extension = fileName.substringAfterLast(".")
                    ModValue(index + 1, "content", "$nameWithoutExtension.$extension", isChecked = true)
                }.toMutableList().filter { it.value !in ignoreList }.toMutableList()

                newModValues.add(ModValue(newModValues.size + 1, "data", modPath, isChecked = true))

                // Convert new mod values to file lines
                val newContentLines = newModValues
                    .filter { it.category == "content" }
                    .map { "content=${it.value}" }
                    .toSet()

                val newDataLines = newModValues
                    .filter { it.category == "data" }
                    .map { "data=\"${it.value}\"" }
                    .toSet()

                // Find insertion points for each category
                val lastContentIndex = existingLines.indexOfLast { it.startsWith("content=") }
                val lastDataIndex = existingLines.indexOfLast { it.startsWith("data=") }

                // Create a new list with inserted lines
                val updatedLines = existingLines.toMutableList()

                // Insert new content lines after the last existing content line
                newContentLines.forEach { line ->
                    if (!existingLines.contains(line)) {
                        if (lastContentIndex != -1) {
                            updatedLines.add(lastContentIndex + 1, line)
                        } else {
                            // If no existing content lines, find or create the Plugins section
                            val pluginsSectionIndex = existingLines.indexOfFirst { it.contains("## Plugins") }
                            if (pluginsSectionIndex != -1) {
                                // Add after the section header
                                updatedLines.add(pluginsSectionIndex + 1, line)
                            } else {
                                // Add at the end if no section found
                                updatedLines.add(line)
                            }
                        }
                    }
                }

                // Insert new data lines after the last existing data line
                newDataLines.forEach { line ->
                    if (!existingLines.contains(line)) {
                        if (lastDataIndex != -1) {
                            updatedLines.add(lastDataIndex + 1, line)
                        } else {
                            // If no existing data lines, find or create the Data Paths section
                            val dataSectionIndex = existingLines.indexOfFirst { it.contains("## Data Paths") }
                            if (dataSectionIndex != -1) {
                                // Add after the section header
                                updatedLines.add(dataSectionIndex + 1, line)
                            } else {
                                // Add at the end if no section found
                                updatedLines.add(line)
                            }
                        }
                    }
                }

                // Write the updated lines back to the file
                file.bufferedWriter().use { writer ->
                    updatedLines.forEach { line ->
                        writer.write(line)
                        writer.newLine()
                    }
                }

                if (files.isNotEmpty()) {
                    onPathPersisted(modPath)
                } else {
                    onPathPersisted("")
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onPathPersisted(null)
        }
    }

    /**
     * Finds the start and end line numbers of a given category in the file.
     * Returns a Pair<Int, Int> (startLine, endLine), or null if not found.
     */
    private fun findCategoryBounds(file: File, category: String): Pair<Int, Int>? {
        val lines = file.readLines()
        var start = -1
        var end = -1

        lines.forEachIndexed { index, line ->
            val trimmedLine = line.trim()
            if (trimmedLine.startsWith(category) || trimmedLine.startsWith(";$category")) {
                if (start == -1) start = index
                end = index
            } else if (start != -1 && "=" in trimmedLine && !trimmedLine.startsWith(" ") && !trimmedLine.startsWith("#")) {
                // Stop if we hit a different category (new unindented key-value line)
                return@forEachIndexed
            }
        }

        return if (start != -1) Pair(start, end) else null
    }

    /**
     * save list to openmw.cfg
     */
    suspend fun writeModValuesToFile(
        modValues: List<ModValue>,
        filePath: String = Constants.USER_OPENMW_CFG,
        targetCategory: String? = null,  // If set, only updates this category's section
        onFinish: (Boolean) -> Unit = {}
    ) {
        withContext(Dispatchers.IO) {
            val file = File(filePath)
            if (!file.exists()) {
                onFinish(false)
                return@withContext
            }

            val openMWCfg = OpenMWConfigUtils.getOpenMWConfig().toMutableMap()

            if (targetCategory != null && OpenMWConfigUtils.ConfigKeyType.list.contains(targetCategory)) {
                val newCategoryLines = modValues
                    .filter { it.category == targetCategory }
                    .sortedBy { it.id }
                    .map { modValue ->
                        if (modValue.isChecked) "${modValue.category}=${modValue.value}"
                        else ";${modValue.category}=${modValue.value}"
                    }

                OpenMWConfigUtils.ConfigKeyType.getByKey(targetCategory)?.let {
                    openMWCfg[it] = newCategoryLines
                }
                OpenMWConfigUtils.saveOpenMWConfig(openMWCfg)
                onFinish(true)
            } else {
                onFinish(false)
                return@withContext
            }
        }
    }

    fun searchMods(query: String, modValues: List<ModValue>, category: String?): List<ModValue> {
        return modValues.filter {
            (category == null || it.category == category) && it.value.contains(query, ignoreCase = true)
        }
    }

    fun navigateToMod(
        modValue: ModValue,
        categorizedModValues: List<List<ModValue>>,
        setSelectedTabIndex: (Int) -> Unit,
        lazyListState: LazyListState,
        coroutineScope: CoroutineScope,
        setHighlightedCardId: (Int?) -> Unit  // New parameter
    ) {
        setHighlightedCardId(modValue.id)  // Set the highlighted card

        // Reset after 3 seconds
        coroutineScope.launch {
            delay(3000)
            setHighlightedCardId(null)
        }

        val tabIndex = categorizedModValues.indexOfFirst { categoryList ->
            categoryList.any { it.id == modValue.id }
        }
        if (tabIndex != -1) {
            setSelectedTabIndex(tabIndex)
            val itemIndex = categorizedModValues[tabIndex].indexOfFirst { it.id == modValue.id }
            if (itemIndex != -1) {
                coroutineScope.launch {
                    try {
                        lazyListState.scrollToItem(itemIndex)
                    } catch (e: Exception) {
                        addCustomLog(
                            "Error scrolling: ${e.message}",
                            textSize = 14,
                            textColor = Color.Red
                        )
                    }
                }
            }
        }
    }

}

fun readModValues(
    configPath: String = Constants.USER_OPENMW_CFG,
    validCategories: Set<String> = setOf("data", "content", "groundcover")
): List<ModValue> {
    val values = mutableListOf<ModValue>()
    val configFile = File(configPath)

    if (!configFile.exists()) return emptyList()

    configFile.forEachLine { line ->
        val trimmedLine = line.trim()
        if ("=" in trimmedLine) {
            try {
                val isChecked = !trimmedLine.startsWith(";")
                val (category, value) = trimmedLine.removePrefix(";").split("=", limit = 2).map { it.trim() }
                if (category in validCategories) {
                    values.add(ModValue(values.size + 1, category, value, isChecked))
                }
            } catch (e: Exception) {
                // Log or handle malformed lines if needed
                println("Skipping malformed line: $trimmedLine")
            }
        }
    }
    return values
}

fun List<ModValue>.categorizeModValues(categories: List<String>): List<List<ModValue>> {
    return categories.map { category ->
        this.filter { it.category == category }
    }
}

data class ModValue @OptIn(ExperimentalUuidApi::class) constructor(
    val id: Int,
    val category: String,
    val value: String,
    val isChecked: Boolean,
    val stableId: String = Uuid.random().toString(),
) {
    companion object {
        fun updateUIFromModValues(
            categories: List<String>,
            configPath: String = Constants.USER_OPENMW_CFG
        ): List<List<ModValue>> {
            return readModValues(configPath).categorizeModValues(categories)
        }
    }
}
