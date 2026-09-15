package com.mahasvan.karen.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mahasvan.karen.ui.theme.AssistantTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * A thin horizontal strip split into four contiguous color bars whose widths
 * breathe via phase-shifted sine waves, so each color takes turns being
 * dominant.
 *
 * It stacks two blurred layers sharing the same bar geometry:
 *  - a base layer (behind) blurred as a whole for a soft, unified halo, and
 *  - a glow layer (front) where every color bar is blurred individually with an
 *    unbounded edge, so each color casts its own glow that spills out past the
 *    strip's frame.
 *
 * [glowStrength] scales the glow layer's opacity — intended to track speech
 * volume via onRmsChanged. [glowRadius] sets the per-bar glow spread and
 * [glowWidthFraction] the glow layer's width relative to the container.
 */
@Composable
fun ResizableColorStrip(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    glowStrength: Float = 1f,
    glowRadius: Dp = 24.dp,
    glowWidthFraction: Float = 0.7f,
) {
    val stripHeight = 5.dp
    val stripWidthFraction = 0.7f
    val baseBlurRadius = 30.dp

    // Phase animates 0 to 2π and loops every 8 seconds.
    val transition = rememberInfiniteTransition(label = "strip")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000),
            repeatMode = RepeatMode.Restart,
        ),
        label = "phase",
    )

    // Each color takes turns being dominant.
    // weight = 0.25 + 0.2 * sin(i * π/2 + phase); weights always sum to 1.
    val sinx = sin(phase)
    val cosx = cos(phase)
    val amplitude = 0.2f
    val weights = listOf(
        0.25f + amplitude * cosx,
        0.25f - amplitude * sinx,
        0.25f + amplitude * sinx,
        0.25f - amplitude * cosx,
    )

    Box(
        modifier = modifier.height(stripHeight),
        contentAlignment = Alignment.Center,
    ) {
        // Base layer (behind): whole strip blurred for a soft halo.
        ColorBarRow(
            colors = colors,
            weights = weights,
            widthFraction = stripWidthFraction,
            modifier = Modifier
                .clip(RoundedCornerShape(stripHeight / 2))
                .blur(baseBlurRadius),
        )

        // Glow layer (front): each bar blurred individually so it glows past the frame.
        ColorBarRow(
            colors = colors,
            weights = weights,
            widthFraction = glowWidthFraction.coerceIn(0f, 1f),
            modifier = Modifier.alpha(glowStrength.coerceIn(0f, 1f)),
            barModifier = Modifier.blur(
                glowRadius,
                edgeTreatment = BlurredEdgeTreatment.Unbounded,
            ),
        )
    }
}

/**
 * A full-height row of contiguous color bars sized by [weights]. [barModifier]
 * is applied to each individual bar (e.g. a per-bar blur for the glow layer).
 */
@Composable
private fun ColorBarRow(
    colors: List<Color>,
    weights: List<Float>,
    widthFraction: Float,
    modifier: Modifier = Modifier,
    barModifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxHeight()
            .fillMaxWidth(widthFraction),
    ) {
        colors.forEachIndexed { index, color ->
            Box(
                modifier = barModifier
                    .fillMaxHeight()
                    .weight(weights[index])
                    .background(color)
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF000000)
@Composable
private fun ResizableColorStripPreview() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .padding(vertical = 48.dp),
        contentAlignment = Alignment.Center,
    ) {
        ResizableColorStrip(
            colors = AssistantTheme.brandColors,
            modifier = Modifier.fillMaxWidth(),
            glowWidthFraction = 1f
        )
    }
}
