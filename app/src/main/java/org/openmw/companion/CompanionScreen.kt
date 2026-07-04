package org.openmw.companion

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.zIndex
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.derivedStateOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp

// Splash image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import org.openmw.R
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import kotlinx.coroutines.withContext
import java.io.File
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.floor


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
// Shared height for the "top box" on Inventory (EQUIPPED strip) and Spells
// (Active Spell) so the two panels line up. Content is vertically centered.
private val TOP_BOX_HEIGHT = 54.dp

private val ICON_CACHE_DIR by lazy {
    File("${Constants.USER_FILE_STORAGE}/companion_icons").also { it.mkdirs() }
}

// Fraction of a single cell segment visible across the short canvas axis.
// 0.25 = tight zoom (25% of cell visible); increase toward 1.0 to zoom out.
private const val MINIMAP_CROP_FRACTION = 0.25f

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
    InvCategory("Tools",   setOf("lockpick", "probe")),
    InvCategory("Books",       setOf("book", "scroll")),
    InvCategory("Consumables", setOf("potion", "ingredient")),
    InvCategory("Misc",        setOf("misc", "carried_left")),
)

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

/** Stone panel with a bronze frame — the signature Morrowind window look. */
private fun Modifier.mwPanel(): Modifier = this
    .clip(RoundedCornerShape(3.dp))
    .background(StonePanel)
    .border(2.dp, Bronze, RoundedCornerShape(3.dp))

