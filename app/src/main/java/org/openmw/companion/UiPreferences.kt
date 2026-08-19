package org.openmw.companion

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the conversation UI is drawn.
 * - [BOTTOM]: original two-column layout entirely on the bottom screen.
 * - [SPLIT]: history on the top screen, topics/controls on the bottom.
 * - [TOP]: full conversation on the top screen (current default).
 *
 * NOTE: distinct from [GameUiMode]. This only chooses which screen the (DS) conversation is
 * drawn on; whether the companion draws conversation at all is the "game_ui_conversation"
 * [GameUiMode]. Both are chosen on the one Game Menus "Conversation" row; defaults to [TOP], and
 * the "All DS" quick-set forces it back to [TOP].
 */
enum class ConversationLocation { BOTTOM, SPLIT, TOP }

/**
 * Where a service UI (looting, bartering) is drawn.
 * - [BOTTOM]: original layout entirely on the bottom screen (default).
 * - [SPLIT]: an icon grid on the top screen, controls only on the bottom.
 * - [TOP]: the whole thing on the top screen (not yet implemented — the Top pill is
 *   greyed/pending in the menu; treated as [BOTTOM] until implemented).
 *
 * NOTE: distinct from [GameUiMode]. This only chooses which screen the (DS) service is
 * drawn on; whether the companion draws it at all is the element's [GameUiMode].
 */
enum class ScreenLocation { BOTTOM, SPLIT, TOP }

/**
 * How the two-panel item screens (looting/pickpocket, barter) render their item lists. CLASSIC =
 * the tab-filtered icon grid; SHELF = category "shelves" (labelled horizontal rows) with a
 * collapsible Equipped section. One switch drives ALL those contexts. Default CLASSIC.
 */
enum class InventoryLayout { CLASSIC, SHELF }

// Spells tab density (Standard / Compact) — REMOVED: the compact spell list is now the only
// version, so this enum + its pref plumbing (below) are commented out. Kept for reference.
// enum class SpellsListStyle { STANDARD, COMPACT }
//   STANDARD = original 40dp-icon rows; COMPACT = smaller icon + shorter rows (now always used).

/**
 * Layout of the single-panel Inventory TAB. DELIBERATELY separate from [InventoryLayout]
 * (Classic/Shelf, which governs the two-panel looting/bartering screens) — different part of the UI.
 * - [LIST]: original full-width row list (default; nothing changes for existing users).
 * - [CARDS]: wide/short condensed cards in a fixed-row grid, filled top-to-bottom then left-to-right,
 *   with horizontal scroll for more items.
 */
enum class InventoryTabStyle { LIST, CARDS }

/**
 * Where the combat target's health bar is drawn.
 * - [BOTTOM]: original behaviour — the bottom-screen HUD combat-target overlay.
 * - [TOP]: an additional top-centre overlay window on the top screen, shown while a combat
 *   target exists. The bottom-screen version is not drawn in this mode.
 */
enum class TargetHealthLocation { BOTTOM, TOP }

/**
 * Where the persuasion popup is drawn. Decoupled from the conversation location so persuasion can
 * live on a different screen than the conversation controls (same pattern as Repair / Travel).
 * - [BOTTOM]: a centred in-window popup on the bottom screen (hosted at the CompanionScreen root).
 * - [TOP]: an interactive top-screen panel window, shown while the persuasion popup is open.
 */
enum class PersuasionLocation { BOTTOM, TOP }

/**
 * Per-element rendering mode for a "Game UI" element (a menu/overlay the companion can take
 * over from native OpenMW).
 * - [DS]: the companion draws it on the bottom screen; the native top-screen version is suppressed.
 * - [VANILLA]: native OpenMW handles it on the top screen as normal. **Shown to the player as
 *   "Native"** — the constant keeps the name VANILLA because `name` is what gets persisted into
 *   SharedPreferences, so renaming it would orphan every stored setting.
 */
enum class GameUiMode { DS, VANILLA }

/**
 * One "Game UI" element in the options menu's GAME MENUS section, in display order.
 * [pending] elements have no companion (DS) replacement yet: they are locked to [VANILLA]
 * and their DS pill renders greyed with "not yet available".
 */
data class GameUiElement(
    val key: String,
    val label: String,
    val pending: Boolean = false,
) {
    /** First-launch default mode. The app ships in an "all Vanilla" state (native OpenMW handles
     *  every menu on the top screen) — the player opts into DS per element, or all at once via the
     *  All-DS quick-set. Pending elements are locked to VANILLA regardless. (This mirrors a fresh
     *  install immediately having "All Native" pressed.) */
    val defaultMode: GameUiMode get() = GameUiMode.VANILLA
}

/** Catalogue of every Game UI element, in display order — the single source of truth the
 *  GAME MENUS section renders from and [UiPreferences] persists. Non-pending entries get a
 *  merged [Native][Bottom][…] row; pending ones get a single muted "not yet available" line. */
val GAME_UI_ELEMENTS: List<GameUiElement> = listOf(
    GameUiElement("game_ui_conversation", "Conversation"),
    GameUiElement("game_ui_looting", "Looting"),
    GameUiElement("game_ui_bartering", "Bartering"),
    GameUiElement("game_ui_persuasion", "Persuasion"),
    // Repair + Rest/Wait have companion (DS) overlays (RepairOverlay / RestWaitOverlay +
    // companion-repair-export / companion-restwait-export patches), so they are non-pending
    // (default DS): the native GM_MerchantRepair / GM_Rest windows are suppressed and the bottom
    // screen is the sole surface. Spellmaking/Enchanting: native suppression is wired (companionDs*)
    // but no companion overlay exists yet, so they stay pending -> locked to VANILLA and the
    // suppression stays dormant (companionDs*() always false) until an overlay lands. See
    // companion-hide-gamewindows-on-dsmode.patch.
    GameUiElement("game_ui_repair", "Repair"),
    // Travel has a companion (DS) overlay (TravelOverlay + companion-travel-export /
    // companion-hide-travel-on-dsmode patches), so it is non-pending (default DS): the native
    // GM_Travel window is suppressed and the bottom screen is the sole surface.
    GameUiElement("game_ui_travel", "Travel"),
    // Level up is non-pending as of Aug 19 2026: LevelUpOverlay exists on the bottom screen and
    // DS suppresses the native GM_Levelup window via the already-wired companionDsLevelUp() gate.
    // The native LevelupDialog still owns selection state and the commit (CMP:levelup_* bridges) —
    // the DS side is presentation only.
    GameUiElement("game_ui_levelup", "Level up"),
    // Dialogue-service windows (GM_SpellBuying / GM_Training) — now non-pending (default DS): the
    // bottom-screen SpellBuyingOverlay / TrainingOverlayHost exist, and DS suppresses the native
    // window via companion-hide-gamewindows-on-dsmode.patch (setCompanionDsSpellBuying/Training →
    // companionDs*() atomics, driven by dsPushes in EngineActivity). Vanilla un-suppresses and shows
    // the real native window (the _spellBuyingWindowOpen/_trainingWindowOpen booleans still fire for
    // the conversation step-aside).
    GameUiElement("game_ui_spellbuying", "Spell buying"),
    GameUiElement("game_ui_training", "Training"),
    GameUiElement("game_ui_spellmaking", "Spellmaking", pending = true),
    GameUiElement("game_ui_enchanting", "Enchanting", pending = true),
    // Alchemy is non-pending as of Aug 19 2026: AlchemyOverlay (bottom) + AlchemyTopOverlay (top)
    // exist, and DS suppresses the native GM_Alchemy window via the already-wired companionDsAlchemy()
    // gate. The native AlchemyWindow still owns the whole mechanic — the combination rule and its
    // slot order, the session-sticky apparatus prefill, countPotionsToBrew(), the validation order
    // and the per-potion success roll (CMP:alchemy_* bridges). The DS side is presentation only.
    GameUiElement("game_ui_alchemy", "Alchemy"),
    GameUiElement("game_ui_restwait", "Rest / Wait"),
    // Crime "reported" alert: DS = a top-of-stack toast on the companion screen; Vanilla = the native
    // transient message (which renders bottom-center of the top screen, hidden behind DS panels).
    // Non-pending: the native side (windowmanagerimp) already gates on companionDsCrimeAlerts().
    GameUiElement("game_ui_crime", "Crime alerts"),
)

