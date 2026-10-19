package com.mahasvan.karen.provider.openai

import com.mahasvan.karen.assistant.ModelUnavailableException
import com.mahasvan.karen.settings.RemoteConfig
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal transport for an OpenAI-compatible HTTP API, used only to validate a
 * remote config before it is saved: [listModels] doubles as the connection
 * test. Chat completions go through ADK's `ChatCompletions` model instead —
 * this class no longer carries conversation state or tool semantics.
 *
 * Failure mapping is the same ADK-backed turns rely on:
 * [ModelUnavailableException] when the user must fix something (bad key, wrong
 * URL) and [IOException] when a retry could plausibly work.
 */
class OpenAiClient(private val config: RemoteConfig) {

    /** Model ids the endpoint advertises. */
    fun listModels(): List<String> {
        val data = request("models", body = null).optJSONArray("data")
            ?: throw IOException("Response had no 'data' array")
        return (0 until data.length())
            .mapNotNull { data.optJSONObject(it)?.optString("id")?.takeIf(String::isNotBlank) }
            .sorted()
    }

    private fun request(path: String, body: JSONObject?): JSONObject {
        val connection = open(path, body)
        return try {
            connection.sendAndCheckStatus(body)
            JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
        } finally {
            connection.disconnect()
        }
    }

    private fun open(path: String, body: JSONObject?): HttpURLConnection =
        (URL("${config.baseUrl.trimEnd('/')}/$path").openConnection() as HttpURLConnection).apply {
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

    /** Writes the request body (if any), then fails fast on non-2xx before the body stream is opened. */
    private fun HttpURLConnection.sendAndCheckStatus(body: JSONObject?) {
        body?.let { outputStream.use { out -> out.write(it.toString().toByteArray()) } }
        val status = responseCode
        if (status !in 200..299) throw errorFor(status, readErrorBody())
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
            404 -> ModelUnavailableException("Not found — check the base URL and model: $detail")
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
