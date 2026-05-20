@file:OptIn(DelicateCoroutinesApi::class)

package org.openmw.modDownloader

import android.content.Context
import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.openmw.Constants
import org.openmw.R
import org.openmw.ui.view.getMemoryInfo
import org.openmw.utils.stringRes
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

enum class TaskResult { SUCCESS, FAILED, SKIPPED_SMALL, SKIPPED_EXISTS }

data class ConversionResult(
    val totalFiles: Int,
    val processed: Int,
    val skippedSmall: Int,
    val skippedASTC: Int,
    val failed: Int,
    val elapsedTime: Double
)

data class ConversionConfig(
    val sizeThreshold: Int,
    val blockSize: String,
    val astcQuality: Int,
    val outputFormat: OutputFormat,
    val inPlace: Boolean,
    val backupOriginal: Boolean
)

enum class OutputFormat(val displayName: String, val kramFormat: String) {
    ASTC_KTX("ASTC KTX", "ktx"),
    ASTC_DDS("ASTC DDS", "dds")
}

enum class ConversionState {
    IDLE, RUNNING, COMPLETED, ERROR
}

data class LogMessage(
    val message: String,
    val type: LogType,
    val timestamp: String = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
)

enum class LogType {
    INFO, SUCCESS, WARNING, ERROR
}

data class TextureInfo(
    val width: Int,
    val height: Int,
    val format: String,
    val mipCount: Int
)

fun parseKramInfo(output: String): TextureInfo? {
    var width = 0
    var height = 0
    var format = "unknown"
    var mips = 0

    output.lineSequence().forEach { line ->
        when {
            "dims:" in line -> {
                val dims = line.substringAfter("dims:").trim()
                val parts = dims.split("x")
                width = parts[0].toInt()
                height = parts[1].toInt()
            }
            "fmtk:" in line -> {
                format = line.substringAfter("fmtk:").trim()
            }
            "mips:" in line -> {
                mips = line.substringAfter("mips:").trim().toInt()
            }
        }
    }

    if (width == 0 || height == 0) return null

    return TextureInfo(width, height, format, mips)
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KramConversion(context: Context) {
    var conversionState by remember { mutableStateOf(ConversionState.IDLE) }
    var logMessages by remember { mutableStateOf<List<LogMessage>>(emptyList()) }
    var currentProgress by remember { mutableStateOf("") }
    val coroutineScope = rememberCoroutineScope()
    val memoryInfo by MemoryInfoManager.memoryInfoFlow.collectAsState()

    var config by remember {
        mutableStateOf(
            ConversionConfig(
                sizeThreshold = 16,
                blockSize = "astc12x12",
                astcQuality = 50,
                outputFormat = OutputFormat.ASTC_KTX,
                inPlace = true,
                backupOriginal = false
            )
        )
    }

    LaunchedEffect(Unit) {
        MemoryInfoManager.startMemoryInfoUpdates(context)
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .padding(6.dp)
                .fillMaxSize()
        ) {
            Text(
                text = memoryInfo,
                style = MaterialTheme.typography.bodySmall
            )
            ConfigurationSection(
                config = config,
                onConfigChange = { config = it }
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            conversionState = ConversionState.RUNNING
                            logMessages = emptyList()

                            val processor = TextureProcessor()
                            processor.processTextures(
                                context = context,
                                config = config,
                                onProgress = { progress ->
                                    currentProgress = progress
                                },
                                onLog = { message ->
                                    logMessages = logMessages + message
                                }
                            )

                            conversionState = ConversionState.COMPLETED
                        }
                    },
                    enabled = conversionState != ConversionState.RUNNING,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start Conversion")
                }

                Button(
                    onClick = {
                        conversionState = ConversionState.IDLE
                        logMessages = emptyList()
                        currentProgress = ""
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    ),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Clear")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Status and Logs
            ConversionStatusSection(
                modifier = Modifier.weight(1f),
                state = conversionState,
                logs = logMessages,
                currentProgress = currentProgress,
                onAddLog = { message ->
                    logMessages = logMessages + message
                }
            )
        }
    }
}

