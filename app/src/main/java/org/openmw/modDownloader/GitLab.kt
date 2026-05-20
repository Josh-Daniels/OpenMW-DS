package org.openmw.modDownloader

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.openmw.modDownloader.ModListManager.fetchJsonResponse
import java.net.URI
import kotlin.collections.isNotEmpty

suspend fun fetchGitProjectId(mod: ModDesc, modDao: ModDao): String? = withContext(Dispatchers.IO) {
    try {
        // Extract the project slug from the GitLab Pages URL
        val uri = URI(mod.url)
        val pathSegments = uri.path.split("/").filter { it.isNotBlank() }

        val owner = if (mod.url.contains("modding-openmw")) {
            "modding-openmw"
        } else if (pathSegments.size >= 2) {
            pathSegments[pathSegments.size - 2] // Second to last segment
        } else {
            pathSegments.firstOrNull() ?: ""
        }

        val slug = if (pathSegments.size >= 2) {
            pathSegments.last() // This gets "openmw-third-person-alt-attacks"
        } else {
            pathSegments.firstOrNull() ?: ""
        }

        Log.d("GITLAB Base", "Extracted Segments: $pathSegments")
        Log.d("GITLAB Base", "Extracted Slug: $slug")

        val url = "https://gitlab.com/api/v4/projects/$owner%2F$slug/packages?per_page=100"

        // Add logging for the URL being requested
        Log.d("GITLAB_URL", "Fetching URL: $url")
        NexusInfo.downloadProgressMap[mod.slug] =
            "Fetching URL: $url"

        val jsonString = fetchJsonResponse(url)
        val jsonElement = Json.parseToJsonElement(jsonString)
        Log.d("GITLAB_JSON", "Raw JSON response: $jsonString")

        if (jsonElement is JsonArray && jsonElement.isNotEmpty()) {
            val highestVersionPackage = jsonElement.jsonArray.maxByOrNull { element ->
                // Use created_at timestamp to determine the latest package
                val createdAt = element.jsonObject["created_at"]?.jsonPrimitive?.content

                if (createdAt != null) {
                    try {
                        // Parse ISO 8601 date format: "2026-02-12T18:28:18.334Z"
                        val dateFormat = java.time.format.DateTimeFormatter.ISO_DATE_TIME
                        java.time.OffsetDateTime.parse(createdAt, dateFormat)
                    } catch (e: Exception) {
                        // Fallback to epoch if parsing fails
                        java.time.OffsetDateTime.MIN
                    }
                } else {
                    java.time.OffsetDateTime.MIN
                }
            }

            val projectId = highestVersionPackage
                ?.jsonObject
                ?.get("pipeline")
                ?.jsonObject
                ?.get("project_id")
                ?.jsonPrimitive
                ?.content

            val packageId = highestVersionPackage?.jsonObject?.get("id")?.jsonPrimitive?.content

            val ref = highestVersionPackage
                ?.jsonObject
                ?.get("pipeline")
                ?.jsonObject
                ?.get("ref")
                ?.jsonPrimitive
                ?.content

            if (projectId != null && packageId != null) {
                Log.d("GITLAB_JSON", "Found package ID: $packageId\nFound project_id: $projectId")
                Log.d("GITLAB_JSON", "Found ref: $ref")
                NexusInfo.downloadProgressMap[mod.slug] =
                    "Found project ID: $projectId and package ID: $packageId"

                var fileId: String? = null

                try {
                    val packagesUrl =
                        "https://gitlab.com/api/v4/projects/$projectId/packages/$packageId/package_files?id=$projectId&order_by=id&package_id=$packageId&page=1&per_page=20&sort=asc"
                    val packagesJson = fetchJsonResponse(packagesUrl)
                    val packages = Json.parseToJsonElement(packagesJson).jsonArray

                    Log.d("GITLAB_FileID", "Crawling package url: $packagesUrl")
                    Log.d("GITLAB_FileID", "Fetched packages: $packages")
                    Log.d("GITLAB_FileID", "Looking for mod slug: '${mod.slug}'")

                    val matchingFile = packages.firstOrNull { file ->
                        val fileName = file.jsonObject["file_name"]?.jsonPrimitive?.content ?: ""
                        fileName.startsWith(slug) &&
                                !fileName.contains(".sha") &&
                                !fileName.contains(".md5") &&
                                (fileName.endsWith(".zip") || fileName.endsWith(".7z") || fileName.endsWith(".rar"))
                    }

                    if (matchingFile != null) {
                        fileId = matchingFile.jsonObject["id"]?.jsonPrimitive?.content
                        val fileName = matchingFile.jsonObject["file_name"]?.jsonPrimitive?.content
                        Log.d("FILE_ID", "Found matching file ID: $fileId with filename: $fileName")
                    } else {
                        Log.d("FILE_ID", "No matching file found")
                    }
                } catch (e: Exception) {
                    Log.e("FETCH_ERROR", "Failed to get package file ID", e)
                }
                Log.d("GITLAB_FILE", "Found file ID: $fileId")
                NexusInfo.downloadProgressMap[mod.slug] =
                    "Found file ID: $fileId"

                val finalUrl = "https://gitlab.com/$owner/$slug/-/package_files/$fileId/download"
                Log.d("GITLAB_JSON", "Latest URL: $finalUrl")
                NexusInfo.downloadProgressMap[mod.slug] =
                    "Latest URL: $finalUrl"

                val downloadInfo = getFileInfoWithOkHttp(finalUrl)
                val originalFileName = downloadInfo.fileName
                val baseFileName = originalFileName?.substringBeforeLast(".")
                val fileExtension = originalFileName?.substringAfterLast(".", missingDelimiterValue = "")
                val newFileName = if (ref != null && !fileExtension.isNullOrEmpty()) {
                    "${baseFileName}_${ref}.${fileExtension}"
                } else {
                    originalFileName
                }

                Log.d("GITLAB_FILENAME", "Original filename: $originalFileName, New filename: $newFileName, Content-Length: ${downloadInfo.fileSize}")

                val updatedDownloadInfo = mod.downloadInfo.map { info ->
                    info.copy(
                        directDownload = finalUrl,
                        fileName = newFileName,
                        fileSize = downloadInfo.fileSize
                    )
                }

                val updatedMod = mod.copy(
                    gitlabProjectId = projectId,
                    gitlabPackageId = packageId,
                    dlUrl = finalUrl,
                    downloadInfo = updatedDownloadInfo
                )
                modDao.insertOrUpdateMod(updatedMod)

            } else {
                Log.d("GITLAB_JSON", "No projects found or empty array")
                Log.d("GITLAB Base", "Actual URL: ${mod.url}")
            }

            NexusInfo.downloadProgressMap[mod.slug] =
                ""

            projectId
        } else {
            Log.d("GITLAB_JSON", "No projects found or empty array")
            null
        }
    } catch (e: Exception) {
        NexusInfo.downloadProgressMap[mod.slug] =
            "Failed to fetch project ID: ${e.message}"
        Log.e("FETCH_ERROR", "Failed to fetch project ID: ${e.message}", e)
        null
    }
}

