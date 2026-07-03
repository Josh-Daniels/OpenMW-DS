package org.openmw.companion

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** One captured interior map segment plus the interior's mBounds min corner (world units),
 *  needed to compute the player's position within the segment for centering/zoom. */
data class InteriorSegment(val bitmap: Bitmap, val boundsMinX: Float, val boundsMinY: Float)

/**
 * The single source of truth for live game state. The LogReader writes to it;
 * any Compose UI (on either screen) reads from it. Being a plain object means
 * it survives Activity/Service boundaries, which matters when we later move the
 * second-screen rendering into a foreground service.
 */
object GameStateRepository {
    private val _state = MutableStateFlow(GameState())
    val state: StateFlow<GameState> = _state.asStateFlow()

    // Map textures keyed by (segX, segY) for exterior cells, and a separate
    // slot for the interior map (isInterior != 0).  The companion app shows
    // whichever bitmap matches the player's current cell.
    private val _exteriorMapBitmaps = MutableStateFlow<Map<Pair<Int,Int>, Bitmap>>(emptyMap())
    // OpenMW proactively captures a 3×3 grid around the player; keep a
    // slightly larger window here so boundary transitions are seamless.
    private const val MAX_EXTERIOR_SEGMENTS = 25
    val exteriorMapBitmaps: StateFlow<Map<Pair<Int,Int>, Bitmap>> = _exteriorMapBitmaps.asStateFlow()

    // Interior cells are divided into segments the same way exterior cells are
    // (any interior whose bounds exceed one map-world-size tile gets more than
    // one); key by (segX, segY) so multiple segments don't overwrite each other.
    private val _interiorMapBitmaps = MutableStateFlow<Map<Pair<Int, Int>, InteriorSegment>>(emptyMap())
    private const val MAX_INTERIOR_SEGMENTS = 25
    val interiorMapBitmaps: StateFlow<Map<Pair<Int, Int>, InteriorSegment>> = _interiorMapBitmaps.asStateFlow()

    // In-game Hide UI state (OpenMW's mHudEnabled), pushed from native via
    // EngineActivity.onHudVisibilityChanged whenever the player toggles Hide UI.
    // Defaults to visible; used to sync the Alpha3 touch-control overlay with the
    // game's own HUD toggle.
    private val _hudVisible = MutableStateFlow(true)
    val hudVisible: StateFlow<Boolean> = _hudVisible.asStateFlow()

    fun setHudVisible(visible: Boolean) {
        _hudVisible.value = visible
    }

    // Transient detail-popup contents, populated on demand by a CMP:info request
    // and its COMPANION_INFO reply. null = no popup showing. Kept separate from
    // the live GameState so opening the popup never interferes with stat updates.
    private val _itemInfo = MutableStateFlow<ItemInfo?>(null)
    val itemInfo: StateFlow<ItemInfo?> = _itemInfo.asStateFlow()

    fun dismissItemInfo() {
        _itemInfo.value = null
    }

    // Active dialogue topic list for the bottom-screen overlay. Streamed from the
    // engine (COMPANION_DIALOGUE_START/_TOPIC/_END) whenever the topic list changes,
    // and emptied on COMPANION_DIALOGUE_CLOSED. Empty list = no active dialogue.
    // Kept separate from GameState (transient, like itemInfo / the map bitmaps).
    private val _dialogueTopics = MutableStateFlow<List<String>>(emptyList())
    val dialogueTopics: StateFlow<List<String>> = _dialogueTopics.asStateFlow()

    // Service entries (Barter/Spells/Travel/...) for the current NPC, streamed
    // separately from topics (COMPANION_DIALOGUE_SERVICES_*). Empty = hide the
    // Services section. Also cleared on COMPANION_DIALOGUE_CLOSED.
    private val _dialogueServices = MutableStateFlow<List<String>>(emptyList())
    val dialogueServices: StateFlow<List<String>> = _dialogueServices.asStateFlow()

    // NPC name header ("" = no active dialogue) + accumulated response history for the
    // left column. Cleared on COMPANION_DIALOGUE_NPC (new actor) and _CLOSED.
    private val _dialogueNpcName = MutableStateFlow("")
    val dialogueNpcName: StateFlow<String> = _dialogueNpcName.asStateFlow()
    private val _dialogueHistory = MutableStateFlow<List<DialogueSay>>(emptyList())
    val dialogueHistory: StateFlow<List<DialogueSay>> = _dialogueHistory.asStateFlow()

    // Active question/answer choices. Non-empty = the UI shows choices instead of the
    // normal topics/services list. Cleared on COMPANION_DIALOGUE_CLOSED.
    private val _dialogueChoices = MutableStateFlow<List<DialogueChoice>>(emptyList())
    val dialogueChoices: StateFlow<List<DialogueChoice>> = _dialogueChoices.asStateFlow()

