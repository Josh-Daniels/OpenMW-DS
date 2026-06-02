package org.openmw.utils

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import org.openmw.Constants
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.openmw.ui.controls.UIStateManager

// Singleton instance to avoid multiple DataStore instances for the same file
private var dataStoreInstance: DataStore<Preferences>? = null
private val dataStoreLock = Any()

val Context.dataStore: DataStore<Preferences>
    get() = synchronized(dataStoreLock) {
        dataStoreInstance ?: PreferenceDataStoreFactory.create(
            produceFile = { File(Constants.USER_FILE_STORAGE, "game_files_prefs.preferences_pb") }
        ).also { dataStoreInstance = it }
    }

object GameFilesPreferences {
    val GAME_FILES_URI_KEY = stringPreferencesKey("game_files_uri")
    val NEXUS_API_KEY = stringPreferencesKey("nexus_api_key")
    val IS_NEXUS_PREMIUM = booleanPreferencesKey("nexus_is_premium")
    val IS_NEXUS_VALIDATED = booleanPreferencesKey("nexus_is_validated")
    val UI_HIDDEN_STATE_KEY = stringPreferencesKey("ui_hidden_state")
    val VIBRATION_STATE_KEY = stringPreferencesKey("vibration_state")
    val MATCH_ICON_COLOR_KEY = stringPreferencesKey("match_icon_color")
    val RESOLUTION_X_KEY = intPreferencesKey("resolution_x")
    val RESOLUTION_Y_KEY = intPreferencesKey("resolution_y")
    val ICON_GLOW_KEY = booleanPreferencesKey("icon_glow")
    val ARG_LINE_KEY = stringPreferencesKey("argLine")
    val ENV_LINE_KEY = stringPreferencesKey("envLine")
    val USER_OPTIONS_KEY = stringPreferencesKey("userOptions")
    val AUTO_MOUSE_MODE_KEY = stringPreferencesKey("autoMouseMode")
    val AVOID_16_BITS_KEY = booleanPreferencesKey("avoid16bits")
    val USE_ANGLE = booleanPreferencesKey("enableANGLE")
    val USE_SPIRV = booleanPreferencesKey("enableSPIRV")
    val USE_VIRTUAL_KB = booleanPreferencesKey("virtualKB")
    val TEXTURE_SHRINKING_KEY = stringPreferencesKey("textureShrinking")
    val CODE_GROUP_KEY = stringPreferencesKey("whichGame")
    val BUTTON_GROUP_SWITCH_KEY = booleanPreferencesKey("button_group_switch")
    val SELECTED_ANIMATION_KEY = stringPreferencesKey("selected_animation")
    val SELECTED_MOUSE_KEYS = stringPreferencesKey("selected_mouse")
    val ALLOWED_TO_TEXT_EDITOR = stringPreferencesKey("allowed_to_edit")
    val NEW_FEATURE_ENABLED_KEY = booleanPreferencesKey("new_feature_enabled")
    val TRANSLATION_ENABLED_KEY = booleanPreferencesKey("translation_enabled")
    private val _gameFilesUri = MutableStateFlow<String?>(null)
    val BACKGROUND_ANIMATION_KEY = stringPreferencesKey("background_animation")
    val LANGUAGE_KEY = stringPreferencesKey("language")
    val SUPPORTED_LANGUAGES = stringPreferencesKey("supported_languages")
    val WHATS_NEW_KEY = booleanPreferencesKey("whats_new")
    val AUTO_RUN = booleanPreferencesKey("auto_run")
    val HIDE_SYSTEM_BARS = booleanPreferencesKey("hide_system_bars")
    val SCREEN_STAY_ON = booleanPreferencesKey("screen_stay_on")
    val TUTORIAL_KEY = booleanPreferencesKey("tutorial_enable")
    val MENU_CORNER_KEY = intPreferencesKey("menu_corner")

