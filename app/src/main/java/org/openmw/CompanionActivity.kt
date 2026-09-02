package org.openmw

import android.app.ActivityManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.openmw.companion.CompanionScreen


/**
 * The companion UI hosted as an ACTIVITY on its own display, used only when
 * [org.openmw.utils.DisplayRoles] reports the swapped arrangement.
 *
 * **Why this exists at all — a hard platform rule, confirmed on device (Sep 2 2026).**
 * The companion is normally an `android.app.Presentation`, which `WindowManagerService.addWindow`
 * refuses on any display lacking `Display.FLAG_PRESENTATION` ("Attempted to add presentation window
 * to a non-suitable display. Aborting.", then `InvalidDisplayException`). Android does not set that
 * flag on a device's DEFAULT display, so a `Presentation` can never be placed there. Swapping the
 * roles means putting the companion on the default display, and therefore needs a window type that
 * is not a Presentation.
 *
 * An Activity is used rather than a `TYPE_APPLICATION_OVERLAY` window because the latter needs the
 * user-visible "Display over other apps" grant, while `TYPE_APPLICATION_PANEL` — the type the 13
 * top-screen overlays use — is a SUB-window needing a parent token and so cannot cross to another
 * display at all. An Activity needs no permission and, usefully, gives us a real window token on
 * that display, so the existing sub-window machinery (the pause/options overlay) keeps working
 * there unchanged.
 *
 * **This window is FOCUSABLE, unlike the Presentation's — and that is not an oversight.**
 * It was `FLAG_NOT_FOCUSABLE` at first, copying the Presentation, to stop the companion taking
 * controller input from the game. That caused an ANR (confirmed on device, Sep 2 2026):
 * `ANR ... Reason: Application does not have a focused window`, with
 * `mCurrentFocus=null` on the companion's display. A `Presentation` can be non-focusable because
 * it is a window belonging to the game's own focusable activity; an Activity that is the ONLY
 * window of its task cannot, because then the display has a focused app and no focusable window,
 * so the input dispatcher waits five seconds for one to appear and then declares the app hung.
 *
 * Making it focusable is safe here because this hardware has PER-DISPLAY focus: the same dump
 * showed `EngineActivity` holding `mCurrentFocus` on the game's display while the companion's
 * display tracked its own separately. So the companion takes focus on its own screen only, and the
 * game keeps its own. `FLAG_NOT_TOUCH_MODAL` is retained.
 *
 * Nothing about the companion UI itself changes: this hosts the same [CompanionScreen] composable
 * the Presentation does, so every content-role behaviour (`LocalIsTopScreen`, adaptive dimming, the
 * DS map, popup self-dimming) is identical either way.
 */
class CompanionActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NOT_TOUCH_MODAL only. FLAG_NOT_FOCUSABLE is deliberately NOT set — see the class KDoc:
        // on an Activity it leaves the display with a focused app and no focusable window, which
        // ANRs the moment anything is touched.
        window.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)

        instance = this
        excludeFromRecentsAtRuntime()
        applyImmersive()

        // Re-hide whenever the bars come back. A Presentation needed none of this — a presentation
        // window on a secondary display gets no system bars at all — but this is a real Activity on
        // a real display, so it gets the status bar and the gesture pill like any other, and
        // BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE means a stray swipe near the edge can bring them
        // back. Without a re-hide they stay up, which is the black strip and white bar at the
        // bottom of the companion screen.
        //
        // Kept alongside onWindowFocusChanged rather than replaced by it: the bars can be revealed
        // by an edge swipe without this window's focus changing at all. Terminates rather than
        // looping — once the bars are hidden the next callback reports them invisible and does
        // nothing.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.decorView.setOnApplyWindowInsetsListener { _, insets ->
                if (insets.isVisible(WindowInsets.Type.systemBars())) applyImmersive()
                insets
            }
        }

        setContent {
            val visible by companionVisible.collectAsState()
            // Composed away rather than the window being removed, which is the closest analogue of
            // the Presentation path's hide()/show(): the window stays put and simply draws nothing,
            // so returning from a background/shrink does not have to relaunch an activity.
            if (visible) CompanionScreen()
        }
    }

    override fun onStart() {
        super.onStart()
        started = true
    }

    override fun onResume() {
        super.onResume()
        // Mirrors EngineActivity, which re-hides here for the same reason: the bars can come back
        // across a background/foreground cycle.
        applyImmersive()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // The standard immersive hook, usable now that this window is focusable.
        if (hasFocus) applyImmersive()
    }

    private fun applyImmersive() {
        runCatching {
            hideSystemBars(this)
            // The legacy flags AS WELL as the API 30 insets controller: cheap belt and braces on a
            // window that is unusual enough (its own task, on a second display) to be worth not
            // relying on a single mechanism for. Still respected on API 33.
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                )
        }
    }

    /**
     * Ask the system to keep this task out of recents at runtime, in addition to the manifest's
     * `excludeFromRecents`.
     *
     * Belt and braces: the manifest flag is set and verified present in the built APK, but a second
     * task card was still reported on device, and this is the documented runtime equivalent. If the
     * flag is already being honoured this is a no-op.
     *
     * Not a correctness fix — a stray companion task is cosmetic, because a swiped-away companion
     * is recreated by `EngineActivity.ensureCompanionForeground` the next time the game comes to
     * the foreground.
     */
    private fun excludeFromRecentsAtRuntime() {
        runCatching {
            val am = getSystemService(ActivityManager::class.java) ?: return
            val self = CompanionActivity::class.java.name
            am.appTasks.forEach { task ->
                val info = task.taskInfo ?: return@forEach
                if (info.baseActivity?.className == self || info.topActivity?.className == self) {
                    task.setExcludeFromRecents(true)
                }
            }
        }
    }

    /**
     * Report a background so `EngineActivity` can follow this task to the background too.
     *
     * The home gesture is PER DISPLAY, so swiping up on the companion's screen sends this task —
     * and only this task — to the back, leaving the game running on the other screen beside a dead
     * second screen. That state does not exist on the default arrangement, where the companion is a
     * window owned by the game's own activity and has no task to background.
     *
     * **Relaunching this from here does not work and must not be attempted** (confirmed on device,
     * Sep 2 2026): Android holds an app-switch lock for several seconds after a home gesture, so
     * the start is refused with `Background activity start [... appSwitchState: 0 ...]` and
     * silently dropped — `startActivity` does not throw, so it even looks like it succeeded. The
     * lock exists precisely to stop an app defeating the gesture, so the answer is to follow the
     * player's intent rather than to out-wait it: see `EngineActivity.onCompanionBackgrounded`.
     *
     * `isFinishing` is excluded because a deliberate teardown must not be reacted to.
     */
    override fun onStop() {
        super.onStop()
        started = false
        if (!isFinishing) onBackgrounded?.invoke()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (instance === this) instance = null
    }

    companion object {
        /**
         * The live instance, so `EngineActivity` can reach this window's `WindowManager` for the
         * pause/options overlay — the one piece of the companion that is a separate window rather
         * than part of [CompanionScreen]'s composition.
         *
         * A static Activity reference is a leak if it outlives the Activity, so it is cleared in
         * [onDestroy]. This mirrors how the Presentation path holds `companionPresentation`.
         */
        @Volatile
        var instance: CompanionActivity? = null
            private set

        /**
         * Set by `EngineActivity` while it is hosting this, and called when this activity is
         * backgrounded on its own. Cleared on teardown so a stale callback cannot fire.
         */
        @Volatile
        var onBackgrounded: (() -> Unit)? = null

        /**
         * Whether this activity is currently started, i.e. actually on screen.
         *
         * Needed because a blocked activity start is INVISIBLE to the caller — `startActivity`
         * neither throws nor reports anything when the system drops it. This is the only way to
         * tell a launch that worked from one that was refused, and is what lets the re-assert
         * retry instead of assuming success.
         */
        @Volatile
        var started: Boolean = false
            private set

        private val companionVisible = MutableStateFlow(true)
        val visible: StateFlow<Boolean> = companionVisible.asStateFlow()

        /** Mirror of `Presentation.show()/hide()` for this host — see `updateCompanionForWindowState`. */
        fun setVisible(value: Boolean) {
            companionVisible.value = value
        }

        /** Tear down along with the engine. Also resets visibility so a later session starts shown. */
        fun finishIfRunning() {
            companionVisible.value = true
            onBackgrounded = null
            started = false
            instance?.finish()
            instance = null
        }

        /** Send this activity's task to the back, so it follows the game out of the foreground. */
        fun moveToBack() {
            runCatching { instance?.moveTaskToBack(true) }
        }
    }
}
