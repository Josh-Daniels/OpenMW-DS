package org.openmw.ui.page.simplified

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.InputMode
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmw.BuildConfig
import org.openmw.Constants
import org.openmw.R
import org.openmw.ui.controls.UIStateManager.customCFG
import org.openmw.ui.page.main.MainPageViewModel
import org.openmw.ui.page.mod.ModAssistantViewModel
import org.openmw.ui.page.mod.ModValue
import org.openmw.ui.page.mod.readModValues
import org.openmw.ui.page.setting.SettingRow
import org.openmw.ui.theme.MwBone
import org.openmw.ui.theme.MwBoneBright
import org.openmw.ui.theme.MwBoneDim
import org.openmw.ui.theme.MwBronze
import org.openmw.ui.theme.MwBronzeDark
import org.openmw.ui.theme.MwBronzeLight
import org.openmw.ui.theme.MwFloatStone
import org.openmw.ui.theme.MwSlotBg
import org.openmw.ui.theme.MwStoneDark
import org.openmw.ui.theme.MwSlotWorn
import org.openmw.ui.view.AlphaMigrationButtons
import org.openmw.ui.view.BackgroundAnimation
import org.openmw.ui.view.SetupButton
import org.openmw.ui.view.attemptLaunchGame
import org.openmw.ui.view.resetUserSettingsFile
import org.openmw.utils.AlphaMigration
import org.openmw.utils.ApkInstaller
import org.openmw.utils.FileBrowserMode
import org.openmw.utils.InstallResult
import org.openmw.utils.FileBrowserPopup
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.GameFilesPreferences.readCodeGroup
import org.openmw.utils.IniSettings
import org.openmw.utils.MToast
import org.openmw.utils.OpenMWConfigUtils
import org.openmw.utils.UpdateChecker
import org.openmw.utils.UpdateInfo
import org.openmw.utils.UpdateState
import org.openmw.utils.stringRes
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.io.File

/** How long the mod panel watches openmw.cfg for a late async write after a refresh trigger.
 *  `processSelectedFolder` writes it fire-and-forget, so "the path was saved" does not mean "the
 *  cfg is on disk yet". ~2s of cheap stat() polling, then it stops; a full re-read only happens
 *  when the file actually changed. */
private const val CFG_SETTLE_ATTEMPTS = 10
private const val CFG_SETTLE_DELAY_MS = 200L

/** Bounded retry for giving the Play button initial focus, so the controller's confirm button
 *  starts the game on the FIRST press. One pass is the normal case — the retry only covers a
 *  cold-start race with the window settling. Deliberately small: if focus is still refused after
 *  this, something structural is wrong and looping longer would only hide it. */
private const val PLAY_FOCUS_ATTEMPTS = 3
private const val PLAY_FOCUS_RETRY_MS = 100L

/** Width of the centred mod load-order card, as a fraction of the screen. The action row and Play
 *  button stay full width — only this middle panel narrows. */
private const val MOD_PANEL_WIDTH_FRACTION = 0.8f

/** Width of the centred "Transfer from Alpha3" card on the settings screen. Matches the mod-list
 *  panel's fraction so the two bordered cards read as the same visual family. */
private const val TRANSFER_SECTION_WIDTH_FRACTION = 0.6f

/** Width of the centred "OpenMW Settings" card. Wider than the transfer card because it holds
 *  full-width option rows rather than buttons; its HEIGHT is unconstrained so it grows with the
 *  section drop-downs. */
private const val SETTINGS_SECTION_WIDTH_FRACTION = 0.9f

/** Attention colour for the update badge and banner — the same warm orange as the setup buttons
 *  (`SetupOrange`), mirrored locally so this file needs no new theme import. It reads as "notice
 *  me" against the bronze/bone palette without being an error red. */
private val UpdateAccent = Color(0xFFEDA95B)

/** The repo's human-facing releases page. A specific release is this plus `/tag/<tag_name>`; the
 *  bare page is the fallback when no tag is known. Deliberately NOT the API URL in UpdateChecker
 *  (that returns JSON) and NOT `/releases/latest` (a redirect that would show whatever is newest
 *  rather than the release we actually told the user about). */
private const val RELEASES_PAGE_URL = "https://github.com/Josh-Daniels/OpenMW-DS/releases"

/**
 * The update being offered, or null when there is nothing to tell the user about.
 *
 * Available/Downloading/Ready all mean "a newer release is known" — the badge, banner and release
 * notes link should stay put across a download rather than blinking out the moment one starts.
 * Idle/Checking/UpToDate/Failed all mean "nothing to offer", which is what makes the badge clear
 * itself automatically once a check comes back up to date.
 */
private fun UpdateState.offeredUpdate(): UpdateInfo? = when (this) {
    is UpdateState.Available -> info
    is UpdateState.Downloading -> info
    is UpdateState.Ready -> info
    else -> null
}

/** Nearest Activity for [this] context, unwrapping ContextWrapper layers; null if there is none. */
private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/**
 * Open the GitHub release page for [tag] in the browser, falling back to the general releases
 * page when the tag is unknown.
 *
 * No `topScreenLaunchOptions()` here, for the same reason ApkInstaller omits it: this is only
 * reachable from the launcher settings screen hosted by MainActivity, which already self-corrects
 * onto display 0 — so inheriting the caller's display puts the browser on the screen the user just
 * tapped, which is the correct behaviour rather than an oversight.
 */
private fun openReleaseNotes(context: Context, tag: String?) {
    val url = if (tag.isNullOrBlank()) RELEASES_PAGE_URL else "$RELEASES_PAGE_URL/tag/$tag"
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        // Only needed when we're not launching from an Activity; adding it unconditionally would
        // push the browser into its own task even when a normal activity launch is available.
        if (context.findActivity() == null) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
        .onFailure {
            Log.w("SimplifiedLauncher", "could not open release page $url", it)
            MToast(stringRes(R.string.updates_release_notes_failed))
        }
}

