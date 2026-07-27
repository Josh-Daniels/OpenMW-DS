package org.openmw

import android.content.Context
import android.content.Intent
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Display
import android.view.InputDevice.SOURCE_GAMEPAD
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openmw.modDownloader.ModDatabase
import org.openmw.modDownloader.ModListManager
import org.openmw.ui.controls.UIStateManager
import org.openmw.ui.navigation.RootNav
import org.openmw.ui.theme.OpenMWTheme
import org.openmw.ui.view.AlphaMigrationFirstLaunch
import org.openmw.ui.view.MoeDialog
import org.openmw.ui.view.topScreenRealSize
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
import org.openmw.utils.topScreenLaunchOptions

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

        // Must run before any setup: this instance may be about to finish itself.
        if (relaunchOnTopScreenIfNeeded()) return

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

    /**
     * Moves this Activity to the TOP screen if Android placed it anywhere else, and reports
     * whether it did (in which case this instance is finishing — the caller must bail out).
     *
     * The launcher-icon tap is issued by the OS, not by us, so it can't be pinned at a call
     * site the way [topScreenLaunchOptions] pins ours: the bottom screen runs its own
     * persistent SecondaryDisplayLauncher, and a tap there lands MainActivity on display 4.
     * Play then inherits that display and the game renders on the bottom screen underneath
     * the companion Presentation. A task also keeps its display across recents-resume, so
     * without this the wrong-display state persists until the app is killed. Covers the
     * system-delivered USB_DEVICE_ATTACHED launch too, since it runs unconditionally.
     */
    private fun relaunchOnTopScreenIfNeeded(): Boolean {
        // Already corrected once — never bounce again, whatever the OS did with our request.
        // Without this guard, a device that ignored setLaunchDisplayId would relaunch forever.
        if (intent?.getBooleanExtra(EXTRA_DISPLAY_CORRECTED, false) == true) return false

        val currentDisplayId = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.displayId
        } else {
            @Suppress("DEPRECATION")
            windowManager?.defaultDisplay?.displayId
        } ?: return false

        if (currentDisplayId == Display.DEFAULT_DISPLAY) return false

        Log.w(TAG, "Launched on display $currentDisplayId; relaunching on the top screen")

        // Copy the original intent so a system-delivered action/extras survive the bounce.
        // NEW_TASK|CLEAR_TASK is what actually moves us: a task keeps its display, so
        // reusing the existing one would land us right back on the wrong screen.
        val corrected = Intent(intent ?: Intent(this, MainActivity::class.java)).apply {
            setClass(this@MainActivity, MainActivity::class.java)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(EXTRA_DISPLAY_CORRECTED, true)
        }
        startActivity(corrected, topScreenLaunchOptions())
        finish()
        return true
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

            // Pin the game's render resolution to the TOP screen, once per launch.
            // Must NOT use this Activity's own window metrics: MainActivity can be placed
            // on the companion (bottom) display, and SDLSurface force-sizes the render
            // target to whatever ends up in settings.cfg. Runs here rather than in the
            // composition so it can't re-fire on every recomposition.
            val (topWidth, topHeight) = topScreenRealSize()
            withContext(Dispatchers.IO) {
                val avoidInsertion =
                    GameFilesPreferences.readResolutionInsertion(this@MainActivity).first()
                if (!avoidInsertion) {
                    updateResolutionInConfig(topWidth, topHeight)
                }
            }

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
                        val whatsNew by GameFilesPreferences.getWhatsNew(this@MainActivity).collectAsState(initial = false)

                        if (whatsNew) {
                            MyAlertDialog(showDialog = showDialog)
                        }
                        RootNav()
                        MoeDialog()
                        AlphaMigrationFirstLaunch()
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

    private companion object {
        const val TAG = "MainActivity"
        const val EXTRA_DISPLAY_CORRECTED = "org.openmw.DISPLAY_CORRECTED"
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

