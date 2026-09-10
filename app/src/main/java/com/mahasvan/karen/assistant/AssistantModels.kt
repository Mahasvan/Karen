package com.mahasvan.karen.assistant

import androidx.compose.runtime.Immutable
import com.mahasvan.karen.tools.ToolCard

@Immutable
sealed interface AssistantReply {

    /**
     * A message from the assistant. Streaming backends reuse the same [id] for every
     * update so the UI grows a single bubble instead of one per token. One-shot
     * backends can leave [id] null.
     */
    @Immutable
    data class Text(val text: String, val id: String? = null) : AssistantReply

    /** An informational card shown in the conversation after a tool fires. */
    @Immutable
    data class Card(val card: ToolCard) : AssistantReply
}

@Immutable
sealed interface ConversationItem {
    val id: Long

    @Immutable
    data class UserQuery(override val id: Long, val text: String) : ConversationItem

    /** [streamId] carries [AssistantReply.Text.id] so later deltas can find this item again. */
    @Immutable
    data class AssistantText(
        override val id: Long,
        val text: String,
        val streamId: String? = null,
    ) : ConversationItem

    @Immutable
    data class AssistantCard(override val id: Long, val card: ToolCard) : ConversationItem
}

/**
 * Why a turn failed, in terms the UI can act on. Each backend maps its own
 * failures onto these. The UI only branches on [recoverable].
 */
@Immutable
sealed interface AssistantError {
    /** Whether retrying the same prompt could plausibly succeed. */
    val recoverable: Boolean

    /** Transport failed. Retrying is usually worth it. */
    @Immutable
    data object Network : AssistantError {
        override val recoverable: Boolean get() = true
    }

    /** The model could not be loaded or resolved. Retrying the same prompt will not help. */
    @Immutable
    data class ModelUnavailable(val detail: String?) : AssistantError {
        override val recoverable: Boolean get() = false
    }

    @Immutable
    data class Unknown(val detail: String?) : AssistantError {
        override val recoverable: Boolean get() = true
    }
}

enum class AssistantState { Idle, Listening, Processing, DisplayingResult, Error }

/** Which input method the user last interacted with. Drives the center button. */
@Immutable
enum class InputMethod { Voice, Keyboard }

@Immutable
data class AssistantUiState(
    val state: AssistantState = AssistantState.Idle,
    val conversation: List<ConversationItem> = emptyList(),
    val partialTranscript: String = "",
    val keyboardOpen: Boolean = false,
    val lastInputMethod: InputMethod = InputMethod.Voice,
    /** Non-null exactly when [state] is [AssistantState.Error]. */
    val error: AssistantError? = null,
) {
    val showSuggestionGrid: Boolean get() = conversation.isEmpty() && error == null
}
