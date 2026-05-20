@file:Suppress("unused")   // silence warnings for the JNI stubs
package org.openmw.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.openmw.Constants
import org.openmw.R
import org.openmw.ui.controls.UIStateManager.uqmVersion
import java.io.File
import java.net.URL

private const val NUM_DRIVE_SLOTS = 11   // same value as in uqm/units.h
private const val NUM_JET_SLOTS   = 8   // same value as in uqm/units.h

// ---------------------------------------------------------------------
// JNI stubs (keep them exactly as you posted – they will be linked by
// the native library that implements the real functions)
// ---------------------------------------------------------------------
private external fun getActivity(): Int
private external fun isInBattle(): Boolean
private external fun getShipScreenX(): Int
private external fun getShipScreenY(): Int
private external fun getLogX(): Int
private external fun getLogY(): Int
private external fun getAutopilotX(): Int
private external fun getAutopilotY(): Int
private external fun getIPLocationX(): Int
private external fun getIPLocationY(): Int
private external fun getCrew(): Int
private external fun getFuel(): Int
private external fun getRU(): Int
private external fun getMinerals(out: IntArray)
private external fun getModules(out: ByteArray)
private external fun getDriveSlots(out: ByteArray)
private external fun getJetSlots(out: ByteArray)
private external fun getShipName(): String
private external fun getCommanderName(): String

// ---------------------------------------------------------------------
// Helper data class – one snapshot of the whole game state
// ---------------------------------------------------------------------
private data class GameState(
    val activity: Int,
    val inBattle: Boolean,
    val shipScreen: Pair<Int, Int>,
    val universe: Pair<Int, Int>,
    val autopilot: Pair<Int, Int>,
    val ipCursor: Pair<Int, Int>,
    val crew: Int,
    val fuel: Int,
    val ru: Int,
    val minerals: IntArray,
    val modules: ByteArray,
    val driveSlots: ByteArray,
    val jetSlots: ByteArray,
    val shipName: String,
    val commanderName: String
)

// ---------------------------------------------------------------------
// Native wrapper – collects everything in one call (avoids many JNI
// crossings per frame).
// ---------------------------------------------------------------------
private fun fetchGameState(): GameState {
    val minerals = IntArray(8)
    val modules = ByteArray(16)
    val driveSlots = ByteArray(NUM_DRIVE_SLOTS)
    val jetSlots   = ByteArray(NUM_JET_SLOTS)
    val act = getActivity()

    getDriveSlots(driveSlots)
    getJetSlots(jetSlots)

    return GameState(
        activity = act,
        inBattle = isInBattle(),
        shipScreen = getShipScreenX() to getShipScreenY(),
        universe = getLogX() to getLogY(),
        autopilot = getAutopilotX() to getAutopilotY(),
        ipCursor = getIPLocationX() to getIPLocationY(),
        crew = getCrew(),
        fuel = getFuel(),
        ru = getRU(),
        minerals = minerals,
        modules = modules,
        driveSlots = driveSlots,
        jetSlots = jetSlots,
        shipName = getShipName(),
        commanderName = getCommanderName()
    )
}

// ---------------------------------------------------------------------
// Compose UI – a box that refreshes every second
// ---------------------------------------------------------------------
@Composable
fun DebugOverlayBox() {
    var state by remember { mutableStateOf<GameState?>(null) }

    // -----------------------------------------------------------------
    // Coroutine that polls the native side once per second
    // -----------------------------------------------------------------
    LaunchedEffect(Unit) {
        while (true) {
            state = fetchGameState()
            delay(1_000L)               // 1 second
        }
    }

    // -----------------------------------------------------------------
    // If we don't have data yet, show a placeholder
    // -----------------------------------------------------------------
    if (state == null) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .padding(8.dp)
        ) {
            Text(
                text = "Loading…",
                color = Color.White,
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp
            )
        }
        return
    }

    // -----------------------------------------------------------------
    // Real UI – white monospace text on a semi-transparent black box
    // -----------------------------------------------------------------
    Box(
        modifier = Modifier
            .width(300.dp)
            .wrapContentHeight()
            //.background(Color.Black.copy(alpha = 0.75f))
            .padding(12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val s = state!!

            DebugLine("Activity", "${s.activity}")
            DebugLine("InBattle", if (s.inBattle) "YES" else "NO")

            DebugLine("Ship (screen)", "${s.shipScreen}")
            DebugLine("Universe pos", "${s.universe}")
            DebugLine("Autopilot", "${s.autopilot}")
            DebugLine("IP cursor", "${s.ipCursor}")

            DebugLine("Crew", "${s.crew}")
            DebugLine("Fuel", "${s.fuel}")
            DebugLine("RU", "${s.ru}")

            DebugLine("Minerals", s.minerals.contentToString())
            DebugLine("Modules", s.modules.contentToString())
            DebugLine("DriveSlots", s.driveSlots.contentToString())
            DebugLine("JetSlots",   s.jetSlots.contentToString())

            DebugLine("Ship name", s.shipName)
            DebugLine("Commander", s.commanderName)
        }
    }
}

// ---------------------------------------------------------------------
// Tiny helper to keep the layout tidy
// ---------------------------------------------------------------------
@Composable
private fun DebugLine(label: String, value: String) {
    Text(
        text = "$label: $value",
        color = Color.White,
        fontFamily = FontFamily.Monospace,
        fontSize = 13.sp
    )
}

