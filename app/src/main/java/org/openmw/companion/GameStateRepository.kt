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

    // Accumulates journal entries across JOURNAL_START / JOURNAL_ENTRY / JOURNAL_END lines.
    private var journalBuffer: MutableList<JournalEntry>? = null

    // Accumulates inventory across INVENTORY_START / INVENTORY_ITEM / INVENTORY_END
    // lines. Inventory is streamed per-item because one combined line can exceed
    // the engine's 4096-byte stdout flush and arrive truncated (see companion.lua).
    private var inventoryBuffer: MutableList<InventoryItem>? = null

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

    /** Called from JNI on the engine thread for every COMPANION_* log line. */
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
            // A fresh COMPANION_CHARACTER rebuilds attributes/skills from scratch
            // (no descriptions), so re-apply the last description batch on top.
            trimmed.contains(LogParser.P_CHARACTER) -> {
                _state.update { cur ->
                    val next = LogParser.parseLine(trimmed, cur) ?: cur
                    next.copy(character = mergeDetail(next.character, lastDetail))
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