/**
 * One native top-screen HUD element that the "Top Screen" section can show/hide. [pending]
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
    // "Native target health bar", not "Target health": the Top Screen section also carries the DS
    // bar's own location row, and the two used to share a label while meaning different things.
    UiElement("hud_enemy", "Native target health bar"),
    UiElement("hud_crosshair", "Crosshair"),
    // The controller button-hint bar (bottom of the top screen). Gated natively in
    // WindowManager::updateControllerButtonsOverlay via companionHudControllerTooltips()
    // (companion-hud-elements.patch); pushed by EngineActivity like the other HUD toggles.
    UiElement("hud_controller_tooltips", "Controller tooltips"),
)

/**
 * Global (not per-character) UI settings: the per-element Game UI mode (DS/Vanilla), which native
 * HUD elements are visible, and the input/overlay toggles. Backed by SharedPreferences and exposed
 * as StateFlows so the options UI reacts live and rendering code can observe changes.
 *
 * A plain object so it survives Activity boundaries, matching [GameStateRepository].
 */
/**
 * Bounds and defaults for the two adaptive-dimming brightness sliders. Public because both the
 * options rows (slider `valueRange`) and the overlay's mapping read them, and they MUST agree — the
 * lower bound of the MIN range is the legibility guarantee described on
 * [UiPreferences.adaptiveDimMinBrightnessFlow].
 *
 * The two ranges deliberately do not overlap (min tops out at 0.50, max starts at 0.60), so no
 * combination of slider positions can invert the ramp and there is no crossing case to handle.
 */
/** Darkest the companion may get in the darkest scene. The floor is the readability cap. */
val ADAPTIVE_DIM_MIN_RANGE = 0.15f..0.50f
/** Brightest the companion stays in the brightest scene. 1f = untouched. */
val ADAPTIVE_DIM_MAX_RANGE = 0.60f..1.00f
/** Pre-slider behaviour: the ramp ended at a hardcoded 0.75 overlay opacity. */
const val ADAPTIVE_DIM_MIN_DEFAULT = 0.25f
/** Pre-slider behaviour: bright scenes were left completely clear. */
const val ADAPTIVE_DIM_MAX_DEFAULT = 1.00f

/**
 * TOP-screen adaptive dimming. ONE pair of sliders drives BOTH screens — the player sets the
 * dimming range once, in the ranges above — and the top screen re-projects those same positions
 * through the constants here before they become an alpha. There is no second stored setting and no
 * second pair of sliders; these are mapping targets, not preferences.
 *
 * TUNED ON DEVICE (Aug 2026), and the result was not what the original estimate assumed. The two
 * screens were first given DIFFERENT brightness bands on the theory that a top-screen panel, being
 * already thinned by the manual opacity slider and sitting over a dark scene, needs a gentler tint.
 * Comparing a DS panel on each screen at once (Conversation top vs Persuasion bottom) showed the
 * opposite: the top screen looked visibly UNDER-dimmed, and the screens only matched once its band
 * was widened to equal the bottom's. So both projections below are now IDENTITY, and the entire
 * difference between the screens has collapsed into [TOP_DIM_ABSOLUTE_MAX_ALPHA].
 *
 * The projection is kept (rather than folded away) because it is the seam any future retune needs,
 * and because the ceiling alone cannot express "reach the cap sooner" — which is what the shared
 * slider still does on the top screen.
 */
/**
 * The readability guarantee for the top screen, and the ONLY thing that now distinguishes it from
 * the bottom screen's mapping.
 *
 * WHERE 0.42 COMES FROM — it is the alpha the developer accepted as the edge of readability, not a
 * round number. Tuning was done outside at 2am, whose ambient the engine reports at luminance
 * ~0.127-0.137 (from the Weather_*_Ambient_Night_Color fallbacks, Rec.709), i.e. ramp position
 * t ~= 0.82-0.85 — NOT the end of the ramp. The measured alpha there was 0.411 (clear night)
 * to 0.427 (foggy). 0.42 sits inside that band.
 *
 * WHY A CAP WAS NEEDED AT ALL. A pitch-black INTERIOR reports luminance 0.08 — the engine's
 * `Shaders/minimum interior brightness` floor, which is exactly what [DIM_LUMINANCE_DARK] is
 * pinned to — so it reaches t = 1.0, further down the ramp than ANY night exterior can. Without
 * this cap a cave would have rendered at alpha 0.50: ~22% more opaque than the point already
 * described as borderline, in a scene tuning never visited.
 *
 * WHY IT IS A CLAMP AND NOT A NARROWER RANGE. Rescaling the band to end at 0.42 would have made
 * the 2am scene itself lighter than the look that was approved. Clamping leaves the approved curve
 * untouched everywhere below the cap and only refuses to go past it. Verified across every scene x
 * slider combination: the top screen's alpha never exceeds 0.42.
 *
 * NOTE the bottom screen's `MIN_RANGE.start = 1 - ceiling` invariant deliberately does NOT hold up
 * here, and must not be "fixed". There it exists so the slider's dark end is exactly reachable. On
 * the top screen the ceiling is MEANT to be the binding constraint: the floor below sets how
 * quickly the cap is reached, and the cap sets how dark it may ever get.
 */
const val TOP_DIM_ABSOLUTE_MAX_ALPHA = 0.42f
/** Identity with [ADAPTIVE_DIM_MIN_RANGE] — see above; the screens matched only at parity. The
 *  slider still does real work up top: at darker positions the [TOP_DIM_ABSOLUTE_MAX_ALPHA] cap is
 *  reached in progressively less dark scenes. */
val TOP_ADAPTIVE_DIM_MIN_RANGE = 0.15f..0.50f
/** Identity with [ADAPTIVE_DIM_MAX_RANGE]. The bright end was never observable during tuning (at
 *  the default "Brightest" position the projection returns this range's upper bound whatever its
 *  start is), so it is set to parity with the bottom screen — the one relationship the tuning DID
 *  establish — rather than to a value that was changed but never actually seen. */
val TOP_ADAPTIVE_DIM_MAX_RANGE = 0.60f..1.00f

/**
 * Level of the companion's own interface sounds, as a fraction of the device's media volume.
 *
 * Full range: unlike the adaptive-dimming sliders there is no floor to defend here — silencing the
 * sounds entirely is a legitimate thing to want, and the master toggle is simply the faster way to
 * get there.
 *
 * **Defaults to the middle of the slider.** These cues ride the same stream as the game's audio and
 * are meant to confirm a tap landed, not compete with the soundtrack.
 *
 * **This is a fraction of `UiSounds.UI_SOUND_MAX_GAIN`, not of full scale** — the slider's 100% is
 * that ceiling. The default was 0.2 against a ceiling of 1.0 until Aug 10 2026, when the ceiling
 * dropped to 0.4 (the top of the old range was unusable, so most of the travel was wasted) and the
 * default rose to 0.5 to keep the actual level identical: 0.5 x 0.4 = the previous 0.2 x 1.0. Change
 * the two together or the shipped loudness moves.
 *
 * Read by BOTH the flow initialiser and the load fallback, so this one constant is the whole default.
 */
val UI_SOUND_VOLUME_RANGE = 0f..1f
const val UI_SOUND_VOLUME_DEFAULT = 0.5f

/**
 * Range for the exterior NIGHT ambient lift ("Night brightness"), expressed in the same Rec.709
 * relative-luminance units the engine's own interior floor uses, so the two sliders in the Display
 * section speak one language and their numbers are directly comparable.
 *
 * Measured across the ten vanilla weathers, night ambient luminance spans 0.126 (Blight) to 0.213
 * (Snow). The 0.20 ceiling therefore roughly doubles the darkest nights at most — enough to be
 * clearly useful without washing the scene out. 0 is exact vanilla, so the bottom of the slider is
 * a true off.
 */
val NIGHT_BRIGHTNESS_RANGE = 0f..0.20f

/** Conservative default: about +29% luminance on a clear night (0.174 -> 0.224). Visible, but well
 *  short of flattening the moonlit look. The slider goes further for anyone who wants it. */
const val NIGHT_BRIGHTNESS_DEFAULT = 0.05f

