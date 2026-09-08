package com.mahasvan.karen.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.mahasvan.karen.ui.theme.AssistantTheme

private val RestingSize = 16.dp
private val ActiveWidth = 9.3.dp
private val DotPitch = 28.3.dp

/** Per-bar amplitude ceilings, measured from the mockup's bar frame. */
private val PeakHeights = listOf(64.7.dp, 34.0.dp, 54.7.dp, 41.7.dp)
private val BeatDurations = listOf(420, 300, 380, 340)

/**
 * The four brand dots. At rest they are a static row; while [listening] they morph into
 * bars that bounce independently, approximating voice amplitude.
 *
 * [scale] shrinks the whole rig proportionally so the same component can sit inside the
 * 45.dp microphone button as well as standing alone at mockup size.
 */
@Composable
fun VoiceVisualizer(
    listening: Boolean,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
) {
    val transition = rememberInfiniteTransition(label = "voice")
    val brandColors = AssistantTheme.brandColors
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((DotPitch - RestingSize) * scale),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        brandColors.forEachIndexed { index, color ->
            val amplitude by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(BeatDurations[index], easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = StartOffset(index * 90),
                ),
                label = "amplitude$index",
            )
            Bar(
                color = color,
                listening = listening,
                amplitude = amplitude,
                peak = PeakHeights[index] * scale,
                resting = RestingSize * scale,
                activeWidth = ActiveWidth * scale,
            )
        }
    }
}

@Composable
private fun Bar(
    color: Color,
    listening: Boolean,
    amplitude: Float,
    peak: Dp,
    resting: Dp,
    activeWidth: Dp,
) {
    val width by animateDpAsState(
        targetValue = if (listening) activeWidth else resting,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "width",
    )
    val height = if (listening) lerp(resting, peak, amplitude) else resting
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(color, RoundedCornerShape(percent = 50))
    )
}
