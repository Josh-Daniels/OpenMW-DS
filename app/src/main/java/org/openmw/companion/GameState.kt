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

data class InventoryItem(val id: String, val count: Int, val category: String = "misc")
data class GameState(
    val health: Dynamic = Dynamic(0f, 0f),
    val magicka: Dynamic = Dynamic(0f, 0f),
    val fatigue: Dynamic = Dynamic(0f, 0f),
    val cell: String = "—",
    val pos: Vec3 = Vec3(0f, 0f, 0f),
    val spells: List<String> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val equipment: Map<String, String> = emptyMap(),
    val selectedSpell: String? = null,
    /** Wall-clock time we last parsed a STATS line; 0 = no data yet. */
    val lastUpdateMs: Long = 0L
) {
    val hasData: Boolean get() = lastUpdateMs > 0L
}
