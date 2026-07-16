package org.openmw.ui.view

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmw.ui.controls.UIStateManager
import org.openmw.ui.page.main.MainPageViewModel
import org.openmw.ui.page.mod.ModAssistantViewModel
import org.openmw.utils.AlphaMigration
import org.openmw.utils.FileBrowserMode
import org.openmw.utils.FileBrowserPopup
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.MToast

// -------------------------------------------------------------------- copy runners --

/**
 * Run the save copy off the main thread and toast a summary. Shared by the
 * first-launch popup and the home-screen button so there is one implementation.
 */
private fun launchCopySaves(scope: kotlinx.coroutines.CoroutineScope, context: android.content.Context) {
    scope.launch {
        val result = withContext(Dispatchers.IO) { AlphaMigration.copySaves(context) }
        val msg = if (result.filesCopied == 0 && result.filesSkippedNewer == 0) {
            "No saves to copy"
        } else {
            buildString {
                append("${result.filesCopied} saves copied")
                if (result.filesSkippedNewer > 0) append(", ${result.filesSkippedNewer} skipped — already up to date")
                if (result.collisionsRenamed > 0) append(" (${result.collisionsRenamed} kept separate)")
            }
        }
        MToast(msg)
    }
}

private fun launchCopySettings(scope: kotlinx.coroutines.CoroutineScope) {
    scope.launch {
        val result = withContext(Dispatchers.IO) { AlphaMigration.copySettings() }
        val msg = if (result.copied.isEmpty()) {
            "No settings files found to copy"
        } else {
            "Settings copied: ${result.copied.joinToString(", ")}"
        }
        MToast(msg)
    }
}

/** A stored game-files path counts as "selected" once it's a real, non-placeholder path. */
private fun gameFilesSelected(savedPath: String?): Boolean =
    !savedPath.isNullOrBlank() && savedPath != "Game Files: "

// ------------------------------------------------------------------- shared dialogs --

/** Optional (Yes/No) prompt — used by the saves/settings dialogs and the home buttons. */
@Composable
private fun MigrationAlert(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

/**
 * Mandatory action prompt: a message + a single styled action button, NO skip/dismiss.
 * `onDismissRequest` is a no-op so back / tap-outside can't advance past it — the only
 * way forward is completing the embedded action. Used for the Morrowind-folder and
 * Data-Files dialogs.
 */
@Composable
private fun MigrationActionAlert(
    title: String,
    message: String,
    buttonText: String,
    onAction: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(message)
                Spacer12()
                SetupButton(text = buttonText, onClick = onAction)
            }
        },
        confirmButton = {},
    )
}

@Composable
private fun Spacer12() {
    androidx.compose.foundation.layout.Spacer(Modifier.height(12.dp))
}

// -------------------------------------------------------------- first-launch popups --

private enum class MigStep { SAVES, SETTINGS, GAME_FILES, DATA_FILES, DONE }

/**
 * First-launch, one-shot migration sequence for a user coming from the old shared
 * `/Alpha3/` folder. Four dialogs in order:
 *   1. copy saves?      (optional — Yes/No)
 *   2. copy settings?   (optional — Yes/No)
 *   3. select Morrowind folder   (MANDATORY — reuses the Select-Game-Files action)
 *   4. select Data Files folder  (MANDATORY — reuses the Add-Mods folder-picker action)
 *
 * The whole sequence is gated as ONE unit by `migration_prompted`, which is only set once
 * dialog 4 completes — so a genuinely new user (no old folder) sees none of this, and a
 * migrating user isn't re-prompted after finishing.
 *
 * Dialogs 3 & 4 reuse the EXISTING selection logic (`MainPageViewModel.selectMorrowWindFolder`
 * / `onGameFolderSelected` and `ModAssistantViewModel.modPathSelection`). While a folder
 * browser is open, the current action dialog is hidden (`selecting`) so the browser Popup
 * never has to stack over an AlertDialog.
 */
