package com.mahasvan.karen.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mahasvan.karen.R
import com.mahasvan.karen.assistant.AssistantState
import com.mahasvan.karen.assistant.InputMethod
import com.mahasvan.karen.ui.theme.AppTheme
import com.mahasvan.karen.ui.theme.AssistantTheme

private val PrimaryButtonSize = 64.dp
private val SecondaryButtonSize = 48.dp

/**
 * Two buttons: a large center button showing the last-used input method
 * (mic or keyboard), and a small button in the far left corner to switch
 * to the other method.
 */
@Composable
fun BottomActionBar(
    state: AssistantState,
    lastInputMethod: InputMethod,
    onMicClick: () -> Unit,
    onKeyboardClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // Secondary button: far left corner, smaller.
        PrimaryButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (lastInputMethod == InputMethod.Voice) onKeyboardClick() else onMicClick()
            },
            size = SecondaryButtonSize,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(horizontal = 10.dp),
        ) {
            if (lastInputMethod == InputMethod.Voice) {
                Icon(
                    imageVector = Icons.Outlined.Keyboard,
                    contentDescription = stringResource(R.string.action_keyboard),
                    tint = AssistantTheme.palette.actionIconTint,
                    modifier = Modifier.size(25.dp),
                )
            } else {
                Icon(
                    imageVector = Icons.Outlined.Mic,
                    contentDescription = stringResource(R.string.action_speak),
                    tint = AssistantTheme.palette.actionIconTint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        // Center button: large, shows the last-used input method.
        PrimaryButton(
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                if (lastInputMethod == InputMethod.Voice) onMicClick() else onKeyboardClick()
            },
            size = PrimaryButtonSize,
        ) {
            AnimatedContent(
                targetState = state == AssistantState.Listening,
                transitionSpec = {
                    (fadeIn() + scaleIn(initialScale = 0.8f)) togetherWith
                            (fadeOut() + scaleOut(targetScale = 0.8f))
                },
                label = "primary",
            ) { listening ->
                if (listening && lastInputMethod == InputMethod.Voice) {
                    VoiceVisualizer(listening = true, scale = 0.28f)
                } else if (lastInputMethod == InputMethod.Voice) {
                    Icon(
                        painter = painterResource(R.drawable.ic_google_mic),
                        contentDescription = stringResource(R.string.action_speak),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(width = 28.dp, height = 30.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.Keyboard,
                        contentDescription = stringResource(R.string.action_keyboard),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(30.dp),
                    )
                }
            }
        }
    }

}

@Composable
private fun PrimaryButton(
    onClick: () -> Unit,
    size: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .shadow(3.dp, CircleShape)
            .background(AssistantTheme.palette.actionButton, CircleShape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.Center,
        content = { content() },
    )
}


@Preview(showBackground = true)
@Composable
private fun BottomActionBarIdlePreview() {
    AppTheme {
        BottomActionBar(
            state = AssistantState.Idle,
            lastInputMethod = InputMethod.Voice,
            onMicClick = {},
            onKeyboardClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BottomActionBarListeningPreview() {
    AppTheme {
        BottomActionBar(
            state = AssistantState.Listening,
            lastInputMethod = InputMethod.Voice,
            onMicClick = {},
            onKeyboardClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun BottomActionBarKeyboardPreview() {
    AppTheme {
        BottomActionBar(
            state = AssistantState.Idle,
            lastInputMethod = InputMethod.Keyboard,
            onMicClick = {},
            onKeyboardClick = {},
        )
    }
}