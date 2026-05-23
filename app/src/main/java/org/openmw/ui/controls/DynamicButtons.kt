package org.openmw.ui.controls

import android.content.Context
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
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
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import coil.compose.rememberAsyncImagePainter
import coil.compose.rememberImagePainter
import coil.decode.GifDecoder
import coil.request.ImageRequest
import com.composables.core.ScrollArea
import com.composables.core.Thumb
import com.composables.core.VerticalScrollbar
import com.composables.core.rememberScrollAreaState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.libsdl.app.SDLActivity
import org.libsdl.app.SDLActivity.isMouseShown
import org.libsdl.app.SDLActivity.onNativeKeyDown
import org.libsdl.app.SDLActivity.onNativeKeyUp
import org.openmw.EngineActivity
import org.openmw.R
import org.openmw.isControllerConnected
import org.openmw.ui.controls.UIStateManager.configureControls
import org.openmw.ui.controls.UIStateManager.editMode
import org.openmw.ui.controls.UIStateManager.globalColorChange
import org.openmw.ui.controls.UIStateManager.highlightStep
import org.openmw.ui.controls.UIStateManager.isCursorVisible
import org.openmw.ui.controls.UIStateManager.updateButtonState
import org.openmw.ui.overlay.getAnimations
import org.openmw.utils.ColorPickerWheel
import org.openmw.utils.CustomIconPickerButton
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.GameFilesPreferences.getSelectedAnimation
import org.openmw.utils.GameFilesPreferences.getSelectedKeycodes
import org.openmw.utils.GameFilesPreferences.getSensitivityMouse
import org.openmw.utils.GameFilesPreferences.getSensitivityRT
import org.openmw.utils.GameFilesPreferences.loadAutoMouseMode
import org.openmw.utils.GameFilesPreferences.loadButtonShape
import org.openmw.utils.GameFilesPreferences.setTutorial
import org.openmw.utils.GameFilesPreferences.setWhatsNew
import org.openmw.ui.view.currentDeviceRealSize
import org.openmw.utils.fromHex
import org.openmw.ui.view.vibrate
import kotlin.collections.joinToString
import kotlin.math.roundToInt

private const val TAG = "ButtonInput"

fun stringToShape(shapeName: String): Shape {
    return when (shapeName.lowercase()) {
        "circleshape" -> CircleShape
        "roundedcornershape" -> RoundedCornerShape(percent = 50)
        "rectangleshape" -> RectangleShape
        "stadiumshape" -> RoundedCornerShape(50)
        "chamferedshape" -> CutCornerShape(8.dp)
        else -> {
            println("Unknown shape, defaulting to CircleShape")
            CircleShape
        }
    }
}

