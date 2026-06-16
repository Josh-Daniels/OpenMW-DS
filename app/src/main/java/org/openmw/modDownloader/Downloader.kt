package org.openmw.modDownloader

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import okio.Buffer
import okio.ForwardingSource
import okio.buffer
import okio.sink
import org.openmw.Constants
import org.openmw.modDownloader.ModListManager.allowExtract
import org.openmw.modDownloader.ModListManager.applicationContext
import org.openmw.modDownloader.ModListManager.modList
import org.openmw.modDownloader.NexusInfo.extractionProgressMap
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date

enum class ModStatus {
    DOWNLOADING,
    EXTRACTING,
    COMPLETED,
    IDLE
}

fun getModStatus(modId: String): ModStatus {
    val progress = NexusInfo.downloadProgressMap[modId] ?: ""
    val extractionStatus = extractionProgressMap[modId] ?: "Waiting..."

    return when {
        !progress.contains("Downloaded") -> ModStatus.DOWNLOADING
        !extractionStatus.contains("Extracted") && !extractionStatus.contains("Waiting") -> ModStatus.EXTRACTING
        progress.contains("Downloaded") && extractionStatus.contains("Extracted") -> ModStatus.COMPLETED
        else -> ModStatus.IDLE
    }
}

fun appendToDownloadLog(message: String) {
    try {
        val logFile = File("${Constants.USER_FILE_STORAGE}/OpenMW/Download_$modList.log")
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
        val logEntry = "[$timestamp] $message\n"

        logFile.parentFile?.mkdirs()
        logFile.appendText(logEntry)
    } catch (e: Exception) {
        Log.e("DownloadLog", "Failed to write to log file: ${e.message}")
    }
}

suspend fun downloadModFile(mod: ModDesc, info: DownloadInfo) {
    withContext(Dispatchers.IO) {
        try {
            val outputFile = File(
                "${Constants.USER_FILE_STORAGE}/OpenMW/CACHE",
                "${info.fileName}"
            )

            val downloadUrl = info.directDownload
            if (downloadUrl.isNullOrEmpty()) {
                throw IOException("Download URL is null or empty for ${mod.name}")
            }

            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "Alpha3/2.71")
                .build()

            ModListManager.client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP error: ${response.code} - ${response.message}")
                }

                val body = response.body
                val fileLength = body.contentLength()

                var totalBytesRead: Long = 0
                val startTime = System.currentTimeMillis()
                var lastUpdateTime = startTime
                val mainScope = CoroutineScope(Dispatchers.Main)

                // Wrap the source to intercept reads
                val source = object : ForwardingSource(body.source()) {
                    override fun read(sink: Buffer, byteCount: Long): Long {
                        val bytesRead = super.read(sink, byteCount)
                        if (bytesRead > 0) {
                            totalBytesRead += bytesRead
                            val currentTime = System.currentTimeMillis()

                            if (fileLength > 0 &&
                                (currentTime - lastUpdateTime > 200 || totalBytesRead == fileLength)
                            ) {
                                val progress = ((totalBytesRead * 100) / fileLength).toInt()
                                val elapsedTime = (currentTime - startTime) / 1000.0
                                val speedKBs = if (elapsedTime > 0)
                                    (totalBytesRead / 1024.0 / elapsedTime).toInt()
                                else 0

                                // Switch to Main only when needed
                                mainScope.launch {
                                    NexusInfo.downloadProgressMap[mod.slug] = "Downloading ${mod.name}"
                                    NexusInfo.downloadProgressPercentMap[mod.slug] = progress
                                    NexusInfo.downloadSpeedMap[mod.slug] = speedKBs
                                }

                                lastUpdateTime = currentTime
                            }
                        }
                        return bytesRead
                    }
                }

                // Stream file with Okio
                source.use { src ->
                    outputFile.sink().buffer().use { sink ->
                        sink.writeAll(src)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("ProcessModList", "Failed to download file for ${mod.slug}: ${e.message}", e)
        } finally {
            withContext(Dispatchers.Main) {
                NexusInfo.downloadProgressMap[mod.slug] = "${mod.name} Downloaded"
                NexusInfo.downloadProgressPercentMap[mod.slug] = 100
                NexusInfo.downloadSpeedMap.remove(mod.slug)
            }

            calculateAndUpdateSizeOnDisk(mod, info)

            if (allowExtract) {
                extractModFile(mod, info)
            }
        }
    }
}


suspend fun calculateAndUpdateSizeOnDisk(mod: ModDesc, info: DownloadInfo) {
    withContext(Dispatchers.IO) {
        try {
            val destination = File(
                "${Constants.USER_FILE_STORAGE}/OpenMW/CACHE",
                "${info.fileName}"
            )

            if (destination.exists()) {
                val totalExtractedSize = getTotalExtractedSize(destination)

                // Store as raw bytes instead of formatted string
                val updatedInfo = info.copy(extractedSizeBytes = totalExtractedSize)

                // Update the database
                updateDownloadInfoInDatabase(mod.modId, updatedInfo)

                Log.d("SizeCalculation", "Updated sizeOnDisk for ${mod.slug}: ${formatSize(totalExtractedSize)}")
            }
        } catch (e: Exception) {
            Log.e("SizeCalculation", "Failed to calculate size for ${mod.slug}: ${e.message}")
        }
    }
}

suspend fun updateDownloadInfoInDatabase(modId: String, updatedInfo: DownloadInfo) {
    withContext(Dispatchers.IO) {
        try {
            val modDao = ModDatabase.getDatabase(applicationContext).modDao()

            // Get the current mod from database
            val currentMod = modDao.getModById(modId)
            if (currentMod == null) {
                Log.e("DatabaseUpdate", "Mod with ID $modId not found in database")
                return@withContext
            }

            // Update the download info
            val updatedDownloadInfo = currentMod.downloadInfo.map { info ->
                if (info.fileName == updatedInfo.fileName) updatedInfo else info
            }

            // Create updated mod object
            val updatedMod = currentMod.copy(downloadInfo = updatedDownloadInfo)

            // Update in database
            modDao.updateMod(updatedMod)

            Log.d("DatabaseUpdate", "Successfully updated sizeOnDisk for mod $modId")

        } catch (e: Exception) {
            Log.e("DatabaseUpdate", "Failed to update sizeOnDisk in database for mod $modId: ${e.message}", e)
        }
    }
}
