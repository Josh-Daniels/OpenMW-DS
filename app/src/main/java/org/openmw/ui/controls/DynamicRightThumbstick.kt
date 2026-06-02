package org.openmw.ui.controls

import android.content.Context
import android.util.Log
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Mouse
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import coil.compose.rememberAsyncImagePainter
import coil.decode.GifDecoder
import coil.request.ImageRequest
import com.composables.core.ScrollArea
import com.composables.core.Thumb
import com.composables.core.VerticalScrollbar
import com.composables.core.rememberScrollAreaState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import org.libsdl.app.SDLActivity
import org.openmw.EngineActivity
import org.openmw.R
import org.openmw.isControllerConnected
import org.openmw.ui.controls.UIStateManager.configureControls
import org.openmw.ui.controls.UIStateManager.editMode
import org.openmw.ui.controls.UIStateManager.enableRightThumb
import org.openmw.ui.controls.UIStateManager.globalColorChange
import org.openmw.ui.controls.UIStateManager.isCursorVisible
import org.openmw.ui.controls.UIStateManager.updateButtonState
import org.openmw.ui.overlay.getAnimations
import org.openmw.utils.ColorPickerWheel
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.GameFilesPreferences.getSelectedAnimation
import org.openmw.utils.GameFilesPreferences.getSensitivityMouse
import org.openmw.utils.GameFilesPreferences.getSensitivityRT
import org.openmw.utils.GameFilesPreferences.loadAutoMouseMode
import org.openmw.utils.GameFilesPreferences.setSensitivityRT
import org.openmw.utils.GameFilesPreferences.writeVirtualRightThumbstick
import org.openmw.ui.view.currentDeviceRealSize
import org.openmw.utils.CustomIconPickerButton
import org.openmw.utils.fromHex
import kotlin.math.roundToInt

