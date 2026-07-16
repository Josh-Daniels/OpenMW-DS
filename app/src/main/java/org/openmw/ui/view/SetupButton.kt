package org.openmw.ui.view

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.openmw.ui.theme.SetupOnOrange
import org.openmw.ui.theme.SetupOrange

/**
 * Shared style for the home-screen first-launch setup buttons — Select game files,
 * Select data files, Copy saves / Copy settings from Alpha3. A single component so the
 * whole group reads as one consistent set (same width, height, shape and colour),
 * rather than four independently-styled buttons.
 *
 * Style: thin, rounded, blue-filled so they clearly read as buttons.
 *
 * [flashing] applies the same "pulse" attention animation the old game-files button had
 * (a subtle scale, tuned down so it doesn't overlap its neighbours in the group).
 */
@Composable
fun SetupButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = SetupOnOrange,
    flashing: Boolean = false,
) {
    val transition = rememberInfiniteTransition(label = "setupBtn")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 500), RepeatMode.Reverse),
        label = "pulse",
    )
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth(0.7f)
            .padding(vertical = 3.dp)
            .heightIn(min = 40.dp)
            .then(
                if (flashing) Modifier.graphicsLayer(scaleX = pulse, scaleY = pulse) else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
        colors = ButtonDefaults.buttonColors(containerColor = SetupOrange),
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}
