package org.openmw.companion

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.SystemClock
import android.util.Log
import java.io.File
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 * Interface feedback sounds for the companion's own chrome — the on-screen keyboard, the options
 * menu, and the Developer Tools buttons. One master preference (`UiPreferences.uiSoundsFlow`) plus
 * a volume preference gate every cue.
 *
 * WHY THIS IS PURE KOTLIN AND NOT AN ENGINE PATCH. The project already has `companion-ui-sounds
 * .patch`, but it is not reusable here and extending it would be the wrong shape:
 *  - It plays MWWorld item sounds through `WindowManager::playSound`, i.e. OpenMW's own audio
 *    system, which only exists while a game session is running. The options menu is reachable from
 *    the TITLE SCREEN, where there is no player, no cell and no sound manager to speak of.
 *  - Its Lua half (`types.Item.getUpSoundId`) is keyed to item records — there is no item behind a
 *    keystroke or a settings toggle.
 *  - Every trigger point here is a Kotlin composable, so routing through native would mean a
 *    round trip out to the engine and back for a sound whose entire job is to feel instantaneous.
 * So this is Kotlin-only: **no patch, no stamp dance.**
 *
 * WHY THE SAMPLES ARE SYNTHESISED rather than bundled or taken from the game:
 *  - Morrowind's own UI sounds live in `Morrowind.bsa` and are Bethesda assets. The same rule that
 *    keeps the Demibold font out of the APK applies (see the Game Font licensing note): reading a
 *    user's own Data Files is fine, SHIPPING the asset is not. Extracting from a BSA at runtime
 *    would also mean wiring up a BSA reader for four clicks.
 *  - Generating them costs one small file write and adds no binary to the repo.
 * The cache-to-`cacheDir` approach mirrors `GameFont.kt`, which already writes a derived font there
 * with the same versioned-filename + `.part`-then-rename discipline.
 */

private const val TAG = "UiSounds"

private const val SAMPLE_RATE = 44100

/**
 * Bumped whenever the synthesis below changes, so an existing device regenerates instead of
 * playing a stale cached clip. Encoded into the cache filename — same trick as the font cache.
 */
private const val SOUND_VERSION = 1

/** Concurrent streams. Enough for fast typing to overlap without cutting itself off. */
private const val MAX_STREAMS = 6

/**
 * Ceiling on playback gain — the slider's 100% maps to THIS, not to full scale.
 *
 * Lowered to 0.4 on Aug 10 2026 after listening on device: the cues share the engine's audio stream
 * and the top of the old range was simply unusable, so most of the slider's travel was wasted on
 * levels nobody would pick. Shrinking the ceiling spends the whole slider on the range that is
 * actually useful. The preference default moved 0.2 → 0.5 in the same change so the level people
 * already had is unchanged: 0.5 x 0.4 = the previous 0.2 x 1.0.
 *
 * Deliberately applied HERE and not baked into the sample data: the per-cue `gain` values carry the
 * four cues' balance AGAINST EACH OTHER, and folding an overall-loudness change into them would
 * break that (and force a SOUND_VERSION bump to re-cache every clip).
 */
private const val UI_SOUND_MAX_GAIN = 0.4f

object UiSounds {

    /**
     * The four distinct pieces of feedback. Kept deliberately small — one master toggle governs all
     * of them, so these are about giving each ACTION an appropriate weight, not about giving the
     * player four things to configure.
     */
    enum class Cue {
        /** A keystroke on the on-screen keyboard. Light and dry — it fires the most often. */
        KEY,

        /** An options pill changing state. Firmer, with a little more body than a keystroke. */
        TOGGLE,

        /** One notch of slider travel. The quietest cue by far; see [SliderTickThrottle]. */
        TICK,

