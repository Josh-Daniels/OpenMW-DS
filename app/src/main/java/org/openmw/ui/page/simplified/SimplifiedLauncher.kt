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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInputModeManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import org.openmw.ui.theme.GAME_FONT_SIZE_SCALE
import org.openmw.ui.theme.loadGameFont
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
import org.openmw.fragments.USER_CFG_DEFAULT_LINE
import org.openmw.ui.controls.UIStateManager.customCFG
import org.openmw.ui.page.main.MainPageViewModel
import org.openmw.ui.page.mod.ModAssistantViewModel
import org.openmw.ui.page.mod.ModValue
import org.openmw.ui.page.mod.defaultEnabledFor
import org.openmw.ui.page.mod.isTamrielData
import org.openmw.ui.page.mod.readModValues
import org.openmw.ui.page.mod.sortedByDefaultLoadOrder
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

/**
 * Reads openmw.cfg immediately, then WATCHES it for [CFG_SETTLE_ATTEMPTS] × [CFG_SETTLE_DELAY_MS]
 * and re-reads whenever it actually changes, handing every read to [onValues].
 *
 * Shared by the mod load-order panel and the Add-Mods button's count, because both face the same
 * race: the writes behind a refresh trigger (`processSelectedFolder`, `modPathSelection`) are
 * fire-and-forget, so "the selection was made" does not mean "the cfg is on disk yet". Watching the
 * FILE rather than retrying-while-empty also covers switching from one valid folder to another,
 * where the stale read is non-empty and a retry-on-empty would never fire.
 */
private suspend fun collectSettledModValues(onValues: (List<ModValue>) -> Unit) {
    val cfg = File(Constants.USER_OPENMW_CFG)
    fun cfgStamp() = cfg.lastModified() to cfg.length()

    var stamp = cfgStamp()
    onValues(readModValues())

    repeat(CFG_SETTLE_ATTEMPTS) {
        delay(CFG_SETTLE_DELAY_MS)
        val current = cfgStamp()
        if (current != stamp) {
            stamp = current
            onValues(readModValues())
        }
    }
}

/** A cfg path as written may be quoted (`modPathSelection` emits `data="<folder>"`) and may carry a
 *  trailing slash. Everything that compares or opens one of these paths goes through this. */
private fun normPath(path: String) = path.trim().trim('"').trimEnd('/')

/**
 * Whether the stored game-files path is a real selection.
 *
 * `null` means the DataStore has not emitted yet — a state EVERY cold open passes through, since
 * `getGameFilesUriState` is an async flow collected with `initial = null`. `""` is written by
 * `MainPageViewModel` when a selection fails validation, and `"Game Files: "` is a legacy sentinel
 * the older screens still test for. None of the three identifies a game folder, and telling them
 * apart from a real path is the whole job here — see [addedModFolders] for why conflating "not loaded
 * yet" with "no game folder" was actively dangerous.
 *
 * One predicate shared by the screen's `gameFilesMissing` and [addedModFolders], so the button's
 * label and the removable-folder list can never disagree about whether the game folder is known.
 */
private fun gameFilesConfigured(savedPath: String?) =
    !savedPath.isNullOrBlank() && savedPath != "Game Files: "

// NOTE: a `baseDataFilesPath(savedPath)` helper used to live here, deriving the displayed Data Files
// path by appending "/Data Files" to the stored game-files path. It was removed once the Add Mods
// label started reading the path out of the cfg instead — the cfg is what the load order and the
// engine actually use, so a derived path could (and did) advertise a folder with no `data=` line.
// Classification still handles the "player selected the Data Files folder itself" case by matching
// BOTH the root and `<root>/Data Files`; see `manageableFolders` and [addedModFolders].

/**
 * The MOD data folders that have been added, i.e. the `data=` entries in the user openmw.cfg that
 * are not the base game's own `Data Files` and not a folder the app manages for itself.
 *
 * There is no "the selected mods folder" to report the way game files have one saved path:
 * [ModAssistantViewModel.modPathSelection] APPENDS a `data="<folder>"` line (plus a `content=` line
 * per plugin it finds) and nothing anywhere records a single most-recent choice, so the cfg is the
 * only state there is — and it is a list, not a slot.
 *
 * Excluded from the count:
 *  - `<game files>/Data Files`, written by the game-files selection itself. It is the base game, not
 *    a mod, and counting it would show "1 added" before the player has added anything.
 *  - the app's own resources/delta/companion paths, which are written by the launcher and the Delta
 *    merge tool rather than chosen by the player.
 * Duplicates collapse, because adding the same folder twice appends nothing the second time.
 *
 * This is ALSO the list of folders offered as removable in [ManageModFoldersDialog], and the
 * exclusions above are what keeps `Data Files` and the app-managed paths off it — remove one of
 * those and the game has no data at all. One function, so the count on the button and the removable
 * list can never disagree about what counts as a player-added folder.
 *
 * COLD-OPEN CORRECTNESS — why the [gameFilesConfigured] guard is not defensive padding:
 * the base game's folder is identified by string-matching `<savedPath>/Data Files`, and `savedPath`
 * arrives from an async DataStore flow that is `null` for the first frames of EVERY cold open. With
 * no guard, `gameDataFiles` was null in that window, so the base game's own `data=` line failed the
 * exclusion and was reported as a player-added mod folder: on a real device cfg the button showed
 * "Add Mods (1 folder added)" with a Manage line before settling to the correct 0, and while it was
 * up the Manage dialog offered `Morrowind/Data Files` itself for removal. Returning an empty list
 * until the path is known makes the unknown state read as "nothing added" — the same, harmless thing
 * the screen shows when there genuinely is nothing — instead of as a removable base game.
 *
 * The game ROOT is excluded alongside `<root>/Data Files` because a player who browses to the
 * `Data Files` folder and selects THAT as their game files (a natural mistake — the Add Mods button
 * literally says "select Data Files") stores a path for which `<savedPath>/Data Files` never matches
 * anything. That case is permanent, not transient, and produced the same wrongly-removable base game.
 */
