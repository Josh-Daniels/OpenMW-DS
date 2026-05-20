package org.openmw.fragments

import android.content.Context
import android.os.Environment
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

fun processSelectedFolder(context: Context, folder: File, onUriPersisted: (String?) -> Unit) {
    val dataStoreKey = stringPreferencesKey("game_files_uri")
    try {
        scope.launch {
            val savedPath = folder.absolutePath
            val selectedDirectory = DocumentFile.fromFile(folder)
            val iniFile = selectedDirectory.findFile("Morrowind.ini")
            val dataFilesFolder = selectedDirectory.findFile("Data Files")
            val extensions = arrayOf("esm", "bsa")
            val modDirectory = dataFilesFolder ?: selectedDirectory
            val files = findFilesWithExtensions(modDirectory, extensions)
            val fileName = Constants.OPENMW_CFG
            val file = File(fileName)
            val esmFile = File(Constants.USER_OPENMW_CFG)
            val regexData = Regex("""^data\s*=\s*".*?"""")
            val replacementStringData = """data="${savedPath}/Data Files""""
            val overridePath = Constants.USER_FILE_STORAGE + "/OpenMW/Override"
            val defaultLine = "# This is the user openmw.cfg. Feel free to modify it as you wish."

            if (iniFile != null && dataFilesFolder != null && dataFilesFolder.isDirectory) {
                // Get URI string from data store (simplified flow)
                val uriString = context.dataStore.data.first()[dataStoreKey]
                onUriPersisted(uriString)

                // Predefined file lists
                val orderedEsmFiles = listOf("Morrowind.esm", "Tribunal.esm", "Bloodmoon.esm")
                val orderedBsaFiles = listOf("Morrowind.bsa", "Tribunal.bsa", "Bloodmoon.bsa")

                // Create sets of existing files for faster lookup
                val existingFiles = files.map { it.name }.toSet()

                // Prepare content lines using intersection
                val esmContentLines = orderedEsmFiles
                    .intersect(existingFiles)
                    .map { "content=$it" }

                // Prepare fallback-archive lines using intersection
                val fallbackArchiveLines = orderedBsaFiles
                    .intersect(existingFiles)
                    .map { "fallback-archive=$it" }

                // Read and process lines in one pass
                val modifiedLines = file.useLines { lines ->
                    lines.map { line ->
                        when {
                            line.contains(regexData) -> line.replace(regexData, replacementStringData)
                            line.contains("resources=./resources") -> line.replace(
                                "resources=./resources",
                                "resources=${Constants.USER_RESOURCES}"
                            )
                            else -> line
                        }
                    }.toList()
                }

                // Write everything in one operation
                file.writeText(buildString {
                    // Original modified content
                    modifiedLines.forEach { appendLine(it) }

                    // Append fallback-archive lines
                    fallbackArchiveLines.forEach { appendLine(it) }
                })


                if (!customCFG || esmFile.readText().trim() == defaultLine) {
                    esmFile.bufferedWriter().use { writer ->
                        // Write replacementStringData at the top
                        writer.write(replacementStringData)
                        writer.newLine()

                        // Write esm content lines
                        esmContentLines.forEach { line ->
                            writer.write(line)
                            writer.newLine()
                        }
                    }
                }

                // Fix Calendar
                val file2 = File(Constants.OPENMW_CFG)
                file2.appendText("data=${Constants.USER_RESOURCES}/vfs-mw\n")

                val iniData = context.contentResolver.openInputStream(iniFile.uri)?.use {
                    it.bufferedReader().readText()
                } ?: ""

                val converter = IniConverter(iniData)
                val convertedData = converter.convert()

                val outputFile = File(Constants.GLOBAL_CONFIG, "openmw.cfg")
                val currentContent = outputFile.takeIf { it.exists() }?.readText() ?: ""

                // Prepare all content to append in one operation
                val contentToAppend = buildString {
                    if (!currentContent.contains(convertedData)) {
                        append(convertedData)
                        appendLine()
                    }

                    val dataLocalString = "data-local=$overridePath"
                    if (!currentContent.contains(dataLocalString)) {
                        append(dataLocalString)
                        appendLine()
                    }
                }

                // Only write if there's something to append
                if (contentToAppend.isNotEmpty()) {
                    outputFile.appendText(contentToAppend)
                }
            } else {
                context.dataStore.edit { preferences ->
                    preferences[dataStoreKey] = ""
                }
                onUriPersisted("")
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        onUriPersisted(null)
        MToast(stringRes(R.string.an_error_occurred_while_selecting_the_folder))
    }
}
