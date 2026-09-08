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
 * Failures are reported by throwing from the returned [Flow]. Throw
 * [ModelUnavailableException] for anything the user cannot fix by retrying, and let
 * [IOException] propagate for transport failures; the ViewModel maps both onto
 * [AssistantError]. Cancellation is not a failure and must propagate untouched.
 */
interface AssistantBackend {

    /** Emits replies as they are produced. May emit several parts for one prompt. */
    fun respond(prompt: String): Flow<AssistantReply>
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
