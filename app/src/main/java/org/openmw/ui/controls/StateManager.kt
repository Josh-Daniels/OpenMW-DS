package org.openmw.ui.controls

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.KeyEvent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.openmw.Constants
import org.openmw.R
import org.openmw.ui.controls.UIStateManager.buttonsGroup
import org.openmw.ui.controls.UIStateManager.configureControls
import org.openmw.ui.controls.UIStateManager.containerGlobalHeight
import org.openmw.ui.controls.UIStateManager.containerGlobalWidth
import org.openmw.ui.controls.UIStateManager.enableRightThumb
import org.openmw.ui.controls.UIStateManager.highlightStep
import org.openmw.ui.controls.UIStateManager.menuAlpha
import org.openmw.ui.controls.UIStateManager.menuColor
import org.openmw.utils.GameFilesPreferences
import org.openmw.ui.view.addCustomLog
import org.openmw.utils.stringRes
import java.io.BufferedWriter
import java.io.File

data class ButtonState(
    val id: Int,
    val size: Float,
    val offsetX: Float,
    val offsetY: Float,
    val isLocked: Boolean,
    val blockMouse: Boolean,
    val keyCode: Int,
    val color: String,
    val alpha: Float,
    val uri: Uri?,
    val group: Int,
    val vibrate: Boolean = true,
    val isMouseButton: Boolean = false,
    val mouseButton: Int = 1 // 1: Left, 2: Middle, 3: Right
)

val DeletedButtonState = ButtonState(
    id = -1, // Placeholder ID
    size = 0f,
    offsetX = 0f,
    offsetY = 0f,
    isLocked = false,
    blockMouse = false,
    keyCode = -1,
    color = "",
    alpha = 0f,
    uri = null,
    group = -1,
    vibrate = false,
    isMouseButton = false,
    mouseButton = 1
)

object UIStateManager {
    var useNavmesh by mutableStateOf(false)
    var buttonsGroup by mutableIntStateOf(1)
    var globalColorChange by mutableStateOf(false)
    var globalColor by mutableStateOf("#FFFFFF") // Default color
    var globalAlpha by mutableFloatStateOf(1.0f)

    // This is needed for now in Activities
    var tempCodeGroup by mutableStateOf("OpenMW")
    val gameList = arrayOf("OpenMW", "Dethrace", "UQM")
    var uqmVersion by mutableStateOf("0.8.4")
    var uqmJNI by mutableStateOf(false)
    val userUI by derivedStateOf {
        "${Constants.USER_FILE_STORAGE}/${tempCodeGroup}/ui"
    }

    val memoryInfoFlow = MutableStateFlow("")
    val cpuUsageFlow = MutableStateFlow(0)
    var cpuUsageText by mutableStateOf("${stringRes(R.string.cpu_usage)}: 0%")
    var languageSet by mutableStateOf("en")
    val logMessagesFlow = MutableStateFlow("")
    var logMessagesText by mutableStateOf("")
    var isAppLoggingEnabled by mutableStateOf(false)
    var customCFG by mutableStateOf(false)
    val offsetXFlow = MutableStateFlow(0f)
    val offsetYFlow = MutableStateFlow(0f)

    // Added highlightStep
    var highlightStep by mutableIntStateOf(1)

    // Is the Right thumb visible?
    var enableRightThumb by mutableStateOf(false)
    // isMouseShown() toggles this on and off at EngineActivity line 372.
    var isCursorVisible by mutableIntStateOf(0)

    // webview hook, overlay.kt line 444
    var showWebView by mutableStateOf(false)

    // Colors
    val customColor = Color(0xFF1f1e23)
    val transparentBlack = Color(alpha = 0.6f, red = 0f, green = 0f, blue = 0f)
    val transparent = Color(alpha = 0.0f, red = 0f, green = 0f, blue = 0f)
    val darkGray = Color(alpha = 0.6f, red = 0f, green = 0f, blue = 0f)
    //val lightGray = Color(alpha = 0.4f, red = 0f, green = 0f, blue = 0f)
    val gold = Color(202, 165, 96)

    var containerGlobalHeight = mutableFloatStateOf(1.0f)
    var containerGlobalWidth = mutableFloatStateOf(1.0f)

    // Add the shared states
    var memoryInfoText by mutableStateOf("")
    var batteryStatus by mutableStateOf("")
    var isMemoryInfoEnabled by mutableStateOf(false)
    var isBatteryStatusEnabled by mutableStateOf(false)
    var isLoggingEnabled by mutableStateOf(false)
    var isPerformanceHudEnabled by mutableStateOf(false)
    var logcatLevel by mutableStateOf("V")
    // this is for MainPage.kt
    var showLogCat by mutableStateOf(false)
    var isTabExpanded by mutableStateOf(false)

