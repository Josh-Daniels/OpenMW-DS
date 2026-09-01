package org.openmw.ui.view

import androidx.compose.material3.Button
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import org.openmw.ui.theme.MwBone
import org.openmw.ui.theme.MwBoneDim
import org.openmw.ui.theme.MwBronze
import org.openmw.ui.theme.MwBronzeDark
import org.openmw.ui.theme.MwBronzeLight
import org.openmw.ui.theme.MwFloatStone
import org.openmw.ui.theme.MwSlotBg
import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Point
import android.hardware.display.DisplayManager
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.system.Os
import android.view.Display
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.rememberImagePainter
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log
import org.openmw.Constants
import org.openmw.R
import org.openmw.ui.controls.UIStateManager
import org.openmw.ui.controls.UIStateManager.cpuUsageFlow
import org.openmw.ui.controls.UIStateManager.customColor
import org.openmw.ui.controls.UIStateManager.logMessagesFlow
import org.openmw.ui.controls.UIStateManager.memoryInfoFlow
import org.openmw.ui.controls.UIStateManager.userUI
import org.openmw.ui.overlay.MemoryInfo
import org.openmw.utils.GameFilesPreferences.getBackgroundAnimationFlow
import org.openmw.utils.GameFilesPreferences.readCodeGroup
import org.openmw.utils.stringRes
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.sin

data class LogEntry(
    val message: String,
    val textSize: Int,
    val textColor: Color
)

object LogRepository {
    private val _logs = MutableStateFlow<List<LogEntry>>(emptyList())
    val logs: StateFlow<List<LogEntry>> get() = _logs

    fun addLog(message: String, textSize: Int = 12, textColor: Color = Color.White) {
        val logEntry = LogEntry(message, textSize, textColor)
        _logs.value += logEntry
    }
}

fun addCustomLog(message: String, textSize: Int = 12, textColor: Color = Color.White) {
    LogRepository.addLog(message, textSize, textColor)
}

@Composable
fun LogsBox(logs: StateFlow<List<LogEntry>>, fontSize: Float, boxWidth: Float, boxHeight: Float) {
    val logList by logs.collectAsState()
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto scroll to bottom when content changes
    LaunchedEffect(logList.size) {
        if (logList.isNotEmpty()) {
            coroutineScope.launch {
                lazyListState.animateScrollToItem(logList.size - 1)
            }
        }
    }

    Box(
        modifier = Modifier
            .width(boxWidth.dp)
            .height(boxHeight.dp)
            .background(Color.Transparent)
            .padding(8.dp)
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Bottom
        ) {
            items(logList) { log ->
                Text(
                    text = log.message,
                    color = log.textColor,
                    fontSize = fontSize.sp,
                    style = TextStyle(fontWeight = FontWeight.Normal)
                )
            }
        }
    }
}

// Function to start logging updates
@DelicateCoroutinesApi
fun startLoggingUpdates() {
    GlobalScope.launch {
        while (UIStateManager.isLoggingEnabled || UIStateManager.showLogCat) {
            val logMessages = getMessages().joinToString("\n")
            logMessagesFlow.value = logMessages
            delay(2000)
        }
        logMessagesFlow.value = ""
    }
}

@DelicateCoroutinesApi
private var resourceUpdateJob: kotlinx.coroutines.Job? = null

