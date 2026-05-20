package org.openmw

import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.view.InputDevice.SOURCE_GAMEPAD
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmw.modDownloader.ModDatabase
import org.openmw.modDownloader.ModListManager
import org.openmw.ui.controls.UIStateManager
import org.openmw.ui.navigation.RootNav
import org.openmw.ui.theme.OpenMWTheme
import org.openmw.ui.view.MoeDialog
import org.openmw.ui.view.currentDeviceRealSize
import org.openmw.ui.view.updateResolutionInConfig
import org.openmw.utils.CaptureCrash
import org.openmw.utils.ConfigFileObserver
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.GameFilesPreferences.getScreenStayOn
import org.openmw.utils.GameFilesPreferences.getSystemBars
import org.openmw.utils.GameFilesPreferences.readCodeGroup
import org.openmw.utils.MyAlertDialog
import org.openmw.utils.PermissionHelper
import org.openmw.utils.PermissionHelper.getManageExternalStoragePermission
import org.openmw.utils.UserManageAssets

@InternalCoroutinesApi
@ExperimentalMaterial3Api
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val uiScope = CoroutineScope(Dispatchers.Main)

    @Suppress("RECEIVER_NULLABILITY_MISMATCH_BASED_ON_JAVA_ANNOTATIONS")
    @OptIn(DelicateCoroutinesApi::class)
    @ExperimentalFoundationApi
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        CaptureCrash.initialize(this)
        Thread.setDefaultUncaughtExceptionHandler(CaptureCrash())

        ModListManager.init(this)
        ModDatabase.getDatabase(this)

        lifecycleScope.launch {
            val permissionGranted = getManageExternalStoragePermission(this@MainActivity)
            if (permissionGranted) {
                proceedWithNextSteps()
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    @OptIn(ExperimentalFoundationApi::class)
    @RequiresApi(Build.VERSION_CODES.R)
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        PermissionHelper.handlePermissionResult(requestCode, intArrayOf(resultCode)) { granted ->
            if (granted) {
                proceedWithNextSteps()
            }
        }
    }

    @OptIn(ExperimentalFoundationApi::class)
    private fun proceedWithNextSteps() {
        lifecycleScope.launch(Dispatchers.Main) {
            GameFilesPreferences.initialize(this@MainActivity)

            withContext(Dispatchers.Default) {
                UserManageAssets(applicationContext).onFirstLaunch()
            }

            val windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
            val (width, height) = windowManager.currentDeviceRealSize()

            setContent {
                OpenMWTheme(
                    darkTheme = true // have to force it bcs hardcode color is used
                ) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        val scope = rememberCoroutineScope()
                        val hideSystemBars by getSystemBars(this@MainActivity).collectAsState(initial = false)
                        val screenStayOn by getScreenStayOn(this@MainActivity).collectAsState(initial = false)

                        if (hideSystemBars) {
                            hideSystemBars(this@MainActivity)
                        } else {
                            showSystemBars(this@MainActivity)
                        }

                        if (screenStayOn) {
                            enableScreenStayOn(this@MainActivity)
                        } else {
                            disableScreenStayOn(this@MainActivity)
                        }

                        LaunchedEffect(Unit) {
                            scope.launch(Dispatchers.IO) {
                                startObservingCodeGroup(this@MainActivity, uiScope)
                                val configFilePath = Constants.SETTINGS_FILE
                                val configFileObserver = ConfigFileObserver(configFilePath)
                                configFileObserver.startWatching()
                            }
                        }

                        val showDialog = remember { mutableStateOf(true) }
                        val avoidInsertion by GameFilesPreferences.readResolutionInsertion(this@MainActivity).collectAsState(initial = false)
                        val whatsNew by GameFilesPreferences.getWhatsNew(this@MainActivity).collectAsState(initial = false)

                        if (!avoidInsertion) {
                            updateResolutionInConfig(width, height)
                        }

                        if (whatsNew) {
                            MyAlertDialog(showDialog = showDialog)
                        }
                        RootNav()
                        MoeDialog()
                    }
                }
            }
        }
    }

    public override fun onDestroy() {
        finish()
        uiScope.cancel()
        super.onDestroy()
        // Process.killProcess(Process.myPid())
    }
}

fun isControllerConnected(context: Context): Boolean {
    val inputManager = context.getSystemService(Context.INPUT_SERVICE) as InputManager
    val deviceIds = inputManager.inputDeviceIds
    for (id in deviceIds) {
        val device = inputManager.getInputDevice(id)
        if (device?.sources?.and(SOURCE_GAMEPAD) == SOURCE_GAMEPAD) {
            return true
        }
    }
    return false
}

fun startObservingCodeGroup(context: Context, scope: CoroutineScope) {
    scope.launch {
        readCodeGroup(context).collect { codeGroup ->
            UIStateManager.tempCodeGroup = codeGroup
        }
    }
}