private fun addedModFolders(values: List<ModValue>, savedPath: String?): List<ModValue> {
    // Not "no game folder" but "we do not know it yet" — and without knowing it, nothing here can
    // tell the base game apart from a mod. Report nothing rather than something destructive.
    if (!gameFilesConfigured(savedPath)) return emptyList()

    val gameRoot = normPath(savedPath!!)
    val appManaged = listOf(
        normPath("$gameRoot/Data Files"),
        gameRoot,
        normPath(Constants.USER_RESOURCES),
        normPath("${Constants.USER_RESOURCES}/vfs-mw"),
        normPath(Constants.USER_DELTA),
        normPath("${Constants.USER_FILE_STORAGE}/OpenMW/Mods/companion"),
    )

    return values.asSequence()
        .filter { it.category == "data" && normPath(it.value).isNotEmpty() }
        .distinctBy { normPath(it.value) }
        .filter { entry ->
            appManaged.none { it.equals(normPath(entry.value), ignoreCase = true) }
        }
        .toList()
}

/** One row of the Manage-folders list: a registered `data=` entry, and whether it is the base game's
 *  own folder (which needs a stronger warning and also has to clear the stored game-files path). */
private class ManagedFolder(val entry: ModValue, val isGameFiles: Boolean)

/**
 * Every `data=` folder the PLAYER is responsible for — the base game's own Data Files plus any mod
 * folders added on top. This is what the Manage-folders dialog lists.
 *
 * DELIBERATELY WIDER THAN [addedModFolders], which stays "extra folders on top of the base game" for
 * the button's count. Manage needs the base game folder too: without it, a setup whose mods are
 * merged straight into Data Files had nothing to manage at all, and there was no way to unregister a
 * folder and get back to an empty configuration. Removing the game files row is a real, deliberate
 * action here rather than an accident waiting to happen — it is labelled as the game files, it warns
 * accordingly, and it goes through the same cascade as any other removal.
 *
 * Still excluded: the app's own resources / vfs-mw / delta / companion paths. The player never chose
 * those, they are rewritten by the launcher anyway, and removing one breaks the app rather than the
 * setup. (They normally live in the INTERNAL global cfg rather than the user cfg, so this is a
 * belt-and-braces filter for configs that have picked them up.)
 *
 * When the game folder is not known — the cold-open window before the DataStore emits, or a genuinely
 * unconfigured install — nothing can be classified as the base game, so every entry is listed as an
 * ordinary folder. That is safe HERE precisely because the base game is removable by design in this
 * list; it is NOT safe for [addedModFolders], which is why that one keeps its guard.
 */
private fun manageableFolders(values: List<ModValue>, savedPath: String?): List<ManagedFolder> {
    val appManaged = listOf(
        normPath(Constants.USER_RESOURCES),
        normPath("${Constants.USER_RESOURCES}/vfs-mw"),
        normPath(Constants.USER_DELTA),
        normPath("${Constants.USER_FILE_STORAGE}/OpenMW/Mods/companion"),
    )
    val gameRoot = savedPath?.takeIf { gameFilesConfigured(it) }?.let { normPath(it) }
    val gamePaths = listOfNotNull(gameRoot, gameRoot?.let { normPath("$it/Data Files") })

    return values.asSequence()
        .filter { it.category == "data" && normPath(it.value).isNotEmpty() }
        .distinctBy { normPath(it.value) }
        .filter { entry -> appManaged.none { it.equals(normPath(entry.value), ignoreCase = true) } }
        .map { entry ->
            ManagedFolder(
                entry = entry,
                isGameFiles = gamePaths.any { it.equals(normPath(entry.value), ignoreCase = true) },
            )
        }
        // Game files first: it is the root of the setup, and it is the row that carries the warning.
        .sortedByDescending { it.isGameFiles }
        .toList()
}

/** The extensions `ModAssistantViewModel.modPathSelection` turns into `content=` lines when a mod
 *  folder is added (via `findFilesWithExtensions`). Mirrored here so a removal identifies exactly
 *  what the add wrote — including `.bsa`, which that function also files as content. */
private val MOD_PLUGIN_EXTENSIONS =
    setOf("bsa", "esm", "esp", "esl", "omwaddon", "omwgame", "omwscripts")

/**
 * Plugin file names (lowercased) directly inside [dir].
 *
 * NON-recursive on purpose: `findFilesWithExtensions` is a single `listFiles()`, so only top-level
 * plugins ever became `content=` lines. Recursing here would claim entries the add never created.
 * An absent or unreadable folder yields an empty set — callers distinguish that case themselves.
 */
private fun pluginFileNamesIn(dir: String): Set<String> =
    File(dir).takeIf { it.isDirectory }
        ?.listFiles()
        ?.asSequence()
        ?.filter { it.isFile }
        ?.map { it.name }
        ?.filter { it.substringAfterLast('.', "").lowercase() in MOD_PLUGIN_EXTENSIONS }
        ?.map { it.lowercase() }
        ?.toSet()
        ?: emptySet()

/**
 * The extensions the ENGINE can actually load from a `content=` line.
 *
 * Taken from `World::loadContentFiles`' loader table (worldimp.cpp): `.esm`, `.esp`, `.omwgame`,
 * `.omwaddon`, `.project` and `.omwscripts`. Anything else reaches `GameContentLoader::load`, finds
 * no loader, and **throws `"Cannot load file: <path>"`** — a hard startup failure. So this set is a
 * correctness boundary, not a filter for tidiness.
 *
 * Deliberately NARROWER than [MOD_PLUGIN_EXTENSIONS], which mirrors what `modPathSelection` WRITES:
 *  - `.bsa` is excluded. That function files archives as `content=` lines, which the engine cannot
 *    load; archives belong on `fallback-archive=` (the global cfg already lists the three base ones).
 *    Auto-adding one would break launching, so this never does — even though a removal still cleans
 *    up such a line if `modPathSelection` created one.
 *  - `.esl` is excluded for the same reason: this engine registers no loader for it.
 *  - `.project` is excluded on judgement rather than necessity — it loads, but it is an OpenMW-CS
 *    working file and enabling one automatically is not what dropping it in a folder means.
 */
private val ENGINE_CONTENT_EXTENSIONS =
    setOf("esm", "esp", "omwgame", "omwaddon", "omwscripts")