/**
 * Run the Alpha3 mod-order import and report the outcome.
 *
 * Both halves are existing code: [AlphaMigration.planModOrderImport] decides what is importable
 * (it reads Alpha3's cfg through the stock `readModValues`), and the write goes through
 * [ModAssistantViewModel.writeModValuesToFile] with `targetCategory = "content"` — the same call
 * the drag-reorder handle makes, which replaces only the content section and leaves `data=`
 * (and groundcover) untouched. This function only sequences them and phrases the result.
 */
private suspend fun importAlpha3ModOrder(viewModel: ModAssistantViewModel) {
    val plan = withContext(Dispatchers.IO) { AlphaMigration.planModOrderImport() }

    plan.refusal?.let { refusal ->
        MToast(
            when (refusal) {
                AlphaMigration.ModOrderPlan.Refusal.NO_SOURCE ->
                    stringRes(R.string.simplified_mod_order_no_source)
                AlphaMigration.ModOrderPlan.Refusal.EMPTY_SOURCE ->
                    stringRes(R.string.simplified_mod_order_empty_source)
                // Refuse wholesale rather than write a list that can't launch.
                AlphaMigration.ModOrderPlan.Refusal.WOULD_LOSE_MORROWIND ->
                    stringRes(R.string.simplified_mod_order_no_morrowind)
            }
        )
        return
    }

    viewModel.writeModValuesToFile(
        modValues = plan.entries,
        filePath = Constants.USER_OPENMW_CFG,
        targetCategory = "content",
        onFinish = { isSuccess ->
            if (!isSuccess) {
                MToast(stringRes(R.string.failed_to_save_openmw_config))
            } else {
                MToast(
                    buildString {
                        append(
                            String.format(
                                stringRes(R.string.simplified_mod_order_imported),
                                plan.entries.size
                            )
                        )
                        if (plan.skipped.isNotEmpty()) {
                            append(
                                String.format(
                                    stringRes(R.string.simplified_mod_order_skipped),
                                    plan.skipped.size
                                )
                            )
                        }
                    }
                )
            }
        }
    )
}

/**
 * Root of the simplified launcher — the alternative to the Alpha3 launcher (`MainScreen`), chosen
 * by [GameFilesPreferences.loadSimplifiedLauncher] and swapped in by `MainActivity`.
 *
 * PHASE 1 (scaffolding only): this is a placeholder home screen plus a minimal settings screen
 * carrying the launcher toggle, so switching between the two launchers can be exercised before any
 * of the real layout is built. The home screen's actual content (mod list, file buttons, Play bar)
 * is Phase 2; the full settings screen (Settings.cfg headings, search) is Phase 3.
 *
 * Deliberately self-contained: it owns its own two-screen navigation rather than joining the
 * Alpha3 `RootNav`/`MainPageNav` graph, so nothing here can perturb the existing launcher.
 */
@Composable
fun SimplifiedLauncherRoot() {
    var showSettings by rememberSaveable { mutableStateOf(false) }

    BackHandler(enabled = showSettings) { showSettings = false }

    // Scoped colour override rather than edits to shared code. The Settings.cfg editor's switches
    // (`IniSettingItem`: checkedThumb = colorScheme.primary, checkedTrack = primaryContainer), its
    // section titles and its focused field borders all inherit the theme primary, which is the blue
    // `primaryDark`. Those live in IniAssistant.kt, shared with the Alpha3 settings page — so
    // instead of recolouring them there (which would recolour Alpha3 too), this re-themes only what
    // is composed inside the simplified launcher. Any Material component drawn here picks up the
    // bronze automatically, including ones added later.
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = MwBronzeLight,          // switch thumb, section titles, focused borders
            onPrimary = MwStoneDark,          // content on a bronze fill
            primaryContainer = MwBronzeDark,  // switch track when checked
            outline = MwBronzeDark,           // unchecked switch border
            surfaceVariant = MwSlotBg,        // unchecked switch track
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // The SAME animated background the Alpha3 launcher draws (RootNavHost renders it behind
            // MainScreen). It honours the user's existing `background_animation` preference —
            // bouncing map (default) / rotating / circular / static — so both launchers look the
            // same and the Graphics settings row keeps working for this one too.
            //
            // NOT wrapped in Alpha3's `AnimatedVisibility(visible = !processing)` gate: that hides
            // the background while the mod DOWNLOADER is working, and this launcher has no
            // downloader UI.
            BackgroundAnimation()

            if (showSettings) {
                SimplifiedSettingsScreen(onBack = { showSettings = false })
            } else {
                SimplifiedLauncherHome(onOpenSettings = { showSettings = true })
            }
        }
    }
}

/**
 * The simplified launcher's home screen. Landscape (top screen, 16:9): a row of setup actions,
 * the mod load-order list filling the middle, and a full-width Play button pinned at the bottom.
 *
 * Every action here is wired to the EXISTING flow the Alpha3 launcher uses — this composable
 * contributes presentation only:
 *  - Select game files -> [MainPageViewModel.selectMorrowWindFolder] / [MainPageViewModel.onGameFolderSelected]
 *  - Add mods         -> [ModAssistantViewModel.modPathSelection]
 *  - Load-order writes -> [ModAssistantViewModel.writeModValuesToFile]
 *  - Play             -> [startGame]
 *
 * The view models are obtained with plain `hiltViewModel()` rather than the Alpha3 graph's
 * `LocalModAssistantViewModel`, the same way `AlphaMigrationFirstLaunch` reaches them from outside
 * that graph. That yields a separate VM instance from the Alpha3 tabs', which is harmless here:
 * all persistent state lives in openmw.cfg on disk, and the two launchers are never composed at
 * the same time.
 */
