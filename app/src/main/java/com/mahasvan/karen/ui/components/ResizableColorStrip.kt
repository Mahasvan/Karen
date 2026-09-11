package com.mahasvan.karen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.mahasvan.karen.ui.theme.AssistantTheme

/**
 * A thin horizontal strip split into four contiguous color sections, centered
 * with adjustable width. [edgeSize] is the fraction of the container width the
 * strip occupies (0.8 = 80% width, 1 = full width), driven externally (e.g. by
 * voice intensity during dictation). Internal color proportions are adjustable
 * by dragging the dividers between sections.
 */
@Composable
fun ResizableColorStrip(
    colors: List<Color>,
    modifier: Modifier = Modifier,
    edgeSize: Float = 1f,
) {
    val density = LocalDensity.current
    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    var weights by remember { mutableStateOf(List(colors.size) { 1f / colors.size }) }

    val sizeFraction = edgeSize.coerceIn(0.8f, 1f)
    val stripHeight = 5.dp

    Box(
        modifier = modifier
            .height(stripHeight)
            .onSizeChanged {
                if (containerWidthPx == 0f) containerWidthPx = it.width.toFloat()
            },
    ) {
        if (containerWidthPx > 0f) {
            val stripWidthPx = containerWidthPx * sizeFraction
            Row(
                modifier = Modifier
                    .height(stripHeight)
                    .align(Alignment.Center)
                    .width(with(density) { stripWidthPx.toDp() }),
            ) {
                colors.forEachIndexed { index, color ->
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(weights[index])
                            .background(color),
                    )
                    if (index < colors.size - 1) {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .pointerInput(containerWidthPx) {
                                    detectHorizontalDragGestures { change, dragAmount ->
                                        if (containerWidthPx > 0f) {
                                            val delta = dragAmount / containerWidthPx
                                            weights = weights.toMutableList().also { w ->
                                                val moved = (w[index] + delta)
                                                    .coerceIn(0.05f, w[index] + w[index + 1] - 0.05f)
                                                w[index + 1] += w[index] - moved
                                                w[index] = moved
                                            }
                                        }
                                        change.consume()
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ResizableColorStripPreview() {
    ResizableColorStrip(
        colors = AssistantTheme.brandColors,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Preview(showBackground = true)
@Composable
private fun ResizableColorStripInsetPreview() {
    ResizableColorStrip(
        colors = AssistantTheme.brandColors,
        modifier = Modifier.fillMaxWidth(),
        edgeSize = 0.8f,
    )
}