@DelicateCoroutinesApi
fun startResourceInfoUpdates(context: Context) {
    if (resourceUpdateJob?.isActive == true) return

    resourceUpdateJob = GlobalScope.launch(Dispatchers.IO) {
        while (UIStateManager.isMemoryInfoEnabled || UIStateManager.isPerformanceHudEnabled) {
            // Existing memory & CPU
            val memoryInfo = getMemoryInfo(context)
            val cpuUsage = getCpuProcessUsage()

            // GPU readings (Qualcomm/Adreno specific – may be null/empty on non-KGSL devices)
            var gpuModel = "Unknown"
            var gpuTemp = "--°C"
            var gpuUtilization = "--%"
            var gpuClock = "--MHz"
            var cpuTemp = "--°C"

            try {
                // CPU Temperature
                val cpuPath = "/sys/class/thermal/${UIStateManager.userSetTemp}/temp"
                var cpuTempRaw = readProcessOutput("cat", cpuPath).trim().toDoubleOrNull() ?: 0.0
                
                cpuTemp = if (cpuTempRaw > 0) {
                    val temp = if (cpuTempRaw > 1000) cpuTempRaw / 1000 else cpuTempRaw
                    "${temp.toInt()}°C"
                } else "--°C"

                // GPU Node paths
                val gpuNode = "/sys/class/kgsl/${UIStateManager.userSetGPU}"

                // GPU Model
                gpuModel = readProcessOutput("cat", "$gpuNode/gpu_model")
                    .trim()
                    .takeIf { it.isNotEmpty() } ?: "Unknown"

                // Temperature
                val gpuTempPath = if (UIStateManager.userSetGPUTemp.startsWith("thermal_zone")) {
                    "/sys/class/thermal/${UIStateManager.userSetGPUTemp}/temp"
                } else {
                    "$gpuNode/temp"
                }
                
                val tempStr = readProcessOutput("cat", gpuTempPath)
                val tempValue = tempStr.trim().toDoubleOrNull()?.div(1000) ?: 0.0
                gpuTemp = if (tempValue > 0) {
                    "${tempValue.toInt()}°C"
                } else {
                    // One last attempt at direct kgsl temp if thermal zone failed
                    val kgslTemp = readProcessOutput("cat", "$gpuNode/temp").trim().toDoubleOrNull()?.div(1000) ?: 0.0
                    if (kgslTemp > 0) "${kgslTemp.toInt()}°C" else "--°C"
                }

                // Utilization
                val busyStr = readProcessOutput("cat", "$gpuNode/gpu_busy_percentage").trim()
                gpuUtilization = if (busyStr.isNotEmpty() && busyStr != "0") {
                    val clean = busyStr.removeSuffix("%").trim()
                    if (clean.isNotEmpty()) "$clean%" else "0%"
                } else {
                    val gpuStats = readProcessOutput("cat", "$gpuNode/gpubusy")
                    val statsParts = gpuStats.trim().split("\\s+".toRegex())
                    if (statsParts.size >= 2) {
                        val busy = statsParts[0].toDoubleOrNull() ?: 0.0
                        val total = statsParts[1].toDoubleOrNull() ?: 1.0
                        val utilization = if (total > 0) (busy / total) * 100 else 0.0
                        "${utilization.toInt()}%"
                    } else "0%"
                }

                // Clock
                val clockStr = readProcessOutput("cat", "$gpuNode/clock_mhz")
                gpuClock = if (clockStr.isNotBlank()) {
                    "${clockStr.trim()} MHz"
                } else "--MHz"

            } catch (_: Exception) {}

            // Update Histories
            UIStateManager.totalMemoryMB = memoryInfo.totalBytes / (1024 * 1024)
            updateHistory(UIStateManager.cpuHistory, cpuUsage)
            updateHistory(UIStateManager.gpuHistory, gpuUtilization.removeSuffix("%").trim().toIntOrNull() ?: 0)
            updateHistory(UIStateManager.memoryHistory, (memoryInfo.totalBytes - memoryInfo.availableBytes) / (1024 * 1024))
            updateHistory(UIStateManager.cpuTempHistory, cpuTemp.removeSuffix("°C").trim().toIntOrNull() ?: 0)
            updateHistory(UIStateManager.gpuTempHistory, gpuTemp.removeSuffix("°C").trim().toIntOrNull() ?: 0)

            // Build the display string
            memoryInfoFlow.value = getDetailedSystemInfo(context, memoryInfo, cpuUsage, gpuModel, gpuUtilization, gpuTemp, gpuClock)
            cpuUsageFlow.value = cpuUsage

            delay(1000)
        }

        // Cleanup
        memoryInfoFlow.value = ""
        cpuUsageFlow.value = 0
    }
}

fun vibrate(context: Context, amplitude: Int = VibrationEffect.DEFAULT_AMPLITUDE, duration: Long = 100L) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    if (vibrator.hasVibrator()) {
        vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude))
    }
}

fun vibrateHelper(context: Context, amplitude: Int, duration: Long) {
    vibrate(context, amplitude, duration)
}

fun getDirectorySize(directory: File): Long {
    var totalSize: Long = 0
    if (directory.isDirectory) {
        val files = directory.listFiles()
        if (files != null) {
            for (file in files) {
                totalSize += try {
                    if (file.isDirectory) getDirectorySize(file) else file.length()
                } catch (_: SecurityException) {
                    // If we encounter a security exception, we ignore the size of that particular file/directory
                    0
                }
            }
        }
    }
    return totalSize
}

fun getCpuProcessUsage(): Int {
    try {
        val pid = Process.myPid().toString()
        val cores = Runtime.getRuntime().availableProcessors()
        val process = Runtime.getRuntime().exec("top -n 1 -o PID,%CPU")
        val bufferedReader = BufferedReader(InputStreamReader(process.inputStream))
        var line = bufferedReader.readLine()
        while (line != null) {
            if (line.contains(pid)) {
                val rawCpu = line.split(" ").last().toInt()
                return rawCpu / cores
            }
            line = bufferedReader.readLine()
        }
    } catch (_: Exception) {
        return 0
    }
    return 0
}

