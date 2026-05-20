package org.openmw.utils

import android.content.Context
import android.util.Log
import androidx.compose.ui.graphics.Color
import org.openmw.Constants
import org.openmw.Constants.RANDOM_NUM
import org.openmw.ui.controls.UIStateManager.gameList
import org.openmw.ui.controls.UIStateManager.userUI
import org.openmw.ui.view.addCustomLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class ManageAssets(private val context: Context) {

    fun copy(src: String, dst: String) {
        val assetManager = context.assets
        val assets = assetManager.list(src) ?: return

        if (assets.isEmpty()) {
            copyFile(src, dst)
        } else {
            File(dst).apply { mkdirs() }
            assets.forEach { asset ->
                copy("$src/$asset", "$dst/$asset")
            }
        }
    }

    private fun copyFile(src: String, dst: String) {
        val destinationFile = File(dst)
        if (destinationFile.exists()) {
            Log.d("ManageAssets", "File $dst already exists, skipping copy.")
            addCustomLog(
                "ManageAssets, File $dst already exists, skipping copy.",
                textSize = 10,
                textColor = Color.White
            )
            return
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
            val uiCfgFile = File("$uiDir/ui.cfg")

            if (!uiCfgFile.exists()) {
                assetCopier.copy("libopenmw/ui", uiDir)
            }
        }
        copyIfNotExists("libopenmw/openmw/settings.fallback.cfg", Constants.SETTINGS_FILE)
        copyIfNotExists("libopenmw/ui/input_v3.xml", Constants.USER_CONFIG + "/input_v3.xml")
    }

    fun resourcePrepare() {
        val versionFile = File("${Constants.USER_RESOURCES}/version")
        val versionContent = if (versionFile.exists()) {
            versionFile.readLines().lastOrNull { it.isNotBlank() }?.trim() ?: ""
        } else {
            ""
        }
        val RANDOM_NUM = "Alpha-8086"
        if (versionContent != RANDOM_NUM) {
            Log.d("onFirstLaunch", "Version mismatch: versionContent='$versionContent', expected='$RANDOM_NUM'")
            // Delete the USER_RESOURCES folder if it exists
            val userResourcesDir = File(Constants.USER_RESOURCES)
            if (userResourcesDir.exists()) {
                Log.d("onFirstLaunch", "Deleting USER_RESOURCES folder: ${Constants.USER_RESOURCES}")
                val deleted = userResourcesDir.deleteRecursively()
                Log.d("onFirstLaunch", "USER_RESOURCES folder deletion ${if (deleted) "successful" else "failed"}")
            } else {
                Log.d("onFirstLaunch", "USER_RESOURCES folder does not exist: ${Constants.USER_RESOURCES}")
            }
            // Copy the resources after deletion
            Log.d("onFirstLaunch", "Copying resources from libopenmw/resources to ${Constants.USER_RESOURCES}")
            assetCopier.copy("libopenmw/resources", Constants.USER_RESOURCES)
        } else {
            Log.d("onFirstLaunch", "Version match: versionContent='$versionContent', no action needed")
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
        File(Constants.USER_CONFIG).mkdirs()
        if (!File(Constants.USER_FILE_STORAGE + "/resources/libuqm").isDirectory) {
            assetCopier.copy("libuqm", Constants.USER_RESOURCES + "/libuqm")
        }
    }
}
