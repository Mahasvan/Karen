package com.mahasvan.karen.assistant

import kotlinx.coroutines.flow.Flow

/**
 * Speech-to-text, separate from [AssistantBackend] because recognition and generation
 * are independent capabilities. A real implementation wraps the platform
 * [android.speech.SpeechRecognizer]. Failure and cancellation semantics match
 * [AssistantBackend].
 */
interface SpeechSource {

    /**
     * Emits progressively longer partial transcripts while the microphone is open,
     * then the final utterance as the last emission.
     */
    fun transcribe(): Flow<String>
}
