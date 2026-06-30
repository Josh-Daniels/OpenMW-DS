package org.openmw.companion

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FavSlot(val id: String, val name: String)

data class Favourites(
    val gear: List<FavSlot?> = listOf(null, null),
    val magic: List<FavSlot?> = listOf(null, null)
)

/**
 * Stores the four favourite quick-slots in SharedPreferences.
 * Favourites are a companion-app UI preference — they persist across app restarts
 * independent of live game state, so they live here rather than in GameStateRepository.
 */
object FavouritesRepository {

    private const val PREFS = "companion_favourites"

    private val _state = MutableStateFlow(Favourites())
    val state: StateFlow<Favourites> = _state.asStateFlow()

    fun init(context: Context) {
        val p = prefs(context)
        _state.value = Favourites(
            gear  = listOf(load(p, "gear_0"),  load(p, "gear_1")),
            magic = listOf(load(p, "magic_0"), load(p, "magic_1"))
        )
    }

    fun assignGear(context: Context, slot: FavSlot) {
        val idx = _state.value.gear.indexOfFirst { it == null }.takeIf { it >= 0 } ?: 0
        val updated = _state.value.gear.toMutableList().also { it[idx] = slot }
        _state.value = _state.value.copy(gear = updated)
        save(prefs(context), "gear_$idx", slot)
    }

    fun assignMagic(context: Context, slot: FavSlot) {
        val idx = _state.value.magic.indexOfFirst { it == null }.takeIf { it >= 0 } ?: 0
        val updated = _state.value.magic.toMutableList().also { it[idx] = slot }
        _state.value = _state.value.copy(magic = updated)
        save(prefs(context), "magic_$idx", slot)
    }

    private fun load(p: SharedPreferences, key: String): FavSlot? {
        val id = p.getString("${key}_id", "") ?: ""
        return if (id.isNotEmpty()) FavSlot(id, p.getString("${key}_name", id) ?: id) else null
    }

    private fun save(p: SharedPreferences, key: String, slot: FavSlot) {
        p.edit()
            .putString("${key}_id", slot.id)
            .putString("${key}_name", slot.name)
            .apply()
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
