package com.mahasvan.karen.ui

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.mahasvan.karen.R
import com.mahasvan.karen.assistant.AssistantError
import com.mahasvan.karen.assistant.AssistantState
import com.mahasvan.karen.assistant.AssistantUiState
import com.mahasvan.karen.assistant.ConversationItem
import com.mahasvan.karen.assistant.SpeechFailure
import com.mahasvan.karen.ui.components.AssistantAvatar
import com.mahasvan.karen.ui.components.BottomActionBar
import com.mahasvan.karen.ui.components.ConversationStream
import com.mahasvan.karen.ui.components.ResizableColorStrip
import com.mahasvan.karen.ui.theme.AppTheme
import com.mahasvan.karen.ui.theme.AssistantTheme
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.random.Random

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
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = AssistantTheme.palette

    val context = LocalContext.current
    val micPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) onMicClick() }

    val onMicClickWithPermission: () -> Unit = {
        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        Log.d("AssistantOverlay", "mic toggle, RECORD_AUDIO granted=$granted")
        if (granted) onMicClick() else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // A landscape phone has so little height that the mockup's half-screen sheet leaves
        // nothing usable, so the resting sheet takes proportionally more of a short window.
        val shortWindow = maxHeight < ShortWindowHeight
        val restingFraction = 0.3f
        val grownFraction = if (shortWindow) 0.95f else 0.9f

        val sheetFraction by animateFloatAsState(
            targetValue = if (uiState.conversation.isEmpty()) restingFraction else grownFraction,
            animationSpec = tween(320, easing = FastOutSlowInEasing),
            label = "sheetFraction",
        )

        // Drag-to-dismiss state: px the sheet is pulled down below its resting
        // position. snapTo during the drag keeps it glued to the finger; release
        // either springs back or slides away and calls onDismiss.
        val coroutineScope = rememberCoroutineScope()
        val haptics = LocalHapticFeedback.current
        val density = LocalDensity.current
        val currentOnDismiss by rememberUpdatedState(onDismiss)
        val sheetDragOffset = remember { Animatable(0f) }

        fun sheetHeightPx() = with(density) { (maxHeight * sheetFraction).toPx() }
        fun dismissThresholdPx() = sheetHeightPx() * 0.3f
        fun dismissVelocityPx() = with(density) { 200.dp.toPx() }

        // Haptic once per crossing, so the user feels when the pull becomes a dismiss.
        var thresholdHapticDone by remember { mutableStateOf(false) }

        fun pullSheet(delta: Float) {
            val new = (sheetDragOffset.value + delta).coerceAtLeast(0f)
            coroutineScope.launch { sheetDragOffset.snapTo(new) }
            val past = new > dismissThresholdPx()
            if (past && !thresholdHapticDone) {
                thresholdHapticDone = true
                haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate)
            } else if (!past) {
                thresholdHapticDone = false
            }
        }

        fun settleSheetDrag(velocityY: Float) {
            if (sheetDragOffset.value <= 0f) return
            val dismiss = sheetDragOffset.value > dismissThresholdPx() ||
                    velocityY > dismissVelocityPx()
            thresholdHapticDone = false
            coroutineScope.launch {
                if (dismiss) {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    sheetDragOffset.animateTo(
                        sheetHeightPx(),
                        tween(250, easing = FastOutSlowInEasing),
                    )
                    currentOnDismiss()
                } else {
                    sheetDragOffset.animateTo(
                        0f,
                        spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMediumLow,
                        ),
                    )
                }
            }
        }

        // Drag-to-dismiss is handle-only; the sheet body stays scrollable as normal.

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
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding(),
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = SheetMaxWidth)
                    .fillMaxWidth()
                    .fillMaxHeight(sheetFraction)
                    .offset { IntOffset(0, sheetDragOffset.value.roundToInt()) }
                    .background(palette.sheetSurface, SheetShape)
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {}, // Consume taps on the sheet so the scrim's
                        // dismiss handler doesn't fire when the user taps inside the sheet.
                    ),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DragHandle(
                    onDrag = { delta -> pullSheet(delta) },
                    onDragEnd = { settleSheetDrag(0f) },
                )

                if (uiState.showLanding) {
                    // todo: remove once testing is done
//                    SuggestionGrid(
//                        onSuggestionClick = onSubmit,
//                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 0.dp),
//                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        // Header: logo + "Try saying"
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            AssistantAvatar(size = 24.dp)
                            Text(
                                text = stringResource(R.string.try_saying),
                                style = MaterialTheme.typography.titleLarge,
                                color = palette.textPrimary,
                                fontWeight = FontWeight.Medium,
//                                fontSize = 16.sp,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        // Suggestion text — random pick, relevant to available tools.
                        val suggestion = remember { LandingSuggestions.random(Random) }
                        Text(
                            text = "“$suggestion”",
                            color = palette.suggestionAccent,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 40.dp),
                        )
                    }
                } else {
                    val isThinking = uiState.state == AssistantState.Processing &&
                            uiState.conversation.lastOrNull() is ConversationItem.UserQuery
                    ConversationStream(
                        conversation = uiState.conversation,
                        partialTranscript = uiState.partialTranscript,
                        isThinking = isThinking,
                        isStreaming = uiState.state == AssistantState.Processing,
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
                    lastInputMethod = uiState.lastInputMethod,
                    onMicClick = onMicClickWithPermission,
                    onKeyboardClick = onKeyboardClick,
                    onSettingsClick = onSettingsClick,
                    // Bottom spacing comes from the navigationBars inset on the parent Column;
                    // adding a fixed inset here as well double-pads on gesture-nav devices.
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
                )
            }
        }

        ResizableColorStrip(
            active = uiState.state == AssistantState.Listening,
            colors = AssistantTheme.brandColors,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight()
        )
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

    is AssistantError.SpeechUnavailable -> when (failure) {
        SpeechFailure.PermissionDenied -> stringResource(R.string.error_speech_permission)
        SpeechFailure.LanguageUnavailable -> stringResource(R.string.error_speech_language)
        SpeechFailure.ServiceUnavailable,
        SpeechFailure.Network,
        SpeechFailure.Unknown -> stringResource(R.string.error_speech_unavailable)
    }
}

