package com.mahasvan.karen.provider.platform

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import com.mahasvan.karen.assistant.SpeechEvent
import com.mahasvan.karen.assistant.SpeechException
import com.mahasvan.karen.assistant.SpeechFailure
import com.mahasvan.karen.assistant.SpeechSource
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

private const val TAG = "PlatformSpeechSource"

/**
 * Wraps the platform [SpeechRecognizer] as a [SpeechSource]. Partial results
 * stream out as the user speaks; the final result is the last emission before
 * the flow completes.
 *
 * The recognizer is created and destroyed on the main thread (a platform
 * requirement). Flow cancellation stops and destroys it — the ViewModel's
 * [stopListening][com.mahasvan.karen.assistant.AssistantViewModel.stopListening]
 * cancels the collecting job, so the last partial transcript the UI already
 * holds is what the turn uses.
 */
class PlatformSpeechSource(private val context: Context) : SpeechSource {

    override fun transcribe(): Flow<SpeechEvent> = callbackFlow {
        val mainHandler = Handler(Looper.getMainLooper())

        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            Log.w(TAG, "Speech recognition service not available on this device")
            channel.close(
                SpeechException(
                    SpeechFailure.ServiceUnavailable,
                    "Speech recognition is not available on this device",
                ),
            )
            return@callbackFlow
        }

        // Guard so awaitClose doesn't touch an uninitialized recognizer if
        // the post hasn't run yet when cancellation arrives.
        var recognizer: SpeechRecognizer? = null
        var created = false

        val listener = object : RecognitionListener {

            private var lastPartial = ""

            override fun onReadyForSpeech(params: Bundle?) {
                Log.v(TAG, "onReadyForSpeech")
            }

            override fun onBeginningOfSpeech() {
                Log.v(TAG, "onBeginningOfSpeech")
            }

            override fun onRmsChanged(rmsdB: Float) {
                Log.v(TAG, "onRmsChanged: $rmsdB")
                trySend(SpeechEvent.Amplitude(rmsdB))
            }

            override fun onBufferReceived(buffer: ByteArray?) {}

            override fun onEndOfSpeech() {
                Log.v(TAG, "onEndOfSpeech")
            }

            override fun onError(error: Int) {
                Log.w(TAG, "onError: ${errorName(error)} ($error)")
                when (error) {
                    // The user said nothing; not an error. Completing normally lets the
                    // ViewModel go Idle silently instead of surfacing an ErrorRow.
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> channel.close()

                    else -> channel.close(error.toSpeechException())
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                Log.d(TAG, "onPartialResults: '$text'")
                lastPartial = text
                if (text.isNotEmpty()) trySend(SpeechEvent.PartialTranscript(text))
            }

            override fun onResults(results: Bundle?) {
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    .orEmpty()
                Log.d(TAG, "onResults: '$results'")
                Log.d(TAG, "onResults: '$text'")

                if (text.isNotEmpty()) {
                    trySend(SpeechEvent.FinalTranscript(text))
                } else if (lastPartial.isNotEmpty()) {
                    // sometimes partial text may be recognized, yet final result may be empty.
                    Log.d(TAG, "onResults empty - sending partial: '$lastPartial'")
                    trySend(SpeechEvent.FinalTranscript(lastPartial))
                }
                channel.close()
            }

            override fun onEvent(eventType: Int, params: Bundle?) {}
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
            )

            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)

            // Enable text formatting (punctuation, capitalization) optimized
            // for quality. EXTRA_ENABLE_FORMATTING takes a strategy String, not
            // a boolean — FORMATTING_OPTIMIZE_QUALITY is the value, not a key.
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                putExtra(
                    RecognizerIntent.EXTRA_ENABLE_FORMATTING,
                    RecognizerIntent.FORMATTING_OPTIMIZE_QUALITY,
                )
            }
        }

        Log.d(TAG, "Creating SpeechRecognizer on main thread")
        mainHandler.post {
            try {
                recognizer = SpeechRecognizer.createSpeechRecognizer(context).also {
                    it.setRecognitionListener(listener)
                    it.startListening(intent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create SpeechRecognizer", e)
                channel.close(e)
                return@post
            }
            created = true
        }

        awaitClose {
            Log.d(TAG, "awaitClose — stopping recognizer (created=$created)")
            mainHandler.post {
                recognizer?.let {
                    runCatching { it.stopListening() }
                    runCatching { it.destroy() }
                }
            }
        }
    }
}

private fun Int.toSpeechException(): SpeechException = when (this) {
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechException(
        SpeechFailure.PermissionDenied,
        "Microphone permission is required for voice input",
    )

    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE,
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> SpeechException(
        SpeechFailure.LanguageUnavailable,
        "Speech recognition language pack not available",
    )

    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> SpeechException(
        SpeechFailure.ServiceUnavailable,
        "Speech recognizer is busy",
    )

    SpeechRecognizer.ERROR_SERVER,
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> SpeechException(
        SpeechFailure.ServiceUnavailable,
        "Speech recognition server is unavailable",
    )

    SpeechRecognizer.ERROR_NETWORK,
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> SpeechException(
        SpeechFailure.Network,
        "Couldn't reach the speech recognition service",
    )

    else -> SpeechException(SpeechFailure.Unknown, "Speech recognition failed: ${errorName(this)}")
}

private fun errorName(error: Int): String = when (error) {
    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "NETWORK_TIMEOUT"
    SpeechRecognizer.ERROR_NETWORK -> "NETWORK"
    SpeechRecognizer.ERROR_AUDIO -> "AUDIO"
    SpeechRecognizer.ERROR_SERVER -> "SERVER"
    SpeechRecognizer.ERROR_CLIENT -> "CLIENT"
    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "SPEECH_TIMEOUT"
    SpeechRecognizer.ERROR_NO_MATCH -> "NO_MATCH"
    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "RECOGNIZER_BUSY"
    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "INSUFFICIENT_PERMISSIONS"
    SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "TOO_MANY_REQUESTS"
    SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "SERVER_DISCONNECTED"
    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "LANGUAGE_NOT_SUPPORTED"
    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "LANGUAGE_UNAVAILABLE"
    else -> "UNKNOWN"
}