    // Current NPC disposition (0-100) for the conversation disposition bar. -1 = unknown
    // (not an NPC, or not yet received). Set from COMPANION_DIALOGUE_DISPOSITION; reset to
    // -1 on a new actor (COMPANION_DIALOGUE_NPC) and on _CLOSED.
    private val _dialogueDisposition = MutableStateFlow(-1)
    val dialogueDisposition: StateFlow<Int> = _dialogueDisposition.asStateFlow()

    // Player gold during dialogue, for the persuasion popup's Gold readout. -1 = not yet
    // received (callers fall back to the inventory gold_001 count). Set from
    // COMPANION_DIALOGUE_GOLD; reset to -1 on new actor and _CLOSED.
    private val _dialogueGold = MutableStateFlow(-1)
    val dialogueGold: StateFlow<Int> = _dialogueGold.asStateFlow()

    // Whether this NPC offers persuasion (drives the bottom-screen persuasion popup). Set
    // from the COMPANION_DIALOGUE_PERSUADE_AVAILABLE flag inside the services block;
    // committed on SERVICES_END, reset on new actor and _CLOSED.
    private val _dialoguePersuadeAvailable = MutableStateFlow(false)
    val dialoguePersuadeAvailable: StateFlow<Boolean> = _dialoguePersuadeAvailable.asStateFlow()

    // Accumulates journal entries across JOURNAL_START / JOURNAL_ENTRY / JOURNAL_END lines.
    private var journalBuffer: MutableList<JournalEntry>? = null

    // Known dialogue topics (with their seen responses), exported natively on
    // CMP:refreshTopics. Empty = not yet loaded; native side emits alphabetically
    // sorted, so we just store in received order. Transient, refreshed on demand
    // when the TOPICS tab is viewed. Streamed one line each (TOPICS_START /
    // TOPIC_START / TOPIC_ENTRY / TOPIC_END / TOPICS_END).
    private val _journalTopics = MutableStateFlow<List<TopicInfo>>(emptyList())
    val journalTopics: StateFlow<List<TopicInfo>> = _journalTopics.asStateFlow()
    private var topicsBuffer: MutableList<TopicInfo>? = null
    private var currentTopicName: String = ""
    private var currentTopicEntries: MutableList<TopicEntry>? = null

    // Set of finished (completed) quest ids, exported natively on CMP:questStatus
    // (androidmain.cpp). Kept separate from GameState (transient, refreshed on demand
    // when the Journal tab is viewed). Ids match JournalEntry.questId (RefId text form).
    private val _finishedQuestIds = MutableStateFlow<Set<String>>(emptySet())
    val finishedQuestIds: StateFlow<Set<String>> = _finishedQuestIds.asStateFlow()
    // Accumulates FINISHED_START / FINISHED_QUEST / FINISHED_END lines.
    private var finishedQuestBuffer: MutableSet<String>? = null

    // Accumulates inventory across INVENTORY_START / INVENTORY_ITEM / INVENTORY_END
    // lines. Inventory is streamed per-item because one combined line can exceed
    // the engine's 4096-byte stdout flush and arrive truncated (see companion.lua).
    private var inventoryBuffer: MutableList<InventoryItem>? = null

    // --- Looting/pickpocketing container session (COMPANION_CONTAINER_*) ---
    // null = no container open. OPEN sets the header (name/isCorpse) and starts a
    // fresh item buffer; ITEM/END stream the contents (re-emitted on every change,
    // so END rebuilds the session atomically); CLOSED clears it. The header fields
    // persist across re-emits (which send ITEM/END without a new OPEN).
    private val _containerSession = MutableStateFlow<ContainerSession?>(null)
    val containerSession: StateFlow<ContainerSession?> = _containerSession.asStateFlow()
    private var containerBuffer: MutableList<InventoryItem>? = null
    private var containerName: String = ""
    private var containerIsCorpse: Boolean = false
    private var containerIsPickpocket: Boolean = false

    // Accumulates dialogue topics across DIALOGUE_START / DIALOGUE_TOPIC / DIALOGUE_END.
    private var dialogueBuffer: MutableList<String>? = null

    // Accumulates services across DIALOGUE_SERVICES_START / DIALOGUE_SERVICE / DIALOGUE_SERVICES_END.
    private var dialogueServiceBuffer: MutableList<String>? = null

    // Persuade-availability flag seen within the current SERVICES_START/_END block;
    // committed to _dialoguePersuadeAvailable on SERVICES_END.
    private var dialoguePersuadePending = false

