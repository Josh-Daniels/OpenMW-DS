package org.openmw.ui.view

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.launch
import org.openmw.ui.controls.UIStateManager

@Composable
fun PerformanceHud(
    boxWidth: Float = 240f, 
    boxHeight: Float = 160f,
    fontSize: Float = 10f
) {
    val cpuHistory by UIStateManager.cpuHistory.collectAsState()
    val gpuHistory by UIStateManager.gpuHistory.collectAsState()
    val memoryHistory by UIStateManager.memoryHistory.collectAsState()
    val cpuTempHistory by UIStateManager.cpuTempHistory.collectAsState()
    val gpuTempHistory by UIStateManager.gpuTempHistory.collectAsState()
    var showSettings by remember { mutableStateOf(false) }

    // Use boxWidth and boxHeight but subtract DraggableBox padding (8.dp * 2 = 16.dp)
    val effectiveWidth = (boxWidth - 16).coerceAtLeast(100f)
    val effectiveHeight = (boxHeight - 16).coerceAtLeast(80f)

    Box(modifier = Modifier.size(width = effectiveWidth.dp, height = effectiveHeight.dp)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "PERFORMANCE HUD", 
                    color = Color.Gray, 
                    fontSize = (fontSize * 0.8f).sp, 
                    fontWeight = FontWeight.Bold
                )
                if (UIStateManager.editMode) {
                    IconButton(
                        onClick = { showSettings = true },
                        modifier = Modifier.size((fontSize * 2).dp)
                    ) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.Gray,
                            modifier = Modifier.size((fontSize * 1.5f).dp)
                        )
                    }
                }
            }
            
            // Available height for rows (effectiveHeight minus padding and header)
            val headerHeight = fontSize * 2.5f
            val rowsAvailableHeight = effectiveHeight - 16 - headerHeight
            val rowHeight = rowsAvailableHeight / 3 

            val cpuUsage = cpuHistory.lastOrNull() ?: 0
            val cpuTemp = cpuTempHistory.lastOrNull() ?: 0
            HudRow("CPU", "$cpuUsage% ${if (cpuTemp > 0) "$cpuTemp°C" else ""}", cpuHistory, Color.Cyan, 100, rowHeight, fontSize)
            
            val gpuUsage = gpuHistory.lastOrNull() ?: 0
            val gpuTemp = gpuTempHistory.lastOrNull() ?: 0
            HudRow("GPU", "$gpuUsage% ${if (gpuTemp > 0) "$gpuTemp°C" else ""}", gpuHistory, Color.Green, 100, rowHeight, fontSize)
            
            val currentMem = memoryHistory.lastOrNull() ?: 0
            val totalMem = UIStateManager.totalMemoryMB
            val memText = if (totalMem > 0) "$currentMem / $totalMem MB" else "$currentMem MB"
            HudRow("RAM", memText, memoryHistory.map { it.toInt() }, Color.Yellow, totalMem.toInt().coerceAtLeast(1), rowHeight, fontSize)
        }

        if (showSettings) {
            HudSettingsDialog(onDismiss = { showSettings = false })
        }
    }
}

