@file:OptIn(ExperimentalCoroutinesApi::class)
@file:Suppress("DEPRECATION")

package org.openmw.modDownloader

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.openmw.Constants
import org.openmw.modDownloader.ModListManager.allowExtract
import org.openmw.modDownloader.ModListManager.brokenMods
import org.openmw.modDownloader.ModListManager.modList
import org.openmw.modDownloader.ModListManager.onlySync
import org.openmw.modDownloader.ModListManager.remainingMods
import org.openmw.modDownloader.ModListManager.runSemaphore
import org.openmw.modDownloader.ModListManager.validate
import org.openmw.modDownloader.StatusInfo.processing
import org.openmw.ui.view.addCustomLog
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.dataStore
import java.io.File
import java.io.IOException
import java.net.URL
import java.time.Instant
import java.util.concurrent.TimeUnit
import kotlin.math.absoluteValue

@OptIn(DelicateCoroutinesApi::class)
fun processModList(context: Context, isPremium: Boolean): Flow<List<ModDesc>> = flow {
    // isValidated is required, but also isvalidated = isPremium in this case.
    val modDao = ModDatabase.getDatabase(context).modDao()
    var brokenModCount = 0
    remainingMods = 0

    // Clear previous progress data
    NexusInfo.downloadProgressMap.clear()
    NexusInfo.downloadProgressPercentMap.clear()

    File("${Constants.USER_FILE_STORAGE}/OpenMW/Download_$modList.log").delete()
    File("${Constants.USER_FILE_STORAGE}/OpenMW/Extraction_$modList.log").delete()

    val allMods = modDao.getAllMods()

    // Filter the DB mods by the selected mod list
    val mods = allMods.filter { mod ->
        mod.onLists.contains(modList)
    }

    Log.d("ProcessModList", "Found ${mods.size} mods for list: $modList")
    addCustomLog("Found ${mods.size} mods for list: $modList", textSize = 10, textColor = Color.Cyan)

    remainingMods = mods.size
    StatusInfo.activeMods = mods.size

    coroutineScope {
        validate(context)
        mods.mapIndexed { index, mod ->
            async {
                Log.i(
                    "ProcessModList",
                    "Processing mod ${index + 1}/${mods.size} (${mod.slug}) with ${mod.downloadInfo.size} download links and Premium statue of $isPremium"
                )
                addCustomLog("Processing mod ${index + 1}/${mods.size} (${mod.slug}) [Premium: $isPremium]", textSize = 10, textColor = Color.Cyan)
                when {
                    mod.url.contains("nexusmods.com") -> {
                        val currentUnixTimestamp = Instant.now().epochSecond

                        // Step 1: Get updated DownloadInfo list with nexusFileId values
                        val hasMissingFileIds = mod.downloadInfo.any { it.nexusFileId == null }

                        val updatedInfoList = if (hasMissingFileIds) {
                            try {
                                Log.i("ProcessModList", "Fetching file IDs for ${mod.slug}")
                                addCustomLog("Nexus: Fetching file IDs for ${mod.slug}", textSize = 10, textColor = Color.Cyan)
                                getFileIds(mod)
                            } catch (e: Exception) {
                                Log.i("ProcessModList", "Failed to fetch file IDs for ${mod.slug}: ${e.message}")
                                addCustomLog("Failed to fetch file IDs for ${mod.slug}: ${e.message}", textSize = 10, textColor = Color.Red)
                                mod.downloadInfo
                            }
                        } else {
                            Log.i("ProcessModList", "File IDs for ${mod.slug} already set!")
                            addCustomLog("File IDs for ${mod.slug} already set!", textSize = 10, textColor = Color.Green)
                            mod.downloadInfo
                        }

                        // Step 2: Process each DownloadInfo for direct download links or non-premium actions
                        val finalInfoList = updatedInfoList.map { info ->
                            var updatedInfo = info

                            // Refresh direct download link if expired
                            val expiresTimestamp = updatedInfo.directDownload?.let { extractExpiresValue(it) } ?: Long.MAX_VALUE
                            val isExpired = updatedInfo.directDownload.isNullOrEmpty() || expiresTimestamp < currentUnixTimestamp

                            if (isPremium) {
                                if (isExpired && updatedInfo.nexusFileId != null) {
                                    try {
                                        updatedInfo = getDirectDownload(context, mod, updatedInfo)
                                    } catch (e: Exception) {
                                        Log.i("ProcessModList", "Failed to fetch direct download for ${mod.slug}: ${e.message}")
                                        addCustomLog("Failed to fetch direct download for ${mod.slug}: ${e.message}", textSize = 10, textColor = Color.Red)
                                    }
                                }
                            }
                            updatedInfo
                        }

                        // Update the mod with the new DownloadInfo list
                        val updatedMod = mod.copy(downloadInfo = finalInfoList)
                        modDao.updateMod(updatedMod)
                    }

                    mod.url.startsWith("/") -> {
                        Log.d(
                            "ProcessModList",
                            "Processing local file for ${mod.slug}: ${mod.url}"
                        )
                        addCustomLog("Processing local file: ${mod.slug}", textSize = 10, textColor = Color.Cyan)
                        try {
                            val directDownloadUrl = "https://modding-openmw.com${mod.url}"
                            val fileName =
                                mod.url.substringAfterLast('/').takeIf { it.isNotBlank() }
                                    ?: "${mod.slug}.zip"

                            val updatedDownloadInfo = mod.downloadInfo.map { info ->
                                info.copy(
                                    directDownload = directDownloadUrl,
                                    fileName = fileName
                                )
                            }

                            val updatedMod = mod.copy(
                                downloadInfo = updatedDownloadInfo,
                                dlUrl = directDownloadUrl
                            )

                            modDao.insertOrUpdateMod(updatedMod)
                        } catch (e: Exception) {
                            Log.i(
                                "ProcessModList",
                                "Failed to fetch a direct download link: ${e.message}"
                            )
                            addCustomLog("Error: Failed to fetch local download link: ${e.message}", textSize = 10, textColor = Color.Red)
                        }
                    }
                    mod.url.contains("web.archive.org") -> {
                        val lastDigits = extractLastDigits(mod.url)

                        val archivedUrl = "https://web.archive.org/cdx/search/cdx?url=mw.modhistory.com/file.php?id=$lastDigits&output=json&sort=descending&limit=1"
                        val updatedUrl = getLatestWaybackUrl(archivedUrl)
                        val cleanedDlUrl = mod.dlUrl?.replace("/files/", "")
                        if (updatedUrl != null) {
                            println("Download link: $updatedUrl")
                            addCustomLog("Archive.org Link: $updatedUrl", textSize = 10, textColor = Color.Cyan)
                            val updatedDownloadInfo = mod.downloadInfo.map { info ->
                                info.copy(
                                    directDownload = updatedUrl,
                                    fileName = cleanedDlUrl
                                )
                            }

                            val updatedMod = mod.copy(
                                downloadInfo = updatedDownloadInfo
                            )

                            modDao.insertOrUpdateMod(updatedMod)
                        } else {
                            println("Mod not found or error occurred")
                            addCustomLog("Archive.org error: Mod not found for ${mod.slug}", textSize = 10, textColor = Color.Red)
                        }
                    }

                    mod.url.contains("gitlab") -> {
                        mod.downloadInfo.forEach { info ->
                            if (info.directDownload.isNullOrEmpty()) {
                                fetchGitProjectId(mod, modDao)
                                addCustomLog("Fetching GitLab ID for ${mod.slug}", textSize = 10, textColor = Color.Cyan)
                            } else {
                                Log.d(
                                    "fetchGitLabProjectId",
                                    "Download Link and Filename already exists for ${mod.slug}, skipping"
                                )
                                addCustomLog("GitLab: Skipping ${mod.slug} (Exists)", textSize = 10, textColor = Color.Green)
                            }
                        }
                    }
                    mod.url.startsWith("https://baturin.org") -> {
                        Log.d(
                            "ProcessModList",
                            "Processing local file for ${mod.slug}: ${mod.url}"
                        )
                        addCustomLog("Processing local file for ${mod.slug}: ${mod.url}", textSize = 10, textColor = Color.White)
                        try {
                            // Extract the filename from the URL fragment (part after #)
                            val fragment = mod.url.substringAfterLast("#")
                            val fileName = if (fragment.isNotBlank() && fragment != mod.url) {
                                "${fragment.replace("-", "_")}.zip"
                            } else {
                                // Fallback to slug if no fragment found
                                "${mod.slug.replace("-", "_")}.zip"
                            }

                            val directDownloadUrl = "https://baturin.org/misc/morrowind-mods/$fileName"

                            val updatedDownloadInfo = mod.downloadInfo.map { info ->
                                info.copy(
                                    directDownload = directDownloadUrl,
                                    fileName = fileName
                                )
                            }

                            val updatedMod = mod.copy(
                                downloadInfo = updatedDownloadInfo,
                                dlUrl = directDownloadUrl
                            )

                            modDao.insertOrUpdateMod(updatedMod)
                        } catch (e: Exception) {
                            Log.i(
                                "ProcessModList",
                                "Failed to fetch a direct download link: ${e.message}"
                            )
                            addCustomLog("Error: Failed to fetch local download link: ${e.message}", textSize = 10, textColor = Color.Red)
                        }
                    }
                    else  -> {
                        brokenModCount++
                    }
                }
                brokenMods = "Total Broken mods: $brokenModCount"

                if (onlySync) {
                    remainingMods--
                }

                for (info in mod.downloadInfo.filter { it.sourceLists.contains(modList) }) {
                    runSemaphore.withPermit {
                        appendToDownloadLog("Downloading: ${info.fileName}")
                        if (!onlySync) {
                            try {
                                val destination = File(
                                    "${Constants.USER_FILE_STORAGE}/OpenMW/CACHE",
                                    "${info.fileName}"
                                )
                                if (destination.exists()) {
                                    Log.d(
                                        "DownloadSkip",
                                        "File already exists for ${mod.slug}, skipping download"
                                    )
                                    addCustomLog("Cache Hit: ${info.fileName}", textSize = 10, textColor = Color.Green)

                                    NexusInfo.downloadProgressMap[mod.slug] =
                                        "${mod.name} Downloaded"
                                    NexusInfo.downloadProgressPercentMap[mod.slug] = 100
                                    NexusInfo.downloadSpeedMap.remove(mod.slug)

                                    if (allowExtract) {
                                        extractModFile(mod, info)
                                        remainingMods--
                                    }
                                } else {
                                    when {
                                        mod.url.contains("nexusmods.com") -> {
                                            if (!isPremium) {
                                                NexusInfo.openWebView(
                                                    url = "https://www.nexusmods.com/morrowind/mods/${mod.modId}?tab=files&file_id=${info.nexusFileId}",
                                                    fileName = "${info.fileName}",
                                                    id = mod.modId
                                                )
                                            } else {
                                                if (!info.directDownload.isNullOrEmpty()) {
                                                    downloadModFile(mod, info)
                                                }
                                            }
                                        }
                                        /*
                                        mod.url.contains("mega.nz") -> {
                                            val mega = MegaDownloader()
                                            val cacheDir = File("${Constants.USER_FILE_STORAGE}/OpenMW/CACHE")
                                            val success = mega.download(mod.url, cacheDir)
                                            if (success && allowExtract) {
                                                // After MEGA download, we need to find the real file it created
                                                // since we don't know the name in advance
                                                extractModFile(mod, info)
                                                remainingMods--
                                            }
                                        }
                                         */
                                        else -> {
                                            if (!info.directDownload.isNullOrEmpty()) {
                                                downloadModFile(mod, info)
                                            }
                                        }
                                    }
                                }
                            } catch (e: Exception) {
                                Log.e(
                                    "ProcessModList",
                                    "Failed to download file for ${mod.slug}: ${e.message}",
                                    e
                                )
                                addCustomLog("Download Error (${mod.slug}): ${e.message}", textSize = 10, textColor = Color.Red)
                                NexusInfo.downloadProgressMap[mod.slug] =
                                    "Failed to download ${mod.name}"
                                NexusInfo.downloadProgressPercentMap[mod.slug] = 0
                            } finally {
                                NexusInfo.downloadSpeedMap.remove(mod.slug)
                            }
                        }
                    }
                }
            }
        }.awaitAll()
    }
    Log.d("ProcessModList", "Total updated mods: ${mods.size}")
    addCustomLog("Processing complete. Total updated: ${mods.size}", textSize = 10, textColor = Color.Green)
    processing = false
    StatusInfo.showProcessCompleteDialog = true
    emit(mods)
}

