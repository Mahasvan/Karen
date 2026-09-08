package com.mahasvan.karen.assistant

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

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

class StubAssistantBackend : AssistantBackend {

    override fun respond(prompt: String): Flow<AssistantReply> = flow {
        delay(700)
        val normalized = prompt.lowercase()
        when {
            "call" in normalized -> {
                val contact = normalized.substringAfter("call").trim()
                    .removePrefix("to ").ifBlank { "ABCXYZ" }
                    .split(" ").joinToString(" ") { it.replaceFirstChar(Char::uppercase) }
                emit(AssistantReply.Text("Calling $contact"))
                emit(AssistantReply.Card(RichCard.Calling(contact)))
            }

            "play" in normalized -> {
                val track = normalized.substringAfter("play").trim()
                    .ifBlank { "your music" }
                emit(AssistantReply.Text("Where would you like to play $track?"))
                emit(
                    AssistantReply.Card(
                        RichCard.MusicPicker(track, MusicProvider.entries.toList())
                    )
                )
            }

            "alarm" in normalized || "wake" in normalized -> {
                emit(AssistantReply.Text("Alarm set for 7:00 AM"))
                emit(
                    AssistantReply.Card(
                        RichCard.Alarm(label = "Alarm", time = "7:00 AM", enabled = true)
                    )
                )
            }

            "weather" in normalized -> emit(
                AssistantReply.Text("It's 28°C and mostly sunny in Bengaluru right now.")
            )

            else -> emit(
                AssistantReply.Text("I'm running on a stub backend, so I can't answer that yet.")
            )
        }
    }

    override fun transcribe(): Flow<String> = flow {
        val utterance = STUB_UTTERANCES.random()
        val words = utterance.split(" ")
        val spoken = StringBuilder()
        words.forEach { word ->
            delay(260)
            if (spoken.isNotEmpty()) spoken.append(' ')
            spoken.append(word)
            emit(spoken.toString())
        }
        delay(400)
    }

    private companion object {
        val STUB_UTTERANCES = listOf(
            "call ABCXYZ",
            "play Bohemian Rhapsody",
            "set an alarm for 7 AM",
            "what's the weather like",
        )
    }
}
