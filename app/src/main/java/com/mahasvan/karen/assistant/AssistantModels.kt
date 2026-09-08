package com.mahasvan.karen.assistant

import androidx.compose.runtime.Immutable

@Immutable
sealed interface RichCard {
    @Immutable
    data class Calling(val contact: String) : RichCard

    @Immutable
    data class MusicPicker(val track: String, val providers: List<MusicProvider>) : RichCard

    @Immutable
    data class Alarm(val label: String, val time: String, val enabled: Boolean) : RichCard
}

enum class MusicProvider(val displayName: String) {
    Spotify("Spotify"),
    YouTubeMusic("YouTube Music"),
    AppleMusic("Apple Music"),
}

@Immutable
sealed interface AssistantReply {
    @Immutable
    data class Text(val text: String) : AssistantReply

    @Immutable
    data class Card(val card: RichCard) : AssistantReply
}

@Immutable
sealed interface ConversationItem {
    val id: Long

    @Immutable
    data class UserQuery(override val id: Long, val text: String) : ConversationItem

    @Immutable
    data class AssistantText(override val id: Long, val text: String) : ConversationItem

    @Immutable
    data class AssistantCard(override val id: Long, val card: RichCard) : ConversationItem
}

enum class AssistantState { Idle, Listening, Processing, DisplayingResult }

@Immutable
data class AssistantUiState(
    val state: AssistantState = AssistantState.Idle,
    val conversation: List<ConversationItem> = emptyList(),
    val partialTranscript: String = "",
    val keyboardOpen: Boolean = false,
) {
    val showSuggestionGrid: Boolean get() = conversation.isEmpty()
}
