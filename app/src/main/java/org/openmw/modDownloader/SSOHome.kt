package org.openmw.modDownloader

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion.White
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.keepScreenOn
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.openmw.Constants
import org.openmw.R
import org.openmw.modDownloader.ModListManager.allowExtract
import org.openmw.modDownloader.ModListManager.apiKey
import org.openmw.modDownloader.ModListManager.availableLists
import org.openmw.modDownloader.ModListManager.brokenMods
import org.openmw.modDownloader.ModListManager.modList
import org.openmw.modDownloader.ModListManager.onlySync
import org.openmw.modDownloader.ModListManager.remainingMods
import org.openmw.modDownloader.ModListManager.selectedThreadCount
import org.openmw.modDownloader.ModListManager.validate
import org.openmw.modDownloader.NexusInfo.extractionProgressMap
import org.openmw.modDownloader.NexusInfo.extractionProgressPercentMap
import org.openmw.modDownloader.NexusInfo.isNexusExpanded
import org.openmw.modDownloader.SevenZip.isInitialized
import org.openmw.ui.controls.UIStateManager.customColor
import org.openmw.ui.controls.UIStateManager.gold
import org.openmw.ui.view.ProgressDialog
import org.openmw.ui.view.getAvailableStorageSpace
import org.openmw.ui.view.getMessages
import org.openmw.utils.GameFilesPreferences.IS_NEXUS_PREMIUM
import org.openmw.utils.GameFilesPreferences.NEXUS_API_KEY
import org.openmw.utils.GameFilesPreferences.getNexusAPIFlow
import org.openmw.utils.GameFilesPreferences.loadIsPremiumTier
import org.openmw.utils.dataStore
import org.openmw.utils.stringRes
import java.io.File

object StatusInfo {
    var processing by mutableStateOf(false)
    var activeMods by mutableIntStateOf(0)
}

@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .wrapContentHeight()
            .fillMaxWidth()
            .background(customColor)
            .padding(8.dp)
    ) {
        Column {
            Text(
                "Please enter your nexus API key,\nif nothing happens when you try to press the ok button the key is not valid ",
                style = MaterialTheme.typography.labelLarge,
                color = gold
            )
            OutlinedTextField(
                value = apiKey.replace("null", ""),
                onValueChange = {
                    apiKey = it
                },
                label = { Text(stringResource(R.string.nexus_api_key)) },
                modifier = Modifier
                    .height(100.dp)
                    .fillMaxWidth()
            )
            Spacer(modifier = Modifier.padding(8.dp))
            Row {
                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.Default) {
                            NexusInfo.openWebView(
                                url = "https://users.nexusmods.com/auth/sign_in?redirect_url=https%3A%2F%2Fwww.nexusmods.com%2F",
                                fileName = "null",
                                id = "null"
                            )
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = gold,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Sign in to nexus (If free user)")
                }
                Spacer(modifier = Modifier.weight(1f))

                TextButton(
                    onClick = {
                        scope.launch(Dispatchers.Default) {
                            /*
                            context.dataStore.edit { preferences ->
                                preferences.remove(IS_NEXUS_PREMIUM)
                                preferences.remove(NEXUS_API_KEY)
                            }
                             */
                            try {
                                validate(context)
                            } catch (e: Exception) {
                                Log.e("VALIDATE_ERROR", "Validation failed: ${e.message}")
                                // Handle error appropriately
                            } finally {
                                context.dataStore.edit { preferences ->
                                    preferences[NEXUS_API_KEY] = apiKey
                                }
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = gold,
                        contentColor = Color.Black
                    )
                ) {
                    Text("OK")
                }
            }
        }
    }
}