fun getAvailableStorageSpace(): String {
    val storageDirectory = Environment.getExternalStorageDirectory()
    val stat = StatFs(storageDirectory.toString())
    val availableBytes = stat.availableBytes
    return humanReadableByteCountBin(availableBytes)
}

fun <T> updateHistory(flow: MutableStateFlow<List<T>>, newValue: T) {
    val current = flow.value.toMutableList()
    current.add(newValue)
    if (current.size > 30) current.removeAt(0)
    flow.value = current
}

fun scanSystemNodes(parentPath: String, valueFile: String): List<Triple<String, String, String>> {
    val results = mutableListOf<Triple<String, String, String>>()
    val root = File(parentPath)
    if (!root.exists() || !root.isDirectory) return results

    root.listFiles()?.sortedBy { it.name }?.forEach { folder ->
        if (folder.isDirectory) {
            val target = File(folder, valueFile)
            if (target.exists() && target.canRead()) {
                try {
                    val value = target.readText().trim()
                    val labelFile = if (parentPath.contains("thermal")) "type" else if (parentPath.contains("kgsl")) "gpu_model" else null
                    val label = labelFile?.let { File(folder, it).takeIf { f -> f.exists() && f.canRead() }?.readText()?.trim() } ?: ""
                    
                    results.add(Triple(folder.name, value, label))
                } catch (_: Exception) {
                    // Permission denied or other error
                }
            }
        }
    }
    return results
}

fun getDetailedSystemInfo(
    context: Context,
    mem: MemoryInfo,
    cpu: Int,
    gpuModel: String,
    gpuUsage: String,
    gpuTemp: String,
    gpuClock: String
): String {
    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val runtime = Runtime.getRuntime()
    val pid = Process.myPid()
    val pMemInfo = am.getProcessMemoryInfo(intArrayOf(pid))[0]

    return """
        --- SYSTEM RESOURCES ---
        CPU Usage: $cpu%
        GPU: $gpuModel ($gpuClock)
        GPU Load: $gpuUsage | Temp: $gpuTemp
        
        --- DEVICE MEMORY (ActivityManager) ---
        Total: ${mem.totalMemory}
        Available: ${mem.availableMemory}
        Used: ${mem.usedMemory}
        Low Memory State: ${am.isLowRamDevice} (Threshold: ${humanReadableByteCountBin(ActivityManager.MemoryInfo().apply { am.getMemoryInfo(this) }.threshold)})
        
        --- PROCESS MEMORY (PSS) ---
        Total PSS: ${pMemInfo.totalPss / 1024} MB
        Dalvik PSS: ${pMemInfo.dalvikPss / 1024} MB
        Native PSS: ${pMemInfo.nativePss / 1024} MB
        Other PSS: ${pMemInfo.otherPss / 1024} MB
        Private Dirty: ${pMemInfo.totalPrivateDirty / 1024} MB
        
        --- JVM RUNTIME ---
        Max Heap: ${humanReadableByteCountBin(runtime.maxMemory())}
        Total Allocated: ${humanReadableByteCountBin(runtime.totalMemory())}
        Free in Heap: ${humanReadableByteCountBin(runtime.freeMemory())}
        
        --- DEVICE INFO ---
        Device: ${Build.MANUFACTURER} ${Build.MODEL}
        OS: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})
        ABIs: ${Build.SUPPORTED_ABIS.joinToString(", ")}
    """.trimIndent()
}

fun getMemoryInfo(context: Context): MemoryInfo {
    val memoryInfo = ActivityManager.MemoryInfo()
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    activityManager.getMemoryInfo(memoryInfo)
    val totalMemory = humanReadableByteCountBin(memoryInfo.totalMem)
    val availableMemory = humanReadableByteCountBin(memoryInfo.availMem)
    val usedMemory = humanReadableByteCountBin(memoryInfo.totalMem - memoryInfo.availMem)

    return MemoryInfo(totalMemory, availableMemory, usedMemory, memoryInfo.totalMem, memoryInfo.availMem)
}

@SuppressLint("DefaultLocale")
fun humanReadableByteCountBin(bytes: Long): String {
    val unit = 1024
    if (bytes < unit) return "$bytes B"
    val exp = (ln(bytes.toDouble()) / ln(unit.toDouble())).toInt()
    val pre = "KMGTPE"[exp - 1] + "i"
    return String.format("%.1f %sB", bytes / unit.toDouble().pow(exp.toDouble()), pre)
}

fun getBatteryStatus(context: Context): String {
    val batteryStatus = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
    val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
    val isCharging = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) != 0
    val batteryPct = (level / scale.toFloat()) * 100
    return "${stringRes(R.string.battery)}: ${batteryPct.toInt()}%${if (isCharging) " (${stringRes(R.string.charging)})" else ""}"
}

