package org.openmw

import android.app.ActivityManager
import android.graphics.PixelFormat
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Gravity
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
 * **This window is focusable while it is COMING UP and non-focusable once it is up. Both halves
 * are load-bearing and neither is safe on its own** — see [dropFocusabilityOnceFocused].
 *
 * It was `FLAG_NOT_FOCUSABLE` from `onCreate` at first, copying the Presentation, to stop the
 * companion taking controller input from the game. That caused an ANR (confirmed on device,
 * Sep 2 2026): `ANR ... Reason: Application does not have a focused window`, with
 * `mCurrentFocus=null` on the companion's display. A `Presentation` can be non-focusable because
 * it is a window belonging to the game's own focusable activity; an Activity that is the ONLY
 * window of its task cannot, because then the display has a focused app and no focusable window,
 * so the input dispatcher waits five seconds for one to appear and then declares the app hung.
 *
 * It was then permanently focusable, which is what shipped in 1.1.0. The safety argument was that
 * this hardware has PER-DISPLAY focus, so the companion would take focus on its own screen only.
 * **That argument is wrong, and a Retroid user reported the consequence (Sep 10 2026): tapping the
 * companion killed the game's controller input until the game screen was tapped again.**
 * Per-display focus governs whether two displays can hold focus at the SAME TIME. It does not give
 * you two key-input destinations: `InputDispatcher` keeps a single focused *display*, and a
 * controller is not associated with any display, so its key events go to the focused window of
 * whichever display is focused. Touch routes per-display (which is why the companion itself kept
 * working); keys do not. `EngineActivity` is an `SDLActivity`, so the engine reads the controller
 * through that window's key dispatch — lose the focused display and the game goes deaf.
 *
 * This was never a first-frame race, which is how it had been read: `startCompanionScreen()` runs
 * inside `EngineActivity.onCreate`, so on this profile the companion launches AFTER the game and
 * takes the focused display from it at boot. That is what
 * `CompanionScreen.SPLASH_NOTICE_SWAPPED_CONTROLS` ("Tap the game screen to enable controls.") was
 * papering over. **If this fix holds, that notice should become unnecessary** — which is the
 * cheapest way to confirm the model is right.
 *
 * `FLAG_NOT_TOUCH_MODAL` is retained throughout and is unrelated: it governs where touches OUTSIDE
 * the window go, not focus. A tap inside a focusable window focuses it regardless.
 *
 * Dropping focusability cannot break the companion's own input: the companion has no `TextField`
 * and no IME anywhere, and the Presentation path has always been non-focusable, so the UI cannot
 * depend on window focus.
 *
 * Nothing about the companion UI itself changes: this hosts the same [CompanionScreen] composable
 * the Presentation does, so every content-role behaviour (`LocalIsTopScreen`, adaptive dimming, the
 * DS map, popup self-dimming) is identical either way.
 */
class CompanionActivity : ComponentActivity() {

    /** One-shot guard for [dropFocusabilityOnceFocused], reset per foreground pass by [onStart]. */
    private var focusabilityDropped = false

