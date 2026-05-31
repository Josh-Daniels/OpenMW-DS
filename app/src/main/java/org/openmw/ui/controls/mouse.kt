@file:OptIn(InternalCoroutinesApi::class)

package org.openmw.ui.controls

import android.util.Log
import android.view.MotionEvent.ACTION_SCROLL
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.request.ImageRequest
import com.composables.core.ScrollArea
import com.composables.core.Thumb
import com.composables.core.VerticalScrollbar
import com.composables.core.rememberScrollAreaState
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import org.libsdl.app.SDLActivity
import org.libsdl.app.SDLActivity.onNativeMouse
import org.openmw.EngineActivity
import org.openmw.R
import org.openmw.isControllerConnected
import org.openmw.ui.controls.UIStateManager.configureControls
import org.openmw.ui.controls.UIStateManager.editMode
import org.openmw.ui.controls.UIStateManager.globalColorChange
import org.openmw.ui.controls.UIStateManager.gold
import org.openmw.ui.controls.UIStateManager.userUI
import org.openmw.ui.overlay.getAnimations
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.GameFilesPreferences.getOffsetXMouse
import org.openmw.utils.GameFilesPreferences.getOffsetYMouse
import org.openmw.utils.GameFilesPreferences.getSelectedAnimation
import org.openmw.utils.ColorPickerWheel
import org.openmw.utils.CustomIconPickerButton
import org.openmw.utils.fromHex
import kotlin.math.roundToInt