fun getFolderSize(folder: File): Long {
    if (!folder.exists() || !folder.isDirectory) return 0L
    var totalSize: Long = 0
    folder.listFiles()?.forEach { file ->
        if (file.isFile) {
            totalSize += file.length()
        } else if (file.isDirectory) {
            totalSize += getFolderSize(file)
        }
    }
    return totalSize
}

fun getMessages(): List<String> {
    return try {
        val log = ProcessBuilder("logcat", "-d", "-T", "100", "--pid=${Process.myPid()}", "*:${UIStateManager.logcatLevel}")
            .redirectErrorStream(true)
            .start()

        log.inputStream.bufferedReader().use { it.readLines() }
    } catch (_: Exception) {
        emptyList()
    }
}

fun enableLogcat() {
    val logcatFile = File(Constants.USER_CONFIG + "/openmw_logcat.txt")
    if (logcatFile.exists()) {
        logcatFile.delete()
    }

    val processBuilder = ProcessBuilder()
    val commandToExecute = arrayOf("/system/bin/sh", "-c", "logcat *:${UIStateManager.logcatLevel} -d -f ${Constants.USER_CONFIG}/openmw_logcat.txt")
    processBuilder.command(*commandToExecute)
    processBuilder.redirectErrorStream(true)
    processBuilder.start()
}

/**
 * Writes the game's render resolution into `settings.cfg`.
 *
 * AUTHORITATIVE: every existing `resolution x`/`resolution y` line is overwritten, not
 * just the shipped `= 0` sentinel. The old sentinel-only behaviour meant the very first
 * launch permanently decided the value — so a first launch that landed on the companion
 * (bottom) display baked that display's size in, and nothing could ever re-detect it.
 * Overwriting unconditionally makes each launch self-correcting, which also repairs
 * devices already stuck on a wrong value. Callers gate this on the user's
 * `AVOID_RESOLUTION_INSERTION` preference; pass the TOP screen's size (see
 * [topScreenRealSize]), never the calling window's.
 */
fun updateResolutionInConfig(width: Int, height: Int) {
    if (width <= 0 || height <= 0) return

    // Ensure the larger value is assigned to width
    val (adjustedWidth, adjustedHeight) = if (width > height) width to height else height to width

    val file = File(Constants.SETTINGS_FILE)
    if (!file.exists()) return

    val original = file.readLines()
    var sawX = false
    var sawY = false
    val lines = original.map { line ->
        val key = line.trimStart()
        when {
            // Update lines based on the adjusted width and height
            key.startsWith("# Width recommended for your device") -> "# Width recommended for your device = $adjustedWidth"
            key.startsWith("# Height recommended for your device") -> "# Height recommended for your device = $adjustedHeight"
            key.startsWith("resolution y =") -> { sawY = true; "resolution y = $adjustedHeight" }
            key.startsWith("resolution x =") -> { sawX = true; "resolution x = $adjustedWidth" }
            else -> line
        }
    }.toMutableList()

    // The engine rewrites settings.cfg on shutdown and can drop a key entirely; re-add
    // any missing one so the write stays authoritative.
    if (!sawX || !sawY) {
        var videoIndex = lines.indexOfFirst { it.trim().equals("[Video]", ignoreCase = true) }
        if (videoIndex < 0) {
            lines.add("[Video]")
            videoIndex = lines.lastIndex
        }
        if (!sawY) lines.add(videoIndex + 1, "resolution y = $adjustedHeight")
        if (!sawX) lines.add(videoIndex + 1, "resolution x = $adjustedWidth")
    }

    if (lines != original) {
        file.writeText(lines.joinToString("\n"))
    }
}

/**
 * Seed a larger default size for the native console window into the user's `settings.cfg`.
 *
 * The engine's own default (`0.255 / 0.215 / 0.49 / 0.3125`) is a small floating box in the middle
 * of the screen: 470x169 px at the shipped `[GUI] scaling factor = 2.0`, which shows very little
 * command history. On this device the console is reached from the bottom screen (DS Settings ->
 * Developer Tools, or the on-screen keyboard's ` key) and typed into from there, so it can afford to
 * be much wider without getting in the way of anything.
 *
 * INSERT-IF-ABSENT, and that is the whole safety argument — there is no guard key and none is
 * needed. Absent means the player is on the engine default and has never touched the window
 * (`Settings::Manager::saveUser` omits anything still equal to the default layer); present means
 * either they resized it, in which case `WindowManager::onWindowChangeCoord` wrote their fractions
 * and we must not clobber them, or that a previous run already seeded it. Either way the correct
 * action is to leave it alone, so this is idempotent by construction and hands the keys over to the
 * engine's own writeback from the first run onwards.
 *
 * All four keys are treated as one unit: a partial set is left untouched rather than half-filled,
 * since that can only mean the engine wrote what differed from its defaults.
 *
 * This is the ONLY `[Windows]` value seeded at runtime. The rest live in `settings.fallback.cfg`,
 * which `ManageAssets` copies with `copyIfNotExists` and therefore reaches fresh installs only;
 * the console keys are also shipped there, but they were added long after this app's installs
 * existed, so without this the devices already in the field would never see them.
 */
