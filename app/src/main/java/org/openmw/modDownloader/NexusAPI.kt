package org.openmw.modDownloader

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.net.toUri
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import org.jsoup.Jsoup
import org.openmw.modDownloader.ModListManager.fetchJsonResponse
import org.openmw.modDownloader.ModListManager.json
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.UUID
import java.util.regex.Pattern
import kotlin.collections.isNotEmpty

data class WebTab(
    val id: String,
    val url: String,
    val fileName: String
)

object NexusInfo {
    var isNexusExpanded by mutableStateOf(false)
    val hourlylimit by mutableStateOf(500)
    var hourlyremaining by mutableStateOf(0)
    val dailylimit by mutableStateOf(20000)
    var dailyremaining by mutableStateOf(0)
    var hourlyResetTime by mutableStateOf<ZonedDateTime?>(null)
    var dailyResetTime by mutableStateOf<ZonedDateTime?>(null)
    var hourlyResetInMinutes by mutableStateOf(0L)
    var dailyResetInMinutes by mutableStateOf(0L)
    val downloadProgressMap = mutableStateMapOf<String, String>()
    val downloadProgressPercentMap = mutableStateMapOf<String, Int>()
    val downloadSpeedMap = mutableStateMapOf<String, Int>()
    val extractionProgressMap = mutableStateMapOf<String, String>()
    val extractionProgressPercentMap = mutableStateMapOf<String, Float>()

    // SharedFlow to emit new URLs for WebView tabs
    private val _webViewTabsFlow = MutableSharedFlow<List<WebTab>>(replay = 1, extraBufferCapacity = 1)
    val webViewTabsFlow: SharedFlow<List<WebTab>> = _webViewTabsFlow.asSharedFlow()

    init {
        _webViewTabsFlow.tryEmit(emptyList())
        Log.d("NexusInfo", "Initialized webViewTabsFlow with empty list")
    }

    // Function to add a new URL to open in WebView
    suspend fun openWebView(url: String, fileName: String, id: String? = null) {
        if (url.isNotBlank()) {
            val currentTabs = _webViewTabsFlow.replayCache.firstOrNull() ?: emptyList()
            if (currentTabs.none { it.url == url }) {
                val newTab = WebTab(
                    id = id ?: UUID.randomUUID().toString(),
                    fileName = fileName,
                    url = url
                )
                val newTabs = currentTabs + newTab
                _webViewTabsFlow.emit(newTabs)
                Log.d("NexusInfo", "Opened new tab: $newTab, Current tabs: $newTabs")
            } else {
                Log.d("NexusInfo", "Skipped duplicate URL: $url")
            }
        } else {
            Log.w("NexusInfo", "Attempted to add blank URL")
        }
    }

    suspend fun closeWebViewTab(tabId: String) {
        val currentTabs = _webViewTabsFlow.replayCache.firstOrNull() ?: emptyList()
        val newTabs = currentTabs.filter { it.id != tabId }
        _webViewTabsFlow.emit(newTabs)
        Log.d("NexusInfo", "Closed tab with ID: $tabId, Remaining tabs: $newTabs, Emitted to flow")
        Log.d("NexusInfo", "Flow state after emission: $newTabs")
    }

    // New function to update tab with filename
    suspend fun updateTabFileName(tabId: String, fileName: String) {
        val currentTabs = _webViewTabsFlow.replayCache.firstOrNull() ?: emptyList()
        val newTabs = currentTabs.map { tab ->
            if (tab.id == tabId) tab.copy(fileName = fileName) else tab
        }
        _webViewTabsFlow.emit(newTabs)
        Log.d("NexusInfo", "Updated tab $tabId with fileName: $fileName, New tabs: $newTabs")
    }

    // Helper properties
    val hourlyPercentage: Float
        get() = if (hourlylimit > 0) hourlyremaining.toFloat() / hourlylimit else 1f

    val dailyPercentage: Float
        get() = if (dailylimit > 0) dailyremaining.toFloat() / dailylimit else 1f

    val isNearLimit: Boolean
        get() = (hourlyPercentage in 0.0001f..0.2f && dailyPercentage != 0f) ||
                (dailyPercentage in 0.0001f..0.2f && hourlyPercentage != 0f)


