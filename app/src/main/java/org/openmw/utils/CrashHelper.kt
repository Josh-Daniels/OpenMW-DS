package org.openmw.utils

import android.content.Context
import android.os.Build
import android.util.Log
import org.openmw.Constants
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class CaptureCrash : Thread.UncaughtExceptionHandler {
    private val TAG = "CrashReporter"

    override fun uncaughtException(thread: Thread, throwable: Throwable) {
        try {
            logCrashDetails(thread, throwable)
            saveCrashLog(throwable)
        } catch (e: Exception) {
            Log.e(TAG, "Error while handling crash", e)
        } finally {
            terminateApp()
        }
    }

    private fun logCrashDetails(thread: Thread, throwable: Throwable) {
        Log.e(TAG, "Crash in thread: ${thread.name} (id: ${thread.id})")
        Log.e(TAG, "Crash details:", throwable)
    }

    private fun saveCrashLog(throwable: Throwable) {
        try {
            val logFile = getCrashFile()
            if (ensureFileExists(logFile)) {
                writeEnhancedCrashLog(logFile, throwable)
            } else {
                Log.w(TAG, "Failed to create primary crash log, using fallback")
                fallbackCrashLog(throwable)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save crash log", e)
            fallbackCrashLog(throwable)
        }
    }

    private fun getCrashFile(): File {
        return try {
            File(Constants.CRASH_FILE).apply {
                parentFile?.mkdirs() // Ensure directory exists
            }
        } catch (e: Exception) {
            File(Constants.INTERNAL_CRASH_FILE)
        }
    }

    private fun ensureFileExists(file: File): Boolean {
        return try {
            file.exists() || file.createNewFile()
        } catch (e: Exception) {
            false
        }
    }

    private fun writeEnhancedCrashLog(file: File, throwable: Throwable) {
        FileWriter(file, true).use { writer ->
            writer.append("=== CRASH REPORT ===\n")
            writer.append("Timestamp: ${getCurrentDateTime()}\n")
            writer.append("App Version: Alpha3\n")
            writer.append("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})\n")
            writer.append("Android Version: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            writer.append("Thread: ${Thread.currentThread().name}\n")
            writer.append("Memory Info: ${getMemoryInfo()}\n\n")
            writer.append("Stack Trace:\n")
            printEnhancedStackTrace(throwable, PrintWriter(writer))
            writer.append("\n=== END REPORT ===\n\n")
        }
    }

    private fun printEnhancedStackTrace(throwable: Throwable, printWriter: PrintWriter) {
        printWriter.println(throwable.toString())

        if (throwable is NullPointerException) {
            printWriter.println("\nNULL POINTER ANALYSIS:")

            // Get the first stack trace element where the NPE occurred
            throwable.stackTrace.firstOrNull()?.let { crashSite ->
                printWriter.println("Crash occurred at: ${crashSite.className}.${crashSite.methodName} (line ${crashSite.lineNumber})")

                // Try to extract the variable name from the line (if source is available)
                try {
                    val sourceFile = File(crashSite.fileName ?: "Unknown")
                    if (sourceFile.exists()) {
                        val lines = sourceFile.readLines()
                        if (crashSite.lineNumber > 0 && crashSite.lineNumber <= lines.size) {
                            val line = lines[crashSite.lineNumber - 1]
                            printWriter.println("Code context: $line")

                            // Simple pattern to find potential null variable
                            val dotIndex = line.indexOf('.')
                            if (dotIndex > 0) {
                                val candidate = line.substring(0, dotIndex).trim()
                                if (candidate.matches(Regex("[a-zA-Z_$][a-zA-Z0-9_$]*"))) {
                                    printWriter.println("Potential null variable: $candidate")
                                }
                            }

                            // not supported below sdk29
                            /*val nullVarPattern = ".*(?<var>[a-zA-Z_$][a-zA-Z0-9_$]*)\\..*".toRegex()
                            val match = nullVarPattern.find(line)
                            match?.groups?.get("var")?.let {
                                printWriter.println("Potential null variable: ${it.value}")
                            }*/
                        }
                    }
                } catch (e: Exception) {
                    printWriter.println("Could not analyze source: ${e.message}")
                }
            }
            printWriter.println()
        }


        // Enhanced stack trace with method arguments where available
        throwable.stackTrace.forEachIndexed { index, element ->
            printWriter.println("\t[$index] $element")
        }

        // Include suppressed exceptions
        throwable.suppressed.forEach { suppressed ->
            printWriter.println("\nSuppressed Exception:")
            printEnhancedStackTrace(suppressed, printWriter)
        }

        // Include root cause
        var cause = throwable.cause
        if (cause != null && cause != throwable) {
            printWriter.println("\nCaused by:")
            printEnhancedStackTrace(cause, printWriter)
        }
    }

    private fun getAppVersion(): String {
        return try {
            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            "${packageInfo.versionName} (${packageInfo.versionCode})"
        } catch (e: Exception) {
            "Unknown"
        }
    }

    private fun getMemoryInfo(): String {
        val runtime = Runtime.getRuntime()
        val usedMem = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val maxMem = runtime.maxMemory() / (1024 * 1024)
        return "$usedMem MB used / $maxMem MB max"
    }

    private fun fallbackCrashLog(throwable: Throwable) {
        try {
            val fallbackFile = File(Constants.INTERNAL_CRASH_FILE).apply {
                parentFile?.mkdirs()
            }
            if (ensureFileExists(fallbackFile)) {
                writeEnhancedCrashLog(fallbackFile, throwable)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to write to fallback crash log", e)
        }
    }

    private fun terminateApp() {
        try {
            // Give time for logs to be written
            Thread.sleep(1000)
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(1)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to terminate app properly", e)
            exitProcess(1)
        }
    }

    private fun getCurrentDateTime(): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS Z", Locale.getDefault()).format(Date())
    }

    companion object {
        // Initialize with application context
        lateinit var context: Context
        fun initialize(appContext: Context) {
            context = appContext.applicationContext
        }
    }
}