fun seedConsoleWindowSize() {
    val file = File(Constants.SETTINGS_FILE)
    if (!file.exists()) return

    val keys = listOf(
        "console x" to "0.02",
        "console y" to "0.02",
        "console w" to "0.96",
        "console h" to "0.55"
    )

    val lines = file.readLines().toMutableList()
    // "console x" must not match "console maximized x" (a separate rect the engine keeps for the
    // maximized state), so compare against the text before the "=" rather than using startsWith.
    val present = lines.mapNotNull { line ->
        line.substringBefore('=', "").trim().lowercase().takeIf { it.isNotEmpty() }
    }.toSet()
    if (keys.any { it.first in present }) return

    var windowsIndex = lines.indexOfFirst { it.trim().equals("[Windows]", ignoreCase = true) }
    if (windowsIndex < 0) {
        lines.add("[Windows]")
        windowsIndex = lines.lastIndex
    }
    keys.asReversed().forEach { (key, value) ->
        lines.add(windowsIndex + 1, "$key = $value")
    }
    file.writeText(lines.joinToString("\n"))
    Log.d("UITools", "Seeded default console window size into ${Constants.SETTINGS_FILE}")
}

/**
 * Real pixel size of the TOP screen ([Display.DEFAULT_DISPLAY]), independent of whichever
 * physical display the calling Activity happens to be running on.
 *
 * Deliberately SEPARATE from [currentDeviceRealSize], which reports the caller's own
 * window and is what the touch-coordinate mapping in the Dynamic* controls wants. The
 * game always renders on the top screen, so resolution detection must be pinned to
 * display 0 — otherwise a first launch placed on the companion (bottom) display detects
 * 1240x1080 and `SDLSurface.setFixedSize` then forces the render target to that. Mirrors
 * the explicit display lookup in `EngineActivity.startCompanionScreen()`.
 */
fun Context.topScreenRealSize(): Pair<Int, Int> {
    val display = (getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
        ?.getDisplay(Display.DEFAULT_DISPLAY)

    if (display != null) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val bounds = createDisplayContext(display)
                    .createWindowContext(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY, null)
                    .getSystemService(WindowManager::class.java)
                    .maximumWindowMetrics
                    .bounds
                if (bounds.width() > 0 && bounds.height() > 0) {
                    return Pair(bounds.width(), bounds.height())
                }
            } catch (_: Throwable) {
                // Fall through to the display-metrics path below.
            }
        }

        val size = Point()
        @Suppress("DEPRECATION")
        display.getRealSize(size)
        if (size.x > 0 && size.y > 0) return Pair(size.x, size.y)
    }

    // Last resort only: this display is what the old (buggy) detection always used.
    val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    return windowManager.currentDeviceRealSize()
}

fun WindowManager.currentDeviceRealSize(): Pair<Int, Int> {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Pair(
            currentWindowMetrics.bounds.width(),
            currentWindowMetrics.bounds.height()
        )
    } else {
        val size = Point()
        @Suppress("DEPRECATION")
        defaultDisplay.getRealSize(size)
        Pair(size.x, size.y)
    }
}

@Suppress("DEPRECATION")
@Composable
fun CustomProgressIndicator(progress: Float) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier.size(100.dp)
    ) {
        CircularProgressIndicator(
            progress = progress,
            strokeWidth = 8.dp,
            modifier = Modifier.size(100.dp),
            trackColor = ProgressIndicatorDefaults.circularIndeterminateTrackColor,
        )

        // Checkmark Image with Dynamic Alpha
        if (progress >= 0.95f) {
            val adjustedAlpha = (progress - 0.95f) / 0.05f
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Checkmark",
                tint = Color.Green.copy(alpha = adjustedAlpha),
                modifier = Modifier.size(60.dp) // Adjust size as needed
            )
        }
    }
}

