package org.openmw.modDownloader

import android.util.Base64
import android.util.Log
import androidx.compose.ui.graphics.Color
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.openmw.ui.view.addCustomLog
import java.io.File
import java.io.FileOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

private const val TAG = "MegaDownloader"

class MegaDownloader {

    private val client = OkHttpClient.Builder()
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    fun download(megaUrl: String, targetDir: File): Boolean {
        Log.i(TAG, "Starting MEGA download: $megaUrl")
        addCustomLog("Starting MEGA download...", textSize = 10, textColor = Color.Cyan)

        try {
            // 1. Parse URL
            val parts = parseMegaUrl(megaUrl)
            val fileHandle = parts[0]
            val fileKeyB64 = parts[1]

            val fileKey = base64UrlDecode(fileKeyB64)
            if (fileKey.size != 32) throw Exception("Invalid key length: ${fileKey.size} bytes, expected 32")

            val keyInt = bytesToA32(fileKey)

            // k = [k[0] ^ k[4], k[1] ^ k[5], k[2] ^ k[6], k[3] ^ k[7]]
            val k = intArrayOf(
                keyInt[0] xor keyInt[4],
                keyInt[1] xor keyInt[5],
                keyInt[2] xor keyInt[6],
                keyInt[3] xor keyInt[7]
            )
            val iv = intArrayOf(keyInt[4], keyInt[5], 0, 0)
            val metaMac = intArrayOf(keyInt[6], keyInt[7])

            // 2. Get File Info and Download URL
            val fileInfo = getFileInfo(fileHandle)
            val fileSize = fileInfo.getLong("s")
            val atB64 = fileInfo.getString("at")

            // Get the actual download URL and ensure it's HTTPS
            var downloadUrl = getDownloadUrl(fileHandle)

            // Force HTTPS
            if (downloadUrl.startsWith("http://")) {
                downloadUrl = downloadUrl.replace("http://", "https://")
                Log.d(TAG, "Converted to HTTPS: $downloadUrl")
            }

            // 3. Decrypt Filename
            val realFileName = decryptAttributes(atB64, k) ?: "mega_download_${fileHandle}.bin"

            // Ensure target is a directory and exists
            val workingDir = if (targetDir.isFile) targetDir.parentFile ?: targetDir else targetDir
            if (!workingDir.exists()) workingDir.mkdirs()

            val finalDest = File(workingDir, realFileName)

            Log.i(TAG, "File identified: $realFileName ($fileSize bytes)")
            addCustomLog("MEGA: $realFileName (${fileSize / 1024} KB)", textSize = 10, textColor = Color.White)

            // 4. Download and Decrypt on the fly
            val success = downloadAndDecrypt(downloadUrl, finalDest, k, iv, metaMac, fileSize)

            if (success) {
                Log.i(TAG, "SUCCESS! File verified: ${finalDest.name}")
                addCustomLog("MEGA SUCCESS: ${finalDest.name}", textSize = 10, textColor = Color.Green)
            } else {
                if (finalDest.exists()) finalDest.delete()
                throw Exception("Integrity verification failed (MAC mismatch)")
            }
            return success
        } catch (e: Exception) {
            Log.e(TAG, "Download failed: ${e.message}", e)
            addCustomLog("MEGA Error: ${e.message}", textSize = 10, textColor = Color.Red)
            return false
        }
    }

    private fun parseMegaUrl(url: String): Array<String> {
        return when {
            url.contains("!") -> {
                val parts = url.split("!")
                if (parts.size < 3) throw Exception("Invalid legacy MEGA URL")
                arrayOf(parts[1], parts[2])
            }
            url.contains("#") -> {
                val base = url.substringBefore("#")
                val key = url.substringAfter("#")
                val handle = base.substringAfterLast("/")
                if (handle.isEmpty() || key.isEmpty()) throw Exception("Invalid modern MEGA URL")
                arrayOf(handle, key)
            }
            else -> throw Exception("Unsupported MEGA URL format")
        }
    }

