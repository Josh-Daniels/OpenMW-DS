package org.openmw.companion

import android.graphics.Bitmap
import android.graphics.Matrix
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    val exteriorMapBitmaps: StateFlow<Map<Pair<Int,Int>, Bitmap>> = _exteriorMapBitmaps.asStateFlow()

    private val _interiorMapBitmap = MutableStateFlow<Bitmap?>(null)
    val interiorMapBitmap: StateFlow<Bitmap?> = _interiorMapBitmap.asStateFlow()

    // Accumulates journal entries across JOURNAL_START / JOURNAL_ENTRY / JOURNAL_END lines.
    private var journalBuffer: MutableList<JournalEntry>? = null

    fun update(transform: (GameState) -> GameState) {
        _state.update(transform)
    }

    /**
     * Called from JNI (render thread) when a map segment has been rendered.
     * Flips the image vertically (OpenGL origin is bottom-left) and stores
     * the resulting bitmap for the MapPanel to display.
     */
    fun onMapTexture(width: Int, height: Int, segX: Int, segY: Int, isInterior: Int, rgba: ByteArray) {
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
            _interiorMapBitmap.value = bmp
        } else {
            _exteriorMapBitmaps.update { it + (Pair(segX, segY) to bmp) }
        }
    }

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
            else -> _state.update { cur -> LogParser.parseLine(trimmed, cur) ?: cur }
        }
    }
}
