package org.openmw.companion

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import org.openmw.Constants
import java.io.File
import java.util.UUID

/**
 * The reserved quest id every manual journal entry is filed under.
 *
 * Real quest ids are dialogue-topic `RefId::serializeText()` values — for the `StringRefId`s
 * Morrowind uses that is the raw ESM topic string — so a leading/trailing double underscore
 * cannot realistically collide with content. Nothing derives meaning from the id beyond identity:
 * it is what [JournalQuestList]'s LazyColumn keys on, what [JournalQuestDetail] filters by, and
 * what QuestPrefsRepository's hidden/followed sets store, so the pseudo-quest gets hide/follow
 * for free by simply being a quest id like any other.
 *
 * It is deliberately NOT in `finishedQuestIds`, so "Manual Entries" always reads as active.
 */
const val MANUAL_QUEST_ID = "__openmw_ds_manual__"

/**
 * Display name for the pseudo-quest. Fed into the quest list as a synthetic [QuestInfo] name and
 * shown verbatim, exactly as a real quest's QS_Name is — so this string is what appears.
 */
const val MANUAL_QUEST_NAME = "Manual Entries"

/**
 * One player-written journal entry.
 *
 * Deliberately a WRAPPER rather than extra fields on [JournalEntry]: that type is parser output
 * describing what the game exported, and it should stay that. The two fields here are ones the
 * game model has no slot for —
 *
 * [id] is a stable local UUID, the address for delete (and, later, edit). Position in a list is
 * not usable as an address because the merged journal is rebuilt on every journal tick.
 *
 * [createdAt] is wall-clock, used only to order several entries written on the SAME in-game day.
 * In-game time does not advance while the player is typing on the bottom screen, so [day] alone
 * cannot order them.
 */
data class CustomJournalEntry(
    val id: String,
    val text: String,
    val day: Int,
    val month: Int,
    val dayOfMonth: Int,
    val createdAt: Long
) {
    /**
     * Project into the shape the journal renderer already consumes, so a manual entry flows
     * through `journalDateLabel`, `JournalColumn`, `JournalQuestDetail` and `journalAnnotated`
     * identically to a real one — no parallel rendering path, no styling to keep in step.
     */
    fun toJournalEntry(): JournalEntry = JournalEntry(
        questId = MANUAL_QUEST_ID,
        questName = MANUAL_QUEST_NAME,
        text = text,
        day = day,
        month = month,
        dayOfMonth = dayOfMonth
    )
}

/**
 * Player-written ("manual") journal entries, stored as JSON on EXTERNAL storage and bucketed by
 * the per-save token from `companion.lua`.
 *
 * WHY NOT SharedPreferences, unlike [FavouritesRepository] / [QuestPrefsRepository]: those live in
 * app-private storage, which survives an app UPDATE but is wiped by an uninstall, a
 * signature-mismatch install, or an app-data clear. (The Alpha3 migration guard exists precisely
 * because that store proved unreliable across reinstall events for real users.) Manual journal
 * entries are player-authored content — losing them on a reinstall is not acceptable — so they
 * live under [Constants.USER_FILE_STORAGE], the same shared-storage root the saves themselves use,
 * which Android does not touch on uninstall (only `Android/data/<pkg>` and `Android/obb/<pkg>`).
 *
 * WHY BUCKETED BY SAVE TOKEN, not character name: character name is the only save identity the
 * sibling repositories have, and it means two saves of the same character share one bucket. For a
 * favourites slot that is untidy; for journal entries it would be wrong — notes from one
 * playthrough would appear, dated to days that never happened, in a parallel save. The token comes
 * from COMPANION_SAVE_ID (minted in `companion.lua`, round-tripped through the .omwsave via
 * onSave/onLoad), so it identifies the playthrough rather than the character.
 *
 * Storage shape (`<external>/OpenMW-DS/companion/journal_notes.json`):
 * ```
 * { "version": 1, "lastSaveId": "<token>", "saves": { "<token>": [ {entry}, ... ] } }
 * ```
 * `lastSaveId` plays the same first-frame role `last_character` does in the sibling repos: the
 * live token does not arrive until the player script's first onActive, so the last-known bucket is
 * shown until then rather than an empty journal that fills in a moment later.
 */