    val USER_SET_GPU_KEY = stringPreferencesKey("user_set_gpu")
    val USER_SET_TEMP_KEY = stringPreferencesKey("user_set_temp")
    val USER_SET_GPU_TEMP_KEY = stringPreferencesKey("user_set_gpu_temp")

    val KEYBOARD_BACKLIGHT = floatPreferencesKey("keyboard_backlight")
    val KEYBOARD_THEME = stringPreferencesKey("keyboard_theme")
    val KEYBOARD_WIDTH = floatPreferencesKey("keyboard_width")
    val KEYBOARD_HEIGHT = floatPreferencesKey("keyboard_height")

    // Android 15 bug where game files not detected when launching
    val BYPASS_GAME_FILES_KEY = booleanPreferencesKey("bypass_game_files_check")

    // Developer Options
    val AVOID_RESOLUTION_INSERTION = booleanPreferencesKey("avoidInsertion")

    // Mouse Settings
    val OFFSET_X_MOUSE = floatPreferencesKey("offset_x_mouse")
    val OFFSET_Y_MOUSE = floatPreferencesKey("offset_y_mouse")
    val SENSITIVITY_MOUSE = floatPreferencesKey("sensitivity_mouse")

    val SENSITIVITY_RT = floatPreferencesKey("sensitivity_right_thumb")

    val BUTTON_SHAPE = stringPreferencesKey("button_shape")

    // Enable Virtual Left Thumbstick
    val VIRTUAL_LEFT_THUMBSTICK = booleanPreferencesKey("enable_virtual_left_thumbstick")
    val VIRTUAL_RIGHT_THUMBSTICK = booleanPreferencesKey("enable_virtual_right_thumbstick")

    fun getNexusAPIFlow(context: Context): Flow<String?> {
        return context.dataStore.data.map { settings ->
            settings[NEXUS_API_KEY]
        }
    }

    fun loadIsPremiumTier(context: Context): Flow<Boolean?> {
        return context.dataStore.data
            .map { preferences ->
                preferences[IS_NEXUS_PREMIUM]
            }
    }

    fun getVirtualRightThumbstick(context: Context): Flow<Boolean> {
        return context.dataStore.data
            .map { preferences ->
                preferences[VIRTUAL_RIGHT_THUMBSTICK] ?: false
            }
    }