/**
 * Range for OpenMW's own `Shaders/minimum interior brightness`. This is a pass-through to the
 * engine setting, NOT a companion invention, so the value stored here is the literal engine value
 * and [MINIMUM_INTERIOR_BRIGHTNESS_DEFAULT] is the engine's own shipped default.
 */
val INTERIOR_BRIGHTNESS_RANGE = 0f..0.35f

/** OpenMW's shipped default for `Shaders/minimum interior brightness` (files/settings-default.cfg).
 *  Keep in step with the engine if it ever changes upstream. */
const val MINIMUM_INTERIOR_BRIGHTNESS_DEFAULT = 0.08f

/** Most HUD favourite quick-slots a category can show. Also the number PERSISTED per category —
 *  lowering the visible count hides slots rather than deleting them, so this is the storage width
 *  regardless of what is on screen. See [FavouritesRepository]. */
const val FAV_SLOTS_MAX = 4

/** Shipped favourite slot count per category. 2 reproduces the fixed 2 + 2 the HUD had before the
 *  count became configurable, so a fresh install looks exactly as it always did. */
const val FAV_SLOTS_DEFAULT = 2

object UiPreferences {
    private const val PREFS = "companion_ui_settings"
    private const val GAME_UI_PREFIX = "" // keys already carry the "game_ui_" prefix
    private const val GAME_CURSOR = "game_cursor"
    private const val TOUCH_INPUT = "touch_input"
    // Snapshot of the last hand-made ("Custom") Game UI layout, so [Custom] can restore it after
    // switching to All DS / All Native. Stored as "key=MODE,key=MODE,…" of non-pending elements.
    private const val GAME_UI_CUSTOM = "game_ui_custom"
    private const val CONVERSATION_LOCATION = "conversation_location"
    private const val INVENTORY_LAYOUT = "inventory_layout"
    // private const val SPELLS_LIST_STYLE = "spells_list_style"  // removed (compact-only)
    private const val INVENTORY_TAB_STYLE = "inventory_tab_style"
    private const val HIDE_EQUIPPED_BAR = "hide_equipped_bar"
    private const val SHOW_EQUIPPED_IN_LIST = "show_equipped_in_list"
    private const val ADAPTIVE_DIMMING = "adaptive_dimming"
    // The two ends of the adaptive-dimming ramp, expressed as SCREEN BRIGHTNESS (1f = untouched,
    // 0f = black) rather than overlay opacity, because that is what the options rows show the
    // player. MIN = how dark the screen is allowed to get in the darkest scene; MAX = how bright it
    // stays in the brightest scene. Stored as floats; see the ADAPTIVE_DIM_*_RANGE bounds below,
    // which are what keeps the darkest setting short of unreadable.
    private const val ADAPTIVE_DIM_MIN_BRIGHTNESS = "adaptive_dim_min_brightness"
    private const val ADAPTIVE_DIM_MAX_BRIGHTNESS = "adaptive_dim_max_brightness"
    private const val JOURNAL_PAGE_TURN = "journal_page_turn"
    private const val EFFECT_TIMERS = "effect_timers"
    // Master switch + level for the companion's own interface sounds (keyboard, options pills and
    // sliders, Developer Tools buttons). See UiSounds.
    private const val UI_SOUNDS = "ui_sounds"
    private const val UI_SOUND_VOLUME = "ui_sound_volume"
    private const val NIGHT_BRIGHTNESS = "exterior_night_brightness"
    private const val INTERIOR_BRIGHTNESS = "minimum_interior_brightness"
    private const val FAV_GEAR_SLOTS = "fav_gear_slots"
    private const val FAV_MAGIC_SLOTS = "fav_magic_slots"
    private const val VANILLA_FONT = "vanilla_font"
    private const val DEVELOPER_MODE = "developer_mode"
    private const val LOOTING_LOCATION = "layout_looting"
    private const val BARTER_LOCATION = "layout_bartering"
    // Training / spell-buying popup location (Bottom only for now; Top pending — same as Repair,
    // which is Bottom/Top). There is NO Split for these two (only the centred bottom card is built),
    // so the menu offers just [Bottom][Top]. Default BOTTOM. A stale SPLIT persisted by the earlier
    // build is rejected on load (see init) so it can't leave the pills showing nothing selected.
    private const val TRAINING_LOCATION = "layout_training"
    private const val SPELLBUYING_LOCATION = "layout_spellbuying"
    // Repair / travel popup location — same [Bottom][Top] shape as training/spell-buying (Bottom
    // built, Top pending, no Split). Default BOTTOM. These overlays previously followed the
    // Conversation location; they now read their own pref (a stale SPLIT is rejected on load).
    private const val REPAIR_LOCATION = "layout_repair"
    private const val TRAVEL_LOCATION = "layout_travel"
    // Rest/Wait + crime-alert location — same [Bottom][Top] shape again (Bottom built, Top pending).
    // NOTE both overlays (RestWaitOverlay, CrimeToast) are currently hardcoded to the bottom screen
    // and do NOT read these flows, so today these prefs are presentational only: they move both rows
    // out of the greyed "PENDING" fallback into the same Bottom-selected/Top-pending form the other
    // services use. Wire the overlays to these flows when a Top variant is actually built.
    private const val RESTWAIT_LOCATION = "layout_restwait"
    private const val CRIME_LOCATION = "layout_crime"
    // Background-fill opacity (0f..1f) of DS overlay panels drawn on the TOP screen, so the game
    // can be seen through them. Default 1f = fully opaque, i.e. no visual change until the player
    // moves the slider. BOTTOM-screen companion panels are deliberately NOT affected (nothing is
    // behind them; Adaptive Dimming is the separate bottom-screen concept — do not conflate).
    private const val TOP_PANEL_OPACITY = "top_panel_opacity"
    private const val TARGET_HEALTH_LOCATION = "layout_target_health"
    // Persuasion popup location (Bottom / Top — both implemented, unlike the pending service rows).
    private const val PERSUASION_LOCATION = "layout_persuasion"
    private const val PLAYER_COMBAT = "layout_player_combat"
    private const val HUD_ON_PREFIX = "hud_on_"
    private const val ALPHA3_OVERLAY = "alpha3_overlay"

    // The controller button-hint bar is a native (Native HUD) element, but it only makes sense
    // alongside native menus, so it follows the DS/Native quick-set: All DS hides it, All Native
    // shows it. See [setAllGameUi].
    private const val CONTROLLER_TOOLTIPS_KEY = "hud_controller_tooltips"

    private var prefs: SharedPreferences? = null

    // Per-element Game UI mode (DS = companion draws it; VANILLA = native handles it). Pending
    // elements are locked to their VANILLA default and never persisted/changed.
    private val gameUiModeFlows: Map<String, MutableStateFlow<GameUiMode>> =
        GAME_UI_ELEMENTS.associate { it.key to MutableStateFlow(it.defaultMode) }

    // True while a saved "Custom" Game UI snapshot exists (drives whether the [Custom] quick-set pill
    // is tappable). Set when a hand-made mixed layout is snapshotted; loaded in init.
    private val customSnapshotFlow = MutableStateFlow(false)

    // Guards the per-element snapshot save so a bulk setAllGameUi() doesn't snapshot its own transient
    // mid-loop mixed states. Main-thread only.
    private var bulkGameUi = false

    // Where the conversation UI is drawn (BOTTOM / SPLIT / TOP). Default TOP. This MutableStateFlow
    // init IS the fresh-install fallback: the load below passes null to getString and only overrides
    // when a value is actually stored, so there is no second default site.
    private val conversationLocationFlow = MutableStateFlow(ConversationLocation.TOP)

    // Item-list layout for the two-panel screens (looting/pickpocket, barter): Classic grid vs
    // Shelf. One switch, all those contexts. Default CLASSIC (the proven layout).
    private val inventoryLayoutFlow = MutableStateFlow(InventoryLayout.CLASSIC)

    // Spells tab density flow — removed (compact-only). Kept commented for reference.
    // private val spellsListStyleFlow = MutableStateFlow(SpellsListStyle.STANDARD)

    // Layout of the single-panel Inventory tab (List / Cards). Default CARDS. Unrelated to
    // inventoryLayoutFlow (Classic/Shelf) above.
    private val inventoryTabStyleFlow = MutableStateFlow(InventoryTabStyle.CARDS)