@OptIn(InternalCoroutinesApi::class)
@Composable
fun ResizableDraggableRightThumbstick(
    context: Context,
    containerWidth: Float,
    containerHeight: Float
) {
    val buttonStates by UIStateManager.buttonStates.collectAsState()
    val buttonState = buttonStates[98]
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = ""
    )
    val rotationThumb by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = ""
    )

    buttonState?.let { state ->
        var buttonSize by remember { mutableStateOf(state.size.dp) }
        var hexCode by remember { mutableStateOf(state.color) }
        var buttonColor by remember { mutableStateOf(Color.fromHex(hexCode)) }
        var buttonAlpha by remember { mutableFloatStateOf(state.alpha) }
        val buttonTint = remember { mutableStateOf(state.buttonTint) }
        val offsetX = remember { mutableFloatStateOf(state.offsetX) }
        val offsetY = remember { mutableFloatStateOf(state.offsetY) }
        val isUIHidden by GameFilesPreferences.loadUIState(context).collectAsState(initial = false)
        var offset by remember { mutableStateOf(IntOffset.Zero) }
        val buttonUri = remember { mutableStateOf(state.uri) }
        val density = LocalDensity.current
        val radiusPx = with(density) { (buttonSize / 2).toPx() }
        val isDragging = remember { mutableStateOf(false) }
        val scrollState = rememberScrollState()
        val stateSB = rememberScrollAreaState(scrollState)
        val autoMouseMode by loadAutoMouseMode(context).collectAsState(initial = "Hybrid")
        val selectedAnimation by getSelectedAnimation(context).collectAsState(initial = "None")
        val isVirtualRight by GameFilesPreferences.getVirtualRightThumbstick(context).collectAsState(initial = false)
        val sensitivityMouseFlow = getSensitivityMouse(context).collectAsState(initial = 5000f)
        var sensitivityMouse by remember { mutableFloatStateOf(sensitivityMouseFlow.value ?: 5000f) }
        val sensitivityRTFlow = getSensitivityRT(context).collectAsState(initial = 2500f)
        var sensitivityRT by remember { mutableFloatStateOf(sensitivityRTFlow.value ?: 2500f) }
        val initialOffset =
            with(LocalDensity.current) { Offset(buttonSize.toPx() / 2, buttonSize.toPx() / 2) }
        var touchOffset by remember { mutableStateOf(initialOffset) }
        var showControlsPopup by remember { mutableStateOf(false) }
        val controllerConnected = isControllerConnected(context)
        val painter: Painter? = state.uri?.let { uri ->
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

        val rightThumbColor =
            if (isDragging.value) Color.Red.copy(alpha = buttonAlpha) else buttonColor.copy(
                alpha = buttonAlpha
            )

        val saveState = {
            val updatedState = state.copy(
                size = buttonSize.value,
                offsetX = offsetX.floatValue,
                offsetY = offsetY.floatValue,
                color = hexCode,
                alpha = buttonAlpha,
                uri = buttonUri.value,
                group = 1,
                vibrate = false,
                buttonTint = buttonTint.value
            )
            updateButtonState(98, updatedState)
            UIStateManager.saveButtonState(containerWidth, containerHeight)
        }

        // Manage what's in the tabs here
        var options by remember { mutableStateOf(true) }
        var interact by remember { mutableStateOf(false) }
        var colors by remember { mutableStateOf(false) }

        fun resetStates() {
            options = false
            interact = false
            colors = false
        }

        LaunchedEffect(sensitivityMouseFlow.value) {
            sensitivityMouse = sensitivityMouseFlow.value ?: 5000f
        }
        LaunchedEffect(sensitivityRTFlow.value) {
            sensitivityRT = sensitivityRTFlow.value ?: 2500f
        }

        // Update hexCode, buttonColor, and buttonAlpha to use the global color and alpha if globalColorChange is enabled
        LaunchedEffect(globalColorChange) {
            if (globalColorChange) {
                hexCode = UIStateManager.globalColor
                buttonColor = Color.fromHex(hexCode)
                buttonAlpha = UIStateManager.globalAlpha
            } else {
                hexCode = state.color
                buttonColor = Color.fromHex(hexCode)
                buttonAlpha = state.alpha
            }
        }

        val animations = getAnimations().toMutableMap().apply { put("None", EnterTransition.None to ExitTransition.None) }
        val (currentEnterAnimation, currentExitAnimation) = animations[selectedAnimation] ?: (animations["Slide Vertically"] ?: error("Default animation not found"))

        val visibilityState = if (isCursorVisible != 0 && autoMouseMode == "None") {
            isUIHidden
        } else {
            !isUIHidden
        }
        val visibilityFinal = if (controllerConnected) {
            isUIHidden
        } else {
            visibilityState
        }

        AnimatedVisibility(
            visible = visibilityFinal,
            enter = currentEnterAnimation,
            exit = currentExitAnimation
        ) {
            Box(
                modifier = Modifier
                    .size(buttonSize)
                    .background(Color.Transparent)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(buttonSize)
                        .graphicsLayer {
                            translationX = offsetX.floatValue
                            translationY = offsetY.floatValue
                        }
                        .background(Color.Transparent)
                        .then(
                            if (editMode) {
                                Modifier.pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = {
                                            isDragging.value = true
                                        },
                                        onDrag = { _, dragAmount ->
                                            offsetX.floatValue += dragAmount.x
                                            offsetY.floatValue += dragAmount.y
                                        },
                                        onDragEnd = {
                                            isDragging.value = false
                                            saveState()
                                        }
                                    )
                                }
                            } else Modifier
                        )
                        .border(2.dp, if (painter == null) rightThumbColor else Color.Transparent, CircleShape)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (!configureControls && !editMode) {
                                    Modifier.pointerInput(Unit) {
                                        awaitPointerEventScope {
                                            val windowManager =
                                                context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                                            val (screenWidth, screenHeight) = windowManager.currentDeviceRealSize()
                                            val sdlWidth = EngineActivity.resolutionX.toFloat()
                                            val sdlHeight = EngineActivity.resolutionY.toFloat()

                                            val startX = SDLActivity.getMouseX()
                                                .toFloat() * (screenWidth / sdlWidth)
                                            val startY = SDLActivity.getMouseY()
                                                .toFloat() * (screenHeight / sdlHeight)

                                            var curX = startX
                                            var curY = startY
                                            var draggingStarted = false
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val down =
                                                    event.changes.firstOrNull()?.pressed == true
                                                if (down) {
                                                    while (true) {
                                                        val dragEvent = awaitPointerEvent()
                                                        val dragChange =
                                                            dragEvent.changes.firstOrNull()
                                                        if (dragChange?.pressed == true) {
                                                            val newX = dragChange.position.x
                                                            val newY = dragChange.position.y
                                                            if (!draggingStarted) {
                                                                curX = newX
                                                                curY = newY
                                                                draggingStarted = true
                                                            }
                                                            val movementX =
                                                                if (isCursorVisible != 0) {
                                                                    (newX - curX) * sensitivityMouse / screenWidth
                                                                } else {
                                                                    (newX - curX) * sensitivityRT / screenWidth
                                                                }
                                                            val movementY =
                                                                if (isCursorVisible != 0) {
                                                                    (newY - curY) * sensitivityMouse / screenWidth
                                                                } else {
                                                                    (newY - curY) * sensitivityRT / screenWidth
                                                                }

                                                            touchOffset = Offset(newX, newY)

                                                            if (isVirtualRight) {
                                                                val xRatio = (newX - radiusPx) / radiusPx
                                                                val yRatio = (newY - radiusPx) / radiusPx

                                                                updateStickTest(
                                                                    1,
                                                                    xRatio.coerceIn(-1f, 1f),
                                                                    yRatio.coerceIn(-1f, 1f)
                                                                )
                                                            } else {
                                                                SDLActivity.onNativeMouse(
                                                                    0,
                                                                    2,
                                                                    movementX,
                                                                    movementY,
                                                                    true
                                                                )
                                                            }


                                                            // Update current positions
                                                            curX = newX
                                                            curY = newY
                                                            //Log.d("DragMovement", "movementX: $movementX, movementY: $movementY")
                                                        } else {
                                                            if (isVirtualRight) {
                                                                updateStickTest(1, 0f, 0f)
                                                            }
                                                            draggingStarted = false
                                                            touchOffset = Offset(
                                                                buttonSize.toPx() / 2,
                                                                buttonSize.toPx() / 2
                                                            )
                                                            break
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } else {
                                    Modifier
                                }
                            ),
                    ) {
                        if (painter != null) {
                            Image(
                                painter = painter,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .alpha(buttonAlpha)
                                    .graphicsLayer(
                                        rotationZ = if (editMode) rotationThumb else 0f
                                    ),
                                colorFilter = if (buttonTint.value) {
                                    ColorFilter.tint(rightThumbColor)
                                } else {
                                    null
                                }
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(25.dp)
                                .graphicsLayer {
                                    val centerX = buttonSize.toPx() / 2
                                    translationX = (touchOffset.x - centerX).coerceIn(-radiusPx,
                                        radiusPx
                                    )
                                    translationY = (touchOffset.y - centerX).coerceIn(-radiusPx,
                                        radiusPx
                                    )
                                }
                                .background(
                                    rightThumbColor,
                                    shape = CircleShape
                                )
                                .border(2.dp, if (painter == null) rightThumbColor else Color.Transparent, CircleShape)
                        )
                    }
                    if (editMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .size(30.dp)
                                .background(Color.Black, shape = CircleShape)
                                .clickable { showControlsPopup = true }
                                .border(2.dp, Color.DarkGray, shape = CircleShape),
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
                                                    interact -> 1
                                                    colors -> 2
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
                                                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
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
                                                        selected = interact,
                                                        onClick = {
                                                            resetStates()
                                                            interact = true
                                                        },
                                                        text = { Text(stringResource(R.string.interact)) }
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
                                                        contentDescription = stringResource(R.string.close),
                                                        tint = White
                                                    )
                                                }
                                            }

                                            ScrollArea(state = stateSB) {
                                                Column(
                                                    modifier = Modifier
                                                        .padding(16.dp)
                                                        .verticalScroll(scrollState)
                                                ) {
                                                    if (options) {
                                                        CustomIconPickerButton(
                                                            context = context,
                                                            buttonId = 98,
                                                            buttonUri = buttonUri,
                                                            containerWidth = containerWidth,
                                                            containerHeight = containerHeight
                                                        )

                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(8.dp)
                                                        ) {
                                                            Text(
                                                                text = stringResource(R.string.increase_size_decrease_size),
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
                                                                        buttonSize += 10.dp
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
                                                                        buttonSize -= 10.dp
                                                                        if (buttonSize < 50.dp) buttonSize = 50.dp
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
                                                    if (interact) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Spacer(modifier = Modifier.width(10.dp))
                                                            Text(
                                                                text = stringResource(R.string.disable_right_thumbstick),
                                                                color = White,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Spacer(modifier = Modifier.weight(1f))
                                                            Switch(
                                                                checked = enableRightThumb,
                                                                onCheckedChange = { _ ->
                                                                    enableRightThumb = false
                                                                    UIStateManager.removeButtonState(98, containerWidth, containerHeight)
                                                                    UIStateManager.saveButtonState(containerWidth, containerHeight)
                                                                },
                                                                colors = SwitchDefaults.colors(
                                                                    checkedThumbColor = Color(202, 165, 96),
                                                                    uncheckedThumbColor = Color.Red,
                                                                    checkedTrackColor = Color.Black.copy(alpha = 0.9f),
                                                                    uncheckedTrackColor = Color.Black.copy(alpha = 0.5f),
                                                                    checkedBorderColor = White,
                                                                    uncheckedBorderColor = White
                                                                )
                                                            )
                                                        }
                                                        HorizontalDivider(color = Color(202, 165, 96), thickness = 1.dp)
                                                        Column(
                                                            horizontalAlignment = Alignment.CenterHorizontally,
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(vertical = 8.dp)
                                                        ) {
                                                            Text(
                                                                text = stringResource(R.string.virtual_right_thumbstick),
                                                                color = White,
                                                                fontWeight = FontWeight.Bold,
                                                                modifier = Modifier.padding(bottom = 8.dp)
                                                            )
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.Center,
                                                                modifier = Modifier.fillMaxWidth()
                                                            ) {
                                                                Icon(
                                                                    imageVector = Icons.Default.Gamepad,
                                                                    contentDescription = null,
                                                                    tint = if (isVirtualRight) Color(202, 165, 96) else White.copy(alpha = 0.3f),
                                                                    modifier = Modifier.size(28.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(16.dp))
                                                                Switch(
                                                                    checked = !isVirtualRight,
                                                                    onCheckedChange = { isChecked ->
                                                                        CoroutineScope(Dispatchers.IO).launch {
                                                                            writeVirtualRightThumbstick(context, !isChecked)
                                                                        }
                                                                    },
                                                                    colors = SwitchDefaults.colors(
                                                                        checkedThumbColor = Color(202, 165, 96),
                                                                        uncheckedThumbColor = Color(202, 165, 96),
                                                                        checkedTrackColor = Color.Black.copy(alpha = 0.9f),
                                                                        uncheckedTrackColor = Color.Black.copy(alpha = 0.9f),
                                                                        checkedBorderColor = White,
                                                                        uncheckedBorderColor = White
                                                                    )
                                                                )
                                                                Spacer(modifier = Modifier.width(16.dp))
                                                                Icon(
                                                                    imageVector = Icons.Default.Mouse,
                                                                    contentDescription = null,
                                                                    tint = if (!isVirtualRight) Color(202, 165, 96) else White.copy(alpha = 0.3f),
                                                                    modifier = Modifier.size(28.dp)
                                                                )
                                                            }
                                                        }
                                                        HorizontalDivider(color = Color(202, 165, 96), thickness = 1.dp)
                                                        Spacer(modifier = Modifier.height(16.dp))
                                                        Column(
                                                            modifier = Modifier
                                                                .fillMaxWidth()
                                                                .padding(horizontal = 16.dp) // Add padding on both sides
                                                        ) {
                                                            // Centered title
                                                            Text(
                                                                text = stringResource(R.string.adjust_sensitivity),
                                                                color = White,
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(bottom = 8.dp),
                                                                textAlign = TextAlign.Center // Center the text
                                                            )

                                                            // Slider row with outer padding
                                                            Row(
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(horizontal = 8.dp), // Inner padding for the slider row
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.SpaceBetween
                                                            ) {
                                                                Slider(
                                                                    value = sensitivityRT,
                                                                    onValueChange = { newValue ->
                                                                        sensitivityRT = newValue
                                                                        CoroutineScope(Dispatchers.Main).launch {
                                                                            setSensitivityRT(context, newValue)
                                                                        }
                                                                    },
                                                                    valueRange = 1000f..5000f,
                                                                    steps = 49,
                                                                    modifier = Modifier
                                                                        .weight(1f)
                                                                        .padding(end = 16.dp), // Space between slider and text
                                                                    colors = SliderDefaults.colors(
                                                                        thumbColor = White,
                                                                        activeTrackColor = Color.Black,
                                                                        inactiveTrackColor = Color.Black,
                                                                        activeTickColor = Color.Red,
                                                                        inactiveTickColor = Color.Red
                                                                    )
                                                                )

                                                                Text(
                                                                    text = "${sensitivityRT.roundToInt()}",
                                                                    fontSize = 24.sp,
                                                                    color = White,
                                                                    modifier = Modifier.width(60.dp) // Fixed width for number display
                                                                )
                                                            }
                                                        }
                                                        HorizontalDivider(color = Color(202, 165, 96), thickness = 1.dp)
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
                                                            Switch(
                                                                checked = buttonTint.value,
                                                                onCheckedChange = {
                                                                    buttonTint.value = it
                                                                    saveState()
                                                                },
                                                                colors = SwitchDefaults.colors(
                                                                    checkedThumbColor = Color(202, 165, 96),
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
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Row {
                                                            Button(
                                                                onClick = {
                                                                    UIStateManager.changeAllButtonColorsAndAlpha(hexCode, buttonAlpha)
                                                                    globalColorChange = !globalColorChange
                                                                },
                                                                modifier = Modifier.width(IntrinsicSize.Max),
                                                                colors = ButtonDefaults.buttonColors(
                                                                    containerColor = Color(202, 165, 96),
                                                                    contentColor = White,
                                                                    disabledContainerColor = Color.Gray,
                                                                    disabledContentColor = White.copy(alpha = 0.5f)
                                                                ),
                                                                elevation = ButtonDefaults.buttonElevation(
                                                                    defaultElevation = 4.dp,
                                                                    pressedElevation = 8.dp,
                                                                    disabledElevation = 0.dp
                                                                )
                                                            ) {
                                                                Text(
                                                                    text = stringResource(R.string.apply_globally),
                                                                    fontSize = 12.sp
                                                                )
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
                                                            Color(202, 165, 96).copy(0.3f), RoundedCornerShape(100)
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
}