    private fun getFileInfo(handle: String): JSONObject {
        val payload = "[{\"a\":\"g\",\"g\":1,\"p\":\"$handle\"}]"
        val req = Request.Builder()
            .url("https://g.api.mega.co.nz/cs")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) throw Exception("MEGA API HTTP error ${response.code}")
            val bodyString = response.body!!.string()
            Log.d(TAG, "API Response: ${bodyString.take(200)}...")
            val array = JSONArray(bodyString)
            val first = array.get(0)
            if (first is Int && first < 0) throw Exception("MEGA API error code: $first")
            return array.getJSONObject(0)
        }
    }

    private fun getDownloadUrl(handle: String): String {
        val payload = "[{\"a\":\"g\",\"g\":1,\"p\":\"$handle\"}]"
        val req = Request.Builder()
            .url("https://g.api.mega.co.nz/cs")
            .post(payload.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) throw Exception("MEGA API HTTP error ${response.code}")
            val bodyString = response.body!!.string()
            val array = JSONArray(bodyString)
            val obj = array.getJSONObject(0)

            // Check for error
            if (obj.has("e")) {
                throw Exception("MEGA API error: ${obj.get("e")}")
            }

            // Get the download URL
            return obj.getString("g")
        }
    }

    private fun decryptAttributes(atB64: String, k: IntArray): String? {
        try {
            val at = base64UrlDecode(atB64)
            val aesKey = SecretKeySpec(a32ToBytes(k), "AES")
            val cipher = Cipher.getInstance("AES/ECB/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, aesKey)

            val decrypted = ByteArray(at.size)
            var iv = ByteArray(16)
            for (i in 0 until at.size step 16) {
                val block = ByteArray(16)
                val len = minOf(16, at.size - i)
                System.arraycopy(at, i, block, 0, len)

                val out = cipher.doFinal(block)
                for (j in 0 until len) {
                    decrypted[i + j] = (out[j].toInt() xor iv[j].toInt()).toByte()
                }
                iv = block
            }

            val str = String(decrypted, Charsets.UTF_8).trim()
            if (str.startsWith("MEGA")) {
                val jsonStr = str.substring(4).substringBeforeLast("}") + "}"
                return JSONObject(jsonStr).optString("n")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decrypt attributes", e)
        }
        return null
    }

    private fun downloadAndDecrypt(
        downloadUrl: String,
        finalDest: File,
        k: IntArray,
        iv: IntArray,
        expectedMetaMac: IntArray,
        fileSize: Long
    ): Boolean {
        val aesKey = SecretKeySpec(a32ToBytes(k), "AES")
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aesKey)

        // Initialize MAC with copy of IV
        var macState = intArrayOf(iv[0], iv[1], iv[2], iv[3])

        val req = Request.Builder()
            .url(downloadUrl)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            .header("Accept", "*/*")
            .header("Accept-Encoding", "identity")
            .build()

        Log.d(TAG, "Downloading from: $downloadUrl")

        client.newCall(req).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: "No error body"
                Log.e(TAG, "Download failed with code ${response.code}: $errorBody")
                throw Exception("Download failed with code ${response.code}")
            }

            val contentLength = response.header("Content-Length")?.toLongOrNull()
            Log.d(TAG, "Content-Length: $contentLength, Expected: $fileSize")

            val contentType = response.header("Content-Type", "")
            Log.d(TAG, "Content-Type: $contentType")

            val input = response.body!!.byteStream()
            FileOutputStream(finalDest).use { out ->
                val buffer = ByteArray(65536)
                var bytesRead: Int
                var totalBytesProcessed = 0L

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    // Process chunk
                    val result = processChunk(buffer, bytesRead, totalBytesProcessed, cipher, out, macState, iv)
                    macState = result
                    totalBytesProcessed += bytesRead

                    // Log progress
                    if (totalBytesProcessed % (1024 * 1024) == 0L) {
                        Log.d(TAG, "Downloaded: ${totalBytesProcessed / 1024} KB / ${fileSize / 1024} KB")
                    }
                }

                Log.d(TAG, "Total bytes processed: $totalBytesProcessed, Expected: $fileSize")

                if (totalBytesProcessed != fileSize) {
                    Log.w(TAG, "File size mismatch! Expected: $fileSize, Got: $totalBytesProcessed")
                }
            }
        }

        // Compute final MAC: [mac[0] ^ mac[1], mac[2] ^ mac[3]]
        val computedMetaMac = intArrayOf(
            macState[0] xor macState[1],
            macState[2] xor macState[3]
        )

        Log.d(TAG, "MAC verification - Expected: [${expectedMetaMac[0]}, ${expectedMetaMac[1]}], Got: [${computedMetaMac[0]}, ${computedMetaMac[1]}]")

        val macMatches = computedMetaMac[0] == expectedMetaMac[0] && computedMetaMac[1] == expectedMetaMac[1]
        Log.d(TAG, "MAC verification ${if (macMatches) "PASSED" else "FAILED"}")

        return macMatches
    }

    private fun processChunk(
        data: ByteArray,
        len: Int,
        fileOffset: Long,
        cipher: Cipher,
        out: FileOutputStream,
        macState: IntArray,
        iv: IntArray
    ): IntArray {
        var mac = macState.copyOf()
        val decrypted = ByteArray(len)

        for (i in 0 until len step 16) {
            val blockSize = minOf(16, len - i)
            val blockIndex = (fileOffset / 16).toInt() + (i / 16)

            // Create block nonce: [iv[0], iv[1], iv[2] + blockIndex, iv[3]]
            val blockNonce = intArrayOf(
                iv[0],
                iv[1],
                iv[2] + blockIndex,
                iv[3]
            )

            Log.d(TAG, "blockIndex=$blockIndex fileOffset=$fileOffset i=$i")

            // Encrypt nonce to get keystream
            val encryptedNonce = cipher.doFinal(a32ToBytes(blockNonce))

            // XOR encrypted data with keystream to decrypt
            for (j in 0 until blockSize) {
                decrypted[i + j] = (data[i + j].toInt() xor encryptedNonce[j].toInt()).toByte()
            }

            // Update MAC with decrypted data
            val macBlock = ByteArray(16)
            System.arraycopy(decrypted, i, macBlock, 0, blockSize)

            val blockInt = bytesToA32(macBlock)
            for (m in 0 until 4) {
                mac[m] = mac[m] xor blockInt[m]
            }

            // Encrypt MAC state
            mac = bytesToA32(cipher.doFinal(a32ToBytes(mac)))
        }

        out.write(decrypted)
        return mac
    }

    private fun base64UrlDecode(s: String): ByteArray {
        var str = s.replace('-', '+').replace('_', '/')
        while (str.length % 4 != 0) str += "="
        return Base64.decode(str, Base64.DEFAULT)
    }

    private fun bytesToA32(b: ByteArray): IntArray {
        val result = IntArray(b.size / 4)
        for (i in result.indices) {
            result[i] = ((b[i*4].toInt() and 0xFF) shl 24) or
                    ((b[i*4+1].toInt() and 0xFF) shl 16) or
                    ((b[i*4+2].toInt() and 0xFF) shl 8) or
                    (b[i*4+3].toInt() and 0xFF)
        }
        return result
    }

    private fun a32ToBytes(a: IntArray): ByteArray {
        val b = ByteArray(a.size * 4)
        for (i in a.indices) {
            val v = a[i]
            b[i*4] = (v shr 24).toByte()
            b[i*4+1] = (v shr 16).toByte()
            b[i*4+2] = (v shr 8).toByte()
            b[i*4+3] = v.toByte()
        }
        return b
    }
}