fun hasInternetPermission(context: Context): Boolean {
    return ContextCompat.checkSelfPermission(context, Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED
}

/**
 * Live progress for a navmesh pre-generation run.
 *
 * **The tool gives us a PERCENTAGE and nothing else.** `NAVMESHTOOL_MESSAGE` is a process env var
 * set by `setenv()` on the native side (see navmeshtool.patch), carrying `std::to_string(pct)` and
 * updated at most once per second by `Misc::ProgressReporter`. No tile counts, no cell name, no
 * estimate — so time remaining is extrapolated here.
 *
 * **THE PROCESS ALWAYS DIES AT THE END OF A RUN, AND THAT IS NOT A BUG WE CAN FIX HERE.** The hook
 * makes the engine's `main()` RETURN once the tool finishes; on this SDL build a returning native
 * main takes the process down with it, and `libSDL2.so` is one of the pinned prebuilt libraries so
 * the behaviour is not ours to change. Navigating back to the launcher on completion — which is what
 * the original Alpha3 flow did — therefore produces a launcher window that is destroyed a few
 * seconds later, which reads as a crash. Measured: the engine took 6.4s to tear down after the tool
 * finished (closing a 1.5GB SQLite DB), during which `SDLActivity.onDestroy()` blocks the MAIN
 * thread in `mSDLThread.join()`. So instead this screen ends in an explained, deliberate close.
 *
 * [onFinished] must therefore CLOSE THE APP, not navigate. See EngineActivity for the two variants:
 * completion finishes the task and lets the join protect the final DB commit; Stop kills the process
 * outright, because nothing can interrupt navmeshtool mid-run.
 */
@Suppress("DEPRECATION")
@Composable
fun ProgressWithNavmesh(onFinished: (stopped: Boolean) -> Unit) {
    val progressFlow = remember { MutableStateFlow(0f) }
    val fileSizeFlow = remember { MutableStateFlow(0L) }
    val memoryInfoFlow = remember { MutableStateFlow(MemoryInfo("", "", "")) }
    val logLinesFlow = remember { MutableStateFlow<List<String>>(emptyList()) }
    // Null until there is enough progress to extrapolate from - see ETA_MIN_PROGRESS.
    val etaFlow = remember { MutableStateFlow<String?>(null) }

    val context = LocalContext.current
    val availableSpace = getAvailableStorageSpace()
    val scrollState = rememberScrollState()
    val progress by progressFlow.collectAsState()
    val fileSize by fileSizeFlow.collectAsState()
    val memoryInfo by memoryInfoFlow.collectAsState()
    val logLines by logLinesFlow.collectAsState()
    val eta by etaFlow.collectAsState()
    // Defaults ON. It is the only thing on screen that moves in the first few seconds, and a panel
    // reading "0%" with no estimate yet looks broken; it also stops the toggle being mistaken for a
    // start button, which is exactly what happened the first time this screen was used.
    var showLog by remember { mutableStateOf(true) }
    var cpuUsage by remember { mutableIntStateOf(0) }
    val done = progress >= 1f

    Row(modifier = Modifier.fillMaxSize().padding(12.dp)) {
        Column(
            modifier = Modifier
                .weight(1f)
                .background(MwFloatStone, RoundedCornerShape(12.dp))
                .border(2.dp, MwBronze, RoundedCornerShape(12.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (done) "NAVMESH COMPLETE" else "BUILDING NAVMESH",
                color = MwBronzeLight,
                fontSize = 18.sp,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(12.dp))

            if (done) {
                Text(
                    text = "Pathfinding data has been cached for your whole load order.",
                    color = MwBone,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "The app will now close. Reopen OpenMW-DS to play.",
                    color = MwBoneDim,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            } else {
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                    color = MwBronzeLight,
                    trackColor = MwSlotBg,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "${(progress * 100).toInt()}%",
                    color = MwBone,
                    fontSize = 22.sp,
                    fontFamily = FontFamily.Serif,
                )
                Text(
                    // Honest about not knowing yet, rather than showing a wild early guess.
                    text = eta ?: "estimating time remaining...",
                    color = MwBoneDim,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    // "$availableSpace bytes" used to render as "367GiB bytes" - the helper already
                    // returns a formatted, unit-bearing string.
                    text = "Cache: ${fileSize / 1024 / 1024} MB written\n" +
                        "Free space: $availableSpace\n" +
                        "Memory: ${memoryInfo.usedMemory} / ${memoryInfo.totalMemory}\n" +
                        "CPU: $cpuUsage%",
                    color = MwBoneDim,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { onFinished(true) },
                    colors = ButtonDefaults.buttonColors(containerColor = MwSlotBg),
                    border = BorderStroke(1.dp, MwBronzeDark),
                    shape = RoundedCornerShape(10.dp),
                ) { Text("Stop and close", color = MwBone) }
                Text(
                    // Says what stopping actually costs, because the answer is "almost nothing" and
                    // that is not obvious: tiles are content-addressed and committed about once a
                    // second, so a partial cache is valid and simply covers less ground.
                    text = "Progress so far is kept. You can resume by generating again.",
                    color = MwBoneDim,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "Show log", color = MwBoneDim, fontSize = 12.sp)
                    Switch(
                        checked = showLog,
                        onCheckedChange = { showLog = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MwBronzeLight,
                            checkedTrackColor = MwBronzeDark,
                            uncheckedThumbColor = MwBoneDim,
                            uncheckedTrackColor = MwSlotBg,
                        )
                    )
                }
            }
        }

        if (showLog && !done) {
            Column(
                modifier = Modifier
                    .weight(1.6f)
                    .padding(start = 12.dp)
                    .background(MwSlotBg, RoundedCornerShape(12.dp))
                    .border(1.dp, MwBronzeDark, RoundedCornerShape(12.dp))
                    .padding(8.dp)
                    .verticalScroll(scrollState)
            ) {
                logLines.forEach { line ->
                    Text(text = line, color = MwBoneDim, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
    }

    // Completion close. The linger is so the message is actually read before the app goes away.
    LaunchedEffect(done) {
        if (done) {
            delay(NAVMESH_COMPLETE_LINGER_MS)
            onFinished(false)
        }
    }

    LaunchedEffect(Unit) {
        launch(Dispatchers.IO) {
            // Wall clock for the ETA.
            val startedAt = System.currentTimeMillis()
            while (true) {
                // toFloatOrNull, not toFloat: a malformed or absent value must not throw inside the
                // poll loop and take the whole progress screen down.
                val pct = Os.getenv("NAVMESHTOOL_MESSAGE")?.toFloatOrNull()
                if (pct != null) progressFlow.value = (pct / 100.0f).coerceIn(0f, 1f)

                val file = File("${Constants.USER_FILE_STORAGE}/navmesh.db")
                if (file.exists()) fileSizeFlow.value = file.length()
                memoryInfoFlow.value = getMemoryInfo(context)

                val logFile = File("${Constants.USER_CONFIG}/navmeshtool.log")
                if (logFile.exists()) logLinesFlow.value = logFile.readLines().takeLast(NAVMESH_LOG_TAIL)

                val usage = getCpuProcessUsage()
                withContext(Dispatchers.Main) { cpuUsage = usage }

                // TIME REMAINING, held back until there is enough signal to be worth showing: a
                // guess made from the first 1% of a Tamriel-Rebuilt-sized run is worse than none.
                // Derived from CUMULATIVE elapsed/progress rather than a windowed rate, which makes
                // it inherently smooth because it is an average, not a derivative.
                val p = progressFlow.value
                val elapsed = System.currentTimeMillis() - startedAt
                etaFlow.value = when {
                    p >= 1f -> null
                    p < ETA_MIN_PROGRESS || elapsed < ETA_MIN_ELAPSED_MS -> null
                    else -> formatNavmeshEta(((elapsed / p) * (1f - p)).toLong())
                }

                if (p >= 1f) break
                // The native channel updates at ~1Hz, so the old 50ms cadence was 20x oversampling -
                // and this screen runs DURING a CPU-saturating operation, where polling getenv, file
                // size, meminfo and CPU usage that hard works against the thing it is measuring.
                delay(NAVMESH_POLL_INTERVAL_MS)
            }
        }
    }
    LaunchedEffect(logLines.size) { scrollState.animateScrollTo(scrollState.maxValue) }
}

/** How long the "complete" message stays up before the app closes. */
private const val NAVMESH_COMPLETE_LINGER_MS = 3500L

/** Poll cadence. The native progress channel updates at ~1Hz. */
private const val NAVMESH_POLL_INTERVAL_MS = 250L

/** Lines of navmeshtool.log kept on screen. The file grows to tens of thousands of lines. */
private const val NAVMESH_LOG_TAIL = 200

/** Below this fraction an ETA extrapolated from elapsed time is noise, so none is shown. */
private const val ETA_MIN_PROGRESS = 0.02f

/** ...and likewise before this much wall time has passed. */
private const val ETA_MIN_ELAPSED_MS = 8_000L

/**
 * Rough "time remaining". Deliberately COARSE — it is an extrapolation from a percentage, and
 * navmesh tiles are not uniformly expensive, so minute-level precision would be false confidence.
 * The point is to let a player decide whether to leave the device alone for two minutes or two
 * hours before committing to a Tamriel-Rebuilt-sized run.
 */
private fun formatNavmeshEta(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return when {
        h > 0 -> "about ${h}h ${m}m remaining"
        m > 1 -> "about $m minutes remaining"
        else -> "less than a minute remaining"
    }
}


@Composable
fun BackgroundAnimation() {
    val context = LocalContext.current
    val backgroundAnimation by getBackgroundAnimationFlow(context).collectAsState(initial = "BouncingBackground")
    when (backgroundAnimation) {
        "BouncingBackground" -> BouncingBackground()
        "RotatingImageBackground" -> RotatingImageBackground()
        "CircularBackground" -> CircularBackground()
        else -> NoneBackground()
    }
}

@Suppress("DEPRECATION")
@Composable
fun NoneBackground() {
    val context = LocalContext.current
    val codeGroupOption by readCodeGroup(context).collectAsState(initial = "OpenMW")
    val image: Painter = when (codeGroupOption) {
        "OpenMW" -> rememberImagePainter(data = "file:${userUI}/backgroundbouncebw.jpg")
        "UQM" -> rememberImagePainter(data = "file:${userUI}/starmap.jpg")
        else -> painterResource(id = R.drawable.backgroundbouncebw)
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = image,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .background(color = customColor)
        )
    }
}

@Suppress("DEPRECATION")
@Composable
fun RotatingImageBackground() {
    val context = LocalContext.current
    val codeGroupOption by readCodeGroup(context).collectAsState(initial = "OpenMW")
    val image: Painter = when (codeGroupOption) {
        "OpenMW" -> rememberImagePainter(data = "file:${userUI}/backgroundbouncebw.jpg")
        "UQM" -> rememberImagePainter(data = "file:${userUI}/starmap.jpg")
        else -> painterResource(id = R.drawable.backgroundbouncebw)
    }

    // Create an infinite transition for rotation
    val infiniteTransition = rememberInfiniteTransition()
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 10000, easing = LinearEasing),
            RepeatMode.Restart
        )
    )

    // Adjust the scale factor here to set the zoom level
    val zoomFactor = 3f // Example zoom factor

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = image,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    rotationZ = rotation
                    scaleX = zoomFactor
                    scaleY = zoomFactor
                }
                .background(color = Color.LightGray)
        )
    }
}

