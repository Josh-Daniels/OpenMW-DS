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
// angle = the interior local map's rotation (radians, from the cell NorthMarker); centerX/Y =
// the rotation center in world units (mCenter). The map texture is rendered rotated by `angle`,
// so the player dot must be rotatePoint(pos, center, angle)'d and the arrow offset by `angle`
// to line up (mirrors LocalMap::worldToInteriorMapPosition / updatePlayer). Constant across all
// segments of one interior. Added July 2026 to fix interior arrow/position being unrotated.
data class InteriorSegment(
    val bitmap: Bitmap, val boundsMinX: Float, val boundsMinY: Float,
    val angle: Float = 0f, val centerX: Float = 0f, val centerY: Float = 0f
)

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

    // true while the vanilla sneak indicator (hand-reaching-into-bag icon) would show —
    // i.e. the player is sneaking AND currently undetected. Driven by the native
    // COMPANION_SNEAK_VISIBLE:true/false line emitted from HUD::setSneakVisible (the exact
    // vanilla condition, change-detected). Backs the companion HUD sneak icon. Default false
    // (game starts not sneaking).
    private val _sneakVisible = MutableStateFlow(false)
    val sneakVisible: StateFlow<Boolean> = _sneakVisible.asStateFlow()

    // true while the in-game pause/options menu (GM_MainMenu) is open. Driven by
    // COMPANION_PAUSE_MENU_OPEN / _CLOSED lines from companion.lua. Gates the
    // bottom-screen options/display-settings overlay (EngineActivity).
    private val _pauseMenuVisible = MutableStateFlow(false)
    val pauseMenuVisible: StateFlow<Boolean> = _pauseMenuVisible.asStateFlow()

    // COMPANION_TITLE_MENU_OPEN / _CLOSED lines from the ENGINE (mainmenu.cpp) — the TITLE-screen
    // main menu (no game loaded), which companion.lua can't see (it doesn't run before a game
    // exists). Also gates the options overlay (EngineActivity) so the player can set up before
    // starting a new game, and drives the one-time "welcome" header shown only on the title screen.
    private val _titleMenuVisible = MutableStateFlow(false)
    val titleMenuVisible: StateFlow<Boolean> = _titleMenuVisible.asStateFlow()

    // Transient detail-popup contents, populated on demand by a CMP:info request
    // and its COMPANION_INFO reply. null = no popup showing. Kept separate from
    // the live GameState so opening the popup never interferes with stat updates.
    private val _itemInfo = MutableStateFlow<ItemInfo?>(null)
    val itemInfo: StateFlow<ItemInfo?> = _itemInfo.asStateFlow()

    fun dismissItemInfo() {
        _itemInfo.value = null
    }

    // Controller-navigation events for the DS overlays (native COMPANION_NAV_* → parseNav). Each
    // press is stamped with an incrementing seq so identical consecutive presses are distinct
    // StateFlow values and both re-emit (see NavEvent). Consumers (per-overlay focus handlers,
    // added in later phases) collect this and move their selection / trigger the focused action.
    private var navSeq = 0L
    private val _navEvent = MutableStateFlow<NavEvent?>(null)
    val navEvent: StateFlow<NavEvent?> = _navEvent.asStateFlow()

    // Bottom-screen text-input request. Non-null = a MyGUI EditBox has key focus (native
    // COMPANION_TEXT_INPUT_OPEN); the string is the field's current caption to pre-fill.
    // null = no field focused (COMPANION_TEXT_INPUT_CLOSED) → dismiss the panel + keyboard.
    // Collected by EngineActivity to add/remove the focusable Android-keyboard panel window.
    private val _textInputRequest = MutableStateFlow<String?>(null)
    val textInputRequest: StateFlow<String?> = _textInputRequest.asStateFlow()

    fun requestTextInput(currentText: String) {
        _textInputRequest.value = currentText
    }

    fun dismissTextInput() {
        _textInputRequest.value = null
    }

    /**
     * Flip the current training session into its in-progress state (drives the "Training…" popup).
     * Called by the overlay the moment a train command is sent; cleared when COMPANION_TRAINING_CLOSED
     * nulls the session (after the native 2-hour fade/advance). No-op if there's no session.
     */
    fun markTrainingInProgress() {
        _trainingSession.value = _trainingSession.value?.copy(isTraining = true)
    }

    // --- Barter optimistic UI mutators (Phase 3 UI calls these alongside the CMP:barter_*
    // commands; the engine reconciles the authoritative balance via COMPANION_BARTER_OFFER).
    // The sim is paused during barter, so selection must feel instant rather than waiting
    // for the command round-trip. Items are matched by id (the coarse serialized RefId). ---

    /** Optimistically select/deselect a barter item and set its selected quantity. */
    fun applyBarterSelection(side: BarterSide, id: String, selected: Boolean, count: Int) {
        _barterSession.update { s ->
            if (s == null) return@update null
            val mapped = { list: List<BarterItem> ->
                list.map {
                    if (it.id == id) it.copy(
                        isSelected = selected,
                        selectedCount = if (selected) count.coerceIn(1, it.count) else 0
                    ) else it
                }
            }
            when (side) {
                BarterSide.VENDOR -> s.copy(vendorItems = mapped(s.vendorItems))
                BarterSide.PLAYER -> s.copy(playerItems = mapped(s.playerItems))
            }
        }
    }

    /** Optimistically set the manual extra-gold offset (the engine OFFER reconciles balance). */
    fun applyBarterExtraGold(extra: Int) {
        _barterSession.update { it?.copy(extraGoldOffer = extra) }
    }

    fun dismissBarterResult() {
        _barterResult.value = null
    }

    // Active dialogue topic list for the bottom-screen overlay. Streamed from the
    // engine (COMPANION_DIALOGUE_START/_TOPIC/_END) whenever the topic list changes,
    // and emptied on COMPANION_DIALOGUE_CLOSED. Empty list = no active dialogue.
    // Kept separate from GameState (transient, like itemInfo / the map bitmaps).
    private val _dialogueTopics = MutableStateFlow<List<String>>(emptyList())
    val dialogueTopics: StateFlow<List<String>> = _dialogueTopics.asStateFlow()
    // Per-topic "color topic" read-status flag (name -> 0 none / 1 Specific-unheard / 2 Exhausted-read),
    // from the <flag>|<name> prefix on each COMPANION_DIALOGUE_TOPIC line. Lets the DS topic rows be
    // coloured like the native list. Filled alongside dialogueTopics, cleared on CLOSED.
    private val _dialogueTopicFlags = MutableStateFlow<Map<String, Int>>(emptyMap())
    val dialogueTopicFlags: StateFlow<Map<String, Int>> = _dialogueTopicFlags.asStateFlow()
    private var dialogueFlagBuffer: MutableMap<String, Int>? = null

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

    // Teleport-door markers for the companion minimap (COMPANION_DOORMARKER_*), streamed
    // START/ITEM/END and buffered like the other batches. Change-detected on the Lua side.
    private val _doorMarkers = MutableStateFlow<List<DoorMarker>>(emptyList())
    val doorMarkers: StateFlow<List<DoorMarker>> = _doorMarkers.asStateFlow()
    private var doorMarkerBuffer: MutableList<DoorMarker>? = null

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

    // --- Barter session (COMPANION_BARTER_*) ---
    // null = not bartering. OPEN sets the header + starts vendor/player item buffers;
    // ITEM/END stream both sides (END rebuilds the session, preserving nothing — a fresh
    // OPEN is only sent on a new merchant); OFFER updates the running balance/gold without
    // touching items or the user's optimistic selection; ACCEPTED/REJECTED set the transient
    // result; CLOSED clears everything. Header fields persist across OFFER re-emits.
    private val _barterSession = MutableStateFlow<BarterSession?>(null)
    val barterSession: StateFlow<BarterSession?> = _barterSession.asStateFlow()
    private var barterVendorBuffer: MutableList<BarterItem>? = null
    private var barterPlayerBuffer: MutableList<BarterItem>? = null
    private var barterVendorName: String = ""
    private var barterVendorGold: Int = 0
    private var barterPlayerGold: Int = 0
    // Transient offer outcome (rejection alert / accepted-close); cleared on dismiss,
    // on the next OFFER, and on CLOSED.
    private val _barterResult = MutableStateFlow<BarterResult?>(null)
    val barterResult: StateFlow<BarterResult?> = _barterResult.asStateFlow()

    // --- Merchant repair session (COMPANION_REPAIR_*) ---
    // null = not repairing. OPEN sets the NPC name + starts the item buffer; PLAYER_GOLD sets
    // the gold; ITEM appends; END commits the session; CLOSED clears it. Re-exported (fresh
    // OPEN..END) after each repair, so END just replaces the whole session.
    private val _repairSession = MutableStateFlow<RepairSession?>(null)
    val repairSession: StateFlow<RepairSession?> = _repairSession.asStateFlow()
    private var repairItemBuffer: MutableList<RepairItem>? = null
    private var repairNpcName: String = ""
    private var repairPlayerGold: Int = 0

    // --- Travel session (COMPANION_TRAVEL_*) ---
    // null = not travelling. OPEN sets the NPC name + starts the dest buffer; PLAYER_GOLD sets the
    // gold; DEST appends; END commits; CLOSED clears. Same shape as merchant repair. PLAYER_GOLD is
    // shared with repair, so it routes to whichever export is in progress (see onRawLine).
    private val _travelSession = MutableStateFlow<TravelSession?>(null)
    val travelSession: StateFlow<TravelSession?> = _travelSession.asStateFlow()
    private var travelDestBuffer: MutableList<TravelDest>? = null
    private var travelNpcName: String = ""
    private var travelPlayerGold: Int = 0

    // --- Rest/wait session (COMPANION_SLEEP_*) ---
    // null = not resting/waiting. OPEN sets it (single line — mode + date + warning); CLOSED
    // clears it (confirming a rest/wait also closes it — the engine runs the advance on top).
    private val _sleepSession = MutableStateFlow<SleepSession?>(null)
    val sleepSession: StateFlow<SleepSession?> = _sleepSession.asStateFlow()

    // --- Training session (COMPANION_TRAINING_*) ---
    // null = not training. OPEN sets the NPC name + starts the skill buffer; PLAYER_GOLD sets the
    // gold; SKILL appends; END commits; CLOSED clears. Same shape as repair. Training is one-shot
    // (no re-export): sending a train command flips isTraining (markTrainingInProgress) to show the
    // "Training…" popup, and CLOSED (after the native fade/advance) clears the whole session.
    private val _trainingSession = MutableStateFlow<TrainingSession?>(null)
    val trainingSession: StateFlow<TrainingSession?> = _trainingSession.asStateFlow()
    private var trainingSkillBuffer: MutableList<TrainingSkill>? = null
    private var trainingNpcName: String = ""
    private var trainingPlayerGold: Int = 0

    // --- Spell-buying session (COMPANION_SPELLBUYING_*) ---
    // null = not buying. OPEN sets the NPC name + starts the spell buffer; PLAYER_GOLD sets the gold;
    // SPELL appends; END commits; CLOSED clears. Re-exported (fresh OPEN..END) after each purchase,
    // so END just replaces the whole session (the bought spell flips to known=1, keeping its slot).
    private val _spellBuyingSession = MutableStateFlow<SpellBuyingSession?>(null)
    val spellBuyingSession: StateFlow<SpellBuyingSession?> = _spellBuyingSession.asStateFlow()
    private var spellForSaleBuffer: MutableList<SpellForSale>? = null
    private var spellBuyingNpcName: String = ""
    private var spellBuyingPlayerGold: Int = 0

    // --- Dialogue-service window OPEN/CLOSED flags (COMPANION_{SPELLBUYING,TRAINING,SPELLMAKING,
    // ENCHANTING}_{OPEN,CLOSED}) ---
    // Bare booleans (no payload yet — no companion overlay for these). true while the native window
    // is up; consumed by EngineActivity.nativeServiceVanillaUp so the top-screen conversation overlay
    // steps aside when one opens over a Vanilla conversation.
    private val _spellBuyingWindowOpen = MutableStateFlow(false)
    val spellBuyingWindowOpen: StateFlow<Boolean> = _spellBuyingWindowOpen.asStateFlow()
    private val _trainingWindowOpen = MutableStateFlow(false)
    val trainingWindowOpen: StateFlow<Boolean> = _trainingWindowOpen.asStateFlow()
    private val _spellmakingWindowOpen = MutableStateFlow(false)
    val spellmakingWindowOpen: StateFlow<Boolean> = _spellmakingWindowOpen.asStateFlow()
    private val _enchantingWindowOpen = MutableStateFlow(false)
    val enchantingWindowOpen: StateFlow<Boolean> = _enchantingWindowOpen.asStateFlow()

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
        boundsMinX: Float, boundsMinY: Float, angle: Float, centerX: Float, centerY: Float, rgba: ByteArray
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
                _interiorMapBitmaps.value =
                    mapOf(Pair(0, 0) to InteriorSegment(bmp, boundsMinX, boundsMinY, angle, centerX, centerY))
                // Also drop stale exterior segments here: state.cellIsExterior only flips
                // once the next COMPANION_STATS line arrives (its own async 0.1s timer), so
                // there's a window right after entering an interior where MapPanel would
                // still see cellIsExterior=true and render a leftover exterior segment
                // instead of the interior capture that just started.
                _exteriorMapBitmaps.value = emptyMap()
            } else {
                _interiorMapBitmaps.update { current ->
                    val updated = current +
                        (Pair(segX, segY) to InteriorSegment(bmp, boundsMinX, boundsMinY, angle, centerX, centerY))
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
            // Controller-nav signals (companion-controller-nav.patch). Discrete/high-frequency
            // while a DS overlay is open, so route them first. Each maps to a NavEvent stamped with
            // a fresh seq so repeats re-emit. Non-nav lines fall through to the state parsing below.
            trimmed.contains(LogParser.P_NAV) -> {
                LogParser.parseNav(trimmed)?.let { factory -> _navEvent.value = factory(navSeq++) }
            }
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
            trimmed.contains(LogParser.P_DOORMARKER_ITEM) -> {
                doorMarkerBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_DOORMARKER_ITEM) + LogParser.P_DOORMARKER_ITEM.length
                    LogParser.parseDoorMarker(trimmed.substring(idx).trim())?.let { buf.add(it) }
                }
            }
            trimmed.contains(LogParser.P_DOORMARKER_START) -> {
                doorMarkerBuffer = mutableListOf()
            }
            trimmed.contains(LogParser.P_DOORMARKER_END) -> {
                doorMarkerBuffer?.let { _doorMarkers.value = it.toList() }
                doorMarkerBuffer = null
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
            // Native text-input focus. CLOSED checked before OPEN (neither string contains
            // the other, but keep the dismiss path first). OPEN carries the field's current
            // caption after the prefix — pass it through to pre-fill the panel.
            trimmed.contains(LogParser.P_TEXT_INPUT_CLOSED) -> {
                _textInputRequest.value = null
            }
            trimmed.contains(LogParser.P_TEXT_INPUT_OPEN) -> {
                val idx = trimmed.indexOf(LogParser.P_TEXT_INPUT_OPEN) + LogParser.P_TEXT_INPUT_OPEN.length
                _textInputRequest.value = trimmed.substring(idx)
            }
            trimmed.contains("COMPANION_PAUSE_MENU_OPEN") -> {
                _pauseMenuVisible.value = true
            }
            trimmed.contains("COMPANION_PAUSE_MENU_CLOSED") -> {
                _pauseMenuVisible.value = false
            }
            // Native title-screen main menu (no game loaded). CLOSED checked first (neither string
            // contains the other, but keep the dismiss path first).
            trimmed.contains("COMPANION_TITLE_MENU_CLOSED") -> {
                _titleMenuVisible.value = false
            }
            trimmed.contains("COMPANION_TITLE_MENU_OPEN") -> {
                _titleMenuVisible.value = true
            }
            // Native sneak indicator (sneaking && undetected), change-detected in
            // HUD::setSneakVisible. Payload is "true"/"false".
            trimmed.contains("COMPANION_SNEAK_VISIBLE:") -> {
                _sneakVisible.value = trimmed.substringAfter("COMPANION_SNEAK_VISIBLE:").trim() == "true"
            }
            // Barter session. ITEM first (most frequent). Each ITEM carries its own side,
            // so vendor/player items go to separate buffers. None of these prefixes is a
            // contains()-substring of another: OFFER: (trailing colon) does NOT match the
            // OFFER_ACCEPTED / OFFER_REJECTED: lines, and OPEN:/END/CLOSED are all distinct.
            trimmed.contains(LogParser.P_BARTER_ITEM) -> {
                val idx = trimmed.indexOf(LogParser.P_BARTER_ITEM) + LogParser.P_BARTER_ITEM.length
                LogParser.parseBarterItem(trimmed.substring(idx).trim())?.let { item ->
                    when (item.side) {
                        BarterSide.VENDOR ->
                            (barterVendorBuffer ?: mutableListOf<BarterItem>().also { barterVendorBuffer = it }).add(item)
                        BarterSide.PLAYER ->
                            (barterPlayerBuffer ?: mutableListOf<BarterItem>().also { barterPlayerBuffer = it }).add(item)
                    }
                }
            }
            trimmed.contains(LogParser.P_BARTER_OPEN) -> {
                val idx = trimmed.indexOf(LogParser.P_BARTER_OPEN) + LogParser.P_BARTER_OPEN.length
                LogParser.parseBarterOpen(trimmed.substring(idx).trim())?.let {
                    barterVendorName = it.vendorName
                    barterVendorGold = it.vendorGold
                    barterPlayerGold = it.playerGold
                }
                barterVendorBuffer = mutableListOf()
                barterPlayerBuffer = mutableListOf()
                _barterResult.value = null
            }
            trimmed.contains(LogParser.P_BARTER_END) -> {
                _barterSession.value = BarterSession(
                    vendorName = barterVendorName,
                    vendorGold = barterVendorGold,
                    playerGold = barterPlayerGold,
                    playerItems = barterPlayerBuffer?.toList() ?: emptyList(),
                    vendorItems = barterVendorBuffer?.toList() ?: emptyList(),
                    isVisible = true
                )
                barterVendorBuffer = null
                barterPlayerBuffer = null
            }
            // ACCEPTED / REJECTED checked before the plain OFFER (defensive — the trailing
            // colon on OFFER: already excludes them).
            trimmed.contains(LogParser.P_BARTER_OFFER_ACCEPTED) -> {
                _barterResult.value = BarterResult.Accepted
                // Session also closes on the COMPANION_BARTER_CLOSED that follows; clearing
                // here too keeps the overlay from lingering if CLOSED is ever delayed.
                _barterSession.value = null
            }
            trimmed.contains(LogParser.P_BARTER_OFFER_REJECTED) -> {
                val idx = trimmed.indexOf(LogParser.P_BARTER_OFFER_REJECTED) + LogParser.P_BARTER_OFFER_REJECTED.length
                _barterResult.value = BarterResult.Rejected(
                    LogParser.parseBarterRejectReason(trimmed.substring(idx).trim())
                )
            }
            trimmed.contains(LogParser.P_BARTER_OFFER) -> {
                val idx = trimmed.indexOf(LogParser.P_BARTER_OFFER) + LogParser.P_BARTER_OFFER.length
                LogParser.parseBarterOffer(trimmed.substring(idx).trim())?.let { off ->
                    _barterSession.update { s ->
                        s?.copy(
                            merchantOffer = off.merchantOffer,
                            balance = off.balance,
                            extraGoldOffer = off.extraGold,
                            vendorGold = off.vendorGold,
                            playerGold = off.playerGold
                        )
                    }
                    // A fresh offer (player adjusted) supersedes any stale rejection alert.
                    _barterResult.value = null
                }
            }
            trimmed.contains(LogParser.P_BARTER_CLOSED) -> {
                barterVendorBuffer = null
                barterPlayerBuffer = null
                barterVendorName = ""
                barterVendorGold = 0
                barterPlayerGold = 0
                _barterSession.value = null
                _barterResult.value = null
            }
            // Merchant repair. ITEM first (most frequent). PLAYER_GOLD checked before the
            // REPAIR_ prefixes — its token is distinct and it's emitted inside a repair export.
            trimmed.contains(LogParser.P_REPAIR_ITEM) -> {
                val idx = trimmed.indexOf(LogParser.P_REPAIR_ITEM) + LogParser.P_REPAIR_ITEM.length
                LogParser.parseRepairItem(trimmed.substring(idx).trim())?.let { item ->
                    (repairItemBuffer ?: mutableListOf<RepairItem>().also { repairItemBuffer = it }).add(item)
                }
            }
            trimmed.contains(LogParser.P_REPAIR_OPEN) -> {
                val idx = trimmed.indexOf(LogParser.P_REPAIR_OPEN) + LogParser.P_REPAIR_OPEN.length
                repairNpcName = trimmed.substring(idx).trim()
                repairItemBuffer = mutableListOf()
            }
            trimmed.contains(LogParser.P_PLAYER_GOLD) -> {
                val idx = trimmed.indexOf(LogParser.P_PLAYER_GOLD) + LogParser.P_PLAYER_GOLD.length
                // Shared by repair, travel, training and spell-buying (mutually exclusive GM modes).
                // Route to whichever export is currently being assembled — its OPEN (which starts the
                // matching buffer) ran just before this gold line.
                trimmed.substring(idx).trim().toIntOrNull()?.let { gold ->
                    when {
                        trainingSkillBuffer != null -> trainingPlayerGold = gold
                        spellForSaleBuffer != null -> spellBuyingPlayerGold = gold
                        travelDestBuffer != null -> travelPlayerGold = gold
                        else -> repairPlayerGold = gold
                    }
                }
            }
            trimmed.contains(LogParser.P_REPAIR_END) -> {
                _repairSession.value = RepairSession(
                    npcName = repairNpcName,
                    playerGold = repairPlayerGold,
                    items = repairItemBuffer?.toList() ?: emptyList(),
                    isVisible = true
                )
                repairItemBuffer = null
            }
            trimmed.contains(LogParser.P_REPAIR_CLOSED) -> {
                repairItemBuffer = null
                repairNpcName = ""
                repairPlayerGold = 0
                _repairSession.value = null
            }
            // Travel. DEST first (most frequent). Same buffer pattern as repair; PLAYER_GOLD is
            // routed above. None of the P_TRAVEL_* tokens is a substring of another.
            trimmed.contains(LogParser.P_TRAVEL_DEST) -> {
                val idx = trimmed.indexOf(LogParser.P_TRAVEL_DEST) + LogParser.P_TRAVEL_DEST.length
                LogParser.parseTravelDest(trimmed.substring(idx).trim())?.let { dest ->
                    (travelDestBuffer ?: mutableListOf<TravelDest>().also { travelDestBuffer = it }).add(dest)
                }
            }
            trimmed.contains(LogParser.P_TRAVEL_OPEN) -> {
                val idx = trimmed.indexOf(LogParser.P_TRAVEL_OPEN) + LogParser.P_TRAVEL_OPEN.length
                travelNpcName = trimmed.substring(idx).trim()
                travelDestBuffer = mutableListOf()
            }
            trimmed.contains(LogParser.P_TRAVEL_END) -> {
                _travelSession.value = TravelSession(
                    npcName = travelNpcName,
                    playerGold = travelPlayerGold,
                    destinations = travelDestBuffer?.toList() ?: emptyList(),
                    isVisible = true
                )
                travelDestBuffer = null
            }
            trimmed.contains(LogParser.P_TRAVEL_CLOSED) -> {
                travelDestBuffer = null
                travelNpcName = ""
                travelPlayerGold = 0
                _travelSession.value = null
            }
            // Rest/wait. Single-line OPEN (mode|date|warning); CLOSED clears.
            trimmed.contains(LogParser.P_SLEEP_OPEN) -> {
                val idx = trimmed.indexOf(LogParser.P_SLEEP_OPEN) + LogParser.P_SLEEP_OPEN.length
                LogParser.parseSleepOpen(trimmed.substring(idx))?.let { _sleepSession.value = it }
            }
            trimmed.contains(LogParser.P_SLEEP_CLOSED) -> {
                _sleepSession.value = null
            }
            // Spell buying (GM_SpellBuying → bottom-screen overlay). SPELL first (most frequent),
            // then END, CLOSED, OPEN — none is a substring of another and this order keeps the OPEN
            // contains()-check from swallowing the SPELL/END/CLOSED lines. OPEN also flips the boolean
            // flow (Vanilla-mode conversation step-aside). PLAYER_GOLD is routed above.
            trimmed.contains(LogParser.P_SPELLBUYING_SPELL) -> {
                val idx = trimmed.indexOf(LogParser.P_SPELLBUYING_SPELL) + LogParser.P_SPELLBUYING_SPELL.length
                LogParser.parseSpellForSale(trimmed.substring(idx).trim())?.let { spell ->
                    (spellForSaleBuffer ?: mutableListOf<SpellForSale>().also { spellForSaleBuffer = it }).add(spell)
                }
            }
            trimmed.contains(LogParser.P_SPELLBUYING_END) -> {
                _spellBuyingSession.value = SpellBuyingSession(
                    npcName = spellBuyingNpcName,
                    playerGold = spellBuyingPlayerGold,
                    spells = spellForSaleBuffer?.toList() ?: emptyList()
                )
                spellForSaleBuffer = null
            }
            trimmed.contains(LogParser.P_SPELLBUYING_CLOSED) -> {
                spellForSaleBuffer = null
                spellBuyingNpcName = ""
                spellBuyingPlayerGold = 0
                _spellBuyingSession.value = null
                _spellBuyingWindowOpen.value = false
            }
            trimmed.contains(LogParser.P_SPELLBUYING_OPEN) -> {
                val idx = trimmed.indexOf(LogParser.P_SPELLBUYING_OPEN) + LogParser.P_SPELLBUYING_OPEN.length
                spellBuyingNpcName = trimmed.substring(idx).trim()
                spellForSaleBuffer = mutableListOf()
                _spellBuyingWindowOpen.value = true
            }
            // Training (GM_Training → bottom-screen overlay). Same buffered pattern. Training is
            // one-shot (no re-export): END commits the session, a train command flips isTraining, and
            // CLOSED (after the native fade/advance) clears it. OPEN also flips the boolean flow.
            trimmed.contains(LogParser.P_TRAINING_SKILL) -> {
                val idx = trimmed.indexOf(LogParser.P_TRAINING_SKILL) + LogParser.P_TRAINING_SKILL.length
                LogParser.parseTrainingSkill(trimmed.substring(idx).trim())?.let { skill ->
                    (trainingSkillBuffer ?: mutableListOf<TrainingSkill>().also { trainingSkillBuffer = it }).add(skill)
                }
            }
            trimmed.contains(LogParser.P_TRAINING_END) -> {
                _trainingSession.value = TrainingSession(
                    npcName = trainingNpcName,
                    playerGold = trainingPlayerGold,
                    skills = trainingSkillBuffer?.toList() ?: emptyList()
                )
                trainingSkillBuffer = null
            }
            trimmed.contains(LogParser.P_TRAINING_CLOSED) -> {
                trainingSkillBuffer = null
                trainingNpcName = ""
                trainingPlayerGold = 0
                _trainingSession.value = null
                _trainingWindowOpen.value = false
            }
            trimmed.contains(LogParser.P_TRAINING_OPEN) -> {
                val idx = trimmed.indexOf(LogParser.P_TRAINING_OPEN) + LogParser.P_TRAINING_OPEN.length
                trainingNpcName = trimmed.substring(idx).trim()
                trainingSkillBuffer = mutableListOf()
                _trainingWindowOpen.value = true
            }
            trimmed.contains(LogParser.P_SPELLMAKING_CLOSED) -> { _spellmakingWindowOpen.value = false }
            trimmed.contains(LogParser.P_SPELLMAKING_OPEN) -> { _spellmakingWindowOpen.value = true }
            trimmed.contains(LogParser.P_ENCHANTING_CLOSED) -> { _enchantingWindowOpen.value = false }
            trimmed.contains(LogParser.P_ENCHANTING_OPEN) -> { _enchantingWindowOpen.value = true }
            // Dialogue topic list. Streamed START/TOPIC/END while a conversation is
            // open (re-sent on every topic-list change); CLOSED clears it. TOPIC
            // payloads are plain strings. Buffer until END so the UI swaps atomically.
            trimmed.contains(LogParser.P_DIALOGUE_TOPIC) -> {
                dialogueBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_DIALOGUE_TOPIC) + LogParser.P_DIALOGUE_TOPIC.length
                    val payload = trimmed.substring(idx).trim()
                    // Format is "<flag>|<name>" (flag = read-status). Tolerate an old "<name>" line
                    // with no pipe (flag 0) so a pre-update engine still works.
                    val sep = payload.indexOf('|')
                    val name = if (sep >= 0) payload.substring(sep + 1) else payload
                    val flag = if (sep >= 0) payload.substring(0, sep).toIntOrNull() ?: 0 else 0
                    if (name.isNotEmpty()) {
                        buf.add(name)
                        if (flag != 0) dialogueFlagBuffer?.put(name, flag)
                    }
                }
            }
            trimmed.contains(LogParser.P_DIALOGUE_START) -> {
                dialogueBuffer = mutableListOf()
                dialogueFlagBuffer = mutableMapOf()
            }
            trimmed.contains(LogParser.P_DIALOGUE_END) -> {
                dialogueBuffer?.let { buf -> _dialogueTopics.value = buf.toList() }
                dialogueFlagBuffer?.let { m -> _dialogueTopicFlags.value = m.toMap() }
                dialogueBuffer = null
                dialogueFlagBuffer = null
            }
            trimmed.contains(LogParser.P_DIALOGUE_CLOSED) -> {
                dialogueBuffer = null
                dialogueFlagBuffer = null
                dialogueServiceBuffer = null
                sayLineBuffer = null
                dialogueChoiceBuffer = null
                _dialogueTopics.value = emptyList()
                _dialogueTopicFlags.value = emptyMap()
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
