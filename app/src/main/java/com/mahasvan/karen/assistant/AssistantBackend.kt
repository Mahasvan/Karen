package com.mahasvan.karen.assistant

import kotlinx.coroutines.flow.Flow

/**
 * The only surface the UI knows about. A LiteRT on-device implementation replaces
 * [StubAssistantBackend] without any change to the ViewModel or composables.
 */
interface AssistantBackend {

    /** Emits replies as they are produced. May emit several parts for one prompt. */
    fun respond(prompt: String): Flow<AssistantReply>

    /**
     * Emits progressively longer partial transcripts while the microphone is open,
     * then the final utterance as the last emission.
     */
    fun transcribe(): Flow<String>
}
