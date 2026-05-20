package org.openmw.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import org.openmw.Constants
import java.io.File

@Composable
fun Terminal() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // State
    var outputLines by remember { mutableStateOf(listOf<AnnotatedLine>()) }
    var inputCommandValue by remember { mutableStateOf(TextFieldValue("")) }
    val commandHistory = remember { mutableStateListOf<String>() }
    var historyIndex by remember { mutableIntStateOf(-1) }
    val scrollState = rememberScrollState()
    var temporaryStatus by remember { mutableStateOf<String?>(null) }
    var currentWorkingDir by remember { mutableStateOf(Environment.getExternalStorageDirectory().absolutePath) }
    var isProcessing by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    var showHelp by remember { mutableStateOf(false) }
    var activeProcess by remember { mutableStateOf<Process?>(null) }

    val aliases = remember {
        mutableMapOf(
            "kram" to "${context.applicationInfo.nativeLibraryDir}/libkram.so",
            "ls" to "ls -F --color=never",
            "ll" to "ls -alF --color=never",
            "storage" to "/storage/emulated/0/",
        )
    }

    // Helpers
    fun appendToOutput(text: String, color: Color = Color.Green) {
        val lines = text.split("\n")
        outputLines = outputLines + lines.map { AnnotatedLine(it, color) }
    }

    fun stopProcess() {
        activeProcess?.destroy()
        activeProcess = null
        isProcessing = false
        appendToOutput("\n[Process Terminated by User]", Color.Red)
    }

    suspend fun runShell(cmd: String) {
        if (isProcessing) return
        isProcessing = true

        val expandedCmd = expandAliases(cmd, aliases)
        appendToOutput("user@openmw:${getShortPath(currentWorkingDir)}$ $cmd", Color.Cyan)

        // Handle built-ins
        if (expandedCmd.startsWith("cd ")) {
            val path = expandedCmd.substring(3).trim()
            val newDir = if (path.startsWith("/")) File(path) else File(currentWorkingDir, path)
            if (newDir.exists() && newDir.isDirectory) {
                currentWorkingDir = newDir.canonicalPath
            } else {
                appendToOutput("cd: $path: No such directory", Color.Red)
            }
            isProcessing = false
            return
        }

        if (cmd.trim() == "clear") {
            outputLines = emptyList()
            isProcessing = false
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val libDir = context.applicationInfo.nativeLibraryDir
                val pb = ProcessBuilder("/system/bin/sh", "-c", expandedCmd)
                    .directory(File(currentWorkingDir))
                    .redirectErrorStream(true)

                pb.environment().apply {
                    put("LD_LIBRARY_PATH", "$libDir:/system/lib64:/system/lib")
                    put("PATH", "$libDir:/system/bin:/system/xbin")
                    put("HOME", context.filesDir.absolutePath)
                    put("TMPDIR", context.cacheDir.absolutePath)
                    put("TERM", "xterm-256color")
                }

                val proc = pb.start()
                activeProcess = proc

                proc.inputStream.bufferedReader().use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val currentLine = line ?: ""
                        withContext(Dispatchers.Main) {
                            appendToOutput(currentLine)
                            Log.i("Kram", currentLine)
                        }
                    }
                }

                val exitCode = proc.waitFor()
                withContext(Dispatchers.Main) {
                    val statusColor = if (exitCode == 0) Color.Green else Color.Red
                    appendToOutput("[Process completed with exit code: $exitCode]", statusColor)
                    if (exitCode != 0) {
                        temporaryStatus = "Exit Code: $exitCode"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    appendToOutput("Error: ${e.message}", Color.Red)
                }
            } finally {
                withContext(Dispatchers.Main) {
                    isProcessing = false
                    activeProcess = null
                }
            }
        }
    }

    // Effects
    LaunchedEffect(outputLines.size) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    LaunchedEffect(temporaryStatus) {
        if (temporaryStatus != null) {
            delay(3000)
            temporaryStatus = null
        }
    }

    // UI
    Column(modifier = Modifier
        .fillMaxSize()
        .imePadding()
        .background(Color(0xFF121212))) {
        // Header
        TerminalHeader(
            status = temporaryStatus ?: if (isProcessing) "RUNNING" else "READY",
            isProcessing = isProcessing,
            onClear = { outputLines = emptyList() },
            onCopy = {
                val text = outputLines.joinToString("\n") { it.text }
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.setPrimaryClip(ClipData.newPlainText("terminal", text))
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            },
            onHelp = { showHelp = true }
        )

        // Output Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .verticalScroll(scrollState)
                .clickable { focusRequester.requestFocus() }
        ) {
            SelectionContainer {
                Column {
                    outputLines.forEach { line ->
                        Text(
                            text = line.text,
                            color = line.color,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }

        // Input Area
        TerminalInputRow(
            input = inputCommandValue,
            onInputChange = { inputCommandValue = it },
            isProcessing = isProcessing,
            currentDir = getShortPath(currentWorkingDir),
            focusRequester = focusRequester,
            onExecute = {
                if (inputCommandValue.text.isNotBlank()) {
                    val cmd = inputCommandValue.text
                    commandHistory.add(cmd)
                    historyIndex = -1
                    // Clear text but keep it ready for next command
                    inputCommandValue = TextFieldValue("") 
                    scope.launch { runShell(cmd) }
                }
            }
        )

        // Quick Tools Bar
        TerminalToolsBar(
            isProcessing = isProcessing,
            onStop = { stopProcess() },
            onHistoryUp = {
                if (commandHistory.isNotEmpty()) {
                    historyIndex = (historyIndex + 1).coerceAtMost(commandHistory.size - 1)
                    val historyCmd = commandHistory[commandHistory.size - 1 - historyIndex]
                    // Set text and select all so next type replaces it
                    inputCommandValue = TextFieldValue(
                        text = historyCmd,
                        selection = TextRange(0, historyCmd.length)
                    )
                }
            },
            onHistoryDown = {
                if (historyIndex > 0) {
                    historyIndex--
                    val historyCmd = commandHistory[commandHistory.size - 1 - historyIndex]
                    inputCommandValue = TextFieldValue(
                        text = historyCmd,
                        selection = TextRange(0, historyCmd.length)
                    )
                } else if (historyIndex == 0) {
                    historyIndex = -1
                    inputCommandValue = TextFieldValue("")
                }
            },
            onTab = {
                // Simple tab completion could be added here
            }
        )
    }

    if (showHelp) {
        HelpDialog(onDismiss = { showHelp = false })
    }
}

data class AnnotatedLine(val text: String, val color: Color)

@Composable
fun TerminalHeader(status: String, isProcessing: Boolean, onClear: () -> Unit, onCopy: () -> Unit, onHelp: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E))
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(" OpenMW Shell ", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                status,
                color = if (isProcessing) Color.Yellow else Color.Green,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            IconButton(onClick = onCopy, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ContentCopy, "Copy", tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onClear, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.DeleteSweep, "Clear", tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = onHelp, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.HelpOutline, "Help", tint = Color.Gray, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun TerminalInputRow(
    input: TextFieldValue,
    onInputChange: (TextFieldValue) -> Unit,
    isProcessing: Boolean,
    currentDir: String,
    focusRequester: FocusRequester,
    onExecute: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$currentDir $ ", color = Color.Cyan, fontSize = 13.sp, fontFamily = FontFamily.Monospace)

        BasicTextField(
            value = input,
            onValueChange = onInputChange,
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester)
                .onKeyEvent {
                    if (it.key == Key.Enter && it.type == KeyEventType.KeyUp) {
                        onExecute()
                        true
                    } else {
                        false
                    }
                },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp
            ),
            cursorBrush = SolidColor(Color.Green),
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go, autoCorrect = false),
            keyboardActions = KeyboardActions(onGo = { onExecute() }),
            enabled = !isProcessing
        )

        // Send Button
        IconButton(
            onClick = onExecute,
            enabled = !isProcessing && input.text.isNotBlank(),
            modifier = Modifier.size(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Send,
                contentDescription = "Send",
                tint = if (!isProcessing && input.text.isNotBlank()) Color.Green else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun TerminalToolsBar(
    isProcessing: Boolean,
    onStop: () -> Unit,
    onHistoryUp: () -> Unit,
    onHistoryDown: () -> Unit,
    onTab: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E1E1E))
            .padding(2.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TextButton(onClick = onHistoryUp) { Text("UP", color = Color.White) }
        TextButton(onClick = onHistoryDown) { Text("DN", color = Color.White) }
        TextButton(onClick = onTab) { Text("TAB", color = Color.White) }

        if (isProcessing) {
            Button(
                onClick = onStop,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(30.dp)
                    .align(Alignment.CenterVertically)
            ) {
                Text("STOP", fontSize = 10.sp)
            }
        }
    }
}

private fun getShortPath(fullPath: String): String {
    val lastPart = fullPath.substringAfterLast("/")
    return if (lastPart.isEmpty()) "/" else lastPart
}

@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Terminal Help", color = Color.Green, fontFamily = FontFamily.Monospace) },
        text = {
            Column {
                Text("Available Commands:", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                listOf(
                    "pwd - Show current directory",
                    "ls - List files",
                    "whoami - Show current user",
                    "help - Show this help",
                    "clear - Clear terminal",
                    "[any command] - Execute custom command"
                ).forEach { cmd ->
                    Text(cmd, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Features:", color = Color.White, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                listOf(
                    "• Command history (UP/DN buttons)",
                    "• Text selection and copy/paste",
                    "• Quick command buttons (STOP)",
                    "• Real-time output",
                    "• Send button & Keyboard Enter support"
                ).forEach { feature ->
                    Text(feature, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        },
        containerColor = Color.DarkGray
    )
}

private fun expandAliases(command: String, aliases: Map<String, String>): String {
    val parts = command.trim().split("\\s+".toRegex())
    if (parts.isNotEmpty()) {
        val firstWord = parts[0]
        val alias = aliases[firstWord]
        if (alias != null) {
            return alias + command.substringAfter(firstWord)
        }
    }
    return command
}
