package org.openmw.companion

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which physical screen a UI element is drawn on. */
enum class ScreenRoute { TOP, BOTTOM }

/**
 * Where the conversation UI is drawn.
 * - [BOTTOM]: original two-column layout entirely on the bottom screen.
 * - [SPLIT]: history on the top screen, topics/controls on the bottom (current default).
 * - [TOP]: full conversation on the top screen (not yet implemented — treated as [SPLIT]).
 */
enum class ConversationLocation { BOTTOM, SPLIT, TOP }

/** How a top-screen element is rendered: vanilla OpenMW UI or the DS-styled replacement. */
enum class UiStyle { VANILLA, DS }

/**
 * Master companion UI mode.
 * - [DS] (default): all companion overlays/popups are active.
 * - [VANILLA]: companion overlays are suppressed; native OpenMW UI handles everything.
 * The tab UI (inventory, spells, stats, journal, HUD) works in both modes.
 */
enum class UiMode { VANILLA, DS }

/** The two routing sections of the options menu (the UI-style section is derived, not stored). */
enum class UiSection { HUD, MENUS }

/**
 * One routable UI element. [pending] elements have no companion (DS) UI yet: their route is
 * locked to [default] and they expose no style choice.
 */
data class UiElement(
    val key: String,
    val label: String,
    val section: UiSection,
    val default: ScreenRoute,
    val pending: Boolean = false,
) {
    /** A non-pending element has a DS companion replacement, so it gets a UI-style choice. */
    val hasCompanionUi: Boolean get() = !pending
    /** Pending elements are locked to their default route. */
    val lockedRoute: ScreenRoute get() = default
}

/**
 * Catalogue of every routable UI element, in display order. This is the single source of
 * truth the options menu renders from and [UiPreferences] persists.
 */
val UI_ELEMENTS: List<UiElement> = listOf(
    // ---- HUD elements ----
    UiElement("hud_vitals", "Health / Magicka / Fatigue", UiSection.HUD, ScreenRoute.BOTTOM),
    UiElement("hud_equipped", "Equipped weapon and spell", UiSection.HUD, ScreenRoute.BOTTOM),
    UiElement("hud_minimap", "Minimap", UiSection.HUD, ScreenRoute.BOTTOM),
    UiElement("hud_effects", "Active effects", UiSection.HUD, ScreenRoute.BOTTOM),
    UiElement("hud_crosshair", "Crosshair", UiSection.HUD, ScreenRoute.BOTTOM),
    UiElement("hud_sneak", "Sneak indicator", UiSection.HUD, ScreenRoute.BOTTOM),
    UiElement("hud_enemy", "Target health", UiSection.HUD, ScreenRoute.BOTTOM),
    // ---- Menus and overlays ----
    // NOTE: "Conversation" is NOT a generic route element — it's a dedicated three-way
    // ConversationLocation setting (BOTTOM/SPLIT/TOP), rendered by ConversationLocationRow
    // and backed by conversationLocationFlow below.
    UiElement("menu_conversation_topics", "Conversation topics only", UiSection.MENUS, ScreenRoute.BOTTOM),
    UiElement("menu_persuasion", "Persuasion screen", UiSection.MENUS, ScreenRoute.BOTTOM),
    UiElement("menu_looting", "Looting", UiSection.MENUS, ScreenRoute.BOTTOM),
    UiElement("menu_pickpocket", "Pickpocket", UiSection.MENUS, ScreenRoute.BOTTOM),
    UiElement("menu_bartering", "Bartering", UiSection.MENUS, ScreenRoute.BOTTOM),
    UiElement("menu_repair", "Repair screen", UiSection.MENUS, ScreenRoute.TOP, pending = true),
    UiElement("menu_levelup", "Level up screen", UiSection.MENUS, ScreenRoute.TOP, pending = true),
    UiElement("menu_spellmaking", "Spellmaking / Enchanting", UiSection.MENUS, ScreenRoute.TOP, pending = true),
    UiElement("menu_rest", "Rest / Wait screen", UiSection.MENUS, ScreenRoute.TOP, pending = true),
)

/**
 * Global (not per-character) UI settings: which screen each element is routed to, and the
 * rendering style of top-screen elements. Backed by SharedPreferences and exposed as
 * StateFlows so the options UI reacts live and future rendering code can observe changes.
 *
 * A plain object so it survives Activity boundaries, matching [GameStateRepository]. Values
 * default from [UI_ELEMENTS]; pending elements always report their locked route.
 */
object UiPreferences {
    private const val PREFS = "companion_ui_settings"
    private const val ROUTE_PREFIX = "route_"
    private const val STYLE_PREFIX = "style_"
    private const val GAME_CURSOR = "game_cursor"
    private const val CONVERSATION_LOCATION = "conversation_location"
    private const val UI_MODE = "ui_mode"
    private const val HUD_ON_PREFIX = "hud_on_"
    private const val ALPHA3_OVERLAY = "alpha3_overlay"

    private var prefs: SharedPreferences? = null

    // Master UI mode (DS = companion overlays active; VANILLA = suppressed). Default DS.
    private val uiModeFlow = MutableStateFlow(UiMode.DS)

    // Where the conversation UI is drawn (BOTTOM / SPLIT / TOP). Default SPLIT.
    private val conversationLocationFlow = MutableStateFlow(ConversationLocation.SPLIT)

