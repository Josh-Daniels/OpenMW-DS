@file:Suppress("unused")   // silence warnings for the JNI stubs
package org.openmw.utils

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
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
external fun getActivity(): Int
external fun isInBattle(): Boolean
external fun getShipScreenX(): Int
external fun getShipScreenY(): Int
external fun getLogX(): Int
external fun getLogY(): Int
external fun getAutopilotX(): Int
external fun getAutopilotY(): Int
external fun getIPLocationX(): Int
external fun getIPLocationY(): Int
external fun getCrew(): Int
external fun getFuel(): Int
external fun getRU(): Int
external fun getMinerals(out: IntArray)
external fun getModules(out: ByteArray)
external fun getDriveSlots(out: ByteArray)
external fun getJetSlots(out: ByteArray)
external fun getShipName(): String
external fun getCommanderName(): String

external fun getShipFacing(): Int
external fun getTravelAngle(): Int
external fun getSpeedX(): Float
external fun getSpeedY(): Float
external fun isInOrbit(): Boolean
external fun getIPPlanet(): Int
external fun getEncounterRace(): Int
external fun getBattleRace(side: Int): Int

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
    val commanderName: String,
    val shipFacing: Int,
    val travelAngle: Int,
    val speedX: Float,
    val speedY: Float,
    val inOrbit: Boolean,
    val ipPlanet: Int,
    val encounterRace: Int,
    val playerBattleRace: Int,
    val npcBattleRace: Int
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
        commanderName = getCommanderName(),
        shipFacing = getShipFacing(),
        travelAngle = getTravelAngle(),
        speedX = getSpeedX(),
        speedY = getSpeedY(),
        inOrbit = isInOrbit(),
        ipPlanet = getIPPlanet(),
        encounterRace = getEncounterRace(),
        playerBattleRace = getBattleRace(0),
        npcBattleRace = getBattleRace(1)
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
                .background(Color.Black.copy(alpha = 0.3f))
                .padding(2.dp)
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

            .wrapContentSize()
            .padding(30.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(0.dp)
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

            DebugLine("Facing", "${s.shipFacing}")
            DebugLine("Travel Angle", "${s.travelAngle}")
            val totalSpeed = kotlin.math.sqrt(s.speedX * s.speedX + s.speedY * s.speedY)
            DebugLine("Speed", "%.2f (X: %.2f, Y: %.2f)".format(totalSpeed, s.speedX, s.speedY))
            DebugLine("Orbiting", if (s.inOrbit) "YES" else "NO")
            DebugLine("IP Planet", "${s.ipPlanet}")
            DebugLine("Encounter", "${s.encounterRace}")
            if (s.inBattle) {
                DebugLine("Player Ship", "${s.playerBattleRace}")
                DebugLine("NPC Ship", "${s.npcBattleRace}")
            }
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
        fontSize = 10.sp,
        lineHeight = 11.sp
    )
}

data class DownloadItem(
    val fileName: String,
    val url: String,
    val progress: MutableStateFlow<Float> = MutableStateFlow(0f),
    val isDownloading: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val isComplete: MutableStateFlow<Boolean> = MutableStateFlow(false),
    val fileSize: MutableStateFlow<Long> = MutableStateFlow(-1L)
)

object UqmDownloadManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val items = mutableMapOf<String, DownloadItem>()

    fun getItem(fileName: String, url: String): DownloadItem {
        return items.getOrPut(fileName) {
            val item = DownloadItem(fileName, url)
            checkInitialStatus(item)
            item
        }
    }

    private fun checkInitialStatus(item: DownloadItem) {
        val file = getFileFor(item.fileName)
        if (file.exists()) {
            item.isComplete.value = true
            item.fileSize.value = file.length()
        } else {
            scope.launch(Dispatchers.IO) {
                val size = getFileSize(item.url)
                item.fileSize.value = size
            }
        }
    }

    fun getFileFor(fileName: String): File {
        val directory = Constants.USER_FILE_STORAGE + "/uqm/content/" +
                if (fileName == "mm-${uqmVersion}-content.uqm") "packages" else "addons"
        return File(directory, fileName)
    }

    fun startDownload(item: DownloadItem) {
        if (item.isDownloading.value || item.isComplete.value) return
        item.isDownloading.value = true
        scope.launch(Dispatchers.IO) {
            try {
                val file = getFileFor(item.fileName)
                file.parentFile?.mkdirs()
                downloadFile(item.url, file) { progress ->
                    item.progress.value = progress
                }
                item.isComplete.value = true
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                item.isDownloading.value = false
            }
        }
    }

    fun uninstall(item: DownloadItem) {
        val file = getFileFor(item.fileName)
        if (file.exists()) {
            file.delete()
        }
        item.isComplete.value = false
        item.progress.value = 0f
    }
}

suspend fun getFileSize(url: String): Long {
    return withContext(Dispatchers.IO) {
        try {
            val client = OkHttpClient()
            val request = Request.Builder().url(url).head().build()
            client.newCall(request).execute().use { response ->
                response.header("Content-Length")?.toLongOrNull() ?: -1L
            }
        } catch (e: Exception) {
            -1L
        }
    }
}

@Composable
fun UqmDownloads() {
    val files = remember {
        listOf(
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
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 16.dp)
    ) {
        items(files) { fileName ->
            val url = "https://sourceforge.net/projects/uqm-mods/files/MegaMod/${uqmVersion}/Content/$fileName/download"
            val item = remember(fileName) { UqmDownloadManager.getItem(fileName, url) }
            DownloadCard(item)
        }
    }
}

@Composable
fun DownloadCard(item: DownloadItem) {
    val progress by item.progress.collectAsState()
    val isDownloading by item.isDownloading.collectAsState()
    val isComplete by item.isComplete.collectAsState()
    val fileSize by item.fileSize.collectAsState()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp)),
        color = Color.Black.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Icon based on state
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(
                            if (isComplete) Color.Green.copy(alpha = 0.2f)
                            else Color.White.copy(alpha = 0.1f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isComplete) Icons.Default.DownloadDone else Icons.Default.Download,
                        contentDescription = null,
                        tint = if (isComplete) Color.Green else Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.fileName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    val sizeText = if (fileSize > 0) "${fileSize / (1024 * 1024)} MB" else "---"
                    Text(
                        text = if (isDownloading) "${(progress * 100).toInt()}%" else sizeText,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp
                    )
                }

                if (isComplete) {
                    IconButton(
                        onClick = { UqmDownloadManager.uninstall(item) },
                        colors = IconButtonDefaults.iconButtonColors(contentColor = Color.Red.copy(alpha = 0.7f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = "Uninstall")
                    }
                } else {
                    Button(
                        onClick = { UqmDownloadManager.startDownload(item) },
                        enabled = !isDownloading,
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.2f),
                            contentColor = Color.White,
                            disabledContainerColor = Color.White.copy(alpha = 0.05f)
                        )
                    ) {
                        Text(
                            text = if (isDownloading) "..." else "Install",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (isDownloading) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )
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