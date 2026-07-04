@file:OptIn(DelicateCoroutinesApi::class)

package org.openmw

// For MorrowindDS
import android.app.Presentation
import android.hardware.display.DisplayManager
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import org.openmw.companion.CompanionScreen
import org.openmw.companion.ConversationHistoryOverlay
import org.openmw.companion.GameStateRepository
import org.openmw.companion.OptionsMenuOverlay
import org.openmw.companion.ConversationLocation
import org.openmw.companion.UiMode
import org.openmw.companion.UiPreferences

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.PixelFormat
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.util.Log
import android.view.Choreographer
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.libsdl.app.SDLActivity
import org.openmw.ui.controls.HapticEffect
import org.openmw.ui.controls.MouseIcon
import org.openmw.ui.controls.ResizableDraggableButton
import org.openmw.ui.controls.ResizableDraggableRightThumbstick
import org.openmw.ui.controls.ResizableDraggableThumbstick
import org.openmw.ui.controls.ScrollWheelIndicator
import org.openmw.ui.controls.UIKeyboard
import org.openmw.ui.controls.UIStateManager
import org.openmw.ui.controls.UIStateManager.configureControls
import org.openmw.ui.controls.UIStateManager.containerGlobalHeight
import org.openmw.ui.controls.UIStateManager.containerGlobalWidth
import org.openmw.ui.controls.UIStateManager.editMode
import org.openmw.ui.controls.UIStateManager.enableQuickSlot
import org.openmw.ui.controls.UIStateManager.enableRightThumb
import org.openmw.ui.controls.UIStateManager.gridAlpha
import org.openmw.ui.controls.UIStateManager.gridVisible
import org.openmw.ui.controls.UIStateManager.isCursorVisible
import org.openmw.ui.controls.UIStateManager.isRadialMenuExpanded
import org.openmw.ui.controls.UIStateManager.launchedActivity
import org.openmw.ui.controls.UIStateManager.offsetXFlow
import org.openmw.ui.controls.UIStateManager.offsetYFlow
import org.openmw.ui.controls.UIStateManager.soundHaptics
import org.openmw.ui.controls.UIStateManager.uqmJNI
import org.openmw.ui.controls.VirtualKeyboard
import org.openmw.ui.overlay.ExpandableCircleButton
import org.openmw.ui.overlay.GridOverlay
import org.openmw.ui.overlay.HiddenMenu
import org.openmw.ui.overlay.OverlayUI
import org.openmw.ui.view.BackgroundAnimation
import org.openmw.ui.view.NavmeshScreen
import org.openmw.ui.view.addCustomLog
import org.openmw.ui.view.enableLogcat
import org.openmw.ui.view.vibrateHelper
import org.openmw.utils.DebugOverlayBox
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.GameFilesPreferences.getENVLine
import org.openmw.utils.GameFilesPreferences.loadAutoMouseMode
import org.openmw.utils.GameFilesPreferences.readAngle
import org.openmw.utils.GameFilesPreferences.readSPIRV
import kotlin.math.roundToInt

@Suppress("DEPRECATION")
@InternalCoroutinesApi
@AndroidEntryPoint
class EngineActivity : SDLActivity() {
    private lateinit var sdlView: View
    private external fun getPathToJni(path_global: String, path_user: String)

    // For MorrowindDS
    private var companionPresentation: Presentation? = null

    // Full-screen options/display-settings overlay on the bottom-screen Presentation,
    // shown while the in-game pause/options menu is open.
    private var pauseOverlayView: View? = null

    // Read-only conversation-history overlay on the TOP screen (this activity's own
    // window / Display 0), shown while a conversation is active AND the Conversation
    // element is routed to the top screen (the split-conversation mode).
    private var conversationTopView: View? = null

    external fun getLastResourceName(): String
    external fun initAlpha3()
    private external fun installCompanionSink()

    init {
        System.loadLibrary("Alpha3")
        setEnvironmentVariables(this@EngineActivity)
    }