@Composable
fun CompanionScreen() {
    val state by GameStateRepository.state.collectAsState()
    var tab by remember { mutableStateOf(Tab.HUD) }

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
        // STATS no longer shows the TopStatBar — its Vitals section covers
        // Health/Magicka/Fatigue, so the bar would be redundant and just eats space.
        val showStatBar = tab == Tab.HUD

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

        if (showStatBar) {
            TopStatBar(
                state = state,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(12.dp)
            )
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

        // Detail popup — appears when a COMPANION_INFO reply lands (in response to
        // an "Info" menu tap). Rendered as an in-window overlay (NOT a Dialog):
        // the companion UI lives inside a Presentation on a secondary display, and
        // a Compose Dialog tries to add a TYPE_APPLICATION window into that
        // Presentation's window context, which throws "Window type mismatch".
        val info by GameStateRepository.itemInfo.collectAsState()
        info?.let { ItemInfoOverlay(it, onDismiss = { GameStateRepository.dismissItemInfo() }) }

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

        // Looting / pickpocketing overlay. Driven by COMPANION_CONTAINER_* from the
        // engine (via Lua). Shown whenever a container session is active, regardless
        // of Hide UI — same as the dialogue overlay, so the panel stays available
        // when the player hides the in-game HUD (the native container window is left
        // in place; this overlay is additive, not a replacement). It renders at
        // zIndex 15f (above the global dropdown-dismiss scrim so its buttons stay
        // tappable; it hosts its own local dismiss-scrim) and below QuantitySelector
        // (20f) so take/put quantity prompts stack above it.
        val containerSession by GameStateRepository.containerSession.collectAsState()
        containerSession?.let { session ->
            LootingOverlay(
                session = session,
                playerInventory = state.inventory,
                playerEquipment = state.equipment
            )
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
        if (dialogueNpcName.isNotEmpty() || dialogueTopics.isNotEmpty() ||
            dialogueServices.isNotEmpty() || dialogueHistory.isNotEmpty() ||
            dialogueChoices.isNotEmpty()
        ) {
            // Player gold for the persuasion popup. Prefer the live COMPANION_DIALOGUE_GOLD
            // value (emitted from updateDisposition, so it updates after a bribe); fall back
            // to the inventory gold_001 count until the first gold line arrives.
            val playerGold = if (dialogueGold >= 0) dialogueGold
                else state.inventory.firstOrNull { it.id.equals("gold_001", ignoreCase = true) }?.count ?: 0
            DialogueTopicsOverlay(dialogueNpcName, dialogueHistory, dialogueTopics, dialogueServices, dialogueChoices, dialogueDisposition, dialoguePersuadeAvailable, playerGold)
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
        barterSession?.let { session ->
            BarterOverlay(session = session, disposition = dialogueDisposition)
        }
        if (barterSession != null) {
            (barterResult as? BarterResult.Rejected)?.let { rej ->
                BarterRejectedAlert(rej.reason) { GameStateRepository.dismissBarterResult() }
            }
        }
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
    playerGold: Int
) {
    // Local popup visibility. Keyed on npcName so switching NPCs mid-session closes any
    // open popup; it also disappears automatically when the whole overlay leaves the
    // composition on COMPANION_DIALOGUE_CLOSED. A choice being active suppresses it.
    var showPersuasion by remember(npcName) { mutableStateOf(false) }

    // Non-interactive dark scrim (NOT a Dialog — that crashes on the Presentation
    // display). It has NO tap-away dismiss; the empty detectTapGestures just swallows
    // taps on the exposed margins so they don't fall through to the tab underneath.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(15f)
            .background(Color(0xCC0F0C08))
            .pointerInput(Unit) { detectTapGestures {} }
    ) {
        // Fills the full screen height (bottom = 12.dp, not BOTTOM_BAR_SPACE): dialogue is a
        // modal interaction left via Goodbye, so the panel covers the bottom tab bar — same
        // approach as the barter overlay. 12dp matches the top/side insets.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp, bottom = 12.dp, start = 12.dp, end = 12.dp)
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

            // ---- Two columns: dialogue history (75%) | topics/services (25%) ----
            Row(Modifier.fillMaxSize()) {
                DialogueHistoryColumn(
                    history, choices,
                    modifier = Modifier.weight(0.65f).fillMaxHeight().padding(8.dp)
                )
                Box(Modifier.fillMaxHeight().width(1.dp).background(BronzeDark))
                DialogueRightColumn(
                    topics = topics, services = services, disposition = disposition,
                    persuadeAvailable = persuadeAvailable,
                    // While a choice is active, the Persuade row and every topic/service row
                    // stay visible but greyed and non-tappable (interactive = false) — the
                    // inline choices in the history take priority over topic selection.
                    choicesActive = choices.isNotEmpty(),
                    interactive = choices.isEmpty(),
                    onPersuadeTapped = { showPersuasion = true },
                    modifier = Modifier.weight(0.35f).fillMaxHeight().padding(8.dp)
                )
            }
        }

        // Persuasion popup — centred overlay + scrim over the conversation panel, shown
        // when the player tapped Persuade. Suppressed while a choice is active (Persuade is
        // non-tappable then). Each action sends the command and closes the popup (matches
        // native Morrowind — reopen Persuade for another attempt); Cancel / scrim-tap also
        // dismisses. Leaves automatically on COMPANION_DIALOGUE_CLOSED (overlay disposed).
        if (showPersuasion && choices.isEmpty()) {
            PersuasionPopup(
                gold = playerGold,
                onPersuade = { type ->
                    CompanionActions.persuade(type)
                    showPersuasion = false
                },
                onCancel = { showPersuasion = false }
            )
        }
    }
}

/** The dialogue right column — services, disposition bar, the Persuade trigger, the
 *  scrolling topics list and the Goodbye button. [choicesActive] greys the rows (a
 *  mid-dialogue question is showing its answers in the left column); [interactive] gates
 *  every tap — off while a choice is active OR while the persuasion column owns input. */
@Composable
private fun DialogueRightColumn(
    topics: List<String>,
    services: List<String>,
    disposition: Int,
    persuadeAvailable: Boolean,
    choicesActive: Boolean,
    interactive: Boolean,
    onPersuadeTapped: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Barter is pulled out of the services list so it can sit above the divider (matched by
    // its display string). EVERY other service drops into the scrollable list with the topics.
    val barterService = services.firstOrNull { it.equals("Barter", ignoreCase = true) }
    val otherServices = services.filter { it != barterService }

    Column(modifier) {
        // 1. Disposition bar (fixed at the top). Hidden when unknown (< 0, e.g. a creature).
        //    A small divider below it stays put (outside the LazyColumn) while topics scroll.
        if (disposition >= 0) {
            DispositionBar(disposition)
            Spacer(Modifier.height(6.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.5f)))
            Spacer(Modifier.height(4.dp))
        }

        // 2-5. Barter, Persuade, the divider, any OTHER services, and the dialogue topics all
        //      live in ONE LazyColumn so they scroll together. No "Services"/"Topics" headings.
        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Barter (if the NPC offers it) — a plain row, identical to the topic rows.
            if (barterService != null) {
                item {
                    DialogueOptionRow(barterService, dimmed = choicesActive) {
                        if (interactive) CompanionActions.activateDialogueService(barterService)
                    }
                }
            }
            // Persuade (if available) — SAME styling as Barter.
            if (persuadeAvailable) {
                item {
                    DialogueOptionRow("Persuade", dimmed = choicesActive) {
                        if (interactive) onPersuadeTapped()
                    }
                }
            }
            // Divider between the Barter/Persuade block and the topics (only if something's
            // above it). No leading gap: the last action row already draws its own faint
            // under-divider, so the bold divider sits flush against it and reads as a single
            // section divider rather than a second line floating below the faint one.
            if (barterService != null || persuadeAvailable) {
                item {
                    Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
                    Spacer(Modifier.height(4.dp))
                }
            }
            // Any other services first, then the dialogue topics.
            items(otherServices) { service ->
                DialogueOptionRow(service, dimmed = choicesActive) {
                    if (interactive) CompanionActions.activateDialogueService(service)
                }
            }
            items(topics) { topic ->
                DialogueOptionRow(topic, dimmed = choicesActive) {
                    if (interactive) CompanionActions.selectDialogueTopic(topic)
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
 *  they look identical (only the section header distinguishes them). */
@Composable
private fun DialogueOptionRow(label: String, dimmed: Boolean = false, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Text(
            label,
            color = if (dimmed) BoneDim else Bone, fontSize = 14.sp, fontFamily = MwBody,
            modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp, horizontal = 4.dp)
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.5f)))
    }
}

/** Persuasion popup — a centred in-window overlay (NOT a Dialog; that crashes on the
 *  Presentation display) + scrim over the conversation panel. Six persuasion option rows
 *  (bribes greyed + non-tappable when the player can't afford them, live gold via
 *  dialogueGold), a gold readout and a Cancel button. Each option sends CMPDLG:persuade:
 *  <type> and closes the popup (matches native Morrowind — reopen Persuade for another
 *  attempt); Cancel / scrim-tap dismisses locally (the native modal is never shown, so
 *  there is nothing to cancel engine-side). */
@Composable
private fun PersuasionPopup(gold: Int, onPersuade: (Int) -> Unit, onCancel: () -> Unit) {
    // label, persuade type (matches native companionPersuade switch), gold cost
    val options = listOf(
        Triple("Admire", 0, 0),
        Triple("Intimidate", 1, 0),
        Triple("Taunt", 2, 0),
        Triple("Bribe 10 Gold", 3, 10),
        Triple("Bribe 100 Gold", 4, 100),
        Triple("Bribe 1000 Gold", 5, 1000)
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(30f)
            .background(Color(0x99000000))
            .pointerInput(Unit) { detectTapGestures { onCancel() } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .width(360.dp)
                .mwPanel()
                .pointerInput(Unit) { detectTapGestures {} }
        ) {
            Text(
                "Persuasion", color = BronzeLight, fontSize = 15.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)
            )
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))

            options.forEachIndexed { i, (label, type, cost) ->
                val enabled = gold >= cost
                // Alternating row backgrounds for readability.
                val rowBg = if (i % 2 == 0) Color(0x22000000) else Color(0x11000000)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(rowBg)
                        .then(if (enabled) Modifier.clickable { onPersuade(type) } else Modifier)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(
                        label,
                        color = if (enabled) Bone else BoneDim,
                        fontSize = 14.sp, fontFamily = MwBody
                    )
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.4f)))
            }

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

