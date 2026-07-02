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
 * Stores the four favourite quick-slots in SharedPreferences, **keyed by
 * character name** so each save/character has its own set.
 *
 * Because the character name isn't known on the very first frame (it arrives on
 * the first `COMPANION_CHARACTER` line), we preload the *last-known* character's
 * favourites synchronously in [init] to avoid a flash of empty pills, then swap
 * to the live character via [setCharacter] once the name is confirmed (and again
 * whenever the player loads a different save at runtime).
 *
 * Storage key scheme: `char:<name>:gear_0_id`, `char:<name>:gear_0_name`, … The
 * `char:` prefix + `:` delimiter namespaces the buckets so a character literally
 * named "gear_0" can't collide with a slot key. Legacy (pre-per-character) global
 * favourites written under bare `gear_0_id` etc. are migrated onto the first
 * character seen (see [migrateLegacyIfNeeded]).
 */
object FavouritesRepository {

    private const val PREFS = "companion_favourites"
    private const val LAST_CHARACTER = "last_character"
    private const val LEGACY_MIGRATED = "legacy_migrated"

    // The four persisted slot keys (per character).
    private val SLOT_KEYS = listOf("gear_0", "gear_1", "magic_0", "magic_1")

    private val _state = MutableStateFlow(Favourites())
    val state: StateFlow<Favourites> = _state.asStateFlow()

    // The character whose favourites are currently loaded into _state. Empty
    // until a real name is known; assigns/clears/reconcile no-op while empty so
    // we never write into a junk "char::" bucket.
    private var currentCharacter: String = ""

    /**
     * Synchronous first-frame load of the last-known character's favourites, so
     * the HUD pills aren't briefly empty on launch. The live character (which may
     * differ if the player loads another save) is applied later by [setCharacter].
     */
    fun init(context: Context) {
        val p = prefs(context)
        currentCharacter = p.getString(LAST_CHARACTER, "") ?: ""
        _state.value = loadFor(p, currentCharacter)
    }

    /**
     * Point the repository at [character]'s favourite bucket. Called reactively
     * once `state.character.name` is non-blank and again whenever it changes
     * (runtime save switch). Idempotent: a repeat call with the already-loaded
     * character is a no-op, so it won't clobber in-flight edits or cause churn on
     * every inventory tick.
     */
    fun setCharacter(context: Context, character: String) {
        if (character.isBlank() || character == currentCharacter) return
        val p = prefs(context)
        migrateLegacyIfNeeded(p, character)
        currentCharacter = character
        p.edit().putString(LAST_CHARACTER, character).apply()
        _state.value = loadFor(p, character)
    }

    /** Index of the first free gear slot, or -1 when both are occupied. */
    fun firstEmptyGearIndex(): Int = _state.value.gear.indexOfFirst { it == null }

    /** Index of the first free magic slot, or -1 when both are occupied. */
    fun firstEmptyMagicIndex(): Int = _state.value.magic.indexOfFirst { it == null }

    /**
     * Assign a gear favourite to an explicit slot. The old auto-pick-first-empty
     * behaviour silently overwrote slot 0 once both were full (slot 1 could never
     * be replaced) — callers now choose the index so the user stays in control.
     */
    fun assignGear(context: Context, slot: FavSlot, index: Int) {
        if (currentCharacter.isBlank()) return
        val idx = index.coerceIn(0, 1)
        val updated = _state.value.gear.toMutableList().also { it[idx] = slot }
        _state.value = _state.value.copy(gear = updated)
        save(prefs(context), currentCharacter, "gear_$idx", slot)
    }

    fun assignMagic(context: Context, slot: FavSlot, index: Int) {
        if (currentCharacter.isBlank()) return
        val idx = index.coerceIn(0, 1)
        val updated = _state.value.magic.toMutableList().also { it[idx] = slot }
        _state.value = _state.value.copy(magic = updated)
        save(prefs(context), currentCharacter, "magic_$idx", slot)
    }

