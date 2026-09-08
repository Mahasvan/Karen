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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.unit.dp
import com.mahasvan.karen.assistant.AssistantState
import com.mahasvan.karen.assistant.AssistantUiState
import com.mahasvan.karen.ui.components.AssistantHeroLogo
import com.mahasvan.karen.ui.components.BottomActionBar
import com.mahasvan.karen.ui.components.ConversationStream
import com.mahasvan.karen.ui.components.GlowingLightBar
import com.mahasvan.karen.ui.components.SuggestionGrid
import com.mahasvan.karen.ui.theme.AssistantTheme

private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * The whole screen is transparent; only the bottom slice is occupied. The sheet grows
 * from half height to a 90% cap once the conversation has content to show.
 */
@Composable
fun AssistantOverlay(
    uiState: AssistantUiState,
    onMicClick: () -> Unit,
    onKeyboardClick: () -> Unit,
    onSubmit: (String) -> Unit,
    onConfirmCard: (Long) -> Unit,
    onAlarmToggle: (Long, Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = AssistantTheme.palette
    val sheetFraction by animateFloatAsState(
        targetValue = if (uiState.conversation.isEmpty()) 0.5f else 0.9f,
        animationSpec = tween(320, easing = FastOutSlowInEasing),
        label = "sheetFraction",
    )

    Box(modifier = modifier.fillMaxSize()) {
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
                    ConversationStream(
                        conversation = uiState.conversation,
                        partialTranscript = uiState.partialTranscript,
                        onConfirmCard = onConfirmCard,
                        onAlarmToggle = onAlarmToggle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }

                if (uiState.keyboardOpen) {
                    TextInputRow(onSubmit = onSubmit)
                }

                BottomActionBar(
                    state = uiState.state,
                    onLensClick = {},
                    onMicClick = onMicClick,
                    onKeyboardClick = onKeyboardClick,
                    modifier = Modifier.padding(bottom = 31.dp, top = 8.dp),
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
                    text = "Ask Karen",
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
                contentDescription = "Send",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
