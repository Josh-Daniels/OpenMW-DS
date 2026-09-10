package org.openmw.utils

import android.annotation.SuppressLint
import android.os.Build
import androidx.annotation.StringRes
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.input.KeyboardType
import org.openmw.R
import androidx.compose.ui.unit.dp
import org.openmw.Constants
import org.openmw.ui.controls.UIStateManager.darkGray
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
    var pendingComment: String? = null

    // Define your blacklist here
    val blacklist = setOf("key1", "key2", "key3")

    File(Constants.SETTINGS_FILE).forEachLine { line ->
        val trimmedLine = line.trim()
        when {
            trimmedLine.startsWith("[") && trimmedLine.endsWith("]") -> {
                currentSection = trimmedLine.substring(1, trimmedLine.length - 1).trim()
                sections[currentSection!!] = mutableMapOf()
                pendingComment = null
            }
            trimmedLine.startsWith("#") -> {
                val commentText = trimmedLine.substring(1).trim()
                pendingComment = if (pendingComment == null) commentText else "$pendingComment\n$commentText"
            }
            "=" in trimmedLine -> {
                val (key, value) = trimmedLine.split("=", limit = 2).map { it.trim() }
                if (currentSection != null && key !in blacklist) {
                    sections[currentSection]!![key] = value
                    if (pendingComment != null) {
                        comments[key] = pendingComment
                        pendingComment = null
                    }
                }
            }
            trimmedLine.isEmpty() -> {
                pendingComment = null // Blank line breaks the association between comment and next key
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
        } else if (sectionFound && "=" in line) {
            val lineKey = line.split("=", limit = 2)[0].trim()
            if (lineKey == key) {
                lines[i] = "${key.trim()} = ${value.toString().trim()}"
                keyFound = true
                if (comment != null) {
                    val formattedComment = if (comment.startsWith("#")) comment.trim() else "# ${comment.trim()}"
                    // Check if comment already exists above (either directly or separated by other comments)
                    var existingCommentIndex = -1
                    for (j in (i - 1) downTo 0) {
                        val prevLine = lines[j].trim()
                        if (prevLine == formattedComment) {
                            existingCommentIndex = j
                            break
                        }
                        if (!prevLine.startsWith("#") && prevLine.isNotEmpty()) break
                    }

                    if (existingCommentIndex == -1) {
                        lines.add(i, formattedComment)
                    }
                }
                break
            }
        }
    }

    if (!sectionFound) {
        lines.add("[${section.trim()}]")
        if (comment != null) {
            val formattedComment = if (comment.startsWith("#")) comment.trim() else "# ${comment.trim()}"
            lines.add(formattedComment)
        }
        lines.add("${key.trim()} = ${value.toString().trim()}")
    } else if (!keyFound) {
        if (comment != null) {
            val formattedComment = if (comment.startsWith("#")) comment.trim() else "# ${comment.trim()}"
            lines.add(sectionEndIndex, formattedComment)
            lines.add(sectionEndIndex + 1, "${key.trim()} = ${value.toString().trim()}")
        } else {
            lines.add(sectionEndIndex, "${key.trim()} = ${value.toString().trim()}")
        }
    }
    settingsFile.writeText(lines.joinToString("\n"))
}

/**
 * The Settings.cfg editor: parses settings.cfg, renders every section as a collapsible card, and
 * writes edits straight back. Self-contained and layout-agnostic — it is just a `Column`, so it
 * drops into any host.
 *
 * [externalSearchQuery] lets a host supply the search text and render its own search field (the
 * simplified launcher's settings screen pins search above its scroll area). Leave it null — the
 * default — to keep the built-in search field and internal query state, which is what the Alpha3
 * settings page uses; that path is unchanged.
 */
