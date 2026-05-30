@file:OptIn(DelicateCoroutinesApi::class)

package org.openmw

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.InternalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.libsdl.app.SDLActivity
import org.openmw.ui.controls.ButtonConfigManager
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
import org.openmw.ui.controls.UIStateManager.enableRightThumb
import org.openmw.ui.controls.UIStateManager.gridAlpha
import org.openmw.ui.controls.UIStateManager.gridVisible
import org.openmw.ui.controls.UIStateManager.isCursorVisible
import org.openmw.ui.controls.UIStateManager.isRadialMenuExpanded
import org.openmw.ui.controls.UIStateManager.launchedActivity
import org.openmw.ui.controls.UIStateManager.offsetXFlow
import org.openmw.ui.controls.UIStateManager.offsetYFlow
import org.openmw.ui.controls.UIStateManager.uqmJNI
import org.openmw.ui.controls.VirtualKeyboard
import org.openmw.ui.overlay.ExpandableCircleButton
import org.openmw.ui.overlay.GridOverlay
import org.openmw.ui.overlay.HiddenMenu
import org.openmw.ui.overlay.OverlayUI
import org.openmw.ui.view.BackgroundAnimation
import org.openmw.ui.view.NavmeshScreen
import org.openmw.ui.view.enableLogcat
import org.openmw.utils.DebugOverlayBox
import org.openmw.utils.GameFilesPreferences
import org.openmw.utils.GameFilesPreferences.getENVLine
import org.openmw.utils.GameFilesPreferences.getQuickSlot
import org.openmw.utils.GameFilesPreferences.loadAutoMouseMode
import org.openmw.utils.GameFilesPreferences.readAngle
import org.openmw.utils.GameFilesPreferences.readSPIRV
import org.openmw.utils.patchShaders
import kotlin.math.roundToInt

@Suppress("DEPRECATION")
@InternalCoroutinesApi
@AndroidEntryPoint
class EngineActivity : SDLActivity() {
    private lateinit var sdlView: View
    private external fun getPathToJni(path_global: String, path_user: String)
    external fun initAlpha3()

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
                UIStateManager.loadButtonState(this@EngineActivity, containerWidth, containerHeight)
                ButtonConfigManager.getOrCreateParentButton()
                ButtonConfigManager.getUtilityButtons()

                if (!UIStateManager.useNavmesh) {
                    // Adds Overlay menu for buttons and edit mode
                    composeViewUI.setContent {
                        val autoMouseMode by loadAutoMouseMode(this@EngineActivity).collectAsState(initial = "Hybrid")
                        val quickSlot by getQuickSlot(this@EngineActivity).collectAsState(initial = false)
                        val virtualKeyboard by GameFilesPreferences.useVirtualKeyboard(this@EngineActivity).collectAsState(initial = true)

                        BackHandler {
                            // disable back exit
                            Log.d(TAG, "onGlobalLayout: Back pressed")
                        }

                        if (UIStateManager.tempCodeGroup == "OpenMW") {
                            AutoMouseModeComposable()
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
                            visible = isCursorVisible == 1 && UIStateManager.tempCodeGroup == "OpenMW",
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

                        OverlayUI(
                            context = this@EngineActivity,
                            virtualKeyboard = virtualKeyboard,
                            onKeyEvent = { keyCode -> handleKeyEvent(keyCode) }
                        )

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
                            visible = isCursorVisible == 0 && quickSlot,
                            enter = fadeIn() + expandIn(),
                            exit = fadeOut() + shrinkOut()
                        ) {
                            HiddenMenu(
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

                        AnimatedVisibility(
                            visible = UIStateManager.tempCodeGroup == "OpenMW",
                            enter = fadeIn() + expandIn(),
                            exit = fadeOut() + shrinkOut()
                        ) {
                            Box(modifier = Modifier.wrapContentSize()) {
                                ScrollWheelIndicator(
                                    containerWidth = containerWidth,
                                    containerHeight = containerHeight
                                )
                            }
                        }

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

            Os.setenv("DETHRACE_ROOT_DIR", "${Constants.USER_FILE_STORAGE}/dethrace/", true);

            Os.setenv("HARNESS_OPENGL", "1", true)

            CoroutineScope(Dispatchers.IO).launch {
                patchShaders()
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
        .filter { it.id !in listOf(99, 98) }
        .forEach { button ->
            ResizableDraggableButton(
                context = context,
                id = button.id,
                keyCode = button.keyCode,
                containerWidth = containerWidth,
                containerHeight = containerHeight,
                onDelete = {
                    UIStateManager.removeButtonState(button.id, context, containerWidth, containerHeight)
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