/** Left column: the running NPC-response history, auto-scrolled to the newest line.
 *  When [choices] is non-empty (a mid-dialogue question) the choice buttons render
 *  inline as the LAST item of the list, directly below the most recent response. */
@Composable
private fun DialogueHistoryColumn(
    history: List<DialogueSay>,
    choices: List<DialogueChoice>,
    modifier: Modifier = Modifier,
    interactive: Boolean = true
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
                Text(
                    dialogueAnnotated(say.text, say.hyperlinks, interactive),
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
                choices.forEachIndexed { i, choice ->
                    if (i > 0) Spacer(Modifier.height(6.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(2.dp))
                            .background(BronzeLight.copy(alpha = 0.12f))
                            .border(1.dp, BronzeLight, RoundedCornerShape(2.dp))
                            .clickable {
                                if (interactive) {
                                    // id -1 = the synthetic forced-goodbye prompt (NPC
                                    // taunted into combat, etc.) — route it to goodbye, not
                                    // a real choice answer.
                                    if (choice.id == -1) CompanionActions.dialogueGoodbye()
                                    else CompanionActions.activateDialogueChoice(choice.id)
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            choice.text,
                            color = BronzeLight, fontSize = 14.sp, fontFamily = MwBody
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
        }
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

/* ---- Item / spell detail popup ---- */

@Composable
private fun ItemInfoOverlay(info: ItemInfo, onDismiss: () -> Unit) {
    // Full-screen scrim; tapping it (outside the panel) dismisses. The scrim also
    // blocks tap-through to the tabs/list underneath while the popup is open.
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
                .width(400.dp)
                .heightIn(max = 560.dp)
                .mwPanel()
                // Swallow taps inside the panel so they don't reach the scrim.
                .pointerInput(Unit) { detectTapGestures(onTap = {}) }
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                info.name,
                color = BronzeLight,
                fontSize = 16.sp,
                fontFamily = MwDisplay,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(10.dp))

            info.rows.forEachIndexed { i, (label, value) ->
                if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.5f)))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 7.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = BoneDim, fontSize = 12.sp, fontFamily = MwBody)
                    Text(
                        value,
                        color = Bone,
                        fontSize = 12.sp,
                        fontFamily = MwData,
                        modifier = Modifier.padding(start = 12.dp)
                    )
                }
            }

            if (info.effects.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    "EFFECTS",
                    color = BronzeLight,
                    fontSize = 11.sp,
                    fontFamily = MwDisplay,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp
                )
                Spacer(Modifier.height(4.dp))
                info.effects.forEach { eff ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (eff.harmful) Color(0xFFC75C5C) else Color(0xFF7FBF7F))
                        )
                        Text(
                            eff.text,
                            color = Bone,
                            fontSize = 12.sp,
                            fontFamily = MwBody,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        }
    }
}

