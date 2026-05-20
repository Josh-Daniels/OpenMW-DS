package org.openmw.ui.controls

import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.PaintingStyle
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import org.libsdl.app.SDLControllerManager
import kotlin.math.abs

@Composable
fun GamepadEmulatorScreen() {
    val deviceId = remember { 1384510555 }
    var registered by remember { mutableStateOf(false) }

    fun updateStick(stickId: Int, x: Float, y: Float) {
        if (!registered) {
            registered = true
            val result = SDLControllerManager.nativeAddJoystick(
                deviceId, "Virtual", "Virtual",
                0x045E, 0x028E, false, 0xFFFF, 4, 0b1111, 0, 0
            )
            Log.d("SDL_INIT", "Joystick registration result: $result")
            if (result < 0) {
                Log.e("SDL_INIT", "Failed to register joystick, result: $result")
            }
        }

        val deadzone = 0.05f
        val scale = 1.0f

        val processedX = when {
            abs(x) < deadzone -> 0f
            x > 0 -> (x * scale).coerceAtMost(1f)
            else -> (x * scale).coerceAtLeast(-1f)
        }
        val processedY = when {
            abs(y) < deadzone -> 0f
            y > 0 -> (y * scale).coerceAtMost(1f)
            else -> (y * scale).coerceAtLeast(-1f)
        }

        val axisX = stickId * 2
        val axisY = stickId * 2 + 1

        listOf(
            axisX to processedX,
            axisY to processedY
        ).forEach { (axis, value) ->
            try {
                SDLControllerManager.onNativeJoy(deviceId, axis, value)
                //Log.d("SDL_INPUT", "Sent to axis $axis: $value")
            } catch (e: Exception) {
                Log.e("SDL_INPUT", "Failed to send to axis $axis", e)
            }
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        Joystick(
            stickId = 0,
            onUpdateStick = ::updateStick
        )
        Joystick(
            stickId = 1,
            onUpdateStick = ::updateStick
        )
    }

}

@Composable
fun Joystick(
    stickId: Int,
    onUpdateStick: (Int, Float, Float) -> Unit,
) {
    var initialX by remember { mutableFloatStateOf(0f) }
    var initialY by remember { mutableFloatStateOf(0f) }
    var currentX by remember { mutableFloatStateOf(-1f) }
    var currentY by remember { mutableFloatStateOf(-1f) }
    var down by remember { mutableStateOf(false) }

    Canvas(
        modifier = Modifier
            .size(200.dp)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = {
                        initialX = it.x
                        initialY = it.y
                        currentX = initialX
                        currentY = initialY
                        down = true
                    },
                    onDrag = { change, _ ->
                        currentX = change.position.x
                        currentY = change.position.y
                        //Log.d("JOYSTICK", "Touch: currentX=$currentX, currentY=$currentY")
                    },
                    onDragEnd = {
                        down = false
                        currentX = -1f
                        currentY = -1f
                    }
                )
            }
    ) {
        val strokeWidth2 = with(density) { 2.dp.toPx() }
        val paint = Paint().apply {
            style = PaintingStyle.Stroke
            strokeWidth = strokeWidth2
        }

        if (down) {
            drawCircle(
                color = Color.Gray,
                radius = size.minDimension / 10f,
                center = Offset(initialX, initialY),
                style = Stroke(width = paint.strokeWidth)
            )
            drawCircle(
                color = Color.Gray,
                radius = size.minDimension / 5f,
                center = Offset(currentX, currentY),
                style = Stroke(width = paint.strokeWidth)
            )
        } else {
            drawCircle(
                color = Color.Gray,
                radius = size.minDimension / 2f - paint.strokeWidth,
                center = center,
                style = Stroke(width = paint.strokeWidth)
            )
        }

        if (down) {
            val maxMovement = size.minDimension / 3f
            val diffX = currentX - initialX
            val diffY = currentY - initialY

            val dx = (diffX / maxMovement).coerceIn(-1f, 1f)
            val dy = (diffY / maxMovement).coerceIn(-1f, 1f)

            onUpdateStick(stickId, dx, dy)
        } else {
            onUpdateStick(stickId, 0f, 0f)
        }
    }
}



private var joystickRegistered = false

fun updateStickTest(stickId: Int, x: Float, y: Float) {
    val deviceId = 1384510555
    if (!joystickRegistered) {
        joystickRegistered = true
        val result = SDLControllerManager.nativeAddJoystick(
            deviceId, "Virtual", "Virtual",
            0x045E, 0x028E, false, 0xFFFF, 4, 0b1111, 0, 0
        )
        Log.d("SDL_INIT", "Joystick registration result: $result")
        if (result < 0) {
            Log.e("SDL_INIT", "Failed to register joystick, result: $result")
        }
    }

    val deadzone = 0.05f
    val scale = 1.0f

    val processedX = when {
        abs(x) < deadzone -> 0f
        x > 0 -> (x * scale).coerceAtMost(1f)
        else -> (x * scale).coerceAtLeast(-1f)
    }
    val processedY = when {
        abs(y) < deadzone -> 0f
        y > 0 -> (y * scale).coerceAtMost(1f)
        else -> (y * scale).coerceAtLeast(-1f)
    }

    val axisX = stickId * 2
    val axisY = stickId * 2 + 1

    listOf(
        axisX to processedX,
        axisY to processedY
    ).forEach { (axis, value) ->
        try {
            SDLControllerManager.onNativeJoy(deviceId, axis, value)
            //Log.d("SDL_INPUT", "Sent to axis $axis: $value")
        } catch (e: Exception) {
            Log.e("SDL_INPUT", "Failed to send to axis $axis", e)
        }
    }
}
