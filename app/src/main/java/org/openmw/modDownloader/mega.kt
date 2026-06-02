package org.openmw.modDownloader

import android.util.Base64
import android.util.Log
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

private const val TAG = "MegaDownloader"

class MegaDownloader {

    private val client = OkHttpClient()

    fun download(megaUrl: String, destFile: File): Boolean {
        Log.i(TAG, "Starting MEGA download: $megaUrl")
        Log.i(TAG, "Destination: ${destFile.absolutePath}")

        try {
            val parts = megaUrl.split("!")
            if (parts.size < 3) {
                Log.e(TAG, "Invalid MEGA URL")
                return false
            }

            val fileHandle = parts[1]
            val fileKeyB64 = parts[2]

            val fileKey = base64UrlDecode(fileKeyB64)
            val paddedKey = if (fileKey.size % 4 != 0) {
                ByteArray((fileKey.size + 3) / 4 * 4).also { System.arraycopy(fileKey, 0, it, 0, fileKey.size) }
            } else fileKey
            val keyInt = bytesToA32(paddedKey)

            val k = intArrayOf(
                keyInt[0] xor keyInt[4],
                keyInt[1] xor keyInt[5],
                keyInt[2] xor keyInt[6],
                keyInt[3] xor keyInt[7]
            )
            val iv = intArrayOf(keyInt[4], keyInt[5], 0, 0)
            val expectedMetaMac = intArrayOf(keyInt[6], keyInt[7])

            Log.i(TAG, "AES key (k): ${k.contentToString()}")
            Log.i(TAG, "IV: ${iv[0]}, ${iv[1]}")
            Log.i(TAG, "Expected meta MAC: ${expectedMetaMac[0]}, ${expectedMetaMac[1]}")

            val info = getFileInfo(fileHandle)
            val dlUrl = info.getString("g")
            val fileSize = info.getLong("s")
            Log.i(TAG, "File size: $fileSize bytes")

            val tempFile = File(destFile.parentFile, "${destFile.name}.mega")
            downloadFile(dlUrl, tempFile, fileSize)

            decryptAndVerify(tempFile, destFile, k, iv, expectedMetaMac, fileSize)

            tempFile.delete()
            Log.i(TAG, "SUCCESS! File downloaded and verified: ${destFile.name}")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Download failed", e)
            return false
        }
    }

    private fun getFileInfo(handle: String): JSONObject {
        val payload = JSONArray().put(JSONObject().apply {
            put("a", "g"); put("g", 1); put("p", handle)
        })

        val req = Request.Builder()
            .url("https://g.api.mega.co.nz/cs")
            .post(payload.toString().toRequestBody("application/json".toMediaType()))
            .build()

        return client.newCall(req).execute().use {
            if (!it.isSuccessful) throw Exception("MEGA API error ${it.code}")
            JSONArray(it.body.string()).getJSONObject(0)
        }
    }

