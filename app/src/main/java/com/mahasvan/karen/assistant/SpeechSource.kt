package com.mahasvan.karen.assistant

import kotlinx.coroutines.flow.Flow

/**
 * Speech-to-text, separate from [AssistantBackend] because recognition and generation
 * are independent capabilities. A real implementation wraps the platform
 * [android.speech.SpeechRecognizer]. Failure and cancellation semantics match
 * [AssistantBackend].
 *
 * Emits [SpeechEvent.PartialTranscript] as the user speaks (progressively longer transcripts),
 * then a single [SpeechEvent.FinalTranscript] when recognition completes. The flow may also
 * complete without a [SpeechEvent.FinalTranscript] (error, timeout, cancellation) — in that
 * case the collector should discard, not submit.
 */
interface SpeechSource {

    fun transcribe(): Flow<SpeechEvent>
}

/**
 * A single emission from [SpeechSource.transcribe].
 */
sealed interface SpeechEvent {
    data class PartialTranscript(val text: String) : SpeechEvent
    data class FinalTranscript(val text: String) : SpeechEvent
    data class Amplitude(val rmsdB: Float): SpeechEvent

}
