@file:OptIn(InternalCoroutinesApi::class)

package org.openmw.ui.page.setting

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import org.openmw.Constants
import org.openmw.MainActivity
import org.openmw.R
import org.openmw.isControllerConnected
import org.openmw.ui.controls.UIKeyboard
import org.openmw.ui.controls.UIStateManager
import org.openmw.ui.controls.UIStateManager.configureControls
import org.openmw.ui.controls.UIStateManager.customColor
import org.openmw.ui.controls.UIStateManager.gameList
import org.openmw.ui.controls.UIStateManager.launchedActivity
import org.openmw.ui.controls.UIStateManager.tempCodeGroup
import org.openmw.ui.controls.UIStateManager.uqmJNI
import org.openmw.ui.controls.stringToShape
import org.openmw.ui.overlay.AnimationSettings
import org.openmw.ui.page.main.OpenMW
import org.openmw.ui.page.mod.LandscapeSettings
import org.openmw.ui.view.BouncingBackground
import org.openmw.ui.view.CircularBackground
import org.openmw.ui.view.MyTopBar
import org.openmw.ui.view.NoneBackground
import org.openmw.ui.view.RotatingImageBackground
import org.openmw.utils.FileBrowserPopup
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.GameFilesPreferences.getBackgroundAnimationFlow
import org.openmw.utils.GameFilesPreferences.getLanguageFlow
import org.openmw.utils.GameFilesPreferences.getQuickSlot
import org.openmw.utils.GameFilesPreferences.getScreenStayOn
import org.openmw.utils.GameFilesPreferences.getSystemBars
import org.openmw.utils.GameFilesPreferences.loadAutoMouseMode
import org.openmw.utils.GameFilesPreferences.loadButtonShape
import org.openmw.utils.GameFilesPreferences.readCodeGroup
import org.openmw.utils.GameFilesPreferences.readTextureShrinkingOption
import org.openmw.utils.GameFilesPreferences.saveAutoMouseMode
import org.openmw.utils.GameFilesPreferences.saveIconGlow
import org.openmw.utils.InitialDirectorySelection
import org.openmw.utils.ReadAndDisplayIniValues
import org.openmw.utils.UserManageAssets
import org.openmw.utils.getLayoutType
import org.openmw.utils.startGame
import org.openmw.utils.stringRes
import java.io.File

@InternalCoroutinesApi
@ExperimentalMaterial3Api
@Composable
fun SettingPage(navigateToHome: () -> Unit) {
    val context = LocalContext.current
    val selectedBackgroundAnimation by getBackgroundAnimationFlow(context).collectAsState(initial = "BouncingBackground")
    val newFeatureEnabledChecked by GameFilesPreferences.loadNewFeatureEnabledState(context).collectAsState(initial = false)
    val codeGroupOption by readCodeGroup(context).collectAsState(initial = "OpenMW")
    val layoutType = getLayoutType()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            if (layoutType == NavigationSuiteType.NavigationBar) {
                MyTopBar(context)
            }
        },
        content = { innerPadding ->
            when (selectedBackgroundAnimation) {
                "BouncingBackground" -> BouncingBackground()
                "RotatingImageBackground" -> RotatingImageBackground()
                "CircularBackground" -> CircularBackground()
                else -> NoneBackground()
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                Column(
                    modifier = Modifier
                ) {
                    if (codeGroupOption == "OpenMW" && layoutType == NavigationSuiteType.NavigationBar) {
                        ToggleFeatureSwitch(context)
                        ReadAndDisplayIniValues()
                        ControlsMenu()

                        if (newFeatureEnabledChecked) {
                            DeveloperMenu()
                        }
                    } else if (codeGroupOption == "OpenMW") {
                        //navigateToHome()
                        LandscapeSettings(isNewFeatureEnabledChecked = newFeatureEnabledChecked)
                    }
                    else if (codeGroupOption == "UQM") {
                        UQM()
                        ToggleFeatureSwitch(context)
                        ControlsMenu()
                        if (newFeatureEnabledChecked) {
                            DeveloperMenu()
                        }
                    }
                    else if (codeGroupOption == "Dethrace") {
                        ToggleFeatureSwitch(context)
                        ControlsMenu()
                        if (newFeatureEnabledChecked) {
                            DeveloperMenu()
                        }
                    }
                }
            }
        },
    )
}

@InternalCoroutinesApi
@ExperimentalMaterial3Api
@Composable
fun DeveloperMenu() {
    var isColumnExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.Black)
            .verticalScroll(rememberScrollState())
            .background(color = customColor)
            .clickable { isColumnExpanded = !isColumnExpanded },
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
                text = stringResource(R.string.developer_tools),
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = White
            )
            Icon(
                imageVector = if (isColumnExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isColumnExpanded) "Collapse" else "Expand"
            )
        }
        if (isColumnExpanded) {
            DevInsert()
        }
    }
}

