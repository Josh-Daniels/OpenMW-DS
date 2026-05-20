package org.openmw.ui.view

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class ThermalMonitorActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {

                Surface(modifier = Modifier.fillMaxSize()) {
                    ThermalMonitorScreen()
                }

        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThermalMonitorScreen() {
    var gpuTemp by remember { mutableStateOf("--") }
    var gpuUtilization by remember { mutableStateOf("--") }
    var gpuBusyTime by remember { mutableStateOf("--") }
    var gpuTotalTime by remember { mutableStateOf("--") }
    var isMonitoring by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Reset GPU counters on first run
    LaunchedEffect(Unit) {
        readGpuStats() // First read resets counters
    }

    LaunchedEffect(isMonitoring) {
        while (isMonitoring) {
            try {
                // Read GPU temperature
                val tempResult = readProcessOutput("cat", "/sys/class/thermal/thermal_zone32/temp")
                val tempValue = tempResult.trim()
                gpuTemp = if (tempValue.isNotEmpty()) {
                    val celsius = tempValue.toDoubleOrNull()?.div(1000) ?: 0.0
                    String.format("%.1f°C", celsius)
                } else {
                    "--"
                }

                // Read GPU utilization (this gives values for the interval since last read)
                val gpuStats = readProcessOutput("cat", "/sys/class/kgsl/kgsl-3d0/gpubusy")
                val statsParts = gpuStats.trim().split("\\s+".toRegex())

                if (statsParts.size >= 2) {
                    gpuBusyTime = statsParts[0]
                    gpuTotalTime = statsParts[1]

                    val busy = statsParts[0].toDoubleOrNull() ?: 0.0
                    val total = statsParts[1].toDoubleOrNull() ?: 1.0

                    val utilization = if (total > 0) (busy / total) * 100 else 0.0
                    gpuUtilization = String.format("%.1f%%", utilization)

                    // Reset error state
                    errorMessage = null
                } else {
                    gpuUtilization = "--"
                    gpuBusyTime = "--"
                    gpuTotalTime = "--"
                }

            } catch (e: Exception) {
                errorMessage = "Error: ${e.message}"
                gpuTemp = "--"
                gpuUtilization = "--"
            }

            // Update every second
            delay(1000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("GPU Monitor") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { isMonitoring = !isMonitoring },
                containerColor = if (isMonitoring) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            ) {
                Icon(
                    imageVector = if (isMonitoring) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (isMonitoring) "Pause" else "Start"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Status indicator
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            color = if (isMonitoring) Color.Green else Color.Gray,
                            shape = CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isMonitoring) "Monitoring Active" else "Monitoring Paused",
                    fontSize = 14.sp
                )
            }

            // GPU Temperature Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "GPU Temperature",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = gpuTemp,
                        style = MaterialTheme.typography.displayMedium,
                        color = getTemperatureColor(gpuTemp)
                    )
                }
            }

            // GPU Utilization Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "GPU Utilization",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = gpuUtilization,
                        style = MaterialTheme.typography.displayMedium,
                        color = getUtilizationColor(gpuUtilization)
                    )

                    // Show raw values
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Busy/Total Time (ms)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$gpuBusyTime / $gpuTotalTime",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // Info section
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Information",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "• Temperature is read from /sys/class/thermal/thermal_zone32/temp\n" +
                                "• GPU stats from /sys/class/kgsl/kgsl-3d0/gpubusy\n" +
                                "• Values update every second",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            // Error display
            errorMessage?.let { error ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

// Helper function to read process output
fun readProcessOutput(vararg command: String): String {
    return try {
        val process = ProcessBuilder(*command).start()
        val reader = BufferedReader(InputStreamReader(process.inputStream))
        val output = StringBuilder()
        var line: String?

        while (reader.readLine().also { line = it } != null) {
            output.append(line).append("\n")
        }

        process.waitFor()
        output.toString()
    } catch (e: Exception) {
        ""
    }
}

// Function to read GPU stats (used for resetting counters)
private fun readGpuStats(): String {
    return readProcessOutput("cat", "/sys/class/kgsl/kgsl-3d0/gpubusy")
}

// Color coding based on temperature
@Composable
private fun getTemperatureColor(tempStr: String): Color {
    val temp = tempStr.replace("°C", "").toDoubleOrNull() ?: return MaterialTheme.colorScheme.onSurface

    return when {
        temp < 50 -> Color.Green
        temp < 70 -> Color.Yellow
        temp < 85 -> Color(0xFFFFA500) // Orange
        else -> Color.Red
    }
}

// Color coding based on utilization
@Composable
private fun getUtilizationColor(utilStr: String): Color {
    val util = utilStr.replace("%", "").toDoubleOrNull() ?: return MaterialTheme.colorScheme.onSurface

    return when {
        util < 30 -> Color.Green
        util < 60 -> Color.Yellow
        util < 85 -> Color(0xFFFFA500) // Orange
        else -> Color.Red
    }
}



// Find GPU thermal zone
fun findGpuThermalZone(): String? {
    val zones = File("/sys/class/thermal").listFiles { file ->
        file.name.startsWith("thermal_zone")
    }
    // Look for zone with "gpu" in type
    zones?.forEach { zone ->
        val typeFile = File(zone, "type")
        if (typeFile.exists()) {
            val type = typeFile.readText().trim()
            if (type.contains("gpu", ignoreCase = true)) {
                return zone.name.replace("thermal_zone", "")
            }
        }
    }
    return null
}

@Composable
fun MinimalMonitorScreen() {
    var gpuTemp by remember { mutableStateOf("--") }
    var gpuUtilization by remember { mutableStateOf("--") }
    var isMonitoring by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Reset GPU counters on first run
    LaunchedEffect(Unit) {
        readGpuStats()
    }

    LaunchedEffect(isMonitoring) {
        while (isMonitoring) {
            try {
                // Read GPU temperature
                val tempResult = readProcessOutput("cat", "/sys/class/thermal/thermal_zone32/temp")
                val tempValue = tempResult.trim()
                gpuTemp = if (tempValue.isNotEmpty()) {
                    val celsius = tempValue.toDoubleOrNull()?.div(1000) ?: 0.0
                    String.format("%.1f°C", celsius)
                } else {
                    "--"
                }

                // Read GPU utilization
                val gpuStats = readProcessOutput("cat", "/sys/class/kgsl/kgsl-3d0/gpubusy")
                val statsParts = gpuStats.trim().split("\\s+".toRegex())

                if (statsParts.size >= 2) {
                    val busy = statsParts[0].toDoubleOrNull() ?: 0.0
                    val total = statsParts[1].toDoubleOrNull() ?: 1.0
                    val utilization = if (total > 0) (busy / total) * 100 else 0.0
                    gpuUtilization = String.format("%.1f%%", utilization)
                    errorMessage = null
                }
            } catch (e: Exception) {
                errorMessage = "Error reading stats"
            }
            delay(1000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Status indicator (tiny)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isMonitoring) "● Monitoring" else "○ Paused",
                fontSize = 12.sp,
                color = if (isMonitoring) Color.Green else Color.Gray
            )

            IconButton(
                onClick = { isMonitoring = !isMonitoring },
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = if (isMonitoring) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isMonitoring) "Pause" else "Start",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Compact display - just 2 lines
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "GPU: $gpuUtilization",
                fontSize = 14.sp,
                color = getUtilizationColor(gpuUtilization)
            )

            Text(
                text = "Temp: $gpuTemp",
                fontSize = 14.sp,
                color = getTemperatureColor(gpuTemp)
            )
        }

        // Tiny error message if needed
        errorMessage?.let { error ->
            Text(
                text = error,
                fontSize = 10.sp,
                color = Color.Red,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}

// Or even more minimal - single line:
@Composable
fun SingleLineMonitor() {
    var gpuTemp by remember { mutableStateOf("--") }
    var gpuUtilization by remember { mutableStateOf("--") }
    var isMonitoring by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        readGpuStats()
    }

    LaunchedEffect(isMonitoring) {
        while (isMonitoring) {
            try {
                val tempResult = readProcessOutput("cat", "/sys/class/thermal/thermal_zone32/temp")
                val tempValue = tempResult.trim()
                gpuTemp = if (tempValue.isNotEmpty()) {
                    val celsius = tempValue.toDoubleOrNull()?.div(1000) ?: 0.0
                    String.format("%.0f°C", celsius)
                } else { "--" }

                val gpuStats = readProcessOutput("cat", "/sys/class/kgsl/kgsl-3d0/gpubusy")
                val statsParts = gpuStats.trim().split("\\s+".toRegex())
                if (statsParts.size >= 2) {
                    val busy = statsParts[0].toDoubleOrNull() ?: 0.0
                    val total = statsParts[1].toDoubleOrNull() ?: 1.0
                    val utilization = if (total > 0) (busy / total) * 100 else 0.0
                    gpuUtilization = String.format("%.0f%%", utilization)
                }
            } catch (e: Exception) {
                // silent fail
            }
            delay(1000)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status dot
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    color = if (isMonitoring) Color.Green else Color.Gray,
                    shape = CircleShape
                )
        )

        // Stats display
        Text(
            text = "GPU: $gpuUtilization | Temp: $gpuTemp",
            fontSize = 12.sp
        )

        // Tiny toggle button
        IconButton(
            onClick = { isMonitoring = !isMonitoring },
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                imageVector = if (isMonitoring) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isMonitoring) "Stop" else "Start",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
        }
    }
}

