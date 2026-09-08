package com.mahasvan.karen.provider.stub

import com.mahasvan.karen.assistant.SpeechSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Canned utterances standing in for a real recognizer. A real implementation wraps the
 * platform [android.speech.SpeechRecognizer] and emits its partial results the same way.
 */
class StubSpeechSource : SpeechSource {

    override fun transcribe(): Flow<String> = flow {
        val utterance = STUB_UTTERANCES.random()
        val spoken = StringBuilder()
        utterance.split(" ").forEach { word ->
            delay(260)
            if (spoken.isNotEmpty()) spoken.append(' ')
            spoken.append(word)
            emit(spoken.toString())
        }
        delay(400)
    }

    private companion object {
        val STUB_UTTERANCES = listOf(
            "set an alarm for 7 am",
            "set a timer for 10 minutes",
            "schedule a meeting with the design team",
            "call 555 0134",
        )
    }
}