@Suppress("DEPRECATION")
@Composable
fun BouncingBackground() {
    val context = LocalContext.current
    val codeGroupOption by readCodeGroup(context).collectAsState(initial = "OpenMW")
    val image: Painter = when (codeGroupOption) {
        "OpenMW" -> rememberImagePainter(data = "file:${userUI}/backgroundbouncebw.jpg")
        "UQM" -> rememberImagePainter(data = "file:${userUI}/starmap.jpg")
        else -> painterResource(id = R.drawable.backgroundbouncebw)
    }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp * configuration.densityDpi / 160
    val screenHeight = configuration.screenHeightDp * configuration.densityDpi / 160

    val imageWidth = 2000 // Replace with your image width
    val imageHeight = 2337 // Replace with your image height

    var offset: Offset by remember { mutableStateOf(Offset.Zero) }
    val xDirection by remember { mutableFloatStateOf(1f) }
    val yDirection by remember { mutableFloatStateOf(1f) }

    // Adjust this value to increase the distance
    val stepSize = 1f

    LaunchedEffect(Unit) {
        while (true) {
            offset = Offset(
                x = (offset.x + xDirection * stepSize) % screenWidth,
                y = (offset.y + yDirection * stepSize) % screenHeight
            )

            delay(16L) // Update every frame (approx 60fps)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = image,
            contentDescription = null,
            modifier = Modifier
                .offset { IntOffset(offset.x.toInt(), offset.y.toInt()) }
                .size(imageWidth.dp, imageHeight.dp) // Convert Int to Dp
                .scale(6f) // Scale the image up by a factor of 6
                .background(color = Color.LightGray))
    }
}

