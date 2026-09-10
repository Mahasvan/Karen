package com.mahasvan.karen.assistant

import java.io.IOException
import kotlinx.coroutines.flow.Flow

/**
 * Turns a prompt into replies. Implementations may be on-device (LiteRT) or remote (an
 * OpenAI-compatible endpoint); neither the ViewModel nor the composables can tell which.
 *
 * A backend advertises `ToolRegistry.tools` to its model, routes the model's chosen function
 * name back through `ToolRegistry.tool`, and emits whatever that tool parses. It never needs
 * to know which capabilities exist.
 *
 * The backend owns the conversation history. Each [respond] call is answered in the context
 * of every previously completed turn, the way an OpenAI-compatible thread or an on-device
 * chat model expects. History stays here rather than being passed in: the ViewModel's
 * conversation list is UI state, not model state, and the backend lives and dies with the
 * ViewModel, or one conversation, either way.
 *
 * History is transactional: a turn, the prompt plus whatever was emitted for it, commits
 * only when the returned [Flow] completes normally. Exceptions and cancellation skip the
 * commit, which is what lets [retry] replay a failed turn against exactly the context it
 * failed in. Implementations may assume turns are collected serially: the ViewModel cancels
 * a running turn before starting the next.
 *
 * Failures are reported by throwing from the returned [Flow]. Throw
 * [ModelUnavailableException] for anything the user cannot fix by retrying, and let
 * [IOException] propagate for transport failures; the ViewModel maps both onto
 * [AssistantError]. Cancellation is not a failure and must propagate untouched.
 */
interface AssistantBackend {

    /** Emits replies as they are produced. May emit several parts for one prompt. */
    fun respond(prompt: String): Flow<AssistantReply>

    /**
     * The last prompt again, against unchanged history, or null when no turn has run. Lives
     * here because a retry must not re-append anything to history, and only the owner of
     * history can guarantee that.
     */
    fun retry(): Flow<AssistantReply>?

    /**
     * Releases native resources, open connections, or any other heavyweight state. Called
     * by the ViewModel's [onCleared][androidx.lifecycle.ViewModel.onCleared] when the
     * ViewModel is destroyed. Safe to call when nothing is loaded.
     */
    fun close()
}

/** The backend's model could not be loaded, resolved, or authenticated against. */
class ModelUnavailableException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

internal fun Throwable.toAssistantError(): AssistantError = when (this) {
    is ModelUnavailableException -> AssistantError.ModelUnavailable(message)
    is IOException -> AssistantError.Network
    else -> AssistantError.Unknown(message)
}
