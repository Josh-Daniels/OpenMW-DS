package org.openmw.ui.overlay

import android.util.Log
import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.Popup
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.request.ImageRequest
import com.composables.core.ScrollArea
import com.composables.core.Thumb
import com.composables.core.VerticalScrollbar
import com.composables.core.rememberScrollAreaState
import kotlinx.coroutines.launch
import org.libsdl.app.SDLActivity
import org.openmw.R
import org.openmw.ui.controls.ButtonConfig
import org.openmw.ui.controls.ButtonConfigManager
import org.openmw.ui.controls.ButtonConfigManager.filterButtonsByType
import org.openmw.ui.controls.ButtonConfigManager.loadAllButtons
import org.openmw.ui.controls.ButtonConfigManager.saveButtons
import org.openmw.ui.controls.UIStateManager
import org.openmw.ui.controls.UIStateManager.editMode
import org.openmw.ui.controls.UIStateManager.globalColorChange
import org.openmw.ui.controls.UIStateManager.gold
import org.openmw.ui.controls.UIStateManager.isRadialMenuExpanded
import org.openmw.ui.controls.keyCodeToChar
import org.openmw.utils.ColorPickerWheel
import org.openmw.utils.CustomIconPickerButton
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.GameFilesPreferences.getSelectedAnimation
import org.openmw.utils.fromHex
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

@Composable
fun ExpandableCircleButton() {
    val expandedSize = 300.dp
    var showKeyDialog by remember { mutableStateOf(false) }
    var usedKeys by remember { mutableStateOf(listOf<Int>()) }
    val animationDuration = 500
    val size by animateDpAsState(
        targetValue = if (isRadialMenuExpanded) expandedSize else 60.dp,
        animationSpec = tween(durationMillis = animationDuration), label = ""
    )

    // Empty mutable state for sections
    val sections = remember { mutableStateListOf<Pair<String, () -> Unit>>() }
    val allButtons = remember { mutableStateListOf<ButtonConfig>() }
    val radialButtons = remember { derivedStateOf {
        filterButtonsByType(allButtons, "radial")
    } }

    // Function to add a radial button
    fun addRadialButton(keyCode: Int, label: String? = null) {
        val buttonLabel = label ?: keyCodeToChar(keyCode)
        val newConfig = ButtonConfig(
            type = "radial",
            keyCode = keyCode,
            label = buttonLabel,
            position = radialButtons.value.size,
            color = "#FFFFFF",
            alpha = 0.8f,
            uri = null
        )

        allButtons.add(newConfig)

        // Create proper lambda that triggers the key event
        sections.add(buttonLabel to {
            SDLActivity.onNativeKeyDown(keyCode)
            SDLActivity.onNativeKeyUp(keyCode)
        })

        usedKeys = usedKeys + keyCode
        saveButtons(allButtons)
    }

    // Function to delete a radial button
    fun deleteRadialButton(index: Int) {
        if (index in radialButtons.value.indices) {
            val configToRemove = radialButtons.value[index]
            allButtons.remove(configToRemove)
            sections.removeAt(index)

            // Update positions of remaining radial buttons
            val updatedRadials = filterButtonsByType(allButtons, "radial")
            updatedRadials.forEachIndexed { newIndex, config ->
                val updatedConfig = config.copy(position = newIndex)
                allButtons[allButtons.indexOf(config)] = updatedConfig
            }

            saveButtons(allButtons)
        }
    }

    LaunchedEffect(isRadialMenuExpanded) {
        Log.d("PieChart", "🔍 Global state changed: $isRadialMenuExpanded")
        //Thread.dumpStack()
    }

    LaunchedEffect(Unit) {
        val loadedButtons = loadAllButtons()
        allButtons.addAll(loadedButtons)

        // Initialize sections from radial buttons with proper key event lambdas
        radialButtons.value.sortedBy { it.position }.forEach { config ->
            sections.add(config.label to {
                // Create proper lambda that triggers the key event
                SDLActivity.onNativeKeyDown(config.keyCode)
                SDLActivity.onNativeKeyUp(config.keyCode)
            })
        }

        // Update used keys
        usedKeys = radialButtons.value.map { it.keyCode }
    }

    val radius = expandedSize / 2

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.Transparent)
            .clickable {
                Log.d("PieChart", "🔥 Line:212 isRadialMenuExpanded: $isRadialMenuExpanded")
                isRadialMenuExpanded = false
                Log.d("PieChart", "🔥 Line:214 isRadialMenuExpanded: $isRadialMenuExpanded")
            },
        contentAlignment = Alignment.Center
    ) {
        if (isRadialMenuExpanded && !editMode) {
            Log.d("PieChart", "Rendering PieChart with ${sections.size} sections")
            if (sections.isNotEmpty()) {
                PieChart(
                    sections = sections,
                    radius = radius,
                    onSectionClick = { index ->
                        Log.d("PieChart", "🔥 onSectionClick: $index isRadialMenuExpanded: $isRadialMenuExpanded")
                        if (index == sections.size) {
                            Log.d("PieChart", "Line 223")
                            showKeyDialog = true
                        } else {
                            Log.d("PieChart", "📤 Invoking key")
                            sections[index].second.invoke()
                            isRadialMenuExpanded = false
                            Log.d("PieChart", "🔒 isRadialMenuExpanded: ($isRadialMenuExpanded) Expected (false) Line: 228")
                        }
                    },
                    onAddButtonClick = {
                        Log.d("PieChart", "🔥 Line:232 isRadialMenuExpanded: $isRadialMenuExpanded")
                        showKeyDialog = true
                    },
                    onDeleteSection = ::deleteRadialButton
                )
            } else {
                // Show add button when no sections exist
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.DarkGray.copy(alpha = 0.3f), CircleShape)
                        .clickable { showKeyDialog = !showKeyDialog },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "No buttons added",
                            color = Color.White,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Button(
                            onClick = {
                                Log.d("PieChart", "🔥 Line:258 isRadialMenuExpanded: $isRadialMenuExpanded")
                                showKeyDialog = true
                            },
                            shape = CircleShape,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            modifier = Modifier.size(60.dp)
                        ) {
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add button",
                                tint = Color.White,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Text(
                            text = "Tap + to add a button",
                            color = Color.White,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                }
            }
        }
    }

    if (showKeyDialog) {
        SimpleKeySelectionDialog(
            onKeySelected = { keyCode ->
                addRadialButton(keyCode)
                usedKeys = usedKeys + keyCode
                showKeyDialog = false
            },
            onDismiss = { showKeyDialog = false },
            usedKeys = usedKeys
        )
    }
}

