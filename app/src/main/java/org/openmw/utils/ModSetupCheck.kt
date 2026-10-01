package org.openmw.utils

import android.util.Log
import org.openmw.Constants
import org.openmw.utils.OpenMWConfigUtils.ConfigKeyType
import java.io.File
import java.io.RandomAccessFile

/**
 * Checks the mod setup in `openmw.cfg` for problems the engine will refuse to start with, and fixes
 * the ones that have a safe, mechanical fix.
 *
 * **Every ERROR here is a hard launch failure in the engine, not a style complaint**, which is the
 * bar for calling something an error:
 * - a `content=`/`groundcover=` file found in no enabled data folder:
 *   `World::loadContentFiles` throws "the content file does not exist";
 * - a `content=` with an extension no loader accepts (`.bsa`, `.esl`): `GameContentLoader::load`
 *   throws "Cannot load file". `.bsa` as content is exactly what the Alpha3 Add Mods writer used
 *   to produce, see [ModAssistantViewModel.modPathSelection];
 * - a plugin whose master is missing, disabled, or loads AFTER it: ESMReader's "asks for parent
 *   file ... but it is not available or has been loaded in the wrong order";
 * - a `fallback-archive=` whose file is in no data folder: the archive registration throws.
 *
 * WARNINGs (a data folder that is not on the device, the same folder registered twice, two
 * different `Data Files` folders at once) do not stop the game but cost load time or load files the
 * player did not mean to. INFO is an unregistered `.bsa` sitting in a data folder, i.e. a mod whose
 * textures and meshes will silently not appear.
 *
 * **Data folders are read from BOTH config files**, because the engine reads both: the internal
 * one (`filesDir/config/openmw.cfg`) normally holds the base game's `data=` line and the base
 * archives, the user one holds everything the player added. Content is only ever in the user file.
 *
 * Fixes go through [OpenMWConfigUtils.getOpenMWConfig] / [OpenMWConfigUtils.saveOpenMWConfig], the
 * same pair under [ModAssistantViewModel.writeModValuesToFile]: whole sections are replaced, and
 * every line the utils do not model (comments, `fallback-archive=`, `data-local=`) is kept in its
 * "Others" bucket. Only the USER file is ever written.
 */
object ModSetupCheck {

    private const val TAG = "ModSetupCheck"

    /** What `World::loadContentFiles` has a loader for. Mirrors `ENGINE_CONTENT_EXTENSIONS`. */
    private val LOADABLE_EXTENSIONS = setOf("esm", "esp", "omwgame", "omwaddon", "omwscripts", "project")

    /** Only the TES3 header is needed for the master list; it sits at the very start of the file. */
    private const val MAX_HEADER_BYTES = 256 * 1024

    enum class Severity { ERROR, WARNING, INFO }

    sealed interface Fix {
        val label: String

        /** Comment the entry out (`;content=`), keeping its place in the load order. */
        data class DisableEntry(val key: String, val value: String) : Fix {
            override val label get() = "Disable $value"
        }

        /** Re-enable an existing `;content=` entry. */
        data class EnableEntry(val value: String) : Fix {
            override val label get() = "Enable $value"
        }

        /** Move [master] to just above [plugin]. */
        data class MoveAbove(val master: String, val plugin: String) : Fix {
            override val label get() = "Move $master above $plugin"
        }

        /** Replace a `content=<archive>.bsa` line with `fallback-archive=<archive>.bsa`. */
        data class ContentToArchive(val value: String) : Fix {
            override val label get() = "Register $value as an archive"
        }

        /** Add `fallback-archive=<archive>` for an archive found in a data folder. */
        data class RegisterArchive(val value: String) : Fix {
            override val label get() = "Register $value"
        }

        /** Remove a `fallback-archive=` line whose archive is not on the device. */
        data class RemoveArchive(val value: String) : Fix {
            override val label get() = "Remove $value"
        }
    }

    data class Finding(val severity: Severity, val message: String, val fix: Fix? = null)

