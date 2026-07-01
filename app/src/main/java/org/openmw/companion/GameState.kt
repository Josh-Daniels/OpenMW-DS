package org.openmw.companion

/**
 * Typed representation of everything the Lua mod exports to openmw.log.
 * One immutable snapshot of the player's current state.
 */

data class Dynamic(val current: Float, val max: Float) {
    /** 0f..1f fill ratio, guarded against divide-by-zero. */
    val ratio: Float get() = if (max > 0f) (current / max).coerceIn(0f, 1f) else 0f
}

data class Vec3(val x: Float, val y: Float, val z: Float)

data class InventoryItem(
    val id: String,
    /** Per-stack instance identifier (OpenMW item.id); empty if unavailable. */
    val stackId: String = "",
    val name: String = "",
    val count: Int = 1,
    val category: String = "misc",
    val icon: String = "",
    /** Pre-formatted stat value for display (e.g. "2-15", "30"); "" = no stat. */
    val statVal: String = "",
    /** Pre-formatted stat label (e.g. "SLASH", "ARMOR"); "" = no stat. */
    val statKey: String = "",
    /** Condition ratio 0..1; null = item has no durability (no cond bar). */
    val cond: Float? = null
)

data class SpellEntry(
    val id: String,
    val name: String = "",
    val type: String = "spell"
)

data class ActiveEffect(val name: String, val harmful: Boolean)

/** Current combat/crosshair target, shown as a name + health bar on the HUD. */
data class TargetInfo(val name: String, val health: Dynamic)

data class AttributeStat(val id: String, val name: String, val current: Float, val base: Float)

/** category: "major", "minor", or "misc" per the player's class. */
data class SkillStat(val id: String, val name: String, val value: Float, val category: String)

data class CharacterInfo(
    val name: String = "",
    val race: String = "",
    val className: String = "",
    val birthSign: String = "",
    val level: Int = 0,
    val attributes: List<AttributeStat> = emptyList(),
    val skills: List<SkillStat> = emptyList()
)

data class JournalEntry(
    val questId: String,
    val questName: String = "",  // display name from core.dialogue; empty = fall back to prettified ID
    val text: String,
    val day: Int,
    val month: Int,
    val dayOfMonth: Int
)

data class GameState(
    val health: Dynamic = Dynamic(0f, 0f),
    val magicka: Dynamic = Dynamic(0f, 0f),
    val fatigue: Dynamic = Dynamic(0f, 0f),
    val cell: String = "—",
    val pos: Vec3 = Vec3(0f, 0f, 0f),
    /** True when the player is in an exterior cell. */
    val cellIsExterior: Boolean = false,
    /** Exterior cell grid X coordinate (meaningful only when cellIsExterior). */
    val cellGridX: Int = 0,
    /** Exterior cell grid Y coordinate (meaningful only when cellIsExterior). */
    val cellGridY: Int = 0,
    /** Player yaw in radians (Z-axis Euler angle from self.rotation.z). */
    val rotZ: Float = 0f,
    val spells: List<SpellEntry> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val equipment: Map<String, String> = emptyMap(),
    val selectedSpell: String? = null,
    val activeEffects: List<ActiveEffect> = emptyList(),
    val journalEntries: List<JournalEntry> = emptyList(),
    val character: CharacterInfo = CharacterInfo(),
    /** Current combat target under the crosshair; null = no target. */
    val target: TargetInfo? = null,
    /** Wall-clock time we last parsed a STATS line; 0 = no data yet. */
    val lastUpdateMs: Long = 0L
) {
    val hasData: Boolean get() = lastUpdateMs > 0L
}
