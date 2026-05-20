package org.openmw.utils

import android.annotation.SuppressLint
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openmw.Constants
import org.openmw.R
import org.openmw.ui.controls.UIStateManager.customColor
import org.openmw.ui.controls.UIStateManager.darkGray
import org.openmw.ui.controls.UIStateManager.lightGray
import java.io.File

class IniConverter(private val data: String) {
    fun convert(): String {
        return data
            .lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith(";") }
            .fold(Pair("", "")) { acc, line ->
                if (line.startsWith("[") && line.endsWith("]")) {
                    acc.copy(first = line.substring(1, line.length - 1).replace(" ", "_"))
                } else if (line.contains("=")) {
                    val converted = convertLine(line)
                    if (converted.isNotEmpty()) {
                        acc.copy(second = acc.second + "fallback=${acc.first}_$converted\n")
                    } else acc
                } else acc
            }.second
    }

    private fun convertLine(line: String): String {
        val (key, value) = line.split("=", limit = 2)
        if (key.isBlank() || value.isBlank()) return ""
        return "${key.replace(" ", "_")},$value"
    }
}

private fun readIniValues(): Map<String, List<Triple<String, Any, String?>>> {
    val settings = mutableMapOf<String, MutableList<Triple<String, Any, String?>>>()
    val comments = mutableMapOf<String, String?>()
    val sections = mutableMapOf<String, MutableMap<String, String>>()
    var currentSection: String? = null
    var lastKey: String? = null

    // Define your blacklist here
    val blacklist = setOf("key1", "key2", "key3")

    File(Constants.SETTINGS_FILE).forEachLine { line ->
        val trimmedLine = line.trim()
        when {
            trimmedLine.startsWith("[") && trimmedLine.endsWith("]") -> {
                currentSection = trimmedLine.substring(1, trimmedLine.length - 1).trim()
                sections[currentSection!!] = mutableMapOf()
            }
            trimmedLine.startsWith("#") -> {
                val comment = trimmedLine.substring(1).trim()
                if (lastKey != null && lastKey !in blacklist) {
                    comments[lastKey!!] = comment
                }
            }
            "=" in trimmedLine -> {
                val (key, value) = trimmedLine.split("=", limit = 2).map { it.trim() }
                if (currentSection != null && key !in blacklist) {
                    sections[currentSection]!![key] = value
                    lastKey = key
                } else {
                    lastKey = null // Reset lastKey if the key is blacklisted
                }
            }
        }
    }
    sections.forEach { (section, properties) ->
        val sectionSettings = properties.mapNotNull { (key, value) ->
            if (key in blacklist) return@mapNotNull null
            val parsedValue: Any = when {
                value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true) -> value.toBoolean()
                value.toIntOrNull() != null -> value.toInt()
                value.toFloatOrNull() != null -> value.toFloat()
                else -> value
            }
            Triple(key, parsedValue, comments[key])
        }
        settings[section] = sectionSettings.toMutableList()
    }
    return settings
}


fun writeIniValue(section: String, key: String, value: Any, comment: String?) {
    val settingsFile = File(Constants.SETTINGS_FILE)
    val lines = settingsFile.readLines().toMutableList()
    var sectionFound = false
    var keyFound = false
    var sectionEndIndex = lines.size

    for (i in lines.indices) {
        val line = lines[i].trim()
        if (line.startsWith("[") && line.endsWith("]")) {
            if (sectionFound) {
                sectionEndIndex = i
                break
            }
            if (line.substring(1, line.length - 1).trim() == section) {
                sectionFound = true
            }
        } else if (sectionFound && line.startsWith(key)) {
            lines[i] = "${key.trim()} = ${value.toString().trim()}"
            keyFound = true
            if (comment != null) {
                val commentIndex = i + 1
                if (commentIndex < lines.size && lines[commentIndex].trim() != comment.trim()) {
                    lines.add(commentIndex, comment.trim())
                    lines.add(commentIndex + 1, "") // Add an empty line after the comment
                }
            }
            break
        }
    }

    if (!sectionFound) {
        lines.add("[${section.trim()}]")
        lines.add("${key.trim()} = ${value.toString().trim()}")
        if (comment != null) {
            lines.add(comment.trim())
            lines.add("") // Add an empty line after the comment
        }
    } else if (!keyFound) {
        lines.add(sectionEndIndex, "${key.trim()} = ${value.toString().trim()}")
        if (comment != null) {
            lines.add(sectionEndIndex + 1, comment.trim())
            lines.add(sectionEndIndex + 2, "") // Add an empty line after the comment
        }
    }
    settingsFile.writeText(lines.joinToString("\n"))
}

@SuppressLint("MutableCollectionMutableState")
@Composable
fun ReadAndDisplayIniValues() {
    var isColumnExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.Black)
            .background(color = customColor)
            .clickable { isColumnExpanded = !isColumnExpanded }, // Toggle column expansion
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = customColor)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.openmw_settings),
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = Color.White
            )
            Icon(
                imageVector = if (isColumnExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isColumnExpanded) "Collapse" else "Expand"
            )
        }
        if (isColumnExpanded) {
            IniSettings()
        }
    }
}