    data class Result(
        val userConfigPath: String,
        /** The player's enabled data folders in engine order, with whether each exists. App-owned
         *  folders are left out; this is what the card shows. */
        val dataFolders: List<Pair<String, Boolean>>,
        /** Every enabled data folder, app-owned included, for the bug report. */
        val allDataFolders: List<Pair<String, Boolean>>,
        val findings: List<Finding>,
    ) {
        val errorCount get() = findings.count { it.severity == Severity.ERROR }
    }

    private data class CfgLine(val key: String, val value: String, val enabled: Boolean)

    private fun readCfg(path: String): List<CfgLine> {
        val f = File(path)
        if (!f.exists()) return emptyList()
        return runCatching {
            f.readLines().mapNotNull { raw ->
                val t = raw.trim()
                if (t.isEmpty() || t.startsWith("#")) return@mapNotNull null
                val enabled = !t.startsWith(";")
                val body = t.removePrefix(";").trim()
                if (!body.contains("=")) return@mapNotNull null
                val key = body.substringBefore("=").trim()
                val value = body.substringAfter("=").trim()
                CfgLine(key, value, enabled)
            }
        }.getOrElse {
            Log.w(TAG, "could not read $path: $it")
            emptyList()
        }
    }

    /** `data="..."` values are quoted in openmw.cfg; `&` escapes a quote or another `&`. */
    private fun unquotePath(value: String): String {
        val v = value.trim()
        if (!v.startsWith("\"")) return v.trimEnd('/')
        val sb = StringBuilder()
        var i = 1
        while (i < v.length) {
            val c = v[i]
            if (c == '&' && i + 1 < v.length) {
                sb.append(v[i + 1]); i += 2; continue
            }
            if (c == '"') break
            sb.append(c); i++
        }
        return sb.toString().trimEnd('/')
    }

    private fun ext(name: String) = name.substringAfterLast('.', "").lowercase()

    private fun isAppManaged(path: String): Boolean {
        val p = path.trimEnd('/')
        return listOf(
            Constants.USER_RESOURCES,
            "${Constants.USER_RESOURCES}/vfs-mw",
            Constants.USER_DELTA,
            "${Constants.USER_FILE_STORAGE}/OpenMW/Mods/companion",
            "${Constants.USER_FILE_STORAGE}/OpenMW/Override",
        ).any { it.trimEnd('/').equals(p, ignoreCase = true) }
    }