@Composable
private fun DragHandle(onDrag: (Float) -> Unit, onDragEnd: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onVerticalDrag = { _, dragAmount -> onDrag(dragAmount) },
                    onDragEnd = onDragEnd,
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .padding(vertical = 10.dp)
                .size(width = 36.dp, height = 4.dp)
                .background(AssistantTheme.palette.textSecondary.copy(alpha = 0.3f), CircleShape)
        )
    }
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

@Preview(showBackground = true)
@Composable
fun AssistantOverlayInitialPreview() {
    AppTheme {
        AssistantOverlay(
            uiState = AssistantUiState(
                state = AssistantState.Idle,
                conversation = emptyList()
            ),
            onMicClick = {},
            onKeyboardClick = {},
            onSubmit = {},
            onRetry = {},
            onDismiss = {},
            onSettingsClick = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
fun AssistantOverlayConversationPreview() {
    AppTheme {
        AssistantOverlay(
            uiState = AssistantUiState(
                state = AssistantState.DisplayingResult,
                conversation = listOf(
                    ConversationItem.UserQuery(id = 1L, text = "Hello! What can you do?"),
                    ConversationItem.AssistantText(
                        id = 2L,
                        text = "I can help you **bold your text**, `write code`, and _write italics_.\n\n" +
                                "| I | can | also |\n" +
                                "|---|---|---|\n" +
                                "| write | a | table |"
                    )
                )
            ),
            onMicClick = {},
            onKeyboardClick = {},
            onSubmit = {},
            onRetry = {},
            onDismiss = {},
            onSettingsClick = {},
        )
    }
}

