package com.mahasvan.karen.assistant

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AssistantViewModel(
    private val backend: AssistantBackend,
) : ViewModel() {

    private val _uiState = MutableStateFlow(AssistantUiState())
    val uiState: StateFlow<AssistantUiState> = _uiState.asStateFlow()

    private var nextId = 0L
    private var listenJob: Job? = null

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
        _uiState.update {
            it.copy(
                state = AssistantState.Processing,
                partialTranscript = "",
                keyboardOpen = false,
                conversation = it.conversation + ConversationItem.UserQuery(nextId++, query),
            )
        }
        viewModelScope.launch {
            backend.respond(query).collect { reply ->
                _uiState.update { state ->
                    val item = when (reply) {
                        is AssistantReply.Text -> ConversationItem.AssistantText(nextId++, reply.text)
                        is AssistantReply.Card -> ConversationItem.AssistantCard(nextId++, reply.card)
                    }
                    state.copy(conversation = state.conversation + item)
                }
            }
            _uiState.update { it.copy(state = AssistantState.DisplayingResult) }
        }
    }

    fun setAlarmEnabled(id: Long, enabled: Boolean) {
        _uiState.update { state ->
            state.copy(
                conversation = state.conversation.map { item ->
                    if (item is ConversationItem.AssistantCard && item.id == id &&
                        item.card is RichCard.Alarm
                    ) {
                        item.copy(card = item.card.copy(enabled = enabled))
                    } else {
                        item
                    }
                }
            )
        }
    }

    fun dismissCard(id: Long) {
        _uiState.update { state ->
            state.copy(conversation = state.conversation.filterNot { it.id == id })
        }
    }

    private fun startListening() {
        listenJob?.cancel()
        _uiState.update {
            it.copy(state = AssistantState.Listening, partialTranscript = "", keyboardOpen = false)
        }
        listenJob = viewModelScope.launch {
            var transcript = ""
            backend.transcribe().collect { partial ->
                transcript = partial
                _uiState.update { it.copy(partialTranscript = partial) }
            }
            if (transcript.isNotBlank()) submit(transcript)
        }
    }

    private fun stopListening() {
        listenJob?.cancel()
        val transcript = _uiState.value.partialTranscript
        if (transcript.isNotBlank()) {
            submit(transcript)
        } else {
            _uiState.update { it.copy(state = AssistantState.Idle, partialTranscript = "") }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { AssistantViewModel(StubAssistantBackend()) }
        }
    }
}