@Composable
fun HudSettingsDialog(onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var gpuNodes by remember { mutableStateOf(listOf<Triple<String, String, String>>()) }
    var thermalNodes by remember { mutableStateOf(listOf<Triple<String, String, String>>()) }
    var selectedCategory by remember { mutableStateOf("GPU Core") }

    LaunchedEffect(Unit) {
        // GPU Core: nodes that show utilization
        gpuNodes = scanSystemNodes("/sys/class/kgsl", "gpu_busy_percentage")
        if (gpuNodes.isEmpty()) {
            gpuNodes = scanSystemNodes("/sys/class/kgsl", "gpubusy")
        }
        
        // Thermal: all thermal zones
        thermalNodes = scanSystemNodes("/sys/class/thermal", "temp")
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.75f),
            shape = RoundedCornerShape(12.dp),
            color = Color(0xFF151515)
        ) {
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Column: Categories
                Column(
                    modifier = Modifier
                        .weight(0.3f)
                        .background(Color.Black.copy(alpha = 0.4f))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("MONITOR", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    CategoryButton("GPU Core", isSelected = selectedCategory == "GPU Core") { selectedCategory = "GPU Core" }
                    CategoryButton("CPU Temp", isSelected = selectedCategory == "CPU Temp") { selectedCategory = "CPU Temp" }
                    CategoryButton("GPU Temp", isSelected = selectedCategory == "GPU Temp") { selectedCategory = "GPU Temp" }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(48.dp)
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "Done",
                            tint = Color.Green,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }

                // Right Column: List of Nodes
                Column(
                    modifier = Modifier
                        .weight(0.7f)
                        .padding(16.dp)
                ) {
                    val currentTitle = when(selectedCategory) {
                        "GPU Core" -> "Select GPU Usage Node"
                        "CPU Temp" -> "Select CPU Thermal Zone"
                        "GPU Temp" -> "Select GPU Thermal Zone"
                        else -> ""
                    }
                    
                    Text(
                        text = currentTitle,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    val currentList = if (selectedCategory == "GPU Core") gpuNodes else thermalNodes
                    val currentSelected = when(selectedCategory) {
                        "GPU Core" -> UIStateManager.userSetGPU
                        "CPU Temp" -> UIStateManager.userSetTemp
                        "GPU Temp" -> UIStateManager.userSetGPUTemp
                        else -> ""
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // For GPU Temp, offer "internal" as an option
                        if (selectedCategory == "GPU Temp") {
                            item {
                                NodeItem(
                                    name = "internal",
                                    label = "Use GPU Folder directly",
                                    value = "",
                                    isSelected = UIStateManager.userSetGPUTemp == "internal"
                                ) {
                                    UIStateManager.userSetGPUTemp = "internal"
                                    scope.launch {
                                        org.openmw.utils.GameFilesPreferences.setUserSetGPUTemp(context, "internal")
                                    }
                                }
                            }
                        }

                        items(currentList) { (folder, value, label) ->
                            val displayValue = if (selectedCategory.contains("Temp")) {
                                value.toDoubleOrNull()?.let { 
                                    val temp = if (it > 1000) (it/1000).toInt() else it.toInt()
                                    "${temp}°C"
                                } ?: value
                            } else {
                                // If it's Adreno usage, it might be raw "busy total"
                                if (value.contains(" ")) {
                                    val parts = value.split(" ")
                                    if (parts.size >= 2) {
                                        val b = parts[0].toDoubleOrNull() ?: 0.0
                                        val t = parts[1].toDoubleOrNull() ?: 1.0
                                        "${((b/t)*100).toInt()}%"
                                    } else value
                                } else if (value.toIntOrNull() != null) {
                                    "$value%"
                                } else value
                            }

                            NodeItem(
                                name = folder,
                                label = label,
                                value = displayValue,
                                isSelected = currentSelected == folder
                            ) {
                                when(selectedCategory) {
                                    "GPU Core" -> {
                                        UIStateManager.userSetGPU = folder
                                        scope.launch {
                                            org.openmw.utils.GameFilesPreferences.setUserSetGPU(context, folder)
                                        }
                                    }
                                    "CPU Temp" -> {
                                        UIStateManager.userSetTemp = folder
                                        scope.launch {
                                            org.openmw.utils.GameFilesPreferences.setUserSetTemp(context, folder)
                                        }
                                    }
                                    "GPU Temp" -> {
                                        UIStateManager.userSetGPUTemp = folder
                                        scope.launch {
                                            org.openmw.utils.GameFilesPreferences.setUserSetGPUTemp(context, folder)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CategoryButton(name: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) Color.White.copy(alpha = 0.1f) else Color.Transparent,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 12.dp, horizontal = 8.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = name,
                color = if (isSelected) Color.Cyan else Color.White,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun NodeItem(name: String, label: String, value: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = if (isSelected) Color.Cyan.copy(alpha = 0.1f) else Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        border = if (isSelected) BorderStroke(1.dp, Color.Cyan.copy(alpha = 0.3f)) else null
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(name, color = if (isSelected) Color.Cyan else Color.White, fontSize = 14.sp)
                if (label.isNotEmpty()) {
                    Text(label, color = Color.Gray, fontSize = 11.sp)
                }
            }
            Text(value, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun HudRow(
    label: String, 
    valueText: String, 
    history: List<Int>, 
    color: Color, 
    max: Int, 
    height: Float,
    fontSize: Float
) {
    Column(modifier = Modifier.height(height.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White, fontSize = fontSize.sp)
            Text(valueText, color = color, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
        }
        
        Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(top = 2.dp)) {
            Sparkline(data = history, color = color, max = max)
        }
    }
}

@Composable
fun Sparkline(data: List<Int>, color: Color, max: Int) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (data.size < 2) return@Canvas
        
        val path = Path()
        val width = size.width
        val height = size.height
        val xStep = width / (data.size - 1)
        
        data.forEachIndexed { index, value ->
            val x = index * xStep
            val y = height - (value.toFloat() / max.coerceAtLeast(1) * height).coerceIn(0f, height)
            
            if (index == 0) {
                path.moveTo(x, y)
            } else {
                path.lineTo(x, y)
            }
        }
        
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 1.5.dp.toPx())
        )
    }
}
