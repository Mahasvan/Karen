package com.mahasvan.karen.assistant

import java.io.IOException
import kotlinx.coroutines.flow.Flow

/**
 * Turns a prompt into replies. The ViewModel and composables are agnostic to the
 * implementation (on-device LiteRT, stub, or future remote endpoint).
 *
 * The backend owns conversation history. Each [respond] call is answered in the
 * context of every previously completed turn. History stays here rather than
 * being passed in: the ViewModel's conversation list is UI state, not model state.
 *
 * History is transactional: a turn commits only when the returned [Flow] completes
 * normally. Exceptions and cancellation skip the commit, which lets [retry]
 * replay a failed turn against the context it failed in. Turns are collected
 * serially — the ViewModel cancels a running turn before starting the next.
 *
 * Failures are reported by throwing from the returned [Flow]. Throw
 * [ModelUnavailableException] for anything the user cannot fix by retrying; let
 * [IOException] propagate for transport failures. The ViewModel maps both onto
 * [AssistantError]. Cancellation must propagate untouched.
 */
interface AssistantBackend {

    /** Emits replies as they are produced. May emit several parts for one prompt. */
    fun respond(prompt: String): Flow<AssistantReply>

    /**
     * The last prompt again, against unchanged history, or null when no turn has
     * run. Lives here because a retry must not re-append to history, and only the
     * owner of history can guarantee that.
     */
    fun retry(): Flow<AssistantReply>?

    /**
     * Releases native resources or other heavyweight state. Called by the
     * ViewModel's [onCleared][androidx.lifecycle.ViewModel.onCleared]. Safe to
     * call when nothing is loaded.
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
