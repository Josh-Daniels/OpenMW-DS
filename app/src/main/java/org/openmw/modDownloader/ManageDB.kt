@file:Suppress("DEPRECATION")

package org.openmw.modDownloader

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.Html
import android.text.method.LinkMovementMethod
import android.util.Log
import android.util.TypedValue
import android.widget.TextView
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.Update
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonContentPolymorphicSerializer
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.openmw.Constants
import org.openmw.modDownloader.ModListManager.modList
import org.openmw.modDownloader.NexusInfo.downloadProgressMap
import org.openmw.modDownloader.StatusInfo.activeMods
import org.openmw.modDownloader.ZipUtils.nativeGetArchiveFileList
import org.openmw.ui.controls.UIStateManager.customColor
import org.openmw.utils.GameFilesPreferences.loadIsPremiumTier
import java.io.File
import java.time.Instant
import androidx.core.net.toUri
import org.openmw.ui.controls.UIStateManager.gold

@Dao
interface ModDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMod(mod: ModDesc)

    @Query("SELECT COUNT(*) FROM mods")
    suspend fun getModCount(): Int

    @Update
    suspend fun updateMods(mods: List<ModDesc>)

    @Update
    suspend fun updateMod(mod: ModDesc)

    @Delete
    suspend fun deleteMod(mod: ModDesc)

    @Query("SELECT * FROM mods WHERE slug = :slug")
    fun getModBySlugFlow(slug: String): Flow<ModDesc?>

    @Query("SELECT * FROM mods WHERE slug LIKE :slug")
    fun getModsBySlugFlowFilter(slug: String): Flow<List<ModDesc>>

    suspend fun getModByFileName(fileName: String): ModDesc? {
        return getAllMods().find { mod ->
            mod.downloadInfo.any { it.fileName == fileName || it.versions.any { v -> v.fileName == fileName } }
        }
    }

    @Query("SELECT * FROM mods WHERE slug = :slug")
    fun getModBySlug(slug: String): Flow<ModDesc?>

    @Query("SELECT * FROM mods ORDER BY name COLLATE NOCASE ASC")
    fun getAllModsFlow(): Flow<List<ModDesc>>

    @Query("SELECT * FROM mods WHERE category = :category ORDER BY name COLLATE NOCASE ASC")
    suspend fun getModsByCategory(category: String): List<ModDesc>

    @Query("SELECT * FROM mods ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAllMods(): List<ModDesc>

    @Query("DELETE FROM mods")
    suspend fun clearAllMods()

    @Transaction
    suspend fun replaceAllMods(mods: List<ModDesc>) {
        clearAllMods()
        _insertAll(mods)
    }

    @Query("SELECT * FROM mods WHERE slug IN (:slugs)")
    suspend fun getModsBySlugs(slugs: List<String>): List<ModDesc>

    private fun deduplicateDownloadInfo(list: List<DownloadInfo>): List<DownloadInfo> {
        return list.distinctBy { info ->
            // Create a unique key that includes ALL fields
            "${info.fileName}|${info.nexusFileId}|${info.directDownload}|${info.extractTo}|${info.fileSize}|${info.sourceLists.sorted().joinToString()}"
        }
    }


    @Transaction
    suspend fun insertOrAppendMods(mods: List<ModDesc>, currentList: String, context: Context) {
        StatusInfo.processing = true
        val existingMods = getModsBySlugs(mods.map { it.slug }).associateBy { it.slug }

        //FileLogger.logDebug(context, "DuplicateDebug", "Processing ${mods.size} mods for list: $currentList")

        val alreadyProcessed = mods.all { mod ->
            existingMods[mod.slug]?.onLists?.contains(currentList) == true
        }

        if (alreadyProcessed) {
            //FileLogger.logDebug(context, "DuplicateDebug", "List $currentList already processed - skipping")
            return
        }

        // Log the incoming mods with their sourceLists
        /*
        mods.forEach { mod ->
            FileLogger.logDebug(context, "DuplicateDebug",
                "Incoming mod: ${mod.slug} with sources: ${mod.downloadInfo.flatMap { it.sourceLists }.distinct()}")
        }

         */

        val mergedMods = mods.map { newMod ->
            val existing = existingMods[newMod.slug]
            if (existing != null) {
                //FileLogger.logDebug(context, "DuplicateDebug",
                    //"Merging existing mod: ${newMod.slug} - existing sources: ${existing.onLists}")
                val mergedDownloadInfo = mergeDownloadInfo(existing.downloadInfo, newMod.downloadInfo, currentList, context)
                newMod.copy(
                    onLists = (existing.onLists + newMod.onLists).distinct(),
                    downloadInfo = mergedDownloadInfo
                )
            } else {
                //FileLogger.logDebug(context, "DuplicateDebug", "New mod: ${newMod.slug}")
                newMod
            }
        }

        insertOrReplace(mergedMods)
        StatusInfo.processing = false
        //FileLogger.logDebug(context, "DuplicateDebug", "Completed insertOrAppendMods for list: $currentList")

        // FLUSH ALL LOGS TO FILE AT THE END
        //FileLogger.flushLogsToFile(context)
    }

    private fun mergeDownloadInfo(
        existing: List<DownloadInfo>,
        new: List<DownloadInfo>,
        currentList: String,
        context: Context
    ): List<DownloadInfo> {
        //FileLogger.logDebug(context, "MergeDebug", "Merging download info - existing: ${existing.size}, new: ${new.size}")

        // More comprehensive key function - include ALL identifying fields
        fun key(info: DownloadInfo) =
            "${info.fileName.orEmpty()}|${info.nexusFileId.orEmpty()}|${info.directDownload.orEmpty()}|${info.extractTo.orEmpty()}"

        val mergedMap = existing.associateBy { key(it) }.toMutableMap()

        // Log existing entries
        /*
        existing.forEachIndexed { index, info ->
            FileLogger.logDebug(context, "MergeDebug", "Existing[$index]: key=${key(info)}, fileName=${info.fileName}, sources=${info.sourceLists}")
        }

         */

        for (newInfo in new) {
            val k = key(newInfo)
            val existingInfo = mergedMap[k]

            if (existingInfo == null) {
                // Brand new variant - add currentList to sources
                //FileLogger.logDebug(context, "MergeDebug", "New entry: key=$k, fileName=${newInfo.fileName}")
                val sources = if (newInfo.sourceLists.isEmpty()) {
                    listOf(currentList)
                } else {
                    (newInfo.sourceLists + currentList).distinct()
                }
                mergedMap[k] = newInfo.copy(sourceLists = sources)
            } else {
                //FileLogger.logDebug(context, "MergeDebug", "Duplicate found - key: $k")
                //FileLogger.logDebug(context, "MergeDebug", "  Existing: ${existingInfo.fileName} | ${existingInfo.nexusFileId} | sources=${existingInfo.sourceLists}")
                //FileLogger.logDebug(context, "MergeDebug", "  New: ${newInfo.fileName} | ${newInfo.nexusFileId} | sources=${newInfo.sourceLists}")

                // Same variant, merge sources - FIXED LOGIC
                val newSources = if (newInfo.sourceLists.isEmpty()) {
                    listOf(currentList)
                } else {
                    newInfo.sourceLists
                }

                val mergedSources = (existingInfo.sourceLists + newSources + currentList)
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .sorted()

                //FileLogger.logDebug(context, "MergeDebug", "  Merged sources: $mergedSources")

                // Create merged DownloadInfo with ALL fields from existing (preserving data)
                // but updated sourceLists
                mergedMap[k] = existingInfo.copy(
                    sourceLists = mergedSources,
                    // Preserve other fields from existing info since it has more complete data
                    directDownload = existingInfo.directDownload ?: newInfo.directDownload,
                    nexusFileId = existingInfo.nexusFileId ?: newInfo.nexusFileId,
                    fileSize = existingInfo.fileSize ?: newInfo.fileSize,
                    extractTo = existingInfo.extractTo ?: newInfo.extractTo,
                    extractedSizeBytes = existingInfo.extractedSizeBytes ?: newInfo.extractedSizeBytes
                )
            }
        }

        val result = mergedMap.values
            .filter { !it.fileName.isNullOrBlank() }
            .toList()

        /*
        FileLogger.logDebug(
            context,
            "MergeDebug",
            "Final merged download info count: ${result.size}"
        )
        result.forEachIndexed { index, info ->
            FileLogger.logDebug(
                context,
                "MergeDebug",
                "Result[$index]: ${info.fileName} | sources=${info.sourceLists}"
            )
        }
         */
        return result
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrReplace(mods: List<ModDesc>)

    @Query("SELECT slug FROM mods")
    suspend fun getAllModSlugs(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun _insertAll(mods: List<ModDesc>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdateMod(mod: ModDesc)

    data class InsertResult(val insertedCount: Int, val duplicateCount: Int)

    @Query("SELECT * FROM mods WHERE tags LIKE '%' || :tag || '%' ORDER BY name COLLATE NOCASE ASC")
    suspend fun getModsByTag(tag: String): List<ModDesc>

    @Query("SELECT DISTINCT category FROM mods ORDER BY category COLLATE NOCASE ASC")
    suspend fun getAllCategories(): List<String>

    @Update
    suspend fun update(mod: ModDesc)

    @Query("SELECT * FROM mods WHERE on_lists LIKE '%' || :modList || '%' ORDER BY name COLLATE NOCASE ASC")
    suspend fun getModsByModList(modList: String): List<ModDesc>

    @Query("SELECT * FROM mods WHERE on_lists LIKE '%' || :modList || '%' ORDER BY name COLLATE NOCASE ASC")
    fun getModsByModListFlow(modList: String): Flow<List<ModDesc>>

    @Query("SELECT * FROM mods WHERE modId = :modId")
    suspend fun getModsByModId(modId: String): List<ModDesc>

    @Query("SELECT * FROM mods WHERE modId = :modId")
    suspend fun getModById(modId: String): ModDesc?

    @Query("SELECT * FROM mods WHERE modId = :modId")
    fun getModsByModIdFlow(modId: String): Flow<List<ModDesc>>
}

class Converters {
    private val json = Json {
        ignoreUnknownKeys = true
        serializersModule = SerializersModule {
            polymorphic(Action::class) {
                subclass(Action.RemoveAction::class, Action.RemoveAction.serializer())
                subclass(Action.CleanAction::class, Action.CleanAction.serializer())
                subclass(Action.CopyAction::class, Action.CopyAction.serializer())
                subclass(Action.RenameAction::class, Action.RenameAction.serializer())
            }
        }
    }

    @TypeConverter
    fun fromStringList(value: List<String>): String = json.encodeToString(value)

    @TypeConverter
    fun toStringList(value: String): List<String> = json.decodeFromString(value)

    @TypeConverter
    fun fromDownloadInfoList(value: List<DownloadInfo>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toDownloadInfoList(value: String): List<DownloadInfo> {
        return Json.decodeFromString(value)
    }

    @TypeConverter
    fun fromActionList(value: List<Action>): String = json.encodeToString(value)

    @TypeConverter
    fun toActionList(value: String): List<Action> = json.decodeFromString(value)

    @TypeConverter
    fun fromDownloadInfoVersionList(value: List<DownloadInfo.Version>): String {
        return Json.encodeToString(value)
    }

    @TypeConverter
    fun toDownloadInfoVersionList(value: String): List<DownloadInfo.Version> {
        return Json.decodeFromString(value)
    }
}

@Serializable
data class DownloadInfo(
    @SerialName("direct_download") val directDownload: String? = null,
    @SerialName("file_name") val fileName: String? = null,
    @SerialName("extract_to") val extractTo: String? = null,
    @SerialName("nexus_file_id") val nexusFileId: String? = null,
    @SerialName("size_kb") val fileSize: String? = null,
    @SerialName("versions") val versions: List<Version> = emptyList(),
    @SerialName("source_lists") val sourceLists: List<String> = emptyList(),
    val extractedSizeBytes: Long? = null,
    //val actions: List<String> = emptyList()
){
    @Serializable
    data class Version(
        @SerialName("file_name") val fileName: String,
        @SerialName("file_id") val fileId: String,
        @SerialName("version") val version: String?,
        @SerialName("size_kb") val sizeKb: String?,
        @SerialName("category_name") val category: String
    )
}

@Entity(tableName = "mods")
@TypeConverters(Converters::class)
@Serializable
data class ModDesc(
    val modId: String = "",
    @PrimaryKey val slug: String = "",
    val name: String = "",
    val author: String = "",
    val description: String = "",
    val url: String = "",
    val category: String = "",
    @ColumnInfo(name = "dl_url") @SerialName("dl_url") val dlUrl: String? = null,
    @ColumnInfo(name = "usage_notes") @SerialName("usage_notes") val usageNotes: String? = null,
    val compat: Int = 0,
    val dir: String = "",
    @ColumnInfo(name = "date_added") @SerialName("date_added") val dateAdded: String? = null,
    @ColumnInfo(name = "date_updated") @SerialName("date_updated") val dateUpdated: String? = null,
    val tags: List<String> = emptyList(),
    @ColumnInfo(name = "on_lists") @SerialName("on_lists") val onLists: List<String> = emptyList(),
    @ColumnInfo(name = "data_paths") @SerialName("data_paths") val dataPaths: List<String> = emptyList(),
    @ColumnInfo(name = "gallery_urls") val galleryUrls: List<String> = emptyList(),
    val plugins: List<String> = emptyList(),
    @SerialName("download_info") val downloadInfo: List<DownloadInfo> = emptyList(),
    @ColumnInfo(name = "gitlab_project_id") @SerialName("gitlab_project_id") val gitlabProjectId: String? = null,
    @ColumnInfo(name = "gitlab_package_id") @SerialName("gitlab_package_id") val gitlabPackageId: String? = null
)

@Serializable(with = ActionSerializer::class)
sealed class Action {
    @Serializable
    @SerialName("remove")
    data class RemoveAction(val path: String? = null) : Action()

    @Serializable
    @SerialName("clean")
    data class CleanAction(val path: String? = null) : Action()

    @Serializable
    @SerialName("copy")
    data class CopyAction(val source: String? = null, val destination: String? = null) : Action()

    @Serializable
    @SerialName("rename")
    data class RenameAction(val oldName: String? = null, val newName: String? = null) : Action()
}

object ActionSerializer : JsonContentPolymorphicSerializer<Action>(Action::class) {
    override fun selectDeserializer(element: JsonElement): DeserializationStrategy<Action> {
        val actionType = element.jsonObject["action"]?.toString()?.removeSurrounding("\"")
            ?: throw SerializationException("Missing 'action' field in JSON")

        return when (actionType) {
            "remove" -> Action.RemoveAction.serializer()
            "clean" -> Action.CleanAction.serializer()
            "copy" -> Action.CopyAction.serializer()
            "rename" -> Action.RenameAction.serializer()
            else -> throw SerializationException("Unknown action type: $actionType")
        }
    }
}
// Function to update specific fields for a mod by modId
suspend fun updateModById(context: Context, modId: String, updateBlock: (ModDesc) -> ModDesc) {
    withContext(Dispatchers.IO) {
        val modDao = ModDatabase.getDatabase(context).modDao()
        val mod = modDao.getModById(modId)
        if (mod != null) {
            val updatedMod = updateBlock(mod)
            modDao.updateMod(updatedMod)
        } else {
            Log.w("ModListManager", "Mod with modId $modId not found")
        }
    }
}

@Database(
    entities = [ModDesc::class],
    version = 20,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class ModDatabase : RoomDatabase() {
    abstract fun modDao(): ModDao

    companion object {
        @Volatile
        private var INSTANCE: ModDatabase? = null

        fun getDatabase(context: Context): ModDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ModDatabase::class.java,
                    "mods_database.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

@Composable
fun ModListScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val modDao = remember { ModDatabase.getDatabase(context).modDao() }

    // Track which mods have been processed to prevent duplicate work
    val processedMods = remember { mutableStateSetOf<String>() }

    // Collect mods as State
    val mods by modDao.getAllModsFlow().collectAsState(initial = emptyList())

    // Filter mods and update activeMods count
    val filteredMods = remember(mods, modList) {
        val filtered = mods.filter { mod ->
            mod.onLists.contains(modList)
        }
        activeMods = filtered.size // Update the count whenever filtered list changes
        filtered
    }

    // Single refresh on first load
    LaunchedEffect(Unit) {
        scope.launch {
            mods.forEach { mod ->
                if (!processedMods.contains(mod.slug)) {
                    val currentUnixTimestamp = Instant.now().epochSecond

                    val updatedDownloadInfoList = mod.downloadInfo.map { info ->
                        val expiresTimestamp = info.directDownload?.let { extractExpiresValue(it) }
                        val isExpired = info.directDownload.isNullOrEmpty() ||
                                (expiresTimestamp != null && expiresTimestamp < currentUnixTimestamp)

                        if (isExpired && info.nexusFileId != null) {
                            try {
                                val updatedInfo = withContext(Dispatchers.IO) {
                                    getDirectDownload(context, mod, info)
                                }
                                Log.d("Refresh", "Updated download info for ${mod.slug}")
                                updatedInfo
                            } catch (e: Exception) {
                                Log.e("Refresh", "Failed to refresh ${mod.slug}", e)
                                info // fallback to original
                            }
                        } else {
                            info
                        }
                    }

                    val updatedMod = mod.copy(downloadInfo = updatedDownloadInfoList)
                    modDao.updateMod(updatedMod)
                    processedMods.add(mod.slug)
                    Log.d("Refresh", "Successfully refreshed all downloads for ${mod.slug}")
                }
            }
        }
    }

    Column {
        LazyColumn {
            items(
                count = filteredMods.size,
                key = { index -> filteredMods[index].slug }
            ) { index ->
                ModItem(
                    mod = filteredMods[index],
                    onDelete = { scope.launch { modDao.deleteMod(filteredMods[index]) } },
                    modDao = modDao
                )
            }
        }
    }
}

@Composable
fun CustomPagerIndicator(
    pagerState: PagerState,
    pageCount: Int,
    modifier: Modifier = Modifier,
    activeColor: Color = Color.White,
    inactiveColor: Color = Color.Gray,
    indicatorSize: Int = 8,
    spacing: Int = 4
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(spacing.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(pageCount) { index ->
            Box(
                modifier = Modifier
                    .size(indicatorSize.dp)
                    .clip(CircleShape)
                    .background(
                        if (pagerState.currentPage == index) activeColor else inactiveColor
                    )
            )
        }
    }
}

fun stripHtml(html: String): String {
    return html.replace(Regex("<[^>]+>"), "")
}

@Composable
fun ModItem(mod: ModDesc, onDelete: () -> Unit, modDao: ModDao) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    var detailsExpanded by remember { mutableStateOf(false) }
    var currentMod by remember { mutableStateOf(mod) }
    val pagerState = rememberPagerState(pageCount = { currentMod.galleryUrls.size })
    val downloadingMap = remember { mutableStateMapOf<String, Boolean>() }
    val isDownloading = downloadingMap[mod.slug] ?: false
    val currentUnixTimestamp = Instant.now().epochSecond
    val progressText = downloadProgressMap[mod.slug]
    val coroutineScope = rememberCoroutineScope()
    var isLoadingGallery by remember { mutableStateOf(false) }
    var galleryError by remember { mutableStateOf<String?>(null) }
    var selectedImageUrl by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val plainText = stripHtml(mod.usageNotes ?: "")
    val isPremium by loadIsPremiumTier(context).collectAsState(initial = null)
    val currentListDownloads = mod.downloadInfo
        .filter { it.sourceLists.contains(modList) && !it.fileName.isNullOrBlank() }
        .distinctBy { "${it.fileName}|${it.nexusFileId}|${it.directDownload}" }

    LaunchedEffect(expanded) {
        if (expanded) {
            try {
                if (currentMod.url.contains("nexusmods.com")) {
                    try {
                        isLoadingGallery = true
                        val updatedModGal = getUpdatedModGallery(currentMod)
                        modDao.updateMod(updatedModGal)
                        currentMod = updatedModGal
                    } catch (e: Exception) {
                        galleryError = "Failed to load gallery: ${e.message}"
                    } finally {
                        isLoadingGallery = false
                    }

                    val allNexusFileIdsPresent = mod.downloadInfo.all { !it.nexusFileId.isNullOrEmpty() }

                    val updatedInfoList = if (!allNexusFileIdsPresent) {
                        Log.i("GetFileID_Debug", "Started for ${mod.modId}")
                        try {
                            getFileIds(context, mod)
                        } catch (e: Exception) {
                            errorMessage = "Failed to fetch nexusId: ${e.message}"
                            mod.downloadInfo // Fallback to original list
                        }
                    } else {
                        Log.d(
                            "getFileIds",
                            "Nexus file IDs already present: \n${
                                mod.downloadInfo.joinToString(separator = "\n") {
                                    "fileName=${it.fileName ?: "unknown"}, nexusFileId=${it.nexusFileId}"
                                }
                            }"
                        )
                        mod.downloadInfo // No need to process, use original list
                    }

                    // Combine updates for galleryUrls and downloadInfo
                    val updatedMod = mod.copy(
                        downloadInfo = updatedInfoList
                    )
                    modDao.updateMod(updatedMod)
                }

                if (currentMod.url.contains("web.archive.org")) {
                    val lastDigits = extractLastDigits(currentMod.url)

                    val archivedUrl = "https://web.archive.org/cdx/search/cdx?url=mw.modhistory.com/file.php?id=$lastDigits&output=json&sort=descending&limit=1"
                    val updatedUrl = getLatestWaybackUrl(archivedUrl)
                    val cleanedDlUrl = mod.dlUrl?.replace("/files/", "")
                    if (updatedUrl != null) {
                        println("Download link: $updatedUrl")
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
                    }
                }
                if (currentMod.url.contains("https://mega")) {
                    Log.d(
                        "ProcessModList",
                        "Processing mega link: ${mod.url}"
                    )
                    val fileName = mod.url.substringAfterLast('/')
                        .takeIf { it.isNotBlank() }
                        ?: "${mod.slug}.zip"
                    scope.launch(Dispatchers.IO) {
                        val downloader = MegaDownloader()
                        downloader.download(mod.url, File("${Constants.USER_FILE_STORAGE}/OpenMW/", fileName))
                    }


                }
                if (currentMod.url.contains("https://baturin.org")) {
                    Log.d(
                        "ProcessModList",
                        "Processing local file for ${mod.slug}: ${mod.url}"
                    )
                    try {
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
                    }
                }

                if (mod.url.contains("gitlab")) {
                    fetchGitProjectId(mod, modDao)
                }
                if (mod.url.startsWith("/")) {
                    val directDownloadUrl =
                        "https://modding-openmw.com${mod.url}"
                    val fileName = mod.url.substringAfterLast('/')
                        .takeIf { it.isNotBlank() }
                        ?: "${mod.slug}.zip"

                    val updatedDownloadInfo = mod.downloadInfo.map { info ->
                        info.copy(
                            fileName = fileName,
                            directDownload = directDownloadUrl)
                    }

                    val updatedMod = mod.copy(
                        dlUrl = directDownloadUrl,
                        downloadInfo = updatedDownloadInfo
                    )
                    modDao.insertOrUpdateMod(updatedMod)
                }

            } catch (e: Exception) {
                "modItem LaunchedEffect Failed: ${e.message}"
            } finally {
                isLoadingGallery = false
            }
        }
    }

    LaunchedEffect(mod.downloadInfo) {
        if (mod.downloadInfo.size > 1) {
            println("Duplicate check for ${mod.slug}:")
            mod.downloadInfo.groupBy { it.fileName }
                .filter { it.value.size > 1 }
                .forEach { (name, items) ->
                    println("'$name' appears ${items.size} times")
                }
        }
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(2.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (expanded) Color.DarkGray else customColor,
        )
    ) {
        Column(modifier = Modifier.padding(4.dp)) {
            // Basic Info Section (Always visible)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = mod.name.ifEmpty { "Unnamed Mod" },
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                val isNexusMod = mod.url.contains("nexusmods.com")
                val hasValidDownload = mod.downloadInfo.any { info ->
                    val directDownload = info.directDownload
                    val expiresTimestamp = directDownload?.let { extractExpiresValue(it) } ?: Long.MAX_VALUE
                    val isExpired = directDownload.isNullOrEmpty() || expiresTimestamp < currentUnixTimestamp
                    !directDownload.isNullOrEmpty() && !isExpired
                }
                if ((!isNexusMod && hasValidDownload) || (isNexusMod && hasValidDownload)) {
                    val fileExists = remember(mod.downloadInfo) {
                        mod.downloadInfo.any { info ->
                            val destination = File("${Constants.USER_FILE_STORAGE}/OpenMW/CACHE", "${info.fileName}")
                            destination.exists()
                        }
                    }
                    val folderExists = remember(mod.downloadInfo) {
                        mod.downloadInfo.any { info ->
                            val extractFolder = File("${Constants.USER_FILE_STORAGE}/OpenMW/${modList}/${mod.category}", info.extractTo)
                            extractFolder.exists() && extractFolder.listFiles()?.isNotEmpty() == true
                        }
                    }
                    if (fileExists && !folderExists) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            tint = gold,
                            contentDescription = "File downloaded",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    if (folderExists) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "File Extracted",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Badge(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Text("Ready")
                    }
                }
                IconButton(
                    onClick = { expanded = !expanded },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand"
                    )
                }
            }

            // Expanded Details Section
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = "Mod Id: ${mod.modId.ifEmpty { "Mod ID Not Found" }}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    HtmlText(
                        html = mod.author.ifEmpty { "Unknown author" },
                        style = MaterialTheme.typography.bodySmall.copy(
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    )
                    InfoRow(label = "URL", value = mod.url, isLink = true)
                    //Log.d("URL_Debug", mod.url)
                    Text(
                        text = mod.description.ifEmpty { "No description available" },
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )

                    InfoRow(label = "Category", value = mod.category)
                    InfoRow(label = "Directory", value = mod.dir)
                    InfoRow(label = "Direct URL", value = mod.dlUrl, isLink = true)
                    InfoRow(label = "Usage Notes", value = plainText)
                    InfoRow(label = "Plugins", value = mod.plugins.joinToString(", "))

                    // Download Info Section
                    Text(
                        text = "Download Info",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    if (mod.downloadInfo.isEmpty()) {
                        Text(
                            text = "None",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Column {
                            Row {
                                Text(
                                    text = "Show more details",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                IconButton(
                                    onClick = { detailsExpanded = !detailsExpanded },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        if (detailsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = if (detailsExpanded) "Collapse" else "Expand"
                                    )
                                }
                            }
                            if (detailsExpanded) {
                                InfoRow(label = "Slug", value = mod.slug)
                                InfoRow(label = "Compatibility", value = mod.compat.toString())
                                InfoRow(label = "Date Added", value = mod.dateAdded)
                                InfoRow(label = "Last Updated", value = mod.dateUpdated)
                                InfoRow(label = "ProjectId", value = mod.gitlabProjectId)
                                InfoRow(label = "PackageId", value = mod.gitlabPackageId)
                                InfoRow(
                                    label = "Data Paths",
                                    value = if (mod.dataPaths.isNotEmpty()) mod.dataPaths.joinToString(
                                        ", "
                                    ) else "Empty"
                                )
                                InfoRow(
                                    label = "On Lists",
                                    value = mod.onLists
                                        .filterNot { it.contains("-wip", ignoreCase = true) }
                                        .joinToString(", ") { it.ifEmpty { "None" } }
                                        .ifEmpty { "None" }
                                )
                            }

                            // Render current list downloads with details
                            if (currentListDownloads.isEmpty()) {
                                Text(
                                    text = "No download info available for $modList",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                currentListDownloads.forEach { info ->
                                    Text(
                                        text = "• ${info.fileName ?: "Unknown"} ✓",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )

                                    info.fileSize?.let {
                                        Text(
                                            text = "Filesize: $it kb",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(start = 16.dp)
                                        )
                                    }
                                    info.extractTo?.let {
                                        Text(
                                            text = "Extract To: $it",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(start = 16.dp)
                                        )
                                    }
                                    info.nexusFileId?.let {
                                        Text(
                                            text = "Nexus File ID: $it",
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.padding(start = 16.dp)
                                        )
                                    }


                                }
                            }


                        }
                    }



                    // Modified to use forEach with downloadInfo for multiple directDownloads
                    if (isPremium == true && mod.url.contains("nexusmods.com")) {
                        currentListDownloads.forEach { info ->
                            val expiresTimestamp = info.directDownload?.let { extractExpiresValue(it) }
                            val isExpired = info.directDownload.isNullOrEmpty() ||
                                    (expiresTimestamp != null && expiresTimestamp < currentUnixTimestamp)
                            if (!info.directDownload.isNullOrEmpty()) {
                                InfoRow(
                                    label = "Time until expires",
                                    value = info.directDownload.let { downloadUrl ->
                                        val expiresTimestamp = extractExpiresValue(downloadUrl)
                                        if (expiresTimestamp == null || expiresTimestamp < currentUnixTimestamp) "Expired"
                                        else formatRemainingTime(
                                            expiresTimestamp,
                                            currentUnixTimestamp
                                        )
                                    },
                                    isLink = false
                                )
                                InfoRow(
                                    label = "Direct Download",
                                    value = if (isExpired) "Expired" else info.directDownload,
                                    isLink = !isExpired && info.directDownload.isNotEmpty(),
                                    fileName = info.fileName
                                )
                            }
                            if (isPremium == true && isExpired) {
                                Button(
                                    onClick = {
                                        isLoading = true
                                        errorMessage = null
                                        coroutineScope.launch {
                                            try {
                                                if (mod.url.contains("nexusmods.com")) {
                                                    val currentUnixTimestamp = Instant.now().epochSecond

                                                    val updatedDownloadInfoList =
                                                        mod.downloadInfo.map { info ->

                                                            val expiresTimestamp =
                                                                info.directDownload?.let {
                                                                    extractExpiresValue(it)
                                                                } ?: 0
                                                            val isExpired =
                                                                info.directDownload.isNullOrEmpty() || expiresTimestamp < currentUnixTimestamp

                                                            if (isExpired && info.nexusFileId != null) {
                                                                try {
                                                                    val updatedInfo =
                                                                        withContext(Dispatchers.IO) {
                                                                            getDirectDownload(
                                                                                context,
                                                                                mod,
                                                                                info
                                                                            )
                                                                        }
                                                                    Log.d(
                                                                        "Refresh",
                                                                        "Updated download info for ${mod.slug}"
                                                                    )
                                                                    updatedInfo
                                                                } catch (e: Exception) {
                                                                    errorMessage =
                                                                        "Failed to fetch Direct Download Link: ${e.message}"
                                                                    info // fallback to original
                                                                }
                                                            } else {
                                                                info // not expired
                                                            }
                                                        }
                                                    val updatedMod =
                                                        mod.copy(downloadInfo = updatedDownloadInfoList)
                                                    val modDao =
                                                        ModDatabase.getDatabase(context).modDao()
                                                    modDao.insertOrUpdateMod(updatedMod)
                                                    Log.d(
                                                        "Refresh",
                                                        "Saved updated mod ${mod.slug} with refreshed downloadInfo list"
                                                    )
                                                }

                                                if (mod.url.contains("gitlab")) {
                                                    fetchGitProjectId(mod, modDao)
                                                }
                                                // Direct Handler
                                                if (mod.url.startsWith("/")) {
                                                    val directDownloadUrl =
                                                        "https://modding-openmw.com${mod.url}"
                                                    val fileName = mod.url.substringAfterLast('/')
                                                        .takeIf { it.isNotBlank() } ?: "${mod.slug}.zip"

                                                    val updatedDownloadInfo =
                                                        mod.downloadInfo.map { info ->
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
                                                }
                                            } catch (e: Exception) {
                                                errorMessage = "Failed to refresh: ${e.message}"
                                                Log.e("Refresh", "Error refreshing link", e)
                                            } finally {
                                                isLoading = false
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = customColor, // Background color
                                        contentColor = Color.White   // Text color
                                    ),
                                    enabled = !isLoading,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    if (isLoading) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp
                                        )
                                    } else {
                                        Text(
                                            text = "Refresh Download Links",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                    }
                                }
                                errorMessage?.let { message ->
                                    Text(
                                        text = message,
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                        if (currentListDownloads.none { !it.directDownload.isNullOrEmpty() }) {
                            InfoRow(
                                label = "Time until expires",
                                value = "Unavailable",
                                isLink = false
                            )
                            InfoRow(
                                label = "Direct Download",
                                value = "Unavailable",
                                isLink = false,
                                fileName = null
                            )
                        }
                    }
                    if (isLoadingGallery) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    } else if (galleryError != null) {
                        Text(
                            text = galleryError ?: "Unknown error",
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    } else if (currentMod.galleryUrls.isNotEmpty()) {
                        Column {
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            ) { page ->
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AsyncImage(
                                        model = currentMod.galleryUrls[page],
                                        contentDescription = "Mod Image",
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(180.dp)
                                            .clickable {
                                                selectedImageUrl = currentMod.galleryUrls[page]
                                            }
                                    )
                                    val imageName =
                                        currentMod.galleryUrls[page].substringAfterLast("/")
                                    InfoRow(label = "Image Name", value = imageName)
                                }
                            }
                            CustomPagerIndicator(
                                pagerState = pagerState,
                                pageCount = currentMod.galleryUrls.size,
                                modifier = Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(16.dp)
                            )
                        }
                    } else {
                        Text(
                            text = "No gallery images available",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    // Tags Section
                    if (mod.tags.isNotEmpty()) {
                        FlowRow(
                            modifier = Modifier.padding(top = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            mod.tags.forEach { tag ->
                                SuggestionChip(
                                    onClick = {},
                                    label = { Text(tag) }
                                )
                            }
                        }
                    }

                    // Action Buttons
                    Column(
                        modifier = Modifier.padding(top = 16.dp)
                    ) {
                        currentListDownloads.forEach { info ->
                            val archiveFile = File("${Constants.USER_FILE_STORAGE}/OpenMW/CACHE/${info.fileName}")
                            val filePath = File(
                                "${Constants.USER_FILE_STORAGE}/OpenMW/CACHE",
                                info.fileName ?: "${mod.slug}.zip"
                            )
                            val fileExists = filePath.exists()
                            if (!info.directDownload.isNullOrBlank() || isPremium == false) {
                                HorizontalDivider(color = White, thickness = 1.dp)
                                Text(
                                    text = "${info.fileName}",
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.labelMedium
                                )
                                if (fileExists && info.extractedSizeBytes != null) {
                                    val sizeKb = info.extractedSizeBytes / 1024
                                    Text(
                                        text = "Extracted archive size: ${formatSize(sizeKb)}",
                                        color = MaterialTheme.colorScheme.secondary,
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }

                                if (isDownloading) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(24.dp),
                                            strokeWidth = 2.dp,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        if (progressText != null) {
                                            Text("Downloading... $progressText")
                                        }
                                    }
                                } else {
                                    if (fileExists) {
                                        // Show both "Downloaded" text and Extract button
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = "Downloaded",
                                                color = MaterialTheme.colorScheme.secondary,
                                                style = MaterialTheme.typography.labelMedium
                                            )

                                            Spacer(modifier = Modifier.width(8.dp))

                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        val fileList = nativeGetArchiveFileList(archiveFile)
                                                        val extractTo = File("${Constants.USER_FILE_STORAGE}/OpenMW/${modList}/${info.extractTo}")

                                                        if (info.extractedSizeBytes == null && fileExists) {
                                                            calculateAndUpdateSizeOnDisk(mod, info)
                                                        }

                                                        /*
                                                        val totalSize = ZipUtils.getArchiveTotalSize(archiveFile)
                                                        Log.i("ArchiveInfo", "Total extracted size: ${ZipUtils.formatFileSize(totalSize)}")
                                                         */

                                                        if (shouldSkipExtraction(fileList, extractTo)) {
                                                            Log.i("ExtractionDecision", "Skipping extraction for ${info.fileName}")
                                                        } else {
                                                            extractModFile(mod, info)
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = customColor, // Background color
                                                    contentColor = Color.White   // Text color
                                                )
                                            ) {
                                                Text("Extract")
                                            }
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        val fileName = info.fileName ?: "${mod.slug}.zip"
                                                        val destination = File(
                                                            "${Constants.USER_FILE_STORAGE}/OpenMW/CACHE",
                                                            fileName
                                                        )

                                                        if (destination.exists()) {
                                                            val deleted = destination.delete()
                                                            if (deleted) {
                                                                // Update UI state to reflect deletion
                                                                // You might want to trigger a recomposition or update a state variable
                                                                Log.i("FileDelete", "Successfully deleted: ${destination.absolutePath}")
                                                            } else {
                                                                Log.e("FileDelete", "Failed to delete: ${destination.absolutePath}")
                                                            }
                                                        }
                                                    }
                                                },
                                                colors = ButtonDefaults.buttonColors(
                                                    containerColor = Color.Red, // Red color for delete action
                                                    contentColor = Color.White
                                                )
                                            ) {
                                                Text("Delete")
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                scope.launch {
                                                    if (mod.url.contains("nexusmods.com")) {
                                                        if (isPremium == true) {
                                                            downloadModFile(mod, info)
                                                        } else {
                                                            NexusInfo.openWebView(
                                                                url = "https://www.nexusmods.com/morrowind/mods/${mod.modId}?tab=files&file_id=${info.nexusFileId}",
                                                                fileName = "${info.fileName}",
                                                                id = mod.modId
                                                            )
                                                        }
                                                    } else {
                                                        downloadModFile(mod, info)
                                                    }
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = customColor, // Background color
                                                contentColor = Color.White   // Text color
                                            )
                                        ) {
                                            Text("Download")
                                        }
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = White, thickness = 1.dp)
                        Button(
                            onClick = onDelete,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = customColor, // Background color
                                contentColor = Color.White   // Text color
                            )
                        ) {
                            Text("Remove mod from list")
                        }
                    }
                }
            }
        }
    }

    // Fullscreen Image Dialog
    selectedImageUrl?.let { imageUrl ->
        Dialog(
            onDismissRequest = { selectedImageUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false) // Fullscreen dialog
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.9f)) // Semi-transparent black background
                    .clickable(
                        onClick = { selectedImageUrl = null }, // Dismiss on background click
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Fullscreen Mod Image",
                    contentScale = ContentScale.Fit, // Fit to screen without cropping
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp) // Optional padding for better appearance
                )
                // Optional Close Button
                IconButton(
                    onClick = { selectedImageUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(48.dp)
                        .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Color.White
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionItem(action: Action) {
    val actionText = when (action) {
        is Action.CopyAction -> "Copy: ${action.source} → ${action.destination}"
        is Action.RenameAction -> "Rename: ${action.oldName} → ${action.newName}"
        is Action.RemoveAction -> "Remove: ${action.path}"
        is Action.CleanAction -> "Clean: ${action.path}"
    }

    Text(
        text = "• $actionText",
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(start = 8.dp)
    )
}

@Composable
fun InfoRow(label: String, value: String?, isLink: Boolean = false, fileName: String? = null) {
    if (value.isNullOrEmpty()) return
    val context = LocalContext.current

    Row(
        modifier = Modifier.padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
        )
        if (isLink) {
            ClickableText(
                text = AnnotatedString(value),
                onClick = {
                    try {
                        // Use DownloadManager for direct file downloads
                        val request = DownloadManager.Request(value.toUri())
                        request.setTitle("$label Download")
                        request.setDescription("Downloading $label")
                        request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                        // Use provided fileName for "Direct Download", otherwise fallback to generic name
                        val destinationFileName = if (label == "Direct Download" && !fileName.isNullOrEmpty()) {
                            fileName
                        } else {
                            "mod_${System.currentTimeMillis()}.zip"
                        }
                        // Set destination to cache directory
                        val cacheDir = File(Constants.CACHE_DIR)
                        if (!cacheDir.exists()) {
                            cacheDir.mkdirs() // Ensure cache directory exists
                        }
                        val destinationFile = File(cacheDir, destinationFileName)
                        request.setDestinationUri(Uri.fromFile(destinationFile))
                        request.setAllowedNetworkTypes(
                            DownloadManager.Request.NETWORK_WIFI or DownloadManager.Request.NETWORK_MOBILE
                        )

                        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
                        downloadManager.enqueue(request)
                    } catch (e: Exception) {
                        // Fallback to opening URL in browser if DownloadManager fails
                        Toast.makeText(context, "Failed to start download: ${e.message}", Toast.LENGTH_SHORT).show()
                        val intent = Intent(Intent.ACTION_VIEW, value.toUri())
                        context.startActivity(intent)
                    }
                },
                style = MaterialTheme.typography.bodySmall.copy(
                    color = MaterialTheme.colorScheme.primary
                )
            )
        } else {
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

// HTML Text Composable (from previous implementation)
@Composable
fun HtmlText(html: String, style: TextStyle) {
    AndroidView(
        factory = { context ->
            TextView(context).apply {
                setTextAppearance(context, android.R.style.TextAppearance)
                movementMethod = LinkMovementMethod.getInstance()
            }
        },
        update = { tv ->
            tv.text = Html.fromHtml(html, Html.FROM_HTML_MODE_COMPACT)
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.fontSize.value)
            tv.setTextColor(style.color.toArgb())
        }
    )
}

fun shouldSkipExtraction(archiveFiles: List<ZipUtils.ArchiveFile>, targetFolder: File): Boolean {
    archiveFiles.forEach { archiveFile ->
        if (archiveFile.isFolder) return@forEach // Skip folders

        val targetFile = File(targetFolder, archiveFile.path)
        if (!targetFile.exists()) {
            Log.i("ExtractionCheck", "Missing file: ${archiveFile.path}")
            return false
        }

        if (targetFile.length() != archiveFile.size) {
            Log.i("ExtractionCheck", "Size mismatch: ${archiveFile.path}")
            return false
        }
    }

    Log.i("ExtractionCheck", "All files match — skipping extraction")
    return true
}


fun formatSize(kb: Long): String {
    return when {
        //kb >= 1024 * 1024 -> "${kb / (1024 * 1024)} GB"
        kb >= 1024 -> "${kb / 1024} MB"
        else -> "$kb KB"
    }
}
