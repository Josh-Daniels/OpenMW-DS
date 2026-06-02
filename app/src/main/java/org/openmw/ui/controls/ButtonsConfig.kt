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

    fun saveButtons(allConfigs: List<ButtonConfig>) {
        try {
            val jsonString = jsonFormat.encodeToString(allConfigs)
            val file = File("${userUI}/button_configs.json")
            FileOutputStream(file).use { output ->
                output.write(jsonString.toByteArray())
            }
            //println("Saved buttons: $allConfigs")
        } catch (e: Exception) {
            Log.e("ButtonConfigManager", "Error saving buttons to file", e)
        }
    }

    fun updateMultipleButtonsByTypes(types: List<String>, newConfigs: List<ButtonConfig>) {
        val allButtons = loadAllButtons().toMutableList()

        // Remove existing buttons of the specified types
        allButtons.removeAll { it.type in types }

        // Add the new buttons
        allButtons.addAll(newConfigs)

        saveButtons(allButtons)
    }

    fun loadAllButtons(): List<ButtonConfig> {
        return try {
            val file = File("${userUI}/button_configs.json")
            if (file.exists()) {
                val jsonString = FileInputStream(file).use { input ->
                    input.bufferedReader().use { it.readText() }
                }
                val buttons = jsonFormat.decodeFromString<List<ButtonConfig>>(jsonString)
                //println("Loaded buttons: $buttons")
                buttons
            } else {
                println("No button config file found")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e("ButtonConfigManager", "Error loading buttons from file", e)
            emptyList()
        }
    }

    // Helper functions
    fun filterButtonsByType(configs: List<ButtonConfig>, type: String): List<ButtonConfig> {
        return configs.filter { it.type == type }
    }
}