// If you want to embed it in another screen as a small widget:
@Composable
fun SmallMonitorWidget() {
    var gpuTemp by remember { mutableStateOf("--") }
    var gpuUtilization by remember { mutableStateOf("--") }

    LaunchedEffect(Unit) {
        // Auto-start monitoring for widget
        readGpuStats()
        while (true) {
            try {
                val tempResult = readProcessOutput("cat", "/sys/class/thermal/thermal_zone32/temp")
                val tempValue = tempResult.trim()
                gpuTemp = if (tempValue.isNotEmpty()) {
                    val celsius = tempValue.toDoubleOrNull()?.div(1000) ?: 0.0
                    String.format("%.0f°C", celsius)
                } else { "--" }

                val gpuStats = readProcessOutput("cat", "/sys/class/kgsl/kgsl-3d0/gpubusy")
                val statsParts = gpuStats.trim().split("\\s+".toRegex())
                if (statsParts.size >= 2) {
                    val busy = statsParts[0].toDoubleOrNull() ?: 0.0
                    val total = statsParts[1].toDoubleOrNull() ?: 1.0
                    val utilization = if (total > 0) (busy / total) * 100 else 0.0
                    gpuUtilization = String.format("%.0f%%", utilization)
                }
            } catch (e: Exception) {
                // silent fail
            }
            delay(2000) // Update every 2 seconds for widget mode
        }
    }

    Card(
        modifier = Modifier.padding(4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = "GPU",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = "GPU usage: $gpuUtilization / Temp: $gpuTemp",
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun CompactGpuMonitor() {
    var gpuTemp by remember { mutableStateOf("--") }
    var gpuUtilization by remember { mutableStateOf("--") }
    var gpuClock by remember { mutableStateOf("--") }
    var gpuModel by remember { mutableStateOf("--") }
    var isMonitoring by remember { mutableStateOf(false) }

    LaunchedEffect(isMonitoring) {
        while (isMonitoring) {
            try {
                // Temperature - try direct GPU temp first
                val tempResult = readProcessOutput("cat", "/sys/class/kgsl/kgsl-3d0/temp")
                val tempValue = tempResult.trim().toDoubleOrNull()?.div(1000) ?: 0.0

                // Format temperature properly
                gpuTemp = if (tempValue > 0) {
                    "${tempValue.toInt()}°C"  // "32°C" not "32C"
                } else {
                    // Fallback to thermal_zone32
                    val thermal = readProcessOutput("cat", "/sys/class/thermal/thermal_zone32/temp")
                    val celsius = thermal.trim().toDoubleOrNull()?.div(1000) ?: 0.0
                    if (celsius > 0) "${celsius.toInt()}°C" else "--"
                }

                // GPU utilization - clean the percentage
                val busyPercent = readProcessOutput("cat", "/sys/class/kgsl/kgsl-3d0/gpu_busy_percentage")
                gpuUtilization = if (busyPercent.isNotEmpty()) {
                    val cleanPercent = busyPercent.trim()
                    // Remove any existing % sign and add one
                    cleanPercent.replace("%", "") + "%"
                } else {
                    "--%"
                }

                // GPU clock speed - clean the value
                val clock = readProcessOutput("cat", "/sys/class/kgsl/kgsl-3d0/clock_mhz")
                gpuClock = if (clock.isNotEmpty()) {
                    "${clock.trim()}MHz"
                } else {
                    "--MHz"
                }

                val model = readProcessOutput("cat", "/sys/class/kgsl/kgsl-3d0/gpu_model")
                    .trim()
                    .takeIf { it.isNotEmpty() } ?: "Unknown"
                gpuModel = model

            } catch (e: Exception) {
                // silent fail
            }
            delay(1000)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Status and stats
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Status dot
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .background(
                        color = if (isMonitoring) Color.Green else Color.Gray,
                        shape = CircleShape
                    )
            )

            // GPU icon
            Icon(
                imageVector = Icons.Default.Memory,
                contentDescription = "GPU",
                modifier = Modifier.size(12.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            // Stats with proper spacing
            Text(
                text = "GPU Model: $gpuModel\nGPU usage: $gpuUtilization  temp: $gpuTemp  clock: $gpuClock",
                fontSize = 20.sp,
                color = Color.White
                //maxLines = 1
            )
        }

        // Tiny toggle
        IconButton(
            onClick = { isMonitoring = !isMonitoring },
            modifier = Modifier.size(20.dp)
        ) {
            Icon(
                imageVector = if (isMonitoring) Icons.Default.Stop else Icons.Default.PlayArrow,
                contentDescription = if (isMonitoring) "Stop" else "Start",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(10.dp)
            )
        }
    }
}