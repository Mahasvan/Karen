package com.mahasvan.karen.provider

import android.content.Context
import com.google.adk.kt.models.openai.ChatCompletions
import com.google.adk.kt.types.HttpOptions
import com.mahasvan.karen.assistant.AssistantBackend
import com.mahasvan.karen.assistant.ModelUnavailableException
import com.mahasvan.karen.provider.adk.AdkAssistantBackend
import com.mahasvan.karen.provider.tensor.TensorAssistantBackend
import com.mahasvan.karen.settings.AppSettings
import com.mahasvan.karen.settings.BackendChoice
import com.mahasvan.karen.settings.SettingsRepository
import kotlinx.coroutines.flow.first

/**
 * The single place that decides which [AssistantBackend] is live, so the ViewModel
 * depends on the interface and never on a concrete provider.
 *
 * Config is read at the start of a turn rather than watched, which keeps a
 * half-typed base URL in Settings from tearing down a working backend mid-edit.
 * The instance is cached and only rebuilt when the config that identifies it
 * actually changes — switching backends closes the old one, dropping its
 * conversation history along with it, which is the honest result of changing
 * models mid-conversation.
 */
class AssistantBackendProvider(
    private val context: Context,
    private val settings: SettingsRepository,
) {

    private var current: AssistantBackend? = null
    private var currentKey: Any? = null

    suspend fun get(): AssistantBackend {
        val snapshot = settings.settings.first()
        val key = snapshot.backendKey()
        current?.let { if (key == currentKey) return it }

        close()
        return create(snapshot).also {
            current = it
            currentKey = key
        }
    }

    fun close() {
        current?.close()
        current = null
        currentKey = null
    }

    private fun create(settings: AppSettings): AssistantBackend = when (settings.backend) {
        BackendChoice.OnDevice -> TensorAssistantBackend(
            context,
            TensorAssistantBackend.defaultModelFile(context),
        )

        BackendChoice.Remote -> AdkAssistantBackend(
            {
                val config = settings.remoteBackendConfig
                if (!config.isComplete) {
                    throw ModelUnavailableException(
                        "Set a base URL and pick a model in Settings before using the remote backend.",
                    )
                }
                ChatCompletions(
                    name = config.model,
                    // Local servers (llama.cpp, Ollama, LM Studio) typically need no auth.
                    apiKey = config.apiKey.ifBlank { null },
                    httpOptions = HttpOptions(baseUrl = config.baseUrl),
                )
            },
            context,
        )
    }

    /** Everything that would produce a different backend, and nothing that wouldn't. */
    private fun AppSettings.backendKey(): Any = when (backend) {
        BackendChoice.OnDevice -> BackendChoice.OnDevice
        BackendChoice.Remote -> remoteBackendConfig
    }
}
