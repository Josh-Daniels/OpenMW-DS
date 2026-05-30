package org.openmw.ui.page.mod

import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.LinearLayout
import android.widget.NumberPicker
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteType
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Popup
import com.composables.core.ScrollArea
import com.composables.core.Thumb
import com.composables.core.ThumbVisibility
import com.composables.core.VerticalScrollbar
import com.composables.core.rememberScrollAreaState
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.launch
import org.openmw.Constants
import org.openmw.Constants.SETTINGS_FILE
import org.openmw.R
import org.openmw.modDownloader.KramConversion
import org.openmw.modDownloader.SSOHome
import org.openmw.ui.controls.UIStateManager.customColor
import org.openmw.ui.controls.UIStateManager.transparentBlack
import org.openmw.ui.navigation.LocalModAssistantViewModel
import org.openmw.ui.page.setting.ControlsInsert
import org.openmw.ui.page.setting.DevInsert
import org.openmw.ui.page.setting.FeaturesSwitches
import org.openmw.ui.view.BarOptions
import org.openmw.utils.DeltaScreen
import org.openmw.utils.FileBrowserMode
import org.openmw.utils.FileBrowserPopup
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.IniSettings
import org.openmw.utils.MToast
import org.openmw.utils.S3LightFixes
import org.openmw.utils.Terminal
import org.openmw.utils.TranslateText
import org.openmw.utils.UserManageAssets
import org.openmw.utils.getLayoutType
import org.openmw.utils.stringRes
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalMaterial3Api::class, InternalCoroutinesApi::class)
@Composable
private fun ModTabRow(
    viewModel: ModAssistantViewModel = LocalModAssistantViewModel.current
) {
    val context = LocalContext.current

    var selectedTabIndex by viewModel.selectedTabIndex
    val categories = viewModel.categories
    var showMods by viewModel.showMods
    var showNav by viewModel.showNav
    var showDelta by viewModel.showDelta
    var autoMods by viewModel.autoMods
    var downloader by viewModel.downloader
    var s3LightsDialog by viewModel.s3LightsDialog
    var terminal by viewModel.terminal
    var kram by viewModel.kram
    var showDialogSettings by viewModel.showDialogSettings
    var showSearchDialog by viewModel.showSearchDialog
    var launchFloatingActionButton by viewModel.launchFloatingActionButton
    var showFileBrowser by viewModel.showFileBrowser

    val newFeatureEnabledChecked by viewModel.newFeatureEnabledChecked.collectAsState(initial = false)

    val layoutType = getLayoutType()

    fun resetStates() {
        autoMods = false
        downloader = false
        showMods = false
        showNav = false
        s3LightsDialog = false
        terminal = false
        kram = false
        showDelta = false
        showDialogSettings = false
        showSearchDialog = false
        launchFloatingActionButton = false
    }

    Row(
        modifier = Modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PrimaryScrollableTabRow (
            modifier = Modifier.weight(1f),
            selectedTabIndex = selectedTabIndex,
            edgePadding = 0.dp,
            containerColor = Color.Transparent,
            divider = {}
        ) {
            categories.forEachIndexed { index, category ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = {
                        resetStates()
                        showMods = true
                        selectedTabIndex = index

                    },
                    text = { Text(category.tag) }
                )
            }
            /*Tab(
                selected = selectedTabIndex == categories.size,
                onClick = { showFileBrowser = true },
                text = { Text(stringResource(R.string.add_mod)) }
            )*/
            /*if (newFeatureEnabledChecked) {
                Tab(
                    selected = selectedTabIndex == 3,
                    onClick = {
                        resetStates()
                        autoMods = true
                        selectedTabIndex = 3
                    },
                    text = { Text(stringResource(R.string.search_repair)) }
                )
            }*/
            Tab(
                selected = selectedTabIndex == 3,
                onClick = {
                    resetStates()
                    downloader = true
                    selectedTabIndex = 3
                },
                text = { Text(stringResource(R.string.mod_downloader)) }
            )
            Tab(
                selected = selectedTabIndex == 4,
                onClick = {
                    resetStates()
                    s3LightsDialog = true
                    selectedTabIndex = 4
                },
                text = { Text(stringResource(R.string.s3lightfixes)) }
            )
            Tab(
                selected = selectedTabIndex == 5,
                onClick = {
                    resetStates()
                    showDelta = true
                    selectedTabIndex = 5
                },

                text = { Text(stringResource(R.string.delta_plugin)) }
            )
            Tab(
                selected = selectedTabIndex == 6,
                onClick = {
                    resetStates()
                    showSearchDialog = true
                    selectedTabIndex = 6
                },
                icon = { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_mods)) }
            )
            if (newFeatureEnabledChecked) {
                Tab(
                    selected = selectedTabIndex == 7,
                    onClick = {
                        resetStates()
                        terminal = true
                        selectedTabIndex = 7
                    },
                    text = { Text("Terminal") }
                )
                Tab(
                    selected = selectedTabIndex == 8,
                    onClick = {
                        resetStates()
                        kram = true
                        selectedTabIndex = 8
                    },
                    text = { Text("Kram") }
                )
            }
        }
        if (layoutType != NavigationSuiteType.NavigationBar) {
            BarOptions(context)
        }
    }
}