@Composable
fun SSOHome() {
    val context = LocalContext.current
    val modDao = ModDatabase.getDatabase(context).modDao()
    var settings by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val _mods = remember { MutableStateFlow<List<ModDesc>>(emptyList()) }
    var currentTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var showInitiatingDialog by rememberSaveable { mutableStateOf(false) }
    val webViewTabs by NexusInfo.webViewTabsFlow.collectAsState(initial = emptyList())
    val fetchNexusAPI by getNexusAPIFlow(context).collectAsState(initial = "")
    var showModsLog by remember { mutableStateOf(false) }
    val logMessages = remember { mutableStateListOf<String>() }
    val isPremium by loadIsPremiumTier(context).collectAsState(initial = null)
    var showLogs by remember { mutableStateOf(false) }
    val isValidated by remember {
        derivedStateOf { isPremium != null }
    }

    AnimatedVisibility(
        visible = showModsLog,
        enter = fadeIn() + expandIn(),
        exit = fadeOut() + shrinkOut()
    ) {
        Dialog(
            onDismissRequest = { showModsLog = false }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Remaining mods: $remainingMods")

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        reverseLayout = true // Newest at the top
                    ) {
                        items(logMessages.reversed()) { message ->
                            Text(
                                text = message,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                color = when {
                                    message.contains("ERROR", ignoreCase = true) -> Color.Red
                                    message.contains("WARN", ignoreCase = true) -> Color.Yellow
                                    message.contains("INFO", ignoreCase = true) -> Color.Green
                                    else -> White
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp)
                            )
                        }
                    }

                    Button(
                        onClick = { showModsLog = false },
                        modifier = Modifier.align(Alignment.End),
                        colors = ButtonDefaults.buttonColors(containerColor = gold)
                    ) {
                        Text("Close")
                    }
                }
            }
        }
    }

    LaunchedEffect(showModsLog) {
        while (showModsLog) {
            logMessages.clear()
            logMessages.addAll(getMessages())
            delay(1000) // Refresh every second
        }
    }

    LaunchedEffect(Unit) {
        launch(Dispatchers.Default) {
            _mods.value = ModListManager.downloadAndParseModList(context)

            val potiFile = File("${Constants.USER_FILE_STORAGE}/OpenMW/POTI.modlist")
            if (potiFile.exists()) {
                val count = importPotiModlistStructured(context, potiFile.path)
                Log.d("MAIN", "Imported $count POTI mods")

                // Append to the list
                if (!availableLists.contains("poti")) {
                    availableLists.add("poti")
                }
            } else {
                Log.w("MAIN", "POTI.modlist file not found at ${potiFile.path}")
            }
        }

        println("Entry nexusAPI: $fetchNexusAPI")
        println("Entry isNowPremium: $isPremium")
        println("Entry isValidated: $isValidated")
    }

    LaunchedEffect(fetchNexusAPI, isPremium) {
        apiKey = "$fetchNexusAPI"
        println("nexusAPI changed: $fetchNexusAPI")
        println("apiKey changed: $apiKey")
        println("isNowPremium changed: $isPremium")
        println("isValidated Changed: $isValidated")
    }

    isInitialized()

    Log.d("MainActivity", "Current webViewUrls: $webViewTabs, currentTabIndex: $currentTabIndex")
    LaunchedEffect(webViewTabs) {
        if (webViewTabs.isEmpty()) {
            currentTabIndex = 0
        } else if (currentTabIndex >= webViewTabs.size) {
            currentTabIndex = webViewTabs.size - 1
        }
        Log.d("MainActivity", "Updated currentTabIndex: $currentTabIndex, tabs size: ${webViewTabs.size}")
    }
    if (showInitiatingDialog) {
        ProgressDialog(
            text = stringRes(R.string.loading)
        )
    }

    if (showLogs) {
        LogViewerDialog(onDismiss = { showLogs = false })
    }

    if (
        webViewTabs.isNotEmpty() &&
        currentTabIndex in webViewTabs.indices &&
        webViewTabs[currentTabIndex].url.isNotBlank()
    ) {
        Log.d(
            "MainActivity",
            "Showing WebViewScreen with URL: ${webViewTabs[currentTabIndex].url}"
        )
        Box(modifier = Modifier.wrapContentSize()) {
            WebViewScreen(
                tabs = webViewTabs,
                currentTabIndex = currentTabIndex,
                onTabChange = { index -> currentTabIndex = index },
                onCloseTab = { tabId ->
                    scope.launch {
                        NexusInfo.closeWebViewTab(tabId)
                    }
                }
            )
        }
    }

    if (settings && isValidated) {
        AlertDialog(
            onDismissRequest = { settings = false },
            confirmButton = {
                TextButton(onClick = { settings = false }) {
                    Text("OK", color = gold)
                }
            },
            dismissButton = {
                TextButton(onClick = { settings = false }) {
                    Text("Cancel", color = gold)
                }
            },
            title = {
                Text("Download Options", color = gold)
            },
            text = {
                Column {
                    Text(
                        brokenMods,
                        style = MaterialTheme.typography.labelSmall
                    )
                    HorizontalDivider(thickness = 2.dp)
                    if (!onlySync) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (allowExtract) "Allow Archive Extraction" else "Download Only", color = gold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Switch(
                                checked = allowExtract,
                                onCheckedChange = { newValue ->
                                    allowExtract = newValue
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = gold,
                                    uncheckedThumbColor = gold.copy(alpha = 0.5f),
                                    checkedTrackColor = Color.Black.copy(alpha = 0.9f),
                                    uncheckedTrackColor = Color.Black.copy(alpha = 0.5f),
                                    checkedBorderColor = White,
                                    uncheckedBorderColor = White
                                )
                            )
                        }
                    }
                    HorizontalDivider(thickness = 1.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (onlySync) "Only do the sync process" else "Sync, Download, and Extract", color = gold
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        Switch(
                            checked = onlySync,
                            onCheckedChange = { newValue ->
                                onlySync = newValue
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = gold,
                                uncheckedThumbColor = gold.copy(alpha = 0.5f),
                                checkedTrackColor = Color.Black.copy(alpha = 0.9f),
                                uncheckedTrackColor = Color.Black.copy(alpha = 0.5f),
                                checkedBorderColor = White,
                                uncheckedBorderColor = White
                            )
                        )
                    }
                    HorizontalDivider(thickness = 2.dp)
                    HorizontalDivider(color = Color.Transparent, thickness = 20.dp)
                    HorizontalDivider(thickness = 2.dp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                scope.launch(Dispatchers.IO) {
                                    modDao.clearAllMods()
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(2.dp, White)
                                .background(Color.Red.copy(alpha = 0.2f)),
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = White
                            )
                        ) {
                            Text(
                                "Wipe database",
                                style = TextStyle(color = gold)
                            )
                        }
                    }
                    HorizontalDivider(thickness = 2.dp)
                    HorizontalDivider(color = Color.Transparent, thickness = 20.dp)
                    if (fetchNexusAPI != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.approval_delegation_24dp_e8eaed_fill0_wght400_grad0_opsz24),
                                contentDescription = "Checkmark",
                                colorFilter = ColorFilter.tint(Color.Green),
                                modifier = Modifier
                                    .size(36.dp)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Button(
                                onClick = {
                                    // Remove the API key and set it to an empty string
                                    scope.launch {
                                        context.dataStore.edit { preferences ->
                                            preferences.remove(IS_NEXUS_PREMIUM)
                                            preferences.remove(NEXUS_API_KEY)
                                        }
                                        apiKey = ""
                                        settings = false
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = customColor, // Background color
                                    contentColor = Color.White   // Text color
                                )
                            ) {
                                Text(stringResource(R.string.remove_api_key), color = gold)
                            }
                        }
                    } else {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text(stringResource(R.string.nexus_api_key)) },
                            modifier = Modifier
                                .height(100.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                // Save the value to DataStore
                                scope.launch {
                                    context.dataStore.edit { preferences ->
                                        preferences[NEXUS_API_KEY] = apiKey
                                    }
                                    validate(context)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = customColor, // Background color
                                contentColor = Color.White   // Text color
                            )
                        ) {
                            Text(stringResource(R.string.save), color = gold)
                        }
                    }
                }
            }
        )
    }

    if (isValidated) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(10.dp)
                .keepScreenOn()
        ) {
            Row(
                modifier = Modifier
                    .height(48.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "Settings",
                    modifier = Modifier
                        .size(48.dp)
                        .clickable {
                            settings = !settings
                        }
                )
                Spacer(modifier = Modifier.width(8.dp))
                AvailableModLists(modDao = modDao)
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(64.dp)
                ) {
                    if (!StatusInfo.processing) {
                        IconButton(
                            onClick = {
                                settings = false
                                isNexusExpanded = true
                                StatusInfo.processing = true
                                scope.launch(Dispatchers.Default) {
                                    processModList(context, isPremium!!).collect { processedMods ->
                                        // Optional: Log or handle processed mods
                                        Log.d(
                                            "RateLimitDisplay",
                                            "Processed ${processedMods.size} mods"
                                        )
                                    }
                                }
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val jsonString =
                                            fetchJsonFromUrl("https://modding-openmw.com/api/cfg-generator/$modList")
                                        val config = parseOpenMWConfig(jsonString)
                                        val formattedConfig = formatOpenMWConfig(context, config)
                                        val savedFile = saveConfigToFile(
                                            context,
                                            "$modList.cfg",
                                            formattedConfig
                                        )
                                        Log.d("ConfigSave", "Saved to ${savedFile.absolutePath}")
                                    } catch (e: Exception) {
                                        Log.e("ConfigFetch", "Error fetching config: ${e.message}")
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(customColor)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = gold,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    } else {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(46.dp)
                                .clickable { showModsLog = true }
                        ) {
                            CircularProgressIndicator(
                                color = gold,
                                modifier = Modifier.size(46.dp)
                            )
                            Text(
                                text = "$remainingMods",
                                color = gold, // or Color.White for better contrast
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
            RateLimitDisplay()
            ModListScreen()
        }
    } else {
        OnboardingScreen()
    }
}

@Composable
fun AvailableModLists(modDao: ModDao) {
    var expanded by remember { mutableStateOf(false) }

    // Get all available lists (hardcoded + JSON)
    val allAvailableLists = getAllAvailableLists(modDao)

    // Filter out lists with "-wip" in their name
    val filteredLists = allAvailableLists.filter { !it.contains("-wip") }

    // Collect all mods as state
    val mods by modDao.getAllModsFlow().collectAsState(initial = emptyList())

    // Compute counts per list
    val modCounts = remember(mods) {
        val counts = mutableMapOf<String, Int>()
        mods.forEach { mod ->
            mod.onLists.forEach { listName ->
                counts[listName] = (counts[listName] ?: 0) + 1
            }
        }
        counts
    }

    Box(
        modifier = Modifier
            .wrapContentSize()
    ) {
        Row(
            modifier = Modifier
                .wrapContentSize(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = { expanded = true },
                modifier = Modifier
                    .wrapContentSize(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = customColor,
                    contentColor = White,
                    disabledContainerColor = Color.Gray,
                    disabledContentColor = Color.DarkGray
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, gold)
            ) {
                Text(modList)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(customColor)
                .border(1.dp, Color.White)
        ) {
            filteredLists.forEach { option ->
                val count = modCounts[option] ?: 0
                val sizeForThisListKb = mods
                    .filter { it.onLists.contains(option) }
                    .flatMap { it.downloadInfo }
                    .mapNotNull { it.fileSize?.toLongOrNull() }
                    .sum()

                val extractionSizeBytes = mods
                    .filter { it.onLists.contains(option) }
                    .flatMap { it.downloadInfo }
                    .mapNotNull { it.extractedSizeBytes }
                    .sum()

                DropdownMenuItem(
                    text = {
                        Column {
                            // Highlight custom lists from JSON files
                            val isCustomList = option !in availableLists

                            Text(
                                text = if (isCustomList) "📁 $option (Mods: $count)" else "$option (Mods: $count)",
                                color = if (isCustomList) Color(0xFF4CAF50) else Color.White
                            )
                            Text(
                                "Download Size: ${formatSize(sizeForThisListKb)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(
                                "Extracted Size: ${formatSize(extractionSizeBytes / 1024)}",
                                style = MaterialTheme.typography.labelSmall
                            )
                            HorizontalDivider(color = White, thickness = 1.dp)
                        }
                    },
                    onClick = {
                        modList = option
                        expanded = false
                    }
                )
            }
        }
    }
}

// Add this function to get all available lists (hardcoded + JSON)
@Composable
fun getAllAvailableLists(modDao: ModDao): List<String> {

    // Collect all mods to extract lists from JSON files
    val mods by modDao.getAllModsFlow().collectAsState(initial = emptyList())

    // Extract unique list names from all mods
    val jsonLists = remember(mods) {
        mods.flatMap { it.onLists }.distinct()
    }

    // Combine and remove duplicates
    return remember(mods) {
        (availableLists + jsonLists).distinct().sorted()
    }
}

@Composable
fun RateLimitDisplay() {
    val context = LocalContext.current
    val availableSpace = getAvailableStorageSpace()
    val modDao = remember { ModDatabase.getDatabase(context).modDao() }
    var totalMods by remember { mutableStateOf(0) }
    val isPremium by loadIsPremiumTier(context).collectAsState(initial = null)

    LaunchedEffect(Unit) {
        totalMods = modDao.getModCount()
    }

    Column(
        modifier = Modifier
            .padding(8.dp)
            .background(
                color = when {
                    NexusInfo.isNearLimit -> MaterialTheme.colorScheme.errorContainer
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                shape = MaterialTheme.shapes.small
            )
            .animateContentSize()
            .clickable { isNexusExpanded = !isNexusExpanded } // Toggle on click
            .padding(8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (isNexusExpanded) {
            // Expanded state: Show full content
            // Title with premium badge
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Nexus API Limits",
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                if (isPremium== true) {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Text("Premium")
                    }
                } else {
                    Badge(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                    ) {
                        Text("Free User")
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            RateLimitBar(
                name = "Hourly",
                remaining = NexusInfo.hourlyremaining,
                limit = NexusInfo.hourlylimit,
                resetText = NexusInfo.hourlyResetFormatted,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            RateLimitBar(
                name = "Daily",
                remaining = NexusInfo.dailyremaining,
                limit = NexusInfo.dailylimit,
                resetText = NexusInfo.dailyResetFormatted,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(8.dp))


            Text(
                text = "Free Space: $availableSpace    Cached Mods: $totalMods    $selectedThreadCount Threads Detected",
                color = Color.White,
                fontSize = 12.sp
            )
            HorizontalDivider(color = Color.White, thickness = 1.dp)

            val statusPriority = mapOf(
                ModStatus.DOWNLOADING to 0,
                ModStatus.EXTRACTING to 1,
                ModStatus.IDLE to 2,
                ModStatus.COMPLETED to 3
            )

            NexusInfo.downloadProgressMap
                .toList()
                .sortedBy { (modId, _) -> statusPriority[getModStatus(modId)] ?: 4 }
                .forEach { (modId, progress) ->
                    val extractionStatus = extractionProgressMap[modId] ?: "Waiting..."
                    val showDownloadRow = extractionStatus.contains("Waiting")

                    if (showDownloadRow) {
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Text(
                                text = progress.replace("Downloaded", ""),
                                modifier = Modifier.weight(1f)
                            )
                            if (progress.contains("Downloaded")) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Download Complete",
                                    tint = if (allowExtract) Color.Green.copy(alpha = 0.6f) else Color.Green,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            } else if (progress.contains("Failed")) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "Failed",
                                    tint = Color.Red,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            } else {
                                Text(
                                    text = "${NexusInfo.downloadProgressPercentMap[modId]?: 0}% (${NexusInfo.downloadSpeedMap[modId]?: 0} KB/s)",
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                    if (allowExtract) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = extractionStatus.replace("Extracted", ""),
                                modifier = Modifier.weight(1f)
                            )
                            if (extractionStatus.contains("Extracted")) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Extraction Complete",
                                    tint = Color.Green,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            } else if (extractionStatus.contains("Failed")) {
                                Icon(
                                    imageVector = Icons.Default.Cancel,
                                    contentDescription = "Failed",
                                    tint = Color.Red,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            } else {
                                Text(
                                    text = "${extractionProgressPercentMap[modId]?.toInt() ?: 0}%",
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = White, thickness = 1.dp)
                }

            // Warning message when near limit
            if (NexusInfo.isNearLimit) {
                Text(
                    text = "Approaching rate limit!",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        } else {
            // Collapsed state: Show only hourly and daily limits in a Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hourly: ${NexusInfo.hourlyremaining}/${NexusInfo.hourlylimit}",
                    style = MaterialTheme.typography.labelMedium
                )
                Text(
                    text = "Daily: ${NexusInfo.dailyremaining}/${NexusInfo.dailylimit}",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun RateLimitBar(
    name: String,
    remaining: Int,
    limit: Int,
    resetText: String,
    color: Color
) {
    val percentage = remaining.toFloat() / limit

    Column {
        // Label and count
        Row(
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Resets in: $resetText",
                    style = MaterialTheme.typography.labelSmall,
                    color = color.copy(alpha = 0.7f)
                )
            }
            Text(
                text = "$remaining / $limit",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (percentage < 0.2f) FontWeight.Bold else FontWeight.Normal
            )
        }

        // Progress bar
        LinearProgressIndicator(
            progress = { percentage },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .padding(top = 4.dp),
            color = color,
            trackColor = color.copy(alpha = 0.2f)
        )

        // Percentage text
        Text(
            text = "${(percentage * 100).toInt()}% remaining",
            style = MaterialTheme.typography.labelSmall,
            color = color.copy(alpha = 0.8f),
            modifier = Modifier.align(Alignment.End)
        )
    }
}
