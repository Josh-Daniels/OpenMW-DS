package org.openmw.fragments

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.openmw.Constants
import org.openmw.R
import org.openmw.ui.controls.UIStateManager.customCFG
import org.openmw.utils.IniConverter
import org.openmw.utils.MToast
import org.openmw.utils.dataStore
import org.openmw.utils.stringRes
import java.io.File

private val job = Job()
private val scope = CoroutineScope(Dispatchers.IO + job)

private data class GameFiles(
    val esmContentLines: List<String>,
    val bsaFallbackLines: List<String>
)

private fun findFilesWithExtensions(directory: DocumentFile?, extensions: Array<String>): List<DocumentFile> {
    return directory?.listFiles()?.filter { file ->
        extensions.any { file.name?.endsWith(".$it") == true }
    } ?: emptyList()
}

fun modifyFileLineByNumber(filePath: String, lineNumber: Int, newContent: String) {
    val lines = File(filePath).readLines().toMutableList()
    if (lineNumber in 1..lines.size) {
        lines[lineNumber - 1] = newContent
        File(filePath).writeText(lines.joinToString("\n"))
    } else {
        println("Line number out of range")
    }
}

/**
 * Searches for a line containing `searchString` and replaces it with `newContent`.
 * @param filePath Path to the file
 * @param searchString String to search for in the file
 * @param newContent New content to replace the matching line
 * @param replaceAll If true, replaces all matching lines; if false, replaces only the first match
 * @return true if replacement was made, false if no match was found
 */
fun modifyFileLine(
    filePath: String,
    searchString: String,
    newContent: String,
    replaceAll: Boolean = false
): Boolean {
    val file = File(filePath)
    val lines = file.readLines().toMutableList()
    var replacementMade = false

    for (i in lines.indices) {
        if (lines[i].contains(searchString)) {
            lines[i] = newContent
            replacementMade = true
            if (!replaceAll) break
        }
    }

    if (replacementMade) {
        file.writeText(lines.joinToString("\n"))
    }

    return replacementMade
}

fun modifyFileLineExactMatch(
    filePath: String,
    exactLineContent: String,
    newContent: String,
    replaceAll: Boolean = false
): Boolean {
    val file = File(filePath)
    val lines = file.readLines().toMutableList()
    var replacementMade = false

    for (i in lines.indices) {
        if (lines[i] == exactLineContent) {
            lines[i] = newContent
            replacementMade = true
            if (!replaceAll) break
        }
    }

    if (replacementMade) {
        file.writeText(lines.joinToString("\n"))
    }

    return replacementMade
}

