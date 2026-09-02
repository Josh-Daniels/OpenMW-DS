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
import org.openmw.ui.page.simplified.SimplifiedLauncherRoot
import org.openmw.ui.theme.OpenMWTheme
import org.openmw.ui.view.AlphaMigrationFirstLaunch
import org.openmw.ui.view.MoeDialog
import org.openmw.ui.view.applyGameScreenResolution
import org.openmw.ui.view.TUNED_PERF_SETTINGS_VERSION
import org.openmw.ui.view.applyTunedPerformanceSettings
import org.openmw.ui.view.seedConsoleWindowSize
import org.openmw.utils.CaptureCrash
import org.openmw.utils.ConfigFileObserver
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.GameFilesPreferences.getScreenStayOn
import org.openmw.utils.GameFilesPreferences.getSystemBars
import org.openmw.utils.GameFilesPreferences.readCodeGroup
import org.openmw.utils.MyAlertDialog
import org.openmw.utils.PermissionHelper
import org.openmw.utils.PermissionHelper.getManageExternalStoragePermission
import org.openmw.utils.UpdateChecker
import org.openmw.utils.UserManageAssets
import org.openmw.utils.DisplayRoles
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

            // Load the device display profile before anything reads a display role. The launcher
            // and the engine share a process, so priming it here is what lets startGame() and
            // startCompanionScreen() resolve their displays without suspending. See DisplayRoles.
            DisplayRoles.prime(this@MainActivity)

            // Pin the game's render resolution to the display the GAME will use, once per launch.
            // Must NOT use this Activity's own window metrics: MainActivity can be placed on the
            // companion display, and SDLSurface force-sizes the render target to whatever ends up
            // in settings.cfg. Runs here rather than in the composition so it can't re-fire on
            // every recomposition. It follows the display profile, so a swapped profile writes the
            // OTHER panel's real size with nothing hardcoded. SHARED with the display-profile
            // dropdown, which re-runs it on a profile change — see applyGameScreenResolution.
            applyGameScreenResolution()
            withContext(Dispatchers.IO) {
                // Rides the same settings.cfg pass, on the same IO dispatcher. Unrelated to the
                // resolution insertion and deliberately NOT gated by its opt-out, which is about
                // this app overwriting a resolution the player chose; this one never overwrites
                // anything (see seedConsoleWindowSize).
                seedConsoleWindowSize()
                // Push this build's measured performance defaults ONCE per version. Must run here,
                // before the engine is ever started: settings.cfg is read at engine startup and
                // rewritten from memory on a clean exit, so a write made while the game is running
                // is silently discarded. Unlike the resolution pin above this is NOT
                // authoritative every launch, because the simplified launcher ships the whole
                // settings.cfg editor and the player must be able to keep their own values.
                if (GameFilesPreferences.readTunedPerfSettingsVersion(this@MainActivity)
                    < TUNED_PERF_SETTINGS_VERSION
                ) {
                    applyTunedPerformanceSettings()
                    GameFilesPreferences.setTunedPerfSettingsVersion(
                        this@MainActivity, TUNED_PERF_SETTINGS_VERSION
                    )
                }
                // Alpha3-launcher removal. Deliberately inside this AWAITED block rather than in a
                // detached launch: setContent runs straight after it, and a write that landed later
                // would show the Alpha3 launcher for a frame before swapping it out.
                // runCatching for the same reason [prefsData] exists: this is the FIRST write to
                // a store that lives on external storage, so on a first launch it is the call most
                // likely to meet a folder that does not exist yet. A one-shot repair must never be
                // able to take startup down; failing here just means it retries next launch.
                runCatching {
                    GameFilesPreferences.forceSimplifiedLauncherOnce(this@MainActivity)
                }.onFailure { Log.w("MainActivity", "simplified-launcher one-shot failed", it) }
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
                            // Automatic update check. Deliberately here and NOT in
                            // onFirstLaunch()/IdentityMarker's spot — that runs inside a
                            // withContext the launcher AWAITS before setContent, so a slow or
                            // unreachable network would delay first paint. By this point the UI
                            // is already composed.
                            //
                            // Its own launch (not appended to the block above) so a stalled HTTP
                            // call can't hold up the config observer behind it. Result lands in
                            // UpdateChecker.state, which the Settings screen observes — it only
                            // CHECKS; the ~71MB download stays an explicit user action.
                            scope.launch(Dispatchers.IO) {
                                UpdateChecker.checkOnLaunch()
                            }
                        }

                        val showDialog = remember { mutableStateOf(true) }
                        val whatsNew by GameFilesPreferences.getWhatsNew(this@MainActivity).collectAsState(initial = false)

                        if (whatsNew) {
                            MyAlertDialog(showDialog = showDialog)
                        }
                        // Which launcher home screen to show. Default true = the simplified
                        // launcher; false swaps in the Alpha3 launcher (RootNav), which is left
                        // completely untouched. Both are reachable from each other's settings, so
                        // this is non-destructive either way.
                        val simplifiedLauncher by GameFilesPreferences
                            .loadSimplifiedLauncher(this@MainActivity)
                            .collectAsState(initial = true)

                        if (simplifiedLauncher) {
                            SimplifiedLauncherRoot()
                        } else {
                            RootNav()
                        }
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

