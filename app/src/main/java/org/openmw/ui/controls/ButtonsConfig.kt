package org.openmw.ui.controls

import android.net.Uri
import android.util.Log
import android.view.KeyEvent
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
        return Uri.parse(decoder.decodeString())
    }
}


object ButtonConfigManager {
    fun getOrCreateParentButton(): ButtonConfig {
        val allButtons = loadAllButtons()
        println("CreateParent: $allButtons")
        val parentButton = allButtons.find { it.type == "parent" }

        return parentButton ?: run {
            // Create new parent button
            val newParentButton = ButtonConfig(
                type = "parent",
                keyCode = KeyEvent.KEYCODE_UNKNOWN,
                label = "Menu",
                position = -1,
                size = 60f,
                offsetX = 100f,
                offsetY = 100f,
                isLocked = true,
                color = "#64f3f8ff",
                alpha = 0.39215687f,
                uri = null
            )

            // Save the new parent button to file
            val updatedButtons = allButtons.toMutableList()
            updatedButtons.add(newParentButton)
            saveButtons(updatedButtons)

            newParentButton
        }
    }

    fun saveParentButton(config: ButtonConfig) {
        val allButtons = loadAllButtons().toMutableList()

        // Remove any existing parent button
        allButtons.removeAll { it.type == "parent" }

        // Add the new parent button
        allButtons.add(config)

        saveButtons(allButtons)
    }

    fun getOrCreateUtilityButtonById(id: Int): ButtonConfig {
        val allButtons = loadAllButtons()
        val utilityButton = allButtons.find { it.type == "utility" && it.id == id }

        return utilityButton ?: run {
            val newUtilityButton = ButtonConfig(
                type = "utility",
                keyCode = KeyEvent.KEYCODE_UNKNOWN,
                label = "Scroll Wheel",
                position = -1,
                id = id,
                size = 50f,
                offsetX = 100f,
                offsetY = 200f,
                isLocked = false,
                color = "#64f3f8ff", // deep sky blue
                alpha = 0.39215687f,
                uri = null,
                group = null
            )

            val updatedButtons = allButtons.toMutableList()
            updatedButtons.add(newUtilityButton)
            saveButtons(updatedButtons)

            newUtilityButton
        }
    }

    fun saveUtilityButtonById(config: ButtonConfig) {
        val allButtons = loadAllButtons().toMutableList()

        // Remove any existing utility button with the same id
        allButtons.removeAll { it.type == "utility" && it.id == config.id }

        allButtons.add(config)
        saveButtons(allButtons)
    }

    fun saveButtons(allConfigs: List<ButtonConfig>) {
        try {
            val json = Json { encodeDefaults = true; prettyPrint = true }.encodeToString(allConfigs)
            val file = File("${userUI}/button_configs.json")
            FileOutputStream(file).use { output ->
                output.write(json.toByteArray())
            }
            println("Saved buttons: $allConfigs")
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
                val json = FileInputStream(file).use { input ->
                    input.bufferedReader().use { it.readText() }
                }
                val buttons = Json.decodeFromString<List<ButtonConfig>>(json)
                println("Loaded buttons: $buttons")
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

    fun getUtilityButtons(): List<ButtonConfig> {
        return loadAllButtons().filter { it.type == "utility" }
    }

    fun getRadialButtons(configs: List<ButtonConfig>): List<ButtonConfig> {
        return filterButtonsByType(configs, "radial")
    }

    fun getParentButtons(configs: List<ButtonConfig>): List<ButtonConfig> {
        return filterButtonsByType(configs, "parent")
    }

    fun getUtilityButtons(configs: List<ButtonConfig>): List<ButtonConfig> {
        return filterButtonsByType(configs, "utility")
    }
}
