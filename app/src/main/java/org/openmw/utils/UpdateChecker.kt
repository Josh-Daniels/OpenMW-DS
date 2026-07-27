package org.openmw.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request
import org.openmw.BuildConfig
import org.openmw.modDownloader.ModListManager
import java.io.File
import java.io.IOException

/**
 * In-app update check + download (Phase 1 — check, compare, download ONLY).
 *
 * Deliberately stops at "a verified APK is sitting in the cache". Handing that file to the package
 * installer needs a `FileProvider`, the `REQUEST_INSTALL_PACKAGES` permission and the per-app
 * unknown-sources grant flow, none of which exist yet — that is Phase 2. Nothing here touches the
 * manifest, so this whole feature is exercisable from a debug build.
 *
 * Transport is the SHARED [ModListManager.client] (60s timeouts, retry-on-failure, a 3-attempt
 * retry interceptor). We deliberately do NOT reuse `ModListManager.fetchJsonResponse()` despite
 * the fitting name: it injects a Nexus Mods `apikey` header and writes response headers back into
 * `NexusInfo`'s rate-limit counters, so calling it against GitHub would both leak a key to the
 * wrong host and corrupt the mod downloader's accounting. JSON parsing follows the style already
 * used in `GitLab.kt` (kotlinx.serialization's untyped `parseToJsonElement` tree).
 *
 * State is a single [StateFlow] on this object rather than per-screen state, so the on-launch
 * check and the Settings screen observe the SAME result — opening Settings after launch shows what
 * the automatic check already found instead of re-fetching.
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"

    /**
     * GitHub's "latest release" endpoint. Per GitHub's API contract this returns the most recent
     * NON-draft, NON-prerelease release, which is what makes it safe to stage a test build as a
     * prerelease without every user's updater picking it up. That exclusion is documented but has
     * not been verified against this repo (it has no prereleases to test against) — worth
     * confirming with a throwaway prerelease before relying on it.
     */
    const val RELEASES_LATEST_URL =
        "https://api.github.com/repos/Josh-Daniels/OpenMW-DS/releases/latest"

    /** GitHub rejects API requests with no User-Agent (403), so this header is mandatory. */
    private val USER_AGENT = "OpenMW-DS-Updater/${BuildConfig.RELEASE_VERSION}"

    /** Subdirectory of `context.cacheDir` holding downloaded APKs. */
    private const val CACHE_SUBDIR = "updates"

    /** Headroom demanded on top of the APK size before starting a ~71MB download. */
    private const val FREE_SPACE_MARGIN_BYTES = 32L * 1024 * 1024

    /** Progress is only re-published on a whole-percent change, to avoid recomposition churn. */
    private const val PROGRESS_STEP_PERCENT = 1

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Serialises check/download so a manual tap can't race the on-launch check. */
    private val lock = Mutex()

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    // -------------------------------------------------------------------------------------
    // Version comparison
    // -------------------------------------------------------------------------------------

    /**
     * Split a version string into numeric components.
     *
     * Tolerates the `v` prefix GitHub tags carry (`v0.8.0`) while `BuildConfig.RELEASE_VERSION`
     * does not, and drops any `-beta` / `+build` suffix so it can't poison a component.
     */
    fun parseVersion(version: String): List<Int> =
        version.trim()
            .removePrefix("v")
            .removePrefix("V")
            .substringBefore('-')
            .substringBefore('+')
            .split('.')
            .map { part -> part.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }

    /**
     * Compare two versions component-by-component, NUMERICALLY.
     *
     * String comparison is wrong here and fails soon: lexicographically `"0.10.0" < "0.9.0"`,
     * which would make the first 0.10.x release look older than 0.9.x and silently stop offering
     * updates. Missing trailing components count as 0, so `1.2` == `1.2.0`.
     *
     * @return negative if [a] < [b], zero if equal, positive if [a] > [b].
     */
    fun compareVersions(a: String, b: String): Int {
        val pa = parseVersion(a)
        val pb = parseVersion(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val diff = pa.getOrElse(i) { 0 }.compareTo(pb.getOrElse(i) { 0 })
            if (diff != 0) return diff
        }
        return 0
    }

    // -------------------------------------------------------------------------------------
    // Check
    // -------------------------------------------------------------------------------------

    /**
     * On-launch check. Fire-and-forget: never throws, and quietly does nothing if a check or
     * download is already in flight or has already produced a result worth keeping.
     *
     * Note this only CHECKS — it never starts the ~71MB download on its own. Pulling that much
     * data on every cold start, potentially over mobile, is the user's call to make, so the
     * download is always an explicit tap.
     */
    suspend fun checkOnLaunch() {
        when (_state.value) {
            // Don't clobber an in-flight or already-useful result.
            is UpdateState.Checking, is UpdateState.Downloading,
            is UpdateState.Ready, is UpdateState.Available -> return
            else -> Unit
        }
        runCatching { check() }
            .onFailure { Log.w(TAG, "launch update check failed (ignored)", it) }
    }

    /**
     * Query GitHub for the latest release and compare it against [BuildConfig.RELEASE_VERSION].
     *
     * Leaves [state] as [UpdateState.Available], [UpdateState.UpToDate] or [UpdateState.Failed].
     */
    suspend fun check(): UpdateState = lock.withLock {
        _state.value = UpdateState.Checking
        val result = withContext(Dispatchers.IO) {
            try {
                val body = fetchLatestReleaseJson()
                val release = json.parseToJsonElement(body).jsonObject

                val tag = release["tag_name"]?.jsonPrimitive?.content
                    ?: return@withContext UpdateState.Failed("Release has no tag_name")
                val latest = tag.removePrefix("v").removePrefix("V")

                // Exactly one .apk asset per release today, but pick by extension rather than by
                // index so an added checksum/mapping asset can't break this.
                val asset = release["assets"]?.jsonArray
                    ?.map { it.jsonObject }
                    ?.firstOrNull { a ->
                        a["name"]?.jsonPrimitive?.content?.endsWith(".apk", ignoreCase = true) == true
                    }

                val url = asset?.get("browser_download_url")?.jsonPrimitive?.content
                val name = asset?.get("name")?.jsonPrimitive?.content
                val size = asset?.get("size")?.jsonPrimitive?.content?.toLongOrNull() ?: 0L

                if (url.isNullOrBlank() || name.isNullOrBlank()) {
                    return@withContext UpdateState.Failed("Release $tag has no APK asset")
                }

                val current = BuildConfig.RELEASE_VERSION
                Log.d(TAG, "installed=$current latest=$latest (tag=$tag, asset=$name, ${size}B)")

                if (compareVersions(latest, current) > 0) {
                    UpdateState.Available(
                        UpdateInfo(
                            version = latest,
                            tag = tag,
                            downloadUrl = url,
                            assetName = name,
                            sizeBytes = size
                        )
                    )
                } else {
                    UpdateState.UpToDate(current)
                }
            } catch (e: Exception) {
                Log.w(TAG, "update check failed", e)
                UpdateState.Failed(e.message ?: "Could not reach GitHub")
            }
        }
        _state.value = result
        result
    }

    private fun fetchLatestReleaseJson(): String {
        val request = Request.Builder()
            .url(RELEASES_LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("X-GitHub-Api-Version", "2022-11-28")
            .header("User-Agent", USER_AGENT)
            .build()

        ModListManager.client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub returned HTTP ${response.code}")
            }
            return response.body.string()
        }
    }

    // -------------------------------------------------------------------------------------
    // Download
    // -------------------------------------------------------------------------------------

    /**
     * Download the APK for the currently-[UpdateState.Available] update into
     * `context.cacheDir/updates/`.
     *
     * `cacheDir` (internal, OS-evictable) rather than [org.openmw.Constants.CACHE_DIR] because
     * this file is transient scaffolding, not user-facing mod content — matching how
     * `KramConverter`/`Terminal` use temp files.
     *
     * Downloads to a `.part` file and only renames on a fully-verified read, so an interrupted or
     * cancelled transfer can never leave something that LOOKS like a complete APK. Cancelling the
     * calling coroutine aborts cleanly and removes the partial.
     */
    suspend fun download(context: Context): Boolean = lock.withLock {
        val info = (_state.value as? UpdateState.Available)?.info ?: run {
            Log.w(TAG, "download() with no available update; ignoring")
            return@withLock false
        }

        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, CACHE_SUBDIR).apply { mkdirs() }
            // Clear anything left by a previous run (stale partials, a superseded APK) so the
            // cache can't grow by ~71MB per release checked.
            dir.listFiles()?.forEach { it.delete() }

            val target = File(dir, info.assetName)
            val partial = File(dir, info.assetName + ".part")

            try {
                if (info.sizeBytes > 0) {
                    val needed = info.sizeBytes + FREE_SPACE_MARGIN_BYTES
                    if (dir.usableSpace < needed) {
                        _state.value = UpdateState.Failed(
                            "Not enough free space (need ~${formatBytes(needed)})"
                        )
                        return@withContext false
                    }
                }

                _state.value = UpdateState.Downloading(info, 0L, info.sizeBytes)

                val request = Request.Builder()
                    .url(info.downloadUrl)
                    .header("Accept", "application/octet-stream")
                    .header("User-Agent", USER_AGENT)
                    .build()

                ModListManager.client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Download failed: HTTP ${response.code}")
                    }
                    val body = response.body
                    // Prefer the API-reported size; fall back to Content-Length. Either may be
                    // absent/-1, in which case progress stays indeterminate rather than lying.
                    val total = if (info.sizeBytes > 0) info.sizeBytes else body.contentLength()

                    var read = 0L
                    var lastPercent = -1

                    body.byteStream().use { input ->
                        partial.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                // Honour cancellation between chunks so a user leaving the screen
                                // (or a manual cancel) stops promptly rather than pulling 71MB.
                                ensureActive()
                                val n = input.read(buffer)
                                if (n < 0) break
                                output.write(buffer, 0, n)
                                read += n

                                if (total > 0) {
                                    val percent = ((read * 100) / total).toInt()
                                    if (percent >= lastPercent + PROGRESS_STEP_PERCENT) {
                                        lastPercent = percent
                                        _state.value = UpdateState.Downloading(info, read, total)
                                    }
                                } else {
                                    _state.value = UpdateState.Downloading(info, read, 0L)
                                }
                            }
                            output.flush()
                        }
                    }

                    // A truncated response that still closed cleanly would otherwise be renamed
                    // and presented as installable.
                    if (total > 0 && read != total) {
                        throw IOException("Incomplete download (${read} of ${total} bytes)")
                    }
                }

                if (target.exists()) target.delete()
                if (!partial.renameTo(target)) {
                    throw IOException("Could not finalise ${target.name}")
                }

                Log.d(TAG, "downloaded ${target.name} (${target.length()} bytes)")
                _state.value = UpdateState.Ready(info, target)
                true
            } catch (e: Exception) {
                partial.delete()
                // A cancellation is a user action, not a failure to report as one; drop back to
                // the offer so they can retry.
                if (e is kotlinx.coroutines.CancellationException) {
                    _state.value = UpdateState.Available(info)
                    throw e
                }
                Log.w(TAG, "update download failed", e)
                _state.value = UpdateState.Failed(e.message ?: "Download failed")
                false
            }
        }
    }

    /** Drop a terminal result so the UI returns to its resting state. */
    fun dismiss() {
        when (_state.value) {
            is UpdateState.Downloading, is UpdateState.Checking -> Unit
            else -> _state.value = UpdateState.Idle
        }
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024 * 1024 -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
        bytes >= 1024L * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1024L -> String.format("%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }
}

/** A newer release found on GitHub, plus what is needed to fetch it. */
data class UpdateInfo(
    /** Version with any `v` prefix stripped, e.g. `0.9.0`. */
    val version: String,
    /** The raw git tag, e.g. `v0.9.0`. */
    val tag: String,
    val downloadUrl: String,
    val assetName: String,
    /** Asset size in bytes as reported by the API; 0 when unknown. */
    val sizeBytes: Long
)

sealed interface UpdateState {
    /** Nothing checked yet this session, or a result was dismissed. */
    data object Idle : UpdateState

    data object Checking : UpdateState

    /** Checked, and [current] is the newest release. */
    data class UpToDate(val current: String) : UpdateState

    /** A newer release exists; not downloaded. */
    data class Available(val info: UpdateInfo) : UpdateState

    data class Downloading(
        val info: UpdateInfo,
        val bytesRead: Long,
        /** Total size, or 0 when the server reported none (progress is then indeterminate). */
        val totalBytes: Long
    ) : UpdateState {
        val fraction: Float
            get() = if (totalBytes > 0) (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f) else 0f

        val isDeterminate: Boolean get() = totalBytes > 0
    }

    /**
     * APK downloaded and sitting at [file]. There is deliberately no install action behind this
     * yet — see the Phase 2 note on [UpdateChecker].
     */
    data class Ready(val info: UpdateInfo, val file: File) : UpdateState

    data class Failed(val message: String) : UpdateState
}
