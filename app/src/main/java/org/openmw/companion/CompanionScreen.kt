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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
    // Load synchronously during composition (not LaunchedEffect) so favourite
    // pills show their persisted content on the very first frame, with no
    // post-composition flash of empty slots.
    remember(context) { FavouritesRepository.init(context) }

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
        if (dialogueNpcName.isNotEmpty() || dialogueTopics.isNotEmpty() ||
            dialogueServices.isNotEmpty() || dialogueHistory.isNotEmpty() ||
            dialogueChoices.isNotEmpty()
        ) {
            DialogueTopicsOverlay(dialogueNpcName, dialogueHistory, dialogueTopics, dialogueServices, dialogueChoices)
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
    choices: List<DialogueChoice>
) {
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
        // Same footprint as the Spells-tab list box: full width minus 12dp side padding,
        // top 12dp down to just above the nav bar.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 12.dp, bottom = BOTTOM_BAR_SPACE.dp, start = 12.dp, end = 12.dp)
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
                    history,
                    choices,
                    modifier = Modifier.weight(0.75f).fillMaxHeight().padding(8.dp)
                )
                Box(Modifier.fillMaxHeight().width(1.dp).background(BronzeDark))
                Column(Modifier.weight(0.25f).fillMaxHeight().padding(8.dp)) {
                    // Right column ALWAYS shows services/topics/goodbye. While a choice is
                    // active (choices non-empty) they're DIMMED and inert — the choice buttons
                    // render inline in the left column instead (see DialogueHistoryColumn).
                    val dimmed = choices.isNotEmpty()

                    // Services (fixed, no scroll; hidden when the NPC has none). Styled
                    // identically to topic rows — only the section header distinguishes them.
                    if (services.isNotEmpty()) {
                        SpellSectionHeader("SERVICES")
                        Spacer(Modifier.height(4.dp))
                        services.forEach { service ->
                            DialogueOptionRow(service, dimmed = dimmed) {
                                if (!dimmed) CompanionActions.activateDialogueService(service)
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    // Topics (fills remaining space, scrollable)
                    SpellSectionHeader("TOPICS")
                    Spacer(Modifier.height(4.dp))
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        items(topics) { topic ->
                            DialogueOptionRow(topic, dimmed = dimmed) {
                                if (!dimmed) CompanionActions.selectDialogueTopic(topic)
                            }
                        }
                    }

                    // Goodbye (fixed at the bottom of the right column)
                    Box(Modifier.fillMaxWidth().height(2.dp).background(Bronze))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(BronzeDark.copy(alpha = 0.2f))
                            .clickable { if (!dimmed) CompanionActions.dialogueGoodbye() }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "GOODBYE",
                            color = if (dimmed) BoneDim else BronzeLight, fontSize = 14.sp,
                            fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                            letterSpacing = 2.sp
                        )
                    }
                }
            }
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

/** Left column: the running NPC-response history, auto-scrolled to the newest line.
 *  When [choices] is non-empty (a mid-dialogue question) the choice buttons render
 *  inline as the LAST item of the list, directly below the most recent response. */
@Composable
private fun DialogueHistoryColumn(
    history: List<DialogueSay>,
    choices: List<DialogueChoice>,
    modifier: Modifier = Modifier
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
                    dialogueAnnotated(say.text, say.hyperlinks),
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
                            .clickable { CompanionActions.activateDialogueChoice(choice.id) }
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
 * longest-match-wins at any position so "Imperial cult" beats "Imperial".
 */
@Composable
private fun dialogueAnnotated(text: String, links: List<String>): AnnotatedString = remember(text, links) {
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
            withLink(
                LinkAnnotation.Clickable(
                    tag = "topic",
                    styles = TextLinkStyles(style = SpanStyle(color = BronzeLight))
                ) { CompanionActions.selectDialogueTopic(phrase) }
            ) {
                append(phrase)
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

        // Gear favourites — bottom-left, stacked vertically
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(start = 8.dp, bottom = 8.dp),
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
                FavSlotView(slot = slot, borderColor = BronzeLight, equipped = slotWorn) {
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

        // Magic favourites — bottom-right, stacked vertically
        Column(
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 8.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.End,
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
                FavSlotView(slot = slot, borderColor = BronzeLight, equipped = slotSelected) {
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

/* ---- Favourite slots (floating over the map) ---- */

@Composable
private fun FavSlotView(
    slot: FavSlot?,
    borderColor: Color,
    equipped: Boolean = false,
    onClick: () -> Unit
) {
    val isEmpty = slot == null
    val alpha   = if (isEmpty) 0.4f else 1f
    // Mirror the inventory worn/unworn styling: equipped favourites get the
    // bronze-tinted fill + bright border/name, non-equipped ones stay dim.
    val bgColor    = if (equipped) SlotWorn else Color(0xC0151210)
    val slotBorder = if (equipped) borderColor else BronzeDark
    val textColor  = if (equipped) BoneBright else BoneMuted

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
                .clickable(enabled = !isEmpty) { onClick() },
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
                Text(
                    label,
                    color = if (worn) BoneBright else BoneMuted,
                    fontSize = 14.sp,
                    fontFamily = MwBody,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                )
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
                text = { Text("Add to favourites", fontFamily = MwBody, fontSize = 13.sp) },
                onClick = {
                    menuOpen = false; DropdownState.closeAll()
                    FavouritesRepository.assignGear(context, FavSlot(item.id, label))
                },
                colors = menuItemColors
            )
            DropdownMenuItem(
                text = { Text("Drop", fontFamily = MwBody, fontSize = 13.sp) },
                onClick = { menuOpen = false; DropdownState.closeAll(); CompanionActions.dropItem(item.id, item.count) },
                colors = menuItemColors
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
    val context = LocalContext.current

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
                                title = spell.displayName(),
                                selected = spell.id == sel,
                                onAddToFavourites = {
                                    FavouritesRepository.assignMagic(
                                        context, FavSlot(spell.id, spell.displayName())
                                    )
                                },
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
                                title = spell.displayName(),
                                selected = spell.id == sel,
                                onAddToFavourites = {
                                    FavouritesRepository.assignMagic(
                                        context, FavSlot(spell.id, spell.displayName())
                                    )
                                },
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
                                title = spell.displayName(),
                                selected = false,
                                onAddToFavourites = {
                                    FavouritesRepository.assignMagic(
                                        context, FavSlot(spell.id, spell.displayName())
                                    )
                                },
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
    title: String,
    selected: Boolean = false,
    onAddToFavourites: (() -> Unit)? = null,
    onInfo: (() -> Unit)? = null,
    iconBitmap: ImageBitmap? = null,
    onTap: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }
    LaunchedEffect(DropdownState.closeRequest) {
        if (DropdownState.closeRequest > 0) menuOpen = false
    }
    Box {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = onTap,
                        onLongClick = { if (onAddToFavourites != null || onInfo != null) { menuOpen = true; DropdownState.open() } }
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
                Text(
                    title, color = if (selected) BoneBright else BoneMuted,
                    fontSize = 14.sp, fontFamily = MwBody,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = 12.dp)
                )
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
        if (onAddToFavourites != null || onInfo != null) {
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
                if (onAddToFavourites != null) {
                    DropdownMenuItem(
                        text = { Text("Add to favourites", fontFamily = MwBody, fontSize = 13.sp) },
                        onClick = { menuOpen = false; DropdownState.closeAll(); onAddToFavourites() },
                        colors = MenuDefaults.itemColors(textColor = Bone)
                    )
                }
            }
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
