package com.mahasvan.karen.provider.openai

import com.mahasvan.karen.assistant.ModelUnavailableException
import com.mahasvan.karen.settings.RemoteConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
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
     * One streaming chat-completions round trip. [onDelta] is called with each
     * content fragment the moment the server sends it so the UI can grow one
     * bubble; the returned message is the fully assembled result — identical
     * shape to a non-streaming response — so the caller can append it to
     * history with its `tool_calls` intact.
     */
    suspend fun chatCompletion(
        messages: JSONArray,
        tools: JSONArray,
        onDelta: suspend (String) -> Unit = {},
    ): JSONObject {
        val payload = JSONObject()
            .put("model", config.model)
            .put("messages", messages)
            .put("stream", true)
        if (tools.length() > 0) payload.put("tools", tools)

        val connection = open("chat/completions", payload)
        return try {
            connection.sendAndCheckStatus(payload)
            readSse(connection.inputStream, onDelta)
        } finally {
            connection.disconnect()
        }
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

    /**
     * Reads a server-sent-events stream one line at a time as bytes arrive.
     * Tool-call arguments arrive fragmented across chunks and are concatenated
     * per call index; the deltas never contain the full message, which is why
     * the caller's history needs the reassembled return value.
     */
    private suspend fun readSse(input: InputStream, onDelta: suspend (String) -> Unit): JSONObject =
        input.bufferedReader().use { reader ->
            val content = StringBuilder()
            val toolCalls = sortedMapOf<Int, JSONObject>()

            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data == "[DONE]") break
                val delta = JSONObject(data)
                    .optJSONArray("choices")
                    ?.optJSONObject(0)
                    ?.optJSONObject("delta")
                    ?: continue

                if (!delta.isNull("content")) {
                    val part = delta.getString("content")
                    if (part.isNotEmpty()) {
                        content.append(part)
                        onDelta(part)
                    }
                }

                delta.optJSONArray("tool_calls")?.let { fragments ->
                    for (i in 0 until fragments.length()) {
                        val fragment = fragments.optJSONObject(i) ?: continue
                        val index = fragment.optInt("index", 0)
                        val call = toolCalls.getOrPut(index) {
                            JSONObject()
                                .put("type", "function")
                                .put("function", JSONObject().put("arguments", ""))
                        }
                        fragment.optString("id").takeIf { it.isNotBlank() }?.let { call.put("id", it) }
                        fragment.optJSONObject("function")?.let { fnFragment ->
                            val fn = call.getJSONObject("function")
                            fnFragment.optString("name").takeIf { it.isNotBlank() }?.let { fn.put("name", it) }
                            fn.put("arguments", fn.getString("arguments") + fnFragment.optString("arguments"))
                        }
                    }
                }
            }

            JSONObject().put("role", "assistant").apply {
                if (content.isNotEmpty()) put("content", content.toString())
                if (toolCalls.isNotEmpty()) {
                    put("tool_calls", JSONArray().also { calls -> toolCalls.forEach { (_, call) -> calls.put(call) } })
                }
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