    /** The untouchable focus holder for this display. See [addFocusAnchor]. */
    private var focusAnchorView: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // NOT_TOUCH_MODAL only. FLAG_NOT_FOCUSABLE is deliberately NOT set HERE — setting it at
        // create time is exactly the configuration that ANRs (see the class KDoc). It is added
        // later, once this window has actually held focus: dropFocusabilityOnceFocused().
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
        // Every foreground pass starts focusable again. See restoreFocusability() — a window that
        // came back from the background already non-focusable is byte-for-byte the create-time
        // configuration that ANRs.
        restoreFocusability()
    }

    override fun onResume() {
        super.onResume()
        // Mirrors EngineActivity, which re-hides here for the same reason: the bars can come back
        // across a background/foreground cycle.
        applyImmersive()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        Log.d(TAG, "onWindowFocusChanged hasFocus=$hasFocus ${flagState()}")
        if (hasFocus) {
            applyImmersive()
            dropFocusabilityOnceFocused()
        }
    }

    /**
     * Whether `FLAG_NOT_FOCUSABLE` is actually present on the LIVE window attributes, plus the
     * display this window is on.
     *
     * Reads `window.attributes` rather than tracking what we asked for, because the whole question
     * the logging exists to answer is whether the request STUCK — `addFlags` on a running Activity
     * window is a relayout request, not a guarantee.
     */
    private fun flagState(): String {
        val flags = runCatching { window.attributes.flags }.getOrDefault(0)
        val notFocusable = flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE != 0
        return "notFocusable=$notFocusable display=${runCatching { display?.displayId }.getOrNull()}"
    }

    /**
     * Make this window focusable again for a fresh foreground pass.
     *
     * Called from [onStart], not [onCreate], because this activity is `singleInstance`: after
     * `moveTaskToBack` (the per-display home gesture) and the `ensureCompanionForeground` relaunch,
     * the SAME instance comes back and `onCreate` never runs again. Without this the window would
     * return already carrying `FLAG_NOT_FOCUSABLE`, i.e. the create-time configuration that ANRs.
     *
     * Clearing a flag that is not set is a no-op, so the first pass costs nothing.
     */
    private fun restoreFocusability() {
        focusabilityDropped = false
        runCatching { window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) }
        Log.d(TAG, "restoreFocusability -> ${flagState()}")
    }

    /**
     * Hand focus back as soon as this window no longer needs it. **This is the fix for "tapping the
     * companion kills the game's controller input"** (reported Sep 10 2026); the reasoning is in the
     * class KDoc.
     *
     * The two failure modes are at opposite ends of the window's life, which is why the answer is
     * the same flag at a different TIME rather than a different flag:
     * - focusable at create time is REQUIRED, or the display has a focused app and no focusable
     *   window and the dispatcher ANRs after 5s;
     * - focusable afterwards is HARMFUL, because every tap makes this the focused display and the
     *   controller follows it off the game.
     *
     * Waiting for a real `onWindowFocusChanged(true)` is what separates them: by then the window
     * has been added and focused, so the dispatcher has had its focusable window and the ANR
     * condition is behind us.
     *
     * **MEASURED ON DEVICE Sep 10 2026, and it settles a question this comment used to leave open.**
     * It said WindowManager "should then move the top focused display to the game's". **It does
     * not.** With the main window non-focusable, logcat gave:
     * ```
     * W InputDispatcher: Focused display #0 does not have a focused window.
     * E InputDispatcher: Dropping MOTION event because there is no focused window
     * I WindowManager: ANR in ActivityRecord{...CompanionActivity} Reason: Application does not
     *                  have a focused window
     * E InputDispatcher: But another display has a focused window
     * ```
     * That last line is the whole finding: display 0 stays the focused display, still has
     * `CompanionActivity` as its focused APP, and now has no focusable WINDOW — which is the
     * Sep 2 2026 ANR exactly, just deferred to whenever input next arrives. It surfaced as the
     * OS declaring the app hung during a long modded load, survivable only by tapping the screen
     * every few seconds to reset the dispatcher's 5s timer.
     *
     * **Hence [addFocusAnchor], and the ORDER here is the fix.** Display 0 is given a focused
     * window that cannot be tapped, and only then is the main window made non-focusable. The
     * anchor absorbs the dispatcher's requirement; the main window keeps the property that makes
     * a companion tap harmless.
     *
     * **Fails SAFE:** if the anchor cannot be added, focusability is NOT dropped. A failure
     * therefore regresses to the old focus bug (annoying, recoverable by tapping the game screen)
     * rather than to an ANR (the OS offering to kill the game mid-load). Never reorder these two.
     *
     * Posted rather than applied inline so the flag change is not made from inside the focus
     * callback itself, and one-shot per foreground pass because once it lands this window cannot
     * take focus again to re-run it.
     */
    private fun dropFocusabilityOnceFocused() {
        if (focusabilityDropped) return
        focusabilityDropped = true
        window.decorView.post {
            // ORDER IS LOAD-BEARING: anchor first, and bail out entirely if it did not land.
            if (!addFocusAnchor()) {
                focusabilityDropped = false
                Log.e(TAG, "no focus anchor, staying focusable rather than risking an ANR")
                return@post
            }
            runCatching {
                window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                // onWindowFocusChanged is dead from here (a non-focusable window never gets focus),
                // so its immersive re-hide has to happen once more on the way past. The insets
                // listener in onCreate and the onResume call are what keep the bars down after
                // this, and both are independent of focus.
                applyImmersive()
                Log.d(TAG, "dropFocusability applied -> ${flagState()}")
            }.onFailure {
                focusabilityDropped = false
                Log.e(TAG, "dropFocusability FAILED, staying focusable", it)
            }
        }
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
     * Give this display a focused window that nothing can tap: a 1x1, invisible, untouchable but
     * FOCUSABLE sub-window of this activity.
     *
     * **Why it has to exist.** On this profile the companion is a real Activity on display 0, so
     * that display always has a focused *app*. Android then wants a focused *window* there, and
     * measurement (see [dropFocusabilityOnceFocused]) showed it will NOT go looking on another
     * display when there isn't one — it drops the event and ANRs the app. So display 0 needs a
     * focused window. The bug is that the obvious candidate, the companion's own content window,
     * is the thing the player taps, and a tap on a focusable window makes its display the focused
     * one, which is what takes the controller off the game.
     *
     * Splitting the two roles across two windows satisfies both at once:
     * - `FLAG_NOT_TOUCHABLE` means this window never receives a touch, so it can never be the
     *   window a tap focuses. It is 1x1 as well, but the flag is what actually guarantees it.
     * - No `FLAG_NOT_FOCUSABLE`, so it is a valid focus target for the dispatcher and the ANR
     *   condition cannot arise.
     * - `FLAG_NOT_TOUCH_MODAL` so touches outside its one pixel reach the companion below.
     *
     * `TYPE_APPLICATION_PANEL` with the decor's `windowToken`, i.e. the same sub-window mechanism
     * `EngineActivity.showPauseOverlay` already uses successfully on this display. Being a panel
     * it also sits ABOVE the main window in z-order, which is what lets it take focus from it.
     *
     * **This does not make the game focusable at boot, and is not meant to.** While display 0 is
     * the focused display the controller talks to this anchor, which swallows it — the same state
     * 1.1.0 shipped, covered by `SPLASH_NOTICE_SWAPPED_CONTROLS` ("Tap the game screen to enable
     * controls."). What it removes is the ANR, and what [dropFocusabilityOnceFocused] removes is
     * the tap-to-steal. After that one tap the arrangement is stable for the session.
     *
     * @return true if the anchor is in place; the caller MUST NOT drop focusability otherwise.
     */
    private fun addFocusAnchor(): Boolean {
        focusAnchorView?.let { return true }
        val token = window.decorView.windowToken ?: run {
            Log.w(TAG, "focus anchor: no window token yet")
            return false
        }
        val lp = WindowManager.LayoutParams(
            1, 1,
            WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSPARENT
        ).apply {
            this.token = token
            gravity = Gravity.TOP or Gravity.START
        }
        val view = View(this)
        return runCatching { windowManager.addView(view, lp) }
            .onSuccess {
                focusAnchorView = view
                Log.d(TAG, "focus anchor added")
            }
            .onFailure { Log.e(TAG, "focus anchor addView failed", it) }
            .isSuccess
    }

    private fun removeFocusAnchor() {
        val view = focusAnchorView ?: return
        runCatching { windowManager.removeView(view) }
        focusAnchorView = null
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
        removeFocusAnchor()
        if (instance === this) instance = null
    }

    companion object {

        private const val TAG = "CompanionActivity"

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
