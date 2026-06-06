package org.openmw.ui.view

import android.Manifest
import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Point
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.Process
import android.os.StatFs
import android.os.VibrationEffect
import android.os.Vibrator
import android.system.Os
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
        _logs.value = _logs.value + logEntry
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

fun vibrate(context: Context) {
    val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    if (vibrator.hasVibrator()) {
        vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
    }
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

fun updateResolutionInConfig(width: Int, height: Int) {
    // Ensure the larger value is assigned to width
    val (adjustedWidth, adjustedHeight) = if (width > height) width to height else height to width

    val file = File(Constants.SETTINGS_FILE)
    val lines = file.readLines().map { line ->
        when {
            // Update lines based on the adjusted width and height
            line.startsWith("# Width recommended for your device") -> "# Width recommended for your device = $adjustedWidth"
            line.startsWith("# Height recommended for your device") -> "# Height recommended for your device = $adjustedHeight"
            line.startsWith("resolution y = 0") -> "resolution y = $adjustedHeight"
            line.startsWith("resolution x = 0") -> "resolution x = $adjustedWidth"
            else -> line
        }
    }
    file.writeText(lines.joinToString("\n"))
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

@Suppress("DEPRECATION")
@Composable
fun ProgressWithNavmesh(onComplete: () -> Unit) {
    val progressFlow = remember { MutableStateFlow(0f) }
    val navmeshStatus = remember { MutableStateFlow("0.0") }
    val fileSizeFlow = remember { MutableStateFlow(0L) }
    val memoryInfoFlow = remember { MutableStateFlow(MemoryInfo("", "", "")) }
    val context = LocalContext.current
    val availableSpace = getAvailableStorageSpace()
    val logLinesFlow = remember { MutableStateFlow<List<String>>(emptyList()) }
    val scrollState = rememberScrollState()
    val progress by progressFlow.collectAsState()
    val fileSize by fileSizeFlow.collectAsState()
    val memoryInfo by memoryInfoFlow.collectAsState()
    val logLines by logLinesFlow.collectAsState()
    var detailedLogs by remember { mutableStateOf(false) }
    var cpuUsage by remember { mutableIntStateOf(0) }

    Row(
        modifier = Modifier
            .wrapContentSize(),
    ) {
        Box(
            modifier = Modifier
                .wrapContentSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .wrapContentSize()
                    .padding(16.dp)
                    .background(Color.Black, shape = RoundedCornerShape(8.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (progress < 1f) {
                    Text("${stringRes(R.string.cpu_usage)}: $cpuUsage%", color = Color.White)
                    CustomProgressIndicator(progress = progress)
                    Spacer(modifier = Modifier.height(32.dp))
                    Text(
                        text = "Building Navmesh... ${(progress * 100).toInt()}%",
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "${stringResource(R.string.file_size)}: ${fileSize / 1024} KB \n${stringResource(R.string.free_space)}: $availableSpace bytes \n${stringResource(R.string.memory)}: ${memoryInfo.usedMemory} / ${memoryInfo.totalMemory}",
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(text = stringResource(R.string.navmesh_details), color = Color.White)
                        Switch(
                            checked = detailedLogs,
                            onCheckedChange = { detailedLogs = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.Green)
                        )
                    }
                } else {
                    Text(text = stringResource(R.string.navmesh_complete), color = Color.White)
                    onComplete()
                }
            }
        }
        if (detailedLogs) {
            Box(
                modifier = Modifier
                    .wrapContentSize()
                    .verticalScroll(scrollState)
            ) {
                Column(
                    modifier = Modifier
                        .padding(8.dp)
                        .background(Color.Black, shape = RoundedCornerShape(8.dp))
                ) {
                    logLines.forEach { line ->
                        Text(text = line, color = Color.White)
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) {
        //Log.d("ProgressWithNavmesh", "LaunchedEffect triggered")
        launch(Dispatchers.IO) {

            while (navmeshStatus.value != "Done") {
                val statusMessage = Os.getenv("NAVMESHTOOL_MESSAGE")
                if (statusMessage != null) {
                    navmeshStatus.value = statusMessage
                    progressFlow.value = statusMessage.toFloat() / 100.0f
                }

                // Update file size
                val file = File("${Constants.USER_FILE_STORAGE}/navmesh.db")
                if (file.exists()) {
                    fileSizeFlow.value = file.length()
                }

                // Update memory info
                memoryInfoFlow.value = getMemoryInfo(context)

                // Read navmeshtool.log
                val logFile = File("${Constants.USER_CONFIG}/navmeshtool.log")
                if (logFile.exists()) {
                    val lines = logFile.readLines()
                    //Log.d("LogLines", "Read ${lines.size} lines from navmeshtool.log")
                    logLinesFlow.value = lines
                }

                // Update CPU usage
                val usage = getCpuProcessUsage()
                withContext(Dispatchers.Main) {
                    cpuUsage = usage
                }

                delay(50)
            }
            progressFlow.value = 1f

        }
    }
    LaunchedEffect(logLines.size) { scrollState.animateScrollTo(scrollState.maxValue) }
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
