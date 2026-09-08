package com.mahasvan.karen.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mahasvan.karen.assistant.ConversationItem
import com.mahasvan.karen.assistant.MusicProvider
import com.mahasvan.karen.ui.theme.AssistantTheme

private val BubbleShape = RoundedCornerShape(topStart = 19.dp, topEnd = 19.dp, bottomStart = 19.dp, bottomEnd = 4.dp)
private val AvatarGutter = 40.dp

@Composable
fun ConversationStream(
    conversation: List<ConversationItem>,
    partialTranscript: String,
    onEndCall: (Long) -> Unit,
    onProviderSelected: (Long, MusicProvider) -> Unit,
    onAlarmToggle: (Long, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(conversation.size, partialTranscript) {
        val lastIndex = conversation.size - if (partialTranscript.isBlank()) 1 else 0
        if (lastIndex >= 0) listState.animateScrollToItem(lastIndex)
    }

    LazyColumn(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(conversation, key = { it.id }) { item ->
            when (item) {
                is ConversationItem.UserQuery -> UserBubble(item.text)
                is ConversationItem.AssistantText -> AssistantResponse(item.text)
                is ConversationItem.AssistantCard -> {
                    val visible = rememberOneShotVisibility(item.id)
                    AnimatedVisibility(
                        visible = visible.value,
                        enter = expandVertically() + fadeIn(),
                    ) {
                        RichCardContent(
                            card = item.card,
                            onEndCall = { onEndCall(item.id) },
                            onProviderSelected = { onProviderSelected(item.id, it) },
                            onAlarmToggle = { onAlarmToggle(item.id, it) },
                            modifier = Modifier.padding(start = AvatarGutter),
                        )
                    }
                }
            }
        }

        if (partialTranscript.isNotBlank()) {
            item(key = "partial") { UserBubble(partialTranscript, dimmed = true) }
        }
    }
}

/** Flips to true on first composition so the card expands in rather than snapping. */
@Composable
private fun rememberOneShotVisibility(key: Any): MutableState<Boolean> {
    val state = remember(key) { mutableStateOf(false) }
    LaunchedEffect(key) { state.value = true }
    return state
}

@Composable
private fun UserBubble(text: String, dimmed: Boolean = false) {
    val palette = AssistantTheme.palette
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
        Box(
            modifier = Modifier
                .widthIn(max = 260.dp)
                .background(palette.userBubble, BubbleShape)
                .padding(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                color = if (dimmed) palette.textSecondary else palette.textPrimary,
            )
        }
    }
}

/**
 * Modern Assistant puts response text straight on the surface with no bubble, unlike the
 * mockup's white pills.
 */
@Composable
private fun AssistantResponse(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        AssistantAvatar()
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = AssistantTheme.palette.textPrimary,
        )
    }
}