@Composable
fun DevInsert() {
    var showPopup by remember { mutableStateOf(false) }
    var showFileBrowser by remember { mutableStateOf(false) }
    var selectedInitialDirectory by remember { mutableStateOf<File?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bypassGameCheck by GameFilesPreferences.loadBypassGameCheck(context).collectAsState(initial = false)
    val avoidInsertion by GameFilesPreferences.readResolutionInsertion(context).collectAsState(initial = false)
    val hideSystemBars by getSystemBars(context).collectAsState(initial = true)
    val screenStayOn by getScreenStayOn(context).collectAsState(initial = true)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.app_logging),
                    color = White
                )
                Text(
                    text = stringResource(R.string.enable_a_custom_logging_system_tip),
                    color = White,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                )
            }

            Switch(
                checked = UIStateManager.isAppLoggingEnabled,
                onCheckedChange = { checked ->
                    UIStateManager.isAppLoggingEnabled = checked
                }
            )
        }
        HorizontalDivider(color = White, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Show / Hide system bars",
                color = White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = hideSystemBars,
                onCheckedChange = { isChecked ->
                    CoroutineScope(Dispatchers.Main).launch {
                        GameFilesPreferences.setSystemBars(context, !isChecked)
                    }
                }
            )
        }
        HorizontalDivider(color = White, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Toggle screen stay on",
                color = White,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            Switch(
                checked = screenStayOn,
                onCheckedChange = { isChecked ->
                    CoroutineScope(Dispatchers.Main).launch {
                        GameFilesPreferences.setScreenStayOn(context, !isChecked)
                    }
                }
            )
        }
        HorizontalDivider(color = White, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.don_t_inject_resolution_settings_automatically),
                    color = White
                )
                Text(
                    text = stringResource(R.string.overwriting_resolution_settings_tip),
                    color = White,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                )
            }

            Switch(
                checked = avoidInsertion,
                onCheckedChange = { checked ->
                    scope.launch {
                        GameFilesPreferences.writeResolutionInsertion(context, !avoidInsertion)
                    }
                }
            )
        }
        HorizontalDivider(color = White, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.bypass_the_game_files_check_tip),
                    color = White
                )
                Text(
                    text = stringResource(R.string.this_enables_disables_the_launcher_files_check_tip),
                    color = White,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                )
            }

            Switch(
                checked = bypassGameCheck,
                onCheckedChange = { checked ->
                    scope.launch {
                        GameFilesPreferences.saveBypassGameCheck(context, !bypassGameCheck)
                    }
                }
            )
        }
        HorizontalDivider(color = White, thickness = 1.dp)
        Button(
            onClick = { showPopup = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            )
        ) {
            Text(stringResource(R.string.show_datastore_content), color = White)
        }
        HorizontalDivider(color = White, thickness = 1.dp)
        Button(
            onClick = {
                selectedInitialDirectory = null
                showFileBrowser = true
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.Transparent
            )
        ) {
            Text(stringResource(R.string.open_file_browser), color = White)
        }
        HorizontalDivider(color = White, thickness = 1.dp)
    }

    if (showPopup) {
        DataStoreContentPopup(
            context = context,
            onDismiss = { showPopup = false })
    }

    if (showFileBrowser && selectedInitialDirectory == null) {
        InitialDirectorySelection(
            onSelect = { directory ->
                selectedInitialDirectory = directory
                showFileBrowser = true
            },
            onDismiss = { showFileBrowser = false }
        )
    }

    if (showFileBrowser && selectedInitialDirectory != null) {
        FileBrowserPopup(
            initialDirectory = selectedInitialDirectory!!,
            onDismiss = { showFileBrowser = false }

        )
    }
}