object ModListManager {
    // Mod Manager
    var modList by mutableStateOf("i-heart-vanilla")
    var apiKey by mutableStateOf("")
    var allowExtract by mutableStateOf(true)
    var onlySync by mutableStateOf(false)
    var brokenMods by mutableStateOf("")

    val cpuThreadCount = Runtime.getRuntime().availableProcessors()
    var selectedThreadCount by mutableIntStateOf(cpuThreadCount)
    val runSemaphore = Semaphore(selectedThreadCount)
    private var _applicationContext: Context? = null
    val applicationContext: Context
        get() = _applicationContext ?: throw IllegalStateException("ApplicationContext not initialized")

    // Initialize the context when your app starts
    fun init(context: Context) {
        _applicationContext = context.applicationContext
    }

    var remainingMods by mutableIntStateOf(0)

    val availableLists = runBlocking { fetchAvailableLists() }
    suspend fun fetchAvailableLists(): MutableList<String> = withContext(Dispatchers.IO) {
        try {
            val jsonString = URL("https://modding-openmw.com/lists/json").readText()
            Json.decodeFromString<List<String>>(jsonString)
                .filter { !it.endsWith("-wip") }
                .filter { !it.contains("one-day-morrowind-modernization") }
                .toMutableList()
        } catch (e: Exception) {
            println("Failed to fetch/parse mod lists: ${e.message}")
            addCustomLog("Failed to fetch/parse mod lists: ${e.message}", textSize = 10, textColor = Color.Cyan)
            mutableListOf(
                "i-heart-vanilla",
                "total-overhaul"
            )
        }
    }