        /** A Developer Tools button. The heaviest cue on purpose — the complaint that prompted
         *  this feature was that cheat buttons felt like nothing had happened. */
        ACTION
    }

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(MAX_STREAMS)
        .setAudioAttributes(
            AudioAttributes.Builder()
                // USAGE_GAME (not ASSISTANCE_SONIFICATION) so these ride the same stream as the
                // engine's own audio. SONIFICATION can be routed to the system/notification stream
                // and silenced by the device's "touch sounds" setting, which would make our master
                // toggle a liar. This choice also means the sounds sit at a sane level relative to
                // the game rather than cutting across it.
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    /** Cue → SoundPool id. Populated asynchronously; a missing entry simply plays nothing. */
    private val ids = HashMap<Cue, Int>()

    @Volatile
    private var started = false

    /** Last play time per cue, for the anti-machine-gun floor in [play]. */
    private val lastPlayedMs = HashMap<Cue, Long>()

    /**
     * Minimum gap between two plays of the SAME cue. This is a guard against a control firing its
     * callback several times in one gesture, NOT a rate limit on the user: 25 ms is far faster than
     * anyone can type, so real keystrokes are never swallowed. [Cue.TICK] leans on it hardest,
     * because a fast slider flick can cross several notches inside one frame.
     */
    private fun minGapMs(cue: Cue) = when (cue) {
        Cue.KEY -> 25L
        Cue.TICK -> 30L
        Cue.TOGGLE -> 45L
        Cue.ACTION -> 45L
    }

    /**
     * Prepare the sound bank. Idempotent and safe to call from anywhere; the actual synthesis and
     * file I/O happen on a background thread, so this never blocks a composition or a frame.
     *
     * Playback before loading finishes is a silent no-op rather than an error — in practice the
     * bank is ready long before the first tap, since every trigger point requires the player to
     * open a menu or a keyboard first.
     */
    fun init(context: Context) {
        if (started) return
        synchronized(this) {
            if (started) return
            started = true
        }
        val cacheDir = context.applicationContext.cacheDir
        Thread({
            try {
                Cue.entries.forEach { cue ->
                    val file = File(cacheDir, "uisfx_${cue.name.lowercase()}_v$SOUND_VERSION.wav")
                    if (!file.exists()) writeWav(file, synthesize(cue))
                    val id = pool.load(file.absolutePath, 1)
                    if (id != 0) synchronized(ids) { ids[cue] = id }
                }
            } catch (t: Throwable) {
                // Never let a UI nicety take the app down. Silence is an acceptable degradation.
                Log.w(TAG, "UI sound bank unavailable", t)
            }
        }, "ui-sounds-init").apply { isDaemon = true }.start()
    }

    /**
     * Play [cue], honouring the master toggle and the volume preference.
     *
     * Reads the preferences at call time rather than taking them as parameters so that ~60 call
     * sites do not each have to collect two flows. The flows are plain in-memory `StateFlow`s
     * (`UiPreferences` keeps them hot), so this is a field read, not I/O.
     */
    fun play(cue: Cue) {
        if (!UiPreferences.uiSoundsFlow().value) return
        val volume = UiPreferences.uiSoundVolumeFlow().value
        if (volume <= 0f) return
        val id = synchronized(ids) { ids[cue] } ?: return

        val now = SystemClock.uptimeMillis()
        synchronized(lastPlayedMs) {
            val last = lastPlayedMs[cue] ?: 0L
            if (now - last < minGapMs(cue)) return
            lastPlayedMs[cue] = now
        }
        // Per-stream volume — this is what makes the options slider real rather than cosmetic.
        // Note it scales WITHIN the device's media volume; Android gives no way to exceed that,
        // so "independent of system volume" means "a separate control", not "louder than the OS".
        // The slider's 100% is UI_SOUND_MAX_GAIN, not full scale — see that constant.
        val gain = volume * UI_SOUND_MAX_GAIN
        pool.play(id, gain, gain, 1, 0, 1f)
    }

    /* ---- Synthesis ---------------------------------------------------------------------- */

    /** One damped sine partial of a cue. */
    private data class Partial(val freq: Double, val amp: Double, val decay: Double)

    /**
     * Per-cue voicing. Each cue is a handful of exponentially-damped sinusoids plus an optional
     * noise transient at the very start — the transient is what makes a click read as a physical
     * contact rather than a beep.
     *
     * `gain` sets the cues' loudness RELATIVE to each other and is baked into the sample data, so
     * the volume preference stays a single clean master multiplier.
     */
    private class Voice(
        val durationMs: Int,
        val partials: List<Partial>,
        val noiseMs: Double,
        val noiseAmp: Double,
        val gain: Double
    )

    private fun voiceFor(cue: Cue): Voice = when (cue) {
        // Light, dry, high — it has to survive being heard hundreds of times in a row.
        Cue.KEY -> Voice(
            durationMs = 30,
            partials = listOf(
                Partial(1500.0, 0.55, 90.0),
                Partial(2600.0, 0.25, 150.0)
            ),
            noiseMs = 3.0, noiseAmp = 0.18, gain = 0.55
        )
        // Lower and with more sustain, so a settings change reads as more consequential than a
        // keystroke without being loud.
        Cue.TOGGLE -> Voice(
            durationMs = 70,
            partials = listOf(
                Partial(760.0, 0.50, 45.0),
                Partial(1520.0, 0.22, 70.0)
            ),
            noiseMs = 4.0, noiseAmp = 0.12, gain = 0.75
        )
        // Barely there by design: this one can fire 20 times during a single slider drag.
        Cue.TICK -> Voice(
            durationMs = 16,
            partials = listOf(Partial(2400.0, 0.35, 220.0)),
            noiseMs = 0.0, noiseAmp = 0.0, gain = 0.40
        )
        // The heavy one — low fundamental, longer tail, audible transient.
        Cue.ACTION -> Voice(
            durationMs = 120,
            partials = listOf(
                Partial(300.0, 0.50, 26.0),
                Partial(600.0, 0.30, 34.0),
                Partial(900.0, 0.12, 60.0)
            ),
            noiseMs = 6.0, noiseAmp = 0.20, gain = 1.0
        )
    }

    /** Linear ramp in, to stop the waveform starting on a step (which is itself an audible click). */
    private const val ATTACK_MS = 1.5

    /** Linear ramp out, so the clip cannot end mid-cycle and pop on truncation. */
    private const val RELEASE_MS = 3.0

    private fun synthesize(cue: Cue): ShortArray {
        val v = voiceFor(cue)
        val n = (SAMPLE_RATE * v.durationMs / 1000.0).roundToInt()
        val buf = DoubleArray(n)
        // Fixed seed: the cached file must be byte-identical between runs, or the version-keyed
        // cache would be handing back a different clip than the one that was auditioned.
        val rng = Random(cue.ordinal.toLong() * 7919L + 13L)

        val attack = max(1.0, SAMPLE_RATE * ATTACK_MS / 1000.0)
        val release = max(1.0, SAMPLE_RATE * RELEASE_MS / 1000.0)
        val noiseSamples = SAMPLE_RATE * v.noiseMs / 1000.0

        for (i in 0 until n) {
            val t = i.toDouble() / SAMPLE_RATE
            var s = 0.0
            v.partials.forEach { p ->
                s += p.amp * sin(2.0 * PI * p.freq * t) * exp(-p.decay * t)
            }
            if (i < noiseSamples) {
                // Noise fades across its own short window rather than stopping dead.
                val k = 1.0 - (i / noiseSamples)
                s += (rng.nextDouble() * 2.0 - 1.0) * v.noiseAmp * k
            }
            var env = 1.0
            if (i < attack) env *= i / attack
            val fromEnd = n - 1 - i
            if (fromEnd < release) env *= fromEnd / release
            buf[i] = s * env
        }

        // Normalise to the cue's own peak, THEN apply its relative gain, so voicing tweaks above
        // cannot accidentally change how loud one cue is against the others.
        var peak = 0.0
        buf.forEach { if (kotlin.math.abs(it) > peak) peak = kotlin.math.abs(it) }
        val scale = if (peak > 0.0) (v.gain * 0.9 / peak) else 0.0

        return ShortArray(n) { i ->
            (buf[i] * scale * Short.MAX_VALUE)
                .coerceIn(Short.MIN_VALUE.toDouble(), Short.MAX_VALUE.toDouble())
                .toInt().toShort()
        }
    }

    /**
     * 16-bit mono PCM WAV. Written `.part` then renamed so a process killed mid-write can never
     * leave a truncated file that would then be cached and loaded forever (the font cache uses the
     * same guard for the same reason).
     */
    private fun writeWav(target: File, pcm: ShortArray) {
        val dataBytes = pcm.size * 2
        val out = java.io.ByteArrayOutputStream(44 + dataBytes)

        fun ascii(s: String) = out.write(s.toByteArray(Charsets.US_ASCII))
        fun le32(value: Int) {
            out.write(value and 0xFF); out.write((value ushr 8) and 0xFF)
            out.write((value ushr 16) and 0xFF); out.write((value ushr 24) and 0xFF)
        }
        fun le16(value: Int) { out.write(value and 0xFF); out.write((value ushr 8) and 0xFF) }

        ascii("RIFF"); le32(36 + dataBytes); ascii("WAVE")
        ascii("fmt "); le32(16)
        le16(1)                       // PCM
        le16(1)                       // mono
        le32(SAMPLE_RATE)
        le32(SAMPLE_RATE * 2)         // byte rate (mono, 2 bytes/sample)
        le16(2)                       // block align
        le16(16)                      // bits per sample
        ascii("data"); le32(dataBytes)
        pcm.forEach { le16(it.toInt()) }

        val part = File(target.parentFile, target.name + ".part")
        part.writeBytes(out.toByteArray())
        if (!part.renameTo(target)) {
            part.delete()
            throw java.io.IOException("could not place ${target.name}")
        }
    }
}

/**
 * Turns continuous slider travel into discrete notches, so dragging feels tactile instead of
 * turning into a buzz.
 *
 * A Material `Slider`'s `onValueChange` fires on every pointer sample — potentially every frame —
 * so playing a cue per callback is the audio-spam failure this exists to prevent. Quantising the
 * value into [steps] notches and firing only on a CHANGE of notch bounds a full-travel drag to
 * [steps] sounds no matter how the user moves. (`UiSounds.play`'s per-cue gap is the second line of
 * defence, for a flick that crosses several notches within one frame.)
 *
 * Deliberately NOT "play only on release": the point of the sound is to confirm the drag is
 * registering while it happens, which release-only feedback cannot do.
 *
 * Remember one per slider — the notch it last reported is the state that makes this work.
 */
class SliderTickThrottle(private val steps: Int = 20) {
    private var lastNotch = Int.MIN_VALUE

    fun onValue(value: Float, range: ClosedFloatingPointRange<Float>) {
        val span = range.endInclusive - range.start
        if (span <= 0f) return
        val notch = (((value - range.start) / span) * steps).roundToInt()
        if (notch == lastNotch) return
        lastNotch = notch
        UiSounds.play(UiSounds.Cue.TICK)
    }
}