@SuppressLint("MutableCollectionMutableState")
@Composable
fun DataStoreContentPopup(context: Context, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var dataStoreContent by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    LaunchedEffect(Unit) {
        scope.launch {
            dataStoreContent = GameFilesPreferences.getAllPreferences(context)
        }
    }

    var updatedDataStoreContent by remember { mutableStateOf(dataStoreContent.toMutableMap()) }

    AlertDialog(
        onDismissRequest = { onDismiss() },
                title = { Text(stringResource(R.string.datastore_content)) },
                text = {
                    Column (
                        modifier = Modifier
                            .fillMaxHeight(0.6f) // Adjust the height as needed
                        .verticalScroll(rememberScrollState())
                    ) {
                        dataStoreContent.forEach { (key, value) ->
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(text = key)
                            var textFieldValue by remember { mutableStateOf(value) }
                            TextField(
                                value = textFieldValue,
                                onValueChange = { newValue ->
                                    textFieldValue = newValue
                                    updatedDataStoreContent[key] = newValue
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .wrapContentHeight()
                            )
                            HorizontalDivider(
                                color = Color.Gray,
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        scope.launch {
                            updatedDataStoreContent.forEach { (key, value) ->
                                when (key) {
                                    GameFilesPreferences.GAME_FILES_URI_KEY.name -> GameFilesPreferences.storeGameFilesPath(context, value)
                                    GameFilesPreferences.UI_HIDDEN_STATE_KEY.name -> GameFilesPreferences.saveUIState(context, value.toBoolean())
                                    GameFilesPreferences.MATCH_ICON_COLOR_KEY.name -> GameFilesPreferences.saveMatchIconColorState(context, value.toBoolean())
                                    GameFilesPreferences.RESOLUTION_X_KEY.name -> GameFilesPreferences.saveResolutionX(context, value.toInt())
                                    GameFilesPreferences.RESOLUTION_Y_KEY.name -> GameFilesPreferences.saveResolutionY(context, value.toInt())
                                    GameFilesPreferences.ICON_GLOW_KEY.name -> saveIconGlow(context, value.toBoolean())
                                    GameFilesPreferences.ARG_LINE_KEY.name -> GameFilesPreferences.saveARGLine(context, value)
                                    GameFilesPreferences.ENV_LINE_KEY.name -> GameFilesPreferences.saveENVLine(context, value)
                                    //GameFilesPreferences.NEXUS_API_KEY.name -> GameFilesPreferences.saveNexusApi(context, value)
                                    GameFilesPreferences.USER_OPTIONS_KEY.name -> GameFilesPreferences.saveUserOptions(context, value.split(",").toSet())
                                    GameFilesPreferences.AUTO_MOUSE_MODE_KEY.name -> saveAutoMouseMode(context, value)
                                    GameFilesPreferences.AVOID_16_BITS_KEY.name -> GameFilesPreferences.writeAvoid16Bits(context, value.toBoolean())
                                    GameFilesPreferences.TEXTURE_SHRINKING_KEY.name -> GameFilesPreferences.writeTextureShrinkingOption(context, value)
                                    GameFilesPreferences.SELECTED_MOUSE_KEYS.name -> GameFilesPreferences.setSelectedKeycodes(context, value)
                                    GameFilesPreferences.ALLOWED_TO_TEXT_EDITOR.name -> GameFilesPreferences.setExtensionAllowedToEdit(context, value)
                                    GameFilesPreferences.NEW_FEATURE_ENABLED_KEY.name -> GameFilesPreferences.saveUIState(context, value.toBoolean())
                                    GameFilesPreferences.WHATS_NEW_KEY.name -> GameFilesPreferences.setWhatsNew(context, value.toBoolean())
                                    GameFilesPreferences.TRANSLATION_ENABLED_KEY.name -> GameFilesPreferences.saveTranslationState(context, value.toBoolean())
                                    GameFilesPreferences.TUTORIAL_KEY.name -> GameFilesPreferences.setTutorial(context, value.toBoolean())
                                    GameFilesPreferences.SUPPORTED_LANGUAGES.name -> GameFilesPreferences.setSupportedLanguages(context, value)
                                }
                            }
                            onDismiss()
                        }
                    }, modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                        shape = RectangleShape,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = customColor
                        )) {
                        Text(stringResource(R.string.save), color = White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { onDismiss() }, colors = ButtonDefaults.buttonColors(Color.Transparent)) {
                        Text(stringResource(R.string.cancel), color = White)
                    }
                }
    )
}

@Composable
fun ToggleFeatureSwitch(context: Context) {
    var isColumnExpanded by remember { mutableStateOf(false) }
    if (launchedActivity) {
        isColumnExpanded = true
    }

    Column(
        modifier = Modifier
            .border(1.dp, Color.Black)
            .background(color = customColor)
            .verticalScroll(rememberScrollState())
            .clickable { isColumnExpanded = !isColumnExpanded },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (!launchedActivity) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = customColor)
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.launcher_settings),
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = White
            )
            Icon(
                imageVector = if (isColumnExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isColumnExpanded) "Collapse" else "Expand"
            )
        }
        }
        if (isColumnExpanded) {
            FeaturesSwitches()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeaturesSwitches() {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isUIHidden by GameFilesPreferences.loadUIState(context).collectAsState(initial = false)
    val isVibrationOn by GameFilesPreferences.loadVibrationState(context).collectAsState(initial = true)
    val matchIconColorChecked by GameFilesPreferences.loadMatchIconColorState(context).collectAsState(initial = false)
    val avoid16BitsChecked by GameFilesPreferences.readAvoid16Bits(context).collectAsState(initial = true)
    val useAngle by GameFilesPreferences.readAngle(context).collectAsState(initial = true)
    val useSPIRV by GameFilesPreferences.readSPIRV(context).collectAsState(initial = true)
    val virtualKeyboard by GameFilesPreferences.useVirtualKeyboard(context).collectAsState(initial = true)
    val buttonGroupSwitch by GameFilesPreferences.getButtonGroupSwitch(context).collectAsState(initial = true)
    val iconGlowChecked by GameFilesPreferences.loadIconGlow(context).collectAsState(initial = true)
    var showDialog by remember { mutableStateOf(false) }
    val controllerConnected = isControllerConnected(context)
    val newFeatureEnabledChecked by GameFilesPreferences.loadNewFeatureEnabledState(context).collectAsState(initial = false)
    val buttonTint by GameFilesPreferences.loadButtonTint(context).collectAsState(initial = true)
    val translationChecked by GameFilesPreferences.loadTranslationState(context).collectAsState(initial = false)
    val quickSlot by getQuickSlot(context).collectAsState(initial = true)
    Column {
        // Conditionally display the "Exit to Launcher" button at the bottom center
        if (configureControls) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                Button(
                    onClick = {
                        launchedActivity = false
                        configureControls = false
                        /*(context as? Activity)?.finish()*/
                        context.startActivity(Intent(context, MainActivity::class.java))
                    }
                ) {
                    Text(stringResource(R.string.return_to_launcher))
                }
            }
        }
        if (!launchedActivity) {
            OpenMW()
        }
        HorizontalDivider(color = White, thickness = 1.dp)
        if (launchedActivity) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.display_memory_info),
                        color = White
                    )
                    Text(
                        text = stringResource(R.string.create_a_window_showing_memory_info),
                        color = White,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                    )
                }

                Switch(
                    checked = UIStateManager.isMemoryInfoEnabled,
                    onCheckedChange = { checked ->
                        UIStateManager.isMemoryInfoEnabled = checked
                    }
                )
            }
            if (tempCodeGroup == "UQM") {
                HorizontalDivider(color = White, thickness = 1.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.Start,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = "Uqm Jni",
                            color = White
                        )
                        Text(
                            text = "Display jni info on screen",
                            color = White,
                            style = TextStyle(
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Serif
                            )
                        )
                    }

                    Switch(
                        checked = uqmJNI,
                        onCheckedChange = { checked ->
                            uqmJNI = checked
                        }
                    )
                }
            }
            HorizontalDivider(color = White, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.display_battery_info),
                        color = White
                    )
                    Text(
                        text = stringResource(R.string.create_a_window_showing_battery_info),
                        color = White,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                    )
                }

                Switch(
                    checked = UIStateManager.isBatteryStatusEnabled,
                    onCheckedChange = { checked ->
                        UIStateManager.isBatteryStatusEnabled = checked
                    }
                )
            }
            HorizontalDivider(color = White, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.logcat),
                        color = White
                    )
                    Text(
                        text = stringResource(R.string.create_a_window_showing_logcat_info),
                        color = White,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                    )
                }

                Switch(
                    checked = UIStateManager.isLoggingEnabled,
                    onCheckedChange = { checked ->
                        UIStateManager.isLoggingEnabled = checked
                    }
                )
            }
            HorizontalDivider(color = White, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.custom_log_output),
                        color = White
                    )
                    Text(
                        text = stringResource(R.string.create_a_window_showing_custom_log_output),
                        color = White,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                    )
                }

                Switch(
                    checked = UIStateManager.isAppLoggingEnabled,
                    onCheckedChange = { checked ->
                        UIStateManager.isAppLoggingEnabled = checked
                    }
                )
            }
            HorizontalDivider(color = White, thickness = 1.dp)
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Column to stack the two Text components on the left
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f) // This ensures the Column takes up available space on the left
            ) {
                Text(
                    text = if (controllerConnected || !isUIHidden) {
                        stringResource(R.string.ui_is_visible)
                    } else {
                        stringResource(R.string.ui_is_hidden)
                    },
                    color = White
                )
                Text(
                    text = stringResource(R.string.set_the_ui_visible_state_tip),
                    color = White,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                )
            }
            Switch(
                checked = isUIHidden,
                onCheckedChange = { checked ->
                    scope.launch {
                        GameFilesPreferences.saveUIState(context, !isUIHidden)
                    }
                }
            )
        }
        HorizontalDivider(color = White, thickness = 1.dp)
        Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
        ) {
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.Start,
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = stringResource(R.string.use_virtual_keyboard),
                color = White
            )
            Text(
                text = "${stringResource(R.string.use_virtual_keyboard)}.",
                color = White,
                style = TextStyle(
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Serif
                )
            )
        }

        Switch(
            checked = virtualKeyboard,
            onCheckedChange = { checked ->
                scope.launch {
                    GameFilesPreferences.setVirtualKeyboard(context, !virtualKeyboard)
                }
            }
        )
    }

        HorizontalDivider(color = White, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Column to stack the two Text components on the left
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (!isVibrationOn) stringResource(R.string.vibration_disabled) else stringResource(
                        R.string.vibration_enabled
                    ),
                    color = White
                )
                Text(
                    text = stringResource(R.string.set_the_vibration_state_feat),
                    color = White,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                )
            }
            Switch(
                checked = isVibrationOn,
                onCheckedChange = { checked ->
                    scope.launch {
                        GameFilesPreferences.saveVibrationState(context, !isVibrationOn)
                    }
                }
            )
        }
        HorizontalDivider(color = White, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Column to stack the two Text components on the left
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f) // This ensures the Column takes up available space on the left
            ) {
                Text(
                    text = if (quickSlot) {
                        "Quick Slot enabled"
                    } else {
                        "Quick Slot Disabled"
                    },
                    color = White
                )
            }
            Switch(
                checked = quickSlot,
                onCheckedChange = { checked ->
                    scope.launch {
                        GameFilesPreferences.setQuickSlot(context, !quickSlot)
                    }
                }
            )
        }
        HorizontalDivider(color = White, thickness = 1.dp)
        if (!launchedActivity) {
            HorizontalDivider(color = White, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Use Angle driver",
                        color = White
                    )
                    Text(
                        text = "Using this setting will enable newer shaders on older hardware like shadows.",
                        color = White,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                    )
                }

                Switch(
                    checked = useAngle,
                    onCheckedChange = { checked ->
                        scope.launch {
                            GameFilesPreferences.writeAngle(context, !useAngle)
                        }
                    }
                )
            }
            HorizontalDivider(color = White, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = "Enable spirv",
                        color = White
                    )
                    Text(
                        text = "Using this setting will enable newer shaders on hardware that doesnt support it.",
                        color = White,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                    )
                }

                Switch(
                    checked = useSPIRV,
                    onCheckedChange = { checked ->
                        scope.launch {
                            GameFilesPreferences.writeSPIRV(context, !useSPIRV)
                        }
                    }
                )
            }
            HorizontalDivider(color = White, thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.avoid_16_bits),
                        color = White
                    )
                    Text(
                        text = stringResource(R.string.helps_with_devices_that_have_less_memory_feat),
                        color = White,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                    )
                }

                Switch(
                    checked = avoid16BitsChecked,
                    onCheckedChange = { checked ->
                        scope.launch {
                            GameFilesPreferences.writeAvoid16Bits(context, !avoid16BitsChecked)
                        }
                    }
                )
            }
            HorizontalDivider(color = White, thickness = 1.dp)
            CodeGroupOptionSelector()
            HorizontalDivider(color = White, thickness = 1.dp)
            // Add the new feature enabled switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.enable_translation),
                        color = White
                    )
                    Text(
                        text = stringResource(R.string.this_enables_language_translation_feat),
                        color = White,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                    )
                }

                Switch(
                    checked = translationChecked,
                    onCheckedChange = { checked ->
                        scope.launch {
                            GameFilesPreferences.saveTranslationState(context, !translationChecked)
                        }
                    }
                )
            }
            if (translationChecked) {
                HorizontalDivider(color = White, thickness = 1.dp)
                LanguageSelector(context)
            }
            HorizontalDivider(color = White, thickness = 1.dp)
            BackgroundAnimationOptionSelector()
            HorizontalDivider(color = White, thickness = 1.dp)
            TextureShrinkingOptionSelector()
            HorizontalDivider(color = White, thickness = 1.dp)
            CharsetDropdownMenu { newEncoding ->
                updateCharset(newEncoding)
            }
            HorizontalDivider(color = White, thickness = 1.dp)
        }
        HorizontalDivider(color = White, thickness = 1.dp)
        AnimationSettings(context, scope)
        HorizontalDivider(color = White, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.match_icon_color_to_thumbstick),
                    color = White
                )
                Text(
                    text = stringResource(R.string.thumbstick_share_its_color_feat),
                    color = White,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                )
            }

            Switch(
                checked = matchIconColorChecked,
                onCheckedChange = { checked ->
                    scope.launch {
                        GameFilesPreferences.saveMatchIconColorState(context, !matchIconColorChecked)
                    }
                }
            )
        }

        HorizontalDivider(color = White, thickness = 1.dp)

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.icon_glow),
                    color = White
                )
                Text(
                    text = stringResource(R.string.glow_to_all_the_icons_feat),
                    color = White,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                )
            }

            Switch(
                checked = iconGlowChecked,
                onCheckedChange = { checked ->
                    scope.launch {
                        saveIconGlow(context, !iconGlowChecked)
                    }
                }
            )
        }
        HorizontalDivider(color = White, thickness = 1.dp)
        AutoMouseModeOptionSelector()
        HorizontalDivider(color = White, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.allow_button_groups),
                    color = White
                )
                Text(
                    text = stringResource(R.string.allow_btn_group_feat),
                    color = White,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                )
            }

            Switch(
                checked = buttonGroupSwitch,
                onCheckedChange = { checked ->
                    scope.launch {
                        GameFilesPreferences.setButtonGroupSwitch(context, !buttonGroupSwitch)
                    }
                }
            )
        }
        HorizontalDivider(color = White, thickness = 1.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = stringResource(R.string.allow_button_tint),
                    color = White
                )
                Text(
                    text = stringResource(R.string.allow_button_tint_feat),
                    color = White,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                )
            }

            Switch(
                checked = buttonTint,
                onCheckedChange = { checked ->
                    scope.launch {
                        GameFilesPreferences.saveButtonTint(context, !buttonTint)
                    }
                }
            )
        }
        HorizontalDivider(color = White, thickness = 1.dp)
        ButtonShapeDropdownMenu { selectedShape ->
            scope.launch {
                GameFilesPreferences.saveButtonShape(context, selectedShape)
            }
        }

        if (!launchedActivity) {
            HorizontalDivider(color = White, thickness = 1.dp)
            // Add the new feature enabled switch
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.Start,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.enable_developer_options),
                        color = White
                    )
                    Text(
                        text = stringResource(R.string.enable_developer_options_feat),
                        color = White,
                        style = TextStyle(
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif
                        )
                    )
                }

                Switch(
                    checked = newFeatureEnabledChecked,
                    onCheckedChange = { checked ->
                        if (checked) {
                            showDialog = true
                        } else {
                            scope.launch {
                                GameFilesPreferences.saveNewFeatureEnabledState(context, false)
                            }
                        }
                    }
                )
            }

            // Alert Dialog
            if (showDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showDialog = false
                    },
                    title = {
                        Text(text = stringResource(R.string.warning))
                    },
                    text = {
                        Text(stringResource(R.string.enable_dangerous_feature_tip))
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                scope.launch {
                                    GameFilesPreferences.saveNewFeatureEnabledState(context, true)
                                }
                                showDialog = false
                            }
                        ) {
                            Text(stringResource(R.string.btn_confirm))
                        }
                    },
                    dismissButton = {
                        Button(
                            onClick = {
                                showDialog = false
                            }
                        ) {
                            Text(stringResource(R.string.btn_cancel))
                        }
                    }
                )
            }
        }
    }
}

