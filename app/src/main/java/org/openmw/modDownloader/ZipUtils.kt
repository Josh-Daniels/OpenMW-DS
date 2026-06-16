package org.openmw.modDownloader

import android.util.Log
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.sf.sevenzipjbinding.ArchiveFormat
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.ICryptoGetTextPassword
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZip.getSevenZipVersion
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import okhttp3.internal.closeQuietly
import org.openmw.Constants
import org.openmw.modDownloader.ModListManager.modList
import org.openmw.modDownloader.ModListManager.remainingMods
import org.openmw.modDownloader.NexusInfo.extractionProgressMap
import org.openmw.modDownloader.NexusInfo.extractionProgressPercentMap
import org.openmw.modDownloader.ZipUtils.nativeExtract
import org.openmw.ui.view.addCustomLog
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun appendToExtractLog(message: String) {
    try {
        val logFile = File("${Constants.USER_FILE_STORAGE}/OpenMW/Extraction_$modList.log")
        val timestamp = LocalDateTime.now()
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss", Locale.getDefault()))
        val logEntry = "[$timestamp] $message\n"

        logFile.parentFile?.mkdirs()
        logFile.appendText(logEntry)
    } catch (e: Exception) {
        Log.e("ExtractLog", "Failed to write to log file: ${e.message}")
    }
}

object SevenZip {
    private const val TAG = "7-Zip-JBinding-4Android"

    fun isInitialized() {
        val version = getSevenZipVersion()
        Log.i(TAG, "7-zip version: ${version.major}.${version.minor}.${version.build} (${version.version}), ${version.date}${version.copyright}")
        Log.i(TAG, "7-Zip-JBinding version: ${SevenZip.getSevenZipJBindingVersion()}")
        Log.i(TAG, "Native library initialized: ${SevenZip.isInitializedSuccessfully()}")
    }
}

suspend fun extractModFile(mod: ModDesc, info: DownloadInfo) {
    withContext(Dispatchers.IO) {
        appendToExtractLog("Extracting: ${info.fileName}")
        try {
            getSevenZipVersion()
            val extractTo = File(
                "${Constants.USER_FILE_STORAGE}/OpenMW/Mods",
                "${mod.category}/${info.extractTo}"
            )

            val destination = File(
                "${Constants.USER_FILE_STORAGE}/OpenMW/CACHE",
                "${info.fileName}"
            )

            val randomAccessFile = RandomAccessFile(destination, "r")
            val inStream = RandomAccessFileInStream(randomAccessFile)
            val inArchive = SevenZip.openInArchive(null, inStream) // Auto-detect format
            val detectedFormat = inArchive.archiveFormat
            Log.i("ArchiveDetect", "Detected format: ${detectedFormat.methodName}")
            inArchive.close()
            inStream.close()

            var totalFiles = 0
            var processedFiles = 0
            nativeExtract(
                pathFrom = destination.absolutePath,
                pathTo = extractTo.absolutePath,
                archiveFormat = detectedFormat,
                listener = object : ZipUtils.NameProgressListener {
                    override fun onStart() {
                        extractionProgressMap[mod.slug] = "Starting extraction for ${mod.name}"
                        extractionProgressPercentMap[mod.slug] = 0f
                    }

                    override fun onGetFileNum(num: Int) {
                        totalFiles = num
                        extractionProgressMap[mod.slug] = "Extracting ${mod.name} ($num files)"
                    }

                    override fun onProgressUpdate(item: String, fileSize: Long) {
                        processedFiles++
                        val progressPercent = if (totalFiles > 0) (processedFiles * 100f) / totalFiles else 0f
                        extractionProgressMap[mod.slug] = "Extracting ${mod.name}: $item"
                        extractionProgressPercentMap[mod.slug] = progressPercent
                    }

                    override fun onError(message: String) {
                        extractionProgressMap[mod.slug] = "Extraction error for ${mod.name}: $message"
                        extractionProgressPercentMap[mod.slug] = 0f
                        Log.e(
                            "ProcessModList",
                            "Extraction error for ${mod.slug}: $message"
                        )
                    }

                    override fun onCompleted() {
                        extractionProgressMap[mod.slug] = "${mod.name} Extracted"
                        extractionProgressPercentMap[mod.slug] = 100f

                        remainingMods--
                    }
                }
            )
        } catch (e: Exception) {
            Log.e(
                "ProcessModList",
                "Failed to extract file for ${mod.slug}: ${e.message}",
                e
            )
            extractionProgressMap[mod.slug] = "Failed to extract ${mod.name}"
            extractionProgressPercentMap[mod.slug] = 0f
        }
    }
}