    suspend fun writeVirtualRightThumbstick(context: Context, enableVirtualRight: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[VIRTUAL_RIGHT_THUMBSTICK] = enableVirtualRight
        }
    }

    fun getVirtualLeftThumbstick(context: Context): Flow<Boolean> {
        return context.dataStore.data
            .map { preferences ->
                preferences[VIRTUAL_LEFT_THUMBSTICK] ?: true
            }
    }

    suspend fun writeVirtualLeftThumbstick(context: Context, enableVirtualLeft: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[VIRTUAL_LEFT_THUMBSTICK] = enableVirtualLeft
        }
    }

    fun initialize(context: Context) {
        // Collect DataStore changes to update UIStateManager.languageSet
        CoroutineScope(Dispatchers.Main).launch {
            context.dataStore.data
                .map { preferences ->
                    preferences[LANGUAGE_KEY] ?: "en"
                }
                .collect { newLanguage ->
                    if (UIStateManager.languageSet != newLanguage) {
                        Log.d("LanguageDebug", "Updating UIStateManager.languageSet to: $newLanguage")
                        UIStateManager.languageSet = newLanguage
                    }
                }
        }

        CoroutineScope(Dispatchers.Main).launch {
            context.dataStore.data.collect { preferences ->
                UIStateManager.userSetGPU = preferences[USER_SET_GPU_KEY] ?: "kgsl-3d0"
                UIStateManager.userSetTemp = preferences[USER_SET_TEMP_KEY] ?: "thermal_zone0"
                UIStateManager.userSetGPUTemp = preferences[USER_SET_GPU_TEMP_KEY] ?: "thermal_zone32"
            }
        }
    }

    suspend fun setOffsetXMouse(context: Context, offsetX: Float) {
        context.dataStore.edit { preferences ->
            preferences[OFFSET_X_MOUSE] = offsetX
        }
    }

    suspend fun setOffsetYMouse(context: Context, offsetY: Float) {
        context.dataStore.edit { preferences ->
            preferences[OFFSET_Y_MOUSE] = offsetY
        }
    }

    fun getOffsetXMouse(context: Context): Flow<Float?> {
        return context.dataStore.data.map { preferences ->
            preferences[OFFSET_X_MOUSE]
        }
    }

    fun getOffsetYMouse(context: Context): Flow<Float?> {
        return context.dataStore.data.map { preferences ->
            preferences[OFFSET_Y_MOUSE]
        }
    }

    suspend fun setSensitivityMouse(context: Context, sensitivity: Float) {
        context.dataStore.edit { preferences ->
            preferences[SENSITIVITY_MOUSE] = sensitivity
        }
    }

    fun getSensitivityMouse(context: Context): Flow<Float?> {
        return context.dataStore.data.map { preferences ->
            preferences[SENSITIVITY_MOUSE]
        }
    }

    suspend fun setSensitivityRT(context: Context, sensitivityRT: Float) {
        context.dataStore.edit { preferences ->
            preferences[SENSITIVITY_RT] = sensitivityRT
        }
    }

    fun getSensitivityRT(context: Context): Flow<Float?> {
        return context.dataStore.data.map { preferences ->
            preferences[SENSITIVITY_RT]
        }
    }

    suspend fun setLanguage(context: Context, language: String) {
        context.dataStore.edit { preferences ->
            preferences[LANGUAGE_KEY] = language
            println("DataStore set to: $language")
        }
    }

    fun getLanguageFlow(context: Context): StateFlow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[LANGUAGE_KEY] ?: "en"
        }.stateIn(
            scope = CoroutineScope(Dispatchers.Main),
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = "en"
        )
    }

    suspend fun saveButtonShape(context: Context, buttonShape: String) {
        context.dataStore.edit { preferences ->
            preferences[BUTTON_SHAPE] = buttonShape
        }
    }

    fun loadButtonShape(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[BUTTON_SHAPE] ?: "CircleShape"
        }
    }

    suspend fun setSupportedLanguages(context: Context, languages: String) {
        context.dataStore.edit { preferences ->
            preferences[SUPPORTED_LANGUAGES] = languages
            println("Supported languages set to: $languages")
        }
    }

    fun getSupportedLanguagesFlow(context: Context): Flow<List<String>> {
        return context.dataStore.data.map { preferences ->
            preferences[SUPPORTED_LANGUAGES]?.split(",")?.map { it.trim() } ?: run {
                val defaultLanguages = "en,ru,hr,fr,es,de,it,pt,ja,zh,ar,hi,pl,uk,vi,ko,th,bn,ta,ur,cs"
                context.dataStore.edit { prefs ->
                    prefs[SUPPORTED_LANGUAGES] = defaultLanguages
                    println("Initialized supported languages: $defaultLanguages")
                }
                defaultLanguages.split(",").map { it.trim() }
            }
        }
    }

    fun getGameFilesUri(context: Context): String? {
        val dataStoreKey = GAME_FILES_URI_KEY
        val dataStore = context.dataStore
        return runBlocking {
            val preferences = dataStore.data.first()
            preferences[dataStoreKey]?.also {
                _gameFilesUri.value = it
            }
        }
    }

    fun getGameFilesUriState(context: Context): Flow<String?> {
        val dataStoreKey = GAME_FILES_URI_KEY
        val dataStore = context.dataStore
        return dataStore.data.map { preferences ->
            preferences[dataStoreKey]
        }
    }

    suspend fun storeGameFilesPath(context: Context, path: String) {
        context.dataStore.edit { preferences ->
            preferences[GAME_FILES_URI_KEY] = path
        }
        _gameFilesUri.value = path
    }

    // Synchronous function to get env line string from DataStore
    fun getENVLine(context: Context): String? {
        return runBlocking {
            context.dataStore.data.first()[ENV_LINE_KEY]
        }
    }

    // Function to save env line string to DataStore
    suspend fun saveENVLine(context: Context, envLine: String) {
        context.dataStore.edit { preferences ->
            preferences[ENV_LINE_KEY] = envLine
        }
    }

    suspend fun saveResolutionX(context: Context, resolutionX: Int) {
        context.dataStore.edit { preferences ->
            preferences[RESOLUTION_X_KEY] = resolutionX
        }
    }

    suspend fun saveResolutionY(context: Context, resolutionY: Int) {
        context.dataStore.edit { preferences ->
            preferences[RESOLUTION_Y_KEY] = resolutionY
        }
    }

    suspend fun saveUIState(context: Context, isHidden: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[UI_HIDDEN_STATE_KEY] = isHidden.toString()
        }
    }

    fun loadUIState(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[UI_HIDDEN_STATE_KEY]?.toBoolean() ?: false
        }
    }

    suspend fun saveVibrationState(context: Context, isHidden: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[VIBRATION_STATE_KEY] = isHidden.toString()
        }
    }

    fun loadVibrationState(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[VIBRATION_STATE_KEY]?.toBoolean() ?: true
        }
    }

    suspend fun saveMatchIconColorState(context: Context, isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[MATCH_ICON_COLOR_KEY] = isEnabled.toString()
        }
    }

    fun loadMatchIconColorState(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[MATCH_ICON_COLOR_KEY]?.toBoolean() ?: false
        }
    }
    suspend fun getAllPreferences(context: Context): Map<String, String> {
        val preferences = context.dataStore.data.first()
        return preferences.asMap().mapKeys { it.key.name }.mapValues { it.value.toString() }
    }

    suspend fun saveIconGlow(context: Context, iconGlow: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[ICON_GLOW_KEY] = iconGlow
        }
    }

    fun loadIconGlow(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[ICON_GLOW_KEY] ?: true
        }
    }

    suspend fun saveBypassGameCheck(context: Context, byPass: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BYPASS_GAME_FILES_KEY] = byPass
        }
    }

    fun loadBypassGameCheck(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[BYPASS_GAME_FILES_KEY] ?: false
        }
    }

    suspend fun saveARGLine(context: Context, argLine: String) {
        context.dataStore.edit { preferences ->
            preferences[ARG_LINE_KEY] = argLine
        }
    }

    fun getARGLine(context: Context): Flow<String?> {
        return context.dataStore.data.map { settings ->
            settings[ARG_LINE_KEY]
        }
    }

    suspend fun saveUserOptions(context: Context, options: Set<String>) {
        val existingOptions = getUserOptions(context).first().filterNot { it.isEmpty() }
        val allOptions = (existingOptions + options).filterNot { it.isEmpty() }
        context.dataStore.edit { preferences ->
            preferences[USER_OPTIONS_KEY] = allOptions.joinToString(",")
        }
    }

    fun getUserOptions(context: Context): Flow<Set<String>> {
        return context.dataStore.data.map { preferences ->
            preferences[USER_OPTIONS_KEY]?.split(",")?.filterNot { it.isEmpty() }?.toSet() ?: emptySet()
        }
    }

    suspend fun deleteUserOption(context: Context, option: String) {
        val existingOptions = getUserOptions(context).first()
        val updatedOptions = existingOptions - option
        context.dataStore.edit { preferences ->
            preferences[USER_OPTIONS_KEY] = updatedOptions.joinToString(",")
        }
    }

    suspend fun saveAutoMouseMode(context: Context, autoMouseMode: String) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_MOUSE_MODE_KEY] = autoMouseMode
        }
    }

    fun loadAutoMouseMode(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[AUTO_MOUSE_MODE_KEY] ?: "Hybrid"
        }
    }

    fun readAvoid16Bits(context: Context): Flow<Boolean> {
        return context.dataStore.data
            .map { preferences ->
                preferences[AVOID_16_BITS_KEY] ?: true
            }
    }

    suspend fun writeAvoid16Bits(context: Context, avoid16bits: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AVOID_16_BITS_KEY] = avoid16bits
        }
    }

    fun readSPIRV(context: Context): Flow<Boolean> {
        return context.dataStore.data
            .map { preferences ->
                preferences[USE_SPIRV] ?: false
            }
    }

    suspend fun writeSPIRV(context: Context, useSPIRV: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_SPIRV] = useSPIRV
        }
    }

    fun readAngle(context: Context): Flow<Boolean> {
        return context.dataStore.data
            .map { preferences ->
                preferences[USE_ANGLE] ?: false
            }
    }

    suspend fun writeAngle(context: Context, useAngle: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_ANGLE] = useAngle
        }
    }

    fun useVirtualKeyboard(context: Context): Flow<Boolean> {
        return context.dataStore.data
            .map { preferences ->
                preferences[USE_VIRTUAL_KB] ?: true
            }
    }

    suspend fun setVirtualKeyboard(context: Context, virtualKB: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USE_VIRTUAL_KB] = virtualKB
        }
    }

    fun readResolutionInsertion(context: Context): Flow<Boolean> {
        return context.dataStore.data
            .map { preferences ->
                preferences[AVOID_RESOLUTION_INSERTION] ?: false
            }
    }

    suspend fun writeResolutionInsertion(context: Context, avoidInsertion: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AVOID_RESOLUTION_INSERTION] = avoidInsertion
        }
    }

    fun readTextureShrinkingOption(context: Context): Flow<String> {
        return context.dataStore.data
            .map { preferences ->
                preferences[TEXTURE_SHRINKING_KEY] ?: "None"
            }
    }

    suspend fun writeTextureShrinkingOption(context: Context, option: String) {
        context.dataStore.edit { preferences ->
            preferences[TEXTURE_SHRINKING_KEY] = option
        }
    }

    fun readCodeGroup(context: Context): Flow<String> {
        return context.dataStore.data
            .map { preferences ->
                preferences[CODE_GROUP_KEY] ?: "OpenMW"
            }
    }

    suspend fun setCodeGroup(context: Context, option: String) {
        context.dataStore.edit { preferences ->
            preferences[CODE_GROUP_KEY] = option
        }
    }

    fun getButtonGroupSwitch(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[BUTTON_GROUP_SWITCH_KEY] ?: false
        }
    }

    suspend fun setButtonGroupSwitch(context: Context, value: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[BUTTON_GROUP_SWITCH_KEY] = value
        }
    }

    fun getSelectedAnimation(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[SELECTED_ANIMATION_KEY] ?: "None"
        }
    }

    suspend fun setSelectedAnimation(context: Context, animation: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_ANIMATION_KEY] = animation
        }
    }

    fun getSelectedKeycodes(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[SELECTED_MOUSE_KEYS] ?: "54,111"
        }
    }

    suspend fun setSelectedKeycodes(context: Context, animation: String) {
        context.dataStore.edit { preferences ->
            preferences[SELECTED_MOUSE_KEYS] = animation
        }
    }

    fun getExtensionAllowedToEdit(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[ALLOWED_TO_TEXT_EDITOR] ?: "sh,txt,db,xml,cfg,json,py,yaml,lua,omwscripts,log,frag,vert,layout,glsl,omwfx"
        }
    }

    suspend fun setExtensionAllowedToEdit(context: Context, extensions: String) {
        context.dataStore.edit { preferences ->
            preferences[ALLOWED_TO_TEXT_EDITOR] = extensions
        }
    }

    suspend fun saveNewFeatureEnabledState(context: Context, isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NEW_FEATURE_ENABLED_KEY] = isEnabled
        }
    }

    fun loadNewFeatureEnabledState(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[NEW_FEATURE_ENABLED_KEY] ?: false
        }
    }

    suspend fun saveTranslationState(context: Context, isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TRANSLATION_ENABLED_KEY] = isEnabled
        }
    }

    fun loadTranslationState(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[TRANSLATION_ENABLED_KEY] ?: false
        }
    }

    fun getBackgroundAnimationFlow(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[BACKGROUND_ANIMATION_KEY] ?: "BouncingBackground"
        }
    }

    suspend fun setBackgroundAnimation(context: Context, animation: String) {
        context.dataStore.edit { preferences ->
            preferences[BACKGROUND_ANIMATION_KEY] = animation
        }
    }

    suspend fun setWhatsNew(context: Context, isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[WHATS_NEW_KEY] = isEnabled
        }
    }

    fun getWhatsNew(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[WHATS_NEW_KEY] != false
        }
    }

    suspend fun setAutoRun(context: Context, isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_RUN] = isEnabled
        }
    }

    fun getAutoRun(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[AUTO_RUN] != false
        }
    }

    suspend fun setSystemBars(context: Context, isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[HIDE_SYSTEM_BARS] = isEnabled
        }
    }

    fun getSystemBars(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[HIDE_SYSTEM_BARS] != true
        }
    }

    suspend fun setScreenStayOn(context: Context, isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SCREEN_STAY_ON] = isEnabled
        }
    }

    fun getScreenStayOn(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[SCREEN_STAY_ON] != true
        }
    }

    suspend fun setTutorial(context: Context, isEnabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[TUTORIAL_KEY] = isEnabled
        }
    }

    fun getTutorial(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[TUTORIAL_KEY] != false
        }
    }

    suspend fun setMenuCorner(context: Context, corner: Int) {
        context.dataStore.edit { preferences ->
            preferences[MENU_CORNER_KEY] = corner
        }
    }

    fun getMenuCorner(context: Context): Flow<Int> {
        return context.dataStore.data.map { preferences ->
            preferences[MENU_CORNER_KEY] ?: 0 // Default to TopRight
        }
    }

    suspend fun setUserSetGPU(context: Context, value: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_SET_GPU_KEY] = value
        }
    }

    suspend fun setUserSetTemp(context: Context, value: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_SET_TEMP_KEY] = value
        }
    }

    suspend fun setUserSetGPUTemp(context: Context, value: String) {
        context.dataStore.edit { preferences ->
            preferences[USER_SET_GPU_TEMP_KEY] = value
        }
    }

    fun getKeyboardBacklight(context: Context): Flow<Float> = context.dataStore.data.map { it[KEYBOARD_BACKLIGHT] ?: 1f }

    suspend fun setKeyboardBacklight(context: Context, value: Float) {
        context.dataStore.edit { it[KEYBOARD_BACKLIGHT] = value }
    }

    fun getKeyboardTheme(context: Context): Flow<String> = context.dataStore.data.map { it[KEYBOARD_THEME] ?: "lightMode" }

    suspend fun setKeyboardTheme(context: Context, value: String) {
        context.dataStore.edit { it[KEYBOARD_THEME] = value }
    }

    fun getKeyboardWidth(context: Context): Flow<Float?> = context.dataStore.data.map { it[KEYBOARD_WIDTH] }

    suspend fun setKeyboardWidth(context: Context, value: Float) {
        context.dataStore.edit { it[KEYBOARD_WIDTH] = value }
    }

    fun getKeyboardHeight(context: Context): Flow<Float?> = context.dataStore.data.map { it[KEYBOARD_HEIGHT] }

    suspend fun setKeyboardHeight(context: Context, value: Float) {
        context.dataStore.edit { it[KEYBOARD_HEIGHT] = value }
    }
}