@SuppressLint("SuspiciousIndentation")
@Composable
fun TextureShrinkingOptionSelector() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val textureShrinkingOption by readTextureShrinkingOption(context).collectAsState(initial = "None")
    var expanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = customColor)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.texture_shrinking_option), color = White)
            Box(
                modifier = Modifier
                .clickable { expanded = true }
            ) {
                Text(textureShrinkingOption, modifier = Modifier.padding(8.dp), color = White)
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .border(1.dp, Color.Black)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.none), color = White) },
                                     onClick = {
                                         scope.launch {
                                             try {
                                                 GameFilesPreferences.writeTextureShrinkingOption(context, "None")
                                             } catch (e: Exception) {
                                                 e.printStackTrace()
                                             }
                                         }
                                         expanded = false
                                     })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.low), color = White) },
                                     onClick = {
                                         scope.launch {
                                             try {
                                                 GameFilesPreferences.writeTextureShrinkingOption(context, "low")
                                             } catch (e: Exception) {
                                                 e.printStackTrace()
                                             }
                                         }
                                         expanded = false
                                     })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.medium), color = White) },
                                     onClick = {
                                         scope.launch {
                                             try {
                                                 GameFilesPreferences.writeTextureShrinkingOption(context, "medium")
                                             } catch (e: Exception) {
                                                 e.printStackTrace()
                                             }
                                         }
                                         expanded = false
                                     })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.high), color = White) },
                        onClick = {
                                scope.launch {
                                    try {
                                        GameFilesPreferences.writeTextureShrinkingOption(context, "high")
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                expanded = false
                        })
                }
            }
        }
    }
}

