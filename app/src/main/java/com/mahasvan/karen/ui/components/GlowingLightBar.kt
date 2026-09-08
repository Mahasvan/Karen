package com.mahasvan.karen.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.mahasvan.karen.ui.theme.AssistantTheme

private val GlowHeight = 28.dp
private val MinBarHeight = 4.dp
private val MaxBarHeight = 10.dp

/**
 * The travelling glow pinned to the very bottom edge of the screen while listening.
 * The gradient is repeated across three tile widths so the horizontal sweep never
 * reveals an edge.
 */
@Composable
fun GlowingLightBar(modifier: Modifier = Modifier) {
    val brandColors = AssistantTheme.brandColors
    val transition = rememberInfiniteTransition(label = "glow")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "sweep",
    )
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(560, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )

    val tiled = brandColors + brandColors + brandColors
    val brush = Brush.horizontalGradient(
        colors = tiled,
        startX = -sweep * 1000f,
        endX = (1f - sweep) * 2000f,
    )

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(GlowHeight)
                .alpha(0.35f * (0.6f + 0.4f * pulse))
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, brandColors[0]))
                )
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(lerp(MinBarHeight, MaxBarHeight, pulse))
                .alpha(0.6f + 0.4f * pulse)
                .background(brush)
        )
    }
}
