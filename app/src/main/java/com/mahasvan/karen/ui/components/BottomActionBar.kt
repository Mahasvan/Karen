package com.mahasvan.karen.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mahasvan.karen.R
import com.mahasvan.karen.assistant.AssistantState
import androidx.compose.material3.MaterialTheme
import com.mahasvan.karen.ui.theme.AssistantTheme

private val ButtonSize = 45.dp
private val ButtonGap = 35.dp

/**
 * Lens / microphone / keyboard. The microphone slot swaps to the voice
 * visualizer while listening.
 */
@Composable
fun BottomActionBar(
    state: AssistantState,
    onLensClick: () -> Unit,
    onMicClick: () -> Unit,
    onKeyboardClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGap, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionButton(onClick = onLensClick) {
            Icon(
                painter = painterResource(R.drawable.ic_google_lens),
                contentDescription = stringResource(R.string.action_lens),
                tint = AssistantTheme.palette.actionIconTint,
                modifier = Modifier.size(24.dp),
            )
        }

        ActionButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onMicClick()
            }
        ) {
            AnimatedContent(
                targetState = state == AssistantState.Listening,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith
                        (fadeOut() + scaleOut(targetScale = 0.8f))
                },
                label = "mic",
            ) { listening ->
                if (listening) {
                    VoiceVisualizer(listening = true, scale = 0.28f)
                } else {
                    Icon(
                        painter = painterResource(R.drawable.ic_google_mic),
                        contentDescription = stringResource(R.string.action_speak),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(width = 20.dp, height = 22.dp),
                    )
                }
            }
        }

        ActionButton(onClick = onKeyboardClick) {
            Icon(
                imageVector = Icons.Outlined.Keyboard,
                contentDescription = stringResource(R.string.action_keyboard),
                tint = AssistantTheme.palette.actionIconTint,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun ActionButton(
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(ButtonSize)
            .shadow(3.dp, CircleShape)
            .background(AssistantTheme.palette.actionButton, CircleShape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}