    val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    // Replace all individual client creations with this:
    val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .addInterceptor(RetryInterceptor(3))
        .build()

    class RetryInterceptor(private val maxRetries: Int) : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val request = chain.request()
            var response: Response?
            var retryCount = 0
            var lastException: IOException? = null

            while (retryCount < maxRetries) {
                try {
                    response = chain.proceed(request)
                    try {
                        if (response.isSuccessful) {
                            return response
                        } else {
                            response.close()
                        }
                    } catch (e: Exception) {
                        response.run { close() }
                        throw e
                    }
                } catch (e: IOException) {
                    lastException = e
                    retryCount++
                    if (retryCount >= maxRetries) {
                        break
                    }
                    // Wait before retrying (exponential backoff)
                    Thread.sleep((1000 * retryCount).toLong())
                }
            }

            throw lastException ?: IOException("Unknown error occurred")
        }
    }

    suspend fun validate(context: Context) {
        try {
            val jsonString =
                fetchJsonResponse("https://api.nexusmods.com/v1/users/validate.json")

            val json = Json.parseToJsonElement(jsonString).jsonObject
            val premiumStatus = json["is_premium"]?.jsonPrimitive?.booleanOrNull ?: false

            // Save to DataStore
            context.dataStore.edit { preferences ->
                preferences[GameFilesPreferences.IS_NEXUS_PREMIUM] = premiumStatus
            }
            Log.i("Validate", "Raw JSON: $jsonString")
            addCustomLog("Validate: Raw JSON: $jsonString", textSize = 10, textColor = Color.Yellow)
        } catch (e: Exception) {
            Log.e("VALIDATE_ERROR", "Validation failed: ${e.message}")
            addCustomLog("Validate: Validation failed: ${e.message}", textSize = 10, textColor = Color.Yellow)
        }
    }

    suspend fun downloadAndParseModList(context: Context): List<ModDesc> = withContext(Dispatchers.Default) {
        processing = true
        val allFilteredMods = mutableListOf<ModDesc>()
        val blockedModNames = listOf(
            "Buy and Install Morrowind",
            "Install OpenMW",
            "Set your Site Settings",
            "Folder Deploy Script",
            "How to Install Mods",
            "S3LightFixes",
            "MOMW Tools Pack"
        )

        val modDao = ModDatabase.getDatabase(applicationContext).modDao()

        // Process online mod lists
        for (currentListName in availableLists) {
            try {
                val url = URL("https://modding-openmw.com/lists/$currentListName/json")
                val modListData = json.decodeFromString<List<ModDesc>>(url.readText())

                val filteredModList = modListData
                    .filter { it.name !in blockedModNames }
                    .map { modDesc ->
                        val finalModId = when {
                            modDesc.url.contains("nexusmods.com/morrowind/mods/") -> {
                                modDesc.url
                                    .substringAfter("mods/")
                                    .substringBefore("?")
                                    .trim()
                                    .toIntOrNull()
                            }
                            else -> modDesc.slug.hashCode()
                                .absoluteValue
                                .toString()
                                .padStart(6, '0')
                                .takeLast(6)
                        }

                        val downloadInfoWithSources = modDesc.downloadInfo.map { info ->
                            info.copy(sourceLists = listOf(currentListName))
                        }

                        modDesc.copy(
                            modId = finalModId.toString(),
                            downloadInfo = downloadInfoWithSources,
                            onLists = listOf(currentListName)
                        )
                    }

                try {
                    modDao.insertOrAppendMods(filteredModList, currentListName, context)
                    allFilteredMods.addAll(filteredModList) // This was missing!
                    addCustomLog("Merged $currentListName (${filteredModList.size} mods)", textSize = 10, textColor = Color.Cyan)
                } catch (e: Exception) {
                    Log.w("ModListManager", "Partial failure processing Nexus data for $modList: ${e.message}")
                    addCustomLog("Partial failure processing Nexus data for $modList: ${e.message}", textSize = 10, textColor = Color.Yellow)
                }
            } catch (e: Exception) {
                Log.e("ModListManager", "Critical error loading mod list $modList: ${e.message}")
                addCustomLog("Critical error loading $modList: ${e.message}", textSize = 10, textColor = Color.Red)
            }
        }

        // Process local JSON files - FIXED PATH
        val directory = File("${Constants.USER_FILE_STORAGE}/OpenMW")

        // Debug logging
        Log.d("ModListManager", "Looking for custom lists in: ${directory.absolutePath}")
        addCustomLog("Looking for custom lists in: ${directory.absolutePath}", textSize = 10, textColor = Color.Cyan)
        Log.d("ModListManager", "Directory exists: ${directory.exists()}")
        addCustomLog("Directory exists: ${directory.exists()}", textSize = 10, textColor = Color.Cyan)
        Log.d("ModListManager", "Is directory: ${directory.isDirectory}")
        addCustomLog("Is directory: ${directory.isDirectory}", textSize = 10, textColor = Color.Cyan)

        if (directory.exists() && directory.isDirectory) {
            val jsonFiles = directory.listFiles { _, name -> name.endsWith(".json") }
            Log.d("ModListManager", "Found ${jsonFiles?.size ?: 0} JSON files")
            addCustomLog("Found ${jsonFiles?.size ?: 0} JSON files", textSize = 10, textColor = Color.Cyan)

            jsonFiles?.forEach { file ->
                Log.d("ModListManager", "Processing file: ${file.name}")
                addCustomLog("Processing file: ${file.name}", textSize = 10, textColor = Color.Cyan)
                try {
                    val jsonText = file.readText()
                    Log.d("ModListManager", "File content length: ${jsonText.length}")

                    val modListData = json.decodeFromString<List<ModDesc>>(jsonText)
                    Log.d("ModListManager", "Parsed ${modListData.size} mods from ${file.name}")

                    val listNameFromFileName = file.nameWithoutExtension
                    val filteredModList = modListData
                        .filter { it.name !in blockedModNames }
                        .map { modDesc ->
                            val finalModId = when {
                                modDesc.url.contains("nexusmods.com/morrowind/mods/") -> {
                                    modDesc.url
                                        .substringAfter("mods/")
                                        .substringBefore("?")
                                        .trim()
                                        .toIntOrNull()
                                }
                                else -> (100000..999999).random()
                            }
                            
                            val downloadInfoWithSources = modDesc.downloadInfo.map { info ->
                                info.copy(sourceLists = listOf(listNameFromFileName))
                            }

                            modDesc.copy(
                                modId = finalModId.toString(),
                                downloadInfo = downloadInfoWithSources,
                                onLists = listOf(listNameFromFileName)
                            )
                        }

                    Log.d("ModListManager", "Filtered to ${filteredModList.size} mods")
                    modDao.insertOrAppendMods(filteredModList, listNameFromFileName, context)
                    allFilteredMods.addAll(filteredModList)
                    Log.d("ModListManager", "Successfully processed ${file.name}")
                    addCustomLog("Processed local file: ${file.name}", textSize = 10, textColor = Color.Green)

                } catch (e: Exception) {
                    Log.e("ModListManager", "Error processing local file ${file.name}: ${e.message}")
                    addCustomLog("Error processing local file ${file.name}: ${e.message}", textSize = 10, textColor = Color.Red)
                    e.printStackTrace()
                }
            }
        } else {
            Log.w("ModListManager", "Directory does not exist or is not a directory: ${directory.absolutePath}")
        }
        processing = false
        return@withContext allFilteredMods
    }

    suspend fun fetchJsonResponse(url: String): String {
        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .header("apikey", apiKey)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                .build()

            val response = withTimeout(90_000) { // 90 seconds timeout
                client.newCall(request).execute().also { response ->
                    NexusInfo.apply {
                        hourlyremaining = response.header("x-rl-hourly-remaining")?.toIntOrNull() ?: hourlyremaining
                        dailyremaining = response.header("x-rl-daily-remaining")?.toIntOrNull() ?: dailyremaining
                        val hourlyResetHeader = response.header("x-rl-hourly-reset")
                        val dailyResetHeader = response.header("x-rl-daily-reset")

                        hourlyResetTime = hourlyResetHeader?.let { parseResetTime(it) }
                        dailyResetTime = dailyResetHeader?.let { parseResetTime(it) }

                        // Update time until reset
                        hourlyResetInMinutes = hourlyResetTime?.let { calculateMinutesUntilReset(it) } ?: 0
                        dailyResetInMinutes = dailyResetTime?.let { calculateMinutesUntilReset(it) } ?: 0
                    }

                    // Log all response headers
                    Log.d("HTTP_HEADERS", "Response headers for $url:")
                    addCustomLog("Response headers for $url:", textSize = 10, textColor = Color.Blue)
                    response.headers.forEach { (name, value) ->
                        Log.d("HTTP_HEADER", "$name: $value")
                        addCustomLog("HTTP_HEADER:          $name: $value", textSize = 10, textColor = Color.Green)
                    }

                    // Log specific important headers
                    Log.d("HTTP_DEBUG", "Status: ${response.code} ${response.message}")
                    addCustomLog("Status:           ${response.code} ${response.message}", textSize = 10, textColor = Color.Green)
                    Log.d("HTTP_DEBUG", "Content-Type: ${response.header("Content-Type")}")
                    addCustomLog("Content-Type:         ${response.header("Content-Type")}", textSize = 10, textColor = Color.Green)
                    Log.d("HTTP_DEBUG", "Content-Length: ${response.header("Content-Length")}")
                    addCustomLog("Content-Length:           ${response.header("Content-Length")}", textSize = 10, textColor = Color.Green)
                    Log.d("HTTP_DEBUG", "Cache-Control: ${response.header("Cache-Control")}")
                    addCustomLog("Cache-Control:            ${response.header("Cache-Control")}", textSize = 10, textColor = Color.Green)
                }
            }

            if (!response.isSuccessful) {
                throw IOException("Failed to fetch: ${response.code} ${response.message}")
            }

            return response.body.use { body ->
                val contentLength = body.contentLength()
                Log.d("HTTP_DEBUG", "Received $contentLength bytes")
                addCustomLog("Received: $contentLength bytes", textSize = 10, textColor = Color.Cyan)
                body.string()
            }
        } catch (e: TimeoutCancellationException) {
            throw IOException("Request timed out after 90 seconds", e)
        } catch (e: Exception) {
            throw IOException("Failed to fetch response: ${e.message}", e)
        }
    }
}

class DownloadViewModel : ViewModel() {
    val completedDownloads = mutableStateSetOf<String>()

    fun markAsCompleted(modId: String) {
        completedDownloads.add(modId)
        // Clean up after a short delay to show completion
        viewModelScope.launch {
            delay(2000) // Wait 2 seconds before cleanup
            NexusInfo.downloadProgressMap.remove(modId)
            NexusInfo.downloadProgressPercentMap.remove(modId)
            NexusInfo.downloadSpeedMap.remove(modId)
            completedDownloads.remove(modId)
        }
    }
}