    // In-flight NPC response: topic title + physical lines, committed to history on SAY_END.
    private var sayTopicBuffer: String = ""
    private var sayLineBuffer: MutableList<String>? = null

    // Accumulates choices across DIALOGUE_CHOICE_START / DIALOGUE_CHOICE / DIALOGUE_CHOICE_END.
    private var dialogueChoiceBuffer: MutableList<DialogueChoice>? = null

    // --- Streamed character-description batch (COMPANION_CHARDETAIL_*) ---
    // Descriptions arrive on their own stream, separate from COMPANION_CHARACTER
    // (which rebuilds the attribute/skill lists without descriptions). We buffer
    // an in-flight batch, then keep the last completed one so it can be re-merged
    // whenever a fresh COMPANION_CHARACTER replaces those lists.
    private class DetailBuilder {
        val attrDesc = HashMap<String, String>()
        val attrSkills = HashMap<String, List<String>>()
        val attrIcon = HashMap<String, String>()
        val skillDesc = HashMap<String, String>()
        val skillAttr = HashMap<String, String>()
        val skillSpec = HashMap<String, String>()
        val skillIcon = HashMap<String, String>()
        var healthDesc = ""
        var magickaDesc = ""
        var fatigueDesc = ""
        var raceDesc = ""
        var raceSkills: List<String> = emptyList()
        var raceAbilities: List<String> = emptyList()
        var birthSignDesc = ""
        var birthSignSpells: List<String> = emptyList()
        var birthSignTexture = ""
        var classDesc = ""
        var classSpec = ""
        var classAttrs: List<String> = emptyList()
        var classMajor: List<String> = emptyList()
        var classMinor: List<String> = emptyList()
        var levelProgress = 0
        var levelTotal = 0
    }
    private var detailBuffer: DetailBuilder? = null
    private var lastDetail: DetailBuilder? = null

    // Last-seen player standing (reputation/bounty/factions), streamed on its own
    // COMPANION_PLAYER_STATUS line. Like lastDetail, it's re-merged onto every fresh
    // COMPANION_CHARACTER (which rebuilds the character without these fields).
    private var lastPlayerStatus: LogParser.PlayerStatus? = null

    /** Folds the last-seen player-standing values onto a (possibly rebuilt) character. */
    private fun mergePlayerStatus(ch: CharacterInfo, s: LogParser.PlayerStatus?): CharacterInfo {
        if (s == null) return ch
        return ch.copy(reputation = s.reputation, bounty = s.bounty, factions = s.factions)
    }

    /** Folds the last-seen description batch onto a (possibly freshly rebuilt) character. */
    private fun mergeDetail(ch: CharacterInfo, d: DetailBuilder?): CharacterInfo {
        if (d == null) return ch
        return ch.copy(
            attributes = ch.attributes.map { a ->
                a.copy(
                    desc = d.attrDesc[a.id] ?: a.desc,
                    governedSkills = d.attrSkills[a.id] ?: a.governedSkills,
                    icon = d.attrIcon[a.id] ?: a.icon
                )
            },
            skills = ch.skills.map { s ->
                s.copy(
                    desc = d.skillDesc[s.id] ?: s.desc,
                    governingAttribute = d.skillAttr[s.id] ?: s.governingAttribute,
                    specialization = d.skillSpec[s.id] ?: s.specialization,
                    icon = d.skillIcon[s.id] ?: s.icon
                )
            },
            healthDesc = d.healthDesc,
            magickaDesc = d.magickaDesc,
            fatigueDesc = d.fatigueDesc,
            raceDesc = d.raceDesc,
            raceSkillBonuses = d.raceSkills,
            raceAbilities = d.raceAbilities,
            birthSignDesc = d.birthSignDesc,
            birthSignSpells = d.birthSignSpells,
            birthSignTexture = d.birthSignTexture,
            classDesc = d.classDesc,
            classSpecialization = d.classSpec,
            classFavoredAttributes = d.classAttrs,
            classMajorSkills = d.classMajor,
            classMinorSkills = d.classMinor,
            levelProgress = d.levelProgress,
            levelTotal = d.levelTotal
        )
    }

    fun update(transform: (GameState) -> GameState) {
        _state.update(transform)
    }

