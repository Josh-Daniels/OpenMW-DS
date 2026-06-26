package org.openmw.companion

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.ui.window.PopupProperties

/**
 * SECOND-SCREEN UI - the real layout shell.
 *
 *  +---------------------------------+
 *  | HP ###o  MP ##o  SP ####  (top) |  floating, translucent
 *  |                                 |
 *  |        central content          |  Map / Inventory / Magic / Journal
 *  |        (fills screen)           |
 *  |                                 |
 *  | [Map][Inventory][Magic][Journal]|  floating tab row (bottom)
 *  +---------------------------------+
 *
 * Top stat bar and bottom tabs are persistent overlays; only the central
 * area swaps when you tap a tab. Map is a placeholder for now.
 */

// ---- palette ----
private val Bg = Color(0xFF0B0B0D)
private val FloatBg = Color(0xCC16161A)   // translucent panel for floating bars
private val Divider = Color(0xFF26262C)
private val TextDim = Color(0xFF8A8A93)
private val TextBright = Color(0xFFEDEDF0)
private val Accent = Color(0xFFB8893A)     // muted gold, selected tab
private val HealthCol = Color(0xFFC0392B)
private val MagickaCol = Color(0xFF2E6FB0)
private val FatigueCol = Color(0xFF2E9E5B)

private const val TOP_BAR_SPACE = 92        // dp reserved so content clears the top bar
private const val BOTTOM_BAR_SPACE = 84     // dp reserved so content clears the tabs

private enum class Tab(val label: String) {
    MAP("Map"), INVENTORY("Inventory"), MAGIC("Magic"), JOURNAL("Journal")
}

@Composable
fun CompanionScreen() {
    val state by GameStateRepository.state.collectAsState()
    var tab by remember { mutableStateOf(Tab.MAP) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Bg)
    ) {
        // central content fills the whole screen, behind the floating bars
        when (tab) {
            Tab.MAP -> MapPanel(state)
            Tab.INVENTORY -> InventoryPanel(state)
            Tab.MAGIC -> MagicPanel(state)
            Tab.JOURNAL -> JournalPanel()
        }

        // floating top stat bar
        TopStatBar(
            state = state,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(12.dp)
        )

        // floating bottom tab row
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

/* ---- Top stat bar ---- */

@Composable
private fun TopStatBar(state: GameState, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(FloatBg)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        CompactStat("HP", state.health, HealthCol, Modifier.weight(1f))
        CompactStat("MP", state.magicka, MagickaCol, Modifier.weight(1f))
        CompactStat("SP", state.fatigue, FatigueCol, Modifier.weight(1f))
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
            Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                "${value.current.toInt()}/${value.max.toInt()}",
                color = TextBright,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF000000))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(value.ratio)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

/* ---- Central panels ---- */

@Composable
private fun MapPanel(state: GameState) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("MAP", color = TextDim, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(
                if (state.hasData) state.cell else "-",
                color = TextBright,
                fontSize = 26.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "x ${state.pos.x.toInt()}   y ${state.pos.y.toInt()}   z ${state.pos.z.toInt()}",
                color = TextDim,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(24.dp))
            Text("(local map goes here)", color = Color(0xFF3A3A42), fontSize = 12.sp)
        }
    }
}

@Composable
private fun InventoryPanel(state: GameState) {
    if (state.inventory.isEmpty()) {
        EmptyPanel("No inventory data yet")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = TOP_BAR_SPACE.dp,
            bottom = BOTTOM_BAR_SPACE.dp,
            start = 16.dp,
            end = 16.dp
        )
    ) {
        items(state.inventory) { item ->
            val worn = state.equipment.values.contains(item.id)
            ListRow(
                title = prettify(item.id),
                trailing = if (item.count > 1) "x${item.count}" else "",
                highlighted = worn,
                showItemMenu = true,
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

@Composable
private fun MagicPanel(state: GameState) {
    if (state.spells.isEmpty()) {
        EmptyPanel("No spell data yet")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = TOP_BAR_SPACE.dp,
            bottom = BOTTOM_BAR_SPACE.dp,
            start = 16.dp,
            end = 16.dp
        )
    ) {
        items(state.spells) { spell ->
            ListRow(
                title = prettify(spell),
                trailing = "",
                highlighted = false,
                showItemMenu = false,
                onTap = { CompanionActions.selectSpell(spell) }
            )
        }
    }
}

@Composable
private fun JournalPanel() {
    EmptyPanel("Journal - coming soon")
}

@Composable
private fun EmptyPanel(message: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(message, color = TextDim, fontSize = 16.sp)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ListRow(
    title: String,
    trailing: String,
    highlighted: Boolean,
    onTap: () -> Unit = {},
    showItemMenu: Boolean = false,
    onEquipToggle: () -> Unit = {},
    onDrop: () -> Unit = {}
) {
    var menuOpen by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .combinedClickable(
                    onClick = { onTap() },
                    onLongClick = { if (showItemMenu) menuOpen = true }
                )
                .padding(horizontal = 8.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title,
                color = if (highlighted) Accent else TextBright,
                fontSize = 16.sp,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (trailing.isNotEmpty()) {
                Text(
                    trailing,
                    color = TextDim,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Long-press menu anchored here
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false },
                properties = PopupProperties(focusable = false)

            ) {
                DropdownMenuItem(
                    text = { Text(if (highlighted) "Unequip" else "Equip") },
                    onClick = {
                        menuOpen = false
                        onEquipToggle()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Drop") },
                    onClick = {
                        menuOpen = false
                        onDrop()
                    }
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Divider)
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
            .clip(RoundedCornerShape(14.dp))
            .background(FloatBg)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Tab.values().forEach { t ->
            val isSel = t == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSel) Accent else Color.Transparent)
                    .clickable { onSelect(t) }
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    t.label,
                    color = if (isSel) Color(0xFF0B0B0D) else TextBright,
                    fontSize = 14.sp,
                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/* ---- Helpers ---- */

/** Stopgap readability: "pick_journeyman_01" -> "Pick Journeyman 01".
 *  Real display names will come from a future Lua export or a lookup table. */
private fun prettify(id: String): String =
    id.replace('_', ' ')
        .split(' ')
        .joinToString(" ") { w ->
            if (w.isNotEmpty()) w.replaceFirstChar { it.uppercase() } else w
        }