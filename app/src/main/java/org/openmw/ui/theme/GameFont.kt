package org.openmw.ui.theme

import android.content.Context
import android.util.Log
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.roundToInt

/*
 * The game's typeface, shared by the companion/DS overlays (CompanionScreen) and the simplified
 * launcher. Lives here rather than in either consumer so there is ONE loader, one cache and one
 * place the tuning dials are set — the two surfaces must not drift apart visually, and duplicating
 * the TrueType patching below would be a genuine maintenance hazard.
 *
 * Each surface still owns its own on/off preference and its own CompositionLocal wiring; only the
 * loading is shared.
 */

/**
 * The game's own typeface, as bundled in the APK by the OpenMW asset build.
 *
 * This is MysticCards — OpenMW's SIL-OFL replacement for Morrowind's "Magic Cards" UI font, drawn
 * from Isak Larborn's Pelagiad. It is deliberately NOT Bethesda's original: that ships as a fixed
 * 16 px BITMAP atlas (`.fnt` + `.tex`), which cannot be an Android font resource at all, and which
 * this 369 dpi panel would have to ENLARGE for nearly every size the companion uses (a 14 sp line
 * is 32 px here) — so the TTF is both the only practical option and the sharper one.
 *
 * Loaded from assets rather than copied into `res/font/`: the APK already carries it for the
 * engine, so there is nothing to add and nothing to keep in sync. Note this is a build-GENERATED
 * asset path (the OpenMW CMake step stages `resources/vfs`), hence the defensive load below —
 * if the asset pipeline ever moves it, the UI must fall back rather than crash.
 */
private const val GAME_FONT_ASSET = "libopenmw/resources/vfs/fonts/MysticCards.ttf"

/**
 * How much to enlarge text while the game font is active — THE DIAL TO TURN if it still reads too
 * small or starts to look oversized.
 *
 * Needed because the two faces disagree about how much of the em their letters occupy, measured
 * from their own tables:
 *
 *     x-height / em     MysticCards 0.409    NotoSerif 0.536    (NotoSerif 31% larger)
 *     digit height / em MysticCards 0.603    NotoSerif 0.724    (NotoSerif 20% larger)
 *     cap height / em   MysticCards 0.711    NotoSerif 0.714    (the same)
 *
 * So at an identical `fontSize` the CAPITALS match but the lowercase and digits — i.e. nearly all
 * the text — render visibly smaller. That is a property of the typeface, not of our sizes, so it
 * cannot be fixed per-site; every `.sp` has to scale together.
 *
 * 1.15 is a compromise: it lands digits almost exactly on the old size (0.603 x 1.15 = 0.69 vs
 * 0.72) and closes most of the lowercase gap, while only enlarging capitals ~15% — matching
 * x-height exactly would need 1.31 and would leave headings noticeably oversized.
 */
const val GAME_FONT_SIZE_SCALE = 1.15f

/**
 * Word-space width while the game font is active, as a fraction of the 'n' advance — THE OTHER
 * DIAL, alongside [GAME_FONT_SIZE_SCALE].
 *
 * MysticCards ships a very wide space: 435 units against an 'n' of 571, i.e. a ratio of 0.76,
 * where the Android serif it replaces sits at 0.40. That is nearly double, and it is what made
 * words look adrift. Vanilla Morrowind's own bitmap font is 0.67 — wide as well, so the trait is
 * authentic — but 0.46 was chosen from a side-by-side as the point where spacing reads normally
 * without the words crowding at 12 sp. Deliberately tighter than vanilla: legibility on a 3.9"
 * panel over strict fidelity.
 */
private const val GAME_FONT_WORD_SPACE_RATIO = 0.46f

// Process-wide cache. The font is ~33 KB and parsing it allocates a Typeface, so it is read once
// per process and shared by both screens; `unavailable` latches a failure so a missing asset costs
// one failed lookup rather than one per composition.
private var gameFontFamily: FontFamily? = null
private var gameFontUnavailable = false

fun loadGameFont(context: Context): FontFamily? {
    gameFontFamily?.let { return it }
    if (gameFontUnavailable) return null
    return try {
        // Prefer the respaced copy; fall back to the asset as-is if patching it fails for any
        // reason, so a bad parse costs the wider spacing rather than the whole feature.
        val respaced = respacedGameFontFile(context)
        val font = if (respaced != null) Font(respaced) else Font(GAME_FONT_ASSET, context.assets)
        FontFamily(font).also { gameFontFamily = it }
    } catch (t: Throwable) {
        // Never fatal: the whole companion UI renders through these roles, so a failure here must
        // degrade to the system fonts rather than take the screen down.
        gameFontUnavailable = true
        Log.w("GameFont", "Game font unavailable at $GAME_FONT_ASSET; using system fonts", t)
        null
    }
}

/**
 * The game font with its word space narrowed to [GAME_FONT_WORD_SPACE_RATIO], written to the app's
 * own cache. Returns null if anything about the file is not as expected — every caller treats that
 * as "use the font unmodified".
 *
 * Done at load time rather than by shipping an edited font on purpose: the APK keeps carrying
 * OpenMW's pristine file, and the modified copy never leaves the device. Nothing modified is
 * redistributed, so the SIL OFL's Reserved Font Name clause is not engaged and the font needs no
 * renaming.
 *
 * The cache name carries both the ratio and the source size, so changing the dial or shipping a
 * different build of the font produces a different file instead of silently reusing a stale one.
 */