/**
 * Plugins sitting in a registered data folder with no `content=` line yet, as new entries ready to
 * append to the load order.
 *
 * This is what makes a mod copied into a data folder BY HAND — file manager, adb, an unzip — appear
 * in the load order on the next launcher open, instead of only after re-selecting that folder through
 * Add Mods. Registering the folder again was the only route before, and it is a confusing thing to
 * have to discover.
 *
 * Rules that keep it from fighting the player:
 *  - a plugin already named by ANY content entry is left alone, INCLUDING a disabled `;content=` one.
 *    Disabling a mod must not cause it to be silently re-added and re-enabled on the next open.
 *  - only ENABLED `data=` folders are scanned. A `;data=` folder is not on the engine's search path,
 *    so a content line pointing into it would not resolve — the same crash this avoids elsewhere.
 *  - new entries are appended AFTER every existing content entry (ids continue from the current max,
 *    and the writer sorts by id), so an established load order is never reordered. New plugins going
 *    last is also the conventional default for a freshly added one.
 *  - names are scanned in sorted order so repeated runs are deterministic.
 *  - a name that is blank, not equal to its own `trim()`, or contains a newline is SKIPPED. Not
 *    hypothetical fussiness: `readModValues` trims each value, so a file called `"Foo .esp"` would
 *    round-trip as `"Foo.esp"`, never match on the next scan, and be appended again on every pass —
 *    an endless write loop.
 */
private fun unregisteredContent(all: List<ModValue>): List<ModValue> {
    val known = all.asSequence()
        .filter { it.category == "content" }
        .mapTo(mutableSetOf()) { it.value.trim().lowercase() }

    val folders = all.asSequence()
        .filter { it.category == "data" && it.isChecked }
        .map { normPath(it.value) }
        .filter { it.isNotEmpty() }
        .distinct()
        .toList()

    var nextId = all.maxOfOrNull { it.id } ?: 0

    // Gathered across ALL folders first, then ordered once. Ordering per folder (as this did while
    // it merely sorted alphabetically) would interleave by folder and leave the result dependent on
    // which folder each plugin happened to live in.
    val names = mutableListOf<String>()
    folders.forEach { dir ->
        File(dir).takeIf { it.isDirectory }
            ?.listFiles()
            ?.asSequence()
            ?.filter { it.isFile }
            ?.map { it.name }
            ?.filter { it.substringAfterLast('.', "").lowercase() in ENGINE_CONTENT_EXTENSIONS }
            ?.filter { it.isNotBlank() && it == it.trim() && '\n' !in it }
            ?.sorted()
            ?.forEach { name -> if (known.add(name.lowercase())) names += name }
    }

    // The default load order, not plain alphabetical (see LoadOrder.kt). Newly discovered plugins
    // are still APPENDED after everything already registered — this decides only their order among
    // themselves, so an established order is never rearranged. On a first-time setup that is the
    // whole library bar the base masters, which is exactly when the default matters.
    // isChecked comes from defaultEnabledFor, not a flat `true`: Bethesda's own official plugins
    // ship with every copy of the game and vanilla leaves them OFF, so enabling them here made a
    // first-time setup quietly differ from a stock install. They are still registered, one tick away
    // in the panel below.
    return names.sortedByDefaultLoadOrder().map { name ->
        ModValue(++nextId, "content", name, isChecked = defaultEnabledFor(name))
    }
}

/**
 * What removing one mod data folder entails, resolved BEFORE anything is written so the
 * confirmation can enumerate it.
 *
 * @param plugins the `content=` entries that must go with the folder. NOT cosmetic: a `content=`
 *   line whose file cannot be found in any remaining data folder is a HARD launch failure —
 *   `World::loadContentFiles` throws "the content file does not exist" (worldimp.cpp) rather than
 *   skipping it. Removing the `data=` line alone would brick the next launch.
 * @param remaining every value that survives, i.e. what gets written back.
 */
private class ModFolderRemovalPlan(
    val folder: ModValue,
    val displayPath: String,
    val plugins: List<ModValue>,
    val folderMissing: Boolean,
    val remaining: List<ModValue>,
    /** Removing the base game's folder also has to clear the stored game-files path, or the launcher
     *  keeps claiming a game folder it no longer has a `data=` line for. */
    val isGameFiles: Boolean,
)

/**
 * Work out the full cascade for removing [folder].
 *
 * A content entry is doomed when it loads from the folder being removed AND from nowhere else. The
 * "nowhere else" half matters: the same plugin file can sit in two data folders (a patch folder over
 * a base folder) while the cfg carries only ONE `content=` line for it, and that line still resolves
 * after this folder goes. Only ENABLED data entries can resolve anything — the engine never sees a
 * `;data=` line — so disabled ones do not count as a survivor.
 *
 * Disabled `;content=` entries ARE swept along. They are inert today, but leaving one behind means a
 * later re-tick points at a folder that is gone, which is the same crash deferred.
 *
 * When the folder is no longer on disk (the player deleted it themselves and is now tidying up the
 * config) there is nothing to enumerate, so the rule falls back to "every content entry that
 * resolves in no remaining folder". Those entries already cannot load — the config was broken before
 * this removal — and the confirmation says so explicitly rather than sweeping them silently.
 *
 * Touches the filesystem (`listFiles`), so call it off the main thread.
 */
private fun planModFolderRemoval(
    all: List<ModValue>,
    folder: ManagedFolder,
): ModFolderRemovalPlan {
    val target = normPath(folder.entry.value)
    val folderMissing = !File(target).isDirectory
    val inTarget = pluginFileNamesIn(target)

    val elsewhere = all.asSequence()
        .filter { it.category == "data" && it.isChecked }
        .map { normPath(it.value) }
        .filterNot { it.equals(target, ignoreCase = true) }
        .flatMap { pluginFileNamesIn(it).asSequence() }
        .toSet()

    val doomed = all.filter { entry ->
        if (entry.category != "content") return@filter false
        val name = entry.value.trim().lowercase()
        if (name in elsewhere) return@filter false
        if (folderMissing) true else name in inTarget
    }
    val doomedIds = doomed.mapTo(mutableSetOf()) { it.stableId }

    // Matched by PATH, not stableId, so a cfg that somehow lists the same folder twice loses both
    // entries — otherwise the survivor would keep resolving the plugins we just removed.
    val remaining = all.filter { entry ->
        when {
            entry.category == "data" && normPath(entry.value).equals(target, ignoreCase = true) ->
                false
            entry.stableId in doomedIds -> false
            else -> true
        }
    }

    return ModFolderRemovalPlan(
        folder = folder.entry,
        displayPath = normPath(folder.entry.value).removePrefix("/storage/emulated/0/"),
        plugins = doomed,
        folderMissing = folderMissing,
        remaining = remaining,
        isGameFiles = folder.isGameFiles,
    )
}

