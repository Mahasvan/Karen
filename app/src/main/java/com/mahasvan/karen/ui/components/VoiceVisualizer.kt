package com.mahasvan.karen.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.mahasvan.karen.ui.theme.GoogleBrandColors
import androidx.compose.ui.unit.lerp

private val RestingSize = 16.dp
private val ActiveWidth = 9.3.dp
private val DotPitch = 28.3.dp

/** Per-bar amplitude ceilings, measured from the mockup's bar frame. */
private val PeakHeights = listOf(64.7.dp, 34.0.dp, 54.7.dp, 41.7.dp)
private val BeatDurations = listOf(420, 300, 380, 340)

/**
 * The four brand dots. At rest they are a static row; while [listening] they morph into
 * bars that bounce independently, approximating voice amplitude.
 */
@Composable
fun VoiceVisualizer(
    listening: Boolean,
    modifier: Modifier = Modifier,
) {
    val transition = rememberInfiniteTransition(label = "voice")
    Row(
        modifier = modifier.heightIn(min = PeakHeights.max()),
        horizontalArrangement = Arrangement.spacedBy(DotPitch - RestingSize),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GoogleBrandColors.forEachIndexed { index, color ->
            val amplitude by transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(BeatDurations[index], easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                    initialStartOffset = androidx.compose.animation.core.StartOffset(index * 90),
                ),
                label = "amplitude$index",
            )
            Bar(
                color = color,
                listening = listening,
                amplitude = amplitude,
                peak = PeakHeights[index],
            )
        }
    }
}

@Composable
private fun Bar(color: Color, listening: Boolean, amplitude: Float, peak: androidx.compose.ui.unit.Dp) {
    val width by animateDpAsState(
        targetValue = if (listening) ActiveWidth else RestingSize,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "width",
    )
    val height = if (listening) lerp(RestingSize, peak, amplitude) else RestingSize
    Box(
        modifier = Modifier
            .width(width)
            .height(height)
            .background(color, RoundedCornerShape(percent = 50))
    )
}