    val hourlyResetFormatted: String
        get() = formatTimeUntilReset(hourlyResetInMinutes)

    val dailyResetFormatted: String
        get() = formatTimeUntilReset(dailyResetInMinutes)
}

// Parse reset time from header (format: "2025-07-24 04:00:00 +0000")
fun parseResetTime(resetHeader: String): ZonedDateTime? {
    return try {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z")
        ZonedDateTime.parse(resetHeader, formatter)
    } catch (e: Exception) {
        Log.e("NexusInfo", "Failed to parse reset time: $resetHeader", e)
        null
    }
}

// Calculate minutes until reset
fun calculateMinutesUntilReset(resetTime: ZonedDateTime): Long {
    val now = ZonedDateTime.now()
    return ChronoUnit.MINUTES.between(now, resetTime).coerceAtLeast(0)
}

// Format minutes into "Xh Ym" string
fun formatTimeUntilReset(minutes: Long): String {
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours > 0 -> "${hours}h ${remainingMinutes}m"
        else -> "${remainingMinutes}m"
    }
}

fun extractExpiresValue(url: String): Long? {
    val uri = url.toUri()
    val expiresValue = uri.getQueryParameter("expires")
    return expiresValue?.toLongOrNull()
}

suspend fun fetchResponseBody(url: String): String? {
    return withContext(Dispatchers.IO) {
        // Replace all individual client creations with this:
        val client = ModListManager.client
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            return@withContext if (!response.isSuccessful) null else response.body?.string()
        }
    }
}

suspend fun getUpdatedModGallery(mod: ModDesc): ModDesc {
    val modId = mod.modId
    val galleryUrl = "https://www.nexusmods.com/morrowind/mods/$modId?tab=images"
    val pageHtml = fetchResponseBody(galleryUrl) ?: return mod

    if (mod.galleryUrls.isNotEmpty()) {
        return mod // Already has cached gallery URLs, no need to re-fetch
    }

    return try {
        val doc = Jsoup.parse(pageHtml)
        val galleryImages = doc.select("img[src]")
            .mapNotNull { it.attr("src") }
            .filter {
                it.contains("staticdelivery.nexusmods.com") &&
                        !it.contains("staticdelivery.nexusmods.com/images/News/")
            }

        Log.i("getUpdatedModGallery", galleryImages.toString())

        mod.copy(galleryUrls = galleryImages)
    } catch (e: Exception) {
        Log.e("ModProcessing", "Failed to parse gallery images for mod $modId", e)
        mod
    }
}

