package org.openmw.ui.page.main

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmw.Constants
import org.openmw.R
import org.openmw.fragments.processSelectedFolder
import org.openmw.ui.view.ProgressDialog
import org.openmw.ui.view.dismiss
import org.openmw.ui.view.moeDialog
import org.openmw.utils.GameFilesPreferences.storeGameFilesPath
import org.openmw.utils.MToast
import org.openmw.utils.stringRes
import java.io.File
import javax.inject.Inject

@HiltViewModel
class MainPageViewModel @Inject constructor() : ViewModel() {

    val showFileBrowser = mutableStateOf(false)
    val persistedPath = mutableStateOf<String?>(null)

    fun onGameFolderSelected(folder: File, context: Context) {
        val selectedDirectory = DocumentFile.fromFile(folder)
        val iniFile = selectedDirectory.findFile("Morrowind.ini")
        val dataFilesFolder = selectedDirectory.findFile("Data Files")

        if (iniFile != null && dataFilesFolder != null && dataFilesFolder.isDirectory) {
            viewModelScope.launch(Dispatchers.IO) {
                storeGameFilesPath(context, folder.absolutePath)
            }
            MToast("${stringRes(R.string.selected_folder)}${folder.name}")
            // Re-trigger processing
            processSelectedFolder(
                context,
                folder,
                onUriPersisted = { persistedPath.value = it })

            showFileBrowser.value = false
        } else {
            CoroutineScope(Dispatchers.IO).launch {
                storeGameFilesPath(context, "")
            }
            MToast(stringRes(R.string.please_select_a_folder_with_morrowind_ini_and_data_files))
        }
    }

    fun selectMorrowWindFolder(context: Context) {
        // Show loading dialog
        val progressDialog = "".moeDialog(
            fullCustom = true,
            content = {
                ProgressDialog(
                    text = stringRes(R.string.searching_for_morrowind_esm)
                )
            }
        )

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val configFile = File(Constants.USER_OPENMW_CFG)

                if (!configFile.exists()) {
                    progressDialog.dismiss()
                    stringRes(R.string.openmw_config_file_not_found_msg)
                        .moeDialog(
                            title = stringRes(R.string.config_file_missing),
                            confirmLabel = stringRes(R.string.ok),
                            onConfirm = {
                                showFileBrowser.value = true
                            }
                        )
                    return@launch
                }

                val lines = configFile.readLines()
                val dataPaths = mutableListOf<String>()
                var containsMorrowindEsm = false

                // Parse config file
                lines.forEach { line ->
                    when {
                        line.trim().startsWith("content=Morrowind.esm") -> containsMorrowindEsm = true
                        line.trim().startsWith("data=") -> {
                            val path = line.removePrefix("data=\"").removeSuffix("\"").trim()
                            if (path.isNotEmpty()) dataPaths.add(path)
                        }
                    }
                }

                progressDialog.dismiss()

                if (!containsMorrowindEsm) {
                    stringRes(R.string.morrowind_esm_not_in_cfg_msg).moeDialog(
                        title = stringRes(R.string.morrowind_esm_not_found),
                        confirmLabel = stringRes(R.string.yes),
                        onConfirm = {
                            showFileBrowser.value = true
                        },
                        dismissLabel = stringRes(R.string.cancel)
                    )
                    return@launch
                }

                // Show searching dialog again for the second part
                val searchDialog = "".moeDialog(
                    fullCustom = true,
                    content = {
                        ProgressDialog(
                            text = stringRes(R.string.checking_data_paths)
                        )
                    }
                )

                val foundDir = findMorrowindEsm(dataPaths)
                searchDialog.dismiss()
                foundDir?.let { folder ->
                    String.format(stringRes(R.string.found_morrowind_esm_msg), folder)
                        .moeDialog(
                            title = stringRes(R.string.game_files_found),
                            confirmLabel = stringRes(R.string.yes),
                            onConfirm = {
                                viewModelScope.launch(Dispatchers.IO) {
                                    // Attempt to locate Morrowind.ini in the current or parent directory
                                    val iniFile = listOf(
                                        File(folder, "Morrowind.ini"),
                                        folder.parentFile?.let { File(it, "Morrowind.ini") }
                                    ).firstOrNull { it?.exists() == true }

                                    // Use the directory that contains Morrowind.ini if found, otherwise default
                                    val iniDirectory = iniFile?.parentFile ?: folder

                                    // Store the chosen path
                                    storeGameFilesPath(context, iniDirectory.absolutePath)

                                    // Use the ini directory for folder processing
                                    processSelectedFolder(
                                        context,
                                        iniDirectory,
                                        onUriPersisted = { persistedPath.value = it }
                                    )
                                    MToast(stringRes(R.string.game_files_location_set))
                                }
                            },
                            dismissLabel = stringRes(R.string.no),
                            onDismiss = {
                                showFileBrowser.value = true
                            }
                        )
                } ?: run {
                    stringRes(R.string.morrowind_esm_not_found_msg)
                        .moeDialog(
                            title = stringRes(R.string.not_found),
                            confirmLabel = stringRes(R.string.ok),
                            onConfirm = {
                                showFileBrowser.value = true
                            }
                        )
                }
            } catch (e: Exception) {
                progressDialog.dismiss()
                (stringRes(R.string.failed_to_process_config_file) + e.localizedMessage)
                    .moeDialog(
                        title = stringRes(R.string.error),
                        confirmLabel = stringRes(R.string.ok),
                        onConfirm = {
                            showFileBrowser.value = true
                        }
                    )
            }
        }
    }


    suspend fun findMorrowindEsm(dataPaths: List<String>): File? {
        return withContext(Dispatchers.IO) {
            dataPaths.firstNotNullOfOrNull { dataPath ->
                try {
                    val dataDir = File(dataPath)
                    if (!dataDir.exists() || !dataDir.isDirectory) return@firstNotNullOfOrNull null

                    // Check root directory first
                    val rootFile = dataDir.walk()
                        .firstOrNull {
                            it.isFile && it.name.equals("Morrowind.esm", ignoreCase = true)
                        }
                    if (rootFile != null) return@firstNotNullOfOrNull rootFile.parentFile

                    // Then check Data Files subfolder
                    val dataFilesDir = File(dataPath, "Data Files")
                    if (dataFilesDir.exists() && dataFilesDir.isDirectory) {
                        dataFilesDir.walk()
                            .firstOrNull {
                                it.isFile && it.name.equals("Morrowind.esm", ignoreCase = true)
                            }?.parentFile
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
        }
    }
}