    // Whether the pinned "Equipped (N)" drop-down bar at the bottom of the Inventory tab is hidden
    // (freeing space for an extra row of items). Default true (hidden) — worn items show inline
    // (showEquippedInList default true) and via the Equipped tab.
    private val hideEquippedBarFlow = MutableStateFlow(true)

    // Whether the companion screen dims itself to match how dark the game scene is. The bottom
    // screen renders UI at a fixed brightness, so at equal manual brightness it looks much
    // brighter than the top screen once the player is somewhere dark. Driven by the native
    // ambient-luminance signal (GameStateRepository.ambientLuminance); purely a translucent black
    // overlay — it never touches the device's real screen brightness. Default true (on).
    private val adaptiveDimmingFlow = MutableStateFlow(true)

    // How dark the companion is allowed to get in the DARKEST scene, as screen brightness
    // (1f = undimmed). Default 0.25f reproduces exactly the behaviour before these sliders existed,
    // where the ramp ended at a hardcoded 0.75 overlay opacity.
    //
    // THE LOWER BOUND IS THE LEGIBILITY GUARANTEE, not a taste default: this overlay sits above
    // every other companion layer (popups, toasts), so nothing can punch back through it, and a
    // player who drags this to the bottom in a pitch-dark cave must still be able to read the
    // screen. 0.15f is one modest step darker than the tested-and-approved 0.25f. It is enforced in
    // three places on purpose — the slider's range, the clamp in this setter, and the clamp in
    // [AdaptiveDimOverlay]'s mapping — so neither a corrupt stored value nor a future call site can
    // get past it. Do not lower it to "let people go darker"; turning the feature off is the
    // supported way to want something else.
    private val adaptiveDimMinBrightnessFlow = MutableStateFlow(ADAPTIVE_DIM_MIN_DEFAULT)

    // How bright the companion stays in the BRIGHTEST scene, as screen brightness. Default 1f =
    // fully clear outdoors, i.e. the pre-slider behaviour. Lowering it applies a baseline dim that
    // is present even in daylight, for players who find the bottom screen too bright everywhere.
    private val adaptiveDimMaxBrightnessFlow = MutableStateFlow(ADAPTIVE_DIM_MAX_DEFAULT)

    // Whether the journal's chronological view turns pages as a spine-hinged 3D leaf instead of the
    // pager's plain horizontal slide. Purely visual — the two-column spread, the swipe gesture and
    // the page grouping are identical either way. **Default TRUE since Aug 9 2026**: it shipped OFF
    // while the effect was unfinished (the leaf was visibly clipped by the panel), and was turned on
    // by default once the overlay rewrite landed and it was approved on device. Change at BOTH this
    // init and the getBoolean load fallback.
    private val journalPageTurnFlow = MutableStateFlow(true)

    // Whether a remaining-duration countdown is shown beside each TIMED active effect (HUD effects
    // dropdown, Stats page list, effect detail popup). Permanent effects — abilities, diseases,
    // constant-effect worn enchantments — never show one regardless, because the exporter omits
    // the value for them entirely; this switch only governs the timed ones.
    //
    // Default TRUE, DELIBERATELY unlike OpenMW's own `show effect duration` setting (default
    // false). That default is a vanilla-fidelity choice for a HUD that has no room for it; the
    // companion screen exists precisely to show more than the game does out of the box, so this
    // ships visible and the switch is there for players who want the plainer list. Change at BOTH
    // this init and the getBoolean load fallback.
    private val effectTimersFlow = MutableStateFlow(true)
    // Interface sounds. Default ON — the feature exists because Developer Tools buttons gave no
    // sign a tap had registered, so shipping it off by default would leave that unfixed for anyone
    // who never finds the row.
    private val uiSoundsFlow = MutableStateFlow(true)
    private val uiSoundVolumeFlow = MutableStateFlow(UI_SOUND_VOLUME_DEFAULT)
    private val nightBrightnessFlow = MutableStateFlow(NIGHT_BRIGHTNESS_DEFAULT)
    private val interiorBrightnessFlow = MutableStateFlow(MINIMUM_INTERIOR_BRIGHTNESS_DEFAULT)
    private val favGearSlotsFlow = MutableStateFlow(FAV_SLOTS_DEFAULT)
    private val favMagicSlotsFlow = MutableStateFlow(FAV_SLOTS_DEFAULT)

    // Whether the companion + DS overlays render in the game's own typeface instead of the Android
    // system serif/monospace. The face is MysticCards.ttf — OpenMW's SIL-OFL replacement for
    // Morrowind's Magic Cards, already bundled in the APK under
    // assets/libopenmw/resources/vfs/fonts/. NOT Morrowind's own font: that ships as a fixed 16 px
    // BITMAP atlas (.fnt + .tex), which cannot be an Android font resource at all, and which the
    // bottom screen would have to enlarge (a 14 sp line is 32 px at this 369 dpi panel) — so the
    // TTF lookalike is both the only practical option and the sharper one. Default TRUE: matching
    // the game's own typeface is the intended look, so it is on out of the box and the switch
    // exists to turn it OFF.
    private val vanillaFontFlow = MutableStateFlow(true)

    // Whether the Developer Tools action panel (add gold, max stats, god mode, …) is shown. These
    // are cheats and can change a save in ways normal play cannot, so this gates them behind a
    // deliberate opt-in rather than putting the buttons one tap from the pause menu. Default FALSE
    // — a fresh install never shows the panel. Persisted like any other pref, so it survives a
    // restart once turned on (turning it back off just hides the panel; nothing is undone).
    private val developerModeFlow = MutableStateFlow(false)

    // Whether equipped (worn) items are ALSO shown inline in the Inventory "All" list. Independent of
    // the bar: worn items are always reachable via the "Equipped" filter tab and/or the bar. Default
    // true (worn items listed inline, floated to the front of their section).
    private val showEquippedInListFlow = MutableStateFlow(true)

    // Where the looting / bartering service UIs are drawn (BOTTOM / SPLIT / TOP). Default SPLIT
    // (icon grid on top, controls on the bottom). TOP is pending — the menu greys that pill.
    private val lootingLocationFlow = MutableStateFlow(ScreenLocation.SPLIT)
    private val barterLocationFlow = MutableStateFlow(ScreenLocation.SPLIT)
    private val trainingLocationFlow = MutableStateFlow(ScreenLocation.BOTTOM)
    private val spellBuyingLocationFlow = MutableStateFlow(ScreenLocation.BOTTOM)
    private val repairLocationFlow = MutableStateFlow(ScreenLocation.BOTTOM)
    private val travelLocationFlow = MutableStateFlow(ScreenLocation.BOTTOM)
    private val restwaitLocationFlow = MutableStateFlow(ScreenLocation.BOTTOM)
    private val crimeLocationFlow = MutableStateFlow(ScreenLocation.BOTTOM)
    private val topPanelOpacityFlow = MutableStateFlow(1f)

    // Where the combat target's health bar is drawn (BOTTOM / TOP). Default TOP.
    private val targetHealthLocationFlow = MutableStateFlow(TargetHealthLocation.TOP)
    private val persuasionLocationFlow = MutableStateFlow(PersuasionLocation.BOTTOM)

    // Whether the player's vitals (health/magicka/fatigue) ALSO show on the top screen during
    // combat. Default true (also shown on the top screen).
    private val playerCombatFlow = MutableStateFlow(true)

    // HUD elements are always drawn on the bottom screen by the companion; this Boolean toggles
    // whether the NATIVE top-screen version is visible (true = On/visible, false = Off/hidden).
    // Keyed by HUD element key. Default Off for every element EXCEPT the crosshair (On) — the
    // companion draws the rest on the bottom screen. Actual native hiding is implemented separately.
    private val hudFlows: Map<String, MutableStateFlow<Boolean>> =
        HUD_ELEMENTS.associate { it.key to MutableStateFlow(hudDefaultOn(it.key)) }

    // Input section: whether a visible top-screen game cursor exists and can be steered with the
    // thumbstick. Default false (off). The actual cursor suppression lives in a native patch; this
    // only stores the preference. INDEPENDENT of touchInputFlow below — see setGameCursor.
    private val gameCursorFlow = MutableStateFlow(false)

