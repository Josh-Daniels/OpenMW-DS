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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid

// Splash image
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import org.openmw.R
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import kotlin.time.Duration.Companion.milliseconds


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
private val FloatStone  = Color(0xF02A2318)   // near-opaque stone for floating bars

private val HealthCol   = Color(0xFF8E2B20)   // blood red
private val MagickaCol  = Color(0xFF35608F)   // arcane blue
private val FatigueCol  = Color(0xFF4E7A3A)   // earthy green

// ---- type roles (swap to bundled Morrowind fonts later in one place) ----
private val MwDisplay = FontFamily.Serif
private val MwBody    = FontFamily.Serif
private val MwData    = FontFamily.Monospace

private const val TOP_BAR_SPACE = 96
private const val BOTTOM_BAR_SPACE = 84

private enum class Tab(val label: String) {
    MAP("Map"), INVENTORY("Inventory"), MAGIC("Magic"), JOURNAL("Journal")
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

/** Stone panel with a bronze frame — the signature Morrowind window look. */
private fun Modifier.mwPanel(): Modifier = this
    .clip(RoundedCornerShape(3.dp))
    .background(StonePanel)
    .border(2.dp, Bronze, RoundedCornerShape(3.dp))

@Composable
fun CompanionScreen() {
    val state by GameStateRepository.state.collectAsState()
    var tab by remember { mutableStateOf(Tab.MAP) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StoneDark)
    ) {
        when (tab) {
            Tab.MAP -> MapPanel(state)
            Tab.INVENTORY -> InventoryPanel(state)
            Tab.MAGIC -> MagicPanel(state)
            Tab.JOURNAL -> JournalPanel()
        }

        TopStatBar(
            state = state,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp)
        )

        BottomTabBar(
            selected = tab,
            onSelect = { tab = it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(12.dp)
        )

        var splashVisible by remember { mutableStateOf(true) }

        var now by remember { mutableStateOf(System.currentTimeMillis()) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000L.milliseconds)
                now = System.currentTimeMillis()
            }
        }

// "Live" = we've had a stats update recently. Don't require a non-empty cell here —
// the cell legitimately blanks during exterior transitions, and that must not
// count as "left the game". Use a generous window to ride out cell-load stalls.
        val live = state.lastUpdateMs > 0L && (now - state.lastUpdateMs) < 8000L

        LaunchedEffect(live) {
            if (live) {
                delay(1500L.milliseconds)
                splashVisible = false
            } else {
                splashVisible = true
            }
        }

        if (splashVisible) {
            SplashPanel()
        }
    }
}

