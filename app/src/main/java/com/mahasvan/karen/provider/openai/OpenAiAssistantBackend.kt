package com.mahasvan.karen.provider.openai

import android.content.Context
import android.util.Log
import com.mahasvan.karen.R
import com.mahasvan.karen.assistant.AssistantBackend
import com.mahasvan.karen.assistant.AssistantReply
import com.mahasvan.karen.assistant.ModelUnavailableException
import com.mahasvan.karen.assistant.systemInstructionLines
import com.mahasvan.karen.settings.RemoteConfig
import com.mahasvan.karen.tools.OpenAiToolSchema
import com.mahasvan.karen.tools.ToolCard
import com.mahasvan.karen.tools.ToolExecutor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

private const val TAG = "OpenAiAssistantBackend"

/**
 * [AssistantBackend] over any OpenAI-compatible chat-completions endpoint.
 *
 * Unlike LiteRT there is no automatic tool calling: this backend drives the loop
 * itself — request, read `tool_calls`, execute through [ToolExecutor], append the
 * results, re-request — and emits cards inline as each tool fires rather than
 * draining a queue after the fact.
 *
 * History is genuinely transactional, which the interface documents but LiteRT
 * cannot honour: a turn builds a pending message list and commits it to [history]
 * only on normal completion, so a failed or cancelled turn leaves the context
 * exactly as [retry] needs to find it.
 *
 * The system instruction is prepended per request rather than stored, so its clock
 * reference stays current across a long conversation.
 */
class OpenAiAssistantBackend(
    private val context: Context,
    private val config: RemoteConfig,
    private val client: OpenAiClient = OpenAiClient(config),
) : AssistantBackend {

    private val history = JSONArray()
    private var lastPrompt: String? = null
    private var turnCount = 0

    override fun respond(prompt: String): Flow<AssistantReply> = flow {
        if (!config.isComplete) {
            throw ModelUnavailableException(
                "Set a base URL and pick a model in Settings before using the remote backend.",
            )
        }
        lastPrompt = prompt
        val responseId = "openai-${turnCount++}"
        val tools = OpenAiToolSchema.functions().toJsonArray()

        // Pending, not committed: anything thrown below leaves `history` untouched.
        val pending = history.copy().put(message(role = "user", content = prompt))

        // fallback reply fires only when the whole turn produced nothing.
        // Mirrors TensorAssistantBackend's `accumulated.isEmpty() && !toolFired` failsafe.
        var emittedReply = false

        repeat(MAX_TOOL_ROUNDS) {
            var streamed = ""
            val assistantMessage = client.chatCompletion(withSystemPrompt(pending), tools) { part ->
                streamed += part
                emit(reply(streamed, responseId))
                emittedReply = true
            }
            pending.put(assistantMessage)

            // A non-streamed content field (some servers buffer it whole) still
            // needs an emit; a streamed one re-emitting identical text is a no-op.
            assistantMessage.optString("content").takeIf { it.isNotBlank() }?.let {
                emit(reply(it, responseId))
                emittedReply = true
            }

            val toolCalls = assistantMessage.optJSONArray("tool_calls")
            if (toolCalls == null || toolCalls.length() == 0) {
                if (!emittedReply) {
                    Log.e(TAG, "Assistant produced no content and fired no tools")
                    emit(reply(context.getString(R.string.assistant_fallback_reply), responseId))
                }
                history.replaceWith(pending)
                return@flow
            }

            for (index in 0 until toolCalls.length()) {
                val call = toolCalls.optJSONObject(index) ?: continue
                val outcome = executeToolCall(call)
                outcome.card?.let {
                    emit(AssistantReply.Card(it))
                    emittedReply = true
                }
                pending.put(outcome.message)
            }
        }

        // Only reachable when the model kept asking for tools and never answered.
        throw IOException("Assistant kept calling tools without producing a reply")
    }.flowOn(Dispatchers.IO)

    override fun retry(): Flow<AssistantReply>? = lastPrompt?.let { respond(it) }

    override fun close() {
        // Nothing to release: HttpURLConnection is per-request.
    }

    /** A tool's card for the UI, paired with the `role: "tool"` message the model gets. */
    private class ToolOutcome(val message: JSONObject, val card: ToolCard?)

    /**
     * Runs one `tool_call`. A missing name or unparseable arguments come back as an
     * error status rather than throwing, so the model can correct itself instead of
     * the turn dying.
     */
    private fun executeToolCall(call: JSONObject): ToolOutcome {
        val function = call.optJSONObject("function")
        val name = function?.optString("name").orEmpty()
        var card: ToolCard? = null

        val status = if (function == null || name.isBlank()) {
            mapOf("status" to "error", "message" to "Tool call had no function name")
        } else {
            ToolExecutor.execute(
                context = context,
                toolName = name,
                arguments = parseArguments(function.optString("arguments")),
                onResult = { card = it.card },
            )
        }

        val message = JSONObject()
            .put("role", "tool")
            .put("tool_call_id", call.optString("id"))
            .put("content", status.toJsonObject().toString())
        return ToolOutcome(message, card)
    }

    /**
     * `arguments` arrives as a JSON *string* of an object with typed values, while
     * tools validate `Map<String, String>` — so values are flattened to their string
     * form here rather than every tool learning the wire format.
     */
    private fun parseArguments(raw: String): Map<String, String> = runCatching {
        val json = JSONObject(raw)
        json.keys().asSequence().associateWith { json.get(it).toString() }
    }.getOrElse {
        Log.w(TAG, "Could not parse tool arguments: $raw", it)
        emptyMap()
    }

    private fun withSystemPrompt(messages: JSONArray): JSONArray =
        JSONArray()
            .put(message(role = "system", content = systemInstructionLines().joinToString(" ")))
            .also { request -> messages.forEach { request.put(it) } }

    private fun message(role: String, content: String): JSONObject =
        JSONObject().put("role", role).put("content", content)

    private fun reply(text: String, responseId: String): AssistantReply =
        AssistantReply.Text(text, id = responseId)

    private companion object {
        /** Stops a model that loops on tool calls from hanging the turn forever. */
        const val MAX_TOOL_ROUNDS = 5
    }
}

private fun JSONArray.copy(): JSONArray = JSONArray().also { copy -> forEach { copy.put(it) } }

private fun JSONArray.replaceWith(source: JSONArray) {
    while (length() > 0) remove(0)
    source.forEach { put(it) }
}

private inline fun JSONArray.forEach(action: (Any) -> Unit) {
    for (index in 0 until length()) action(get(index))
}

/** Recursive, so [OpenAiToolSchema]'s nested map/list tree survives intact. */
private fun List<Map<String, Any>>.toJsonArray(): JSONArray =
    JSONArray().also { array -> forEach { array.put(it.toJsonObject()) } }

private fun Map<*, *>.toJsonObject(): JSONObject = JSONObject().also { json ->
    forEach { (key, value) -> json.put(key.toString(), value.toJsonValue()) }
}

private fun Any?.toJsonValue(): Any? = when (this) {
    is Map<*, *> -> toJsonObject()
    is List<*> -> JSONArray().also { array -> forEach { array.put(it.toJsonValue()) } }
    else -> this
}
