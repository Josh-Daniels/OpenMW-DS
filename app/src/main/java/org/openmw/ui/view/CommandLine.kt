package org.openmw.ui.view

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.openmw.Constants
import org.openmw.R
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.GameFilesPreferences.saveARGLine
import org.openmw.utils.GameFilesPreferences.saveENVLine
import java.io.File

@Composable
fun CommandLineInputScreen(context: Context) {
    var argLine by remember { mutableStateOf("") }
    val selectedOptions = remember { mutableStateListOf<String>() }
    var envLine by remember { mutableStateOf("") }
    var allOptions by remember { mutableStateOf(listOf(
        "--OSG_NOTIFY_LEVEL"
    )) }

    val optionDescriptions = mapOf(
        "OSG_NOTIFY_LEVEL" to "Sets the notification level for OpenSceneGraph."
    )

    var isEditingEnv by remember { mutableStateOf(true) }
    var showDialog by remember { mutableStateOf(false) }
    var showDialogENV by remember { mutableStateOf(false) }
    val saveGameOptions by remember { mutableStateOf(emptyList<String>()) }
    val allOptionsCombined = allOptions + saveGameOptions

    Row(modifier = Modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        // Button styled as text field
        // Toggle button
        Box(Modifier.align(Alignment.CenterVertically)) {
            Button(
                onClick = { isEditingEnv = !isEditingEnv },
                modifier = Modifier
                    .size(48.dp),
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    text = if (isEditingEnv) "ENV" else "ARG",
                    style = MaterialTheme.typography.labelSmall
                )
            }
            Icon(
                imageVector = Icons.Default.Autorenew,
                contentDescription = "Change between Env and Args",
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                tint = MaterialTheme.colorScheme.onPrimary
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .background(MaterialTheme.colorScheme.surface, shape = MaterialTheme.shapes.small)
                .clickable {
                    if (isEditingEnv) {
                        showDialogENV = true
                    } else {
                        showDialog = true
                    }
                }
                .padding(16.dp)
        ) {
            Text(
                text = if (isEditingEnv) stringResource(R.string.enter_environmental_variables)
                else if (argLine.isEmpty()) stringResource(R.string.enter_command_args)
                else argLine,
                style = MaterialTheme.typography.bodySmall,
                color = if (argLine.isEmpty()) Color.Gray else Color.White
            )
        }

        // Scan for save games and add them to the options list
        LaunchedEffect(Unit) {
            val saveGames = scanSaveGames()
            val dynamicOptions = saveGames.map { "--load-savegame $it" }
            allOptions = allOptions + dynamicOptions
        }

        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(text = stringResource(R.string.enter_arguments)) },
                text = {
                    Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Column {
                            OutlinedTextField(
                                value = argLine,
                                onValueChange = {
                                    argLine = it
                                    val parts = argLine.split(" ").filter { it in allOptionsCombined }
                                    selectedOptions.clear()
                                    selectedOptions.addAll(parts)
                                },
                                label = { Text(stringResource(R.string.arguments)) },
                                modifier = Modifier.fillMaxWidth(),
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            allOptions.forEach { option ->
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = selectedOptions.contains(option),
                                            onCheckedChange = {
                                                if (selectedOptions.contains(option)) {
                                                    selectedOptions.remove(option)
                                                } else {
                                                    selectedOptions.add(option)
                                                }
                                                val textFieldParts = argLine.split(" ").filter { it !in allOptions }
                                                argLine = (textFieldParts + selectedOptions).distinct().joinToString(" ")
                                            }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = option,
                                            color = if (selectedOptions.contains(option)) Color.Green else Color.White
                                        )
                                        if (option !in optionDescriptions.keys) {
                                            IconButton(onClick = {
                                                runBlocking {
                                                    GameFilesPreferences.deleteUserOption(context, option)
                                                    allOptions = allOptions - option
                                                }
                                                // Also remove the option from the commandLine and selectedOptions
                                                selectedOptions.remove(option)
                                                val textFieldParts = argLine.split(" ").filter { it != option }
                                                argLine = textFieldParts.joinToString(" ")
                                            }) {
                                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                                            }
                                        }
                                    }
                                    Text(
                                        text = optionDescriptions[option] ?: "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(start = 32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val finalCommandLine = argLine
                            runBlocking {
                                // Save command line
                                saveARGLine(context, finalCommandLine)

                                // Extract new options without leading/trailing spaces and avoid empty entries
                                val newOptions = argLine.split(" ").map { it.trim() }.filter { it.isNotEmpty() && it !in allOptions }.toSet()
                                if (newOptions.isNotEmpty()) {
                                    GameFilesPreferences.saveUserOptions(context, newOptions)
                                    allOptions = (allOptions + newOptions).distinct()
                                }
                            }
                            showDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
                dismissButton = {
                    Button(onClick = { showDialog = false }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }

        if (showDialogENV) {
            AlertDialog(
                onDismissRequest = { showDialogENV = false },
                title = { Text(text = stringResource(R.string.environmental_variables)) },
                text = {
                    Box(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        Column {
                            OutlinedTextField(
                                value = envLine, // Use envLine directly
                                onValueChange = { newValue ->
                                    envLine = newValue // Update state with user input
                                },
                                label = { Text(stringResource(R.string.enter_environmental_variables)) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            CoroutineScope(Dispatchers.IO).launch {
                                saveENVLine(context, envLine) // Save the envLine string
                            }
                            showDialogENV = false
                        }
                    ) {
                        Text(stringResource(R.string.save))
                    }
                },
                dismissButton = {
                    Button(onClick = { showDialogENV = false }) {
                        Text(stringResource(R.string.btn_cancel))
                    }
                }
            )
        }


    }
}

fun scanSaveGames(): List<String> {
    val saveGames = mutableListOf<String>()
    val savesDirectory = File(Constants.USER_SAVES)

    if (savesDirectory.exists() && savesDirectory.isDirectory) {
        savesDirectory.walk().forEach { file ->
            if (file.extension == "omwsave") {
                saveGames.add(file.absolutePath)
            }
        }
    }

    return saveGames.distinct() // Ensure the list contains unique paths
}



