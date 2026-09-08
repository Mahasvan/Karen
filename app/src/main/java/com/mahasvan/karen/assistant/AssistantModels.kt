package com.mahasvan.karen.assistant

import androidx.compose.runtime.Immutable

/**
 * The four things Karen can do. Anything else is answered with a spoken refusal rather
 * than a card.
 */
@Immutable
sealed interface RichCard {
    @Immutable
    data class Alarm(
        val label: String,
        val hour: Int,
        val minute: Int,
        val enabled: Boolean,
    ) : RichCard

    @Immutable
    data class Timer(
        val label: String,
        val totalSeconds: Int,
    ) : RichCard

    @Immutable
    data class CalendarEvent(
        val title: String,
        val startMillis: Long,
        val endMillis: Long,
    ) : RichCard
}

/**
 * Work that has to leave the app. Karen builds the intent and the platform owns whatever
 * happens next, so there is no in-app calling or clock UI to maintain.
 */
@Immutable
sealed interface AssistantAction {
    /** [query] is either a dialable number or a contact name to look up. */
    @Immutable
    data class Dial(val query: String) : AssistantAction

    @Immutable
    data class SetAlarm(val hour: Int, val minute: Int, val label: String) : AssistantAction

    @Immutable
    data class StartTimer(val seconds: Int, val label: String) : AssistantAction

    @Immutable
    data class CreateEvent(
        val title: String,
        val startMillis: Long,
        val endMillis: Long,
    ) : AssistantAction
}

@Immutable
sealed interface AssistantReply {
    @Immutable
    data class Text(val text: String) : AssistantReply

    @Immutable
    data class Card(val card: RichCard) : AssistantReply

    /** Handed straight to the platform without waiting for the user to confirm. */
    @Immutable
    data class Handoff(val action: AssistantAction) : AssistantReply
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
