package org.openmw.utils

import android.content.Context
import android.os.Environment
import android.util.Log
import org.openmw.Constants
import org.openmw.ui.page.mod.ModValue
import org.openmw.ui.page.mod.readModValues
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

    /**
     * True when this device already has a REAL OpenMW-DS setup — at least one actual save file in
     * our own saves dir ([Constants.USER_SAVES] = `/OpenMW-DS/saves/`).
     *
     * This is the reliable "the user already has genuine progress" signal: saves live on EXTERNAL
     * storage and survive an uninstall+reinstall / signature-mismatch fresh install / app-data
     * clear — all of which DO wipe [wasPrompted]'s internal flag. So a returning player who hits a
     * reinstall event should NOT be force-shown the first-launch migration popup just because the
     * internal flag reset. We require a character folder containing an actual `.omwsave` (an empty
     * or leftover `saves/` dir does not count).
     */
    fun hasExistingSaves(): Boolean {
        val saves = File(Constants.USER_SAVES)
        if (!saves.isDirectory) return false
        val charDirs = saves.listFiles { f -> f.isDirectory } ?: return false
        return charDirs.any { dir ->
            dir.listFiles { f -> f.isFile && f.name.endsWith(".omwsave", ignoreCase = true) }
                ?.isNotEmpty() == true
        }
    }

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

    // ---------------------------------------------------------------------------------------
    // Mod load-order import (content= only)
    // ---------------------------------------------------------------------------------------

    /**
     * Outcome of planning a mod-order import. [entries] is what should be written (already in
     * Alpha3's order with ids renumbered from 1); a non-null [refusal] means write NOTHING.
     */
    data class ModOrderPlan(
        val entries: List<ModValue> = emptyList(),
        val skipped: List<String> = emptyList(),
        val refusal: Refusal? = null,
    ) {
        enum class Refusal { NO_SOURCE, EMPTY_SOURCE, WOULD_LOSE_MORROWIND }
    }

    /** Alpha3's own openmw.cfg. Read-only, like everything else under the old root. */
    fun oldOpenMwCfg(): File = File(oldFolderRoot(), "config/openmw.cfg")

    /**
     * Work out which of Alpha3's `content=` entries can be imported, WITHOUT writing anything.
     *
     * Deliberately scoped to `content=` — `data=` is left alone so the game-files path this
     * install already has (and the curated defaults [copySettings] protects by excluding
     * openmw.cfg) survive untouched. Alpha3's order becomes ours verbatim; this is an overwrite of
     * the content section, not a merge, because the whole point is transferring load ORDER and a
     * merge would preserve OUR order for every entry the two installs share.
     *
     * An entry is importable only if a file of that name exists under one of THIS install's
     * `data=` roots — matched case-insensitively, since the roots are typically on a
     * case-insensitive SD card while the cfg's spelling may differ. Unresolvable entries are
     * reported rather than written, so a missing mod can't turn into a load-time failure.
     *
     * Refuses outright rather than writing a partial list if Morrowind.esm wouldn't survive the
     * import — an install without it cannot launch.
     */
    fun planModOrderImport(): ModOrderPlan {
        val src = oldOpenMwCfg()
        if (!src.isFile) return ModOrderPlan(refusal = ModOrderPlan.Refusal.NO_SOURCE)

        val incoming = readModValues(src.absolutePath).filter { it.category == "content" }
        if (incoming.isEmpty()) return ModOrderPlan(refusal = ModOrderPlan.Refusal.EMPTY_SOURCE)

        // THIS install's data roots — never Alpha3's. Values round-trip through the parser with
        // their surrounding quotes, so strip those before treating them as paths.
        val roots = readModValues()
            .filter { it.category == "data" }
            .map { it.value.trim().removeSurrounding("\"") }
            .filter { it.isNotBlank() }

        // One listing per root, lowercased, so resolution is a set lookup rather than a stat per
        // (entry x root) and is case-insensitive.
        val available = buildSet {
            roots.forEach { root ->
                File(root).listFiles()?.forEach { if (it.isFile) add(it.name.lowercase()) }
            }
        }

        val resolved = mutableListOf<ModValue>()
        val skipped = mutableListOf<String>()
        incoming.forEach { entry ->
            if (entry.value.lowercase() in available) resolved += entry else skipped += entry.value
        }

        // Must be present AND enabled: a `;content=Morrowind.esm` would import as unchecked and
        // leave exactly the unlaunchable install this guard exists to prevent.
        if (resolved.none { it.value.equals("Morrowind.esm", ignoreCase = true) && it.isChecked }) {
            return ModOrderPlan(skipped = skipped, refusal = ModOrderPlan.Refusal.WOULD_LOSE_MORROWIND)
        }

        // writeModValuesToFile sorts by id, so ids must carry Alpha3's ordering.
        val ordered = resolved.mapIndexed { index, entry -> entry.copy(id = index + 1) }
        val plan = ModOrderPlan(entries = ordered, skipped = skipped)
        Log.d(TAG, "planModOrderImport: ${ordered.size} importable, ${skipped.size} skipped")
        return plan
    }
}
