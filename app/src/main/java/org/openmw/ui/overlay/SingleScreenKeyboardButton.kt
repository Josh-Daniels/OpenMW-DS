package org.openmw.ui.overlay

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import org.openmw.R
import org.openmw.ui.controls.UIKeyboard
import org.openmw.utils.GameFilesPreferences

/**
 * SINGLE SCREEN DEVICE only: a lone keyboard button on the game screen.
 *
 * **Replaces the whole Alpha3 gear-and-arrow cluster** ([OverlayUI]), which this profile used to
 * enable in full purely to get at one of its icons. Everything else that cluster carries — movement
 * buttons, the mouse menu, edit mode, the button manager, the world-map WebView — is legacy Alpha3
 * surface that a single-screen player never asked for and that nothing else in this app exposes any
 * more. This is the one control that profile actually needs.
 *
 * **The console needs no control of its own, and this was checked rather than assumed.** The Alpha3
 * overlay has no console button; its only console route is the virtual keyboard's backtick key
 * (`Keyboard.kt`: `"`" -> sendKeyEvent(KeyEvent.KEYCODE_GRAVE)`, which the engine matches against
 * `A_Console`, default `SDL_SCANCODE_GRAVE`). So raising the keyboard IS raising console access,
 * and a second icon would only duplicate a key that is already on the thing this button opens. The
 * companion's DS keyboard is built the same way (`KbKey("`") { openNativeConsole() }`); its separate
 * "Open Console" row in Developer Tools is a convenience on a screen with room for one, not a
 * distinct mechanism.
 *
 * **The action is copied verbatim from the cluster's keyboard icon**, including the
 * `virtualKeyboard` preference branch — on, the app's own [UIKeyboard] overlay; off, the system IME
 * via [showKeyboard]. `VirtualKeyboard()` itself is composed by `EngineActivity` outside the
 * cluster and always was, so this button only has to flip the flag; nothing about the keyboard
 * moved.
 *
 * **Deliberately NOT gated on `isUIHidden` or `hudVisible`**, for the reasons the call site in
 * `EngineActivity` spells out at length: `isUIHidden` is what made the old overlay unrecoverable
 * (it hides the control while showing what the control turns off), and `hudVisible` is pushed by a
 * native log sink this profile never installs, so it would be frozen at its initial value. The
 * launcher toggle is the only gate, which is what makes it a real off switch.
 *
 * Placed on the same [GameFilesPreferences.getMenuCorner] the cluster used, so it appears where the
 * gear used to and inherits a corner the player had already chosen. Note that with the cluster gone
 * this profile has no way to CHANGE that corner any more; top-left is the default and the corner
 * cycler lived inside the cluster. That is accepted rather than overlooked — adding a
 * long-press-to-move would put drag state and a persisted position back into the one place this
 * change exists to keep small.
 */
@Composable
fun SingleScreenKeyboardButton(
    context: Context,
    virtualKeyboard: Boolean,
) {
    val menuCorner by GameFilesPreferences.getMenuCorner(context).collectAsState(initial = 1)
    val alignment = when (menuCorner) {
        0 -> Alignment.TopEnd
        1 -> Alignment.TopStart
        2 -> Alignment.BottomEnd
        3 -> Alignment.BottomStart
        else -> Alignment.TopStart
    }

    Box(modifier = Modifier.fillMaxSize()) {
        IconButton(
            onClick = {
                if (virtualKeyboard) {
                    UIKeyboard.showVKB = !UIKeyboard.showVKB
                } else {
                    (context as? Activity)?.let { showKeyboard(it) }
                }
            },
            modifier = Modifier
                .align(alignment)
                .padding(8.dp)
                // Its own backdrop rather than the cluster's icon-glow/tint preferences: those are
                // Alpha3 theming this profile no longer surfaces a way to set. A translucent disc
                // keeps the glyph readable over both a bright exterior and a black cave, which a
                // bare tinted icon does not.
                .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                .size(44.dp)
        ) {
            Icon(
                painter = painterResource(
                    R.drawable.keyboard_24dp_e8eaed_fill0_wght400_grad0_opsz24
                ),
                contentDescription = "Show keyboard",
                tint = Color.White.copy(alpha = 0.9f),
                modifier = Modifier.size(26.dp)
            )
        }
    }
}
