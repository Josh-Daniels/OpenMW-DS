package org.openmw.companion

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the conversation UI is drawn.
 * - [BOTTOM]: original two-column layout entirely on the bottom screen.
 * - [SPLIT]: history on the top screen, topics/controls on the bottom (current default).
 * - [TOP]: full conversation on the top screen (not yet implemented — treated as [SPLIT]).
 *
 * NOTE: distinct from [GameUiMode]. This only chooses which screen the (DS) conversation is
 * drawn on; whether the companion draws conversation at all is the "game_ui_conversation"
 * [GameUiMode]. No longer has a dedicated options-menu row — defaults to [SPLIT].
 */
enum class ConversationLocation { BOTTOM, SPLIT, TOP }

/**
 * Per-element rendering mode for a "Game UI" element (a menu/overlay the companion can take
 * over from native OpenMW).
 * - [DS]: the companion draws it on the bottom screen; the native top-screen version is suppressed.
 * - [VANILLA]: native OpenMW handles it on the top screen as normal.
 */
enum class GameUiMode { DS, VANILLA }

/**
 * One "Game UI" element in the options menu's GAME UI section, in display order.
 * [pending] elements have no companion (DS) replacement yet: they are locked to [VANILLA]
 * and their DS pill renders greyed with "not yet available".
 */
data class GameUiElement(
    val key: String,
    val label: String,
    val pending: Boolean = false,
) {
    /** Pending elements are locked to VANILLA; everything else defaults to DS. */
    val defaultMode: GameUiMode get() = if (pending) GameUiMode.VANILLA else GameUiMode.DS
}

/** Catalogue of every Game UI element, in display order — the single source of truth the
 *  GAME UI section renders from and [UiPreferences] persists. */
val GAME_UI_ELEMENTS: List<GameUiElement> = listOf(
    GameUiElement("game_ui_conversation", "Conversation"),
    GameUiElement("game_ui_looting", "Looting"),
    GameUiElement("game_ui_bartering", "Bartering"),
    GameUiElement("game_ui_persuasion", "Persuasion"),
    GameUiElement("game_ui_repair", "Repair"),
    GameUiElement("game_ui_levelup", "Level up", pending = true),
    GameUiElement("game_ui_spellmaking", "Spellmaking", pending = true),
    GameUiElement("game_ui_restwait", "Rest / Wait", pending = true),
)

/**
 * One native top-screen HUD element that the "Vanilla HUD" section can show/hide. [pending]
 * elements have no native gate implemented yet: their On/Off pills render greyed and locked to On.
 */
data class UiElement(val key: String, val label: String, val pending: Boolean = false)

/**
 * Catalogue of the native top-screen HUD elements, in display order. The companion always draws
 * these on the bottom screen; the On/Off toggle controls whether the NATIVE top-screen version is
 * also visible (On) or hidden (Off). Keys must stay stable — [org.openmw.EngineActivity] pushes
 * each to native by key and the persisted prefs are keyed on them.
 */
val HUD_ELEMENTS: List<UiElement> = listOf(
    UiElement("hud_vitals", "Health / Magicka / Fatigue"),
    UiElement("hud_equipped", "Equipped weapon and spell"),
    UiElement("hud_minimap", "Minimap"),
    UiElement("hud_effects", "Active effects"),
    UiElement("hud_sneak", "Sneak indicator"),
    UiElement("hud_enemy", "Target health"),
    UiElement("hud_crosshair", "Crosshair"),
    // Pending: pref exists (default On, persisted) but no native gate is wired yet, so
    // EngineActivity does NOT push it and the row renders locked/greyed.
    UiElement("hud_controller_tooltips", "Controller tooltips", pending = true),
)

/**
 * Global (not per-character) UI settings: the per-element Game UI mode (DS/Vanilla), which native
 * HUD elements are visible, and the input/overlay toggles. Backed by SharedPreferences and exposed
 * as StateFlows so the options UI reacts live and rendering code can observe changes.
 *
 * A plain object so it survives Activity boundaries, matching [GameStateRepository].
 */
object UiPreferences {
    private const val PREFS = "companion_ui_settings"
    private const val GAME_UI_PREFIX = "" // keys already carry the "game_ui_" prefix
    private const val GAME_CURSOR = "game_cursor"
    private const val CONVERSATION_LOCATION = "conversation_location"
    private const val HUD_ON_PREFIX = "hud_on_"
    private const val ALPHA3_OVERLAY = "alpha3_overlay"

    private var prefs: SharedPreferences? = null

    // Per-element Game UI mode (DS = companion draws it; VANILLA = native handles it). Pending
    // elements are locked to their VANILLA default and never persisted/changed.
    private val gameUiModeFlows: Map<String, MutableStateFlow<GameUiMode>> =
        GAME_UI_ELEMENTS.associate { it.key to MutableStateFlow(it.defaultMode) }

    // Where the conversation UI is drawn (BOTTOM / SPLIT / TOP). Default SPLIT.
    private val conversationLocationFlow = MutableStateFlow(ConversationLocation.SPLIT)

