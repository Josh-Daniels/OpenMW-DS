package org.openmw.ui.page.main

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import org.openmw.R
import org.openmw.fragments.processSelectedFolder
import org.openmw.ui.controls.UIStateManager
import org.openmw.ui.controls.UIStateManager.customCFG
import org.openmw.ui.controls.UIStateManager.customColor
import org.openmw.ui.controls.UIStateManager.gold
import org.openmw.ui.controls.UIStateManager.isTabExpanded
import org.openmw.ui.controls.UIStateManager.logMessagesFlow
import org.openmw.ui.controls.UIStateManager.showLogCat
import org.openmw.ui.controls.UIStateManager.transparentBlack
import org.openmw.ui.navigation.LocalModAssistantViewModel
import org.openmw.ui.page.mod.ModAssistantViewModel
import org.openmw.ui.page.mod.ModValuesList
import org.openmw.ui.page.mod.readModValues
import org.openmw.ui.view.LogRepository
import org.openmw.ui.view.LogsBox
import org.openmw.ui.view.MyTopBar
import org.openmw.ui.view.startLoggingUpdates
import org.openmw.utils.FileBrowserMode
import org.openmw.utils.FileBrowserPopup
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.GameFilesPreferences.readCodeGroup
import org.openmw.utils.MToast
import org.openmw.utils.UqmDownloads
import org.openmw.utils.getLayoutType
import org.openmw.utils.stringRes
import org.openmw.utils.updateConsoleOutput
import java.io.File