@Composable
fun IniSettings() {
    val settings = remember { mutableStateOf(readIniValues()) }
    val view = LocalView.current
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val translationChecked by GameFilesPreferences.loadTranslationState(context).collectAsState(initial = false)
    LazyColumn {
        items(settings.value.entries.toList()) { (section, sectionSettings) ->
            var isExpanded by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier
                    .background(color = customColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.Black)
                        .clickable { isExpanded = !isExpanded }, // Toggle expansion
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = section,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = Color.White,
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                            vertical = 2.dp
                        )
                    )
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.padding(
                            horizontal = 16.dp,
                            vertical = 10.dp
                        )
                    )
                }
                if (isExpanded) {
                    sectionSettings.forEachIndexed { index, (propertyKey, value, comment) ->
                        val extractedPropertyKey =
                            propertyKey.substringAfterLast('.')
                        val backgroundColor = if (index % 2 == 0) darkGray else lightGray
                        settings.value = readIniValues()

                        var translatedText by remember { mutableStateOf("") }
                        var translatedTextCOM by remember { mutableStateOf("") }
                        if (translationChecked) {
                            TranslateText(
                                context = context,
                                inputText = extractedPropertyKey, // Pass modValue.value here
                                onTranslationResult = { result ->
                                    translatedText =
                                        result // Update the state with the translated text
                                }
                            )
                            TranslateText(
                                context = context,
                                inputText = "$comment", // Pass modValue.value here
                                onTranslationResult = { result ->
                                    translatedTextCOM =
                                        result // Update the state with the translated text
                                }
                            )
                        }

                        when (value) {
                            is Boolean -> {
                                var switchState by remember { mutableStateOf(value) }
                                Column(
                                    modifier = Modifier
                                        .background(color = backgroundColor)
                                        .fillMaxWidth()
                                        .border(2.dp, Color.Black)
                                        .padding(bottom = 16.dp) // Add space between each iteration
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(1.dp, Color.Black),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (translationChecked) translatedText else extractedPropertyKey,
                                            fontWeight = FontWeight.Bold,
                                            color = if (value.toString() == "false") Color.Red else Color.Green,
                                            fontSize = 16.sp,
                                            modifier = Modifier.padding(start = 10.dp, top = 4.dp)
                                        )
                                        Spacer(modifier = Modifier.weight(1f))
                                        Switch(
                                            checked = switchState,
                                            onCheckedChange = {
                                                switchState = it

                                                writeIniValue(section, extractedPropertyKey, it, null)

                                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                                    view.performHapticFeedback(
                                                        HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                                                    )
                                                }

                                                settings.value = readIniValues() // Reload settings
                                            },
                                            colors = SwitchDefaults.colors(
                                                checkedThumbColor = MaterialTheme.colorScheme.primary,
                                                checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                                                uncheckedThumbColor = MaterialTheme.colorScheme.secondary,
                                                uncheckedTrackColor = MaterialTheme.colorScheme.secondaryContainer,
                                            )
                                        )
                                    }
                                    if (comment != null) {
                                        Text(
                                            text = if (translationChecked) translatedTextCOM else comment,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(start = 10.dp, top = 4.dp)
                                        )
                                    }
                                }
                            }

                            is Float -> {
                                var floatValue by remember { mutableStateOf(value.toString()) }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color.Black),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (translationChecked) translatedText else extractedPropertyKey,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Green,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(
                                            start = 10.dp,
                                            top = 4.dp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextField(
                                        value = floatValue,
                                        onValueChange = {
                                            floatValue = it
                                            val floatVal =
                                                it.toFloatOrNull() ?: 0.0f
                                            writeIniValue(
                                                section,
                                                extractedPropertyKey,
                                                floatVal,
                                                null
                                            )
                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                                            }

                                            settings.value = readIniValues() // Reload settings

                                        },
                                        label = { Text(stringResource(R.string.enter_value)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                focusManager.clearFocus()
                                            }
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (comment != null) {
                                        Text(
                                            text = if (translationChecked) translatedTextCOM else comment,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(start = 10.dp, top = 4.dp)
                                        )
                                    }
                                }
                            }

                            is Int -> {
                                var intValue by remember { mutableStateOf(value.toString()) }
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.dp, Color.Black),
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Text(
                                        text = if (translationChecked) translatedText else extractedPropertyKey,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Green,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(
                                            start = 10.dp,
                                            top = 4.dp
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    TextField(
                                        value = intValue,
                                        onValueChange = {
                                            intValue = it
                                            val intVal = it.toIntOrNull() ?: 0
                                            writeIniValue(
                                                section,
                                                extractedPropertyKey,
                                                intVal,
                                                null
                                            )

                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                                view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                                            }
                                            settings.value = readIniValues() // Reload settings
                                        },
                                        label = { Text(stringResource(R.string.enter_value)) },
                                        modifier = Modifier.fillMaxWidth(),
                                        keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                focusManager.clearFocus()
                                            }
                                        )
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    if (comment != null) {
                                        Text(
                                            text = if (translationChecked) translatedTextCOM else comment,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(start = 10.dp, top = 4.dp)
                                        )
                                    }
                                }
                            }

                            else -> {
                                Text(
                                    text = if (translationChecked) "$translatedText = $value" else "$extractedPropertyKey = $value",
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(
                                        start = 10.dp,
                                        top = 4.dp
                                    )
                                )
                                if (comment != null) {
                                    Text(
                                        text = if (translationChecked) translatedTextCOM else comment,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 16.sp,
                                        modifier = Modifier.padding(
                                            start = 24.dp,
                                            top = 4.dp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}