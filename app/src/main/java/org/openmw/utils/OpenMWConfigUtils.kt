package org.openmw.utils

import org.openmw.Constants
import org.openmw.R
import java.io.File

/**
 * Utils for openmw.cfg in
 */
object OpenMWConfigUtils {

    val String.isCommentLine get() = when (trimStart().firstOrNull()) {
        '#', ';', '!' -> true
        else -> false
    }

    val String.isCfgLine get() = this.contains("=") && trim().indexOf('=') > 0

    fun String.removeComment() = if (isCommentLine)
            trimStart().dropWhile { it in "#;!" }.trimStart()
        else this

    fun String.getKey(): String? = if (this.contains("=")) this.substringBefore("=").trim() else null
     fun String.getValue(): String? = if (this.contains("=")) this.substringAfter("=").trim() else null


    enum class ConfigKeyType(
        val key: String,
        val tag: String
    ) { // we do it in order for convenient
        Others("", ""),
        Data("data", stringRes(R.string.cfg_key_data)),
        Content("content", stringRes(R.string.content)),
        GroundCover("groundcover", stringRes(R.string.groundcover));

        companion object {
            val list get() = entries.filter { it != Others }.map { it.key }.toList()
            val entryList get() = entries.filter { it != Others }.toList()
            fun getByKey(key: String): ConfigKeyType? = entries.firstOrNull { it.key == key }
        }
    }

    /**
     * Get openmw.cfg from file to a Map
     * @param filePath path to openmw.cfg
     * @return map of mod configs and other configs String, not consider keeping comments
     */
    fun getOpenMWConfig(filePath: String = Constants.USER_OPENMW_CFG): Map<ConfigKeyType, List<String>> {
        val file = File(filePath)
        if (!file.exists()) return emptyMap<ConfigKeyType, List<String>>()

        val cfgList = file.readLines()
        val (targetLines, otherLines) = cfgList.partition {
            val key = it.removeComment().getKey()
            key != null && key in ConfigKeyType.list
        }

        val dataLines = mutableListOf<String>()
        val contentLines = mutableListOf<String>()
        val groundcoverLines = mutableListOf<String>()

        for (line in targetLines) {
            val key = line.removeComment().getKey()
            when (key) {
                ConfigKeyType.Data.key -> dataLines += line
                ConfigKeyType.Content.key -> contentLines += line
                ConfigKeyType.GroundCover.key -> groundcoverLines += line
            }
        }

        return mapOf(
            ConfigKeyType.Others to otherLines,
            ConfigKeyType.Data to dataLines,
            ConfigKeyType.Content to contentLines,
            ConfigKeyType.GroundCover to groundcoverLines,
        )
    }

    fun saveOpenMWConfig(config: Map<ConfigKeyType, List<String>>, filePath: String = Constants.USER_OPENMW_CFG) {
        val file = File(filePath)
        if (!file.exists()) {
            file.createNewFile()
        }

        val outputLines = mutableListOf<String>()

        // write others, data、content、groundcover
        ConfigKeyType.entries.forEachIndexed { index, keyType ->
            val lines = config[keyType].orEmpty()
            outputLines.addAll(lines)

            /*if (index < ConfigKeyType.list.lastIndex && lines.isNotEmpty()) {
                outputLines.add("")
            }*/
        }

        file.writeText(outputLines.joinToString("\n"))
    }

}