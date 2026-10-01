package org.openmw.utils

import android.content.Context
import android.hardware.display.DisplayManager
import android.os.Build
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import org.openmw.BuildConfig
import org.openmw.Constants
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Everything a player needs to hand over when reporting a problem, gathered in one place.
 *
 * Two outputs:
 * - [displayReport]: a plain-text description of every display Android reports and of what
 *   [DisplayRoles] decided to do with them. Small enough for the clipboard, and the one piece of
 *   information a dual-screen bug on unfamiliar hardware cannot be diagnosed without (the AYANEO
 *   Pocket DS report, Oct 2026, is the case that prompted it: its MAIN display is the bottom panel,
 *   the opposite of the Thor, and nothing in the app could tell us what it reports).
 * - [writeBugReport]: a zip under `<OpenMW-DS>/bug-reports/` holding that text, device and app
 *   details, the engine log, both `openmw.cfg` files, `settings.cfg`, the crash logs and the
 *   app's own logcat. Saved where a file manager can reach it, because the app has no
 *   FileProvider to share from; the player attaches the file to their report themselves.
 *
 * **No saves, no game data, no files outside the app's own folders.** The configs do contain the
 * paths of the player's mod folders, which the card says before anything is written.
 *
 * Pure I/O: call [writeBugReport] off the main thread.
 */
object BugReport {

    private const val TAG = "BugReport"

    /** Kept small on purpose: a report is made, attached and forgotten, and each one is a few MB.
     *  Applied per kind, so saving display info never pushes out a bug report or the reverse. */
    private const val MAX_REPORTS_KEPT = 5

    private const val REPORT_PREFIX = "OpenMW-DS-report-"
    private const val DISPLAY_PREFIX = "OpenMW-DS-display-"

    /** The useful part of a long session's log is its END, where the failure is. */
    private const val OPENMW_LOG_TAIL_BYTES = 2L * 1024 * 1024
    private const val SMALL_LOG_TAIL_BYTES = 256L * 1024

    /** Bounded so a wedged `logcat` can never hang the report. */
    private const val LOGCAT_TIMEOUT_SECONDS = 10L
    private const val LOGCAT_LINES = 5000

    fun reportsDir(): File = File("${Constants.USER_FILE_STORAGE}/bug-reports")

