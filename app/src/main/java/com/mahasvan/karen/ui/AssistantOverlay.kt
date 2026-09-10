package com.mahasvan.karen.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mahasvan.karen.R
import com.mahasvan.karen.assistant.AssistantError
import com.mahasvan.karen.assistant.AssistantState
import com.mahasvan.karen.assistant.AssistantUiState
import com.mahasvan.karen.assistant.ConversationItem
import com.mahasvan.karen.ui.components.AssistantHeroLogo
import com.mahasvan.karen.ui.components.BottomActionBar
import com.mahasvan.karen.ui.components.ConversationStream
import com.mahasvan.karen.ui.components.GlowingLightBar
import com.mahasvan.karen.ui.components.SuggestionGrid
import com.mahasvan.karen.ui.theme.AssistantTheme

private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * Past this width the sheet centres instead of spanning, so it stays thumb-sized
 * on tablets and foldables.
 */
private val SheetMaxWidth = 560.dp

/** Below this height the window is short enough (landscape phone) to need a taller sheet. */
private val ShortWindowHeight = 480.dp

/**
 * Transparent full-screen overlay; only the bottom sheet is occupied. The sheet
 * grows from its resting fraction to a near-full cap once the conversation has
 * content.
 */
@Composable
fun AssistantOverlay(
    uiState: AssistantUiState,
    onMicClick: () -> Unit,
    onKeyboardClick: () -> Unit,
    onSubmit: (String) -> Unit,
    onRetry: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = AssistantTheme.palette

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // A landscape phone has so little height that the mockup's half-screen sheet leaves
        // nothing usable, so the resting sheet takes proportionally more of a short window.
        val shortWindow = maxHeight < ShortWindowHeight
        val restingFraction = if (shortWindow) 0.72f else 0.5f
        val grownFraction = if (shortWindow) 0.95f else 0.9f

        val sheetFraction by animateFloatAsState(
            targetValue = if (uiState.conversation.isEmpty()) restingFraction else grownFraction,
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            label = "sheetFraction",
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Transparent,
                        0.4f to Color.Transparent,
                        1.0f to palette.scrim,
                    )
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClickLabel = stringResource(R.string.overlay_dismiss),
                    onClick = onDismiss,
                )
        )

        AnimatedVisibility(
            visible = true,
            enter = slideInVertically(
                initialOffsetY = { it },
                animationSpec = tween(300, easing = FastOutSlowInEasing),
            ) + fadeIn(tween(300)),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = SheetMaxWidth)
                    .fillMaxWidth()
                    .fillMaxHeight(sheetFraction)
                    .background(palette.sheetSurface, SheetShape)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DragHandle()

                if (uiState.showSuggestionGrid) {
                    SuggestionGrid(
                        onSuggestionClick = onSubmit,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        AssistantHeroLogo(animate = uiState.state != AssistantState.Listening)
                    }
                } else {
                    val isThinking = uiState.state == AssistantState.Processing &&
                        uiState.conversation.lastOrNull() is ConversationItem.UserQuery
                    ConversationStream(
                        conversation = uiState.conversation,
                        partialTranscript = uiState.partialTranscript,
                        isThinking = isThinking,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }

                uiState.error?.let { error ->
                    ErrorRow(error = error, onRetry = onRetry)
                }

                if (uiState.keyboardOpen) {
                    TextInputRow(onSubmit = onSubmit)
                }

                BottomActionBar(
                    state = uiState.state,
                    onLensClick = {},
                    onMicClick = onMicClick,
                    onKeyboardClick = onKeyboardClick,
                    // Bottom spacing comes from the navigationBars inset on the parent Column;
                    // adding a fixed inset here as well double-pads on gesture-nav devices.
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
            }
        }

        AnimatedVisibility(
            visible = uiState.state == AssistantState.Listening,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            GlowingLightBar()
        }
    }
}

@Composable
private fun ErrorRow(error: AssistantError, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = error.message(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
        )
        if (error.recoverable) {
            TextButton(onClick = onRetry) {
                Text(stringResource(R.string.error_retry))
            }
        }
    }
}

@Composable
private fun AssistantError.message(): String = when (this) {
    AssistantError.Network -> stringResource(R.string.error_network)
    is AssistantError.ModelUnavailable -> detail
        ?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.error_model_unavailable)
    is AssistantError.Unknown -> detail
        ?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.error_unknown)
}

@Composable
private fun DragHandle() {
    Box(
        modifier = Modifier
            .padding(vertical = 10.dp)
            .size(width = 36.dp, height = 4.dp)
            .background(AssistantTheme.palette.textSecondary.copy(alpha = 0.3f), CircleShape)
    )
}

@Composable
private fun TextInputRow(onSubmit: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
            placeholder = {
                Text(
                    text = stringResource(R.string.input_placeholder),
                    style = MaterialTheme.typography.bodyLarge,
                    color = AssistantTheme.palette.textSecondary,
                )
            },
            singleLine = true,
            shape = CircleShape,
            colors = TextFieldDefaults.colors(
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        )
        IconButton(
            onClick = {
                onSubmit(text)
                text = ""
            }
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = stringResource(R.string.action_send),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