/**
 * Write a planned removal to openmw.cfg. Returns whether the config now reflects it.
 *
 * CONTENT IS WRITTEN FIRST, AND THAT ORDER IS LOAD-BEARING. Each call rewrites one section, so a
 * failure between the two leaves a partial state — and only one of the two orders degrades safely:
 *  - content then data: worst case the `content=` lines are gone but the `data=` line remains. A
 *    data folder with nothing loading from it is inert, and the game still starts.
 *  - data then content: worst case the `data=` line is gone and the `content=` lines are orphaned,
 *    which is exactly the "content file does not exist" crash this whole feature exists to prevent.
 * The data write is therefore skipped entirely if the content write failed.
 *
 * Both calls pass the same full [ModFolderRemovalPlan.remaining] list; `writeModValuesToFile`
 * filters it to the target category itself. Passing the whole list is what preserves `Data Files`
 * and the app-managed `data=` entries — that writer REPLACES the section it is given, so anything
 * omitted would be dropped.
 */
private suspend fun applyModFolderRemoval(
    viewModel: ModAssistantViewModel,
    plan: ModFolderRemovalPlan,
): Boolean {
    var ok = false
    viewModel.writeModValuesToFile(
        modValues = plan.remaining,
        filePath = Constants.USER_OPENMW_CFG,
        targetCategory = OpenMWConfigUtils.ConfigKeyType.Content.key,
    ) { ok = it }
    if (!ok) return false

    viewModel.writeModValuesToFile(
        modValues = plan.remaining,
        filePath = Constants.USER_OPENMW_CFG,
        targetCategory = OpenMWConfigUtils.ConfigKeyType.Data.key,
    ) { ok = it }
    if (!ok) return false

    // Removing the last folder leaves `saveOpenMWConfig` writing a ZERO-BYTE file, which is not a
    // state the rest of the app recognises: `updateUserConfig` decides whether a game-files selection
    // may reseed this cfg by testing for the default header, so an empty file read as "custom, do not
    // touch" and re-selecting game files silently wrote nothing — a registered game folder with an
    // empty load order. That guard now also accepts an empty file, and writing the header back here
    // means the file says what it is either way rather than sitting at zero bytes.
    // Gated on the FILE being zero bytes, not on `remaining` being empty. Those are different: a cfg
    // can carry lines this screen never models (`fallback-archive=`, `data-local=`, comments), which
    // `saveOpenMWConfig` preserves in its "Others" bucket — so an empty `remaining` does not mean an
    // empty file, and writing the header on that basis would DESTROY those lines. Length 0 is the one
    // case where there is provably nothing to lose.
    withContext(Dispatchers.IO) {
        runCatching {
            val cfg = File(Constants.USER_OPENMW_CFG)
            if (cfg.length() == 0L) cfg.writeText("$USER_CFG_DEFAULT_LINE\n")
        }
    }
    return true
}

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
 * The "Manage folders" list: every registered `data=` folder the player owns — the game files folder
 * and any mod folders added on top — each with a Remove action.
 *
 * Read-plus-remove only. Adding stays on the buttons that open this, and nothing here edits the load
 * order — that is the load-order panel's job.
 *
 * The game files row is TAGGED rather than hidden or disabled. Removing it is a legitimate way to
 * unregister everything and get back to an empty configuration, so the design makes it visible and
 * clearly labelled instead of unreachable; the strength of the warning lives in the confirmation.
 *
 * Shown only while no removal is being confirmed, so this and the confirmation never stack as two
 * dialog windows (the same reason `AlphaMigrationFirstLaunch` gates its browser behind `selecting`).
 */
@Composable
private fun ManageModFoldersDialog(
    folders: List<ManagedFolder>,
    onRemoveRequested: (ManagedFolder) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.simplified_manage_folders_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 300.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = stringResource(R.string.simplified_manage_folders_intro),
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(10.dp))

                if (folders.isEmpty()) {
                    Text(
                        text = stringResource(R.string.simplified_manage_folders_empty),
                        style = MaterialTheme.typography.bodySmall,
                        color = MwBoneDim,
                    )
                    return@Column
                }

                folders.forEachIndexed { index, folder ->
                    if (index > 0) HorizontalDivider(color = MwBronzeDark)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(vertical = 8.dp, horizontal = 2.dp)
                        ) {
                            Text(
                                // Storage prefix trimmed for legibility, as the Alpha3 list does.
                                text = normPath(folder.entry.value)
                                    .removePrefix("/storage/emulated/0/"),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            if (folder.isGameFiles) {
                                // So the consequential row is identifiable BEFORE tapping Remove,
                                // not only in the confirmation that follows.
                                Text(
                                    text = stringResource(R.string.simplified_manage_folders_game_tag),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MwBronzeLight,
                                )
                            }
                        }
                        TextButton(onClick = { onRemoveRequested(folder) }) {
                            Text(stringResource(R.string.remove))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) }
        },
    )
}

/**
 * Confirmation for one folder removal, which ENUMERATES the cascade rather than asking a generic
 * "are you sure".
 *
 * Naming the plugins is the point of this dialog: removing a data folder silently takes plugins out
 * of the load order too, and that is not something the player can infer from "remove this folder".
 * It also states that nothing leaves the device — otherwise Remove reads as deleting a download.
 */
