package org.openmw.companion

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Per-character journal-quest preferences.
 *
 * [hidden] — quest ids the player has manually removed from the quest list. Vanilla Morrowind
 * leaves plenty of finished quests flagged active (the native quest list has the same problem),
 * so this is a manual tidy-up, NOT a fix for that: a hidden quest never appears in the Active or
 * All views regardless of its completion state, only in the Hidden view.
 *
 * [followed] — the single quest whose name is shown top-centre on the HUD map. At most one at a
 * time; following a different quest replaces it.
 *
 * Ids are [JournalEntry.questId] — the journal topic's RefId::serializeText(), the same identity
 * the Lua journal export and the native finished-quest export both emit, and the same one
 * `finishedQuestIds` keys on.
 */
data class QuestPrefs(
    val hidden: Set<String> = emptySet(),
    val followed: String? = null
)

/**
 * Stores [QuestPrefs] in SharedPreferences, **keyed by character name** so each save/character has
 * its own hidden set and followed quest — structurally a mirror of [FavouritesRepository], which
 * solved the same "save-specific companion state" problem first.
 *
 * As there, the character name isn't known on the very first frame (it arrives on the first
 * `COMPANION_CHARACTER` line), so [init] synchronously preloads the *last-known* character's prefs
 * and [setCharacter] swaps to the live one once confirmed (and again whenever the player loads a
 * different save at runtime).
 *
 * Storage key scheme: `char:<name>:hidden` (a String set) and `char:<name>:followed` (a String).
 * The `char:` prefix + `:` delimiter namespaces the buckets the same way favourites do.
 *
 * KNOWN LIMITATION (accepted, shared with [FavouritesRepository]): the character NAME is the only
 * save identity the companion has — two saves of the same character share one bucket.
 *
 * There is no legacy migration: this is new state with nothing to migrate from.
 */
object QuestPrefsRepository {

    private const val PREFS = "companion_quest_prefs"
    private const val LAST_CHARACTER = "last_character"

    private val _state = MutableStateFlow(QuestPrefs())
    val state: StateFlow<QuestPrefs> = _state.asStateFlow()

    // The character whose prefs are currently loaded into _state. Empty until a real name is
    // known; every mutator no-ops while empty so we never write into a junk "char::" bucket.
    private var currentCharacter: String = ""

    /**
     * Synchronous first-frame load of the last-known character's prefs, so the HUD label and the
     * quest list's stars/hidden filtering are right on the very first frame. The live character
     * (which may differ if the player loads another save) is applied later by [setCharacter].
     */
    fun init(context: Context) {
        val p = prefs(context)
        currentCharacter = p.getString(LAST_CHARACTER, "") ?: ""
        _state.value = loadFor(p, currentCharacter)
    }

    /**
     * Point the repository at [character]'s bucket. Called reactively once `state.character.name`
     * is non-blank and again whenever it changes (runtime save switch). Idempotent.
     */
    fun setCharacter(context: Context, character: String) {
        if (character.isBlank() || character == currentCharacter) return
        val p = prefs(context)
        currentCharacter = character
        p.edit().putString(LAST_CHARACTER, character).apply()
        _state.value = loadFor(p, character)
    }

    /**
     * Hide [questId] from the Active/All views.
     *
     * Hiding the quest that is currently FOLLOWED also unfollows it: the HUD label is a pointer
     * into the quest list, so leaving it pointing at something the player just removed from that
     * list would be contradictory. (The reverse is NOT true — deliberately following a quest from
     * the Hidden view is allowed and keeps it hidden: "off my list but tracked on the HUD".)
     */
    fun hide(context: Context, questId: String) {
        if (currentCharacter.isBlank() || questId.isEmpty()) return
        val cur = _state.value
        if (questId in cur.hidden && cur.followed != questId) return
        val next = QuestPrefs(
            hidden = cur.hidden + questId,
            followed = if (cur.followed == questId) null else cur.followed
        )
        commit(context, next)
    }

    /** Return [questId] to the Active/All views (it reappears per its actual completion state). */
    fun unhide(context: Context, questId: String) {
        if (currentCharacter.isBlank() || questId !in _state.value.hidden) return
        commit(context, _state.value.copy(hidden = _state.value.hidden - questId))
    }

    /** Show [questId]'s name on the HUD, replacing whichever quest was followed before. */
    fun follow(context: Context, questId: String) {
        if (currentCharacter.isBlank() || questId.isEmpty()) return
        if (_state.value.followed == questId) return
        commit(context, _state.value.copy(followed = questId))
    }

    /** Stop showing a followed quest on the HUD. */
    fun unfollow(context: Context) {
        if (currentCharacter.isBlank() || _state.value.followed == null) return
        commit(context, _state.value.copy(followed = null))
    }

    /**
     * Unfollow the followed quest once it has COMPLETED — same reasoning as [hide]: the HUD label
     * is a pointer into the active quest list, so leaving it on a quest that has finished points at
     * something no longer relevant.
     *
     * Unlike [reconcile] this needs no "is the data loaded yet" gate, and deliberately takes no
     * null: it only ever acts when the followed id is PRESENT in [finishedQuestIds], so a set that
     * is empty or still being assembled (the save-load window, or before the first
     * `CMP:questStatus` reply) simply does nothing. Nothing here can clear a followed quest for any
     * other reason.
     */
    fun clearFollowedIfFinished(context: Context, finishedQuestIds: Set<String>) {
        if (currentCharacter.isBlank()) return
        val followed = _state.value.followed ?: return
        if (followed !in finishedQuestIds) return
        commit(context, _state.value.copy(followed = null))
    }

    /**
     * Drop hidden/followed ids that no longer exist in the loaded save.
     *
     * [questIds] MUST be null while the journal hasn't loaded yet — the journal is empty during
     * the save-load window, and reconciling against that empty list would wipe the player's whole
     * hidden set. This is the same trap [FavouritesRepository.reconcile] guards against, and the
     * reason callers gate on `journalEntries.isNotEmpty()`.
     *
     * Only ever touches the ACTIVE character's bucket, so it's non-destructive to other saves.
     */
    fun reconcile(context: Context, questIds: Set<String>?) {
        if (currentCharacter.isBlank() || questIds == null) return
        val cur = _state.value
        val hidden = cur.hidden.filterTo(mutableSetOf()) { it in questIds }
        val followed = cur.followed?.takeIf { it in questIds }
        if (hidden == cur.hidden && followed == cur.followed) return
        commit(context, QuestPrefs(hidden, followed))
    }

    // ---- storage helpers ----

    private fun commit(context: Context, next: QuestPrefs) {
        _state.value = next
        save(prefs(context), currentCharacter, next)
    }

    private fun loadFor(p: SharedPreferences, character: String): QuestPrefs {
        if (character.isBlank()) return QuestPrefs()
        // Copy the returned set: SharedPreferences hands back its own instance, which must never
        // be mutated or stored back.
        val hidden = p.getStringSet(charKey(character, "hidden"), emptySet())?.toSet() ?: emptySet()
        val followed = p.getString(charKey(character, "followed"), "")?.takeIf { it.isNotEmpty() }
        return QuestPrefs(hidden, followed)
    }

    private fun save(p: SharedPreferences, character: String, prefs: QuestPrefs) {
        if (character.isBlank()) return
        val e = p.edit().putStringSet(charKey(character, "hidden"), prefs.hidden)
        if (prefs.followed != null) e.putString(charKey(character, "followed"), prefs.followed)
        else e.remove(charKey(character, "followed"))
        e.apply()
    }

    private fun charKey(character: String, key: String) = "char:$character:$key"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
