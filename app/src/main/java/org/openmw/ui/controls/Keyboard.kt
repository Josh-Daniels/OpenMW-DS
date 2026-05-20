package org.openmw.ui.controls

import android.view.KeyCharacterMap
import android.view.KeyEvent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import org.libsdl.app.SDLActivity.nativeCommitText
import org.libsdl.app.SDLActivity.onNativeKeyDown
import org.libsdl.app.SDLActivity.onNativeKeyUp
import org.openmw.R
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.TranslateText
import org.openmw.utils.sendKeyEvent
import kotlin.math.roundToInt
import kotlin.random.Random

object UIKeyboard {
    var showVKB by mutableStateOf(false)
    var isShiftPressed by mutableStateOf(false)
    var isCapsLock by mutableStateOf(false)
    var themeMode by mutableStateOf("lightMode")
    var backLight by mutableFloatStateOf(1f)
}

fun cycleBackLight() {
    UIKeyboard.backLight = when (UIKeyboard.backLight) {
        1f -> 0.75f
        0.75f -> 0.50f
        0.50f -> 0.25f
        0.25f -> 1f
        else -> 1f // fallback for unexpected values
    }
}

fun cycleColorTheme() {
    UIKeyboard.themeMode = when (UIKeyboard.themeMode) {
        "darkMode" -> "lightMode"
        "lightMode" -> "RGB"
        "RGB" -> "gold"
        "gold" -> "darkMode"
        else -> "darkMode" // fallback
    }
}

