package com.mahasvan.karen.provider.openai

import com.mahasvan.karen.assistant.ModelUnavailableException
import com.mahasvan.karen.settings.RemoteConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Transport for an OpenAI-compatible HTTP API. Owns the wire format and nothing
 * else: no conversation state, no tool semantics. [OpenAiAssistantBackend] layers
 * those on top, and the settings screen reuses [listModels] to validate a config
 * before it is saved.
 *
 * Failures are split the way [com.mahasvan.karen.assistant.AssistantError] needs
 * them: [ModelUnavailableException] when the user must go fix something (bad key,
 * wrong URL, unknown model) and [IOException] when a retry could plausibly work
 * (timeout, rate limit, 5xx).
 */
class OpenAiClient(private val config: RemoteConfig) {

    /** Model ids the endpoint advertises. Doubles as the connection test. */
    fun listModels(): List<String> {
        val data = request("models", body = null).optJSONArray("data")
            ?: throw IOException("Response had no 'data' array")
        return (0 until data.length())
            .mapNotNull { data.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }
            .sorted()
    }

    /**
     * One chat-completions round trip. Returns the assistant message verbatim so
     * the caller can append it to history with its `tool_calls` intact.
     */
    fun chatCompletion(messages: JSONArray, tools: JSONArray): JSONObject {
        val payload = JSONObject()
            .put("model", config.model)
            .put("messages", messages)
        if (tools.length() > 0) payload.put("tools", tools)

        val choices = request("chat/completions", payload).optJSONArray("choices")
            ?: throw IOException("Response had no 'choices' array")
        return choices.optJSONObject(0)?.optJSONObject("message")
            ?: throw IOException("Response had no assistant message")
    }

    private fun request(path: String, body: JSONObject?): JSONObject {
        val url = URL("${config.baseUrl.trimEnd('/')}/$path")
        val connection = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = if (body == null) "GET" else "POST"
            connectTimeout = CONNECT_TIMEOUT_MILLIS
            readTimeout = READ_TIMEOUT_MILLIS
            setRequestProperty("Accept", "application/json")
            // Local servers (llama.cpp, Ollama, LM Studio) typically need no auth.
            if (config.apiKey.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer ${config.apiKey}")
            }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        return try {
            body?.let { connection.outputStream.use { out -> out.write(it.toString().toByteArray()) } }
            val status = connection.responseCode
            if (status !in 200..299) throw errorFor(status, connection.readErrorBody())
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.readErrorBody(): String =
        runCatching { errorStream?.bufferedReader()?.use { it.readText() }.orEmpty() }.getOrDefault("")

    /**
     * The server's own `error.message` is far more actionable than the status code
     * ("model X does not exist", "incorrect API key"), so surface it when present.
     */
    private fun errorFor(status: Int, errorBody: String): Exception {
        val detail = runCatching {
            JSONObject(errorBody).optJSONObject("error")?.optString("message")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: "HTTP $status"

        return when (status) {
            401, 403 -> ModelUnavailableException("Endpoint rejected the API key: $detail")
            404 -> ModelUnavailableException("Not found \u2014 check the base URL and model: $detail")
            400, 422 -> ModelUnavailableException("Endpoint rejected the request: $detail")
            else -> IOException(detail)
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000

        /** Generous: a remote model can think for a while before the first byte. */
        const val READ_TIMEOUT_MILLIS = 60_000
    }
}
