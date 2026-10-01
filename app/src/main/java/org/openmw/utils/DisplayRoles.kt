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

    /**
     * A device with ONE screen. There is no companion role at all: the game takes the only
     * display and the bottom-screen UI is never created.
     *
     * Unlike the other two this is not a role MAPPING but a role REMOVAL, so it is deliberately
     * not expressed as "the swap that could not be performed" ([swapSupported] already handles
     * that, and quietly). The distinction matters because the two want opposite handling — a
     * failed swap should still bring up the companion wherever it can, while this profile must
     * never try. See [singleScreen] for who reads it and what they skip.
     */
    const val PROFILE_SINGLE = "single"

    /**
     * The player picks the game screen and the companion screen themselves (Oct 1 2026), for
     * dual-screen hardware none of the presets fit (the AYANEO Pocket DS report: its MAIN display
     * is the bottom panel).
     *
     * **ISOLATION RULE: every Custom behaviour sits behind an `isCustom` test, and the code each
     * other profile runs is unchanged below it.** The Thor and Retroid paths were working and were
     * deliberately not refactored to share this one. If you generalise later, do it as a separate,
     * measured change.
     *
     * Two differences from the presets, both needed for unknown hardware:
     * - it can use ANY public display, not only those Android lists in the PRESENTATION category
     *   (the presets find "the other screen" through that category alone, which is the likeliest
     *   reason the Pocket DS found none);
     * - the companion's host is chosen from the TARGET display ([companionUsesActivity]): a
     *   Presentation where `FLAG_PRESENTATION` allows one, otherwise [org.openmw.CompanionActivity].
     */
    const val PROFILE_CUSTOM = "custom"

    /** Stored as the Custom companion choice to mean "no companion": the single-screen behaviour. */
    const val CUSTOM_NONE = "none"

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

    /** The Custom profile's choices, as descriptors. Null = never chosen (use the defaults). */
    @Volatile
    private var cachedCustomGame: String? = null
    @Volatile
    private var cachedCustomCompanion: String? = null
    @Volatile
    private var customPrimed = false

    /** Read the stored profile into the cache. Call early, from a coroutine. */
    suspend fun prime(context: Context) {
        cached = runCatching { GameFilesPreferences.loadDisplayProfile(context).first() }
            .getOrDefault(PROFILE_DEFAULT)
        primeCustom(context)
        Log.d(TAG, "primed profile=$cached")
    }

    private suspend fun primeCustom(context: Context) {
        cachedCustomGame = runCatching { GameFilesPreferences.loadCustomGameDisplay(context).first() }.getOrNull()
        cachedCustomCompanion = runCatching { GameFilesPreferences.loadCustomCompanionDisplay(context).first() }.getOrNull()
        customPrimed = true
    }

    /** Keep the Custom cache in step with the settings screen, like [onProfileChanged]. */
    fun onCustomDisplaysChanged(game: String, companion: String) {
        cachedCustomGame = game
        cachedCustomCompanion = companion
        customPrimed = true
        Log.d(TAG, "custom displays changed: game=$game companion=$companion")
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
     * True on [PROFILE_SINGLE]: this device has one screen, so there is no companion at all.
     *
     * **This is a PROFILE test, not a hardware test, and that is deliberate.** A device that
     * happens to report no presentation display already falls back safely everywhere (see
     * [swapSupported] and [companionDisplay]); what this adds is the player SAYING so, which is
     * what lets the app skip the second screen up front instead of discovering it is missing --
     * and, more importantly, what lets it turn on the single-screen replacements for the things
     * that lived on the bottom screen (see the Alpha3 overlay gate in `EngineActivity`).
     *
     * Read by `EngineActivity.startCompanionScreen`, which returns before creating any companion
     * host, and by the same activity's Compose overlay, which puts the legacy touch overlay back
     * on the game screen so text entry and the console are still reachable.
     */
    fun singleScreen(context: Context): Boolean =
        profile(context) == PROFILE_SINGLE ||
            // Custom with the companion set to None behaves as single screen (keyboard button,
            // full Vanilla). Unreachable for the presets: their profile is never PROFILE_CUSTOM.
            (profile(context) == PROFILE_CUSTOM && customChoices(context).second == CUSTOM_NONE)

    /**
     * The display id `EngineActivity` should be launched on.
     *
     * Falls back to [Display.DEFAULT_DISPLAY] when swapped but there is no second display to swap
     * to — a single-screen device (or one whose second screen is not yet attached) must not end up
     * with the game launched at an id that does not exist.
     */
    fun gameDisplayId(context: Context): Int {
        if (isCustom(context)) return customGameDisplay(context)?.displayId ?: Display.DEFAULT_DISPLAY
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
        when {
            // Belt and braces. The one caller already returns before asking on this profile, but a
            // display handed back here is a companion window somewhere, and "there is no companion"
            // is the whole meaning of the profile.
            singleScreen(context) -> null
            isCustom(context) -> customCompanionDisplay(context)
            rolesSwapped(context) -> defaultDisplay(context)
            else -> presentationDisplay(context)
        }

    // ------------------------------------------------------------------------------------------
    // Custom profile. Nothing below is reached by the Thor, Retroid or Single Screen profiles.
    // ------------------------------------------------------------------------------------------

    fun isCustom(context: Context): Boolean = profile(context) == PROFILE_CUSTOM

    /** One display as the settings screen and the bug report describe it. */
    data class ScreenInfo(
        val id: Int,
        val name: String,
        val width: Int,
        val height: Int,
        val isDefault: Boolean,
        val presentationFlag: Boolean,
        val descriptor: String,
    ) {
        /** e.g. "Screen 4: Screen-2 (1240 x 1080)", plus "main" for the default display. */
        val label: String
            get() = "Screen $id: $name ($width x $height)" + if (isDefault) ", main" else ""
    }

    /**
     * Every display an app may put a window on. `FLAG_PRIVATE` displays (another app's virtual
     * display, e.g. a screen recorder) are excluded: only their owner can show anything there.
     */
    fun screens(context: Context): List<ScreenInfo> {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return emptyList()
        return dm.displays
            .filter { it.flags and Display.FLAG_PRIVATE == 0 }
            .map { d ->
                val (w, h) = landscapeSize(d)
                ScreenInfo(
                    id = d.displayId,
                    name = d.name ?: "Display ${d.displayId}",
                    width = w,
                    height = h,
                    isDefault = d.displayId == Display.DEFAULT_DISPLAY,
                    presentationFlag = d.flags and Display.FLAG_PRESENTATION != 0,
                    descriptor = descriptorOf(d),
                )
            }
            .sortedBy { it.id }
    }

    /** Physical mode size, long side first, so rotation never changes a descriptor. */
    private fun landscapeSize(d: Display): Pair<Int, Int> {
        val m = d.mode
        val a = m.physicalWidth
        val b = m.physicalHeight
        return if (a >= b) a to b else b to a
    }

    /**
     * "<id>|<name>|<w>x<h>". Matched by NAME AND SIZE first, with the id only as a tiebreaker:
     * secondary display ids are not guaranteed stable across boots, but a panel's name and size are.
     */
    fun descriptorOf(d: Display): String {
        val (w, h) = landscapeSize(d)
        return "${d.displayId}|${d.name}|${w}x$h"
    }

    private fun resolveDescriptor(context: Context, descriptor: String?): Display? {
        if (descriptor.isNullOrBlank() || descriptor == CUSTOM_NONE) return null
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager ?: return null
        val parts = descriptor.split("|")
        if (parts.size < 3) return null
        val id = parts[0].toIntOrNull()
        val name = parts[1]
        val size = parts[2]
        val candidates = dm.displays.filter { it.flags and Display.FLAG_PRIVATE == 0 }
        val byNameAndSize = candidates.filter {
            val (w, h) = landscapeSize(it)
            it.name == name && "${w}x$h" == size
        }
        return byNameAndSize.firstOrNull { it.displayId == id }
            ?: byNameAndSize.firstOrNull()
            ?: candidates.firstOrNull { it.displayId == id }.also {
                if (it != null) Log.w(TAG, "custom: '$descriptor' matched by id only (${descriptorOf(it)})")
            }
    }

    private fun customChoices(context: Context): Pair<String?, String?> {
        if (!customPrimed) {
            runCatching { runBlocking { primeCustom(context) } }
            Log.d(TAG, "custom: cold read game=$cachedCustomGame companion=$cachedCustomCompanion")
        }
        return cachedCustomGame to cachedCustomCompanion
    }

    /** Custom game display: the chosen one if it is present, else the default display. */
    fun customGameDisplay(context: Context): Display? {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val chosen = resolveDescriptor(context, customChoices(context).first)
        if (chosen == null && customChoices(context).first != null) {
            Log.w(TAG, "custom: game screen '${customChoices(context).first}' not found; using the main display")
        }
        return chosen ?: dm?.getDisplay(Display.DEFAULT_DISPLAY)
    }

    /**
     * Custom companion display, or null for "none" (single screen), for a chosen screen that is
     * not present, or for one that is the game's own screen (they cannot share).
     * Never chosen = the first other public display, so picking Custom alone mirrors today.
     */
    fun customCompanionDisplay(context: Context): Display? {
        val choice = customChoices(context).second
        if (choice == CUSTOM_NONE) return null
        val gameId = customGameDisplay(context)?.displayId ?: Display.DEFAULT_DISPLAY
        val d = if (choice == null) {
            val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
            dm?.displays?.firstOrNull { it.flags and Display.FLAG_PRIVATE == 0 && it.displayId != gameId }
        } else {
            resolveDescriptor(context, choice).also {
                if (it == null) Log.w(TAG, "custom: companion screen '$choice' not found; no companion")
            }
        }
        return d?.takeIf { it.displayId != gameId }
    }

    /** The stored Custom choices, for the settings screen (null = never chosen). */
    fun customChoiceDescriptors(context: Context): Pair<String?, String?> = customChoices(context)

    /**
     * Whether the companion must be hosted by [org.openmw.CompanionActivity] rather than a
     * Presentation. **For every preset this is exactly [rolesSwapped]**, the test
     * `EngineActivity` and the splash used before Custom existed. Custom decides from the target
     * display instead: Android only allows a Presentation on a display with `FLAG_PRESENTATION`.
     */
    fun companionUsesActivity(context: Context): Boolean {
        if (!isCustom(context)) return rolesSwapped(context)
        val d = companionDisplay(context) ?: return false
        return d.flags and Display.FLAG_PRESENTATION == 0
    }
}
