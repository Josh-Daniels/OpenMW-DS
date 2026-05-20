package org.openmw.modDownloader

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.lingala.zip4j.ZipFile
import net.lingala.zip4j.exception.ZipException
import net.lingala.zip4j.progress.ProgressMonitor
import net.sf.sevenzipjbinding.ArchiveFormat
import net.sf.sevenzipjbinding.ExtractAskMode
import net.sf.sevenzipjbinding.ExtractOperationResult
import net.sf.sevenzipjbinding.IArchiveExtractCallback
import net.sf.sevenzipjbinding.IArchiveOpenCallback
import net.sf.sevenzipjbinding.ICryptoGetTextPassword
import net.sf.sevenzipjbinding.IInArchive
import net.sf.sevenzipjbinding.ISequentialOutStream
import net.sf.sevenzipjbinding.PropID
import net.sf.sevenzipjbinding.SevenZip
import net.sf.sevenzipjbinding.SevenZip.getSevenZipVersion
import net.sf.sevenzipjbinding.SevenZipException
import net.sf.sevenzipjbinding.impl.RandomAccessFileInStream
import okhttp3.internal.closeQuietly
import org.apache.commons.compress.archivers.sevenz.SevenZArchiveEntry
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import org.openmw.Constants
import org.openmw.modDownloader.ModListManager.modList
import org.openmw.modDownloader.ModListManager.remainingMods
import org.openmw.modDownloader.NexusInfo.extractionProgressMap
import org.openmw.modDownloader.NexusInfo.extractionProgressPercentMap
import org.openmw.modDownloader.ZipUtils.nativeExtract
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Date