@Composable
fun ConfigurationSection(
    config: ConversionConfig,
    onConfigChange: (ConversionConfig) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Configuration", style = MaterialTheme.typography.titleLarge)
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))

                    // Size Threshold
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Size Threshold:", modifier = Modifier.weight(1f))
                        TextField(
                            value = config.sizeThreshold.toString(),
                            onValueChange = {
                                it.toIntOrNull()?.let { value ->
                                    onConfigChange(config.copy(sizeThreshold = value))
                                }
                            },
                            modifier = Modifier.width(80.dp)
                        )
                        Text("px", modifier = Modifier.padding(start = 8.dp))
                    }
                    AstcBlockSizeDropdown(
                        currentValue = config.blockSize,
                        onValueChange = { newSize ->
                            onConfigChange(config.copy(blockSize = newSize))
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // ASTC Quality
                    Text("ASTC Quality: ${config.astcQuality}")
                    Slider(
                        value = config.astcQuality.toFloat(),
                        onValueChange = {
                            onConfigChange(config.copy(astcQuality = it.toInt()))
                        },
                        valueRange = 0f..100f,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Output Format
                    Text("Output Format:", style = MaterialTheme.typography.titleSmall)
                    OutputFormat.entries.forEach { format ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = config.outputFormat == format,
                                onClick = { onConfigChange(config.copy(outputFormat = format)) }
                            )
                            Text(
                                text = format.displayName,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    // Toggle Options
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Switch(
                            checked = config.inPlace,
                            onCheckedChange = { onConfigChange(config.copy(inPlace = it)) }
                        )
                        Text("In-place conversion", modifier = Modifier.padding(start = 8.dp))
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Switch(
                            checked = config.backupOriginal,
                            onCheckedChange = { onConfigChange(config.copy(backupOriginal = it)) }
                        )
                        Text("Backup original files", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
    }
}

object MemoryInfoManager {
    private val _memoryInfoFlow = MutableStateFlow("")
    val memoryInfoFlow: StateFlow<String> = _memoryInfoFlow.asStateFlow()

    @DelicateCoroutinesApi
    fun startMemoryInfoUpdates(context: Context) {
        CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                val memoryInfo = getMemoryInfo(context)
                _memoryInfoFlow.value = """
                    ${stringRes(R.string.total_used_memory)}: ${memoryInfo.usedMemory} / ${memoryInfo.totalMemory}
                """.trimIndent()
                delay(1000)
            }
        }
    }
}

@Composable
fun ConversionStatusSection(
    modifier: Modifier = Modifier,
    state: ConversionState,
    logs: List<LogMessage>,
    currentProgress: String,
    onAddLog: (LogMessage) -> Unit
) {
    val listState = rememberLazyListState()
    val limitedLogs = remember(logs) {
        if (logs.size > 50) logs.takeLast(50) else logs
    }

    LaunchedEffect(limitedLogs.size) {
        if (limitedLogs.isNotEmpty()) {
            listState.animateScrollToItem(limitedLogs.lastIndex)
        }
    }

    Card(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(4.dp).fillMaxHeight()
        ) {
            Text(
                text = "Conversion Log",
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val (color, text) = when (state) {
                    ConversionState.IDLE -> Pair(MaterialTheme.colorScheme.outline, "Ready")
                    ConversionState.RUNNING -> Pair(MaterialTheme.colorScheme.primary, "Running...")
                    ConversionState.COMPLETED -> Pair(MaterialTheme.colorScheme.primary, "Completed")
                    ConversionState.ERROR -> Pair(MaterialTheme.colorScheme.error, "Error")
                }

                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(color, androidx.compose.foundation.shape.CircleShape)
                )
                Text(
                    text = text,
                    modifier = Modifier.padding(start = 8.dp),
                    color = color
                )
            }

            // Current progress
            if (currentProgress.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = currentProgress,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Log display
            Surface(
                tonalElevation = 8.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(8.dp)
                ) {
                    if (limitedLogs.isEmpty()) {
                        item {
                            Text(
                                "No logs yet. Press 'Start Conversion' to begin.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(limitedLogs) { log ->
                            Text(
                                text = log.message,
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = when (log.type) {
                                    LogType.INFO -> MaterialTheme.colorScheme.onSurface
                                    LogType.SUCCESS -> MaterialTheme.colorScheme.primary
                                    LogType.WARNING -> MaterialTheme.colorScheme.onSurfaceVariant
                                    LogType.ERROR -> MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

class TextureProcessor {
    // Shared concurrency control
    private val maxConcurrent = Runtime.getRuntime().availableProcessors()
    private val semaphore = Semaphore(maxConcurrent)

    suspend fun processTextures(
        context: Context,
        config: ConversionConfig,
        onProgress: (String) -> Unit,
        onLog: (LogMessage) -> Unit
    ): ConversionResult = coroutineScope { // Structured concurrency
        val startTime = System.currentTimeMillis()
        val workingDir = context.applicationInfo.nativeLibraryDir
        val kramPath = File(workingDir, "libkram.so").absolutePath

        onLog(LogMessage("=== Parallel Texture Converter ===", LogType.INFO))
        onLog(LogMessage("  Concurrency Limit: $maxConcurrent threads", LogType.INFO))

        val modsPath = "${Constants.USER_FILE_STORAGE}/OpenMW/Mods"
        onLog(LogMessage("Searching for textures in: $modsPath", LogType.INFO))

        val textureFiles = withContext(Dispatchers.IO) { findTextureFiles(modsPath) }
        onLog(LogMessage("Found ${textureFiles.size} textures to evaluate", LogType.INFO))
        onLog(LogMessage("=== Texture to ASTC Converter ===", LogType.INFO))
        onLog(LogMessage("  Configuration:", LogType.INFO))
        onLog(LogMessage("  Size threshold: > ${config.sizeThreshold}px", LogType.INFO))
        onLog(LogMessage("  Quality: ${config.astcQuality}", LogType.INFO))
        onLog(LogMessage("  Output format: ${config.outputFormat.displayName}", LogType.INFO))
        onLog(LogMessage("  In-place: ${config.inPlace}", LogType.INFO))


        val total = textureFiles.size
        val counter = java.util.concurrent.atomic.AtomicInteger(0)

        // Process files in parallel
        val tasks = textureFiles.map { file ->
            async(Dispatchers.IO) {
                semaphore.withPermit {
                    val result = processSingleFile(context, file, kramPath, workingDir, config, onLog)

                    // Update shared progress UI
                    val current = counter.incrementAndGet()
                    withContext(Dispatchers.Main) {
                        onProgress("Progress: $current/$total - ${file.name}")
                    }
                    result
                }
            }
        }

        val results = tasks.awaitAll()

        // Tally results
        val processed = results.count { it == TaskResult.SUCCESS }
        val skipped = results.count { it == TaskResult.SKIPPED_EXISTS }
        val small = results.count { it == TaskResult.SKIPPED_SMALL }
        val failed = results.count { it == TaskResult.FAILED }

        val elapsed = (System.currentTimeMillis() - startTime) / 1000.0
        onLog(LogMessage("=== Batch Finished ===", LogType.INFO))
        onLog(LogMessage("Successfully processed: $processed", LogType.SUCCESS))
        onLog(LogMessage("Skipped (Duplicates/ASTC): $skipped", LogType.INFO))
        onLog(LogMessage("Execution time: ${"%.2f".format(elapsed)}s", LogType.INFO))

        ConversionResult(total, processed, small, skipped, failed, elapsed)
    }

    private fun processSingleFile(
        context: Context,
        file: File,
        kramPath: String,
        workingDir: String,
        config: ConversionConfig,
        onLog: (LogMessage) -> Unit
    ): TaskResult {
        val outputFile = File(file.parent, "${file.nameWithoutExtension}.ktx")
        if (outputFile.exists()) {
            onLog(LogMessage("Skipping ${file.name}: output exists", LogType.INFO))
            Log.i("Conversion", "Skipping ${file.name}: output exists")
            return TaskResult.SKIPPED_EXISTS
        }

        val kramOutput = runKramInfo(file, kramPath, workingDir, context, onLog)
        val info = parseKramInfo(kramOutput) ?: run {
            onLog(LogMessage("Skipping ${file.name}: could not read texture info", LogType.INFO))
            return TaskResult.FAILED
        }

        if (info.width < config.sizeThreshold || info.height < config.sizeThreshold) {
            onLog(LogMessage("Skipping ${file.name}: too small (${info.width}x${info.height})", LogType.INFO))
            Log.i("Conversion", "Skipping ${file.name}: too small (${info.width}x${info.height})")
            return TaskResult.SKIPPED_SMALL
        }

        return if (convertToASTC(context, file, info, kramPath, workingDir, config, onLog)) {
            TaskResult.SUCCESS
        } else {
            TaskResult.FAILED
        }
    }

    private fun findTextureFiles(baseDir: String): List<File> {
        val baseDirFile = File(baseDir)
        if (!baseDirFile.exists() || !baseDirFile.isDirectory) {
            return emptyList()
        }

        return baseDirFile.walk()
            .filter { it.isFile }
            .filter { it.extension.equals("dds", ignoreCase = true) }
            .toList()
    }

    fun runKramInfo(file: File, kramPath: String, workingDir: String, context: Context, onLog: (LogMessage) -> Unit): String {
        val cmd = "$kramPath info -i \"${file.absolutePath}\""

        val pb = ProcessBuilder("/system/bin/sh", "-c", cmd)
            .directory(File(workingDir))
            .redirectErrorStream(true)

        pb.environment().apply {
            put("LD_LIBRARY_PATH", "$workingDir:/system/lib64:/system/lib")
            put("PATH", "$workingDir:/system/bin:/system/xbin")
            put("HOME", context.filesDir.absolutePath)
            put("TMPDIR", context.cacheDir.absolutePath)
            put("TERM", "xterm-256color")
        }

        val process = pb.start()
        val output = StringBuilder()

        process.inputStream.bufferedReader().useLines { lines ->
            lines.forEach { line ->
                output.appendLine(line)
                onLog(LogMessage(line, LogType.INFO))
                //Log.i("Conversion", line)
            }
        }

        process.waitFor()

        return output.toString()
    }

    private fun convertToASTC(
        context: Context,
        file: File,
        info: TextureInfo,
        kramPath: String,
        workingDir: String,
        config: ConversionConfig,
        onLog: (LogMessage) -> Unit
    ): Boolean {
        return try {
            val tempKtx = File(context.cacheDir, "temp_${System.currentTimeMillis()}.ktx")
            val finalOutputFile = File(file.parent, "${file.nameWithoutExtension}.ktx")
            val intermediateKtx = File(file.parent, "${file.name}.ktx")

            onLog(LogMessage("Starting conversion for: ${file.name}", LogType.INFO))
            onLog(LogMessage("  Texture info: $info", LogType.INFO))
            onLog(LogMessage("  Final output: ${finalOutputFile.absolutePath}", LogType.INFO))

            //Log.i("KRAM", "Converting ${file.name} to ASTC")

            if (config.backupOriginal) {
                val backupFile = File(file.parent, "${file.nameWithoutExtension}.backup.${file.extension}")
                file.copyTo(backupFile, overwrite = true)
                onLog(LogMessage("  Backed up to: ${backupFile.name}", LogType.INFO))
            }

            val decodeCmd = buildString {
                append(kramPath)
                append(" decode")
                append(" -i ")
                append("\"${file.absolutePath}\"")
                append(" -o ")
                append("\"${intermediateKtx.absolutePath}\"")
            }

            val encodeCmd = buildString {
                append(kramPath)
                append(" encode")
                append(" -f ")
                append(config.blockSize)
                append(" -encoder")
                append(" astcenc")
                append(" -quality ")
                append(config.astcQuality)
                append(" -i ")
                append("\"${intermediateKtx.absolutePath}\"")
                append(" -o ")
                append("\"${finalOutputFile.absolutePath}\"")
                append(" -v")
            }

            val pb = ProcessBuilder("/system/bin/sh", "-c", "$decodeCmd && $encodeCmd")
                .directory(File(workingDir))
                .redirectErrorStream(true)

            pb.environment().apply {
                put("LD_LIBRARY_PATH", "$workingDir:/system/lib64:/system/lib")
                put("PATH", "$workingDir:/system/bin:/system/xbin")
                put("HOME", context.filesDir.absolutePath)
                put("TMPDIR", context.cacheDir.absolutePath)
                put("TERM", "xterm-256color")
            }

            val process = pb.start()
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    onLog(LogMessage(line, LogType.INFO))
                    //Log.i("Conversion", line)
                }
            }
            val exitCode = process.waitFor()

            if (exitCode == 0 && finalOutputFile.exists() && finalOutputFile.length() > 0) {
                if (config.inPlace) {
                    file.delete()
                    intermediateKtx.delete()
                }
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
}

@Composable
fun AstcBlockSizeDropdown(
    currentValue: String,
    onValueChange: (String) -> Unit
) {
    val options = listOf(
        "astc4x4",
        "astc5x5",
        "astc6x6",
        "astc8x8",
        "astc10x10",
        "astc12x12"
    )

    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = currentValue,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text("ASTC Block Size") },
            readOnly = true,
            trailingIcon = {
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.clickable { expanded = true }
                )
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onValueChange(option)
                    }
                )
            }
        }
    }
}
