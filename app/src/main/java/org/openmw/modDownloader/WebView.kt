package org.openmw.modDownloader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.openmw.Constants
import org.openmw.modDownloader.NexusInfo.downloadProgressMap
import org.openmw.modDownloader.NexusInfo.extractionProgressMap
import org.openmw.modDownloader.NexusInfo.extractionProgressPercentMap
import org.openmw.ui.controls.UIStateManager.customColor
import org.openmw.ui.controls.UIStateManager.gold
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

@SuppressLint("JavascriptInterface", "SetJavaScriptEnabled")
@Composable
fun WebViewScreen(
    tabs: List<WebTab>,
    currentTabIndex: Int,
    onTabChange: (Int) -> Unit,
    onCloseTab: (String) -> Unit,
    viewModel: DownloadViewModel = viewModel()
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    val modDao = ModDatabase.getDatabase(context).modDao()
    var currentMod by remember { mutableStateOf<ModDesc?>(null) }

    val currentTab = tabs.getOrNull(currentTabIndex)

    val modId = currentTab?.id
    val fileName = currentTab?.fileName
    val url = currentTab?.url

    LaunchedEffect(currentTabIndex, tabs) {
        if (tabs.isNotEmpty() && currentTabIndex in tabs.indices) {
            val currentTab = tabs[currentTabIndex]
            Log.d("NexusInfo", "Processing tab $currentTabIndex, URL: ${currentTab.url}, extracted modId: $modId")
            if (modId != null) {
                try {
                    val mod = modDao.getModById(modId)
                    if (mod != null) {
                        currentMod = mod
                        Log.d("NexusInfo", "Fetched ModDesc for modId=$modId: $mod")
                    } else {
                        Log.w("NexusInfo", "No ModDesc found for modId=$modId")
                        currentMod = null
                    }
                } catch (e: Exception) {
                    Log.e("NexusInfo", "Failed to fetch ModDesc for modId=$modId", e)
                    currentMod = null
                }
            } else {
                Log.w("NexusInfo", "Could not extract modId from URL: ${currentTab.url}")
                currentMod = null
            }
        } else {
            Log.w("NexusInfo", "Invalid tab state: tabs=$tabs, currentTabIndex=$currentTabIndex")
            currentMod = null
        }
    }

    Dialog(
        onDismissRequest = { },
        properties = androidx.compose.ui.window.DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier
            )
        }
        Column {
            val statusPriority = mapOf(
                ModStatus.DOWNLOADING to 0,
                ModStatus.EXTRACTING to 1,
                ModStatus.IDLE to 2,
                ModStatus.COMPLETED to 3
            )

            val activeDownloads by remember(downloadProgressMap, viewModel.completedDownloads) {
                derivedStateOf {
                    downloadProgressMap
                        .filter { (modId, _) -> modId !in viewModel.completedDownloads }
                        .toList()
                        .sortedBy { (modId, _) -> statusPriority[getModStatus(modId)] ?: 4 }
                }
            }

            if (activeDownloads.isNotEmpty()) {
                Text(
                    text = "Downloads:",
                    modifier = Modifier.padding(8.dp),
                    fontWeight = FontWeight.Bold
                )
            }

            activeDownloads.forEach { (modId, progress) ->
                val extractionStatus = extractionProgressMap[modId] ?: "Waiting..."
                val isDownloading = extractionStatus.contains("Waiting")
                val isExtracting = extractionStatus.contains("Extracting")

                if (!progress.contains("Downloaded") && isDownloading || isExtracting) {
                    Row(
                        modifier = Modifier
                            .padding(8.dp)
                            .fillMaxWidth()
                            .background(customColor)
                    ) {
                        Text(
                            text = if (isDownloading) {
                                progress.replace("Downloaded", "")
                            } else {
                                extractionStatus
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = if (isDownloading) {
                                "${NexusInfo.downloadProgressPercentMap[modId] ?: 0}% (${NexusInfo.downloadSpeedMap[modId] ?: 0} KB/s)"
                            } else {
                                "${extractionProgressPercentMap[modId]?.toInt() ?: 0}%"
                            },
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }

                    // Progress bar for download or extraction
                    LinearProgressIndicator(
                        progress = if (isDownloading) {
                            (NexusInfo.downloadProgressPercentMap[modId] ?: 0) / 100f
                        } else {
                            (extractionProgressPercentMap[modId] ?: 0f) / 100f
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .padding(horizontal = 8.dp)
                    )
                }
            }
            Row {
                IconButton(
                    onClick = {
                        if (tabs.isNotEmpty() && currentTabIndex in tabs.indices) {
                            onCloseTab(tabs[currentTabIndex].id)
                        }
                    }
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close Tab")
                }
                if (tabs.size > 1) {
                    ScrollableTabRow(
                        selectedTabIndex = currentTabIndex,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(gold),
                        edgePadding = 0.dp,
                        divider = {}
                    ) {
                        tabs.forEachIndexed { index, tab ->
                            Tab(
                                selected = currentTabIndex == index,
                                onClick = { onTabChange(index) },
                                text = {
                                    Text(
                                        text = "Tab ${index + 1}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = Color.White
                                    )
                                },
                                modifier = Modifier
                                    .background(
                                        if (currentTabIndex == index) {
                                            customColor
                                        } else {
                                            Color.DarkGray
                                        }
                                    )
                            )
                        }
                    }
                }
            }

            // Wrap AndroidView in a key block to force recreation when tabs or currentTabIndex change
            key(tabs.hashCode(), currentTabIndex) {
                AndroidView(
                    factory = { context ->
                        WebView(context).apply {
                            setLayerType(View.LAYER_TYPE_HARDWARE, null)
                            settings.apply {
                                javaScriptEnabled = true
                                domStorageEnabled = true
                                setSupportZoom(true)
                                builtInZoomControls = true
                                displayZoomControls = false
                                useWideViewPort = true
                                loadWithOverviewMode = true
                                allowContentAccess = true
                                allowFileAccess = true
                                allowFileAccessFromFileURLs = true
                                allowUniversalAccessFromFileURLs = true
                            }

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    isLoading = newProgress < 100
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(
                                    view: WebView?,
                                    url: String?,
                                    favicon: Bitmap?
                                ) {
                                    isLoading = true
                                }

                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    view?.loadUrl(request?.url.toString())
                                    return true
                                }
                            }

                            setDownloadListener { url, userAgent, contentDisposition, mimetype, contentLength ->

                                Log.d(
                                    "WebViewScreen",
                                    "Download started for $fileName, currentTabIndex: $currentTabIndex, tabs: $tabs"
                                )

                                // Find the tab associated with the download URL or filename
                                val tabToClose =
                                    tabs.firstOrNull { it.fileName == fileName || it.url == url }
                                        ?: tabs.getOrNull(currentTabIndex)

                                if (tabToClose != null) {
                                    // Update the tab with the filename if not already set
                                    if (tabToClose.fileName == null) {
                                        CoroutineScope(Dispatchers.Default).launch {
                                            NexusInfo.updateTabFileName(tabToClose.id, "$fileName")
                                        }
                                    }

                                    Log.d(
                                        "WebViewScreen",
                                        "Closing tab with ID: ${tabToClose.id} for file: $fileName"
                                    )
                                    onCloseTab(tabToClose.id)
                                } else {
                                    Log.w(
                                        "WebViewScreen",
                                        "No tab found to close for file: $fileName, tabs=$tabs, currentTabIndex=$currentTabIndex"
                                    )
                                }

                                CoroutineScope(Dispatchers.IO).launch {
                                    val modToExtract = currentMod
                                    val modId = if (modToExtract != null) {
                                        modToExtract.slug
                                    } else {
                                        // Fallback: Try to find ModDesc by filename
                                        val mod = try {
                                            modDao.getModByFileName("$fileName")
                                        } catch (e: Exception) {
                                            Log.e("WebViewScreen", "Failed to fetch ModDesc by fileName=$fileName", e)
                                            null
                                        }
                                        mod?.slug ?: extractModIdFromUrl(url) ?: fileName
                                    }
                                    val outputFile = File("${Constants.USER_FILE_STORAGE}/OpenMW/CACHE", "$fileName")

                                    // Check if file exists and size matches
                                    var shouldDownload = true
                                    if (modToExtract != null) {
                                        val downloadInfo = modToExtract.downloadInfo.find { it.fileName == fileName }
                                        val versionInfo = modToExtract.downloadInfo.flatMap { it.versions }.find { it.fileName == fileName }
                                        val fileSizeKb = downloadInfo?.fileSize?.toLongOrNull() ?: versionInfo?.sizeKb?.toLongOrNull()
                                        if (outputFile.exists() && fileSizeKb != null) {
                                            val fileSizeBytes = fileSizeKb * 1024 // Convert KB to bytes
                                            if (outputFile.length() == fileSizeBytes) {
                                                shouldDownload = false
                                                Log.d("WebViewScreen", "Skipping download for $fileName: File exists and size matches ($fileSizeBytes bytes)")
                                            } else {
                                                Log.d("WebViewScreen", "File $fileName exists but size mismatch (expected $fileSizeBytes, got ${outputFile.length()})")
                                            }
                                        } else if (outputFile.exists()) {
                                            Log.w("WebViewScreen", "File $fileName exists but no fileSize available in DownloadInfo")
                                        }
                                    } else {
                                        Log.w("WebViewScreen", "No ModDesc available to check fileSize for $fileName")
                                    }


                                    Log.d(
                                        "WebViewScreen",
                                        "Starting download to: ${outputFile.absolutePath}"
                                    )
                                    if (shouldDownload) {
                                    val connection =
                                        URL(url).openConnection() as HttpURLConnection
                                    connection.connect()
                                    val fileLength = connection.contentLengthLong
                                    val inputStream: InputStream = connection.inputStream
                                    val outputStream = FileOutputStream(outputFile)
                                    var totalBytesRead: Long = 0
                                    val buffer = ByteArray(65536)
                                    val startTime = System.currentTimeMillis()

                                    inputStream.use { input ->
                                        outputStream.use { output ->
                                            while (true) {
                                                val bytesRead = input.read(buffer)
                                                if (bytesRead == -1) break
                                                output.write(buffer, 0, bytesRead)
                                                totalBytesRead += bytesRead
                                                if (fileLength > 0) {
                                                    val progress =
                                                        ((totalBytesRead * 100) / fileLength).toInt()
                                                    val elapsedTime =
                                                        (System.currentTimeMillis() - startTime) / 1000.0
                                                    val speedKBs =
                                                        if (elapsedTime > 0) (totalBytesRead / 1024.0 / elapsedTime).toInt() else 0
                                                    downloadProgressMap["$modId"] = "Downloading $fileName"
                                                    NexusInfo.downloadProgressPercentMap["$modId"] = progress
                                                    NexusInfo.downloadSpeedMap["$modId"] = speedKBs
                                                }
                                            }
                                        }
                                    }
                                    Log.d(
                                        "WebViewScreen",
                                        "Download completed for $fileName"
                                    )
                                    } else {
                                        Log.d("WebViewScreen", "Using existing file for $fileName")
                                    }

                                    downloadProgressMap["$fileName"] = "$fileName downloaded"
                                    viewModel.markAsCompleted("$fileName")

                                    if (modToExtract != null) {
                                        extractionProgressMap["$modId"] = "Starting extraction for ${modToExtract.name}"
                                    }
                                    extractionProgressPercentMap["$modId"] = 0f
                                    if (modToExtract != null) {
                                        for (info in modToExtract.downloadInfo) {
                                            extractModFile(modToExtract, info)
                                        }
                                    }
                                }
                            }

                            val cookieManager = CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(this, true)

                            tabs.getOrNull(currentTabIndex)?.url?.let { loadUrl(it) }
                        }
                    },
                    update = { webView ->
                        val urlToLoad = tabs.getOrNull(currentTabIndex)?.url
                        if (!urlToLoad.isNullOrBlank() && webView.url != urlToLoad) {
                            Log.d("WebViewScreen", "Updating WebView to load URL: $urlToLoad")
                            webView.loadUrl(urlToLoad)
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

fun extractModIdFromUrl(url: String): String? {
    return try {
        val uri = Uri.parse(url)
        val pathSegments = uri.pathSegments
        // URL format: https://www.nexusmods.com/morrowind/mods/<modId>?tab=files&file_id=<nexusFileId>
        // pathSegments: ["morrowind", "mods", "<modId>"]
        if (pathSegments.size >= 3 && pathSegments[1] == "mods") {
            pathSegments[2] // The modId is the third segment
        } else {
            null
        }
    } catch (e: Exception) {
        Log.e("NexusInfo", "Failed to parse modId from URL: $url", e)
        null
    }
}