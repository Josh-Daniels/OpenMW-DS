package org.openmw.companion

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Which physical screen a UI element is drawn on. */
enum class ScreenRoute { TOP, BOTTOM }

/** How a top-screen element is rendered: vanilla OpenMW UI or the DS-styled replacement. */
enum class UiStyle { VANILLA, DS }

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
    UiElement("hud_crosshair", "Crosshair", UiSection.HUD, ScreenRoute.TOP, pending = true),
    UiElement("hud_sneak", "Sneak indicator", UiSection.HUD, ScreenRoute.TOP, pending = true),
    // ---- Menus and overlays ----
    UiElement("menu_conversation", "Conversation", UiSection.MENUS, ScreenRoute.TOP),
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

    private var prefs: SharedPreferences? = null

    private val routeFlows: Map<String, MutableStateFlow<ScreenRoute>> =
        UI_ELEMENTS.associate { it.key to MutableStateFlow(it.default) }
    private val styleFlows: Map<String, MutableStateFlow<UiStyle>> =
        UI_ELEMENTS.associate { it.key to MutableStateFlow(UiStyle.VANILLA) }

    // Input section: whether touch / thumbsticks drive the top-screen game cursor.
    // Default false (off). The actual cursor suppression lives in a native patch;
    // this only stores the preference.
    private val gameCursorFlow = MutableStateFlow(false)

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
    }

    fun routeFlow(key: String): StateFlow<ScreenRoute> = routeFlows.getValue(key).asStateFlow()
    fun styleFlow(key: String): StateFlow<UiStyle> = styleFlows.getValue(key).asStateFlow()

    /** Input: whether touch / thumbsticks control the top-screen game cursor. */
    fun gameCursorFlow(): StateFlow<Boolean> = gameCursorFlow.asStateFlow()

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