@Composable
fun IniSettings(externalSearchQuery: String? = null) {
    var settings by remember { mutableStateOf(readIniValues()) }
    var internalSearchQuery by remember { mutableStateOf("") }
    val searchQuery = externalSearchQuery ?: internalSearchQuery
    val view = LocalView.current
    val context = LocalContext.current
    val translationChecked by GameFilesPreferences.loadTranslationState(context)
        .collectAsState(initial = false)

    val filteredSettings = remember(settings, searchQuery) {
        if (searchQuery.isBlank()) {
            settings
        } else {
            settings.mapValues { (_, sectionSettings) ->
                sectionSettings.filter { (key, _, comment) ->
                    key.contains(searchQuery, ignoreCase = true) ||
                            (comment?.contains(searchQuery, ignoreCase = true) == true)
                }
            }.filterValues { it.isNotEmpty() }
        }
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Search Bar — skipped when the host supplies the query and renders its own field.
        if (externalSearchQuery == null) {
            OutlinedTextField(
                value = internalSearchQuery,
                onValueChange = { internalSearchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                placeholder = { Text("Search settings...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray
                )
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            filteredSettings.entries.forEach { (section, sectionSettings) ->
                IniSectionCard(
                    section = section,
                    sectionSettings = sectionSettings,
                    translationChecked = translationChecked,
                    onValueChange = { key, newValue ->
                        writeIniValue(section, key, newValue, null)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                            view.performHapticFeedback(HapticFeedbackConstants.SEGMENT_FREQUENT_TICK)
                        }
                        settings = readIniValues()
                    }
                )
            }
        }
    }
}

@Composable
fun IniSectionCard(
    section: String,
    sectionSettings: List<Triple<String, Any, String?>>,
    translationChecked: Boolean,
    onValueChange: (String, Any) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = darkGray.copy(alpha = 0.9f)
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = section,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = Color.White
                )
            }

            if (isExpanded) {
                HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f))
                Column(modifier = Modifier.padding(8.dp)) {
                    sectionSettings.forEachIndexed { index, (key, value, comment) ->
                        IniSettingItem(
                            section = section,
                            propertyKey = key,
                            value = value,
                            comment = comment,
                            translationChecked = translationChecked,
                            onValueChange = { onValueChange(key, it) }
                        )
                        if (index < sectionSettings.size - 1) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 8.dp),
                                color = Color.Gray.copy(alpha = 0.2f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Keys this editor shows but does not let you EDIT, because something else owns them.
 *
 * `[Video] resolution x/y` is written authoritatively on every launcher start by
 * `applyGameScreenResolution()`, from the live game display's real size and the player's Resolution
 * tier. Any edit made here is therefore silently reverted at the next launch, which is worse than
 * not offering it: the row looks like the live control and is not.
 *
 * **Shown-but-locked rather than hidden, deliberately.** These rows are not a hand-placed option
 * that could simply be deleted — this editor enumerates every key in `settings.cfg`, so hiding them
 * would mean teaching a generic editor to lie about the file's contents. The value is real and
 * worth seeing; only the illusion of control is removed. Use the Resolution setting in the
 * launcher's Settings instead.
 */
/**
 * Normalise a section name for matching.
 *
 * **`readIniValues` stores sections with the BRACKETS STRIPPED** (`"Video"`, not `"[Video]"`) — see
 * the `substring(1, length - 1)` where it parses a `[Section]` header. Matching against `"[Video]"`
 * silently matches nothing, which is exactly how the first cut of these notes shipped invisible.
 * Accept either spelling so the trap cannot be re-sprung by whichever form a caller has to hand.
 */
private fun normalisedSection(section: String): String =
    section.trim().removePrefix("[").removeSuffix("]").trim().lowercase()

private fun isManagedByResolutionSetting(section: String, propertyKey: String): Boolean {
    val key = propertyKey.substringAfterLast('.').trim().lowercase()
    val sec = normalisedSection(section)
    return (sec == "video" && (key == "resolution x" || key == "resolution y")) ||
        // Owned by the tier too, and for a reason that is not obvious from the key: MyGUI lays out
        // on a logical canvas of `resolution / scaling factor`, so a tier that cut the resolution
        // without cutting this would shrink that canvas and vanilla windows would stop FITTING.
        // `applyGuiScalingForTier` derives it from a base kept in the DataStore. An edit here would
        // be reverted on the next launcher start, exactly like the resolution rows.
        (sec == "gui" && key == "scaling factor")
}

/**
 * An advisory note for a key this project has MEASURED a best value for.
 *
 * Not a lock: these stay fully editable. The point is that a value chosen from profiling looks
 * exactly like an arbitrary default once it is a number in a generic list, so someone tuning by
 * hand has no way to know they are about to undo a measured result. The note carries the number and
 * the evidence, so changing it is an informed decision rather than an accidental one.
 *
 * Keep these in step with `applyTunedPerformanceSettings` in `UITools.kt`, which is what actually
 * writes them (once per version, so the player owns the key afterwards) and where the full
 * measurement record lives.
 */
@StringRes
private fun iniSettingHint(section: String, propertyKey: String): Int? {
    val key = propertyKey.substringAfterLast('.').trim().lowercase()
    return when {
        normalisedSection(section) == "water" && key == "reflection detail" ->
            R.string.ini_hint_water_reflection
        else -> null
    }
}

@Composable
fun IniSettingItem(
    section: String,
    propertyKey: String,
    value: Any,
    comment: String?,
    translationChecked: Boolean,
    onValueChange: (Any) -> Unit
) {
    val context = LocalContext.current
    val extractedKey = propertyKey.substringAfterLast('.')
    val managedElsewhere = isManagedByResolutionSetting(section, propertyKey)
    val hint = iniSettingHint(section, propertyKey)
    
    var translatedKey by remember { mutableStateOf(extractedKey) }
    var translatedComment by remember { mutableStateOf(comment ?: "") }

    if (translationChecked) {
        TranslateText(context, extractedKey) { translatedKey = it }
        if (comment != null) {
            TranslateText(context, comment) { translatedComment = it }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = translatedKey,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (comment != null) {
                    Text(
                        text = translatedComment,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.LightGray,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (managedElsewhere) {
                    Text(
                        text = stringResource(R.string.ini_managed_by_resolution),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Cyan,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                if (hint != null) {
                    Text(
                        text = stringResource(hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFD9A441),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            when (value) {
                is Boolean -> {
                    Switch(
                        checked = value,
                        onCheckedChange = { onValueChange(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    )
                }
                is Int, is Float -> {
                    var textValue by remember(value) { mutableStateOf(value.toString()) }
                    val focusManager = LocalFocusManager.current

                    OutlinedTextField(
                        value = textValue,
                        // readOnly rather than enabled=false: the value stays legible at full
                        // contrast (it is real, and worth reading) while the field refuses edits
                        // and never raises a keyboard. A disabled field greys the number out, which
                        // reads as "no value" rather than "not yours to set".
                        readOnly = managedElsewhere,
                        onValueChange = {
                            if (managedElsewhere) return@OutlinedTextField
                            textValue = it
                            if (value is Int) {
                                it.toIntOrNull()?.let { onValueChange(it) }
                            } else {
                                it.toFloatOrNull()?.let { onValueChange(it) }
                            }
                        },
                        modifier = Modifier.width(100.dp),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color.White),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Done,
                            keyboardType = if (value is Int) KeyboardType.Number 
                                          else KeyboardType.Decimal
                        ),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray
                        )
                    )
                }
                else -> {
                    Text(
                        text = value.toString(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Cyan
                    )
                }
            }
        }
    }
}
