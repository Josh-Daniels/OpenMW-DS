package org.openmw.utils

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.github.skydoves.colorpicker.compose.AlphaSlider
import com.github.skydoves.colorpicker.compose.BrightnessSlider
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.drawColorIndicator
import com.github.skydoves.colorpicker.compose.rememberColorPickerController
import android.graphics.Color as AndroidColor

fun Color.Companion.fromHex(hexColorCode: String): Color {
    val processedColor = hexColorCode.uppercase().removePrefix("#")

    val colorInt = when (processedColor.length) {

        3 -> { // Short RGB format (#RGB)
            val (r, g, b) = processedColor.map { it.toString().repeat(2) }
            AndroidColor.parseColor("#$r$g$b")
        }

        4 -> { // Short ARGB format (#ARGB)
            val (a, r, g, b) = processedColor.map { it.toString().repeat(2) }
            AndroidColor.parseColor("#$a$r$g$b")
        }

        // Standard RGB or ARGB formats
        6, 8 -> AndroidColor.parseColor("#$processedColor")

        else -> {
            Black.hashCode() // Default color if the input is not hex
        }
    }

    return Color(colorInt)
}

@Composable
fun ColorPickerWheel(initialColor: Color, onColorSelected: (Color, String, Float) -> Unit) {
    val controller = rememberColorPickerController()
    var hexCode by remember { mutableStateOf("") }
    var textColor by remember { mutableStateOf(initialColor) }

    Column(
        modifier = Modifier
            .wrapContentHeight(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HsvColorPicker(
            modifier = Modifier
                .size(200.dp)
                .padding(10.dp),
            controller = controller,
            drawOnPosSelected = {
                drawColorIndicator(
                    controller.selectedPoint.value,
                    controller.selectedColor.value,
                )
            },
            onColorChanged = { colorEnvelope ->
                hexCode = colorEnvelope.hexCode
                textColor = colorEnvelope.color
                onColorSelected(textColor, hexCode, textColor.alpha)
            },
            initialColor = initialColor
        )

        AlphaSlider(
            modifier = Modifier
                .testTag("HSV_AlphaSlider")
                .fillMaxWidth()
                .padding(5.dp)
                .height(15.dp),
            controller = controller,
            wheelColor = Color(202, 165, 96),
            initialColor = initialColor
        )

        BrightnessSlider(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp)
                .height(15.dp),
            controller = controller,
            wheelColor = Color(202, 165, 96),
            initialColor = initialColor
        )
    }
}