data class DownloadItem(
    val title: String,
    val size: String,
    val fileName: String,
    val url: String,
    var progress: MutableStateFlow<Int> = MutableStateFlow(0),
    var bytesDownloaded: MutableStateFlow<Long> = MutableStateFlow(0L),
    var totalBytes: MutableStateFlow<Long> = MutableStateFlow(0L),
    var isDownloadComplete: MutableStateFlow<Boolean> = MutableStateFlow(false)
)

suspend fun getFileSize(url: String): Long {
    return withContext(Dispatchers.IO) {
        val client = OkHttpClient()
        val request = Request.Builder().url(url).head().build()
        client.newCall(request).execute().use { response ->
            val contentLength = response.header("Content-Length")?.toLongOrNull()
            contentLength ?: -1
        }
    }
}

@Composable
fun UqmDownloads() {
    val files = listOf(
        "mm-${uqmVersion}-content.uqm",
        "mm-${uqmVersion}-dosmode.uqm",
        "mm-${uqmVersion}-volasaurus-space-music.uqm",
        "uqm-0.8.0-3DOMusicRemastered.uqm",
        "mm-${uqmVersion}-3dovoice.uqm",
        "mm-${uqmVersion}-3domode.uqm",
        "uqm-0.8.0-3dovideo.uqm",
        "mm-remix-timing.uqm",
        "mm-${uqmVersion}-volasaurus-remix-pack.uqm",
        "mm-${uqmVersion}-SyreenVoiceFix.uqm",
        "mm-${uqmVersion}-sol-textures.uqm",
        "mm-${uqmVersion}-rmx-utwig.uqm",
        "mm-${uqmVersion}-purple-urquan-background.uqm",
        "mm-${uqmVersion}-MelnormeVoiceFix.uqm",
        "mm-${uqmVersion}-hd-content.uqm",
        "mm-${uqmVersion}-hd-classic-pack.uqm",
        "mm-${uqmVersion}-distorted-hayes-audio.uqm"
    )
    LazyColumn {
        files.forEach { file ->
            item {
                Row {
                    val url = "https://sourceforge.net/projects/uqm-mods/files/MegaMod/${uqmVersion}/Content/$file/download"
                    val downloadItem = remember {
                        DownloadItem(
                            title = "File",
                            size = "Unknown",
                            fileName = file,
                            url = url
                        )
                    }
                    DownloadScreen(downloadItem)
                }
            }
        }
    }
}

@Composable
fun DownloadScreen(item: DownloadItem) {
    var fileSize by remember { mutableStateOf<Long?>(null) }
    var progress by remember { mutableStateOf(0f) }
    var isDownloading by remember { mutableStateOf(false) }
    var isDownloadComplete by remember { mutableStateOf(false) }
    var isInitialized by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val directory = Constants.USER_FILE_STORAGE + "/uqm/content/" +
            if (item.fileName == "mm-${uqmVersion}-content.uqm") "packages" else "addons"

    val file = File(directory, item.fileName)
    file.parentFile?.mkdirs()

    LaunchedEffect(Unit) {
        if (!isInitialized) {
            // Check if file already exists
            if (file.exists()) {
                isDownloadComplete = true
            } else {
                fileSize = getFileSize(item.url)
            }
            isInitialized = true
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isDownloadComplete) {
            val totalMB = fileSize?.let { it / (1024 * 1024) } ?: stringResource(R.string.unknown)

            Text(
                text = "${stringResource(R.string.file)}: ${item.fileName}\n${stringResource(R.string.size)}: $totalMB MB",
                color = Color.Green
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = {
                    if (file.exists()) {
                        file.delete()
                        isDownloadComplete = false
                        progress = 0f
                    }
                }
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Uninstall")
                }
            }
        } else {
            val downloadedMB = (progress * (fileSize ?: 0)) / (1024 * 1024)
            val totalMB = fileSize?.let { it / (1024 * 1024) } ?: 0

            Column {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${stringResource(R.string.file)}: ${item.fileName}\n${stringResource(R.string.downloaded)}: ${downloadedMB.toInt()} MB / ${if (totalMB > 0) "$totalMB MB" else stringResource(R.string.unknown)}",
                        fontSize = 14.sp,
                    )
                    Spacer(modifier = Modifier.weight(1f))

                    Button(
                        onClick = {
                            if (!isDownloading) {
                                isDownloading = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    try {
                                        downloadFile(
                                            url = item.url,
                                            outputFile = file,
                                            updateProgress = { newProgress ->
                                                progress = newProgress
                                            }
                                        )
                                        withContext(Dispatchers.Main) {
                                            isDownloadComplete = true
                                            isDownloading = false
                                        }
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        withContext(Dispatchers.Main) {
                                            isDownloading = false
                                            // Handle error (show toast, etc.)
                                        }
                                    }
                                }
                            }
                        },
                        enabled = !isDownloading
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isDownloading) "Downloading..." else stringResource(R.string.install))
                        }
                    }
                }

                // Optional: Add progress bar
                if (isDownloading && progress > 0) {
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        }
    }
}

private suspend fun downloadFile(
    url: String,
    outputFile: File,
    updateProgress: (Float) -> Unit
) {
    withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection()
        val totalSize = connection.contentLength.toFloat()
        var downloadedSize = 0

        println("Downloading to: ${outputFile.absolutePath}")
        println("Total size: $totalSize bytes")

        connection.getInputStream().use { input ->
            outputFile.outputStream().use { output ->
                val buffer = ByteArray(8 * 1024)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                    downloadedSize += bytesRead
                    updateProgress(downloadedSize / totalSize)
                }
            }
        }
        println("Download complete. File size on disk: ${outputFile.length()} bytes")
    }
}