@OptIn(InternalCoroutinesApi::class)
@Suppress("DEPRECATION")
@Composable
fun ResizableDraggableButton(
    context: Context,
    id: Int,
    keyCode: Int,
    containerWidth: Float,
    containerHeight: Float,
    sdlView: View,
    onDelete: (Int) -> Unit
) {
    val buttonStates by UIStateManager.buttonStates.collectAsState()
    val buttonState = buttonStates[id]
    val infiniteTransition = rememberInfiniteTransition(label = "")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ), label = ""
    )

    buttonState?.let { state ->
        val buttonSize = remember { mutableStateOf(state.size.dp) }
        var offset by remember { mutableStateOf(IntOffset.Zero) }
        var isPressed by remember { mutableStateOf(false) }
        var showControlsPopup by remember { mutableStateOf(false) }
        val isUIHidden by GameFilesPreferences.loadUIState(context).collectAsState(initial = false)
        val isDragging = remember { mutableStateOf(false) }
        val isLocked = remember { mutableStateOf(state.isLocked) }
        val blockMouse = remember { mutableStateOf(state.blockMouse) }
        val isToggle = remember { mutableStateOf(false) }
        val offsetX = remember { mutableFloatStateOf(state.offsetX) }
        val offsetY = remember { mutableFloatStateOf(state.offsetY) }
        val snapX = remember { mutableStateOf<Float?>(null) }
        val snapY = remember { mutableStateOf<Float?>(null) }
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
        val buttonUri = remember { mutableStateOf(state.uri) } // Handling URI state
        var hexCode by remember { mutableStateOf(state.color) } // Set initial hex code directly from state
        var buttonColor by remember { mutableStateOf(Color.fromHex(hexCode)) }
        var buttonAlpha by remember { mutableFloatStateOf(state.alpha) }
        val scrollState = rememberScrollState()
        val stateSB = rememberScrollAreaState(scrollState)
        val autoMouseMode by loadAutoMouseMode(context).collectAsState(initial = "Hybrid")
        val buttonShape by loadButtonShape(context).collectAsState(initial = "CircleShape")
        val buttonTint by GameFilesPreferences.loadButtonTint(context).collectAsState(initial = true)
        val selectedAnimation by getSelectedAnimation(context).collectAsState(initial = "None")
        val sensitivityMouseFlow = getSensitivityMouse(context).collectAsState(initial = 5000f)
        var sensitivityMouse by remember { mutableFloatStateOf(sensitivityMouseFlow.value ?: 5000f) }
        val sensitivityRTFlow = getSensitivityRT(context).collectAsState(initial = 5000f)
        var sensitivityRT by remember { mutableFloatStateOf(sensitivityRTFlow.value ?: 5000f) }
        val tutorial by GameFilesPreferences.getTutorial(context).collectAsState(initial = false)
        val isVibrationOn by GameFilesPreferences.loadVibrationState(context).collectAsState(initial = true)
        val buttonGroupSwitch by GameFilesPreferences.getButtonGroupSwitch(context).collectAsState(initial = true)
        val animations = getAnimations().toMutableMap().apply { put("None", EnterTransition.None to ExitTransition.None) }
        val (currentEnterAnimation, currentExitAnimation) = animations[selectedAnimation] ?: (animations["None"] ?: error("Default animation not found"))
        val controllerConnected = isControllerConnected(context)
        val scope = rememberCoroutineScope()

        val animatedOffsetX = remember { Animatable(offsetX.floatValue) }
        val animatedOffsetY = remember { Animatable(offsetY.floatValue) }

        // Get the selected key codes from the data store as a flow of lists of integers
        val selectedKeycodesFlow = getSelectedKeycodes(context).map { keycodeString ->
            keycodeString.split(",").map { it.toInt() }
        }

        val selectedKeycodes by selectedKeycodesFlow.collectAsState(initial = emptyList())

        val isOnMouseScreen by derivedStateOf { keyCode in selectedKeycodes }

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

        LaunchedEffect(state.isLocked) {
            isLocked.value = state.isLocked
        }

        LaunchedEffect(state.blockMouse) {
            blockMouse.value = state.blockMouse
        }

        LaunchedEffect(sensitivityMouseFlow.value) {
            sensitivityMouse = sensitivityMouseFlow.value ?: 5000f
        }
        LaunchedEffect(sensitivityRTFlow.value) {
            sensitivityRT = sensitivityRTFlow.value ?: 5000f
        }

        if (configureControls && tutorial && id == 999) {
            if (highlightStep == 4) {
                LaunchedEffect(Unit) {
                    // Define target values for the animation (e.g., move 100 pixels in each direction)
                    val targetX = offsetX.floatValue + 300f
                    val targetY = offsetY.floatValue + 150f

                    // Animate to target values over 3 seconds
                    launch {
                        animatedOffsetX.animateTo(
                            targetValue = targetX,
                            animationSpec = tween(durationMillis = 1000, easing = LinearEasing)
                        )
                        // After animation, update offsetX to the final value
                        offsetX.floatValue = animatedOffsetX.value
                    }
                    launch {
                        animatedOffsetY.animateTo(
                            targetValue = targetY,
                            animationSpec = tween(durationMillis = 1000, easing = LinearEasing)
                        )
                        // After animation, update offsetY to the final value
                        offsetY.floatValue = animatedOffsetY.value
                        highlightStep++
                    }
                }
            }
        }

        val saveState = {
            val updatedState = state.copy(
                size = buttonSize.value.value,
                offsetX = offsetX.floatValue,
                offsetY = offsetY.floatValue,
                isLocked = state.isLocked,
                blockMouse = state.blockMouse,
                color = hexCode, // Use the hex string conversion
                alpha = buttonAlpha,
                uri = buttonUri.value,
                group = state.group
            )
            updateButtonState(buttonState.id, updatedState)
            UIStateManager.saveButtonState(containerWidth, containerHeight)
        }

        // Manage whats in the tabs here
        var options by remember { mutableStateOf(true) }
        var colors by remember { mutableStateOf(false) }

        fun resetStates() {
            options = false
            colors = false
        }

        // Separate function to save group changes
        val saveGroup = { group: Int ->
            val updatedState = state.copy(group = group)
            updateButtonState(buttonState.id, updatedState)
            UIStateManager.saveButtonState(containerWidth, containerHeight)
        }

        val saveToggle = { isLocked: Boolean ->
            val updatedState = state.copy(isLocked = isLocked)
            updateButtonState(state.id, updatedState)
            UIStateManager.saveButtonState(containerWidth, containerHeight)
        }

        val saveBlockToggle = { blockMouse: Boolean ->
            val updatedState = state.copy(blockMouse = blockMouse)
            updateButtonState(state.id, updatedState)
            UIStateManager.saveButtonState(containerWidth, containerHeight)
        }

        // Final visibility state
        val isVisible = when {
            isCursorVisible != 0 && autoMouseMode == "None" && state.group == UIStateManager.buttonsGroup && keyCode == 111 -> !isUIHidden
            isCursorVisible == 0 && autoMouseMode == "None" && state.group == UIStateManager.buttonsGroup -> !isUIHidden
            isCursorVisible != 0 && autoMouseMode != "None" && state.group == UIStateManager.buttonsGroup -> !isUIHidden && keyCode in selectedKeycodes
            isCursorVisible != 0 && autoMouseMode != "None" && state.group != UIStateManager.buttonsGroup -> !isUIHidden && keyCode in selectedKeycodes
            isCursorVisible == 0 && autoMouseMode != "None" && state.group == UIStateManager.buttonsGroup -> !isUIHidden
            else -> false
        }
        val visibilityFinal = if (controllerConnected) {
            isUIHidden
        } else {
            isVisible
        }

        AnimatedVisibility(
            visible = visibilityFinal,
            enter = currentEnterAnimation,
            exit = currentExitAnimation
        ) {

            Box(
                modifier = Modifier
                    .size(buttonSize.value)
                    .background(Color.Transparent)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(buttonSize.value)
                        .background(Color.Transparent)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .offset {
                                IntOffset(
                                    (if (highlightStep == 4 && id == 999) animatedOffsetX.value else offsetX.floatValue).roundToInt(),
                                    (if (highlightStep == 4 && id == 999) animatedOffsetY.value else offsetY.floatValue).roundToInt()
                                )
                            }
                            .border(
                                2.dp,
                                if (isDragging.value && painter == null) Color.Red else if (painter == null) Color.Black else Color.Transparent,
                                shape = stringToShape(buttonShape)
                            )
                            .size(buttonSize.value)
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
                                                // Snap to nearest grid
                                                val currentGridSize =
                                                    UIStateManager.gridSize.intValue
                                                offsetX.floatValue =
                                                    ((offsetX.floatValue / currentGridSize).roundToInt() * currentGridSize).toFloat()
                                                offsetY.floatValue =
                                                    ((offsetY.floatValue / currentGridSize).roundToInt() * currentGridSize).toFloat()

                                                saveState()
                                                if (configureControls && tutorial && id == 999 && highlightStep == 5) {
                                                    highlightStep++
                                                }
                                            },
                                            onDragCancel = {
                                                isDragging.value = false
                                            }
                                        )
                                    }
                                } else Modifier
                            )
                    ) {
                        // Main button
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    if (painter != null) {
                                        Color.Transparent
                                    } else {
                                        if (isPressed) buttonColor.copy(alpha = 1.0f) else buttonColor.copy(
                                            alpha = buttonAlpha
                                        )
                                    },
                                    shape = stringToShape(buttonShape)
                                )
                                .pointerInput(Unit) {
                                    awaitPointerEventScope {
                                        if (!configureControls && !isDragging.value) {
                                            val windowManager =
                                                context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
                                            val (screenWidth, screenHeight) = windowManager.currentDeviceRealSize()
                                            val sdlWidth = EngineActivity.resolutionX.toFloat()
                                            val sdlHeight = EngineActivity.resolutionY.toFloat()

                                            var curX: Float = 0f
                                            var curY: Float = 0f

                                            if (UIStateManager.tempCodeGroup == "OpenMW") {
                                                val startX = SDLActivity.getMouseX().toFloat() * (screenWidth / sdlWidth)
                                                val startY = SDLActivity.getMouseY().toFloat() * (screenHeight / sdlHeight)
                                                curX = startX
                                                curY = startY
                                            }

                                            var draggingStarted = false
                                            var downFrom = 0L

                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val down =
                                                    event.changes.firstOrNull()?.pressed == true

                                                if (down) {
                                                    downFrom = System.currentTimeMillis()
                                                    // Handle the onPress event
                                                    if (isLocked.value) {
                                                        isPressed = !isPressed
                                                        if (isPressed) {
                                                            onNativeKeyDown(keyCode)
                                                            isToggle.value = true
                                                        } else {
                                                            isToggle.value = false
                                                            onNativeKeyUp(keyCode)
                                                        }
                                                    } else {
                                                        when (keyCode) {
                                                            KeyEvent.KEYCODE_Z -> {
                                                                if (isCursorVisible == 1 && UIStateManager.tempCodeGroup == "OpenMW") {
                                                                    SDLActivity.onNativeMouse(
                                                                        1,
                                                                        0,
                                                                        0f,
                                                                        0f,
                                                                        true
                                                                    )
                                                                } else {
                                                                    if (isVibrationOn) {
                                                                        vibrate(context)
                                                                    }
                                                                }
                                                            }

                                                            KeyEvent.KEYCODE_E -> {
                                                                if (isVibrationOn) {
                                                                    vibrate(context)
                                                                }
                                                            }
                                                        }
                                                        onNativeKeyDown(keyCode)
                                                        Log.d(TAG, "keyCode: $keyCode")
                                                        if (keyCode == KeyEvent.KEYCODE_B) {
                                                            onNativeKeyUp(keyCode)
                                                            if (isCursorVisible == 1) {
                                                                SDLActivity.onNativeMouse(2, 0, 0f, 0f, true)
                                                            }
                                                        }
                                                    }
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

                                                            if (System.currentTimeMillis() - downFrom > 100) {
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

                                                                // Call the native function with updated coordinates
                                                                if (UIStateManager.tempCodeGroup == "OpenMW" && !blockMouse.value) {
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
                                                            }
                                                        } else {
                                                            if (!draggingStarted) {
                                                                // No action required
                                                            }
                                                            draggingStarted = false
                                                            break
                                                        }
                                                    }
                                                }
                                                // End the press event
                                                if (isLocked.value) {
                                                    // Do nothing for SHIFT or ALT keys as they toggle
                                                } else {
                                                    onNativeKeyUp(keyCode)
                                                    if (UIStateManager.tempCodeGroup == "OpenMW") {
                                                        if (isMouseShown() == 1) {
                                                            SDLActivity.onNativeMouse(1, 1, 0f, 0f, true)
                                                            SDLActivity.onNativeMouse(2, 1, 0f, 0f, true)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
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
                                        if (isToggle.value) {
                                            ColorFilter.tint(buttonColor.copy(alpha = 1.0f))
                                        } else {
                                            ColorFilter.tint(buttonColor.copy(alpha = buttonAlpha))
                                        }
                                    } else {
                                        null
                                    }
                                )
                            } else {
                                Text(
                                    text = keyCodeToChar(keyCode),
                                    color = White,
                                    modifier = Modifier.align(Alignment.Center)
                                )
                            }
                            if (editMode) {
                                Text(
                                    text = "ID: $id, Key: ${keyCodeToChar(keyCode)}",
                                    color = White
                                )
                            }
                        }
                        if (editMode) {
                            val iconTint = if (configureControls && tutorial && highlightStep == 6 && id == 999) {
                                Color.Yellow
                            } else {
                                Color.Red
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopStart)
                                    .size(30.dp)
                                    .background(Color.Black, shape = stringToShape(buttonShape))
                                    .clickable { showControlsPopup = true }
                                    .border(
                                        2.dp,
                                        Color.DarkGray,
                                        shape = stringToShape(buttonShape)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More",
                                    tint = iconTint,
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

                                                    if (buttonGroupSwitch && !isOnMouseScreen) {
                                                        val text = if (state.group == 1) "${state.group} -> 2" else "${state.group} -> 1"
                                                        IconButton(
                                                            onClick = {
                                                                if (state.group == 1) {
                                                                    saveGroup(2)
                                                                } else {
                                                                    saveGroup(1)
                                                                }
                                                            }
                                                        ) {
                                                            Text(
                                                                text = text,
                                                                color = White,
                                                                modifier = Modifier.padding(4.dp),
                                                                maxLines = 1,
                                                                softWrap = false
                                                            )
                                                        }
                                                        Spacer(modifier = Modifier.width(10.dp))
                                                    }


                                                    IconButton(
                                                        onClick = {
                                                            showControlsPopup = false
                                                            saveState()
                                                            if (configureControls && tutorial && id == 999) {
                                                                highlightStep++
                                                                onDelete(id)
                                                                scope.launch {
                                                                    setWhatsNew(context, false)
                                                                    setTutorial(context, false)
                                                                }
                                                            }
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
                                                        if (highlightStep == 6 && id == 999) {
                                                            Text(
                                                                text = stringResource(R.string.edit_each_btn_tip),
                                                                style = TextStyle(
                                                                    color = White,
                                                                    fontSize = 20.sp
                                                                )
                                                            )
                                                        }
                                                        if (options) {
                                                            if (keyCode !in listOf(54, 111)) {
                                                                Row(
                                                                    verticalAlignment = Alignment.CenterVertically,
                                                                    horizontalArrangement = Arrangement.spacedBy(
                                                                        8.dp
                                                                    ),
                                                                    modifier = Modifier
                                                                        .height(40.dp)
                                                                        .fillMaxWidth()
                                                                ) {
                                                                    Text(
                                                                        text = stringResource(R.string.show_in_mouse_ui),
                                                                        color = White,
                                                                        fontWeight = FontWeight.Bold
                                                                    )
                                                                    Spacer(
                                                                        modifier = Modifier.weight(
                                                                            1f
                                                                        )
                                                                    )
                                                                    Switch(
                                                                        checked = isOnMouseScreen,
                                                                        onCheckedChange = { checked ->
                                                                            val updatedKeycodes = if (checked) {
                                                                                selectedKeycodes + keyCode
                                                                            } else {
                                                                                selectedKeycodes - keyCode
                                                                            }.joinToString(",")

                                                                            CoroutineScope(Dispatchers.Main).launch {
                                                                                GameFilesPreferences.setSelectedKeycodes(context, updatedKeycodes)
                                                                            }
                                                                        }
                                                                        ,
                                                                        colors = SwitchDefaults.colors(
                                                                            checkedThumbColor = Color(202,165,96),
                                                                            uncheckedThumbColor = Color.Red,
                                                                            checkedTrackColor = Color.Black.copy(alpha = 0.9f),
                                                                            uncheckedTrackColor = Color.Black.copy(alpha = 0.5f),
                                                                            checkedBorderColor = White,
                                                                            uncheckedBorderColor = White
                                                                        )
                                                                    )
                                                                }
                                                            }
                                                            HorizontalDivider(color = Color(202, 165, 96), thickness = 1.dp)
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(
                                                                    8.dp
                                                                ),
                                                                modifier = Modifier
                                                                    .height(40.dp)
                                                                    .fillMaxWidth()
                                                            ) {
                                                                Text(
                                                                    text = stringResource(R.string.toggleable),
                                                                    color = White,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                                Spacer(modifier = Modifier.weight(1f))
                                                                Switch(
                                                                    checked = state.isLocked,
                                                                    onCheckedChange = {
                                                                        if (state.isLocked) {
                                                                            saveToggle(false)
                                                                        } else {
                                                                            saveToggle(true)
                                                                        }
                                                                    },
                                                                    colors = SwitchDefaults.colors(
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
                                                            Row(
                                                                verticalAlignment = Alignment.CenterVertically,
                                                                horizontalArrangement = Arrangement.spacedBy(
                                                                    8.dp
                                                                ),
                                                                modifier = Modifier
                                                                    .height(40.dp)
                                                                    .fillMaxWidth()
                                                            ) {
                                                                Text(
                                                                    text = "Block mouse movement?",
                                                                    color = White,
                                                                    fontWeight = FontWeight.Bold
                                                                )
                                                                Spacer(modifier = Modifier.weight(1f))
                                                                Switch(
                                                                    checked = state.blockMouse,
                                                                    onCheckedChange = {
                                                                        if (state.blockMouse) {
                                                                            saveBlockToggle(false)
                                                                        } else {
                                                                            saveBlockToggle(true)
                                                                        }
                                                                    },
                                                                    colors = SwitchDefaults.colors(
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

                                                            CustomIconPickerButton(
                                                                context = context,
                                                                buttonId = id,
                                                                buttonUri = buttonUri,
                                                                containerWidth = containerWidth,
                                                                containerHeight = containerHeight
                                                            )
                                                            Spacer(modifier = Modifier.height(4.dp))
                                                            Column(
                                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                                modifier = Modifier
                                                                    .fillMaxWidth()
                                                                    .padding(8.dp)
                                                            ) {
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
                                                                                stringToShape(
                                                                                    buttonShape
                                                                                )
                                                                            )
                                                                            .border(
                                                                                2.dp,
                                                                                White,
                                                                                stringToShape(
                                                                                    buttonShape
                                                                                )
                                                                            )
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.Add,
                                                                            contentDescription = "Increase size",
                                                                            tint = White,
                                                                            modifier = Modifier.size(24.dp)
                                                                        )
                                                                    }

                                                                    // Delete button (center)
                                                                    IconButton(
                                                                        onClick = { onDelete(id) },
                                                                        modifier = Modifier
                                                                            .size(40.dp)
                                                                            .background(
                                                                                Color.Red,
                                                                                stringToShape(
                                                                                    buttonShape
                                                                                )
                                                                            )
                                                                            .border(
                                                                                2.dp,
                                                                                White,
                                                                                stringToShape(
                                                                                    buttonShape
                                                                                )
                                                                            )
                                                                    ) {
                                                                        Icon(
                                                                            imageVector = Icons.Default.Delete,
                                                                            contentDescription = "Delete",
                                                                            tint = White,
                                                                            modifier = Modifier.size(24.dp)
                                                                        )
                                                                    }

                                                                    // Decrease size button (-)
                                                                    IconButton(
                                                                        onClick = {
                                                                            buttonSize.value -= 10.dp
                                                                            if (buttonSize.value < 50.dp) buttonSize.value = 30.dp
                                                                            saveState()
                                                                        },
                                                                        modifier = Modifier
                                                                            .size(40.dp)
                                                                            .background(
                                                                                Color.Black,
                                                                                stringToShape(
                                                                                    buttonShape
                                                                                )
                                                                            )
                                                                            .border(
                                                                                2.dp,
                                                                                White,
                                                                                stringToShape(
                                                                                    buttonShape
                                                                                )
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
}

fun keyCodeToChar(keyCode: Int): String {
    return when (keyCode) {
        // ────── Function keys ──────
        in KeyEvent.KEYCODE_F1..KeyEvent.KEYCODE_F12 ->
            "F${keyCode - KeyEvent.KEYCODE_F1 + 1}"

        // ────── Modifier keys (your original list) ──────
        KeyEvent.KEYCODE_SHIFT_LEFT  -> "Shift-L"
        KeyEvent.KEYCODE_SHIFT_RIGHT -> "Shift-R"
        KeyEvent.KEYCODE_CTRL_LEFT   -> "Ctrl-L"
        KeyEvent.KEYCODE_CTRL_RIGHT  -> "Ctrl-R"
        KeyEvent.KEYCODE_ALT_LEFT    -> "Alt-L"
        KeyEvent.KEYCODE_ALT_RIGHT   -> "Alt-R"

        // ────── Common editing keys ──────
        KeyEvent.KEYCODE_SPACE   -> "Space"
        KeyEvent.KEYCODE_ESCAPE  -> "Escape"
        KeyEvent.KEYCODE_ENTER   -> "Enter"
        KeyEvent.KEYCODE_GRAVE   -> "Grave"
        KeyEvent.KEYCODE_TAB     -> "Tab"

        // ────── D-Pad ──────
        KeyEvent.KEYCODE_DPAD_UP -> "\u2191"
        KeyEvent.KEYCODE_DPAD_DOWN -> "\u2193"
        KeyEvent.KEYCODE_DPAD_LEFT -> "\u2190"
        KeyEvent.KEYCODE_DPAD_RIGHT -> "\u2192"

        // ────── Gamepad face buttons ──────
        KeyEvent.KEYCODE_BUTTON_A -> "A"
        KeyEvent.KEYCODE_BUTTON_B -> "B"
        KeyEvent.KEYCODE_BUTTON_X -> "X"
        KeyEvent.KEYCODE_BUTTON_Y -> "Y"

        // ────── Bumpers ──────
        KeyEvent.KEYCODE_BUTTON_L1 -> "LB"
        KeyEvent.KEYCODE_BUTTON_R1 -> "RB"

        // ────── Triggers (digital press) ──────
        KeyEvent.KEYCODE_BUTTON_L2 -> "LT"
        KeyEvent.KEYCODE_BUTTON_R2 -> "RT"

        // ────── System / menu ──────
        KeyEvent.KEYCODE_BUTTON_START  -> "Start"
        KeyEvent.KEYCODE_BUTTON_SELECT -> "Select"
        KeyEvent.KEYCODE_BUTTON_MODE   -> "Guide"

        // ────── Thumb-stick clicks ──────
        KeyEvent.KEYCODE_BUTTON_THUMBL -> "LS"
        KeyEvent.KEYCODE_BUTTON_THUMBR -> "RS"

        // ────── Extra generic buttons (C, Z, numeric) ──────
        KeyEvent.KEYCODE_BUTTON_C -> "C"
        KeyEvent.KEYCODE_BUTTON_Z -> "Z"

        KeyEvent.KEYCODE_BUTTON_1  -> "Btn1"
        KeyEvent.KEYCODE_BUTTON_2  -> "Btn2"
        KeyEvent.KEYCODE_BUTTON_3  -> "Btn3"
        KeyEvent.KEYCODE_BUTTON_4  -> "Btn4"
        KeyEvent.KEYCODE_BUTTON_5  -> "Btn5"
        KeyEvent.KEYCODE_BUTTON_6  -> "Btn6"
        KeyEvent.KEYCODE_BUTTON_7  -> "Btn7"
        KeyEvent.KEYCODE_BUTTON_8  -> "Btn8"
        KeyEvent.KEYCODE_BUTTON_9  -> "Btn9"
        KeyEvent.KEYCODE_BUTTON_10 -> "Btn10"
        KeyEvent.KEYCODE_BUTTON_11 -> "Btn11"
        KeyEvent.KEYCODE_BUTTON_12 -> "Btn12"
        KeyEvent.KEYCODE_BUTTON_13 -> "Btn13"
        KeyEvent.KEYCODE_BUTTON_14 -> "Btn14"
        KeyEvent.KEYCODE_BUTTON_15 -> "Btn15"
        KeyEvent.KEYCODE_BUTTON_16 -> "Btn16"

        // ────── Numeric keypad (0-9) ──────
        KeyEvent.KEYCODE_NUMPAD_0 -> "n0"
        KeyEvent.KEYCODE_NUMPAD_1 -> "n1"
        KeyEvent.KEYCODE_NUMPAD_2 -> "n2"
        KeyEvent.KEYCODE_NUMPAD_3 -> "n3"
        KeyEvent.KEYCODE_NUMPAD_4 -> "n4"
        KeyEvent.KEYCODE_NUMPAD_5 -> "n5"
        KeyEvent.KEYCODE_NUMPAD_6 -> "n6"
        KeyEvent.KEYCODE_NUMPAD_7 -> "n7"
        KeyEvent.KEYCODE_NUMPAD_8 -> "n8"
        KeyEvent.KEYCODE_NUMPAD_9 -> "n9"

        // ────── Numeric keypad (0-9) ──────
        in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 ->
            "${keyCode - KeyEvent.KEYCODE_0}"

        // ────── Letters A-Z (fallback) ──────
        else -> {
            // Handles KEYCODE_A .. KEYCODE_Z automatically
            if (keyCode in KeyEvent.KEYCODE_A..KeyEvent.KEYCODE_Z) {
                (keyCode - KeyEvent.KEYCODE_A + 'A'.code).toChar().toString()
            } else {
                // Unknown key → show its raw code (helps debugging)
                "Key$keyCode"
            }
        }
    }
}
