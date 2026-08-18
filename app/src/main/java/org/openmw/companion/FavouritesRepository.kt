package org.openmw.companion

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class FavSlot(val id: String, val name: String)

data class Favourites(
    val gear: List<FavSlot?> = emptyList(),
    val magic: List<FavSlot?> = emptyList()
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

    // The legacy (pre-per-character) slot keys. Deliberately still just the original two per
    // category: this list is ONLY used by migrateLegacyIfNeeded, and the old global scheme never
    // had more than two. It is not the current storage width — that is FAV_SLOTS_MAX.
    private val SLOT_KEYS = listOf("gear_0", "gear_1", "magic_0", "magic_1")

    // FULL-WIDTH model: always FAV_SLOTS_MAX per category, regardless of what is shown.
    private var all = Favourites(blank(), blank())

    // Visible counts, applied as a truncation of `all` when publishing to [state].
    private var gearVisible = FAV_SLOTS_DEFAULT
    private var magicVisible = FAV_SLOTS_DEFAULT

    // Seeded with the SAME truncation publish() applies, not the full width: `state` is read before
    // init() on the very first frame, and an untruncated seed would briefly let the favourite menu
    // offer more slots than the HUD is showing.
    private val _state = MutableStateFlow(
        Favourites(all.gear.take(gearVisible), all.magic.take(magicVisible))
    )

    /** Favourites as the UI should see them — already truncated to the visible counts. */
    val state: StateFlow<Favourites> = _state.asStateFlow()

    private fun blank(): List<FavSlot?> = List(FAV_SLOTS_MAX) { null }

    /** Re-publish `all` truncated to the visible counts. */
    private fun publish() {
        _state.value = Favourites(
            gear = all.gear.take(gearVisible),
            magic = all.magic.take(magicVisible)
        )
    }

    /**
     * Set how many slots each category shows. Cheap and idempotent, so it can be driven straight
     * from the preference flows. Does NOT touch storage — see the class note on why lowering the
     * count must not delete anything.
     */
    fun setVisibleCounts(gear: Int, magic: Int) {
        val g = gear.coerceIn(0, FAV_SLOTS_MAX)
        val m = magic.coerceIn(0, FAV_SLOTS_MAX)
        if (g == gearVisible && m == magicVisible) return
        gearVisible = g
        magicVisible = m
        publish()
    }

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
        all = loadFor(p, currentCharacter)
        publish()
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
        all = loadFor(p, character)
        publish()
    }

    /** Index of the first free VISIBLE gear slot, or -1 when they are all occupied (or none are
     *  shown). Searches the visible range only, so a hidden slot is never auto-filled. */
    fun firstEmptyGearIndex(): Int = _state.value.gear.indexOfFirst { it == null }

    /** Index of the first free VISIBLE magic slot, or -1. See [firstEmptyGearIndex]. */
    fun firstEmptyMagicIndex(): Int = _state.value.magic.indexOfFirst { it == null }

    /**
     * Assign a gear favourite to an explicit slot. The old auto-pick-first-empty
     * behaviour silently overwrote slot 0 once both were full (slot 1 could never
     * be replaced) — callers now choose the index so the user stays in control.
     */
    fun assignGear(context: Context, slot: FavSlot, index: Int) {
        if (currentCharacter.isBlank()) return
        val idx = index.coerceIn(0, FAV_SLOTS_MAX - 1)
        all = all.copy(gear = all.gear.toMutableList().also { it[idx] = slot })
        publish()
        save(prefs(context), currentCharacter, "gear_$idx", slot)
    }

    fun assignMagic(context: Context, slot: FavSlot, index: Int) {
        if (currentCharacter.isBlank()) return
        val idx = index.coerceIn(0, FAV_SLOTS_MAX - 1)
        all = all.copy(magic = all.magic.toMutableList().also { it[idx] = slot })
        publish()
        save(prefs(context), currentCharacter, "magic_$idx", slot)
    }

    /** Clear a gear favourite slot (Unfavourite). */
    fun clearGear(context: Context, index: Int) {
        if (currentCharacter.isBlank()) return
        val idx = index.coerceIn(0, FAV_SLOTS_MAX - 1)
        all = all.copy(gear = all.gear.toMutableList().also { it[idx] = null })
        publish()
        clear(prefs(context), currentCharacter, "gear_$idx")
    }

    fun clearMagic(context: Context, index: Int) {
        if (currentCharacter.isBlank()) return
        val idx = index.coerceIn(0, FAV_SLOTS_MAX - 1)
        all = all.copy(magic = all.magic.toMutableList().also { it[idx] = null })
        publish()
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
        // Operates on the FULL width, not the visible view: a favourite in a hidden slot can still
        // be sold or unlearned, and if it were skipped here it would reappear stale the moment the
        // player raised the slot count again.
        val cur = all
        var gear = cur.gear
        var magic = cur.magic
        if (inventoryIds != null) {
            gear = gear.map { s -> if (s != null && s.id !in inventoryIds) null else s }
        }
        if (spellIds != null) {
            magic = magic.map { s -> if (s != null && s.id !in spellIds) null else s }
        }
        if (gear == cur.gear && magic == cur.magic) return
        all = Favourites(gear, magic)
        publish()
        // Persist the pruned slots.
        val p = prefs(context)
        gear.forEachIndexed { i, slot ->
            if (slot == null) clear(p, currentCharacter, "gear_$i") else save(p, currentCharacter, "gear_$i", slot)
        }
        magic.forEachIndexed { i, slot ->
            if (slot == null) clear(p, currentCharacter, "magic_$i") else save(p, currentCharacter, "magic_$i", slot)
        }
    }

    // ---- storage helpers ----

    /** Loads the FULL width (FAV_SLOTS_MAX per category), never the visible count — see the class
     *  note. Slots beyond what is currently shown are still read, so raising the count restores
     *  them without a reload. */
    private fun loadFor(p: SharedPreferences, character: String): Favourites {
        if (character.isBlank()) return Favourites(blank(), blank())
        return Favourites(
            gear  = List(FAV_SLOTS_MAX) { load(p, character, "gear_$it") },
            magic = List(FAV_SLOTS_MAX) { load(p, character, "magic_$it") }
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