@Composable
private fun ConfirmRemoveModFolderDialog(
    plan: ModFolderRemovalPlan,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.simplified_remove_folder_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(text = plan.displayPath, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))

                if (plan.isGameFiles) {
                    // First thing after the path, before the plugin list, because it changes what
                    // the whole action means: this is not "drop a mod", it is "unregister the game".
                    Text(
                        text = stringResource(R.string.simplified_remove_game_files_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MwBronzeLight,
                    )
                    Spacer(Modifier.height(10.dp))
                }

                if (plan.plugins.isEmpty()) {
                    Text(
                        text = stringResource(R.string.simplified_remove_folder_no_plugins),
                        style = MaterialTheme.typography.bodySmall,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.simplified_remove_folder_plugins),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(4.dp))
                    plan.plugins.forEach { plugin ->
                        Text(
                            text = plugin.value,
                            style = MaterialTheme.typography.bodySmall,
                            color = MwBronzeLight,
                            modifier = Modifier.padding(start = 8.dp, top = 1.dp),
                        )
                    }
                    if (plan.folderMissing) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.simplified_remove_folder_missing),
                            style = MaterialTheme.typography.bodySmall,
                            color = MwBoneDim,
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text(
                    text = stringResource(R.string.simplified_remove_folder_keeps_files),
                    style = MaterialTheme.typography.bodySmall,
                    color = MwBoneDim,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.btn_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.btn_cancel)) }
        },
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
    val context = LocalContext.current

    BackHandler(enabled = showSettings) { showSettings = false }

    // Game typeface, defaulting ON so this launcher matches the companion screens out of the box.
    // Remembered before collecting — an inline accessor returns a new Flow each composition and
    // would restart the DataStore subscription every time (see the note on the home screen's flows).
    val gameFontFlow = remember(context) { GameFilesPreferences.loadLauncherGameFont(context) }
    val useGameFont by gameFontFlow.collectAsState(initial = true)
    val gameFont = if (useGameFont) remember(context) { loadGameFont(context) } else null

    // Scoped colour override rather than edits to shared code. The Settings.cfg editor's switches
    // (`IniSettingItem`: checkedThumb = colorScheme.primary, checkedTrack = primaryContainer), its
    // section titles and its focused field borders all inherit the theme primary, which is the blue
    // `primaryDark`. Those live in IniAssistant.kt, shared with the Alpha3 settings page — so
    // instead of recolouring them there (which would recolour Alpha3 too), this re-themes only what
    // is composed inside the simplified launcher. Any Material component drawn here picks up the
    // bronze automatically, including ones added later.
    // The font rides the SAME scoped MaterialTheme as the colours, for the same reason: it reaches
    // every Material component composed inside this launcher — including the shared Settings.cfg
    // editor's labels, switches and section titles — without touching IniAssistant.kt and therefore
    // without changing the Alpha3 launcher, which keeps the system font.
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = MwBronzeLight,          // switch thumb, section titles, focused borders
            onPrimary = MwStoneDark,          // content on a bronze fill
            primaryContainer = MwBronzeDark,  // switch track when checked
            outline = MwBronzeDark,           // unchecked switch border
            surfaceVariant = MwSlotBg,        // unchecked switch track
        ),
        typography = if (gameFont == null) MaterialTheme.typography
                     else MaterialTheme.typography.withFontFamily(gameFont)
    ) {
        // Same size compensation the companion applies: MysticCards' lowercase and digits occupy
        // much less of the em than the system serif (x-height 0.409 vs 0.536), so at an identical
        // sp it reads a couple of points small. fontScale is text-only — `.dp` reads `density`,
        // left untouched — so panels and paddings keep their sizes and only the glyphs grow.
        val density = LocalDensity.current
        val scaled = remember(density, gameFont) {
            if (gameFont == null) density
            else Density(density.density, density.fontScale * GAME_FONT_SIZE_SCALE)
        }
        CompositionLocalProvider(
            LocalLauncherFont provides gameFont,
            LocalDensity provides scaled
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
}

/**
 * The launcher's typeface for the current composition, or null for the Android system fonts.
 * Provided once at [SimplifiedLauncherRoot]; read by [LauncherSerif] so the handful of explicitly
 * styled headings follow the setting along with everything Material draws.
 */
private val LocalLauncherFont = compositionLocalOf<FontFamily?> { null }

/**
 * The launcher's display face. Mirrors the companion's `MwDisplay` role: the game font when the
 * setting is on, otherwise the platform serif this launcher shipped with.
 *
 * A @Composable getter, so the existing `fontFamily = LauncherSerif` call sites need no other
 * change — the same trick the companion uses for its three font roles.
 */
private val LauncherSerif: FontFamily
    @Composable get() = LocalLauncherFont.current ?: FontFamily.Serif

/**
 * Every Material text style re-pointed at [family]. Compose has no single "set the theme font"
 * switch — `Typography` carries an independent `TextStyle` per role — so each of the fifteen has to
 * be copied. Only the family changes; sizes, weights and letter spacing are left exactly as the
 * Material defaults, which is what keeps this a font swap rather than a restyle.
 */
private fun Typography.withFontFamily(family: FontFamily) = Typography(
    displayLarge = displayLarge.copy(fontFamily = family),
    displayMedium = displayMedium.copy(fontFamily = family),
    displaySmall = displaySmall.copy(fontFamily = family),
    headlineLarge = headlineLarge.copy(fontFamily = family),
    headlineMedium = headlineMedium.copy(fontFamily = family),
    headlineSmall = headlineSmall.copy(fontFamily = family),
    titleLarge = titleLarge.copy(fontFamily = family),
    titleMedium = titleMedium.copy(fontFamily = family),
    titleSmall = titleSmall.copy(fontFamily = family),
    bodyLarge = bodyLarge.copy(fontFamily = family),
    bodyMedium = bodyMedium.copy(fontFamily = family),
    bodySmall = bodySmall.copy(fontFamily = family),
    labelLarge = labelLarge.copy(fontFamily = family),
    labelMedium = labelMedium.copy(fontFamily = family),
    labelSmall = labelSmall.copy(fontFamily = family),
)

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
    // Same predicate the removable-folder list uses, so "is the game folder known" is decided in one
    // place. (Widened from the old `isNullOrEmpty` to `isNullOrBlank`: a whitespace-only stored path
    // is not a game folder either, and treating it as one left Play enabled on an unusable setup.)
    val gameFilesMissing = !gameFilesConfigured(savedPath)

    // The whole cfg mod list, for the Add-Mods button's count AND the Manage-folders dialog's
    // removable list. Read on the SAME triggers as the load-order panel below and through the same
    // settling helper, because `modPathSelection` writes the cfg on its own IO coroutine —
    // `refreshKey` is bumped from its completion callback, but the game-files path can still land
    // late.
    //
    // Kept as the FULL list rather than just the count, because a removal has to rewrite the
    // `content` and `data` sections in full and therefore needs every entry it is preserving — the
    // load-order panel's content-only view is not enough.
    var allModValues by remember { mutableStateOf(emptyList<ModValue>()) }
    LaunchedEffect(refreshKey, savedPath) {
        collectSettledModValues { values -> allModValues = values }
    }
    // Two DIFFERENT lists, deliberately. The count on the Add Mods button is "extra folders on top
    // of the base game", so it excludes the game files folder. The Manage list INCLUDES it, so a
    // setup with no extra folders still has something to manage and can be taken back to empty.
    val addedModFolderCount = remember(allModValues, savedPath) {
        addedModFolders(allModValues, savedPath).size
    }
    val manageable = remember(allModValues, savedPath) {
        manageableFolders(allModValues, savedPath)
    }
    // The base game's row, if it is actually registered in the cfg. Null means the game folder has no
    // `data=` line — which is a real, reachable state (a full removal, or a selection that failed to
    // seed the cfg) and one the Add Mods label must not paper over.
    val gameFilesRow = manageable.firstOrNull { it.isGameFiles }

    // Pick up plugins copied into a registered folder from OUTSIDE the app, so a mod dropped into
    // Data Files by hand appears in the load order without re-selecting the folder through Add Mods.
    //
    // Keyed on `allModValues` rather than on refreshKey, so it runs after every read — including the
    // one the write below triggers. That terminates rather than looping: the second pass finds the
    // entries it just wrote already registered and writes nothing. If the write FAILS the cfg does
    // not change, so no re-read is triggered and it does not spin either.
    LaunchedEffect(allModValues) {
        if (allModValues.isEmpty()) return@LaunchedEffect
        val discovered = withContext(Dispatchers.IO) { unregisteredContent(allModValues) }
        if (discovered.isEmpty()) return@LaunchedEffect

        var ok = false
        modVm.writeModValuesToFile(
            modValues = allModValues + discovered,
            filePath = Constants.USER_OPENMW_CFG,
            targetCategory = OpenMWConfigUtils.ConfigKeyType.Content.key,
        ) { ok = it }
        if (ok) {
            // Deterministic refresh: the write bumps the cfg mtime, but `collectSettledModValues`
            // only watches for a bounded window, so a late write would otherwise not be picked up
            // until something else changed.
            refreshKey++
            MToast(
                String.format(
                    stringRes(R.string.simplified_plugins_discovered), discovered.size
                )
            )
        } else {
            MToast(stringRes(R.string.failed_to_save_openmw_config))
        }
    }

    // Manage-folders state. `pendingPlan` is resolved off the main thread (it lists the folder), so
    // the request and the resolved plan are separate states.
    var showManageFolders by remember { mutableStateOf(false) }
    var folderPendingPlan by remember { mutableStateOf<ManagedFolder?>(null) }
    var pendingRemoval by remember { mutableStateOf<ModFolderRemovalPlan?>(null) }
    LaunchedEffect(folderPendingPlan) {
        val folder = folderPendingPlan ?: return@LaunchedEffect
        pendingRemoval = withContext(Dispatchers.IO) {
            planModFolderRemoval(allModValues, folder)
        }
        folderPendingPlan = null
    }

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
        // CompanionScreen's `MwDisplay` font role + Bold. LauncherSerif follows the Game font
        // setting, resolving to the game typeface or the platform serif
        // and is `private` to CompanionScreen.kt (as is its BronzeLight palette), so this mirrors
        // the value rather than importing it — the companion's typography is deliberately not
        // modified to widen visibility. Colour is MwBronzeLight — the same bronze the companion
        // uses for that title (see the mirrored palette in ui/theme/Color.kt).
        Text(
            text = stringResource(R.string.simplified_launcher_title),
            color = MwBronzeLight,
            fontSize = 28.sp,
            fontFamily = LauncherSerif,
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
                },
                // Same entry point as the Add Mods button's, on the same shared dialog. Both buttons
                // carry it because the dialog spans both concerns — the game files folder and the mod
                // folders are all just registered `data=` entries — and this is the only route to
                // unregistering the game files folder and getting back to an empty configuration.
                secondaryText = stringResource(R.string.simplified_manage_folders)
                    .takeIf { manageable.isNotEmpty() },
                onSecondaryClick = { showManageFolders = true },
            )
            LauncherActionButton(
                modifier = Modifier.weight(1f),
                // Unlike game files there is no single "selected" path to echo back — every tap
                // APPENDS another data folder to openmw.cfg — so the feedback is a running count of
                // what has been added. A path would have to pick one of several arbitrarily, and
                // would silently stop changing on every add after the first.
                //
                // THREE states, not two. Zero added folders means two completely different things,
                // and collapsing them was misleading for anyone who merges their mods straight into
                // the base Data Files folder: that is a perfectly valid setup with mods installed and
                // showing in the load-order panel, yet the button kept saying "select Data Files" as
                // though no setup had happened. So the prompt is shown ONLY while the game folder is
                // genuinely unset; once it is set, the button ECHOES THE DATA FILES PATH IN USE, in
                // the same "<label>: <path>" shape as the game-files button beside it, so a merged
                // setup reads as configured rather than as unfinished.
                //
                // The count still wins when there ARE separate folders, because that is exactly the
                // state where one path cannot represent the setup — there are several, and picking
                // one to display would be arbitrary and would stop changing after the first add.
                //
                // The path comes from the CFG (the Manage list), NOT from the stored game-files path.
                // Deriving it from the stored path let the button advertise a Data Files folder that
                // had no `data=` line at all: after a full removal, re-selecting game files restored
                // the preference while the cfg stayed empty, so the button read correctly while the
                // load order below it was empty. Reading the same source the load order reads means
                // the two cannot disagree.
                text = when {
                    addedModFolderCount > 0 -> pluralStringResource(
                        R.plurals.simplified_mods_added, addedModFolderCount, addedModFolderCount
                    )
                    gameFilesRow != null -> stringResource(R.string.simplified_data_files_label) +
                        normPath(gameFilesRow.entry.value)
                    else -> stringResource(R.string.select_data_files)
                },
                onClick = {
                    showModsBrowser = true
                    MToast(stringRes(R.string.add_mod))
                },
                // The way in to removing a folder. Gated on the MANAGE list, not on the button's own
                // count: with only the game files folder registered the count is 0, but there is
                // still a folder to unregister — and gating on the count is what previously made a
                // merged setup unable to reach the Manage dialog at all.
                //
                // Its own tap target inside the button rather than a second action bound to the whole
                // button: the primary tap must stay ADD, and a mis-tap that removed a folder instead
                // of opening a browser would be a genuinely bad outcome.
                secondaryText = stringResource(R.string.simplified_manage_folders)
                    .takeIf { manageable.isNotEmpty() },
                onSecondaryClick = { showManageFolders = true },
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
                            stringResource(R.string.setting) + ", " +
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

        // 1c. Tamriel Rebuilt launch-time notice. Derived from the SAME `allModValues` the panel
        //     below renders, so it appears and disappears in step with the checkbox the player just
        //     ticked, with no second read of openmw.cfg to fall out of sync.
        val tamrielRebuiltOn = remember(allModValues) {
            allModValues.any { it.category == "content" && it.isChecked && isTamrielData(it.value) }
        }
        if (tamrielRebuiltOn) {
            TamrielRebuiltNotice(
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

    // --- Manage folders. Hidden while a removal is being confirmed so the two never stack. ---
    if (showManageFolders && pendingRemoval == null) {
        ManageModFoldersDialog(
            folders = manageable,
            onRemoveRequested = { folderPendingPlan = it },
            onDismiss = { showManageFolders = false },
        )
    }
    pendingRemoval?.let { plan ->
        ConfirmRemoveModFolderDialog(
            plan = plan,
            onConfirm = {
                pendingRemoval = null
                coroutineScope.launch {
                    if (applyModFolderRemoval(modVm, plan)) {
                        // Removing the game files folder must also clear the STORED PATH, or the
                        // launcher keeps reporting a game folder whose `data=` line no longer
                        // exists — Play would stay enabled on a setup with no game to run. Written
                        // AFTER the cfg succeeds, so a failed write cannot leave the pref cleared
                        // and the cfg intact. "" is the same value MainPageViewModel writes when a
                        // selection fails validation, so `gameFilesConfigured` reads it as unset.
                        if (plan.isGameFiles) {
                            GameFilesPreferences.storeGameFilesPath(context, "")
                        }
                        // Re-reads the cfg, which refreshes BOTH the load-order panel (the removed
                        // plugins leave it) and this dialog's own list behind us.
                        refreshKey++
                        MToast(stringRes(R.string.simplified_remove_folder_done))
                    } else {
                        MToast(stringRes(R.string.failed_to_save_openmw_config))
                    }
                }
            },
            // Cancel drops the plan only, so the manage list comes back — nothing has been written
            // at this point.
            onDismiss = { pendingRemoval = null },
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

/**
 * Launch-time notice for Tamriel Rebuilt, shown only while `Tamriel_Data.esm` is registered AND
 * enabled.
 *
 * TR adds a very large amount of content to load, and the resulting wait looks like a hang on a
 * handheld: the launcher disappears and nothing visible happens for around half a minute. Saying so
 * up front turns that into an expected pause.
 *
 * Purely informational, so it has NO dismiss control, unlike [UpdateBanner]. Dismissing it would
 * have to be remembered somewhere, and the condition already removes it the moment the player turns
 * TR off. Styled as that banner's quieter sibling: same card, bronze rather than the update accent,
 * so it reads as a note about the current setup rather than as something to act on.
 */
@Composable
private fun TamrielRebuiltNotice(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .background(MwFloatStone, RoundedCornerShape(10.dp))
            .border(1.dp, MwBronze, RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(MwBronzeLight, CircleShape)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.simplified_tr_notice),
            color = MwBone,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
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
    secondaryText: String? = null,
    onSecondaryClick: (() -> Unit)? = null,
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
            // 52dp rather than 44dp so a button carrying a secondary line is the same height as one
            // that does not — two visibly different heights side by side in the action row read as a
            // layout mistake.
            .heightIn(min = 52.dp)
            .then(if (flashing) Modifier.graphicsLayer(scaleX = pulse, scaleY = pulse) else Modifier),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MwSlotBg),
        border = BorderStroke(1.dp, if (flashing) MwBronzeLight else MwBronzeDark),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                color = if (flashing) MwBronzeLight else MwBone,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (secondaryText != null) {
                // A clickable INSIDE a Button: the inner clickable consumes the tap, so the
                // enclosing Button's onClick does not also fire. Underlined so it reads as its own
                // target rather than as a subtitle — an inert-looking label that is actually the
                // only way to reach removal would never be found.
                Text(
                    text = secondaryText,
                    color = MwBronzeLight,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .clickable(enabled = onSecondaryClick != null) {
                            onSecondaryClick?.invoke()
                        },
                )
            }
        }
    }
}

/**
 * The mod load-order panel: the Content (ESM/ESP) entries from openmw.cfg, drag-reorderable by the
 * handle on each row and individually enable/disable-able by the checkbox on each row.
 *
 * A DISABLED entry is a `;content=…`-commented line, which [readModValues] surfaces as
 * `isChecked = false` and [ModAssistantViewModel.writeModValuesToFile] re-emits with the `;` — so
 * the persistence half of the toggle was already proven by drag-reorder (a disabled row has always
 * survived a reorder correctly). What was missing was any UI: this panel used to ignore
 * `isChecked` entirely, so a disabled plugin rendered IDENTICALLY to an enabled one. The checkbox
 * and the dimmed row below are the two halves of that fix, and the dimming matters on its own —
 * without it the list actively misreports the config.
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
    // So the read goes through collectSettledModValues, which re-reads for a bounded window while
    // the cfg is still settling. Bounded and self-limiting, in the spirit of the icon pipeline's
    // bounded retry.
    LaunchedEffect(refreshKey, savedPath) {
        collectSettledModValues { values ->
            items = values.filter { it.category == contentKey }
        }
    }

    val lazyListState = rememberLazyListState()
    val reorderableState = rememberReorderableLazyListState(lazyListState) { from, to ->
        items = items.toMutableList().apply { add(to.index, removeAt(from.index)) }
    }

    // The ONE writer for this panel, shared by the drag handler and the enable/disable checkbox —
    // the same call the Alpha3 Content tab makes on drop. It rewrites the whole `content` section
    // from `values`, mapping each entry to `content=` or `;content=` by its `isChecked`, so a
    // reorder and a toggle are the same operation as far as the file is concerned.
    //
    // The list is passed in rather than read from `items` inside, so a caller that has just
    // assigned `items` cannot depend on snapshot-visibility timing for what it persists.
    //
    // Deliberately NOT Alpha3's approach of rewriting the single matching line in openmw.cfg
    // directly: that hand-rolls the `;` prefixing a second time and matches lines by string
    // equality, which drifts from the writer the drag path already uses.
    fun persist(values: List<ModValue>) {
        coroutineScope.launch {
            viewModel.writeModValuesToFile(
                modValues = values,
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

    var showResetOrder by remember { mutableStateOf(false) }
    if (showResetOrder) {
        AlertDialog(
            onDismissRequest = { showResetOrder = false },
            title = { Text(stringResource(R.string.simplified_reset_load_order)) },
            text = { Text(stringResource(R.string.simplified_reset_load_order_tip)) },
            confirmButton = {
                TextButton(onClick = {
                    // Renumbered exactly as the drag handle does — writeModValuesToFile sorts by
                    // id, so reordering the list without reassigning ids would write the OLD order
                    // straight back. isChecked rides along on the copy, so a disabled plugin stays
                    // disabled through a reset; this reorders, it does not re-enable anything.
                    // Also puts Bethesda's official plugins back to vanilla's OFF. That is the
                    // one enabled-state change a reset makes: every other plugin keeps whatever the
                    // player set, since disabling a mod is a deliberate act and a reorder button
                    // must not undo it.
                    val reset = items
                        .sortedByDefaultLoadOrder { it.value }
                        .mapIndexed { i, item ->
                            item.copy(
                                id = i + 1,
                                isChecked = item.isChecked && defaultEnabledFor(item.value)
                            )
                        }
                    items = reset
                    persist(reset)
                    showResetOrder = false
                }) { Text(stringResource(R.string.btn_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showResetOrder = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    Column(
        modifier = modifier
            .background(MwFloatStone, RoundedCornerShape(12.dp))
            // A defined edge so the narrower width reads as a deliberate card rather than as
            // content that just failed to fill the screen. Bronze frame = the companion's mwPanel.
            .border(2.dp, MwBronze, RoundedCornerShape(12.dp))
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                // Own string, NOT the shared R.string.content — that one is ConfigKeyType.Content.tag
                // and the Alpha3 tab label, so renaming it would rename those too.
                text = stringResource(R.string.simplified_load_order),
                color = MwBronzeLight,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp).weight(1f)
            )
            // The way back to the default order (LoadOrder.kt) once it has been rearranged — and the
            // only way for an EXISTING player to get it at all, since the default is applied when a
            // plugin is first registered and their library was registered before it existed.
            // Confirmed, because it discards whatever arrangement is there.
            if (items.isNotEmpty()) {
                TextButton(onClick = { showResetOrder = true }) {
                    Text(
                        text = stringResource(R.string.simplified_reset_load_order),
                        color = MwBronzeLight,
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }

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
                            // Enable/disable. A CHECKBOX rather than a tap on the row: the row is
                            // also the drag surface, and a mis-tap while reaching for the handle
                            // must not silently disable a plugin. Its own 48dp touch target keeps
                            // it well clear of the handle at the far end of the row.
                            Checkbox(
                                checked = modValue.isChecked,
                                onCheckedChange = { checked ->
                                    val updated = items.map {
                                        // Matched on stableId, not id: ids are only sequential
                                        // after a drag renumbers them, and readModValues numbers
                                        // across ALL categories, so they are not unique-per-row
                                        // in any way worth relying on here.
                                        if (it.stableId == modValue.stableId) {
                                            it.copy(isChecked = checked)
                                        } else {
                                            it
                                        }
                                    }
                                    items = updated
                                    persist(updated)
                                },
                                colors = CheckboxDefaults.colors(
                                    checkedColor = MwBronzeLight,
                                    checkmarkColor = MwStoneDark,
                                    uncheckedColor = MwBronzeDark,
                                )
                            )
                            Text(
                                // The ordinal is the row's POSITION (index + 1), and disabled rows
                                // keep theirs — load order still matters for when the plugin comes
                                // back, and numbering only the enabled rows would make every
                                // number below a toggle jump.
                                text = "${index + 1}",
                                color = if (modValue.isChecked) MwBronzeLight else MwBronzeDark,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(end = 6.dp)
                            )
                            Text(
                                text = modValue.value,
                                color = if (modValue.isChecked) MwBone else MwBoneDim,
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
                                        // Renumber so the writer's sortedBy(id) matches the order
                                        // just dragged, then persist. `isChecked` rides along on
                                        // the copy, so a disabled row keeps its `;` through a
                                        // reorder.
                                        val reordered =
                                            items.mapIndexed { i, item -> item.copy(id = i + 1) }
                                        items = reordered
                                        persist(reordered)
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
    // Same remember-then-collect shape; `initial = true` matches the store's own default so the
    // switch never shows the wrong position for a frame.
    val gameFontFlow = remember(context) { GameFilesPreferences.loadLauncherGameFont(context) }
    val launcherGameFont by gameFontFlow.collectAsState(initial = true)
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
                        fontFamily = LauncherSerif,
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
                    fontFamily = LauncherSerif,
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

            // Game font — above the launcher toggle, since it changes THIS launcher's appearance
            // while the toggle leaves it entirely. Applies to the simplified launcher only; the
            // Alpha3 launcher keeps the system font either way.
            SettingRow(
                title = stringResource(R.string.launcher_game_font),
                subtitle = stringResource(R.string.launcher_game_font_tip)
            ) {
                Switch(
                    checked = launcherGameFont,
                    onCheckedChange = {
                        scope.launch { GameFilesPreferences.saveLauncherGameFont(context, it) }
                    }
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 8.dp),
                color = MwBronzeDark
            )

            // The "Use simplified launcher" switch stood here. REMOVED (Aug 25 2026) along with
            // its twin in the Alpha3 launcher's own settings, so the simplified launcher is the
            // only one a player can reach. Nothing was deleted behind it — the Alpha3 launcher and
            // GameFilesPreferences.saveSimplifiedLauncher are both intact — only the way in.
            // GameFilesPreferences.forceSimplifiedLauncherOnce moves anyone already on Alpha3 over.

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
            fontFamily = LauncherSerif,
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