    var isLogcatEnabled by mutableStateOf(false)
    var activeSettingSection by mutableStateOf<String?>(null)
    val expandedSections = mutableStateListOf<String>()
    var editMode by mutableStateOf(false)
    val gridSize = mutableIntStateOf(50)
    val gridVisible = mutableStateOf(false)
    val gridAlpha = mutableFloatStateOf(0.25f)
    var configureControls by mutableStateOf(false)
    var menuAlpha by mutableFloatStateOf(1f)
    var menuColor by mutableStateOf(Color.Blue)
    var launchedActivity by mutableStateOf(false)
    
    // System path overrides for Performance HUD
    var userSetGPU by mutableStateOf("kgsl-3d0")
    var userSetTemp by mutableStateOf("thermal_zone0")
    var userSetGPUTemp by mutableStateOf("thermal_zone32")
    
    // Performance History (last 30 seconds)
    var totalMemoryMB by mutableLongStateOf(0L)
    val cpuHistory = MutableStateFlow<List<Int>>(emptyList())
    val gpuHistory = MutableStateFlow<List<Int>>(emptyList())
    val memoryHistory = MutableStateFlow<List<Long>>(emptyList()) // MB
    val cpuTempHistory = MutableStateFlow<List<Int>>(emptyList())
    val gpuTempHistory = MutableStateFlow<List<Int>>(emptyList())

    var isRadialMenuExpanded by mutableStateOf(false)
    private val _buttonStates = MutableStateFlow<Map<Int, ButtonState>>(emptyMap())
    val buttonStates: StateFlow<Map<Int, ButtonState>> get() = _buttonStates

    // Function to change all button colors and alpha
    fun changeAllButtonColorsAndAlpha(newColor: String, newAlpha: Float) {
        val updatedStates = _buttonStates.value.mapValues { (_, buttonState) ->
            buttonState.copy(color = newColor, alpha = newAlpha)
        }
        _buttonStates.value = updatedStates
        globalColor = newColor
        globalAlpha = newAlpha
    }

    fun saveImageUri(id: Int, uri: Uri) {
        _buttonStates.value = _buttonStates.value.toMutableMap().apply {
            this[id]?.let { existingState ->
                put(id, existingState.copy(uri = uri))
            }
        }
    }

    fun updateButtonState(buttonId: Int, buttonState: ButtonState) {
        _buttonStates.value = _buttonStates.value.toMutableMap().apply {
            this[buttonId] = buttonState
        }
    }

    fun removeButtonState(buttonId: Int, context: Context, containerWidth: Float, containerHeight: Float) {
        // Update the state by removing the button
        _buttonStates.value = _buttonStates.value.toMutableMap().apply { this[buttonId] = DeletedButtonState }

        //Log.d("RemoveButtonState", "Button with ID $buttonId removed from state.")

        // Delete the associated image file
        val imageExtensions = listOf("png", "gif")
        imageExtensions.forEach { extension ->
            val imageFile = File(userUI, "$buttonId.$extension")
            if (imageFile.exists()) {
                if (imageFile.delete()) {
                    addCustomLog("Image file deleted: ${imageFile.absolutePath}", textSize = 10, textColor = Color.Cyan)
                    println("Image file deleted: ${imageFile.absolutePath} from $tempCodeGroup")

                } else {
                    addCustomLog("Failed to delete image file: ${imageFile.absolutePath}", textSize = 10, textColor = Color.Red)
                    println("Failed to delete image file: ${imageFile.absolutePath} from $tempCodeGroup")
                }
            }
        }

        // Save the updated state
        saveButtonState(containerWidth, containerHeight)
        //Log.d("RemoveButtonState", "Button state saved.")
        addCustomLog("Button with ID $buttonId removed from state.", textSize = 10, textColor = Color.Cyan)
    }

    fun addButtonState(buttonState: ButtonState) {
        _buttonStates.value += (buttonState.id to buttonState)
    }