@Composable
fun LanguageSelector(context: Context) {
    val languageSelection by getLanguageFlow(context).collectAsState(initial = "en")
    val supportedLanguages by GameFilesPreferences.getSupportedLanguagesFlow(context).collectAsState(initial = listOf("en,ru,hr,fr"))
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = customColor)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.select_language), color = White)
            Box(
                modifier = Modifier
                    .clickable { expanded = true }
            ) {
                Text(languageSelection, modifier = Modifier.padding(8.dp), color = White)
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .border(1.dp, Color.Black)
                ) {
                    supportedLanguages.forEach { language ->
                        DropdownMenuItem(
                            text = { Text(language, color = White) },
                            onClick = {
                                scope.launch {
                                    try {
                                        GameFilesPreferences.setLanguage(context, language)
                                        println("Language set to: $language")
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CodeGroupOptionSelector() {
    val scope = rememberCoroutineScope()
    var expanded by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val codeGroupOption by readCodeGroup(context).collectAsState(initial = "OpenMW")

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = customColor)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.select_game), color = White)
            Box(
                modifier = Modifier
                    .clickable { expanded = true }
            ) {
                Text(codeGroupOption, modifier = Modifier.padding(8.dp), color = White)
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .border(1.dp, Color.Black)
                ) {
                    gameList.forEach { game ->
                        DropdownMenuItem(
                            text = { Text(game, color = White) },
                            onClick = {
                                scope.launch {
                                    try {
                                        GameFilesPreferences.setCodeGroup(context, game)
                                        tempCodeGroup = game
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                }
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AutoMouseModeOptionSelector() {
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val autoMouseMode by loadAutoMouseMode(context).collectAsState(initial = "Hybrid")

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = customColor)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.Start,
                modifier = Modifier
                    .weight(1f)
                    .background(color = customColor)
            ) {
                Text(
                    text = stringResource(R.string.mouse_mode),
                    color = White
                )
                Text(
                    text = stringResource(R.string.mouse_mode_feat),
                    color = White,
                    style = TextStyle(
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Serif
                    )
                )
            }
            Box(
                modifier = Modifier
                    .clickable { expanded = true }
            ) {
                Text(autoMouseMode, modifier = Modifier.padding(8.dp), color = White)
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .border(1.dp, Color.Black)
                        .background(color = customColor)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.none), color = White) },
                        onClick = {
                            scope.launch {
                                try {
                                    saveAutoMouseMode(context, "None")
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            expanded = false
                        })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.hybrid), color = White) },
                        onClick = {
                            scope.launch {
                                try {
                                    saveAutoMouseMode(context, "Hybrid")
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            expanded = false
                        })
                }
            }
        }
    }
}