@InternalCoroutinesApi
@OptIn(ExperimentalMaterial3Api::class)
@DelicateCoroutinesApi
@ExperimentalFoundationApi
@Composable
fun ModValuesList(
    modValues: List<ModValue>,
    viewModel: ModAssistantViewModel = LocalModAssistantViewModel.current
) {
    var selectedTabIndex by viewModel.selectedTabIndex
    var showFileBrowser by viewModel.showFileBrowser

    var selectedModPath by remember { mutableStateOf<String?>(null) }
    val categories = viewModel.categories
    var categorizedModValues by remember {
        mutableStateOf(categories.map { category ->
            modValues.filter { it.category == category.key }
        })
    }
    val lazyListState = rememberLazyListState()
    val context = LocalContext.current
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedIndex by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var showDialogPC by remember { mutableStateOf(false) }
    var searchResults by remember { mutableStateOf(emptyList<ModValue>()) }
    var searchInProgress by remember { mutableStateOf(false) }
    var pathCorrectorInProgress by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf("Status") }
    var changes by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    val foundFiles = remember { mutableStateListOf<String>() }
    /*val orientation = LocalConfiguration.current.orientation
    val newModValues by lazy { readModValues() }*/
    val stateSB = rememberScrollAreaState(lazyListState)
    var expanded by remember { mutableStateOf(false) }
    var showDialog by remember { mutableStateOf(false) }
    val settingsFile = File(SETTINGS_FILE)

    val savedPath by GameFilesPreferences.getGameFilesUriState(context).collectAsState(initial = null)

    // Manage whats in the tabs here
    var showMods by viewModel.showMods
    var showNav by viewModel.showNav
    var showDelta by viewModel.showDelta
    var autoMods by viewModel.autoMods
    var downloader by viewModel.downloader
    var s3LightsDialog by viewModel.s3LightsDialog
    var terminal by viewModel.terminal
    var kram by viewModel.kram
    var showDialogSettings by viewModel.showDialogSettings

    var showSearchDialog by viewModel.showSearchDialog

    val coroutineScope = rememberCoroutineScope()
    var highlightedCardId by remember { mutableStateOf<Int?>(null) }
    val translationChecked by GameFilesPreferences.loadTranslationState(context).collectAsState(initial = false)
    val newFeatureEnabledChecked by viewModel.newFeatureEnabledChecked.collectAsState(initial = false)

    val reorderableLazyListState = rememberReorderableLazyListState(lazyListState) { from, to ->
        val currentList = categorizedModValues[selectedTabIndex].toMutableList()
        val movedItem = currentList.removeAt(from.index)
        currentList.add(to.index, movedItem)

        categorizedModValues = categorizedModValues.toMutableList().apply {
            this[selectedTabIndex] = currentList
        }
    }

    LaunchedEffect(savedPath) {
        savedPath?.let { path ->
            categorizedModValues = ModValue.updateUIFromModValues(categories.map { it.key })
        }
    }

    // Reload the mod values and update the UI
    categorizedModValues = ModValue.updateUIFromModValues(categories.map { it.key })

    Column {
        Row(
            modifier = Modifier
                .background(transparentBlack),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModTabRow()
        }

        AnimatedVisibility(
            visible = showDialog,
            enter = fadeIn() + expandIn(),
            exit = fadeOut() + shrinkOut()
        ) {
            AlertDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(stringResource(R.string.confirm_reset)) },
                text = { Text(stringResource(R.string.reset_the_settings_tip)) },
                confirmButton = {
                    TextButton(
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
                    TextButton(
                        onClick = { showDialog = false }
                    ) {
                        Text(stringRes(R.string.btn_cancel))
                    }
                }
            )
        }
        BackHandler(showFileBrowser) {
            showFileBrowser = false
        }
        AnimatedVisibility(
            visible = showFileBrowser,
            enter = fadeIn() + expandIn(),
            exit = fadeOut() + shrinkOut()
        ) {
            FileBrowserPopup(
                onDismiss = { showFileBrowser = false },
                onFolderSelected = { folder ->
                    viewModel.modPathSelection(context, folder) { modPath ->
                        selectedModPath = modPath
                        Log.d("Add_Data", "Add_Data: $modPath")
                    }
                    showFileBrowser = false
                },
                mode = FileBrowserMode.FOLDER
            )
        }
        fun handleSearchResult(modValue: ModValue) {
            viewModel.navigateToMod(
                modValue = modValue,
                categorizedModValues = categorizedModValues,
                setSelectedTabIndex = { selectedTabIndex = it },
                lazyListState = lazyListState,
                coroutineScope = coroutineScope,
                setHighlightedCardId = { id -> highlightedCardId = id }  // Pass the state setter
            )
            showSearchDialog = false
            showMods = true
        }
        AnimatedVisibility(
            visible = showSearchDialog,
            enter = fadeIn() + expandIn(),
            exit = fadeOut() + shrinkOut()
        ) {
            Column(
                modifier = Modifier
                    .padding(8.dp)
                    .fillMaxSize(),  // Increased height for filter options
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(stringRes(R.string.search_mods), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Category Filter Checkboxes
                    categories.forEach { category ->
                        val isChecked = selectedCategory == category.key
                        Checkbox(
                            checked = isChecked,
                            onCheckedChange = { checked ->
                                selectedCategory = if (checked) category.key else null
                                // Trigger search update when category changes
                                searchResults = if (checked) {
                                    viewModel.searchMods(searchQuery, modValues, category.key)  // Show only selected category
                                } else {
                                    emptyList()  // Or use searchMods(searchQuery, modValues, null) to show all
                                }
                            }
                        )
                        Text(category.tag)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                TextField(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        // Always include selectedCategory in the search
                        searchResults = viewModel.searchMods(it, modValues, selectedCategory)
                    },
                    label = { Text(stringResource(R.string.search)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(color = Color.Black)
                ) {
                    items(searchResults, key = { it.id }) { result ->
                        Text(
                            text = result.value,
                            modifier = Modifier
                                .padding(8.dp)
                                .clickable {
                                    handleSearchResult(result)
                                }
                        )
                    }
                }
            }
        }
        AnimatedVisibility(
            visible = showNav,
            enter = fadeIn() + expandIn(),
            exit = fadeOut() + shrinkOut()
        ) {
            BarOptions(context)
        }
        AnimatedVisibility(
            visible = s3LightsDialog,
            enter = fadeIn() + expandIn(),
            exit = fadeOut() + shrinkOut()
        ) {
            S3LightFixes()
        }
        AnimatedVisibility(
            visible = terminal,
            enter = fadeIn() + expandIn(),
            exit = fadeOut() + shrinkOut()
        ) {
            Terminal()
        }
        AnimatedVisibility(
            visible = downloader,
            enter = fadeIn() + expandIn(),
            exit = fadeOut() + shrinkOut()
        ) {
            SSOHome()
        }
        AnimatedVisibility(
            visible = kram,
            enter = fadeIn() + expandIn(),
            exit = fadeOut() + shrinkOut()
        ) {
            KramConversion(context)
        }
        AnimatedVisibility(
            visible = showDelta,
            enter = fadeIn() + expandIn(),
            exit = fadeOut() + shrinkOut()
        ) {
            DeltaScreen()
        }
        // Show dialog when showDialog is true
        if (showDialogSettings) {
            LandscapeSettings(newFeatureEnabledChecked)
        } else {
            // Update UI after dialog closes
            categorizedModValues = ModValue.updateUIFromModValues(categories.map { it.key })

        }

        ScrollArea(state = stateSB) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
                state = lazyListState,
                contentPadding = PaddingValues(8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (showMods) {
                    items(
                        categorizedModValues[selectedTabIndex],
                        key = { it.stableId }) { modValue ->
                        ReorderableItem(reorderableLazyListState, key = modValue.stableId) { isDragging ->
//                            var isDragging by remember { mutableStateOf(false) }
                            var showPopup by remember { mutableStateOf(false) }
                            var showDialog2 by remember { mutableStateOf(false) }
                            val isChecked = modValue.isChecked
                            var showDeleteConfirmation by remember { mutableStateOf(false) }
                            var showMoveDialog by remember { mutableStateOf(false) }
                            val isHighlighted = modValue.id == highlightedCardId
                            val view = LocalView.current
                            val backgroundColor by animateColorAsState(
                                when {
                                    isHighlighted -> Color(0xFF4CAF50)
                                    isDragging -> Color(0xFF8BC34A) // Color during drag
                                    modValue.isChecked -> Color.DarkGray
                                    else -> Color.DarkGray.copy(alpha = 0.5f)
                                }, label = ""
                            )
                            val scaling by animateFloatAsState(if (isDragging) 1.05f else 1f)
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = backgroundColor,
                                ),
                                modifier = Modifier
                                    .graphicsLayer {
                                        scaleX = scaling
                                        scaleY = scaling
                                    },
                                elevation = CardDefaults.cardElevation(
                                    if (isDragging) 0.dp else 0.dp // fix transparent shadow bug
                                ),
                                onClick = { showPopup = true },
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->

                                            // Update in-memory state
                                            val currentList = categorizedModValues[selectedTabIndex].toMutableList()
                                            val index = currentList.indexOfFirst { it.id == modValue.id }
                                            if (index != -1) {
                                                currentList[index] = currentList[index].copy(isChecked = checked)
                                                categorizedModValues = categorizedModValues.toMutableList().apply {
                                                    this[selectedTabIndex] = currentList
                                                }
                                            }

                                            // Directly modify just this line in the file
                                            val file = File(Constants.USER_OPENMW_CFG)
                                            if (file.exists()) {
                                                val lines = file.readLines()
                                                val newLines = lines.map { line ->
                                                    val trimmed = line.trim()
                                                    when {
                                                        trimmed == "${modValue.category}=${modValue.value}" && !checked ->
                                                            ";${modValue.category}=${modValue.value}"
                                                        trimmed == ";${modValue.category}=${modValue.value}" && checked ->
                                                            "${modValue.category}=${modValue.value}"
                                                        else -> line
                                                    }
                                                }
                                                file.writeText(newLines.joinToString("\n"))
                                            }
                                        },
                                        colors = CheckboxDefaults.colors(
                                            checkedColor = Color.Transparent,
                                            uncheckedColor = Color.Red.copy(alpha = 0.5f),
                                            checkmarkColor = Color.Green,
                                            disabledCheckedColor = Color.Red,
                                            disabledUncheckedColor = Color.Red
                                        )
                                    )
                                    Column(modifier = Modifier
                                        .padding(4.dp)
                                        .weight(1f)) {
                                        var translatedText by remember { mutableStateOf("") }
                                        if (translationChecked) {
                                            TranslateText(
                                                context = context,
                                                inputText = modValue.value,
                                                onTranslationResult = { result ->
                                                    translatedText =
                                                        result
                                                }
                                            )
                                        }
                                        Text(
                                            text = if (translationChecked) translatedText else modValue.value.replace("/storage/emulated/0/", ""),
                                            style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                        )
                                        Text(
                                            text = String.format(stringResource(R.string.load_order_s), modValue.id.toString()),
                                            style = MaterialTheme.typography.bodySmall.copy(color = Color.White)
                                        )
                                    }
                                    Box(
                                        modifier = Modifier,
                                        contentAlignment = Alignment.CenterEnd
                                    ) {
                                        IconButton(
                                            modifier = Modifier.draggableHandle(
                                                onDragStarted = {
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                    }
                                                },
                                                onDragStopped = {
                                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                                    }

                                                    val currentList = categorizedModValues[selectedTabIndex].toMutableList()

                                                    // Update IDs based on new order
                                                    currentList.forEachIndexed { index, item ->
                                                        currentList[index] = item.copy(id = index + 1)
                                                    }

                                                    categorizedModValues = categorizedModValues.toMutableList().apply {
                                                        this[selectedTabIndex] = currentList
                                                    }

                                                    // Get the category being modified (e.g., "data", "content", "groundcover")
                                                    val modifiedCategory = categories[selectedTabIndex]

                                                    // Write ONLY the modified category's section
                                                    coroutineScope.launch {
                                                        viewModel.writeModValuesToFile(
                                                            modValues = categorizedModValues.flatten(),
                                                            filePath = Constants.USER_OPENMW_CFG,
                                                            targetCategory = modifiedCategory.key,  // This ensures only that section is updated
                                                            onFinish = { isSuccess ->
                                                                if (!isSuccess) {
                                                                    MToast(stringRes(R.string.failed_to_save_openmw_config))
                                                                }
                                                            }
                                                        )
                                                    }
                                                }
                                            ),
                                            onClick = { showDialog2 = true },
                                        ) {
                                            Icon(Icons.Rounded.Menu, contentDescription = "Reorder")
                                        }
                                    }
                                }
                                if (showPopup) {
                                    Popup(
                                        alignment = Alignment.Center,
                                        onDismissRequest = {
                                            showPopup = false
                                        } // Hide the popup when dismissed
                                    ) {
                                        Surface(
                                            modifier = Modifier.padding(16.dp),
                                            shape = MaterialTheme.shapes.medium,
                                            color = MaterialTheme.colorScheme.background
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(16.dp),
                                                horizontalAlignment = Alignment.CenterHorizontally
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.choose_an_action_for_the_selected_mod),
                                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text(
                                                    text = "${stringResource(R.string.name)}: ${modValue.value}",
                                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                                )
                                                Text(
                                                    text = "${stringResource(R.string.category)}: ${modValue.category}",
                                                    style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                                )
                                                Spacer(modifier = Modifier.height(8.dp))

                                                Button(
                                                    onClick = {
                                                        // Handle the switch category action
                                                        val updatedList = categorizedModValues[selectedTabIndex].toMutableList()
                                                        val index = updatedList.indexOfFirst { it.id == modValue.id }
                                                        if (index != -1) {
                                                            val newCategory = if (modValue.category == "content") "groundcover" else "content"
                                                            updatedList[index] = updatedList[index].copy(category = newCategory)
                                                            categorizedModValues = categorizedModValues.toMutableList().apply {
                                                                this[selectedTabIndex] = updatedList
                                                            }

                                                            // Update the file by removing the matching line and appending to the correct category
                                                            val file = File(Constants.USER_OPENMW_CFG)
                                                            val updatedLines = mutableListOf<String>()
                                                            if (file.exists()) {
                                                                file.forEachLine { line ->
                                                                    // Skip the line that matches the modValue to remove it
                                                                    if (line.trim() != ";${modValue.category}=${modValue.value}" && line.trim() != "${modValue.category}=${modValue.value}") {
                                                                        updatedLines.add(line)
                                                                    }
                                                                }
                                                            }

                                                            // Find the last line with the new category to append after it
                                                            val newLine = "${newCategory}=${modValue.value}"
                                                            val lastCategoryIndex = updatedLines.indexOfLast { it.trim().startsWith("$newCategory=") }
                                                            val appendIndex = if (lastCategoryIndex != -1) {
                                                                // Append after the last line of the new category
                                                                lastCategoryIndex + 1
                                                            } else {
                                                                // If no lines exist for the new category, append at the end
                                                                updatedLines.size
                                                            }

                                                            // Insert the new line at the determined index
                                                            updatedLines.add(appendIndex, newLine)

                                                            // Write the updated content back to the file
                                                            file.writeText(updatedLines.joinToString("\n") + if (updatedLines.lastOrNull()?.isNotEmpty() == true) "\n" else "")

                                                            // Reload the mod values and update the UI
                                                            categorizedModValues = ModValue.updateUIFromModValues(categories.map { it.key })
                                                        }
                                                        showPopup = false
                                                    }
                                                ) {
                                                    Text(

                                                        text = "${stringResource(R.string.switch_to)} ${if (modValue.category == "content") 
                                                            stringResource(R.string.groundcover) 
                                                        else 
                                                            stringResource(R.string.content)
                                                        }",
                                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.height(8.dp))

                                                Button(
                                                    onClick = {
                                                        // Show the delete confirmation dialog
                                                        showDeleteConfirmation = true
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.delete),
                                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))

                                                Button(
                                                    onClick = {
                                                        // Show the move dialog
                                                        showMoveDialog = true
                                                    }
                                                ) {
                                                    Text(
                                                        text = stringResource(R.string.move),
                                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                                    )
                                                }

                                                Spacer(modifier = Modifier.height(8.dp))

                                                Button(onClick = { showPopup = false }) {
                                                    Text(
                                                        text = stringRes(R.string.btn_cancel),
                                                        style = MaterialTheme.typography.bodyMedium.copy(color = Color.White)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                if (showDeleteConfirmation) {
                                    AlertDialog(
                                        onDismissRequest = { showDeleteConfirmation = false },
                                        title = { Text(stringResource(R.string.confirm_deletion)) },
                                        text = { Text(stringResource(R.string.are_you_sure_you_want_to_delete_this_mod)) },
                                        confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    // Handle the delete action
                                                    val updatedList = categorizedModValues[selectedTabIndex].toMutableList()
                                                    val index = updatedList.indexOfFirst { it.id == modValue.id }
                                                    if (index != -1) {
                                                        updatedList.removeAt(index)
                                                        categorizedModValues = categorizedModValues.toMutableList().apply {
                                                            this[selectedTabIndex] = updatedList
                                                        }

                                                        // Update the file by removing the matching line
                                                        val file = File(Constants.USER_OPENMW_CFG)
                                                        val updatedLines = mutableListOf<String>()
                                                        if (file.exists()) {
                                                            file.forEachLine { line ->
                                                                // Skip the line that matches the modValue to remove it
                                                                if (line.trim() != ";${modValue.category}=${modValue.value}" && line.trim() != "${modValue.category}=${modValue.value}") {
                                                                    updatedLines.add(line)
                                                                }
                                                            }
                                                        }

                                                        // Write the updated content back to the file, ensuring no trailing empty line
                                                        file.writeText(updatedLines.joinToString("\n") + if (updatedLines.lastOrNull()?.isNotEmpty() == true) "\n" else "")

                                                        // Reload the mod values and update the UI
                                                        categorizedModValues = ModValue.updateUIFromModValues(categories.map { it.key })
                                                    }
                                                    showDeleteConfirmation = false
                                                    showPopup = false
                                                }
                                            ) {
                                                Text(stringRes(R.string.btn_confirm))
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showDeleteConfirmation = false }) {
                                                Text(stringRes(R.string.btn_cancel))
                                            }
                                        }
                                    )
                                }
                                if (showMoveDialog) {
                                    // Get the initial index of the mod
                                    val initialIndex =
                                        categorizedModValues[selectedTabIndex].indexOfFirst { it.id == modValue.id }
                                    selectedIndex = initialIndex

                                    AlertDialog(
                                        onDismissRequest = { showMoveDialog = false },
                                        title = {
                                            Box(
                                                modifier = Modifier.fillMaxWidth(),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = stringResource(R.string.mod_mover),
                                                    color = Color.Green,
                                                    textAlign = TextAlign.Center
                                                )
                                            }
                                        },
                                        text = {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(stringResource(R.string.select_the_new_position_for_the_mod))
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.Center
                                                ) {

                                                    AndroidView(factory = {
                                                        NumberPicker(context).apply {
                                                            minValue = 0
                                                            maxValue =
                                                                categorizedModValues[selectedTabIndex].size - 1
                                                            value =
                                                                initialIndex  // Set the initial value
                                                            wrapSelectorWheel = true
                                                            // Custom formatter to display index and mod value
                                                            setFormatter { index ->
                                                                val modValues =
                                                                    categorizedModValues[selectedTabIndex].getOrNull(
                                                                        index
                                                                    )?.value ?: ""
                                                                "$index = $modValues"
                                                            }
                                                            setOnValueChangedListener { _, _, newVal ->
                                                                selectedIndex = newVal
                                                            }
                                                        }.also { numberPicker ->
                                                            // Set the layout parameters to make the NumberPicker wider
                                                            numberPicker.layoutParams =
                                                                LinearLayout.LayoutParams(
                                                                    LinearLayout.LayoutParams.MATCH_PARENT,
                                                                    LinearLayout.LayoutParams.WRAP_CONTENT
                                                                ).apply {
                                                                    weight = 1f
                                                                }

                                                            // Remove the grayed-out effect
                                                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                                                numberPicker.setSelectionDividerHeight(0)
                                                            }
                                                        }
                                                    })

                                                }

                                                // Keep the current mod value as the one you clicked on
                                                val currentModValue = modValue.value

                                                // Display the mod value above the selected index
                                                val aboveModValue =
                                                    categorizedModValues[selectedTabIndex].getOrNull(
                                                        selectedIndex - 1
                                                    )?.value
                                                if (aboveModValue != null) {

                                                    Text("${stringResource(R.string.above_mod)}: $aboveModValue")
                                                }
                                                Text(

                                                    text = "${stringResource(R.string.selected_mod)}: $currentModValue",
                                                    color = Color.Green,
                                                    textDecoration = TextDecoration.Underline,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        },
                                        confirmButton = {
                                            val isSamePosition = selectedIndex == initialIndex
                                            TextButton(
                                                onClick = {
                                                    // Handle the move action if the positions are different
                                                    if (!isSamePosition) {
                                                        val updatedList = categorizedModValues[selectedTabIndex].toMutableList()
                                                        val currentIndex = updatedList.indexOfFirst { it.id == modValue.id }

                                                        if (currentIndex != -1) {
                                                            // Implement the logic to move the mod to the new position
                                                            val newPosition = selectedIndex
                                                            Log.d("MoveDialog", "Moving mod from $currentIndex to $newPosition")
                                                            val movedMod = updatedList.removeAt(currentIndex)
                                                            updatedList.add(newPosition, movedMod)

                                                            // Update id for all mods
                                                            updatedList.forEachIndexed { index, modValue ->
                                                                Log.d("MoveDialog", "Mod at index $index has id ${modValue.id}")
                                                            }

                                                            // Update the mod values
                                                            categorizedModValues = categorizedModValues.toMutableList().apply {
                                                                this[selectedTabIndex] = updatedList
                                                            }

                                                            // Update the file by removing the matching line and inserting at the new position
                                                            val file = File(Constants.USER_OPENMW_CFG)
                                                            val updatedLines = mutableListOf<String>()
                                                            if (file.exists()) {
                                                                file.forEachLine { line ->
                                                                    // Skip the line that matches the modValue to remove it
                                                                    if (line.trim() != ";${modValue.category}=${modValue.value}" && line.trim() != "${modValue.category}=${modValue.value}") {
                                                                        updatedLines.add(line)
                                                                    }
                                                                }
                                                            }

                                                            // Find the insertion point for the new position among same-category lines
                                                            val categoryLines = updatedLines.withIndex().filter { it.value.trim().startsWith("${modValue.category}=") }
                                                            val insertIndex = if (categoryLines.isNotEmpty() && newPosition < categoryLines.size) {
                                                                // Insert after the line at newPosition for the same category
                                                                categoryLines[newPosition].index + 1
                                                            } else if (categoryLines.isNotEmpty()) {
                                                                // If newPosition is beyond the last same-category line, append after the last one
                                                                categoryLines.last().index + 1
                                                            } else {
                                                                // If no lines exist for the category, append at the end
                                                                updatedLines.size
                                                            }

                                                            // Insert the new line at the determined index
                                                            updatedLines.add(insertIndex, "${modValue.category}=${modValue.value}")

                                                            // Write the updated content back to the file
                                                            file.writeText(updatedLines.joinToString("\n") + if (updatedLines.lastOrNull()?.isNotEmpty() == true) "\n" else "")

                                                            // Reload the mod values and update the UI
                                                            categorizedModValues = ModValue.updateUIFromModValues(categories.map { it.key })
                                                            Log.d("MoveDialog", "Mod values reloaded")
                                                        } else {
                                                            Log.d("MoveDialog", "Mod with value not found.")
                                                        }
                                                        showMoveDialog = false
                                                        showPopup = false
                                                    } else {
                                                        Log.d("MoveDialog", "Selected index is the same as the current index.")
                                                    }
                                                },
                                                enabled = !isSamePosition
                                            ) {
                                                Text(stringRes(R.string.move))
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showMoveDialog = false }) {
                                                Text(stringRes(R.string.btn_cancel))
                                            }
                                        }
                                    )
                                }

                                if (showDialog2) {
                                    AlertDialog(
                                        onDismissRequest = { showDialog2 = false },
                                        confirmButton = {
                                            TextButton(
                                                onClick = {
                                                    // Handle the switch category action
                                                    val updatedList = categorizedModValues[selectedTabIndex].toMutableList()
                                                    val index = updatedList.indexOfFirst { it.id == modValue.id }
                                                    if (index != -1) {
                                                        val newCategory = if (modValue.category == "content") "groundcover" else "content"
                                                        updatedList[index] = updatedList[index].copy(category = newCategory)
                                                        categorizedModValues = categorizedModValues.toMutableList().apply {
                                                            this[selectedTabIndex] = updatedList
                                                        }

                                                        // Update the file by removing the matching line and appending to the correct category
                                                        val file = File(Constants.USER_OPENMW_CFG)
                                                        val updatedLines = mutableListOf<String>()
                                                        if (file.exists()) {
                                                            file.forEachLine { line ->
                                                                // Skip the line that matches the modValue to remove it
                                                                if (line.trim() != ";${modValue.category}=${modValue.value}" && line.trim() != "${modValue.category}=${modValue.value}") {
                                                                    updatedLines.add(line)
                                                                }
                                                            }
                                                        }

                                                        // Find the last line with the new category to append after it
                                                        val newLine = "${newCategory}=${modValue.value}"
                                                        val lastCategoryIndex = updatedLines.indexOfLast { it.trim().startsWith("$newCategory=") }
                                                        val appendIndex = if (lastCategoryIndex != -1) {
                                                            // Append after the last line of the new category
                                                            lastCategoryIndex + 1
                                                        } else {
                                                            // If no lines exist for the new category, append at the end
                                                            updatedLines.size
                                                        }

                                                        // Insert the new line at the determined index
                                                        updatedLines.add(appendIndex, newLine)

                                                        // Write the updated content back to the file
                                                        file.writeText(updatedLines.joinToString("\n") + if (updatedLines.lastOrNull()?.isNotEmpty() == true) "\n" else "")

                                                        // Reload the mod values and update the UI
                                                        categorizedModValues = ModValue.updateUIFromModValues(categories.map { it.key })
                                                    }
                                                    showDialog2 = false
                                                }
                                            ) {
                                                Text(stringRes(R.string.btn_confirm))
                                            }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { showDialog2 = false }) {
                                                Text(stringRes(R.string.btn_cancel))
                                            }
                                        },
                                        title = { Text(stringResource(R.string.confirm_action)) },
                                        text = { Text(String.format(stringResource(R.string.switch_the_category_to_tip), if (modValue.category == "content") stringRes(
                                            R.string.groundcover
                                        ) else stringRes(R.string.content)
                                        )) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
            VerticalScrollbar(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxHeight()
                    .width(10.dp)
            ) {
                Thumb(
                    modifier = Modifier.background(Color.Black.copy(0.3f), RoundedCornerShape(100)),
                    thumbVisibility = ThumbVisibility.HideWhileIdle(
                        enter = fadeIn(),
                        exit = fadeOut(),
                        hideDelay = 0.5.seconds
                    )
                )
            }
        }
    }
}

