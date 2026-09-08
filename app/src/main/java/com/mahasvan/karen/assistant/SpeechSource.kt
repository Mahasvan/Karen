package com.mahasvan.karen.assistant

import kotlinx.coroutines.flow.Flow

/**
 * Speech-to-text, deliberately separate from [AssistantBackend].
 *
 * Recognition and generation are independent capabilities: a remote OpenAI-compatible
 * endpoint generates text but does no on-device recognition, and the most likely real
 * configuration is the platform [android.speech.SpeechRecognizer] feeding a remote model.
 * Bundling the two onto one interface would force every provider to implement a capability
 * it does not have, and would make that pairing inexpressible.
 *
 * Failure and cancellation semantics match [AssistantBackend].
 */
interface SpeechSource {

    /**
     * Emits progressively longer partial transcripts while the microphone is open,
     * then the final utterance as the last emission.
     */
    fun transcribe(): Flow<String>
}