@Composable
fun CharsetDropdownMenu(updateCharset: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val charsets = listOf("utf-8", "utf-16", "us-ascii", "iso-8859-1", "shift_jis", "euc-jp", "win1250", "win1251", "windows-1252", "gbk")
    var selectedCharset by remember { mutableStateOf(readCurrentCharset()) }
    var feedbackMessage by remember { mutableStateOf("") }

    Row(
        modifier = Modifier
            .padding(16.dp)
            .background(color = customColor)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.select_charset), modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        Button(onClick = { expanded = true }) {
            Text(selectedCharset)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .border(1.dp, Color.Black)
        ) {
            charsets.forEach { charset ->
                DropdownMenuItem(
                    text = { Text(charset, color = White) },
                    onClick = {
                        selectedCharset = charset
                        expanded = false
                        updateCharset(charset)

                        feedbackMessage = "${stringRes(R.string.charset_changed_to)} $charset"
                    }
                )
            }
        }
    }
    if (feedbackMessage.isNotEmpty()) {
        Text(feedbackMessage, color = Color.Green, modifier = Modifier.padding(8.dp))
    }
}

fun readCurrentCharset(): String {
    val configFilePath = Constants.OPENMW_CFG
    val file = File(configFilePath)
    var encodingLine = file.readLines().find { it.startsWith("encoding=") }
    if (encodingLine == null) {
        encodingLine = "encoding=win1252"
        file.appendText("\n$encodingLine")
    }
    return encodingLine.split("=")[1]
}

