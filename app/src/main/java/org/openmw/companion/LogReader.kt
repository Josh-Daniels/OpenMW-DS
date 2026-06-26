package org.openmw.companion

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.io.RandomAccessFile

/**
 * Tails openmw.log. Polls the file size; when it grows, reads only the new
 * bytes, processes complete lines, and remembers the byte offset of the last
 * newline so we never re-process or split a line. If the file shrinks (OpenMW
 * was restarted and truncated the log) we reset to the start.
 *
 * Host-independent: give it a CoroutineScope and it runs until that scope is
 * cancelled or stop() is called.
 */
class LogReader(private val path: String) {

    private var job: Job? = null

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch(Dispatchers.IO) {
            var lastPos = 0L
            while (isActive) {
                try {
                    val file = File(path)
                    if (file.exists()) {
                        val len = file.length()
                        if (len < lastPos) lastPos = 0L           // truncated -> restart
                        if (len > lastPos) {
                            RandomAccessFile(file, "r").use { raf ->
                                raf.seek(lastPos)
                                val buf = ByteArray((len - lastPos).toInt())
                                raf.readFully(buf)
                                val text = String(buf, Charsets.UTF_8)
                                val lastNl = text.lastIndexOf('\n')
                                if (lastNl >= 0) {
                                    val complete = text.substring(0, lastNl)
                                    for (raw in complete.split('\n')) {
                                        val line = raw.trimEnd('\r')
                                        if (line.contains("COMPANION_")) {
                                            GameStateRepository.update { cur ->
                                                LogParser.parseLine(line, cur) ?: cur
                                            }
                                        }
                                    }
                                    // advance by BYTE length actually consumed (UTF-8 safe)
                                    val consumed = text.substring(0, lastNl + 1)
                                        .toByteArray(Charsets.UTF_8).size
                                    lastPos += consumed
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    // transient IO error (file mid-write etc.) — just retry next tick
                }
                delay(POLL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    companion object {
        private const val POLL_MS = 150L
    }
}