private fun respacedGameFontFile(context: Context): File? = try {
    val source = context.assets.open(GAME_FONT_ASSET).use { it.readBytes() }
    val ratioTag = (GAME_FONT_WORD_SPACE_RATIO * 100).roundToInt()
    val out = File(context.cacheDir, "gamefont-sp$ratioTag-${source.size}.ttf")
    when {
        out.isFile && out.length() > 0L -> out
        else -> respaceFont(source)?.let { patched ->
            // Write via a temp file then rename, so a kill mid-write can't leave a truncated font
            // that would then be cached and loaded on every later launch.
            val tmp = File(context.cacheDir, out.name + ".part")
            tmp.writeBytes(patched)
            if (tmp.renameTo(out)) out else { tmp.delete(); null }
        }
    }
} catch (t: Throwable) {
    Log.w("GameFont", "Could not respace the game font; using its own spacing", t)
    null
}

/**
 * Rewrite the `hmtx` advance width of U+0020 in a TrueType file. Returns the modified bytes, or
 * null if the tables needed are missing or malformed.
 *
 * Only that one 16-bit field changes; glyph outlines are untouched. Table checksums are left stale,
 * which FreeType (and therefore Android) does not verify — noted because it is an assumption, not a
 * guarantee, and it is the first thing to suspect if a future platform rejects the file.
 */
private fun respaceFont(font: ByteArray): ByteArray? {
    val bb = ByteBuffer.wrap(font)          // TrueType is big-endian, which is ByteBuffer's default
    fun u16(offset: Int) = bb.getShort(offset).toInt() and 0xFFFF
    fun u32(offset: Int) = bb.getInt(offset).toLong() and 0xFFFFFFFFL

    if (font.size < 12) return null
    var head = -1; var hhea = -1; var hmtx = -1; var cmap = -1
    for (i in 0 until u16(4)) {
        val record = 12 + i * 16
        if (record + 16 > font.size) return null
        val offset = u32(record + 8).toInt()
        when (String(font, record, 4, Charsets.US_ASCII)) {
            "head" -> head = offset
            "hhea" -> hhea = offset
            "hmtx" -> hmtx = offset
            "cmap" -> cmap = offset
        }
    }
    if (head < 0 || hhea < 0 || hmtx < 0 || cmap < 0) return null

    val unitsPerEm = u16(head + 18).takeIf { it > 0 } ?: return null
    val numHMetrics = u16(hhea + 34).takeIf { it > 0 } ?: return null

    val spaceGlyph = glyphIndexOf(bb, font.size, cmap, ' '.code) ?: return null
    // Beyond numHMetrics a glyph inherits the last entry's advance, so patching it would move every
    // trailing glyph at once. Not the case for this font (space is glyph 4), but bail rather than
    // corrupt an unexpected one.
    if (spaceGlyph >= numHMetrics) return null

    // Express the target against the 'n' advance so the dial stays meaningful if the font is ever
    // swapped for one with different proportions.
    val nGlyph = glyphIndexOf(bb, font.size, cmap, 'n'.code) ?: return null
    val nAdvance = u16(hmtx + minOf(nGlyph, numHMetrics - 1) * 4)
    val target = (GAME_FONT_WORD_SPACE_RATIO * nAdvance).roundToInt().coerceIn(1, unitsPerEm)

    val slot = hmtx + spaceGlyph * 4
    if (slot + 2 > font.size) return null
    bb.putShort(slot, target.toShort())     // wraps the same array, so `font` now holds the change
    return font
}

/** Glyph index for a character via the `cmap` format-4 subtable, or null if unavailable. */
private fun glyphIndexOf(bb: ByteBuffer, size: Int, cmap: Int, char: Int): Int? {
    fun u16(offset: Int) = bb.getShort(offset).toInt() and 0xFFFF
    fun u32(offset: Int) = bb.getInt(offset).toLong() and 0xFFFFFFFFL

    var subtable = -1
    for (i in 0 until u16(cmap + 2)) {
        val record = cmap + 4 + i * 8
        if (record + 8 > size) return null
        val platform = u16(record)
        val encoding = u16(record + 2)
        // Windows BMP/full, or Unicode — the ones that map codepoints directly.
        if ((platform == 3 && (encoding == 1 || encoding == 10)) || platform == 0) {
            subtable = cmap + u32(record + 4).toInt()
        }
    }
    if (subtable < 0 || subtable + 16 > size || u16(subtable) != 4) return null

    val segmentsX2 = u16(subtable + 6)
    val segments = segmentsX2 / 2
    val endCodes = subtable + 14
    val startCodes = endCodes + segmentsX2 + 2   // +2 skips the reserved pad
    val idDeltas = startCodes + segmentsX2
    val idRangeOffsets = idDeltas + segmentsX2
    if (idRangeOffsets + segmentsX2 > size) return null

    for (i in 0 until segments) {
        if (char > u16(endCodes + i * 2)) continue
        val start = u16(startCodes + i * 2)
        if (char < start) return 0
        val delta = bb.getShort(idDeltas + i * 2).toInt()
        val rangeOffset = u16(idRangeOffsets + i * 2)
        if (rangeOffset == 0) return (char + delta) and 0xFFFF
        val address = idRangeOffsets + i * 2 + rangeOffset + (char - start) * 2
        if (address + 2 > size) return null
        val glyph = u16(address)
        return if (glyph == 0) 0 else (glyph + delta) and 0xFFFF
    }
    return null
}