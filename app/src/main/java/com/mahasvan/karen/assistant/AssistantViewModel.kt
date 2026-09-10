package com.mahasvan.karen.assistant

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mahasvan.karen.provider.stub.StubSpeechSource
import com.mahasvan.karen.provider.tensor.TensorAssistantBackend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AssistantViewModel(
    private val backend: AssistantBackend,
    private val speech: SpeechSource,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var nextId = 0L
    private var listenJob: Job? = null
    private var turnJob: Job? = null

    override fun onCleared() {
        super.onCleared()
        backend.close()
    }

    fun toggleMicrophone() {
        if (_uiState.value.state == AssistantState.Listening) stopListening() else startListening()
    }

    fun toggleKeyboard() {
        _uiState.update { it.copy(keyboardOpen = !it.keyboardOpen) }
    }

    fun submit(text: String) {
        val query = text.trim()
        if (query.isEmpty()) return
        listenJob?.cancel()
        listenJob = null
        beginTurn(query)
    }

    /**
     * Re-runs the last prompt without duplicating it in the transcript. The backend
     * owns history and therefore owns the retry.
     */
    fun retry() {
        val replies = backend.retry() ?: return
        _uiState.update { it.copy(state = AssistantState.Processing, error = null) }
        runTurn(replies)
    }

    private fun beginTurn(query: String) {
        _uiState.update {
            it.copy(
                state = AssistantState.Processing,
                partialTranscript = "",
                keyboardOpen = false,
                error = null,
                conversation = it.conversation + ConversationItem.UserQuery(nextId++, query),
            )
        }
        runTurn(backend.respond(query))
    }

    private fun runTurn(replies: Flow<AssistantReply>) {
        turnJob?.cancel()
        turnJob = viewModelScope.launch {
            try {
                replies.collect { reply ->
                    when (reply) {
                        is AssistantReply.Text -> appendOrUpdateText(reply)
                        is AssistantReply.Card ->
                            append(ConversationItem.AssistantCard(nextId++, reply.card))
                    }
                }
                _uiState.update { it.copy(state = AssistantState.DisplayingResult) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                fail(e)
            }
        }
    }

    /**
     * Streaming backends re-emit the same message as it grows, tagged with a
     * stable [AssistantReply.Text.id]. Matching on that id keeps a streamed reply
     * in one bubble rather than one per token.
     */
    private fun appendOrUpdateText(reply: AssistantReply.Text) {
        val streamId = reply.id
        val existing = streamId?.let { id ->
            _uiState.value.conversation.firstOrNull {
                it is ConversationItem.AssistantText && it.streamId == id
            }
        }
        if (existing == null) {
            append(ConversationItem.AssistantText(nextId++, reply.text, streamId))
            return
        }
        _uiState.update { state ->
            state.copy(
                conversation = state.conversation.map { item ->
                    if (item.id == existing.id && item is ConversationItem.AssistantText) {
                        item.copy(text = reply.text)
                    } else {
                        item
                    }
                }
            )
        }
    }

    private fun append(item: ConversationItem) {
        _uiState.update { it.copy(conversation = it.conversation + item) }
    }

    private fun fail(cause: Throwable) {
        _uiState.update {
            it.copy(state = AssistantState.Error, error = cause.toAssistantError())
        }
    }

    private fun startListening() {
        listenJob?.cancel()
        _uiState.update {
            it.copy(
                state = AssistantState.Listening,
                partialTranscript = "",
                keyboardOpen = false,
                error = null,
            )
        }
        listenJob = viewModelScope.launch {
            var transcript = ""
            try {
                speech.transcribe().collect { partial ->
                    transcript = partial
                    _uiState.update { it.copy(partialTranscript = partial) }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                fail(e)
                return@launch
            }
            listenJob = null
            if (transcript.isNotBlank()) beginTurn(transcript)
        }
    }

    private fun stopListening() {
        listenJob?.cancel()
        listenJob = null
        val transcript = _uiState.value.partialTranscript
        if (transcript.isNotBlank()) {
            beginTurn(transcript)
        } else {
            _uiState.update { it.copy(state = AssistantState.Idle, partialTranscript = "") }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                AssistantViewModel(
                    TensorAssistantBackend(app, TensorAssistantBackend.defaultModelFile(app)),
                    StubSpeechSource(),
                )
            }
        }
    }
}