@Composable
fun LandscapeSettings(isNewFeatureEnabledChecked: Boolean) {
    val newFeatureEnabledChecked by rememberUpdatedState(isNewFeatureEnabledChecked)
    // Manage what's in the tabs here
    var switches by remember { mutableStateOf(true) } // selected in default
    var iniValues by remember { mutableStateOf(false) }
    var controls by remember { mutableStateOf(false) }
    var dev by remember { mutableStateOf(false) }

    fun resetStates() {
        switches = false
        iniValues = false
        controls = false
        dev = false
    }
    Row(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(4.dp)
                .background(color = customColor),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    resetStates()
                    switches = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = customColor
                )
            ) {
                Text(
                    text = stringResource(R.string.launcher_settings),
                    color = if (switches) Color.Green else Color.White
                )
            }
            Button(
                onClick = {
                    resetStates()
                    iniValues = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = customColor
                )
            ) {
                Text(
                    text = stringResource(R.string.settings_cfg),
                    color = if (iniValues) Color.Green else Color.White
                )
            }
            Button(
                onClick = {
                    resetStates()
                    controls = true
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = customColor
                )
            ) {
                Text(
                    text = stringResource(R.string.controls),
                    color = if (controls) Color.Green else Color.White
                )
            }
            if (newFeatureEnabledChecked) {
                Button(
                    onClick = {
                        resetStates()
                        dev = true
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = customColor
                    )
                ) {
                    Text(
                        text = stringResource(R.string.developer_menu),
                        color = if (dev) Color.Green else Color.White
                    )
                }
            }
        }
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight()
        ) {
            if (switches) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .background(color = customColor),
                ) {
                    FeaturesSwitches()
                }
            }
            if (iniValues) {
                Column(
                    modifier = Modifier
                        .fillMaxSize(),
                ) {
                    IniSettings()
                }
            }
            if (controls) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .background(color = customColor),
                ) {
                    ControlsInsert()
                }
            }
            if (dev) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .background(color = customColor),
                ) {
                    DevInsert()
                }
            }
        }
    }
}