    // Input section: direct touch-to-click on the top screen while a menu (GUI mode) is open —
    // tap a spot = a mouse click there. Default true (on). This only stores the preference; the
    // touch handler reads it to decide whether to inject the direct click. What it uniquely buys is
    // tapping while the cursor is HIDDEN: with the game cursor on, taps already reach the engine
    // through the ordinary visible-cursor path. INDEPENDENT of gameCursorFlow — see setTouchInput.
    private val touchInputFlow = MutableStateFlow(true)

    // Whether the Alpha3 launcher overlay (gear + arrow cluster) is shown. Default true (shown on
    // first launch). Purely Kotlin-side (gates a composable in EngineActivity); no native involvement.
    private val alpha3OverlayFlow = MutableStateFlow(false)

    /** Default On/Off for a Native HUD element on first launch. The crosshair and the controller
     *  button-hint bar default On (the app ships in the all-Vanilla state, which shows the hint bar
     *  alongside the native menus); the companion draws every other HUD element on the bottom
     *  screen, so their native versions default Off. */
    private fun hudDefaultOn(key: String): Boolean = key == "hud_crosshair" || key == CONTROLLER_TOOLTIPS_KEY

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
        customSnapshotFlow.value = !p.getString(GAME_UI_CUSTOM, null).isNullOrEmpty()
        // If the loaded layout is already a Custom mix, make sure a snapshot exists (covers configs
        // made before this feature), so [Custom] is immediately tappable rather than dim-highlighted.
        if (gameUiIsMixed()) saveCustomSnapshot(context)
        // Game cursor and touch input load verbatim — either or both on are all valid states. The
        // one invalid state is BOTH OFF (no top-screen pointer input at all), which the setters
        // prevent; reconcile it here too, since a config persisted while both-off was briefly
        // reachable must not come back as a lockout. Touch input is the one turned back on (it is
        // the shipped default, and unlike the cursor it doesn't change what A does in native menus).
        gameCursorFlow.value = p.getBoolean(GAME_CURSOR, false)
        touchInputFlow.value = p.getBoolean(TOUCH_INPUT, true)
        if (!gameCursorFlow.value && !touchInputFlow.value) {
            touchInputFlow.value = true
            p.edit().putBoolean(TOUCH_INPUT, true).apply()
        }
        p.getString(CONVERSATION_LOCATION, null)
            ?.let { runCatching { ConversationLocation.valueOf(it) }.getOrNull() }
            ?.let { conversationLocationFlow.value = it }
        p.getString(INVENTORY_LAYOUT, null)
            ?.let { runCatching { InventoryLayout.valueOf(it) }.getOrNull() }
            ?.let { inventoryLayoutFlow.value = it }
        // Spells tab density load — removed (compact-only):
        // p.getString(SPELLS_LIST_STYLE, null)
        //     ?.let { runCatching { SpellsListStyle.valueOf(it) }.getOrNull() }
        //     ?.let { spellsListStyleFlow.value = it }
        p.getString(INVENTORY_TAB_STYLE, null)
            ?.let { runCatching { InventoryTabStyle.valueOf(it) }.getOrNull() }
            ?.let { inventoryTabStyleFlow.value = it }
        hideEquippedBarFlow.value = p.getBoolean(HIDE_EQUIPPED_BAR, true)
        showEquippedInListFlow.value = p.getBoolean(SHOW_EQUIPPED_IN_LIST, true)
        adaptiveDimmingFlow.value = p.getBoolean(ADAPTIVE_DIMMING, true)
        // Clamped on read as well as on write (same reasoning as topPanelOpacity below): a stored
        // value from a corrupt prefs file, or from a build whose ranges differed, must never be able
        // to produce a darker overlay than the legibility floor allows.
        adaptiveDimMinBrightnessFlow.value =
            p.getFloat(ADAPTIVE_DIM_MIN_BRIGHTNESS, ADAPTIVE_DIM_MIN_DEFAULT)
                .coerceIn(ADAPTIVE_DIM_MIN_RANGE)
        adaptiveDimMaxBrightnessFlow.value =
            p.getFloat(ADAPTIVE_DIM_MAX_BRIGHTNESS, ADAPTIVE_DIM_MAX_DEFAULT)
                .coerceIn(ADAPTIVE_DIM_MAX_RANGE)
        journalPageTurnFlow.value = p.getBoolean(JOURNAL_PAGE_TURN, true)
        effectTimersFlow.value = p.getBoolean(EFFECT_TIMERS, true)
        uiSoundsFlow.value = p.getBoolean(UI_SOUNDS, true)
        uiSoundVolumeFlow.value = p.getFloat(UI_SOUND_VOLUME, UI_SOUND_VOLUME_DEFAULT)
            .coerceIn(UI_SOUND_VOLUME_RANGE)
        nightBrightnessFlow.value =
            p.getFloat(NIGHT_BRIGHTNESS, NIGHT_BRIGHTNESS_DEFAULT).coerceIn(NIGHT_BRIGHTNESS_RANGE)
        interiorBrightnessFlow.value =
            p.getFloat(INTERIOR_BRIGHTNESS, MINIMUM_INTERIOR_BRIGHTNESS_DEFAULT)
                .coerceIn(INTERIOR_BRIGHTNESS_RANGE)
        favGearSlotsFlow.value =
            p.getInt(FAV_GEAR_SLOTS, FAV_SLOTS_DEFAULT).coerceIn(0, FAV_SLOTS_MAX)
        favMagicSlotsFlow.value =
            p.getInt(FAV_MAGIC_SLOTS, FAV_SLOTS_DEFAULT).coerceIn(0, FAV_SLOTS_MAX)
        vanillaFontFlow.value = p.getBoolean(VANILLA_FONT, true)
        developerModeFlow.value = p.getBoolean(DEVELOPER_MODE, false)
        p.getString(LOOTING_LOCATION, null)
            ?.let { runCatching { ScreenLocation.valueOf(it) }.getOrNull() }
            ?.let { lootingLocationFlow.value = it }
        p.getString(BARTER_LOCATION, null)
            ?.let { runCatching { ScreenLocation.valueOf(it) }.getOrNull() }
            ?.let { barterLocationFlow.value = it }
        // Only BOTTOM / TOP are valid for these two (no Split). Reject a stale SPLIT from the earlier
        // build so it falls back to the BOTTOM default.
        p.getString(TRAINING_LOCATION, null)
            ?.let { runCatching { ScreenLocation.valueOf(it) }.getOrNull() }
            ?.takeIf { it != ScreenLocation.SPLIT }
            ?.let { trainingLocationFlow.value = it }
        p.getString(SPELLBUYING_LOCATION, null)
            ?.let { runCatching { ScreenLocation.valueOf(it) }.getOrNull() }
            ?.takeIf { it != ScreenLocation.SPLIT }
            ?.let { spellBuyingLocationFlow.value = it }
        p.getString(REPAIR_LOCATION, null)
            ?.let { runCatching { ScreenLocation.valueOf(it) }.getOrNull() }
            ?.takeIf { it != ScreenLocation.SPLIT }
            ?.let { repairLocationFlow.value = it }
        p.getString(TRAVEL_LOCATION, null)
            ?.let { runCatching { ScreenLocation.valueOf(it) }.getOrNull() }
            ?.takeIf { it != ScreenLocation.SPLIT }
            ?.let { travelLocationFlow.value = it }
        p.getString(RESTWAIT_LOCATION, null)
            ?.let { runCatching { ScreenLocation.valueOf(it) }.getOrNull() }
            ?.takeIf { it != ScreenLocation.SPLIT }
            ?.let { restwaitLocationFlow.value = it }
        p.getString(CRIME_LOCATION, null)
            ?.let { runCatching { ScreenLocation.valueOf(it) }.getOrNull() }
            ?.takeIf { it != ScreenLocation.SPLIT }
            ?.let { crimeLocationFlow.value = it }
        // Clamped on read as well as on write: a corrupt/out-of-range stored value must not produce
        // an invalid alpha (Compose throws on alpha outside 0..1).
        topPanelOpacityFlow.value = p.getFloat(TOP_PANEL_OPACITY, 1f).coerceIn(0f, 1f)
        p.getString(TARGET_HEALTH_LOCATION, null)
            ?.let { runCatching { TargetHealthLocation.valueOf(it) }.getOrNull() }
            ?.let { targetHealthLocationFlow.value = it }
        p.getString(PERSUASION_LOCATION, null)
            ?.let { runCatching { PersuasionLocation.valueOf(it) }.getOrNull() }
            ?.let { persuasionLocationFlow.value = it }
        playerCombatFlow.value = p.getBoolean(PLAYER_COMBAT, true)
        HUD_ELEMENTS.forEach { el ->
            hudFlows.getValue(el.key).value = p.getBoolean(HUD_ON_PREFIX + el.key, hudDefaultOn(el.key))
        }
        alpha3OverlayFlow.value = p.getBoolean(ALPHA3_OVERLAY, false)
    }

    /** The DS/Vanilla mode for a Game UI element (e.g. "game_ui_looting"). */
    fun gameUiModeFlow(key: String): StateFlow<GameUiMode> = gameUiModeFlows.getValue(key).asStateFlow()

    /** Set a Game UI element's mode and persist. No-op for pending (locked) elements. */
    fun setGameUiMode(context: Context, key: String, mode: GameUiMode) {
        val el = GAME_UI_ELEMENTS.firstOrNull { it.key == key } ?: return
        if (el.pending) return
        gameUiModeFlows.getValue(key).value = mode
        editor(context).putString(GAME_UI_PREFIX + key, mode.name).apply()
        // An individual row change that leaves the layout MIXED is a "Custom" layout — snapshot it so
        // [Custom] can restore it after the user later switches to All DS / All Native. Skipped
        // during a bulk setAllGameUi() (its mid-loop states are transient, and its final state is
        // uniform anyway, so the snapshot keeps holding the last real mix).
        if (!bulkGameUi && gameUiIsMixed()) saveCustomSnapshot(context)
    }

    /** Bulk-set every non-pending Game UI element to [mode] (the "All DS" / "All Native" quick-set
     *  buttons). Pending elements stay locked to VANILLA. Also flips the controller button-hint bar
     *  ([CONTROLLER_TOOLTIPS_KEY]): DS -> Off, Vanilla -> On (only useful when navigating native
     *  menus). DS additionally forces the Conversation Screen Layout to TOP (the other per-window
     *  layouts — Repair/Travel/Spell buying/Training/Persuasion — are left at their own settings).
     *  The Input section (Touch input / Game cursor) is deliberately NOT touched by either preset —
     *  it's the player's own choice and survives a quick-set. The Item List Style (Classic/Shelf) is
     *  on that same side of the line as of Aug 2026: All DS used to force SHELF, which silently
     *  overwrote a deliberate Classic choice AND made Shelf the effective default even though the
     *  stored default is CLASSIC. All other Native HUD toggles are left untouched too; individual
     *  rows can still be overridden afterwards. */
    fun setAllGameUi(context: Context, mode: GameUiMode) {
        // Snapshot the current layout first if it's a Custom mix, so [Custom] can restore it even if
        // it wasn't captured by an earlier individual change. (No-op if the snapshot already matches.)
        if (gameUiIsMixed()) saveCustomSnapshot(context)
        bulkGameUi = true
        GAME_UI_ELEMENTS.filter { !it.pending }.forEach { setGameUiMode(context, it.key, mode) }
        bulkGameUi = false
        setHudOn(context, CONTROLLER_TOOLTIPS_KEY, on = mode == GameUiMode.VANILLA)
        // All DS also drops the DS conversation onto the top screen (Conversation layout -> TOP).
        // It does NOT touch the Item List Style any more — see the KDoc; Classic is the default and
        // a player who picked Shelf keeps it. All Native changes nothing beyond the elements.
        if (mode == GameUiMode.DS) {
            setConversationLocation(context, ConversationLocation.TOP)
        }
    }

    /** Whether a saved "Custom" Game UI layout exists (backs the [Custom] quick-set pill's enabled
     *  state). */
    fun customSnapshotFlow(): StateFlow<Boolean> = customSnapshotFlow.asStateFlow()

    /** True when the non-pending Game UI elements are a mix of DS and Vanilla (not all one mode). */
    private fun gameUiIsMixed(): Boolean {
        val modes = GAME_UI_ELEMENTS.filter { !it.pending }.map { gameUiModeFlows.getValue(it.key).value }
        return modes.isNotEmpty() && !modes.all { it == GameUiMode.DS } && !modes.all { it == GameUiMode.VANILLA }
    }

    /** Persist the current non-pending element modes as the Custom snapshot. */
    private fun saveCustomSnapshot(context: Context) {
        val snapshot = GAME_UI_ELEMENTS.filter { !it.pending }
            .joinToString(",") { "${it.key}=${gameUiModeFlows.getValue(it.key).value.name}" }
        editor(context).putString(GAME_UI_CUSTOM, snapshot).apply()
        customSnapshotFlow.value = true
    }

    /** Re-apply the saved Custom snapshot (the [Custom] quick-set pill). No-op if none saved. Does
     *  not touch input mode / HUD — only the per-element Game UI layout. */
    fun restoreCustomGameUi(context: Context) {
        val snapshot = (prefs ?: return).getString(GAME_UI_CUSTOM, null)?.takeIf { it.isNotEmpty() } ?: return
        bulkGameUi = true
        snapshot.split(",").forEach { entry ->
            val eq = entry.indexOf('=')
            if (eq > 0) {
                val key = entry.substring(0, eq)
                runCatching { GameUiMode.valueOf(entry.substring(eq + 1)) }.getOrNull()
                    ?.let { setGameUiMode(context, key, it) }
            }
        }
        bulkGameUi = false
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

    /** Input: whether a tap on the top screen directly clicks there while a menu is open. */
    fun touchInputFlow(): StateFlow<Boolean> = touchInputFlow.asStateFlow()

    /** Enable/disable direct touch-to-click and persist. Mostly independent of [setGameCursor] —
     *  this one governs whether a tap CLICKS, that one governs whether a steerable cursor is DRAWN,
     *  and either or BOTH may be on. The single constraint is that **at least one must stay on**:
     *  with both off there is no top-screen pointer input at all, which reads as a lockout rather
     *  than a setting. So turning this off while the cursor is also off turns the cursor ON. */
    fun setTouchInput(context: Context, enabled: Boolean) {
        touchInputFlow.value = enabled
        editor(context).putBoolean(TOUCH_INPUT, enabled).apply()
        if (!enabled && !gameCursorFlow.value) setGameCursor(context, true)
    }

    /** Whether the Alpha3 launcher overlay (gear + arrow cluster) is shown. */
    fun alpha3OverlayFlow(): StateFlow<Boolean> = alpha3OverlayFlow.asStateFlow()

    /** Show/hide the Alpha3 launcher overlay and persist. */
    fun setAlpha3Overlay(context: Context, shown: Boolean) {
        alpha3OverlayFlow.value = shown
        editor(context).putBoolean(ALPHA3_OVERLAY, shown).apply()
    }

    /** Item-list layout (CLASSIC grid / SHELF) for looting + barter — one shared switch. */
    fun inventoryLayoutFlow(): StateFlow<InventoryLayout> = inventoryLayoutFlow.asStateFlow()

    /** Set the inventory layout and persist. */
    fun setInventoryLayout(context: Context, layout: InventoryLayout) {
        inventoryLayoutFlow.value = layout
        editor(context).putString(INVENTORY_LAYOUT, layout.name).apply()
    }

    // Spells tab density accessor + setter — removed (compact-only). Kept commented for reference.
    // fun spellsListStyleFlow(): StateFlow<SpellsListStyle> = spellsListStyleFlow.asStateFlow()
    // fun setSpellsListStyle(context: Context, style: SpellsListStyle) {
    //     spellsListStyleFlow.value = style
    //     editor(context).putString(SPELLS_LIST_STYLE, style.name).apply()
    // }

    /** Layout of the single-panel Inventory tab (List / Cards). */
    fun inventoryTabStyleFlow(): StateFlow<InventoryTabStyle> = inventoryTabStyleFlow.asStateFlow()

    /** Set the Inventory tab layout and persist. */
    fun setInventoryTabStyle(context: Context, style: InventoryTabStyle) {
        inventoryTabStyleFlow.value = style
        editor(context).putString(INVENTORY_TAB_STYLE, style.name).apply()
    }

    /** Whether the pinned "Equipped (N)" bar at the bottom of the Inventory tab is hidden. */
    fun hideEquippedBarFlow(): StateFlow<Boolean> = hideEquippedBarFlow.asStateFlow()

    /** Set whether the Equipped bar is hidden and persist. */
    fun setHideEquippedBar(context: Context, hide: Boolean) {
        hideEquippedBarFlow.value = hide
        editor(context).putBoolean(HIDE_EQUIPPED_BAR, hide).apply()
    }

    /** Whether the companion screen dims to match the game scene's ambient light. */
    fun adaptiveDimmingFlow(): StateFlow<Boolean> = adaptiveDimmingFlow.asStateFlow()

    /** Set whether adaptive dimming is enabled and persist. */
    fun setAdaptiveDimming(context: Context, enabled: Boolean) {
        adaptiveDimmingFlow.value = enabled
        editor(context).putBoolean(ADAPTIVE_DIMMING, enabled).apply()
    }

    /** Master switch for the companion's own interface sounds. See [UiSounds]. */
    fun uiSoundsFlow(): StateFlow<Boolean> = uiSoundsFlow.asStateFlow()

    /** Set whether interface sounds play and persist. */
    fun setUiSounds(context: Context, enabled: Boolean) {
        uiSoundsFlow.value = enabled
        editor(context).putBoolean(UI_SOUNDS, enabled).apply()
    }

    /** Interface-sound level, 0f..1f, applied per play as the SoundPool stream volume. */
    fun uiSoundVolumeFlow(): StateFlow<Float> = uiSoundVolumeFlow.asStateFlow()

    /** Set the interface-sound level and persist. */
    fun setUiSoundVolume(context: Context, value: Float) {
        val v = value.coerceIn(UI_SOUND_VOLUME_RANGE)
        uiSoundVolumeFlow.value = v
        editor(context).putFloat(UI_SOUND_VOLUME, v).apply()
    }

    /** Screen brightness (1f = undimmed) the companion reaches in the DARKEST scene. */
    fun adaptiveDimMinBrightnessFlow(): StateFlow<Float> = adaptiveDimMinBrightnessFlow.asStateFlow()

    /** Set the darkest-scene brightness and persist. Clamped to [ADAPTIVE_DIM_MIN_RANGE], whose
     *  lower bound is the readability cap — this clamp is deliberate and must not be removed. */
    fun setAdaptiveDimMinBrightness(context: Context, value: Float) {
        val v = value.coerceIn(ADAPTIVE_DIM_MIN_RANGE)
        adaptiveDimMinBrightnessFlow.value = v
        editor(context).putFloat(ADAPTIVE_DIM_MIN_BRIGHTNESS, v).apply()
    }

    /** Screen brightness (1f = undimmed) the companion keeps in the BRIGHTEST scene. */
    fun adaptiveDimMaxBrightnessFlow(): StateFlow<Float> = adaptiveDimMaxBrightnessFlow.asStateFlow()

    /** Set the brightest-scene brightness and persist. Clamped to [ADAPTIVE_DIM_MAX_RANGE]. */
    fun setAdaptiveDimMaxBrightness(context: Context, value: Float) {
        val v = value.coerceIn(ADAPTIVE_DIM_MAX_RANGE)
        adaptiveDimMaxBrightnessFlow.value = v
        editor(context).putFloat(ADAPTIVE_DIM_MAX_BRIGHTNESS, v).apply()
    }

    /**
     * Exterior NIGHT ambient lift, in relative-luminance units (0 = exact vanilla).
     *
     * Applied by `companion.lua`'s `applyNightAmbientFloor` via `CMP:night_brightness`, which
     * rewrites each weather record's night ambient colour. Floor-only correctness is structural,
     * not tuned: a weather's day ambient is a SEPARATE value that `TimeOfDayInterpolator::getValue`
     * returns unblended across the whole day window, so nothing set here can reach midday.
     */
    fun nightBrightnessFlow(): StateFlow<Float> = nightBrightnessFlow.asStateFlow()

    /** Set the exterior night ambient lift and persist. Clamped to [NIGHT_BRIGHTNESS_RANGE]. */
    fun setNightBrightness(context: Context, value: Float) {
        val v = value.coerceIn(NIGHT_BRIGHTNESS_RANGE)
        nightBrightnessFlow.value = v
        editor(context).putFloat(NIGHT_BRIGHTNESS, v).apply()
    }

    /**
     * OpenMW's `Shaders/minimum interior brightness` — the engine's own interior ambient FLOOR.
     *
     * A pass-through, not a reimplementation: the value is pushed to the engine by
     * `EngineActivity.setMinimumInteriorBrightness`, which parks it for the engine thread to apply
     * through the same `processChangedSettings` path the game's own settings window uses. Stored
     * here only so the DS options menu can show and restore it — the engine persists its own copy
     * to settings.cfg independently.
     */
    fun interiorBrightnessFlow(): StateFlow<Float> = interiorBrightnessFlow.asStateFlow()

    /** Set the engine's minimum interior brightness and persist. Clamped to
     *  [INTERIOR_BRIGHTNESS_RANGE]. */
    fun setInteriorBrightness(context: Context, value: Float) {
        val v = value.coerceIn(INTERIOR_BRIGHTNESS_RANGE)
        interiorBrightnessFlow.value = v
        editor(context).putFloat(INTERIOR_BRIGHTNESS, v).apply()
    }

    /**
     * How many HUD favourite GEAR slots to show (0..[FAV_SLOTS_MAX]).
     *
     * This is a VISIBILITY count, not a storage width: [FAV_SLOTS_MAX] slots are always persisted,
     * so lowering it hides favourites rather than destroying them and raising it brings them back.
     * 0 hides the whole FAV. GEAR group.
     */
    fun favGearSlotsFlow(): StateFlow<Int> = favGearSlotsFlow.asStateFlow()

    /** Set the visible HUD favourite gear slot count and persist. Clamped to 0..[FAV_SLOTS_MAX]. */
    fun setFavGearSlots(context: Context, value: Int) {
        val v = value.coerceIn(0, FAV_SLOTS_MAX)
        favGearSlotsFlow.value = v
        editor(context).putInt(FAV_GEAR_SLOTS, v).apply()
    }

    /** How many HUD favourite SPELL slots to show (0..[FAV_SLOTS_MAX]). See [favGearSlotsFlow]. */
    fun favMagicSlotsFlow(): StateFlow<Int> = favMagicSlotsFlow.asStateFlow()

    /** Set the visible HUD favourite spell slot count and persist. Clamped to 0..[FAV_SLOTS_MAX]. */
    fun setFavMagicSlots(context: Context, value: Int) {
        val v = value.coerceIn(0, FAV_SLOTS_MAX)
        favMagicSlotsFlow.value = v
        editor(context).putInt(FAV_MAGIC_SLOTS, v).apply()
    }

    /** Whether the journal's chronological view uses the spine-hinged page-turn animation. */
    fun journalPageTurnFlow(): StateFlow<Boolean> = journalPageTurnFlow.asStateFlow()

    /** Set whether the journal page-turn animation is enabled and persist. */
    fun setJournalPageTurn(context: Context, enabled: Boolean) {
        journalPageTurnFlow.value = enabled
        editor(context).putBoolean(JOURNAL_PAGE_TURN, enabled).apply()
    }

    /** Whether timed active effects show a remaining-duration countdown. Permanent effects never
     *  do, independently of this. */
    fun effectTimersFlow(): StateFlow<Boolean> = effectTimersFlow.asStateFlow()

    /** Set whether active-effect timers are shown and persist. */
    fun setEffectTimers(context: Context, enabled: Boolean) {
        effectTimersFlow.value = enabled
        editor(context).putBoolean(EFFECT_TIMERS, enabled).apply()
    }

    /** Whether the companion + DS overlays use the game's typeface (MysticCards) instead of the
     *  Android system serif/monospace. Deliberately NOT read by the options menu, which stays on
     *  the system fonts so this switch can always be found and turned back off. */
    fun vanillaFontFlow(): StateFlow<Boolean> = vanillaFontFlow.asStateFlow()

    /** Set whether the game typeface is used and persist. */
    fun setVanillaFont(context: Context, enabled: Boolean) {
        vanillaFontFlow.value = enabled
        editor(context).putBoolean(VANILLA_FONT, enabled).apply()
    }

    /** Whether the Developer Tools action panel (cheats / test helpers) is shown. */
    fun developerModeFlow(): StateFlow<Boolean> = developerModeFlow.asStateFlow()

    /** Set whether Developer Tools actions are shown and persist. */
    fun setDeveloperMode(context: Context, enabled: Boolean) {
        developerModeFlow.value = enabled
        editor(context).putBoolean(DEVELOPER_MODE, enabled).apply()
    }

    /** Whether worn items are also shown inline in the Inventory "All" list. */
    fun showEquippedInListFlow(): StateFlow<Boolean> = showEquippedInListFlow.asStateFlow()

    /** Set whether worn items show inline in the Inventory list and persist. */
    fun setShowEquippedInList(context: Context, show: Boolean) {
        showEquippedInListFlow.value = show
        editor(context).putBoolean(SHOW_EQUIPPED_IN_LIST, show).apply()
    }

    /** Where the conversation UI is drawn (BOTTOM / SPLIT / TOP). */
    fun conversationLocationFlow(): StateFlow<ConversationLocation> = conversationLocationFlow.asStateFlow()

    /** Set the conversation location and persist. */
    fun setConversationLocation(context: Context, loc: ConversationLocation) {
        conversationLocationFlow.value = loc
        editor(context).putString(CONVERSATION_LOCATION, loc.name).apply()
    }

    /** Where the looting UI is drawn (BOTTOM / SPLIT / TOP). */
    fun lootingLocationFlow(): StateFlow<ScreenLocation> = lootingLocationFlow.asStateFlow()

    /** Set the looting location and persist. */
    fun setLootingLocation(context: Context, loc: ScreenLocation) {
        lootingLocationFlow.value = loc
        editor(context).putString(LOOTING_LOCATION, loc.name).apply()
    }

    /** Where the bartering UI is drawn (BOTTOM / SPLIT / TOP). */
    fun barterLocationFlow(): StateFlow<ScreenLocation> = barterLocationFlow.asStateFlow()

    /** Set the bartering location and persist. */
    fun setBarterLocation(context: Context, loc: ScreenLocation) {
        barterLocationFlow.value = loc
        editor(context).putString(BARTER_LOCATION, loc.name).apply()
    }

    /** Where the training popup is drawn (BOTTOM / SPLIT; TOP pending). */
    fun trainingLocationFlow(): StateFlow<ScreenLocation> = trainingLocationFlow.asStateFlow()

    /** Set the training popup location and persist. */
    fun setTrainingLocation(context: Context, loc: ScreenLocation) {
        trainingLocationFlow.value = loc
        editor(context).putString(TRAINING_LOCATION, loc.name).apply()
    }

    /** Where the spell-buying popup is drawn (BOTTOM / SPLIT; TOP pending). */
    fun spellBuyingLocationFlow(): StateFlow<ScreenLocation> = spellBuyingLocationFlow.asStateFlow()

    /** Set the spell-buying popup location and persist. */
    fun setSpellBuyingLocation(context: Context, loc: ScreenLocation) {
        spellBuyingLocationFlow.value = loc
        editor(context).putString(SPELLBUYING_LOCATION, loc.name).apply()
    }

    /** Where the repair popup is drawn (BOTTOM; TOP pending). */
    fun repairLocationFlow(): StateFlow<ScreenLocation> = repairLocationFlow.asStateFlow()

    /** Set the repair popup location and persist. */
    fun setRepairLocation(context: Context, loc: ScreenLocation) {
        repairLocationFlow.value = loc
        editor(context).putString(REPAIR_LOCATION, loc.name).apply()
    }

    /** Where the travel popup is drawn (BOTTOM; TOP pending). */
    fun travelLocationFlow(): StateFlow<ScreenLocation> = travelLocationFlow.asStateFlow()

    /** Set the travel popup location and persist. */
    fun setTravelLocation(context: Context, loc: ScreenLocation) {
        travelLocationFlow.value = loc
        editor(context).putString(TRAVEL_LOCATION, loc.name).apply()
    }

    /** Where the rest/wait popup is drawn (BOTTOM; TOP pending — the overlay is bottom-only today). */
    fun restwaitLocationFlow(): StateFlow<ScreenLocation> = restwaitLocationFlow.asStateFlow()

    /** Set the rest/wait popup location and persist. */
    fun setRestwaitLocation(context: Context, loc: ScreenLocation) {
        restwaitLocationFlow.value = loc
        editor(context).putString(RESTWAIT_LOCATION, loc.name).apply()
    }

    /** Where the crime alert toast is drawn (BOTTOM; TOP pending — the toast is bottom-only today). */
    fun crimeLocationFlow(): StateFlow<ScreenLocation> = crimeLocationFlow.asStateFlow()

    /** Set the crime-alert location and persist. */
    fun setCrimeLocation(context: Context, loc: ScreenLocation) {
        crimeLocationFlow.value = loc
        editor(context).putString(CRIME_LOCATION, loc.name).apply()
    }

    /** Background-fill opacity (0f..1f) of DS overlay panels on the TOP screen. 1f = opaque. */
    fun topPanelOpacityFlow(): StateFlow<Float> = topPanelOpacityFlow.asStateFlow()

    /** Set the top-screen panel opacity and persist. Clamped to 0..1. */
    fun setTopPanelOpacity(context: Context, value: Float) {
        val v = value.coerceIn(0f, 1f)
        topPanelOpacityFlow.value = v
        editor(context).putFloat(TOP_PANEL_OPACITY, v).apply()
    }

    /** Where the combat target's health bar is drawn (BOTTOM / TOP). */
    fun targetHealthLocationFlow(): StateFlow<TargetHealthLocation> = targetHealthLocationFlow.asStateFlow()

    /** Set the target-health location and persist. */
    fun setTargetHealthLocation(context: Context, loc: TargetHealthLocation) {
        targetHealthLocationFlow.value = loc
        editor(context).putString(TARGET_HEALTH_LOCATION, loc.name).apply()
    }

    /** Where the persuasion popup is drawn (BOTTOM / TOP — both implemented). */
    fun persuasionLocationFlow(): StateFlow<PersuasionLocation> = persuasionLocationFlow.asStateFlow()

    /** Set the persuasion popup location and persist. */
    fun setPersuasionLocation(context: Context, loc: PersuasionLocation) {
        persuasionLocationFlow.value = loc
        editor(context).putString(PERSUASION_LOCATION, loc.name).apply()
    }

    /** Whether player vitals also show on the top screen during combat. */
    fun playerCombatFlow(): StateFlow<Boolean> = playerCombatFlow.asStateFlow()

    /** Enable/disable the top-screen player-combat vitals overlay and persist. */
    fun setPlayerCombat(context: Context, enabled: Boolean) {
        playerCombatFlow.value = enabled
        editor(context).putBoolean(PLAYER_COMBAT, enabled).apply()
    }

    /** Enable/disable the top-screen game cursor and persist. Mostly independent of [setTouchInput]
     *  (see there) — either or both may be on, but at least one must stay on, so turning this off
     *  while touch input is also off turns touch input ON.
     *  The two do share the engine's single cursor position (`MouseManager::mGuiCursorX/Y`),
     *  which composes fine with no coordination: a tap SETS it absolutely, the thumbstick ADDS to it
     *  relatively, last input wins — ordinary mouse-plus-trackpad behaviour.
     *
     *  NOTE turning this on also changes what **A** does in NATIVE menus: with a cursor
     *  present the engine treats A as a mouse click at the cursor instead of "activate the
     *  highlighted item" (the `mGamepadGuiCursorEnabled` branches in `controllermanager.cpp`). D-pad
     *  highlight navigation is unaffected. That is pre-existing engine behaviour tied to the cursor,
     *  not to touch input — surfaced in this option's description and on the DS Controls page. */
    fun setGameCursor(context: Context, enabled: Boolean) {
        gameCursorFlow.value = enabled
        editor(context).putBoolean(GAME_CURSOR, enabled).apply()
        if (!enabled && !touchInputFlow.value) setTouchInput(context, true)
    }

    private fun editor(context: Context): SharedPreferences.Editor {
        val p = prefs ?: context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE).also { prefs = it }
        return p.edit()
    }
}