    fun saveButtonState(containerWidth: Float, containerHeight: Float) {
        val width = if (containerWidth > 1f) containerWidth else containerGlobalWidth.floatValue
        val height = if (containerHeight > 1f) containerHeight else containerGlobalHeight.floatValue

        CoroutineScope(Dispatchers.IO).launch {
            val buttonStateMap = _buttonStates.value
            val configs = buttonStateMap.values
                .filter { it.id != -1 } // Exclude DeletedButtonState
                .map { button ->
                    val type = when (button.id) {
                        99 -> "leftStick"
                        98 -> "rightStick"
                        101 -> "utility"
                        else -> "dynamic"
                    }
                    ButtonConfig(
                        type = type,
                        keyCode = button.keyCode,
                        label = if (button.id == 99) "Left Thumbstick" else "",
                        id = button.id,
                        size = button.size,
                        offsetX = button.offsetX / width,
                        offsetY = button.offsetY / height,
                        isLocked = button.isLocked,
                        blockMouse = button.blockMouse,
                        color = button.color,
                        alpha = button.alpha,
                        uri = button.uri,
                        group = button.group,
                        vibrate = button.vibrate,
                        isMouseButton = button.isMouseButton,
                        mouseButton = button.mouseButton
                    )
                }

            ButtonConfigManager.updateMultipleButtonsByTypes(
                listOf("parent", "leftStick", "rightStick", "utility", "dynamic"),
                configs
            )

            configs.forEach { config ->
                val log = "ButtonID_${config.id}(${config.size};${config.offsetX};${config.offsetY};${config.isLocked};${config.blockMouse};${config.keyCode};#${config.color};${config.alpha};${config.uri};${config.group};vibrate=${config.vibrate};mouse=${config.isMouseButton}:${config.mouseButton})"
                println("Saved to JSON: $log")
                addCustomLog("Saved to JSON: $log", textSize = 10, textColor = Color.Cyan)
            }
        }
    }

    fun loadButtonState(context: Context, containerWidth: Float, containerHeight: Float) {
        val width = if (containerWidth > 1f) containerWidth else containerGlobalWidth.floatValue
        val height = if (containerHeight > 1f) containerHeight else containerGlobalHeight.floatValue

        val configs = ButtonConfigManager.loadAllButtons()

        if (configs.isEmpty()) {
            println("No button configs found to load.")
            return
        }

        val buttonStateMap = mutableMapOf<Int, ButtonState>()
        var foundButton98 = false

        configs.forEach { config ->
            val buttonId = config.id ?: return@forEach
            if (buttonId == 98) foundButton98 = true

            // Calculate absolute offsets based on the container dimensions
            val absoluteOffsetX = (config.offsetX ?: 0f) * width
            val absoluteOffsetY = (config.offsetY ?: 0f) * height

            val buttonState = ButtonState(
                id = buttonId,
                size = config.size ?: 60f,
                offsetX = absoluteOffsetX,
                offsetY = absoluteOffsetY,
                isLocked = config.isLocked ?: false,
                blockMouse = config.blockMouse ?: false,
                keyCode = config.keyCode,
                color = config.color,
                alpha = config.alpha,
                uri = config.uri,
                group = config.group ?: 1,
                vibrate = config.vibrate ?: true,
                isMouseButton = config.isMouseButton ?: false,
                mouseButton = config.mouseButton ?: 1
            )

            println("Loaded button state: $buttonState")
            buttonStateMap[buttonId] = buttonState
        }
        _buttonStates.value = buttonStateMap
        enableRightThumb = foundButton98
    }
}

