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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mahasvan.karen.ui.theme.AssistantTheme
import kotlin.math.cos
import kotlin.math.sin

/**
 * A thin horizontal strip split into four contiguous color sections. The
 * section widths breathe via phase-shifted sine waves so each color takes
 * turns being dominant. The strip is blurred for a soft glow effect.
 */
@Composable
fun ResizableColorStrip(
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val stripHeight = 5.dp
    val stripWidthFraction = 0.7f

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

    // weight =  0.2 sin(i * π/2 + phase) + 0.25
    // +0.25 to ensure all weights are >0 and significant
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
        Row(
            modifier = Modifier
                .height(stripHeight)
                .fillMaxWidth(stripWidthFraction)
                .clip(RoundedCornerShape(stripHeight / 2))
                .blur(50.dp)
        ) {
            colors.forEachIndexed { index, color ->
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .weight(weights[index])
                        .background(color)
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ResizableColorStripPreview() {
    ResizableColorStrip(
        colors = AssistantTheme.brandColors,
        modifier = Modifier.fillMaxWidth()
    )
}