    /** Run every check. Pure I/O: call off the main thread. */
    fun run(): Result {
        val internal = readCfg(Constants.OPENMW_CFG)
        val user = readCfg(Constants.USER_OPENMW_CFG)
        val findings = mutableListOf<Finding>()

        // ---- Data folders, in engine order (internal file first) ----
        val dataLines = (internal + user).filter { it.enabled && (it.key == "data" || it.key == "data-local") }
        val folders = dataLines.map { unquotePath(it.value) }
        val distinctFolders = folders.distinctBy { it.lowercase() }
        val folderExists = distinctFolders.map { it to File(it).isDirectory }

        // Only the player's own folders. The internal file's app-managed lines include
        // `data-local=<...>/Override`, which is allowed not to exist and is no one's problem.
        user.filter { it.enabled && it.key == "data" }
            .map { unquotePath(it.value) }
            .distinctBy { it.lowercase() }
            .filterNot { File(it).isDirectory }
            .forEach { path ->
                findings += Finding(
                    Severity.WARNING,
                    "Data folder not found on this device: $path. Anything in it will not load.",
                )
            }
        // The same folder twice in the USER file is a leftover; once in each file is the normal
        // shape for the base game and the engine ignores the repeat.
        user.filter { it.enabled && it.key == "data" }
            .groupBy { unquotePath(it.value).lowercase() }
            .filter { it.value.size > 1 }
            .forEach { (_, lines) ->
                findings += Finding(
                    Severity.WARNING,
                    "Data folder registered ${lines.size} times: ${unquotePath(lines.first().value)}.",
                )
            }
        val dataFilesFolders = distinctFolders.filter { it.endsWith("Data Files", ignoreCase = true) }
        if (dataFilesFolders.size > 1) {
            findings += Finding(
                Severity.WARNING,
                "More than one Data Files folder is loaded (${dataFilesFolders.joinToString(" and ")}). " +
                    "Every file in both is loaded, which slows startup. Select your game files " +
                    "again to fix this.",
            )
        }

        // ---- What is on disk: lowercase file name -> the file the ENGINE would pick (last folder wins) ----
        val onDisk = LinkedHashMap<String, File>()
        for ((path, exists) in folderExists) {
            if (!exists) continue
            File(path).listFiles()?.filter { it.isFile }?.forEach { onDisk[it.name.lowercase()] = it }
        }

        // ---- Archives ----
        val archiveLines = (internal + user).filter { it.enabled && it.key == "fallback-archive" }
        val registeredArchives = archiveLines.map { it.value.lowercase() }.toSet()
        for (line in user.filter { it.enabled && it.key == "fallback-archive" }) {
            if (line.value.lowercase() !in onDisk) {
                findings += Finding(
                    Severity.ERROR,
                    "Archive ${line.value} is registered but is not in any data folder. The game " +
                        "will not start.",
                    Fix.RemoveArchive(line.value),
                )
            }
        }

        // ---- Content and groundcover ----
        val contentLines = user.filter { it.key == "content" }
        val groundcover = user.filter { it.key == "groundcover" && it.enabled }
        val asContent = contentLines.map { it.value.lowercase() }.toSet()

        for (line in groundcover) {
            if (line.value.lowercase() !in onDisk) {
                findings += Finding(
                    Severity.ERROR,
                    "Groundcover ${line.value} is not in any data folder. The game will not start.",
                    Fix.DisableEntry("groundcover", line.value),
                )
            }
        }

        // A grass plugin on BOTH lists: loaded twice, once as ordinary objects. Not a launch
        // failure, but a large performance cost and never intended. Auto-registration used to
        // create exactly this (it did not count groundcover entries as registered), so configs
        // touched before Oct 1 2026 can carry it.
        val groundcoverNames = groundcover.map { it.value.lowercase() }.toSet()
        contentLines.filter { it.enabled && it.value.lowercase() in groundcoverNames }.forEach {
            findings += Finding(
                Severity.WARNING,
                "${it.value} is a groundcover (grass) plugin but is also enabled as a normal " +
                    "plugin, so it is loaded twice and slows the game down.",
                Fix.DisableEntry("content", it.value),
            )
        }

        val enabledContent = contentLines.filter { it.enabled }
        val enabledIndex = enabledContent.withIndex().associate { (i, l) -> l.value.lowercase() to i }
        val disabledContent = contentLines.filterNot { it.enabled }.map { it.value.lowercase() }.toSet()

        for ((index, line) in enabledContent.withIndex()) {
            val name = line.value
            val extension = ext(name)
            if (extension == "bsa") {
                findings += Finding(
                    Severity.ERROR,
                    "$name is an archive but is listed as a plugin. The game will not start.",
                    Fix.ContentToArchive(name),
                )
                continue
            }
            if (extension !in LOADABLE_EXTENSIONS) {
                findings += Finding(
                    Severity.ERROR,
                    "$name cannot be loaded by OpenMW (.$extension files are not supported). " +
                        "The game will not start.",
                    Fix.DisableEntry("content", name),
                )
                continue
            }
            val file = onDisk[name.lowercase()]
            if (file == null) {
                findings += Finding(
                    Severity.ERROR,
                    "$name is enabled but is not in any data folder. The game will not start.",
                    Fix.DisableEntry("content", name),
                )
                continue
            }
            if (extension == "omwscripts") continue

            for (master in readMasters(file)) {
                val key = master.lowercase()
                val masterIndex = enabledIndex[key]
                when {
                    masterIndex == null && key in disabledContent -> findings += Finding(
                        Severity.ERROR,
                        "$name needs $master, which is disabled. The game will not start.",
                        // Enabling is only a fix when the disabled entry already sits above.
                        if (contentLines.indexOfFirst { it.value.equals(master, true) } <
                            contentLines.indexOfFirst { it.value.equals(name, true) }
                        ) Fix.EnableEntry(master) else Fix.DisableEntry("content", name),
                    )
                    masterIndex == null -> findings += Finding(
                        Severity.ERROR,
                        if (key in onDisk) {
                            "$name needs $master, which is installed but not in the load order. " +
                                "The game will not start."
                        } else {
                            "$name needs $master, which is not installed. The game will not start."
                        },
                        Fix.DisableEntry("content", name),
                    )
                    masterIndex > index -> findings += Finding(
                        Severity.ERROR,
                        "$name loads before its master $master. The game will not start.",
                        Fix.MoveAbove(master, name),
                    )
                }
            }
        }

        // ---- Archives sitting in a data folder that nothing registers ----
        // Only the USER-registered folders: the base game's own archives are the internal file's
        // business, and the internal file always registers Morrowind/Tribunal/Bloodmoon.
        val userFolders = user.filter { it.enabled && it.key == "data" }
            .map { unquotePath(it.value) }.filter { File(it).isDirectory }
        val unregistered = userFolders.flatMap { dir ->
            File(dir).listFiles()?.filter { it.isFile && ext(it.name) == "bsa" }?.map { it.name }.orEmpty()
        }.distinctBy { it.lowercase() }
            .filter { it.lowercase() !in registeredArchives && it.lowercase() !in asContent }
        for (name in unregistered) {
            findings += Finding(
                Severity.INFO,
                "Archive $name is in a data folder but is not registered, so its meshes and " +
                    "textures are not loaded.",
                Fix.RegisterArchive(name),
            )
        }

        // Shown to the player: only folders they chose. The app's own (resources, vfs-mw, delta,
        // the companion mod, the data-local Override) are required, cannot be changed, and Manage
        // folders hides them for the same reason; same list as `manageableFolders`' appManaged.
        val shownFolders = (internal + user).filter { it.enabled && it.key == "data" }
            .map { unquotePath(it.value) }
            .distinctBy { it.lowercase() }
            .filterNot { isAppManaged(it) }
            .map { it to File(it).isDirectory }

        return Result(
            userConfigPath = Constants.USER_OPENMW_CFG,
            dataFolders = shownFolders,
            allDataFolders = folderExists,
            findings = findings.sortedBy { it.severity.ordinal },
        )
    }

