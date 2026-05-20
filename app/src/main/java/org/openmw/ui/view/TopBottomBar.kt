package org.openmw.ui.view

import android.content.Context
import android.system.Os
import android.util.Log
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmw.Constants
import org.openmw.Constants.SETTINGS_FILE
import org.openmw.R
import org.openmw.isControllerConnected
import org.openmw.ui.controls.UIStateManager
import org.openmw.ui.controls.UIStateManager.editMode
import org.openmw.ui.controls.UIStateManager.isAppLoggingEnabled
import org.openmw.ui.controls.UIStateManager.isTabExpanded
import org.openmw.ui.controls.UIStateManager.launchedActivity
import org.openmw.ui.controls.UIStateManager.transparentBlack
import org.openmw.ui.page.setting.CodeGroupOptionSelector
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.GameFilesPreferences.readCodeGroup
import org.openmw.utils.MToast
import org.openmw.utils.UserManageAssets
import org.openmw.utils.startGame
import org.openmw.utils.stringRes
import java.io.File

@InternalCoroutinesApi
@ExperimentalMaterial3Api
@Composable
fun MyTopBar(context: Context) {
    val controllerConnected = isControllerConnected(context)
    TopAppBar(
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = transparentBlack,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        title = {
            Text(
                stringRes(R.string.launcher_topbar_title),
                maxLines = 1,
                color = Color.White,
                overflow = TextOverflow.Ellipsis
            )
            //CodeGroupOptionSelector()
        },
        actions = {
            if (controllerConnected) {
                Image(
                    painter = painterResource(id = R.drawable.stadia_controller_24dp_e8eaed_fill0_wght400_grad0_opsz24), // Replace with your icon resource ID
                    contentDescription = "Icon",
                    modifier = Modifier.size(24.dp),
                    colorFilter = ColorFilter.tint(Color.Green) // Tint the icon green
                )
            }
            BarOptions(context)
        },
        modifier = Modifier
    )
}

@Composable
fun NavmeshScreen(onComplete: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ProgressWithNavmesh(onComplete = onComplete)
    }
}

@InternalCoroutinesApi
@ExperimentalMaterial3Api
@Composable
fun MyFloatingActionButton() {
    val context = LocalContext.current
    val savedPath by GameFilesPreferences.getGameFilesUriState(context).collectAsState(initial = null)
    val codeGroupOption by readCodeGroup(context).collectAsState(initial = "OpenMW")
    val bypassGameCheck by GameFilesPreferences.loadBypassGameCheck(context).collectAsState(initial = false)
    var isCopyingResources by remember { mutableStateOf(false) } // Track copying state
    val coroutineScope = rememberCoroutineScope()

    val image: Painter = when (codeGroupOption) {
        "OpenMW" -> painterResource(id = R.drawable.ic_launcher_foreground)
        "UQM" -> painterResource(id = R.drawable.dreadnought_big_004)
        else -> painterResource(id = R.drawable.ic_launcher_foreground)
    }

    FloatingActionButton(
        onClick = {
            coroutineScope.launch {
                UIStateManager.configureControls = false
                launchedActivity = true
                if (codeGroupOption == "OpenMW") {
                    if (!bypassGameCheck) {
                        val uri = savedPath
                        if (uri != null) {
                            val morrowindEsm = File(uri, "Morrowind.esm")
                            val morrowindEsmFallback = File(uri, "/Data Files/Morrowind.esm")
                            if (morrowindEsm.exists() || morrowindEsmFallback.exists()) {
                                isCopyingResources = true // Start copying
                                withContext(Dispatchers.Default) {
                                    UserManageAssets(context).resourcePrepare()
                                }
                                isCopyingResources = false // Copying complete
                                editMode = false
                                isAppLoggingEnabled = false
                                context.startGame()
                            } else {
                                MToast(stringRes(R.string.morrowind_folder_not_found_tip))
                            }
                        }
                    } else {
                        isCopyingResources = true // Start copying
                        withContext(Dispatchers.Default) {
                            UserManageAssets(context).resourcePrepare()
                        }
                        isCopyingResources = false // Copying complete

                        isAppLoggingEnabled = false
                        context.startGame()

                    }
                } else  {
                    isAppLoggingEnabled = false
                    context.startGame()
                }
            }
        },
        containerColor = Color(alpha = 0.6f, red = 0f, green = 0f, blue = 0f),
        elevation = FloatingActionButtonDefaults.bottomAppBarFabElevation()
    ) {
        if (isCopyingResources) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp), // Adjust size as needed
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp
            )
        } else {
            Image(
                painter = image,
                contentDescription = "Launch"
            )
        }
    }
}