    @SuppressLint("UnsafeDynamicallyLoadedCode")
    override fun getLibraries(): Array<String> {
        val jniLibsArray = when (UIStateManager.tempCodeGroup) {
            "OpenMW" -> arrayOf("SDL2", "ng_gl4es", "c++_shared", "openal", "openmw")
            "UQM" -> arrayOf("SDL2", "ng_gl4es", "c++_shared", "openal", "uqm")
            "Dethrace" -> arrayOf("SDL2", "ng_gl4es", "c++_shared", "openal", "dethrace")
            else -> emptyArray()
        }

        jniLibsArray.forEach { libName ->
            System.loadLibrary(libName)
        }
        return jniLibsArray
    }

    override fun getMainSharedObject(): String {
        val sharedObjectName = when (UIStateManager.tempCodeGroup) {
            "OpenMW" -> OPENMW_MAIN_LIB
            "UQM" -> UQM_MAIN_LIB
            "Dethrace" -> DETHRACE_MAIN_LIB
            else -> ""
        }

        return sharedObjectName
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars(this) // fix sometimes systemBars will show again
    }

    override fun onDestroy() {
        super.onDestroy()

        hidePauseOverlay()
        hideConversationTopOverlay()

        // MorrowindDS
        companionPresentation?.dismiss()

        finishAffinity() // kill all activity
        Process.killProcess(Process.myPid())
//        exitProcess(0)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        runCatching {
            initAlpha3()
        }.onFailure {
            Log.e(TAG, "initAlpha3: ", it)
        }
        super.onCreate(savedInstanceState)

        launchedActivity = true
        val controllerConnected = isControllerConnected(this)

        setContentView(R.layout.engine_activity)