fun updateCharset(newEncoding: String) {
    val configFilePath = Constants.OPENMW_CFG
    val file = File(configFilePath)
    val updatedLines = file.readLines().map { line ->
        if (line.startsWith("encoding=")) {
            "encoding=$newEncoding"
        } else {
            line
        }
    }
    file.writeText(updatedLines.joinToString("\n"))
    println("Charset changed to $newEncoding")
}

@InternalCoroutinesApi
@ExperimentalMaterial3Api
@Composable
fun ControlsMenu() {
    var isColumnExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.Black)
            .background(color = customColor)
            .clickable { isColumnExpanded = !isColumnExpanded },
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
                text = stringResource(R.string.customize_controls),
                fontWeight = FontWeight.Bold,
                fontSize = 24.sp,
                color = White
            )
            Icon(
                imageVector = if (isColumnExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = if (isColumnExpanded) "Collapse" else "Expand"
            )
        }
        if (isColumnExpanded) {
            ControlsInsert()
        }
    }
}

@Composable
fun BackgroundAnimationOptionSelector() {
    val context = LocalContext.current
    val backgroundAnimation by getBackgroundAnimationFlow(context).collectAsState(initial = "BouncingBackground")
    var expanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(color = customColor)
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(stringResource(R.string.background_animations), color = White)
            Box(
                modifier = Modifier
                    .clickable { expanded = true }
            ) {
                Text(backgroundAnimation, modifier = Modifier.padding(8.dp), color = White)
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .border(1.dp, Color.Black)
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.none), color = White) },
                        onClick = {
                            scope.launch {
                                try {
                                    GameFilesPreferences.setBackgroundAnimation(context, "None")
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            expanded = false
                        })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.bouncing_background), color = White) },
                        onClick = {
                            scope.launch {
                                try {
                                    GameFilesPreferences.setBackgroundAnimation(context, "BouncingBackground")
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            expanded = false
                        })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.rotating_image_background), color = White) },
                        onClick = {
                            scope.launch {
                                try {
                                    GameFilesPreferences.setBackgroundAnimation(context, "RotatingImageBackground")
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            expanded = false
                        })
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.circular_background), color = White) },
                        onClick = {
                            scope.launch {
                                try {
                                    GameFilesPreferences.setBackgroundAnimation(context, "CircularBackground")
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                            expanded = false
                        })
                }
            }
        }
    }
}