    // HUD elements are always drawn on the bottom screen by the companion; this Boolean toggles
    // whether the NATIVE top-screen version is visible (true = On/visible, false = Off/hidden).
    // Keyed by HUD element key, default true (On). Actual native hiding is implemented separately.
    private val hudFlows: Map<String, MutableStateFlow<Boolean>> =
        HUD_ELEMENTS.associate { it.key to MutableStateFlow(true) }

    // Input section: whether touch / thumbsticks drive the top-screen game cursor.
    // Default false (off). The actual cursor suppression lives in a native patch;
    // this only stores the preference.
    private val gameCursorFlow = MutableStateFlow(false)

    // Whether the Alpha3 launcher overlay (gear + arrow cluster) is shown. Default true.
    // Purely Kotlin-side (gates a composable in EngineActivity); no native involvement.
    private val alpha3OverlayFlow = MutableStateFlow(true)

    /** Load persisted values into the flows. Idempotent — safe to call on every compose. */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        GAME_UI_ELEMENTS.forEach { el ->
            if (el.pending) {
                // Pending elements ignore any stored value — the mode is locked to VANILLA.
                gameUiModeFlows.getValue(el.key).value = GameUiMode.VANILLA
            } else {
                p.getString(GAME_UI_PREFIX + el.key, null)
                    ?.let { runCatching { GameUiMode.valueOf(it) }.getOrNull() }
                    ?.let { gameUiModeFlows.getValue(el.key).value = it }
            }
        }
        gameCursorFlow.value = p.getBoolean(GAME_CURSOR, false)
        p.getString(CONVERSATION_LOCATION, null)
            ?.let { runCatching { ConversationLocation.valueOf(it) }.getOrNull() }
            ?.let { conversationLocationFlow.value = it }
        HUD_ELEMENTS.forEach { el ->
            hudFlows.getValue(el.key).value = p.getBoolean(HUD_ON_PREFIX + el.key, true)
        }
        alpha3OverlayFlow.value = p.getBoolean(ALPHA3_OVERLAY, true)
    }

    /** The DS/Vanilla mode for a Game UI element (e.g. "game_ui_looting"). */
    fun gameUiModeFlow(key: String): StateFlow<GameUiMode> = gameUiModeFlows.getValue(key).asStateFlow()

    /** Set a Game UI element's mode and persist. No-op for pending (locked) elements. */
    fun setGameUiMode(context: Context, key: String, mode: GameUiMode) {
        val el = GAME_UI_ELEMENTS.firstOrNull { it.key == key } ?: return
        if (el.pending) return
        gameUiModeFlows.getValue(key).value = mode
        editor(context).putString(GAME_UI_PREFIX + key, mode.name).apply()
    }

    /** Bulk-set every non-pending Game UI element to [mode] (the "All DS" / "All Vanilla" quick-set
     *  buttons). Pending elements stay locked to VANILLA; Vanilla HUD toggles are NOT affected. */
    fun setAllGameUi(context: Context, mode: GameUiMode) {
        GAME_UI_ELEMENTS.filter { !it.pending }.forEach { setGameUiMode(context, it.key, mode) }
    }

    fun hudOnFlow(key: String): StateFlow<Boolean> = hudFlows.getValue(key).asStateFlow()

    /** Set a HUD element's on/off state and persist. */
    fun setHudOn(context: Context, key: String, on: Boolean) {
        val flow = hudFlows[key] ?: return
        flow.value = on
        editor(context).putBoolean(HUD_ON_PREFIX + key, on).apply()
    }

    /** Input: whether touch / thumbsticks control the top-screen game cursor. */
    fun gameCursorFlow(): StateFlow<Boolean> = gameCursorFlow.asStateFlow()

    /** Whether the Alpha3 launcher overlay (gear + arrow cluster) is shown. */
    fun alpha3OverlayFlow(): StateFlow<Boolean> = alpha3OverlayFlow.asStateFlow()

    /** Show/hide the Alpha3 launcher overlay and persist. */
    fun setAlpha3Overlay(context: Context, shown: Boolean) {
        alpha3OverlayFlow.value = shown
        editor(context).putBoolean(ALPHA3_OVERLAY, shown).apply()
    }

    /** Where the conversation UI is drawn (BOTTOM / SPLIT / TOP). */
    fun conversationLocationFlow(): StateFlow<ConversationLocation> = conversationLocationFlow.asStateFlow()

    /** Set the conversation location and persist. */
    fun setConversationLocation(context: Context, loc: ConversationLocation) {
        conversationLocationFlow.value = loc
        editor(context).putString(CONVERSATION_LOCATION, loc.name).apply()
    }

    /** Enable/disable the top-screen game cursor and persist. */
    fun setGameCursor(context: Context, enabled: Boolean) {
        gameCursorFlow.value = enabled
        editor(context).putBoolean(GAME_CURSOR, enabled).apply()
    }

    private fun editor(context: Context): SharedPreferences.Editor {
        val p = prefs ?: context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        return p.edit()
    }
}