        if (!configureControls) {
            sdlView = getContentView()
        } else if (configureControls) {
            sdlView = View(this).apply {
                layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.MATCH_PARENT
                )
            }
        }

        hideSystemBars(this)
        enableScreenStayOn(this)

        // MorrowindDS Second screen
        startCompanionScreen()

        // Add SDL view programmatically
        val sdlContainer = findViewById<FrameLayout>(R.id.sdl_container)
        (sdlView.parent as? ViewGroup)?.removeView(sdlView)
        sdlContainer.addView(sdlView) // Add SDL view to the sdl_container

        if (UIStateManager.tempCodeGroup == "OpenMW") {
            getPathToJni(filesDir.parent!!, Constants.USER_FILE_STORAGE)
        }

        if (UIStateManager.isLogcatEnabled) {
            enableLogcat()
        }

        // Setup Compose overlay for buttons
        val composeViewUI = findViewById<ComposeView>(R.id.compose_overlayUI)
        (composeViewUI.parent as? ViewGroup)?.removeView(composeViewUI)
        sdlContainer.addView(composeViewUI)

        // Adding a Global Layout Listener to get container dimensions
        sdlContainer.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                val containerWidth = sdlContainer.width.toFloat()
                val containerHeight = sdlContainer.height.toFloat()

                containerGlobalHeight.floatValue = sdlContainer.height.toFloat()
                containerGlobalWidth.floatValue = sdlContainer.width.toFloat()

                // Load button states into UIStateManager with container dimensions
                UIStateManager.loadButtonState(containerWidth, containerHeight)

                if (!UIStateManager.useNavmesh) {
                    // Adds Overlay menu for buttons and edit mode
                    composeViewUI.setContent {
                        val isUIHidden by GameFilesPreferences.loadUIState(this@EngineActivity).collectAsState(initial = false)
                        // In-game Hide UI (OpenMW mHudEnabled), pushed from native via
                        // onHudVisibilityChanged. The overlay shows only when the app's own
                        // hide preference is off AND the game HUD is currently visible.
                        val hudVisible by GameStateRepository.hudVisible.collectAsState()
                        // Companion toggle for the Alpha3 launcher overlay (gear + arrow cluster).
                        val showAlpha3Overlay by UiPreferences.alpha3OverlayFlow().collectAsState()
                        val autoMouseMode by loadAutoMouseMode(this@EngineActivity).collectAsState(initial = "Hybrid")
                        val virtualKeyboard by GameFilesPreferences.useVirtualKeyboard(this@EngineActivity).collectAsState(initial = true)
                        val isVibrationOn by GameFilesPreferences.loadVibrationState(this@EngineActivity).collectAsState(initial = true)

                        BackHandler {
                            // disable back exit
                            Log.d(TAG, "onGlobalLayout: Back pressed")
                        }

                        if (UIStateManager.tempCodeGroup == "OpenMW") {
                            AutoMouseModeComposable()
                            SoundWatcher { name ->
                                val effect = findHapticForSound(name)
                                if (effect != null && isVibrationOn) {
                                    vibrateHelper(this@EngineActivity, effect.amplitude, effect.duration)
                                }
                                addCustomLog(
                                    "Sound: $name",
                                    textSize = 10,
                                    textColor = Color.White
                                )
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .pointerInput(Unit) {
                                        // Consumes all touch events, effectively disabling them
                                        awaitPointerEventScope {
                                            while (true) {
                                                val event = awaitPointerEvent()
                                                event.changes.forEach { it.consume() }
                                            }
                                        }
                                    }
                            )
                        }
                        val snapX = remember { mutableStateOf<Float?>(null) }
                        val snapY = remember { mutableStateOf<Float?>(null) }

                        AnimatedVisibility(
                            visible = configureControls,
                            enter = fadeIn() + expandIn(),
                            exit = fadeOut() + shrinkOut()
                        ) {
                            BackgroundAnimation()
                        }

                        AnimatedVisibility(
                            visible = editMode && gridVisible.value,
                            enter = fadeIn() + expandIn(),
                            exit = fadeOut() + shrinkOut()
                        ) {
                            GridOverlay(
                                gridSize = UIStateManager.gridSize.intValue,
                                snapX = snapX.value,
                                snapY = snapY.value,
                                alpha = gridAlpha.floatValue
                            )
                        }

                        AnimatedVisibility(
                            visible = isCursorVisible == 1 && UIStateManager.tempCodeGroup == "OpenMW" && !isUIHidden,
                            enter = fadeIn() + expandIn(),
                            exit = fadeOut() + shrinkOut()
                        ) {
                            if (autoMouseMode != "None" || controllerConnected) {
                                MouseIcon(
                                    containerWidth = containerWidth,
                                    containerHeight = containerHeight
                                )
                            }
                        }

                        if (!isUIHidden && hudVisible && showAlpha3Overlay) {
                            OverlayUI(
                                context = this@EngineActivity,
                                virtualKeyboard = virtualKeyboard,
                                onKeyEvent = { keyCode -> handleKeyEvent(keyCode) }
                            )
                        }

                        Buttons(context = this@EngineActivity, containerWidth = containerWidth, containerHeight = containerHeight)

                        AnimatedVisibility(
                            visible = enableRightThumb && UIStateManager.tempCodeGroup == "OpenMW",
                            enter = fadeIn() + expandIn(),
                            exit = fadeOut() + shrinkOut()
                        ) {
                            ResizableDraggableRightThumbstick(
                                context = this@EngineActivity,
                                containerWidth = containerWidth,
                                containerHeight = containerHeight
                            )
                        }

                        AnimatedVisibility(
                            visible = UIKeyboard.showVKB,
                            enter = fadeIn() + expandIn(),
                            exit = fadeOut() + shrinkOut()
                        ) {
                            Box(modifier = Modifier.wrapContentSize()) {
                                VirtualKeyboard()
                            }
                        }

                        AnimatedVisibility(
                            visible = isCursorVisible == 0 && enableQuickSlot,
                            enter = fadeIn() + expandIn(),
                            exit = fadeOut() + shrinkOut()
                        ) {
                            HiddenMenu(
                                context = this@EngineActivity,
                                containerWidth = containerWidth,
                                containerHeight = containerHeight
                            )
                        }

                        AnimatedVisibility(
                            visible = isRadialMenuExpanded,
                            enter = fadeIn() + expandIn(),
                            exit = fadeOut() + shrinkOut()
                        ) {
                            Box(modifier = Modifier.wrapContentSize()) {
                                ExpandableCircleButton()
                            }
                        }

                        ScrollWheelIndicator(
                            context = this@EngineActivity,
                            containerWidth = containerWidth,
                            containerHeight = containerHeight
                        )

                        AnimatedVisibility(
                            visible = UIStateManager.tempCodeGroup == "UQM" && uqmJNI,
                            enter = fadeIn() + expandIn(),
                            exit = fadeOut() + shrinkOut()
                        ) {
                            DebugOverlayBox()
                        }
                    }

                    //if (UIStateManager.tempCodeGroup == "OpenMW") {
                    val composeViewLeftThumb = findViewById<ComposeView>(R.id.compose_leftThumb)
                    (composeViewLeftThumb.parent as? ViewGroup)?.removeView(composeViewLeftThumb)

                    initializeOffsetsFromStateFlow()

                    composeViewLeftThumb.setContent {
                        ResizableDraggableThumbstick(
                            context = this@EngineActivity,
                            containerWidth = containerWidth,
                            containerHeight = containerHeight
                        )
                    }

                    sdlContainer.addView(composeViewLeftThumb)
                    updateComposeViewPosition(composeViewLeftThumb)
                    //}
                } else if (UIStateManager.useNavmesh) {
                    composeViewUI.setContent {
                        BackgroundAnimation()
                        NavmeshScreen(onComplete = { navigateToMain() })
                    }
                }
                // Remove the Global Layout Listener to prevent multiple calls
                sdlContainer.viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
    }

    @OptIn(ExperimentalMaterial3Api::class)
    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }

    private fun initializeOffsetsFromStateFlow() {
        lifecycleScope.launch {
            val buttonStates = UIStateManager.buttonStates.value
            buttonStates[99]?.let { state ->
                offsetXFlow.value = state.offsetX
                offsetYFlow.value = state.offsetY
            }
        }
    }

    private fun updateComposeViewPosition(composeViewLeftThumb: ComposeView) {
        lifecycleScope.launch {
            launch {
                offsetXFlow.collect { offsetX ->
                    val params = composeViewLeftThumb.layoutParams as FrameLayout.LayoutParams
                    params.leftMargin = offsetX.roundToInt()
                    composeViewLeftThumb.layoutParams = params
                }
            }
            launch {
                offsetYFlow.collect { offsetY ->
                    val params = composeViewLeftThumb.layoutParams as FrameLayout.LayoutParams
                    params.topMargin = offsetY.roundToInt()
                    composeViewLeftThumb.layoutParams = params
                }
            }
        }
    }

    private fun setEnvironmentVariables(context: Context) {
        try {
            Os.setenv("OPENMW_GLES_VERSION", "32", true)
            Os.setenv("LIBGL_ES", "3", true)

            Os.setenv("UQM_CONFIG_DIR", "${Constants.USER_FILE_STORAGE}/uqm", true)

            if ((UIStateManager.tempCodeGroup) != "UQM") {
                val enableANGLE = runBlocking {
                    readAngle(context).first()
                }
                val enableSPIRV = runBlocking {
                    readSPIRV(context).first()
                }
                if (enableSPIRV) {
                    Os.setenv("LIBGL_SIMPLE_SHADERCONV", "1", true)
                }
                if (enableANGLE) {
                    Os.setenv("LIBGL_SIMPLE_SHADERCONV", "0", true)
                    Os.setenv("LIBGL_GLES", "libGLESv2_angle.so", true)
                    Os.setenv("LIBGL_EGL", "libEGL_angle.so", true)
                    Os.setenv("SDL_VIDEO_GL_DRIVER", "libGLESv2_angle.so", true)
                    Os.setenv("SDL_VIDEO_EGL_DRIVER", "libEGL_angle.so", true)
                }
            }

            Os.setenv("OSG_TEXT_SHADER_TECHNIQUE", "ALL", true)
            Os.setenv("OSG_VERTEX_BUFFER_HINT", "VBO", true)
            Os.setenv("OSG_GL_TEXTURE_STORAGE", "OFF", true)
            Os.setenv("LIBGL_INSTANCING", "1", true)

            Os.setenv("DETHRACE_ROOT_DIR", "${Constants.USER_FILE_STORAGE}/dethrace/", true)

            Os.setenv("HARNESS_OPENGL", "1", true)

            CoroutineScope(Dispatchers.IO).launch {
                GameFilesPreferences.readAvoid16Bits(context).collect { avoid16bits ->
                    if (avoid16bits) {
                        Os.setenv("LIBGL_AVOID16BITS", "1", true)
                    } else {
                        Os.setenv("LIBGL_AVOID16BITS", "0", true)
                    }
                }
                // Set LIBGL_SHRINK based on preference
                GameFilesPreferences.readTextureShrinkingOption(context)
                    .collect { textureShrinkingOption ->
                        when (textureShrinkingOption) {
                            "low" -> Os.setenv("LIBGL_SHRINK", "2", true)
                            "medium" -> Os.setenv("LIBGL_SHRINK", "7", true)
                            "high" -> Os.setenv("LIBGL_SHRINK", "6", true)
                            else -> Os.setenv("LIBGL_SHRINK", "0", true)
                        }
                    }
            }

            val envLine = getENVLine(context)
            envLine?.let {
                if (it.isNotEmpty()) {
                    // Split the command line string by commas to get individual key-value pairs
                    envLine.split(" ").forEach { pair ->
                        // Split each pair by '=' to separate key and value
                        val keyValue = pair.trim().split("=")
                        if (keyValue.size == 2) {
                            val key = keyValue[0].trim()
                            val value = keyValue[1].trim()
                            if (key.isNotEmpty() && value.isNotEmpty()) {
                                Os.setenv(key, value, true)
                            }
                        }
                    }
                }
            }
        } catch (e: ErrnoException) {
            Log.e("Alpha3", "Failed setting environment variables.")
            e.printStackTrace()
        }

        Log.d("EngineActivity", "Environment variables set")
    }

    private fun handleKeyEvent(keyCode: Int) {
        val keyEventDown = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
        val keyEventUp = KeyEvent(KeyEvent.ACTION_UP, keyCode)
        dispatchKeyEvent(keyEventDown)
        dispatchKeyEvent(keyEventUp)
        Log.d(TAG, "Sent key event: $keyCode")
    }

    companion object {
        private const val TAG = "OpenMW-Launcher"

        var resolutionX = 0
        var resolutionY = 0

        /** Called from native (engine thread) for every COMPANION_* log line. */
        @JvmStatic
        fun onCompanionLine(line: String) {
            GameStateRepository.onJniLine(line)
        }

        /**
         * Called from the OSG render thread once per cell entry with raw RGBA pixels
         * from glReadPixels. width/height are in pixels; segX/segY are the map segment
         * grid coordinates; isInterior is 1 for interior cells, 0 for exterior.
         * boundsMinX/boundsMinY are the interior's mBounds min corner in world units
         * (0.0f for exterior segments, where they're unused).
         */
        @JvmStatic
        fun onCompanionMapTexture(
            width: Int, height: Int, segX: Int, segY: Int, isInterior: Int,
            boundsMinX: Float, boundsMinY: Float, rgba: ByteArray
        ) {
            GameStateRepository.onMapTexture(width, height, segX, segY, isInterior, boundsMinX, boundsMinY, rgba)
        }

        /**
         * Called from native (engine thread) whenever OpenMW's in-game Hide UI
         * toggle flips mHudEnabled. Mirrors the state onto the Alpha3 second-screen
         * overlay so the touch controls / gear icon / arrow hide and show in sync.
         */
        @JvmStatic
        fun onHudVisibilityChanged(visible: Boolean) {
            GameStateRepository.setHudVisible(visible)
        }

        /** Queues a CMP: command for delivery to Lua on the engine thread. */
        @JvmStatic
        external fun sendCompanionCommand(cmd: String)

        /**
         * Pushes the companion "Game cursor" option (UiPreferences "gameCursor") to
         * native, where companionCursorEnabled() reads it to suppress the top-screen
         * SDL cursor when off. Called on change and once at startup.
         */
        @JvmStatic
        external fun setCompanionCursorEnabled(enabled: Boolean)

        /**
         * Per-element native HUD visibility (companion "Vanilla HUD Elements" options).
         * true = native top-screen element shown; false = hidden. Read natively by
         * companionHud*() (companion-hud-elements.patch). Pushed on change + at startup.
         * "Equipped" gates both the weapon and spell boxes.
         */
        @JvmStatic external fun setCompanionHudHms(on: Boolean)
        @JvmStatic external fun setCompanionHudEquipped(on: Boolean)
        @JvmStatic external fun setCompanionHudMinimap(on: Boolean)
        @JvmStatic external fun setCompanionHudEffects(on: Boolean)
        @JvmStatic external fun setCompanionHudSneak(on: Boolean)
        @JvmStatic external fun setCompanionHudCrosshair(on: Boolean)
        @JvmStatic external fun setCompanionHudEnemy(on: Boolean)

        /**
         * Decodes an item icon from the VFS (BSA/loose files) and writes it as PNG.
         * [iconPath] is the VFS-normalized path from rec.icon (e.g. "icons/m/misc_shirt_01.dds").
         * [outputPath] is the absolute filesystem path for the output PNG.
         * Safe to call from any thread; does not require a GL context.
         */
        @JvmStatic
        external fun exportIconToPng(iconPath: String, outputPath: String)
    }

    fun findHapticForSound(name: String): HapticEffect? {
        val lower = name.lowercase()
        return soundHaptics.entries.firstOrNull { (pattern, _) ->
            lower.contains(pattern)
        }?.value
    }

    @Composable
    fun SoundWatcher(onSound: (String) -> Unit) {
        var last by remember { mutableStateOf("") }

        LaunchedEffect(Unit) {
            while (true) {
                withFrameNanos {
                    val name = getLastResourceName()
                    if (name.isNotEmpty() && name != last) {
                        last = name
                        onSound(name)
                    }
                }
            }
        }
    }

    override fun getArguments(): Array<String> {
        val commandLineArgs = runBlocking {
            GameFilesPreferences.getARGLine(this@EngineActivity).first() ?: ""
        }

        // Check if command-line arguments are valid
        if (commandLineArgs.isEmpty() || !commandLineArgs.contains("-")) {
            return super.getArguments()
        }

        return try {
            // Construct resource path
            val resourcesPath = "--resources " + Constants.USER_RESOURCES
            val combinedArgs = "$resourcesPath $commandLineArgs"

            // Parse the combined arguments
            val args = arrayListOf<String>()
            combinedArgs.split(" ".toRegex()).forEach {
                if (it.isNotEmpty()) {
                    args.add(it)
                }
            }
            args.toTypedArray()
        } catch (_: Exception) {
            super.getArguments()
        }
    }

    // MorrowindDS Second screen function.
    private fun startCompanionScreen() {
        val dm = getSystemService(Context.DISPLAY_SERVICE) as DisplayManager
        val displays = dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION)
        Log.d(TAG, "Second-screen: found ${displays.size} presentation display(s)")

        if (displays.isEmpty()) {
            Log.e(TAG, "Second-screen: NO presentation display found")
            return
        }

        // Install in-process log sink so COMPANION_* lines go directly to Kotlin.
        runCatching { installCompanionSink() }
            .onFailure { Log.e(TAG, "installCompanionSink failed", it) }

        val presentation = Presentation(this, displays[0])
        val composeView = ComposeView(presentation.context).apply {
            setContent { CompanionScreen() }
        }

        // Compose needs these owners wired onto the presentation's window decor.
        // EngineActivity is a ComponentActivity (via SDLActivity), so it can serve as all three.
        presentation.window?.decorView?.let { decor ->
            decor.setViewTreeLifecycleOwner(this@EngineActivity)
            decor.setViewTreeViewModelStoreOwner(this@EngineActivity)
            decor.setViewTreeSavedStateRegistryOwner(this@EngineActivity)
        }

        presentation.window?.apply {
            addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
            addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
        }

        presentation.setContentView(composeView)
        runCatching {
            presentation.show()
            companionPresentation = presentation
            Log.d(TAG, "Second-screen: companion UI shown on display ${displays[0].displayId}")
        }.onFailure {
            Log.e(TAG, "Second-screen: show() failed", it)
        }

        // Add/remove a full-screen WindowManager overlay on the bottom-screen
        // Presentation when the in-game pause/options menu opens/closes.
        lifecycleScope.launch {
            GameStateRepository.pauseMenuVisible.collect { visible ->
                if (visible) showPauseOverlay() else hidePauseOverlay()
            }
        }

        // Split-conversation mode: show a read-only conversation-history overlay on the
        // TOP screen while a conversation is active AND the Conversation location is SPLIT
        // or TOP (i.e. anything but BOTTOM). Independent of the Hide UI toggle —
        // conversation is always visible when active.
        UiPreferences.init(applicationContext)

        // Push the "Game cursor" option to native (companionCursorEnabled) so the
        // engine suppresses the top-screen SDL cursor when off. Fires once with the
        // persisted value, then on every toggle.
        lifecycleScope.launch {
            UiPreferences.gameCursorFlow().collect { enabled ->
                runCatching { setCompanionCursorEnabled(enabled) }
                    .onFailure { Log.e(TAG, "setCompanionCursorEnabled failed", it) }
            }
        }

        // Push each "Vanilla HUD Elements" On/Off option to native (companionHud*), so the
        // engine hides/shows the corresponding native top-screen HUD element. Fires once with
        // the persisted value, then on every toggle. "Equipped" gates weapon + spell together.
        val hudPushes: List<Pair<String, (Boolean) -> Unit>> = listOf(
            "hud_vitals" to { on: Boolean -> setCompanionHudHms(on) },
            "hud_equipped" to { on: Boolean -> setCompanionHudEquipped(on) },
            "hud_minimap" to { on: Boolean -> setCompanionHudMinimap(on) },
            "hud_effects" to { on: Boolean -> setCompanionHudEffects(on) },
            "hud_crosshair" to { on: Boolean -> setCompanionHudCrosshair(on) },
            "hud_sneak" to { on: Boolean -> setCompanionHudSneak(on) },
            "hud_enemy" to { on: Boolean -> setCompanionHudEnemy(on) },
        )
        hudPushes.forEach { (key, push) ->
            lifecycleScope.launch {
                UiPreferences.hudOnFlow(key).collect { on ->
                    runCatching { push(on) }.onFailure { Log.e(TAG, "setCompanionHud[$key] failed", it) }
                }
            }
        }

        lifecycleScope.launch {
            combine(
                GameStateRepository.dialogueNpcName,
                GameStateRepository.dialogueHistory,
                GameStateRepository.dialogueTopics,
                GameStateRepository.dialogueServices,
                GameStateRepository.dialogueChoices,
            ) { npc, hist, topics, services, choices ->
                npc.isNotEmpty() || hist.isNotEmpty() || topics.isNotEmpty() ||
                    services.isNotEmpty() || choices.isNotEmpty()
            }.combine(UiPreferences.conversationLocationFlow()) { active, loc ->
                active && loc != ConversationLocation.BOTTOM
            }.combine(UiPreferences.uiModeFlow()) { show, mode ->
                // Suppress the top-screen conversation overlay entirely in Vanilla mode.
                show && mode == UiMode.DS
            }.distinctUntilChanged().collect { show ->
                if (show) showConversationTopOverlay() else hideConversationTopOverlay()
            }
        }
    }

    private fun showConversationTopOverlay() {
        if (conversationTopView != null) return
        // The top-screen overlay lives in THIS activity's own window (Display 0), unlike the
        // pause overlay which lives in the bottom-screen Presentation. Same TYPE_APPLICATION_
        // PANEL technique. FLAG_NOT_FOCUSABLE + FLAG_NOT_TOUCH_MODAL keep it from stealing focus
        // from the game; FLAG_NOT_TOUCHABLE is deliberately omitted so the history panel can
        // receive touch for scrolling (game-area touches above the panel still reach OpenMW).
        // The window token is null until the decor is attached — defer via post.
        val decor = window.decorView
        decor.post {
            if (conversationTopView != null) return@post
            val token = decor.windowToken ?: return@post
            val overlay = ComposeView(this).apply {
                setViewTreeLifecycleOwner(this@EngineActivity)
                setViewTreeViewModelStoreOwner(this@EngineActivity)
                setViewTreeSavedStateRegistryOwner(this@EngineActivity)
                setContent { ConversationHistoryOverlay() }
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply { this.token = token }
            runCatching { windowManager.addView(overlay, lp) }
                .onSuccess { conversationTopView = overlay }
                .onFailure { Log.e(TAG, "CONVERSATION TOP: addView failed", it) }
        }
    }

    private fun hideConversationTopOverlay() {
        val overlay = conversationTopView ?: return
        runCatching { windowManager.removeView(overlay) }
        conversationTopView = null
    }

    private fun showPauseOverlay() {
        if (pauseOverlayView != null) return
        val presentation = companionPresentation ?: return
        val wm = presentation.window?.windowManager ?: return
        val decor = presentation.window?.decorView ?: return
        decor.post {
            if (pauseOverlayView != null) return@post
            val token = decor.windowToken ?: return@post
            val overlay = ComposeView(presentation.context).apply {
                setViewTreeLifecycleOwner(this@EngineActivity)
                setViewTreeViewModelStoreOwner(this@EngineActivity)
                setViewTreeSavedStateRegistryOwner(this@EngineActivity)
                setContent {
                    OptionsMenuOverlay()
                }
            }
            val lp = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_PANEL,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
            ).apply { this.token = token }
            runCatching { wm.addView(overlay, lp) }
                .onSuccess { pauseOverlayView = overlay }
                .onFailure { Log.e(TAG, "PAUSE POC: addView failed", it) }
        }
    }

    private fun hidePauseOverlay() {
        val overlay = pauseOverlayView ?: return
        val wm = companionPresentation?.window?.windowManager
        runCatching { wm?.removeView(overlay) }
        pauseOverlayView = null
    }

}