@Composable
fun AlphaMigrationFirstLaunch() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val mainVm: MainPageViewModel = hiltViewModel()
    val modVm: ModAssistantViewModel = hiltViewModel()

    var step by remember {
        mutableStateOf(
            if (AlphaMigration.oldFolderExists() && !AlphaMigration.wasPrompted(context)) {
                MigStep.SAVES
            } else {
                MigStep.DONE
            }
        )
    }
    // True while a folder browser is open — hides the action dialog behind it.
    var selecting by remember { mutableStateOf(false) }
    // Our own data-files browser flag (separate from the home-screen one in ModValuesList).
    var dataBrowser by remember { mutableStateOf(false) }

    val savedPath by GameFilesPreferences.getGameFilesUriState(context).collectAsState(initial = null)
    val gameBrowser by mainVm.showFileBrowser

    // Advance out of GAME_FILES the moment a valid Morrowind folder is stored (whether it
    // was chosen via the auto-detect confirm or the manual browser).
    LaunchedEffect(step, savedPath) {
        if (step == MigStep.GAME_FILES && gameFilesSelected(savedPath)) {
            selecting = false
            step = MigStep.DATA_FILES
        }
    }

    when (step) {
        MigStep.SAVES -> MigrationAlert(
            title = "Alpha3 saves detected",
            message = "Would you like to copy your saves?",
            confirmLabel = "Yes",
            dismissLabel = "No",
            onConfirm = {
                launchCopySaves(scope, context)
                step = MigStep.SETTINGS
            },
            onDismiss = { step = MigStep.SETTINGS },
        )

        MigStep.SETTINGS -> MigrationAlert(
            title = "Alpha3 settings",
            message = "Would you like to copy your Alpha3 settings? (not recommended)",
            confirmLabel = "Yes",
            dismissLabel = "No",
            onConfirm = {
                launchCopySettings(scope)
                step = MigStep.GAME_FILES
            },
            onDismiss = { step = MigStep.GAME_FILES },
        )

        MigStep.GAME_FILES -> {
            // Skip straight through if a Morrowind folder is somehow already set.
            if (!gameFilesSelected(savedPath) && !selecting) {
                MigrationActionAlert(
                    title = "Select your Morrowind folder",
                    message = "Choose the folder that contains Morrowind.ini and the " +
                        "Data Files folder. OpenMW-DS can't run without it.",
                    buttonText = "Select Game Files",
                    onAction = {
                        selecting = true
                        UIStateManager.customCFG = true
                        mainVm.selectMorrowWindFolder(context)
                    },
                )
            }
        }

        MigStep.DATA_FILES -> {
            if (!selecting) {
                MigrationActionAlert(
                    title = "Select your Data Files folder",
                    message = "Now select your Data Files folder so your game data and any " +
                        "mods are registered.",
                    buttonText = "Add Mods (select Data Files)",
                    onAction = {
                        selecting = true
                        dataBrowser = true
                    },
                )
            }
        }

        MigStep.DONE -> Unit
    }

    // ---- hosted folder browsers (shown alone, with the action dialog hidden) ----

    // Game files: the same manual browser the home-screen Select-Game-Files uses.
    if (step == MigStep.GAME_FILES && gameBrowser) {
        FileBrowserPopup(
            onDismiss = {
                mainVm.showFileBrowser.value = false
                // Re-show the (mandatory) dialog if they backed out without a valid pick.
                selecting = false
            },
            onFolderSelected = { folder -> mainVm.onGameFolderSelected(folder, context) },
            mode = FileBrowserMode.FOLDER,
        )
    }

    // Data files: same underlying add-mods-folder logic (modPathSelection).
    if (step == MigStep.DATA_FILES && dataBrowser) {
        FileBrowserPopup(
            onDismiss = {
                // Fired for both "Select folder" and cancel. Only advance on a real pick
                // (handled in onFolderSelected); a plain cancel re-shows the dialog.
                dataBrowser = false
                if (step == MigStep.DATA_FILES) selecting = false
            },
            onFolderSelected = { folder ->
                modVm.modPathSelection(context, folder) { }
                AlphaMigration.markPrompted(context)
                step = MigStep.DONE
            },
            mode = FileBrowserMode.FOLDER,
        )
    }
}

// ------------------------------------------------------------- home-screen buttons --

/**
 * "I changed my mind later" affordances. Visible only while the old `/Alpha3/`
 * folder still exists; work independently of the first-launch prompt flag. Reuse the
 * exact same copy logic as the popups.
 */
@Composable
fun AlphaMigrationButtons() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Re-evaluated on (re)composition; hides itself after the user deletes the folder.
    if (!AlphaMigration.oldFolderExists()) return

    var confirmSaves by remember { mutableStateOf(false) }
    var confirmSettings by remember { mutableStateOf(false) }

    Column {
        SetupButton(
            text = "Copy saves from Alpha3",
            onClick = { confirmSaves = true },
        )
        SetupButton(
            text = "Copy settings from Alpha3",
            onClick = { confirmSettings = true },
        )
    }

    if (confirmSaves) {
        MigrationAlert(
            title = "Copy saves from Alpha3",
            message = "Copy your Alpha3 saves into OpenMW-DS?\n\n" +
                "A character you already have here is kept as a separate \"Name - N\" " +
                "folder, and saves that are newer here are not overwritten.",
            confirmLabel = "Copy",
            dismissLabel = "Cancel",
            onConfirm = {
                launchCopySaves(scope, context)
                confirmSaves = false
            },
            onDismiss = { confirmSaves = false },
        )
    }

    if (confirmSettings) {
        MigrationAlert(
            title = "Copy settings from Alpha3",
            message = "Replace your current settings with Alpha3's? This isn't " +
                "recommended — it brings back Alpha3's old defaults.",
            confirmLabel = "Copy",
            dismissLabel = "Cancel",
            onConfirm = {
                launchCopySettings(scope)
                confirmSettings = false
            },
            onDismiss = { confirmSettings = false },
        )
    }
}