    /** Clear a gear favourite slot (Unfavourite). */
    fun clearGear(context: Context, index: Int) {
        if (currentCharacter.isBlank()) return
        val idx = index.coerceIn(0, 1)
        val updated = _state.value.gear.toMutableList().also { it[idx] = null }
        _state.value = _state.value.copy(gear = updated)
        clear(prefs(context), currentCharacter, "gear_$idx")
    }

    fun clearMagic(context: Context, index: Int) {
        if (currentCharacter.isBlank()) return
        val idx = index.coerceIn(0, 1)
        val updated = _state.value.magic.toMutableList().also { it[idx] = null }
        _state.value = _state.value.copy(magic = updated)
        clear(prefs(context), currentCharacter, "magic_$idx")
    }

    /**
     * Drop favourites that no longer exist in the loaded save. Pass the current
     * inventory record-ids and known-spell ids; a category is only pruned when
     * its set is non-null. Callers MUST pass `null` for a category whose source
     * list hasn't loaded yet (empty inventory/spells during the save-load window)
     * so we don't wipe favourites against transiently-empty state. Because this
     * only ever touches the *active* character's bucket, it's non-destructive to
     * other characters' favourites.
     */
    fun reconcile(context: Context, inventoryIds: Set<String>?, spellIds: Set<String>?) {
        if (currentCharacter.isBlank()) return
        val cur = _state.value
        var gear = cur.gear
        var magic = cur.magic
        if (inventoryIds != null) {
            gear = gear.map { s -> if (s != null && s.id !in inventoryIds) null else s }
        }
        if (spellIds != null) {
            magic = magic.map { s -> if (s != null && s.id !in spellIds) null else s }
        }
        if (gear == cur.gear && magic == cur.magic) return
        _state.value = Favourites(gear, magic)
        // Persist the pruned slots.
        val p = prefs(context)
        listOf("gear_0" to gear[0], "gear_1" to gear[1],
               "magic_0" to magic[0], "magic_1" to magic[1]).forEach { (key, slot) ->
            if (slot == null) clear(p, currentCharacter, key)
            else save(p, currentCharacter, key, slot)
        }
    }

    // ---- storage helpers ----

    private fun loadFor(p: SharedPreferences, character: String): Favourites {
        if (character.isBlank()) return Favourites()
        return Favourites(
            gear  = listOf(load(p, character, "gear_0"),  load(p, character, "gear_1")),
            magic = listOf(load(p, character, "magic_0"), load(p, character, "magic_1"))
        )
    }

    /**
     * One-time move of pre-per-character global favourites (bare `gear_0_id` …)
     * onto the first character we see, then delete the legacy keys. Guarded by a
     * boolean flag so it runs at most once.
     */
    private fun migrateLegacyIfNeeded(p: SharedPreferences, character: String) {
        if (p.getBoolean(LEGACY_MIGRATED, false)) return
        val editor = p.edit()
        SLOT_KEYS.forEach { key ->
            val id = p.getString("${key}_id", "") ?: ""
            if (id.isNotEmpty()) {
                val name = p.getString("${key}_name", id) ?: id
                editor.putString(charKey(character, key, "id"), id)
                editor.putString(charKey(character, key, "name"), name)
            }
            editor.remove("${key}_id").remove("${key}_name")
        }
        editor.putBoolean(LEGACY_MIGRATED, true).apply()
    }

    private fun charKey(character: String, slotKey: String, suffix: String) =
        "char:$character:${slotKey}_$suffix"

    private fun load(p: SharedPreferences, character: String, key: String): FavSlot? {
        val id = p.getString(charKey(character, key, "id"), "") ?: ""
        return if (id.isNotEmpty())
            FavSlot(id, p.getString(charKey(character, key, "name"), id) ?: id)
        else null
    }

    private fun save(p: SharedPreferences, character: String, key: String, slot: FavSlot) {
        p.edit()
            .putString(charKey(character, key, "id"), slot.id)
            .putString(charKey(character, key, "name"), slot.name)
            .apply()
    }

    private fun clear(p: SharedPreferences, character: String, key: String) {
        p.edit()
            .remove(charKey(character, key, "id"))
            .remove(charKey(character, key, "name"))
            .apply()
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
