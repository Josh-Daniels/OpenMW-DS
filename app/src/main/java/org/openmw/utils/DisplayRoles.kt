package org.openmw.utils

import android.content.Context
import android.hardware.display.DisplayManager
import android.util.Log
import android.view.Display
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * Which physical display plays the GAME role and which plays the COMPANION role.
 *
 * The app was built around the AYN Thor, where the game belongs on [Display.DEFAULT_DISPLAY] and
 * the companion on the one display carrying `FLAG_PRESENTATION`. Two users on Retroid dual-screen
 * hardware report those landing on the opposite physical panels, so the mapping is now a setting
 * rather than an assumption.
 *
 * **Only TWO things in the app read this** — the launch display for `EngineActivity`
 * ([gameLaunchOptions], via `startGame`) and the display the companion `Presentation` is created on
 * ([companionDisplay]) — plus [gameScreenRealSize], which derives the game's render resolution from
 * whichever display the game role resolved to. Everything else in the app is relative to its own
 * host window (the top-screen overlay windows, the pause overlay, `LocalIsTopScreen` and the
 * dimming that reads it, the focus flags, touch mapping, and all native code), so it follows a swap
 * with no code of its own.
 *
 * **The LAUNCHER is deliberately NOT affected and must never be.** `topScreenLaunchOptions()` and
 * `MainActivity.relaunchOnTopScreenIfNeeded()` stay pinned to [Display.DEFAULT_DISPLAY] whatever
 * this is set to, so a wrong choice can only ever put the GAME on the wrong screen — never strand
 * the player on a screen where they cannot reach the setting to undo it.
 */
object DisplayRoles {

    private const val TAG = "DisplayRoles"

    /** AYN Thor, and the default: game on the default display, companion on the presentation one. */
    const val PROFILE_THOR = "thor"

    /** Retroid dual screen: the two roles exchanged, relative to [PROFILE_THOR]. */
    const val PROFILE_RETROID = "retroid"

    const val PROFILE_DEFAULT = PROFILE_THOR

    /**
     * Last known profile, so the two call sites can resolve a role synchronously.
     *
     * Both of them run at a point where suspending is not available — `startGame()` is a plain
     * Context extension with six callers, and `startCompanionScreen()` runs inside
     * `EngineActivity.onCreate`. The cache is primed from `MainActivity` (see [prime]) and, because
     * the launcher and the engine share one process, is still warm by the time the engine asks.
     */
    @Volatile
    private var cached: String? = null

    /** Read the stored profile into the cache. Call early, from a coroutine. */
    suspend fun prime(context: Context) {
        cached = runCatching { GameFilesPreferences.loadDisplayProfile(context).first() }
            .getOrDefault(PROFILE_DEFAULT)
        Log.d(TAG, "primed profile=$cached")
    }

    /** Keep the cache in step when the setting is changed, so it takes effect on the next Play
     *  without a relaunch. */
    fun onProfileChanged(profileId: String) {
        cached = profileId
        Log.d(TAG, "profile changed to $profileId")
    }

    /**
     * The active profile.
     *
     * Falls back to a BLOCKING read only when the cache is cold, which in practice cannot happen on
     * the normal path (the launcher primes it before Play). It is kept as a backstop rather than
     * defaulting silently, because guessing wrong here puts the game on the wrong screen.
     */
    fun profile(context: Context): String = cached ?: runCatching {
        runBlocking { GameFilesPreferences.loadDisplayProfile(context).first() }
    }.getOrDefault(PROFILE_DEFAULT).also {
        cached = it
        Log.d(TAG, "cold read profile=$it")
    }

    /** The presentation-capable display, i.e. the one the companion sits on by default. Null on a
     *  single-screen device. */
    private fun presentationDisplay(context: Context): Display? =
        (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            ?.firstOrNull()

    private fun defaultDisplay(context: Context): Display? =
        (context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager)
            ?.getDisplay(Display.DEFAULT_DISPLAY)

    /**
     * Whether the swapped arrangement is achievable on this hardware: is there a second display to
     * move the game to at all.
     *
     * **This deliberately does NOT test `Display.FLAG_PRESENTATION` on the companion's target.**
     * It briefly did, when the swapped companion was still an `android.app.Presentation` — Android
     * refuses `TYPE_PRESENTATION` on any display without that flag and never sets it on the default
     * display, which made the swap impossible and was confirmed on device (Sep 2 2026):
     * `Attempted to add presentation window to a non-suitable display. Aborting.` The swapped
     * companion is now an Activity ([org.openmw.CompanionActivity]), which has no such restriction,
     * so the only real requirement left is a second display.
     *
     * Checked BEFORE anything is moved, and both roles read it, so the two can never half-apply —
     * a game relocated to the other panel with no companion to accompany it is worse than not
     * swapping at all, and is exactly what the first cut produced on the Thor.
     */
    fun swapSupported(context: Context): Boolean {
        if (presentationDisplay(context) == null) {
            Log.w(TAG, "swap not supported: no second display; keeping the default arrangement")
            return false
        }
        return true
    }

    /** True when the roles are exchanged relative to the Thor arrangement. False when the profile
     *  asks for a swap this device cannot perform, so both roles fall back together. */
    fun rolesSwapped(context: Context): Boolean =
        profile(context) == PROFILE_RETROID && swapSupported(context)

    /**
     * The display id `EngineActivity` should be launched on.
     *
     * Falls back to [Display.DEFAULT_DISPLAY] when swapped but there is no second display to swap
     * to — a single-screen device (or one whose second screen is not yet attached) must not end up
     * with the game launched at an id that does not exist.
     */
    fun gameDisplayId(context: Context): Int {
        if (!rolesSwapped(context)) return Display.DEFAULT_DISPLAY
        val presentation = presentationDisplay(context)
        if (presentation == null) {
            Log.w(TAG, "swap requested but no presentation display; game stays on the default")
            return Display.DEFAULT_DISPLAY
        }
        return presentation.displayId
    }

    /**
     * The display the companion `Presentation` should be created on, or null when there is nowhere
     * to put it (the existing "no second screen" case, handled by the caller).
     *
     * [rolesSwapped] has already established that a swapped target can actually host a
     * `Presentation`, so this cannot hand back a display that `Presentation.show()` will refuse.
     */
    fun companionDisplay(context: Context): Display? =
        if (rolesSwapped(context)) defaultDisplay(context) else presentationDisplay(context)
}
