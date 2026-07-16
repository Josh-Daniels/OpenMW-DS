package org.openmw.utils

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import org.openmw.BuildConfig
import org.openmw.Constants
import org.openmw.ui.controls.UIStateManager.gameList
import org.openmw.ui.controls.UIStateManager.userUI
import org.openmw.ui.view.addCustomLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ManageAssets(private val context: Context) {

    fun copy(src: String, dst: String, overwrite: Boolean = false) {
        val assetManager = context.assets
        val assets = assetManager.list(src) ?: return

        if (assets.isEmpty()) {
            copyFile(src, dst, overwrite)
        } else {
            File(dst).apply { mkdirs() }
            assets.forEach { asset ->
                copy("$src/$asset", "$dst/$asset", overwrite)
            }
        }
    }

    private fun copyFile(src: String, dst: String, overwrite: Boolean = false) {
        val destinationFile = File(dst)
        if (destinationFile.exists()) {
            if (!overwrite) {
                Log.d("ManageAssets", "File $dst already exists, skipping copy.")
                addCustomLog(
                    "ManageAssets, File $dst already exists, skipping copy.",
                    textSize = 10,
                    textColor = Color.White
                )
                return
            }
            // overwrite=true: always copy — size-only comparison is unreliable
            // (files that change but keep the same byte count would be wrongly skipped).
            // Callers that pass overwrite=true (resourcePrepare) are already gated by the
            // version stamp, so unconditional copy here is correct and still fast on
            // subsequent launches when the stamp matches and this code is never reached.
        }
        try {
            context.assets.open(src).use { input ->
                FileOutputStream(dst).use { output ->
                    input.copyTo(output)
                }
            }
            Log.d("ManageAssets", "Copied file from $src to $dst")
            addCustomLog(
                "ManageAssets Copied file from $src to $dst",
                textSize = 10,
                textColor = Color.White
            )
        } catch (e: IOException) {
            Log.e("ManageAssets", "Error copying file from $src to $dst", e)
        }
    }
}

class UserManageAssets(val context: Context) {
    val assetCopier = ManageAssets(context)

    fun deleteRecursive(fileOrDirectory: File) {
        if (fileOrDirectory.isDirectory) {
            fileOrDirectory.listFiles()?.forEach { deleteRecursive(it) }
        }
        fileOrDirectory.delete()
    }

    fun resetUserConfig() {
        val settingsFile = File(Constants.SETTINGS_FILE)
        if (settingsFile.exists()) {
            Log.d("ManageAssets", "Deleting existing file: ${Constants.SETTINGS_FILE}")
            settingsFile.delete()
        }
        assetCopier.copy("libopenmw/openmw/settings.fallback.cfg", Constants.SETTINGS_FILE)
    }

    private fun copyIfNotExists(source: String, destination: String) {
        if (!File(destination).exists()) {
            Log.d("ManageAssets", "Copying $source to $destination")
            assetCopier.copy(source, destination)
        }
    }

    private fun ensureDirectoryExists(directory: String) {
        val dir = File(directory)
        if (!dir.isDirectory) {
            dir.mkdirs()
            Log.d("ManageAssets", "Created directory at $directory")
        }
    }

    fun onFirstLaunch() {

        assetCopier.copy("libopenmw/openmw", Constants.GLOBAL_CONFIG)
        // Setup storage directories
        listOf("OpenMW/Override", "OpenMW/CACHE", "OpenMW/Mods", "delta", "config", "dethrace", "uqm", "uqm/content").forEach { dir ->
            ensureDirectoryExists("${Constants.USER_FILE_STORAGE}/$dir")
        }
        if (!File("${Constants.USER_FILE_STORAGE}/.nomedia").exists()) {
            File("${Constants.USER_FILE_STORAGE}/.nomedia").writeText("nomedia")
        }
        if (!File(Constants.USER_OPENMW_CFG).exists()) {
            File(Constants.USER_OPENMW_CFG).writeText("# This is the user openmw.cfg. Feel free to modify it as you wish.\n")
        }
        gameList.forEach { game ->
            val uiDir = "${Constants.USER_FILE_STORAGE}/$game/ui"
            val uiCfgFile = File("$uiDir/button_configs.json")

            if (!uiCfgFile.exists()) {
                assetCopier.copy("libopenmw/ui", uiDir)
            }
        }

        copyIfNotExists("libopenmw/openmw/settings.fallback.cfg", Constants.SETTINGS_FILE)
        UserManageAssets(context).installUQMResourceFiles()
        org.openmw.fragments.onFirstLaunch(context)

        // General-purpose app identity/version marker (write-only; fire-and-forget).
        // Independent of the Alpha3 migration feature. Runs each launch so it always
        // reflects the current app + version. Never fails startup.
        IdentityMarker.write(context)
    }

    fun resourcePrepare() {
        val stampFile = File(Constants.VERSION_STAMP)
        val currentStamp = if (stampFile.exists()) stampFile.readText().trim() else ""
        val targetStamp = BuildConfig.RANDOMIZER.toString()

        if (currentStamp == targetStamp) {
            Log.d("ManageAssets", "Version match (stamp: $currentStamp), skipping resource update.")
            return
        }

        Log.d("ManageAssets", "Version mismatch: current='$currentStamp', target='$targetStamp'. Updating resources...")

        // Copy the resources with overwrite enabled
        Log.d("ManageAssets", "Smart copying resources from libopenmw/resources to ${Constants.USER_RESOURCES}")
        assetCopier.copy("libopenmw/openmw/defaults.bin", Constants.DEFAULTS_BIN, overwrite = true)
        assetCopier.copy("libopenmw/resources", Constants.USER_RESOURCES, overwrite = true)
        assetCopier.copy("companion", "${Constants.USER_FILE_STORAGE}/OpenMW/Mods/companion", overwrite = true)

        // Write the new stamp after successful copy
        try {
            stampFile.writeText(targetStamp)
            patchShaders()
            Log.d("ManageAssets", "Successfully updated resources and stamp: $targetStamp")
        } catch (e: Exception) {
            Log.e("ManageAssets", "Failed to write version stamp", e)
        }
    }

    fun resetUI() {
        val uiDir = File(userUI)
        uiDir.deleteRecursively()
        if (uiDir.exists()) {
            uiDir.delete()
        }
        ensureDirectoryExists(userUI)
        assetCopier.copy("libopenmw/ui", userUI)
    }

    fun installUQMResourceFiles() {
        val contentDir = File(Constants.USER_FILE_STORAGE, "uqm/content")
        if (!contentDir.exists()) {
            contentDir.mkdirs()
        }

        val versionFile = File(contentDir, "version")

        // Write EXACT content
        versionFile.writeText(
        $$"""
        0.8.3

        $Format:%ad$
        """.trimIndent()
        )
    }
}