fun appendToExtractLog(message: String) {
    try {
        val logFile = File("${Constants.USER_FILE_STORAGE}/OpenMW/Extraction_$modList.log")
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
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



    interface ProgressListener {
        fun onProgressUpdate(percent: Int)
        fun onCompleted()
        fun onError(error: String)
    }

    interface NameProgressListener {
        fun onStart()
        fun onGetFileNum(num: Int)
        fun onProgressUpdate(fileName: String, fileSize: Long)
        fun onCompleted()
        fun onError(error: String)
    }

    fun getArchiveTotalSize(archiveFile: File): Long {
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
            Log.e(TAG, "Error calculating total size: ${e.message}")
        } finally {
            try {
                inArchive?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to close archive: ${e.message}")
            }
            try {
                inStream?.close()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to close stream: ${e.message}")
            }
        }

        return totalSize
    }

    // Helper function to format file size for display
    fun formatFileSize(size: Long): String {
        return when {
            size < 1024 -> "$size B"
            size < 1024 * 1024 -> "${String.format("%.1f", size / 1024.0)} KB"
            size < 1024 * 1024 * 1024 -> "${String.format("%.1f", size / (1024.0 * 1024.0))} MB"
            else -> "${String.format("%.1f", size / (1024.0 * 1024.0 * 1024.0))} GB"
        }
    }

    /**
     * 解压 Zip
     */
    fun unZip(
        pathFrom: String,
        pathTo: String,
        listener: ProgressListener
    ) {
        try {
            val zipFile = ZipFile(pathFrom)
            zipFile.charset = StandardCharsets.UTF_8
            zipFile.isRunInThread = true
            zipFile.extractAll(pathTo)

            val progressMonitor = zipFile.progressMonitor // 解压进度监听
            while (!progressMonitor.state.equals(ProgressMonitor.State.READY)) {
                listener.onProgressUpdate(progressMonitor.percentDone) // 更新解压进度
                Thread.sleep(100)
            }

            listener.onCompleted() // 完成解压
        } catch (e: ZipException) {
            listener.onError("解压失败: ${e.message}")
        } catch (e: InterruptedException) {
            listener.onError("解压失败: ${e.message}")
        } catch (e: Error) {
            listener.onError("解压失败: ${e.message}")
        }
    }


    /**
     * 解压 7Zip Apache Common 库
     */
    fun sevenUnZip(
        pathFrom: String,
        pathTo: String,
        listener: ProgressListener
    ) {
        val file = File(pathFrom)
        val sevenZFile = SevenZFile(file)
        var totalSize: Long = 0
        var extractedSize: Long = 0

        try {
            // 计算总大小
            totalSize =
                sevenZFile.entries.asSequence().filterNot { it.isDirectory }.sumOf { it.size }

            // 遍历所有条目
            var entry: SevenZArchiveEntry?
            while (sevenZFile.nextEntry.also { entry = it } != null) {
                entry?.let { currentEntry ->
                    if (currentEntry.isDirectory) return@let

                    // 创建输出文件及父目录
                    val outputFile = File(pathTo, currentEntry.name)
                    outputFile.parentFile?.mkdirs()

                    val bufferSize = 1024 * 1024 // 搞大一些，加快解压速度
                    val buffer = ByteArray(bufferSize)
                    // 写入文件
                    FileOutputStream(outputFile).use { fileOutputStream ->
                        BufferedOutputStream(
                            fileOutputStream,
                            bufferSize
                        ).use { out -> // 套一层BufferedStream加速，但还是挺慢的
                            var bytesRead: Int
                            while (sevenZFile.read(buffer).also { bytesRead = it } != -1) {
                                out.write(buffer, 0, bytesRead)
                                extractedSize += bytesRead

                                // 更新进度
                                val progress =
                                    ((extractedSize.toDouble() / totalSize) * 100).toInt()
                                listener.onProgressUpdate(progress)
                            }
                        }
                    }
                }
            }

            // 通知完成
            listener.onCompleted()
        } catch (e: Exception) {
            listener.onError(e.message ?: "解压失败")
        } finally {
            sevenZFile.close()
        }
    }

    fun native7ZipMT(
        pathFrom: String,
        pathTo: String,
        listener: NameProgressListener
    ) {
        nativeExtract(
            pathFrom = pathFrom,
            pathTo = pathTo,
            archiveFormat = ArchiveFormat.SEVEN_ZIP,
            listener = listener
        )
    }

    /*
    val archiveFile = File("/path/to/your/file.7z")
    val fileList = nativeGetArchiveFileList(archiveFile)

    fileList.forEach {
        Log.i("ArchiveContents", "Found: ${it.path} (${it.size} bytes)")
    }
     */

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

                    /*
                    val outputFile = if (strippedPath.isNotEmpty()) {
                        File(outputDir, strippedPath)
                    } else {
                        // Handle root-level files
                        File(outputDir, item)
                    }
                    val outputFile = File(outputDir, item)
                     */



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

    /**
     * ✅ 解压行为说明
     * validRootPath	解压行为
     * ""	                    ✅ 创建新文件夹（使用压缩包文件名），将压缩包全部内容解压进去
     * "aaa"	                ✅ 不创建文件夹，将压缩包全部内容直接解压到目标路径（即原始 extractAll 逻辑）
     * "aaa/bbb/ccc"	        ✅ 解压 bbb/ 文件夹下的所有内容（不含 bbb 本身）到目标
     * "aaa/bbb/ccc/ddd"	    ✅ 解压 ccc/ 文件夹下的所有内容（不含 ccc 本身）到目标
     * "aaa/bbb/ccc/ddd/eee"	✅ 解压 ddd/ 文件夹下的所有内容（不含 ddd 本身）到目标
     *
     */
    fun nativeExtractSelective(
        pathFrom: String,
        pathTo: String,
        validRootPath: String, // "", "aaa", "aaa/bbb", ...
        archiveFormat: ArchiveFormat = ArchiveFormat.SEVEN_ZIP,
        listener: NameProgressListener,
        password: String? = null
    ) {
        val archiveFile = File(pathFrom)
        val baseOutputDir = File(pathTo)
        val pathSegments = validRootPath.split('/').filter { it.isNotBlank() }

        val isRoot = pathSegments.isEmpty()
        val isFlat = pathSegments.size == 1

        // 是否需要包一层压缩包名文件夹
        val outputDir = if (isRoot) {
            File(baseOutputDir, archiveFile.nameWithoutExtension).apply { mkdirs() }
        } else {
            baseOutputDir
        }

        // 当为多级路径时，stripPrefix 代表要被剥离掉的上层路径（保留的那一层以下的内容）
        val stripPrefix = when {
            isRoot -> emptyList()
            isFlat -> null
            else -> pathSegments.dropLast(1)
        }

        listener.onStart()
        try {
            val inStream = RandomAccessFileInStream(RandomAccessFile(archiveFile, "r"))
            val inArchive = SevenZip.openInArchive(archiveFormat, inStream, password)

            listener.onGetFileNum(inArchive.numberOfItems)

            val extractCallback = object : IArchiveExtractCallback, ICryptoGetTextPassword {
                private var currentFile: File? = null
                private var fileOutputStream: OutputStream? = null

                val outputCallback = ISequentialOutStream { data ->
                    fileOutputStream?.write(data)
                    data.size
                }

                override fun getStream(index: Int, mode: ExtractAskMode): ISequentialOutStream? {
                    val fullPath = inArchive.getProperty(index, PropID.PATH) as? String ?: return null
                    val isDirectory = inArchive.getProperty(index, PropID.IS_FOLDER) as? Boolean ?: false
                    val fileSize = inArchive.getProperty(index, PropID.SIZE) as? Long ?: 0L

                    Log.d(TAG, "getStream: fullPath:$fullPath isDirectory:$isDirectory")

                    val normalized = fullPath.replace('\\', '/').trimStart('/')
                    val parts = normalized.split('/')

                    val relativePath = when {
                        isRoot || isFlat -> {
                            // 全量解压
                            normalized
                        }
                        parts.size <= stripPrefix!!.size -> {
                            // 不满足要求，跳过
                            return null
                        }
                        parts.subList(0, stripPrefix.size) == stripPrefix -> {
                            // 剥离指定前缀
                            parts.drop(stripPrefix.size).joinToString("/")
                        }
                        else -> return null
                    }

                    if (relativePath.isBlank() || isDirectory) return null

                    listener.onProgressUpdate(fullPath, fileSize)

                    val outputFile = File(outputDir, relativePath)
                    if (isDirectory) {
                        Log.d(TAG, "getStream: 创建空文件夹")
                        outputFile.mkdirs() // ✅ 空文件夹也创建
                        return null
                    }

                    outputFile.parentFile?.mkdirs()
                    currentFile = outputFile
                    fileOutputStream = outputFile.outputStream().buffered()
                    return outputCallback
                }

                override fun prepareOperation(mode: ExtractAskMode) {}
                override fun setOperationResult(result: ExtractOperationResult) {
                    fileOutputStream?.closeQuietly()
                    if (result != ExtractOperationResult.OK) {
                        listener.onError("Extract failed: $result")
                    }
                }

                override fun setTotal(total: Long) {}
                override fun setCompleted(complete: Long) {}
                override fun cryptoGetTextPassword(): String {
                    return password.orEmpty()
                }
            }

            inArchive.extract(null, false, extractCallback)
            inArchive.close()
            inStream.close()

            listener.onCompleted()
        } catch (e: Exception) {
            listener.onError(e.message ?: "Unknown error")
            Log.e("Extract", "Exception during selective extract", e)
        }
    }

    fun native7Zip(
        pathFrom: String,
        pathTo: String,
        listener: NameProgressListener
    ) {
        // native7Zip3rd(pathFrom, pathTo, listener)
        native7ZipMT(pathFrom, pathTo, listener)
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