/* ---- Splash panel for when not in game ---- */
@Composable
private fun SplashPanel() {
    Image(
        painter = painterResource(id = R.drawable.morrowind_splash),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
}

/* ---- Top stat bar (floats over all tabs) ---- */

@Composable
private fun TopStatBar(state: GameState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(3.dp))
            .background(FloatStone)
            .border(2.dp, Bronze, RoundedCornerShape(3.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CompactStat("Health", state.health, HealthCol, Modifier.weight(1f))
        CompactStat("Magicka", state.magicka, MagickaCol, Modifier.weight(1f))
        CompactStat("Fatigue", state.fatigue, FatigueCol, Modifier.weight(1f))
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

/* ---- Map: UESP web map in a WebView, follows the player ---- */

/*
// The map URL builder. If centering doesn't work, this is the ONE place to change
// the parameter scheme (e.g. oblocx/oblocy instead of x/y, or a different zoom range).
private fun uespMapUrl(x: Int, y: Int, zoom: Int): String =
    "https://kezyma.github.io/map/morrowind/map.html"

// Only recenter when the player has moved at least this far (world units),
// to avoid constant reloads. One cell is 8192 units.
private const val RECENTER_THRESHOLD = 1500f

@Composable
private fun MapPanel(state: GameState) {
    var follow by remember { mutableStateOf(true) }
    var zoom by remember { mutableIntStateOf(5) }

    // Hold the WebView so we can reload it on demand.
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    // Last position we centered on, to throttle reloads.
    var lastX by remember { mutableStateOf(Int.MIN_VALUE) }
    var lastY by remember { mutableStateOf(Int.MIN_VALUE) }

    Box(
        Modifier
            .fillMaxSize()
            .padding(top = TOP_BAR_SPACE.dp, bottom = BOTTOM_BAR_SPACE.dp, start = 12.dp, end = 12.dp)
    ) {
        Column(Modifier.fillMaxSize().mwPanel().padding(4.dp)) {

            // The web map itself
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(2.dp)),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            webViewClient = WebViewClient()
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.userAgentString =
                                "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
                            webViewRef.value = this
                            val px = state.pos.x.toInt()
                            val py = state.pos.y.toInt()
                            loadUrl(uespMapUrl(px, py, zoom))
                            lastX = px; lastY = py
                        }
                    }
                )
            }

            // Control strip
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MapButton(if (follow) "Following" else "Free", filled = follow) {
                    follow = !follow
                    if (follow) {
                        // snap back to player immediately
                        val px = state.pos.x.toInt(); val py = state.pos.y.toInt()
                        webViewRef.value?.loadUrl(uespMapUrl(px, py, zoom))
                        lastX = px; lastY = py
                    }
                }
                MapButton("Recenter", filled = false) {
                    val px = state.pos.x.toInt(); val py = state.pos.y.toInt()
                    webViewRef.value?.loadUrl(uespMapUrl(px, py, zoom))
                    lastX = px; lastY = py
                }
            }
        }
    }

    // When following, recenter on meaningful movement.
    LaunchedEffect(state.pos.x, state.pos.y, follow) {
        if (!follow) return@LaunchedEffect
        val px = state.pos.x.toInt()
        val py = state.pos.y.toInt()
        if (abs(px - lastX) > RECENTER_THRESHOLD || abs(py - lastY) > RECENTER_THRESHOLD) {
            webViewRef.value?.loadUrl(uespMapUrl(px, py, zoom))
            lastX = px; lastY = py
        }
    }
}

*/
/* ---- Map: coordinate readout (web/bundled map deferred) ---- */

@Composable
private fun MapPanel(state: GameState) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(top = TOP_BAR_SPACE.dp, bottom = BOTTOM_BAR_SPACE.dp, start = 12.dp, end = 12.dp)
    ) {
        Column(
            Modifier.fillMaxSize().mwPanel().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                if (state.hasData) state.cell else "—",
                color = Bone, fontSize = 24.sp, fontFamily = MwDisplay,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "x ${state.pos.x.toInt()}    y ${state.pos.y.toInt()}    z ${state.pos.z.toInt()}",
                color = BoneDim, fontSize = 14.sp, fontFamily = MwData
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Local map to be inscribed here",
                color = BronzeDark, fontSize = 12.sp, fontFamily = MwBody
            )
        }
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

/* ---- Inventory: paper-doll + grid of framed slots ---- */

@Composable
private fun InventoryPanel(state: GameState) {
    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = TOP_BAR_SPACE.dp, bottom = BOTTOM_BAR_SPACE.dp, start = 12.dp, end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        PaperDoll(Modifier.weight(0.38f).fillMaxHeight())

        Box(Modifier.weight(0.62f).fillMaxHeight().mwPanel().padding(6.dp)) {
            if (state.inventory.isEmpty()) {
                EmptyPanel("No inventory recorded")
            } else {
                val ordered = remember(state.inventory, state.equipment) {
                    val wornIds = state.equipment.values.toSet()

                    // Build equipped items sorted by slot order
                    val slotToItemId = state.equipment  // Map<slotName, itemId>
                    val equippedSorted = EQUIPMENT_SLOT_ORDER
                        .mapNotNull { slot -> slotToItemId[slot] }
                        .mapNotNull { itemId -> state.inventory.find { it.id == itemId } }
                        .distinctBy { it.id }

                    // Remaining unequipped items alphabetically
                    val categoryOrder = EQUIPMENT_SLOT_ORDER + listOf("misc")

                    val rest = state.inventory
                        .filter { !wornIds.contains(it.id) }
                        .sortedWith(
                            compareBy(
                                { categoryOrder.indexOf(it.category).let { i -> if (i < 0) Int.MAX_VALUE else i } },
                                { it.displayName().lowercase() }
                            )
                        )

                    equippedSorted + rest
                }
                LazyHorizontalGrid(
                    rows = GridCells.Adaptive(64.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    gridItems(ordered) { item ->
                        val worn = state.equipment.values.contains(item.id)
                        val readable = item.category == "book" || item.category == "scroll"
                        val equippable = item.category != "misc" && !readable
                        ItemCell(
                            label = item.displayName(),
                            count = item.count,
                            worn = worn,
                            equippable = equippable,
                            readable = readable,
                            onTap = {
                                when {
                                    readable -> CompanionActions.readItem(item.id)
                                    equippable && worn -> CompanionActions.unequipItem(item.id)
                                    equippable -> CompanionActions.equipItem(item.id)
                                }
                            },
                            onEquipToggle = {
                                if (!equippable) return@ItemCell
                                if (worn) CompanionActions.unequipItem(item.id)
                                else CompanionActions.equipItem(item.id)
                            },
                            onRead = { CompanionActions.readItem(item.id) },
                            onDrop = { CompanionActions.dropItem(item.id, item.count) }
                        )
                    }
                }
            }
        }
    }
}

/** Empty character frame — where the paper-doll image will go. */
@Composable
private fun PaperDoll(modifier: Modifier = Modifier) {
    Box(modifier = modifier.mwPanel().padding(8.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // simple silhouette placeholder
            Box(Modifier.size(30.dp).clip(CircleShape).background(BronzeDark))
            Spacer(Modifier.height(5.dp))
            Box(
                Modifier
                    .size(width = 46.dp, height = 58.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BronzeDark)
            )
            Spacer(Modifier.height(12.dp))
            Text("Character", color = BoneDim, fontSize = 12.sp, fontFamily = MwDisplay)
            Text("paper doll", color = BronzeDark, fontSize = 10.sp, fontFamily = MwBody)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ItemCell(
    label: String,
    count: Int,
    worn: Boolean,
    equippable: Boolean,
    readable: Boolean,
    onTap: () -> Unit,
    onEquipToggle: () -> Unit,
    onRead: () -> Unit,
    onDrop: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(2.dp))
            .background(if (worn) SlotWorn else SlotBg)
            .border(
                if (worn) 2.dp else 1.dp,
                if (worn) BronzeLight else BronzeDark,
                RoundedCornerShape(2.dp)
            )
            .combinedClickable(onClick = onTap, onLongClick = { menuOpen = true })
            .padding(4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (worn) BronzeLight else Bone,
            fontSize = 10.sp,
            fontFamily = MwBody,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 11.sp
        )
        if (count > 1) {
            Text(
                "$count",
                color = BoneDim,
                fontSize = 9.sp,
                fontFamily = MwData,
                modifier = Modifier.align(Alignment.BottomEnd)
            )
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            properties = PopupProperties(focusable = false)
        ) {
            if (worn || equippable) {
                DropdownMenuItem(
                    text = { Text(if (worn) "Unequip" else "Equip", fontFamily = MwBody) },
                    onClick = { menuOpen = false; onEquipToggle() }
                )
            }
            if (readable) {
                DropdownMenuItem(
                    text = { Text("Read", fontFamily = MwBody) },
                    onClick = { menuOpen = false; onRead() }
                )
            }
            DropdownMenuItem(
                text = { Text("Drop", fontFamily = MwBody) },
                onClick = { menuOpen = false; onDrop() }
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
            .padding(top = TOP_BAR_SPACE.dp, bottom = BOTTOM_BAR_SPACE.dp, start = 12.dp, end = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // ---- Active spell panel ----
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .mwPanel()
                .padding(horizontal = 14.dp, vertical = 10.dp)
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
            val powers = remember(state.spells) {
                state.spells.filter { it.type == "power" }.sortedBy { it.id.lowercase() }
            }
            val spells = remember(state.spells) {
                state.spells.filter { it.type == "spell" }.sortedBy { it.id.lowercase() }
            }
            val scrolls = remember(state.spells) {
                state.spells.filter { it.type == "scroll" }.sortedBy { it.id.lowercase() }
            }

            Column(Modifier.weight(1f).mwPanel().padding(8.dp)) {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (powers.isNotEmpty()) {
                        item {
                            SpellSectionHeader("Powers")
                        }
                        items(powers) { spell ->
                            SpellRow(
                                title = spell.displayName(),
                                selected = spell.id == sel
                            ) { CompanionActions.selectSpell(spell.id) }
                        }
                    }
                    if (spells.isNotEmpty()) {
                        item {
                            SpellSectionHeader("Spells")
                        }
                        items(spells) { spell ->
                            SpellRow(
                                title = spell.displayName(),
                                selected = spell.id == sel
                            ) { CompanionActions.selectSpell(spell.id) }
                        }
                    }
                    if (scrolls.isNotEmpty()) {
                        item {
                            SpellSectionHeader("Scrolls")
                        }
                        items(scrolls) { spell ->
                            SpellRow(
                                title = spell.displayName(),
                                selected = false
                            ) { CompanionActions.selectSpell(spell.id) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpellRow(title: String, selected: Boolean = false, onTap: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (selected) SlotWorn else Color.Transparent)
                .clickable { onTap() }
                .padding(horizontal = 8.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, color = if (selected) BronzeLight else Bone,
                fontSize = 15.sp, fontFamily = MwBody,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark))
    }
}

/* ---- Journal ---- */

private sealed class JournalNav {
    object Journal : JournalNav()
    object QuestList : JournalNav()
    data class QuestDetail(val questId: String) : JournalNav()
}

private fun morrowindMonthName(month: Int): String = listOf(
    "Morning Star", "Sun's Dawn", "First Seed", "Rain's Hand",
    "Second Seed", "Midyear", "Sun's Height", "Last Seed",
    "Hearthfire", "Frostfall", "Sun's Dusk", "Evening Star"
).getOrElse(month - 1) { "Month $month" }

@Composable
private fun JournalPanel() {
    val state by GameStateRepository.state.collectAsState()
    var nav by remember { mutableStateOf<JournalNav>(JournalNav.Journal) }

    LaunchedEffect(Unit) { CompanionActions.refreshJournal() }

    Box(
        Modifier
            .fillMaxSize()
            .padding(top = TOP_BAR_SPACE.dp, bottom = BOTTOM_BAR_SPACE.dp, start = 12.dp, end = 12.dp)
    ) {
        if (state.journalEntries.isEmpty()) {
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
        } else {
            when (val n = nav) {
                is JournalNav.Journal ->
                    JournalChronological(state.journalEntries) { nav = JournalNav.QuestList }
                is JournalNav.QuestList ->
                    JournalQuestList(
                        entries = state.journalEntries,
                        onBack = { nav = JournalNav.Journal },
                        onQuest = { nav = JournalNav.QuestDetail(it) }
                    )
                is JournalNav.QuestDetail ->
                    JournalQuestDetail(
                        questId = n.questId,
                        entries = state.journalEntries.filter { it.questId == n.questId },
                        onBack = { nav = JournalNav.QuestList }
                    )
            }
        }
    }
}

@Composable
private fun JournalNavBar(left: String?, onLeft: (() -> Unit)?, center: String, right: String?, onRight: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (left != null && onLeft != null)
            Text("◀ $left", color = BronzeLight, fontSize = 12.sp, fontFamily = MwBody,
                modifier = Modifier.clickable { onLeft() }.padding(4.dp))
        else
            Spacer(Modifier.size(1.dp))
        Text(center, color = Bone, fontSize = 15.sp, fontFamily = MwDisplay, fontWeight = FontWeight.SemiBold)
        if (right != null && onRight != null)
            Text("$right ▶", color = BronzeLight, fontSize = 12.sp, fontFamily = MwBody,
                modifier = Modifier.clickable { onRight() }.padding(4.dp))
        else
            Spacer(Modifier.size(1.dp))
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark))
}

@Composable
private fun JournalChronological(entries: List<JournalEntry>, onQuestsClick: () -> Unit) {
    // Precompute list with date-break headers inserted whenever the date changes.
    // Each item is either (dateString, null) for a header or (null, entry) for text.
    val listItems = remember(entries) {
        buildList<Pair<String?, JournalEntry?>> {
            var lastDate = ""
            entries.forEach { e ->
                val date = "${e.dayOfMonth} ${morrowindMonthName(e.month)}"
                if (date != lastDate) { add(date to null); lastDate = date }
                add(null to e)
            }
        }
    }

    Column(Modifier.fillMaxSize().mwPanel()) {
        JournalNavBar(left = null, onLeft = null, center = "Journal", right = "Quests", onRight = onQuestsClick)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
            items(listItems, key = { (date, entry) ->
                date?.let { "hdr:$it" } ?: entry!!.run { "e:$questId:$day:$dayOfMonth:${text.length}" }
            }) { (date, entry) ->
                if (date != null) {
                    Text(date, color = BronzeLight, fontSize = 12.sp, fontFamily = MwDisplay,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 10.dp, top = 12.dp, bottom = 2.dp))
                } else if (entry != null) {
                    Text(entry.text, color = Bone, fontSize = 12.sp, fontFamily = MwBody,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun JournalQuestList(entries: List<JournalEntry>, onBack: () -> Unit, onQuest: (String) -> Unit) {
    // Most-recently-active quest first: scan in reverse to find last occurrence of each questId.
    val quests = remember(entries) {
        entries.reversed().map { it.questId }.distinct()
    }

    Column(Modifier.fillMaxSize().mwPanel()) {
        JournalNavBar(left = "Journal", onLeft = onBack, center = "Quests", right = null, onRight = null)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
            items(quests, key = { it }) { questId ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onQuest(questId) }
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(prettifyQuestId(questId), color = Bone, fontSize = 13.sp,
                        fontFamily = MwBody, modifier = Modifier.weight(1f))
                    Text("▶", color = BronzeLight, fontSize = 11.sp, fontFamily = MwBody)
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark.copy(alpha = 0.4f)))
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun JournalQuestDetail(questId: String, entries: List<JournalEntry>, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().mwPanel()) {
        JournalNavBar(left = "Quests", onLeft = onBack, center = prettifyQuestId(questId), right = null, onRight = null)
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 4.dp)) {
            items(entries, key = { e -> "e:${e.day}:${e.dayOfMonth}:${e.text.length}" }) { entry ->
                val date = "${entry.dayOfMonth} ${morrowindMonthName(entry.month)}"
                Text(date, color = BronzeLight, fontSize = 11.sp, fontFamily = MwDisplay,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 10.dp, top = 10.dp, bottom = 2.dp))
                Text(entry.text, color = Bone, fontSize = 12.sp, fontFamily = MwBody,
                    lineHeight = 17.sp,
                    modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 6.dp))
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

private fun prettifyQuestId(id: String): String =
    id.split(Regex("[\\s_]+"))
        .filter { it.isNotEmpty() }
        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }

@Composable
private fun EmptyPanel(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = BoneDim, fontSize = 15.sp, fontFamily = MwBody)
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