@Composable
fun MouseIcon(
    containerWidth: Float,
    containerHeight: Float
) {
    val context = LocalContext.current
    val offsetXMouse by getOffsetXMouse(context).collectAsState(initial = 0f)
    val offsetYMouse by getOffsetYMouse(context).collectAsState(initial = 0f)
    val sdlWidth = EngineActivity.resolutionX.toFloat()
    val sdlHeight = EngineActivity.resolutionY.toFloat()

    var xPos by remember { mutableFloatStateOf(0f) }
    var yPos by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(containerWidth, containerHeight, sdlWidth, sdlHeight) {
        while (true) {
            withFrameNanos {
                val rawX = SDLActivity.getMouseX().toFloat()
                val rawY = SDLActivity.getMouseY().toFloat()
                xPos = (rawX * (containerWidth / sdlWidth)) + (offsetXMouse ?: 0f)
                yPos = (rawY * (containerHeight / sdlHeight)) + (offsetYMouse ?: 0f)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = rememberAsyncImagePainter(model = "file:${userUI}/pointer_arrow.png"),
            contentDescription = "Pointer Icon",
            alignment = Alignment.TopStart,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(32.dp)
                .graphicsLayer {
                    translationX = xPos
                    translationY = yPos
                }
        )
    }
}

@Composable
fun ScrollWheelIndicator(
    containerWidth: Float,
    containerHeight: Float
) {
    val context = LocalContext.current
    val scrollState = remember { Animatable(0f) }
    var showControlsPopup by remember { mutableStateOf(false) }
    val utilityButton by remember {
        mutableStateOf(ButtonConfigManager.getOrCreateUtilityButtonById(id = 101))
    }
    var offset by remember { mutableStateOf(IntOffset.Zero) }
    val offsetX = remember { mutableFloatStateOf(utilityButton.offsetX ?: 0.5f) }
    val offsetY = remember { mutableFloatStateOf(utilityButton.offsetY ?: 0.5f) }
    val buttonSize = remember { mutableStateOf((utilityButton.size ?: 60f).dp) }
    val coroutineScope = rememberCoroutineScope()
    val scrollStateBars = rememberScrollState()
    val stateSB = rememberScrollAreaState(scrollStateBars)
    val haptic = LocalHapticFeedback.current
    val isDragging = remember { mutableStateOf(false) }
    val selectedAnimation by getSelectedAnimation(context).collectAsState(initial = "None")
    val animations = getAnimations().toMutableMap().apply { put("None", EnterTransition.None to ExitTransition.None) }
    val (currentEnterAnimation, currentExitAnimation) = animations[selectedAnimation] ?: (animations["None"] ?: error("Default animation not found"))
    val controllerConnected = isControllerConnected(context)
    val snapX = remember { mutableStateOf<Float?>(null) }
    val snapY = remember { mutableStateOf<Float?>(null) }
    val isUIHidden by GameFilesPreferences.loadUIState(context).collectAsState(initial = false)
    val buttonUri = remember { mutableStateOf(utilityButton.uri) } // Handling URI state
    val buttonTint = remember { mutableStateOf(utilityButton.buttonTint ?: true) }
    var hexCode by remember { mutableStateOf(utilityButton.color) } // Set initial hex code directly from state
    var buttonColor by remember { mutableStateOf(Color.fromHex(hexCode)) }
    var buttonAlpha by remember { mutableFloatStateOf(utilityButton.alpha) }
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = ""
    )

    LaunchedEffect(containerWidth, containerHeight, utilityButton) {
        offsetX.floatValue = utilityButton.offsetX ?: (0.5f / containerWidth)
        offsetY.floatValue = utilityButton.offsetY ?: (0.5f / containerHeight)
    }
    LaunchedEffect(globalColorChange) {
        if (globalColorChange) {
            hexCode = UIStateManager.globalColor
            buttonColor = Color.fromHex(hexCode)
            buttonAlpha = UIStateManager.globalAlpha
        } else {
            hexCode = utilityButton.color
            buttonColor = Color.fromHex(hexCode)
            buttonAlpha = utilityButton.alpha
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
            // Create an updated copy of the utility button with new UI values
            val updatedUtilityButton = utilityButton.copy(
                offsetX = offsetX.floatValue,
                offsetY = offsetY.floatValue,
                size = buttonSize.value.value,
                color = hexCode,
                alpha = buttonAlpha,
                uri = buttonUri.value,
                buttonTint = buttonTint.value
            )

            // Save the updated utility button by its ID
            ButtonConfigManager.saveUtilityButtonById(updatedUtilityButton)
        }
    }

    // dimensions
    val wheelWidth = buttonSize.value
    val wheelHeight = wheelWidth * 3
    val notchCount = 5
    val notchHeight = 3.dp
    val notchSpacing = wheelHeight / (notchCount + 1)

    val visibilityFinal = if (controllerConnected) {
        isUIHidden
    } else {
        !isUIHidden
    }

    AnimatedVisibility(
        visible = visibilityFinal,
        enter = currentEnterAnimation,
        exit = currentExitAnimation
    ) {
        Box(
            modifier = Modifier
                .background(Color.Transparent)
                .offset {
                    IntOffset(
                        (offsetX.floatValue).roundToInt(),
                        (offsetY.floatValue).roundToInt()
                    )
                }
                .border(
                    2.dp,
                    if (isDragging.value) Color.Red else Color.Transparent
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
                    .size(width = wheelWidth, height = wheelHeight)
                    .background(if (buttonUri.value != null) Color.Transparent else buttonColor.copy(buttonAlpha), RoundedCornerShape(4.dp))
                    .pointerInput(Unit, editMode) {
                        if (!configureControls && !isDragging.value && !editMode) {
                            detectVerticalDragGestures(
                                onVerticalDrag = { change, dragAmount ->
                                    val distanceY = -dragAmount / 5f
                                    onNativeMouse(0, ACTION_SCROLL, 0f, distanceY, false)

                                    val previousNotch =
                                        (scrollState.value / notchSpacing.toPx()).toInt()
                                    coroutineScope.launch {
                                        scrollState.snapTo(scrollState.value + dragAmount / 10)
                                        val newNotch =
                                            (scrollState.value / notchSpacing.toPx()).toInt()
                                        if (newNotch != previousNotch) {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        }
                                    }
                                    change.consume()
                                },
                                onDragEnd = {
                                    coroutineScope.launch {
                                        scrollState.animateTo(
                                            0f,
                                            animationSpec = spring(dampingRatio = 0.5f)
                                        )
                                    }
                                }
                            )
                        }
                    }
            ) {
                if (painter != null) {
                    Image(
                        painter = painter,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(buttonAlpha),
                        colorFilter = if (buttonTint.value) {
                            ColorFilter.tint(buttonColor.copy(alpha = buttonAlpha))
                        } else {
                            null
                        }
                    )
                } else {
                    Canvas(modifier = Modifier.matchParentSize()) {
                        val scrollOffset = scrollState.value

                        repeat(notchCount) { i ->
                            val yPos = notchSpacing.toPx() * (i + 1) + scrollOffset

                            if (yPos >= 0 && yPos <= size.height) {
                                drawRoundRect(
                                    color = Color.LightGray.copy(alpha = 1.0f),
                                    topLeft = Offset(
                                        size.width * 0.25f,
                                        yPos - notchHeight.toPx() / 2
                                    ),
                                    size = Size(size.width * 0.5f, notchHeight.toPx()),
                                    cornerRadius = CornerRadius(notchHeight.toPx() / 2)
                                )
                            }
                        }
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
                                                    .verticalScroll(scrollStateBars)
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
                                                                    colorFilter = if (buttonTint.value) {
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
                                                            buttonId = 101,
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
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier
                                                            .height(40.dp)
                                                            .fillMaxWidth()
                                                    ) {
                                                        Text(
                                                            text = "Colorize Icon?",
                                                            color = White,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                        Spacer(modifier = Modifier.weight(1f))
                                                        androidx.compose.material3.Switch(
                                                            checked = buttonTint.value,
                                                            onCheckedChange = {
                                                                buttonTint.value = it
                                                                saveState()
                                                            },
                                                            colors = androidx.compose.material3.SwitchDefaults.colors(
                                                                checkedThumbColor = Color(202,165,96),
                                                                uncheckedThumbColor = Color.Red,
                                                                checkedTrackColor = Color.Black.copy(alpha = 0.9f),
                                                                uncheckedTrackColor = Color.Black.copy(alpha = 0.5f),
                                                                checkedBorderColor = White,
                                                                uncheckedBorderColor = White
                                                            )
                                                        )
                                                    }
                                                    HorizontalDivider(color = Color(202, 165, 96), thickness = 1.dp)

                                                    if (buttonTint.value) {
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
                                                    } else {
                                                        Box(modifier = Modifier.fillMaxWidth().height(150.dp), contentAlignment = Alignment.Center) {
                                                            Text("Colorize icon disabled", color = White)
                                                        }
                                                    }
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
}