/* ---- Looting / pickpocketing overlay ---- */

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
    playerEquipment: Map<String, String>
) {
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
    val wornIds = remember(playerEquipment) { playerEquipment.values.toSet() }
    fun isWorn(item: InventoryItem): Boolean =
        if (item.stackId.isNotEmpty()) wornIds.contains(item.stackId) else wornIds.contains(item.id)

    // Take: container → player (optimistic) + real CMP:container_take. Put: the reverse.
    fun take(item: InventoryItem, n: Int) {
        val (c, p) = moveOptimistic(containerItems, playerItems, item, n)
        containerItems = c; playerItems = p
        CompanionActions.containerTake(item.stackId.ifEmpty { item.id }, n)
    }
    fun put(item: InventoryItem, n: Int) {
        val (p, c) = moveOptimistic(playerItems, containerItems, item, n)
        playerItems = p; containerItems = c
        CompanionActions.containerPut(item.stackId.ifEmpty { item.id }, n)
    }

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
                    header = "Player (${playerGold}g)",
                    legend = "tap to put · long press for more",
                    items = playerItems,
                    isPlayerSide = true,
                    isWorn = { isWorn(it) },
                    onTransfer = { it, n -> put(it, n) },
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
                    header = session.containerName.ifBlank { "Container" },
                    legend = "tap to take · long press for more",
                    items = containerItems,
                    isPlayerSide = false,
                    isWorn = { false },
                    onTransfer = { it, n -> take(it, n) },
                    // Pickpocket: an empty visible list usually means your Sneak hid the
                    // items (not that the NPC is broke) — say so. Corpses/chests: "Empty".
                    emptyText = if (session.isPickpocket) "Nothing you can lift" else "Empty",
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
                ) {
                    // Optimistically empty the container; the Lua handler takes all AND
                    // closes the overlay (removeMode → COMPANION_CONTAINER_CLOSED).
                    playerItems = containerItems.fold(playerItems) { acc, it ->
                        moveOptimistic(listOf(it), acc, it, it.count).second
                    }
                    containerItems = emptyList()
                    CompanionActions.containerTakeAll()
                }
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

