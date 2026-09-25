package org.openmw.companion

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a favourite slot holds. Until Sep 2026 this was implied by WHICH list the slot sat in;
 *  mixed mode (see [UiPreferences.favMixedFlow]) lets either side hold either, so each assignment
 *  now carries its own kind and every consumer branches on that rather than on the list. */
enum class FavKind { GEAR, MAGIC;
    val storageValue: String get() = if (this == GEAR) "gear" else "magic"
    companion object {
        fun fromStorage(v: String?, fallback: FavKind) = when (v) {
            "gear" -> GEAR
            "magic" -> MAGIC
            else -> fallback
        }
    }
}

/**
 * The two on-screen favourite groups, by screen position.
 *
 * [LEFT] is the bottom-left group (captioned FAV. SPELLS in the default layout) and [RIGHT] the
 * bottom-right one (FAV. GEAR). The names are deliberately positional rather than by content: in
 * mixed mode a side has no fixed content, and the side is what the player actually picks.
 *
 * **The storage keys still read `magic_N` (left) and `gear_N` (right).** That is not an oversight:
 * keeping them is what lets every existing favourite load untouched, with no migration, and the
 * mapping is fixed here in one place.
 */
enum class FavSide { LEFT, RIGHT;
    /** Historic key prefix for this side, and the kind a slot here defaults to when its stored
     *  kind is missing (i.e. every favourite written before mixed mode existed). */
    val keyPrefix: String get() = if (this == LEFT) "magic" else "gear"
    val defaultKind: FavKind get() = if (this == LEFT) FavKind.MAGIC else FavKind.GEAR
}

/**
 * One favourite assignment.
 *
 * [category] is the coarse item category from Lua's `itemCategory()` (gear only; spells have no
 * category and leave it blank). It is stored so [FavouritesRepository.reconcile] can tell a
 * CONSUMABLE favourite from an equipment one **without the item being in the inventory** — a potion
 * you have just drunk the last of is absent from the export, and the whole point is to keep its
 * slot assigned. Defaults to "" for back-compat: favourites written before Sep 2026 have no stored
 * category and load blank, which reconcile then fills in the first time it sees the item in the
 * inventory (the "Present: adopt the live category" branch there).
 *
 * [kind] says whether this is an item or a spell. It defaults to GEAR only so the constructor stays
 * usable positionally; every real call site passes it, and [FavouritesRepository.load] defaults a
 * kind-less stored row from the side it was found on.
 */
data class FavSlot(
    val id: String,
    val name: String,
    val category: String = "",
    val kind: FavKind = FavKind.GEAR
)

/**
 * Both groups, already truncated to their visible counts.
 *
 * Named by SIDE, not by content. [left] is what used to be called `magic` and [right] what used to
 * be `gear`; with mixed mode either can now hold either kind.
 */
