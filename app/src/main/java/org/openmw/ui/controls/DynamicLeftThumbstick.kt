package org.openmw.ui.controls

import android.content.Context
import android.util.Log
import android.view.KeyEvent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.libsdl.app.SDLActivity.onNativeKeyDown
import org.libsdl.app.SDLActivity.onNativeKeyUp
import org.openmw.R
import org.openmw.isControllerConnected
import org.openmw.ui.controls.UIStateManager.configureControls
import org.openmw.ui.controls.UIStateManager.editMode
import org.openmw.ui.controls.UIStateManager.globalColorChange
import org.openmw.ui.controls.UIStateManager.isCursorVisible
import org.openmw.ui.controls.UIStateManager.menuAlpha
import org.openmw.ui.controls.UIStateManager.menuColor
import org.openmw.ui.controls.UIStateManager.offsetXFlow
import org.openmw.ui.controls.UIStateManager.offsetYFlow
import org.openmw.ui.controls.UIStateManager.showWebView
import org.openmw.ui.controls.UIStateManager.updateButtonState
import org.openmw.ui.overlay.getAnimations
import org.openmw.utils.ColorPickerWheel
import org.openmw.utils.CustomIconPickerButton
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.GameFilesPreferences.getAutoRun
import org.openmw.utils.GameFilesPreferences.getSelectedAnimation
import org.openmw.utils.GameFilesPreferences.getSelectedKeycodes
import org.openmw.utils.GameFilesPreferences.loadAutoMouseMode
import org.openmw.utils.GameFilesPreferences.writeVirtualLeftThumbstick
import org.openmw.utils.fromHex
import kotlin.math.roundToInt
import kotlin.math.sqrt

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ResizableDraggableThumbstick(
    context: Context,
    containerWidth: Float,
    containerHeight: Float,
) {
    val buttonStates by UIStateManager.buttonStates.collectAsState()
    val buttonState = buttonStates[99]
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
        val offsetX by offsetXFlow.collectAsState()
        val offsetY by offsetYFlow.collectAsState()
        val scrollState = rememberScrollState()
        val stateSB = rememberScrollAreaState(scrollState)
        val buttonUri = remember { mutableStateOf(state.uri) }
        val selectedAnimation by getSelectedAnimation(context).collectAsState(initial = "None")
        val autoMouseMode by loadAutoMouseMode(context).collectAsState(initial = "Hybrid")
        val autoRUN by getAutoRun(context).collectAsState(initial = true)
        var offset by remember { mutableStateOf(IntOffset.Zero) }
        val density = LocalDensity.current
        val radiusPx = with(density) { (buttonSize / 2).toPx() }
        val deadZone = 0.2f * radiusPx
        var touchState by remember { mutableStateOf(Offset(0f, 0f)) }
        val isDragging = remember { mutableStateOf(false) }

        // Track key states to prevent redundant events
        var isWPressed by remember { mutableStateOf(false) }
        var isAPressed by remember { mutableStateOf(false) }
        var isSPressed by remember { mutableStateOf(false) }
        var isDPressed by remember { mutableStateOf(false) }
        var isShiftPressed by remember { mutableStateOf(false) }

        var showControlsPopup by remember { mutableStateOf(false) }
        val isUIHidden by GameFilesPreferences.loadUIState(context).collectAsState(initial = false)
        val isVirtualLeft by GameFilesPreferences.getVirtualLeftThumbstick(context).collectAsState(initial = true)
        val buttonGroupSwitch by GameFilesPreferences.getButtonGroupSwitch(context).collectAsState(initial = false)
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

        // Manage what's in the tabs here
        var options by remember { mutableStateOf(true) }
        var interact by remember { mutableStateOf(false) }
        var colors by remember { mutableStateOf(false) }

        fun resetStates() {
            options = false
            interact = false
            colors = false
        }

        val thumbColor =
            if (isDragging.value) Color.Red.copy(alpha = buttonAlpha) else buttonColor.copy(
                alpha = buttonAlpha
            )

        menuColor = buttonColor
        menuAlpha = buttonAlpha

        val saveState = {
            val updatedState = state.copy(
                size = buttonSize.value,
                offsetX = offsetX,
                offsetY = offsetY,
                color = hexCode,
                alpha = buttonAlpha,
                uri = buttonUri.value,
                group = 1,
                vibrate = false,
                buttonTint = buttonTint.value
            )
            updateButtonState(99, updatedState)
            UIStateManager.saveButtonState(containerWidth, containerHeight)
        }

        val animations = getAnimations().toMutableMap().apply { put("None", EnterTransition.None to ExitTransition.None) }
        val (currentEnterAnimation, currentExitAnimation) = animations[selectedAnimation] ?: (animations["Slide Vertically"] ?: error("Default animation not found"))

        val selectedKeycodesFlow = getSelectedKeycodes(context).map { keycodeString ->
            keycodeString.split(",").map { it.toInt() }
        }
        val selectedKeycodes by selectedKeycodesFlow.collectAsState(initial = emptyList())
        val isOnMouseScreen by derivedStateOf { 99 in selectedKeycodes }
        val visibility = when {
            isCursorVisible == 0 && autoMouseMode == "None" -> !isUIHidden
            isCursorVisible == 0 && autoMouseMode == "Hybrid" || 99 in selectedKeycodes -> !isUIHidden
            isCursorVisible == 0 && autoMouseMode == "Hybrid" || 99 !in selectedKeycodes -> isUIHidden
            isCursorVisible == 0 -> !isUIHidden
            else -> !isUIHidden
        }

        val visibilityFinal = if (controllerConnected || UIKeyboard.showVKB || showWebView) {
            isUIHidden
        } else {
            visibility
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
                        .background(Color.Transparent)
                        .then(
                            if (editMode) {
                                Modifier.pointerInput(Unit) {
                                    detectDragGestures(
                                        onDragStart = {
                                            isDragging.value = true
                                        },
                                        onDrag = { _, dragAmount ->
                                            offsetX + dragAmount.x
                                            offsetY + dragAmount.y
                                            updateOffsets(
                                                offsetX + dragAmount.x,
                                                offsetY + dragAmount.y
                                            )
                                        },
                                        onDragEnd = {
                                            isDragging.value = false
                                            saveState()
                                        }
                                    )
                                }
                            } else Modifier
                        )
                        .border(2.dp, if (painter == null) thumbColor else Color.Transparent, CircleShape)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                Modifier.pointerInput(Unit) {
                                    if (!configureControls) {
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                val location = Offset(
                                                    event.changes.first().position.x,
                                                    event.changes.first().position.y
                                                )
                                                when (event.type) {
                                                    PointerEventType.Press, PointerEventType.Move -> {
                                                        val newX = (location.x - radiusPx).coerceIn(
                                                            -radiusPx,
                                                            radiusPx
                                                        )
                                                        val newY = (location.y - radiusPx).coerceIn(
                                                            -radiusPx,
                                                            radiusPx
                                                        )
                                                        touchState = Offset(newX, newY)

                                                        val xRatio = touchState.x / radiusPx
                                                        val yRatio = touchState.y / radiusPx
                                                        val distanceFromCenter =
                                                            sqrt(xRatio * xRatio + yRatio * yRatio)

                                                        if (isVirtualLeft) {
                                                            val clampedX = xRatio.coerceIn(-1f, 1f)
                                                            val clampedY = yRatio.coerceIn(-1f, 1f)
                                                            updateStickTest(0, clampedX, clampedY)
                                                        } else {
                                                            if (autoRUN) {
                                                                // Handle shift key (90%+ tilt in any direction)
                                                                if (distanceFromCenter > 0.9f) {
                                                                    if (!isShiftPressed) {
                                                                        onNativeKeyDown(KeyEvent.KEYCODE_SHIFT_LEFT)
                                                                        isShiftPressed = true
                                                                    }
                                                                } else if (isShiftPressed) {
                                                                    onNativeKeyUp(KeyEvent.KEYCODE_SHIFT_LEFT)
                                                                    isShiftPressed = false
                                                                }
                                                            }

                                                            // Handle movement keys with state tracking
                                                            val shouldPressW = touchState.y < -deadZone
                                                            val shouldPressS = touchState.y > deadZone
                                                            val shouldPressA = touchState.x < -deadZone
                                                            val shouldPressD = touchState.x > deadZone

                                                            if (shouldPressW != isWPressed) {
                                                                if (shouldPressW) onNativeKeyDown(KeyEvent.KEYCODE_W) else onNativeKeyUp(KeyEvent.KEYCODE_W)
                                                                isWPressed = shouldPressW
                                                            }
                                                            if (shouldPressS != isSPressed) {
                                                                if (shouldPressS) onNativeKeyDown(KeyEvent.KEYCODE_S) else onNativeKeyUp(KeyEvent.KEYCODE_S)
                                                                isSPressed = shouldPressS
                                                            }
                                                            if (shouldPressA != isAPressed) {
                                                                if (shouldPressA) onNativeKeyDown(KeyEvent.KEYCODE_A) else onNativeKeyUp(KeyEvent.KEYCODE_A)
                                                                isAPressed = shouldPressA
                                                            }
                                                            if (shouldPressD != isDPressed) {
                                                                if (shouldPressD) onNativeKeyDown(KeyEvent.KEYCODE_D) else onNativeKeyUp(KeyEvent.KEYCODE_D)
                                                                isDPressed = shouldPressD
                                                            }
                                                        }
                                                    }

                                                    PointerEventType.Release, PointerEventType.Exit -> {
                                                        if (isVirtualLeft) {
                                                            touchState = Offset.Zero
                                                            updateStickTest(0, 0f, 0f)
                                                        } else {
                                                            touchState = Offset.Zero
                                                            if (isWPressed) { onNativeKeyUp(KeyEvent.KEYCODE_W); isWPressed = false }
                                                            if (isAPressed) { onNativeKeyUp(KeyEvent.KEYCODE_A); isAPressed = false }
                                                            if (isSPressed) { onNativeKeyUp(KeyEvent.KEYCODE_S); isSPressed = false }
                                                            if (isDPressed) { onNativeKeyUp(KeyEvent.KEYCODE_D); isDPressed = false }
                                                            if (isShiftPressed) {
                                                                onNativeKeyUp(KeyEvent.KEYCODE_SHIFT_LEFT)
                                                                isShiftPressed = false
                                                            }
                                                        }
                                                    }

                                                    else -> Unit
                                                }
                                            }
                                        }
                                    }
                                }
                            )
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
                                    ColorFilter.tint(thumbColor)
                                } else {
                                    null
                                }
                            )
                        }
                        Box(
                            modifier = Modifier
                                .size(25.dp)
                                .graphicsLayer {
                                    translationX = touchState.x
                                    translationY = touchState.y
                                }
                                .background(
                                    thumbColor,
                                    shape = CircleShape
                                )
                                .border(if (painter == null) 2.dp else 0.dp, thumbColor, CircleShape)
                        )
                    }
                    // Small button at the top right outside the border, relative to buttonSize
                    if (buttonGroupSwitch) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(
                                    x = -(buttonSize / 20).value.dp,
                                    y = (buttonSize / 20).value.dp
                                )
                                .size(20.dp)
                                .background(Color.Transparent, shape = CircleShape)
                                .clickable {
                                    UIStateManager.buttonsGroup =
                                        if (UIStateManager.buttonsGroup == 1) 2 else 1
                                }
                                .border(1.dp, thumbColor, shape = CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "${UIStateManager.buttonsGroup}",
                                color = White,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(4.dp)
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
                                                            buttonId = 99,
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
                                                        Spacer(modifier = Modifier.height(2.dp))
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            modifier = Modifier
                                                                .height(40.dp)
                                                                .fillMaxWidth()
                                                        ) {
                                                            Text(
                                                                text = stringResource(R.string.show_in_mouse_ui),
                                                                color = White,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Spacer(modifier = Modifier.weight(1f))
                                                            Switch(
                                                                checked = isOnMouseScreen,
                                                                onCheckedChange = { isChecked ->
                                                                    val updatedKeycodes = if (isChecked) {
                                                                        selectedKeycodes + 99
                                                                    } else {
                                                                        selectedKeycodes - 99
                                                                    }.joinToString(",")
                                                                    CoroutineScope(Dispatchers.Main).launch {
                                                                        GameFilesPreferences.setSelectedKeycodes(context, updatedKeycodes)
                                                                    }
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
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                            modifier = Modifier
                                                                .height(40.dp)
                                                                .fillMaxWidth()
                                                        ) {
                                                            Text(
                                                                text = stringResource(R.string.auto_run),
                                                                color = White,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Spacer(modifier = Modifier.weight(1f))
                                                            Switch(
                                                                checked = autoRUN,
                                                                onCheckedChange = { isChecked ->
                                                                    CoroutineScope(Dispatchers.Main).launch {
                                                                        GameFilesPreferences.setAutoRun(context, isChecked)
                                                                    }
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
                                                                text = stringResource(R.string.virtual_left_thumbstick),
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
                                                                    tint = if (isVirtualLeft) Color(202, 165, 96) else White.copy(alpha = 0.3f),
                                                                    modifier = Modifier.size(28.dp)
                                                                )
                                                                Spacer(modifier = Modifier.width(16.dp))
                                                                Switch(
                                                                    checked = !isVirtualLeft,
                                                                    onCheckedChange = { isChecked ->
                                                                        CoroutineScope(Dispatchers.IO).launch {
                                                                            writeVirtualLeftThumbstick(context, !isChecked)
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
                                                                    imageVector = Icons.Default.Keyboard,
                                                                    contentDescription = null,
                                                                    tint = if (!isVirtualLeft) Color(202, 165, 96) else White.copy(alpha = 0.3f),
                                                                    modifier = Modifier.size(28.dp)
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

fun updateOffsets(newOffsetX: Float, newOffsetY: Float) {
    offsetXFlow.value = newOffsetX
    offsetYFlow.value = newOffsetY
}