    private val routeFlows: Map<String, MutableStateFlow<ScreenRoute>> =
        UI_ELEMENTS.associate { it.key to MutableStateFlow(it.default) }
    private val styleFlows: Map<String, MutableStateFlow<UiStyle>> =
        UI_ELEMENTS.associate { it.key to MutableStateFlow(UiStyle.VANILLA) }

    // HUD elements are always drawn on the bottom screen by the companion; this Boolean toggles
    // whether the NATIVE top-screen version is visible (true = On/visible, false = Off/hidden).
    // Keyed by HUD element key, default true (On). Actual native hiding is implemented separately.
    private val hudFlows: Map<String, MutableStateFlow<Boolean>> =
        UI_ELEMENTS.filter { it.section == UiSection.HUD }.associate { it.key to MutableStateFlow(true) }

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
        UI_ELEMENTS.forEach { el ->
            if (el.pending) {
                // Pending elements ignore any stored value — the route is locked.
                routeFlows.getValue(el.key).value = el.lockedRoute
            } else {
                p.getString(ROUTE_PREFIX + el.key, null)
                    ?.let { runCatching { ScreenRoute.valueOf(it) }.getOrNull() }
                    ?.let { routeFlows.getValue(el.key).value = it }
                p.getString(STYLE_PREFIX + el.key, null)
                    ?.let { runCatching { UiStyle.valueOf(it) }.getOrNull() }
                    ?.let { styleFlows.getValue(el.key).value = it }
            }
        }
        gameCursorFlow.value = p.getBoolean(GAME_CURSOR, false)
        p.getString(CONVERSATION_LOCATION, null)
            ?.let { runCatching { ConversationLocation.valueOf(it) }.getOrNull() }
            ?.let { conversationLocationFlow.value = it }
        p.getString(UI_MODE, null)
            ?.let { runCatching { UiMode.valueOf(it) }.getOrNull() }
            ?.let { uiModeFlow.value = it }
        // HUD element on/off (non-pending only; pending elements stay locked On).
        UI_ELEMENTS.filter { it.section == UiSection.HUD && !it.pending }.forEach { el ->
            hudFlows.getValue(el.key).value = p.getBoolean(HUD_ON_PREFIX + el.key, true)
        }
        alpha3OverlayFlow.value = p.getBoolean(ALPHA3_OVERLAY, true)
    }

    fun routeFlow(key: String): StateFlow<ScreenRoute> = routeFlows.getValue(key).asStateFlow()
    fun styleFlow(key: String): StateFlow<UiStyle> = styleFlows.getValue(key).asStateFlow()

    /** HUD element on/off: whether the native top-screen version is visible (true = On). */
    fun hudOnFlow(key: String): StateFlow<Boolean> = hudFlows.getValue(key).asStateFlow()

    /** Set a HUD element's on/off state and persist. No-op for pending (locked) elements. */
    fun setHudOn(context: Context, key: String, on: Boolean) {
        val el = UI_ELEMENTS.firstOrNull { it.key == key } ?: return
        if (el.pending) return
        hudFlows.getValue(key).value = on
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

    /** Master UI mode (DS = companion overlays active; VANILLA = suppressed). */
    fun uiModeFlow(): StateFlow<UiMode> = uiModeFlow.asStateFlow()

    /** HUD element keys bulk-toggled by a DS/Vanilla mode switch. The crosshair is handled
     *  separately (ON in both modes) and so is NOT in this list. */
    private val MODE_HUD_KEYS = listOf(
        "hud_vitals", "hud_equipped", "hud_minimap", "hud_effects", "hud_sneak", "hud_enemy",
    )

    /** Set the master UI mode and persist. Also bulk-sets the native HUD element toggles to
     *  match the mode: DS hides every native top-screen HUD element (the companion draws them
     *  on the bottom screen), Vanilla shows them all; the Alpha3 overlay follows the same rule.
     *  The crosshair stays ON in both modes. Each write goes through setHudOn/setAlpha3Overlay,
     *  so the shared StateFlows update immediately — EngineActivity's collectors push the new
     *  values to the native JNI setters, and the options-menu rows (observing the same flows)
     *  refresh at once — and each value is persisted. Only an explicit mode switch resets these;
     *  init() loads the persisted per-element values on launch and never calls this. */
    fun setUiMode(context: Context, mode: UiMode) {
        uiModeFlow.value = mode
        editor(context).putString(UI_MODE, mode.name).apply()

        val on = mode == UiMode.VANILLA
        MODE_HUD_KEYS.forEach { setHudOn(context, it, on) }
        setHudOn(context, "hud_crosshair", true) // crosshair on in both DS and Vanilla
        setAlpha3Overlay(context, on)
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

    /** Route an element to [route] and persist. No-op for pending (locked) elements. */
    fun setRoute(context: Context, key: String, route: ScreenRoute) {
        val el = UI_ELEMENTS.firstOrNull { it.key == key } ?: return
        if (el.pending) return
        routeFlows.getValue(key).value = route
        editor(context).putString(ROUTE_PREFIX + key, route.name).apply()
    }

    /** Set an element's top-screen render style and persist. No-op for pending elements. */
    fun setStyle(context: Context, key: String, style: UiStyle) {
        val el = UI_ELEMENTS.firstOrNull { it.key == key } ?: return
        if (el.pending) return
        styleFlows.getValue(key).value = style
        editor(context).putString(STYLE_PREFIX + key, style.name).apply()
    }

    private fun editor(context: Context): SharedPreferences.Editor {
        val p = prefs ?: context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        return p.edit()
    }
}
