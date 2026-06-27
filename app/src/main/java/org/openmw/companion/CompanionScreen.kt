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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
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

// Map
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.viewinterop.AndroidView
import kotlin.math.abs

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
    }
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
                    val worn = state.equipment.values.toSet()
                    val (equipped, rest) = state.inventory.partition { worn.contains(it.id) }
                    equipped + rest
                }
                LazyHorizontalGrid(
                    rows = GridCells.Adaptive(64.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    gridItems(ordered) { item ->
                        val worn = state.equipment.values.contains(item.id)
                        ItemCell(
                            label = prettify(item.id),
                            count = item.count,
                            worn = worn,
                            onTap = {
                                if (worn) CompanionActions.unequipItem(item.id)
                                else CompanionActions.equipItem(item.id)
                            },
                            onEquipToggle = {
                                if (worn) CompanionActions.unequipItem(item.id)
                                else CompanionActions.equipItem(item.id)
                            },
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
    onTap: () -> Unit,
    onEquipToggle: () -> Unit,
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
        // Icon placeholder: the item name for now (replaced by real icons later)
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
            DropdownMenuItem(
                text = { Text(if (worn) "Unequip" else "Equip", fontFamily = MwBody) },
                onClick = { menuOpen = false; onEquipToggle() }
            )
            DropdownMenuItem(
                text = { Text("Drop", fontFamily = MwBody) },
                onClick = { menuOpen = false; onDrop() }
            )
        }
    }
}

/* ---- Magic (list of spells) ---- */

@Composable
private fun MagicPanel(state: GameState) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(top = TOP_BAR_SPACE.dp, bottom = BOTTOM_BAR_SPACE.dp, start = 12.dp, end = 12.dp)
    ) {
        if (state.spells.isEmpty()) {
            EmptyPanel("No spells known")
        } else {
            // Pin the selected spell to the top; keep the rest in their existing order.
            val ordered = remember(state.spells, state.selectedSpell) {
                val sel = state.selectedSpell
                if (sel != null && state.spells.contains(sel))
                    listOf(sel) + state.spells.filter { it != sel }
                else state.spells
            }
            Column(Modifier.fillMaxSize().mwPanel().padding(8.dp)) {
                Text("Spells", color = BronzeLight, fontSize = 14.sp,
                    fontFamily = MwDisplay, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 6.dp, bottom = 6.dp))
                Box(Modifier.fillMaxWidth().height(1.dp).background(BronzeDark))
                LazyColumn(Modifier.fillMaxSize()) {
                    items(ordered) { spell ->
                        SpellRow(
                            title = prettify(spell),
                            selected = spell == state.selectedSpell
                        ) {
                            CompanionActions.selectSpell(spell)
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

/* ---- Journal (deferred: no readable-journal data via stable Lua yet) ---- */

@Composable
private fun JournalPanel() {
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
            Text("Journal", color = Bone, fontSize = 20.sp,
                fontFamily = MwDisplay, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            Text("Awaiting a way to read quest entries",
                color = BoneDim, fontSize = 13.sp, fontFamily = MwBody,
                textAlign = TextAlign.Center)
        }
    }
}

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
        Tab.values().forEach { t ->
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