object CustomJournalRepository {

    private const val TAG = "CustomJournal"
    private const val SCHEMA_VERSION = 1

    private const val K_VERSION = "version"
    private const val K_LAST_SAVE_ID = "lastSaveId"
    private const val K_SAVES = "saves"
    private const val K_ID = "id"
    private const val K_TEXT = "text"
    private const val K_DAY = "day"
    private const val K_MONTH = "month"
    private const val K_DAY_OF_MONTH = "dayOfMonth"
    private const val K_CREATED_AT = "createdAt"

    /** Entries of the CURRENTLY selected save bucket, oldest first. */
    private val _state = MutableStateFlow<List<CustomJournalEntry>>(emptyList())
    val state: StateFlow<List<CustomJournalEntry>> = _state.asStateFlow()

    // Every bucket, keyed by save token. Held whole because the file is rewritten whole: a partial
    // write would drop other saves' entries.
    //
    // TOUCHED FROM TWO THREADS: setSaveId is driven by COMPANION_SAVE_ID and so runs on the JNI
    // line-delivery thread, while add/delete run on the UI thread. Every public mutator is
    // therefore @Synchronized on this object. _state is a StateFlow and safe on its own; this
    // lock exists for the map and its lists.
    private val buckets = mutableMapOf<String, MutableList<CustomJournalEntry>>()

    // The bucket currently projected into _state. Empty until a token is known; every mutator
    // no-ops while empty so nothing is ever written into a junk "" bucket.
    private var currentSaveId: String = ""

    private var loaded = false

    // Writes are serialized and off the main thread. The document is always written WHOLE from the
    // in-memory snapshot, so a queued write is idempotent and the last one always wins.
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val writeMutex = Mutex()

    private val file: File
        get() = File(File(Constants.USER_FILE_STORAGE, "companion"), "journal_notes.json")

    /**
     * Synchronous first-frame load, mirroring the sibling repositories: the journal can be opened
     * on the very first frame, so the last-known bucket must already be in [state] rather than
     * appearing a frame later. The file holds a handful of short entries, so this is cheap.
     *
     * Idempotent — safe to call from a composable `remember`.
     */
    @Synchronized
    fun init() {
        if (loaded) return
        loaded = true
        runCatching { readFile() }
            .onFailure { Log.e(TAG, "Failed to read $file", it) }
        _state.value = bucketList(currentSaveId)
    }

    /**
     * Point the repository at [saveId]'s bucket. Driven by COMPANION_SAVE_ID, which the player
     * script emits on every onActive — i.e. at game start and again after every load, so loading a
     * different save at runtime re-buckets automatically. Idempotent.
     */
    @Synchronized
    fun setSaveId(saveId: String) {
        if (saveId.isBlank() || saveId == currentSaveId) return
        init()
        currentSaveId = saveId
        _state.value = bucketList(saveId)
        persist()
    }