suspend fun getFileIds(context: Context, mod: ModDesc): List<DownloadInfo> = withContext(Dispatchers.IO) {
    // Initial setup and Nexus Mods check
    if (!mod.url.contains("nexusmods.com/morrowind/mods/")) {
        return@withContext mod.downloadInfo // Return unchanged list
    }

    val modId = mod.url.substringAfter("mods/").substringBefore("?")
    Log.i("GetFileID", "Started for ${mod.modId}")
    val jsonResponse = try {
        fetchJsonResponse("https://api.nexusmods.com/v1/games/morrowind/mods/${mod.modId}/files.json")
    } catch (e: IOException) {
        Log.e("GetFileID", "Failed to fetch JSON for mod $modId", e)
        return@withContext mod.downloadInfo
    }

    val jsonElement = try {
        json.parseToJsonElement(jsonResponse)
    } catch (e: IllegalArgumentException) {
        Log.e("GetFileID", "Failed to parse JSON for mod $modId", e)
        return@withContext mod.downloadInfo
    }

    val filesArray = jsonElement.jsonObject["files"]?.jsonArray
        ?: jsonElement.jsonArray.takeIf { it.isNotEmpty() }
        ?: run {
            Log.w("GetFileID", "No files array in JSON for mod $modId")
            return@withContext mod.downloadInfo
        }

    // Collect all file versions
    val allFileVersions = filesArray.mapNotNull { fileElement ->
        val fileObject = fileElement.jsonObject
        val nexusFileName = fileObject["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val fileName = fileObject["file_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val fileId = fileObject["file_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
        val version = fileObject["version"]?.jsonPrimitive?.contentOrNull
        val sizeInKb = fileObject["size_kb"]?.jsonPrimitive?.contentOrNull
        val category = fileObject["category_name"]?.jsonPrimitive?.contentOrNull ?: "N/A"

        Log.d("GetFileID", """
            File Info for mod $modId:
            - Name: $nexusFileName
            - fileName: $fileName
            - ID: $fileId
            - Version: $version
            - Size: $sizeInKb KB
            - Category: $category
        """.trimIndent())

        DownloadInfo.Version(
            fileName = nexusFileName,
            fileId = fileId,
            version = version,
            sizeKb = sizeInKb,
            category = category
        )
    }

    // Process each DownloadInfo in mod.downloadInfo
    mod.downloadInfo.map { downloadInfo ->
        // Skip if nexusFileId already exists
        if (!downloadInfo.nexusFileId.isNullOrEmpty()) {
            return@map downloadInfo
        }

        val targetFileName = downloadInfo.fileName ?: return@map downloadInfo
        downloadInfo.copy(versions = allFileVersions)

        var selectedFileId: String? = null
        var selectedFileName: String? = null
        var latestTimestamp: Long? = null
        var selectedSizeInKb: String? = null
        val matchingVersions = allFileVersions.filter {
            it.fileName.equals(targetFileName, ignoreCase = true)
        }

        loop@ for (fileElement in filesArray) {
            val fileObject = fileElement.jsonObject
            val nexusFileName = fileObject["name"]?.jsonPrimitive?.contentOrNull ?: continue
            val fileName = fileObject["file_name"]?.jsonPrimitive?.contentOrNull ?: continue
            val fileId = fileObject["file_id"]?.jsonPrimitive?.contentOrNull
            val timestamp = fileObject["uploaded_timestamp"]?.jsonPrimitive?.contentOrNull?.toLongOrNull()
            val version = fileObject["version"]?.jsonPrimitive?.contentOrNull
            val sizeInKb = fileObject["size_kb"]?.jsonPrimitive?.contentOrNull
            val category = fileObject["category_name"]?.jsonPrimitive?.contentOrNull ?: "N/A"

            Log.d("GetFileID", """
                File Info for mod $modId:
                - Name: $nexusFileName
                - fileName: $fileName
                - ID: $fileId
                - Version: $version
                - Timestamp: $timestamp
                - Size: $sizeInKb KB
                - Category: $category
            """.trimIndent())

            if (nexusFileName.equals(targetFileName, ignoreCase = true)) {
                // Compare timestamps to find the latest
                if (timestamp != null && (latestTimestamp == null || timestamp > latestTimestamp)) {
                    selectedFileId = fileId
                    selectedFileName = fileName
                    latestTimestamp = timestamp
                    selectedSizeInKb = sizeInKb
                    Log.d("GetFileID", "New latest timestamp for targetFileName '$targetFileName': $nexusFileName with timestamp $timestamp")
                }
            }
        }

        downloadInfo.copy(
            nexusFileId = selectedFileId,
            fileName = selectedFileName,
            fileSize = selectedSizeInKb,
            versions = matchingVersions
        )
    }
}

// Function to get archived Nexus file IDs
suspend fun getArchivedNexusFileIds(modId: String): List<String>? {
    val searchUrl = "https://www.nexusmods.com/morrowind/mods/$modId?tab=files"
    val versionsResponse = fetchResponseBody(
        "http://web.archive.org/cdx/search/cdx?url=$searchUrl"
    ) ?: return null
    val pattern = Pattern.compile("[0-9]{14}")
    val matcher = pattern.matcher(versionsResponse)
    if (matcher.find()) {
        val cacheDate = matcher.group(0)
        val archivedPage = fetchResponseBody(
            "https://web.archive.org/web/$cacheDate/$searchUrl"
        ) ?: return null
        val doc = Jsoup.parse(archivedPage)
        return doc.select("a[href*='file_id']").map { it.attr("href").split("file_id=")[1] }
    }
    return null
}

fun formatRemainingTime(expiresTimestamp: Long, currentTimestamp: Long): String {
    val secondsRemaining = expiresTimestamp - currentTimestamp
    if (secondsRemaining <= 0) return "Expired"

    val days = secondsRemaining / (24 * 60 * 60)
    val hours = (secondsRemaining % (24 * 60 * 60)) / (60 * 60)
    val minutes = (secondsRemaining % (60 * 60)) / 60

    return buildString {
        if (days > 0) append("$days day${if (days > 1) "s" else ""}")
        if (hours > 0) {
            if (days > 0) append(", ")
            append("$hours hour${if (hours > 1) "s" else ""}")
        }
        if (minutes > 0 && days == 0L) { // Only show minutes if no days
            if (hours > 0) append(", ")
            append("$minutes minute${if (minutes > 1) "s" else ""}")
        }
    }.takeIf { it.isNotEmpty() } ?: "Less than a minute"
}

@OptIn(DelicateCoroutinesApi::class)
suspend fun getDirectDownload(context: Context, mod: ModDesc, downloadInfo: DownloadInfo): DownloadInfo = withContext(Dispatchers.IO) {
    var directDownload: String? = null
    var successfulFileId: String? = null

    println("trying to fetch download link for ${mod.name} with ID ${mod.modId} - FileID: ${downloadInfo.nexusFileId}")

    // Prioritize file IDs: MAIN > current file ID > others
    val prioritizedFileIds = mutableListOf<String>().apply {
        // First, try MAIN category files
        downloadInfo.versions
            .filter { it.category == "MAIN" && it.fileId.isNotEmpty() }
            .forEach { add(it.fileId) }

        // Then try the current file ID (if not already included)
        if (!downloadInfo.nexusFileId.isNullOrEmpty() && !contains(downloadInfo.nexusFileId)) {
            add(downloadInfo.nexusFileId)
        }

        // Then try all other files
        downloadInfo.versions
            .filter { it.category != "MAIN" && it.fileId.isNotEmpty() && !contains(it.fileId) }
            .forEach { add(it.fileId) }
    }.distinct().filter { it.isNotEmpty() }

    for (fileId in prioritizedFileIds) {
        try {
            println("Trying file ID: $fileId for mod ${mod.modId}")
            val downloadJsonResponse = fetchJsonResponse("https://api.nexusmods.com/v1/games/morrowind/mods/${mod.modId}/files/$fileId/download_link.json")
            println(downloadJsonResponse)
            // Check for 404 or other errors in the response
            if (downloadJsonResponse.contains("\"code\":404") ||
                downloadJsonResponse.contains("No File found") ||
                downloadJsonResponse.contains("\"error\"")) {
                println("File ID $fileId returned error, trying next one")
                continue
            }

            val jsonElementFile = json.parseToJsonElement(downloadJsonResponse)
            if (jsonElementFile is JsonArray && jsonElementFile.isNotEmpty()) {
                val uri = jsonElementFile[0].jsonObject["URI"]?.jsonPrimitive?.contentOrNull
                if (uri != null) {
                    directDownload = uri.replace(" ", "%20")
                    successfulFileId = fileId
                    println("Successfully fetched download link for file ID $fileId: $directDownload")
                    break
                }
            }

        } catch (e: Exception) {
            println("Failed to fetch download link for file ID $fileId: ${e.message}")
            // Continue to next file ID
        }
    }

    val updatedInfo = if (directDownload != null) {
        downloadInfo.copy(
            directDownload = directDownload,
            nexusFileId = successfulFileId ?: downloadInfo.nexusFileId
        )
    } else {
        downloadInfo.copy(directDownload = null)
    }

    // Persist if changed
    if (directDownload != null && directDownload != downloadInfo.directDownload) {
        val modDao = ModDatabase.getDatabase(context).modDao()
        val updatedDownloadList = mod.downloadInfo.map {
            if (it == downloadInfo) updatedInfo else it
        }
        val updatedMod = mod.copy(downloadInfo = updatedDownloadList)
        modDao.insertOrUpdateMod(updatedMod)
        println("Updated mod ${mod.modId} with new download link from file ID $successfulFileId")
    }

    return@withContext updatedInfo
}
