package org.openmw.utils

import android.util.Log
import android.view.KeyEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.libsdl.app.SDLActivity.nativeCommitText
import org.openmw.ui.controls.UIStateManager.menuAlpha
import org.openmw.ui.controls.UIStateManager.menuColor

// Declare a lambda for updating console output and handling key events
lateinit var updateConsoleOutput: (String) -> Unit
lateinit var sendKeyEvent: (Int) -> Unit

@Composable
fun TravelMenuPopup(
    onKeyEvent: (Int) -> Unit,
    blurRadius: Dp = 6.dp,
    shadowColor: Color = Color.White.copy(alpha = 0.6f),
    scaleFactor:Float = 1.2f,
) {
    var popupVisible by remember { mutableStateOf(false) }
    var dropdownExpanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    var selectedText by remember { mutableStateOf("Travel to destination ") }
    val matchIconColorChecked by GameFilesPreferences.loadMatchIconColorState(context).collectAsState(initial = false)
    val iconGlowChecked by GameFilesPreferences.loadIconGlow(context).collectAsState(initial = true)
    val options = listOf(
        "Balmora", "Suran", "Vivec", "Ald'ruhn", "Caldera", "Ebonheart", "Ghostgate", "Gnisis",
        "Maar Gan", "Molag Mar", "Pelagiad", "Tel Aruhn", "Tel Branora", "Tel Mora",
        "Ald Velothi", "Dagon Fel", "Gnaar Mok", "Hla Oad", "Khuul", "Seyda Neen",
        "Tel Fyr", "Tel Vos", "Vos"
    )

    Column(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
        IconButton(onClick = { popupVisible = true }) {
            if (iconGlowChecked) {
                Icon(
                    imageVector = Icons.Filled.Menu,
                    contentDescription = "Open Travel Menu",
                    modifier = Modifier
                        .size(30.dp)
                        .scale(scaleFactor)
                        .blur(blurRadius),
                    tint = shadowColor
                )
            }
            Icon(
                imageVector = Icons.Filled.Menu,
                contentDescription = "Open Travel Menu",
                modifier = Modifier.size(24.dp),
                tint = if (matchIconColorChecked) {
                    menuColor.copy(alpha = menuAlpha)
                } else {
                    Color.Black
                }
            )
        }
        if (popupVisible) {
            Popup(
                onDismissRequest = {
                    popupVisible = false
                    sendKeyEvent(KeyEvent.KEYCODE_ESCAPE) },
                properties = PopupProperties(focusable = true)
            ) {
                Column(
                    modifier = Modifier
                        .background(Color.Black)
                        .padding(16.dp)
                        .width(300.dp)
                ) {
                    // Dropdown Menu Button
                    Box(modifier = Modifier.clickable { dropdownExpanded = true }) {
                        Row(modifier = Modifier.padding(8.dp).background(Color.Black)) {
                            Text(
                                text = selectedText,
                                color = Color.White,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start
                            )
                            Icon(
                                imageVector = if (dropdownExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                                contentDescription = null,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                        }
                    }

                    if (dropdownExpanded) {
                        DropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            options.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(text = option, color = Color.White) },
                                    modifier = Modifier.padding(8.dp).background(Color.Black),
                                    onClick = {
                                        selectedText = option
                                        dropdownExpanded = false
                                        val command = when (option) {
                                            "Balmora" -> "coc balmora"
                                            "Suran" -> "coc suran"
                                            "Vivec" -> "coc vivec"
                                            "Ald'ruhn" -> "coc aldrhuun"
                                            "Caldera" -> "coc caldera"
                                            "Ebonheart" -> "coc ebonheart"
                                            "Ghostgate" -> "coc ghostgate"
                                            "Gnisis" -> "coc gnisis"
                                            "Maar Gan" -> "coc maar_gan"
                                            "Molag Mar" -> "coc molag_mar"
                                            "Pelagiad" -> "coc pelagiad"
                                            "Tel Aruhn" -> "coc tel_aruhn"
                                            "Tel Branora" -> "coc tel_branora"
                                            "Tel Mora" -> "coc tel_mora"
                                            "Ald Velothi" -> "coc ald_velothi"
                                            "Dagon Fel" -> "coc dagon_fel"
                                            "Gnaar Mok" -> "coc gnaar_mok"
                                            "Hla Oad" -> "coc hla_oad"
                                            "Khuul" -> "coc khuul"
                                            "Seyda Neen" -> "coc seyda_neen"
                                            "Tel Fyr" -> "coc tel_fyr"
                                            "Tel Vos" -> "coc tel_vos"
                                            "Vos" -> "coc vos"
                                            else -> ""
                                        }
                                        Log.d("Overlay", "Run Commands with $command")
                                        automateCommands(command)
                                    }
                                )
                            }
                        }
                    }

                    // Additional Buttons (Placeholders)
                    Spacer(modifier = Modifier.height(1.dp))
                    Button(onClick = { automateCommands("tcl") }) {
                        Text("Enable / Disable Clipping")
                    }
                    Button(onClick = { automateCommands("tgm") }) {
                        Text("Enable / Disable Godmode")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { automateCommands("player->setspeed 400") }) {
                        Text("Speed Increase")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { automateCommands("player->setspeed 100") }) {
                        Text("Default Speed")
                    }
                }
            }
        }
    }
}

fun automateCommands(command: String) {
    CoroutineScope(Dispatchers.Main).launch {
        // Send the grave key first
        sendKeyEvent(KeyEvent.KEYCODE_GRAVE)
        delay(100) // Delay for 100 ms

        // Send command
        nativeCommitText(command, 0)
        delay(100) // Delay for 100 ms

        // Send the enter key
        sendKeyEvent(KeyEvent.KEYCODE_ENTER)
        //delay(1000) // Delay for 1 second

        // Send the escape key
        sendKeyEvent(KeyEvent.KEYCODE_ESCAPE)
    }
}