@Composable
fun KeySelectionMenu(context: Context, onKeySelected: (Int) -> Unit, usedKeys: List<Int>, containerWidth: Float, containerHeight: Float) {
    // Add A, S, D, and W to usedKeys
    val reservedKeys = listOf(
        KeyEvent.KEYCODE_A,
        KeyEvent.KEYCODE_S,
        KeyEvent.KEYCODE_D,
        KeyEvent.KEYCODE_W
    )
    val allUsedKeys = usedKeys + reservedKeys

    val letterKeys = ('A'..'Z').toList().filter { key ->
        val keyCode = KeyEvent.KEYCODE_A + key.minus('A')
        keyCode !in allUsedKeys
    }

    val numericKeys = ('0'..'9').toList().filter { key ->
        val keyCode = KeyEvent.KEYCODE_0 + key.minus('0')
        keyCode !in allUsedKeys
    }

    val fKeys = listOf(
        KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3,
        KeyEvent.KEYCODE_F4, KeyEvent.KEYCODE_F5, KeyEvent.KEYCODE_F6,
        KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8, KeyEvent.KEYCODE_F9,
        KeyEvent.KEYCODE_F10, KeyEvent.KEYCODE_F11, KeyEvent.KEYCODE_F12
    ).filter { keyCode ->
        keyCode !in allUsedKeys
    }

    val additionalKeys = listOf(
        // ----- Original list -----
        KeyEvent.KEYCODE_SHIFT_LEFT,
        KeyEvent.KEYCODE_SHIFT_RIGHT,
        KeyEvent.KEYCODE_CTRL_LEFT,
        KeyEvent.KEYCODE_CTRL_RIGHT,
        KeyEvent.KEYCODE_ALT_LEFT,
        KeyEvent.KEYCODE_ALT_RIGHT,
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_ESCAPE,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_GRAVE,
        KeyEvent.KEYCODE_TAB
    ).filter { keyCode -> keyCode !in allUsedKeys }

    val numpadKeys = listOf(
        // ----- Numpad list -----
        KeyEvent.KEYCODE_NUMPAD_0,
        KeyEvent.KEYCODE_NUMPAD_1,
        KeyEvent.KEYCODE_NUMPAD_2,
        KeyEvent.KEYCODE_NUMPAD_3,
        KeyEvent.KEYCODE_NUMPAD_4,
        KeyEvent.KEYCODE_NUMPAD_5,
        KeyEvent.KEYCODE_NUMPAD_6,
        KeyEvent.KEYCODE_NUMPAD_7,
        KeyEvent.KEYCODE_NUMPAD_8,
        KeyEvent.KEYCODE_NUMPAD_9
    ).filter { keyCode -> keyCode !in allUsedKeys }

    val virtualController = listOf(
        KeyEvent.KEYCODE_DPAD_UP,
        KeyEvent.KEYCODE_DPAD_DOWN,
        KeyEvent.KEYCODE_DPAD_LEFT,
        KeyEvent.KEYCODE_DPAD_RIGHT,

        // ----- Game-controller face buttons -----
        KeyEvent.KEYCODE_BUTTON_A,      // A (bottom)
        KeyEvent.KEYCODE_BUTTON_B,      // B (right)
        KeyEvent.KEYCODE_BUTTON_X,      // X (left)
        KeyEvent.KEYCODE_BUTTON_Y,      // Y (top)

        // ----- Bumpers & Triggers -----
        KeyEvent.KEYCODE_BUTTON_L1,     // Left bumper
        KeyEvent.KEYCODE_BUTTON_R1,     // Right bumper
        KeyEvent.KEYCODE_BUTTON_L2,     // Left trigger (analog)
        KeyEvent.KEYCODE_BUTTON_R2,     // Right trigger (analog)

        // ----- System / menu buttons -----
        KeyEvent.KEYCODE_BUTTON_START,  // Start / Menu
        KeyEvent.KEYCODE_BUTTON_SELECT, // Select / Back
        KeyEvent.KEYCODE_BUTTON_MODE,   // “Home” / “Guide” on some controllers

        // ----- Thumb-stick buttons (press) -----
        KeyEvent.KEYCODE_BUTTON_THUMBL, // Left stick press
        KeyEvent.KEYCODE_BUTTON_THUMBR, // Right stick press

        // ----- Extra generic gamepad buttons (often used for “C”, “Z”, etc.) -----
        KeyEvent.KEYCODE_BUTTON_C,
        KeyEvent.KEYCODE_BUTTON_Z,

        // ----- Numeric gamepad buttons (rarely used but part of the API) -----
        KeyEvent.KEYCODE_BUTTON_1,
        KeyEvent.KEYCODE_BUTTON_2,
        KeyEvent.KEYCODE_BUTTON_3,
        KeyEvent.KEYCODE_BUTTON_4,
        KeyEvent.KEYCODE_BUTTON_5,
        KeyEvent.KEYCODE_BUTTON_6,
        KeyEvent.KEYCODE_BUTTON_7,
        KeyEvent.KEYCODE_BUTTON_8,
        KeyEvent.KEYCODE_BUTTON_9,
        KeyEvent.KEYCODE_BUTTON_10,
        KeyEvent.KEYCODE_BUTTON_11,
        KeyEvent.KEYCODE_BUTTON_12,
        KeyEvent.KEYCODE_BUTTON_13,
        KeyEvent.KEYCODE_BUTTON_14,
        KeyEvent.KEYCODE_BUTTON_15,
        KeyEvent.KEYCODE_BUTTON_16
    ).filter { keyCode -> keyCode !in allUsedKeys }

    var showDialog by remember { mutableStateOf(false) }
    IconButton(onClick = {
        showDialog = true
    }) {
        Icon(
            Icons.Default.Add,
            contentDescription = "Add Button",
            modifier = Modifier.size(36.dp), // Adjust the icon size here
            tint = Color.Red // Change the color here
        )
    }

    if (showDialog) {
        Dialog(onDismissRequest = { showDialog = false }) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = Color.Black.copy(alpha = 0.7f)
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .widthIn(min = 300.dp, max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = stringResource(R.string.select_a_numeric_key),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp, top = 16.dp)
                    )
                    numericKeys.chunked(5).forEach { rowKeys ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowKeys.forEach { key ->
                                val keyCode = KeyEvent.KEYCODE_0 + key.minus('0')
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.LightGray, shape = CircleShape)
                                        .clickable {
                                            onKeySelected(keyCode)
                                            showDialog = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color.White, thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = stringResource(R.string.select_a_key),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    letterKeys.chunked(5).forEach { rowKeys ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowKeys.forEach { key ->
                                val keyCode = KeyEvent.KEYCODE_A + key.minus('A')
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.LightGray, shape = CircleShape)
                                        .clickable {
                                            onKeySelected(keyCode)
                                            showDialog = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key.toString(),
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color.White, thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = stringResource(R.string.select_a_function_key),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    fKeys.chunked(5).forEach { rowKeys ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowKeys.forEach { keyCode ->
                                val key = "F${keyCode - KeyEvent.KEYCODE_F1 + 1}"
                                Log.d("DisplayKey", "Processing key: $key with keyCode: $keyCode") // Log each key
                                addCustomLog("Processing key: $key with keyCode: $keyCode", textSize = 10, textColor = Color.Green)
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.LightGray, shape = CircleShape)
                                        .clickable {
                                            Log.d(
                                                "FKeyClick",
                                                "Clicked on key: $key with keyCode: $keyCode"
                                            )
                                            addCustomLog(
                                                "FKeyClick\", \"Clicked on key: $key with keyCode: $keyCode",
                                                textSize = 10,
                                                textColor = Color.Green
                                            )
                                            onKeySelected(keyCode)
                                            showDialog = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color.White, thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = stringResource(R.string.select_a_unique_key_the_shift_and_alt_keys_toggle),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    additionalKeys.chunked(5).forEach { rowKeys ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowKeys.forEach { keyCode ->
                                val key = when (keyCode) {
                                    KeyEvent.KEYCODE_SHIFT_LEFT -> "Shift-L"
                                    KeyEvent.KEYCODE_SHIFT_RIGHT -> "Shift-R"
                                    KeyEvent.KEYCODE_CTRL_LEFT -> "Ctrl-L"
                                    KeyEvent.KEYCODE_CTRL_RIGHT -> "Ctrl-R"
                                    KeyEvent.KEYCODE_ALT_LEFT -> "Alt-L"
                                    KeyEvent.KEYCODE_ALT_RIGHT -> "Alt-R"
                                    KeyEvent.KEYCODE_SPACE -> "Space"
                                    KeyEvent.KEYCODE_ESCAPE -> "Escape"
                                    KeyEvent.KEYCODE_ENTER -> "Enter"
                                    KeyEvent.KEYCODE_GRAVE -> "`"
                                    KeyEvent.KEYCODE_TAB -> "Tab"
                                    else -> keyCode.toString()
                                }
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.LightGray, shape = CircleShape)
                                        .clickable {
                                            onKeySelected(keyCode)
                                            showDialog = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color.White, thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = stringResource(R.string.select_a_numpad_button),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    numpadKeys.chunked(5).forEach { rowKeys ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowKeys.forEach { keyCode ->
                                val key = when (keyCode) {
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
                                    else -> keyCode.toString()
                                }
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.LightGray, shape = CircleShape)
                                        .clickable {
                                            onKeySelected(keyCode)
                                            showDialog = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color.White, thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = stringResource(R.string.select_a_controller_button),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    virtualController.chunked(5).forEach { rowKeys ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowKeys.forEach { keyCode ->
                                val key = when (keyCode) {
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
                                    else -> keyCode.toString()
                                }
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .background(Color.LightGray, shape = CircleShape)
                                        .clickable {
                                            onKeySelected(keyCode)
                                            showDialog = false
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = key,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.Black
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = Color.White, thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))
                    Text(
                        text = stringResource(R.string.enable_right_thumbstick),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    Switch(
                        checked = enableRightThumb,
                        onCheckedChange = { isChecked ->
                            enableRightThumb = isChecked
                            if (isChecked) {
                                // Logic to add ButtonID_98
                                val newButtonState = ButtonState(
                                    id = 98,
                                    size = 160f,
                                    offsetX = 200f,
                                    offsetY = 200f,
                                    isLocked = false,
                                    blockMouse = false,
                                    keyCode = 98,
                                    color = "aafdfffe",
                                    alpha = 0.25f,
                                    uri = null,
                                    group = 1,
                                    vibrate = false
                                )

                                // Update UIStateManager with the new button state
                                UIStateManager.updateButtonState(newButtonState.id, newButtonState)
                            } else {
                                // Logic to remove ButtonID_98
                                UIStateManager.removeButtonState(98, context, containerWidth, containerHeight)
                            }

                            // Save the updated button states to the file
                            UIStateManager.saveButtonState(containerWidth, containerHeight)

                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Green,
                            uncheckedThumbColor = Color.Gray
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = {
                            showDialog = false
                        },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            }
        }
    }
}

@Composable
fun DynamicButtonManager(
    context: Context,
    onNewButtonAdded: (ButtonState) -> Unit,
    blurRadius: Dp = 6.dp,
    shadowColor: Color = Color.White.copy(alpha = 0.6f),
    scaleFactor:Float = 1.2f,
) {
    val buttonStates by UIStateManager.buttonStates.collectAsState()
    val matchIconColorChecked by GameFilesPreferences.loadMatchIconColorState(context).collectAsState(initial = false)
    val tutorial by GameFilesPreferences.getTutorial(context).collectAsState(initial = false)
    val iconGlowChecked by GameFilesPreferences.loadIconGlow(context).collectAsState(initial = true)
    val offsetX by animateDpAsState(
        targetValue = if (UIStateManager.editMode) 2.dp else 0.dp,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        finishedListener = {
            if (!UIStateManager.editMode) 0.dp else it
        }, label = ""
    )

    val infiniteTransition = rememberInfiniteTransition()

    // Animate alpha for flashing effect
    val alpha = infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 500, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    val iconTint = if (configureControls && tutorial && highlightStep == 2) {
        Color.Yellow
    } else if (matchIconColorChecked) {
        menuColor.copy(alpha = menuAlpha)
    } else {
        Color.Black
    }


    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                UIStateManager.editMode = !UIStateManager.editMode
                highlightStep++
            }) {
                if (iconGlowChecked) {
                    Icon(
                        imageVector = Icons.Default.Build,
                        contentDescription = "Button Menu",
                        modifier = Modifier
                            .size(30.dp)
                            .scale(scaleFactor)
                            .blur(blurRadius),
                        tint = shadowColor,
                    )
                }
                Icon(
                    imageVector = Icons.Default.Build,
                    contentDescription = "Button Menu",
                    modifier = Modifier
                        .offset(x = if (UIStateManager.editMode) offsetX else 0.dp)
                        .alpha(if (configureControls && tutorial && highlightStep == 2) alpha.value else 1f),
                    tint = iconTint
                )
            }
        }
        if (UIStateManager.editMode) {
            KeySelectionMenu(
                onKeySelected = { keyCode ->
                    // Generate a new ID for the button
                    val existingIds = UIStateManager.buttonStates.value.keys.filter { it !in listOf(98, 99) }
                    val newId = if (configureControls && tutorial) {
                        999
                    } else {
                        (1..Int.MAX_VALUE).first { it !in existingIds }
                    }

                    // Create a new ButtonState
                    val newButtonState = ButtonState(
                        id = newId,
                        size = 60f,
                        offsetX = 200f,
                        offsetY = 200f,
                        isLocked = false,
                        blockMouse = false,
                        keyCode = keyCode,
                        color = "000000FF",
                        alpha = 0.25f,
                        uri = null,
                        group = buttonsGroup,
                        vibrate = true,
                        isMouseButton = false,
                        mouseButton = 1
                    )

                    onNewButtonAdded(newButtonState)

                    highlightStep++

                    // Update the UIStateManager with the new button state
                    UIStateManager.updateButtonState(newButtonState.id, newButtonState)
                    addCustomLog("Saving with containerWidth=${containerGlobalWidth.value}, containerHeight=${containerGlobalHeight.value}", textSize = 10, textColor = Color.Yellow)

                    UIStateManager.saveButtonState(containerGlobalWidth.value, containerGlobalHeight.value)

                },
                usedKeys = buttonStates.values.map { it.keyCode },
                context = context,
                containerWidth = containerGlobalWidth.value,
                containerHeight = containerGlobalHeight.value
            )
        }
    }
}