object ZipUtils {

    private const val TAG = "ZipUtils"

    fun isArchiveFullyExtracted(
        inArchive: IInArchive,
        outputDir: File,
        pathFrom: String
    ): Boolean {
        for (i in 0 until inArchive.numberOfItems) {
            val itemPath = inArchive.getProperty(i, PropID.PATH) as String
            val fileSize = inArchive.getProperty(i, PropID.SIZE) as? Long ?: continue
            val isDirectory = inArchive.getProperty(i, PropID.IS_FOLDER) as Boolean

            val outputPath = if (pathFrom.contains("-master", ignoreCase = true)) {
                itemPath.split("/").drop(1).joinToString("/")
            } else {
                itemPath
            }

            val outputFile = File(outputDir, outputPath)

            if (isDirectory) {
                if (!outputFile.exists() || !outputFile.isDirectory) return false
            } else {
                if (!outputFile.exists() || outputFile.length() != fileSize) return false
            }
        }
        return true
    }

    interface NameProgressListener {
        fun onStart()
        fun onGetFileNum(num: Int)
        fun onProgressUpdate(fileName: String, fileSize: Long)
        fun onCompleted()
        fun onError(error: String)
    }

    data class ArchiveFile(
        val path: String,
        val name: String,
        val extension: String,
        val isFolder: Boolean,
        val size: Long,
        val encrypted: Boolean
    )

    fun nativeGetArchiveFileList(file: File): List<ArchiveFile> {
        val fileList = mutableListOf<ArchiveFile>()
        var inStream: RandomAccessFileInStream? = null
        var inArchive: IInArchive? = null

        try {
            val randomAccessFile = RandomAccessFile(file, "r")
            inStream = RandomAccessFileInStream(randomAccessFile)

            // Auto-detect format
            inArchive = SevenZip.openInArchive(null, inStream)
            val format = inArchive.archiveFormat
            Log.i("ArchiveRead", "Detected format: ${format.methodName}")

            val itemCount = inArchive.numberOfItems
            Log.i("ArchiveRead", "Items in archive: $itemCount")

            for (i in 0 until itemCount) {
                val path = inArchive.getStringProperty(i, PropID.PATH) ?: continue
                val name = inArchive.getStringProperty(i, PropID.NAME) ?: ""
                val extension = inArchive.getStringProperty(i, PropID.EXTENSION) ?: ""
                val isFolder = inArchive.getProperty(i, PropID.IS_FOLDER) as? Boolean ?: false
                val size = (inArchive.getProperty(i, PropID.SIZE) as? Long) ?: 0L
                val encrypted = inArchive.getProperty(i, PropID.ENCRYPTED) as? Boolean ?: false

                fileList.add(
                    ArchiveFile(
                        path = path,
                        name = name,
                        extension = extension,
                        isFolder = isFolder,
                        size = size,
                        encrypted = encrypted
                    )
                )
            }
        } catch (e: Exception) {
            Log.e("ArchiveRead", "Error reading archive: ${e.message}")
            e.printStackTrace()
        } finally {
            try {
                inArchive?.close()
            } catch (e: Exception) {
                Log.e("ArchiveRead", "Failed to close archive: ${e.message}")
            }
            try {
                inStream?.close()
            } catch (e: Exception) {
                Log.e("ArchiveRead", "Failed to close stream: ${e.message}")
            }
        }

        return fileList
    }