/** One side of the looting overlay — header, legend, and a scrolling item list. */
@Composable
private fun LootColumn(
    header: String,
    legend: String,
    items: List<InventoryItem>,
    isPlayerSide: Boolean,
    isWorn: (InventoryItem) -> Boolean,
    onTransfer: (InventoryItem, Int) -> Unit,
    emptyText: String = "Empty",
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
            // Sort worn-first (player side) then alphabetical, mirroring the inventory tab.
            val sorted = remember(items, isPlayerSide) {
                items.sortedWith(
                    compareByDescending<InventoryItem> { isPlayerSide && isWorn(it) }
                        .thenBy { it.displayName().lowercase() }
                )
            }
            LazyColumn(Modifier.fillMaxSize()) {
                items(sorted) { item ->
                    LootRow(
                        item = item,
                        isPlayerSide = isPlayerSide,
                        worn = isPlayerSide && isWorn(item),
                        iconBitmap = rememberItemIcon(item.icon),
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

    Box {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
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
                onClick = { menuOpen = false; DropdownState.closeAll(); CompanionActions.requestItemInfo(item.id) },
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

/* ---- Barter overlay (bottom screen) ---- */

private val BarterGreen = Color(0xFF7FBF7F)   // Offer / "player receives" (matches effect green)
private val BarterRed = Color(0xFFC75C5C)     // "player pays" / rejected (matches effect red)
private val BarterBlue = Color(0xFF6E93C9)    // Cancel

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
private fun BarterOverlay(session: BarterSession, disposition: Int) {
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
    // Step the gold offset, clamping the resulting balance to what each side can pay
    // (you can't offer to pay more than you have, nor demand more than the vendor's gold).
    fun adjustGold(delta: Int) {
        val current = session.merchantOffer + extraGold
        val target = (current + delta).coerceIn(-session.playerGold, session.vendorGold)
        extraGold = target - session.merchantOffer
        CompanionActions.barterSetExtraGold(extraGold)
    }
    // Tapping the centre resets the manual offset (back to the fair market price).
    fun resetGold() {
        extraGold = 0
        CompanionActions.barterSetExtraGold(0)
    }

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
                    header = "You (${session.playerGold}g)",
                    items = playerItems,
                    isPlayerSide = true,
                    selectedCategory = playerCat,
                    onSelectCategory = { playerCat = it },
                    onToggle = ::toggle,
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
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(8.dp)
                )
            }

            // ---- Offer section: a single compact gold row ----
            Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
            BarterGoldBar(balance = offerBalance, onStep = ::adjustGold, onReset = ::resetGold)

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

/** One side of the barter overlay — header, per-side category tabs, scrolling item list. */
@Composable
private fun BarterColumn(
    header: String,
    items: List<BarterItem>,
    isPlayerSide: Boolean,
    selectedCategory: String?,
    onSelectCategory: (String?) -> Unit,
    onToggle: (BarterItem) -> Unit,
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

        // Selected-first, then worn-first (player), then alphabetical — mirrors inventory.
        val visible = remember(items, selectedCategory, isPlayerSide) {
            items.filter { selectedCategory == null || it.category == selectedCategory }
                .sortedWith(
                    compareByDescending<BarterItem> { it.isSelected }
                        .thenByDescending { isPlayerSide && it.worn }
                        .thenBy { it.displayName().lowercase() }
                )
        }
        // weight(1f) so the list explicitly takes ALL remaining height in the column —
        // the item lists expand to fill the overlay (matching the looting overlay).
        if (visible.isEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("Nothing to trade", color = BoneDim, fontSize = 12.sp, fontFamily = MwBody)
            }
        } else {
            LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                items(visible) { item ->
                    BarterRow(
                        item = item,
                        isPlayerSide = isPlayerSide,
                        iconBitmap = rememberItemIcon(item.icon),
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
    onToggle: (BarterItem) -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(DropdownState.closeRequest) {
        if (DropdownState.closeRequest > 0) menuOpen = false
    }
    val selected = item.isSelected
    val label = item.displayName()
    Box {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (selected) SlotWorn else Color.Transparent)
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
                onClick = { menuOpen = false; DropdownState.closeAll(); CompanionActions.requestItemInfo(item.id) },
                colors = menuItemColors
            )
        }
    }
}

/**
 * Compact offer bar: [−100] [−10] [ balance ] [+10] [+100]. The centre shows the gold
 * actually changing hands (the engine's fair price + the manual offset): green = you
 * receive, red = you pay. Tapping the centre resets the offset to the fair price. No
 * other text.
 */
@Composable
private fun BarterGoldBar(balance: Int, onStep: (Int) -> Unit, onReset: () -> Unit) {
    val balanceColor = when {
        balance > 0 -> BarterGreen
        balance < 0 -> BarterRed
        else -> Bone
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        GoldStep("−100", Modifier.weight(1f)) { onStep(-100) }
        GoldStep("−10", Modifier.weight(1f)) { onStep(-10) }
        // Centre: gold changing hands; tap to reset to the fair market price.
        Box(
            modifier = Modifier
                .weight(1.4f)
                .height(44.dp)
                .clip(RoundedCornerShape(3.dp))
                .clickable { onReset() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (balance > 0) "+${balance}g" else "${balance}g",
                color = balanceColor, fontSize = 20.sp, fontFamily = MwData, fontWeight = FontWeight.Bold
            )
        }
        GoldStep("+10", Modifier.weight(1f)) { onStep(10) }
        GoldStep("+100", Modifier.weight(1f)) { onStep(100) }
    }
}

/** A gold-adjust stepper button — ≥40dp tall for easy tapping. */
@Composable
private fun GoldStep(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(SlotWorn)
            .border(1.dp, Bronze, RoundedCornerShape(3.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = BronzeLight, fontSize = 15.sp, fontFamily = MwData, fontWeight = FontWeight.Bold)
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

    val favs by FavouritesRepository.state.collectAsState()
    val context = LocalContext.current

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

    var showWeaponName by remember { mutableStateOf(false) }
    var showSpellName by remember { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .padding(top = TOP_BAR_SPACE.dp, bottom = BOTTOM_BAR_SPACE.dp, start = 12.dp, end = 12.dp)
            .mwPanel()
    ) {
        // Canvas fills the whole panel; labels float over it. Tapping the map
        // (anywhere not covered by an overlay declared later in this Box — those
        // consume the tap first via Compose z-order) opens the in-game world map.
        Canvas(
            Modifier
                .fillMaxSize()
                // Disabled while the splash is up so a splash-dismiss tap doesn't
                // fall through to the map canvas and open the world map instead.
                .clickable(enabled = !splashVisible) { CompanionActions.openWorldMap() }
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val cellPx = size.minDimension / MINIMAP_CROP_FRACTION
            val arrowDeg = state.rotZ * (180f / Math.PI.toFloat())

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
                val boundsMinX = interiorMaps.values.first().boundsMinX
                val boundsMinY = interiorMaps.values.first().boundsMinY

                val rawX = (state.pos.x - boundsMinX) / interiorMapWorldSize
                val rawY = (state.pos.y - boundsMinY) / interiorMapWorldSize
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

            if (hasMap) drawArrow(cx, cy, size.minDimension * 0.04f, arrowDeg)
        }

        // Cell name — bottom-centre overlay. Horizontal padding keeps it clear of
        // the GEAR/SPELLS favourite groups in the bottom corners; truncates rather
        // than overlapping.
        Text(
            if (state.hasData) state.cell.ifEmpty { "Exterior" } else "—",
            color = Bone, fontSize = 16.sp, fontFamily = MwDisplay,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
        // pills. Only present while a target exists (during combat).
        state.target?.let { target ->
            TargetHealthOverlay(
                target = target,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 6.dp)
            )
        }
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
                    .sortedWith(compareByDescending<InventoryItem> { isWorn(it) }
                        .thenBy { it.displayName().lowercase() })
                if (groupItems.isNotEmpty()) {
                    item(key = "hdr_${grp.label}") { SpellSectionHeader(grp.label) }
                    items(groupItems) { item ->
                        val worn = isWorn(item)
                        val readable = item.category == "book" || item.category == "scroll"
                        ItemRow(item, worn, equippable = item.category !in setOf("misc", "potion", "ingredient") && !readable, readable, iconBitmap = rememberItemIcon(item.icon))
                    }
                }
            }
        } else {
            val groupItems = state.inventory
                .filter { it.category in selectedGroup.cats }
                // Worn items first, then the rest alphabetically.
                .sortedWith(compareByDescending<InventoryItem> { isWorn(it) }
                    .thenBy { it.displayName().lowercase() })
            items(groupItems) { item ->
                val worn = isWorn(item)
                val readable = item.category == "book" || item.category == "scroll"
                ItemRow(item, worn, equippable = item.category !in setOf("misc", "potion", "ingredient") && !readable, readable, iconBitmap = rememberItemIcon(item.icon))
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
        bitmap = withContext(Dispatchers.IO) {
            val cacheDir = File(context.cacheDir, "item_icons").apply { mkdirs() }
            val cacheFile = File(cacheDir, iconPath.replace('\\', '_').replace('/', '_') + ".png")
            if (!cacheFile.exists()) {
                CompanionActions.exportIconToPng(iconPath, cacheFile.absolutePath)
            }
            if (cacheFile.exists()) {
                val rawBitmap = BitmapFactory.decodeFile(cacheFile.absolutePath)
                if (rawBitmap == null) {
                    null
                } else {
                    // Flip vertically: OpenGL row 0 = bottom, Android bitmap row 0
                    // = top, so exported icon PNGs come out upside-down (same as
                    // the minimap flip in GameStateRepository).
                    val flipMatrix = Matrix().apply { preScale(1f, -1f) }
                    val flipped = Bitmap.createBitmap(
                        rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, flipMatrix, false
                    )
                    rawBitmap.recycle()
                    flipped.asImageBitmap()
                }
            } else null
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

    Box {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = {
                            val target = item.stackId.ifEmpty { item.id }
                            when {
                                readable -> CompanionActions.readItem(item.id)
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
            DropdownMenuItem(
                text = { Text("Info", fontFamily = MwBody, fontSize = 13.sp) },
                onClick = { menuOpen = false; DropdownState.closeAll(); CompanionActions.requestItemInfo(item.id) },
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
                state.spells.filter { it.type == "scroll" }.sortedWith(bySelectionThenName)
            }

            // Category filter tabs — only present (non-empty) categories get a tab.
            // ALL is always shown. Mirrors InventoryPanel's CategorySubTabs pattern.
            var selectedMagicTab by remember { mutableStateOf("ALL") }
            val presentMagicTabs = remember(powers, spells, scrolls) {
                buildList {
                    if (powers.isNotEmpty()) add("POWERS")
                    if (spells.isNotEmpty()) add("SPELLS")
                    if (scrolls.isNotEmpty()) add("SCROLLS")
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
                                onInfo = { CompanionActions.requestSpellInfo(spell.id) },
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
                                onInfo = { CompanionActions.requestSpellInfo(spell.id) },
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
                                onInfo = { CompanionActions.requestSpellInfo(spell.id) },
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
    Box {
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

/**
 * The display-settings menu: route each HUD element / menu between the top and bottom
 * screens, and choose the render style of top-screen elements. Full-screen, scrollable,
 * BronzeLight-themed. Writes to [UiPreferences] live on every tap (no save/cancel).
 *
 * Public because [org.openmw.EngineActivity] hosts it in a WindowManager panel window on
 * the companion Presentation when the pause/options menu opens.
 */
@Composable
fun OptionsMenuOverlay() {
    val context = LocalContext.current
    remember(context) { UiPreferences.init(context); true }

    // The UI-style section lists non-pending elements currently routed to the top screen.
    // Collect their routes here (composable scope) so the LazyColumn body — a non-composable
    // LazyListScope lambda — can just read the resulting list and re-render on route changes.
    val styleElements = UI_ELEMENTS
        .filter { it.hasCompanionUi }
        .filter { UiPreferences.routeFlow(it.key).collectAsState().value == ScreenRoute.TOP }

    val hudElements = remember { UI_ELEMENTS.filter { it.section == UiSection.HUD } }
    val menuElements = remember { UI_ELEMENTS.filter { it.section == UiSection.MENUS } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(OptionsBg)
    ) {
        // Title bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(StonePanel)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                "Display settings",
                color = BronzeLight,
                fontSize = 18.sp,
                fontFamily = MwDisplay,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }
        Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
        ) {
            item { OptionsSectionHeader("HUD Elements") }
            items(hudElements, key = { it.key }) { ScreenRouteRow(it) }

            item { OptionsSectionHeader("Menus and Overlays") }
            items(menuElements, key = { it.key }) { ScreenRouteRow(it) }

            item { OptionsSectionHeader("UI Style (top screen)") }
            if (styleElements.isEmpty()) {
                item {
                    Text(
                        "Route an element to Top screen to see style options",
                        color = BoneDim,
                        fontSize = 12.sp,
                        fontFamily = MwBody,
                        modifier = Modifier.padding(vertical = 10.dp)
                    )
                }
            } else {
                items(styleElements, key = { "style_" + it.key }) { UiStyleRow(it) }
            }
        }
    }
}

@Composable
private fun OptionsSectionHeader(title: String) {
    Column(Modifier.padding(top = 18.dp, bottom = 2.dp)) {
        Text(
            title.uppercase(),
            color = BoneDim,
            fontSize = 11.sp,
            fontFamily = MwDisplay,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            modifier = Modifier.padding(bottom = 5.dp)
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark))
    }
}

/** A screen-routing row: element name (+ PENDING tag) over a [Top][Bottom] pill selector. */
@Composable
private fun ScreenRouteRow(el: UiElement) {
    val context = LocalContext.current
    val route by UiPreferences.routeFlow(el.key).collectAsState()

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
                val topLocked = el.lockedRoute == ScreenRoute.TOP
                OptionPill(
                    Modifier.weight(1f),
                    label = if (topLocked) "Top" else "Not yet available",
                    active = topLocked,
                    enabled = false
                ) {}
                OptionPill(
                    Modifier.weight(1f),
                    label = if (!topLocked) "Bottom" else "Not yet available",
                    active = !topLocked,
                    enabled = false
                ) {}
            } else {
                OptionPill(
                    Modifier.weight(1f),
                    label = "Top",
                    active = route == ScreenRoute.TOP,
                    enabled = true
                ) { UiPreferences.setRoute(context, el.key, ScreenRoute.TOP) }
                OptionPill(
                    Modifier.weight(1f),
                    label = "Bottom",
                    active = route == ScreenRoute.BOTTOM,
                    enabled = true
                ) { UiPreferences.setRoute(context, el.key, ScreenRoute.BOTTOM) }
            }
        }
    }
}

/** A UI-style row: element name + "Top screen selected above" note over a [Vanilla][DS] selector. */
@Composable
private fun UiStyleRow(el: UiElement) {
    val context = LocalContext.current
    val style by UiPreferences.styleFlow(el.key).collectAsState()

    Column(Modifier.fillMaxWidth().padding(vertical = 9.dp)) {
        Text(el.label, color = BoneMuted, fontSize = 13.sp, fontFamily = MwBody)
        Text(
            "Top screen selected above",
            color = BoneDim,
            fontSize = 10.sp,
            fontFamily = MwBody,
            modifier = Modifier.padding(top = 1.dp)
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OptionPill(
                Modifier.weight(1f),
                label = "Vanilla",
                active = style == UiStyle.VANILLA,
                enabled = true
            ) { UiPreferences.setStyle(context, el.key, UiStyle.VANILLA) }
            OptionPill(
                Modifier.weight(1f),
                label = "DS",
                active = style == UiStyle.DS,
                enabled = true
            ) { UiPreferences.setStyle(context, el.key, UiStyle.DS) }
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