@Suppress("DEPRECATION")
@Composable
fun CircularBackground() {
    val context = LocalContext.current
    val codeGroupOption by readCodeGroup(context).collectAsState(initial = "OpenMW")
    val image: Painter = when (codeGroupOption) {
        "OpenMW" -> rememberImagePainter(data = "file:${userUI}/backgroundbouncebw.jpg")
        "UQM" -> rememberImagePainter(data = "file:${userUI}/starmap.jpg")
        else -> painterResource(id = R.drawable.backgroundbouncebw)
    }

    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp * configuration.densityDpi / 160
    val screenHeight = configuration.screenHeightDp * configuration.densityDpi / 160

    val imageWidth = 2000 // Replace with your image width
    val imageHeight = 2337 // Replace with your image height

    var offset: Offset by remember { mutableStateOf(Offset.Zero) }
    var angle by remember { mutableFloatStateOf(0f) }
    val radius = 1000f // Adjust the radius of the circular motion
    val speed = 0.0020f

    LaunchedEffect(Unit) {
        while (true) {
            offset = Offset(
                x = screenWidth / 2f + radius * cos(angle),
                y = screenHeight / 2f + radius * sin(angle)
            )
            angle += speed // Adjust the speed by changing this value

            delay(16L) // Update every frame (approx 60fps)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = image,
            contentDescription = null,
            modifier = Modifier
                .offset { IntOffset(offset.x.toInt(), offset.y.toInt()) }
                .size(imageWidth.dp, imageHeight.dp) // Convert Int to Dp
                .scale(6f) // Scale the image up by a factor of 6
                .background(color = Color.LightGray)
        )
    }
}