@SuppressLint("CoroutineCreationDuringComposition")
@Composable
fun AutoMouseModeComposable() {
    var isMouseShown by remember { mutableIntStateOf(SDLActivity.isMouseShown()) }
    // Launch a Choreographer callback to update isMouseShown in real-time
    DisposableEffect(Unit) {
        val choreographer = Choreographer.getInstance()
        val frameCallback = object : Choreographer.FrameCallback {
            override fun doFrame(frameTimeNanos: Long) {
                isMouseShown = SDLActivity.isMouseShown()
                isCursorVisible = isMouseShown
                choreographer.postFrameCallback(this)
            }
        }
        choreographer.postFrameCallback(frameCallback)

        onDispose {
            choreographer.removeFrameCallback(frameCallback)
        }
    }
}

@Composable
fun Buttons(context: Context, containerWidth: Float, containerHeight: Float) {
    val buttonStates by UIStateManager.buttonStates.collectAsState()

    buttonStates.values
        .filter { it.id !in listOf(99, 98, 101, 201) }
        .forEach { button ->
            ResizableDraggableButton(
                context = context,
                id = button.id,
                keyCode = button.keyCode,
                containerWidth = containerWidth,
                containerHeight = containerHeight,
                onDelete = {
                    UIStateManager.removeButtonState(button.id, containerWidth, containerHeight)
                }
            )
        }
}

fun showSystemBars(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
    } else {
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }
}


fun hideSystemBars(activity: Activity) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.window.insetsController?.let {
            it.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
            it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    } else {
        @Suppress("DEPRECATION")
        activity.window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }
}

fun enableScreenStayOn(activity: Activity) {
    activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}

fun disableScreenStayOn(activity: Activity) {
    activity.window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
}
