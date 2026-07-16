package org.openmw.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import org.openmw.Constants
import java.io.File

/**
 * Phase 2 migration from the old, shared Alpha3 identity/folder
 * (`/storage/emulated/0/Alpha3/`) into this app's own root
 * (`/storage/emulated/0/OpenMW-DS/`, i.e. [Constants.USER_FILE_STORAGE]).
 *
 * Covers ONLY: copy saves + copy three settings files. NO mod copying and NO
 * game-files-folder carryover (that is deferred to a later phase's guided picker).
 *
 * HARD RULE: the old `/Alpha3/` folder is treated as READ-ONLY. Every operation
 * here only reads from it (via [File.copyTo], which reads source / writes dest) and
 * never writes, renames, or deletes anything under it.
 */
object AlphaMigration {

    private const val TAG = "AlphaMigration"

    // Migration bookkeeping lives in this app's own (internal) SharedPreferences.
    private const val PREFS = "alpha_migration"
    private const val KEY_PROMPTED = "migration_prompted"
    // savemap:<sourceCharacterName> -> the destination folder name migration copied it
    // into (either "<name>" or a de-collided "<name> - N"). Lets a re-run merge back
    // into the SAME destination it first created, instead of spawning duplicates.
    private const val SAVEMAP_PREFIX = "savemap:"

    /** The old shared folder root. NOT [Constants.USER_FILE_STORAGE] (that's ours now). */
    fun oldFolderRoot(): File =
        File(Environment.getExternalStorageDirectory(), "Alpha3")

    /** True when an old Alpha3 (or pre-split OpenMW-DS v0.7.0) folder is present. */
    fun oldFolderExists(): Boolean = oldFolderRoot().isDirectory

    // ---- one-shot first-launch prompt gate ----

    fun wasPrompted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_PROMPTED, false)

    fun markPrompted(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PROMPTED, true).apply()
    }

    // ---------------------------------------------------------------- saves ----

    data class SaveResult(
        val charactersProcessed: Int = 0,
        val filesCopied: Int = 0,
        val filesSkippedNewer: Int = 0,
        val collisionsRenamed: Int = 0,
    )

    /**
     * Copy every character folder from the old `saves/` into our `saves/`.
     *
     * - Fresh character (no destination folder of that name) → copy as-is.
     * - Destination already holds a folder of that name that migration itself
     *   previously created (tracked in [SAVEMAP_PREFIX]) → this is the SAME migrated
     *   character being re-copied → merge into it, skipping any `.omwsave` whose
     *   destination copy is newer than the source (protects saves made in the new app
     *   after an earlier migration).
     * - Destination holds a same-named folder that is NOT a prior migration (a
     *   genuinely different playthrough created in the new app) → do not merge; copy
     *   the incoming character into the next free `"<name> - N"` slot (OpenMW's own
     *   collision convention — note the exact `" - "` spacing).
     */
    fun copySaves(context: Context): SaveResult {
        val srcSaves = File(oldFolderRoot(), "saves")
        if (!srcSaves.isDirectory) {
            Log.d(TAG, "copySaves: no source saves dir at ${srcSaves.absolutePath}")
            return SaveResult()
        }

        val destSaves = File(Constants.USER_SAVES)
        destSaves.mkdirs()

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        var chars = 0
        var copied = 0
        var skipped = 0
        var renamed = 0

        val srcCharDirs = srcSaves.listFiles { f -> f.isDirectory } ?: emptyArray()
        for (srcChar in srcCharDirs) {
            val name = srcChar.name

            // Where did we last migrate this source character to?
            val mapped = prefs.getString(SAVEMAP_PREFIX + name, null)
            val destName: String
            if (mapped != null && File(destSaves, mapped).isDirectory) {
                // Re-run of a prior migration → merge back into the same destination.
                destName = mapped
            } else {
                val direct = File(destSaves, name)
                if (!direct.exists()) {
                    destName = name
                } else {
                    // Same name but a different (non-migrated) playthrough → de-collide.
                    destName = nextFreeSuffixedName(destSaves, name)
                    renamed++
                }
            }

            val destChar = File(destSaves, destName)
            destChar.mkdirs()

            val srcFiles = srcChar.listFiles { f -> f.isFile } ?: emptyArray()
            for (srcFile in srcFiles) {
                val destFile = File(destChar, srcFile.name)
                if (destFile.exists() && destFile.lastModified() > srcFile.lastModified()) {
                    // Destination copy is newer → keep it, skip the older source save.
                    skipped++
                    continue
                }
                try {
                    srcFile.copyTo(destFile, overwrite = true)
                    copied++
                } catch (e: Exception) {
                    Log.e(TAG, "copySaves: failed copying ${srcFile.absolutePath}", e)
                }
            }

            prefs.edit().putString(SAVEMAP_PREFIX + name, destName).apply()
            chars++
        }

        val result = SaveResult(chars, copied, skipped, renamed)
        Log.d(TAG, "copySaves: $result")
        return result
    }

    /** Next unused `"<name> - N"` (N starting at 1), matching OpenMW's collision naming. */
    private fun nextFreeSuffixedName(destSaves: File, name: String): String {
        var n = 1
        while (File(destSaves, "$name - $n").exists()) n++
        return "$name - $n"
    }

    // ------------------------------------------------------------- settings ----

    data class SettingsResult(
        val copied: List<String> = emptyList(),
        val missing: List<String> = emptyList(),
    )

    /**
     * Overwrite-copy EXACTLY three settings files, old → new. Nothing else — in
     * particular NOT `openmw.cfg` (regenerated from [Constants]) and NOT the Lua
     * `*_storage.bin` files (so the curated new defaults survive).
     */
    fun copySettings(): SettingsResult {
        val oldRoot = oldFolderRoot()
        val oldConfig = File(oldRoot, "config")

        // (label, source, destination)
        val files = listOf(
            Triple(
                "settings.cfg",
                File(oldConfig, "settings.cfg"),
                File(Constants.SETTINGS_FILE),
            ),
            Triple(
                "input_v3.xml",
                File(oldConfig, "input_v3.xml"),
                File(Constants.USER_CONFIG, "input_v3.xml"),
            ),
            Triple(
                "button_configs.json",
                File(oldRoot, "OpenMW/ui/button_configs.json"),
                File(Constants.USER_FILE_STORAGE, "OpenMW/ui/button_configs.json"),
            ),
        )

        val copied = mutableListOf<String>()
        val missing = mutableListOf<String>()
        for ((label, src, dest) in files) {
            if (!src.isFile) {
                missing += label
                continue
            }
            try {
                dest.parentFile?.mkdirs()
                src.copyTo(dest, overwrite = true)
                copied += label
            } catch (e: Exception) {
                Log.e(TAG, "copySettings: failed copying ${src.absolutePath}", e)
                missing += label
            }
        }

        val result = SettingsResult(copied, missing)
        Log.d(TAG, "copySettings: $result")
        return result
    }
}