    /**
     * Called from JNI (render thread) when a map segment has been rendered.
     * Flips the image vertically (OpenGL origin is bottom-left) and stores
     * the resulting bitmap for the MapPanel to display.
     */
    fun onMapTexture(
        width: Int, height: Int, segX: Int, segY: Int, isInterior: Int,
        boundsMinX: Float, boundsMinY: Float, rgba: ByteArray
    ) {
        // Convert RGBA bytes to Android ARGB_8888 pixel array.
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val r = rgba[i * 4].toInt() and 0xFF
            val g = rgba[i * 4 + 1].toInt() and 0xFF
            val b = rgba[i * 4 + 2].toInt() and 0xFF
            val a = rgba[i * 4 + 3].toInt() and 0xFF
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        val raw = Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        // Flip vertically: OpenGL row 0 = bottom, Android bitmap row 0 = top.
        val flipMatrix = Matrix().apply { preScale(1f, -1f) }
        val bmp = Bitmap.createBitmap(raw, 0, 0, width, height, flipMatrix, false)
        raw.recycle()

        if (isInterior != 0) {
            if (segX == 0 && segY == 0) {
                // requestInteriorMap() always requests (0,0) first for any interior-entry
                // cycle (fresh bounds/segments computed from scratch each time), so its
                // arrival is a reliable "start of a new capture batch" signal — unlike the
                // COMPANION_STATS cell-name transition (see below), which runs on its own
                // 0.1s Lua timer and isn't ordered relative to when segments actually render.
                _interiorMapBitmaps.value = mapOf(Pair(0, 0) to InteriorSegment(bmp, boundsMinX, boundsMinY))
                // Also drop stale exterior segments here: state.cellIsExterior only flips
                // once the next COMPANION_STATS line arrives (its own async 0.1s timer), so
                // there's a window right after entering an interior where MapPanel would
                // still see cellIsExterior=true and render a leftover exterior segment
                // instead of the interior capture that just started.
                _exteriorMapBitmaps.value = emptyMap()
            } else {
                _interiorMapBitmaps.update { current ->
                    val updated = current + (Pair(segX, segY) to InteriorSegment(bmp, boundsMinX, boundsMinY))
                    if (updated.size <= MAX_INTERIOR_SEGMENTS) updated
                    else updated.entries.drop(updated.size - MAX_INTERIOR_SEGMENTS).associate { it.key to it.value }
                }
            }
        } else {
            _exteriorMapBitmaps.update { current ->
                val updated = current + (Pair(segX, segY) to bmp)
                if (updated.size <= MAX_EXTERIOR_SEGMENTS) updated
                else updated.entries.drop(updated.size - MAX_EXTERIOR_SEGMENTS).associate { it.key to it.value }
            }
        }
    }

    /** Substring after a COMPANION_CHARDETAIL_* prefix, trimmed. */
    private fun detailPayload(line: String, prefix: String): String =
        line.substring(line.indexOf(prefix) + prefix.length).trim()

    // The same COMPANION_ lines arrive from BOTH the in-process JNI sink and the
    // LogReader file-tail fallback (the engine still writes them to openmw.log). Most
    // state is idempotent so double-processing was invisible — but dialogueHistory
    // appends, so every topic response was added twice (greetings self-corrected via
    // the NPC-clear that precedes them). Gate the tail: only let it through when the
    // JNI sink has gone quiet, so each line is handled once while the sink is healthy,
    // and the tail still takes over if the sink ever stalls.
    // How long after the last JNI line the tail stays suppressed. STATS ticks every
    // ~100ms so the sink keeps this fresh during play; 1.5s tolerates a brief stall.
    private const val TAIL_FALLBACK_DELAY_MS = 1500L
    @Volatile private var lastJniLineMs = 0L

    /** In-process JNI sink (primary path, engine thread). Always processed. */
    fun onJniLine(line: String) {
        lastJniLineMs = System.currentTimeMillis()
        onRawLine(line)
    }

    /** Log-tail fallback (LogReader). Suppressed while the JNI sink is delivering. */
    fun onTailLine(line: String) {
        if (System.currentTimeMillis() - lastJniLineMs < TAIL_FALLBACK_DELAY_MS) return
        onRawLine(line)
    }

