package org.openmw.modDownloader

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.openmw.Constants
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

object FileLogger {
    private const val LOG_FILE_NAME = "mod_downloader_debug.log"
    private const val MAX_LOG_SIZE = 50 * 1024 * 1024 // 50MB
    private val lock = ReentrantLock()
    private val logBuffer = StringBuilder()

    private fun getLogFile(context: Context): File {
        val logDir = File("${Constants.USER_FILE_STORAGE}/OpenMW")
        if (!logDir.exists()) {
            logDir.mkdirs()
        }
        return File(logDir, LOG_FILE_NAME)
    }

    private fun shouldRotateLog(file: File): Boolean {
        return file.exists() && file.length() > MAX_LOG_SIZE
    }

    private fun rotateLog(file: File) {
        try {
            if (file.exists()) {
                val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                val rotatedFile = File(file.parent, "mod_downloader_debug_$timestamp.log")
                file.renameTo(rotatedFile)
            }
        } catch (e: Exception) {
            Log.e("FileLogger", "Failed to rotate log file", e)
        }
    }

    fun logDebug(context: Context, tag: String, message: String) {
        lock.withLock {
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                val logMessage = "$timestamp [DEBUG] $tag: $message\n"
                logBuffer.append(logMessage)

                // Also log to Android logcat for immediate viewing
                Log.d(tag, message)

            } catch (e: Exception) {
                Log.e("FileLogger", "Failed to buffer log message", e)
            }
        }
    }

    fun flushLogsToFile(context: Context) {
        lock.withLock {
            try {
                if (logBuffer.isEmpty()) return

                val logFile = getLogFile(context)

                // Rotate log if it's too large
                if (shouldRotateLog(logFile)) {
                    rotateLog(logFile)
                }

                FileWriter(logFile, true).use { writer ->
                    PrintWriter(writer).use { printWriter ->
                        printWriter.append(logBuffer.toString())
                        printWriter.flush()
                    }
                }

                // Clear the buffer after writing
                logBuffer.clear()

                Log.d("FileLogger", "Flushed ${logBuffer.length} characters to log file")

            } catch (e: Exception) {
                Log.e("FileLogger", "Failed to write buffered logs to file", e)
            }
        }
    }

    fun logError(context: Context, tag: String, message: String, exception: Exception? = null) {
        lock.withLock {
            try {
                val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US).format(Date())
                val errorMessage = if (exception != null) {
                    "$timestamp [ERROR] $tag: $message - ${exception.message}\n${exception.stackTraceToString()}\n"
                } else {
                    "$timestamp [ERROR] $tag: $message\n"
                }
                logBuffer.append(errorMessage)

                // Also log to Android logcat
                Log.e(tag, message, exception)

            } catch (e: Exception) {
                Log.e("FileLogger", "Failed to buffer error log", e)
            }
        }
    }

    fun clearLog(context: Context) {
        lock.withLock {
            try {
                val logFile = getLogFile(context)
                if (logFile.exists()) {
                    logFile.delete()
                }
                logBuffer.clear()
            } catch (e: Exception) {
               Log.e("FileLogger", "Failed to clear log file", e)
            }
        }
    }

    fun getLogContents(context: Context): String? {
        lock.withLock {
            return try {
                val logFile = getLogFile(context)
                if (logFile.exists()) {
                    logFile.readText()
                } else {
                    "No log file found"
                }
            } catch (e: Exception) {
                Log.e("FileLogger", "Failed to read log file", e)
                "Error reading log file: ${e.message}"
            }
        }
    }

    fun getBufferSize(): Int {
        return logBuffer.length
    }
}

@Composable
fun LogViewerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var logContent by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        logContent = withContext(Dispatchers.IO) {
            FileLogger.getLogContents(context)
        }
        isLoading = false
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(600.dp)
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Debug Logs", style = MaterialTheme.typography.h1)
                    Row {
                        Button(
                            onClick = {
                                // Clear logs
                                FileLogger.clearLog(context)
                                logContent = "Logs cleared"
                            },
                        ) {
                            Text("Clear")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            Text(
                                text = logContent ?: "No log content",
                                style = MaterialTheme.typography.h2,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.1f))
                                    .padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}