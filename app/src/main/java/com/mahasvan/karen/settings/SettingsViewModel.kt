package com.mahasvan.karen.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mahasvan.karen.provider.openai.OpenAiClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Outcome of probing a remote endpoint, surfaced under the connection form. */
sealed interface ConnectionState {
    data object Idle : ConnectionState
    data object Testing : ConnectionState

    /** The endpoint answered; [models] is what it offers. */
    data class Connected(val models: List<String>) : ConnectionState
    data class Failed(val detail: String) : ConnectionState
}

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    /**
     * Null until the first read lands. The theme is needed before the first
     * composition, so callers hold the frame rather than render defaults and
     * snap to the stored value.
     */
    val settings: StateFlow<AppSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Idle)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    fun setColorTheme(theme: ColorTheme) {
        viewModelScope.launch { repository.setColorTheme(theme) }
    }

    fun setBackend(backend: BackendChoice) {
        viewModelScope.launch { repository.setBackend(backend) }
    }

    fun setRemoteModel(model: String) {
        viewModelScope.launch { repository.setRemoteModel(model) }
    }

    /**
     * Probes the endpoint and, only if it answers, persists it. Saving a config
     * that was never reachable is how you end up debugging the assistant instead
     * of the typo in the URL.
     */
    fun testAndSaveRemote(baseUrl: String, apiKey: String) {
        viewModelScope.launch {
            _connection.value = ConnectionState.Testing
            val trimmedUrl = baseUrl.trim()
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    OpenAiClient(RemoteConfig(trimmedUrl, apiKey.trim())).listModels()
                }
            }
            _connection.value = result.fold(
                onSuccess = { models ->
                    repository.setRemoteEndpoint(trimmedUrl, apiKey.trim())
                    ConnectionState.Connected(models)
                },
                onFailure = { ConnectionState.Failed(it.message ?: "Could not reach the endpoint") },
            )
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                SettingsViewModel(SettingsRepository(app))
            }
        }
    }
}
