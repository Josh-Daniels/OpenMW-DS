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
 * The single source of truth for live game state. The native JNI push writes to it
 * (via onJniLine); any Compose UI (on either screen) reads from it. Being a plain object means
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

    // Live god-mode / noclip state, from the change-detected COMPANION_DEV_STATE line (Lua
    // openmw.debug isGodMode/isCollisionEnabled, slow tick). Backs the Developer Tools toggle
    // pills so they show what the ENGINE actually has set rather than a blind local guess —
    // these flags can also be changed from the console or survive across a save load.
    private val _devToggles = MutableStateFlow(DevToggleState())
    val devToggles: StateFlow<DevToggleState> = _devToggles.asStateFlow()

    // Scene ambient luminance (Rec.709 relative luminance of the engine's ambient light colour),
    // from the native COMPANION_AMBIENT line emitted in RenderingManager::setAmbientColour. That
    // is the choke point both the exterior (weather, which folds in time of day) and interior
    // (cell mood, post minimum-interior-brightness clamp) paths pass through, so one signal
    // covers interior/exterior, time of day and weather.
    //
    // Natively throttled (min 0.02 change, max ~4Hz) — it is called every frame otherwise.
    // Backs the companion's adaptive dimming overlay. Typically ~0..1 but NOT clamped here: the
    // engine's ambient colour can exceed 1.0 (weather flashes add to it), so consumers must
    // handle >1. Default 1f = "bright", so nothing dims before the first line arrives.
    private val _ambientLuminance = MutableStateFlow(1f)
    val ambientLuminance: StateFlow<Float> = _ambientLuminance.asStateFlow()

    // How much of the CURRENT exterior ambient colour is the weather's NIGHT value: 1 deep at
    // night, tapering through dawn/dusk, 0 across the day window and 0 in every cell the weather
    // system does not light (interiors). From COMPANION_NIGHT_WEIGHT (companion.lua), which
    // reproduces TimeOfDayInterpolator::getValue's own night factor rather than picking an hour.
    //
    // It exists because [ambientLuminance] ALONE can no longer tell night from day: the Game
    // Brightness night lift raises the night ambient, and at the shipped default the two bands
    // overlap (lifted Overcast night 0.384 vs Overcast day 0.377). This selects which bright-end
    // ceiling the dimming ramp uses — see UiPreferences.ADAPTIVE_DIM_NIGHT_MAX_RANGE.
    //
    // Quantized to 0.05 and change-detected Lua-side, so it arrives a handful of times per in-game
    // night rather than continuously. Default 0f = day, so nothing shifts to the night ceiling
    // before the first line arrives.
    private val _nightWeight = MutableStateFlow(0f)
    val nightWeight: StateFlow<Float> = _nightWeight.asStateFlow()

    // true while the in-game pause/options menu (GM_MainMenu) is open. Driven by
    // COMPANION_PAUSE_MENU_OPEN / _CLOSED lines from companion.lua. Gates the
    // bottom-screen options/display-settings overlay (EngineActivity).
    private val _pauseMenuVisible = MutableStateFlow(false)
    val pauseMenuVisible: StateFlow<Boolean> = _pauseMenuVisible.asStateFlow()

    /**
     * Bumped every time the game asks Kotlin to re-push settings that live in Lua state.
     *
     * Needed because `companion.lua`'s locals do NOT survive a game LOAD — LuaManager::clear()
     * destroys the player script and a fresh one is created, so anything Kotlin pushed earlier in
     * the session (currently the exterior night ambient lift) is silently back at its default. A
     * plain "push once at activity start" collector cannot cover that; the game has to ask. Lua
     * emits the request from onActive, which fires both at game start and on the script recreated
     * after every load.
     *
     * A monotonically increasing counter rather than a Boolean/Unit so that two identical requests
     * still register as two distinct events (a StateFlow would dedupe them).
     */
    private val _settingsRequest = MutableStateFlow(0L)
    val settingsRequest: StateFlow<Long> = _settingsRequest.asStateFlow()

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

    // Transient crime-message toast (COMPANION_CRIME_MSG): text + a monotonic seq so an identical
    // repeat message still re-fires the toast (a plain StateFlow would dedupe equal values). The DS
    // shows a top-of-stack, auto-dismissing banner — the native message renders behind the looting/
    // barter panels. Reset to null when it auto-dismisses.
    private var crimeSeq = 0L
    private val _crimeMessage = MutableStateFlow<Pair<String, Long>?>(null)
    val crimeMessage: StateFlow<Pair<String, Long>?> = _crimeMessage.asStateFlow()

    fun clearCrimeMessage() { _crimeMessage.value = null }

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
    // The localized "Persuasion" topic name, carried on COMPANION_DIALOGUE_PERSUADE_AVAILABLE:<name>.
    // When persuasion is VANILLA the DS "Persuade" tap sends CMPDLG:topic:<name> to open the NATIVE
    // modal (onSelectListItem(sPersuasion)); unused when persuasion is DS. Empty until first seen.
    private val _dialoguePersuadeTopicName = MutableStateFlow("")
    val dialoguePersuadeTopicName: StateFlow<String> = _dialoguePersuadeTopicName.asStateFlow()

    // Whether the persuasion popup is currently open. Promoted from local composable state so the
    // popup can be hosted independently of the conversation overlay (its own Screen Layout location,
    // Bottom or Top). Set true when the Persuade row is tapped; reset on Cancel, on the conversation
    // ending (COMPANION_DIALOGUE_CLOSED) and on the NPC changing.
    private val _persuasionVisible = MutableStateFlow(false)
    val persuasionVisible: StateFlow<Boolean> = _persuasionVisible.asStateFlow()
    fun setPersuasionVisible(visible: Boolean) { _persuasionVisible.value = visible }

    // Live text of a manual journal entry being composed on the bottom screen. null = the composer
    // is closed. Drives the TOP-screen preview overlay, which is the only reason this is repository
    // state rather than local composable state: the two screens are separate windows in separate
    // compositions, so the draft has to travel through something both can see (the same reason
    // persuasionVisible above is here).
    private val _manualJournalDraft = MutableStateFlow<String?>(null)
    val manualJournalDraft: StateFlow<String?> = _manualJournalDraft.asStateFlow()
    fun setManualJournalDraft(text: String?) { _manualJournalDraft.value = text }

    // Accumulates journal entries across JOURNAL_START / JOURNAL_ENTRY / JOURNAL_END lines.
    private var journalBuffer: MutableList<JournalEntry>? = null

    // Current in-game date (COMPANION_GAMEDATE, change-detected on day rollover in
    // companion_global.lua). null until the first line arrives, which is deliberate: a manual
    // journal entry must be stamped with a REAL date or not written at all, so the UI gates on
    // this being non-null rather than falling back to a guess.
    private val _gameDate = MutableStateFlow<GameDate?>(null)
    val gameDate: StateFlow<GameDate?> = _gameDate.asStateFlow()

    // Per-save identity token (COMPANION_SAVE_ID), emitted by companion.lua on every onActive —
    // game start and every load. Buckets the manual journal entries.
    private val _saveId = MutableStateFlow<String?>(null)
    val saveId: StateFlow<String?> = _saveId.asStateFlow()

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

    // Accumulates spells across SPELLS_START / SPELLS_ITEM / SPELLS_END lines — streamed per-spell
    // for the same 4096-byte reason as inventory (a mage's list plus the added per-spell stats can
    // exceed one flush).
    private var spellsBuffer: MutableList<SpellEntry>? = null
    private var spellChanceBuffer: MutableMap<String, Int>? = null

    /**
     * Spell id -> success chance (whole percent), the second half of the vanilla magic menu's
     * "Cost/Chance" column. Learned spells only — powers, scrolls and enchanted items are absent by
     * design (see the Lua exporter), so a missing id means "no chance to show", not "0%".
     *
     * Kept OUT of [SpellEntry] deliberately. Chance is dynamic (it tracks the fatigue term and drops
     * to 0 when magicka is short) while the rest of a spell row is static, so it arrives on its own
     * stream and is joined by id at render time. Folding it into the spell list would re-stream every
     * spell's name, icon and effect text every time the player's fatigue ticked.
     */
    private val _spellChances = MutableStateFlow<Map<String, Int>>(emptyMap())
    val spellChances: StateFlow<Map<String, Int>> = _spellChances.asStateFlow()

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
    private var containerIsOrganic: Boolean = false
    private var containerCapacity: Float = -1f

    // Vanilla put-restriction GMST strings (COMPANION_GMST, one-shot) for the loot put-blocked
    // banner; both empty until the first container opens.
    private val _putMessages = MutableStateFlow(LogParser.PutMessages("", ""))
    val putMessages: StateFlow<LogParser.PutMessages> = _putMessages.asStateFlow()
    // Backstop signal: the Lua guard blocked a put that slipped past the client-side gate. Text +
    // monotonic seq so an identical repeat re-fires the banner (a plain StateFlow would dedupe).
    private var putBlockedSeq = 0L
    private val _putBlocked = MutableStateFlow<Pair<String, Long>?>(null)
    val putBlocked: StateFlow<Pair<String, Long>?> = _putBlocked.asStateFlow()
    fun clearPutBlocked() { _putBlocked.value = null }

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

    private var levelUpBuffer: MutableList<LevelUpAttribute>? = null
    private var levelUpHeader: LevelUpSession? = null

    /**
     * The live DS level-up screen; null when GM_Levelup is not open.
     *
     * Assembled from the Lua START/ATTR/END batch, then kept in step by the NATIVE
     * COMPANION_LEVELUP_SELECTION line, which is the source of truth for the coin count and the
     * picks. A SELECTION can arrive BEFORE the Lua batch (the native window emits one from
     * onOpen), so it is stashed and applied when the batch commits — otherwise the first coin
     * count would be lost and the Done gate would briefly use the wrong number.
     */
    private val _levelUpSession = MutableStateFlow<LevelUpSession?>(null)
    val levelUpSession: StateFlow<LevelUpSession?> = _levelUpSession.asStateFlow()
    private var pendingLevelUpSelection: Pair<Int, List<String>>? = null
    // --- DS Alchemy session (COMPANION_ALCHEMY_*) ---
    // null = GM_Alchemy is not open. OPEN starts the session (as an EMPTY placeholder, so the
    // overlay can mount before the first batch lands); STATE_START..STATE_END brackets one full
    // re-publish, which the native window emits after EVERY mutation; CLOSED clears.
    //
    // Buffered and committed only on STATE_END, exactly like the journal/inventory streams: a batch
    // cut short (a truncated line, a mode pop mid-emit) then leaves the previous complete state on
    // screen rather than a half-applied one.
    private val _alchemySession = MutableStateFlow<AlchemySession?>(null)
    val alchemySession: StateFlow<AlchemySession?> = _alchemySession.asStateFlow()
    private var alchemyHeader: AlchemySession? = null
    private var alchemyApparatusBuffer: MutableList<AlchemyApparatus>? = null
    private var alchemySlotBuffer: MutableList<AlchemySlot>? = null
    private var alchemyEffectBuffer: MutableList<AlchemyEffect>? = null
    private var alchemyToolBuffer: MutableList<AlchemyTool>? = null
    private var alchemyItemBuffer: MutableList<AlchemyIngredient>? = null

    // Result / validation text for the DS alchemy banner. text + monotonic seq so an IDENTICAL
    // repeat still re-fires (a plain StateFlow would dedupe it) — brewing the same failure twice in
    // a row must show the message twice. Same shape as crimeMessage.
    private var alchemyMsgSeq = 0L
    private val _alchemyMessage = MutableStateFlow<Pair<String, Long>?>(null)
    val alchemyMessage: StateFlow<Pair<String, Long>?> = _alchemyMessage.asStateFlow()

    fun clearAlchemyMessage() { _alchemyMessage.value = null }

    // --- DS Enchanting session (COMPANION_ENCHANTING_*) ---
    // null = GM_Enchanting is not open. OPEN starts the session (as an EMPTY placeholder, so a
    // stale session from a previous visit can never linger if the first batch is lost); STATE_START
    // .. STATE_END commits one full republish; CLOSED tears it down.
    private val _enchantSession = MutableStateFlow<EnchantSession?>(null)
    val enchantSession: StateFlow<EnchantSession?> = _enchantSession.asStateFlow()
    private var enchantHeader: EnchantSession? = null
    private var enchantItemSlot: EnchantSlotItem? = null
    private var enchantSoulSlot: EnchantSlotItem? = null
    private var enchantAvailBuffer: MutableList<EnchantAvailEffect>? = null
    private var enchantEffectBuffer: MutableList<EnchantEffect>? = null
    private var enchantItemOptBuffer: MutableList<EnchantPickOption>? = null
    private var enchantSoulOptBuffer: MutableList<EnchantPickOption>? = null

    // Validation / result text for the DS enchanting banner. text + monotonic seq so an IDENTICAL
    // repeat (pressing Buy twice with the same thing missing) re-fires instead of being deduped.
    private var enchantMsgSeq = 0L
    private val _enchantMessage = MutableStateFlow<Pair<String, Long>?>(null)
    val enchantMessage: StateFlow<Pair<String, Long>?> = _enchantMessage.asStateFlow()

    fun clearEnchantMessage() { _enchantMessage.value = null }

    // --- DS Map (COMPANION_MAP_* + three binary JNI deliveries) ---
    // Pushed WHOLE once per DS-map mount: GM_Inventory pauses the sim, so none of this can change
    // while the map is on screen. The two global layers and the fog segments are large, so they
    // arrive as byte arrays through their own JNI methods rather than as COMPANION_ text.
    /** True while the in-game map VIEW is open (the Interface mode showing only the Map window).
     *  Reported by Lua, which owns that toggle — the minimap tap is the same command for open and
     *  close, so the companion cannot infer the state from having sent it. */
    private val _mapModeOpen = MutableStateFlow(false)
    val mapModeOpen: StateFlow<Boolean> = _mapModeOpen.asStateFlow()

    private val _mapDsState = MutableStateFlow<MapDsState?>(null)
    val mapDsState: StateFlow<MapDsState?> = _mapDsState.asStateFlow()
    private var mapStateBuffer: MapDsState? = null

    private val _mapNotes = MutableStateFlow<List<MapNote>>(emptyList())
    val mapNotes: StateFlow<List<MapNote>> = _mapNotes.asStateFlow()
    private var mapNoteBuffer: MutableList<MapNote>? = null

    private val _mapPlaces = MutableStateFlow<List<MapPlace>>(emptyList())
    val mapPlaces: StateFlow<List<MapPlace>> = _mapPlaces.asStateFlow()
    private var mapPlaceBuffer: MutableList<MapPlace>? = null

    /** The static world-map terrain layer, and where it sits in cell space. */
    private val _globalMapBase = MutableStateFlow<Bitmap?>(null)
    val globalMapBase: StateFlow<Bitmap?> = _globalMapBase.asStateFlow()
    private val _globalMapInfo = MutableStateFlow<GlobalMapInfo?>(null)
    val globalMapInfo: StateFlow<GlobalMapInfo?> = _globalMapInfo.asStateFlow()

    /** The explored-areas layer, drawn over the base. Transparent where unexplored. */
    private val _globalMapOverlay = MutableStateFlow<Bitmap?>(null)
    val globalMapOverlay: StateFlow<Bitmap?> = _globalMapOverlay.asStateFlow()

    /** Per-segment fog, keyed the same way the local map segments already are. ALPHA_8: the engine
     *  writes `val = alpha << 24` and never touches RGB, so only the alpha plane is sent. */
    private val _mapFog = MutableStateFlow<Map<Triple<Int, Int, Boolean>, Bitmap>>(emptyMap())
    val mapFog: StateFlow<Map<Triple<Int, Int, Boolean>, Bitmap>> = _mapFog.asStateFlow()

    /** OpenGL row 0 is the BOTTOM row; an Android Bitmap's row 0 is the TOP. Every image coming out
     *  of the engine needs this — onMapTexture has always done it for the local segments, and the
     *  two world-map layers were missing it, which rendered the world map upside down. Flipping the
     *  IMAGE (rather than inverting the coordinate maths) keeps the marker formulas, which already
     *  assume a top-down image, correct. */
    private fun flipVertically(src: Bitmap): Bitmap {
        val m = Matrix().apply { preScale(1f, -1f) }
        val out = Bitmap.createBitmap(src, 0, 0, src.width, src.height, m, false)
        if (out != src) src.recycle()
        return out
    }

    /** Called from native on the engine thread with the static world-map terrain (RGB or RGBA). */
    fun onGlobalMapBase(
        width: Int, height: Int, minX: Int, minY: Int, cellPixels: Int, channels: Int, data: ByteArray
    ) {
        if (width <= 0 || height <= 0) return
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val o = i * channels
            val r = data[o].toInt() and 0xFF
            val g = data[o + 1].toInt() and 0xFF
            val b = data[o + 2].toInt() and 0xFF
            // The base is opaque terrain; when it arrives as RGB there is no alpha byte to read.
            val a = if (channels == 4) data[o + 3].toInt() and 0xFF else 0xFF
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        _globalMapBase.value?.recycle()
        _globalMapBase.value = flipVertically(
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888))
        _globalMapInfo.value = GlobalMapInfo(width, height, minX, minY, cellPixels)
    }

    /** Called from native with the explored-areas layer (RGBA, transparent where unexplored). */
    fun onGlobalMapOverlay(width: Int, height: Int, rgba: ByteArray) {
        if (width <= 0 || height <= 0) return
        val pixels = IntArray(width * height)
        for (i in pixels.indices) {
            val o = i * 4
            val r = rgba[o].toInt() and 0xFF
            val g = rgba[o + 1].toInt() and 0xFF
            val b = rgba[o + 2].toInt() and 0xFF
            val a = rgba[o + 3].toInt() and 0xFF
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }
        _globalMapOverlay.value?.recycle()
        _globalMapOverlay.value = flipVertically(
            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888))
    }

    /** Called from native with one segment's fog mask. ALPHA_8 — one byte per texel. */
    fun onMapFog(segX: Int, segY: Int, isInterior: Int, width: Int, height: Int, alpha: ByteArray) {
        if (width <= 0 || height <= 0) return
        val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ALPHA_8)
        bmp.copyPixelsFromBuffer(java.nio.ByteBuffer.wrap(alpha))
        _mapFog.value = _mapFog.value + (Triple(segX, segY, isInterior != 0) to bmp)
    }

    /** Dropped when the DS map unmounts — these are the largest bitmaps the companion ever holds
     *  (~3 MB each for the two global layers), and they are re-pushed on the next mount anyway. */
    fun clearMapDs() {
        _mapDsState.value = null
        _mapNotes.value = emptyList()
        _mapPlaces.value = emptyList()
        _mapFog.value = emptyMap()
        _globalMapBase.value?.recycle(); _globalMapBase.value = null
        _globalMapOverlay.value?.recycle(); _globalMapOverlay.value = null
        _globalMapInfo.value = null
        mapStateBuffer = null; mapNoteBuffer = null; mapPlaceBuffer = null
    }

    // One-shot popup requests from the engine, both seq'd for the same reason as the message above:
    // adding the same Fortify Skill twice must raise the selector twice.
    private var enchantArgPickSeq = 0L
    private val _enchantArgPick = MutableStateFlow<Pair<EnchantArgPick, Long>?>(null)
    val enchantArgPick: StateFlow<Pair<EnchantArgPick, Long>?> = _enchantArgPick.asStateFlow()

    fun clearEnchantArgPick() { _enchantArgPick.value = null }

    private var enchantEditSeq = 0L
    private val _enchantEdit = MutableStateFlow<Pair<EnchantEditRequest, Long>?>(null)
    val enchantEdit: StateFlow<Pair<EnchantEditRequest, Long>?> = _enchantEdit.asStateFlow()

    fun clearEnchantEdit() { _enchantEdit.value = null }

    private fun resetEnchantBuffers() {
        enchantHeader = null
        enchantItemSlot = null
        enchantSoulSlot = null
        enchantAvailBuffer = null
        enchantEffectBuffer = null
        enchantItemOptBuffer = null
        enchantSoulOptBuffer = null
    }

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
    // Native persuasion modal open (Vanilla-persuasion mode). Same shape; consumed by
    // anyServiceVanillaUpFlow so the conversation overlay steps aside + the controller reaches it.
    private val _persuasionWindowOpen = MutableStateFlow(false)
    val persuasionWindowOpen: StateFlow<Boolean> = _persuasionWindowOpen.asStateFlow()

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

    // Companion data arrives ONLY via the in-process JNI push (companion.lua's core.companionPush
    // → androidmain.cpp companionPushLine → EngineActivity.onCompanionLine → onJniLine). Companion
    // lines are no longer written to openmw.log at all, so there is no file-tail fallback anymore
    // (the old LogReader path + its quiet-triggers-fallback suppression were retired once the disk
    // write was eliminated). Each line is therefore delivered exactly once.
    fun onJniLine(line: String) {
        onRawLine(line)
    }

    /** Called for every COMPANION_* line (via onJniLine, the sole transport). */
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
            trimmed.contains(LogParser.P_CRIME_MSG) -> {
                val text = trimmed.substring(trimmed.indexOf(LogParser.P_CRIME_MSG) + LogParser.P_CRIME_MSG.length).trim()
                if (text.isNotEmpty()) _crimeMessage.value = text to crimeSeq++
            }
            // Current in-game date + save token. Both are small standalone lines and neither
            // prefix collides with the JOURNAL_* family under contains(), but they are matched
            // ahead of it anyway so the cheap cases short-circuit first.
            trimmed.contains(LogParser.P_GAMEDATE) -> {
                val idx = trimmed.indexOf(LogParser.P_GAMEDATE) + LogParser.P_GAMEDATE.length
                LogParser.parseGameDate(trimmed.substring(idx).trim())?.let { _gameDate.value = it }
            }
            trimmed.contains(LogParser.P_SAVE_ID) -> {
                val idx = trimmed.indexOf(LogParser.P_SAVE_ID) + LogParser.P_SAVE_ID.length
                val token = trimmed.substring(idx).trim()
                if (token.isNotEmpty()) {
                    _saveId.value = token
                    // Re-bucket immediately rather than via a UI effect: the journal may not be
                    // composed at load time, and the entries must be right the moment it is.
                    CustomJournalRepository.setSaveId(token)
                }
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
            // Spells stream (SPELLS_ITEM checked first — most frequent — then START/END).
            // "COMPANION_SPELLS:" (old single-line prefix) is NOT a substring of these (the char
            // after SPELLS is '_' here, ':' there), so the legacy parseLine path can't collide.
            trimmed.contains(LogParser.P_SPELLS_ITEM) -> {
                spellsBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_SPELLS_ITEM) + LogParser.P_SPELLS_ITEM.length
                    LogParser.parseSpellItem(trimmed.substring(idx).trim())?.let { buf.add(it) }
                }
            }
            trimmed.contains(LogParser.P_SPELLS_START) -> {
                spellsBuffer = mutableListOf()
            }
            trimmed.contains(LogParser.P_SPELLS_END) -> {
                spellsBuffer?.let { buf ->
                    _state.update { it.copy(spells = buf.toList()) }
                }
                spellsBuffer = null
            }
            // Spell success chances (ITEM first — most frequent). Same buffered START/ITEM/END
            // shape as the spell list itself; committed only on END so a batch lost part-way
            // leaves the previous map intact rather than a half-filled one.
            trimmed.contains(LogParser.P_SPELL_CHANCE) -> {
                spellChanceBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_SPELL_CHANCE) + LogParser.P_SPELL_CHANCE.length
                    val payload = trimmed.substring(idx).trim()
                    val sep = payload.lastIndexOf('|')
                    if (sep > 0) {
                        val id = payload.substring(0, sep)
                        payload.substring(sep + 1).toIntOrNull()?.let { buf[id] = it }
                    }
                }
            }
            trimmed.contains(LogParser.P_SPELL_CHANCES_START) -> {
                spellChanceBuffer = mutableMapOf()
            }
            trimmed.contains(LogParser.P_SPELL_CHANCES_END) -> {
                spellChanceBuffer?.let { _spellChances.value = it.toMap() }
                spellChanceBuffer = null
            }
            // Level up. ATTR first (most frequent within a batch), then START/END, then the
            // native SELECTION echo, then CLOSED. None of these prefixes is a substring of
            // another (END/CLOSED differ from the ':' forms), so ordering is for clarity only.
            trimmed.contains(LogParser.P_LEVELUP_ATTR) -> {
                levelUpBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_LEVELUP_ATTR) + LogParser.P_LEVELUP_ATTR.length
                    LogParser.parseLevelUpAttr(trimmed.substring(idx).trim())?.let { buf.add(it) }
                }
            }
            trimmed.contains(LogParser.P_LEVELUP_START) -> {
                val idx = trimmed.indexOf(LogParser.P_LEVELUP_START) + LogParser.P_LEVELUP_START.length
                levelUpHeader = LogParser.parseLevelUpStart(trimmed.substring(idx).trim())
                levelUpBuffer = mutableListOf()
            }
            trimmed.contains(LogParser.P_LEVELUP_END) -> {
                val header = levelUpHeader
                val buf = levelUpBuffer
                if (header != null && buf != null) {
                    val sel = pendingLevelUpSelection
                    _levelUpSession.value = header.copy(
                        attributes = buf.toList(),
                        coinCount = sel?.first ?: header.coinCount,
                        selected = sel?.second ?: emptyList()
                    )
                }
                levelUpBuffer = null
                levelUpHeader = null
            }
            trimmed.contains(LogParser.P_LEVELUP_SELECTION) -> {
                val idx = trimmed.indexOf(LogParser.P_LEVELUP_SELECTION) + LogParser.P_LEVELUP_SELECTION.length
                LogParser.parseLevelUpSelection(trimmed.substring(idx).trim())?.let { (count, ids) ->
                    pendingLevelUpSelection = count to ids
                    _levelUpSession.value = _levelUpSession.value?.copy(coinCount = count, selected = ids)
                }
            }
            // DS Alchemy. Per-record lines first (most frequent inside a batch), then the
            // brackets. No prefix here is a substring of another, so the order is for cost only.
            trimmed.contains(LogParser.P_ALCHEMY_ITEM) -> {
                alchemyItemBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_ALCHEMY_ITEM) + LogParser.P_ALCHEMY_ITEM.length
                    LogParser.parseAlchemyIngredient(trimmed.substring(idx).trim())?.let { buf.add(it) }
                }
            }
            trimmed.contains(LogParser.P_ALCHEMY_APPARATUS) -> {
                alchemyApparatusBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_ALCHEMY_APPARATUS) + LogParser.P_ALCHEMY_APPARATUS.length
                    LogParser.parseAlchemyApparatus(trimmed.substring(idx).trim())?.let { buf.add(it) }
                }
            }
            trimmed.contains(LogParser.P_ALCHEMY_SLOT) -> {
                alchemySlotBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_ALCHEMY_SLOT) + LogParser.P_ALCHEMY_SLOT.length
                    LogParser.parseAlchemySlot(trimmed.substring(idx).trim())?.let { buf.add(it) }
                }
            }
            trimmed.contains(LogParser.P_ALCHEMY_EFFECT) -> {
                alchemyEffectBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_ALCHEMY_EFFECT) + LogParser.P_ALCHEMY_EFFECT.length
                    LogParser.parseAlchemyEffect(trimmed.substring(idx).trim())?.let { buf.add(it) }
                }
            }
            trimmed.contains(LogParser.P_ALCHEMY_TOOL) -> {
                alchemyToolBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_ALCHEMY_TOOL) + LogParser.P_ALCHEMY_TOOL.length
                    LogParser.parseAlchemyTool(trimmed.substring(idx).trim())?.let { buf.add(it) }
                }
            }
            trimmed.contains(LogParser.P_ALCHEMY_STATE_START) -> {
                val idx = trimmed.indexOf(LogParser.P_ALCHEMY_STATE_START) + LogParser.P_ALCHEMY_STATE_START.length
                alchemyHeader = LogParser.parseAlchemyStart(trimmed.substring(idx).trim())
                alchemyApparatusBuffer = mutableListOf()
                alchemySlotBuffer = mutableListOf()
                alchemyEffectBuffer = mutableListOf()
                alchemyToolBuffer = mutableListOf()
                alchemyItemBuffer = mutableListOf()
            }
            trimmed.contains(LogParser.P_ALCHEMY_STATE_END) -> {
                val header = alchemyHeader
                if (header != null) {
                    _alchemySession.value = header.copy(
                        // sortedBy slot rather than trusting emission order: the slot index is what
                        // the clear commands address, so a mismatch would clear the wrong one.
                        apparatus = (alchemyApparatusBuffer ?: mutableListOf()).sortedBy { it.slot },
                        slots = (alchemySlotBuffer ?: mutableListOf()).sortedBy { it.slot },
                        // Created effects keep EMISSION order — that is listEffects() order, which
                        // is gameplay-significant and must never be sorted.
                        created = (alchemyEffectBuffer ?: mutableListOf()).toList(),
                        tools = (alchemyToolBuffer ?: mutableListOf()).toList(),
                        ingredients = (alchemyItemBuffer ?: mutableListOf()).toList()
                    )
                }
                alchemyHeader = null
                alchemyApparatusBuffer = null
                alchemySlotBuffer = null
                alchemyEffectBuffer = null
                alchemyToolBuffer = null
                alchemyItemBuffer = null
            }
            trimmed.contains(LogParser.P_ALCHEMY_MSG) -> {
                val idx = trimmed.indexOf(LogParser.P_ALCHEMY_MSG) + LogParser.P_ALCHEMY_MSG.length
                val text = trimmed.substring(idx).trim()
                if (text.isNotEmpty()) _alchemyMessage.value = text to alchemyMsgSeq++
            }
            trimmed.contains(LogParser.P_ALCHEMY_CLOSED) -> {
                _alchemySession.value = null
                _alchemyMessage.value = null
                alchemyHeader = null
                alchemyApparatusBuffer = null
                alchemySlotBuffer = null
                alchemyEffectBuffer = null
                alchemyToolBuffer = null
                alchemyItemBuffer = null
            }
            trimmed.contains(LogParser.P_MAPMODE) -> {
                val idx = trimmed.indexOf(LogParser.P_MAPMODE) + LogParser.P_MAPMODE.length
                _mapModeOpen.value = trimmed.substring(idx).trim().startsWith("1")
            }
            // DS Map. Per-record lines first, then the brackets.
            trimmed.contains(LogParser.P_MAP_NOTE) -> {
                mapNoteBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_MAP_NOTE) + LogParser.P_MAP_NOTE.length
                    LogParser.parseMapNote(trimmed.substring(idx).trim())?.let { buf.add(it) }
                }
            }
            trimmed.contains(LogParser.P_MAP_PLACE) -> {
                mapPlaceBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_MAP_PLACE) + LogParser.P_MAP_PLACE.length
                    LogParser.parseMapPlace(trimmed.substring(idx).trim())?.let { buf.add(it) }
                }
            }
            trimmed.contains(LogParser.P_MAP_NOTES_START) -> { mapNoteBuffer = mutableListOf() }
            trimmed.contains(LogParser.P_MAP_NOTES_END) -> {
                mapNoteBuffer?.let { _mapNotes.value = it.toList() }
                mapNoteBuffer = null
            }
            trimmed.contains(LogParser.P_MAP_PLACES_START) -> { mapPlaceBuffer = mutableListOf() }
            trimmed.contains(LogParser.P_MAP_PLACES_END) -> {
                mapPlaceBuffer?.let { _mapPlaces.value = it.toList() }
                mapPlaceBuffer = null
            }
            trimmed.contains(LogParser.P_MAP_STATE) -> {
                val idx = trimmed.indexOf(LogParser.P_MAP_STATE) + LogParser.P_MAP_STATE.length
                mapStateBuffer = LogParser.parseMapState(trimmed.substring(idx).trim())
            }
            trimmed.contains(LogParser.P_MAP_END) -> {
                // Committed only at the end of the push, so a half-arrived batch never replaces a
                // good one on screen.
                mapStateBuffer?.let { _mapDsState.value = it }
                mapStateBuffer = null
            }
            // DS Enchanting. Per-record lines first (most frequent inside a batch), then the
            // brackets, then the one-shot requests, and OPEN last — it is the SHORTEST prefix here
            // and this dispatch is contains()-based.
            trimmed.contains(LogParser.P_ENCH_EFFECT) -> {
                enchantEffectBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_ENCH_EFFECT) + LogParser.P_ENCH_EFFECT.length
                    LogParser.parseEnchantEffect(trimmed.substring(idx).trim())?.let { buf.add(it) }
                }
            }
            trimmed.contains(LogParser.P_ENCH_AVAIL) -> {
                enchantAvailBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_ENCH_AVAIL) + LogParser.P_ENCH_AVAIL.length
                    LogParser.parseEnchantAvail(trimmed.substring(idx).trim())?.let { buf.add(it) }
                }
            }
            trimmed.contains(LogParser.P_ENCH_ITEMOPT) -> {
                enchantItemOptBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_ENCH_ITEMOPT) + LogParser.P_ENCH_ITEMOPT.length
                    LogParser.parseEnchantPick(trimmed.substring(idx).trim())?.let { buf.add(it) }
                }
            }
            trimmed.contains(LogParser.P_ENCH_SOULOPT) -> {
                enchantSoulOptBuffer?.let { buf ->
                    val idx = trimmed.indexOf(LogParser.P_ENCH_SOULOPT) + LogParser.P_ENCH_SOULOPT.length
                    LogParser.parseEnchantPick(trimmed.substring(idx).trim())?.let { buf.add(it) }
                }
            }
            trimmed.contains(LogParser.P_ENCH_ITEMSLOT) -> {
                val idx = trimmed.indexOf(LogParser.P_ENCH_ITEMSLOT) + LogParser.P_ENCH_ITEMSLOT.length
                LogParser.parseEnchantSlot(trimmed.substring(idx).trim())?.let { enchantItemSlot = it }
            }
            trimmed.contains(LogParser.P_ENCH_SOULSLOT) -> {
                val idx = trimmed.indexOf(LogParser.P_ENCH_SOULSLOT) + LogParser.P_ENCH_SOULSLOT.length
                LogParser.parseEnchantSlot(trimmed.substring(idx).trim())?.let { enchantSoulSlot = it }
            }
            trimmed.contains(LogParser.P_ENCH_STATE_START) -> {
                val idx = trimmed.indexOf(LogParser.P_ENCH_STATE_START) + LogParser.P_ENCH_STATE_START.length
                enchantHeader = LogParser.parseEnchantStart(trimmed.substring(idx).trim())
                enchantItemSlot = null
                enchantSoulSlot = null
                enchantAvailBuffer = mutableListOf()
                enchantEffectBuffer = mutableListOf()
                enchantItemOptBuffer = mutableListOf()
                enchantSoulOptBuffer = mutableListOf()
            }
            trimmed.contains(LogParser.P_ENCH_STATE_END) -> {
                val header = enchantHeader
                if (header != null) {
                    _enchantSession.value = header.copy(
                        item = enchantItemSlot ?: EnchantSlotItem(),
                        soul = enchantSoulSlot ?: EnchantSlotItem(),
                        // The browse list keeps EMISSION order, which is the engine's own
                        // sort-by-display-name from startEditing.
                        available = (enchantAvailBuffer ?: mutableListOf()).toList(),
                        // Effects keep emission order too — that IS mEffects order, and the index in
                        // each row is the handle every command uses, so it must never be re-sorted.
                        effects = (enchantEffectBuffer ?: mutableListOf()).toList(),
                        itemOptions = (enchantItemOptBuffer ?: mutableListOf()).toList(),
                        soulOptions = (enchantSoulOptBuffer ?: mutableListOf()).toList()
                    )
                }
                resetEnchantBuffers()
            }
            trimmed.contains(LogParser.P_ENCH_ARGPICK) -> {
                val idx = trimmed.indexOf(LogParser.P_ENCH_ARGPICK) + LogParser.P_ENCH_ARGPICK.length
                LogParser.parseEnchantArgPick(trimmed.substring(idx).trim())?.let {
                    _enchantArgPick.value = it to enchantArgPickSeq++
                }
            }
            trimmed.contains(LogParser.P_ENCH_EDIT) -> {
                val idx = trimmed.indexOf(LogParser.P_ENCH_EDIT) + LogParser.P_ENCH_EDIT.length
                LogParser.parseEnchantEdit(trimmed.substring(idx).trim())?.let {
                    _enchantEdit.value = it to enchantEditSeq++
                }
            }
            trimmed.contains(LogParser.P_ENCH_MSG) -> {
                val idx = trimmed.indexOf(LogParser.P_ENCH_MSG) + LogParser.P_ENCH_MSG.length
                val text = trimmed.substring(idx).trim()
                if (text.isNotEmpty()) _enchantMessage.value = text to enchantMsgSeq++
            }
            trimmed.contains(LogParser.P_ENCH_CLOSED) -> {
                // Also the Vanilla-mode signal: the conversation overlay steps aside while this
                // dialogue-service window is up, and this is the only place that line is handled.
                _enchantingWindowOpen.value = false
                _enchantSession.value = null
                _enchantMessage.value = null
                _enchantArgPick.value = null
                _enchantEdit.value = null
                resetEnchantBuffers()
            }
            trimmed.contains(LogParser.P_ENCH_OPEN) -> {
                _enchantingWindowOpen.value = true
                // Mount the overlay immediately on an EMPTY session; the first batch follows in the
                // same frame (setPtr emits OPEN, then startEditing -> updateLabels emits the batch).
                if (_enchantSession.value == null) _enchantSession.value = EnchantSession()
            }
            trimmed.contains(LogParser.P_ALCHEMY_OPEN) -> {
                // Mount the overlay immediately on an EMPTY session. The first batch follows in the
                // same frame (onOpen emits OPEN then calls update()), but starting from null here
                // would leave a stale session from a previous visit on screen if that batch were
                // ever lost. Checked AFTER the other ALCHEMY prefixes only for dispatch cost.
                _alchemySession.value = AlchemySession()
                _alchemyMessage.value = null
            }
            trimmed.contains(LogParser.P_LEVELUP_CLOSED) -> {
                _levelUpSession.value = null
                levelUpBuffer = null
                levelUpHeader = null
                pendingLevelUpSelection = null
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
                    containerIsOrganic = it.isOrganic
                    containerCapacity = it.capacity
                }
                containerBuffer = mutableListOf()
            }
            trimmed.contains(LogParser.P_CONTAINER_END) -> {
                val buf = containerBuffer ?: mutableListOf()
                _containerSession.value = ContainerSession(
                    containerName, containerIsCorpse, containerIsPickpocket, buf.toList(),
                    isVisible = true, isOrganic = containerIsOrganic, capacity = containerCapacity
                )
                containerBuffer = null
            }
            trimmed.contains(LogParser.P_CONTAINER_CLOSED) -> {
                containerBuffer = null
                containerName = ""
                containerIsCorpse = false
                containerIsPickpocket = false
                containerIsOrganic = false
                containerCapacity = -1f
                _containerSession.value = null
            }
            trimmed.contains(LogParser.P_GMST) -> {
                val idx = trimmed.indexOf(LogParser.P_GMST) + LogParser.P_GMST.length
                LogParser.parseGmst(trimmed.substring(idx).trim())?.let { _putMessages.value = it }
            }
            trimmed.contains(LogParser.P_PUT_BLOCKED) -> {
                val msg = trimmed.substring(trimmed.indexOf(LogParser.P_PUT_BLOCKED) + LogParser.P_PUT_BLOCKED.length).trim()
                if (msg.isNotEmpty()) _putBlocked.value = msg to putBlockedSeq++
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
            // The game (re)activated and wants its Lua-side settings pushed again. See
            // [settingsRequest] for why a load makes this mandatory rather than belt-and-braces.
            trimmed.contains("COMPANION_REQUEST_SETTINGS") -> {
                _settingsRequest.value = _settingsRequest.value + 1
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
            // Developer Tools engine toggles, "<god 0/1>|<noclip 0/1>". Change-detected Lua-side,
            // so this only arrives on a real transition. A malformed line is ignored rather than
            // parsed as "both off", which would make the pills lie.
            trimmed.contains("COMPANION_DEV_STATE:") -> {
                val parts = trimmed.substringAfter("COMPANION_DEV_STATE:").trim().split("|")
                if (parts.size == 2) {
                    _devToggles.value = DevToggleState(
                        godMode = parts[0] == "1",
                        noclip = parts[1] == "1"
                    )
                }
            }
            // Scene ambient luminance for the adaptive dimming overlay. A malformed or partial
            // line is ignored rather than defaulting to 0, which would slam the overlay to its
            // darkest.
            trimmed.contains("COMPANION_AMBIENT:") -> {
                trimmed.substringAfter("COMPANION_AMBIENT:").trim().toFloatOrNull()
                    ?.let { _ambientLuminance.value = it }
            }
            // Exterior night weight for the dimming ramp's bright-end ceiling. Clamped here as
            // well as Lua-side: this multiplies a ceiling, so a value outside 0..1 would push the
            // blend past either endpoint. A malformed line is ignored rather than read as 0, which
            // would silently drop back to the daytime ceiling in the middle of the night.
            trimmed.contains("COMPANION_NIGHT_WEIGHT:") -> {
                trimmed.substringAfter("COMPANION_NIGHT_WEIGHT:").trim().toFloatOrNull()
                    ?.let { _nightWeight.value = it.coerceIn(0f, 1f) }
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
            // COMPANION_ENCHANTING_OPEN / _CLOSED are handled with the rest of the DS enchanting
            // batch further up this chain (that branch also flips _enchantingWindowOpen, the
            // Vanilla-mode conversation step-aside flag) — a duplicate pair here would be dead code,
            // since `when` takes the first match.
            // CLOSED before OPEN (neither token is a substring of the other, but keep the convention).
            trimmed.contains(LogParser.P_PERSUASION_CLOSED) -> { _persuasionWindowOpen.value = false }
            trimmed.contains(LogParser.P_PERSUASION_OPEN) -> { _persuasionWindowOpen.value = true }
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
                _persuasionVisible.value = false
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
                // Close any open persuasion popup when switching NPCs mid-session.
                _persuasionVisible.value = false
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
                // Optional ":<sPersuasion name>" tail (used to open the native modal in Vanilla mode).
                val idx = trimmed.indexOf(LogParser.P_DIALOGUE_PERSUADE_AVAILABLE) +
                    LogParser.P_DIALOGUE_PERSUADE_AVAILABLE.length
                trimmed.substring(idx).removePrefix(":").trim()
                    .takeIf { it.isNotEmpty() }?.let { _dialoguePersuadeTopicName.value = it }
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