@Composable
fun VirtualKeyboard() {
    var functionKEYS by remember { mutableStateOf(false) }
    var numROWS by remember { mutableIntStateOf(if (functionKEYS) 6 else 5) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var showSettingsDialog by remember { mutableStateOf(false) }
    var boxHeight by remember { mutableFloatStateOf(numROWS* 40f) }
    var boxWidth by remember { mutableFloatStateOf(boxHeight * 3.5f) }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .then(
                Modifier.pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { },
                        onDrag = { _, dragAmount ->
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        },
                        onDragEnd = { }
                    )
                }
            )
    ) {
        // Define the colors for the RGB effect
        val colors = listOf(
            Color.Red, Color.Green, Color.Blue
        )
        // Animation state
        var colorIndex by remember { mutableIntStateOf(0) }
        var nextColorIndex by remember { mutableIntStateOf(1) }
        var fraction by remember { mutableFloatStateOf(0f) }

        // Launch a coroutine to gradually change the color fraction
        LaunchedEffect(Unit) {
            while (true) {
                delay(20)  // Adjust delay for smoother transition
                fraction += 0.01f
                if (fraction >= 1f) {
                    fraction = 0f
                    colorIndex = nextColorIndex
                    nextColorIndex = (nextColorIndex + 1) % colors.size
                }
            }
        }

        // Custom lerp function for Color
        fun customLerp(start: Color, end: Color, fraction: Float): Color {
            return androidx.compose.ui.graphics.lerp(start, end, fraction.coerceIn(0f, 1f))
        }

        // Function to get the current color based on fraction
        fun getCurrentColor(offset: Float): Color {
            return customLerp(colors[colorIndex], colors[nextColorIndex], fraction + offset)
        }

        // Function to create rows of boxes with sizes
        @Composable
        fun createRow(
            charsNormal: List<String>,
            sizes: List<Modifier>,
            modifier: Modifier = Modifier
        ) {
            val context = LocalContext.current
            val translationChecked by GameFilesPreferences.loadTranslationState(context).collectAsState(initial = false)
            val mKeyCharacterMap = KeyCharacterMap.load(KeyCharacterMap.VIRTUAL_KEYBOARD)

            Box(
                modifier = modifier
                    .width((boxHeight.dp / numROWS) * 17.5f)
            ) {
                Row {
                    charsNormal.zip(sizes).forEach { (char, sizeModifier) ->
                        val keyEvent = mKeyCharacterMap.getEvents(char.toCharArray())
                        var isPressed by remember { mutableStateOf(false) }
                        val offset = remember { Random.nextFloat() * 0.5f } // Cache random offset
                        val animatedColor by animateColorAsState(
                            targetValue = getCurrentColor(offset)
                        )

                        Box(
                            modifier = sizeModifier
                                .background(Color.Black.copy(alpha = 0.25f))
                                .border(
                                    width = 2.dp,
                                    color = when (UIKeyboard.themeMode) {
                                        "darkMode" -> Color.Black.copy(alpha = UIKeyboard.backLight)
                                        "lightMode" -> Color.White.copy(alpha = UIKeyboard.backLight)
                                        "RGB" -> animatedColor.copy(alpha = UIKeyboard.backLight)
                                        "gold" -> Color(
                                            202,
                                            165,
                                            96
                                        ).copy(alpha = UIKeyboard.backLight)

                                        else -> Color.LightGray.copy(alpha = UIKeyboard.backLight)
                                    }
                                )
                                .pointerInput(char) {
                                    awaitPointerEventScope {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val down = event.changes.firstOrNull()?.pressed == true

                                            if (down && !isPressed) {
                                                isPressed = true
                                                when (char) {
                                                    "ALPHA" -> cycleBackLight()
                                                    "␣" -> sendKeyEvent(KeyEvent.KEYCODE_SPACE)
                                                    "`" -> sendKeyEvent(KeyEvent.KEYCODE_GRAVE)
                                                    "TAB" -> {
                                                        sendKeyEvent(KeyEvent.KEYCODE_TAB)
                                                        onNativeKeyDown(KeyEvent.KEYCODE_TAB)
                                                    }

                                                    "⌫" -> sendKeyEvent(KeyEvent.KEYCODE_DEL)
                                                    "⏎" -> sendKeyEvent(KeyEvent.KEYCODE_ENTER)
                                                    "↑" -> sendKeyEvent(KeyEvent.KEYCODE_DPAD_UP)
                                                    "↓" -> sendKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN)
                                                    "→" -> sendKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT)
                                                    "←" -> sendKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT)
                                                    "ESC" -> sendKeyEvent(KeyEvent.KEYCODE_ESCAPE)
                                                    "F1" -> sendKeyEvent(KeyEvent.KEYCODE_F1)
                                                    "F2" -> sendKeyEvent(KeyEvent.KEYCODE_F2)
                                                    "F3" -> sendKeyEvent(KeyEvent.KEYCODE_F3)
                                                    "F4" -> sendKeyEvent(KeyEvent.KEYCODE_F4)
                                                    "F5" -> sendKeyEvent(KeyEvent.KEYCODE_F5)
                                                    "F6" -> sendKeyEvent(KeyEvent.KEYCODE_F6)
                                                    "F7" -> sendKeyEvent(KeyEvent.KEYCODE_F7)
                                                    "F8" -> sendKeyEvent(KeyEvent.KEYCODE_F8)
                                                    "F9" -> sendKeyEvent(KeyEvent.KEYCODE_F9)
                                                    "F10" -> sendKeyEvent(KeyEvent.KEYCODE_F10)
                                                    "F11" -> sendKeyEvent(KeyEvent.KEYCODE_F11)
                                                    "F12" -> sendKeyEvent(KeyEvent.KEYCODE_F12)
                                                    "CAPS" -> {
                                                        UIKeyboard.isCapsLock =
                                                            !UIKeyboard.isCapsLock
                                                        UIKeyboard.isShiftPressed =
                                                            UIKeyboard.isCapsLock
                                                    }

                                                    "Fn" -> functionKEYS = !functionKEYS
                                                    "⚙" -> cycleColorTheme() //showSettingsDialog = true

                                                    else -> keyEvent?.firstOrNull()?.let {
                                                        onNativeKeyDown(it.keyCode)
                                                        nativeCommitText(char, 0)
                                                    }
                                                }
                                            } else if (!down && isPressed) {
                                                isPressed = false
                                                keyEvent?.firstOrNull()?.let {
                                                    onNativeKeyUp(it.keyCode)
                                                    when (char) {
                                                        "TAB" -> {
                                                            onNativeKeyUp(KeyEvent.KEYCODE_TAB)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                .then(
                                    if (char == "SCALE") {
                                        Modifier.pointerInput(Unit) {
                                            detectDragGestures(
                                                onDragStart = { },
                                                onDrag = { change, dragAmount ->
                                                    change.consume()
                                                    boxWidth += dragAmount.x
                                                    boxHeight += dragAmount.y
                                                },
                                                onDragEnd = { }
                                            )
                                        }
                                    } else {
                                        Modifier
                                    }
                                )
                        ) {
                            var translatedText by remember { mutableStateOf("") }

                            if (translationChecked) {
                                TranslateText(
                                    context = context,
                                    inputText = char, // Pass modValue.value here
                                    onTranslationResult = { result ->
                                        translatedText =
                                            result // Update the state with the translated text
                                    }
                                )

                            }
                            Text(
                                text = if (translationChecked) translatedText else char,
                                color = when (UIKeyboard.themeMode) {
                                    "darkMode" -> if (UIKeyboard.backLight == 1f) Color.Black else Color.Black.copy(alpha = UIKeyboard.backLight)
                                    "lightMode" -> if (UIKeyboard.backLight == 1f) Color.White else Color.White.copy(alpha = UIKeyboard.backLight)
                                    "RGB" -> if (UIKeyboard.backLight == 1f) animatedColor else animatedColor.copy(alpha = UIKeyboard.backLight)
                                    "gold" -> if (UIKeyboard.backLight == 1f) Color(202, 165, 96) else Color(202, 165, 96).copy(alpha = UIKeyboard.backLight)
                                    else -> if (UIKeyboard.backLight == 1f) Color.LightGray else Color.LightGray.copy(alpha = UIKeyboard.backLight)
                                },
                                fontSize = 16.sp,  // Set the text size
                                fontWeight = FontWeight.Bold,  // Set the text style to bold
                                modifier = Modifier.align(Alignment.Center)  // Center the text in the box
                            )
                        }
                    }
                }
            }
        }

        val fnKEYS =  listOf("ESC", " ", "F1", "F2", "F3", "F4", " ", "F5", "F6", "F7", "F8", " ", "F9", "F10", "F11", "F12")
        val fnKEYSSizes = listOf(
            Modifier
                .padding(all = 2.dp)
                .height(boxHeight.dp / numROWS)
                .width((boxHeight.dp / numROWS) * 1.5f)
        ) + listOf(
            Modifier
                .padding(all = 2.dp)
                .height(boxHeight.dp / numROWS)
                .alpha(0.0f)
                .weight(1f)
        ) + List(4) {
            Modifier
                .padding(all = 2.dp)
                .size(boxHeight.dp / numROWS)
        } + listOf(
            Modifier
                .padding(all = 2.dp)
                .height(boxHeight.dp / numROWS)
                .width((boxHeight.dp / numROWS) / 2)
                .alpha(0.0f)
        ) + List(4) {
            Modifier
                .padding(all = 2.dp)
                .size(boxHeight.dp / numROWS)
        } + listOf(
            Modifier
                .padding(all = 2.dp)
                .height(boxHeight.dp / numROWS)
                .width((boxHeight.dp / numROWS) / 2)
                .alpha(0.0f)
        ) + List(4) {
            Modifier
                .padding(all = 2.dp)
                .size(boxHeight.dp / numROWS)
        }

        if (functionKEYS){
            createRow(
                charsNormal = fnKEYS,
                sizes = fnKEYSSizes
            )
        }

        val topRowNormal = when {
            UIKeyboard.isShiftPressed -> listOf("~", "!", "@", "#", "$", "%", "^", "&", "*", "(", ")", "_", "+", "⌫")
            else -> listOf("`", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0", "-", "=", "⌫")
        }
        val topRowSizes = List(13) {
            Modifier
                .padding(all = 2.dp)
                .size(boxHeight.dp / numROWS)
        } + listOf(
            Modifier
                .padding(all = 2.dp)
                .height(boxHeight.dp / numROWS)
                .weight(1f)
        )
        createRow(
            charsNormal = topRowNormal,
            sizes = topRowSizes
        )

        val secondRowNormal = when {
            UIKeyboard.isShiftPressed -> listOf("TAB", "Q", "W", "E", "R", "T", "Y", "U", "I", "O", "P", "{", "}", "|")
            else -> listOf("TAB", "q", "w", "e", "r", "t", "y", "u", "i", "o", "p", "[", "]", "\\")
        }
        val secondRowSizes = listOf(
            // Tab Key
            Modifier
                .padding(all = 2.dp)
                .height(boxHeight.dp / numROWS)
                .width((boxHeight.dp / numROWS) * 1.125f),
        ) + List(12) {
            Modifier
                .padding(all = 2.dp)
                .size(boxHeight.dp / numROWS)
        } + listOf(
            Modifier
                .padding(all = 2.dp)
                .height(boxHeight.dp / numROWS)
                .weight(1f)
        )
        createRow(
            charsNormal = secondRowNormal,
            sizes = secondRowSizes
        )

        val thirdRowNormal = when {
            UIKeyboard.isShiftPressed -> listOf("CAPS", "A", "S", "D", "F", "G", "H", "J", "K", "L", ":", "\"", "⏎")
            else -> listOf("CAPS", "a", "s", "d", "f", "g", "h", "j", "k", "l", ";", "'", "⏎")
        }
        val thirdRowSizes = listOf(
            Modifier
                .padding(all = 2.dp)
                .height(boxHeight.dp / numROWS)
                .width((boxHeight.dp / numROWS) * 1.5f)
        ) + List(11) {
            Modifier
                .padding(all = 2.dp)
                .size(boxHeight.dp / numROWS)
        } + listOf(
            Modifier
                .padding(all = 2.dp)
                .height(boxHeight.dp / numROWS)
                .weight(1f)
        )
        createRow(
            charsNormal = thirdRowNormal,
            sizes = thirdRowSizes
        )

        val fourthRowNormal = when {
            UIKeyboard.isShiftPressed -> listOf("ALPHA", "Z", "X", "C", "V", "B", "N", "M", "<", ">", "?", "", "↑", "")
            else -> listOf("ALPHA", "z", "x", "c", "v", "b", "n", "m", ",", ".", "/", "", "↑", "")
        }

        val fourthRowSizes = listOf(
            Modifier
                .padding(all = 2.dp)
                .height(boxHeight.dp / numROWS)
                .width((boxHeight.dp / numROWS) * 2f)
        ) + List(10) {
            Modifier
                .padding(all = 2.dp)
                .size(boxHeight.dp / numROWS)
        } + listOf(
            Modifier
                .padding(all = 2.dp)
                .alpha(0.0f)
                .weight(1f)
        ) + listOf(
            Modifier
                .padding(all = 2.dp)
                .size(boxHeight.dp / numROWS)
        ) + listOf(
            Modifier
                .padding(all = 2.dp)
                .alpha(0.0f)
                .size(boxHeight.dp / numROWS)
        )
        createRow(
            charsNormal = fourthRowNormal,
            sizes = fourthRowSizes
        )

        val fifthRowChars = listOf("⚙", "Fn", "␣", "SCALE", "←", "↓", "→")
        val fifthRowSizes = listOf(
            Modifier
                .padding(all = 2.dp)
                .height(boxHeight.dp / numROWS)
                .width((boxHeight.dp / numROWS) * 2f),
            // Spacer
            Modifier
                .padding(all = 2.dp)
                .height(boxHeight.dp / numROWS)
                //.alpha(0.0f)
                .width((boxHeight.dp / numROWS) * 2f),
            // Space bar
            Modifier
                .padding(all = 2.dp)
                .height(boxHeight.dp / numROWS)
                .width((boxHeight.dp / numROWS) * 7.5f),
            // Spacer
            Modifier
                .padding(all = 2.dp)
                .height(boxHeight.dp / numROWS)
                //.alpha(0.0f)
                .weight(1f)
        ) + List(3) {
            Modifier
                .padding(all = 2.dp)
                .size(boxHeight.dp / numROWS)
        }

        createRow(
            charsNormal = fifthRowChars,
            sizes = fifthRowSizes
        )

        // Settings Dialog
        if (showSettingsDialog) {
            Dialog(onDismissRequest = { showSettingsDialog = false }) {
                Surface(
                    modifier = Modifier
                        .size(300.dp),
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF2C2C2E)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            stringResource(R.string.keyboard_settings),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White)

                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = { expanded = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFF3A3A3C),
                                contentColor = Color.White
                            )
                        ) {
                            Text(stringResource(R.string.select_theme))
                        }
                        Spacer(Modifier.weight(1f))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {

                            DropdownMenu(
                                expanded = expanded,
                                onDismissRequest = { expanded = false },
                                modifier = Modifier
                                    .border(1.dp, Color.Black)
                                    .background(Color.Black)
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.lightmode), color = Color.White) },
                                    onClick = {
                                        UIKeyboard.themeMode = "lightMode"
                                        expanded = false
                                    })
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.darkmode), color = Color.White) },
                                    onClick = {
                                        UIKeyboard.themeMode = "darkMode"
                                        expanded = false
                                    })
                                DropdownMenuItem(
                                    text = { Text("RGB", color = Color.White) },
                                    onClick = {
                                        UIKeyboard.themeMode = "RGB"
                                        expanded = false
                                    })
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.gold), color = Color.White) },
                                    onClick = {
                                        UIKeyboard.themeMode = "gold"
                                        expanded = false
                                    })
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        Button(
                            onClick = { showSettingsDialog = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.Blue,
                                contentColor = Color.White
                            )
                        ) {
                            Text(stringResource(R.string.close))
                        }
                    }
                }
            }
        }
    }
}

