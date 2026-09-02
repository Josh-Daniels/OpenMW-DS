package org.openmw.utils

import android.app.Activity
import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.Display
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import org.openmw.EngineActivity
import org.openmw.MyApp.Companion.app

fun Context.Toast(msg: String, isLong: Boolean = false) {
    CoroutineScope(Dispatchers.Main).launch {
        Toast.makeText(this@Toast.applicationContext, msg, if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}

fun MToast(msg: String, isLong: Boolean = false) {
    CoroutineScope(Dispatchers.Main).launch {
        Toast.makeText(app, msg, if (isLong) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).show()
    }
}

fun stringRes(resId: Int) = app.getString(resId)

/**
 * Launch options that pin an Activity to the LAUNCHER's screen ([Display.DEFAULT_DISPLAY]).
 *
 * Without an explicit launch display, Android starts an Activity on whichever display the
 * caller is currently showing on. So anything triggered from the bottom (companion) screen
 * drags the launcher — and then the game — onto display 4. That breaks the game badly:
 * EngineActivity hosts the SDL GL surface as its own activity window, while
 * `startCompanionScreen()` picks its display from a GLOBAL DisplayManager query that always
 * resolves to display 4 regardless of where EngineActivity landed. Both then occupy display
 * 4 and the companion Presentation (TYPE_PRESENTATION) layers over the game, leaving the top
 * screen empty.
 *
 * **This is the LAUNCHER's display and is deliberately NOT affected by [DisplayRoles].** The
 * game moved to [gameLaunchOptions] when the display-role setting landed; every remaining
 * caller of this function — MainActivity's own self-correction, EngineActivity's return to the
 * launcher, and the settings-page relaunch — is putting the LAUNCHER somewhere and must stay
 * pinned to the default display whatever the setting says. That is the safety property that
 * makes a wrong setting recoverable: it can only ever misplace the game, never leave the player
 * unable to reach the toggle that undoes it.
 */
fun topScreenLaunchOptions(): Bundle? =
    ActivityOptions.makeBasic().setLaunchDisplayId(Display.DEFAULT_DISPLAY).toBundle()

/**
 * Launch options for the GAME, which follows the device display profile — see [DisplayRoles].
 *
 * Identical to [topScreenLaunchOptions] on the default (AYN Thor) profile; on the swapped profile
 * it targets the other display instead. Used by [startGame] only.
 */
fun gameLaunchOptions(context: Context): Bundle? =
    ActivityOptions.makeBasic()
        .setLaunchDisplayId(DisplayRoles.gameDisplayId(context))
        .toBundle()

@OptIn(InternalCoroutinesApi::class)
fun Context.startGame(isFinish: Boolean = true) {
    val intent = Intent(this, EngineActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    this.startActivity(intent, gameLaunchOptions(this))
    if (isFinish) {
        if ((this is Activity)) {
            this.finish()
        }
    }
}