    /** Called for every COMPANION_* log line (via onJniLine / onTailLine). */
    fun onRawLine(line: String) {
        val trimmed = line.trimEnd()
        if (trimmed.contains("COMPANION_DEBUG")) Log.d("CompanionRepo", trimmed)
        when {
            trimmed.contains(LogParser.P_JOURNAL_START) -> {
                journalBuffer = mutableListOf()
            }
            trimmed.contains(LogParser.P_JOURNAL_ENTRY) -> {
                journalBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_JOURNAL_ENTRY) + LogParser.P_JOURNAL_ENTRY.length
                    LogParser.parseJournalEntry(trimmed.substring(idx).trim())?.let { buf.add(it) }
                }
            }
            trimmed.contains(LogParser.P_JOURNAL_END) -> {
                journalBuffer?.let { buf ->
                    _state.update { it.copy(journalEntries = buf.toList()) }
                }
                journalBuffer = null
            }
            // Finished-quest set (native). Checked before nothing else can match these;
            // "FINISHED" in the prefix keeps them from colliding with JOURNAL_START/END.
            trimmed.contains(LogParser.P_JOURNAL_FINISHED_START) -> {
                finishedQuestBuffer = mutableSetOf()
            }
            trimmed.contains(LogParser.P_JOURNAL_FINISHED_QUEST) -> {
                finishedQuestBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_JOURNAL_FINISHED_QUEST) +
                        LogParser.P_JOURNAL_FINISHED_QUEST.length
                    val id = trimmed.substring(idx).trim()
                    if (id.isNotEmpty()) buf.add(id)
                }
            }
            trimmed.contains(LogParser.P_JOURNAL_FINISHED_END) -> {
                finishedQuestBuffer?.let { _finishedQuestIds.value = it.toSet() }
                finishedQuestBuffer = null
            }
            // Known-topics batch (native, on CMP:refreshTopics). ENTRY checked first
            // (most frequent), then the per-topic and outer brackets. The trailing "S"
            // on TOPICS_* means none of these collide under contains() (see LogParser).
            trimmed.contains(LogParser.P_TOPIC_ENTRY) -> {
                currentTopicEntries?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_TOPIC_ENTRY) + LogParser.P_TOPIC_ENTRY.length
                    LogParser.parseTopicEntry(trimmed.substring(idx))?.let { buf.add(it) }
                }
            }
            trimmed.contains(LogParser.P_TOPIC_START) -> {
                val idx = trimmed.indexOf(LogParser.P_TOPIC_START) + LogParser.P_TOPIC_START.length
                currentTopicName = trimmed.substring(idx).trim()
                currentTopicEntries = mutableListOf()
            }
            trimmed.contains(LogParser.P_TOPIC_END) -> {
                val entries = currentTopicEntries
                if (entries != null) {
                    topicsBuffer?.add(TopicInfo(currentTopicName, entries.toList()))
                }
                currentTopicName = ""
                currentTopicEntries = null
            }
            trimmed.contains(LogParser.P_TOPICS_START) -> {
                topicsBuffer = mutableListOf()
                currentTopicName = ""
                currentTopicEntries = null
            }
            trimmed.contains(LogParser.P_TOPICS_END) -> {
                topicsBuffer?.let { _journalTopics.value = it.toList() }
                topicsBuffer = null
                currentTopicName = ""
                currentTopicEntries = null
            }
            trimmed.contains(LogParser.P_INVENTORY_ITEM) -> {
                inventoryBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_INVENTORY_ITEM) + LogParser.P_INVENTORY_ITEM.length
                    LogParser.parseInventoryItem(trimmed.substring(idx).trim())?.let { buf.add(it) }
                }
            }
            trimmed.contains(LogParser.P_INVENTORY_START) -> {
                inventoryBuffer = mutableListOf()
            }
            trimmed.contains(LogParser.P_INVENTORY_END) -> {
                inventoryBuffer?.let { buf ->
                    _state.update { it.copy(inventory = buf.toList()) }
                }
                inventoryBuffer = null
            }
            trimmed.contains(LogParser.P_INFO) -> {
                val idx = trimmed.indexOf(LogParser.P_INFO) + LogParser.P_INFO.length
                LogParser.parseItemInfo(trimmed.substring(idx).trim())?.let { _itemInfo.value = it }
            }
            // Container/looting session. ITEM first (most frequent). The buffer is
            // created lazily on the first ITEM so re-emits (which send ITEM/END with
            // no fresh OPEN) still assemble correctly. None of these prefixes are a
            // contains()-substring of another (END vs CLOSED differ past the underscore).
            trimmed.contains(LogParser.P_CONTAINER_ITEM) -> {
                val buf = containerBuffer ?: mutableListOf<InventoryItem>().also { containerBuffer = it }
                val idx = trimmed.indexOf(LogParser.P_CONTAINER_ITEM) + LogParser.P_CONTAINER_ITEM.length
                LogParser.parseInventoryItem(trimmed.substring(idx).trim())?.let { buf.add(it) }
            }
            trimmed.contains(LogParser.P_CONTAINER_OPEN) -> {
                val idx = trimmed.indexOf(LogParser.P_CONTAINER_OPEN) + LogParser.P_CONTAINER_OPEN.length
                LogParser.parseContainerOpen(trimmed.substring(idx).trim())?.let {
                    containerName = it.name
                    containerIsCorpse = it.isCorpse
                    containerIsPickpocket = it.isPickpocket
                }
                containerBuffer = mutableListOf()
            }
            trimmed.contains(LogParser.P_CONTAINER_END) -> {
                val buf = containerBuffer ?: mutableListOf()
                _containerSession.value = ContainerSession(
                    containerName, containerIsCorpse, containerIsPickpocket, buf.toList(), isVisible = true
                )
                containerBuffer = null
            }
            trimmed.contains(LogParser.P_CONTAINER_CLOSED) -> {
                containerBuffer = null
                containerName = ""
                containerIsCorpse = false
                containerIsPickpocket = false
                _containerSession.value = null
            }
            // Dialogue topic list. Streamed START/TOPIC/END while a conversation is
            // open (re-sent on every topic-list change); CLOSED clears it. TOPIC
            // payloads are plain strings. Buffer until END so the UI swaps atomically.
            trimmed.contains(LogParser.P_DIALOGUE_TOPIC) -> {
                dialogueBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_DIALOGUE_TOPIC) + LogParser.P_DIALOGUE_TOPIC.length
                    val topic = trimmed.substring(idx).trim()
                    if (topic.isNotEmpty()) buf.add(topic)
                }
            }
            trimmed.contains(LogParser.P_DIALOGUE_START) -> {
                dialogueBuffer = mutableListOf()
            }
            trimmed.contains(LogParser.P_DIALOGUE_END) -> {
                dialogueBuffer?.let { buf -> _dialogueTopics.value = buf.toList() }
                dialogueBuffer = null
            }
            trimmed.contains(LogParser.P_DIALOGUE_CLOSED) -> {
                dialogueBuffer = null
                dialogueServiceBuffer = null
                sayLineBuffer = null
                dialogueChoiceBuffer = null
                _dialogueTopics.value = emptyList()
                _dialogueServices.value = emptyList()
                _dialogueNpcName.value = ""
                _dialogueHistory.value = emptyList()
                _dialogueChoices.value = emptyList()
                _dialogueDisposition.value = -1
                _dialogueGold.value = -1
                _dialoguePersuadeAvailable.value = false
                dialoguePersuadePending = false
            }
            // Disposition (0-100) for the conversation disposition bar. Matched before the
            // generic dialogue branches — its token is not a substring of any other prefix.
            trimmed.contains(LogParser.P_DIALOGUE_DISPOSITION) -> {
                val idx = trimmed.indexOf(LogParser.P_DIALOGUE_DISPOSITION) + LogParser.P_DIALOGUE_DISPOSITION.length
                trimmed.substring(idx).trim().toIntOrNull()?.let { _dialogueDisposition.value = it }
            }
            // Player gold — emitted alongside disposition; token is not a substring of any
            // other prefix, and DISPOSITION above doesn't match a GOLD line either.
            trimmed.contains(LogParser.P_DIALOGUE_GOLD) -> {
                val idx = trimmed.indexOf(LogParser.P_DIALOGUE_GOLD) + LogParser.P_DIALOGUE_GOLD.length
                trimmed.substring(idx).trim().toIntOrNull()?.let { _dialogueGold.value = it }
            }
            // Question/answer choices, streamed CHOICE_START / CHOICE:<text>|<id> / CHOICE_END.
            // The colon on CHOICE keeps it from matching CHOICE_START/_END under contains.
            trimmed.contains(LogParser.P_DIALOGUE_CHOICE) -> {
                dialogueChoiceBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_DIALOGUE_CHOICE) + LogParser.P_DIALOGUE_CHOICE.length
                    val payload = trimmed.substring(idx).trim()
                    val sep = payload.lastIndexOf('|')   // id is the last field; text may contain anything
                    if (sep > 0) {
                        val id = payload.substring(sep + 1).toIntOrNull()
                        if (id != null) buf.add(DialogueChoice(payload.substring(0, sep), id))
                    }
                }
            }
            trimmed.contains(LogParser.P_DIALOGUE_CHOICE_START) -> {
                dialogueChoiceBuffer = mutableListOf()
            }
            trimmed.contains(LogParser.P_DIALOGUE_CHOICE_END) -> {
                dialogueChoiceBuffer?.let { _dialogueChoices.value = it.toList() }
                dialogueChoiceBuffer = null
            }
            // NPC name — new conversation: set the header and clear the accumulated
            // history (emitted before the greeting's SAY lines, so this never wipes them).
            trimmed.contains(LogParser.P_DIALOGUE_NPC) -> {
                val idx = trimmed.indexOf(LogParser.P_DIALOGUE_NPC) + LogParser.P_DIALOGUE_NPC.length
                _dialogueNpcName.value = trimmed.substring(idx).trim()
                _dialogueHistory.value = emptyList()
                sayLineBuffer = null
                // Reset until the fresh DISPOSITION line for this actor arrives (emitted
                // immediately after, from setPtr → updateDisposition).
                _dialogueDisposition.value = -1
                _dialogueGold.value = -1
                // Persuade availability is re-asserted by the new actor's services block.
                _dialoguePersuadeAvailable.value = false
                dialoguePersuadePending = false
            }
            // Response text, streamed SAY_START / SAY_TOPIC / SAY_LINE* / SAY_END, then an
            // optional SAY_LINKS attached to the just-published entry. Buffer until END so
            // the history grows atomically. (Prefix colons keep _LINE/_LINKS/_TOPIC from
            // matching each other or _START/_END under contains — see LogParser.)
            trimmed.contains(LogParser.P_DIALOGUE_SAY_TOPIC) -> {
                if (sayLineBuffer != null) {
                    val idx = trimmed.indexOf(LogParser.P_DIALOGUE_SAY_TOPIC) + LogParser.P_DIALOGUE_SAY_TOPIC.length
                    sayTopicBuffer = trimmed.substring(idx).trim()
                }
            }
            trimmed.contains(LogParser.P_DIALOGUE_SAY_LINE) -> {
                sayLineBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_DIALOGUE_SAY_LINE) + LogParser.P_DIALOGUE_SAY_LINE.length
                    buf.add(trimmed.substring(idx).trimEnd())   // keep leading indentation
                }
            }
            trimmed.contains(LogParser.P_DIALOGUE_SAY_LINKS) -> {
                val idx = trimmed.indexOf(LogParser.P_DIALOGUE_SAY_LINKS) + LogParser.P_DIALOGUE_SAY_LINKS.length
                val links = trimmed.substring(idx).trim().split("|").filter { it.isNotEmpty() }.distinct()
                if (links.isNotEmpty()) {
                    _dialogueHistory.update { hist ->
                        if (hist.isEmpty()) hist
                        else hist.toMutableList().also { it[it.lastIndex] = it.last().copy(hyperlinks = links) }
                    }
                }
            }
            trimmed.contains(LogParser.P_DIALOGUE_SAY_START) -> {
                sayTopicBuffer = ""
                sayLineBuffer = mutableListOf()
            }
            trimmed.contains(LogParser.P_DIALOGUE_SAY_END) -> {
                sayLineBuffer?.let { lines ->
                    _dialogueHistory.update { it + DialogueSay(topic = sayTopicBuffer, text = lines.joinToString("\n")) }
                }
                sayLineBuffer = null
                sayTopicBuffer = ""
            }
            // In-dialogue system message box (single short line, no streaming). Append
            // immediately as an isMessage entry — no topic header, no hyperlinks.
            trimmed.contains(LogParser.P_DIALOGUE_MSG) -> {
                val idx = trimmed.indexOf(LogParser.P_DIALOGUE_MSG) + LogParser.P_DIALOGUE_MSG.length
                val msg = trimmed.substring(idx).trim()
                if (msg.isNotEmpty()) {
                    _dialogueHistory.update { it + DialogueSay(text = msg, isMessage = true) }
                }
            }
            // Service entries, streamed alongside topics. The SERVICE: colon keeps this
            // from matching SERVICES_START/_END (see prefix comment in LogParser).
            trimmed.contains(LogParser.P_DIALOGUE_SERVICE) -> {
                dialogueServiceBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_DIALOGUE_SERVICE) + LogParser.P_DIALOGUE_SERVICE.length
                    val service = trimmed.substring(idx).trim()
                    if (service.isNotEmpty()) buf.add(service)
                }
            }
            // Persuade-availability flag — emitted inside the services block, before
            // SERVICES_END. Its token is not a substring of any other prefix, so checking
            // it before SERVICES_START/_END is safe (and it must precede them so a
            // contains() on SERVICES_* doesn't shadow it — it doesn't here, but keep it
            // first for clarity).
            trimmed.contains(LogParser.P_DIALOGUE_PERSUADE_AVAILABLE) -> {
                dialoguePersuadePending = true
            }
            trimmed.contains(LogParser.P_DIALOGUE_SERVICES_START) -> {
                dialogueServiceBuffer = mutableListOf()
                dialoguePersuadePending = false
            }
            trimmed.contains(LogParser.P_DIALOGUE_SERVICES_END) -> {
                dialogueServiceBuffer?.let { buf -> _dialogueServices.value = buf.toList() }
                dialogueServiceBuffer = null
                _dialoguePersuadeAvailable.value = dialoguePersuadePending
            }
            // Character-description batch. Buffered, then merged into the character
            // on END (and re-merged onto any later COMPANION_CHARACTER, see below).
            trimmed.contains(LogParser.P_CHARDETAIL_START) -> {
                detailBuffer = DetailBuilder()
            }
            trimmed.contains(LogParser.P_CHARDETAIL_ATTR) -> detailBuffer?.let { b ->
                LogParser.parseDetailAttr(detailPayload(trimmed, LogParser.P_CHARDETAIL_ATTR))?.let {
                    b.attrDesc[it.id] = it.desc
                    b.attrSkills[it.id] = it.skills
                    b.attrIcon[it.id] = it.icon
                }
            }
            trimmed.contains(LogParser.P_CHARDETAIL_SKILL) -> detailBuffer?.let { b ->
                LogParser.parseDetailSkill(detailPayload(trimmed, LogParser.P_CHARDETAIL_SKILL))?.let {
                    b.skillDesc[it.id] = it.desc
                    b.skillAttr[it.id] = it.attr
                    b.skillSpec[it.id] = it.spec
                    b.skillIcon[it.id] = it.icon
                }
            }
            trimmed.contains(LogParser.P_CHARDETAIL_DYN) -> detailBuffer?.let { b ->
                LogParser.parseDetailDyn(detailPayload(trimmed, LogParser.P_CHARDETAIL_DYN))?.let {
                    when (it.first) {
                        "health" -> b.healthDesc = it.second
                        "magicka" -> b.magickaDesc = it.second
                        "fatigue" -> b.fatigueDesc = it.second
                    }
                }
            }
            trimmed.contains(LogParser.P_CHARDETAIL_RACE) -> detailBuffer?.let { b ->
                LogParser.parseDetailRace(detailPayload(trimmed, LogParser.P_CHARDETAIL_RACE))?.let {
                    b.raceDesc = it.desc
                    b.raceSkills = it.skills
                    b.raceAbilities = it.abilities
                }
            }
            trimmed.contains(LogParser.P_CHARDETAIL_BIRTHSIGN) -> detailBuffer?.let { b ->
                LogParser.parseDetailBirthSign(detailPayload(trimmed, LogParser.P_CHARDETAIL_BIRTHSIGN))?.let {
                    b.birthSignDesc = it.desc
                    b.birthSignSpells = it.spells
                    b.birthSignTexture = it.texture
                }
            }
            trimmed.contains(LogParser.P_CHARDETAIL_CLASS) -> detailBuffer?.let { b ->
                LogParser.parseDetailClass(detailPayload(trimmed, LogParser.P_CHARDETAIL_CLASS))?.let {
                    b.classDesc = it.desc
                    b.classSpec = it.spec
                    b.classAttrs = it.attrs
                    b.classMajor = it.major
                    b.classMinor = it.minor
                }
            }
            trimmed.contains(LogParser.P_CHARDETAIL_LEVEL) -> detailBuffer?.let { b ->
                LogParser.parseDetailLevel(detailPayload(trimmed, LogParser.P_CHARDETAIL_LEVEL))?.let {
                    b.levelProgress = it.first
                    b.levelTotal = it.second
                }
            }
            trimmed.contains(LogParser.P_CHARDETAIL_END) -> {
                detailBuffer?.let { b ->
                    lastDetail = b
                    _state.update { it.copy(character = mergeDetail(it.character, b)) }
                }
                detailBuffer = null
            }
            // Player standing (reputation/bounty/factions). Merged onto the character
            // now and re-merged on each fresh CHARACTER (which rebuilds it without these).
            // Checked before P_CHARACTER: "CHARACTER" is not a substring of this prefix,
            // but keeping it above avoids any future contains() ambiguity.
            trimmed.contains(LogParser.P_PLAYER_STATUS) -> {
                val idx = trimmed.indexOf(LogParser.P_PLAYER_STATUS) + LogParser.P_PLAYER_STATUS.length
                LogParser.parsePlayerStatus(trimmed.substring(idx).trim())?.let { ps ->
                    lastPlayerStatus = ps
                    _state.update { it.copy(character = mergePlayerStatus(it.character, ps)) }
                }
            }
            // A fresh COMPANION_CHARACTER rebuilds attributes/skills from scratch
            // (no descriptions), so re-apply the last description batch on top.
            trimmed.contains(LogParser.P_CHARACTER) -> {
                _state.update { cur ->
                    val next = LogParser.parseLine(trimmed, cur) ?: cur
                    val merged = mergeDetail(next.character, lastDetail)
                    next.copy(character = mergePlayerStatus(merged, lastPlayerStatus))
                }
            }
            // Note: interior segment cleanup happens in onMapTexture (keyed off segment
            // (0,0) arrival), not here — the STATS line and the native map-capture
            // pipeline are two independent async streams with no ordering guarantee
            // between them, so clearing based on this cell-name transition raced with
            // (and could wipe) a freshly-captured interior bitmap.
            else -> _state.update { cur -> LogParser.parseLine(trimmed, cur) ?: cur }
        }
    }
}
