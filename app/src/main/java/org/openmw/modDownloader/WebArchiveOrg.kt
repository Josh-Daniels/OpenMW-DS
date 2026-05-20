package org.openmw.modDownloader

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONArray

fun extractLastDigits(url: String): String? {
    val lastSegment = url.substringAfterLast('-')
    return lastSegment.takeIf { it.isNotEmpty() && it.all(Char::isDigit) }
}

suspend fun getLatestWaybackUrl(originalArchivedUrl: String): String? {
    return withContext(Dispatchers.IO) {
        try {
            println("🔍 Starting getLatestWaybackUrl for: $originalArchivedUrl")

            // Step 1: Download the HTML content of the archived page
            val connection = URL(originalArchivedUrl).openConnection() as HttpURLConnection
            println("🌐 Connecting to archived URL...")
            connection.connect()
            val html = connection.inputStream.bufferedReader().readText()
            println("📄 HTML content length: ${html.length}")

            // Step 2: Extract the actual file URL using improved regex
            val regex = Regex("""https?://mw\.modhistory\.com/file\.php\?id=\d+""")
            val match = regex.find(html)?.value
            println("🔗 Regex match result: $match")

            if (match == null) {
                println("⚠️ No file URL found in HTML content")
                return@withContext null
            }

            println("🌍 Found file URL: $match")

            // Extract just the domain and path for CDX query
            val urlParts = match.replaceFirst("https?://".toRegex(), "")
            val encodedFileUrl = URLEncoder.encode(urlParts, "UTF-8")

            println("🔐 Encoded file URL: $encodedFileUrl")

            val cdxApiUrl =
                "https://web.archive.org/cdx/search/cdx?url=$encodedFileUrl&output=json&sort=descending&limit=1"
            println("📡 CDX API URL: $cdxApiUrl")

            // Step 4: Query the CDX API
            val cdxConnection = URL(cdxApiUrl).openConnection() as HttpURLConnection
            println("🌐 Connecting to CDX API...")
            val response = cdxConnection.inputStream.bufferedReader().readText()
            println("📦 CDX API response: $response")

            val json = JSONArray(response)
            println("📊 JSON array length: ${json.length()}")

            if (json.length() < 2) {
                println("⚠️ No captures found in CDX response")
                return@withContext null
            }

            val latestCapture = json.getJSONArray(1)
            val timestamp = latestCapture.getString(1)
            val original = latestCapture.getString(2)

            println("🕒 Latest timestamp: $timestamp")
            println("🌍 Original URL from CDX: $original")

            // Step 5: Construct updated Wayback URL
            val updatedUrl = "https://web.archive.org/web/${timestamp}if_/$original"
            println("✅ Final updated Wayback URL: $updatedUrl")

            return@withContext updatedUrl
        } catch (e: Exception) {
            println("💥 Exception occurred: ${e.message}")
            e.printStackTrace()
            return@withContext null
        }
    }
}
