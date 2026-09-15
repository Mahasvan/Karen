package com.mahasvan.karen.ui.components
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.times
import androidx.compose.ui.util.lerp
import com.mahasvan.karen.ui.theme.AssistantTheme
import kotlin.math.cos
import kotlin.math.sin

private const val TOP_WIDTH_FRACTION = 0.7f
private const val BLUR_WIDTH_FRACTION = 0.9f
private val BASE_BLUR_RADIUS = 30.dp
private val MIN_BLUR_RADIUS = 10.dp
/** Glow progress at which the layer starts fading in; keeps it hidden until it's already blurred. */

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
 * The strip width stays constant; while [active], the front glow layer eases in
 * (blur radius and opacity ramp up), and eases back out when inactive.
 */

private val TopHalfEllipseShape = GenericShape { size, _ ->
    // The full ellipse is twice as tall as the box; draw only its upper half.
    arcTo(
        rect = Rect(Offset.Zero, Size(size.width, size.height * 2f)),
        startAngleDegrees = 180f,
        sweepAngleDegrees = 180f,
        forceMoveTo = false,
    )
    close()
}

@Composable
fun HalfEllipse(width: Dp, height: Dp, color: Color) {
    Box(
        modifier = Modifier
            // required, not size: overrides the weighted parent's width
            // constraint so this can overdraw past its slot.
            .requiredSize(width = width, height = height)
            .background(color = color, shape = TopHalfEllipseShape)
    )
}

@Composable
fun ColorStripBackground(
    modifier: Modifier = Modifier,
    colors: List<Color>,
    weights: List<Float>,
    heights: List<Float>,
    extraWidth: Dp,
    maxHeight: Dp) {

    // Measure the row so each bar knows its own slot width in Dp — needed to
    // paint its half-ellipse wider than the slot (below).
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        val rowWidth = maxWidth
        Row(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            colors.forEachIndexed { index, color ->
                val slotWidth = rowWidth * weights[index]
                Box(
                    modifier = modifier
                        .weight(weights[index]),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Overdraw past the slot on both sides so blur — which eats
                    // the shape's edge inward — still reads at the intended width.
                    HalfEllipse(
                        width = slotWidth + extraWidth,
                        height = heights[index].coerceIn(0f, 1f) * maxHeight,
                        color = color,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun ColorStripBackgroundPreview() {

    Box(Modifier
        .width(300.dp)
        .height(200.dp)) {
        ColorStripBackground(
            colors = AssistantTheme.brandColors,
            weights = listOf(0.25f, 0.25f, 0.25f, 0.25f),
            heights = listOf(0.3f, 0.5f, 0.3f, 0.5f),
            extraWidth = 10.dp,
            maxHeight = 100.dp
        )
    }
}

@Composable
fun ResizableColorStrip(
    active: Boolean,
    colors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val stripHeight = 5.dp


    val glow by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "glow",
    )
    val blurRadius = lerp(MIN_BLUR_RADIUS, BASE_BLUR_RADIUS, glow)
    val blurAlpha = lerp(0f, 1f, glow)

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

        // Glow layer (front): each bar blurred individually so it glows past the frame.

        Box(
            modifier = Modifier.fillMaxWidth(TOP_WIDTH_FRACTION),
            contentAlignment = Alignment.BottomStart
        ){

            // Base layer (behind): whole strip blurred for a soft halo.
            ColorBarRow(
                colors = colors,
                weights = weights,
                widthFraction = 1f,
                modifier = Modifier
                    .clip(RoundedCornerShape(stripHeight / 2))
                    .blur(blurRadius)
                    .height(stripHeight),
            )

            ColorStripBackground(
                colors = colors,
                weights = weights,
                heights = arrayListOf(0.3f, 0.6f, 0.2f, 0.4f),
                maxHeight = stripHeight * 5,
                extraWidth = 20.dp,
                modifier = Modifier
//                    .offset(y = -stripHeight)
                    .blur(20.dp, BlurredEdgeTreatment.Unbounded)

            )
        }

//        ColorBarRow(
//            colors = colors,
//            weights = weights,
//            widthFraction = BLUR_WIDTH_FRACTION,
//            modifier = Modifier
//                .scale(scaleY = 3f, scaleX = 1f)
//                .alpha(blurAlpha),
//            barModifier = Modifier.blur(
//                blurRadius,
//                edgeTreatment = BlurredEdgeTreatment.Unbounded,
//            ),
//        )
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
            active = false,
            colors = AssistantTheme.brandColors,
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
        )
    }
}