data class Favourites(
    val left: List<FavSlot?> = emptyList(),
    val right: List<FavSlot?> = emptyList()
) {
    fun side(s: FavSide): List<FavSlot?> = if (s == FavSide.LEFT) left else right

    /** True when [id] occupies a VISIBLE slot on either side. Backs the ⭐ indicator in the
     *  Inventory and Spells lists, which must find a favourite wherever the player put it. */
    fun contains(id: String): Boolean =
        left.any { it?.id == id } || right.any { it?.id == id }

    /** Every visible favourite id of one kind, across both sides. Backs the inventory sort's
     *  favourite-first tier, which only wants items. */
    fun idsOf(kind: FavKind): Set<String> =
        (left + right).mapNotNull { if (it?.kind == kind) it.id else null }.toSet()
}

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

    // FULL-WIDTH model: always FAV_SLOTS_MAX per side, regardless of what is shown.
    private var all = Favourites(blank(), blank())

    // Visible counts per SIDE, applied as a truncation of `all` when publishing to [state].
    private var leftVisible = FAV_SLOTS_DEFAULT
    private var rightVisible = FAV_SLOTS_DEFAULT

    // Seeded with the SAME truncation publish() applies, not the full width: `state` is read before
    // init() on the very first frame, and an untruncated seed would briefly let the favourite menu
    // offer more slots than the HUD is showing.
    private val _state = MutableStateFlow(
        Favourites(all.left.take(leftVisible), all.right.take(rightVisible))
    )

    /** Favourites as the UI should see them — already truncated to the visible counts. */
    val state: StateFlow<Favourites> = _state.asStateFlow()

    private fun blank(): List<FavSlot?> = List(FAV_SLOTS_MAX) { null }

    /** Re-publish `all` truncated to the visible counts. */
    private fun publish() {
        _state.value = Favourites(
            left = all.left.take(leftVisible),
            right = all.right.take(rightVisible)
        )
    }

    /**
     * Set how many slots each side shows. Cheap and idempotent, so it can be driven straight
     * from the preference flows. Does NOT touch storage — see the class note on why lowering the
     * count must not delete anything.
     */
    fun setVisibleCounts(left: Int, right: Int) {
        val l = left.coerceIn(0, FAV_SLOTS_MAX)
        val r = right.coerceIn(0, FAV_SLOTS_MAX)
        if (l == leftVisible && r == rightVisible) return
        leftVisible = l
        rightVisible = r
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

    /** Index of the first free VISIBLE slot on [side], or -1 when they are all occupied (or none
     *  are shown). Searches the visible range only, so a hidden slot is never auto-filled. */
    fun firstEmptyIndex(side: FavSide): Int = _state.value.side(side).indexOfFirst { it == null }

    /** The VISIBLE slots on [side], as the menus and the HUD see them. */
    fun visibleSlots(side: FavSide): List<FavSlot?> = _state.value.side(side)

    /** The side holding [id], and its index there, or null when it is not favourited anywhere.
     *  Searches the VISIBLE range of both sides, matching what the menus can act on. */
    fun locate(id: String): Pair<FavSide, Int>? {
        FavSide.entries.forEach { side ->
            val i = _state.value.side(side).indexOfFirst { it?.id == id }
            if (i >= 0) return side to i
        }
        return null
    }

    /**
     * Assign a favourite to an explicit slot on an explicit side. The old auto-pick-first-empty
     * behaviour silently overwrote slot 0 once both were full (slot 1 could never be replaced) —
     * callers choose both side and index so the user stays in control.
     */
    fun assign(context: Context, side: FavSide, slot: FavSlot, index: Int) {
        if (currentCharacter.isBlank()) return
        val idx = index.coerceIn(0, FAV_SLOTS_MAX - 1)
        all = withSide(side) { it.toMutableList().also { l -> l[idx] = slot } }
        publish()
        save(prefs(context), currentCharacter, "${side.keyPrefix}_$idx", slot)
    }

    /** Clear one favourite slot (Unfavourite). */
    fun clear(context: Context, side: FavSide, index: Int) {
        if (currentCharacter.isBlank()) return
        val idx = index.coerceIn(0, FAV_SLOTS_MAX - 1)
        all = withSide(side) { it.toMutableList().also { l -> l[idx] = null } }
        publish()
        clear(prefs(context), currentCharacter, "${side.keyPrefix}_$idx")
    }

    private inline fun withSide(side: FavSide, edit: (List<FavSlot?>) -> List<FavSlot?>) =
        if (side == FavSide.LEFT) all.copy(left = edit(all.left))
        else all.copy(right = edit(all.right))

    /**
     * Drop favourites that no longer exist in the loaded save. Pass the current inventory as
     * record-id → category, and the known-spell ids; a category is only pruned when its argument
     * is non-null. Callers MUST pass `null` for a category whose source list hasn't loaded yet
     * (empty inventory/spells during the save-load window) so we don't wipe favourites against
     * transiently-empty state. Because this only ever touches the *active* character's bucket,
     * it's non-destructive to other characters' favourites.
     *
     * **CONSUMABLE favourites are exempt from pruning** (Sep 2026). Equipment leaves the inventory
     * because it was dropped, sold or stolen — the assignment is genuinely dead and clearing it is
     * right. A potion or ingredient leaves because you USED it, which is the slot working as
     * intended; pruning there meant drinking your last Sujamma silently un-favourited it and you
     * had to re-assign the slot after every restock. So a slot whose stored category is usable
     * (see [isUsableCategory]) survives its id going absent, and the HUD renders it as an empty
     * slot until the player picks one up again — at which point the lookup simply starts resolving
     * and the slot repopulates itself from the same stored id. Apparatus/repair tools are in the
     * same bucket: not consumed, but equally not equipment, and a mortar left in a chest should not
     * cost you the slot either.
     *
     * The same pass LEARNS the category for any slot that loaded blank (a favourite written before
     * the field existed), so pre-existing favourites get the new behaviour as soon as the item is
     * seen in the inventory once, with no re-favouriting.
     */
    fun reconcile(context: Context, inventoryCats: Map<String, String>?, spellIds: Set<String>?) {
        if (currentCharacter.isBlank()) return
        // Operates on the FULL width, not the visible view: a favourite in a hidden slot can still
        // be sold or unlearned, and if it were skipped here it would reappear stale the moment the
        // player raised the slot count again.
        //
        // Branches PER ENTRY on its kind, not per list. Before mixed mode the gear list was pruned
        // against the inventory and the magic list against the spellbook, which was the same thing
        // only because a list could hold one kind. Now either side can hold either, so the rule
        // has to follow the assignment.
        val cur = all
        val left = cur.left.map { reconcileSlot(it, inventoryCats, spellIds) }
        val right = cur.right.map { reconcileSlot(it, inventoryCats, spellIds) }
        if (left == cur.left && right == cur.right) return
        all = Favourites(left, right)
        publish()
        // Persist the pruned slots.
        val p = prefs(context)
        FavSide.entries.forEach { side ->
            (if (side == FavSide.LEFT) left else right).forEachIndexed { i, slot ->
                val key = "${side.keyPrefix}_$i"
                if (slot == null) clear(p, currentCharacter, key) else save(p, currentCharacter, key, slot)
            }
        }
    }

    /** One slot's share of [reconcile]: keep, back-fill or prune. Null [inventoryCats]/[spellIds]
     *  means "that source has not loaded yet", so a slot of that kind is left strictly alone. */
    private fun reconcileSlot(
        s: FavSlot?,
        inventoryCats: Map<String, String>?,
        spellIds: Set<String>?
    ): FavSlot? = when {
        s == null -> null
        s.kind == FavKind.MAGIC ->
            if (spellIds == null || s.id in spellIds) s else null
        inventoryCats == null -> s
        // Present: adopt the live category. Normally a no-op; it is what back-fills a legacy slot
        // that loaded with a blank one, and it self-heals if a mod ever changes an item's type
        // between sessions.
        s.id in inventoryCats ->
            if (s.category == inventoryCats[s.id]) s
            else s.copy(category = inventoryCats.getValue(s.id))
        // Absent: consumables keep the assignment, equipment is pruned as before.
        isUsableCategory(s.category) -> s
        else -> null
    }

    // ---- storage helpers ----

    /** Loads the FULL width (FAV_SLOTS_MAX per side), never the visible count — see the class
     *  note. Slots beyond what is currently shown are still read, so raising the count restores
     *  them without a reload. */
    private fun loadFor(p: SharedPreferences, character: String): Favourites {
        if (character.isBlank()) return Favourites(blank(), blank())
        return Favourites(
            left  = List(FAV_SLOTS_MAX) { load(p, character, FavSide.LEFT, it) },
            right = List(FAV_SLOTS_MAX) { load(p, character, FavSide.RIGHT, it) }
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

    private fun load(p: SharedPreferences, character: String, side: FavSide, index: Int): FavSlot? {
        val key = "${side.keyPrefix}_$index"
        val id = p.getString(charKey(character, key, "id"), "") ?: ""
        return if (id.isNotEmpty())
            FavSlot(
                id,
                p.getString(charKey(character, key, "name"), id) ?: id,
                // Absent for favourites written before the field existed; reconcile back-fills it
                // the first time the item is seen in the inventory. See [FavSlot.category].
                p.getString(charKey(character, key, "cat"), "") ?: "",
                // Likewise absent for every favourite written before mixed mode. Defaulting from
                // the SIDE is exactly right for those: back then the left group could only hold
                // spells and the right only gear, so the side IS the kind. This is the whole
                // migration — there is no rewrite pass and no version stamp.
                FavKind.fromStorage(p.getString(charKey(character, key, "kind"), null), side.defaultKind)
            )
        else null
    }

    private fun save(p: SharedPreferences, character: String, key: String, slot: FavSlot) {
        p.edit()
            .putString(charKey(character, key, "id"), slot.id)
            .putString(charKey(character, key, "name"), slot.name)
            .putString(charKey(character, key, "cat"), slot.category)
            .putString(charKey(character, key, "kind"), slot.kind.storageValue)
            .apply()
    }

    private fun clear(p: SharedPreferences, character: String, key: String) {
        p.edit()
            .remove(charKey(character, key, "id"))
            .remove(charKey(character, key, "name"))
            .remove(charKey(character, key, "cat"))
            .remove(charKey(character, key, "kind"))
            .apply()
    }

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