    /**
     * Master files named in a TES3 plugin header (`MAST` subrecords of the leading `TES3` record).
     * Anything that is not a TES3 file returns an empty list rather than guessing.
     */
    internal fun readMasters(file: File): List<String> = runCatching {
        RandomAccessFile(file, "r").use { raf ->
            val head = ByteArray(16)
            if (raf.length() < 16) return emptyList()
            raf.readFully(head)
            if (String(head, 0, 4, Charsets.US_ASCII) != "TES3") return emptyList()
            val recordSize = le32(head, 4).coerceIn(0, MAX_HEADER_BYTES)
            val body = ByteArray(recordSize.coerceAtMost((raf.length() - 16).toInt()))
            raf.readFully(body)
            val masters = mutableListOf<String>()
            var p = 0
            while (p + 8 <= body.size) {
                val tag = String(body, p, 4, Charsets.US_ASCII)
                val size = le32(body, p + 4)
                if (size < 0 || p + 8 + size > body.size) break
                if (tag == "MAST") {
                    masters += String(body, p + 8, size, Charsets.ISO_8859_1).trimEnd('\u0000').trim()
                }
                p += 8 + size
            }
            masters.filter { it.isNotEmpty() }
        }
    }.getOrElse {
        Log.w(TAG, "could not read header of ${file.name}: $it")
        emptyList()
    }

    private fun le32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or ((b[o + 3].toInt() and 0xFF) shl 24)

