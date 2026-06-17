package org.openmw.ui.page.main

import android.annotation.SuppressLint
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
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
import org.openmw.ui.controls.UIStateManager.gold
import org.openmw.ui.controls.UIStateManager.transparentBlack
import org.openmw.ui.navigation.LocalModAssistantViewModel
import org.openmw.ui.page.mod.ModAssistantViewModel
import org.openmw.ui.page.mod.ModValuesList
import org.openmw.ui.page.mod.readModValues
import org.openmw.ui.view.LogRepository
import org.openmw.ui.view.LogsBox
import org.openmw.ui.view.MyTopBar
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
import kotlin.math.roundToInt

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
    val density = LocalDensity.current
    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val screenHeightPx = with(density) { screenHeight.toPx() }

    // Drag state for the logs overlay
    val offsetY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    
    // Sync isTabExpanded with offsetY
    LaunchedEffect(UIStateManager.isTabExpanded) {
        if (UIStateManager.isTabExpanded) {
            if (offsetY.value < screenHeightPx / 3f) {
                offsetY.animateTo(screenHeightPx / 3f)
            }
        } else {
            offsetY.animateTo(0f)
        }
    }

    val savedPath by GameFilesPreferences.getGameFilesUriState(context).collectAsState(initial = null)
    val codeGroupOption by readCodeGroup(context).collectAsState(initial = "OpenMW")
    val layoutType = getLayoutType()

    updateConsoleOutput = { _ ->
        // Update logic if needed, but LogsBox uses StateFlow directly
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
                            if (savedPath.isNullOrEmpty() || savedPath == "Game Files: ") {
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

        val handleHeight = 32.dp
        val handleHeightPx = with(density) { handleHeight.toPx() }
        
        // The box is screenHeightPx tall.
        // When offsetY is 0, the bottom of the handle should be at -1 (completely hidden).
        // When offsetY is screenHeightPx, the top of the box is at 0.
        // Offset = offsetY - screenHeightPx
        
        Box(
            modifier = Modifier
                .offset { 
                    // Add handleHeightPx to the negative offset so that when offsetY is 0, 
                    // the handle is also fully off-screen.
                    IntOffset(0, (offsetY.value - screenHeightPx - (if (!UIStateManager.isTabExpanded && offsetY.value == 0f) handleHeightPx else 0f)).roundToInt()) 
                }
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f))
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    // Fade out as it gets close to the top
                    alpha = (offsetY.value / (handleHeightPx * 2)).coerceIn(0f, 1f)
                }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = handleHeight)
            ) {
                //Spacer(modifier = Modifier.height(WindowInsets.statusBars.asPaddingValues().calculateTopPadding() + 8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Runtime Logs",
                        color = gold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    
                    IconButton(onClick = { UIStateManager.isTabExpanded = false }) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Close", tint = gold)
                    }
                }

                Box(modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
                    LogsBox(
                        logs = LogRepository.logs,
                        fontSize = 12f,
                        boxWidth = Float.MAX_VALUE,
                        boxHeight = Float.MAX_VALUE
                    )
                }
            }

            // Drag Handle at the bottom
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(handleHeight)
                    .background(Color.Black.copy(alpha = 0.7f))
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            val newValue = (offsetY.value + delta).coerceIn(0f, screenHeightPx)
                            scope.launch { offsetY.snapTo(newValue) }
                        },
                        onDragStopped = {
                            when {
                                offsetY.value < screenHeightPx * 0.25f -> {
                                    scope.launch {
                                        offsetY.animateTo(0f)
                                        UIStateManager.isTabExpanded = false
                                    }
                                }
                                offsetY.value < screenHeightPx * 0.6f -> {
                                    scope.launch {
                                        offsetY.animateTo(screenHeightPx / 3f)
                                        UIStateManager.isTabExpanded = true
                                    }
                                }
                                else -> {
                                    scope.launch {
                                        offsetY.animateTo(screenHeightPx)
                                        UIStateManager.isTabExpanded = true
                                    }
                                }
                            }
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Visual indicator
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(40.dp, 4.dp)
                            .clip(CircleShape)
                            .background(gold.copy(alpha = 0.8f))
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp, 2.dp)
                            .clip(CircleShape)
                            .background(gold.copy(alpha = 0.4f))
                    )
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