@InternalCoroutinesApi
@ExperimentalMaterial3Api
@Composable
fun BarOptions (context: Context) {
    var expanded by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    val settingsFile = File(SETTINGS_FILE)
    val codeGroupOption by readCodeGroup(context).collectAsState(initial = "OpenMW")

    if (isAppLoggingEnabled) {
        IconButton(onClick = { isTabExpanded = !isTabExpanded }) {
            Icon(
                imageVector = if (isTabExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = "Toggle Tab",
                tint = Color.White
            )
        }
    }

    IconButton(onClick = { expanded = true }) {
        Icon(
            imageVector = Icons.Filled.Menu,
            modifier = Modifier,
            contentDescription = "Localized description"
        )
    }

    DropdownMenu(
        modifier = Modifier
            .background(color = transparentBlack)
            .border(
                BorderStroke(width = 1.dp, color = Color.Black)
            ),
        expanded = expanded,
        onDismissRequest = { expanded = false },
    ) {
        DropdownMenuItem(
            text = { Text(stringRes(R.string.env_args), color = Color.White) },
            onClick = {
                "".moeDialog(
                    title = stringRes(R.string.env_args),
                    content = {
                        CommandLineInputScreen(context)
                    },
                    confirmLabel = stringRes(R.string.close),
                    onConfirm = {}
                )
            }
        )
        if (codeGroupOption == "OpenMW") {
            DropdownMenuItem(
                text = {
                    val navmeshFile = File("${Constants.USER_FILE_STORAGE}/navmesh.db")
                    if (navmeshFile.exists()) {
                        val fileSize = navmeshFile.length()

                        Text("${stringResource(R.string.regenerate_navmesh)} \n(${stringRes(R.string.size)}: ${fileSize / 1024} KB)", color = Color.White) // Display size in KB
                    } else {
                        Text(stringResource(R.string.generate_navmesh), color = Color.White)
                    }
                },
                onClick = {
                    // Delete the navmesh file if it exists
                    val navmeshFile = File("${Constants.USER_FILE_STORAGE}/navmesh.db")
                    if (navmeshFile.exists()) {
                        navmeshFile.delete()
                    }

                    // Set environment variable to start navmesh generation
                    Os.setenv("OPENMW_GENERATE_NAVMESH_CACHE", "1", true)

                    UIStateManager.useNavmesh = true

                    // Start the navmesh activity
                    context.startGame()
                }
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reset_settings), color = Color.White) },
                onClick = {
                    showDialog = true
                }
            )
        }
        DropdownMenuItem(
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.enable_logcat), color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Switch(
                        checked = UIStateManager.isLogcatEnabled,
                        onCheckedChange = {
                            UIStateManager.isLogcatEnabled = it
                        }
                    )
                }
            },
            onClick = { /* Handle click if necessary */ }
        )
        CodeGroupOptionSelector()
        if (showDialog) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(stringRes(R.string.confirm_reset)) },
                text = { Text(stringRes(R.string.reset_the_settings_tip)) },
                confirmButton = {
                    Button(
                        onClick = {
                            if (settingsFile.exists()) {
                                settingsFile.delete()
                                Log.d("ManageAssets", "Deleted existing file: $SETTINGS_FILE")
                                // Copy over settings.cfg
                                UserManageAssets(context).resetUserConfig()
                                expanded = false
                            }
                            MToast(stringRes(R.string.settings_file_reset))
                            showDialog = false
                        }
                    ) {
                        Text(stringRes(R.string.btn_confirm))
                    }
                },
                dismissButton = {
                    Button(
                        onClick = { showDialog = false }
                    ) {
                        Text(stringRes(R.string.btn_cancel))
                    }
                }
            )
        }
    }
}
