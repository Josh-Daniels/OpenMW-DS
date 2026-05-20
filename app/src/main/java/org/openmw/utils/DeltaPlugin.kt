package org.openmw.utils

import android.content.Context
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmw.Constants
import org.openmw.R
import org.openmw.ui.controls.UIStateManager.customColor
import org.openmw.utils.GameFilesPreferences.getGameFilesUri
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

fun baseRules(context: Context) {
    val openmwCfgFile = File("${Constants.USER_CONFIG}/openmw.cfg")
    if (openmwCfgFile.exists()) {
        val lines = openmwCfgFile.readLines().toMutableList()
        val gameFilesUri = getGameFilesUri(context)
        val lineToAdd = "data = ${gameFilesUri}/Data Files/"
        if (gameFilesUri != null && !lines.contains(lineToAdd)) {
            lines.add(0, lineToAdd)
            openmwCfgFile.writeText(lines.joinToString("\n"))
        }
    }
}

@Composable
fun DeltaScreen() {
    val context = LocalContext.current
    var commandInput by remember { mutableStateOf("") }
    var output by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(output) {
        listState.animateScrollToItem(listState.layoutInfo.totalItemsCount)
    }

    Column(modifier = Modifier.padding(1.dp)) {
        TextField(
            value = commandInput,
            onValueChange = { commandInput = it },
            label = { Text(stringResource(R.string.command_input)) },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(
                onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        executeQueryCommand(context, commandInput) { commandOutput ->
                            withContext(Dispatchers.Main) {
                                output += commandOutput
                                output = output.plus(commandOutput).plus("\n")
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = customColor, // Background color
                    contentColor = Color.White   // Text color
                )
            ) {
                Text(stringResource(R.string.query))
            }

            Button(
                onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        delta(context, commandInput) { commandOutput ->
                            withContext(Dispatchers.Main) {
                                output = output.plus(commandOutput).plus("\n")
                            }
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = customColor, // Background color
                    contentColor = Color.White   // Text color
                )
            ) {
                Text(stringResource(R.string.delta))
            }


            Button(
                onClick = {
                    CoroutineScope(Dispatchers.IO).launch {
                        groundcoverify(context, pretend = false, onOutputUpdate = { newOutput ->
                            withContext(Dispatchers.Main) {
                                output = output.plus(newOutput).plus("\n")

                            }
                        })
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = customColor, // Background color
                    contentColor = Color.White   // Text color
                )
            ) {
                Text(stringResource(R.string.groundcoverify))
            }


        }

        Button(
            onClick = {
                CoroutineScope(Dispatchers.IO).launch {
                    revertChanges(context) { newOutput ->
                        withContext(Dispatchers.Main) {
                            output += newOutput + "\n"
                        }
                    }
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = customColor, // Background color
                contentColor = Color.White   // Text color
            )
        ) {
            Text(stringResource(R.string.reset_all))
        }

        Spacer(modifier = Modifier.height(16.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.Black)
                .padding(8.dp)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    Text(
                        text = output,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }
    }
}

fun executeQueryCommand(context: Context, commandInput: String, onOutputUpdate: suspend (AnnotatedString) -> Unit) {
    val command = "./libdelta_plugin.so -v --verbose -c ${Constants.USER_OPENMW_CFG} query $commandInput"
    CoroutineScope(Dispatchers.IO).launch {
        // Execute the command with real-time updates
        shellExec(context, command, onOutputUpdate)
    }
}

fun delta(context: Context, commandInput: String, onOutputUpdate: suspend (AnnotatedString) -> Unit) {
    val gameFilesUri = getGameFilesUri(context)
    val gamePath = "$gameFilesUri"

    baseRules(context)

    val configFile = File(Constants.USER_OPENMW_CFG)

    val lines = configFile.readLines().toMutableList().apply {
        removeAll { it.contains("content=delta-merged.omwaddon") || it.contains("data=${Constants.USER_DELTA}") || it.contains("data=\"$gamePath/Data Files\"") }
    }
    configFile.writeText(lines.joinToString("\n"))

    val command = "./libdelta_plugin.so -v --verbose -c ${Constants.USER_CONFIG}/openmw.cfg merge --skip Cell $commandInput ${Constants.USER_DELTA}/delta-merged.omwaddon"

    CoroutineScope(Dispatchers.IO).launch {
        // Execute the command with real-time updates
        shellExec(context, command) { outputLine ->
            withContext(Dispatchers.Main) {
                onOutputUpdate(outputLine)
            }
        }

        // Append the lines after the command execution
        val deltaMergeOutput = "content=delta-merged.omwaddon"
        val deltaPath = "data=${Constants.USER_DELTA}"
        configFile.appendText("\n$deltaPath\n$deltaMergeOutput\n")
    }
}

fun groundcoverify(context: Context, pretend: Boolean = false, onOutputUpdate: suspend (AnnotatedString) -> Unit) {
    val gameFilesUri = getGameFilesUri(context)
    val gamePath = "$gameFilesUri"
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val configFile = File("${Constants.USER_CONFIG}/openmw.cfg")

            baseRules(context)

            appendFallbackArchives(context)

            val lines2 = configFile.readLines().toMutableList().apply {
                removeAll { it.contains("data=\"$gamePath/Data Files\"") }
            }
            configFile.writeText(lines2.joinToString("\n"))

            val lines = configFile.readLines().toMutableList().apply {
                removeAll { it.contains("groundcover=groundcover.omwaddon") || it.contains("content=deleted_groundcover.omwaddon") }
            }

            val grassIds = "grass|kelp|lilypad|fern|thirrlily|spartium|in_cave_plant|reedgroup"
            val excludeIds = "refernce|infernace|planter|_furn_|_skelp|t_glb_var_skeleton|cliffgrass|terr|grassplane|flora_s_m_10_grass|cave_mud_rocks_fern|ab_in_cavemold|rp_mh_rock|ex_cave_grass00|secret_fern"
            val ids_expr = "^(?!.*($excludeIds).*).*($grassIds).*$"
            val exteriorCellRegex = "^[0-9\\-]+x[0-9\\-]+$"

            val command = StringBuilder("./libdelta_plugin.so -v --verbose -c ")
                .append("$configFile filter --all --output ")
                .append("${Constants.USER_DELTA}/groundcover.omwaddon --desc \"Generated groundcover plugin\" match Cell --cellref-object-id \"$ids_expr\" --id \"$exteriorCellRegex\" match Static --id \"$ids_expr\" --modify model \"^\" \"grass\\\\\"")
            if (!pretend) {
                command.append(" && ")
                    .append("./libdelta_plugin.so -v --verbose -c $configFile filter --all --output ${Constants.USER_DELTA}/deleted_groundcover.omwaddon match Cell --cellref-object-id \"$ids_expr\" --id \"$exteriorCellRegex\" --delete")
            }
            command.append(" && ")
                .append("./libdelta_plugin.so -v --verbose -c $configFile query --input ${Constants.USER_DELTA}/groundcover.omwaddon --ignore ${Constants.USER_DELTA}/deleted_groundcover.omwaddon match Static")

            val output = shellExec(context, command.toString(), onOutputUpdate)
            val outputlines = output.split("\n")
            val modelLines = outputlines.filter { it.trim().startsWith("model:") }
            val paths = modelLines.map { it.substringAfter("model: \"grass").replace("\\\\", "/").trim().replace("\"", "") }
            File(Constants.USER_CONFIG + "/groundcoverify.log").writeText(output)
            File(Constants.USER_CONFIG + "/paths.log").writeText(paths.toString())

            // Total number of files to process
            val totalFiles = paths.size
            var processedFiles = 0

            paths.forEach { path ->
                val filename = path.substringAfterLast("/")
                val correctedPath = path.substringBeforeLast("/").trim()

                val command2 = StringBuilder("mkdir -p ")
                command2.append(Constants.USER_DELTA + "/Meshes/grass/$correctedPath")
                command2.append(" && ")
                command2.append("./libdelta_plugin.so -v --verbose -c ")
                command2.append("$configFile vfs-extract \"Meshes$correctedPath/$filename\" ")
                command2.append(Constants.USER_DELTA + "/Meshes/grass/$correctedPath/$filename")

                Log.d("Groundcoverify", "Executing command: $command2")

                // Wrapper for converting String to AnnotatedString
                val stringOutputUpdate: suspend (AnnotatedString) -> Unit = { line ->
                    withContext(Dispatchers.Main) {
                        onOutputUpdate(AnnotatedString(line.toString()))
                    }
                }
                // Execute the command and log output
                shellExec(context, command2.toString(), stringOutputUpdate)

                // Update processed file count
                processedFiles++

                // Calculate and print progress percentage
                val progressPercentage = (processedFiles.toDouble() / totalFiles.toDouble()) * 100
                withContext(Dispatchers.Main) {
                    onOutputUpdate(greenText("Progress: ${progressPercentage.toInt()}%"))
                }
            }

            lines.add("content=deleted_groundcover.omwaddon")
            lines.add("groundcover=groundcover.omwaddon")
            configFile.writeText(lines.joinToString("\n"))

            withContext(Dispatchers.Main) {
                onOutputUpdate(greenText("Groundcoverify Complete!"))
            }

        } catch (e: Exception) {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            e.printStackTrace(pw)
            withContext(Dispatchers.Main) {
                onOutputUpdate(greenText("Error executing command: ${e.message}\nStacktrace:\n$sw"))
            }
        }
    }
}

suspend fun shellExec(context: Context, cmd: String, onOutputUpdate: suspend (AnnotatedString) -> Unit): String {
    val output = StringBuilder()
    val filesDirPath = context.filesDir.absolutePath
    val workingDir = context.applicationInfo.nativeLibraryDir

    try {
        val processBuilder = ProcessBuilder().apply {
            if (workingDir != null) {
                directory(File(workingDir))
            }
            command("/system/bin/sh", "-c", "export HOME=$filesDirPath; $cmd")
            redirectErrorStream(true)
        }

        val process = withContext(Dispatchers.IO) {
            processBuilder.start()
        }
        val reader = process.inputStream.bufferedReader()

        var line: String?
        while (withContext(Dispatchers.IO) {
                reader.readLine()
            }.also { line = it } != null) {
            withContext(Dispatchers.Main) {
                onOutputUpdate(AnnotatedString(line ?: ""))
            }
            output.append(line).append("\n")
        }

        withContext(Dispatchers.IO) {
            process.waitFor()
        }
    } catch (e: Exception) {
        val sw = StringWriter()
        val pw = PrintWriter(sw)
        e.printStackTrace(pw)
        val errorOutput = "Error executing command: ${e.message}\nStacktrace:\n$sw"
        withContext(Dispatchers.Main) {
            onOutputUpdate(AnnotatedString(errorOutput))
        }
        output.append(errorOutput)
    }

    return output.toString()
}

fun revertChanges(context: Context, onOutputUpdate: suspend (String) -> Unit) {
    val gameFilesUri = getGameFilesUri(context)
    val gamePath = "$gameFilesUri"
    CoroutineScope(Dispatchers.IO).launch {
        try {
            val configFile = File("${Constants.USER_CONFIG}/openmw.cfg")
            val lines = configFile.readLines().toMutableList().apply {
                removeAll {
                    it.contains("content=delta-merged.omwaddon") ||
                            it.contains("data=${Constants.USER_DELTA}") ||
                            it.contains("data=\"$gamePath/Data Files\"") ||
                            it.contains("groundcover=groundcover.omwaddon") ||
                            it.contains("content=deleted_groundcover.omwaddon")
                }
            }
            configFile.writeText(lines.joinToString("\n"))

            // Delete the generated files
            val deltaMergedFile = File("${Constants.USER_DELTA}/delta-merged.omwaddon")
            val outputGroundcoverFile = File("${Constants.USER_DELTA}/groundcover.omwaddon")
            val outputDeletedFile = File("${Constants.USER_DELTA}/deleted_groundcover.omwaddon")
            val meshesFolder = File("${Constants.USER_DELTA}/Meshes")

            if (deltaMergedFile.exists()) deltaMergedFile.delete()
            if (outputGroundcoverFile.exists()) outputGroundcoverFile.delete()
            if (outputDeletedFile.exists()) outputDeletedFile.delete()

            // Delete the Meshes folder and its contents
            if (meshesFolder.exists()) {
                meshesFolder.deleteRecursively()
            }

            withContext(Dispatchers.Main) {
                onOutputUpdate("Reverted changes successfully.")
            }
        } catch (e: Exception) {
            val sw = StringWriter()
            val pw = PrintWriter(sw)
            e.printStackTrace(pw)
            withContext(Dispatchers.Main) {
                onOutputUpdate("Error reverting changes: ${e.message}\nStacktrace:\n$sw")
            }
        }
    }
}

fun greenText(text: String): AnnotatedString {
    return buildAnnotatedString {
        withStyle(style = SpanStyle(color = Color.Green, fontWeight = FontWeight.Bold, fontSize = 20.sp)) {
            append(text)
        }
    }
}

fun appendFallbackArchives(context: Context) {
    val configFile = File("${Constants.USER_CONFIG}/openmw.cfg")

    // Get game files URI
    val gameFilesUri = getGameFilesUri(context)
    val gamePath = "$gameFilesUri/Data Files"

    // List of BSA files to check
    val bsaFiles = listOf("Morrowind.bsa", "Tribunal.bsa", "Bloodmoon.bsa")

    // Search for existing BSA files in the specified directory
    val existingBsaFiles = bsaFiles.filter { bsaFile ->
        File("$gamePath/$bsaFile").exists()
    }.map { bsaFile ->
        "fallback-archive=$bsaFile"
    }

    if (configFile.exists()) {
        // Read the current content of the config file
        val currentConfigContent = configFile.readText()

        // Filter out the fallback archive lines that already exist in the config file
        val newFallbackArchiveLines = existingBsaFiles.filterNot { line ->
            currentConfigContent.contains(line)
        }

        // Append only the new fallback archive lines to the config file
        if (newFallbackArchiveLines.isNotEmpty()) {
            configFile.appendText("\n" + newFallbackArchiveLines.joinToString("\n"))
        } else {
            println("All fallback-archive lines already exist.")
        }
    } else {
        // Handle the case where the file does not exist
        println("Configuration file not found.")
    }
}