    /**
     * Apply one fix to the USER openmw.cfg. Returns false if the file is missing or the entry the
     * fix names is no longer there (the file changed since the check ran).
     */
    fun apply(fix: Fix): Boolean = runCatching {
        if (!File(Constants.USER_OPENMW_CFG).exists()) return false
        val cfg = OpenMWConfigUtils.getOpenMWConfig().toMutableMap()
        val content = cfg[ConfigKeyType.Content].orEmpty().toMutableList()
        val others = cfg[ConfigKeyType.Others].orEmpty().toMutableList()

        fun bodyOf(line: String) = line.trim().removePrefix(";").trim()
        fun valueOf(line: String) = bodyOf(line).substringAfter("=").trim()
        fun indexOfValue(lines: List<String>, value: String) =
            lines.indexOfFirst { valueOf(it).equals(value, ignoreCase = true) }

        when (fix) {
            is Fix.DisableEntry -> {
                val type = ConfigKeyType.getByKey(fix.key) ?: return false
                val lines = cfg[type].orEmpty().toMutableList()
                val i = lines.indexOfFirst {
                    !it.trim().startsWith(";") && valueOf(it).equals(fix.value, ignoreCase = true)
                }
                if (i < 0) return false
                lines[i] = ";${fix.key}=${valueOf(lines[i])}"
                cfg[type] = lines
            }
            is Fix.EnableEntry -> {
                val i = indexOfValue(content, fix.value)
                if (i < 0) return false
                content[i] = "content=${valueOf(content[i])}"
                cfg[ConfigKeyType.Content] = content
            }
            is Fix.MoveAbove -> {
                val from = indexOfValue(content, fix.master)
                if (from < 0) return false
                val line = content.removeAt(from)
                val to = indexOfValue(content, fix.plugin)
                if (to < 0) return false
                content.add(to, line)
                cfg[ConfigKeyType.Content] = content
            }
            is Fix.ContentToArchive -> {
                val i = indexOfValue(content, fix.value)
                if (i < 0) return false
                val name = valueOf(content.removeAt(i))
                cfg[ConfigKeyType.Content] = content
                if (others.none { bodyOf(it).equals("fallback-archive=$name", ignoreCase = true) }) {
                    others += "fallback-archive=$name"
                }
                cfg[ConfigKeyType.Others] = others
            }
            is Fix.RegisterArchive -> {
                if (others.none { bodyOf(it).equals("fallback-archive=${fix.value}", ignoreCase = true) }) {
                    others += "fallback-archive=${fix.value}"
                }
                cfg[ConfigKeyType.Others] = others
            }
            is Fix.RemoveArchive -> {
                val removed = others.removeAll {
                    !it.trim().startsWith(";") &&
                        it.trim().substringBefore("=").trim() == "fallback-archive" &&
                        valueOf(it).equals(fix.value, ignoreCase = true)
                }
                if (!removed) return false
                cfg[ConfigKeyType.Others] = others
            }
        }
        OpenMWConfigUtils.saveOpenMWConfig(cfg)
        Log.i(TAG, "applied ${fix.label}")
        true
    }.getOrElse {
        Log.w(TAG, "fix failed (${fix.label}): $it")
        false
    }

    /**
     * Remove every enabled USER `fallback-archive=` line whose archive is now in no enabled data
     * folder. Called after a mod folder is unregistered: Add Mods registers a folder's archives, so
     * removing the folder must unregister them too, or the next launch fails on the missing archive.
     * Returns the names removed.
     */
    fun pruneOrphanedArchives(): List<String> = runCatching {
        val folders = (readCfg(Constants.OPENMW_CFG) + readCfg(Constants.USER_OPENMW_CFG))
            .filter { it.enabled && (it.key == "data" || it.key == "data-local") }
            .map { unquotePath(it.value) }
            .filter { File(it).isDirectory }
        val onDisk = folders.flatMap { dir ->
            File(dir).listFiles()?.filter { it.isFile }?.map { it.name.lowercase() }.orEmpty()
        }.toSet()
        val orphans = readCfg(Constants.USER_OPENMW_CFG)
            .filter { it.enabled && it.key == "fallback-archive" && it.value.lowercase() !in onDisk }
            .map { it.value }
        orphans.filter { apply(Fix.RemoveArchive(it)) }
    }.getOrElse {
        Log.w(TAG, "archive prune failed: $it")
        emptyList()
    }

    /** Plain-text summary for the bug report. */
    fun reportText(): String = runCatching {
        val r = run()
        buildString {
            appendLine("User openmw.cfg: ${r.userConfigPath}")
            appendLine("Data folders (engine order):")
            r.allDataFolders.forEach { (p, exists) -> appendLine("  $p${if (exists) "" else "  [MISSING]"}") }
            if (r.findings.isEmpty()) appendLine("No problems found.")
            r.findings.forEach { appendLine("${it.severity}: ${it.message}") }
        }
    }.getOrElse { "Check failed: $it" }
}