@Composable
private fun SimplifiedLauncherHome(onOpenSettings: () -> Unit) {
    val context = LocalContext.current
    val mainVm: MainPageViewModel = hiltViewModel()
    val modVm: ModAssistantViewModel = hiltViewModel()

    var showGameFilesBrowser by mainVm.showFileBrowser
    var showModsBrowser by modVm.showFileBrowser
    // Bumped whenever something rewrites openmw.cfg, to re-read the load-order list.
    var refreshKey by remember { mutableStateOf(0) }

    // These three flows MUST be remembered, not rebuilt inline each composition. Each accessor
    // (`getGameFilesUriState` etc.) returns a NEW Flow object every call, and `collectAsState` keys
    // its internal LaunchedEffect on the flow instance — so an inline call cancels and restarts the
    // DataStore subscription on EVERY recomposition of this screen. That churn is what left the Play
    // button greyed after game files were selected: the value only reappeared once some unrelated
    // recomposition (e.g. opening the Add Mods browser) happened to restart the collection and
    // re-read the store. A remembered flow gives one stable, uninterrupted subscription.
    val gameFilesFlow = remember(context) { GameFilesPreferences.getGameFilesUriState(context) }
    val codeGroupFlow = remember(context) { readCodeGroup(context) }
    val bypassFlow = remember(context) { GameFilesPreferences.loadBypassGameCheck(context) }

    val savedPath by gameFilesFlow.collectAsState(initial = null)
    val gameFilesMissing = savedPath.isNullOrEmpty() || savedPath == "Game Files: "

    // Play state, read exactly as the Alpha3 Play FAB reads it and handed to the shared
    // attemptLaunchGame() preamble — see that function for why Play must not call startGame() bare.
    val codeGroupOption by codeGroupFlow.collectAsState(initial = "OpenMW")
    val bypassGameCheck by bypassFlow.collectAsState(initial = false)
    var isCopyingResources by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Update notification state. `offeredUpdate()` is non-null for Available/Downloading/Ready.
    val updateState by UpdateChecker.state.collectAsState()
    val offeredUpdate = updateState.offeredUpdate()
    // Remembered for the same reason as the three flows above — an inline accessor call returns a
    // new Flow each composition and would restart the DataStore subscription every time.
    val dismissedFlow = remember(context) { GameFilesPreferences.loadDismissedUpdateBanner(context) }
    // `initial = null` is a deliberate "not loaded yet" sentinel distinct from the flow's own ""
    // ("nothing dismissed"). Gating the banner on non-null means a previously-dismissed version
    // never flashes the banner for one frame before the stored value arrives.
    val dismissedVersion by dismissedFlow.collectAsState(initial = null)
    val showUpdateBanner = offeredUpdate != null &&
        dismissedVersion != null &&
        dismissedVersion != offeredUpdate.version

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        // Screen heading, styled to match the companion UI's own "OpenMW-DS" title:
        // CompanionScreen's `MwDisplay` font role + Bold. MwDisplay resolves to FontFamily.Serif
        // and is `private` to CompanionScreen.kt (as is its BronzeLight palette), so this mirrors
        // the value rather than importing it — the companion's typography is deliberately not
        // modified to widen visibility. Colour is MwBronzeLight — the same bronze the companion
        // uses for that title (see the mirrored palette in ui/theme/Color.kt).
        Text(
            text = stringResource(R.string.simplified_launcher_title),
            color = MwBronzeLight,
            fontSize = 28.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 10.dp)
        )

        // 1. Setup actions. Settings is deliberately a small square next to the two wide buttons.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LauncherActionButton(
                modifier = Modifier.weight(1f),
                text = if (gameFilesMissing) {
                    stringResource(R.string.select_games_files)
                } else {
                    "${stringResource(R.string.game_files)}$savedPath"
                },
                flashing = gameFilesMissing,
                onClick = {
                    customCFG = true
                    mainVm.selectMorrowWindFolder(context)
                }
            )
            LauncherActionButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.select_data_files),
                onClick = {
                    showModsBrowser = true
                    MToast(stringRes(R.string.add_mod))
                }
            )
            // Settings, with an update dot overlaid. The dot tracks update availability DIRECTLY
            // and is deliberately not dismissible — it's the quiet always-on signal, so the
            // banner's dismiss state must not silence it. It clears itself once a check comes
            // back up to date, because offeredUpdate() is then null.
            Box {
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(44.dp)
                        .background(MwSlotBg, RoundedCornerShape(12.dp))
                        .border(1.dp, MwBronzeDark, RoundedCornerShape(12.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        // The dot itself is a decorative Box, so the badge is announced here
                        // instead — otherwise it would be invisible to a screen reader.
                        contentDescription = if (offeredUpdate != null) {
                            stringResource(R.string.setting) + " — " +
                                stringResource(R.string.updates_badge_desc)
                        } else {
                            stringResource(R.string.setting)
                        },
                        tint = MwBronzeLight
                    )
                }
                if (offeredUpdate != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .size(10.dp)
                            // Dark ring so the dot stays legible over the icon's own strokes.
                            .background(MwStoneDark, CircleShape)
                            .padding(1.5.dp)
                            .background(UpdateAccent, CircleShape)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        // 1b. Update banner, directly above the load-order panel and matched to its width so the
        //     two read as one centred column. Dismiss is remembered PER VERSION, so a later
        //     release brings it back. Not weighted — it sizes to its content and simply takes a
        //     little height from the panel below.
        if (showUpdateBanner && offeredUpdate != null) {
            UpdateBanner(
                version = offeredUpdate.version,
                onDismiss = {
                    coroutineScope.launch {
                        GameFilesPreferences.saveDismissedUpdateBanner(
                            context, offeredUpdate.version
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(MOD_PANEL_WIDTH_FRACTION)
                    .align(Alignment.CenterHorizontally)
            )
            Spacer(Modifier.height(8.dp))
        }

        // 2. Mod load order. weight(1f) bounds it so the LIST scrolls inside the panel and the
        //    screen itself never scrolls — a scrolling screen would fight the drag-reorder gesture.
        ModLoadOrderPanel(
            viewModel = modVm,
            refreshKey = refreshKey,
            savedPath = savedPath,
            gameFilesMissing = gameFilesMissing,
            // Deliberately narrower than the full screen and centred, so it reads as a card rather
            // than as content that failed to fill the width. The top row and Play stay full width.
            modifier = Modifier
                .fillMaxWidth(MOD_PANEL_WIDTH_FRACTION)
                .align(Alignment.CenterHorizontally)
                .weight(1f)
        )

        Spacer(Modifier.height(10.dp))

        // 3. Play. Runs the SHARED preamble the Alpha3 Play FAB runs (game-files guard +
        //    resourcePrepare), not a bare startGame() — see attemptLaunchGame.
        //
        // Disabled while game files are missing. On this sparse screen an enabled Play button that
        // silently does nothing (attemptLaunchGame's null-savedPath no-op) reads as broken; Alpha3
        // keeps that silent no-op because its Data tab and mod list already signal an unconfigured
        // setup. NOT disabled when bypassGameCheck is on — that opt-out deliberately launches
        // without game files, so greying the button there would break it.
        val playEnabled = !isCopyingResources && (!gameFilesMissing || bypassGameCheck)
        val playContentAlpha = if (playEnabled) 1f else 0.5f

        // Play holds focus by default so the controller can start the game without touching the
        // screen.
        //
        // The request MUST wait for the button to actually be placed: FocusRequester.requestFocus()
        // throws "FocusRequester is not initialized" if its node hasn't been laid out yet, and a
        // LaunchedEffect keyed only on state runs before that first layout. Keying off onPlaced is
        // what makes this deterministic — an earlier version guarded the call with runCatching and
        // silently never focused anything.
        //
        // Also gated on playEnabled, because a disabled Button is not focusable at all: on a cold
        // start `savedPath` arrives asynchronously from the DataStore, so Play is briefly disabled
        // and a request made in that window would be dropped.
        val playFocus = remember { FocusRequester() }
        var playFocused by remember { mutableStateOf(false) }
        var playPlaced by remember { mutableStateOf(false) }
        // Requesting on placement alone is NOT enough, even though it reports success: when the
        // Android window subsequently gains focus, Compose hands initial focus to the first
        // focusable in layout order — the top-left "Select game files" button — silently
        // overriding us. So wait for the window to actually be focused and request after that.
        // Re-requesting on each window-focus gain is deliberate: it also re-selects Play when
        // returning from the game or the settings screen.
        //
        // TOUCH MODE is the reason the two earlier attempts at this failed, and it is why the
        // input-mode request below is load-bearing rather than defensive:
        //  - The app is opened by TAPPING its icon, so the window starts in Android touch mode.
        //  - Material3's Button is built on Modifier.clickable, which registers its focus target
        //    as `Focusability.SystemDefined` — defined as `canFocus = inputMode != InputMode.Touch`
        //    (Focusability.kt). In touch mode the button is therefore not focusable AT ALL, and
        //    `requestFocus()` is refused no matter how correctly it is timed.
        //  - The refusal is SILENT: requestFocus() reports it in its RETURN VALUE, so the previous
        //    `runCatching { … }.isSuccess` logged ok=true (only an exception would have made it
        //    false) and the feature looked wired up while nothing was ever focused.
        //  - The observed symptom followed from Android, not from us: with nothing focused, the
        //    first confirm press was swallowed by ViewRootImpl leaving touch mode and assigning
        //    default focus (checkForLeavingTouchModeAndConsume -> restoreDefaultFocus), which
        //    landed on "Select game files"; the second press then activated THAT button. Only once
        //    its dialog had been dismissed — by which point the system was out of touch mode, so
        //    Play was focusable — did the window-focus request below finally take, which is why
        //    Play appeared selected on the third press.
        // Requesting InputMode.Keyboard leaves touch mode (AndroidComposeView maps it to
        // View.requestFocusFromTouch(), which calls ViewRootImpl.ensureTouchMode(false)), so the
        // Button becomes focusable and the FIRST press activates Play. It must come BEFORE the
        // focus request: leaving touch mode itself assigns default focus to the first focusable,
        // and we want the last word.
        val windowInfo = LocalWindowInfo.current
        val inputModeManager = LocalInputModeManager.current
        LaunchedEffect(playPlaced, playEnabled, windowInfo) {
            if (!playPlaced || !playEnabled) return@LaunchedEffect
            snapshotFlow { windowInfo.isWindowFocused }.collect { hasWindowFocus ->
                if (!hasWindowFocus) return@collect
                // Bounded retry, because the request can legitimately lose a race with the
                // window/layout settling on a cold start. It stops as soon as focus is granted, so
                // the normal path is a single pass. runCatching still guards the "FocusRequester is
                // not initialized" throw (the node can go away between placement and this call) —
                // but the GRANTED result is now read from the return value, not inferred from the
                // absence of an exception.
                var attempt = 0
                var granted = false
                while (!granted && attempt < PLAY_FOCUS_ATTEMPTS) {
                    val keyboardMode = inputModeManager.requestInputMode(InputMode.Keyboard)
                    granted = runCatching { playFocus.requestFocus() }.getOrDefault(false)
                    attempt++
                    if (!granted) {
                        Log.d(
                            "SimplifiedLauncher",
                            "Play focus refused (attempt $attempt, keyboardMode=$keyboardMode, " +
                                "inputMode=${inputModeManager.inputMode}); retrying"
                        )
                        delay(PLAY_FOCUS_RETRY_MS)
                    }
                }
                Log.d(
                    "SimplifiedLauncher",
                    "Play focus granted=$granted after $attempt attempt(s), " +
                        "inputMode=${inputModeManager.inputMode}"
                )
            }
        }

        Button(
            onClick = {
                coroutineScope.launch {
                    attemptLaunchGame(
                        context = context,
                        savedPath = savedPath,
                        codeGroupOption = codeGroupOption,
                        bypassGameCheck = bypassGameCheck,
                        onCopyingChanged = { isCopyingResources = it },
                    )
                }
            },
            enabled = playEnabled,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                // onPlaced must come before focusRequester/the focus target so we only request
                // focus once this node genuinely exists in the layout.
                .onPlaced { playPlaced = true }
                .focusRequester(playFocus)
                .onFocusChanged { playFocused = it.isFocused },
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MwSlotWorn,
                // Explicit so the button reads as visibly greyed-out rather than merely inert —
                // the Material default would leave our hardcoded content sitting on a washed-out
                // surface.
                disabledContainerColor = MwSlotBg,
            ),
            // Focus has to be VISIBLE or a pre-selected button is indistinguishable from an idle
            // one — the player would have no way to know A does anything.
            border = BorderStroke(
                if (playFocused && playEnabled) 3.dp else 2.dp,
                when {
                    !playEnabled -> MwBronzeDark
                    playFocused -> MwBoneBright
                    else -> MwBronzeLight
                }
            )
        ) {
            if (isCopyingResources) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MwBronzeLight,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MwBronzeLight.copy(alpha = playContentAlpha)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.start_game),
                    color = MwBoneBright.copy(alpha = playContentAlpha),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    // --- File pickers, hosted here so this screen is self-contained (same shape as the Alpha3
    // --- call sites and AlphaMigrationFirstLaunch: reuse the flow, host our own browser). ---
    BackHandler(showGameFilesBrowser) { showGameFilesBrowser = false }
    if (showGameFilesBrowser) {
        FileBrowserPopup(
            onDismiss = { showGameFilesBrowser = false },
            onFolderSelected = { folder ->
                mainVm.onGameFolderSelected(folder, context)
                refreshKey++
            },
            mode = FileBrowserMode.FOLDER
        )
    }

    BackHandler(showModsBrowser) { showModsBrowser = false }
    if (showModsBrowser) {
        FileBrowserPopup(
            onDismiss = { showModsBrowser = false },
            onFolderSelected = { folder ->
                modVm.modPathSelection(context, folder) { modPath ->
                    Log.d("Add_Data", "Add_Data: $modPath")
                    refreshKey++
                }
                showModsBrowser = false
            },
            mode = FileBrowserMode.FOLDER
        )
    }
}

/**
 * Home-screen "an update is available" strip, with an inline tappable "Dismiss".
 *
 * Dismiss is plain text rather than a Button widget on purpose: this is a passive notice, and a
 * second real button next to Play/Settings would read as another primary action. Only the word
 * itself is clickable — tapping the rest of the strip does nothing, so a stray tap while reaching
 * for the load-order list below can't silently dismiss the notice.
 */
@Composable
private fun UpdateBanner(
    version: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(MwFloatStone, RoundedCornerShape(10.dp))
            .border(1.dp, UpdateAccent, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Same dot as the Settings badge, tying the two signals together visually.
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(UpdateAccent, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.updates_banner, version),
            color = MwBone,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.updates_banner_dismiss),
            color = MwBronzeLight,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            textDecoration = TextDecoration.Underline,
            // Padding inside the clickable so the tap target is comfortably bigger than the glyphs.
            modifier = Modifier
                .clickable { onDismiss() }
                .padding(horizontal = 6.dp, vertical = 3.dp)
        )
    }
}

/** Setup-action button for the top row. Same palette as [org.openmw.ui.view.SetupButton], but
 *  width-flexible so three items can share a row (SetupButton hardcodes `fillMaxWidth(0.7f)`). */
@Composable
private fun LauncherActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    flashing: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "launcherBtn")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 500), RepeatMode.Reverse),
        label = "pulse",
    )
    Button(
        onClick = onClick,
        modifier = modifier
            .heightIn(min = 44.dp)
            .then(if (flashing) Modifier.graphicsLayer(scaleX = pulse, scaleY = pulse) else Modifier),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MwSlotBg),
        border = BorderStroke(1.dp, if (flashing) MwBronzeLight else MwBronzeDark),
    ) {
        Text(
            text = text,
            color = if (flashing) MwBronzeLight else MwBone,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * The mod load-order panel: the Content (ESM/ESP) entries from openmw.cfg, drag-reorderable by the
 * handle on each row.
 *
 * Reuses the Alpha3 load-order LOGIC wholesale — [readModValues] to load, and
 * [ModAssistantViewModel.writeModValuesToFile] with `targetCategory = "content"` to persist, which
 * is the same call the Alpha3 Content tab makes on drop. Both launchers therefore read and write
 * the same openmw.cfg section in the same format.
 *
 * The drag GLUE (the `sh.calvin.reorderable` state + handle) is written here rather than shared,
 * because in `ModValuesList` it is inline inside a ~300-line item lambda that also carries the tab
 * bar, search/nav/terminal panels and several dialogs; extracting it would mean substantially
 * rewriting the Alpha3 Content tab, which this phase is not permitted to touch. Only the ~10 lines
 * of library wiring are re-expressed — no reorder or persistence logic is duplicated.
 *
 * Unlike the Alpha3 list this one is fixed to the Content category (no tab index), which is what
 * removes the coupling to the Alpha3 tab layout.
 */
@Composable
private fun ModLoadOrderPanel(
    viewModel: ModAssistantViewModel,
    refreshKey: Int,
    savedPath: String?,
    gameFilesMissing: Boolean,
    modifier: Modifier = Modifier,
) {
    val contentKey = OpenMWConfigUtils.ConfigKeyType.Content.key
    val coroutineScope = rememberCoroutineScope()
    val view = LocalView.current

    var items by remember { mutableStateOf(emptyList<ModValue>()) }

    // Re-read from openmw.cfg only when something could have changed it — NOT on every
    // recomposition (Alpha3's ModValuesList does that, which would discard an in-progress drag).
    //
    // But neither trigger means the cfg is READY: `processSelectedFolder` writes the content=/data=
    // lines on its own fire-and-forget `scope.launch`, so it can land AFTER this effect runs. On the
    // "Yes" (auto-detected folder) path that is deterministic — `storeGameFilesPath` is awaited, so
    // `savedPath` always emits before the write — which showed an empty list that never recovered.
    // The "No" path only appeared to work because it happens to fire two triggers.
    //
    // So: read immediately, then WATCH the cfg file for a bounded window and re-read whenever it
    // actually changes. Watching the file (not just retrying while empty) also covers switching from
    // one valid folder to another, where the stale read is non-empty and a retry-on-empty would
    // never fire. Bounded and self-limiting, in the spirit of the icon pipeline's bounded retry.
    LaunchedEffect(refreshKey, savedPath) {
        val cfg = File(Constants.USER_OPENMW_CFG)
        fun cfgStamp() = cfg.lastModified() to cfg.length()

        var stamp = cfgStamp()
        items = readModValues().filter { it.category == contentKey }

        repeat(CFG_SETTLE_ATTEMPTS) {
            delay(CFG_SETTLE_DELAY_MS)
            val current = cfgStamp()
            if (current != stamp) {
                stamp = current
                items = readModValues().filter { it.category == contentKey }
            }
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    Column(
        modifier = modifier
            .background(MwFloatStone, RoundedCornerShape(12.dp))
            // A defined edge so the narrower width reads as a deliberate card rather than as
            // content that just failed to fill the screen. Bronze frame = the companion's mwPanel.
            .border(2.dp, MwBronze, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Text(
            // Own string, NOT the shared R.string.content — that one is ConfigKeyType.Content.tag
            // and the Alpha3 tab label, so renaming it would rename those too.
            text = stringResource(R.string.simplified_load_order),
            color = MwBronzeLight,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp, bottom = 6.dp)
        )

        if (items.isEmpty()) {
            // Two DIFFERENT empty states. Showing "Morrowind folder not found" for both is what
            // made the refresh race above look like a path bug: game files were set correctly and
            // the list was merely empty, but the panel claimed the folder was missing.
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (gameFilesMissing) {
                        stringResource(R.string.morrowind_folder_not_found_tip)
                    } else {
                        stringResource(R.string.simplified_no_content_files)
                    },
                    color = MwBoneDim,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
            }
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = lazyListState,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(items, key = { _, item -> item.stableId }) { index, modValue ->
                ReorderableItem(reorderableState, key = modValue.stableId) { isDragging ->
                    val background by animateColorAsState(
                        if (isDragging) MwSlotWorn else MwSlotBg,
                        label = "rowBg"
                    )
                    Card(colors = CardDefaults.cardColors(containerColor = background)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${index + 1}",
                                color = MwBronzeLight,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(start = 10.dp, end = 6.dp)
                            )
                            Text(
                                text = modValue.value,
                                color = MwBone,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(vertical = 8.dp)
                            )
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
                                        // Renumber, then hand off to the SAME writer the Alpha3
                                        // Content tab uses on drop.
                                        items = items.mapIndexed { i, item -> item.copy(id = i + 1) }
                                        coroutineScope.launch {
                                            viewModel.writeModValuesToFile(
                                                modValues = items,
                                                filePath = Constants.USER_OPENMW_CFG,
                                                targetCategory = contentKey,
                                                onFinish = { isSuccess ->
                                                    if (!isSuccess) {
                                                        MToast(stringRes(R.string.failed_to_save_openmw_config))
                                                    }
                                                }
                                            )
                                        }
                                    }
                                ),
                                onClick = {}
                            ) {
                                Icon(
                                    Icons.Rounded.Menu,
                                    contentDescription = "Reorder",
                                    tint = MwBoneDim
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The simplified launcher's settings screen: a pinned back arrow + search field above one
 * scrolling list of the Settings.cfg categories, the launcher toggle, and Reset settings.
 *
 * The Settings.cfg half is entirely [IniSettings] — the same composable the Alpha3 settings page
 * hosts — so parsing, the collapsible section cards, the per-option editors, the search filter and
 * the write-back are shared, not reimplemented. The only thing this screen owns is the search
 * FIELD, handed to `IniSettings` via `externalSearchQuery` so it can be pinned above the scroll
 * area instead of scrolling away with the categories.
 */
@Composable
private fun SimplifiedSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // The SAME preference the Alpha3 launcher's Settings -> Launcher Settings row reads/writes.
    // Remembered for the same reason as the home screen's flows — an inline accessor call would
    // restart the DataStore subscription on every recomposition.
    val simplifiedLauncherFlow =
        remember(context) { GameFilesPreferences.loadSimplifiedLauncher(context) }
    val simplifiedLauncher by simplifiedLauncherFlow.collectAsState(initial = true)
    var showResetDialog by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var confirmModOrder by rememberSaveable { mutableStateOf(false) }
    // Bumped whenever something OUTSIDE the settings list rewrites settings.cfg (Copy settings from
    // Alpha3, Reset settings). IniSettings snapshots that file at first composition and only
    // re-reads after one of its own edits, so an external write is invisible to it; re-keying the
    // composable discards its remembered snapshot and forces a fresh readIniValues().
    var settingsRefreshKey by rememberSaveable { mutableStateOf(0) }
    // Needed only for writeModValuesToFile — the same writer the drag-reorder UI uses.
    val modVm: ModAssistantViewModel = hiltViewModel()

    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Back arrow + search, PINNED above the scroll area.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.back),
                    tint = MwBronzeLight
                )
            }
            Spacer(Modifier.width(4.dp))
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Search settings...", color = MwBoneDim) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = MwBoneDim)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = MwBoneBright,
                    unfocusedTextColor = MwBone,
                    focusedBorderColor = MwBronzeLight,
                    unfocusedBorderColor = MwBronzeDark,
                    cursorColor = MwBronzeLight,
                )
            )
        }

        // Breathing room between the pinned search row and the scrolling content below it.
        Spacer(Modifier.height(12.dp))

        // 2. One scrolling list: the 20 Settings.cfg categories, then the launcher toggle, then
        //    Reset settings. No drag interaction here (unlike the mod panel), so a single
        //    verticalScroll is correct — and IniSettings is a plain Column, not a lazy list.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            // Alpha3 transfer. Placed ABOVE the Settings.cfg cards, with its own header, because
            // it is a transient first-run affordance — AlphaMigrationButtons returns early (and
            // this whole block disappears) once /Alpha3/ is gone, so it never permanently competes
            // with the settings content, and a migrating user shouldn't have to scroll past 20
            // category cards to find it.
            //
            // The buttons themselves are the SAME composable the Alpha3 home screen uses: it owns
            // the oldFolderExists() gate, the confirm dialogs, the copySaves()/copySettings() calls
            // and the result toasts. Nothing about the copy behaviour is re-expressed here.
            if (AlphaMigration.oldFolderExists()) {
                // Centred bordered card, matching the home screen's "Content" mod-list panel, so
                // the three migration actions read as one deliberate group rather than as loose
                // buttons above the Settings.cfg cards.
                Column(
                    modifier = Modifier
                        .fillMaxWidth(TRANSFER_SECTION_WIDTH_FRACTION)
                        .align(Alignment.CenterHorizontally)
                        .background(MwFloatStone, RoundedCornerShape(12.dp))
                        .border(2.dp, MwBronze, RoundedCornerShape(12.dp))
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = stringResource(R.string.simplified_transfer_from_alpha3),
                        color = MwBronzeLight,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Serif,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    AlphaMigrationButtons(onSettingsCopied = { settingsRefreshKey++ })
                    // Third migration action, alongside the two AlphaMigrationButtons renders.
                    // Same SetupButton styling so the group reads as one set.
                    SetupButton(
                        text = stringResource(R.string.simplified_copy_mod_order),
                        onClick = { confirmModOrder = true },
                    )
                }
                Spacer(Modifier.height(14.dp))
            }

            // In-app updater. Sits ABOVE the Settings.cfg cards (and below the transient Alpha3
            // transfer block) so it's reachable without scrolling past 20 category cards — it's
            // an occasional, deliberate action, not something buried in the settings tree.
            UpdatesCard()
            Spacer(Modifier.height(14.dp))

            // Settings.cfg editor in its own bordered card, same family as the Transfer card above
            // but wider. Deliberately NO fixed height and no weight(): the Column wraps its content,
            // so the box grows and shrinks as the section drop-downs expand, and the surrounding
            // verticalScroll absorbs the extra height.
            Column(
                modifier = Modifier
                    .fillMaxWidth(SETTINGS_SECTION_WIDTH_FRACTION)
                    .align(Alignment.CenterHorizontally)
                    .background(MwFloatStone, RoundedCornerShape(12.dp))
                    .border(2.dp, MwBronze, RoundedCornerShape(12.dp))
                    .padding(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = stringResource(R.string.openmw_settings),
                    color = MwBronzeLight,
                    fontSize = 16.sp,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                // The ENTIRE Settings.cfg editor, reused as-is from the Alpha3 settings page:
                // parsing, the collapsible section cards, the option editors and the write-back are
                // all IniSettings'. Only the search field is ours, handed in so it can be pinned
                // above. Re-keyed so an external rewrite of settings.cfg (Copy settings / Reset
                // settings) discards its one-shot snapshot and re-reads the file.
                key(settingsRefreshKey) {
                    IniSettings(externalSearchQuery = searchQuery)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MwBronzeDark
            )

            // Launcher toggle — moved down here (Phase 1 had it up near the top) so it sits
            // directly above Reset settings.
            SettingRow(
                title = stringResource(R.string.use_simplified_launcher),
                subtitle = stringResource(R.string.use_simplified_launcher_tip)
            ) {
                Switch(
                    checked = simplifiedLauncher,
                    onCheckedChange = {
                        scope.launch { GameFilesPreferences.saveSimplifiedLauncher(context, it) }
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MwBronzeDark
            )

            SettingRow(title = stringResource(R.string.reset_settings)) {
                OutlinedButton(onClick = { showResetDialog = true }) {
                    Text(stringResource(R.string.reset_settings))
                }
            }
        }
    }

    if (confirmModOrder) {
        AlertDialog(
            onDismissRequest = { confirmModOrder = false },
            title = { Text(stringResource(R.string.simplified_copy_mod_order)) },
            text = { Text(stringResource(R.string.simplified_copy_mod_order_tip)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmModOrder = false
                        scope.launch { importAlpha3ModOrder(modVm) }
                    }
                ) { Text(stringResource(R.string.btn_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmModOrder = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { showResetDialog = false },
            title = { Text(stringResource(R.string.confirm_reset)) },
            text = { Text(stringResource(R.string.reset_the_settings_tip)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        // Exactly the action behind the Alpha3 launcher's "Reset Settings" menu
                        // item — shared, not reimplemented.
                        resetUserSettingsFile(context)
                        // Also rewrites settings.cfg, so the list below must re-read it.
                        settingsRefreshKey++
                        showResetDialog = false
                    }
                ) {
                    Text(stringResource(R.string.btn_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

/**
 * "Updates" card — check GitHub for a newer release and download it.
 *
 * Phase 1: there is deliberately NO working install action. Handing the downloaded APK to the
 * package installer needs a `FileProvider`, `REQUEST_INSTALL_PACKAGES` and the per-app
 * unknown-sources grant, none of which exist yet, so the Install button is present but disabled
 * with an explanatory note rather than faked.
 *
 * All state lives in [UpdateChecker], not here, so the result of the automatic on-launch check is
 * already showing when this screen opens instead of being re-fetched.
 */
@Composable
private fun ColumnScope.UpdatesCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by UpdateChecker.state.collectAsState()
    // Held so the Cancel button can abort an in-flight download. UpdateChecker treats the
    // resulting CancellationException as a user action and restores the "Available" offer.
    var downloadJob by remember { mutableStateOf<Job?>(null) }

    // --- Install (Phase 2) -------------------------------------------------------------------
    // Deliberately NO "installing…" state anywhere below. Once the system installer is showing,
    // this app has no reliable completion event (it may be killed and replaced mid-install), and
    // backing out of the prompt notifies us of nothing — so any spinner we set could never be
    // cleared. Leaving the button plainly tappable is both simpler and impossible to get stuck.

    /** Hand the currently-downloaded APK to the installer, reporting anything that blocks it. */
    fun runInstall() {
        val ready = UpdateChecker.state.value as? UpdateState.Ready ?: return
        when (val result = ApkInstaller.install(context, ready.file)) {
            // The installer owns the screen from here; nothing more for us to do.
            is InstallResult.Launched -> Unit
            // cacheDir is OS-evictable, so the file can genuinely vanish between download and
            // tap. Reset to Idle so the card offers a fresh check rather than a dead button.
            is InstallResult.FileMissing -> {
                MToast(stringRes(R.string.updates_install_file_missing))
                UpdateChecker.dismiss()
            }
            is InstallResult.Failed ->
                MToast(context.getString(R.string.updates_install_failed, result.reason))
        }
    }

    // ACTION_MANAGE_UNKNOWN_APP_SOURCES reports no meaningful result code — the user can flip the
    // switch and press back, or just press back — so re-QUERY the permission instead of trusting
    // a RESULT_OK. Uses registerForActivityResult; the codebase's older PermissionAssistant still
    // uses the deprecated startActivityForResult/onActivityResult pair, but it's a standalone
    // helper with no shared plumbing to reuse here.
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (ApkInstaller.canInstall(context)) {
            // Granted while they were away — continue straight into the install they asked for.
            runInstall()
        } else {
            // Declined. One explanatory toast, then nothing: no re-prompt, no loop. The Install
            // button stays exactly where it was and can be tapped again.
            MToast(stringRes(R.string.updates_install_permission_needed))
        }
    }

    fun startInstall() {
        if (ApkInstaller.canInstall(context)) {
            runInstall()
            return
        }
        runCatching { permissionLauncher.launch(ApkInstaller.unknownSourcesSettingsIntent(context)) }
            .onFailure {
                // Some devices/ROMs lack the per-app unknown-sources screen entirely.
                MToast(context.getString(R.string.updates_install_failed, it.message ?: ""))
            }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth(TRANSFER_SECTION_WIDTH_FRACTION)
            .align(Alignment.CenterHorizontally)
            .background(MwFloatStone, RoundedCornerShape(12.dp))
            .border(2.dp, MwBronze, RoundedCornerShape(12.dp))
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = stringResource(R.string.updates_title),
            color = MwBronzeLight,
            fontSize = 16.sp,
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 2.dp)
        )
        Text(
            text = stringResource(R.string.updates_current_version, BuildConfig.RELEASE_VERSION),
            color = MwBoneDim,
            fontSize = 12.sp,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        // Status line — one per state, so the card always says what just happened.
        val status: String? = when (val s = state) {
            is UpdateState.Idle -> null
            is UpdateState.Checking -> stringResource(R.string.updates_checking)
            is UpdateState.UpToDate -> stringResource(R.string.updates_up_to_date)
            is UpdateState.Available -> stringResource(R.string.updates_available, s.info.version)
            is UpdateState.Downloading ->
                if (s.isDeterminate) {
                    stringResource(
                        R.string.updates_downloading,
                        "${UpdateChecker.formatBytes(s.bytesRead)} / " +
                            UpdateChecker.formatBytes(s.totalBytes)
                    )
                } else {
                    stringResource(R.string.updates_downloading_indeterminate)
                }
            is UpdateState.Ready -> stringResource(R.string.updates_ready, s.info.version)
            is UpdateState.Failed -> stringResource(R.string.updates_failed, s.message)
        }
        if (status != null) {
            Text(
                text = status,
                color = if (state is UpdateState.Failed) Color(0xFFC75C5C) else MwBone,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        // Real progress, not a spinner — this is a ~71MB transfer, so an indeterminate bar would
        // leave the user with no idea whether it is 5 seconds or 5 minutes from done.
        (state as? UpdateState.Downloading)?.let { s ->
            if (s.isDeterminate) {
                LinearProgressIndicator(
                    progress = { s.fraction },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    color = MwBronzeLight,
                    trackColor = MwSlotBg
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    color = MwBronzeLight,
                    trackColor = MwSlotBg
                )
            }
        }

        when (val s = state) {
            is UpdateState.Downloading -> {
                SetupButton(
                    text = stringResource(R.string.updates_cancel),
                    onClick = {
                        downloadJob?.cancel()
                        downloadJob = null
                    }
                )
            }

            is UpdateState.Available -> {
                SetupButton(
                    text = if (s.info.sizeBytes > 0) {
                        stringResource(
                            R.string.updates_download,
                            UpdateChecker.formatBytes(s.info.sizeBytes)
                        )
                    } else {
                        stringResource(R.string.updates_download_no_size)
                    },
                    onClick = {
                        downloadJob = scope.launch { UpdateChecker.download(context) }
                    }
                )
            }

            is UpdateState.Ready -> {
                SetupButton(
                    text = stringResource(R.string.updates_install, s.info.version),
                    onClick = { startInstall() }
                )
                Text(
                    text = stringResource(R.string.updates_install_hint),
                    color = MwBoneDim,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            else -> {
                SetupButton(
                    text = stringResource(R.string.updates_check),
                    onClick = { scope.launch { UpdateChecker.check() } }
                )
            }
        }

        // Release notes for the specific release being offered. Shown only when there IS one —
        // there is nothing useful to link to in the up-to-date/idle/failed states. Uses the real
        // tag_name from the API response rather than a /releases/latest redirect, so it always
        // lands on the release we actually named above.
        state.offeredUpdate()?.let { info ->
            Text(
                text = stringResource(R.string.updates_release_notes),
                color = MwBronzeLight,
                fontSize = 12.sp,
                textDecoration = TextDecoration.Underline,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable { openReleaseNotes(context, info.tag) }
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }
    }
}