@InternalCoroutinesApi
@DelicateCoroutinesApi
@ExperimentalFoundationApi
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("UnusedMaterial3ScaffoldPaddingParameter")
@Composable
fun MainPage(
    modifier: Modifier = Modifier
) {
    val modValues by lazy { readModValues() }
    val context = LocalContext.current
    val savedPath by GameFilesPreferences.getGameFilesUriState(context).collectAsState(initial = null)
    val codeGroupOption by readCodeGroup(context).collectAsState(initial = "OpenMW")
    val layoutType = getLayoutType()
    var showAppLog by rememberSaveable { mutableStateOf(false) }
    var consoleOutput by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()
    val coroutineScope = rememberCoroutineScope()
    val animatedTabHeight by animateDpAsState(
        targetValue = if (isTabExpanded) (LocalConfiguration.current.screenHeightDp * 0.75).dp else 1.dp,
        animationSpec = tween(durationMillis = 300)
    )
    val animatedTabWidth by animateDpAsState(
        targetValue = (LocalConfiguration.current.screenWidthDp * 0.95).dp,
        animationSpec = tween(durationMillis = 300)
    )

    if (UIStateManager.isAppLoggingEnabled) {
        updateConsoleOutput = { newOutput ->
            consoleOutput += newOutput
        }

        LaunchedEffect(scrollState.maxValue) {
            coroutineScope.launch {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }

        LaunchedEffect(showLogCat) {
            if (showLogCat) {
                startLoggingUpdates()
            }
            launch {
                logMessagesFlow.collect { logMessages ->
                    UIStateManager.logMessagesText = logMessages
                }
            }
        }
    }
    val modAssistantViewModel: ModAssistantViewModel = LocalModAssistantViewModel.current

    Box(modifier = Modifier.fillMaxSize()) {
        Scaffold(
            modifier = modifier,
            containerColor = Color.Transparent,
            floatingActionButton = {
                AnimatedVisibility(
                    visible = modAssistantViewModel.selectedTabIndex.intValue == 0,
                    enter = scaleIn(
                        initialScale = 0.0f,
                        animationSpec = tween(durationMillis = 300),
                        transformOrigin = TransformOrigin.Center
                    ) + fadeIn(animationSpec = tween(300)),
                    exit = scaleOut(
                        targetScale = 0.0f,
                        animationSpec = tween(durationMillis = 300),
                        transformOrigin = TransformOrigin.Center
                    ) + fadeOut(animationSpec = tween(300))
                ) {
                    // show add Mods folder button
                    FloatingActionButton(
                        onClick = {
                            modAssistantViewModel.showFileBrowser.value = true
                            MToast(stringRes(R.string.add_mod))
                        },
                        containerColor = FloatingActionButtonDefaults.containerColor.copy(alpha = 0.7f)
                    ) {
                        Icon(Icons.Outlined.CreateNewFolder, contentDescription = "Add Mods folder")
                    }
                }
            },
            floatingActionButtonPosition = FabPosition.Center,
            topBar = {
                if (layoutType == NavigationSuiteType.NavigationBar) {
                    MyTopBar(context)
                } else {
                    Spacer(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
                            .background(transparentBlack)
                    )
                }
            },
            content = { innerPadding ->
                Column(
                    modifier = Modifier.padding(innerPadding),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    when (codeGroupOption) {
                        "OpenMW" -> {
                            if (layoutType == NavigationSuiteType.NavigationBar && (savedPath.isNullOrEmpty() || savedPath == "Game Files: ")) {
                                OpenMW()
                            }
                            ModValuesList(modValues)
                        }
                        "UQM" -> {
                            UqmDownloads()
                        }
                    }
                }

            }
        )
        
        if (UIStateManager.isAppLoggingEnabled) {
            Box(
                modifier = Modifier
                    .height(animatedTabHeight)
                    .width(animatedTabWidth)
                    .background(
                        color = customColor,
                        shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
                    )
                    .clickable { isTabExpanded = !isTabExpanded }
                    .align(Alignment.TopCenter)
            ) {
                // Content of the expanded tab
                if (isTabExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp, top = 48.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row {
                            Button(onClick = {
                                showLogCat = false
                                showAppLog = true
                            }) {
                                Text("AppLog")
                            }
                            Button(onClick = {
                                showAppLog = false
                                showLogCat = true
                            }) {
                                Text("Logcat")
                            }
                        }
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(scrollState),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (showAppLog) {
                                LogsBox(
                                    logs = LogRepository.logs,
                                    fontSize = 10f,
                                    boxWidth = Float.MAX_VALUE,
                                    boxHeight = 600f
                                )
                            }
                            if (showLogCat) {
                                Text(
                                    text = UIStateManager.logMessagesText,
                                    color = gold,
                                    style = TextStyle(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Serif
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

@Composable
fun OpenMW(
    viewModel: MainPageViewModel = hiltViewModel()
) {
    var showFileBrowser by viewModel.showFileBrowser
    var selectedFolderPath by remember { mutableStateOf<String?>(null) }
    var persistedPath by viewModel.persistedPath
    val infiniteTransition = rememberInfiniteTransition(label = "")
    var lastProcessedFolder by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val savedPath by GameFilesPreferences.getGameFilesUriState(context).collectAsState(initial = null)

    val pulse by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = 500
            ),
            repeatMode = RepeatMode.Reverse
        ), label = ""
    )

    Column {
        Button(
            onClick = {
                customCFG = true
                viewModel.selectMorrowWindFolder(
                    context = context
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, bottom = 4.dp)
                .height(56.dp)
                .let {
                    if (savedPath.isNullOrEmpty() || savedPath == "Game Files: ") {
                        it.graphicsLayer(scaleX = pulse, scaleY = pulse)
                    } else {
                        it
                    }
                },
            shape = RectangleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = transparentBlack
            )
        ) {
            Text(
                text = if (savedPath.isNullOrEmpty() || savedPath == "Game Files:  ") {
                    stringRes(R.string.select_games_files)
                } else {
                    "${stringRes(R.string.game_files)}$savedPath"
                },
                color = Color.White
            )
        }

        LaunchedEffect(selectedFolderPath) {
            selectedFolderPath?.let { path ->
                if (path != lastProcessedFolder) {
                    lastProcessedFolder = path
                    processSelectedFolder(context, File(path), onUriPersisted = { newPath ->
                        persistedPath = newPath
                        Log.d("Add_Morrowind", "Add_Morrowind: $persistedPath = $newPath")
                    })
                }
            }
        }

        BackHandler(showFileBrowser) {
            showFileBrowser = false
        }

        if (showFileBrowser) {
            FileBrowserPopup(
                onDismiss = { showFileBrowser = false },
                onFolderSelected = { folder ->
                    viewModel.onGameFolderSelected(folder, context)
                },
                mode = FileBrowserMode.FOLDER
            )
        }
    }
}