fun getFileInfoWithOkHttp(url: String): DownloadInfo {
    return try {
        val client = OkHttpClient()
        val request = Request.Builder()
            .url(url)
            .head()
            .build()

        client.newCall(request).execute().use { response ->
            val contentDisposition = response.header("Content-Disposition")
            val contentLength = response.header("Content-Length")?.toLongOrNull()
            val filename = extractFilenameFromContentDisposition(contentDisposition)
                ?: url.substringAfterLast("/").substringBefore("?")
            val sizeKb = contentLength?.let { (it / 1024).toString() }
            DownloadInfo(
                fileName = filename,
                fileSize = sizeKb
            )
        }
    } catch (e: Exception) {
        Log.e("GITLAB_FILENAME", "Error getting file info: ${e.message}")
        DownloadInfo(directDownload = url)
    }
}

private fun extractFilenameFromContentDisposition(contentDisposition: String?): String? {
    if (contentDisposition.isNullOrEmpty()) return null

    // Look for filename in format: filename="example.zip"
    val filenameRegex = "filename=\"([^\"]+)\"".toRegex()
    val matchResult = filenameRegex.find(contentDisposition)

    return matchResult?.groupValues?.get(1) ?: run {
        // Fallback: look for filename=example.zip (without quotes)
        val fallbackRegex = "filename=([^;]+)".toRegex()
        fallbackRegex.find(contentDisposition)?.groupValues?.get(1)?.trim()
    }
}