@Composable
fun ControlsInsert() {
    val context = LocalContext.current
    var showDialog2 by remember { mutableStateOf(false) }
    HorizontalDivider(color = White, thickness = 1.dp)
    Button(
        onClick = {
            configureControls = true
            context.startGame()
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        )
    ) {
        Text(stringResource(R.string.configure_controls), color = White)
    }
    HorizontalDivider(color = White, thickness = 1.dp)
    Button(
        onClick = {
            showDialog2 = true
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RectangleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent
        )
    ) {
        Text(stringResource(R.string.reset_controls), color = White)
    }
    HorizontalDivider(color = White, thickness = 1.dp)

    if (showDialog2) {
        AlertDialog(
            onDismissRequest = {
                showDialog2 = false
            },
            title = {
                Text(text = stringResource(R.string.confirm_reset), color = White)
            },
            text = {
                Text(
                    stringResource(R.string.reset_the_controls_and_icons_tip),
                    color = White
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        UserManageAssets(context).resetUI()
                        showDialog2 = false
                    }, colors = ButtonDefaults.buttonColors(Color.Transparent)
                ) {
                    Text(stringResource(R.string.btn_confirm), color = White)
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        showDialog2 = false
                    }, colors = ButtonDefaults.buttonColors(Color.Transparent)
                ) {
                    Text(stringResource(R.string.btn_cancel), color = White)
                }
            }
        )
    }
}

@Composable
fun UQM() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalDivider(color = White, thickness = 1.dp)
        CodeGroupOptionSelector()
        /*
        HorizontalDivider(color = White, thickness = 1.dp)
        Button(onClick = {
            UserManageAssets(context).installUQMResourceFiles()
        }, colors = ButtonDefaults.buttonColors(Color.Transparent)) {
            Text(stringResource(R.string.install_uqm_base_files), color = White)
        }

         */
    }
}


@Composable
fun ButtonShapeDropdownMenu(updateShape: (String) -> Unit) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val shapes = listOf("CircleShape", "RoundedCornerShape", "RectangleShape", "StadiumShape", "ChamferedShape")
    val currentShape by loadButtonShape(context).collectAsState(initial = "StadiumShape")
    var feedbackMessage by remember { mutableStateOf("") }
    val buttonShape = stringToShape(currentShape)

    Row(
        modifier = Modifier
            .padding(16.dp)
            .background(color = customColor)
            .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.select_button_shape), color = White, modifier = Modifier.weight(1f))
        Spacer(modifier = Modifier.width(8.dp))
        Button(
            onClick = { expanded = true },
            modifier = Modifier,
            shape = buttonShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = customColor,
                contentColor = Color.Black
            )
        ) {
            Text(stringResource(R.string.shape), color = White)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .border(1.dp, Color.Black)
                .background(color = customColor)
        ) {
            shapes.forEach { shape ->
                DropdownMenuItem(
                    text = { Text(shape, color = White) },
                    onClick = {
                        updateShape(shape)
                        expanded = false
                        feedbackMessage = String.format(stringRes(R.string.button_shape_changed_to_s), shape)
                    }
                )
            }
        }
    }

    if (feedbackMessage.isNotEmpty()) {
        Text(feedbackMessage, color = Color.Green, modifier = Modifier.padding(8.dp))
    }
}
