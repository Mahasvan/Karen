// referenced from: https://github.com/google-ai-edge/gallery/blob/main/Android/src/app/src/main/java/com/google/ai/edge/gallery/ui/common/BufferedFadingMarkdownText.kt

package com.mahasvan.karen.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import com.halilibo.richtext.commonmark.Markdown
import com.halilibo.richtext.ui.CodeBlockStyle
import com.halilibo.richtext.ui.RichTextStyle
import com.halilibo.richtext.ui.material3.RichText
import com.halilibo.richtext.ui.string.RichTextStringStyle
import com.mahasvan.karen.ui.theme.AssistantTheme
import kotlinx.coroutines.android.awaitFrame
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate

private const val FADE_INTERVAL_MS = 250

/**
 * Displays Markdown text with a smooth fading transition when the text content changes.
 *
 * Two layers of [MarkdownText] are used. When the input `text` changes:
 * 1. The new text is rendered on a hidden overlay layer (`text2`).
 * 2. The overlay layer is smoothly faded in.
 * 3. Once the fade-in is complete, the base layer (`text1`) is updated to the new text.
 * 4. The overlay layer is instantly hidden, ready for the next text update.
 *
 * Adapted from the gallery reference's BufferedFadingMarkdownText.
 */
@Composable
fun BufferedFadingMarkdownText(text: String, inProgress: Boolean, modifier: Modifier = Modifier) {
    var text1 by remember { mutableStateOf(text) }
    var text2 by remember { mutableStateOf("") }
    val alpha2 = remember { Animatable(0f) }
    val currentText by rememberUpdatedState(text)
    var showOverlay by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        snapshotFlow { currentText }
            .conflate()
            .collect { newText ->
                if (newText == text1) return@collect

                // Set the new text onto the hidden overlay layer
                text2 = newText
                alpha2.snapTo(0f)

                // Smoothly fade the new layout in.
                // Text1 remains 100% solid, unchanged, and stationary underneath.
                alpha2.animateTo(
                    1f,
                    animationSpec = tween(FADE_INTERVAL_MS, easing = LinearOutSlowInEasing),
                )

                // Swap the background text to match the new text instantly.
                text1 = newText

                // Wait a frame for the overlay layer to fully lay out and draw.
                val unused = awaitFrame()

                // Instantly hide the overlay so we don't double-render the text.
                alpha2.snapTo(0f)
            }
    }

    val previousInProgress = rememberUpdatedState(inProgress)
    LaunchedEffect(inProgress) {
        // Trigger only when inProgress changes from true to false.
        if (previousInProgress.value && !inProgress) {
            delay(FADE_INTERVAL_MS.toLong() * 2)
            showOverlay = false
        }
    }

    Box(modifier = modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }) {
        // LAYER 1: Base text.
        //
        // The alpha of the base text is 1f - alpha2.value. As alpha2 animates from 0f to 1f,
        // the base text fades out from full opacity (1f) to transparent (0f).
        // This works in conjunction with BlendMode.Plus on the overlay layer:
        // As the overlay fades in, the base text fades out. Since the sum of their alphas is always 1,
        // and BlendMode.Plus adds the colors, the transition appears as a smooth crossfade
        // without any intermediate darkening or color artifacts.
        SelectionContainer {
            MarkdownText(text = text1, modifier = Modifier.graphicsLayer { alpha = 1f - alpha2.value })
        }

        // LAYER 2: Overlay text.
        if (showOverlay) {
            MarkdownText(
                text = text2,
                modifier =
                // Ignore this layer in terms of accessibility semantics (won't be read by screen
                // readers).
                Modifier.clearAndSetSemantics {}
                    .graphicsLayer {
                        alpha = alpha2.value
                        // Use BlendMode.Plus to ensure that when alpha2 is between 0 and 1,
                        // the overlay text is added on top of the base text without
                        // darkening or other complex blending effects.
                        this.blendMode = BlendMode.Plus
                    },
            )
        }
    }
}

/** Composable function to display Markdown-formatted text. */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = AssistantTheme.palette.textPrimary,
    linkColor: Color = MaterialTheme.colorScheme.primary,
) {
    val fontSize = MaterialTheme.typography.bodyLarge.fontSize
    CompositionLocalProvider {
        ProvideTextStyle(
            value = TextStyle(
                fontSize = fontSize,
                lineHeight = fontSize * 1.5f,
                color = textColor,
                letterSpacing = 0.2.sp,
            )
        ) {
            RichText(
                modifier = modifier,
                style = RichTextStyle(
                    codeBlockStyle = CodeBlockStyle(
                        textStyle = TextStyle(
                            fontSize = MaterialTheme.typography.bodySmall.fontSize,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = MaterialTheme.typography.bodySmall.fontSize * 1.4f,
                        )
                    ),
                    stringStyle = RichTextStringStyle(linkStyle = TextLinkStyles(style = SpanStyle(color = linkColor))),
                ),
            ) {
                Markdown(content = text)
            }
        }
    }
}