    /**
     * Append a new manual entry stamped with [day]/[month]/[dayOfMonth].
     *
     * The caller must pass the CURRENT in-game date (COMPANION_GAMEDATE), never a value derived
     * from existing journal entries: `day` is the monotonic DaysPassed counter and is what the
     * chronological view groups pages by, so a stale or synthesised value would either merge the
     * entry into the wrong day or strand it on a phantom page.
     *
     * No-ops on blank text or while no save token is known.
     */
    @Synchronized
    fun add(text: String, day: Int, month: Int, dayOfMonth: Int) {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || currentSaveId.isBlank()) return
        val entry = CustomJournalEntry(
            id = UUID.randomUUID().toString(),
            text = trimmed,
            day = day,
            month = month,
            dayOfMonth = dayOfMonth,
            createdAt = System.currentTimeMillis()
        )
        val bucket = buckets.getOrPut(currentSaveId) { mutableListOf() }
        bucket.add(entry)
        bucket.sortWith(ENTRY_ORDER)
        _state.value = bucket.toList()
        persist()
    }

    /** Remove the entry with [entryId] from the current bucket. No-op if it isn't there. */
    @Synchronized
    fun delete(entryId: String) {
        if (currentSaveId.isBlank()) return
        val bucket = buckets[currentSaveId] ?: return
        if (!bucket.removeAll { it.id == entryId }) return
        _state.value = bucket.toList()
        persist()
    }

    // ---- ordering ----

    // In-game day first, then wall-clock. Several entries written on one in-game day keep the
    // order they were typed in — in-game time does not advance while the keyboard is open.
    private val ENTRY_ORDER =
        compareBy<CustomJournalEntry> { it.day }.thenBy { it.createdAt }

    private fun bucketList(saveId: String): List<CustomJournalEntry> =
        if (saveId.isBlank()) emptyList() else buckets[saveId]?.toList() ?: emptyList()

    // ---- storage ----

    private fun readFile() {
        val f = file
        if (!f.exists()) return
        val root = JSONObject(f.readText())
        // No migration branch yet — version is written so a future shape change has something to
        // switch on rather than having to guess at the format.
        currentSaveId = root.optString(K_LAST_SAVE_ID, "")
        val saves = root.optJSONObject(K_SAVES) ?: return
        for (key in saves.keys()) {
            val arr = saves.optJSONArray(key) ?: continue
            val list = mutableListOf<CustomJournalEntry>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val text = o.optString(K_TEXT, "")
                if (text.isEmpty()) continue
                list.add(
                    CustomJournalEntry(
                        // A pre-id file could only come from a build that never shipped, but a
                        // missing id would make the entry undeletable — mint one rather than drop it.
                        id = o.optString(K_ID, "").ifEmpty { UUID.randomUUID().toString() },
                        text = text,
                        day = o.optInt(K_DAY, 0),
                        month = o.optInt(K_MONTH, 1),
                        dayOfMonth = o.optInt(K_DAY_OF_MONTH, 1),
                        createdAt = o.optLong(K_CREATED_AT, 0L)
                    )
                )
            }
            list.sortWith(ENTRY_ORDER)
            buckets[key] = list
        }
    }

    private fun snapshot(): String {
        val saves = JSONObject()
        buckets.forEach { (saveId, list) ->
            // Don't persist emptied buckets — a delete that empties a save shouldn't leave a husk
            // in the file forever.
            if (list.isEmpty()) return@forEach
            val arr = JSONArray()
            list.forEach { e ->
                arr.put(
                    JSONObject()
                        .put(K_ID, e.id)
                        .put(K_TEXT, e.text)
                        .put(K_DAY, e.day)
                        .put(K_MONTH, e.month)
                        .put(K_DAY_OF_MONTH, e.dayOfMonth)
                        .put(K_CREATED_AT, e.createdAt)
                )
            }
            saves.put(saveId, arr)
        }
        return JSONObject()
            .put(K_VERSION, SCHEMA_VERSION)
            .put(K_LAST_SAVE_ID, currentSaveId)
            .put(K_SAVES, saves)
            .toString()
    }

    /**
     * Write the whole document off the main thread.
     *
     * The snapshot is taken SYNCHRONOUSLY at call time, so the bytes queued always match the state
     * the UI just showed — serializing inside the coroutine would race a subsequent edit and could
     * persist a newer state under an older write's turn. The mutex then guarantees the writes land
     * in call order.
     */
    private fun persist() {
        val json = snapshot()
        ioScope.launch {
            writeMutex.withLock {
                runCatching { writeAtomically(json) }
                    .onFailure { Log.e(TAG, "Failed to write $file", it) }
            }
        }
    }

    // Written to a sibling ".part" then renamed, so a process kill mid-write can never leave a
    // truncated JSON document that the next launch would fail to parse (the same discipline the
    // game-font cache uses).
    private fun writeAtomically(json: String) {
        val f = file
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".part")
        tmp.writeText(json)
        if (!tmp.renameTo(f)) {
            // Rename can fail if the destination exists on some filesystems; fall back to an
            // explicit replace rather than losing the write.
            f.delete()
            if (!tmp.renameTo(f)) {
                f.writeText(json)
                tmp.delete()
            }
        }
    }
}
