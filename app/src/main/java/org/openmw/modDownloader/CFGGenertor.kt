package org.openmw.modDownloader

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.openmw.Constants
import org.openmw.modDownloader.ModListManager.modList
import org.openmw.utils.GameFilesPreferences.getGameFilesUri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL

private val pluginsToCommentOut: MutableSet<String> = mutableSetOf()

suspend fun loadPluginsToCommentOut() {
    if (pluginsToCommentOut.isNotEmpty()) return  // already loaded

    withContext(Dispatchers.IO) {
        try {
            val url = URL("https://gitlab.com/cavebros/openmw-android-docker/-/raw/main/patches/linesToCommentOut.txt")
            //val url = URL("https://gitlab.com/duron27/alpha3/-/raw/main/app/linesToCommentOut.txt")
            val text = url.readText()

            val lines = text.lines()
                .map { it.trim() }
                .filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("//") }

            pluginsToCommentOut.clear()
            pluginsToCommentOut.addAll(lines)

            //println("Loaded ${pluginsToCommentOut.size} plugins to comment out")
            Log.d("ConfigSave", "Loaded ${pluginsToCommentOut.size} plugins to comment out")

        } catch (e: Exception) {
            println("Failed to load remote plugin blacklist: ${e.message}")
            pluginsToCommentOut.addAll(
                setOf(
                    "content=Shal Overhaul Skeleton Fix.esp",
                    "content=Clean_Vennin's Lleran Overhaul.ESP",
                    "content=Clean_Vennin's Punabi.ESP",
                    "content=Clean_Vennin's Hlaalu Tomb.ESP",
                    "content=Clean_Vennin's Shal Overhaul.ESP",
                    "content=Clean_sm_golden_mask.ESP",
                    "content=Clean_Daedric_Maul.ESP",
                    "content=Clean_Mines and Caverns.esp",
                    "content=Clean_Vennin's Pulk Overhaul.ESP",
                    "content=Clean_Andrethi.esp",
                    "content=Clean_Vennin's Ulummusa Overhaul.ESP",
                    "content=Clean_Vennin's Hlervi Tomb.ESP",
                    "content=Clean_Addamasartus Overhaul.ESP",
                    "content=Clean_imperial_skirt.esp"
                )
            )
        }
    }
}

fun saveConfigToFile(fileName: String, content: String): File {
    val file = File("${Constants.USER_FILE_STORAGE}/OpenMW/", fileName)
    FileOutputStream(file).use { output ->
        output.write(content.toByteArray())
    }
    return file
}

fun fetchJsonFromUrl(url: String): String {
    val client = OkHttpClient()
    val request = Request.Builder().url(url).build()
    client.newCall(request).execute().use { response ->
        if (response.isSuccessful) {
            return response.body.string()
        } else {
            throw Exception("Failed to fetch JSON: ${response.code}")
        }
    }
}

data class OpenMWConfig(
    val fallbackValues: String,
    val dataPaths: List<String>,
    val fallbackArchives: List<String>,
    val contentFiles: List<String>,
    val groundcoverFiles: String
)

fun parseOpenMWConfig(jsonString: String): OpenMWConfig {
    val jsonObject = JSONObject(jsonString)
    val openmwCfg = jsonObject.getJSONObject("openmw_cfg")

    // Helper function to transform paths
    fun transformPath(path: String): String {
        return path
            .replace("C:\\games\\OpenMWMods\\", "${Constants.USER_FILE_STORAGE}/OpenMW/Mods/")
            .replace("\\", "/")
    }

    val fallbackValues = transformPath(openmwCfg.getString("fallback values"))
    val dataPaths = openmwCfg.getString("data paths")
        .split("\n")
        .filter { it.isNotBlank() }
        .map { transformPath(it) }
    val fallbackArchives = openmwCfg.getString("fallback archives")
        .split("\n")
        .filter { it.isNotBlank() }
        .map { transformPath(it) }
    val contentFiles = openmwCfg.getString("content files")
        .split("\n")
        .filter { it.isNotBlank() }
        .map { transformPath(it) }
    val groundcoverFiles = transformPath(openmwCfg.getString("groundcover files"))

    return OpenMWConfig(
        fallbackValues = fallbackValues,
        dataPaths = dataPaths,
        fallbackArchives = fallbackArchives,
        contentFiles = contentFiles,
        groundcoverFiles = groundcoverFiles
    )
}

suspend fun formatOpenMWConfig(context: Context, config: OpenMWConfig): String {
    loadPluginsToCommentOut()
    val gameFilesUri = getGameFilesUri(context)
    val builder = StringBuilder()

    // Fallback Values
    builder.append(config.fallbackValues).append("\n\n")

    // Data Paths
    builder.append("## Data Paths\n")
    builder.append("data=$gameFilesUri/Data Files\n")
    config.dataPaths.forEach { path ->
        builder.append("$path\n")
    }
    builder.append("\n")

    // BSAs
    builder.append("## BSAS\n")
    config.fallbackArchives.forEach { archive ->
        builder.append("$archive\n")
    }
    builder.append("\n")

    // Plugins
    builder.append("## Plugins\n")

    config.contentFiles
        .filter {
            it !in listOf(
                "content=S3LightFixes.omwaddon",
                "content=delta-merged.omwaddon",
                "content=deleted_groundcover.omwaddon"
            )
        }
        .forEach { content ->
            val contentLine = if (content.startsWith("content=")) {
                content
            } else {
                "content=$content"
            }

            // Comment out specific lines, keep others as-is
            if (contentLine in pluginsToCommentOut) {
                builder.append(";$contentLine\n")
            } else {
                builder.append("$contentLine\n")
            }
        }

    return builder.toString()
}