@Composable
fun PieChart(
    sections: List<Pair<String, () -> Unit>>,
    radius: Dp,
    onSectionClick: (Int) -> Unit,
    onAddButtonClick: () -> Unit,
    onDeleteSection: (Int) -> Unit
) {
    val radiusPx = with(LocalDensity.current) { radius.toPx() }

    // Dynamically increase ring size if more than 7 sections
    val ringMultiplier = if (sections.size <= 7) 1.0f else 1.4f
    val adjustedRadiusPx = radiusPx * ringMultiplier
    val angleStep = 360f / sections.size

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Center "+" button
        Button(
            onClick = onAddButtonClick,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            modifier = Modifier
                .size(radius / 2.0f)
                .border(
                    2.dp,
                    Color.Black,
                    shape = CircleShape
                )
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add button",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }

        // Section buttons in a single ring
        sections.forEachIndexed { index, (label, _) ->
            val angle = (angleStep * index - 90) * PI / 180
            val x = cos(angle) * adjustedRadiusPx / 1.8f
            val y = sin(angle) * adjustedRadiusPx / 1.8f
            SectionButton(
                x, y, radius, label,
                onDelete = { onDeleteSection(index) },
                onClick = {
                    onSectionClick(index)
                    Log.d("PieChart", "🔥 Line:344 isRadialMenuExpanded: $isRadialMenuExpanded")
                }
            )
        }
    }
}

