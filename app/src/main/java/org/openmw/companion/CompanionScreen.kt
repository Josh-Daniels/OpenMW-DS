package org.openmw.companion

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.layout.BoxWithConstraints
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit

// Splash image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import org.openmw.R
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import kotlin.time.Duration.Companion.milliseconds
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.compose.ui.graphics.ImageBitmap
import org.openmw.Constants
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmw.utils.GameFilesPreferences
import java.io.File
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt


/**
 * SECOND-SCREEN UI — Morrowind-styled.
 *
 *  Carved-stone panels, bronze frames, bone serif text.
 *  Top stat bar (HP/MP/SP) floats over EVERY tab; bottom tab row switches
 *  the central panel. Inventory is a grid of framed slots (ready for icons)
 *  with a paper-doll placeholder beside it.
 */

// ---- palette: warm stone & bronze ----
private val StoneDark   = Color(0xFF15120D)   // deep warm background
private val StonePanel  = Color(0xFF252017)   // raised panel fill
private val SlotBg      = Color(0xFF1C1812)   // item slot fill
private val SlotWorn    = Color(0xFF3A2E1A)   // equipped slot fill (bronze-tinted)
private val Bronze      = Color(0xFF8C6D3F)   // frame border
private val BronzeDark  = Color(0xFF5A4528)   // inner border / dividers
private val BronzeLight = Color(0xFFC9A063)   // highlights, selected
private val Bone        = Color(0xFFD8CBB0)   // primary text
private val BoneDim     = Color(0xFF9A8C70)   // secondary text
private val BoneBright  = Color(0xFFF2EEE3)   // item name, high prominence
private val BoneMuted   = Color(0xFFBCAF96)   // item name, low prominence
private val FloatStone  = Color(0xF02A2318)   // near-opaque stone for floating bars
private val EnchantTint  = Color(0xFF5BA8E0).copy(alpha = 0.4f) // subtle light-blue backdrop for enchanted item icons

private val HealthCol   = Color(0xFF8E2B20)   // blood red
private val MagickaCol  = Color(0xFF35608F)   // arcane blue (stat bar)
private val SpellFavCol = Color(0xFF83AEBE)   // spell fav slot border/text (muted steel blue)
private val FatigueCol  = Color(0xFF4E7A3A)   // earthy green

// ---- dropdown focus guard ----
// Compose DropdownMenu creates a new popup window in WindowManager when opened.
// On the dual-screen Presentation, that popup doesn't inherit FLAG_NOT_FOCUSABLE
// from the presentation window. This singleton tracks open state so a transparent
// scrim can intercept outside taps before they reach underlying list items.
private object DropdownState {
    var anyOpen by mutableStateOf(false)
    var closeRequest by mutableStateOf(0)
    fun open() { anyOpen = true }
    fun closeAll() { anyOpen = false; closeRequest++ }
}

// A pending "how many?" prompt. Set by a deeply-nested row (e.g. an inventory
// Drop menu item) and observed by CompanionScreen, which renders the shared
// QuantitySelector overlay. Mirrors the DropdownState global-holder pattern so
// callers don't have to thread a callback through every list/panel layer.
// The QuantitySelector composable itself stays fully reusable (name/max/
// callbacks) — this holder is just the inventory's way of hosting it; looting
// and bartering would host it with their own state the same way.
private data class QuantityRequest(
    val name: String,
    val max: Int,
    val confirmLabel: String,
    val onConfirm: (Int) -> Unit,
)

/**
 * Tracks whether a cancelable bottom-screen MODAL is on screen — the QuantitySelector or the
 * PersuasionPopup. While [open]: (a) the barter/loot grid + slider and the dialogue-topic nav
 * collectors YIELD so controller input drives the modal, not the surface underneath; and (b) the
 * modal pushes the native "B = cancel" flag (companionQtySelectorOpen) so the controller B button
 * closes just the modal (COMPANION_NAV_CANCEL) instead of the whole overlay/conversation. A counter
 * (not a bool) is robust to any brief enter/exit overlap.
 */
private object ModalNav {
    var count by mutableStateOf(0)
    val open: Boolean get() = count > 0
}

private object QuantityRequestState {
    var request by mutableStateOf<QuantityRequest?>(null)
    fun clear() { request = null }

    /** Show the selector only when it can matter: count > 1. For a single item,
     *  invoke the action immediately with quantity 1 (no pointless prompt). */
    fun requestOrRun(name: String, count: Int, confirmLabel: String, action: (Int) -> Unit) {
        if (count > 1) {
            request = QuantityRequest(name, count, confirmLabel) { n -> clear(); action(n) }
        } else {
            action(1)
        }
    }
}

// ---- type roles (swap to bundled Morrowind fonts later in one place) ----
private val MwDisplay = FontFamily.Serif
private val MwBody    = FontFamily.Serif
private val MwData    = FontFamily.Monospace

private const val TOP_BAR_SPACE = 76
private const val BOTTOM_BAR_SPACE = 76
// Unified row font size for the SPLIT-conversation bottom-screen surfaces: the dialogue
// topics/services list plus the persuade / repair / travel popups. BOTTOM/TOP modes keep their
// own smaller defaults.
private val SPLIT_ROW_FONT_SIZE = 16.sp
// Unified panel size for those same SPLIT surfaces: width matches the topics popup and height
// spans the HUD map box's vertical band (TOP_BAR_SPACE..BOTTOM_BAR_SPACE), so topics, persuade,
// repair and travel are all identically sized and line up with the map on the HUD page. Applied
// via [splitDialoguePanel]; the outer scrim supplies the band padding.
private const val SPLIT_PANEL_WIDTH = 0.65f

/** Sizes a SPLIT-view dialogue panel (topics/persuade/repair/travel) uniformly: [SPLIT_PANEL_WIDTH]
 *  of the screen width, full height of the band its parent Box pads to. */
private fun Modifier.splitDialoguePanel(): Modifier =
    this.fillMaxWidth(SPLIT_PANEL_WIDTH).fillMaxHeight()
// Shared height for the "top box" on Inventory (EQUIPPED strip) and Spells
// (Active Spell) so the two panels line up. Content is vertically centered.
private val TOP_BOX_HEIGHT = 54.dp

private val ICON_CACHE_DIR by lazy {
    File("${Constants.USER_FILE_STORAGE}/companion_icons").also { it.mkdirs() }
}

// Fraction of a single cell segment visible across the short canvas axis.
// 0.25 = tight zoom (25% of cell visible); increase toward 1.0 to zoom out.
private const val MINIMAP_CROP_FRACTION = 0.25f

// How long a tapped door-marker's name bubble stays before auto-dismissing (ms). Tweakable;
// mirrors the Training overlay's dwell consts. A tap elsewhere / re-tapping it closes it sooner.
private const val DOOR_MARKER_POPUP_MS = 3000L

private val FAV_SLOT_WIDTH  = 132.dp
private val FAV_SLOT_HEIGHT = 34.dp

private enum class Tab(val label: String) {
    INVENTORY("Inventory"), MAGIC("Spells"), HUD("HUD"), STATS("Stats"), JOURNAL("Journal")
}

private fun InventoryItem.displayName(): String =
    if (name.isNotBlank()) name else prettify(id)

private fun SpellEntry.displayName(): String =
    if (name.isNotBlank()) name else prettify(id)

private fun BarterItem.displayName(): String =
    if (name.isNotBlank()) name else prettify(id)

private val EQUIPMENT_SLOT_ORDER = listOf(
    "weapon", "ammo", "shield",
    "lockpick", "probe",
    "helmet", "cuirass",
    "left_pauldron", "right_pauldron",
    "greaves", "boots",
    "left_gauntlet", "right_gauntlet",
    "amulet", "left_ring", "right_ring",
    "shirt", "pants", "skirt", "robe",
    "carried_left", "carried_right",
    "book"
)

private data class InvCategory(val label: String, val cats: Set<String>)

private val INV_CATEGORIES = listOf(
    InvCategory("Weapons", setOf("weapon", "ammo")),
    InvCategory("Armor",   setOf("helmet", "cuirass", "left_pauldron", "right_pauldron",
                                  "greaves", "boots", "left_gauntlet", "right_gauntlet",
                                  "shield", "armor")),
    InvCategory("Apparel", setOf("amulet", "left_ring", "shirt", "pants", "skirt",
                                  "robe", "clothing")),
    InvCategory("Tools",   setOf("lockpick", "probe", "apparatus", "repair")),
    InvCategory("Books",       setOf("book", "scroll")),
    InvCategory("Consumables", setOf("potion", "ingredient")),
    InvCategory("Misc",        setOf("misc", "carried_left")),
)

// Vanilla-style category ordering (SortFilterItemModel::getTypeOrder) mapped onto BOTH the fine
// inventory/container category strings (Lua itemCategory) AND the coarse barter category strings
// (native companionBarterCategory), so every item list sorts by category then name like the menus.
private fun itemCategoryRank(category: String): Int = when (category) {
    "weapon", "ammo" -> 0
    "armor", "helmet", "cuirass", "left_pauldron", "right_pauldron", "greaves", "boots",
        "left_gauntlet", "right_gauntlet", "shield" -> 1
    "apparel", "amulet", "left_ring", "shirt", "pants", "skirt", "robe", "clothing" -> 2
    "consumable", "potion" -> 3
    "ingredient" -> 4
    "book", "scroll" -> 6
    "carried_left" -> 7
    "tools", "lockpick" -> 9
    "probe" -> 11
    else -> 8   // misc (apparatus/repair/light are folded into "misc" by itemCategory)
}

/** Subtle gold backdrop behind an enchanted item's icon (mirrors vanilla's menu_icon_magic frame).
 *  Call as the FIRST child of an icon Box so it draws behind the icon image. */
@Composable
private fun BoxScope.EnchantBackdrop(enchanted: Boolean) {
    if (enchanted) Box(Modifier.matchParentSize().background(EnchantTint))
}

/** The live player character name (from COMPANION_CHARACTER → state.character.name), used for the
 *  "your side" column headers. Falls back to [fallback] until the name is known. Maps to just the
 *  name (distinctUntilChanged) so it doesn't recompose on every fast COMPANION_STATS tick. */
@Composable
private fun rememberPlayerName(fallback: String = "You"): String {
    val flow = remember { GameStateRepository.state.map { it.character.name }.distinctUntilChanged() }
    val name by flow.collectAsState(initial = "")
    return name.ifBlank { fallback }
}

/** A thin, subtle horizontal scrollbar for a [LazyHorizontalGrid], shown ONLY when the content is
 *  wider than the viewport (more item-columns than fit). Track = muted bronze, thumb = brighter
 *  bronze; thumb width/position approximated from the visible/total item counts. */
@Composable
private fun HorizontalGridScrollbar(state: LazyGridState, modifier: Modifier = Modifier) {
    val metrics by remember(state) {
        derivedStateOf {
            val info = state.layoutInfo
            val total = info.totalItemsCount
            val visible = info.visibleItemsInfo.size
            if (total <= 0 || visible >= total) null
            else {
                val thumb = (visible.toFloat() / total).coerceIn(0.08f, 1f)
                val start = (state.firstVisibleItemIndex.toFloat() / total).coerceIn(0f, 1f - thumb)
                start to thumb
            }
        }
    }
    val m = metrics
    if (m != null) {
        val (start, thumb) = m
        BoxWithConstraints(modifier.fillMaxWidth().height(4.dp)) {
            val w = maxWidth
            Box(
                Modifier.fillMaxWidth().height(3.dp).align(Alignment.Center)
                    .clip(RoundedCornerShape(2.dp)).background(BronzeDark.copy(alpha = 0.35f))
            )
            Box(
                Modifier.align(Alignment.CenterStart)
                    .offset(x = w * start).width(w * thumb).height(3.dp)
                    .clip(RoundedCornerShape(2.dp)).background(BronzeLight.copy(alpha = 0.7f))
            )
        }
    }
}

// How a tap / long-press acts on an inventory item. Mutually exclusive, keyed off
// the coarse category from Lua's itemCategory(). Kept in one place so the two
// inventory list call sites (grouped + single-category) stay in agreement.
//   readable   → open the book/scroll reader (CMP:read)
//   usable     → native use() action (CMP:use): potion=drink, ingredient=eat,
//                apparatus=alchemy menu, repair=repair menu — NONE are worn
//   equippable → worn gear toggled via CMP:equip / CMP:unequip (weapons, armor,
//                clothing, lockpick/probe, and lights/torches → carried_left)
private fun InventoryItem.isReadable() = category == "book" || category == "scroll"
private fun InventoryItem.isUsable() =
    category == "potion" || category == "ingredient" || category == "apparatus" || category == "repair"
private fun InventoryItem.isEquippable() = !isUsable() && !isReadable() && category != "misc"

// Long-press / tap verb for a usable item: potion→Drink, food→Eat, tool→Use.
private fun InventoryItem.useVerb() = when (category) {
    "potion" -> "Drink"
    "ingredient" -> "Eat"
    else -> "Use"
}

// Barter overlay category tabs — one bucket per tab, matching the coarse `cat` the
// engine emits on COMPANION_BARTER_ITEM (weapon/armor/apparel/tools/consumable/misc).
// Deliberately NOT the inventory's fine slot categories: the barter screen groups into
// these seven tabs directly.
private data class BarterCat(val label: String, val cat: String)

private val BARTER_CATEGORIES = listOf(
    BarterCat("Weapons", "weapon"),
    BarterCat("Armor", "armor"),
    BarterCat("Apparel", "apparel"),
    BarterCat("Tools", "tools"),
    BarterCat("Consumables", "consumable"),
    BarterCat("Misc", "misc"),
)

/** Cycle a barter side's category filter by [dir] (-1 = previous, +1 = next) through
 *  [All] + the categories actually present in [items] (in BARTER_CATEGORIES order), wrapping.
 *  Returns the new selection (null = All). Matches the visible CategoryTab set, so the L1/R1
 *  shoulder buttons mirror tapping the tabs. */
private fun cycleBarterCat(items: List<BarterItem>, current: String?, dir: Int): String? {
    val present = items.map { it.category }.toSet()
    val avail: List<String?> = listOf<String?>(null) + BARTER_CATEGORIES.filter { it.cat in present }.map { it.cat }
    if (avail.size <= 1) return current
    val cur = avail.indexOf(current).coerceAtLeast(0)
    val next = ((cur + dir) % avail.size + avail.size) % avail.size
    return avail[next]
}

/** Stone panel with a bronze frame — the signature Morrowind window look. */
private fun Modifier.mwPanel(): Modifier = this
    .clip(RoundedCornerShape(3.dp))
    .background(StonePanel)
    .border(2.dp, Bronze, RoundedCornerShape(3.dp))

@Composable
fun CompanionScreen() {
    val state by GameStateRepository.state.collectAsState()
    var tab by remember { mutableStateOf(Tab.HUD) }
    // Dismiss the item info popup when the tab changes so it never lingers over another screen.
    LaunchedEffect(tab) { ItemInfoPopupState.close() }

    // Log-tail fallback: feeds GameStateRepository the same way the JNI sink does,
    // so the UI works even if installCompanionSink() has a hiccup.
    val scope = rememberCoroutineScope()
    val logReader = remember {
        LogReader("${Constants.USER_FILE_STORAGE}/config/openmw.log")
    }
    DisposableEffect(Unit) {
        logReader.start(scope)
        onDispose { logReader.stop() }
    }

    val context = LocalContext.current
    // Load the last-known character's favourites synchronously during composition
    // (not LaunchedEffect) so favourite pills show persisted content on the very
    // first frame, with no post-composition flash of empty slots.
    remember(context) { FavouritesRepository.init(context) }
    // Load persisted UI routing (which screen the conversation lives on, etc.).
    remember(context) { UiPreferences.init(context); true }

    // Favourites are save-dependent (keyed by character name). Once the live
    // character is known — and whenever the player loads a different save at
    // runtime — swap to that character's bucket, THEN prune any favourite whose
    // item/spell no longer exists in the loaded save. Ordering matters: switch
    // first so reconcile only ever prunes the ACTIVE character's set.
    //
    // A category is pruned only when its source list is non-empty; passing null
    // while inventory/spells are still empty (the save-load window) prevents a
    // transient blank state from wiping favourites.
    LaunchedEffect(state.character.name, state.inventory, state.spells) {
        val name = state.character.name
        if (name.isNotBlank()) {
            FavouritesRepository.setCharacter(context, name)
            FavouritesRepository.reconcile(
                context,
                inventoryIds = state.inventory.takeIf { it.isNotEmpty() }?.map { it.id }?.toSet(),
                spellIds = state.spells.takeIf { it.isNotEmpty() }?.map { it.id }?.toSet()
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .imePadding()
            .background(StoneDark)
    ) {
        // Tapped stat row on the Stats screen. Rendered as a root-level overlay
        // (below) so its scrim covers the tab bars, exactly like ItemInfoOverlay.
        var selectedStat by remember { mutableStateOf<StatInfo?>(null) }

        // Hoisted above the tab content so MapPanel can gate its map-canvas
        // clickable on it — otherwise a tap meant to dismiss the splash lands on
        // the map canvas (HUD is the default tab) and fires openWorldMap instead.
        var splashVisible by remember { mutableStateOf(true) }

        when (tab) {
            Tab.INVENTORY -> InventoryPanel(state)
            Tab.MAGIC -> MagicPanel(state)
            Tab.HUD -> MapPanel(state, splashVisible = splashVisible)
            Tab.STATS -> StatsPanel(state, onSelectStat = { selectedStat = it })
            Tab.JOURNAL -> JournalPanel()
        }

        BottomTabBar(
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
        )

        // Transparent scrim: intercepts outside taps while a dropdown is open so they
        // dismiss the menu without also firing the click handler of the row beneath.
        // Uses PointerEventPass.Initial so the DOWN event is seen but not consumed,
        // letting scroll gestures reach the LazyColumn; only a confirmed tap-UP is consumed.
        if (DropdownState.anyOpen) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .zIndex(10f)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitPointerEvent(PointerEventPass.Initial)
                            val startPos = down.changes.firstOrNull()?.position
                                ?: return@awaitEachGesture
                            var dragged = false
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull() ?: break
                                if ((change.position - startPos).getDistance() > viewConfiguration.touchSlop) {
                                    dragged = true
                                }
                                if (!change.pressed) {
                                    if (!dragged) {
                                        change.consume()
                                        DropdownState.closeAll()
                                    }
                                    break
                                }
                            }
                        }
                    }
            )
        }

        // Key on the stable hasData boolean (false→true once), NOT on
        // state.lastUpdateMs: COMPANION_STATS ticks every 0.1s, so keying on
        // lastUpdateMs cancels+restarts this effect every ~100ms and the
        // delay(500ms) can never complete — the splash would only ever dismiss
        // on a manual tap. hasData flips exactly once, so the delay runs to
        // completion and auto-dismisses on first load.
        LaunchedEffect(state.hasData) {
            if (state.hasData && splashVisible) {
                delay(500L.milliseconds)
                splashVisible = false
            }
        }
        if (splashVisible) {
            SplashPanel(onDismiss = { splashVisible = false })
        }

        // Item info popup — a small, position-aware tooltip anchored near the triggering item
        // (long-press → Info, or R3 on the focused item). In-window overlay (NOT a Dialog — see the
        // Presentation window-type mismatch). Base rows arrive async via COMPANION_INFO; the
        // enchant section renders instantly from the local item's `enchant`. Dismiss: tap the scrim,
        // R3 again (toggle), or it follows D-pad focus to the newly focused item.
        // Bottom-screen host: renders unless the trigger came from a SPLIT top-grid cell (then the
        // top overlay hosts it — see ItemInfoPopupHost usage in LootingTopOverlay/BarterTopOverlay).
        if (ItemInfoPopupState.isOpen && !ItemInfoPopupState.onTopScreen) {
            ItemInfoPopupHost()
        }

        // Stat detail popup (Stats screen). Same in-window overlay pattern as
        // ItemInfoOverlay — tap outside to dismiss.
        selectedStat?.let { StatInfoPopup(it, onDismiss = { selectedStat = null }) }

        // Quantity prompt (e.g. "drop N of a stack"). Reusable in-window overlay
        // driven by QuantityRequestState; the confirm callback already carries the
        // action, so this just renders the picker.
        QuantityRequestState.request?.let { req ->
            QuantitySelector(
                name = req.name,
                max = req.max,
                confirmLabel = req.confirmLabel,
                onConfirm = req.onConfirm,
                onCancel = { QuantityRequestState.clear() }
            )
        }

        // Per-element Game UI mode. Each companion overlay is gated by its OWN element: when that
        // element is VANILLA it's suppressed so native OpenMW handles it; DS shows the companion
        // version. The tab UI (inventory/spells/stats/journal/HUD) works regardless.
        val lootingDs by UiPreferences.gameUiModeFlow("game_ui_looting").collectAsState()
        val conversationDs by UiPreferences.gameUiModeFlow("game_ui_conversation").collectAsState()
        val barteringDs by UiPreferences.gameUiModeFlow("game_ui_bartering").collectAsState()
        val persuasionDs by UiPreferences.gameUiModeFlow("game_ui_persuasion").collectAsState()
        val repairDs by UiPreferences.gameUiModeFlow("game_ui_repair").collectAsState()
        val restwaitDs by UiPreferences.gameUiModeFlow("game_ui_restwait").collectAsState()
        val travelDs by UiPreferences.gameUiModeFlow("game_ui_travel").collectAsState()

        // Looting / pickpocketing overlay. Driven by COMPANION_CONTAINER_* from the
        // engine (via Lua). Shown whenever a container session is active, regardless
        // of Hide UI — same as the dialogue overlay, so the panel stays available
        // when the player hides the in-game HUD (the native container window is left
        // in place; this overlay is additive, not a replacement). It renders at
        // zIndex 15f (above the global dropdown-dismiss scrim so its buttons stay
        // tappable; it hosts its own local dismiss-scrim) and below QuantitySelector
        // (20f) so take/put quantity prompts stack above it.
        val containerSession by GameStateRepository.containerSession.collectAsState()
        val lootingLocation by UiPreferences.lootingLocationFlow().collectAsState()
        if (lootingDs == GameUiMode.DS) {
            containerSession?.let { session ->
                LootingOverlay(
                    session = session,
                    playerInventory = state.inventory,
                    playerEquipment = state.equipment,
                    playerEncumbrance = state.encumbrance,
                    location = lootingLocation
                )
            }
        }

        // Active-dialogue overlay. Driven by COMPANION_DIALOGUE_* from the engine and
        // shown over whatever tab is active. There is NO tap-away dismiss: it closes
        // only when the conversation ends (engine sends COMPANION_DIALOGUE_CLOSED →
        // both lists empty) or the player taps a topic/GOODBYE. Topics or services
        // non-empty = a conversation is open.
        val dialogueTopics by GameStateRepository.dialogueTopics.collectAsState()
        val dialogueServices by GameStateRepository.dialogueServices.collectAsState()
        val dialogueNpcName by GameStateRepository.dialogueNpcName.collectAsState()
        val dialogueHistory by GameStateRepository.dialogueHistory.collectAsState()
        val dialogueChoices by GameStateRepository.dialogueChoices.collectAsState()
        val dialogueDisposition by GameStateRepository.dialogueDisposition.collectAsState()
        val dialoguePersuadeAvailable by GameStateRepository.dialoguePersuadeAvailable.collectAsState()
        val dialogueGold by GameStateRepository.dialogueGold.collectAsState()
        if (conversationDs == GameUiMode.DS && (dialogueNpcName.isNotEmpty() || dialogueTopics.isNotEmpty() ||
                dialogueServices.isNotEmpty() || dialogueHistory.isNotEmpty() ||
                dialogueChoices.isNotEmpty())
        ) {
            // Bottom-screen conversation UI depends on where the conversation is routed:
            // - BOTTOM: the classic full two-column overlay here.
            // - SPLIT: history is on the top screen; the bottom shows ONLY the controls.
            // - TOP: the whole conversation is on the top screen (hosted by EngineActivity);
            //   the bottom screen is just a dimmed, inert scrim over the current tab.
            val conversationLocation by UiPreferences.conversationLocationFlow().collectAsState()
            // Show the "Persuade" entry whenever the NPC offers persuasion, in BOTH modes: DS opens
            // the companion popup, Vanilla opens the native modal (see triggerPersuade). Gating it to
            // DS-only previously left Vanilla persuasion with no trigger in a DS conversation.
            val persuadeAvailable = dialoguePersuadeAvailable
            DialogueTopicsOverlay(
                dialogueNpcName, dialogueHistory, dialogueTopics, dialogueServices,
                dialogueChoices, dialogueDisposition, persuadeAvailable,
                location = conversationLocation
            )
        }

        // Persuasion popup — BOTTOM location. Hosted at the CompanionScreen root (independent of the
        // conversation overlay above, so it works even when the conversation is routed to the top
        // screen). Open-state is the shared GameStateRepository.persuasionVisible flow (set by the
        // Persuade row, reset on Cancel / conversation-close / NPC-change). Gated on the persuasion
        // element being DS. The TOP location is a separate top-screen panel window (EngineActivity).
        // Renders at PersuasionPopup's own zIndex(30f) — above the dialogue (15f) / service overlays.
        val persuasionVisible by GameStateRepository.persuasionVisible.collectAsState()
        val persuasionLocation by UiPreferences.persuasionLocationFlow().collectAsState()
        if (persuasionVisible && persuasionLocation == PersuasionLocation.BOTTOM &&
            persuasionDs == GameUiMode.DS
        ) {
            // Prefer the live COMPANION_DIALOGUE_GOLD value (updates after a bribe); fall back to the
            // inventory gold_001 count until the first gold line arrives.
            val persuasionGold = if (dialogueGold >= 0) dialogueGold
                else state.inventory.firstOrNull { it.id.equals("gold_001", ignoreCase = true) }?.count ?: 0
            // Band-sized only when the conversation controls are the SPLIT bottom band, matching them.
            val conversationLocation by UiPreferences.conversationLocationFlow().collectAsState()
            PersuasionPopup(
                gold = persuasionGold,
                bandSized = conversationLocation == ConversationLocation.SPLIT,
                onPersuade = { type ->
                    CompanionActions.persuade(type); GameStateRepository.setPersuasionVisible(false)
                },
                onCancel = { GameStateRepository.setPersuasionVisible(false) }
            )
        }

        // Barter overlay. Driven by COMPANION_BARTER_* from the engine (native TradeWindow),
        // shown over whatever tab/dialogue is active whenever a barter session exists —
        // regardless of Hide UI (the companion-hide-barter-on-hideui patch makes the bottom
        // screen the sole barter surface). Renders above the dialogue overlay (barter is
        // pushed on top of the conversation) at zIndex 16f; the rejection alert stacks above
        // at 18f; the shared QuantitySelector (20f) stacks above both for stack-split prompts.
        // The disposition bar reuses the dialogue disposition (the native TradeWindow re-emits
        // COMPANION_DIALOGUE_DISPOSITION during barter — DialogueWindow::onFrame is dormant then).
        val barterSession by GameStateRepository.barterSession.collectAsState()
        val barterResult by GameStateRepository.barterResult.collectAsState()
        val barterLocation by UiPreferences.barterLocationFlow().collectAsState()
        if (barteringDs == GameUiMode.DS) {
            barterSession?.let { session ->
                BarterOverlay(session = session, disposition = dialogueDisposition, location = barterLocation)
            }
            if (barterSession != null) {
                (barterResult as? BarterResult.Rejected)?.let { rej ->
                    BarterRejectedAlert(rej.reason) { GameStateRepository.dismissBarterResult() }
                }
            }
        }

        // Merchant-repair overlay. Driven by COMPANION_REPAIR_* from the engine (native
        // MerchantRepair window). Shown over whatever tab/dialogue is active whenever a repair
        // session exists AND Repair is in DS mode (Vanilla → native OpenMW handles it). A
        // centred popup (zIndex 17f, above barter's 16f — repair is a dialogue service pushed
        // like barter); the shared QuantitySelector (20f) still stacks above.
        val repairSession by GameStateRepository.repairSession.collectAsState()
        if (repairDs == GameUiMode.DS) {
            repairSession?.let { session ->
                RepairOverlay(session = session)
            }
        }

        // Travel overlay. Driven by COMPANION_TRAVEL_* from the engine (native TravelWindow). Shown
        // over whatever tab/dialogue is active whenever a travel session exists AND Travel is DS
        // (Vanilla → native OpenMW handles it). Centred popup (zIndex 17f, same tier as repair —
        // travel is a dialogue service pushed like barter/repair, never open simultaneously with them).
        val travelSession by GameStateRepository.travelSession.collectAsState()
        if (travelDs == GameUiMode.DS) {
            travelSession?.let { session ->
                TravelOverlay(session = session)
            }
        }

        // Training overlay. Driven by COMPANION_TRAINING_* (native TrainingWindow). Shown while
        // Training is DS. NOT gated purely on the session: the "Training…"/completion messages must
        // outlive the session going null on COMPANION_TRAINING_CLOSED, so the host latches its own
        // mount (see TrainingOverlayHost's LIST→TRAINING→COMPLETE machine). zIndex 17f (repair tier).
        val trainingDs by UiPreferences.gameUiModeFlow("game_ui_training").collectAsState()
        val trainingSession by GameStateRepository.trainingSession.collectAsState()
        if (trainingDs == GameUiMode.DS) {
            TrainingOverlayHost(session = trainingSession)
        }

        // Spell-buying overlay. Driven by COMPANION_SPELLBUYING_* (native SpellBuyingWindow). Shown
        // whenever a session exists AND Spell buying is DS. The engine re-exports the list after each
        // purchase (bought spell flips to known=1, keeping its slot). zIndex 17f (repair tier).
        val spellBuyingDs by UiPreferences.gameUiModeFlow("game_ui_spellbuying").collectAsState()
        val spellBuyingSession by GameStateRepository.spellBuyingSession.collectAsState()
        if (spellBuyingDs == GameUiMode.DS) {
            spellBuyingSession?.let { session ->
                SpellBuyingOverlay(session = session)
            }
        }

        // Rest/wait overlay. Driven by COMPANION_SLEEP_* from the engine (native WaitDialog).
        // Shown whenever a rest/wait picker is open AND Rest/Wait is DS. Confirming dismisses it
        // (the engine runs the fade + time advance on the top screen). zIndex 17f (same tier as
        // repair — they're never open simultaneously).
        val sleepSession by GameStateRepository.sleepSession.collectAsState()
        if (restwaitDs == GameUiMode.DS) {
            sleepSession?.let { session ->
                RestWaitOverlay(session = session)
            }
        }

        // On-screen text-entry keyboard. Driven by COMPANION_TEXT_INPUT_OPEN/_CLOSED (native, when a
        // MyGUI EditBox — character name / class name / save name — gains/loses key focus). A CUSTOM
        // Compose keyboard, NOT the Android IME: the OS keyboard is a focusable window that steals the
        // input-focused display to the bottom screen (controller then hits the bottom launcher, not the
        // game — unrecoverable without a manual top-screen tap), and its screen depends on the user's
        // IME pinning. A tappable Compose overlay needs touch, not key-focus, so nothing ever steals
        // focus from the game and the keys are always on the bottom panel for every user. Enter sends
        // the existing CMPTEXT:set (fill the field + accept/advance the dialog natively).
        val textInputRequest by GameStateRepository.textInputRequest.collectAsState()
        textInputRequest?.let { initial ->
            TextInputOverlay(
                initialText = initial,
                onConfirm = { text ->
                    CompanionActions.submitTextInput(text)
                    GameStateRepository.dismissTextInput()
                }
            )
        }
    }
}

/**
 * Route a "Persuade" tap by the persuasion Game UI mode:
 *  - DS: open the companion persuasion popup (hosted by its own Screen Layout location).
 *  - Vanilla: open the NATIVE PersuasionDialog modal by selecting the sPersuasion topic
 *    (CMPDLG:topic:<name> -> onSelectListItem(sPersuasion) -> setVisible(true)); the DS overlay
 *    doesn't list persuasion as a topic, so this is the only trigger. The native modal's onOpen
 *    emits COMPANION_PERSUASION_OPEN, stepping the conversation overlay aside so it's visible.
 * Reads the mode/name synchronously off the flows (this runs on tap, not during composition).
 */
private fun triggerPersuade() {
    if (UiPreferences.gameUiModeFlow("game_ui_persuasion").value == GameUiMode.DS) {
        GameStateRepository.setPersuasionVisible(true)
    } else {
        val name = GameStateRepository.dialoguePersuadeTopicName.value
        if (name.isNotEmpty()) CompanionActions.selectDialogueTopic(name)
    }
}

/* ---- Dialogue topic list overlay (bottom screen) ---- */

@Composable
private fun DialogueTopicsOverlay(
    npcName: String,
    history: List<DialogueSay>,
    topics: List<String>,
    services: List<String>,
    choices: List<DialogueChoice>,
    disposition: Int,
    persuadeAvailable: Boolean,
    location: ConversationLocation
) {
    // Persuasion popup open-state is a shared repo flow now (the popup is hosted independently by
    // its own Screen Layout location — bottom root or top window — so it's not composed here).
    // While it's open the topic/service list is dimmed + inert, same as when a choice question is up.
    val persuasionVisible by GameStateRepository.persuasionVisible.collectAsState()

    // Non-interactive dark scrim (NOT a Dialog — that crashes on the Presentation display).
    // NO tap-away dismiss; the empty detectTapGestures swallows taps so they don't fall
    // through to the tab underneath. In TOP mode this scrim IS the entire bottom-screen UI:
    // the conversation itself lives on the top screen (hosted by EngineActivity).
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(15f)
            .background(Color(0xCC0F0C08))
            .pointerInput(Unit) { detectTapGestures {} }
    ) {
        when (location) {
            // TOP: the whole conversation is on the top screen; the bottom screen is just a
            // dimmed, inert scrim (the active tab shows through). Nothing else to render.
            ConversationLocation.TOP -> Unit

            // SPLIT: history is on the top screen; the bottom screen shows ONLY the controls
            // (disposition, Barter, Repair, Persuade, topics, Goodbye) centred at 65% width,
            // with the persuasion/choices popups over it.
            ConversationLocation.SPLIT -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        // Same vertical band as the HUD map box (TOP_BAR_SPACE..BOTTOM_BAR_SPACE)
                        // so the topics panel lines up with the map on the HUD page.
                        .padding(top = TOP_BAR_SPACE.dp, bottom = BOTTOM_BAR_SPACE.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Column(
                        modifier = Modifier
                            .splitDialoguePanel()
                            .mwPanel()
                            .pointerInput(Unit) { detectTapGestures {} }
                    ) {
                        DialogueRightColumn(
                            topics = topics, services = services, disposition = disposition,
                            persuadeAvailable = persuadeAvailable,
                            // A choice question OR an open persuasion popup dims the topic/service/
                            // Goodbye rows and makes them inert; a choice's answers render inline in
                            // the history on the top screen (SPLIT), and the persuasion popup is
                            // hosted by its own location.
                            choicesActive = choices.isNotEmpty() || persuasionVisible,
                            interactive = choices.isEmpty() && !persuasionVisible,
                            onPersuadeTapped = { triggerPersuade() },
                            // SPLIT mode uses a larger, unified row font (topics/services on the
                            // bottom screen — matches the persuade/repair/travel popups).
                            // BOTTOM/TOP keep the default 13sp.
                            rowFontSize = SPLIT_ROW_FONT_SIZE,
                            modifier = Modifier.fillMaxSize().padding(8.dp)
                        )
                    }
                }
            }

            // BOTTOM: the classic full two-column conversation on the bottom screen. Choices render
            // inline in the history (below the newest response), matching vanilla, and are
            // controller-navigable. Fills the screen (12dp insets, covering the tab bar — dialogue
            // is a modal left via Goodbye).
            ConversationLocation.BOTTOM -> {
                DialogueConversationOverlay(
                    npcName = npcName, history = history, topics = topics, services = services,
                    choices = choices, disposition = disposition,
                    persuadeAvailable = persuadeAvailable,
                    panelAlignment = Alignment.Center,
                    panelWidthFraction = null, panelHeightFraction = null,
                    panelPadding = PaddingValues(12.dp)
                )
            }
        }
    }
}

/** The full interactive two-column conversation — NPC title bar, scrollable history (left)
 *  and the right column of controls. Shared by the BOTTOM-screen and TOP-screen full layouts.
 *  A mid-dialogue choice question renders INLINE in the history (below the newest response),
 *  matching vanilla — never a separate popup. The panel is sized/anchored by
 *  [panelWidthFraction]/[panelHeightFraction]/[panelAlignment]/[panelPadding] (null fraction =
 *  fill that axis). Must be placed inside a fillMaxSize parent Box. */
@Composable
private fun DialogueConversationOverlay(
    npcName: String,
    history: List<DialogueSay>,
    topics: List<String>,
    services: List<String>,
    choices: List<DialogueChoice>,
    disposition: Int,
    persuadeAvailable: Boolean,
    panelAlignment: Alignment,
    panelWidthFraction: Float?,
    panelHeightFraction: Float?,
    panelPadding: PaddingValues
) {
    // Persuasion popup open-state is a shared repo flow (the popup is hosted separately by its own
    // Screen Layout location). While open, the topic/service list here is dimmed + inert.
    val persuasionVisible by GameStateRepository.persuasionVisible.collectAsState()

    Box(
        modifier = Modifier.fillMaxSize().padding(panelPadding),
        contentAlignment = panelAlignment
    ) {
        Column(
            modifier = Modifier
                .then(if (panelWidthFraction != null) Modifier.fillMaxWidth(panelWidthFraction) else Modifier.fillMaxWidth())
                .then(if (panelHeightFraction != null) Modifier.fillMaxHeight(panelHeightFraction) else Modifier.fillMaxHeight())
                .mwPanel()
                .pointerInput(Unit) { detectTapGestures {} }
        ) {
            // ---- NPC name title bar (full width, window-title style) ----
            if (npcName.isNotEmpty()) {
                Text(
                    npcName,
                    color = BronzeLight, fontSize = 14.sp,
                    fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                )
                Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
            }

            // ---- Two columns: dialogue history (65%) | topics/services (35%) ----
            Row(Modifier.fillMaxSize()) {
                DialogueHistoryColumn(
                    // Choices render inline in the history (below the newest response).
                    history = history,
                    choices = choices,
                    topics = topics,
                    modifier = Modifier.weight(0.65f).fillMaxHeight().padding(8.dp)
                )
                Box(Modifier.fillMaxHeight().width(1.dp).background(BronzeDark))
                DialogueRightColumn(
                    topics = topics, services = services, disposition = disposition,
                    persuadeAvailable = persuadeAvailable,
                    // While a choice is active OR the persuasion popup is open, the topic/service/
                    // Goodbye rows stay visible but greyed and non-tappable — the popup takes
                    // priority over topic selection.
                    choicesActive = choices.isNotEmpty() || persuasionVisible,
                    interactive = choices.isEmpty() && !persuasionVisible,
                    onPersuadeTapped = { triggerPersuade() },
                    modifier = Modifier.weight(0.35f).fillMaxHeight().padding(8.dp)
                )
            }
        }
    }
}

/**
 * TOP-screen conversation overlay (Display 0). Hosted by [org.openmw.EngineActivity] in a
 * WindowManager panel window while a conversation is active AND the Conversation location is
 * SPLIT or TOP. Its content depends on the location:
 *  - SPLIT: a read-only, bottom-anchored history box (75% width, 51% height) — NPC name +
 *    scrolling history. The controls live on the bottom screen. (Touchable only for scrolling.)
 *  - TOP: the full interactive two-column conversation (history + controls + popups), the SAME
 *    75% width, expanded to 92% height. The bottom screen is a dimmed, inert scrim.
 */
@Composable
fun ConversationHistoryOverlay() {
    val location by UiPreferences.conversationLocationFlow().collectAsState()
    val npcName by GameStateRepository.dialogueNpcName.collectAsState()
    val history by GameStateRepository.dialogueHistory.collectAsState()
    // Collected here (not just in the TOP branch) so the SPLIT read-only history below
    // can also retroactively highlight newly-introduced topics.
    val topics by GameStateRepository.dialogueTopics.collectAsState()

    if (location == ConversationLocation.TOP) {
        // Full interactive conversation on the top screen (bottom screen is scrim-only).
        val services by GameStateRepository.dialogueServices.collectAsState()
        val choices by GameStateRepository.dialogueChoices.collectAsState()
        val disposition by GameStateRepository.dialogueDisposition.collectAsState()
        val persuadeAvailable by GameStateRepository.dialoguePersuadeAvailable.collectAsState()

        Box(Modifier.fillMaxSize()) {
            DialogueConversationOverlay(
                npcName = npcName, history = history, topics = topics, services = services,
                choices = choices, disposition = disposition,
                persuadeAvailable = persuadeAvailable,
                panelAlignment = Alignment.BottomCenter,
                panelWidthFraction = 0.83f,   // ~10% wider than the SPLIT history box (0.75)
                panelHeightFraction = 0.70f,  // taller than the previous 0.55
                panelPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 12.dp)
            )
        }
        return
    }

    // SPLIT: read-only history box (bottom-anchored, 75% width, 51% height). A mid-dialogue
    // choice question renders inline here (with the conversation), controller-navigable — the
    // controls live on the bottom screen but the choices live with the text, matching vanilla.
    val choices by GameStateRepository.dialogueChoices.collectAsState()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.75f)             // ~25% narrower than full width
                .fillMaxHeight(0.51f)            // ~10% taller than the previous 0.46
                .clip(RoundedCornerShape(3.dp))
                .background(StonePanel)          // match the TOP-view conversation panel (mwPanel)
                .border(2.dp, Bronze, RoundedCornerShape(3.dp))
        ) {
            if (npcName.isNotEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        npcName,
                        color = BronzeLight, fontSize = 15.sp,
                        fontFamily = MwDisplay, fontWeight = FontWeight.Bold
                    )
                }
                Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
            }
            // interactive = true so topic hyperlinks AND inline choices are tappable/navigable on
            // the top screen (the panel window has FLAG_NOT_TOUCHABLE removed, so touch reaches
            // these clickable spans; the controller drives choice nav via the shared navEvent flow).
            // choicesHorizontal = true → the short, wide Split box lays the options side-by-side,
            // navigated Left/Right (BOTTOM/TOP keep the vertical Up/Down stack).
            DialogueHistoryColumn(
                history = history,
                choices = choices,
                topics = topics,
                modifier = Modifier.fillMaxSize().padding(12.dp),
                interactive = true,
                choicesHorizontal = true
            )
        }
    }
}

/**
 * Top-screen persuasion popup (a panel-window ComposeView added by EngineActivity on Display 0 when
 * the persuasion Screen Layout location is [PersuasionLocation.TOP] AND the popup is open). The
 * window's lifecycle is driven by [GameStateRepository.persuasionVisible]; this just renders the
 * centred [PersuasionPopup] card. Fully interactive (the window omits FLAG_NOT_TOUCHABLE). Actions
 * send the persuade command and clear the shared open-flow; Cancel clears it.
 */
@Composable
fun PersuasionTopOverlay() {
    val dialogueGold by GameStateRepository.dialogueGold.collectAsState()
    val state by GameStateRepository.state.collectAsState()
    // Prefer the live COMPANION_DIALOGUE_GOLD value (updates after a bribe); fall back to inventory.
    val gold = if (dialogueGold >= 0) dialogueGold
        else state.inventory.firstOrNull { it.id.equals("gold_001", ignoreCase = true) }?.count ?: 0

    Box(Modifier.fillMaxSize()) {
        PersuasionPopup(
            gold = gold,
            bandSized = false,   // centred card on the top screen (never the SPLIT bottom band)
            onPersuade = { type ->
                CompanionActions.persuade(type); GameStateRepository.setPersuasionVisible(false)
            },
            onCancel = { GameStateRepository.setPersuasionVisible(false) }
        )
    }
}

/**
 * Top-screen combat-target health overlay (a panel-window ComposeView added by EngineActivity on
 * Display 0 when [TargetHealthLocation.TOP] is selected AND a combat target exists). Top-centre,
 * 8dp from top, 30% of screen width: target name, a full-width red health bar, and a percentage.
 * Renders nothing when there is no target (EngineActivity also removes the window then).
 */
@Composable
fun CombatTargetTopOverlay() {
    val state by GameStateRepository.state.collectAsState()
    val target = state.target ?: return

    Box(
        modifier = Modifier.fillMaxSize().padding(top = 8.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.30f)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Health bar first so it sits at the same vertical offset as the player's health bar
            // (the top bar of PlayerCombatTopOverlay) — both columns are 8dp from top with the same
            // 6dp padding. The NPC name goes UNDER the bar to keep them horizontally aligned.
            CombatBar(
                ratio = target.health.ratio,
                color = HealthCol,
                centerText = "${(target.health.ratio * 100).roundToInt()}%"
            )
            Spacer(Modifier.height(3.dp))
            Text(
                target.name,
                color = BronzeLight, fontSize = 10.sp, fontFamily = MwDisplay,
                fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Top-screen player-status-in-combat overlay (a panel-window ComposeView added by EngineActivity on
 * Display 0 when the "Player status in combat" option is On AND a combat target exists). Top-left,
 * 8dp from top / 12dp from left, 20% of screen width: three stacked labelled bars for
 * Health / Magicka / Fatigue with "current/max" values. Renders nothing when there is no target.
 */
@Composable
fun PlayerCombatTopOverlay() {
    val state by GameStateRepository.state.collectAsState()
    if (state.target == null) return

    Box(
        modifier = Modifier.fillMaxSize().padding(top = 8.dp, start = 12.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.20f)
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            // No labels — the bar colours communicate which vital is which. The "cur/max" value
            // rides INSIDE each bar.
            CombatBar(ratio = state.health.ratio, color = HealthCol, centerText = dynValue(state.health))
            Spacer(Modifier.height(4.dp))
            CombatBar(ratio = state.magicka.ratio, color = MagickaCol, centerText = dynValue(state.magicka))
            Spacer(Modifier.height(4.dp))
            CombatBar(ratio = state.fatigue.ratio, color = FatigueCol, centerText = dynValue(state.fatigue))
        }
    }
}

/** "cur/max" for a [Dynamic], rounded — the value shown inside a player combat bar. */
private fun dynValue(dyn: Dynamic): String = "${dyn.current.roundToInt()}/${dyn.max.roundToInt()}"

/** A full-width 18dp stat bar (dark track, bronze border, [color] fill) for the combat overlays,
 *  with an optional [centerText] drawn centred INSIDE the bar (light, small, over the fill). */
@Composable
private fun CombatBar(ratio: Float, color: Color, centerText: String? = null) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(18.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(Color(0xFF0E0B07))
            .border(1.dp, BronzeDark, RoundedCornerShape(2.dp)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            Modifier
                .fillMaxWidth(ratio.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(2.dp))
                .background(color)
                .align(Alignment.CenterStart)
        )
        if (centerText != null) {
            Text(
                centerText,
                color = Color.White, fontSize = 8.sp, fontFamily = MwData,
                fontWeight = FontWeight.Bold, maxLines = 1
            )
        }
    }
}

/** One navigable row in the dialogue right column: its [label] and what activating it does.
 *  Built in render order so the controller focus index maps 1:1 to a visible row. */
private class DialogueNavItem(val label: String, val onActivate: () -> Unit)

/** The dialogue right column — services, disposition bar, the Persuade trigger, the
 *  scrolling topics list and the Goodbye button. [choicesActive] greys the rows (a
 *  mid-dialogue question is showing its answers in the left column); [interactive] gates
 *  every tap — off while a choice is active OR while the persuasion column owns input.
 *  Owns controller navigation (Phase 2): D-pad up/down move focus through the services+topics
 *  list, A activates the focused row (B closes via the existing patch). This is the single
 *  composable rendering that list in every conversation location (BOTTOM/SPLIT/TOP), and only
 *  one instance is ever composed, so the nav collector below is unambiguous. */
@Composable
private fun DialogueRightColumn(
    topics: List<String>,
    services: List<String>,
    disposition: Int,
    persuadeAvailable: Boolean,
    choicesActive: Boolean,
    interactive: Boolean,
    onPersuadeTapped: () -> Unit,
    rowFontSize: TextUnit = 13.sp,
    modifier: Modifier = Modifier
) {
    // Per-topic read-status flags (name -> 0/Specific/Exhausted) for colouring the topic rows,
    // collected directly here (this column already reads the repo for nav/session state).
    val topicFlags by GameStateRepository.dialogueTopicFlags.collectAsState()
    // Barter, Repair and Travel are pulled out of the services list so they can sit above the
    // divider (matched by their display strings — the sBarter/sRepair/sTravel GMST values the engine
    // exports). EVERY other service drops into the scrollable list with the topics.
    val barterService = services.firstOrNull { it.equals("Barter", ignoreCase = true) }
    val spellsService = services.firstOrNull { it.equals("Spells", ignoreCase = true) }
    val repairService = services.firstOrNull { it.equals("Repair", ignoreCase = true) }
    // sSpellmakingMenuTitle / sEnchanting GMST display values (verified against Morrowind.esm);
    // matched case-insensitively so "SpellMaking"/"Spellmaking" spelling variants both hit.
    val spellmakingService = services.firstOrNull { it.equals("Spellmaking", ignoreCase = true) }
    val enchantingService = services.firstOrNull { it.equals("Enchanting", ignoreCase = true) }
    val trainingService = services.firstOrNull { it.equals("Training", ignoreCase = true) }
    val travelService = services.firstOrNull { it.equals("Travel", ignoreCase = true) }
    // "Beds" (renting a room) is a dialogue TOPIC in Morrowind, not a service window — it's exported
    // among the topics and (when tapped) fires a yes/no choice. Pull it out of whichever list it
    // arrives in so it can sit in the services block above the divider; dispatch it as a service or
    // a topic depending on where it came from.
    val bedsFromService = services.firstOrNull { it.equals("Beds", ignoreCase = true) }
    val bedsFromTopic = topics.firstOrNull { it.equals("Beds", ignoreCase = true) }
    val bedsService = bedsFromService ?: bedsFromTopic
    val otherServices = services.filter {
        it != barterService && it != spellsService && it != repairService &&
            it != spellmakingService && it != enchantingService && it != trainingService &&
            it != travelService && it != bedsService
    }
    val topicRows = topics.filter { it != bedsService }

    // Flattened, ordered navigable rows: the services block (Barter/Beds/Persuade/Repair/Travel),
    // then any OTHER services, then the dialogue topics — the SAME order they render, so the D-pad
    // focus index maps 1:1 to a visible row. Goodbye is deliberately excluded (controller B closes
    // the conversation via companion-b-button-choice-fix.patch).
    // Above-divider order: Persuade → Barter → Spells → Repair → Spellmaking → Enchanting →
    // Training → Travel → Beds. (Beds is the conditional rent-a-room topic; Persuade sits first.)
    val serviceBlock = buildList {
        if (persuadeAvailable) add(DialogueNavItem("Persuade") { onPersuadeTapped() })
        barterService?.let { s -> add(DialogueNavItem(s) { CompanionActions.activateDialogueService(s) }) }
        spellsService?.let { s -> add(DialogueNavItem(s) { CompanionActions.activateDialogueService(s) }) }
        repairService?.let { s -> add(DialogueNavItem(s) { CompanionActions.activateDialogueService(s) }) }
        spellmakingService?.let { s -> add(DialogueNavItem(s) { CompanionActions.activateDialogueService(s) }) }
        enchantingService?.let { s -> add(DialogueNavItem(s) { CompanionActions.activateDialogueService(s) }) }
        trainingService?.let { s -> add(DialogueNavItem(s) { CompanionActions.activateDialogueService(s) }) }
        travelService?.let { s -> add(DialogueNavItem(s) { CompanionActions.activateDialogueService(s) }) }
        bedsService?.let { s ->
            add(DialogueNavItem(s) {
                if (bedsFromService != null) CompanionActions.activateDialogueService(s)
                else CompanionActions.selectDialogueTopic(s)
            })
        }
    }
    val listRows = buildList {
        otherServices.forEach { s -> add(DialogueNavItem(s) { CompanionActions.activateDialogueService(s) }) }
        topicRows.forEach { t -> add(DialogueNavItem(t) { CompanionActions.selectDialogueTopic(t) }) }
    }
    val navItems = serviceBlock + listRows
    // Index of the first non-block row (where the bold services/topics divider is drawn). Prefixing
    // the divider onto that row keeps one lazy item per navItem, so focusIndex == lazy index.
    val dividerAt = serviceBlock.size

    // ---- Controller focus (Phase 2) --------------------------------------------------------------
    // Focus starts at the first row when the conversation opens (this composable enters composition),
    // persists across topic selections within a conversation, and is clamped as the topic list grows/
    // shrinks. Only ONE DialogueRightColumn is ever composed (BOTTOM/SPLIT/TOP), so this collector
    // uniquely owns dialogue navigation.
    var focusIndex by remember { mutableStateOf(0) }
    LaunchedEffect(navItems.size) {
        if (navItems.isEmpty()) focusIndex = 0
        else if (focusIndex > navItems.lastIndex) focusIndex = navItems.lastIndex
    }
    val listState = rememberLazyListState()
    LaunchedEffect(focusIndex) {
        if (focusIndex in navItems.indices) listState.animateScrollToItem(focusIndex)
    }
    val itemsState = rememberUpdatedState(navItems)
    val interactiveState = rememberUpdatedState(interactive)
    val choicesActiveState = rememberUpdatedState(choicesActive)
    LaunchedEffect(Unit) {
        // Skip the StateFlow's replayed latest value / any event that predates this overlay.
        var lastSeq = GameStateRepository.navEvent.value?.seq ?: -1L
        GameStateRepository.navEvent.collect { ev ->
            if (ev == null || ev.seq <= lastSeq) return@collect
            lastSeq = ev.seq
            // Yield to a choice question (the choices popup owns nav), a cancelable modal on top
            // (persuasion popup) OR any higher overlay pushed OVER the conversation (barter/repair/
            // travel/rest): its own collector owns nav while it's up, so the dialogue list doesn't
            // move — and A can't select a topic behind it. Barter is Phase-4; the rest are Phase-5.
            if (choicesActiveState.value ||
                ModalNav.open ||
                GameStateRepository.barterSession.value != null ||
                GameStateRepository.repairSession.value != null ||
                GameStateRepository.travelSession.value != null ||
                GameStateRepository.sleepSession.value != null ||
                GameStateRepository.trainingSession.value != null ||
                GameStateRepository.spellBuyingSession.value != null
            ) return@collect
            val items = itemsState.value
            if (items.isEmpty()) return@collect
            when (ev) {
                is NavEvent.Down -> focusIndex = (focusIndex + 1) % items.size            // wraps
                is NavEvent.Up -> focusIndex = (focusIndex - 1 + items.size) % items.size  // wraps
                // A activates the focused row; only when the list is interactive (not while a choice
                // question is up — choices are navigated separately in Phase 5).
                is NavEvent.Confirm ->
                    if (interactiveState.value) items.getOrNull(focusIndex)?.onActivate?.invoke()
                else -> Unit // Left/Right/Action1/L2/R2/R1/slider are unused in the topic list
            }
        }
    }

    Column(modifier) {
        // 1. Disposition bar (fixed at the top). Hidden when unknown (< 0, e.g. a creature).
        //    A small divider below it stays put (outside the LazyColumn) while topics scroll.
        if (disposition >= 0) {
            DispositionBar(disposition)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.5f)))
            Spacer(Modifier.height(4.dp))
        }

        // 2. Services block + divider + other services + topics — one lazy item per navItem so the
        //    D-pad focus index maps directly to a lazy item. The divider is prefixed onto the
        //    boundary row (index == dividerAt), so it doesn't shift indices.
        LazyColumn(state = listState, modifier = Modifier.weight(1f).fillMaxWidth()) {
            itemsIndexed(navItems) { index, navItem ->
                if (index == dividerAt && dividerAt > 0) {
                    // Extra breathing room either side of the services/topics divider so it reads
                    // clearly — especially in Split mode and when Persuade (the last block row) is
                    // focused/highlighted right above it.
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
                    Spacer(Modifier.height(8.dp))
                }
                DialogueOptionRow(
                    navItem.label,
                    dimmed = choicesActive,
                    fontSize = rowFontSize,
                    focused = index == focusIndex,
                    // Only the below-divider topic rows carry the read-status colour; the service
                    // block (Persuade/Barter/…/Beds) stays the normal colour. Service rows below the
                    // divider aren't in the flags map either, so they resolve to 0.
                    topicFlag = if (index >= dividerAt) topicFlags[navItem.label] ?: 0 else 0
                ) {
                    if (interactive) navItem.onActivate()
                }
            }
        }

        // 6. Goodbye (pinned to the bottom of the column).
        Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(BronzeDark.copy(alpha = 0.2f))
                .clickable { if (interactive) CompanionActions.dialogueGoodbye() }
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "GOODBYE",
                color = if (choicesActive) BoneDim else BronzeLight, fontSize = 14.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
        }
    }
}

/** One tappable row in the right column — shared by topics, services and choices so
 *  they look identical (only the section header distinguishes them). [focused] draws the
 *  controller focus highlight (a 2dp BronzeLight border + faint fill) — deliberately a
 *  BORDER, distinct from any selection/worn styling elsewhere; dialogue rows are actions
 *  with no persistent "selected" state, so the border reads unambiguously as "cursor here". */
@Composable
private fun DialogueOptionRow(
    label: String,
    dimmed: Boolean = false,
    fontSize: TextUnit = 13.sp,
    focused: Boolean = false,
    // "color topic" read-status (0 none, 1 Specific-unheard, 2 Exhausted-read) — 0 for service/choice
    // rows. Colours a topic like the native list: BronzeLight = new/unheard, BoneDim = already read.
    topicFlag: Int = 0,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focused) Modifier
                    .background(BronzeLight.copy(alpha = 0.12f))
                    .border(2.dp, BronzeLight)
                else Modifier
            )
            .clickable(onClick = onClick)
    ) {
        Text(
            label,
            color = when {
                focused -> BoneBright
                dimmed -> BoneDim
                topicFlag and TOPIC_SPECIFIC != 0 -> BronzeLight   // NPC-specific info not yet heard
                topicFlag and TOPIC_EXHAUSTED != 0 -> BoneDim       // already read (in journal)
                else -> Bone
            },
            fontSize = fontSize, fontFamily = MwBody,
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp, horizontal = 4.dp)
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.5f)))
    }
}

// "color topic" read-status bit flags (mirror MWBase::DialogueManager::TopicType).
private const val TOPIC_SPECIFIC = 1
private const val TOPIC_EXHAUSTED = 2

/** Persuasion popup — a centred in-window overlay (NOT a Dialog; that crashes on the
 *  Presentation display) + scrim. Six persuasion option rows (bribes greyed + non-tappable
 *  when the player can't afford them, live gold via dialogueGold), a gold readout and a Cancel
 *  button. Each option sends CMPDLG:persuade:<type> and closes the popup (matches native
 *  Morrowind — reopen Persuade for another attempt); only the Cancel button dismisses (the scrim
 *  swallows taps — no tap-outside dismiss). Hosted independently of the conversation overlay, by
 *  the persuasion Screen Layout location (BOTTOM = CompanionScreen root; TOP = a top-screen panel
 *  window). [bandSized] = draw as the SPLIT topics-band panel (bottom screen, SPLIT conversation)
 *  instead of a centred 360dp card. */
@Composable
private fun PersuasionPopup(gold: Int, bandSized: Boolean, onPersuade: (Int) -> Unit, onCancel: () -> Unit) {
    // label, persuade type (matches native companionPersuade switch), gold cost
    val options = listOf(
        Triple("Admire", 0, 0),
        Triple("Intimidate", 1, 0),
        Triple("Taunt", 2, 0),
        Triple("Bribe 10 Gold", 3, 10),
        Triple("Bribe 100 Gold", 4, 100),
        Triple("Bribe 1000 Gold", 5, 1000)
    )
    // In the SPLIT bottom-screen band the popups use a larger row font (matches the topics/services
    // increase); the centred card keeps the default size.
    val splitMode = bandSized
    val optionFontSize = if (splitMode) SPLIT_ROW_FONT_SIZE else 14.sp

    // Controller: this popup is a cancelable modal. Mark it open so the dialogue-topic nav collector
    // yields underneath (no accidental topic select behind it) and native routes B to cancel JUST the
    // popup (COMPANION_NAV_CANCEL) — returning to the conversation instead of closing it. Selecting an
    // option with the D-pad/A is Phase 5; for now B cancels and touch picks an option.
    DisposableEffect(Unit) {
        ModalNav.count++
        CompanionActions.setModalCancelOpen(true)
        onDispose {
            ModalNav.count--
            if (ModalNav.count == 0) CompanionActions.setModalCancelOpen(false)
        }
    }
    // D-pad up/down navigates the options, A selects the focused one (respecting affordability),
    // B cancels the popup (native → COMPANION_NAV_CANCEL while this modal is open).
    val focusIndex = rememberListNavFocus(
        itemCount = options.size,
        onConfirm = { i ->
            val (_, type, cost) = options[i]
            if (gold >= cost) onPersuade(type)
        },
        onCancel = onCancel,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(30f)
            .background(Color(0x99000000))
            // In SPLIT view this popup matches the unified topics-panel band; otherwise it stays
            // a centred, content-sized card.
            .then(
                if (splitMode) Modifier.padding(top = TOP_BAR_SPACE.dp, bottom = BOTTOM_BAR_SPACE.dp)
                else Modifier
            )
            // No tap-outside dismiss — the scrim swallows taps; only the Cancel button closes it.
            .pointerInput(Unit) { detectTapGestures {} },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .then(if (splitMode) Modifier.splitDialoguePanel() else Modifier.width(360.dp))
                .mwPanel()
                .pointerInput(Unit) { detectTapGestures {} }
        ) {
            Text(
                "Persuasion", color = BronzeLight, fontSize = 15.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
            )
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))

            // Options. In SPLIT the panel is height-bounded (fills the topics band), so the options
            // take weight(1f) and SCROLL — otherwise 6 rows at the larger split font overflow and push
            // the gold/Cancel row past the clipped panel edge (bug). In BOTTOM/TOP the panel wraps its
            // content, so the options render plain and the whole box grows to fit.
            Column(
                modifier = if (splitMode)
                    Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                else Modifier.fillMaxWidth()
            ) {
                options.forEachIndexed { i, (label, type, cost) ->
                    val enabled = gold >= cost
                    // Alternating row backgrounds for readability.
                    val rowBg = if (i % 2 == 0) Color(0x22000000) else Color(0x11000000)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (i == focusIndex) BronzeLight.copy(alpha = 0.15f) else rowBg)
                            .then(if (i == focusIndex) Modifier.border(2.dp, BronzeLight) else Modifier)
                            .then(if (enabled) Modifier.clickable { onPersuade(type) } else Modifier)
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text(
                            label,
                            color = when {
                                !enabled -> BoneDim
                                i == focusIndex -> BoneBright
                                else -> Bone
                            },
                            fontSize = optionFontSize, fontFamily = MwBody
                        )
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.4f)))
                }
            }

            // Gold + Cancel — pinned below the (scrollable in SPLIT) options, always visible.
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Gold: $gold", color = BronzeLight, fontSize = 13.sp, fontFamily = MwData,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(SlotBg)
                        .border(1.dp, BronzeDark, RoundedCornerShape(2.dp))
                        .clickable { onCancel() }
                        .padding(horizontal = 18.dp, vertical = 8.dp)
                ) {
                    Text(
                        "CANCEL", color = BronzeLight, fontSize = 12.sp,
                        fontFamily = MwDisplay, fontWeight = FontWeight.Bold, letterSpacing = 1.sp
                    )
                }
            }
        }
    }
}

/** Conversation disposition bar: "Disposition" label + "X/100" over a filled bar.
 *  Fill colour follows disposition thresholds — green above 50, amber 30..50, red below
 *  30. Same track/border/shape as [CompactStat] so it reads as one system. [value] is
 *  clamped 0..100; callers hide it entirely when disposition is unknown (< 0). */
@Composable
private fun DispositionBar(value: Int) {
    val v = value.coerceIn(0, 100)
    val fillColor = when {
        v > 50 -> Color(0xFF7FBF7F)   // green — beneficial (matches effect-dot green)
        v >= 30 -> Color(0xFFD9A441)  // amber — neutral/wary
        else -> Color(0xFFC75C5C)     // red — hostile (matches effect-dot red)
    }
    // No "Disposition" label (it overflowed the narrow column at 100/100). Just the bar
    // with the value to its right.
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(9.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color(0xFF0E0B07))
                .border(1.dp, BronzeDark, RoundedCornerShape(1.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(v / 100f)
                    .height(9.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(fillColor)
            )
        }
        Spacer(Modifier.width(6.dp))
        Text("$v/100", color = Bone, fontSize = 12.sp, fontFamily = MwData)
    }
}

/**
 * Controller focus for a simple vertical single-column list overlay (Phase 5: persuasion, travel,
 * repair, choices). Owns a focus [index] into a list of [itemCount] items and consumes NavEvents:
 * D-pad Up/Down move focus (wrapping), A confirms the focused item ([onConfirm]), optional X
 * ([onAction1], e.g. Repair All) and B ([onCancel], for Compose-only modals — native B closes the
 * GM-mode overlays). [enabled] gates the whole collector so the overlay yields to a higher-priority
 * one on top (same pattern as Phase 4). Focus starts at 0 when the overlay opens and is clamped as
 * the list changes; returns the live focus index (-1 when empty). Only the topmost enabled collector
 * should act, so callers pass enabled = "no higher overlay is up".
 */
@Composable
private fun rememberListNavFocus(
    itemCount: Int,
    enabled: Boolean = true,
    // Which D-pad axis moves focus. false (default) = a vertical list (Up/Down); true = a
    // horizontal row (Left/Right — e.g. side-by-side choices in the Split conversation box).
    horizontal: Boolean = false,
    onConfirm: (Int) -> Unit,
    onAction1: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
): Int {
    var index by remember { mutableStateOf(0) }
    LaunchedEffect(itemCount) {
        index = if (itemCount <= 0) 0 else index.coerceIn(0, itemCount - 1)
    }
    val enabledState = rememberUpdatedState(enabled)
    val countState = rememberUpdatedState(itemCount)
    val confirmState = rememberUpdatedState(onConfirm)
    val action1State = rememberUpdatedState(onAction1)
    val cancelState = rememberUpdatedState(onCancel)
    LaunchedEffect(Unit) {
        var lastSeq = GameStateRepository.navEvent.value?.seq ?: -1L
        GameStateRepository.navEvent.collect { ev ->
            if (ev == null || ev.seq <= lastSeq) return@collect
            lastSeq = ev.seq
            if (!enabledState.value) return@collect
            val n = countState.value
            // The "next"/"prev" events depend on the axis: horizontal → Right/Left, else Down/Up.
            val isNext = if (horizontal) ev is NavEvent.Right else ev is NavEvent.Down
            val isPrev = if (horizontal) ev is NavEvent.Left else ev is NavEvent.Up
            when {
                isNext -> if (n > 0) index = (index + 1) % n
                isPrev -> if (n > 0) index = (index - 1 + n) % n
                ev is NavEvent.Confirm -> if (n > 0) confirmState.value(index)
                ev is NavEvent.Action1 -> action1State.value?.invoke()
                ev is NavEvent.Cancel -> cancelState.value?.invoke()
                else -> Unit
            }
        }
    }
    return if (itemCount > 0) index.coerceIn(0, itemCount - 1) else -1
}

/**
 * Right-stick scroll: nudges [state] by [step] pixels per tick, driven by the native per-frame
 * right-stick poll (companion-controller-nav.patch). The stick's axis is matched to the content's
 * axis: a VERTICAL list ([horizontal] = false) scrolls on right-stick up/down (ScrollUp/ScrollDown);
 * a HORIZONTAL grid ([horizontal] = true) scrolls on right-stick left/right (ScrollLeft/ScrollRight)
 * — so a physical left push scrolls left, right scrolls right. Each scrollable consumes only its own
 * axis's events, so the two never interfere.
 *
 * Each tick is a SHORT linear [animateScrollBy] (not an instant scrollBy) so that even when ticks
 * arrive at slightly irregular intervals (log-sink latency), each animation eases into the next and
 * the motion stays fluid; LinearEasing keeps constant velocity across ticks; the ~70ms tween ≈ the
 * native ~60ms cadence so animations chain back-to-back. [active] gates it so only the focused
 * scrollable reacts; it also yields while the quantity selector owns input.
 */
@Composable
private fun ScrollByNav(
    state: ScrollableState,
    active: Boolean = true,
    step: Float = 72f,
    horizontal: Boolean = false
) {
    val activeState = rememberUpdatedState(active)
    val spec = remember { tween<Float>(durationMillis = 70, easing = LinearEasing) }
    LaunchedEffect(Unit) {
        var lastSeq = GameStateRepository.navEvent.value?.seq ?: -1L
        GameStateRepository.navEvent.collect { ev ->
            if (ev == null || ev.seq <= lastSeq) return@collect
            lastSeq = ev.seq
            if (!activeState.value || ModalNav.open) return@collect
            // − = toward start (up / left), + = toward end (down / right).
            when {
                !horizontal && ev is NavEvent.ScrollUp -> state.animateScrollBy(-step, spec)
                !horizontal && ev is NavEvent.ScrollDown -> state.animateScrollBy(step, spec)
                horizontal && ev is NavEvent.ScrollLeft -> state.animateScrollBy(-step, spec)
                horizontal && ev is NavEvent.ScrollRight -> state.animateScrollBy(step, spec)
                else -> Unit
            }
        }
    }
}

/** Left column: the running NPC-response history, auto-scrolled to the newest line.
 *  When [choices] is non-empty (a mid-dialogue question) the choice buttons render
 *  inline as the LAST item of the list, directly below the most recent response. */
@Composable
private fun DialogueHistoryColumn(
    history: List<DialogueSay>,
    choices: List<DialogueChoice>,
    modifier: Modifier = Modifier,
    interactive: Boolean = true,
    // Current known-topic list. Unioned with each response's own captured hyperlinks
    // so a topic INTRODUCED by a response (not yet known when that line was received —
    // see the COMPANION_DIALOGUE emit ordering) highlights retroactively once it lands
    // in this list. Passing it here (vs. only say.hyperlinks) is what makes the history
    // recompose when topics change.
    topics: List<String> = emptyList(),
    // Lay the inline choice block out side-by-side (a Row, Left/Right nav) instead of a
    // vertical stack (Up/Down). Only the Split top-screen box uses this — the box is short
    // and wide, so two options read better beside each other. BOTTOM/TOP keep the stack.
    choicesHorizontal: Boolean = false
) {
    val listState = rememberLazyListState()
    // Auto-scroll to the newest item — a fresh response OR the choice block appearing.
    // Keyed on both history.size and choices.size so choices scrolling into view is
    // covered; the choice block is appended after all history so the last index shifts
    // by one when it's present.
    LaunchedEffect(history.size, choices.size) {
        val itemCount = history.size + if (choices.isNotEmpty()) 1 else 0
        if (itemCount > 0) listState.animateScrollToItem(itemCount - 1)
    }
    // Right stick scrolls the conversation history.
    ScrollByNav(listState)
    // Controller focus for the inline choice block (a mid-dialogue question). Reuses the same
    // nav helper the old centred DialogueChoicesPopup used: D-pad move focus (Up/Down for the
    // vertical stack, Left/Right for the Split side-by-side row), A confirms. Disabled unless a
    // choice is active AND the history is interactive; returns -1 when idle. id -1 = the synthetic
    // forced-goodbye prompt (NPC taunted into combat, etc.) → goodbye.
    val choiceFocus = rememberListNavFocus(
        itemCount = choices.size,
        enabled = choices.isNotEmpty() && interactive,
        horizontal = choicesHorizontal,
        onConfirm = { i ->
            val c = choices[i]
            if (c.id == -1) CompanionActions.dialogueGoodbye()
            else CompanionActions.activateDialogueChoice(c.id)
        },
    )
    LazyColumn(state = listState, modifier = modifier) {
        itemsIndexed(history) { i, say ->
            if (i > 0) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.5f)))
                Spacer(Modifier.height(8.dp))
            }
            if (say.topic.isNotEmpty()) {
                Text(
                    say.topic.uppercase(),
                    color = BronzeLight, fontSize = 11.sp,
                    fontFamily = MwDisplay, fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
            }
            if (say.isMessage) {
                // System message box (gold removed, etc.): highlighted BronzeLight, no
                // topic header, no tappable hyperlinks — matches the game's highlight.
                Text(
                    say.text,
                    color = BronzeLight, fontSize = 14.sp, fontFamily = MwBody, lineHeight = 22.4.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            } else {
                // Retroactive-highlight fix: union the response's captured hyperlinks with
                // the live topic list. When a newly-introduced topic arrives in `topics`
                // (after this line was already stored), the combined list changes and the
                // annotated string recomposes, lighting up the topic in-place.
                val links = remember(say.hyperlinks, topics) {
                    if (topics.isEmpty()) say.hyperlinks
                    else (say.hyperlinks + topics).distinct()
                }
                Text(
                    dialogueAnnotated(say.text, links, interactive),
                    color = Bone, fontSize = 14.sp, fontFamily = MwBody, lineHeight = 22.4.sp,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
            }
        }
        // Inline choice buttons, below the last response, while a question is active.
        if (choices.isNotEmpty()) {
            item {
                Spacer(Modifier.height(6.dp))
                Text(
                    "CHOOSE",
                    color = BoneDim, fontSize = 10.sp,
                    fontFamily = MwDisplay, fontWeight = FontWeight.Bold, letterSpacing = 2.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                if (choicesHorizontal) {
                    // Split: side-by-side options in equal-width columns, navigated Left/Right.
                    Row(Modifier.fillMaxWidth()) {
                        choices.forEachIndexed { i, choice ->
                            if (i > 0) Spacer(Modifier.width(6.dp))
                            DialogueChoiceBox(
                                choice, focused = i == choiceFocus, interactive = interactive,
                                centered = true, modifier = Modifier.weight(1f)
                            )
                        }
                    }
                } else {
                    // Bottom/Top: vertical stack, navigated Up/Down.
                    choices.forEachIndexed { i, choice ->
                        if (i > 0) Spacer(Modifier.height(6.dp))
                        DialogueChoiceBox(
                            choice, focused = i == choiceFocus, interactive = interactive,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

/** One inline dialogue-choice button, shared by [DialogueHistoryColumn]'s vertical stack and its
 *  Split side-by-side row. [focused] draws the controller-focus highlight (brighter fill + thicker
 *  BronzeLight border + BoneBright text — same as the old popup); taps are inert unless
 *  [interactive]. [centered] centres the label (for the equal-width side-by-side layout). id -1 =
 *  the synthetic forced-goodbye prompt (NPC taunted into combat, etc.) → goodbye, not an answer. */
@Composable
private fun DialogueChoiceBox(
    choice: DialogueChoice,
    focused: Boolean,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    centered: Boolean = false
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(2.dp))
            .background(BronzeLight.copy(alpha = if (focused) 0.22f else 0.06f))
            .border(
                if (focused) 2.dp else 1.dp,
                if (focused) BronzeLight else BronzeDark,
                RoundedCornerShape(2.dp)
            )
            .clickable {
                if (interactive) {
                    if (choice.id == -1) CompanionActions.dialogueGoodbye()
                    else CompanionActions.activateDialogueChoice(choice.id)
                }
            }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = if (centered) Alignment.Center else Alignment.CenterStart
    ) {
        Text(
            choice.text,
            color = if (focused) BoneBright else BronzeLight,
            fontSize = 14.sp, fontFamily = MwBody,
            textAlign = if (centered) TextAlign.Center else null
        )
    }
}

/**
 * Builds the response text as an AnnotatedString with each topic-hyperlink phrase
 * rendered BronzeLight and tappable (→ selectDialogueTopic). Case-insensitive,
 * longest-match-wins at any position so "Imperial cult" beats "Imperial". When
 * [interactive] is false (e.g. the history column is dimmed during persuasion) the
 * phrases stay highlighted BronzeLight but are NOT tappable.
 */
@Composable
private fun dialogueAnnotated(text: String, links: List<String>, interactive: Boolean = true): AnnotatedString = remember(text, links, interactive) {
    buildAnnotatedString {
        if (links.isEmpty()) {
            append(text)
            return@buildAnnotatedString
        }
        val lower = text.lowercase()
        val lowerLinks = links.map { it.lowercase() }
        var i = 0
        while (i < text.length) {
            var bestStart = -1
            var bestLen = 0
            for (w in lowerLinks) {
                if (w.isEmpty()) continue
                val idx = lower.indexOf(w, i)
                if (idx >= 0 && (bestStart == -1 || idx < bestStart || (idx == bestStart && w.length > bestLen))) {
                    bestStart = idx
                    bestLen = w.length
                }
            }
            if (bestStart == -1) {
                append(text.substring(i))
                break
            }
            if (bestStart > i) append(text.substring(i, bestStart))
            val phrase = text.substring(bestStart, bestStart + bestLen)  // preserve original case
            if (interactive) {
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "topic",
                        styles = TextLinkStyles(style = SpanStyle(color = BronzeLight))
                    ) { CompanionActions.selectDialogueTopic(phrase) }
                ) {
                    append(phrase)
                }
            } else {
                // Dimmed/inert (persuasion active): keep the highlight, drop the link.
                withStyle(SpanStyle(color = BronzeLight)) { append(phrase) }
            }
            i = bestStart + bestLen
        }
    }
}

/* ---- Item / spell info popup (small, position-aware) ---- */

private enum class PopupEdge { LEFT, RIGHT, TOP, BOTTOM }

/**
 * Global holder for the item info popup (mirrors the DropdownState pattern). Set from a long-press
 * "Info" tap or an R3 (NavEvent.Info) press on the focused item; consumed by the popup host at the
 * CompanionScreen root. `name`/`enchant` come from the LOCAL item (instant); the detailed base
 * rows arrive asynchronously via COMPANION_INFO (GameStateRepository.itemInfo). `anchor` is the
 * triggering row's bounds in root coords, refreshed by that row's onGloballyPositioned.
 */
object ItemInfoPopupState {
    var targetId by mutableStateOf<String?>(null)
    var name by mutableStateOf("")
    var enchant by mutableStateOf<ItemEnchant?>(null)
    var anchor by mutableStateOf<Rect?>(null)
    // true = the trigger came from a SPLIT top-screen grid cell, so the popup must render in the
    // top overlay (LootingTopOverlay/BarterTopOverlay), not the bottom CompanionScreen root.
    var onTopScreen by mutableStateOf(false)
    val isOpen get() = targetId != null

    fun open(id: String, name: String, ench: ItemEnchant?, isSpell: Boolean = false, onTop: Boolean = false) {
        if (id.isBlank()) return
        targetId = id
        this.name = name
        enchant = ench
        anchor = null
        onTopScreen = onTop
        GameStateRepository.dismissItemInfo()      // clear stale rows so the previous item's don't flash
        if (isSpell) CompanionActions.requestSpellInfo(id) else CompanionActions.requestItemInfo(id)
    }

    /** Long-press / R3 on the SAME item toggles the popup; a different item re-targets. */
    fun toggle(id: String, name: String, ench: ItemEnchant?, isSpell: Boolean = false, onTop: Boolean = false) {
        if (targetId == id) close() else open(id, name, ench, isSpell, onTop)
    }

    /** D-pad focus moved while open → follow to the newly focused item (don't dismiss). */
    fun follow(id: String, name: String, ench: ItemEnchant?, onTop: Boolean = false) {
        if (isOpen && id != targetId) open(id, name, ench, onTop = onTop)
    }

    fun close() {
        targetId = null
        name = ""
        enchant = null
        anchor = null
        onTopScreen = false
        GameStateRepository.dismissItemInfo()
    }

    /** A row reports its current bounds; only the active target's anchor is tracked (survives scroll). */
    fun reportAnchor(id: String, r: Rect) {
        if (targetId == id) anchor = r
    }
}

/**
 * Full scrim (tap-to-dismiss) + the floating card. Hosted at the bottom CompanionScreen root for
 * bottom contexts, and inside LootingTopOverlay/BarterTopOverlay for SPLIT top-grid triggers, so the
 * popup renders on whichever screen the triggering item lives (and anchors in that screen's coords).
 */
@Composable
private fun ItemInfoPopupHost() {
    // Own fillMaxSize box with TopStart alignment so the card's absolute .offset positioning is
    // independent of the CALLER's contentAlignment. The top overlays' root uses BottomCenter, which
    // would otherwise offset the card from a bottom-centre base and push it off-screen.
    Box(modifier = Modifier.fillMaxSize().zIndex(20f), contentAlignment = Alignment.TopStart) {
        // Scrim: tap outside dismisses. Shown immediately so interaction is blocked while we wait
        // for the anchor.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { ItemInfoPopupState.close() } }
        )
        // Only render the card once the triggering row's onGloballyPositioned has reported its
        // anchor. On open, anchor is reset to null; rendering with a null anchor would flash the
        // card centred for a frame before it jumps to the item — so wait for the real anchor.
        ItemInfoPopupState.anchor?.let { a ->
            ItemInfoFloatingPopup(anchor = a, enchant = ItemInfoPopupState.enchant)
        }
    }
}

/**
 * Positions the info card next to [anchor] and flips to stay on-screen: to the item's right when it
 * fits, else its left; vertically aligned to the row top, clamped to the screen. The side the popup
 * lands on is highlighted with a bright border edge (in lieu of a drawn arrow).
 */
@Composable
private fun ItemInfoFloatingPopup(anchor: Rect?, enchant: ItemEnchant?) {
    val info by GameStateRepository.itemInfo.collectAsState()
    val density = LocalDensity.current
    val cfg = LocalConfiguration.current
    val screenW = with(density) { cfg.screenWidthDp.dp.toPx() }
    val screenH = with(density) { cfg.screenHeightDp.dp.toPx() }
    val marginPx = with(density) { 8.dp.toPx() }
    val widthPx = with(density) { INFO_POPUP_WIDTH.dp.toPx() }
    var popupSize by remember { mutableStateOf(IntSize.Zero) }
    val hPx = popupSize.height.toFloat()

    val x: Float
    val y: Float
    val edge: PopupEdge
    if (anchor == null) {
        x = ((screenW - widthPx) / 2f).coerceAtLeast(marginPx)
        y = ((screenH - hPx) / 2f).coerceAtLeast(marginPx)
        edge = PopupEdge.TOP
    } else {
        val placeRight = anchor.right + widthPx + marginPx <= screenW
        edge = if (placeRight) PopupEdge.LEFT else PopupEdge.RIGHT
        val xRaw = if (placeRight) anchor.right + marginPx else anchor.left - widthPx - marginPx
        x = xRaw.coerceIn(marginPx, (screenW - widthPx - marginPx).coerceAtLeast(marginPx))
        y = anchor.top.coerceIn(marginPx, (screenH - hPx - marginPx).coerceAtLeast(marginPx))
    }

    Box(
        modifier = Modifier
            .zIndex(21f)
            .offset { IntOffset(x.roundToInt(), y.roundToInt()) }
            .width(INFO_POPUP_WIDTH.dp)
            .onSizeChanged { popupSize = it }
            // Swallow taps so tapping the card doesn't reach the dismiss scrim underneath.
            .pointerInput(Unit) { detectTapGestures {} }
    ) {
        ItemInfoPopupCard(info = info, enchant = enchant, edge = edge)
    }
}

private const val INFO_POPUP_WIDTH = 220

@Composable
private fun ItemInfoPopupCard(info: ItemInfo?, enchant: ItemEnchant?, edge: PopupEdge) {
    Box(
        Modifier
            .clip(RoundedCornerShape(3.dp))
            .background(StonePanel.copy(alpha = 0.97f))
            .border(2.dp, BronzeDark, RoundedCornerShape(3.dp))
            .wrapContentHeight()
    ) {
        // The Column drives the card height (wraps its content, scrolls if it exceeds the cap).
        Column(
            modifier = Modifier
                .padding(12.dp)
                .heightIn(max = 480.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                ItemInfoPopupState.name.ifBlank { info?.name ?: "" },
                color = BronzeLight, fontSize = 14.sp, fontFamily = MwDisplay,
                fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Bronze))
            Spacer(Modifier.height(6.dp))

            if (info == null) {
                Text("…", color = BoneDim, fontSize = 11.sp, fontFamily = MwData)
            } else {
                info.rows.forEach { (label, value) ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, color = BoneDim, fontSize = 11.sp, fontFamily = MwBody)
                        Text(
                            value, color = Bone, fontSize = 11.sp, fontFamily = MwData,
                            modifier = Modifier.padding(start = 10.dp)
                        )
                    }
                }
                // Intrinsic potion/ingredient alchemy effects (COMPANION_INFO effects).
                if (info.effects.isNotEmpty()) {
                    InfoSectionHeader("EFFECTS")
                    info.effects.forEach { eff -> InfoEffectRow(eff.text, eff.harmful, null) }
                }
            }

            // Enchantment section (from the local item — renders instantly, with effect icons).
            if (enchant != null && enchant.effects.isNotEmpty()) {
                InfoSectionHeader("ENCHANTMENT")
                if (enchant.type.isNotBlank()) {
                    Text(enchant.type, color = BronzeLight, fontSize = 10.sp, fontFamily = MwBody)
                    Spacer(Modifier.height(2.dp))
                }
                enchant.effects.forEach { e ->
                    val parts = buildString {
                        if (e.mag.isNotBlank() && e.mag != "0") append(" ${e.mag} pts")
                        if (e.durationSecs > 0) append(" for ${e.durationSecs}s")
                        if (e.area > 0) append(" in ${e.area}ft")
                    }
                    InfoEffectRow(e.name + parts, e.harmful, e.icon)
                }
            }
        }

        // Bright edge strip on the side facing the source item (border-highlight instead of an
        // arrow). matchParentSize() makes this overlay MATCH the Column-driven card height rather
        // than inflate it — a fillMaxHeight() direct child would have stretched the card to the
        // incoming (near-screen) height constraint, leaving empty space below the content.
        Box(Modifier.matchParentSize()) {
            val strip = when (edge) {
                PopupEdge.LEFT -> Modifier.align(Alignment.CenterStart).fillMaxHeight().width(3.dp)
                PopupEdge.RIGHT -> Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(3.dp)
                PopupEdge.TOP -> Modifier.align(Alignment.TopCenter).fillMaxWidth().height(3.dp)
                PopupEdge.BOTTOM -> Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(3.dp)
            }
            Box(strip.background(BronzeLight))
        }
    }
}

@Composable
private fun InfoSectionHeader(text: String) {
    Spacer(Modifier.height(8.dp))
    Text(
        text, color = BronzeLight, fontSize = 10.sp, fontFamily = MwDisplay,
        fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp
    )
    Spacer(Modifier.height(3.dp))
}

@Composable
private fun InfoEffectRow(text: String, harmful: Boolean, iconPath: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (!iconPath.isNullOrBlank()) {
            val bmp = rememberItemIcon(iconPath)
            Box(
                Modifier.size(18.dp).clip(RoundedCornerShape(2.dp)).background(SlotBg)
            ) { if (bmp != null) Image(bmp, null, modifier = Modifier.fillMaxSize()) }
            Spacer(Modifier.width(6.dp))
        } else {
            Box(Modifier.size(8.dp).clip(CircleShape)
                .background(if (harmful) Color(0xFFC75C5C) else Color(0xFF7FBF7F)))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = Bone, fontSize = 11.sp, fontFamily = MwBody)
    }
}

/* ---- Looting / pickpocketing overlay ---- */

/** Current controller focus in the looting overlay: which [side] (0 = player/left,
 *  1 = container/right) and which [index] into that side's sorted list. */
private data class LootFocus(val side: Int, val index: Int)

/** The per-side visible looting list: filter by the selected category tab (an INV_CATEGORIES
 *  label, null = All), then sort by category then name. Shared by the SPLIT grid columns' display
 *  and the controller focus index so the D-pad index maps 1:1 to a visible cell. Mirrors
 *  [barterVisible]. */
private fun lootVisible(items: List<InventoryItem>, categoryLabel: String?): List<InventoryItem> {
    val group = INV_CATEGORIES.find { it.label == categoryLabel }
    return items.filter { group == null || it.category in group.cats }
        .sortedWith(compareBy({ itemCategoryRank(it.category) }, { it.displayName().lowercase() }))
}

/** Cycle a looting column's category filter by [dir] (-1 = previous, +1 = next) through
 *  [All] + the INV_CATEGORIES actually present in [items] (in INV_CATEGORIES order), wrapping.
 *  Returns the new selection (null = All). Matches the visible CategoryTab set, so the L1/R1
 *  shoulder buttons mirror tapping the tabs. Mirrors [cycleBarterCat]. */
private fun cycleLootCat(items: List<InventoryItem>, current: String?, dir: Int): String? {
    val present = items.map { it.category }.toSet()
    val avail: List<String?> =
        listOf<String?>(null) + INV_CATEGORIES.filter { grp -> grp.cats.any { it in present } }.map { it.label }
    if (avail.size <= 1) return current
    val cur = avail.indexOf(current).coerceAtLeast(0)
    val next = ((cur + dir) % avail.size + avail.size) % avail.size
    return avail[next]
}

/**
 * Controller focus + navigation for the looting overlay (Phase 3), shared by the BOTTOM list
 * layout ([rows] = 1) and the SPLIT grid layout ([rows] = 4). Owns focus (side + index into that
 * side's pre-sorted list) and consumes NavEvents while the overlay is up:
 *  - D-pad moves focus. In grid mode (rows = 4) the list is column-major so Up/Down step ±1 within
 *    a grid column and Left/Right jump ±rows to the adjacent column (clamped). In list mode
 *    (rows = 1) Up/Down step ±1 and Left/Right switch side (the two lists sit side by side).
 *  - L2 / R2 switch to the player / container side (in both layouts).
 *  - A activates the focused item via [requestQty] → [onPut]/[onTake] (stacks > 1 prompt first).
 *  - X = [onTakeAll]; Y = [onDispose] (corpse only, [isCorpse]).
 *  - L1 / R1 cycle the focused column's category filter via [onCycleCategory] (SPLIT grids only).
 * Focus starts on the container side when it has items (the point of looting), else the player
 * side, and is clamped as items move between sides. Returns the live focus so the columns can
 * highlight the focused cell. Only ONE looting layout is composed at a time, so this collector is
 * unambiguous. B (Close) is handled by the existing patch, not here.
 */
@Composable
private fun rememberLootNavFocus(
    playerSorted: List<InventoryItem>,
    containerSorted: List<InventoryItem>,
    rows: Int,
    isCorpse: Boolean,
    requestQty: (String, Int, String, (Int) -> Unit) -> Unit,
    onPut: (InventoryItem, Int) -> Unit,
    onTake: (InventoryItem, Int) -> Unit,
    onTakeAll: () -> Unit,
    onDispose: () -> Unit,
    // Cycle the focused column's category filter (L1 = -1 prev, R1 = +1 next). No-op in the
    // BOTTOM list layout (no category tabs there); wired to the per-side state in the SPLIT grids.
    onCycleCategory: (side: Int, dir: Int) -> Unit = { _, _ -> },
): LootFocus {
    var side by remember { mutableStateOf(if (containerSorted.isNotEmpty()) 1 else 0) }
    var index by remember { mutableStateOf(0) }

    // Keep focus in range as the two lists change size (items taken/put) or the side switches.
    LaunchedEffect(playerSorted.size, containerSorted.size, side) {
        val n = if (side == 0) playerSorted.size else containerSorted.size
        index = index.coerceIn(0, (n - 1).coerceAtLeast(0))
    }

    val snapshot = rememberUpdatedState(Triple(playerSorted, containerSorted, isCorpse))
    // Category-cycle callback reads the parent's live per-side category state, so keep it fresh.
    val cycleState = rememberUpdatedState(onCycleCategory)
    LaunchedEffect(Unit) {
        var lastSeq = GameStateRepository.navEvent.value?.seq ?: -1L
        GameStateRepository.navEvent.collect { ev ->
            if (ev == null || ev.seq <= lastSeq) return@collect
            lastSeq = ev.seq
            if (ModalNav.open) return@collect // the quantity selector owns nav while up
            val (pSorted, cSorted, corpse) = snapshot.value
            fun sizeOf(s: Int) = if (s == 0) pSorted.size else cSorted.size
            fun clampTo(s: Int) { index = index.coerceIn(0, (sizeOf(s) - 1).coerceAtLeast(0)) }
            val size = sizeOf(side)
            when (ev) {
                is NavEvent.Down ->
                    if (rows <= 1) { if (index + 1 < size) index++ }
                    else if (index % rows < rows - 1 && index + 1 < size) index++
                is NavEvent.Up ->
                    if (rows <= 1) { if (index > 0) index-- }
                    else if (index % rows > 0) index--
                is NavEvent.Right ->
                    if (rows <= 1) { side = 1; clampTo(1) }
                    else if (index + rows < size) index += rows
                is NavEvent.Left ->
                    if (rows <= 1) { side = 0; clampTo(0) }
                    else if (index - rows >= 0) index -= rows
                is NavEvent.L2 -> { side = 0; clampTo(0) }
                is NavEvent.R2 -> { side = 1; clampTo(1) }
                is NavEvent.Confirm -> {
                    val item = (if (side == 0) pSorted else cSorted).getOrNull(index)
                    if (item != null) {
                        val isPlayer = side == 0
                        requestQty(item.displayName(), item.count, if (isPlayer) "Put" else "Take") { n ->
                            if (isPlayer) onPut(item, n) else onTake(item, n)
                        }
                    }
                }
                is NavEvent.Action1 -> onTakeAll()
                is NavEvent.Action2 -> if (corpse) onDispose()
                // Shoulder buttons cycle the focused side's category filter; reset focus to the
                // first item of the new (filtered) list. No-op in BOTTOM mode (no tabs there).
                is NavEvent.L1 -> { cycleState.value(side, -1); index = 0 }
                is NavEvent.R1 -> { cycleState.value(side, 1); index = 0 }
                is NavEvent.Info -> (if (side == 0) pSorted else cSorted).getOrNull(index)?.let {
                    ItemInfoPopupState.toggle(it.id, it.displayName(), it.enchant, onTop = rows > 1)
                }
                else -> Unit // Slider* / R2 handled above; nothing else used here
            }
            // While the info popup is open, D-pad focus moves follow to the newly focused item.
            if (ItemInfoPopupState.isOpen) {
                (if (side == 0) pSorted else cSorted).getOrNull(index)?.let {
                    ItemInfoPopupState.follow(it.id, it.displayName(), it.enchant, onTop = rows > 1)
                }
            }
        }
    }
    return LootFocus(side, index)
}

/**
 * Two-panel looting/pickpocketing overlay: the player's inventory on the left,
 * the container/corpse/NPC's contents on the right. Tapping a player item puts
 * it into the container; tapping a container item takes it (a QuantitySelector
 * prompt appears first for stacks > 1). Long-press exposes the fuller menu.
 *
 * In-window overlay (NOT a Compose Dialog — that crashes on the Presentation
 * display; see ItemInfoOverlay). zIndex 8f sits above the tab content/bars but
 * below the shared dropdown-dismiss scrim (10f) and QuantitySelector (20f).
 * A non-interactive scrim dims and blocks the tabs beneath; there is NO
 * tap-away dismiss (Close / B / the container window closing end the session).
 */
@Composable
private fun LootingOverlay(
    session: ContainerSession,
    playerInventory: List<InventoryItem>,
    playerEquipment: Map<String, String>,
    playerEncumbrance: Dynamic,
    location: ScreenLocation
) {
    // Dismiss the item info popup when the looting/pickpocket session ends (Take All/Dispose/Close/B)
    // so it doesn't linger. Composed in BOTH bottom and SPLIT modes, so this also covers the top-grid
    // popup (both this and LootingTopOverlay leave composition on session-null).
    DisposableEffect(Unit) { onDispose { ItemInfoPopupState.close() } }
    val playerName = rememberPlayerName()
    // SPLIT: the item grids live on the TOP screen (LootingTopOverlay, hosted by
    // EngineActivity); the bottom screen shows ONLY the terminal controls. Take All /
    // Dispose / Close all END the session, so the bottom controls never need the
    // optimistic item lists — all per-item take/put taps happen on the top grid.
    if (location == ScreenLocation.SPLIT) {
        LootingControlsOnly(session)
        return
    }

    // Optimistic local copies of both item lists. GM_Container pauses the sim, which
    // freezes the Lua slow tick AND starves the companion UI of background frames, so
    // engine re-exports can't refresh the display mid-session. Instead we mutate these
    // copies locally on each tap — the tap drives its own recomposition, so the move
    // shows instantly — while the CMP:container_* command still performs the REAL move
    // in the engine. Initialized once when the overlay enters composition (on open) and
    // deliberately NOT re-synced from later `session` emissions (stale/frame-starved
    // while paused). On close→reopen the overlay leaves+re-enters composition, so these
    // re-initialize from the fresh session/inventory and the true GameState reconciles.
    var containerItems by remember { mutableStateOf(session.items) }
    var playerItems by remember { mutableStateOf(playerInventory) }

    val playerGold = playerItems.firstOrNull { it.id.equals("gold_001", ignoreCase = true) }?.count ?: 0
    // Optimistic encumbrance: anchor on the real value at open (playerEncumbrance), then add/subtract
    // moved item weight per take/put/takeAll so the header updates live while GM_Container has the
    // Lua export frozen. Reconciles exactly to the engine value once looting closes.
    var encDelta by remember { mutableStateOf(0f) }
    val liveEncumbrance = Dynamic(playerEncumbrance.current + encDelta, playerEncumbrance.max)
    val wornIds = remember(playerEquipment) { playerEquipment.values.toSet() }
    fun isWorn(item: InventoryItem): Boolean =
        if (item.stackId.isNotEmpty()) wornIds.contains(item.stackId) else wornIds.contains(item.id)

    // Take: container → player (optimistic) + real CMP:container_take. Put: the reverse.
    fun take(item: InventoryItem, n: Int) {
        val (c, p) = moveOptimistic(containerItems, playerItems, item, n)
        containerItems = c; playerItems = p
        encDelta += item.weight * n
        CompanionActions.containerTake(item.stackId.ifEmpty { item.id }, n)
    }
    fun put(item: InventoryItem, n: Int) {
        val (p, c) = moveOptimistic(playerItems, containerItems, item, n)
        playerItems = p; containerItems = c
        encDelta -= item.weight * n
        CompanionActions.containerPut(item.stackId.ifEmpty { item.id }, n)
    }
    // Take All: optimistically empty the container into the player list, then fire the command
    // (Lua takes all AND closes the overlay). Shared by the button and the controller X action.
    fun takeAll() {
        encDelta += containerItems.sumOf { (it.weight * it.count).toDouble() }.toFloat()
        playerItems = containerItems.fold(playerItems) { acc, it ->
            moveOptimistic(listOf(it), acc, it, it.count).second
        }
        containerItems = emptyList()
        CompanionActions.containerTakeAll()
    }

    // Pre-sort both sides ONCE at the parent (worn-first player, alphabetical) — the single source
    // of order shared by the columns' display and the controller focus index.
    val playerSorted = remember(playerItems, wornIds) {
        playerItems.sortedWith(
            compareBy({ itemCategoryRank(it.category) }, { it.displayName().lowercase() })
        )
    }
    val containerSorted = remember(containerItems) {
        containerItems.sortedWith(compareBy({ itemCategoryRank(it.category) }, { it.displayName().lowercase() }))
    }
    // Controller focus (BOTTOM = two side-by-side lists → rows = 1).
    val focus = rememberLootNavFocus(
        playerSorted = playerSorted,
        containerSorted = containerSorted,
        rows = 1,
        isCorpse = session.isCorpse,
        requestQty = { name, count, label, action ->
            QuantityRequestState.requestOrRun(name, count, label, action)
        },
        onPut = { it, n -> put(it, n) },
        onTake = { it, n -> take(it, n) },
        onTakeAll = { takeAll() },
        onDispose = { CompanionActions.containerDispose() },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            // zIndex 15f (same as the dialogue overlay) — ABOVE the global
            // dropdown-dismiss scrim (10f). At the old 8f it sat BELOW that scrim,
            // so whenever the scrim was active (a LootRow long-press menu open, or
            // its anyOpen state stuck after a row left composition on a container
            // re-emit) it covered the whole overlay and swallowed every tap —
            // including the bottom buttons. Still below QuantitySelector/ItemInfo (20f)
            // so those stack above. Long-press-menu dismissal is handled by the local
            // scrim below (we can no longer rely on the global one from up here).
            .zIndex(15f)
            .background(Color(0xCC0F0C08))
            .pointerInput(Unit) { detectTapGestures {} }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Fills the full screen height (bottom = 12.dp, not BOTTOM_BAR_SPACE): looting is
                // a modal interaction left via Close / Take All / B, so the panel covers the
                // bottom tab bar — same approach as the barter overlay. 12dp matches the insets.
                .padding(top = 12.dp, bottom = 12.dp, start = 12.dp, end = 12.dp)
                .mwPanel()
                .pointerInput(Unit) { detectTapGestures {} }
        ) {
            // ---- Title bar: container name ----
            Text(
                session.containerName.ifBlank { "Container" },
                color = BronzeLight, fontSize = 14.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
            )
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))

            // ---- Two equal columns: player | container ----
            Row(Modifier.weight(1f).fillMaxWidth()) {
                LootColumn(
                    header = playerLootHeader(playerName, playerGold, liveEncumbrance),
                    legend = "tap to put · long press for more",
                    items = playerSorted,
                    isPlayerSide = true,
                    isWorn = { isWorn(it) },
                    onTransfer = { it, n -> put(it, n) },
                    focusedIndex = if (focus.side == 0) focus.index else -1,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp)
                )
                // Dashed vertical divider between the two columns.
                Canvas(Modifier.fillMaxHeight().width(1.dp)) {
                    drawLine(
                        color = BronzeDark,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = size.width.coerceAtLeast(1f),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                }
                LootColumn(
                    header = AnnotatedString(session.containerName.ifBlank { "Container" }),
                    legend = "tap to take · long press for more",
                    items = containerSorted,
                    isPlayerSide = false,
                    isWorn = { false },
                    onTransfer = { it, n -> take(it, n) },
                    // Pickpocket: an empty visible list usually means your Sneak hid the
                    // items (not that the NPC is broke) — say so. Corpses/chests: "Empty".
                    emptyText = if (session.isPickpocket) "Nothing you can lift" else "Empty",
                    focusedIndex = if (focus.side == 1) focus.index else -1,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp)
                )
            }

            // ---- Bottom action buttons (taller than standard rows) ----
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                LootButton(
                    // Always enabled (like Close). Gating on containerItems.isNotEmpty()
                    // made the button go dead — and non-responsive to taps — once the
                    // local list had been optimistically emptied (which is exactly the
                    // state you're in after taking items via the companion, e.g. under
                    // Hide UI where the companion is the only take path). Take All over
                    // an empty container simply closes, which is harmless.
                    label = "Take All", hint = "X",
                    enabled = true,
                    modifier = Modifier.weight(1f)
                ) { takeAll() }
                if (session.isCorpse) {
                    LootButton(
                        label = "Dispose of Corpse", hint = "R1",
                        enabled = true,
                        modifier = Modifier.weight(1f)
                    ) { CompanionActions.containerDispose() }
                }
                LootButton(
                    label = "Close", hint = "B",
                    enabled = true,
                    modifier = Modifier.weight(1f)
                ) { CompanionActions.containerClose() }
            }
        }

        // Local dropdown-dismiss scrim for the LootRow long-press menus. Because this
        // overlay renders ABOVE the global DropdownState scrim (10f) — required so its
        // own buttons stay tappable — we can't lean on that global scrim to close a
        // menu on tap-away. Replicate it here: rendered only while a menu is open,
        // above the panel content but below the DropdownMenu popup window, so a tap
        // outside the menu dismisses it instead of tapping through to a row/button.
        if (DropdownState.anyOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { DropdownState.closeAll() } }
            )
        }
    }
}

/** Colour for the loot-header encumbrance readout: muted normally, amber above 75 % of
 *  capacity, red at/over 100 %. */
private fun encumbranceColor(enc: Dynamic): Color = when {
    enc.max <= 0f -> BoneMuted
    enc.current >= enc.max -> Color(0xFFC75C5C)         // at/over capacity — red
    enc.current / enc.max > 0.75f -> Color(0xFFD9A441)  // over 75 % — amber
    else -> BoneMuted                                   // normal — muted bone
}

/** Player loot/pickpocket column header: "Name (123g) 145/200kg" with the weight tinted by
 *  load (see [encumbranceColor]). The name+gold ride the base BronzeLight; only the weight
 *  span overrides colour. NOTE: the sim is paused during GM_Container so [enc] is a snapshot
 *  from session open — it does not track optimistic take/put (unlike gold, recomputed live). */
private fun playerLootHeader(name: String, gold: Int, enc: Dynamic): AnnotatedString =
    buildAnnotatedString {
        append("$name (${gold}g) ")
        withStyle(SpanStyle(color = encumbranceColor(enc))) {
            append("${dynValue(enc)}kg")
        }
    }

/** One side of the looting overlay — header, legend, and a scrolling item list. [items] arrives
 *  pre-sorted from the parent (single source of order shared with the controller focus index).
 *  [focusedIndex] highlights that row (-1 = this side isn't focused) and keeps it on-screen. */
@Composable
private fun LootColumn(
    header: AnnotatedString,
    legend: String,
    items: List<InventoryItem>,
    isPlayerSide: Boolean,
    isWorn: (InventoryItem) -> Boolean,
    onTransfer: (InventoryItem, Int) -> Unit,
    emptyText: String = "Empty",
    focusedIndex: Int = -1,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            header,
            color = BronzeLight, fontSize = 13.sp,
            fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Text(
            legend,
            color = BoneDim, fontSize = 9.sp, fontFamily = MwBody,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.5f)))
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyText, color = BoneDim, fontSize = 12.sp, fontFamily = MwBody)
            }
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(focusedIndex) {
                if (focusedIndex in items.indices) listState.animateScrollToItem(focusedIndex)
            }
            // Right stick scrolls this list while it's the focused side.
            ScrollByNav(listState, active = focusedIndex >= 0)
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
                itemsIndexed(items) { i, item ->
                    LootRow(
                        item = item,
                        isPlayerSide = isPlayerSide,
                        worn = isPlayerSide && isWorn(item),
                        iconBitmap = rememberItemIcon(item.icon),
                        focused = i == focusedIndex,
                        onTransfer = onTransfer
                    )
                }
            }
        }
    }
}

/**
 * One item row in the looting overlay. Tap = transfer (put/take); long-press =
 * the fuller menu. A stack > 1 routes through QuantityRequestState so the shared
 * QuantitySelector asks "how many?" first.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LootRow(
    item: InventoryItem,
    isPlayerSide: Boolean,
    worn: Boolean,
    iconBitmap: ImageBitmap? = null,
    focused: Boolean = false,
    onTransfer: (InventoryItem, Int) -> Unit
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(DropdownState.closeRequest) {
        if (DropdownState.closeRequest > 0) menuOpen = false
    }
    val label = item.displayName()
    val sid = item.stackId.ifEmpty { item.id }
    val favs by FavouritesRepository.state.collectAsState()
    val isFav = isPlayerSide && favs.gear.any { it?.id == item.id }
    val confirmLabel = if (isPlayerSide) "Put" else "Take"

    // The tap action (put or take), prompting for a quantity when the stack > 1.
    // onTransfer performs BOTH the optimistic list move and the CMP:container_* command.
    fun transfer() {
        QuantityRequestState.requestOrRun(label, item.count, confirmLabel) { n ->
            onTransfer(item, n)
        }
    }

    Box(modifier = Modifier.onGloballyPositioned { ItemInfoPopupState.reportAnchor(item.id, it.boundsInRoot()) }) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Controller focus: a bronze border + faint fill on the whole row. Worn is shown
                    // by the brighter name + "WORN" tag (text only, no border), so this reads as a
                    // distinct "cursor here" marker.
                    .then(
                        if (focused) Modifier
                            .background(BronzeLight.copy(alpha = 0.12f))
                            .border(2.dp, BronzeLight)
                        else Modifier
                    )
                    .combinedClickable(
                        onClick = { transfer() },
                        onLongClick = { menuOpen = true; DropdownState.open() }
                    )
                    .padding(start = 8.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon box.
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SlotBg)
                        .border(1.dp, BronzeDark, RoundedCornerShape(2.dp))
                ) {
                    EnchantBackdrop(item.enchant != null)
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                // Name + sub-line (category · qty / cond).
                Column(Modifier.weight(1f).padding(end = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            label,
                            color = if (worn) BoneBright else BoneMuted,
                            fontSize = 13.sp, fontFamily = MwBody,
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isFav) { Spacer(Modifier.width(4.dp)); FavStar() }
                        if (worn) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "WORN",
                                color = BronzeLight, fontSize = 9.sp,
                                fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                    }
                    Text(
                        lootSubline(item),
                        color = BoneDim, fontSize = 9.sp, fontFamily = MwBody,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                // Tap hint (muted).
                Text(
                    if (isPlayerSide) "tap to put" else "tap to take",
                    color = BoneDim.copy(alpha = 0.7f), fontSize = 8.sp,
                    fontFamily = MwBody, letterSpacing = 0.3.sp
                )
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.4f)))
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false; DropdownState.closeAll() },
            properties = PopupProperties(focusable = false, dismissOnClickOutside = false),
            containerColor = StonePanel,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Bronze),
            shape = RoundedCornerShape(3.dp)
        ) {
            Text(
                label,
                color = BronzeLight, fontSize = 12.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark))
            val menuItemColors = MenuDefaults.itemColors(textColor = Bone)
            // Primary transfer.
            DropdownMenuItem(
                text = { Text(if (isPlayerSide) "Put" else "Take", fontFamily = MwBody, fontSize = 13.sp) },
                onClick = { menuOpen = false; DropdownState.closeAll(); transfer() },
                colors = menuItemColors
            )
            // Equip. Player side equips in place; container side takes the whole
            // stack then equips it by record id (the taken instance re-stacks, so we
            // can't target its new stackId — equipItem finds the first matching record).
            val equippable = item.category !in setOf("misc", "potion", "ingredient", "book", "scroll")
            if (equippable) {
                DropdownMenuItem(
                    text = { Text("Equip", fontFamily = MwBody, fontSize = 13.sp) },
                    onClick = {
                        menuOpen = false; DropdownState.closeAll()
                        if (isPlayerSide) {
                            CompanionActions.equipItem(sid)
                        } else {
                            // Optimistically take the whole stack (list move + command),
                            // then equip the taken item by record id.
                            onTransfer(item, item.count)
                            CompanionActions.equipItem(item.id)
                        }
                    },
                    colors = menuItemColors
                )
            }
            DropdownMenuItem(
                text = { Text("Info", fontFamily = MwBody, fontSize = 13.sp) },
                onClick = { menuOpen = false; DropdownState.closeAll(); ItemInfoPopupState.open(item.id, item.name, item.enchant) },
                colors = menuItemColors
            )
            // Favourite (player side only).
            if (isPlayerSide) {
                FavouriteMenuItems(
                    context = context,
                    isGear = true,
                    itemId = item.id,
                    makeSlot = { FavSlot(item.id, label) },
                    onDone = { menuOpen = false; DropdownState.closeAll() }
                )
            }
        }
    }
}

/**
 * Move `n` of `item` from `source` to `dest` for the optimistic looting UI. Reduces
 * (or removes at 0) the matched stack in `source` — matched by stackId when present,
 * else by record id — and merges `n` into a same-record stack in `dest`, else appends
 * a new stack. Approximate (e.g. distinct-condition stacks may merge for display); the
 * real GameState reconciles when the container closes. Returns (newSource, newDest).
 */
private fun moveOptimistic(
    source: List<InventoryItem>,
    dest: List<InventoryItem>,
    item: InventoryItem,
    n: Int
): Pair<List<InventoryItem>, List<InventoryItem>> {
    val moved = n.coerceIn(1, item.count)
    fun sameStack(a: InventoryItem): Boolean =
        if (a.stackId.isNotEmpty() && item.stackId.isNotEmpty()) a.stackId == item.stackId
        else a.id == item.id
    val newSource = source.mapNotNull { s ->
        if (sameStack(s)) (if (s.count > moved) s.copy(count = s.count - moved) else null) else s
    }
    val idx = dest.indexOfFirst { it.id == item.id }
    val newDest = if (idx >= 0) {
        dest.mapIndexed { i, d -> if (i == idx) d.copy(count = d.count + moved) else d }
    } else {
        dest + item.copy(count = moved)
    }
    return newSource to newDest
}

/** Sub-line under a loot item name: capitalized category + count + condition %. */
/* ---- Looting SPLIT mode (top-screen grids + bottom-screen controls) ---- */

// Gap between the two separate column boxes on the top screen. Single tweakable constant.
private val LOOT_SPLIT_COLUMN_GAP = 8.dp

// Each split-overlay column is its own boxed panel (dark fill, subtle border, rounded, padded);
// the gap between the two boxes provides the visual separation (no divider line).
private val SplitBoxBg = Color(0xFF1A1410)
private fun Modifier.splitColumnBox(): Modifier = this
    .clip(RoundedCornerShape(6.dp))
    .background(SplitBoxBg)
    .border(1.dp, BronzeDark, RoundedCornerShape(6.dp))
    .padding(8.dp)

/**
 * SPLIT-mode bottom-screen looting controls: just the terminal action buttons (Take All /
 * Dispose / Close), centred vertically in a narrow panel. The item grids live on the top
 * screen ([LootingTopOverlay]). All three actions END the session, so no optimistic item
 * state is needed here. Full-screen scrim (covers the tab bar — looting is modal); NO
 * tap-away dismiss.
 */
@Composable
private fun LootingControlsOnly(session: ContainerSession) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(15f)
            .background(Color(0xCC0F0C08))
            .pointerInput(Unit) { detectTapGestures {} },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.5f)
                .mwPanel()
                .pointerInput(Unit) { detectTapGestures {} }
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                session.containerName.ifBlank { "Container" },
                color = BronzeLight, fontSize = 14.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
            LootButton(
                label = "Take All", hint = "X", enabled = true,
                modifier = Modifier.fillMaxWidth()
            ) { CompanionActions.containerTakeAll() }
            if (session.isCorpse) {
                LootButton(
                    label = "Dispose of Corpse", hint = "Y", enabled = true,
                    modifier = Modifier.fillMaxWidth()
                ) { CompanionActions.containerDispose() }
            }
            LootButton(
                label = "Close", hint = "B", enabled = true,
                modifier = Modifier.fillMaxWidth()
            ) { CompanionActions.containerClose() }
        }
    }
}

/**
 * TOP-screen looting grids (Display 0). Hosted by [org.openmw.EngineActivity] in an
 * interactive WindowManager panel window while a container session is active AND Looting is
 * routed to SPLIT. Container items LEFT, player inventory RIGHT — both 3-row horizontally
 * scrolling icon grids. Tapping a cell takes/puts (a QuantitySelector prompt appears first
 * for stacks > 1); long-press opens the fuller menu.
 *
 * Reads its state straight from [GameStateRepository] (no params, like the conversation top
 * overlay). Optimistic local copies of both lists are mutated on each tap — the sim is paused
 * so engine re-exports can't refresh mid-session; the true GameState reconciles on close.
 */
@Composable
fun LootingTopOverlay() {
    val sessionState by GameStateRepository.containerSession.collectAsState()
    val state by GameStateRepository.state.collectAsState()
    val session = sessionState ?: return
    val playerName = rememberPlayerName()

    // Optimistic lists — remember WITHOUT a key so they init once on enter and are NOT
    // re-synced from later (frame-starved) session emissions, matching the bottom overlay.
    // The window is removed on session-null (EngineActivity), so close→reopen re-inits.
    var containerItems by remember { mutableStateOf(session.items) }
    var playerItems by remember { mutableStateOf(state.inventory) }

    // Gold recomputed from the optimistic list so it updates live as you loot coins (matches the
    // bottom overlay).
    val playerGold = playerItems.firstOrNull { it.id.equals("gold_001", ignoreCase = true) }?.count ?: 0
    // Optimistic encumbrance: anchor on the real value snapshotted at open, then add/subtract moved
    // item weight per take/put/takeAll (mirrors the bottom overlay). Reconciles exactly on close.
    val baseEncumbrance = remember { state.encumbrance }
    var encDelta by remember { mutableStateOf(0f) }
    val liveEncumbrance = Dynamic(baseEncumbrance.current + encDelta, baseEncumbrance.max)

    val wornIds = remember(state.equipment) { state.equipment.values.toSet() }
    fun isWorn(item: InventoryItem): Boolean =
        if (item.stackId.isNotEmpty()) wornIds.contains(item.stackId) else wornIds.contains(item.id)

    // Local quantity picker (kept on the TOP screen, not the global bottom-screen holder).
    var qtyReq by remember { mutableStateOf<QuantityRequest?>(null) }
    val requestQty: (String, Int, String, (Int) -> Unit) -> Unit = { name, count, label, action ->
        if (count > 1) qtyReq = QuantityRequest(name, count, label) { n -> qtyReq = null; action(n) }
        else action(1)
    }

    fun take(item: InventoryItem, n: Int) {
        val (c, p) = moveOptimistic(containerItems, playerItems, item, n)
        containerItems = c; playerItems = p
        encDelta += item.weight * n
        CompanionActions.containerTake(item.stackId.ifEmpty { item.id }, n)
    }
    fun put(item: InventoryItem, n: Int) {
        val (p, c) = moveOptimistic(playerItems, containerItems, item, n)
        playerItems = p; containerItems = c
        encDelta -= item.weight * n
        CompanionActions.containerPut(item.stackId.ifEmpty { item.id }, n)
    }
    fun takeAll() {
        encDelta += containerItems.sumOf { (it.weight * it.count).toDouble() }.toFloat()
        playerItems = containerItems.fold(playerItems) { acc, it ->
            moveOptimistic(listOf(it), acc, it, it.count).second
        }
        containerItems = emptyList()
        CompanionActions.containerTakeAll()
    }

    // Per-side category filter (an INV_CATEGORIES label; null = All), matching the barter grids.
    var playerCat by remember { mutableStateOf<String?>(null) }
    var containerCat by remember { mutableStateOf<String?>(null) }

    // The VISIBLE (category-filtered + sorted) lists — shared by the controller focus index; each
    // column re-derives the same via lootVisible for its display. Mirrors the barter overlay.
    val playerVisible = remember(playerItems, playerCat) { lootVisible(playerItems, playerCat) }
    val containerVisible = remember(containerItems, containerCat) { lootVisible(containerItems, containerCat) }
    // Controller focus (SPLIT = two 3-row icon grids → rows = 3). X/Y fire here too even though
    // the Take All / Dispose buttons live on the bottom controls window — they're plain commands.
    val focus = rememberLootNavFocus(
        playerSorted = playerVisible,
        containerSorted = containerVisible,
        rows = 3,
        isCorpse = session.isCorpse,
        requestQty = requestQty,
        onPut = { it, n -> put(it, n) },
        onTake = { it, n -> take(it, n) },
        onTakeAll = { takeAll() },
        onDispose = { CompanionActions.containerDispose() },
        onCycleCategory = { side, dir ->
            if (side == 0) playerCat = cycleLootCat(playerItems, playerCat, dir)
            else containerCat = cycleLootCat(containerItems, containerCat, dir)
        },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures {} }
            // Anchor the panel to the BOTTOM of the top screen, 12dp up.
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Two separate boxed columns (player | container) with an 8dp gap — no outer panel,
        // no divider line. 75% of the previous 0.9 height.
        Row(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.675f)
        ) {
            // LEFT: player.
            LootGridColumn(
                header = playerLootHeader(playerName, playerGold, liveEncumbrance),
                items = playerItems,
                isPlayerSide = true,
                isWorn = { isWorn(it) },
                emptyText = "Empty",
                selectedCategory = playerCat,
                onSelectCategory = { playerCat = it },
                onTransfer = { it, n -> put(it, n) },
                onRequestQty = requestQty,
                focusedIndex = if (focus.side == 0) focus.index else -1,
                modifier = Modifier.weight(1f).fillMaxHeight().splitColumnBox()
            )
            Spacer(Modifier.width(LOOT_SPLIT_COLUMN_GAP))
            // RIGHT: container.
            LootGridColumn(
                header = AnnotatedString(session.containerName.ifBlank { "Container" }),
                items = containerItems,
                isPlayerSide = false,
                isWorn = { false },
                emptyText = if (session.isPickpocket) "Nothing you can lift" else "Empty",
                selectedCategory = containerCat,
                onSelectCategory = { containerCat = it },
                onTransfer = { it, n -> take(it, n) },
                onRequestQty = requestQty,
                focusedIndex = if (focus.side == 1) focus.index else -1,
                modifier = Modifier.weight(1f).fillMaxHeight().splitColumnBox()
            )
        }

        // Local dropdown-dismiss scrim (this window has its own composition — can't lean on
        // the bottom-screen global scrim). Below the DropdownMenu popup, above the grids.
        if (DropdownState.anyOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { DropdownState.closeAll() } }
            )
        }

        // Local quantity picker (fills the window at zIndex 20f with its own scrim).
        qtyReq?.let { req ->
            QuantitySelector(
                name = req.name,
                max = req.max,
                confirmLabel = req.confirmLabel,
                onConfirm = req.onConfirm,
                onCancel = { qtyReq = null }
            )
        }

        // Item info popup on the TOP screen (SPLIT looting): anchored to the grid cell in THIS
        // window's coords. Only when the trigger came from here (onTopScreen).
        if (ItemInfoPopupState.isOpen && ItemInfoPopupState.onTopScreen) {
            ItemInfoPopupHost()
        }
    }
}

/** One side of the SPLIT looting grids — header, per-side category tabs, 3-row horizontally-
 *  scrolling icon grid. [items] is the FULL column; it's filtered+sorted here via [lootVisible]
 *  (the same the parent computes for the controller focus index). [focusedIndex] highlights that
 *  cell (-1 = this side isn't focused) and keeps it on-screen. Mirrors [BarterGridColumn]. */
@Composable
private fun LootGridColumn(
    header: AnnotatedString,
    items: List<InventoryItem>,
    isPlayerSide: Boolean,
    isWorn: (InventoryItem) -> Boolean,
    emptyText: String,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    onTransfer: (InventoryItem, Int) -> Unit,
    onRequestQty: (String, Int, String, (Int) -> Unit) -> Unit,
    focusedIndex: Int = -1,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            header,
            color = BronzeLight, fontSize = 14.sp,
            fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        val presentCats = remember(items) { items.map { it.category }.toSet() }
        val tabs = remember(presentCats) { INV_CATEGORIES.filter { grp -> grp.cats.any { it in presentCats } } }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CategoryTab("All", active = selectedCategory == null) { onSelectCategory(null) }
            tabs.forEach { c ->
                CategoryTab(c.label, active = selectedCategory == c.label) { onSelectCategory(c.label) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.5f)))
        Spacer(Modifier.height(6.dp))

        // Same filter+sort the controller focus index is computed against (lootVisible).
        val visible = remember(items, selectedCategory) { lootVisible(items, selectedCategory) }
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(emptyText, color = BoneDim, fontSize = 12.sp, fontFamily = MwBody)
            }
        } else {
            val gridState = rememberLazyGridState()
            LaunchedEffect(focusedIndex) {
                if (focusedIndex in visible.indices) gridState.animateScrollToItem(focusedIndex)
            }
            // Right stick left/right scrolls this horizontal grid while it's the focused side.
            ScrollByNav(gridState, active = focusedIndex >= 0, horizontal = true)
            LazyHorizontalGrid(
                // 3 rows. Column-major fill, so item index i sits at grid row i%3, grid column i/3
                // — MUST match the focus math in rememberLootNavFocus (rows = 3).
                state = gridState,
                rows = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                gridItemsIndexed(visible) { i, item ->
                    LootGridCell(
                        item = item,
                        isPlayerSide = isPlayerSide,
                        worn = isPlayerSide && isWorn(item),
                        iconBitmap = rememberItemIcon(item.icon),
                        focused = i == focusedIndex,
                        onTransfer = onTransfer,
                        onRequestQty = onRequestQty
                    )
                }
            }
            HorizontalGridScrollbar(gridState, Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * One icon cell in the SPLIT looting grids. Tap = transfer (take/put); long-press = the fuller
 * menu (Take/Put, Info, and Favourite on the player side). A worn item gets a bright bronze
 * border highlight. Stacks > 1 route the transfer through the quantity picker first.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LootGridCell(
    item: InventoryItem,
    isPlayerSide: Boolean,
    worn: Boolean,
    iconBitmap: ImageBitmap? = null,
    focused: Boolean = false,
    onTransfer: (InventoryItem, Int) -> Unit,
    onRequestQty: (String, Int, String, (Int) -> Unit) -> Unit
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(DropdownState.closeRequest) {
        if (DropdownState.closeRequest > 0) menuOpen = false
    }
    val label = item.displayName()
    val confirmLabel = if (isPlayerSide) "Put" else "Take"
    fun transfer() { onRequestQty(label, item.count, confirmLabel) { n -> onTransfer(item, n) } }

    Box(modifier = Modifier.onGloballyPositioned { ItemInfoPopupState.reportAnchor(item.id, it.boundsInRoot()) }) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(54.dp)
                // Controller focus: a bronze ring + faint fill around the WHOLE cell (icon + label).
                // Worn is an inner-icon BronzeLight border, so this outer ring + fill is distinct.
                .then(
                    if (focused) Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(BronzeLight.copy(alpha = 0.15f))
                        .border(2.dp, BronzeLight, RoundedCornerShape(4.dp))
                    else Modifier
                )
                .combinedClickable(
                    onClick = { transfer() },
                    onLongClick = { menuOpen = true; DropdownState.open() }
                )
                .padding(2.dp)
        ) {
            // Icon box — bronze highlight border when worn.
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(SlotBg)
                    .border(
                        BorderStroke(if (worn) 2.dp else 1.dp, if (worn) BronzeLight else BronzeDark),
                        RoundedCornerShape(3.dp)
                    )
            ) {
                EnchantBackdrop(item.enchant != null)
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(3.dp)
                    )
                }
                // Count badge (bottom-right).
                if (item.count > 1) {
                    Text(
                        "×${item.count}",
                        color = BoneBright, fontSize = 9.sp,
                        fontFamily = MwData, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(Color(0xCC0E0B07))
                            .padding(horizontal = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                color = if (worn) BoneBright else BoneMuted,
                fontSize = 8.sp, fontFamily = MwBody,
                textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                lineHeight = 9.sp,
                modifier = Modifier.width(54.dp)
            )
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false; DropdownState.closeAll() },
            properties = PopupProperties(focusable = false, dismissOnClickOutside = false),
            containerColor = StonePanel,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Bronze),
            shape = RoundedCornerShape(3.dp)
        ) {
            Text(
                label,
                color = BronzeLight, fontSize = 12.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark))
            val menuItemColors = MenuDefaults.itemColors(textColor = Bone)
            DropdownMenuItem(
                text = { Text(if (isPlayerSide) "Put" else "Take", fontFamily = MwBody, fontSize = 13.sp) },
                onClick = { menuOpen = false; DropdownState.closeAll(); transfer() },
                colors = menuItemColors
            )
            DropdownMenuItem(
                text = { Text("Info", fontFamily = MwBody, fontSize = 13.sp) },
                onClick = { menuOpen = false; DropdownState.closeAll(); ItemInfoPopupState.open(item.id, item.name, item.enchant, onTop = true) },
                colors = menuItemColors
            )
            if (isPlayerSide) {
                FavouriteMenuItems(
                    context = context,
                    isGear = true,
                    itemId = item.id,
                    makeSlot = { FavSlot(item.id, label) },
                    onDone = { menuOpen = false; DropdownState.closeAll() }
                )
            }
        }
    }
}

private fun lootSubline(item: InventoryItem): String {
    val parts = mutableListOf<String>()
    if (item.category.isNotBlank()) {
        parts.add(item.category.replaceFirstChar { it.uppercase() })
    }
    if (item.count > 1) parts.add("x${item.count}")
    item.cond?.let { parts.add("${(it.coerceIn(0f, 1f) * 100).toInt()}%") }
    return parts.joinToString(" · ")
}

/** A bottom action button in the looting overlay (Take All / Dispose / Close),
 *  taller than a list row, with a controller-binding hint (display only). */
@Composable
private fun LootButton(
    label: String,
    hint: String,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(35.dp)
            .clip(RoundedCornerShape(2.dp))
            .background(if (enabled) SlotWorn else SlotBg)
            .border(1.dp, if (enabled) BronzeLight else BronzeDark, RoundedCornerShape(2.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                label,
                color = if (enabled) BronzeLight else BoneDim, fontSize = 12.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "[$hint]",
                color = if (enabled) BoneDim else BoneDim.copy(alpha = 0.5f),
                fontSize = 10.sp, fontFamily = MwData
            )
        }
    }
}

/* ---- Reusable quantity picker ---- */

// A standalone, reusable "how many?" overlay. Kept decoupled from inventory/
// dropping so it can be dropped into looting (transfer N corpse→player) and
// bartering (transfer N player↔vendor) later without changes: it only knows a
// display name, a max, and confirm/cancel callbacks.
//
// In-window overlay (NOT a Compose Dialog): the companion UI lives inside a
// Presentation on a secondary display, where a Dialog throws "Window type
// mismatch" — see ItemInfoOverlay. Tapping the scrim cancels; taps inside the
// panel are swallowed. +/- step by 1; ±10 buttons appear for larger stacks so
// big quantities aren't a hundred taps. Quantity is always clamped to [1, max].
@Composable
private fun QuantitySelector(
    name: String,
    max: Int,
    confirmLabel: String = "Confirm",
    onConfirm: (Int) -> Unit,
    onCancel: () -> Unit
) {
    val safeMax = max.coerceAtLeast(1)
    // Default to 1 (a mis-tapped Confirm then only drops one item); the "Max"
    // button jumps straight to the whole stack for dump-everything.
    var qty by remember(name, safeMax) { mutableStateOf(1) }
    fun set(v: Int) { qty = v.coerceIn(1, safeMax) }
    val showTens = safeMax > 10

    // Controller: while this selector is up it OWNS nav — mark it open so the grid/slider collectors
    // underneath yield, and drive the quantity from the D-pad. D-pad up/right = +1, down/left = −1;
    // A confirms; B cancels JUST the selector (native intercepts B → COMPANION_NAV_CANCEL while
    // companionQtySelectorOpen, instead of closing the whole overlay). Also pushes that open flag to
    // native so the B interception knows to fire; the counter guards against brief enter/exit overlap.
    DisposableEffect(Unit) {
        ModalNav.count++
        CompanionActions.setModalCancelOpen(true)
        onDispose {
            ModalNav.count--
            if (ModalNav.count == 0) CompanionActions.setModalCancelOpen(false)
        }
    }
    LaunchedEffect(name, safeMax) {
        var lastSeq = GameStateRepository.navEvent.value?.seq ?: -1L
        GameStateRepository.navEvent.collect { ev ->
            if (ev == null || ev.seq <= lastSeq) return@collect
            lastSeq = ev.seq
            when (ev) {
                is NavEvent.Up, is NavEvent.Right -> set(qty + 1)
                is NavEvent.Down, is NavEvent.Left -> set(qty - 1)
                is NavEvent.Confirm -> onConfirm(qty)
                is NavEvent.Cancel -> onCancel()
                else -> Unit
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(Color(0xB3000000))
            .pointerInput(Unit) { detectTapGestures(onTap = { onCancel() }) },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .mwPanel()
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                name,
                color = BronzeLight,
                fontSize = 16.sp,
                fontFamily = MwDisplay,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(16.dp))

            // Stepper row: [-10] [-]  qty  [+] [+10]
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showTens) {
                    QtyButton("−10", enabled = qty > 1) { set(qty - 10) }
                    Spacer(Modifier.width(6.dp))
                }
                QtyButton("−", enabled = qty > 1) { set(qty - 1) }
                Text(
                    qty.toString(),
                    color = BoneBright,
                    fontSize = 34.sp,
                    fontFamily = MwData,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(84.dp)
                )
                QtyButton("+", enabled = qty < safeMax) { set(qty + 1) }
                if (showTens) {
                    Spacer(Modifier.width(6.dp))
                    QtyButton("+10", enabled = qty < safeMax) { set(qty + 10) }
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("of $safeMax", color = BoneDim, fontSize = 12.sp, fontFamily = MwBody)
                Spacer(Modifier.width(10.dp))
                // Quick "Max" set — cheaper than tapping +10 up a big stack.
                Text(
                    "MAX",
                    color = if (qty < safeMax) BronzeLight else BoneDim,
                    fontSize = 12.sp,
                    fontFamily = MwDisplay,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .clickable { set(safeMax) }
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }

            Spacer(Modifier.height(18.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QtyActionButton("Cancel", Modifier.weight(1f), primary = false) { onCancel() }
                QtyActionButton(confirmLabel, Modifier.weight(1f), primary = true) { onConfirm(qty) }
            }
        }
    }
}

// Square +/- stepper button used by QuantitySelector.
@Composable
private fun QtyButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (enabled) SlotWorn else SlotBg)
            .border(1.dp, if (enabled) Bronze else BronzeDark, RoundedCornerShape(4.dp))
            .clickable(enabled = enabled) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) BronzeLight else BoneDim,
            fontSize = if (label.length > 1) 15.sp else 22.sp,
            fontFamily = MwData,
            fontWeight = FontWeight.Bold
        )
    }
}

// Confirm / Cancel button used by QuantitySelector.
@Composable
private fun QtyActionButton(
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (primary) SlotWorn else BronzeDark.copy(alpha = 0.2f))
            .border(1.dp, if (primary) Bronze else BronzeDark, RoundedCornerShape(3.dp))
            .clickable { onClick() }
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (primary) BronzeLight else Bone,
            fontSize = 14.sp,
            fontFamily = MwDisplay,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }
}

/* ---- On-screen text-entry keyboard (bottom screen) ---- */

// Custom keyboard rows: a letters page and a numbers/symbols page. See TextInputOverlay for why
// this is a hand-drawn Compose keyboard rather than the Android IME.
private val KB_LETTERS = listOf(
    listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
    listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
    listOf("z", "x", "c", "v", "b", "n", "m"),
)
private val KB_SYMBOLS = listOf(
    listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
    listOf("-", "/", ":", ";", "(", ")", "&", "@", "'", "\""),
    listOf(".", ",", "?", "!", "+", "_"),
)

private fun shiftLabel(k: String, shift: Boolean): String = if (shift) k.uppercase() else k

/**
 * On-screen text-entry overlay for character name / class name / save name. Driven by
 * COMPANION_TEXT_INPUT_OPEN/_CLOSED (native, when a MyGUI EditBox gains/loses key focus) and shown
 * whenever [GameStateRepository.textInputRequest] is non-null. A CUSTOM Compose keyboard, deliberately
 * NOT the Android OS keyboard: the IME is a focusable window that moves Android's input-focused
 * display to the bottom screen, after which controller input goes to the bottom-screen launcher and
 * cannot be reliably returned to the game (proven across several attempts — moveTaskToFront, focus
 * nudges, etc.). This overlay is only TOUCH-interactive (no key focus), so the game keeps controller
 * focus the whole time, and the keys always render on the bottom panel regardless of the user's IME
 * pinning. [onConfirm] fires on Enter with the full text → CMPTEXT:set, which fills the field and
 * injects Return to accept/advance the dialog. Auto-dismisses when the request clears (commit, or the
 * top field losing focus). In-window overlay, NOT a Dialog (Dialog crashes on the Presentation).
 */
@Composable
private fun TextInputOverlay(
    initialText: String,
    onConfirm: (String) -> Unit
) {
    var text by remember(initialText) { mutableStateOf(initialText) }
    // One-shot shift: capitalises the next letter then resets. Starts on for an empty field so names
    // begin with a capital.
    var shift by remember(initialText) { mutableStateOf(initialText.isEmpty()) }
    var symbols by remember(initialText) { mutableStateOf(false) }

    val typeKey: (String) -> Unit = { k ->
        text += shiftLabel(k, shift)
        if (shift) shift = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(22f)
            .background(Color(0xCC0B0906))
            // Swallow taps outside the card — no cancel/escape (in-game prompts can't always be
            // escaped; cancelling, when possible, is done on the top screen).
            .pointerInput(Unit) { detectTapGestures(onTap = {}) },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.97f)
                .mwPanel()
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .padding(horizontal = 12.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "ENTER TEXT",
                color = BronzeLight,
                fontSize = 14.sp,
                fontFamily = MwDisplay,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp
            )
            Spacer(Modifier.height(8.dp))
            // Read-only display of the current text (the keys edit [text]).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(SlotBg)
                    .border(1.dp, Bronze, RoundedCornerShape(4.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    text.ifEmpty { " " },
                    color = BoneBright,
                    fontSize = 20.sp,
                    fontFamily = MwData,
                    maxLines = 1
                )
            }
            Spacer(Modifier.height(10.dp))

            val rows = if (symbols) KB_SYMBOLS else KB_LETTERS
            KbRow { rows[0].forEach { k -> KbKey(shiftLabel(k, shift)) { typeKey(k) } } }
            KbRow { rows[1].forEach { k -> KbKey(shiftLabel(k, shift)) { typeKey(k) } } }
            KbRow {
                if (symbols) {
                    Spacer(Modifier.weight(1.6f))
                } else {
                    KbKey("⇧", weight = 1.6f, active = shift) { shift = !shift }
                }
                rows[2].forEach { k -> KbKey(shiftLabel(k, shift)) { typeKey(k) } }
                KbKey("⌫", weight = 1.6f) { if (text.isNotEmpty()) text = text.dropLast(1) }
            }
            KbRow {
                KbKey(if (symbols) "ABC" else "123", weight = 1.6f) { symbols = !symbols }
                KbKey("space", weight = 4.4f) { text += " " }
                KbKey("Enter", weight = 2.2f, primary = true) { onConfirm(text) }
            }
        }
    }
}

@Composable
private fun KbRow(content: @Composable RowScope.() -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), content = content)
}

@Composable
private fun RowScope.KbKey(
    label: String,
    weight: Float = 1f,
    primary: Boolean = false,
    active: Boolean = false,
    onClick: () -> Unit
) {
    val highlight = primary || active
    Box(
        modifier = Modifier
            .weight(weight)
            .padding(2.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(if (highlight) SlotWorn else SlotBg)
            .border(1.dp, if (highlight) Bronze else BronzeDark, RoundedCornerShape(4.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (highlight) BronzeLight else Bone,
            fontSize = 17.sp,
            fontFamily = MwData
        )
    }
}

/* ---- Merchant-repair overlay (bottom screen) ---- */

// Condition-bar thresholds (spec): red ≤40%, amber 41–70%, green 71–99% (and 100%).
private val RepairCondRed = Color(0xFFE87070)
private val RepairCondAmber = Color(0xFFC8A040)
private val RepairCondGreen = Color(0xFF6AAA6A)

private fun repairCondColor(ratio: Float): Color = when {
    ratio <= 0.40f -> RepairCondRed
    ratio <= 0.70f -> RepairCondAmber
    else -> RepairCondGreen
}

/**
 * Bottom-screen merchant-repair overlay. Driven by COMPANION_REPAIR_* (native MerchantRepair
 * window), shown whenever [GameStateRepository.repairSession] is non-null and Repair is DS.
 * NOT a Compose Dialog (crashes on the Presentation display — see ItemInfoOverlay); an
 * in-window centred Box at zIndex 17f. No tap-outside dismiss — only the Cancel button
 * closes it (→ CMP:repair_cancel → the engine pops GM_MerchantRepair and emits
 * COMPANION_REPAIR_CLOSED). Each row taps to repair that item immediately; the engine
 * re-exports the (shorter) list + updated gold after every repair, so [session] just
 * refreshes in place (no optimistic local state needed — repair is one authoritative call).
 */
@Composable
private fun RepairOverlay(session: RepairSession) {
    // Bottom vs Split from the repair layout pref (Top pending → falls back to the bottom card).
    // Currently BOTTOM-only (no Split pill), so this is always the centred card.
    val splitMode = UiPreferences.repairLocationFlow().collectAsState().value == ScreenLocation.SPLIT
    val rowFontSize = if (splitMode) SPLIT_ROW_FONT_SIZE else 14.sp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(17f)
            .background(Color(0xCC0F0C08))
            // In SPLIT view match the unified topics-panel band; otherwise centre a 0.7×0.86 card.
            .then(
                if (splitMode) Modifier.padding(top = TOP_BAR_SPACE.dp, bottom = BOTTOM_BAR_SPACE.dp)
                else Modifier
            )
            // Swallow taps so nothing falls through to the tab underneath. NO dismiss.
            .pointerInput(Unit) { detectTapGestures {} },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .then(if (splitMode) Modifier.splitDialoguePanel() else Modifier.fillMaxWidth(0.7f).fillMaxHeight(0.86f))
                .mwPanel()
                .pointerInput(Unit) { detectTapGestures {} }
        ) {
            // ---- Title bar ----
            Text(
                "Repair — ${session.npcName.ifBlank { "Merchant" }}",
                color = BronzeLight, fontSize = 15.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
            )
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))

            // ---- Item list ---- (D-pad up/down navigate, A repairs the focused one, X repairs all)
            val focusIndex = rememberListNavFocus(
                itemCount = session.items.size,
                onConfirm = { i ->
                    val it = session.items[i]
                    if (it.cost <= session.playerGold) CompanionActions.repairItem(it.sid)
                },
                onAction1 = { if (session.items.isNotEmpty()) CompanionActions.repairAll() },
            )
            val listState = rememberLazyListState()
            LaunchedEffect(focusIndex) {
                if (focusIndex in session.items.indices) listState.animateScrollToItem(focusIndex)
            }
            if (session.items.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "Nothing needs repair", color = BoneDim,
                        fontSize = 13.sp, fontFamily = MwBody
                    )
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                    itemsIndexed(session.items, key = { _, it -> it.sid }) { i, item ->
                        RepairRow(
                            item = item,
                            affordable = item.cost <= session.playerGold,
                            nameFontSize = rowFontSize,
                            focused = i == focusIndex,
                            onRepair = { CompanionActions.repairItem(item.sid) }
                        )
                        Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.4f)))
                    }
                }
            }

            // ---- Divider + bottom row ----
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Gold: ${session.playerGold}", color = BronzeLight, fontSize = 13.sp,
                    fontFamily = MwData, modifier = Modifier.weight(1f)
                )
                // Repair All shows the total cost of ALL listed items; the engine repairs as
                // many as the player can afford (cheapest-affordable first). Disabled only
                // when there's nothing at all to repair.
                RepairButton(
                    label = "Repair All (${session.totalCost}g)",
                    color = BarterGreen,
                    enabled = session.items.isNotEmpty()
                ) { CompanionActions.repairAll() }
                RepairButton(label = "Cancel", color = BarterBlue, enabled = true) {
                    CompanionActions.repairCancel()
                }
            }
        }
    }
}

/** One repair row: name (flex) + condition bar with "X/Y" + cost, tap to repair. Unaffordable
 *  rows are dimmed with a red cost and are not tappable (the engine ignores them anyway). */
@Composable
private fun RepairRow(
    item: RepairItem,
    affordable: Boolean,
    nameFontSize: TextUnit = 14.sp,
    focused: Boolean = false,
    onRepair: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Controller focus: a bronze border + faint fill on the focused row.
            .then(
                if (focused) Modifier.background(BronzeLight.copy(alpha = 0.12f)).border(2.dp, BronzeLight)
                else Modifier
            )
            .then(if (affordable) Modifier.clickable { onRepair() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            item.name,
            color = when {
                !affordable -> BoneDim
                focused -> BoneBright
                else -> Bone
            },
            fontSize = nameFontSize, fontFamily = MwBody,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        // Condition column: a thin bar + "X/Y" text.
        Column(
            modifier = Modifier.width(96.dp).padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color(0xFF0E0B07))
                    .border(1.dp, BronzeDark, RoundedCornerShape(2.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(item.ratio)
                        .fillMaxHeight()
                        .background(repairCondColor(item.ratio))
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                "${item.condition}/${item.maxCondition}",
                color = BoneDim, fontSize = 9.sp, fontFamily = MwData
            )
        }
        Text(
            "${item.cost}g",
            color = if (affordable) BronzeLight else RepairCondRed,
            fontSize = 13.sp, fontFamily = MwData,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(54.dp)
        )
    }
}

/** A repair action button (Repair All / Cancel), styled like [BarterButton]. */
@Composable
private fun RepairButton(
    label: String,
    color: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (enabled) color.copy(alpha = 0.20f) else SlotBg)
            .border(1.dp, if (enabled) color else BronzeDark, RoundedCornerShape(3.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (enabled) color else BoneDim,
            fontSize = 12.sp, fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

/* ---- Training overlay (bottom screen) ---- */

// Tweakable timings for the training completion flow (see TrainingOverlayHost).
private const val TRAINING_SAFETY_MS = 5000L         // force-dismiss if CLOSED never arrives
private const val TRAINING_COMPLETE_DWELL_MS = 1200L // how long "…training complete" shows

private enum class TrainingPhase { LIST, TRAINING, COMPLETE }

/**
 * Host for the bottom-screen training overlay. Driven by COMPANION_TRAINING_* (native TrainingWindow).
 * Unlike Repair, the overlay must OUTLIVE [GameStateRepository.trainingSession] going null: the repo
 * nulls the session on COMPANION_TRAINING_CLOSED (fired after the native 2-hour fade/advance), but the
 * "…training complete" message needs to show afterwards. So mount is latched locally ([overlayActive],
 * set when a session first appears, cleared only by the dismiss path) and the NPC name is cached, and
 * a local three-state machine drives the content:
 *
 *   LIST → (tap a valid skill) → TRAINING → (CLOSED / 5s safety) → COMPLETE → (1.2s dwell) → dismissed
 *
 * On EVERY dismiss path we also call [CompanionActions.trainCancel] — idempotent (native guards on
 * containsMode(GM_Training); the CLOSED emit is once-guarded), so it no-ops if the mode already popped,
 * but rescues a stuck hidden GM_Training if a train command was rejected and no CLOSED ever came.
 */
@Composable
private fun TrainingOverlayHost(session: TrainingSession?) {
    var overlayActive by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(TrainingPhase.LIST) }
    var trainedSkillName by remember { mutableStateOf("") }
    var cachedNpcName by remember { mutableStateOf("") }

    val hasSession = session != null

    fun dismiss() {
        CompanionActions.trainCancel() // idempotent; rescues a stuck GM_Training if the train was rejected
        overlayActive = false
        phase = TrainingPhase.LIST
    }

    // Activate + cache the NPC name when a session first appears. Latched — we do NOT unmount when
    // the session later goes null (the TRAINING/COMPLETE messages must outlive COMPANION_TRAINING_CLOSED).
    LaunchedEffect(hasSession) {
        if (session != null) {
            overlayActive = true
            session.npcName.takeIf { it.isNotBlank() }?.let { cachedNpcName = it }
        }
    }

    // Session-null transitions: from TRAINING it means the train finished (→ COMPLETE); from LIST it
    // means the conversation ended externally (B / native close / NPC left) with no train in flight,
    // so just tear the overlay down. COMPLETE is timer-driven and ignores the session.
    LaunchedEffect(phase, hasSession) {
        if (!hasSession && overlayActive) {
            when (phase) {
                TrainingPhase.TRAINING -> phase = TrainingPhase.COMPLETE
                TrainingPhase.LIST -> dismiss()
                else -> Unit
            }
        }
    }

    // Timed transitions: safety net out of TRAINING, and the COMPLETE dwell before dismiss.
    LaunchedEffect(phase) {
        when (phase) {
            TrainingPhase.TRAINING -> {
                delay(TRAINING_SAFETY_MS)
                if (phase == TrainingPhase.TRAINING) phase = TrainingPhase.COMPLETE
            }
            TrainingPhase.COMPLETE -> {
                delay(TRAINING_COMPLETE_DWELL_MS)
                dismiss()
            }
            else -> Unit
        }
    }

    if (!overlayActive) return

    // Bottom vs Split from the training layout pref (Top pending → never selectable; falls back to
    // the bottom card). Mirrors the looting/bartering per-service layout selectors.
    val splitMode = UiPreferences.trainingLocationFlow().collectAsState().value == ScreenLocation.SPLIT
    val rowFontSize = if (splitMode) SPLIT_ROW_FONT_SIZE else 14.sp
    val skills = session?.skills ?: emptyList()
    val playerGold = session?.playerGold ?: 0

    // Tapping any NON-capped row sends the train command; native silently rejects it if the player
    // can't afford it (matches Spell Buying — unaffordable is a transient gold state, not a hard block
    // like capped). We only enter the TRAINING/COMPLETE flow when the train will actually happen
    // (affordable), so a rejected unaffordable tap stays on the list instead of showing a false
    // "…training complete". Capped rows never reach here (non-tappable / onConfirm-guarded).
    fun attemptTrain(sk: TrainingSkill) {
        if (sk.capped) return
        CompanionActions.trainSkill(sk.index)
        if (sk.cost <= playerGold) {
            trainedSkillName = sk.skillName
            GameStateRepository.markTrainingInProgress()
            phase = TrainingPhase.TRAINING
        }
    }

    // List navigation (LIST phase only): D-pad up/down, A trains the focused row, B cancels.
    val focusIndex = rememberListNavFocus(
        itemCount = skills.size,
        enabled = phase == TrainingPhase.LIST,
        onConfirm = { i -> skills.getOrNull(i)?.let { attemptTrain(it) } },
        onCancel = { if (phase == TrainingPhase.LIST) dismiss() }
    )
    val listState = rememberLazyListState()
    LaunchedEffect(focusIndex) {
        if (focusIndex in skills.indices) listState.animateScrollToItem(focusIndex)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(17f)
            .background(Color(0xCC0F0C08))
            .then(
                if (splitMode) Modifier.padding(top = TOP_BAR_SPACE.dp, bottom = BOTTOM_BAR_SPACE.dp)
                else Modifier
            )
            .pointerInput(Unit) { detectTapGestures {} }, // swallow taps; NO tap-outside dismiss
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .then(if (splitMode) Modifier.splitDialoguePanel() else Modifier.fillMaxWidth(0.7f).fillMaxHeight(0.86f))
                .mwPanel()
                .pointerInput(Unit) { detectTapGestures {} }
        ) {
            // ---- Title bar ----
            Text(
                "Training — ${cachedNpcName.ifBlank { "Trainer" }}",
                color = BronzeLight, fontSize = 15.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
            )
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))

            // ---- Content: list (LIST) or centred message (TRAINING / COMPLETE) ----
            if (phase == TrainingPhase.LIST) {
                if (skills.isEmpty()) {
                    Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                        Text("No skills to train", color = BoneDim, fontSize = 13.sp, fontFamily = MwBody)
                    }
                } else {
                    LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                        itemsIndexed(skills, key = { _, sk -> sk.index }) { i, sk ->
                            TrainingRow(
                                skill = sk,
                                affordable = sk.cost <= playerGold,
                                nameFontSize = rowFontSize,
                                focused = i == focusIndex,
                                onTrain = { attemptTrain(sk) }
                            )
                            Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.4f)))
                        }
                    }
                }
            } else {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        if (phase == TrainingPhase.COMPLETE) "${trainedSkillName.ifBlank { "Skill" }} training complete"
                        else "Training…",
                        color = BronzeLight, fontSize = 16.sp,
                        fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
            }

            // ---- Divider + bottom row ----
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Gold: $playerGold", color = BronzeLight, fontSize = 13.sp,
                    fontFamily = MwData, modifier = Modifier.weight(1f)
                )
                // Cancel only makes sense while choosing (LIST); during the messages it's inert.
                RepairButton(label = "Cancel", color = BarterBlue, enabled = phase == TrainingPhase.LIST) {
                    dismiss()
                }
            }
        }
    }
}

/** One training row: skill name (flex) + "Current: X[ (capped)]" beneath + cost. Capped rows are
 *  dimmed with "—" and fully non-tappable (a hard block — the skill can't be trained here at all).
 *  Unaffordable rows show a RED cost but stay tappable/focusable (matches SpellForSaleRow): a tap
 *  sends the train command and the native window rejects it silently, and keeping them focusable
 *  preserves a stable D-pad order as gold changes. */
@Composable
private fun TrainingRow(
    skill: TrainingSkill,
    affordable: Boolean,
    nameFontSize: TextUnit = 14.sp,
    focused: Boolean = false,
    onTrain: () -> Unit
) {
    val tappable = !skill.capped
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focused) Modifier.background(BronzeLight.copy(alpha = 0.12f)).border(2.dp, BronzeLight)
                else Modifier
            )
            .then(if (tappable) Modifier.clickable { onTrain() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                skill.skillName,
                color = when {
                    skill.capped -> BoneDim
                    focused -> BoneBright
                    else -> Bone
                },
                fontSize = nameFontSize, fontFamily = MwBody,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                if (skill.capped) "Current: ${skill.currentLevel} (capped)" else "Current: ${skill.currentLevel}",
                color = BoneDim, fontSize = 10.sp, fontFamily = MwData
            )
        }
        Text(
            if (skill.capped) "—" else "${skill.cost}g",
            color = when {
                skill.capped -> BoneDim
                affordable -> BronzeLight
                else -> RepairCondRed
            },
            fontSize = 13.sp, fontFamily = MwData, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(60.dp)
        )
    }
}

/* ---- Spell-buying overlay (bottom screen) ---- */

/**
 * Bottom-screen spell-buying overlay. Driven by COMPANION_SPELLBUYING_* (native SpellBuyingWindow),
 * shown whenever [GameStateRepository.spellBuyingSession] is non-null and Spell buying is DS. Same
 * frame as RepairOverlay (in-window centred Box at zIndex 17f, no tap-outside dismiss). Each row taps
 * to buy immediately; the engine re-exports the list + gold after each purchase, so [session] just
 * refreshes in place (the bought spell reappears greyed as "Already known", holding indices stable).
 */
@Composable
private fun SpellBuyingOverlay(session: SpellBuyingSession) {
    // Bottom vs Split from the spell-buying layout pref (Top pending → falls back to the bottom card).
    val splitMode = UiPreferences.spellBuyingLocationFlow().collectAsState().value == ScreenLocation.SPLIT
    val rowFontSize = if (splitMode) SPLIT_ROW_FONT_SIZE else 14.sp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(17f)
            .background(Color(0xCC0F0C08))
            .then(
                if (splitMode) Modifier.padding(top = TOP_BAR_SPACE.dp, bottom = BOTTOM_BAR_SPACE.dp)
                else Modifier
            )
            .pointerInput(Unit) { detectTapGestures {} }, // swallow taps; NO tap-outside dismiss
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .then(if (splitMode) Modifier.splitDialoguePanel() else Modifier.fillMaxWidth(0.7f).fillMaxHeight(0.86f))
                .mwPanel()
                .pointerInput(Unit) { detectTapGestures {} }
        ) {
            // ---- Title bar ----
            Text(
                "Spells — ${session.npcName.ifBlank { "Merchant" }}",
                color = BronzeLight, fontSize = 15.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
            )
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))

            // ---- Spell list ---- (D-pad up/down navigate, A buys the focused one)
            val focusIndex = rememberListNavFocus(
                itemCount = session.spells.size,
                onConfirm = { i ->
                    session.spells.getOrNull(i)?.let { sp ->
                        // Known → nothing to buy. Unaffordable → tappable but native rejects silently.
                        if (!sp.known) CompanionActions.buySpell(sp.index)
                    }
                },
            )
            val listState = rememberLazyListState()
            LaunchedEffect(focusIndex) {
                if (focusIndex in session.spells.indices) listState.animateScrollToItem(focusIndex)
            }
            if (session.spells.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No spells for sale", color = BoneDim, fontSize = 13.sp, fontFamily = MwBody)
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                    itemsIndexed(session.spells, key = { _, sp -> sp.index }) { i, spell ->
                        SpellForSaleRow(
                            spell = spell,
                            affordable = spell.cost <= session.playerGold,
                            nameFontSize = rowFontSize,
                            focused = i == focusIndex,
                            onBuy = { if (!spell.known) CompanionActions.buySpell(spell.index) }
                        )
                        Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.4f)))
                    }
                }
            }

            // ---- Divider + bottom row ----
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Gold: ${session.playerGold}", color = BronzeLight, fontSize = 13.sp,
                    fontFamily = MwData, modifier = Modifier.weight(1f)
                )
                RepairButton(label = "Cancel", color = BarterBlue, enabled = true) {
                    CompanionActions.spellBuyingCancel()
                }
            }
        }
    }
}

/** One spell-for-sale row: name (flex) + school beneath + cost. Known spells are dimmed with
 *  "Already known" + "—" and non-tappable; unaffordable spells show a red cost but stay tappable
 *  (the native window rejects the purchase silently — the red cost is the only signal). */
@Composable
private fun SpellForSaleRow(
    spell: SpellForSale,
    affordable: Boolean,
    nameFontSize: TextUnit = 14.sp,
    focused: Boolean = false,
    onBuy: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (focused) Modifier.background(BronzeLight.copy(alpha = 0.12f)).border(2.dp, BronzeLight)
                else Modifier
            )
            .then(if (!spell.known) Modifier.clickable { onBuy() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                spell.spellName,
                color = when {
                    spell.known -> BoneDim
                    focused -> BoneBright
                    else -> Bone
                },
                fontSize = nameFontSize, fontFamily = MwBody,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            Text(
                if (spell.known) "Already known" else spell.school,
                color = BoneDim, fontSize = 10.sp, fontFamily = MwData
            )
        }
        Text(
            if (spell.known) "—" else "${spell.cost}g",
            color = when {
                spell.known -> BoneDim
                affordable -> BronzeLight
                else -> RepairCondRed
            },
            fontSize = 13.sp, fontFamily = MwData, fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(60.dp)
        )
    }
}

/* ---- Travel overlay (bottom screen) ---- */

/**
 * Bottom-screen travel overlay. Driven by COMPANION_TRAVEL_* (native TravelWindow), shown whenever
 * [GameStateRepository.travelSession] is non-null and Travel is DS. NOT a Compose Dialog (crashes on
 * the Presentation display — see ItemInfoOverlay); an in-window centred Box at zIndex 17f. No
 * tap-outside dismiss — only Cancel closes it (→ CMP:travel_cancel → the engine pops GM_Travel and
 * emits COMPANION_TRAVEL_CLOSED). Each row taps to travel to that destination immediately
 * (→ CMP:travel_go <index> → the native onTravelButtonClick path: gold, time advance, follower
 * teleport). Travelling also ends the conversation, so the dialogue overlay dismisses alongside.
 */
@Composable
private fun TravelOverlay(session: TravelSession) {
    // Bottom vs Split from the travel layout pref (Top pending → falls back to the bottom card).
    // Currently BOTTOM-only (no Split pill), so this is always the centred card.
    val splitMode = UiPreferences.travelLocationFlow().collectAsState().value == ScreenLocation.SPLIT
    val rowFontSize = if (splitMode) SPLIT_ROW_FONT_SIZE else 14.sp
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(17f)
            .background(Color(0xCC0F0C08))
            // In SPLIT view match the unified topics-panel band; otherwise centre a 0.7×0.86 card.
            .then(
                if (splitMode) Modifier.padding(top = TOP_BAR_SPACE.dp, bottom = BOTTOM_BAR_SPACE.dp)
                else Modifier
            )
            // Swallow taps so nothing falls through to the tab underneath. NO dismiss.
            .pointerInput(Unit) { detectTapGestures {} },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .then(if (splitMode) Modifier.splitDialoguePanel() else Modifier.fillMaxWidth(0.7f).fillMaxHeight(0.86f))
                .mwPanel()
                .pointerInput(Unit) { detectTapGestures {} }
        ) {
            // ---- Title bar ----
            Text(
                "Travel — ${session.npcName.ifBlank { "Caravaner" }}",
                color = BronzeLight, fontSize = 15.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
            )
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))

            // ---- Destination list ---- (D-pad up/down navigate, A travels to the focused one)
            val focusIndex = rememberListNavFocus(
                itemCount = session.destinations.size,
                onConfirm = { i ->
                    val d = session.destinations[i]
                    if (d.cost <= session.playerGold) CompanionActions.travelGo(d.index)
                },
            )
            val listState = rememberLazyListState()
            LaunchedEffect(focusIndex) {
                if (focusIndex in session.destinations.indices) listState.animateScrollToItem(focusIndex)
            }
            if (session.destinations.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        "No destinations", color = BoneDim,
                        fontSize = 13.sp, fontFamily = MwBody
                    )
                }
            } else {
                LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                    itemsIndexed(session.destinations, key = { _, it -> it.index }) { i, dest ->
                        TravelRow(
                            dest = dest,
                            affordable = dest.cost <= session.playerGold,
                            nameFontSize = rowFontSize,
                            focused = i == focusIndex,
                            onTravel = { CompanionActions.travelGo(dest.index) }
                        )
                        Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.4f)))
                    }
                }
            }

            // ---- Divider + bottom row (player gold + Cancel; no "Travel All") ----
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Gold: ${session.playerGold}", color = BronzeLight, fontSize = 13.sp,
                    fontFamily = MwData, modifier = Modifier.weight(1f)
                )
                RepairButton(label = "Cancel", color = BarterBlue, enabled = true) {
                    CompanionActions.travelCancel()
                }
            }
        }
    }
}

/** One travel row: destination name (flex) + cost, tap to travel immediately. Unaffordable rows are
 *  dimmed with a red cost and are not tappable (the engine ignores them anyway). */
@Composable
private fun TravelRow(
    dest: TravelDest,
    affordable: Boolean,
    nameFontSize: TextUnit = 14.sp,
    focused: Boolean = false,
    onTravel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Controller focus: a bronze border + faint fill on the focused row.
            .then(
                if (focused) Modifier.background(BronzeLight.copy(alpha = 0.12f)).border(2.dp, BronzeLight)
                else Modifier
            )
            .then(if (affordable) Modifier.clickable { onTravel() } else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            dest.name,
            color = when {
                !affordable -> BoneDim
                focused -> BoneBright
                else -> Bone
            },
            fontSize = nameFontSize, fontFamily = MwBody,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            "${dest.cost}g",
            color = if (affordable) BronzeLight else RepairCondRed,
            fontSize = 13.sp, fontFamily = MwData,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.End,
            modifier = Modifier.width(60.dp)
        )
    }
}

/* ---- Rest/Wait overlay (bottom screen) ---- */

/**
 * Bottom-screen rest/wait picker. Driven by COMPANION_SLEEP_* (native WaitDialog), shown
 * whenever [GameStateRepository.sleepSession] is non-null and Rest/Wait is DS. NOT a Compose
 * Dialog (crashes on the Presentation display); an in-window centred Box at zIndex 17f, no
 * tap-outside dismiss. Confirming "Rest"/"Wait" sends CMP:sleep <hours> — the engine then runs
 * the fade + time advance (+ sleep interruption / level-up) on the TOP screen and emits
 * COMPANION_SLEEP_CLOSED, which dismisses this overlay. Cancel sends CMP:sleep_cancel.
 */
@Composable
private fun RestWaitOverlay(session: SleepSession) {
    val isRest = session.mode == SleepMode.REST
    var hours by remember { mutableStateOf(1) }

    // Controller: D-pad left/right adjust the hours by ±1; the left stick (SliderLeft/Right, polled
    // at ~60ms) does the same but smoothly while held; A confirms (Rest/Wait); X rests until healed
    // when that button is available (mirrors vanilla's X binding). B cancels natively.
    LaunchedEffect(Unit) {
        var lastSeq = GameStateRepository.navEvent.value?.seq ?: -1L
        GameStateRepository.navEvent.collect { ev ->
            if (ev == null || ev.seq <= lastSeq) return@collect
            lastSeq = ev.seq
            when (ev) {
                is NavEvent.Left, is NavEvent.SliderLeft -> hours = (hours - 1).coerceIn(1, 24)
                is NavEvent.Right, is NavEvent.SliderRight -> hours = (hours + 1).coerceIn(1, 24)
                is NavEvent.Confirm -> CompanionActions.sleep(hours)
                is NavEvent.Action1 ->
                    if (isRest && session.untilHealedAvailable) CompanionActions.sleep(session.hoursToHeal)
                else -> Unit
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(17f)
            .background(Color(0xCC0F0C08))
            .pointerInput(Unit) { detectTapGestures {} },   // swallow taps, NO dismiss
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .mwPanel()
                .pointerInput(Unit) { detectTapGestures {} }
        ) {
            // ---- Title ----
            Text(
                if (isRest) "Rest" else "Wait",
                color = BronzeLight, fontSize = 15.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
            )
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))

            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ---- Date / time ----
                Text(
                    session.dateString, color = Bone, fontSize = 15.sp,
                    fontFamily = MwBody, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(14.dp))

                // ---- Middle line: illegal-rest warning (WAIT) or "REST" banner ----
                if (!isRest && session.warning.isNotBlank()) {
                    Text(
                        session.warning, color = RepairCondRed, fontSize = 13.sp,
                        fontFamily = MwBody, textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                } else if (isRest) {
                    Text(
                        "REST", color = BronzeLight, fontSize = 22.sp,
                        fontFamily = MwDisplay, fontWeight = FontWeight.Bold, letterSpacing = 3.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(14.dp))
                }

                // ---- Hours label ----
                Text(
                    "$hours ${if (hours == 1) "hour" else "hours"}",
                    color = Bone, fontSize = 16.sp, fontFamily = MwData,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))

                // ---- Slider (1..24, no track numbers, 1h / 24h end labels) ----
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("1h", color = BoneDim, fontSize = 11.sp, fontFamily = MwData)
                    Slider(
                        value = hours.toFloat(),
                        onValueChange = { hours = it.roundToInt().coerceIn(1, 24) },
                        valueRange = 1f..24f,
                        steps = 22,   // 24 discrete stops (1..24)
                        colors = SliderDefaults.colors(
                            thumbColor = BronzeLight,
                            activeTrackColor = BronzeLight,
                            inactiveTrackColor = BronzeDark,
                            activeTickColor = Color.Transparent,
                            inactiveTickColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
                    )
                    Text("24h", color = BoneDim, fontSize = 11.sp, fontFamily = MwData)
                }
            }

            // ---- Buttons ----
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
            // "Rest Until Healed" — REST mode only, and only when the engine reports it available
            // (health or magicka below max). Rests for the exact native getHoursToRest() count,
            // replayed via the existing CMP:sleep <hours>. Full-width row above Rest/Cancel so the
            // long label has room; mirrors vanilla, which shows it only in this same condition.
            if (isRest && session.untilHealedAvailable) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 14.dp),
                ) {
                    RepairButton(
                        label = "Rest Until Healed",
                        color = BronzeLight, enabled = true,
                        modifier = Modifier.fillMaxWidth()
                    ) { CompanionActions.sleep(session.hoursToHeal) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RepairButton(
                    label = if (isRest) "Rest" else "Wait",
                    color = BarterGreen, enabled = true,
                    modifier = Modifier.weight(1f)
                ) { CompanionActions.sleep(hours) }
                RepairButton(
                    label = "Cancel", color = BarterBlue, enabled = true,
                    modifier = Modifier.weight(1f)
                ) { CompanionActions.sleepCancel() }
            }
        }
    }
}

/* ---- Barter overlay (bottom screen) ---- */

private val BarterGreen = Color(0xFF7FBF7F)   // Offer / "player receives" (matches effect green)
private val BarterRed = Color(0xFFC75C5C)     // "player pays" / rejected (matches effect red)
private val BarterBlue = Color(0xFF6E93C9)    // Cancel

/** The per-side visible barter list: filter by the selected category tab, then sort
 *  selected-first, worn-first (player), alphabetical. Shared by the columns' display and the
 *  controller focus index so the D-pad index maps 1:1 to a visible cell/row. */
private fun barterVisible(items: List<BarterItem>, category: String?, isPlayerSide: Boolean): List<BarterItem> =
    items.filter { category == null || it.category == category }
        .sortedWith(
            compareBy({ itemCategoryRank(it.category) }, { it.displayName().lowercase() })
        )

/** Current controller focus in the barter overlay: which [side] (0 = player/left,
 *  1 = vendor/right) and which [index] into that side's visible list. */
private data class BarterFocus(val side: Int, val index: Int)

/**
 * Controller focus + navigation for the barter overlay (Phase 4), shared by the BOTTOM list
 * layout ([rows] = 1) and the SPLIT grid layout ([rows] = 4). Owns focus (side + index into that
 * side's VISIBLE list) and consumes NavEvents:
 *  - D-pad moves focus and WRAPS within the grid (grid: column-major ±1 within a column, ±rows
 *    across columns, both wrapping; list: Up/Down wrap ±1, Left/Right switch side to its first item).
 *  - L2 / R2 switch to the player / vendor side, focus → that side's FIRST item.
 *  - A toggles the focused item (select/deselect) via [onToggle] (stacks > 1 prompt first).
 *  - X = [onOffer] (Make offer); left stick left/right = [onSlider](-1 / +1) (gold offset). In SPLIT
 *    these two are no-ops here — the bottom controls window owns the slider + Offer button (its own
 *    collector), because the gold offset state lives there; passing no-ops avoids a top/bottom desync.
 * Focus starts on the VENDOR side's first item when barter opens; clears when the overlay leaves
 * composition. Only one barter grid layout is composed at a time (its collector owns barter nav);
 * the dialogue collector yields while a barter session is active. B (Cancel) is the existing patch.
 */
@Composable
private fun rememberBarterNavFocus(
    visiblePlayer: List<BarterItem>,
    visibleVendor: List<BarterItem>,
    rows: Int,
    onToggle: (BarterItem) -> Unit,
    onOffer: () -> Unit,
    onSlider: (Int) -> Unit,
    // Cycle the given side's category filter (L1 = -1 prev, R1 = +1 next).
    onCycleCategory: (side: Int, dir: Int) -> Unit,
): BarterFocus {
    var side by remember { mutableStateOf(1) } // start on the vendor side
    var index by remember { mutableStateOf(0) }

    LaunchedEffect(visiblePlayer.size, visibleVendor.size, side) {
        val n = if (side == 0) visiblePlayer.size else visibleVendor.size
        index = index.coerceIn(0, (n - 1).coerceAtLeast(0))
    }

    val snapshot = rememberUpdatedState(Triple(visiblePlayer, visibleVendor, rows))
    val toggleState = rememberUpdatedState(onToggle)
    val offerState = rememberUpdatedState(onOffer)
    val sliderState = rememberUpdatedState(onSlider)
    val cycleState = rememberUpdatedState(onCycleCategory)
    LaunchedEffect(Unit) {
        var lastSeq = GameStateRepository.navEvent.value?.seq ?: -1L
        var sliderTick = 0 // apply the gold step every OTHER slider tick → ~half rate (~8g/sec held)
        GameStateRepository.navEvent.collect { ev ->
            if (ev == null || ev.seq <= lastSeq) return@collect
            lastSeq = ev.seq
            if (ModalNav.open) return@collect // the quantity selector owns nav while up
            val (pVis, vVis, r) = snapshot.value
            fun list(s: Int) = if (s == 0) pVis else vVis
            val size = list(side).size
            // Column-major grid move with per-axis wrap (rows = r); partial last column clamps rows.
            fun gridMove(i: Int, n: Int): Int {
                if (n <= 0) return 0
                val cols = (n + r - 1) / r
                val col = i / r; val row = i % r
                fun colLast(c: Int) = minOf((c + 1) * r, n) - 1
                return when (ev) {
                    is NavEvent.Down -> if (row + 1 >= r || col * r + row + 1 >= n) col * r else i + 1
                    is NavEvent.Up -> if (row == 0) colLast(col) else i - 1
                    is NavEvent.Right -> (if (col + 1 >= cols) 0 else col + 1).let { minOf(it * r + row, colLast(it)) }
                    is NavEvent.Left -> (if (col - 1 < 0) cols - 1 else col - 1).let { minOf(it * r + row, colLast(it)) }
                    else -> i
                }
            }
            when (ev) {
                is NavEvent.Down -> if (size > 0) index = if (r <= 1) (index + 1) % size else gridMove(index, size)
                is NavEvent.Up -> if (size > 0) index = if (r <= 1) (index - 1 + size) % size else gridMove(index, size)
                is NavEvent.Right ->
                    if (r <= 1) { side = 1; index = 0 } else if (size > 0) index = gridMove(index, size)
                is NavEvent.Left ->
                    if (r <= 1) { side = 0; index = 0 } else if (size > 0) index = gridMove(index, size)
                is NavEvent.L2 -> { side = 0; index = 0 }
                is NavEvent.R2 -> { side = 1; index = 0 }
                // Shoulder buttons cycle the focused side's category filter; reset focus to the
                // first item of the new (filtered) list.
                is NavEvent.L1 -> { cycleState.value(side, -1); index = 0 }
                is NavEvent.R1 -> { cycleState.value(side, 1); index = 0 }
                is NavEvent.Confirm -> list(side).getOrNull(index)?.let { toggleState.value(it) }
                is NavEvent.Action1 -> offerState.value()
                is NavEvent.Info -> list(side).getOrNull(index)?.let {
                    ItemInfoPopupState.toggle(it.id, it.displayName(), it.enchant, onTop = r > 1)
                }
                is NavEvent.SliderLeft -> if (sliderTick++ % 2 == 0) sliderState.value(-1)
                is NavEvent.SliderRight -> if (sliderTick++ % 2 == 0) sliderState.value(1)
                else -> Unit // Scroll* handled elsewhere
            }
            // While the info popup is open, D-pad focus moves follow to the newly focused item.
            if (ItemInfoPopupState.isOpen) {
                list(side).getOrNull(index)?.let {
                    ItemInfoPopupState.follow(it.id, it.displayName(), it.enchant, onTop = r > 1)
                }
            }
        }
    }
    return BarterFocus(side, index)
}

/**
 * Bottom-screen barter overlay. Driven by COMPANION_BARTER_* (native TradeWindow),
 * shown whenever [GameStateRepository.barterSession] is non-null. NOT a Compose Dialog
 * (crashes on the Presentation display — see ItemInfoOverlay); an in-window Box at
 * zIndex 16f (above the dialogue overlay it sits on top of, below QuantitySelector 20f).
 *
 * Item selection is OPTIMISTIC and local: GM_Barter pauses the sim, so the engine's
 * COMPANION_BARTER_OFFER re-exports are frame-starved and can't refresh the display mid-
 * session (same as LootingOverlay). Each tap mutates the local lists (driving its own
 * recomposition) AND sends the real CMP:barter_* command; the engine reconciles the
 * authoritative balance. Lists are initialized once on open and NOT re-synced from later
 * `session` emissions; on close→reopen the overlay re-enters composition and re-inits.
 */
@Composable
private fun BarterOverlay(session: BarterSession, disposition: Int, location: ScreenLocation) {
    // Dismiss the item info popup when the barter session ends (Offer/Cancel/B/close) so it doesn't
    // linger over the game. Composed in BOTH bottom and SPLIT modes, so this covers the top-grid
    // popup too (both this and BarterTopOverlay leave composition on session-null).
    DisposableEffect(Unit) { onDispose { ItemInfoPopupState.close() } }
    val playerName = rememberPlayerName()
    // SPLIT: the item grids live on the TOP screen (BarterTopOverlay, hosted by EngineActivity);
    // the bottom screen shows ONLY the gold bar + Offer/Cancel. Item selection (borrow/return) is
    // sent to the engine from the top grid; the bottom controls read the engine's authoritative
    // merchantOffer + a local gold offset, so they need no shared item-selection state. The
    // disposition bar is omitted here (no natural place in the controls-only panel).
    if (location == ScreenLocation.SPLIT) {
        BarterControlsOnly(session)
        return
    }

    var vendorItems by remember { mutableStateOf(session.vendorItems) }
    var playerItems by remember { mutableStateOf(session.playerItems) }
    var extraGold by remember { mutableStateOf(session.extraGoldOffer) }
    var vendorCat by remember { mutableStateOf<String?>(null) }
    var playerCat by remember { mutableStateOf<String?>(null) }

    // Net estimate from the optimistic selection (positive = player receives gold). `value`
    // is the merchant's actual barter price per unit, so this tracks the engine balance.
    val playerSelValue = playerItems.filter { it.isSelected }.sumOf { it.value * it.selectedCount }
    val vendorSelValue = vendorItems.filter { it.isSelected }.sumOf { it.value * it.selectedCount }
    val net = playerSelValue - vendorSelValue + extraGold
    val anySelected = playerItems.any { it.isSelected } || vendorItems.any { it.isSelected }

    // The actual gold changing hands = the engine's fair price for the selected items
    // (merchantOffer, updated by COMPANION_BARTER_OFFER) + the player's manual offset.
    // Positive = the player receives gold, negative = the player pays.
    val offerBalance = session.merchantOffer + extraGold

    val playerCantAfford = net < 0 && -net > session.playerGold
    val vendorCantAfford = net > 0 && net > session.vendorGold
    // Enabled when there's anything to offer (items OR a gold adjustment) and it's
    // affordable. A gold-only offer (no items) is still submittable so the engine's
    // no_items rejection surfaces as feedback; a completely empty offer stays disabled.
    val offerEnabled = (anySelected || extraGold != 0) && !playerCantAfford && !vendorCantAfford
    val offerLabel = when {
        playerCantAfford -> "Insufficient gold"
        vendorCantAfford -> "Vendor lacks gold"
        else -> "Offer"
    }

    fun mutate(side: BarterSide, id: String, transform: (BarterItem) -> BarterItem) {
        if (side == BarterSide.VENDOR) vendorItems = vendorItems.map { if (it.id == id) transform(it) else it }
        else playerItems = playerItems.map { if (it.id == id) transform(it) else it }
    }

    fun toggle(item: BarterItem) {
        if (item.isSelected) {
            mutate(item.side, item.id) { it.copy(isSelected = false, selectedCount = 0) }
            CompanionActions.barterReturn(item.side, item.id, item.selectedCount)
        } else {
            // Stack > 1 → ask "how many?" via the shared QuantitySelector (20f, above this).
            QuantityRequestState.requestOrRun(item.displayName(), item.count, "Select") { n ->
                mutate(item.side, item.id) { it.copy(isSelected = true, selectedCount = n) }
                CompanionActions.barterBorrow(item.side, item.id, n)
            }
        }
    }
    // Set the gold changing hands to an absolute signed balance (positive = player receives),
    // clamped to what each side can pay; derive the manual offset from it. Matches the SPLIT
    // controls (BarterGoldSlider).
    fun setBalance(target: Int) {
        val clamped = target.coerceIn(-session.playerGold, session.vendorGold)
        extraGold = clamped - session.merchantOffer
        CompanionActions.barterSetExtraGold(extraGold)
    }

    // Controller focus (BOTTOM = two side-by-side lists → rows = 1). All state is co-located here,
    // so this collector also owns X (Offer) + the left-stick gold slider.
    val visiblePlayer = remember(playerItems, playerCat) { barterVisible(playerItems, playerCat, true) }
    val visibleVendor = remember(vendorItems, vendorCat) { barterVisible(vendorItems, vendorCat, false) }
    // Flat 1g per left-stick tick. At the ~60ms native slider-poll cadence (~16 ticks/sec) that's
    // ~16g/sec when held — precise for fine adjustment, still reaches large values by holding. Tune
    // here (or add an accelerating hold) if needed.
    val goldStep = 1
    // Clamp the left-stick gold adjustment to the SAME balance range the touch slider
    // (BarterGoldSlider) can reach — selling (merchantOffer >= 0): 0 .. vendorGold; buying
    // (merchantOffer < 0): -min(item value, playerGold) .. 0 — so the stick can't push the
    // offer past what the slider allows.
    val navReceiving = session.merchantOffer >= 0
    val navSliderMax = (if (navReceiving) session.vendorGold
        else minOf(abs(session.merchantOffer), session.playerGold)).coerceAtLeast(0)
    val navSliderLo = if (navReceiving) 0 else -navSliderMax
    val navSliderHi = if (navReceiving) navSliderMax else 0
    val focus = rememberBarterNavFocus(
        visiblePlayer = visiblePlayer,
        visibleVendor = visibleVendor,
        rows = 1,
        onToggle = ::toggle,
        onOffer = { if (offerEnabled) CompanionActions.barterOffer() },
        // Physical stick right always moves the slider visually rightward (toward max), matching the
        // touch slider. When selling (balance positive) rightward = +gold; when buying (balance
        // negative, magnitude = cost) rightward = MORE negative → invert dir. Clamped to the touch-
        // slider range; no-op at a bound. onSlider is recreated each recomposition
        // (rememberUpdatedState'd in the collector), so offerBalance / setBalance / navReceiving here
        // are always live-frame (unlike the SPLIT LaunchedEffect capture).
        onSlider = { dir ->
            val delta = (if (navReceiving) dir else -dir) * goldStep
            val next = (offerBalance + delta).coerceIn(navSliderLo, navSliderHi)
            if (next != offerBalance) setBalance(next)
        },
        onCycleCategory = { side, dir ->
            if (side == 0) playerCat = cycleBarterCat(playerItems, playerCat, dir)
            else vendorCat = cycleBarterCat(vendorItems, vendorCat, dir)
        },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(16f)
            .background(Color(0xCC0F0C08))
            .pointerInput(Unit) { detectTapGestures {} }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // bottom = 12.dp (not BOTTOM_BAR_SPACE): barter is a modal interaction left
                // via Cancel / B, so the panel fills over the bottom tab bar rather than
                // reserving space for it. 12dp matches the top/side insets.
                .padding(top = 12.dp, bottom = 12.dp, start = 12.dp, end = 12.dp)
                .mwPanel()
                .pointerInput(Unit) { detectTapGestures {} }
        ) {
            // ---- Title bar: "Vendor — Barter" + disposition bar ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${session.vendorName.ifBlank { "Merchant" }} — Barter",
                    color = BronzeLight, fontSize = 14.sp,
                    fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (disposition >= 0) {
                    Spacer(Modifier.width(12.dp))
                    Box(Modifier.width(120.dp)) { DispositionBar(disposition) }
                }
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))

            // ---- Two equal columns: player | vendor ----
            Row(Modifier.weight(1f).fillMaxWidth()) {
                BarterColumn(
                    header = "$playerName (${session.playerGold}g)",
                    items = playerItems,
                    isPlayerSide = true,
                    selectedCategory = playerCat,
                    onSelectCategory = { playerCat = it },
                    onToggle = ::toggle,
                    focusedIndex = if (focus.side == 0) focus.index else -1,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp)
                )
                // Dashed vertical divider.
                Canvas(Modifier.fillMaxHeight().width(1.dp)) {
                    drawLine(
                        color = BronzeDark,
                        start = Offset(0f, 0f),
                        end = Offset(0f, size.height),
                        strokeWidth = size.width.coerceAtLeast(1f),
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
                    )
                }
                BarterColumn(
                    header = "${session.vendorName.ifBlank { "Merchant" }} (${session.vendorGold}g)",
                    items = vendorItems,
                    isPlayerSide = false,
                    selectedCategory = vendorCat,
                    onSelectCategory = { vendorCat = it },
                    onToggle = ::toggle,
                    focusedIndex = if (focus.side == 1) focus.index else -1,
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp)
                )
            }

            // ---- Offer section: the gold-amount slider (same as SPLIT controls) ----
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
            Box(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)) {
                BarterGoldSlider(
                    merchantOffer = session.merchantOffer,
                    playerGold = session.playerGold,
                    vendorGold = session.vendorGold,
                    offerBalance = offerBalance,
                    onSetBalance = ::setBalance
                )
            }

            // ---- Buttons ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BarterButton(
                    label = offerLabel, color = BarterGreen, enabled = offerEnabled,
                    modifier = Modifier.weight(1f)
                ) { CompanionActions.barterOffer() }
                BarterButton(
                    label = "Cancel", hint = "B", color = BarterBlue, enabled = true,
                    modifier = Modifier.weight(1f)
                ) { CompanionActions.barterCancel() }
            }
        }

        // Local dropdown-dismiss scrim for BarterRow long-press (Info) menus — same as
        // LootingOverlay (this overlay renders above the global scrim at 10f).
        if (DropdownState.anyOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { DropdownState.closeAll() } }
            )
        }
    }
}

/* ---- Barter SPLIT mode (top-screen grids + bottom-screen controls) ---- */

// Gap between the two separate column boxes on the top screen. Single tweakable constant.
private val BARTER_SPLIT_COLUMN_GAP = 8.dp

/**
 * SPLIT-mode bottom-screen barter controls: gold totals + the [−100..+100] gold offset bar +
 * Offer / Cancel, centred vertically. Item selection happens on the top grid ([BarterTopOverlay])
 * and is tracked engine-side (barter_borrow/return), so these controls read only the engine's
 * authoritative [BarterSession.merchantOffer] plus a LOCAL gold offset — no shared item state.
 * The disposition bar is intentionally omitted (decision: no natural place here).
 */
@Composable
private fun BarterControlsOnly(session: BarterSession) {
    // Local gold offset (the +/- row), initialised once from the session; NOT re-synced from
    // later emissions (matches the full overlay). Persisted engine-side via barter_gold.
    var extraGold by remember { mutableStateOf(session.extraGoldOffer) }

    // Balance actually changing hands = engine fair price for staged items + the manual offset.
    // Positive = the player receives gold, negative = the player pays. Affordability is derived
    // from this authoritative balance (no optimistic item net needed on the bottom screen).
    val offerBalance = session.merchantOffer + extraGold
    val playerCantAfford = offerBalance < 0 && -offerBalance > session.playerGold
    val vendorCantAfford = offerBalance > 0 && offerBalance > session.vendorGold
    val offerEnabled = !playerCantAfford && !vendorCantAfford
    val offerLabel = when {
        playerCantAfford -> "Insufficient gold"
        vendorCantAfford -> "Vendor lacks gold"
        else -> "Offer"
    }

    // Set the gold changing hands to an absolute signed balance (positive = player receives),
    // clamped to what each side can pay; derive the manual offset from it.
    fun setBalance(target: Int) {
        val clamped = target.coerceIn(-session.playerGold, session.vendorGold)
        extraGold = clamped - session.merchantOffer
        CompanionActions.barterSetExtraGold(extraGold)
    }

    // Controller: in SPLIT mode the grid nav + A + L2/R2 live on the TOP grids (BarterTopOverlay);
    // this bottom window owns the gold slider (left stick) + X (Offer) because the gold-offset state
    // lives here. A small dedicated collector — no focus/grid on the bottom, disjoint event set from
    // the top collector (Action1/SliderLeft/SliderRight only), so the two never double-handle.
    // Flat 1g per left-stick tick. At the ~60ms native slider-poll cadence (~16 ticks/sec) that's
    // ~16g/sec when held — precise for fine adjustment, still reaches large values by holding. Tune
    // here (or add an accelerating hold) if needed.
    val goldStep = 1
    val ctlSnapshot = rememberUpdatedState(Triple(offerBalance, offerEnabled, goldStep))
    // Clamp the left-stick gold adjustment to the SAME balance range the touch slider
    // (BarterGoldSlider) can reach — selling (merchantOffer >= 0): 0 .. vendorGold; buying
    // (merchantOffer < 0): -min(item value, playerGold) .. 0 — so the stick can't push the
    // offer past what the slider allows.
    val navReceiving = session.merchantOffer >= 0
    val navSliderMax = (if (navReceiving) session.vendorGold
        else minOf(abs(session.merchantOffer), session.playerGold)).coerceAtLeast(0)
    val navBounds = rememberUpdatedState(
        (if (navReceiving) 0 else -navSliderMax) to (if (navReceiving) navSliderMax else 0)
    )
    // Fresh (live-frame) receiving flag for the captured collector — buying inverts the stick dir.
    val navReceivingState = rememberUpdatedState(navReceiving)
    // setBalance() reads session.merchantOffer to derive the manual offset. LaunchedEffect(Unit)
    // captures its closure once, so calling setBalance directly would use the STALE first-frame
    // merchantOffer (0, before items are staged) and re-fold the item price into extraGold — the
    // "cost doubles regardless of direction" bug. rememberUpdatedState keeps a reference to the
    // current-frame setBalance so the offset is derived from the live merchantOffer.
    val setBalanceState = rememberUpdatedState<(Int) -> Unit> { target -> setBalance(target) }
    LaunchedEffect(Unit) {
        var lastSeq = GameStateRepository.navEvent.value?.seq ?: -1L
        var sliderTick = 0 // apply the gold step every OTHER slider tick → ~half rate (~8g/sec held)
        GameStateRepository.navEvent.collect { ev ->
            if (ev == null || ev.seq <= lastSeq) return@collect
            lastSeq = ev.seq
            if (ModalNav.open) return@collect // the quantity selector owns nav while up
            val (balance, enabled, step) = ctlSnapshot.value
            val (sliderLo, sliderHi) = navBounds.value
            val receiving = navReceivingState.value
            when (ev) {
                is NavEvent.Action1 -> if (enabled) CompanionActions.barterOffer()
                // Physical stick right always moves the slider visually rightward (toward max cost/
                // gold), left toward 0, matching the touch slider. Selling: right=+gold, left=-gold.
                // Buying (balance negative, magnitude=cost): invert — right=more negative (pay more),
                // left=less negative (pay less). Clamped; no-op at a bound.
                is NavEvent.SliderLeft -> if (sliderTick++ % 2 == 0) {
                    val next = (balance + (if (receiving) -step else step)).coerceIn(sliderLo, sliderHi)
                    if (next != balance) setBalanceState.value(next)
                }
                is NavEvent.SliderRight -> if (sliderTick++ % 2 == 0) {
                    val next = (balance + (if (receiving) step else -step)).coerceIn(sliderLo, sliderHi)
                    if (next != balance) setBalanceState.value(next)
                }
                else -> Unit
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(16f)
            .background(Color(0xCC0F0C08))
            .pointerInput(Unit) { detectTapGestures {} },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .mwPanel()
                .pointerInput(Unit) { detectTapGestures {} }
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "${session.vendorName.ifBlank { "Merchant" }} — Barter",
                color = BronzeLight, fontSize = 14.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            // Gold totals: your gold | their gold.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("You: ${session.playerGold}g", color = Bone, fontSize = 12.sp, fontFamily = MwData)
                Text("${session.vendorName.ifBlank { "Merchant" }}: ${session.vendorGold}g",
                    color = Bone, fontSize = 12.sp, fontFamily = MwData,
                    maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
            BarterGoldSlider(
                merchantOffer = session.merchantOffer,
                playerGold = session.playerGold,
                vendorGold = session.vendorGold,
                offerBalance = offerBalance,
                onSetBalance = ::setBalance
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BarterButton(
                    label = offerLabel, color = BarterGreen, enabled = offerEnabled,
                    modifier = Modifier.weight(1f)
                ) { CompanionActions.barterOffer() }
                BarterButton(
                    label = "Cancel", hint = "B", color = BarterBlue, enabled = true,
                    modifier = Modifier.weight(1f)
                ) { CompanionActions.barterCancel() }
            }
        }
    }
}

/**
 * Trade-amount slider for the SPLIT barter controls. The slider sets the gold changing hands; its
 * range depends on the current direction of the deal (the sign of the engine's fair price for the
 * staged items, [merchantOffer]):
 *  - player RECEIVES (merchantOffer >= 0): 0 .. vendorGold (how much the vendor pays).
 *  - player PAYS (merchantOffer < 0): 0 .. min(item value, playerGold) — can't pay more than the
 *    goods are worth (nor more than you hold).
 * The signed gold amount is shown as a coloured label above the slider (green = you receive, red =
 * you pay).
 *
 * NOTE: the "0" end is neutral/no-gold, not the mathematically "fair" balance — see the brief; the
 * exact semantics are easy to retune here.
 */
@Composable
private fun BarterGoldSlider(
    merchantOffer: Int,
    playerGold: Int,
    vendorGold: Int,
    offerBalance: Int,
    onSetBalance: (Int) -> Unit
) {
    val receiving = merchantOffer >= 0
    val sliderMax = (if (receiving) vendorGold else minOf(abs(merchantOffer), playerGold))
        .coerceAtLeast(0)
    // Slider magnitude in the active direction (gold received when selling, paid when buying).
    val magnitude = (if (receiving) offerBalance else -offerBalance).coerceIn(0, sliderMax)

    val labelColor = when {
        offerBalance > 0 -> BarterGreen
        offerBalance < 0 -> BarterRed
        else -> Bone
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            if (offerBalance > 0) "+${offerBalance}g" else "${offerBalance}g",
            color = labelColor, fontSize = 20.sp, fontFamily = MwData, fontWeight = FontWeight.Bold
        )
        Slider(
            value = magnitude.toFloat(),
            onValueChange = { v ->
                val mag = v.roundToInt().coerceIn(0, sliderMax)
                onSetBalance(if (receiving) mag else -mag)
            },
            valueRange = 0f..sliderMax.coerceAtLeast(1).toFloat(),
            enabled = sliderMax > 0,
            colors = SliderDefaults.colors(
                thumbColor = BronzeLight,
                activeTrackColor = Bronze,
                inactiveTrackColor = BronzeDark
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * TOP-screen barter grids (Display 0). Hosted by [org.openmw.EngineActivity] in an interactive
 * WindowManager panel window while a barter session is active AND Bartering is routed to SPLIT.
 * Player inventory LEFT, vendor inventory RIGHT — both 3-row horizontally-scrolling icon grids
 * with per-side category tabs. Tapping a cell selects/deselects it (adds to / removes from the
 * offer, sending barter_borrow / barter_return); a stack > 1 prompts for a quantity first.
 * Selected cells get a bronze border highlight. Reads its state from [GameStateRepository].
 */
@Composable
fun BarterTopOverlay() {
    val sessionState by GameStateRepository.barterSession.collectAsState()
    val session = sessionState ?: return
    val playerName = rememberPlayerName()

    // Optimistic COLUMN contents — remember WITHOUT a key so they init once on enter and are NOT
    // re-synced from later (frame-starved) emissions. The window is removed on session-null.
    // Items MOVE between columns on tap (like looting): a bought vendor item leaves the vendor
    // column and appears (highlighted) in the player column, and a sold player item vice versa.
    // An item's `side` is its HOME; `isSelected` = staged (currently shown in the OPPOSITE column).
    var playerCol by remember { mutableStateOf(session.playerItems) }
    var vendorCol by remember { mutableStateOf(session.vendorItems) }
    var playerCat by remember { mutableStateOf<String?>(null) }
    var vendorCat by remember { mutableStateOf<String?>(null) }

    // Local quantity picker (kept on the TOP screen).
    var qtyReq by remember { mutableStateOf<QuantityRequest?>(null) }
    val requestQty: (String, Int, String, (Int) -> Unit) -> Unit = { name, count, label, action ->
        if (count > 1) qtyReq = QuantityRequest(name, count, label) { n -> qtyReq = null; action(n) }
        else action(1)
    }

    // Brief auto-dismissing inline banner (e.g. the merchant won't buy a restricted item). The
    // nonce re-arms the auto-dismiss timer even when the same message is tapped again.
    var restrictionBanner by remember { mutableStateOf<String?>(null) }
    var bannerNonce by remember { mutableStateOf(0) }
    LaunchedEffect(bannerNonce) {
        if (restrictionBanner != null) {
            delay(2200)
            restrictionBanner = null
        }
    }

    // Move `n` of `item` from `source` column to `dest` column, setting the destination copy's
    // staged flag. Reduces/removes the matched entry in source (matched by id+side+isSelected) and
    // merges into (or appends) a same-(id, side, selected) entry in dest — so moving 1 of a stack
    // of 5 leaves a ×4 remainder behind. Mirrors looting's moveOptimistic for BarterItem. Returns
    // (newSource, newDest).
    fun moveBarter(
        source: List<BarterItem>, dest: List<BarterItem>, item: BarterItem, n: Int, selected: Boolean
    ): Pair<List<BarterItem>, List<BarterItem>> {
        val moved = n.coerceIn(1, item.count)
        val newSource = source.mapNotNull { e ->
            if (e.id == item.id && e.side == item.side && e.isSelected == item.isSelected) {
                if (e.count > moved) e.copy(count = e.count - moved) else null
            } else e
        }
        val idx = dest.indexOfFirst { it.id == item.id && it.side == item.side && it.isSelected == selected }
        val newDest = if (idx >= 0) {
            dest.mapIndexed { i, e ->
                if (i == idx) e.copy(count = e.count + moved, selectedCount = if (selected) e.count + moved else 0) else e
            }
        } else {
            dest + item.copy(count = moved, isSelected = selected, selectedCount = if (selected) moved else 0)
        }
        return newSource to newDest
    }

    // Buy/sell: move `n` from the item's HOME column to the opposite column, mark it staged, and
    // tell the engine (barter_borrow). PLAYER items go player→vendor (sell); VENDOR items go
    // vendor→player (buy).
    fun borrow(item: BarterItem, n: Int) {
        if (item.side == BarterSide.PLAYER) {
            val (s, d) = moveBarter(playerCol, vendorCol, item, n, selected = true)
            playerCol = s; vendorCol = d
        } else {
            val (s, d) = moveBarter(vendorCol, playerCol, item, n, selected = true)
            vendorCol = s; playerCol = d
        }
        CompanionActions.barterBorrow(item.side, item.id, n.coerceIn(1, item.count))
    }

    // Un-stage: move a staged item back to its HOME column (whole amount) and return it to the
    // engine (barter_return). A staged PLAYER item currently sits in the vendor column; a staged
    // VENDOR item in the player column.
    fun returnHome(item: BarterItem) {
        val amount = item.count
        if (item.side == BarterSide.PLAYER) {
            val (s, d) = moveBarter(vendorCol, playerCol, item, amount, selected = false)
            vendorCol = s; playerCol = d
        } else {
            val (s, d) = moveBarter(playerCol, vendorCol, item, amount, selected = false)
            playerCol = s; vendorCol = d
        }
        CompanionActions.barterReturn(item.side, item.id, amount)
    }

    fun toggle(item: BarterItem) {
        if (item.isSelected) { returnHome(item); return }
        // Pre-tap restriction gate: the merchant won't buy this player item — flash a banner and
        // skip (no move to undo). Vendor items are always sellable=true.
        if (item.side == BarterSide.PLAYER && !item.sellable) {
            restrictionBanner = "This vendor doesn't buy that"
            bannerNonce++
            return
        }
        requestQty(item.displayName(), item.count, "Select") { n -> borrow(item, n) }
    }

    // Controller focus (SPLIT = two 3-row icon grids → rows = 3). X (Offer) + the gold slider are
    // owned by the bottom controls window (BarterControlsOnly, its own collector) because the gold-
    // offset state lives there — passing no-ops here avoids a top/bottom desync.
    val visiblePlayer = remember(playerCol, playerCat) { barterVisible(playerCol, playerCat, true) }
    val visibleVendor = remember(vendorCol, vendorCat) { barterVisible(vendorCol, vendorCat, false) }
    val focus = rememberBarterNavFocus(
        visiblePlayer = visiblePlayer,
        visibleVendor = visibleVendor,
        rows = 3,
        onToggle = ::toggle,
        onOffer = {},
        onSlider = {},
        onCycleCategory = { side, dir ->
            if (side == 0) playerCat = cycleBarterCat(playerCol, playerCat, dir)
            else vendorCat = cycleBarterCat(vendorCol, vendorCat, dir)
        },
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) { detectTapGestures {} }
            // Bottom-anchored, 12dp up — matching the looting overlay.
            .padding(bottom = 12.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        // Two separate boxed columns (player | vendor) with an 8dp gap — no outer panel, no
        // divider. Same height as the looting overlay (0.675). Items MOVE across on tap.
        Row(
            modifier = Modifier
                .fillMaxWidth(0.96f)
                .fillMaxHeight(0.675f)
        ) {
            // LEFT: player column (own inventory + bought vendor items, highlighted).
            BarterGridColumn(
                header = playerName,
                items = playerCol,
                isPlayerSide = true,
                selectedCategory = playerCat,
                onSelectCategory = { playerCat = it },
                onToggle = ::toggle,
                focusedIndex = if (focus.side == 0) focus.index else -1,
                modifier = Modifier.weight(1f).fillMaxHeight().splitColumnBox()
            )
            Spacer(Modifier.width(BARTER_SPLIT_COLUMN_GAP))
            // RIGHT: vendor column (own stock + sold player items, highlighted).
            BarterGridColumn(
                header = session.vendorName.ifBlank { "Merchant" },
                items = vendorCol,
                isPlayerSide = false,
                selectedCategory = vendorCat,
                onSelectCategory = { vendorCat = it },
                onToggle = ::toggle,
                focusedIndex = if (focus.side == 1) focus.index else -1,
                modifier = Modifier.weight(1f).fillMaxHeight().splitColumnBox()
            )
        }

        if (DropdownState.anyOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) { detectTapGestures { DropdownState.closeAll() } }
            )
        }
        qtyReq?.let { req ->
            QuantitySelector(
                name = req.name,
                max = req.max,
                confirmLabel = req.confirmLabel,
                onConfirm = req.onConfirm,
                onCancel = { qtyReq = null }
            )
        }

        // Brief auto-dismissing restriction banner, top-centre (non-blocking, not a popup).
        restrictionBanner?.let { msg ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = 24.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                Text(
                    msg,
                    color = BoneBright, fontSize = 13.sp, fontFamily = MwBody,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color(0xF01A1206))
                        .border(1.dp, BarterRed, RoundedCornerShape(4.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
        }

        // Item info popup on the TOP screen (SPLIT barter): anchored to the grid cell in THIS
        // window's coords. Only when the trigger came from here (onTopScreen).
        if (ItemInfoPopupState.isOpen && ItemInfoPopupState.onTopScreen) {
            ItemInfoPopupHost()
        }
    }
}

/** One side of the SPLIT barter grids — header, per-side category tabs, 3-row icon grid. */
@Composable
private fun BarterGridColumn(
    header: String,
    items: List<BarterItem>,
    isPlayerSide: Boolean,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    onToggle: (BarterItem) -> Unit,
    focusedIndex: Int = -1,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            header,
            color = BronzeLight, fontSize = 14.sp,
            fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        val presentCats = remember(items) { items.map { it.category }.toSet() }
        val tabs = remember(presentCats) { BARTER_CATEGORIES.filter { it.cat in presentCats } }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CategoryTab("All", active = selectedCategory == null) { onSelectCategory(null) }
            tabs.forEach { c ->
                CategoryTab(c.label, active = selectedCategory == c.cat) { onSelectCategory(c.cat) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.5f)))
        Spacer(Modifier.height(6.dp))

        // Same filter+sort the controller focus index is computed against (barterVisible).
        val visible = remember(items, selectedCategory, isPlayerSide) {
            barterVisible(items, selectedCategory, isPlayerSide)
        }
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Nothing to trade", color = BoneDim, fontSize = 12.sp, fontFamily = MwBody)
            }
        } else {
            val gridState = rememberLazyGridState()
            LaunchedEffect(focusedIndex) {
                if (focusedIndex in visible.indices) gridState.animateScrollToItem(focusedIndex)
            }
            // Right stick left/right scrolls this horizontal grid while it's the focused side.
            ScrollByNav(gridState, active = focusedIndex >= 0, horizontal = true)
            LazyHorizontalGrid(
                // 3 rows / tight spacing — matching the looting overlay. Column-major fill, so item
                // index i is at grid row i%3, column i/3 — MUST match rememberBarterNavFocus (rows = 3).
                state = gridState,
                rows = GridCells.Fixed(3),
                modifier = Modifier.fillMaxWidth().weight(1f),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                gridItemsIndexed(visible) { i, item ->
                    BarterGridCell(
                        item = item,
                        isPlayerSide = isPlayerSide,
                        iconBitmap = rememberItemIcon(item.icon),
                        focused = i == focusedIndex,
                        onToggle = onToggle
                    )
                }
            }
            HorizontalGridScrollbar(gridState, Modifier.padding(top = 4.dp))
        }
    }
}

/**
 * One icon cell in the SPLIT barter grids. Tap = select/deselect (add to / remove from the
 * offer); long-press = Info + Buy/Sell. A selected cell gets a bright bronze border highlight
 * (mirroring the current barter overlay's selected state). Stacks > 1 prompt for a quantity.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BarterGridCell(
    item: BarterItem,
    isPlayerSide: Boolean,
    iconBitmap: ImageBitmap? = null,
    focused: Boolean = false,
    onToggle: (BarterItem) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(DropdownState.closeRequest) {
        if (DropdownState.closeRequest > 0) menuOpen = false
    }
    val selected = item.isSelected
    val label = item.displayName()

    Box(modifier = Modifier.onGloballyPositioned { ItemInfoPopupState.reportAnchor(item.id, it.boundsInRoot()) }) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(54.dp)
                // Controller focus: a bronze ring + faint fill around the WHOLE cell (icon + label +
                // price). SELECTED is an inner-icon BronzeLight border + SlotWorn icon fill, so this
                // outer-cell ring reads as a distinct "cursor here" marker even on a selected cell.
                .then(
                    if (focused) Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(BronzeLight.copy(alpha = 0.15f))
                        .border(2.dp, BronzeLight, RoundedCornerShape(4.dp))
                    else Modifier
                )
                .combinedClickable(
                    onClick = { onToggle(item) },
                    onLongClick = { menuOpen = true; DropdownState.open() }
                )
                .padding(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (selected) SlotWorn else SlotBg)
                    .border(
                        BorderStroke(if (selected) 2.dp else 1.dp, if (selected) BronzeLight else BronzeDark),
                        RoundedCornerShape(3.dp)
                    )
            ) {
                EnchantBackdrop(item.enchant != null)
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(3.dp)
                    )
                }
                // Selected-count badge (bottom-right) when > 1.
                if (selected && item.selectedCount > 1) {
                    Text(
                        "×${item.selectedCount}",
                        color = BarterGreen, fontSize = 9.sp,
                        fontFamily = MwData, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(Color(0xCC0E0B07))
                            .padding(horizontal = 2.dp)
                    )
                } else if (!selected && item.count > 1) {
                    Text(
                        "×${item.count}",
                        color = BoneBright, fontSize = 9.sp,
                        fontFamily = MwData, fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .background(Color(0xCC0E0B07))
                            .padding(horizontal = 2.dp)
                    )
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                color = if (selected) BoneBright else Bone,
                fontSize = 8.sp, fontFamily = MwBody,
                textAlign = TextAlign.Center,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
                lineHeight = 9.sp,
                modifier = Modifier.width(54.dp)
            )
            // Per-unit barter price.
            Text(
                "${item.value}g",
                color = BoneDim, fontSize = 8.sp, fontFamily = MwData,
                maxLines = 1
            )
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false; DropdownState.closeAll() },
            properties = PopupProperties(focusable = false, dismissOnClickOutside = false),
            containerColor = StonePanel,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Bronze),
            shape = RoundedCornerShape(3.dp)
        ) {
            Text(
                label,
                color = BronzeLight, fontSize = 12.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark))
            val menuItemColors = MenuDefaults.itemColors(textColor = Bone)
            DropdownMenuItem(
                text = {
                    Text(
                        if (selected) "Remove from offer"
                        else if (isPlayerSide) "Sell" else "Buy",
                        fontFamily = MwBody, fontSize = 13.sp
                    )
                },
                onClick = { menuOpen = false; DropdownState.closeAll(); onToggle(item) },
                colors = menuItemColors
            )
            DropdownMenuItem(
                text = { Text("Info", fontFamily = MwBody, fontSize = 13.sp) },
                onClick = { menuOpen = false; DropdownState.closeAll(); ItemInfoPopupState.open(item.id, item.name, item.enchant, onTop = true) },
                colors = menuItemColors
            )
        }
    }
}

/** One side of the barter overlay — header, per-side category tabs, scrolling item list. */
@Composable
private fun BarterColumn(
    header: String,
    items: List<BarterItem>,
    isPlayerSide: Boolean,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    onToggle: (BarterItem) -> Unit,
    focusedIndex: Int = -1,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Text(
            header,
            color = BronzeLight, fontSize = 13.sp,
            fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(4.dp))
        // Category tabs — All + only the buckets present on this side.
        val presentCats = remember(items) { items.map { it.category }.toSet() }
        val tabs = remember(presentCats) { BARTER_CATEGORIES.filter { it.cat in presentCats } }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            CategoryTab("All", active = selectedCategory == null) { onSelectCategory(null) }
            tabs.forEach { c ->
                CategoryTab(c.label, active = selectedCategory == c.cat) { onSelectCategory(c.cat) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.5f)))

        // Same filter+sort the controller focus index is computed against (barterVisible).
        val visible = remember(items, selectedCategory, isPlayerSide) {
            barterVisible(items, selectedCategory, isPlayerSide)
        }
        // weight(1f) so the list explicitly takes ALL remaining height in the column —
        // the item lists expand to fill the overlay (matching the looting overlay).
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Nothing to trade", color = BoneDim, fontSize = 12.sp, fontFamily = MwBody)
            }
        } else {
            val listState = rememberLazyListState()
            LaunchedEffect(focusedIndex) {
                if (focusedIndex in visible.indices) listState.animateScrollToItem(focusedIndex)
            }
            // Right stick scrolls this list while it's the focused side.
            ScrollByNav(listState, active = focusedIndex >= 0)
            LazyColumn(state = listState, modifier = Modifier.fillMaxWidth().weight(1f)) {
                itemsIndexed(visible) { i, item ->
                    BarterRow(
                        item = item,
                        isPlayerSide = isPlayerSide,
                        iconBitmap = rememberItemIcon(item.icon),
                        focused = i == focusedIndex,
                        onToggle = onToggle
                    )
                }
            }
        }
    }
}

/**
 * One barter item row. Tap = select/deselect (add to / remove from the offer); a stack
 * > 1 routes the SELECT through QuantityRequestState first. Long-press = Info. Selected
 * rows get a SlotWorn highlight + a green "✓ selling"/"✓ buying" tag replacing the tap hint.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BarterRow(
    item: BarterItem,
    isPlayerSide: Boolean,
    iconBitmap: ImageBitmap? = null,
    focused: Boolean = false,
    onToggle: (BarterItem) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(DropdownState.closeRequest) {
        if (DropdownState.closeRequest > 0) menuOpen = false
    }
    val selected = item.isSelected
    val label = item.displayName()
    Box(modifier = Modifier.onGloballyPositioned { ItemInfoPopupState.reportAnchor(item.id, it.boundsInRoot()) }) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) SlotWorn else Color.Transparent)
                    // Controller focus: a bronze border + faint fill on the row. SELECTED is a
                    // SlotWorn fill (no border), so the border reads as a distinct "cursor here".
                    .then(
                        if (focused) Modifier
                            .background(BronzeLight.copy(alpha = 0.12f))
                            .border(2.dp, BronzeLight)
                        else Modifier
                    )
                    .combinedClickable(
                        onClick = { onToggle(item) },
                        onLongClick = { menuOpen = true; DropdownState.open() }
                    )
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon box.
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SlotBg)
                        .border(1.dp, BronzeDark, RoundedCornerShape(2.dp))
                ) {
                    EnchantBackdrop(item.enchant != null)
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.width(8.dp))
                // Name + sub-line (qty · value).
                Column(Modifier.weight(1f).padding(end = 6.dp)) {
                    Text(
                        label,
                        color = if (selected) BoneBright else Bone,
                        fontSize = 13.sp, fontFamily = MwBody,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    val sub = buildString {
                        if (item.count > 1) append("×${item.count}  ")
                        append("${item.value}g")
                    }
                    Text(sub, color = BoneDim, fontSize = 9.sp, fontFamily = MwData)
                }
                // Right column: selection state / tap hint + worn tag.
                Column(horizontalAlignment = Alignment.End) {
                    if (selected) {
                        val n = if (item.selectedCount > 1) " ×${item.selectedCount}" else ""
                        Text(
                            (if (isPlayerSide) "✓ selling" else "✓ buying") + n,
                            color = BarterGreen, fontSize = 10.sp,
                            fontFamily = MwDisplay, fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            if (isPlayerSide) "tap to sell" else "tap to buy",
                            color = BoneDim.copy(alpha = 0.7f), fontSize = 9.sp, fontFamily = MwBody
                        )
                    }
                    if (isPlayerSide && item.worn) {
                        Text(
                            "WORN", color = BronzeLight, fontSize = 8.sp,
                            fontFamily = MwDisplay, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.4f)))
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false; DropdownState.closeAll() },
            properties = PopupProperties(focusable = false, dismissOnClickOutside = false),
            containerColor = StonePanel,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Bronze),
            shape = RoundedCornerShape(3.dp)
        ) {
            Text(
                label,
                color = BronzeLight, fontSize = 12.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark))
            val menuItemColors = MenuDefaults.itemColors(textColor = Bone)
            DropdownMenuItem(
                text = {
                    Text(
                        if (selected) "Remove from offer"
                        else if (isPlayerSide) "Sell" else "Buy",
                        fontFamily = MwBody, fontSize = 13.sp
                    )
                },
                onClick = { menuOpen = false; DropdownState.closeAll(); onToggle(item) },
                colors = menuItemColors
            )
            DropdownMenuItem(
                text = { Text("Info", fontFamily = MwBody, fontSize = 13.sp) },
                onClick = { menuOpen = false; DropdownState.closeAll(); ItemInfoPopupState.open(item.id, item.name, item.enchant) },
                colors = menuItemColors
            )
        }
    }
}

/** Offer / Cancel button — colored border + tinted fill + colored label (theme-consistent
 *  take on the spec's "green Offer / blue Cancel"). Disabled → dim, non-tappable. */
@Composable
private fun BarterButton(
    label: String,
    color: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    hint: String? = null,
    onClick: () -> Unit
) {
    val c = if (enabled) color else BoneDim
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(if (enabled) color.copy(alpha = 0.18f) else SlotBg)
            .border(1.dp, c, RoundedCornerShape(3.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = c, fontSize = 14.sp, fontFamily = MwDisplay, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            if (hint != null) {
                Spacer(Modifier.width(6.dp))
                Text("[$hint]", color = c.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = MwData)
            }
        }
    }
}

/** Offer-rejected alert — in-window Box overlay (NOT a Dialog). Tap scrim or OK to dismiss;
 *  the barter session stays open so the player can adjust and try again. */
@Composable
private fun BarterRejectedAlert(reason: String, onDismiss: () -> Unit) {
    val msg = when (reason) {
        "player_gold" -> "You don't have enough gold for that."
        "vendor_gold" -> "The merchant can't afford that."
        "no_items" -> "Select something to trade first."
        "stolen" -> "That item was stolen — it has been confiscated."
        else -> "The merchant refused your offer."
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(18f)
            .background(Color(0x99000000))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(320.dp)
                .mwPanel()
                .pointerInput(Unit) { detectTapGestures {} }
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Offer rejected", color = BarterRed, fontSize = 16.sp, fontFamily = MwDisplay, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(msg, color = Bone, fontSize = 13.sp, fontFamily = MwBody, textAlign = TextAlign.Center)
            Spacer(Modifier.height(16.dp))
            QtyActionButton("OK", Modifier.fillMaxWidth(), primary = true) { onDismiss() }
        }
    }
}

/* ---- Splash panel for when not in game ---- */
@Composable
private fun SplashPanel(onDismiss: () -> Unit = {}) {
    Image(
        painter = painterResource(id = R.drawable.openmw_ds_splash),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxSize()
            .clickable(onClick = onDismiss)
    )
}

/* ---- Top stat bar (floats over all tabs) ---- */

@Composable
private fun TopStatBar(state: GameState, modifier: Modifier = Modifier) {
    var effectsExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(DropdownState.closeRequest) {
        if (DropdownState.closeRequest > 0) effectsExpanded = false
    }
    val hasEffects = state.activeEffects.isNotEmpty()

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(FloatStone)
            .border(2.dp, Bronze, RoundedCornerShape(3.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CompactStat("Health", state.health, HealthCol, Modifier.weight(1f))
        CompactStat("Magicka", state.magicka, MagickaCol, Modifier.weight(1f))
        CompactStat("Fatigue", state.fatigue, FatigueCol, Modifier.weight(1f))

        if (hasEffects) {
            Box(
                Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(BronzeDark)
            )
            Box {
                Column(
                    modifier = Modifier.clickable {
                        effectsExpanded = !effectsExpanded
                        if (effectsExpanded) DropdownState.open() else DropdownState.closeAll()
                    },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        "Effects",
                        color = BronzeLight, fontSize = 12.sp,
                        fontFamily = MwDisplay, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        state.activeEffects.forEach { effect ->
                            EffectDot(effect.harmful, 10.dp)
                        }
                    }
                }
                DropdownMenu(
                    expanded = effectsExpanded,
                    onDismissRequest = { effectsExpanded = false; DropdownState.closeAll() },
                    properties = PopupProperties(focusable = false, dismissOnClickOutside = false),
                    containerColor = StonePanel,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, Bronze),
                    shape = RoundedCornerShape(3.dp)
                ) {
                    state.activeEffects.forEach { effect ->
                        val effectIcon = rememberItemIcon(effect.icon)
                        Row(
                            modifier = Modifier
                                .widthIn(min = 170.dp)
                                .padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Compact 20dp icon box (same placeholder styling as
                            // inventory/spell rows, sized down for the dropdown).
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(SlotBg)
                                    .border(1.dp, BronzeDark, RoundedCornerShape(2.dp))
                            ) {
                                if (effectIcon != null) {
                                    Image(
                                        bitmap = effectIcon,
                                        contentDescription = null,
                                        contentScale = ContentScale.Fit,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                            Spacer(Modifier.width(6.dp))
                            EffectDot(effect.harmful, 8.dp)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    effect.name,
                                    color = Bone, fontSize = 12.sp,
                                    fontFamily = MwBody,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                                val subtitle = effectSubtitle(effect)
                                if (subtitle.isNotEmpty()) {
                                    Text(
                                        subtitle,
                                        color = BoneDim, fontSize = 10.sp,
                                        fontFamily = MwData,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EffectDot(harmful: Boolean, sizeDp: Dp) {
    val dotColor = if (harmful) Color(0xFFC75C5C) else Color(0xFF7FBF7F)
    Canvas(Modifier.size(sizeDp)) {
        drawCircle(dotColor.copy(alpha = 0.3f))
        drawCircle(dotColor, radius = size.minDimension * 0.38f)
    }
}

@Composable
private fun CompactStat(
    label: String,
    value: Dynamic,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = BronzeLight, fontSize = 12.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.Bold)
            Text(
                "${value.current.toInt()}/${value.max.toInt()}",
                color = Bone, fontSize = 12.sp, fontFamily = MwData
            )
        }
        Spacer(Modifier.height(5.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(9.dp)
                .clip(RoundedCornerShape(1.dp))
                .background(Color(0xFF0E0B07))
                .border(1.dp, BronzeDark, RoundedCornerShape(1.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value.ratio)
                    .height(9.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(color)
            )
        }
    }
}

/* ---- Map: local-map texture from the engine + player direction arrow ---- */

@Composable
private fun MapPanel(state: GameState, splashVisible: Boolean = false) {
    val exteriorMaps by GameStateRepository.exteriorMapBitmaps.collectAsState()
    val interiorMaps by GameStateRepository.interiorMapBitmaps.collectAsState()

    val hasMap = if (state.cellIsExterior)
        exteriorMaps.containsKey(Pair(state.cellGridX, state.cellGridY))
    else
        interiorMaps.isNotEmpty()

    // Teleport-door markers + the interior rotation params for placing them (exterior = identity).
    val doorMarkers by GameStateRepository.doorMarkers.collectAsState()
    val markerSeg = if (!state.cellIsExterior) interiorMaps.values.firstOrNull() else null
    val markerAngle = markerSeg?.angle ?: 0f
    val markerCX = markerSeg?.centerX ?: 0f
    val markerCY = markerSeg?.centerY ?: 0f
    // Holds ONLY the tapped DoorMarker (not a captured screen offset) — its name bubble's screen
    // position is recomputed live from state.pos each recomposition (see the overlay below), so the
    // bubble tracks the marker as the player moves instead of freezing where it was tapped.
    var selectedMarker by remember { mutableStateOf<DoorMarker?>(null) }
    // Map panel size in px, captured from the Box (== the fillMaxSize Canvas) so the name-bubble
    // overlay can run the same markerScreenPos transform the Canvas draw uses.
    var mapBoxSize by remember { mutableStateOf(IntSize.Zero) }
    // Auto-dismiss the door-marker name bubble after DOOR_MARKER_POPUP_MS. Keyed on selectedMarker,
    // so tapping a DIFFERENT marker restarts the timer for it, and closing it (tap-elsewhere / re-tap)
    // cancels the pending dismiss (the null-key branch does nothing). Same LaunchedEffect+delay pattern
    // as the splash / Training dwell timers.
    LaunchedEffect(selectedMarker) {
        if (selectedMarker != null) {
            delay(DOOR_MARKER_POPUP_MS)
            selectedMarker = null
        }
    }

    val favs by FavouritesRepository.state.collectAsState()
    val context = LocalContext.current

    // Native sneak indicator (sneaking && undetected). Drives the stealth icon below.
    val sneakVisible by GameStateRepository.sneakVisible.collectAsState()

    val weaponId = state.equipment["weapon"]
    val weaponItem = weaponId?.let {
        state.inventory.find { it.stackId == weaponId }
            ?: state.inventory.find { it.stackId.isEmpty() && it.id == weaponId }
    }
    val weaponName = if (weaponId == null) "Hand-to-Hand"
                     else weaponItem?.displayName() ?: "None"
    val weaponIcon = weaponItem?.icon ?: ""

    val selectedSpellEntry = state.selectedSpell
        ?.let { sid -> state.spells.find { it.id == sid } }
    val selectedSpellName = selectedSpellEntry?.displayName() ?: "None"
    val spellIcon = selectedSpellEntry?.icon ?: ""

    // The game's own player-direction arrow texture (same asset the native HUD minimap and world
    // map use, via RotatingSkin). Extracted through the shared icon pipeline (raw VFS path, DXT
    // decompressed) and drawn rotated by arrowDeg below; falls back to the drawn arrow while it
    // loads or if extraction fails.
    val compassBitmap = rememberItemIcon("textures/compass.dds")

    var showWeaponName by remember { mutableStateOf(false) }
    var showSpellName by remember { mutableStateOf(false) }

    // Laid out like Inventory/Spells: a fixed-height top box (the vitals bar) at 12dp from the
    // top, a 6dp gap, then the map box filling the rest to the tab bar — so all three tabs' boxes
    // line up. The map still fills its own box, so the crop math and overlays are unaffected.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 12.dp, bottom = BOTTOM_BAR_SPACE.dp, start = 12.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        TopStatBar(state, Modifier.fillMaxWidth().height(TOP_BOX_HEIGHT))
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .mwPanel()
                .onSizeChanged { mapBoxSize = it }
        ) {
        // Canvas fills the whole panel; labels float over it. A tap on a door marker shows its
        // destination name (and is reserved for richer per-marker info later); a tap anywhere else
        // (not covered by an overlay declared later in this Box — those consume the tap first via
        // Compose z-order) opens the in-game world map. Disabled while the splash is up.
        // mapReady gates opening the in-game map: during character creation the inventory/map GUI
        // isn't available, and forcing it (CMP:openmap -> AddUiMode Interface) wedges the game (the
        // top-screen map can't render). The first journal entry is the reliable "character created"
        // signal; it's a pointerInput key so the handler refreshes once char-gen completes.
        val mapReady = state.journalEntries.isNotEmpty()
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(doorMarkers, state.pos, markerAngle, markerCX, markerCY, splashVisible, mapReady) {
                    detectTapGestures { tap ->
                        if (splashVisible) return@detectTapGestures
                        val w = size.width.toFloat(); val h = size.height.toFloat()
                        val hitR = minOf(w, h) * 0.06f
                        // Nearest marker within the (generous, finger-sized) hit radius wins.
                        val hit = doorMarkers
                            .map { m ->
                                m to markerScreenPos(m.worldX, m.worldY, state.pos.x, state.pos.y,
                                    markerAngle, markerCX, markerCY, w, h)
                            }
                            .filter { (_, p) -> (p - tap).getDistance() <= hitR }
                            .minByOrNull { (_, p) -> (p - tap).getDistance() }
                        if (hit != null) {
                            // Re-tapping the marker already shown toggles it closed; tapping a
                            // different one swaps (and the LaunchedEffect above restarts its 3s timer).
                            val m = hit.first
                            selectedMarker = if (selectedMarker == m) null else m
                        } else if (selectedMarker != null) {
                            // A name bubble is showing: this empty-map tap just dismisses it and is
                            // consumed — do NOT also open the world map. A subsequent tap opens it.
                            selectedMarker = null
                        } else if (mapReady) {
                            CompanionActions.openWorldMap()
                        }
                        // else: character not yet created (no journal entries) — ignore the tap
                        // rather than opening the map, which would wedge the game during char-gen.
                    }
                }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val cellPx = size.minDimension / MINIMAP_CROP_FRACTION
            // Interior maps are rendered rotated by the cell's NorthMarker angle, so the interior
            // branch adds that angle below; exterior maps are world-aligned (angle 0).
            var arrowDeg = state.rotZ * (180f / Math.PI.toFloat())

            if (state.cellIsExterior) {
                val cellSize = 8192f
                val playerU = ((state.pos.x - state.cellGridX * cellSize) / cellSize).coerceIn(0f, 1f)
                val playerV = (1f - (state.pos.y - state.cellGridY * cellSize) / cellSize).coerceIn(0f, 1f)

                val originX = cx - playerU * cellPx
                val originY = cy - playerV * cellPx

                val reach = (maxOf(size.width, size.height) / cellPx).toInt() + 1
                for (dy in -reach..reach) {
                    for (dx in -reach..reach) {
                        val bmp = exteriorMaps[Pair(state.cellGridX + dx, state.cellGridY + dy)] ?: continue
                        val left = originX + dx * cellPx
                        val top  = originY - dy * cellPx
                        if (left + cellPx < 0f || left > size.width)  continue
                        if (top  + cellPx < 0f || top  > size.height) continue
                        drawImage(
                            image = bmp.asImageBitmap(),
                            dstOffset = IntOffset(left.toInt(), top.toInt()),
                            dstSize   = IntSize(cellPx.toInt(), cellPx.toInt()),
                        )
                    }
                }
            } else if (interiorMaps.isNotEmpty()) {
                // Interior cells are divided into mMapWorldSize-sized segments the same
                // way exterior cells are, anchored at the interior's mBounds min corner
                // (boundsMinX/boundsMinY — constant across all segments of one interior).
                // Compute which segment the player is standing in and their fractional
                // position within it, then crop/zoom exactly like the exterior branch.
                val interiorMapWorldSize = 8192f
                val seg0 = interiorMaps.values.first()
                val boundsMinX = seg0.boundsMinX
                val boundsMinY = seg0.boundsMinY

                // The interior map texture is rendered ROTATED by the cell's NorthMarker angle, so
                // the raw world position/yaw don't line up with it. Mirror the engine's
                // LocalMap::worldToInteriorMapPosition: rotate the player position by `angle` about
                // `center` (rotatePoint), THEN crop; and offset the arrow by the same `angle`
                // (LocalMap::updatePlayer applies +mAngle to the direction). Without this the dot
                // and arrow are off by a constant angle (the interior-spawn bug). angle=0 for a
                // pre-fix export leaves the old world-aligned behaviour.
                val cosA = cos(seg0.angle)
                val sinA = sin(seg0.angle)
                val rotX = cosA * (state.pos.x - seg0.centerX) - sinA * (state.pos.y - seg0.centerY) + seg0.centerX
                val rotY = sinA * (state.pos.x - seg0.centerX) + cosA * (state.pos.y - seg0.centerY) + seg0.centerY
                // Subtract (not add) the map angle: the vertical bitmap flip inverts the arrow's
                // rotation sense, so the interior offset is -angle. Position uses +angle (rotatePoint
                // matches the engine exactly); the arrow's screen convention is the mirror of it.
                arrowDeg -= seg0.angle * (180f / Math.PI.toFloat())

                val rawX = (rotX - boundsMinX) / interiorMapWorldSize
                val rawY = (rotY - boundsMinY) / interiorMapWorldSize
                val playerSegX = floor(rawX).toInt()
                val playerSegY = floor(rawY).toInt()
                val playerU = (rawX - playerSegX).coerceIn(0f, 1f)
                val playerV = (1f - (rawY - playerSegY)).coerceIn(0f, 1f)

                val originX = cx - playerU * cellPx
                val originY = cy - playerV * cellPx

                val reach = (maxOf(size.width, size.height) / cellPx).toInt() + 1
                for (dy in -reach..reach) {
                    for (dx in -reach..reach) {
                        val seg = interiorMaps[Pair(playerSegX + dx, playerSegY + dy)] ?: continue
                        val left = originX + dx * cellPx
                        val top  = originY - dy * cellPx
                        if (left + cellPx < 0f || left > size.width)  continue
                        if (top  + cellPx < 0f || top  > size.height) continue
                        drawImage(
                            image = seg.bitmap.asImageBitmap(),
                            dstOffset = IntOffset(left.toInt(), top.toInt()),
                            dstSize   = IntSize(cellPx.toInt(), cellPx.toInt()),
                        )
                    }
                }
            }

            // Door markers: a small square per teleport door, placed with the same transform as the
            // arrow (drawn UNDER the arrow). The tapped marker is highlighted with a brighter border.
            if (hasMap) {
                val half = size.minDimension * 0.028f
                doorMarkers.forEach { m ->
                    val p = markerScreenPos(m.worldX, m.worldY, state.pos.x, state.pos.y,
                        markerAngle, markerCX, markerCY, size.width, size.height)
                    if (p.x < -half || p.x > size.width + half || p.y < -half || p.y > size.height + half)
                        return@forEach
                    val sel = selectedMarker == m
                    val topLeft = Offset(p.x - half, p.y - half)
                    val sz = Size(half * 2f, half * 2f)
                    drawRect(color = SlotBg, topLeft = topLeft, size = sz)
                    drawRect(
                        color = if (sel) BoneBright else BronzeLight,
                        topLeft = topLeft, size = sz,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(
                            width = half * (if (sel) 0.55f else 0.35f))
                    )
                }
            }

            if (hasMap) {
                val bmp = compassBitmap
                if (bmp != null) {
                    // Draw the game's compass.dds arrow centered on the player, rotated to face.
                    // Its neutral orientation is "up" (angle 0), matching drawArrow, so arrowDeg
                    // feeds in directly. Diameter ~matches the old drawn arrow's footprint.
                    val side = (size.minDimension * 0.12f)
                    rotate(degrees = arrowDeg, pivot = Offset(cx, cy)) {
                        drawImage(
                            image = bmp,
                            dstOffset = IntOffset((cx - side / 2f).toInt(), (cy - side / 2f).toInt()),
                            dstSize = IntSize(side.toInt(), side.toInt()),
                        )
                    }
                } else {
                    drawArrow(cx, cy, size.minDimension * 0.04f, arrowDeg)
                }
            }
        }

        // Tapped door marker's name, anchored just above the marker (px offset within this Box; the
        // offset{} lambda is a Density receiver so dp→px is available). Tapping it dismisses; tapping
        // elsewhere on the map clears it and opens the world map. This is the seed for the future
        // richer per-marker info popup.
        // The anchor is recomputed EACH recomposition from the live state.pos via the SAME
        // markerScreenPos transform the Canvas draw uses, so the bubble follows the marker as the
        // player moves (rather than freezing at the tap-time screen position). state.pos updates on
        // the fast tick, so the offset re-runs and tracks smoothly. Gated on a laid-out map size.
        if (mapBoxSize.width > 0 && mapBoxSize.height > 0) selectedMarker?.let { marker ->
            val pos = markerScreenPos(
                marker.worldX, marker.worldY, state.pos.x, state.pos.y,
                markerAngle, markerCX, markerCY,
                mapBoxSize.width.toFloat(), mapBoxSize.height.toFloat()
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .offset {
                        IntOffset(
                            (pos.x - 80.dp.toPx()).roundToInt().coerceAtLeast(4),
                            (pos.y - 46.dp.toPx()).roundToInt().coerceAtLeast(4)
                        )
                    }
                    .widthIn(max = 160.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(StoneDark)
                    .border(1.dp, Bronze, RoundedCornerShape(4.dp))
                    .pointerInput(Unit) { detectTapGestures { selectedMarker = null } }
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    marker.name.ifBlank { "Door" },
                    color = BoneBright, fontSize = 12.sp, fontFamily = MwBody,
                    maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 14.sp
                )
            }
        }

        // Cell name — bottom-centre overlay. Horizontal padding keeps it clear of
        // the GEAR/SPELLS favourite groups in the bottom corners. Up to TWO lines: because the
        // Text is BottomCenter-anchored, a short name renders as one line in its current spot and
        // a long one grows UPWARD into a second row (so more of e.g. "Seyda Neen, Census and
        // Excise Office" is shown); anything past two lines still truncates with an ellipsis.
        // textAlign centres both rows.
        Text(
            if (state.hasData) state.cell.ifEmpty { "Exterior" } else "—",
            color = Bone, fontSize = 16.sp, fontFamily = MwDisplay,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp, start = 144.dp, end = 144.dp)
        )

        // Weapon — top-left icon box. Tapping toggles a persistent name label.
        EquippedCornerIcon(
            modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 6.dp),
            label = "WEAPON",
            iconPath = weaponIcon,
            showName = showWeaponName,
            onToggle = { showWeaponName = !showWeaponName }
        )

        // Spell — top-right icon box, mirror of the weapon group.
        EquippedCornerIcon(
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 8.dp, top = 6.dp),
            label = "SPELL",
            iconPath = spellIcon,
            showName = showSpellName,
            onToggle = { showSpellName = !showSpellName }
        )

        // Sneak indicator — vanilla stealth icon (sneaking && undetected), shown just
        // below the WEAPON icon at the same 40dp size. Clear of the name-label popouts
        // (horizontal), the combat-target overlay (centre) and the bottom fav groups.
        // Driven by the native COMPANION_SNEAK_VISIBLE signal.
        if (sneakVisible) {
            SneakCornerIcon(
                modifier = Modifier.align(Alignment.TopStart).padding(start = 8.dp, top = 64.dp)
            )
        }

        // Name labels — absolutely-positioned siblings of the icon columns above,
        // so showing/hiding them never shifts the icons. Each sits beside its icon
        // (weapon: to the right; spell: to the left), vertically centred on the box.
        // Horizontal offset = corner gap (8dp) + icon width (40dp) + 6dp gap.
        if (showWeaponName) {
            CornerNameLabel(
                name = weaponName,
                alignEnd = false,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 8.dp + 40.dp + 6.dp, top = 6.dp)
            )
        }
        if (showSpellName) {
            CornerNameLabel(
                name = selectedSpellName,
                alignEnd = true,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 8.dp + 40.dp + 6.dp, top = 6.dp)
            )
        }

        // Loading placeholder
        if (!hasMap && state.hasData) {
            Text(
                "Loading map…",
                color = BronzeDark, fontSize = 13.sp, fontFamily = MwBody,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        // Gear favourites — bottom-right, stacked vertically
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "FAV. GEAR",
                color = BoneDim,
                fontSize = 10.sp,
                fontFamily = MwDisplay,
                letterSpacing = 1.sp
            )
            repeat(2) { idx ->
                val slot = favs.gear.getOrNull(idx)
                val slotItem = slot?.let { s -> state.inventory.find { it.id == s.id } }
                val slotWorn = slot != null && (
                    if (slotItem?.stackId?.isNotEmpty() == true)
                        state.equipment.values.contains(slotItem.stackId)
                    else
                        state.equipment.values.contains(slot.id)
                )
                FavSlotView(
                    slot = slot,
                    borderColor = BronzeLight,
                    equipped = slotWorn,
                    menuItems = { s, dismiss ->
                        val item = state.inventory.find { it.id == s.id }
                        val readable = item?.category == "book" || item?.category == "scroll"
                        val equippable = item != null && item.category != "misc" && !readable
                        val target = item?.stackId?.takeIf { it.isNotEmpty() } ?: s.id
                        val worn = if (item?.stackId?.isNotEmpty() == true)
                            state.equipment.values.contains(item.stackId)
                        else
                            state.equipment.values.contains(s.id)
                        val colors = MenuDefaults.itemColors(textColor = Bone)
                        if (equippable) {
                            DropdownMenuItem(
                                text = { Text(if (worn) "Unequip" else "Equip", fontFamily = MwBody, fontSize = 13.sp) },
                                onClick = {
                                    dismiss()
                                    if (worn) CompanionActions.unequipItem(target)
                                    else CompanionActions.equipItem(target)
                                },
                                colors = colors
                            )
                        }
                        if (readable) {
                            DropdownMenuItem(
                                text = { Text("Read", fontFamily = MwBody, fontSize = 13.sp) },
                                onClick = { dismiss(); CompanionActions.readItem(s.id) },
                                colors = colors
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Drop", fontFamily = MwBody, fontSize = 13.sp) },
                            onClick = {
                                dismiss()
                                val count = item?.count ?: 1
                                QuantityRequestState.requestOrRun(s.name, count, "Drop") { n ->
                                    CompanionActions.dropItem(s.id, n)
                                }
                            },
                            colors = colors
                        )
                        DropdownMenuItem(
                            text = { Text("Unfavourite", fontFamily = MwBody, fontSize = 13.sp) },
                            onClick = { dismiss(); FavouritesRepository.clearGear(context, idx) },
                            colors = colors
                        )
                    }
                ) {
                    val s = slot ?: return@FavSlotView
                    val item = state.inventory.find { it.id == s.id }
                    val readable = item?.category == "book" || item?.category == "scroll"
                    val equippable = item != null && item.category != "misc" && !readable
                    // Use per-stack instance id when available; fall back to recordId.
                    val target = item?.stackId?.takeIf { it.isNotEmpty() } ?: s.id
                    val worn = if (item?.stackId?.isNotEmpty() == true)
                        state.equipment.values.contains(item.stackId)
                    else
                        state.equipment.values.contains(s.id)
                    when {
                        readable    -> CompanionActions.readItem(s.id)
                        equippable && worn -> CompanionActions.unequipItem(target)
                        equippable  -> CompanionActions.equipItem(target)
                    }
                }
            }
        }

        // Magic favourites — bottom-left, stacked vertically
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "FAV. SPELLS",
                color = BoneDim,
                fontSize = 10.sp,
                fontFamily = MwDisplay,
                letterSpacing = 1.sp
            )
            repeat(2) { idx ->
                val slot = favs.magic.getOrNull(idx)
                val slotSelected = slot != null && state.selectedSpell == slot.id
                FavSlotView(
                    slot = slot,
                    borderColor = BronzeLight,
                    equipped = slotSelected,
                    menuItems = { s, dismiss ->
                        val colors = MenuDefaults.itemColors(textColor = Bone)
                        DropdownMenuItem(
                            text = { Text("Set as active spell", fontFamily = MwBody, fontSize = 13.sp) },
                            onClick = { dismiss(); CompanionActions.selectSpell(s.id) },
                            colors = colors
                        )
                        DropdownMenuItem(
                            text = { Text("Unfavourite", fontFamily = MwBody, fontSize = 13.sp) },
                            onClick = { dismiss(); FavouritesRepository.clearMagic(context, idx) },
                            colors = colors
                        )
                    }
                ) {
                    slot?.let { CompanionActions.selectSpell(it.id) }
                }
            }
        }

        // Combat target — top-centre, in the gap between the WEAPON and SPELL
        // pills. Only present while a target exists (during combat), and only when the
        // target-health bar is routed to the bottom screen (TOP moves it to a top-screen overlay).
        val targetHealthLocation by UiPreferences.targetHealthLocationFlow().collectAsState()
        if (targetHealthLocation == TargetHealthLocation.BOTTOM) {
            state.target?.let { target ->
                TargetHealthOverlay(
                    target = target,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 6.dp)
                )
            }
        }
        }  // end map Box
    }
}

/** Target name + health bar shown at the bottom-centre of the HUD map. */
@Composable
private fun TargetHealthOverlay(target: TargetInfo, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(200.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "TARGET",
            color = BoneDim,
            fontSize = 9.sp,
            fontFamily = MwDisplay,
            letterSpacing = 1.5.sp
        )
        Spacer(Modifier.height(4.dp))
        Text(
            target.name,
            color = BronzeLight,
            fontSize = 16.sp,
            fontFamily = MwBody,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 196.dp)
        )
        Spacer(Modifier.height(5.dp))
        // Taller variant of the player health bar in TopStatBar's CompactStat.
        Box(
            modifier = Modifier
                .width(200.dp)
                .height(16.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color(0xFF0E0B07))
                .border(1.dp, BronzeDark, RoundedCornerShape(2.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(target.health.ratio)
                    .height(16.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(HealthCol)
            )
        }
    }
}

// Top-corner equipped weapon / selected spell: a 40dp icon box with a centered
// caption above it. Tapping the box toggles a persistent name label (rendered as
// a separate sibling overlay — see CornerNameLabel — so it never shifts the icon).
// Border brightens to BronzeLight while the name label is showing.
@Composable
private fun EquippedCornerIcon(
    modifier: Modifier,
    label: String,
    iconPath: String,
    showName: Boolean,
    onToggle: () -> Unit,
) {
    val icon = rememberItemIcon(iconPath)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            label,
            color = BoneDim,
            fontSize = 7.sp,
            fontFamily = MwDisplay,
            letterSpacing = 1.sp
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SlotBg)
                .border(1.dp, if (showName) BronzeLight else BronzeDark, RoundedCornerShape(2.dp))
                .clickable { onToggle() }
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

// The sneak indicator icon (vanilla "sneaking && undetected" state). Mirrors
// EquippedCornerIcon's 40dp box + caption but is non-interactive (no name toggle).
// Rendered only while GameStateRepository.sneakVisible is true. The icon is the same
// VFS DDS the native HUD uses (icons/k/stealth_sneak.dds), loaded via rememberItemIcon.
@Composable
private fun SneakCornerIcon(modifier: Modifier) {
    val icon = rememberItemIcon("icons/k/stealth_sneak.dds")
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            "SNEAK",
            color = BoneDim,
            fontSize = 7.sp,
            fontFamily = MwDisplay,
            letterSpacing = 1.sp
        )
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SlotBg)
                .border(1.dp, BronzeDark, RoundedCornerShape(2.dp))
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(40.dp)
                )
            }
        }
    }
}

// The equipped weapon / spell name label, rendered as an absolutely-positioned
// sibling of the icon Column (NOT a child) so toggling it can never move the icon.
// It sits beside the 40dp icon box and is vertically centred on it: an invisible
// caption placeholder + 4dp spacer reproduce the icon Column's vertical offset
// (top padding → caption → gap → icon) so the label lines up with the icon exactly
// regardless of font metrics. `alignEnd` mirrors the layout for the right corner.
@Composable
private fun CornerNameLabel(name: String, alignEnd: Boolean, modifier: Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (alignEnd) Alignment.End else Alignment.Start
    ) {
        // Transparent copy of the WEAPON/SPELL caption: reserves the exact same
        // height as the real caption above the icon so the label below aligns.
        Text(
            "W",
            color = Color.Transparent,
            fontSize = 7.sp,
            fontFamily = MwDisplay,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(4.dp))
        // 40dp-tall band matching the icon box; centre the label within it.
        Box(Modifier.height(40.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .widthIn(max = 120.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(StonePanel)
                    .border(1.dp, BronzeDark, RoundedCornerShape(2.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    name,
                    color = Bone,
                    fontSize = 9.sp,
                    fontFamily = MwBody,
                    maxLines = 1,
                    softWrap = false,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private val EQUIPPED_PILL_MAX_FONT = 12.sp
private val EQUIPPED_PILL_MIN_FONT = 8.sp

@Composable
private fun EquippedDisplayPill(value: String) {
    val isEmpty = value == "None"
    var fontSize by remember(value) { mutableStateOf(EQUIPPED_PILL_MAX_FONT) }
    Box(
        modifier = Modifier
            .width(FAV_SLOT_WIDTH)
            .height(FAV_SLOT_HEIGHT)
            .clip(RoundedCornerShape(4.dp))
            .background(Color(0xC0151210))
            .border(1.dp, BronzeDark, RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            value,
            color = if (isEmpty) BoneDim else Bone,
            fontSize = fontSize,
            fontFamily = MwBody,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            softWrap = false,
            onTextLayout = { result ->
                if (result.didOverflowWidth && fontSize > EQUIPPED_PILL_MIN_FONT) {
                    fontSize = (fontSize.value - 1).sp
                }
            },
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
}

// Screen position (px, within a wxh canvas) of a world point on the companion minimap, using the
// SAME transform as the player arrow: player centered, exterior world-aligned or interior rotated by
// `angle` about (centerX,centerY). angle=0 (with center 0,0) → the identity used for exterior. Shared
// by the door-marker draw and the tap hit-test so they can never diverge.
private fun markerScreenPos(
    worldX: Float, worldY: Float, playerX: Float, playerY: Float,
    angle: Float, centerX: Float, centerY: Float, w: Float, h: Float
): Offset {
    val cellPx = minOf(w, h) / MINIMAP_CROP_FRACTION
    val ca = cos(angle); val sa = sin(angle)
    val rmx = ca * (worldX - centerX) - sa * (worldY - centerY) + centerX
    val rmy = sa * (worldX - centerX) + ca * (worldY - centerY) + centerY
    val rpx = ca * (playerX - centerX) - sa * (playerY - centerY) + centerX
    val rpy = sa * (playerX - centerX) + ca * (playerY - centerY) + centerY
    return Offset(w / 2f + (rmx - rpx) / 8192f * cellPx, h / 2f - (rmy - rpy) / 8192f * cellPx)
}

private fun DrawScope.drawArrow(cx: Float, cy: Float, r: Float, degrees: Float) {
    val path = androidx.compose.ui.graphics.Path().apply {
        moveTo(cx, cy - r * 1.8f)
        lineTo(cx - r * 0.7f, cy + r * 0.6f)
        lineTo(cx,             cy + r * 0.1f)
        lineTo(cx + r * 0.7f, cy + r * 0.6f)
        close()
    }
    rotate(degrees = degrees, pivot = Offset(cx, cy)) {
        drawPath(path, color = androidx.compose.ui.graphics.Color(0xFFFFD700))
        drawPath(path, color = androidx.compose.ui.graphics.Color(0xFF000000),
            style = androidx.compose.ui.graphics.drawscope.Stroke(width = r * 0.25f))
    }
}


@Composable
private fun MapButton(label: String, filled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (filled) Bronze else Color.Transparent)
            .border(1.dp, if (filled) BronzeLight else BronzeDark, RoundedCornerShape(2.dp))
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (filled) StoneDark else Bone,
            fontSize = 12.sp,
            fontFamily = MwDisplay,
            fontWeight = if (filled) FontWeight.Bold else FontWeight.Normal
        )
    }
}

/* ---- Favourites: shared helpers ---- */

/** Small gold star drawn to the RIGHT of a favourited item/spell name. It's a
 *  standalone Icon (never concatenated into the name string) so it appears and
 *  disappears the instant the favourites state changes. */
@Composable
private fun FavStar() {
    Icon(
        imageVector = Icons.Filled.Star,
        contentDescription = "Favourite",
        tint = BronzeLight,
        modifier = Modifier.size(13.dp)
    )
}

/**
 * Favourites section for an item/spell long-press dropdown (rendered inside a
 * DropdownMenu's ColumnScope). Shows "Unfavourite" when the record already
 * occupies a slot; otherwise "Add to favourites" when a slot is free, or an
 * explicit two-slot picker when BOTH are full — the old auto-pick always
 * clobbered slot 1, so slot 2 could never be replaced. `isGear` selects the
 * gear vs magic slot pair; `makeSlot` builds the FavSlot with the live name.
 */
@Composable
private fun ColumnScope.FavouriteMenuItems(
    context: Context,
    isGear: Boolean,
    itemId: String,
    makeSlot: () -> FavSlot,
    onDone: () -> Unit
) {
    val favs by FavouritesRepository.state.collectAsState()
    val slots = if (isGear) favs.gear else favs.magic
    val colors = MenuDefaults.itemColors(textColor = Bone)
    val favIdx = slots.indexOfFirst { it?.id == itemId }

    fun assign(index: Int) {
        onDone()
        if (isGear) FavouritesRepository.assignGear(context, makeSlot(), index)
        else FavouritesRepository.assignMagic(context, makeSlot(), index)
    }

    if (favIdx >= 0) {
        DropdownMenuItem(
            text = { Text("Unfavourite", fontFamily = MwBody, fontSize = 13.sp) },
            onClick = {
                onDone()
                if (isGear) FavouritesRepository.clearGear(context, favIdx)
                else FavouritesRepository.clearMagic(context, favIdx)
            },
            colors = colors
        )
    } else {
        val emptyIdx = slots.indexOfFirst { it == null }
        if (emptyIdx >= 0) {
            DropdownMenuItem(
                text = { Text("Add to favourites", fontFamily = MwBody, fontSize = 13.sp) },
                onClick = { assign(emptyIdx) },
                colors = colors
            )
        } else {
            // Both slots full — let the user choose which one to overwrite.
            Text(
                "Replace favourite…",
                color = BoneDim, fontSize = 10.sp, fontFamily = MwDisplay,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 2.dp)
            )
            slots.forEachIndexed { i, s ->
                DropdownMenuItem(
                    text = {
                        Text(
                            "Slot ${i + 1}: ${s?.name ?: "—"}",
                            fontFamily = MwBody, fontSize = 13.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                    },
                    onClick = { assign(i) },
                    colors = colors
                )
            }
        }
    }
}

/* ---- Favourite slots (floating over the map) ---- */

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FavSlotView(
    slot: FavSlot?,
    borderColor: Color,
    equipped: Boolean = false,
    menuItems: (@Composable ColumnScope.(slot: FavSlot, dismiss: () -> Unit) -> Unit)? = null,
    onClick: () -> Unit
) {
    val isEmpty = slot == null
    val alpha   = if (isEmpty) 0.4f else 1f
    // Mirror the inventory worn/unworn styling: equipped favourites get the
    // bronze-tinted fill + bright border/name, non-equipped ones stay dim.
    val bgColor    = if (equipped) SlotWorn else Color(0xC0151210)
    val slotBorder = if (equipped) borderColor else BronzeDark
    val textColor  = if (equipped) BoneBright else BoneMuted

    var menuOpen by remember { mutableStateOf(false) }
    // Snapshot the slot the menu was opened for. The menu renders from this, NOT
    // the live `slot`, so that Unfavourite (which nulls the live slot the instant
    // it's tapped) doesn't blank the menu content mid-close and make the popup
    // reposition/"slide" — it fades in place like the other actions. Never reset
    // on dismiss; it's overwritten on the next long-press.
    var menuSlot by remember { mutableStateOf<FavSlot?>(null) }
    LaunchedEffect(DropdownState.closeRequest) {
        if (DropdownState.closeRequest > 0) menuOpen = false
    }

    Box(
        modifier = Modifier
            .width(FAV_SLOT_WIDTH)
            .height(FAV_SLOT_HEIGHT)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(4.dp))
                .background(bgColor)
                .then(
                    if (!isEmpty) Modifier.border(1.dp, slotBorder, RoundedCornerShape(4.dp))
                    else Modifier
                )
                .combinedClickable(
                    enabled = !isEmpty,
                    onClick = { onClick() },
                    onLongClick = {
                        if (menuItems != null && slot != null) {
                            menuSlot = slot; menuOpen = true; DropdownState.open()
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = slot?.name ?: "Empty",
                color = textColor.copy(alpha = alpha),
                fontSize = 11.sp,
                fontFamily = MwBody,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 6.dp)
            )
        }
        if (menuItems != null) {
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false; DropdownState.closeAll() },
                properties = PopupProperties(focusable = false, dismissOnClickOutside = false),
                containerColor = StonePanel,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, Bronze),
                shape = RoundedCornerShape(3.dp)
            ) {
                menuSlot?.let { s ->
                    Text(
                        s.name,
                        color = BronzeLight, fontSize = 12.sp,
                        fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                    Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark))
                    menuItems(s) { menuOpen = false; DropdownState.closeAll() }
                }
            }
        }
        // Dashed border for empty slots drawn as Canvas overlay (avoids clip-halving issue)
        if (isEmpty) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawRoundRect(
                    color = borderColor.copy(alpha = alpha),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = androidx.compose.ui.graphics.PathEffect
                            .dashPathEffect(floatArrayOf(6f, 4f))
                    ),
                    cornerRadius = CornerRadius(4.dp.toPx())
                )
            }
        }
    }
}

/* ---- Inventory ---- */

@Composable
private fun InventoryPanel(state: GameState) {
    var selectedCategoryLabel by remember { mutableStateOf<String?>(null) }
    val presentCategories = remember(state.inventory) {
        INV_CATEGORIES.filter { grp -> state.inventory.any { it.category in grp.cats } }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 12.dp, bottom = BOTTOM_BAR_SPACE.dp, start = 12.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        EquippedStrip(state)
        Column(Modifier.weight(1f).mwPanel()) {
            CategorySubTabs(
                categories = presentCategories,
                selected = selectedCategoryLabel,
                onSelect = { selectedCategoryLabel = it }
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark))
            Box(Modifier.weight(1f)) {
                InventoryItemList(state, selectedCategoryLabel)
            }
        }
    }
}

@Composable
private fun EquippedStrip(state: GameState) {
    val wornItems = remember(state.inventory, state.equipment) {
        EQUIPMENT_SLOT_ORDER
            .mapNotNull { slot -> state.equipment[slot] }
            .mapNotNull { sid ->
                // equipment values are now per-stack instance ids (stackId); fall back
                // to recordId matching for older Lua that emits recordId instead.
                state.inventory.find { it.stackId == sid }
                    ?: state.inventory.find { it.stackId.isEmpty() && it.id == sid }
            }
            .distinctBy { it.stackId.ifEmpty { it.id } }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(TOP_BOX_HEIGHT)
            .mwPanel()
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gold + carried weight, before the EQUIPPED strip. From COMPANION_STATS.
        StripStat("GOLD", state.gold.toString())
        StripDivider()
        StripStat(
            "WEIGHT",
            "${state.encumbrance.current.toInt()}/${state.encumbrance.max.toInt()}"
        )
        StripDivider()
        Text(
            "EQUIPPED",
            color = BronzeLight,
            fontSize = 10.sp,
            fontFamily = MwDisplay,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
        Box(
            Modifier
                .padding(horizontal = 10.dp)
                .width(1.dp)
                .height(28.dp)
                .background(BronzeDark)
        )
        if (wornItems.isEmpty()) {
            Text("Nothing equipped", color = BoneDim, fontSize = 12.sp, fontFamily = MwBody)
        } else {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                wornItems.forEach { item -> EquippedChip(item.displayName()) }
            }
        }
    }
}

/** A compact label-over-value readout for the equipped strip (Gold / Weight). */
@Composable
private fun StripStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            color = BoneDim,
            fontSize = 8.sp,
            fontFamily = MwDisplay,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Text(
            value,
            color = BronzeLight,
            fontSize = 12.sp,
            fontFamily = MwData,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

/** The 1dp vertical divider used between segments of the equipped strip. */
@Composable
private fun StripDivider() {
    Box(
        Modifier
            .padding(horizontal = 10.dp)
            .width(1.dp)
            .height(28.dp)
            .background(BronzeDark)
    )
}

@Composable
private fun EquippedChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(SlotWorn)
            .border(1.dp, BronzeLight, RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            label,
            color = BronzeLight,
            fontSize = 11.sp,
            fontFamily = MwBody,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CategorySubTabs(
    categories: List<InvCategory>,
    selected: String?,
    onSelect: (String?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        CategoryTab(label = "All", active = selected == null) { onSelect(null) }
        categories.forEach { cat ->
            CategoryTab(label = cat.label, active = selected == cat.label) { onSelect(cat.label) }
        }
    }
}

@Composable
private fun CategoryTab(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(2.dp))
            .background(if (active) Bronze else Color.Transparent)
            .border(1.dp, if (active) BronzeLight else BronzeDark, RoundedCornerShape(2.dp))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Text(
            label.uppercase(),
            color = if (active) StoneDark else Bone,
            fontSize = 12.sp,
            fontFamily = MwDisplay,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun InventoryItemList(state: GameState, selectedCategoryLabel: String?) {
    val wornIds = remember(state.equipment) { state.equipment.values.toSet() }
    val selectedGroup = INV_CATEGORIES.find { it.label == selectedCategoryLabel }

    if (state.inventory.isEmpty()) {
        EmptyPanel("No inventory recorded")
        return
    }

    // Match by per-stack instance id (stackId) when available; fall back to
    // recordId for items whose Lua side didn't emit a sid field.
    fun isWorn(item: InventoryItem): Boolean =
        if (item.stackId.isNotEmpty()) wornIds.contains(item.stackId)
        else wornIds.contains(item.id)

    LazyColumn(modifier = Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
        if (selectedGroup == null) {
            INV_CATEGORIES.forEach { grp ->
                val groupItems = state.inventory
                    .filter { it.category in grp.cats }
                    // Worn items first, then the rest alphabetically.
                    .sortedWith(compareBy({ itemCategoryRank(it.category) }, { it.displayName().lowercase() }))
                if (groupItems.isNotEmpty()) {
                    item(key = "hdr_${grp.label}") { SpellSectionHeader(grp.label) }
                    items(groupItems) { item ->
                        val worn = isWorn(item)
                        ItemRow(item, worn, equippable = item.isEquippable(),
                            usable = item.isUsable(), readable = item.isReadable(),
                            iconBitmap = rememberItemIcon(item.icon))
                    }
                }
            }
        } else {
            val groupItems = state.inventory
                .filter { it.category in selectedGroup.cats }
                // Worn items first, then the rest alphabetically.
                .sortedWith(compareBy({ itemCategoryRank(it.category) }, { it.displayName().lowercase() }))
            items(groupItems) { item ->
                val worn = isWorn(item)
                ItemRow(item, worn, equippable = item.isEquippable(),
                    usable = item.isUsable(), readable = item.isReadable(),
                    iconBitmap = rememberItemIcon(item.icon))
            }
        }
        item { Spacer(Modifier.height(4.dp)) }
    }
}

// Lazily extracts + caches an item icon from the OpenMW VFS (BSA / loose files)
// and returns it as an ImageBitmap, or null while loading / on failure (in which
// case the caller shows the empty placeholder box). Cache key is the icon path,
// which is shared by every instance of the same record — icons never change, so a
// PNG is written to cacheDir/item_icons once and reloaded from disk thereafter.
// Safe to call from inside a LazyColumn item: LaunchedEffect cancels/restarts as
// rows recycle, and the actual extract/decode runs on Dispatchers.IO.
@Composable
private fun rememberItemIcon(iconPath: String): ImageBitmap? {
    val context = LocalContext.current
    var bitmap by remember(iconPath) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(iconPath) {
        if (iconPath.isEmpty()) return@LaunchedEffect
        val cacheDir = File(context.cacheDir, "item_icons").apply { mkdirs() }
        val cacheFile = File(cacheDir, iconPath.replace('\\', '_').replace('/', '_') + ".png")
        // Bounded retry: exportIconToPng needs the engine's ResourceSystem, which may not be ready
        // yet when an icon on the default HUD tab (the compass) first composes at launch / save-load.
        // A single early failure used to be permanent (LaunchedEffect(iconPath) never re-runs); now
        // we retry until it succeeds. A genuinely-missing texture just exhausts the retries and the
        // caller shows its placeholder/fallback (same end state as before, only delayed).
        var attempt = 0
        while (true) {
            val decoded = withContext(Dispatchers.IO) {
                if (!cacheFile.exists()) {
                    CompanionActions.exportIconToPng(iconPath, cacheFile.absolutePath)
                }
                if (!cacheFile.exists()) return@withContext null
                val rawBitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                if (rawBitmap == null) {
                    cacheFile.delete()  // corrupt/partial write — let the next attempt re-extract
                    return@withContext null
                }
                // Flip vertically: OpenGL row 0 = bottom, Android bitmap row 0 = top, so exported
                // icon PNGs come out upside-down (same as the minimap flip in GameStateRepository).
                val flipMatrix = Matrix().apply { preScale(1f, -1f) }
                val flipped = Bitmap.createBitmap(
                    rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, flipMatrix, false
                )
                rawBitmap.recycle()
                flipped.asImageBitmap()
            }
            if (decoded != null) {
                bitmap = decoded
                return@LaunchedEffect
            }
            attempt++
            if (attempt >= 12) return@LaunchedEffect
            delay(500L)
        }
    }
    return bitmap
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemRow(
    item: InventoryItem,
    worn: Boolean,
    equippable: Boolean,
    usable: Boolean = false,
    readable: Boolean,
    iconBitmap: ImageBitmap? = null
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(DropdownState.closeRequest) {
        if (DropdownState.closeRequest > 0) menuOpen = false
    }
    val label = item.displayName()
    val favs by FavouritesRepository.state.collectAsState()
    val isFav = favs.gear.any { it?.id == item.id }

    Box(modifier = Modifier.onGloballyPositioned { ItemInfoPopupState.reportAnchor(item.id, it.boundsInRoot()) }) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            val target = item.stackId.ifEmpty { item.id }
                            when {
                                readable -> CompanionActions.readItem(item.id)
                                usable -> CompanionActions.useItem(item.id)
                                equippable && worn -> CompanionActions.unequipItem(target)
                                equippable -> CompanionActions.equipItem(target)
                            }
                        },
                        onLongClick = { menuOpen = true; DropdownState.open() }
                    )
                    .padding(start = 14.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon box (leftmost). Placeholder empty slot until the icon
                // pipeline loads real DDS textures; drop in a real bitmap by
                // passing iconBitmap non-null at the call site.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SlotBg)
                        .border(1.dp, BronzeDark, RoundedCornerShape(2.dp))
                ) {
                    EnchantBackdrop(item.enchant != null)
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                // Name + (optional) favourite star share the flex column so the
                // name truncates before the star, and the star stays a standalone
                // Icon — never concatenated into the name string.
                Row(
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        label,
                        color = if (worn) BoneBright else BoneMuted,
                        fontSize = 14.sp,
                        fontFamily = MwBody,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isFav) {
                        Spacer(Modifier.width(4.dp))
                        FavStar()
                    }
                }
                // Trailing columns are fixed-width slots (empty when N/A) so the
                // stat value, condition bar and worn/count tag always sit in the
                // same horizontal position across every row.
                //
                // Stat column: weapon damage / armor rating / etc.
                Box(Modifier.width(66.dp), contentAlignment = Alignment.CenterEnd) {
                    if (item.statVal.isNotEmpty()) {
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                item.statVal,
                                color = BronzeLight,
                                fontSize = 11.sp,
                                fontFamily = MwData,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (item.statKey.isNotEmpty()) {
                                Text(
                                    item.statKey,
                                    color = BoneDim,
                                    fontSize = 8.sp,
                                    fontFamily = MwBody,
                                    letterSpacing = 0.5.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                // Condition column.
                Box(Modifier.width(50.dp), contentAlignment = Alignment.Center) {
                    if (item.cond != null) {
                        val fillColor = if (item.cond >= 0.5f) BronzeLight else Color(0xFFC75C5C)
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "COND",
                                color = BoneDim,
                                fontSize = 7.sp,
                                fontFamily = MwBody,
                                letterSpacing = 0.5.sp
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(1.dp))
                                    .background(Color(0xFF0E0B07))
                                    .border(1.dp, BronzeDark, RoundedCornerShape(1.dp))
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth(item.cond.coerceIn(0f, 1f))
                                        .fillMaxHeight()
                                        .background(fillColor)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(16.dp))
                // Worn / count column — fixed slot, empty when neither applies.
                Box(Modifier.width(46.dp), contentAlignment = Alignment.CenterEnd) {
                    when {
                        worn -> Text(
                            "WORN",
                            color = BronzeLight,
                            fontSize = 10.sp,
                            fontFamily = MwDisplay,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.8.sp
                        )
                        item.count > 1 -> Text(
                            "x${item.count}",
                            color = BoneDim,
                            fontSize = 11.sp,
                            fontFamily = MwData
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.4f)))
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false; DropdownState.closeAll() },
            properties = PopupProperties(focusable = false, dismissOnClickOutside = false),
            containerColor = StonePanel,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Bronze),
            shape = RoundedCornerShape(3.dp)
        ) {
            Text(
                label,
                color = BronzeLight,
                fontSize = 12.sp,
                fontFamily = MwDisplay,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark))
            val menuItemColors = MenuDefaults.itemColors(textColor = Bone)
            if (worn || equippable) {
                DropdownMenuItem(
                    text = { Text(if (worn) "Unequip" else "Equip", fontFamily = MwBody, fontSize = 13.sp) },
                    onClick = {
                        menuOpen = false; DropdownState.closeAll()
                        val target = item.stackId.ifEmpty { item.id }
                        if (worn) CompanionActions.unequipItem(target)
                        else CompanionActions.equipItem(target)
                    },
                    colors = menuItemColors
                )
            }
            if (readable) {
                DropdownMenuItem(
                    text = { Text("Read", fontFamily = MwBody, fontSize = 13.sp) },
                    onClick = { menuOpen = false; DropdownState.closeAll(); CompanionActions.readItem(item.id) },
                    colors = menuItemColors
                )
            }
            if (usable) {
                DropdownMenuItem(
                    text = { Text(item.useVerb(), fontFamily = MwBody, fontSize = 13.sp) },
                    onClick = { menuOpen = false; DropdownState.closeAll(); CompanionActions.useItem(item.id) },
                    colors = menuItemColors
                )
            }
            DropdownMenuItem(
                text = { Text("Info", fontFamily = MwBody, fontSize = 13.sp) },
                onClick = { menuOpen = false; DropdownState.closeAll(); ItemInfoPopupState.open(item.id, item.name, item.enchant) },
                colors = menuItemColors
            )
            DropdownMenuItem(
                text = { Text("Drop", fontFamily = MwBody, fontSize = 13.sp) },
                onClick = {
                    menuOpen = false; DropdownState.closeAll()
                    // count > 1 → ask how many; count == 1 drops immediately.
                    QuantityRequestState.requestOrRun(label, item.count, "Drop") { n ->
                        CompanionActions.dropItem(item.id, n)
                    }
                },
                colors = menuItemColors
            )
            // Add to favourites / Unfavourite / replace-slot picker.
            FavouriteMenuItems(
                context = context,
                isGear = true,
                itemId = item.id,
                makeSlot = { FavSlot(item.id, label) },
                onDone = { menuOpen = false; DropdownState.closeAll() }
            )
        }
    }
}

/* ---- Magic (list of spells) ---- */

@Composable
private fun SpellSectionHeader(title: String) {
    Column {
        Text(
            title,
            color = BronzeLight, fontSize = 13.sp,
            fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 6.dp, top = 8.dp, bottom = 4.dp)
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark))
    }
}

@Composable
private fun MagicPanel(state: GameState) {
    val sel = state.selectedSpell

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 12.dp, bottom = BOTTOM_BAR_SPACE.dp, start = 12.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // ---- Active spell panel ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(TOP_BOX_HEIGHT)
                .mwPanel()
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (sel != null) {
                val selName = state.spells.find { it.id == sel }?.displayName() ?: prettify(sel)

                Column {
                    Text(
                        "Active Spell",
                        color = BronzeLight, fontSize = 10.sp,
                        fontFamily = MwDisplay, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        selName,
                        color = Bone, fontSize = 18.sp,
                        fontFamily = MwBody, fontWeight = FontWeight.SemiBold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Text(
                    "No spell selected",
                    color = BoneDim, fontSize = 14.sp,
                    fontFamily = MwBody
                )
            }
        }

        // ---- Spell list panel ----
        if (state.spells.isEmpty()) {
            EmptyPanel("No spells known")
        } else {
            // Selected/equipped entry first, then the rest alphabetically.
            val bySelectionThenName = compareByDescending<SpellEntry> { it.id == sel }
                .thenBy { it.id.lowercase() }
            val powers = remember(state.spells, sel) {
                state.spells.filter { it.type == "power" }.sortedWith(bySelectionThenName)
            }
            val spells = remember(state.spells, sel) {
                state.spells.filter { it.type == "spell" }.sortedWith(bySelectionThenName)
            }
            val scrolls = remember(state.spells, sel) {
                state.spells.filter { it.type == "scroll" && !it.isItem }.sortedWith(bySelectionThenName)
            }
            // Cast-on-use enchanted items (rings/amulets/clothing/weapons). They
            // arrive with type "scroll" but carry isItem=true; broken out into
            // their own section.
            val enchantedItems = remember(state.spells, sel) {
                state.spells.filter { it.isItem }.sortedWith(bySelectionThenName)
            }

            // Category filter tabs — only present (non-empty) categories get a tab.
            // ALL is always shown. Mirrors InventoryPanel's CategorySubTabs pattern.
            var selectedMagicTab by remember { mutableStateOf("ALL") }
            val presentMagicTabs = remember(powers, spells, scrolls, enchantedItems) {
                buildList {
                    if (powers.isNotEmpty()) add("POWERS")
                    if (spells.isNotEmpty()) add("SPELLS")
                    if (scrolls.isNotEmpty()) add("SCROLLS")
                    if (enchantedItems.isNotEmpty()) add("ITEMS")
                }
            }
            // Reset to ALL if the selected category became empty (spells removed mid-game).
            LaunchedEffect(presentMagicTabs) {
                if (selectedMagicTab != "ALL" && selectedMagicTab !in presentMagicTabs) {
                    selectedMagicTab = "ALL"
                }
            }

            val showPowers = selectedMagicTab == "ALL" || selectedMagicTab == "POWERS"
            val showSpells = selectedMagicTab == "ALL" || selectedMagicTab == "SPELLS"
            val showScrolls = selectedMagicTab == "ALL" || selectedMagicTab == "SCROLLS"
            val showItems = selectedMagicTab == "ALL" || selectedMagicTab == "ITEMS"
            // Section headers only in the "ALL" view; a single-category tab is self-labelling.
            val showHeaders = selectedMagicTab == "ALL"

            // Tabs + list live in one mwPanel box, matching InventoryPanel.
            Column(Modifier.weight(1f).mwPanel()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoryTab(label = "ALL", active = selectedMagicTab == "ALL") {
                        selectedMagicTab = "ALL"
                    }
                    presentMagicTabs.forEach { label ->
                        CategoryTab(label = label, active = selectedMagicTab == label) {
                            selectedMagicTab = label
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark))
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 4.dp)) {
                    if (showPowers && powers.isNotEmpty()) {
                        if (showHeaders) {
                            item {
                                SpellSectionHeader("Powers")
                            }
                        }
                        items(powers) { spell ->
                            SpellRow(
                                spellId = spell.id,
                                title = spell.displayName(),
                                selected = spell.id == sel,
                                onInfo = { ItemInfoPopupState.open(spell.id, spell.name, null, isSpell = true) },
                                iconBitmap = rememberItemIcon(spell.icon)
                            ) { CompanionActions.selectSpell(spell.id) }
                        }
                    }
                    if (showSpells && spells.isNotEmpty()) {
                        if (showHeaders) {
                            item {
                                SpellSectionHeader("Spells")
                            }
                        }
                        items(spells) { spell ->
                            SpellRow(
                                spellId = spell.id,
                                title = spell.displayName(),
                                selected = spell.id == sel,
                                onInfo = { ItemInfoPopupState.open(spell.id, spell.name, null, isSpell = true) },
                                iconBitmap = rememberItemIcon(spell.icon)
                            ) { CompanionActions.selectSpell(spell.id) }
                        }
                    }
                    if (showScrolls && scrolls.isNotEmpty()) {
                        if (showHeaders) {
                            item {
                                SpellSectionHeader("Scrolls")
                            }
                        }
                        items(scrolls) { spell ->
                            SpellRow(
                                spellId = spell.id,
                                title = spell.displayName(),
                                selected = false,
                                onInfo = { ItemInfoPopupState.open(spell.id, spell.name, null, isSpell = true) },
                                iconBitmap = rememberItemIcon(spell.icon)
                            ) { CompanionActions.selectSpell(spell.id) }
                        }
                    }
                    if (showItems && enchantedItems.isNotEmpty()) {
                        if (showHeaders) {
                            item {
                                SpellSectionHeader("Enchanted Items")
                            }
                        }
                        items(enchantedItems) { spell ->
                            SpellRow(
                                spellId = spell.id,
                                title = spell.displayName(),
                                selected = spell.id == sel,
                                charge = spell.charge,
                                maxCharge = spell.maxCharge,
                                onInfo = { ItemInfoPopupState.open(spell.id, spell.name, null, isSpell = true) },
                                iconBitmap = rememberItemIcon(spell.icon)
                            ) { CompanionActions.selectSpell(spell.id) }
                        }
                    }
                    item { Spacer(Modifier.height(4.dp)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpellRow(
    spellId: String,
    title: String,
    selected: Boolean = false,
    charge: Int = 0,
    maxCharge: Int = 0,
    onInfo: (() -> Unit)? = null,
    iconBitmap: ImageBitmap? = null,
    onTap: () -> Unit
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(DropdownState.closeRequest) {
        if (DropdownState.closeRequest > 0) menuOpen = false
    }
    val favs by FavouritesRepository.state.collectAsState()
    val isFav = favs.magic.any { it?.id == spellId }
    Box(modifier = Modifier.onGloballyPositioned { ItemInfoPopupState.reportAnchor(spellId, it.boundsInRoot()) }) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onTap,
                        onLongClick = { menuOpen = true; DropdownState.open() }
                    )
                    .padding(start = 14.dp, end = 10.dp, top = 11.dp, bottom = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon box (leftmost). Placeholder empty slot until the icon
                // pipeline loads real textures; drop in a real bitmap by
                // passing iconBitmap non-null at the call site.
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(SlotBg)
                        .border(1.dp, BronzeDark, RoundedCornerShape(2.dp))
                ) {
                    if (iconBitmap != null) {
                        Image(
                            bitmap = iconBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Spacer(Modifier.width(10.dp))
                // Text colours mirror the inventory worn/unworn styling: the
                // equipped spell is the brighter BoneBright (like a worn item)
                // with an "EQUIPPED" tag, rather than a bronze/bold highlight.
                // Name + (optional) favourite star share the flex column so the
                // name truncates before the star; the star is a standalone Icon.
                Row(
                    modifier = Modifier.weight(1f).padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title, color = if (selected) BoneBright else BoneMuted,
                        fontSize = 14.sp, fontFamily = MwBody,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (isFav) {
                        Spacer(Modifier.width(4.dp))
                        FavStar()
                    }
                }
                // Charge column (enchanted items only) — current / capacity with
                // a thin bar, mirroring the inventory condition column.
                if (maxCharge > 0) {
                    val ratio = (charge.toFloat() / maxCharge).coerceIn(0f, 1f)
                    val fillColor = if (ratio >= 0.5f) BronzeLight else Color(0xFFC75C5C)
                    Column(
                        modifier = Modifier.width(56.dp).padding(end = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "$charge/$maxCharge",
                            color = BronzeLight, fontSize = 10.sp, fontFamily = MwData,
                            maxLines = 1, overflow = TextOverflow.Ellipsis
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(Color(0xFF0E0B07))
                                .border(1.dp, BronzeDark, RoundedCornerShape(1.dp))
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(ratio)
                                    .fillMaxHeight()
                                    .background(fillColor)
                            )
                        }
                    }
                }
                if (selected) {
                    Text(
                        "EQUIPPED",
                        color = BronzeLight,
                        fontSize = 10.sp,
                        fontFamily = MwDisplay,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.8.sp
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.4f)))
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false; DropdownState.closeAll() },
            properties = PopupProperties(focusable = false, dismissOnClickOutside = false),
            containerColor = StonePanel,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, Bronze),
            shape = RoundedCornerShape(3.dp)
        ) {
            Text(
                title,
                color = BronzeLight, fontSize = 12.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
            Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark))
            if (onInfo != null) {
                DropdownMenuItem(
                    text = { Text("Info", fontFamily = MwBody, fontSize = 13.sp) },
                    onClick = { menuOpen = false; DropdownState.closeAll(); onInfo() },
                    colors = MenuDefaults.itemColors(textColor = Bone)
                )
            }
            // Add to favourites / Unfavourite / replace-slot picker (magic slots).
            FavouriteMenuItems(
                context = context,
                isGear = false,
                itemId = spellId,
                makeSlot = { FavSlot(spellId, title) },
                onDone = { menuOpen = false; DropdownState.closeAll() }
            )
        }
    }
}

/* ---- Journal ---- */

// Persistent three-tab header for the Journal panel. Declared left→right so
// JournalTab.entries renders TOPICS | JOURNAL | QUESTS in order.
private enum class JournalTab { Topics, Journal, Quests }

private fun morrowindMonthName(month: Int): String = listOf(
    "Morning Star", "Sun's Dawn", "First Seed", "Rain's Hand",
    "Second Seed", "Midyear", "Sun's Height", "Last Seed",
    "Hearthfire", "Frostfall", "Sun's Dusk", "Evening Star"
).getOrElse(month - 1) { "Month $month" }

@Composable
private fun JournalPanel() {
    val state by GameStateRepository.state.collectAsState()
    val finishedQuestIds by GameStateRepository.finishedQuestIds.collectAsState()
    val topics by GameStateRepository.journalTopics.collectAsState()
    var selectedJournalTab by remember { mutableStateOf(JournalTab.Journal) }
    // Sub-navigation within the QUESTS tab: null = quest list, non-null = detail.
    var selectedQuestId by remember { mutableStateOf<String?>(null) }
    // Sub-navigation within the TOPICS tab: null = topic list, non-null = detail.
    var selectedTopic by remember { mutableStateOf<TopicInfo?>(null) }

    LaunchedEffect(Unit) {
        CompanionActions.refreshJournal()
        CompanionActions.refreshQuestStatus()
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(top = 12.dp, bottom = BOTTOM_BAR_SPACE.dp, start = 12.dp, end = 12.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            JournalTabBar(selected = selectedJournalTab, onSelect = { selectedJournalTab = it })
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxSize()) {
                when (selectedJournalTab) {
                    JournalTab.Topics -> {
                        val sel = selectedTopic
                        if (sel == null)
                            JournalTopicsList(topics = topics, onTopic = { selectedTopic = it })
                        else
                            JournalTopicDetail(topic = sel, onBack = { selectedTopic = null })
                    }
                    JournalTab.Journal ->
                        if (state.journalEntries.isEmpty()) JournalEmptyState()
                        else JournalChronological(state.journalEntries)
                    JournalTab.Quests -> {
                        val qid = selectedQuestId
                        if (qid == null)
                            JournalQuestList(
                                entries = state.journalEntries,
                                finishedQuestIds = finishedQuestIds,
                                onQuest = { selectedQuestId = it }
                            )
                        else
                            JournalQuestDetail(
                                questId = qid,
                                entries = state.journalEntries.filter { it.questId == qid },
                                onBack = { selectedQuestId = null }
                            )
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalTabBar(selected: JournalTab, onSelect: (JournalTab) -> Unit) {
    Column(Modifier.fillMaxWidth().background(StonePanel)) {
        // Top edge, matching mwPanel()'s Bronze frame.
        Box(Modifier.fillMaxWidth().height(1.dp).background(Bronze))
        Row(Modifier.fillMaxWidth().height(36.dp)) {
            JournalTab.entries.forEach { tab ->
                val active = tab == selected
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable { onSelect(tab) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        tab.name.uppercase(),
                        color = if (active) BronzeLight else BoneDim,
                        fontSize = 12.sp, fontFamily = MwBody, letterSpacing = 1.5.sp
                    )
                    if (active) {
                        Box(
                            Modifier.align(Alignment.BottomCenter)
                                .fillMaxWidth().height(2.dp).background(BronzeDark)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalEmptyState() {
    Column(
        Modifier.fillMaxSize().mwPanel().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Journal", color = Bone, fontSize = 20.sp,
            fontFamily = MwDisplay, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text("No journal entries yet",
            color = BoneDim, fontSize = 13.sp, fontFamily = MwBody,
            textAlign = TextAlign.Center)
    }
}

@Composable
private fun JournalTopicsList(topics: List<TopicInfo>, onTopic: (TopicInfo) -> Unit) {
    // Ask the engine for the known-topic list whenever this view is (re)shown —
    // new topics may have been learned since the last visit. Native reply arrives
    // as the streamed COMPANION_TOPICS_* block. Mirrors JournalPanel's refreshJournal().
    LaunchedEffect(Unit) { CompanionActions.refreshTopics() }

    if (topics.isEmpty()) {
        Box(Modifier.fillMaxSize().mwPanel(), contentAlignment = Alignment.Center) {
            Text("Loading topics…", color = BoneDim, fontSize = 15.sp, fontFamily = MwBody)
        }
        return
    }

    // Group by uppercased first letter (non-letter initials → "#"), keys sorted
    // with "#" first. Native emits topics already alphabetical, so groupBy keeps
    // each letter's topics in that order.
    val grouped = remember(topics) {
        topics.groupBy { t ->
            val c = t.name.trim().firstOrNull()
            if (c != null && c.isLetter()) c.uppercaseChar().toString() else "#"
        }.toSortedMap(compareBy { if (it == "#") "" else it })
    }

    Column(Modifier.fillMaxSize().mwPanel()) {
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
            grouped.forEach { (letter, list) ->
                item(key = "hdr_$letter") { TopicLetterHeader(letter) }
                // No item key: topic display names are usually but not guaranteed
                // unique (keyed by RefId engine-side), and duplicate keys crash LazyColumn.
                items(list) { topic ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onTopic(topic) }
                            .padding(horizontal = 10.dp, vertical = 11.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(topic.name, color = Bone, fontSize = 14.sp, fontFamily = MwBody,
                            modifier = Modifier.weight(1f))
                        Text("▶", color = BronzeLight, fontSize = 13.sp, fontFamily = MwBody)
                    }
                    Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.4f)))
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// Single-letter alphabet divider for the topics list — SpellSectionHeader style,
// bumped to 16sp for the letter.
@Composable
private fun TopicLetterHeader(letter: String) {
    Column {
        Text(
            letter,
            color = BronzeLight, fontSize = 16.sp,
            fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 8.dp, bottom = 4.dp)
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark))
    }
}

@Composable
private fun JournalTopicDetail(topic: TopicInfo, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().mwPanel()) {
        // Back nav (matches the journal back-navigation style).
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("◀ Topics", color = BronzeLight, fontSize = 14.sp, fontFamily = MwBody,
                modifier = Modifier.clickable { onBack() }.padding(4.dp))
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark))
        // Topic name header.
        Text(topic.name, color = BronzeLight, fontSize = 16.sp, fontFamily = MwDisplay,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 6.dp))
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
            itemsIndexed(topic.entries) { idx, entry ->
                if (entry.actorName.isNotEmpty()) {
                    Text(entry.actorName, color = BronzeLight, fontSize = 12.sp,
                        fontFamily = MwBody, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 2.dp))
                }
                Text(entry.text, color = Bone, fontSize = 14.sp, fontFamily = MwBody,
                    lineHeight = 22.4.sp,
                    modifier = Modifier.padding(
                        start = 10.dp, end = 10.dp,
                        top = if (entry.actorName.isEmpty()) 10.dp else 0.dp, bottom = 8.dp))
                if (idx < topic.entries.lastIndex)
                    Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.4f)))
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun JournalNavBar(left: String?, onLeft: (() -> Unit)?, center: String, right: String?, onRight: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (left != null && onLeft != null)
            Text("◀ $left", color = BronzeLight, fontSize = 14.sp, fontFamily = MwBody,
                modifier = Modifier.clickable { onLeft() }.padding(4.dp))
        else
            Spacer(Modifier.size(1.dp))
        Text(center, color = Bone, fontSize = 16.sp, fontFamily = MwDisplay, fontWeight = FontWeight.SemiBold)
        if (right != null && onRight != null)
            Text("$right ▶", color = BronzeLight, fontSize = 14.sp, fontFamily = MwBody,
                modifier = Modifier.clickable { onRight() }.padding(4.dp))
        else
            Spacer(Modifier.size(1.dp))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark))
}

@Composable
private fun JournalChronological(entries: List<JournalEntry>) {
    // Each day fills one column. Two days per page (left = older, right = newer),
    // like an open book where each physical page holds one day.
    val pages = remember(entries) {
        val byDay = LinkedHashMap<String, MutableList<JournalEntry>>()
        entries.forEach { e ->
            val date = "${e.dayOfMonth} ${morrowindMonthName(e.month)}"
            byDay.getOrPut(date) { mutableListOf() }.add(e)
        }
        val dayCols = byDay.entries.map { (date, dayEntries) ->
            buildList<Pair<String?, JournalEntry?>> {
                add(date to null)
                dayEntries.forEach { add(null to it) }
            }
        }
        dayCols.chunked(2)  // pair days: [leftDay, rightDay]
    }
    val pagerState = rememberPagerState(
        initialPage = (pages.size - 1).coerceAtLeast(0),
        pageCount = { pages.size }
    )

    Column(Modifier.fillMaxSize().mwPanel()) {
        HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { pageIdx ->
            val pageDays = pages.getOrElse(pageIdx) { emptyList() }
            Row(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 4.dp)) {
                JournalColumn(pageDays.getOrElse(0) { emptyList() }, Modifier.weight(1f))
                Box(Modifier.width(1.dp).fillMaxHeight().background(BronzeDark))
                JournalColumn(pageDays.getOrElse(1) { emptyList() }, Modifier.weight(1f))
            }
        }

        if (pages.size > 1) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(pages.size) { idx ->
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (idx == pagerState.currentPage) 7.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (idx == pagerState.currentPage) BronzeLight else BronzeDark)
                    )
                }
            }
        }
    }
}

@Composable
private fun JournalColumn(items: List<Pair<String?, JournalEntry?>>, modifier: Modifier) {
    // verticalScroll lets long entries overflow cleanly rather than clip.
    Column(modifier.padding(horizontal = 6.dp).verticalScroll(rememberScrollState())) {
        items.forEach { (date, entry) ->
            if (date != null) {
                Text(date, color = BronzeLight, fontSize = 12.sp, fontFamily = MwDisplay,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 1.dp))
            } else if (entry != null) {
                Text(entry.text, color = Bone, fontSize = 14.sp, fontFamily = MwBody,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(bottom = 4.dp))
            }
        }
    }
}

@Composable
private fun JournalQuestList(
    entries: List<JournalEntry>,
    finishedQuestIds: Set<String>,
    onQuest: (String) -> Unit
) {
    // Off = active quests only (default); on = also show finished quests.
    var showAll by remember { mutableStateOf(false) }

    // Most-recently-active quest first. Keep one representative entry per quest to resolve the display name.
    val quests = remember(entries) {
        val seen = LinkedHashMap<String, JournalEntry>()
        entries.reversed().forEach { e -> seen.putIfAbsent(e.questId, e) }
        seen.values.toList()
    }
    val visible = remember(quests, finishedQuestIds, showAll) {
        if (showAll) quests else quests.filter { it.questId !in finishedQuestIds }
    }

    Column(Modifier.fillMaxSize().mwPanel()) {
        // Toggle button: label reflects the mode it switches TO.
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                if (showAll) "Active Only" else "Show All",
                color = BronzeLight, fontSize = 12.sp, fontFamily = MwBody,
                modifier = Modifier.clickable { showAll = !showAll }.padding(4.dp)
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark))
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
            items(visible, key = { it.questId }) { rep ->
                val finished = rep.questId in finishedQuestIds
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onQuest(rep.questId) }
                        .padding(horizontal = 10.dp, vertical = 11.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(questDisplayName(rep), color = if (finished) BoneDim else Bone,
                        fontSize = 14.sp, fontFamily = MwBody, modifier = Modifier.weight(1f))
                    Text("▶", color = BronzeLight, fontSize = 13.sp, fontFamily = MwBody)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.4f)))
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun JournalQuestDetail(questId: String, entries: List<JournalEntry>, onBack: () -> Unit) {
    val title = entries.firstOrNull()?.let { questDisplayName(it) } ?: prettifyQuestId(questId)
    Column(Modifier.fillMaxSize().mwPanel()) {
        JournalNavBar(left = "Quests", onLeft = onBack, center = title, right = null, onRight = null)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
            items(entries, key = { e -> "e:${e.day}:${e.dayOfMonth}:${e.text.length}" }) { entry ->
                val date = "${entry.dayOfMonth} ${morrowindMonthName(entry.month)}"
                Text(date, color = BronzeLight, fontSize = 12.sp, fontFamily = MwDisplay,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 2.dp))
                Text(entry.text, color = Bone, fontSize = 14.sp, fontFamily = MwBody,
                    lineHeight = 20.sp,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 6.dp))
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

private fun questDisplayName(entry: JournalEntry): String {
    val raw = entry.questName
    return when {
        raw.isEmpty() -> prettifyQuestId(entry.questId)
        raw.contains(' ') -> raw.split(' ')
            .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
        else -> prettifyRawQuestName(raw)
    }
}

// Processes ESM CamelCase names like "A1_1_FindSpymaster" → "Find Spymaster".
// Strips short code prefixes (BM, MS, A1, …) and pure-number segments, then
// splits CamelCase boundaries.
private fun prettifyRawQuestName(raw: String): String {
    val parts = raw.split('_')
    val meaningful = parts.filter { seg ->
        seg.isNotEmpty() &&
        !seg.all { it.isDigit() } &&
        !(seg.length <= 3 && seg.all { it.isUpperCase() || it.isDigit() })
    }
    val toProcess = meaningful.ifEmpty { parts.takeLast(1) }
    return toProcess.flatMap { seg ->
        seg.replace(Regex("([a-z])([A-Z])"), "$1 $2")
           .replace(Regex("([A-Z]{2,})([A-Z][a-z])"), "$1 $2")
           .split(' ')
    }.filter { it.isNotEmpty() }
     .joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
}

private fun prettifyQuestId(id: String): String {
    val parts = id.split(Regex("[\\s_]+")).filter { it.isNotEmpty() }
    val body = parts.dropWhile { it.length <= 3 || it.all { c -> c.isDigit() } }.ifEmpty { parts }
    return body.joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
}

@Composable
private fun EmptyPanel(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = BoneDim, fontSize = 15.sp, fontFamily = MwBody)
    }
}

/* ---- Stats ---- */

/**
 * What a tapped stat row opens a popup for. Each variant carries the data it
 * needs; [toContent] renders it into the shared [StatInfoPopup] layout.
 */
private sealed interface StatInfo {
    data class AttributeInfo(val attr: AttributeStat) : StatInfo
    data class SkillInfo(val skill: SkillStat) : StatInfo
    data class DynamicInfo(val label: String, val value: Dynamic, val desc: String) : StatInfo
    data class LevelInfo(val level: Int, val progress: Int, val total: Int) : StatInfo
    data class RaceInfo(val character: CharacterInfo) : StatInfo
    data class BirthSignInfo(
        val name: String, val desc: String, val spells: List<String>, val texture: String
    ) : StatInfo
    data class ClassInfo(val character: CharacterInfo) : StatInfo
    data class FactionInfo(val faction: FactionMembership) : StatInfo
    data class ReputationInfo(val value: Int) : StatInfo
    data class BountyInfo(val value: Int) : StatInfo
    data class ActiveEffectInfo(val effect: ActiveEffect) : StatInfo
}

/** Uniform, render-ready popup contents produced from any [StatInfo]. */
private data class StatPopupContent(
    val title: String,
    val rows: List<Pair<String, String>>,
    val sections: List<Pair<String, List<String>>>,
    val description: String,
    /** VFS icon path for the header, "" when the popup has no icon. */
    val icon: String = "",
    /** Optional large body image (VFS path) shown below the title; "" = none. */
    val bodyImage: String = "",
    /** Optional progress bar (skill increase / level-up progress). */
    val progress: StatProgress? = null,
    /** Header title color; defaults to the usual BronzeLight. */
    val titleColor: Color = BronzeLight,
    /** Optional per-row value colors, aligned by index with [rows]; null/absent = Bone. */
    val rowValueColors: List<Color?> = emptyList()
)

/** A labelled 0..1 progress bar with a caption line beneath it. */
private data class StatProgress(val label: String, val ratio: Float, val caption: String)

private fun capWord(s: String): String =
    if (s.isBlank()) s else s.replaceFirstChar { it.uppercase() }

private fun StatInfo.toContent(): StatPopupContent = when (this) {
    is StatInfo.AttributeInfo -> StatPopupContent(
        title = attr.name.ifBlank { attr.id },
        rows = listOf(
            "Current" to attr.current.toInt().toString(),
            "Base" to attr.base.toInt().toString()
        ),
        sections = if (attr.governedSkills.isNotEmpty())
            listOf("Governed Skills" to attr.governedSkills) else emptyList(),
        description = attr.desc,
        icon = attr.icon
    )
    is StatInfo.SkillInfo -> StatPopupContent(
        title = skill.name.ifBlank { skill.id },
        rows = listOf(
            "Value" to skill.value.toInt().toString(),
            "Category" to capWord(skill.category),
            "Governing Attribute" to skill.governingAttribute,
            "Specialization" to skill.specialization
        ).filter { it.second.isNotBlank() },
        sections = emptyList(),
        description = skill.desc,
        icon = skill.icon,
        // Per-skill progress toward the next skill increase ([0-1] from the
        // engine's SkillStat.progress). Caption shows the rounded percentage.
        progress = skill.progress.coerceIn(0f, 1f).let { r ->
            StatProgress("Progress", r, "${(r * 100).toInt()}% to next increase")
        }
    )
    is StatInfo.DynamicInfo -> StatPopupContent(
        title = label,
        rows = listOf(
            "Current" to value.current.toInt().toString(),
            "Maximum" to value.max.toInt().toString()
        ),
        sections = emptyList(),
        description = desc
    )
    is StatInfo.LevelInfo -> StatPopupContent(
        title = "Level $level",
        rows = buildList {
            add("Level" to level.toString())
            if (total > 0) add("Progress to Next" to "$progress / $total")
        },
        sections = emptyList(),
        description = "",
        progress = if (total > 0)
            StatProgress(
                "Level Progress",
                (progress.toFloat() / total).coerceIn(0f, 1f),
                "$progress / $total increases"
            ) else null
    )
    is StatInfo.RaceInfo -> StatPopupContent(
        title = character.race.ifBlank { "Race" },
        rows = emptyList(),
        sections = buildList {
            if (character.raceSkillBonuses.isNotEmpty())
                add("Skill Bonuses" to character.raceSkillBonuses)
            if (character.raceAbilities.isNotEmpty())
                add("Abilities" to character.raceAbilities)
        },
        description = character.raceDesc
    )
    is StatInfo.BirthSignInfo -> StatPopupContent(
        title = name.ifBlank { "Birthsign" },
        rows = emptyList(),
        sections = if (spells.isNotEmpty()) listOf("Abilities" to spells) else emptyList(),
        description = desc,
        bodyImage = texture
    )
    is StatInfo.ClassInfo -> StatPopupContent(
        title = character.className.ifBlank { "Class" },
        rows = if (character.classSpecialization.isNotBlank())
            listOf("Specialization" to character.classSpecialization) else emptyList(),
        sections = buildList {
            if (character.classFavoredAttributes.isNotEmpty())
                add("Favored Attributes" to character.classFavoredAttributes)
            if (character.classMajorSkills.isNotEmpty())
                add("Major Skills" to character.classMajorSkills)
            if (character.classMinorSkills.isNotEmpty())
                add("Minor Skills" to character.classMinorSkills)
        },
        description = character.classDesc
    )
    is StatInfo.FactionInfo -> StatPopupContent(
        title = faction.name.ifBlank { faction.id },
        rows = buildList {
            if (faction.rankName.isNotBlank()) add("Rank" to faction.rankName)
            if (faction.rank > 0) add("Rank Number" to faction.rank.toString())
        },
        sections = emptyList(),
        description = ""
    )
    is StatInfo.ReputationInfo -> StatPopupContent(
        title = "Reputation",
        rows = listOf("Reputation" to value.toString()),
        sections = emptyList(),
        description = "Reputation reflects your renown across Vvardenfell. Higher " +
            "reputation improves NPC disposition toward you, which in turn lowers " +
            "merchant prices and makes people more willing to help."
    )
    is StatInfo.BountyInfo -> StatPopupContent(
        title = "Bounty",
        rows = listOf("Bounty" to value.toString()),
        sections = emptyList(),
        description = "Your bounty is the total fine for crimes witnessed by others. " +
            "While it is above zero, guards will demand you pay the fine, go to jail, " +
            "or resist arrest — clearing it restores your standing with the law.",
        // Match the row's red-when-wanted styling in the popup header.
        titleColor = if (value > 0) Color(0xFFC75C5C) else BronzeLight
    )
    is StatInfo.ActiveEffectInfo -> StatPopupContent(
        // No description is exported for effects — just name, type, source, magnitude, icon.
        title = effect.name,
        rows = buildList {
            add("Type" to if (effect.harmful) "Harmful" else "Beneficial")
            if (effect.source.isNotBlank()) add("Source" to effect.source)
            if (effect.magnitude > 0) add("Magnitude" to "${effect.magnitude} pts")
        },
        sections = emptyList(),
        description = "",
        icon = effect.icon,
        // Colour only the Type value (index 0): red when harmful, dim when beneficial.
        rowValueColors = listOf(if (effect.harmful) Color(0xFFC75C5C) else BoneDim)
    )
}

/**
 * Vanilla-style effect subtitle: "<source>: <mag> pts", or just the source / just
 * the magnitude when only one is available; "" when neither is.
 */
private fun effectSubtitle(e: ActiveEffect): String = when {
    e.source.isNotBlank() && e.magnitude > 0 -> "${e.source}: ${e.magnitude} pts"
    e.source.isNotBlank() -> e.source
    e.magnitude > 0 -> "${e.magnitude} pts"
    else -> ""
}

/**
 * Shared stat detail popup. In-window overlay (NOT a Dialog — the companion UI
 * lives in a Presentation on a secondary display where Dialog throws a window
 * type mismatch), mirroring ItemInfoOverlay: full-screen scrim dismisses on tap,
 * the panel swallows its own taps.
 */
@Composable
private fun StatInfoPopup(info: StatInfo, onDismiss: () -> Unit) {
    val content = info.toContent()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(20f)
            .background(Color(0xB3000000))
            .pointerInput(Unit) { detectTapGestures(onTap = { onDismiss() }) },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(420.dp)
                .heightIn(max = 560.dp)
                .mwPanel()
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (content.icon.isNotBlank()) {
                    val headerIcon = rememberItemIcon(content.icon)
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(SlotBg)
                            .border(1.dp, BronzeDark, RoundedCornerShape(2.dp))
                    ) {
                        if (headerIcon != null) {
                            Image(
                                bitmap = headerIcon,
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    content.title,
                    color = content.titleColor,
                    fontSize = 16.sp,
                    fontFamily = MwDisplay,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(10.dp))

            // Optional large portrait art below the title (e.g. the birthsign
            // sign image), framed like the app's other art slots. Loaded through
            // the same rememberItemIcon pipeline as icons — the path is a uniform
            // VFS texture path. Only rendered once the bitmap resolves.
            if (content.bodyImage.isNotBlank()) {
                val bodyBitmap = rememberItemIcon(content.bodyImage)
                if (bodyBitmap != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(SlotBg)
                            .border(1.dp, BronzeDark, RoundedCornerShape(2.dp))
                            .padding(6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            bitmap = bodyBitmap,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            content.rows.forEachIndexed { i, (label, value) ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.5f)))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = BoneDim, fontSize = 12.sp, fontFamily = MwBody)
                    Text(
                        value,
                        color = content.rowValueColors.getOrNull(i) ?: Bone,
                        fontSize = 12.sp,
                        fontFamily = MwData,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            content.sections.forEach { (heading, items) ->
                Spacer(Modifier.height(10.dp))
                Text(
                    heading.uppercase(),
                    color = BronzeLight,
                    fontSize = 11.sp,
                    fontFamily = MwDisplay,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(4.dp))
                items.forEach { item ->
                    Text(
                        item,
                        color = Bone,
                        fontSize = 12.sp,
                        fontFamily = MwBody,
                        modifier = Modifier.padding(vertical = 3.dp)
                    )
                }
            }

            content.progress?.let { p ->
                Spacer(Modifier.height(14.dp))
                Text(
                    p.label.uppercase(),
                    color = BoneDim,
                    fontSize = 11.sp,
                    fontFamily = MwBody,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(5.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(1.dp))
                        .background(Color(0xFF0E0B07))
                        .border(1.dp, BronzeDark, RoundedCornerShape(1.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(p.ratio)
                            .height(6.dp)
                            .clip(RoundedCornerShape(1.dp))
                            .background(BronzeLight)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(p.caption, color = BoneDim, fontSize = 11.sp, fontFamily = MwBody)
            }

            if (content.description.isNotBlank()) {
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.5f)))
                Spacer(Modifier.height(10.dp))
                Text(
                    content.description,
                    color = Bone,
                    fontSize = 13.sp,
                    fontFamily = MwBody,
                    lineHeight = 18.sp
                )
            }
        }
    }
}

private val STATS_LEFT_WIDTH = 165.dp

@Composable
private fun StatsPanel(state: GameState, onSelectStat: (StatInfo) -> Unit) {
    val character = state.character

    Column(
        Modifier
            .fillMaxSize()
            .padding(top = 12.dp, bottom = BOTTOM_BAR_SPACE.dp, start = 12.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CharacterHeaderPanel(character, onSelectStat)

        Row(
            Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AttributesAndEffectsPanel(
                state = state,
                modifier = Modifier.width(STATS_LEFT_WIDTH).fillMaxHeight(),
                onSelectStat = onSelectStat
            )
            SkillsPanel(
                skills = character.skills,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onSelectStat = onSelectStat
            )
        }
    }
}

@Composable
private fun CharacterHeaderPanel(character: CharacterInfo, onSelectStat: (StatInfo) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .mwPanel()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                character.name.ifBlank { "—" },
                color = Bone, fontSize = 18.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.SemiBold
            )
            // Race · Class · Birthsign — all three are tappable for their popups.
            val hasSubtitle = listOf(character.race, character.className, character.birthSign)
                .any { it.isNotBlank() }
            if (hasSubtitle) {
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (character.race.isNotBlank()) {
                        Text(
                            character.race, color = BoneDim, fontSize = 12.sp, fontFamily = MwBody,
                            modifier = Modifier.clickable { onSelectStat(StatInfo.RaceInfo(character)) }
                        )
                    }
                    if (character.className.isNotBlank()) {
                        if (character.race.isNotBlank())
                            Text(" · ", color = BoneDim, fontSize = 12.sp, fontFamily = MwBody)
                        Text(
                            character.className, color = BoneDim, fontSize = 12.sp, fontFamily = MwBody,
                            modifier = Modifier.clickable { onSelectStat(StatInfo.ClassInfo(character)) }
                        )
                    }
                    if (character.birthSign.isNotBlank()) {
                        if (character.race.isNotBlank() || character.className.isNotBlank())
                            Text(" · ", color = BoneDim, fontSize = 12.sp, fontFamily = MwBody)
                        Text(
                            character.birthSign, color = BoneDim, fontSize = 12.sp, fontFamily = MwBody,
                            modifier = Modifier.clickable {
                                onSelectStat(
                                    StatInfo.BirthSignInfo(
                                        character.birthSign,
                                        character.birthSignDesc,
                                        character.birthSignSpells,
                                        character.birthSignTexture
                                    )
                                )
                            }
                        )
                    }
                }
            }
        }
        Column(
            modifier = Modifier
                .border(1.dp, BronzeDark, RoundedCornerShape(2.dp))
                .clickable {
                    onSelectStat(
                        StatInfo.LevelInfo(character.level, character.levelProgress, character.levelTotal)
                    )
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "${character.level}",
                color = BronzeLight, fontSize = 20.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.Bold
            )
            Text(
                "LEVEL",
                color = BoneDim, fontSize = 9.sp, fontFamily = MwDisplay,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun AttributesAndEffectsPanel(
    state: GameState,
    modifier: Modifier = Modifier,
    onSelectStat: (StatInfo) -> Unit
) {
    val character = state.character
    Column(
        modifier.mwPanel().padding(10.dp).verticalScroll(rememberScrollState())
    ) {
        SpellSectionHeader("Vitals")
        Column(Modifier.padding(top = 4.dp)) {
            VitalRow("Health", state.health, HealthCol) {
                onSelectStat(StatInfo.DynamicInfo("Health", state.health, character.healthDesc))
            }
            VitalRow("Magicka", state.magicka, MagickaCol) {
                onSelectStat(StatInfo.DynamicInfo("Magicka", state.magicka, character.magickaDesc))
            }
            VitalRow("Fatigue", state.fatigue, FatigueCol) {
                onSelectStat(StatInfo.DynamicInfo("Fatigue", state.fatigue, character.fatigueDesc))
            }
        }

        Spacer(Modifier.height(4.dp))
        SpellSectionHeader("Attributes")
        Column(Modifier.padding(top = 4.dp)) {
            character.attributes.forEach { attr ->
                AttributeRow(attr) { onSelectStat(StatInfo.AttributeInfo(attr)) }
            }
        }

        Spacer(Modifier.height(4.dp))
        SpellSectionHeader("Standing")
        Column(Modifier.padding(top = 4.dp)) {
            StandingRow("Reputation", character.reputation.toString(), BronzeLight) {
                onSelectStat(StatInfo.ReputationInfo(character.reputation))
            }
            // Bounty in red when the player is wanted, dim when clean.
            StandingRow(
                "Bounty", character.bounty.toString(),
                if (character.bounty > 0) Color(0xFFC75C5C) else BoneDim
            ) {
                onSelectStat(StatInfo.BountyInfo(character.bounty))
            }
        }

        Spacer(Modifier.height(4.dp))
        SpellSectionHeader("Factions")
        if (character.factions.isEmpty()) {
            Text(
                "None",
                color = BoneDim, fontSize = 12.sp, fontFamily = MwBody,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp)
            )
        } else {
            Column(Modifier.padding(top = 4.dp)) {
                character.factions.forEach { faction ->
                    FactionRow(faction) { onSelectStat(StatInfo.FactionInfo(faction)) }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        SpellSectionHeader("Active Effects")
        if (state.activeEffects.isEmpty()) {
            Text(
                "No active effects",
                color = BoneDim, fontSize = 12.sp, fontFamily = MwBody,
                fontStyle = FontStyle.Italic,
                modifier = Modifier.padding(top = 6.dp, start = 2.dp)
            )
        } else {
            Column(Modifier.padding(top = 4.dp)) {
                state.activeEffects.forEach { effect ->
                    ActiveEffectRow(effect, onTap = { onSelectStat(StatInfo.ActiveEffectInfo(effect)) })
                }
            }
        }
    }
}

@Composable
private fun StandingRow(label: String, value: String, valueColor: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Bone, fontSize = 13.sp, fontFamily = MwBody)
        Text(
            value,
            color = valueColor, fontSize = 13.sp,
            fontFamily = MwData, fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun FactionRow(faction: FactionMembership, onClick: () -> Unit) {
    // Rank title is intentionally omitted here — it's revealed in the tap popup.
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            faction.name.ifBlank { faction.id },
            color = Bone, fontSize = 13.sp, fontFamily = MwBody,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun VitalRow(label: String, value: Dynamic, barColor: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = Bone, fontSize = 13.sp, fontFamily = MwBody)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .padding(end = 6.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(barColor)
            )
            Text(
                "${value.current.toInt()}",
                color = BronzeLight, fontSize = 13.sp,
                fontFamily = MwData, fontWeight = FontWeight.Bold
            )
            Text(
                " / ${value.max.toInt()}",
                color = BoneDim, fontSize = 13.sp, fontFamily = MwData
            )
        }
    }
}

@Composable
private fun AttributeRow(attr: AttributeStat, onClick: () -> Unit) {
    val diff = attr.current - attr.base
    val currentColor = when {
        diff > 0.01f -> Color(0xFF7FBF7F)
        diff < -0.01f -> Color(0xFFC75C5C)
        else -> BronzeLight
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            attr.name.ifBlank { attr.id },
            color = Bone, fontSize = 13.sp, fontFamily = MwBody
        )
        Row {
            Text(
                "${attr.current.toInt()}",
                color = currentColor, fontSize = 13.sp,
                fontFamily = MwData, fontWeight = FontWeight.Bold
            )
            Text(
                " / ${attr.base.toInt()}",
                color = BoneDim, fontSize = 13.sp, fontFamily = MwData
            )
        }
    }
}

@Composable
private fun ActiveEffectRow(effect: ActiveEffect, onTap: (() -> Unit)? = null) {
    // Vanilla layout: effect icon, then the effect name, with "<source>: <mag> pts"
    // beneath it (e.g. "Fortify Attack" / "Warwyrd: 10 pts").
    val icon = rememberItemIcon(effect.icon)
    val subtitle = effectSubtitle(effect)
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onTap != null) it.clickable(onClick = onTap) else it }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(SlotBg)
                .border(1.dp, BronzeDark, RoundedCornerShape(2.dp))
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        EffectDot(effect.harmful, 8.dp)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(
                effect.name,
                color = Bone, fontSize = 12.sp, fontFamily = MwBody,
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    color = BoneDim, fontSize = 10.sp, fontFamily = MwData,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SkillsPanel(
    skills: List<SkillStat>,
    modifier: Modifier = Modifier,
    onSelectStat: (StatInfo) -> Unit
) {
    val major = remember(skills) { skills.filter { it.category == "major" } }
    val minor = remember(skills) { skills.filter { it.category == "minor" } }
    val misc = remember(skills) { skills.filter { it.category == "misc" } }

    Row(modifier.mwPanel().padding(10.dp)) {
        SkillColumn("Major", major, Modifier.weight(1f).fillMaxHeight(), onSelectStat)
        Box(Modifier.width(1.dp).fillMaxHeight().background(BronzeDark))
        SkillColumn("Minor", minor, Modifier.weight(1f).fillMaxHeight(), onSelectStat)
        Box(Modifier.width(1.dp).fillMaxHeight().background(BronzeDark))
        SkillColumn("Misc", misc, Modifier.weight(1f).fillMaxHeight(), onSelectStat)
    }
}

@Composable
private fun SkillColumn(
    label: String,
    skills: List<SkillStat>,
    modifier: Modifier = Modifier,
    onSelectStat: (StatInfo) -> Unit
) {
    Column(modifier.padding(horizontal = 8.dp)) {
        SpellSectionHeader(label)
        LazyColumn(Modifier.fillMaxSize()) {
            items(skills) { skill ->
                SkillRow(skill) { onSelectStat(StatInfo.SkillInfo(skill)) }
            }
        }
    }
}

@Composable
private fun SkillRow(skill: SkillStat, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            skill.name.ifBlank { skill.id },
            color = Bone, fontSize = 12.sp, fontFamily = MwBody,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            "${skill.value.toInt()}",
            color = BronzeLight, fontSize = 12.sp,
            fontFamily = MwData, fontWeight = FontWeight.Bold
        )
    }
}

/* ---- Bottom tab row ---- */

@Composable
private fun BottomTabBar(
    selected: Tab,
    onSelect: (Tab) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(FloatStone)
            .border(2.dp, Bronze, RoundedCornerShape(3.dp))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Tab.entries.forEach { t ->
            val isSel = t == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (isSel) Bronze else Color.Transparent)
                    .border(
                        1.dp,
                        if (isSel) BronzeLight else BronzeDark,
                        RoundedCornerShape(2.dp)
                    )
                    .clickable { onSelect(t) }
                    .padding(vertical = 13.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    t.label,
                    color = if (isSel) StoneDark else Bone,
                    fontSize = 14.sp,
                    fontFamily = MwDisplay,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/* ---- Helpers ---- */

/** Stopgap readability: "pick_journeyman_01" -> "Pick Journeyman 01".
 *  Real display names will come from a future export or lookup table. */
private fun prettify(id: String): String =
    id.replace('_', ' ')
        .split(' ')
        .joinToString(" ") { w ->
            if (w.isNotEmpty()) w.replaceFirstChar { it.uppercase() } else w
        }

/* ---- Options / display-settings menu (shown on the bottom screen while the in-game
 *      pause/options menu is open; hosted as a WindowManager panel by EngineActivity). ---- */

/** Warm near-black background for the full-screen options menu. */
private val OptionsBg = Color(0xFF1A1410)
/** Fill of the "active" pill in the two-option selectors. */
private val PillActiveBg = Color(0xFF3A2A10)

/** Remembers the options-menu scroll position across pause open/close cycles. The menu's host
 *  window is added/removed each time the pause menu opens, destroying any compose-scoped state, so
 *  this plain object (like [UiPreferences]/[GameStateRepository]) survives to restore the position. */
private object OptionsMenuScrollState {
    var index: Int = 0
    var offset: Int = 0
}

/**
 * The display-settings menu. A quick-set row ([All DS]/[All Vanilla]), then three sections:
 * SCREEN LAYOUT, GAME UI (per-element DS/Vanilla), VANILLA HUD (native top-screen HUD element
 * On/Off toggles), and INPUT. Full-screen, scrollable, BronzeLight-themed. Writes to
 * [UiPreferences] live on every tap (no save/cancel).
 *
 * Public because [org.openmw.EngineActivity] hosts it in a WindowManager panel window on
 * the companion Presentation when the pause/options menu opens.
 */
@Composable
fun OptionsMenuOverlay() {
    val context = LocalContext.current
    remember(context) { UiPreferences.init(context); true }

    // True while shown for the TITLE-screen main menu (no game loaded), vs the in-game pause menu.
    val onTitleScreen by GameStateRepository.titleMenuVisible.collectAsState()
    // On the title screen the settings live in a popup (opened by the Options button) below the
    // intro/info, keeping the welcome screen uncluttered. The in-game pause menu is UNCHANGED — it
    // shows the settings list inline.
    var showOptionsPopup by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(OptionsBg)) {
            OptionsTitleBar(onTitleScreen)
            if (onTitleScreen) {
                // Title screen: intro/info + an "Options" button that opens the settings in a popup.
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    OptionsWelcomeBlock()
                    Spacer(Modifier.height(14.dp))
                    OptionsOpenButton { showOptionsPopup = true }
                    Spacer(Modifier.height(24.dp))
                }
            } else {
                // In-game pause menu — settings list inline (unchanged behaviour).
                OptionsSettingsList()
            }
        }
        if (onTitleScreen && showOptionsPopup) {
            OptionsPopup(onDismiss = { showOptionsPopup = false })
        }
    }
}

/** The options overlay's title bar + divider. On the TITLE screen it's a large centred "OpenMW-DS"
 *  banner (no subtitle); the in-game pause menu keeps the compact app-identity bar (unchanged). */
@Composable
private fun OptionsTitleBar(titleScreen: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(StonePanel)
            .padding(horizontal = 16.dp, vertical = if (titleScreen) 26.dp else 12.dp),
        contentAlignment = if (titleScreen) Alignment.Center else Alignment.CenterStart
    ) {
        Column(horizontalAlignment = if (titleScreen) Alignment.CenterHorizontally else Alignment.Start) {
            Text(
                "OpenMW-DS",
                color = BronzeLight,
                fontSize = if (titleScreen) 46.sp else 18.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                letterSpacing = if (titleScreen) 3.sp else 1.sp
            )
            if (!titleScreen) {
                Text("Display settings", color = BoneDim, fontSize = 11.sp, fontFamily = MwBody)
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
}

/** The scrollable list of all settings sections. Rendered inline for the in-game pause menu and
 *  inside [OptionsPopup] for the title screen. Scroll position persists via OptionsMenuScrollState
 *  (the pause overlay window is recreated each open, so a plain rememberLazyListState would reset). */
@Composable
private fun OptionsSettingsList() {
    val listState = rememberLazyListState(
        OptionsMenuScrollState.index, OptionsMenuScrollState.offset
    )
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                OptionsMenuScrollState.index = index
                OptionsMenuScrollState.offset = offset
            }
    }
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
    ) {
        // Quick-set row: bulk-set every (non-pending) Game UI element. Never touches HUD.
        item { QuickSetRow() }

        // SCREEN LAYOUT: which screen each element is drawn on.
        item { OptionsSectionHeader("Screen Layout") }
        item { ConversationLocationRow() }
        item { LootingLocationRow() }
        item { BarteringLocationRow() }
        item { TargetHealthLocationRow() }
        item { PlayerCombatRow() }
        item { PersuasionLocationRow() }
        item { RepairLocationRow() }
        item { TravelLocationRow() }
        item { SpellBuyingLocationRow() }
        item { TrainingLocationRow() }
        items(
            GAME_UI_ELEMENTS.filter {
                it.key != "game_ui_conversation" &&
                    it.key != "game_ui_persuasion" &&
                    it.key != "game_ui_looting" &&
                    it.key != "game_ui_bartering" &&
                    it.key != "game_ui_spellbuying" &&
                    it.key != "game_ui_training" &&
                    it.key != "game_ui_repair" &&
                    it.key != "game_ui_travel"
            },
            key = { "layout_" + it.key }
        ) { ScreenLayoutPendingRow(it) }

        // GAME UI: per-element DS/Vanilla.
        item { OptionsSectionHeader("Game UI") }
        items(GAME_UI_ELEMENTS, key = { it.key }) { GameUiRow(it) }

        // VANILLA HUD: whether each native top-screen HUD element is shown.
        item { OptionsSectionHeader("Vanilla HUD") }
        items(HUD_ELEMENTS, key = { it.key }) { HudToggleRow(it) }
        item { Alpha3OverlayRow() }

        item { OptionsSectionHeader("Input") }
        item { TouchInputRow() }
        item { GameCursorRow() }
    }
}

/** The title-screen "Options" button that opens the settings popup. */
@Composable
private fun OptionsOpenButton(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(PillActiveBg.copy(alpha = 0.94f))
            .border(1.dp, BronzeLight, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "Options",
            color = BronzeLight, fontSize = 24.sp, fontFamily = MwDisplay,
            fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp
        )
    }
}

/** Title-screen settings popup — an in-window overlay (NOT a Compose Dialog; the options overlay
 *  lives in a Presentation panel window). Scrim tap or the Close button dismisses. */
@Composable
private fun OptionsPopup(onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(30f)
            .background(Color(0xCC000000))
            .pointerInput(Unit) { detectTapGestures { onDismiss() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(0.9f)
                .clip(RoundedCornerShape(4.dp))
                .background(OptionsBg)
                .border(2.dp, Bronze, RoundedCornerShape(4.dp))
                // Swallow taps so tapping the panel doesn't reach the dismiss scrim.
                .pointerInput(Unit) { detectTapGestures {} }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(StonePanel)
                    .padding(start = 12.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Prominent Back button (top-left) — returns to the title/intro screen.
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(PillActiveBg.copy(alpha = 0.94f))
                        .border(1.dp, BronzeLight, RoundedCornerShape(4.dp))
                        .clickable { onDismiss() }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        "◀ Back",
                        color = BronzeLight, fontSize = 15.sp, fontFamily = MwDisplay,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "Options",
                    color = BronzeLight, fontSize = 16.sp, fontFamily = MwDisplay,
                    fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)
                )
            }
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
            OptionsSettingsList()
        }
    }
}

/** One-time welcome shown at the top of the options overlay ONLY on the title screen (before a game
 *  is loaded), to orient first-time users and nudge them toward a starting preset. */
@Composable
private fun OptionsWelcomeBlock() {
    Column(Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)) {
        Text(
            "Welcome to OpenMW-DS",
            color = BronzeLight,
            fontSize = 26.sp,
            fontFamily = MwDisplay,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "An app designed for use with the AYN Thor. This bottom screen is your companion. It shows Morrowind's menus (inventory, magic, " +
                "map, journal and stats) with touch. Tap Options below to set your layout and input before you start.",
            color = Bone,
            fontSize = 18.sp,
            fontFamily = MwBody,
            lineHeight = 25.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "New here? Open Options and start with “All Vanilla” for the closest-to-original experience, " +
                    "then move individual pieces to DS as you like. " +
            "I also recommend enabling touch input (in the Input section at the bottom).\n" +
            "Want your old UI (health, minimap) back? See the Vanilla HUD section.",
            color = BoneDim,
            fontSize = 17.sp,
            fontFamily = MwBody,
            lineHeight = 24.sp
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "This app is a work in progress, some features are missing or greyed out.",
            color = BoneDim,
            fontSize = 17.sp,
            fontFamily = MwBody,
            lineHeight = 24.sp
        )
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
    }
}

/** The quick-set row: [All DS] [All Vanilla]. Bulk-sets every non-pending Game UI element and the
 *  controller-tooltips HUD toggle (DS -> Off, Vanilla -> On); other Vanilla HUD toggles are left
 *  untouched. Individual rows can be overridden afterwards. */
@Composable
private fun QuickSetRow() {
    val context = LocalContext.current
    // Aggregate state of every non-pending Game UI element, so the pills reflect the current mode:
    // all DS -> [All DS], all Vanilla -> [All Vanilla], mixed -> [Custom]. (Pending elements are
    // locked to Vanilla and excluded so they never force a permanent "mixed" reading.)
    val nonPending = remember { GAME_UI_ELEMENTS.filter { !it.pending } }
    val modes = nonPending.map { UiPreferences.gameUiModeFlow(it.key).collectAsState().value }
    val allDs = modes.isNotEmpty() && modes.all { it == GameUiMode.DS }
    val allVanilla = modes.isNotEmpty() && modes.all { it == GameUiMode.VANILLA }
    val mixed = !allDs && !allVanilla
    // Whether a saved Custom layout exists — [Custom] is tappable to restore it.
    val hasCustom by UiPreferences.customSnapshotFlow().collectAsState()

    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 6.dp)) {
        Text("Quick set", color = Bone, fontSize = 14.sp, fontFamily = MwBody)
        Text(
            "Set every Game UI row at once (also sets input mode + the controller hint bar)",
            color = BoneDim,
            fontSize = 10.sp,
            fontFamily = MwBody,
            modifier = Modifier.padding(top = 1.dp)
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionPill(Modifier.weight(1f), label = "All DS", active = allDs, enabled = true) {
                UiPreferences.setAllGameUi(context, GameUiMode.DS)
            }
            // "Custom" lights up when the rows are a mix of DS and Vanilla. It's tappable once a
            // custom layout has been saved (snapshotted when you hand-tweak into a mix, or before
            // switching to a preset), restoring that layout so you can bounce back to it.
            OptionPill(Modifier.weight(1f), label = "Custom", active = mixed, enabled = hasCustom) {
                UiPreferences.restoreCustomGameUi(context)
            }
            OptionPill(Modifier.weight(1f), label = "All Vanilla", active = allVanilla, enabled = true) {
                UiPreferences.setAllGameUi(context, GameUiMode.VANILLA)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
    }
}

/** A GAME UI row: element name (+ PENDING tag) over a [DS][Vanilla] pill selector. Pending elements
 *  are locked to Vanilla — their DS pill is disabled and labelled "Not yet available". Writes a
 *  GameUiMode to UiPreferences on every tap. */
@Composable
private fun GameUiRow(el: GameUiElement) {
    val context = LocalContext.current

    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                el.label,
                color = if (el.pending) BoneDim else Bone,
                fontSize = 14.sp,
                fontFamily = MwBody,
                modifier = Modifier.weight(1f)
            )
            if (el.pending) {
                Text(
                    "PENDING",
                    color = BoneDim.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontFamily = MwDisplay,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (el.pending) {
                // No companion replacement yet — DS disabled, Vanilla locked active.
                OptionPill(Modifier.weight(1f), label = "Not yet available", active = false, enabled = false) {}
                OptionPill(Modifier.weight(1f), label = "Vanilla", active = true, enabled = false) {}
            } else {
                val mode by UiPreferences.gameUiModeFlow(el.key).collectAsState()
                OptionPill(
                    Modifier.weight(1f),
                    label = "DS",
                    active = mode == GameUiMode.DS,
                    enabled = true
                ) { UiPreferences.setGameUiMode(context, el.key, GameUiMode.DS) }
                OptionPill(
                    Modifier.weight(1f),
                    label = "Vanilla",
                    active = mode == GameUiMode.VANILLA,
                    enabled = true
                ) { UiPreferences.setGameUiMode(context, el.key, GameUiMode.VANILLA) }
            }
        }
    }
}

/** The Conversation location row: a three-option [Bottom][Split][Top] pill selector.
 *  BOTTOM = original two-column layout; SPLIT = history top / topics bottom (default);
 *  TOP = full conversation on top (not yet implemented — selectable, behaves like SPLIT).
 *  Writes ConversationLocation to UiPreferences on every tap. Dimmed and inert when the
 *  Conversation Game UI element is Vanilla (native handles it, so there's no layout to pick). */
@Composable
private fun ConversationLocationRow() {
    val context = LocalContext.current
    val loc by UiPreferences.conversationLocationFlow().collectAsState()
    val convMode by UiPreferences.gameUiModeFlow("game_ui_conversation").collectAsState()
    val enabled = convMode == GameUiMode.DS

    // Dim the whole row and swallow taps (onClick guarded on `enabled`) when Conversation is
    // Vanilla; pills stay enabled=true so they don't double-dim under the column alpha.
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).padding(vertical = 9.dp)) {
        Text("Conversation", color = Bone, fontSize = 14.sp, fontFamily = MwBody)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionPill(
                Modifier.weight(1f),
                label = "Bottom",
                active = loc == ConversationLocation.BOTTOM,
                enabled = true
            ) { if (enabled) UiPreferences.setConversationLocation(context, ConversationLocation.BOTTOM) }
            OptionPill(
                Modifier.weight(1f),
                label = "Split",
                active = loc == ConversationLocation.SPLIT,
                enabled = true
            ) { if (enabled) UiPreferences.setConversationLocation(context, ConversationLocation.SPLIT) }
            OptionPill(
                Modifier.weight(1f),
                label = "Top",
                active = loc == ConversationLocation.TOP,
                enabled = true
            ) { if (enabled) UiPreferences.setConversationLocation(context, ConversationLocation.TOP) }
        }
    }
}

/** The Persuasion location row: a [Bottom][Top] selector (both implemented — the popup is hosted at
 *  the CompanionScreen root for Bottom, or a top-screen panel window for Top). Gated on the
 *  Persuasion Game UI element being DS; dimmed + inert when Vanilla (native handles persuasion). */
@Composable
private fun PersuasionLocationRow() {
    val context = LocalContext.current
    val loc by UiPreferences.persuasionLocationFlow().collectAsState()
    val mode by UiPreferences.gameUiModeFlow("game_ui_persuasion").collectAsState()
    val enabled = mode == GameUiMode.DS

    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).padding(vertical = 9.dp)) {
        Text("Persuasion", color = Bone, fontSize = 14.sp, fontFamily = MwBody)
        Text(
            "Which screen the persuasion popup opens on",
            color = BoneDim,
            fontSize = 10.sp,
            fontFamily = MwBody,
            modifier = Modifier.padding(top = 1.dp)
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionPill(
                Modifier.weight(1f),
                label = "Bottom",
                active = loc == PersuasionLocation.BOTTOM,
                enabled = true
            ) { if (enabled) UiPreferences.setPersuasionLocation(context, PersuasionLocation.BOTTOM) }
            OptionPill(
                Modifier.weight(1f),
                label = "Top",
                active = loc == PersuasionLocation.TOP,
                enabled = true
            ) { if (enabled) UiPreferences.setPersuasionLocation(context, PersuasionLocation.TOP) }
        }
    }
}

/** A service location row: a pill selector ending in a greyed, not-yet-implemented Top pill with a
 *  small "PENDING" caption above it. Looting / Bartering use the full [Bottom][Split][Top]
 *  ([showSplit] = true); Spell buying / Training use [Bottom][Top] ([showSplit] = false — only the
 *  centred bottom card is built, no Split, matching Repair). BOTTOM = classic bottom-screen overlay;
 *  SPLIT = icon grid on top, controls on the bottom; TOP = pending. Writes a [ScreenLocation] on
 *  every tap. Dimmed and inert when the element's Game UI mode is Vanilla (native handles it). */
@Composable
private fun ServiceLocationRow(
    label: String,
    gameUiKey: String,
    loc: ScreenLocation,
    showSplit: Boolean = true,
    onSelect: (ScreenLocation) -> Unit
) {
    val mode by UiPreferences.gameUiModeFlow(gameUiKey).collectAsState()
    val enabled = mode == GameUiMode.DS

    // Dim the whole row and swallow taps (onClick guarded on `enabled`) when the element is
    // Vanilla; the Bottom/Split pills stay enabled=true so they don't double-dim under the
    // column alpha. The Top pill is always enabled=false (pending → greyed).
    Column(Modifier.fillMaxWidth().alpha(if (enabled) 1f else 0.4f).padding(vertical = 9.dp)) {
        Text(label, color = Bone, fontSize = 14.sp, fontFamily = MwBody)
        Spacer(Modifier.height(6.dp))
        // "PENDING" caption aligned above the greyed Top pill (Top isn't implemented for any of these
        // rows yet). Same weight-1f column layout as the pills below, so it centres over the Top pill;
        // styled like the persuasion/level-up pending tag.
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Spacer(Modifier.weight(1f)) // over Bottom
            if (showSplit) Spacer(Modifier.weight(1f)) // over Split
            Text(
                "PENDING",
                color = BoneDim.copy(alpha = 0.7f),
                fontSize = 9.sp,
                fontFamily = MwDisplay,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionPill(
                Modifier.weight(1f),
                label = "Bottom",
                active = loc == ScreenLocation.BOTTOM,
                enabled = true
            ) { if (enabled) onSelect(ScreenLocation.BOTTOM) }
            if (showSplit) {
                OptionPill(
                    Modifier.weight(1f),
                    label = "Split",
                    active = loc == ScreenLocation.SPLIT,
                    enabled = true
                ) { if (enabled) onSelect(ScreenLocation.SPLIT) }
            }
            // TOP is not implemented yet — greyed and inert (see the PENDING caption above).
            OptionPill(
                Modifier.weight(1f),
                label = "Top",
                active = loc == ScreenLocation.TOP,
                enabled = false
            ) {}
        }
    }
}

/** The Looting location row (Bottom/Split, Top pending). Gated on the Looting Game UI element. */
@Composable
private fun LootingLocationRow() {
    val context = LocalContext.current
    val loc by UiPreferences.lootingLocationFlow().collectAsState()
    ServiceLocationRow("Looting", "game_ui_looting", loc) {
        UiPreferences.setLootingLocation(context, it)
    }
}

/** The Bartering location row (Bottom/Split, Top pending). Gated on the Bartering Game UI element. */
@Composable
private fun BarteringLocationRow() {
    val context = LocalContext.current
    val loc by UiPreferences.barterLocationFlow().collectAsState()
    ServiceLocationRow("Bartering", "game_ui_bartering", loc) {
        UiPreferences.setBarterLocation(context, it)
    }
}

/** The Spell-buying location row (Bottom/Split, Top pending). Gated on the Spell buying Game UI
 *  element. Only Bottom + Split are implemented (there's no top-screen SpellBuyingOverlay). */
@Composable
private fun SpellBuyingLocationRow() {
    val context = LocalContext.current
    val loc by UiPreferences.spellBuyingLocationFlow().collectAsState()
    ServiceLocationRow("Spell buying", "game_ui_spellbuying", loc, showSplit = false) {
        UiPreferences.setSpellBuyingLocation(context, it)
    }
}

/** The Training location row (Bottom/Split, Top pending). Gated on the Training Game UI element.
 *  Only Bottom + Split are implemented (there's no top-screen TrainingOverlay). */
@Composable
private fun TrainingLocationRow() {
    val context = LocalContext.current
    val loc by UiPreferences.trainingLocationFlow().collectAsState()
    ServiceLocationRow("Training", "game_ui_training", loc, showSplit = false) {
        UiPreferences.setTrainingLocation(context, it)
    }
}

/** The Repair location row (Bottom; Top pending). Gated on the Repair Game UI element.
 *  Bottom = the centred RepairOverlay card; no Split / Top yet. */
@Composable
private fun RepairLocationRow() {
    val context = LocalContext.current
    val loc by UiPreferences.repairLocationFlow().collectAsState()
    ServiceLocationRow("Repair", "game_ui_repair", loc, showSplit = false) {
        UiPreferences.setRepairLocation(context, it)
    }
}

/** The Travel location row (Bottom; Top pending). Gated on the Travel Game UI element.
 *  Bottom = the centred TravelOverlay card; no Split / Top yet. */
@Composable
private fun TravelLocationRow() {
    val context = LocalContext.current
    val loc by UiPreferences.travelLocationFlow().collectAsState()
    ServiceLocationRow("Travel", "game_ui_travel", loc, showSplit = false) {
        UiPreferences.setTravelLocation(context, it)
    }
}

/** The Target-health location row: a [Bottom][Top] pill selector. BOTTOM (default) = the
 *  bottom-screen HUD combat-target bar; TOP = an additional top-screen overlay (top-centre). */
@Composable
private fun TargetHealthLocationRow() {
    val context = LocalContext.current
    val loc by UiPreferences.targetHealthLocationFlow().collectAsState()

    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Text("Target health", color = Bone, fontSize = 14.sp, fontFamily = MwBody)
        Text(
            "Shows enemy health bar on the top screen during combat",
            color = BoneDim,
            fontSize = 10.sp,
            fontFamily = MwBody,
            modifier = Modifier.padding(top = 1.dp)
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionPill(
                Modifier.weight(1f),
                label = "Bottom",
                active = loc == TargetHealthLocation.BOTTOM,
                enabled = true
            ) { UiPreferences.setTargetHealthLocation(context, TargetHealthLocation.BOTTOM) }
            OptionPill(
                Modifier.weight(1f),
                label = "Top",
                active = loc == TargetHealthLocation.TOP,
                enabled = true
            ) { UiPreferences.setTargetHealthLocation(context, TargetHealthLocation.TOP) }
        }
    }
}

/** The Player-status-in-combat row: an [Off][On] pill selector (default Off). On additionally
 *  shows the player's vitals on the top screen (top-left) while a combat target exists. */
@Composable
private fun PlayerCombatRow() {
    val context = LocalContext.current
    val enabled by UiPreferences.playerCombatFlow().collectAsState()

    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Text("Player status in combat", color = Bone, fontSize = 14.sp, fontFamily = MwBody)
        Text(
            "Also show your health / magicka / fatigue on the top screen during combat",
            color = BoneDim,
            fontSize = 10.sp,
            fontFamily = MwBody,
            modifier = Modifier.padding(top = 1.dp)
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionPill(
                Modifier.weight(1f),
                label = "Off",
                active = !enabled,
                enabled = true
            ) { UiPreferences.setPlayerCombat(context, false) }
            OptionPill(
                Modifier.weight(1f),
                label = "On",
                active = enabled,
                enabled = true
            ) { UiPreferences.setPlayerCombat(context, true) }
        }
    }
}

/** A pending Screen Layout row: element name + PENDING tag over a locked [Bottom][Top] selector
 *  (Bottom active, both pills disabled). Per-screen routing isn't implemented for these yet. */
@Composable
private fun ScreenLayoutPendingRow(el: GameUiElement) {
    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                el.label,
                color = BoneDim,
                fontSize = 14.sp,
                fontFamily = MwBody,
                modifier = Modifier.weight(1f)
            )
            Text(
                "PENDING",
                color = BoneDim.copy(alpha = 0.7f),
                fontSize = 9.sp,
                fontFamily = MwDisplay,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionPill(Modifier.weight(1f), label = "Bottom", active = true, enabled = false) {}
            OptionPill(Modifier.weight(1f), label = "Top", active = false, enabled = false) {}
        }
    }
}

/** Input row: toggle whether touch / thumbsticks drive the top-screen game cursor.
 *  [Off][On] pill selector (default Off), writing to UiPreferences on every tap. */
/** Input: direct touch-to-click on the top screen while a menu is open. [Off][On] pill selector
 *  (default On), writing to UiPreferences on every tap. */
@Composable
private fun TouchInputRow() {
    val context = LocalContext.current
    val enabled by UiPreferences.touchInputFlow().collectAsState()

    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Text("Touch input", color = Bone, fontSize = 14.sp, fontFamily = MwBody)
        Text(
            "Tap the top screen to click there directly in menus, like a touchscreen",
            color = BoneDim,
            fontSize = 10.sp,
            fontFamily = MwBody,
            modifier = Modifier.padding(top = 1.dp)
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionPill(
                Modifier.weight(1f),
                label = "Off",
                active = !enabled,
                enabled = true
            ) { UiPreferences.setTouchInput(context, false) }
            OptionPill(
                Modifier.weight(1f),
                label = "On",
                active = enabled,
                enabled = true
            ) { UiPreferences.setTouchInput(context, true) }
        }
    }
}

@Composable
private fun GameCursorRow() {
    val context = LocalContext.current
    val enabled by UiPreferences.gameCursorFlow().collectAsState()

    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Text("Game cursor", color = Bone, fontSize = 14.sp, fontFamily = MwBody)
        Text(
            "Touch and thumbsticks control the game cursor on the top screen",
            color = BoneDim,
            fontSize = 10.sp,
            fontFamily = MwBody,
            modifier = Modifier.padding(top = 1.dp)
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionPill(
                Modifier.weight(1f),
                label = "Off",
                active = !enabled,
                enabled = true
            ) { UiPreferences.setGameCursor(context, false) }
            OptionPill(
                Modifier.weight(1f),
                label = "On",
                active = enabled,
                enabled = true
            ) { UiPreferences.setGameCursor(context, true) }
        }
    }
}

@Composable
private fun OptionsSectionHeader(title: String, dimmed: Boolean = false) {
    Column(Modifier.alpha(if (dimmed) 0.4f else 1f).padding(top = 20.dp, bottom = 4.dp)) {
        Text(
            title.uppercase(),
            color = BronzeLight,
            fontSize = 22.sp,
            fontFamily = MwDisplay,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
    }
}

/** Toggle for the Alpha3 launcher overlay (the gear + arrow cluster on the top screen).
 *  [On][Off] pill selector (default On). On = shown; Off hides the whole cluster including the
 *  arrow's expanded quick-action row (one composable). Purely Kotlin-side; writes on every tap. */
@Composable
private fun Alpha3OverlayRow() {
    val context = LocalContext.current
    val shown by UiPreferences.alpha3OverlayFlow().collectAsState()

    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Text("Alpha3 overlay", color = Bone, fontSize = 14.sp, fontFamily = MwBody)
        Text(
            "The gear + arrow menu cluster in the top corner",
            color = BoneDim,
            fontSize = 10.sp,
            fontFamily = MwBody,
            modifier = Modifier.padding(top = 1.dp)
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionPill(
                Modifier.weight(1f),
                label = "On",
                active = shown,
                enabled = true
            ) { UiPreferences.setAlpha3Overlay(context, true) }
            OptionPill(
                Modifier.weight(1f),
                label = "Off",
                active = !shown,
                enabled = true
            ) { UiPreferences.setAlpha3Overlay(context, false) }
        }
    }
}

/** A HUD-element row: element name (+ PENDING tag) over an [On][Off] pill selector. On = the native
 *  top-screen version is visible; Off = hidden (companion bottom-screen version only). The companion
 *  always draws these on the bottom screen. Writes a Boolean to UiPreferences on every tap. Pending
 *  elements (no native gate yet) render dimmed and locked to On. */
@Composable
private fun HudToggleRow(el: UiElement) {
    val context = LocalContext.current

    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                el.label,
                color = if (el.pending) BoneDim else Bone,
                fontSize = 14.sp,
                fontFamily = MwBody,
                modifier = Modifier.weight(1f)
            )
            if (el.pending) {
                Text(
                    "PENDING",
                    color = BoneDim.copy(alpha = 0.7f),
                    fontSize = 9.sp,
                    fontFamily = MwDisplay,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (el.pending) {
                // Not gated yet — native version stays On, both pills disabled.
                OptionPill(Modifier.weight(1f), label = "On", active = true, enabled = false) {}
                OptionPill(Modifier.weight(1f), label = "Off", active = false, enabled = false) {}
            } else {
                val on by UiPreferences.hudOnFlow(el.key).collectAsState()
                OptionPill(
                    Modifier.weight(1f),
                    label = "On",
                    active = on,
                    enabled = true
                ) { UiPreferences.setHudOn(context, el.key, true) }
                OptionPill(
                    Modifier.weight(1f),
                    label = "Off",
                    active = !on,
                    enabled = true
                ) { UiPreferences.setHudOn(context, el.key, false) }
            }
        }
    }
}

/**
 * One option in a two-choice pill selector. Active = bronze-tinted fill + BronzeLight
 * border/text with a trailing "●"; inactive = dark fill + muted border/text. When
 * [enabled] is false the pill is dimmed and non-tappable (used for PENDING elements).
 */
@Composable
private fun OptionPill(
    modifier: Modifier,
    label: String,
    active: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val a = if (enabled) 1f else 0.4f
    val bg = (if (active) PillActiveBg else SlotBg).copy(alpha = (if (active) 0.94f else 1f) * a)
    val border = (if (active) BronzeLight else BronzeDark).copy(alpha = a)
    val fg = (if (active) BronzeLight else BoneDim).copy(alpha = a)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(4.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(vertical = 8.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (active) "$label ●" else label,
            color = fg,
            fontSize = 12.sp,
            fontFamily = MwDisplay,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}
