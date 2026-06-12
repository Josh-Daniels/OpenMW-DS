package org.openmw.modDownloader

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.openmw.modDownloader.ModListManager.applicationContext
import org.openmw.modDownloader.StatusInfo.processing
import java.io.File

suspend fun importPotiModlistStructured(context: Context, filePath: String): Int = withContext(Dispatchers.IO) {
    val modDao = ModDatabase.getDatabase(applicationContext).modDao()
    var importedCount = 0

    val file = File(filePath)
    if (!file.exists()) {
        Log.w("POTI_IMPORT", "File not found: $filePath")
        processing = false
        return@withContext 0
    }

    Log.i("POTI_IMPORT", "Starting import of ${file.length() / 1_048_576.0} MB: $filePath")

    try {
        val jsonString = file.readText()
        val json = JSONObject(jsonString)
        val archivesArray = json.getJSONArray("Archives")

        for (i in 0 until archivesArray.length()) {
            val archive = archivesArray.getJSONObject(i)
            val meta = archive.optString("Meta", "")

            if (meta.contains("gameName=morrowind")) {
                val modIdMatch = Regex("modID=(\\d+)").find(meta)
                val fileIdMatch = Regex("fileID=(\\d+)").find(meta)

                if (modIdMatch != null && fileIdMatch != null) {
                    val modId = modIdMatch.groupValues[1]
                    val fileId = fileIdMatch.groupValues[1]

                    val fullName = archive.optString("Name", "").let { fileName ->
                        fileName.ifBlank {
                            "POTI Mod #$modId"
                        }
                    }

                    val name = archive.optString("Name", "").let { fileName ->
                        if (fileName.endsWith(".7z") || fileName.endsWith(".rar") || fileName.endsWith(".zip")) {
                            fileName.substringBeforeLast('.')
                        } else {
                            "POTI Mod #$modId"
                        }
                    }

                    // Get additional info from State object if available
                    val state = archive.optJSONObject("State")
                    val author = state?.optString("Author") ?: ""
                    val stateName = state?.optString("Name") ?: ""
                    val description = state?.optString("Description") ?: ""
                    val fileSize = archive.optLong("Size", 0L)

                    Log.d("POTI_IMPORT", "Detected modID=$modId, fileID=$fileId, Name=$stateName, Filename=$fullName, FileSize=$fileSize")

                    val downloadInfo = DownloadInfo(
                        nexusFileId = fileId,
                        fileName = fullName,
                        fileSize = (fileSize / 1024).toString(), // Store as KB string
                        sourceLists = listOf("poti")
                    )

                    val modDesc = ModDesc(
                        modId = "poti_$modId",
                        slug = "poti_$modId",
                        name = name,
                        author = author,
                        description = description,
                        url = "https://www.nexusmods.com/morrowind/mods/$modId",
                        dlUrl = "https://www.nexusmods.com/morrowind/mods/$modId?tab=files&file_id=$fileId",
                        downloadInfo = listOf(downloadInfo),
                        gitlabProjectId = null,
                        gitlabPackageId = null,
                        onLists = listOf("poti") // Add "poti" to onLists
                    )

                    try {
                        modDao.insertOrAppendMods(listOf(modDesc), "poti", context)
                        importedCount++
                        if (importedCount % 50 == 0) {
                            Log.d("POTI_IMPORT", "Imported $importedCount mods...")
                        }
                    } catch (e: Exception) {
                        Log.w("POTI_IMPORT", "Failed to insert mod $modId: ${e.message}")
                        processing = false
                    }
                }
            }
        }
    } catch (e: Exception) {
        Log.e("POTI_IMPORT", "Error parsing JSON: ${e.message}")
        processing = false
    }

    Log.i("POTI_IMPORT", "Import finished – $importedCount Morrowind mods added.")
    processing = false
    importedCount
}