    fun nativeExtract(
        pathFrom: String,
        pathTo: String,
        archiveFormat: ArchiveFormat = ArchiveFormat.SEVEN_ZIP,
        listener: NameProgressListener
    ) {
        val fileFrom = File(pathFrom)
        val outputDir = File(pathTo)
        Log.i(TAG, "Starting extraction: $pathFrom -> $pathTo")
        listener.onStart()
        try {
            val inStream = RandomAccessFileInStream(RandomAccessFile(fileFrom, "r"))
            val inArchive = SevenZip.openInArchive(archiveFormat, inStream)
            if (isArchiveFullyExtracted(inArchive, outputDir, pathFrom)) {
                Log.i(TAG, "Archive already fully extracted. Skipping.")
                listener.onCompleted()
                inArchive.close()
                inStream.close()
                return
            }

            listener.onGetFileNum(inArchive.numberOfItems)

            val extractCallback = object : IArchiveExtractCallback {
                private var currentFile: File? = null
                private var fileOutputStream: OutputStream? = null

                val outputCallback = ISequentialOutStream { data ->
                    // Log.i(TAG, "write size ${data.size} to ${currentFile}")
                    fileOutputStream?.write(data)
                    return@ISequentialOutStream data.size
                }

                override fun getStream(
                    index: Int,
                    extractAskMode: ExtractAskMode
                ): ISequentialOutStream? {
                    val item = inArchive.getProperty(index, PropID.PATH) as String
                    val fileSize = inArchive.getProperty(index, PropID.SIZE) as Long
                    val isDirectory = inArchive.getProperty(index, PropID.IS_FOLDER) as Boolean
                    // Strip top-level folder if pathFrom contains "-master"
                    val outputPath = if (pathFrom.contains("-master", ignoreCase = true)) {
                        item.split("/").drop(1).joinToString("/")
                    } else {
                        item
                    }

                    val outputFile = File(outputDir, outputPath)
                    if (isDirectory) {
                        return null
                    }
                    listener.onProgressUpdate(item, fileSize)
                    outputFile.parentFile?.mkdirs()
                    currentFile = outputFile
                    fileOutputStream = outputFile.outputStream().buffered()
                    return outputCallback
                }

                override fun prepareOperation(extractAskMode: ExtractAskMode) {
                    //Log.i(TAG, "Preparing extraction: $extractAskMode")
                }

                override fun setOperationResult(extractOperationResult: ExtractOperationResult) {
                    /*
                    Log.i(
                        TAG,
                        "Extraction result: $extractOperationResult for ${currentFile?.absolutePath}"
                    )
                     */
                    addCustomLog("Extraction result: $extractOperationResult for ${currentFile?.absolutePath}", textSize = 10, textColor = Color.Green)
                    fileOutputStream?.closeQuietly()
                    if (extractOperationResult != ExtractOperationResult.OK) {
                        listener.onError("Extraction failed: $extractOperationResult")
                    }
                }

                override fun setTotal(total: Long) {
                    Log.i(TAG, "Total extraction size: $total bytes")
                }

                override fun setCompleted(complete: Long) {
                    // Log.i(TAG, "Completed extraction: $complete bytes")
                }
            }

            inArchive.extract(null, false, extractCallback)

            inArchive.close()
            inStream.close()

            listener.onCompleted()

            Log.i(TAG, "Extraction finished successfully.")
        } catch (e: FileNotFoundException) {
            listener.onError(e.message ?: "Unknown error")
            Log.e(TAG, "File not found: ${e.message}")
            e.printStackTrace()
        } catch (e: SevenZipException) {
            listener.onError(e.message ?: "Unknown error")
            Log.e(TAG, "7-Zip error: ${e.message}")
            e.printStackTrace()
        } catch (e: IOException) {
            listener.onError(e.message ?: "Unknown error")
            Log.e(TAG, "IO error: ${e.message}")
            e.printStackTrace()
        }
    }
}

fun getTotalExtractedSize(archiveFile: File): Long {
    var totalSize: Long = 0
    var inStream: RandomAccessFileInStream? = null
    var inArchive: IInArchive? = null

    try {
        val randomAccessFile = RandomAccessFile(archiveFile, "r")
        inStream = RandomAccessFileInStream(randomAccessFile)
        inArchive = SevenZip.openInArchive(null, inStream)

        val itemCount = inArchive.numberOfItems
        for (i in 0 until itemCount) {
            val isDirectory = inArchive.getProperty(i, PropID.IS_FOLDER) as? Boolean ?: false
            if (!isDirectory) {
                val size = (inArchive.getProperty(i, PropID.SIZE) as? Long) ?: 0L
                totalSize += size
            }
        }
    } catch (e: Exception) {
        Log.e("SizeCalculation", "Error calculating total size: ${e.message}")
    } finally {
        try {
            inArchive?.close()
        } catch (e: Exception) {
            Log.e("SizeCalculation", "Failed to close archive: ${e.message}")
        }
        try {
            inStream?.close()
        } catch (e: Exception) {
            Log.e("SizeCalculation", "Failed to close stream: ${e.message}")
        }
    }

    return totalSize
}
