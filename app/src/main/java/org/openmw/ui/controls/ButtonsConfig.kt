package org.openmw.ui.controls

import android.net.Uri
import android.util.Log
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import org.openmw.ui.controls.UIStateManager.userUI
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import androidx.core.net.toUri

@Serializable
data class ButtonConfig(
    val type: String,
    val keyCode: Int,
    val label: String = "",
    val position: Int = -1,
    // Optional fields from ButtonState
    val id: Int? = null,
    val size: Float? = null,
    val offsetX: Float? = null,
    val offsetY: Float? = null,
    val isLocked: Boolean? = null,
    val blockMouse: Boolean? = null,
    val color: String,
    val alpha: Float,
    @Serializable(with = UriSerializer::class) val uri: Uri?,
    val group: Int? = null,
    val vibrate: Boolean? = null,
    val isMouseButton: Boolean? = null,
    val mouseButton: Int? = null,
    val buttonTint: Boolean? = null
)

@Serializable
data class UIConfig(
    val buttons: List<ButtonConfig> = emptyList(),
    val soundHaptics: Map<String, HapticEffect> = emptyMap()
)

object UriSerializer : KSerializer<Uri> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Uri", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Uri) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Uri {
        return decoder.decodeString().toUri()
    }
}

object ButtonConfigManager {
    private val jsonFormat = Json {
        encodeDefaults = true
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    fun saveConfig(config: UIConfig) {
        try {
            val jsonString = jsonFormat.encodeToString(config)
            val file = File("${userUI}/button_configs.json")
            FileOutputStream(file).use { output ->
                output.write(jsonString.toByteArray())
            }
        } catch (e: Exception) {
            Log.e("ButtonConfigManager", "Error saving config to file", e)
        }
    }

    fun saveButtons(allConfigs: List<ButtonConfig>) {
        val currentConfig = loadConfig()
        saveConfig(currentConfig.copy(buttons = allConfigs))
    }

    fun updateMultipleButtonsByTypes(types: List<String>, newConfigs: List<ButtonConfig>) {
        val currentConfig = loadConfig()
        val allButtons = currentConfig.buttons.toMutableList()

        // Remove existing buttons of the specified types
        allButtons.removeAll { it.type in types }

        // Add the new buttons
        allButtons.addAll(newConfigs)

        saveConfig(currentConfig.copy(buttons = allButtons))
    }

    fun loadConfig(): UIConfig {
        return try {
            val file = File("${userUI}/button_configs.json")
            if (file.exists()) {
                val jsonString = FileInputStream(file).use { input ->
                    input.bufferedReader().use { it.readText() }
                }
                try {
                    jsonFormat.decodeFromString<UIConfig>(jsonString)
                } catch (e: Exception) {
                    // Backward compatibility: if it's a list, wrap it in UIConfig
                    try {
                        val buttons = jsonFormat.decodeFromString<List<ButtonConfig>>(jsonString)
                        UIConfig(buttons = buttons)
                    } catch (e2: Exception) {
                        Log.e("ButtonConfigManager", "Failed to decode as UIConfig or List<ButtonConfig>", e2)
                        UIConfig()
                    }
                }
            } else {
                UIConfig()
            }
        } catch (e: Exception) {
            Log.e("ButtonConfigManager", "Error loading config from file", e)
            UIConfig()
        }
    }

    fun loadAllButtons(): List<ButtonConfig> {
        return loadConfig().buttons
    }

    // Helper functions
    fun filterButtonsByType(configs: List<ButtonConfig>, type: String): List<ButtonConfig> {
        return configs.filter { it.type == type }
    }
}