    private fun timestamp(): String = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())

    private fun ensureReportsDir(): File {
        val dir = reportsDir()
        if (!dir.exists() && !dir.mkdirs()) error("Could not create ${dir.absolutePath}")
        return dir
    }

    /**
     * Save [displayReport] as a text file beside the bug reports, for a player who wants to attach
     * it rather than paste it (the clipboard alone left no way to keep it). Pure I/O.
     */
    fun saveDisplayReport(context: Context, launcherDisplayId: Int? = null): File {
        val dir = ensureReportsDir()
        val out = File(dir, "$DISPLAY_PREFIX${timestamp()}.txt")
        out.writeText(displayReport(context, launcherDisplayId))
        prune(dir, DISPLAY_PREFIX, ".txt")
        Log.i(TAG, "wrote ${out.absolutePath}")
        return out
    }

    /**
     * Every display Android reports, plus what [DisplayRoles] resolved for this device.
     *
     * [launcherDisplayId] is the display the launcher itself is showing on, when the caller knows
     * it; it is reported because the launcher always moves itself to [Display.DEFAULT_DISPLAY], so
     * a mismatch here is a finding in its own right.
     */
    fun displayReport(context: Context, launcherDisplayId: Int? = null): String = buildString {
        val dm = context.getSystemService(Context.DISPLAY_SERVICE) as? DisplayManager
        val all = dm?.displays.orEmpty()
        val presentationIds = dm?.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
            .orEmpty().map { it.displayId }.toSet()

        appendLine("== Displays ==")
        appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE}), Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        appendLine("App: OpenMW-DS ${BuildConfig.RELEASE_VERSION} (${BuildConfig.VERSION_NAME}, code ${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
        appendLine("Displays reported: ${all.size}")
        if (launcherDisplayId != null) appendLine("Launcher is on display: $launcherDisplayId")
        for (d in all) {
            appendLine()
            appendLine(describeDisplay(d, presentationIds.contains(d.displayId)))
        }

        appendLine()
        appendLine("== Display roles ==")
        runCatching {
            appendLine("Profile: ${DisplayRoles.profile(context)}")
            appendLine("Second display available (swap supported): ${DisplayRoles.swapSupported(context)}")
            appendLine("Roles swapped: ${DisplayRoles.rolesSwapped(context)}")
            appendLine("Single screen: ${DisplayRoles.singleScreen(context)}")
            appendLine("Game display id: ${DisplayRoles.gameDisplayId(context)}")
            appendLine("Companion display id: ${DisplayRoles.companionDisplay(context)?.displayId ?: "none"}")
            appendLine("Companion host: ${if (DisplayRoles.companionUsesActivity(context)) "activity" else "presentation"}")
            if (DisplayRoles.isCustom(context)) {
                val (g, c) = DisplayRoles.customChoiceDescriptors(context)
                appendLine("Custom game choice: ${g ?: "(not chosen, main display)"}")
                appendLine("Custom companion choice: ${c ?: "(not chosen, first other display)"}")
            }
        }.onFailure { appendLine("Could not resolve roles: $it") }

        org.openmw.IdentifyScreenActivity.lastResultText()?.let {
            appendLine()
            appendLine("== Identify screens (last run) ==")
            appendLine(it)
        }

        appendLine()
        appendLine("== settings.cfg (app-owned keys) ==")
        appendLine(ownedSettingsLines())
    }

    private fun describeDisplay(d: Display, isPresentation: Boolean): String = buildString {
        val real = DisplayMetrics()
        @Suppress("DEPRECATION")
        d.getRealMetrics(real)
        val tags = buildList {
            if (d.displayId == Display.DEFAULT_DISPLAY) add("DEFAULT")
            if (isPresentation) add("PRESENTATION-category")
            val f = d.flags
            if (f and Display.FLAG_PRESENTATION != 0) add("FLAG_PRESENTATION")
            if (f and Display.FLAG_PRIVATE != 0) add("FLAG_PRIVATE")
            if (f and Display.FLAG_SECURE != 0) add("FLAG_SECURE")
            if (f and Display.FLAG_ROUND != 0) add("FLAG_ROUND")
            if (f and Display.FLAG_SUPPORTS_PROTECTED_BUFFERS != 0) add("FLAG_PROTECTED_BUFFERS")
        }
        appendLine("Display ${d.displayId}: \"${d.name}\"")
        appendLine("  tags: ${tags.joinToString(", ").ifEmpty { "(none)" }} (raw flags 0x${Integer.toHexString(d.flags)})")
        appendLine("  real size: ${real.widthPixels}x${real.heightPixels}, density ${real.densityDpi}dpi")
        val mode = d.mode
        appendLine("  mode: ${mode.physicalWidth}x${mode.physicalHeight} @ ${"%.1f".format(Locale.US, mode.refreshRate)}Hz")
        append("  state: ${stateName(d.state)}, rotation ${d.rotation * 90}")
    }

    private fun stateName(state: Int) = when (state) {
        Display.STATE_ON -> "ON"
        Display.STATE_OFF -> "OFF"
        Display.STATE_DOZE -> "DOZE"
        Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
        Display.STATE_VR -> "VR"
        Display.STATE_ON_SUSPEND -> "ON_SUSPEND"
        else -> "UNKNOWN($state)"
    }

    /** The three keys the app writes every launch: what the game was actually told to render at. */
    private fun ownedSettingsLines(): String {
        val file = File(Constants.SETTINGS_FILE)
        if (!file.exists()) return "(settings.cfg not found)"
        val wanted = Regex("""^\s*(resolution x|resolution y|scaling factor|window mode)\s*=""")
        return runCatching {
            file.readLines().filter { wanted.containsMatchIn(it) }.joinToString("\n").ifEmpty { "(keys not present)" }
        }.getOrElse { "(could not read: $it)" }
    }

    /**
     * Write the zip and return it, or throw. Older reports beyond [MAX_REPORTS_KEPT] are removed
     * afterwards so the folder does not grow without bound.
     *
     * [extraSections] is appended to `report.txt` verbatim (title to body), so other screens can
     * contribute findings without this file knowing about them.
     */
    fun writeBugReport(
        context: Context,
        launcherDisplayId: Int? = null,
        extraSections: Map<String, String> = emptyMap(),
    ): File {
        val dir = ensureReportsDir()
        val stamp = timestamp()
        val out = File(dir, "$REPORT_PREFIX$stamp.zip")

        ZipOutputStream(FileOutputStream(out)).use { zip ->
            val summary = buildString {
                appendLine("OpenMW-DS bug report, created $stamp")
                appendLine()
                append(displayReport(context, launcherDisplayId))
                for ((title, body) in extraSections) {
                    appendLine()
                    appendLine()
                    appendLine("== $title ==")
                    append(body)
                }
                appendLine()
            }
            zip.putText("report.txt", summary)

            zip.putTail("openmw.log", File(Constants.OPENMW_LOG), OPENMW_LOG_TAIL_BYTES)
            zip.putTail("MyGUI.log", File(Constants.USER_CONFIG, "MyGUI.log"), SMALL_LOG_TAIL_BYTES)
            zip.putTail("native_crash.log", File(Constants.USER_CONFIG, "native_crash.log"), SMALL_LOG_TAIL_BYTES)
            zip.putTail("crash.log", File(Constants.CRASH_FILE), SMALL_LOG_TAIL_BYTES)
            zip.putTail("crash-internal.log", File(Constants.INTERNAL_CRASH_FILE), SMALL_LOG_TAIL_BYTES)
            zip.putTail("settings.cfg", File(Constants.SETTINGS_FILE), SMALL_LOG_TAIL_BYTES)
            // Two different files with the same name: see "The openmw.cfg write path" in
            // CLAUDE_SETTINGS.md. The internal one holds resources=, the app's own data= lines and
            // the fallback block, and the two disagreeing has caused real bugs before.
            zip.putTail("openmw.cfg", File(Constants.USER_OPENMW_CFG), SMALL_LOG_TAIL_BYTES)
            zip.putTail("openmw-internal.cfg", File(Constants.OPENMW_CFG), SMALL_LOG_TAIL_BYTES)
            zip.putText("logcat.txt", ownLogcat())
        }

        prune(dir, REPORT_PREFIX, ".zip")
        Log.i(TAG, "wrote ${out.absolutePath} (${out.length()} bytes)")
        return out
    }

    private fun ZipOutputStream.putText(name: String, text: String) {
        putNextEntry(ZipEntry(name))
        write(text.toByteArray())
        closeEntry()
    }

    /** The last [maxBytes] of [file], with a note when it was cut. Missing files are skipped. */
    private fun ZipOutputStream.putTail(name: String, file: File, maxBytes: Long) {
        if (!file.exists() || !file.canRead()) return
        runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val len = raf.length()
                val start = (len - maxBytes).coerceAtLeast(0L)
                val bytes = ByteArray((len - start).toInt())
                raf.seek(start)
                raf.readFully(bytes)
                putNextEntry(ZipEntry(name))
                if (start > 0) write("[... first $start bytes omitted ...]\n".toByteArray())
                write(bytes)
                closeEntry()
            }
        }.onFailure { Log.w(TAG, "skipped $name: $it") }
    }

    /**
     * The app's own logcat. An app can read its OWN log lines without any permission (Android
     * restricts an unprivileged reader to its own UID), and those carry every Kotlin `Log.*` call,
     * including [DisplayRoles] and the second-screen setup, which never reach openmw.log.
     *
     * **Deliberately NOT filtered by `--pid`.** The game and the launcher share a process, and that
     * process ends when the game is quit, so the launcher a player reports FROM is a new process.
     * `--pid` kept only its 50-odd startup lines and dropped the whole game session, which is the
     * part a dual-screen report needs. The UID restriction already keeps other apps out.
     */
    private fun ownLogcat(): String = runCatching {
        val proc = ProcessBuilder(
            "logcat", "-d", "-v", "threadtime", "-t", LOGCAT_LINES.toString(),
        ).redirectErrorStream(true).start()
        val text = proc.inputStream.bufferedReader().readText()
        if (!proc.waitFor(LOGCAT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) proc.destroy()
        text.ifEmpty { "(logcat returned nothing)" }
    }.getOrElse { "(logcat unavailable: $it)" }

    private fun prune(dir: File, prefix: String, suffix: String) {
        val reports = dir.listFiles { f -> f.isFile && f.name.startsWith(prefix) && f.name.endsWith(suffix) }
            ?: return
        reports.sortedByDescending { it.lastModified() }
            .drop(MAX_REPORTS_KEPT)
            .forEach { runCatching { it.delete() } }
    }
}