    private fun downloadFile(url: String, dest: File, totalSize: Long) {
        var downloaded = 0L
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { resp ->
            resp.body.byteStream().use { input ->
                dest.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded % (1024 * 1024) < 8192) {
                            Log.i(TAG, "Downloaded: ${downloaded / 1024 / 1024} MB / ${totalSize / 1024 / 1024} MB")
                        }
                    }
                }
            }
        }
        Log.i(TAG, "Encrypted download complete: $downloaded bytes")
    }

    private fun decryptAndVerify(
        encrypted: File,
        output: File,
        k: IntArray,
        iv: IntArray,
        expectedMetaMac: IntArray,
        fileSize: Long
    ) {
        Log.i(TAG, "Starting decryption + MAC verification")

        val keyBytes = a32ToBytes(k)
        val aesKey = SecretKeySpec(keyBytes, "AES")

        // CTR IV: first 8 bytes = iv[0], iv[1] in big-endian
        val ctrIv = ByteArray(16).apply {
            for (i in 0..3) this[i] = (iv[0] shr (24 - i * 8)).toByte()
            for (i in 0..3) this[4 + i] = (iv[1] shr (24 - i * 8)).toByte()
        }
        val ctrCipher = Cipher.getInstance("AES/CTR/NoPadding")
        ctrCipher.init(Cipher.DECRYPT_MODE, aesKey, IvParameterSpec(ctrIv))

        // MAC calculation - initialize with zeros
        var macState = ByteArray(16)
        val macCipher = Cipher.getInstance("AES/CBC/NoPadding")
        macCipher.init(Cipher.ENCRYPT_MODE, aesKey, IvParameterSpec(ByteArray(16)))

        var decryptedBytes = 0L

        encrypted.inputStream().use { input ->
            output.outputStream().use { out ->
                val buffer = ByteArray(16384)
                var bytesRead: Int

                while (input.read(buffer).also { bytesRead = it } != -1) {
                    // === DECRYPTION ===
                    val decryptedChunk = if (bytesRead == buffer.size)
                        ctrCipher.update(buffer)
                    else
                        ctrCipher.update(buffer, 0, bytesRead)
                    out.write(decryptedChunk)
                    decryptedBytes += decryptedChunk.size

                    // === MAC: feed ENCRYPTED bytes (not decrypted!) ===
                    var pos = 0
                    while (pos < bytesRead) {
                        val blockSize = minOf(16, bytesRead - pos)
                        val block = ByteArray(16)
                        System.arraycopy(buffer, pos, block, 0, blockSize)

                        // Pad the block if necessary
                        if (blockSize < 16) {
                            // For the last block, pad with zeros
                            for (i in blockSize until 16) {
                                block[i] = 0
                            }
                        }

                        // Update MAC state
                        val updateResult = macCipher.update(block)
                        if (updateResult != null) {
                            macState = updateResult
                        }
                        pos += 16
                    }

                    if (decryptedBytes % (1024 * 1024) < 16384) {
                        Log.i(TAG, "Decrypted: ${decryptedBytes / 1024 / 1024} MB")
                    }
                }

                // Final CTR block
                val finalDecrypted = ctrCipher.doFinal()
                if (finalDecrypted.isNotEmpty()) {
                    out.write(finalDecrypted)
                    decryptedBytes += finalDecrypted.size
                }

                // Finalize MAC - handle potential null return
                val finalMac = macCipher.doFinal()
                if (finalMac != null && finalMac.isNotEmpty()) {
                    macState = finalMac
                }
            }
        }

        // Safety check - ensure macState is valid
        if (macState.isEmpty()) {
            Log.e(TAG, "MAC state is empty - verification failed")
            output.delete()
            throw SecurityException("MEGA MAC verification failed - empty MAC state")
        }

        // Calculate meta MAC
        val computedMac = bytesToA32(macState)
        val computedMetaMac = intArrayOf(
            computedMac[0] xor computedMac[1],
            computedMac[2] xor computedMac[3]
        )

        Log.i(TAG, "Computed meta MAC: ${computedMetaMac[0]}, ${computedMetaMac[1]}")
        Log.i(TAG, "Expected meta MAC: ${expectedMetaMac[0]}, ${expectedMetaMac[1]}")

        if (computedMetaMac[0] == expectedMetaMac[0] && computedMetaMac[1] == expectedMetaMac[1]) {
            Log.i(TAG, "MAC VERIFICATION PASSED!")
        } else {
            Log.e(TAG, "MAC VERIFICATION FAILED!")
            output.delete()
            throw SecurityException("MEGA MAC verification failed — file corrupted or wrong key")
        }
    }

    private fun base64UrlDecode(s: String): ByteArray {
        var str = s.replace('-', '+').replace('_', '/')
        str += when (str.length % 4) { 2 -> "=="; 3 -> "="; else -> "" }
        return Base64.decode(str, Base64.DEFAULT)
    }

    private fun bytesToA32(b: ByteArray): IntArray {
        val len = (b.size + 3) / 4
        val result = IntArray(len)
        for (i in 0 until len) {
            result[i] =
                ((b.getOrElse(i*4) { 0 }.toInt() and 0xFF) shl 24) or
                        ((b.getOrElse(i*4+1) { 0 }.toInt() and 0xFF) shl 16) or
                        ((b.getOrElse(i*4+2) { 0 }.toInt() and 0xFF) shl 8) or
                        (b.getOrElse(i*4+3) { 0 }.toInt() and 0xFF)
        }
        return result
    }

    private fun a32ToBytes(a: IntArray): ByteArray {
        val b = ByteArray(a.size * 4)
        a.forEachIndexed { i, v ->
            b[i*4]     = (v shr 24).toByte()
            b[i*4 + 1] = (v shr 16).toByte()
            b[i*4 + 2] = (v shr  8).toByte()
            b[i*4 + 3] = v.toByte()
        }
        return b
    }
}