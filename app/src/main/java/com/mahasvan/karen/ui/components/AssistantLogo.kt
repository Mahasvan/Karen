package com.mahasvan.karen.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mahasvan.karen.ui.theme.GoogleBlue
import com.mahasvan.karen.ui.theme.GoogleGreen
import com.mahasvan.karen.ui.theme.GoogleRed
import com.mahasvan.karen.ui.theme.GoogleYellow
import kotlin.math.min

private data class LogoDot(val cx: Float, val cy: Float, val r: Float, val color: Color)

/**
 * Circle centres and radii are lifted straight from the mockup SVG and re-originned to
 * the logo's own bounding box, so proportions survive any scaling.
 */
private val SmallLogoViewport = Size(144.11f, 129.73f)
private val SmallLogoDots = listOf(
    LogoDot(39.51f, 39.51f, 39.51f, GoogleBlue),
    LogoDot(103.51f, 58.51f, 19.55f, GoogleRed),
    LogoDot(104.51f, 106.51f, 23.22f, GoogleYellow),
    LogoDot(133.51f, 36.51f, 10.59f, GoogleGreen),
)

private val HeroLogoViewport = Size(278.09f, 249.31f)
private val HeroLogoDots = listOf(
    LogoDot(75.78f, 75.78f, 75.78f, GoogleBlue),
    LogoDot(199.78f, 111.78f, 37.50f, GoogleRed),
    LogoDot(200.78f, 204.78f, 44.53f, GoogleYellow),
    LogoDot(257.78f, 69.78f, 20.31f, GoogleGreen),
)

/** The inline avatar that precedes every assistant response. */
@Composable
fun AssistantAvatar(modifier: Modifier = Modifier, size: Dp = 28.dp) {
    LogoCanvas(SmallLogoDots, SmallLogoViewport, modifier.size(size))
}

/** The large resting "blob", breathing gently while the assistant is idle. */
@Composable
fun AssistantHeroLogo(
    modifier: Modifier = Modifier,
    size: Dp = 80.dp,
    animate: Boolean = true,
) {
    val transition = rememberInfiniteTransition(label = "hero")
    val pulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulse",
    )
    LogoCanvas(
        dots = HeroLogoDots,
        viewport = HeroLogoViewport,
        modifier = modifier.size(size),
        scale = if (animate) pulse else 1f,
    )
}

@Composable
private fun LogoCanvas(
    dots: List<LogoDot>,
    viewport: Size,
    modifier: Modifier,
    scale: Float = 1f,
) {
    Canvas(modifier) {
        val fit = min(size.width / viewport.width, size.height / viewport.height) * scale
        val originX = (size.width - viewport.width * fit) / 2f
        val originY = (size.height - viewport.height * fit) / 2f
        dots.forEach { dot ->
            drawCircle(
                color = dot.color,
                radius = dot.r * fit,
                center = Offset(originX + dot.cx * fit, originY + dot.cy * fit),
            )
        }
    }
}