fun onFirstLaunch(context: Context) {
    val dataStoreKey = stringPreferencesKey("game_files_uri")
    scope.launch {
        try {
            val uriString = context.dataStore.data.first()[dataStoreKey]
            if (!uriString.isNullOrBlank()) {
                val folder = File(uriString)
                val cfgFile = File(Constants.OPENMW_CFG)

                val needsUpdate = if (!cfgFile.exists()) {
                    true
                } else {
                    val content = cfgFile.readText()
                    !content.contains("vfs-mw") || !content.contains("data-local=") ||
                        !content.contains("Mods/companion")
                }

                if (needsUpdate) {
                    val selectedDirectory = DocumentFile.fromFile(folder)
                    val iniFile = selectedDirectory.findFile("Morrowind.ini")
                    val dataFilesFolder = selectedDirectory.findFile("Data Files")

                    if (iniFile != null && dataFilesFolder?.isDirectory == true) {
                        val gameFiles = scanForGameFiles(dataFilesFolder)
                        val iniData = context.contentResolver.openInputStream(iniFile.uri)?.use {
                            it.bufferedReader().readText()
                        } ?: ""
                        val convertedData = IniConverter(iniData).convert()

                        updateMainConfig(folder.absolutePath, gameFiles, convertedData)
                        //updateUserConfig(folder.absolutePath, gameFiles)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

fun processSelectedFolder(context: Context, folder: File, onUriPersisted: (String?) -> Unit) {
    val dataStoreKey = stringPreferencesKey("game_files_uri")
    scope.launch {
        try {
            val selectedDirectory = DocumentFile.fromFile(folder)
            val iniFile = selectedDirectory.findFile("Morrowind.ini")
            val dataFilesFolder = selectedDirectory.findFile("Data Files")

            if (iniFile != null && dataFilesFolder?.isDirectory == true) {
                // Persist URI and notify UI
                val uriString = context.dataStore.data.first()[dataStoreKey]
                onUriPersisted(uriString)

                val gameFiles = scanForGameFiles(dataFilesFolder)

                // 1. Process main openmw.cfg
                val iniData = context.contentResolver.openInputStream(iniFile.uri)?.use {
                    it.bufferedReader().readText()
                } ?: ""
                val convertedData = IniConverter(iniData).convert()

                updateMainConfig(folder.absolutePath, gameFiles, convertedData)
                updateUserConfig(folder.absolutePath, gameFiles)
            } else {
                context.dataStore.edit { it[dataStoreKey] = "" }
                onUriPersisted("")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            onUriPersisted(null)
            MToast(stringRes(R.string.an_error_occurred_while_selecting_the_folder))
        }
    }
}

private fun scanForGameFiles(dataFilesFolder: DocumentFile): GameFiles {
    val extensions = arrayOf("esm", "bsa")
    val files = findFilesWithExtensions(dataFilesFolder, extensions)
    val existingFiles = files.mapNotNull { it.name }.toSet()

    val orderedEsmFiles = listOf("Morrowind.esm", "Tribunal.esm", "Bloodmoon.esm")
    val orderedBsaFiles = listOf("Morrowind.bsa", "Tribunal.bsa", "Bloodmoon.bsa")

    return GameFiles(
        esmContentLines = orderedEsmFiles.intersect(existingFiles).map { "content=$it" },
        bsaFallbackLines = orderedBsaFiles.intersect(existingFiles).map { "fallback-archive=$it" }
    )
}

private fun updateMainConfig(savedPath: String, gameFiles: GameFiles, convertedData: String) {
    val cfgFile = File(Constants.OPENMW_CFG)
    val regexData = Regex("""^data\s*=\s*"specify-me!"""")
    val replacementStringData = """data="${savedPath}/Data Files""""
    val vfsPathLine = "data=${Constants.USER_RESOURCES}/vfs-mw"
    val overridePathLine = "data-local=${Constants.USER_FILE_STORAGE}/OpenMW/Override"
    val companionPathLine = """data="${Constants.USER_FILE_STORAGE}/OpenMW/Mods/companion""""

    val currentLines = if (cfgFile.exists()) cfgFile.readLines() else emptyList()

    val mappedLines = currentLines.map { line ->
        when {
            line.contains(regexData) -> line.replace(regexData, replacementStringData)
            line.contains("resources=./resources") -> line.replace(
                "resources=./resources",
                "resources=${Constants.USER_RESOURCES}"
            )
            else -> line
        }
    }

    // Drop duplicate Morrowind data lines. A stale `data="./Mods/companion"` line
    // clobbered by an earlier over-broad regex now reads identical to the real
    // data path; keep only the first occurrence so an existing config self-heals.
    var seenDataFiles = false
    val modifiedLines = mappedLines.filterNot { line ->
        if (line == replacementStringData) {
            val duplicate = seenDataFiles
            seenDataFiles = true
            duplicate
        } else {
            false
        }
    }

    val finalContent = buildString {
        modifiedLines.forEach { appendLine(it) }

        // Append game-specific settings if not already present
        gameFiles.bsaFallbackLines.forEach { line ->
            if (!modifiedLines.contains(line)) appendLine(line)
        }

        if (!modifiedLines.contains(vfsPathLine)) appendLine(vfsPathLine)

        if (convertedData.isNotBlank() && !modifiedLines.any { it.contains(convertedData.substringBefore("=")) }) {
            appendLine(convertedData)
        }

        if (!modifiedLines.contains(overridePathLine)) appendLine(overridePathLine)

        if (!modifiedLines.contains(companionPathLine)) appendLine(companionPathLine)
    }

    cfgFile.writeText(finalContent.trimEnd() + "\n")
}

private fun updateUserConfig(savedPath: String, gameFiles: GameFiles) {
    val esmFile = File(Constants.USER_OPENMW_CFG)
    val defaultLine = "# This is the user openmw.cfg. Feel free to modify it as you wish."
    val replacementStringData = """data="${savedPath}/Data Files""""

    val shouldOverwrite = !customCFG || (esmFile.exists() && esmFile.readText().trim() == defaultLine)

    if (shouldOverwrite) {
        esmFile.bufferedWriter().use { writer ->
            writer.write(replacementStringData)
            writer.newLine()
            gameFiles.esmContentLines.forEach { line ->
                writer.write(line)
                writer.newLine()
            }
        }
    }
}
