package org.openmw.ui.overlay

import android.content.Context
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.openmw.R
import org.openmw.ui.controls.UIStateManager.customColor
import org.openmw.utils.GameFilesPreferences

@Composable
fun getAnimations(): Map<String, Pair<EnterTransition, ExitTransition>> {
    val density = LocalDensity.current

    val slideVerticallyEnter = slideInVertically(
        initialOffsetY = { with(density) { -20.dp.roundToPx() } },
        animationSpec = tween(durationMillis = 1000)
    ) + expandVertically(
        expandFrom = Alignment.Bottom,
        animationSpec = tween(durationMillis = 1000)
    ) + fadeIn(
        initialAlpha = 0.3f,
        animationSpec = tween(durationMillis = 1000)
    )

    val slideVerticallyExit = slideOutVertically(
        targetOffsetY = { with(density) { -20.dp.roundToPx() } },
        animationSpec = tween(durationMillis = 1000)
    ) + shrinkVertically(
        animationSpec = tween(durationMillis = 1000)
    ) + fadeOut(
        animationSpec = tween(durationMillis = 1000)
    )

    val slideHorizontallyEnter = slideInHorizontally(
        initialOffsetX = { with(density) { -200.dp.roundToPx() } },
        animationSpec = tween(durationMillis = 1000)
    ) + fadeIn(
        initialAlpha = 0.3f,
        animationSpec = tween(durationMillis = 1000)
    )

    val slideHorizontallyExit = slideOutHorizontally(
        targetOffsetX = { with(density) { 200.dp.roundToPx() } },
        animationSpec = tween(durationMillis = 1000)
    ) + fadeOut(
        animationSpec = tween(durationMillis = 1000)
    )

    val scaleAnimationEnter = scaleIn(
        initialScale = 0.5f,
        animationSpec = tween(durationMillis = 1000)
    ) + fadeIn(
        initialAlpha = 0.3f,
        animationSpec = tween(durationMillis = 1000)
    )

    val scaleAnimationExit = scaleOut(
        targetScale = 0.5f,
        animationSpec = tween(durationMillis = 1000)
    ) + fadeOut(
        animationSpec = tween(durationMillis = 1000)
    )

    val rotateAnimationEnter = fadeIn(
        initialAlpha = 0.3f,
        animationSpec = tween(durationMillis = 1000)
    )

    val rotateAnimationExit = fadeOut(
        animationSpec = tween(durationMillis = 1000)
    )

    val bounceIn = scaleIn(
        initialScale = 0.5f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
    ) + fadeIn(
        initialAlpha = 0.3f,
        animationSpec = tween(durationMillis = 800)
    )

    val bounceOut = scaleOut(
        targetScale = 0.5f,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
    ) + fadeOut(
        animationSpec = tween(durationMillis = 800)
    )

    val fadeThroughIn = fadeIn(
        initialAlpha = 0.3f,
        animationSpec = tween(durationMillis = 600)
    ) + scaleIn(
        initialScale = 0.8f,
        animationSpec = tween(durationMillis = 600)
    )

    val fadeThroughOut = fadeOut(
        animationSpec = tween(durationMillis = 600)
    ) + scaleOut(
        targetScale = 0.8f,
        animationSpec = tween(durationMillis = 600)
    )

    val delayedSlideIn = slideInVertically(
        initialOffsetY = { with(density) { -20.dp.roundToPx() } },
        animationSpec = tween(durationMillis = 1200)
    ) + fadeIn(
        initialAlpha = 0.3f,
        animationSpec = tween(durationMillis = 1200)
    )

    val delayedSlideOut = slideOutVertically(
        targetOffsetY = { with(density) { 20.dp.roundToPx() } },
        animationSpec = tween(durationMillis = 1200)
    ) + fadeOut(
        animationSpec = tween(durationMillis = 1200)
    )

    val shrinkToCenterIn = scaleIn(
        initialScale = 0.8f,
        animationSpec = tween(durationMillis = 700)
    ) + fadeIn(
        initialAlpha = 0.3f,
        animationSpec = tween(durationMillis = 700)
    )

    val shrinkToCenterOut = scaleOut(
        targetScale = 0.8f,
        animationSpec = tween(durationMillis = 700)
    ) + fadeOut(
        animationSpec = tween(durationMillis = 700)
    )

    return mapOf(
        "Slide Vertically" to (slideVerticallyEnter to slideVerticallyExit),
        "Slide Horizontally" to (slideHorizontallyEnter to slideHorizontallyExit),
        "Scale" to (scaleAnimationEnter to scaleAnimationExit),
        "Rotate" to (rotateAnimationEnter to rotateAnimationExit),
        "Bounce" to (bounceIn to bounceOut),
        "Fade Through" to (fadeThroughIn to fadeThroughOut),
        "Delayed Slide" to (delayedSlideIn to delayedSlideOut),
        "Shrink to Center" to (shrinkToCenterIn to shrinkToCenterOut)
    )
}

@Composable
fun AnimationSettings(context: Context, scope: CoroutineScope) {
    val animations = getAnimations().toMutableMap().apply { put("None", EnterTransition.None to ExitTransition.None) }
    var selectedAnimation by remember { mutableStateOf("None") }
    var expanded by remember { mutableStateOf(false) }

    // Initialize selected animation from DataStore
    LaunchedEffect(Unit) {
        scope.launch {
            GameFilesPreferences.getSelectedAnimation(context).collect { animation ->
                selectedAnimation = animation
            }
        }
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = stringResource(R.string.animation_for_controls), color = Color.White)
            Box(
                modifier = Modifier
                    .clickable { expanded = true }
            ) {
                Text(text = selectedAnimation, modifier = Modifier.padding(8.dp), color = Color.White)
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .background(customColor)
                        .border(1.dp, Color.Black)
                ) {
                    animations.keys.forEach { animation ->
                        DropdownMenuItem(
                            text = { Text(animation, color = Color.White) },
                            onClick = {
                                selectedAnimation = animation
                                expanded = false
                                scope.launch {
                                    // Save the selected animation to DataStore
                                    GameFilesPreferences.setSelectedAnimation(context, animation)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