@Composable
private fun SectionButton(
    x: Double,
    y: Double,
    radius: Dp,
    label: String,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    var showDelete by remember { mutableStateOf(false) }
    if (showDelete) {
        // Delete button
        Box(
            modifier = Modifier
                .size(radius / 3f)
                .background(Color.Red.copy(alpha = 0.9f), CircleShape)
                .clickable {
                    onDelete()
                    showDelete = false
                    Log.d("PieChart", "🔥 Line:370 isRadialMenuExpanded: $isRadialMenuExpanded")
                },
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "✕",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
    Box(
        modifier = Modifier
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .size(radius / 2.5f)
            .background(Color.DarkGray.copy(alpha = 0.9f), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = {
                        Log.d("PieChart", "🔥 Line:386 isRadialMenuExpanded: $isRadialMenuExpanded")
                        onClick()
                    },
                    onLongPress = { showDelete = true }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(4.dp)
        )
    }
}

@Composable
fun SimpleKeySelectionDialog(
    onKeySelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    usedKeys: List<Int>
) {
    val tabTitles = listOf("F1 - 12", "A - Z", "0-9", "Extra")
    var selectedTabIndex by remember { mutableStateOf(0) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = Color(0xFF2C2C2C),
            modifier = Modifier
                .wrapContentHeight()
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = "Select a Key",
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.DarkGray,
                    contentColor = Color.White
                ) {
                    tabTitles.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = {
                                Log.d("PieChart", "🔥 Line:442 isRadialMenuExpanded: $isRadialMenuExpanded")
                                selectedTabIndex = index
                             },
                            text = { Text(title) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                when (selectedTabIndex) {
                    0 -> {
                        val availableFunctionKeys = (1..12).filter {
                            val keyCode = KeyEvent.KEYCODE_F1 + it - 1
                            keyCode !in usedKeys
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.wrapContentHeight()
                        ) {
                            availableFunctionKeys.forEach { fNumber ->
                                val keyCode = KeyEvent.KEYCODE_F1 + fNumber - 1
                                item {
                                    KeyButton(
                                        label = "F$fNumber",
                                        onClick = {
                                            Log.d("PieChart", "🔥 Line:464 isRadialMenuExpanded: $isRadialMenuExpanded")
                                            onKeySelected(keyCode)
                                        }
                                    )
                                }
                            }
                        }
                    }

                    1 -> {
                        val availableChars = ('A'..'Z').filter {
                            val keyCode = KeyEvent.KEYCODE_A + (it - 'A')
                            keyCode !in usedKeys
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            modifier = Modifier.fillMaxHeight(0.8f)
                        ) {
                            availableChars.forEach { char ->
                                val keyCode = KeyEvent.KEYCODE_A + (char - 'A')
                                item {
                                    KeyButton(
                                        label = char.toString(),
                                        onClick = { onKeySelected(keyCode) }
                                    )
                                }
                            }
                        }
                    }

                    2 -> {
                        val availableNumbers = (0..9).filter {
                            val keyCode = KeyEvent.KEYCODE_0 + it
                            keyCode !in usedKeys
                        }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(4),
                            modifier = Modifier.wrapContentHeight()
                        ) {
                            availableNumbers.forEach { number ->
                                val keyCode = KeyEvent.KEYCODE_0 + number
                                item {
                                    KeyButton(
                                        label = number.toString(),
                                        onClick = { onKeySelected(keyCode) }
                                    )
                                }
                            }
                        }
                    }

                    3 -> {
                        val specialKeys = listOf(
                            KeyEvent.KEYCODE_SPACE to "Space",
                            KeyEvent.KEYCODE_ENTER to "Enter",
                            KeyEvent.KEYCODE_ESCAPE to "Esc",
                            KeyEvent.KEYCODE_TAB to "Tab",
                            KeyEvent.KEYCODE_GRAVE to "`"
                        ).filter { (keyCode, _) -> keyCode !in usedKeys }

                        LazyVerticalGrid(
                            columns = GridCells.Fixed(3),
                            modifier = Modifier.wrapContentHeight()
                        ) {
                            specialKeys.forEach { (keyCode, label) ->
                                item {
                                    KeyButton(
                                        label = label,
                                        onClick = { onKeySelected(keyCode) }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
fun HiddenMenu(
    containerWidth: Float,
    containerHeight: Float
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val context = LocalContext.current
        val coroutineScope = rememberCoroutineScope()
        val scrollState = rememberScrollState()
        val stateSB = rememberScrollAreaState(scrollState)
        val parentButton by remember { mutableStateOf(ButtonConfigManager.getOrCreateParentButton()) }
        val isDragging = remember { mutableStateOf(false) }
        var showControlsPopup by remember { mutableStateOf(false) }
        var offset by remember { mutableStateOf(IntOffset.Zero) }
        val offsetX = remember { mutableFloatStateOf(parentButton.offsetX ?: 200f) }
        val offsetY = remember { mutableFloatStateOf(parentButton.offsetY ?: 200f) }
        val buttonSize = remember { mutableStateOf((parentButton.size ?: 60f).dp) }
        val selectedAnimation by getSelectedAnimation(context).collectAsState(initial = "None")
        val animations = getAnimations().toMutableMap().apply { put("None", EnterTransition.None to ExitTransition.None) }
        val (currentEnterAnimation, currentExitAnimation) = animations[selectedAnimation] ?: (animations["None"] ?: error("Default animation not found"))
        val snapX = remember { mutableStateOf<Float?>(null) }
        val snapY = remember { mutableStateOf<Float?>(null) }
        val isUIHidden by GameFilesPreferences.loadUIState(context).collectAsState(initial = false)
        val buttonTint by GameFilesPreferences.loadButtonTint(context).collectAsState(initial = true)
        val buttonUri = remember { mutableStateOf(parentButton.uri) } // Handling URI state
        var hexCode by remember { mutableStateOf(parentButton.color) } // Set initial hex code directly from state
        var buttonColor by remember { mutableStateOf(Color.fromHex(hexCode)) }
        var buttonAlpha by remember { mutableFloatStateOf(parentButton.alpha) }
        val infiniteTransition = rememberInfiniteTransition(label = "")
        val rotation by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ), label = ""
        )

        LaunchedEffect(containerWidth, containerHeight, parentButton) {
            offsetX.value = parentButton.offsetX ?: (200f / containerWidth)
            offsetY.value = parentButton.offsetY ?: (200f / containerHeight)
        }
        LaunchedEffect(globalColorChange) {
            if (globalColorChange) {
                hexCode = UIStateManager.globalColor
                buttonColor = Color.fromHex(hexCode)
                buttonAlpha = UIStateManager.globalAlpha
            } else {
                hexCode = parentButton.color
                buttonColor = Color.fromHex(hexCode)
                buttonAlpha = parentButton.alpha
            }
        }

        val painter: Painter? = buttonUri.value?.let { uri ->
            if (uri.toString().endsWith(".gif", ignoreCase = true)) {
                // Use Coil's GIF decoder for animated GIFs
                rememberAsyncImagePainter(
                    ImageRequest.Builder(context)
                        .data(uri)
                        .decoderFactory(GifDecoder.Factory())
                        .build()
                )
            } else {
                // Regular image handling
                rememberAsyncImagePainter(uri)
            }
        }

        // Manage whats in the tabs here
        var options by remember { mutableStateOf(true) }
        var colors by remember { mutableStateOf(false) }

        fun resetStates() {
            options = false
            colors = false
        }

        fun saveState() {
            coroutineScope.launch {
                // Save the parent button position to JSON
                val updatedParentButton = parentButton.copy(
                    offsetX = offsetX.value,
                    offsetY = offsetY.value,
                    size = buttonSize.value.value,
                    color = hexCode,
                    alpha = buttonAlpha,
                    uri = buttonUri.value
                )
                ButtonConfigManager.saveParentButton(updatedParentButton)
            }
        }

        AnimatedVisibility(
            visible = !isUIHidden,
            enter = currentEnterAnimation,
            exit = currentExitAnimation
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Transparent)
                    .offset {
                        IntOffset(
                            (offsetX.value).roundToInt(),
                            (offsetY.value).roundToInt()
                        )
                    }
                    .border(
                        2.dp,
                        if (isDragging.value && painter == null) Color.Red else Color.Transparent,
                        shape = CircleShape
                    )
                    .then(
                        if (editMode) {
                            Modifier.pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        isDragging.value = true
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        offsetX.floatValue += dragAmount.x
                                        offsetY.floatValue += dragAmount.y

                                        // Calculate snap points with current grid size
                                        val currentGridSize =
                                            UIStateManager.gridSize.intValue
                                        snapX.value =
                                            ((offsetX.floatValue / currentGridSize).roundToInt() * currentGridSize).toFloat()
                                        snapY.value =
                                            ((offsetY.floatValue / currentGridSize).roundToInt() * currentGridSize).toFloat()
                                    },
                                    onDragEnd = {
                                        isDragging.value = false
                                        val currentGridSize =
                                            UIStateManager.gridSize.intValue
                                        offsetX.floatValue =
                                            ((offsetX.floatValue / currentGridSize).roundToInt() * currentGridSize).toFloat()
                                        offsetY.floatValue =
                                            ((offsetY.floatValue / currentGridSize).roundToInt() * currentGridSize).toFloat()
                                        saveState()
                                    },
                                    onDragCancel = {
                                        isDragging.value = false
                                        saveState()
                                    }
                                )
                            }
                        } else Modifier
                    )
            ) {
                Box(
                    modifier = Modifier
                        .size(buttonSize.value)
                        .background(
                            if (painter != null) {
                                Color.Transparent
                            } else {
                                buttonColor.copy(alpha = buttonAlpha)
                            },
                            shape = CircleShape
                        )
                        .clickable(enabled = !editMode) {
                            Log.d("PieChart", "Before toggle Line 729 - isRadialMenuExpanded: $isRadialMenuExpanded")
                            isRadialMenuExpanded = !isRadialMenuExpanded
                            Log.d("PieChart", "After toggle - isRadialMenuExpanded: $isRadialMenuExpanded")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (painter != null) {
                        Image(
                            painter = painter,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .alpha(buttonAlpha),
                            colorFilter = if (buttonTint) {
                                ColorFilter.tint(buttonColor.copy(alpha = buttonAlpha))
                            } else {
                                null
                            }
                        )
                    } else {
                        Icon(
                            Icons.Default.Menu,
                            contentDescription = "Expand menu",
                            tint = (buttonColor.copy(alpha = buttonAlpha))
                        )
                    }
                }
                if (editMode) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .size(30.dp)
                            .background(Color.Black, shape = CircleShape)
                            .clickable { showControlsPopup = true }
                            .border(
                                2.dp,
                                Color.DarkGray,
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = Color.Red,
                            modifier = Modifier.graphicsLayer(
                                rotationZ = rotation
                            )
                        )
                    }
                }
                if (showControlsPopup) {
                        Popup(
                            alignment = Alignment.Center
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Transparent),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .width(300.dp)
                                        .offset { offset }
                                        .padding(top = 5.dp)
                                        .background(
                                            Color.Black.copy(alpha = 0.8f),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .border(
                                            2.dp,
                                            Color(202, 165, 96),
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .pointerInput(Unit) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                offset = IntOffset(
                                                    offset.x + dragAmount.x.roundToInt(),
                                                    offset.y // + dragAmount.y.roundToInt()
                                                )
                                            }
                                        }
                                ) {
                                    Column {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            val selectedTabIndex = when {
                                                options -> 0
                                                colors -> 1
                                                else -> 0
                                            }
                                            ScrollableTabRow(
                                                selectedTabIndex = selectedTabIndex,
                                                edgePadding = 1.dp,
                                                contentColor = White,
                                                containerColor = Color.Transparent,
                                                modifier = Modifier.weight(1f),
                                                indicator = { tabPositions ->
                                                    TabRowDefaults.Indicator(
                                                        Modifier.tabIndicatorOffset(
                                                            tabPositions[selectedTabIndex]
                                                        ),
                                                        color = Color(202, 165, 96)
                                                    )
                                                }
                                            ) {
                                                Tab(
                                                    selected = options,
                                                    onClick = {
                                                        resetStates()
                                                        options = true
                                                    },
                                                    text = { Text(stringResource(R.string.options)) }
                                                )
                                                Tab(
                                                    selected = colors,
                                                    onClick = {
                                                        resetStates()
                                                        colors = true
                                                    },
                                                    text = { Text(stringResource(R.string.colors)) }
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    showControlsPopup = false
                                                    saveState()
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Close",
                                                    tint = White
                                                )
                                            }
                                        }

                                        ScrollArea(state = stateSB) {
                                            Column(
                                                modifier = Modifier
                                                    .padding(8.dp)
                                                    .verticalScroll(scrollState)
                                            ) {
                                                if (options) {
                                                    HorizontalDivider(
                                                        color = Color(202, 165, 96),
                                                        thickness = 1.dp
                                                    )
                                                    if (buttonUri.value != null) {
                                                        Row {
                                                            TextButton(
                                                                onClick = {
                                                                    buttonUri.value = null
                                                                    saveState()
                                                                },
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = gold,
                                                                    contentColor = Color.Red
                                                                )
                                                            ) {
                                                                Text("Remove Icon")
                                                            }
                                                            if (painter != null) {
                                                                Image(
                                                                    painter = painter,
                                                                    contentDescription = null,
                                                                    modifier = Modifier
                                                                        .fillMaxSize()
                                                                        .alpha(buttonAlpha),
                                                                    colorFilter = if (buttonTint) {
                                                                        ColorFilter.tint(
                                                                            buttonColor.copy(
                                                                                alpha = buttonAlpha
                                                                            )
                                                                        )
                                                                    } else {
                                                                        null
                                                                    }
                                                                )
                                                            }
                                                        }
                                                        Text(
                                                            text = "${buttonUri.value}",
                                                            color = White,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(bottom = 8.dp)
                                                        )
                                                    } else {
                                                        CustomIconPickerButton(
                                                            context = context,
                                                            buttonId = 100,
                                                            buttonUri = buttonUri,
                                                            containerWidth = containerWidth,
                                                            containerHeight = containerHeight
                                                        )
                                                    }
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    Column(
                                                        horizontalAlignment = Alignment.CenterHorizontally,
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .padding(8.dp)
                                                    ) {
                                                        HorizontalDivider(color = Color(202, 165, 96), thickness = 1.dp)


                                                        Text(
                                                            text = stringResource(R.string.increase_size_delete_decrease_size),
                                                            color = White,
                                                            fontWeight = FontWeight.Bold,
                                                            modifier = Modifier.padding(bottom = 8.dp)
                                                        )
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.SpaceEvenly, // Even spacing
                                                            modifier = Modifier
                                                                .height(48.dp)
                                                                .fillMaxWidth()
                                                        ) {
                                                            // Increase size button (+)
                                                            IconButton(
                                                                onClick = {
                                                                    buttonSize.value += 10.dp
                                                                    saveState()
                                                                },
                                                                modifier = Modifier
                                                                    .size(40.dp)
                                                                    .background(
                                                                        Color.Black,
                                                                        CircleShape
                                                                    )
                                                                    .border(
                                                                        2.dp,
                                                                        White,
                                                                        CircleShape
                                                                    )
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Add,
                                                                    contentDescription = "Increase size",
                                                                    tint = White,
                                                                    modifier = Modifier.size(24.dp)
                                                                )
                                                            }

                                                            // Decrease size button (-)
                                                            IconButton(
                                                                onClick = {
                                                                    buttonSize.value -= 10.dp
                                                                    if (buttonSize.value < 50.dp) buttonSize.value =
                                                                        30.dp
                                                                    saveState()
                                                                },
                                                                modifier = Modifier
                                                                    .size(40.dp)
                                                                    .background(
                                                                        Color.Black,
                                                                        CircleShape
                                                                    )
                                                                    .border(
                                                                        2.dp,
                                                                        White,
                                                                        CircleShape
                                                                    )
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Remove,
                                                                    contentDescription = "Decrease size",
                                                                    tint = White,
                                                                    modifier = Modifier.size(24.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                                if (colors) {
                                                    ColorPickerWheel(
                                                        initialColor = buttonColor,
                                                        onColorSelected = { color, hex, alphaValue ->
                                                            hexCode = hex
                                                            buttonAlpha = alphaValue
                                                            buttonColor = color
                                                            saveState()
                                                            Log.d("ColorWheel", "Selected Color: $color, Hex: $hex, Alpha: $alphaValue")
                                                        }
                                                    )
                                                }
                                            }
                                            VerticalScrollbar(
                                                modifier = Modifier
                                                    .align(Alignment.TopEnd)
                                                    .fillMaxHeight()
                                                    .width(10.dp)
                                            ) {
                                                Thumb(
                                                    modifier = Modifier.background(
                                                        Color(202, 165, 96).copy(0.3f),
                                                        RoundedCornerShape(100)
                                                    ),
                                                )
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
fun KeyButton(label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(containerColor = Color.LightGray),
        modifier = Modifier
            .size(60.dp)
            .padding(1.dp)
    ) {
        Text(
            text = label,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Serif
            )
        )
    }
}
