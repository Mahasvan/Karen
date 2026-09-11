package com.mahasvan.karen.provider.tensor

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Capabilities
import com.google.ai.edge.litertlm.Content
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.SupportedModalities
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.ToolSet
import com.google.ai.edge.litertlm.tool
import com.mahasvan.karen.assistant.AssistantBackend
import com.mahasvan.karen.assistant.AssistantReply
import com.mahasvan.karen.assistant.ModelUnavailableException
import com.mahasvan.karen.tools.ToolRegistry
import com.mahasvan.karen.tools.ToolResult
import com.mahasvan.karen.tools.calendar.CALENDAR_EVENT_TOOL
import com.mahasvan.karen.tools.clock.ALARM_TOOL
import com.mahasvan.karen.tools.clock.TIMER_TOOL
import com.mahasvan.karen.tools.telephony.CALL_TOOL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue

private const val TAG = "TensorAssistantBackend"

/** Extracts the concatenated text content from a [Message]'s [Contents]. */
private fun Message.text(): String =
    contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

/**
 * On-device [AssistantBackend] backed by a LiteRT-LM model on the Google Tensor NPU
 * (`Backend.NPU` — "TPU" in Pixel UI is the same NPU API).
 *
 * The model is a pre-compiled Edge TPU build
 * (`gemma-4-E2B-it_Google_Tensor_G5.litertlm`) that needs
 * `libLiteRtDispatch_GoogleTensor.so` in `jniLibs/arm64-v8a/`; without it every
 * signature falls back to `XNNPACK` and the `main` subgraph fails to prepare.
 *
 * The [Engine] is a process-wide singleton (see [companion][Companion]): created
 * once, kept alive across Activity destruction. Only the [Conversation] is
 * per-instance — [close] closes the conversation, not the engine. This avoids
 * re-reading model weights and re-compiling for the NPU on every invocation.
 *
 * Tool calling uses LiteRT-LM's [ToolSet] / [ToolProvider] with
 * `automaticToolCalling = on`: the runtime invokes the matching `@Tool` method and
 * feeds its return value back without the caller driving the loop.
 * `sendMessageAsync`'s flow emits only model text, so tool-produced cards are
 * queued in [completedTools] and emitted after the text stream completes.
 *
 * The [Conversation] owns chat history. [retry] replays the last prompt; a failed
 * turn may leave a dangling user message, so a retry can produce a duplicate user
 * turn — the conversation cannot be rolled back.
 */
class TensorAssistantBackend(
    private val context: Context,
    private val modelFile: File,
    private val maxNumTokens: Int = 4096,
) : AssistantBackend {

    private var conversation: Conversation? = null
    private var lastPrompt: String? = null
    private var turnCount = 0

    /**
     * This companion object controls the LLM lifecycle.
     * The model will be held in memory even after the view is destroyed.
     * Unless of course the OS closes the app and reclaims the memory.
     **/
    companion object {
        // todo: Make a settings page to select the model and backend and stuff
        private const val MODEL_FILENAME = "gemma-4-E2B-it_Google_Tensor_G5.litertlm"

        private var sharedEngine: Engine? = null

        fun defaultModelFile(context: Context): File =
            File(context.filesDir, MODEL_FILENAME)

        @OptIn(ExperimentalApi::class)
        private fun ensureEngine(context: Context, modelFile: File, maxNumTokens: Int): Engine {
            sharedEngine?.let { return it }

            // On NPU load failure, the useful lines are:
            // `No dispatch library found in `, `Detected NPU prefill size: `
            // and `Passed in max_num_tokens (...) with NPU model max sequence length: (...)`.
            Engine.setNativeMinLogSeverity(LogSeverity.VERBOSE)

            val engineConfig = EngineConfig(
                modelPath = modelFile.absolutePath,
                // nativeLibraryDir is where LiteRT looks for the
                // dispatch backend (libLiteRtDispatch_GoogleTensor.so).
                backend = Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir),
                maxNumTokens = maxNumTokens,
                // By default, the NPU JIT cache is written next to the model, which lives
                // on FUSE-mounted external storage. Keep it on internal storage instead.
                cacheDir = context.filesDir.absolutePath,
            )

            val capabilities = readModelCapabilities(modelFile)
            Log.i(
                TAG,
                "Model capabilities: speculativeDecoding=${capabilities.speculativeDecoding}, " +
                        "thinking=${capabilities.thinking}, " +
                        "functionCalling=${capabilities.functionCalling}, " +
                        "modalities=${capabilities.inputModalities}",
            )
            if (!capabilities.functionCalling) {
                Log.w(
                    TAG,
                    "Model does not advertise function calling; tools may be " +
                            "ignored or hallucinated rather than invoked.",
                )
            }
            ExperimentalFlags.enableSpeculativeDecoding = capabilities.speculativeDecoding

            return try {
                val newEngine = Engine(engineConfig).also { it.initialize() }
                sharedEngine = newEngine
                newEngine
            } catch (e: Exception) {
                throw ModelUnavailableException(
                    "Could not start the on-device model: ${e.message ?: e.javaClass.simpleName}. " +
                            "Check logcat (tag: litert) for dispatch backend or TPU errors.",
                    e,
                )
            }
        }

        /** OS reclaims native memory on process death.
         * Use releaseEngine() only if memory pressure becomes measurable.
         * Haven't found a use for this yet. Keeping it here just because. */
        fun releaseEngine() {
            sharedEngine?.let { runCatching { it.close() } }
            sharedEngine = null
        }

        /**
         * Model capabilities probed before the engine touches the file, so a mismatched
         * or truncated model reports something legible. `functionCalling` returns `false`
         * but tool calling works anyway (prompt-driven, not grammar-constrained), so it's
         * a warning, never a hard gate.
         */
        data class ModelCapabilities(
            val speculativeDecoding: Boolean,
            val thinking: Boolean,
            val functionCalling: Boolean,
            val inputModalities: SupportedModalities,
        )

        private fun readModelCapabilities(modelFile: File): ModelCapabilities =
            runCatching {
                Capabilities(modelFile.absolutePath).use { c ->
                    ModelCapabilities(
                        speculativeDecoding = c.hasSpeculativeDecodingSupport(),
                        thinking = c.supportsThinking(),
                        functionCalling = c.supportsFunctionCalling(),
                        inputModalities = c.inputModalities(),
                    )
                }
            }.getOrElse {
                Log.w(TAG, "Could not read model capabilities", it)
                // return that the model supports only text, to be safe
                ModelCapabilities(
                    false,
                    thinking = false,
                    functionCalling = false,
                    inputModalities = SupportedModalities(
                        text = true,
                        vision = false,
                        audio = false,
                        video = false
                    )
                )
            }
    }

    /**
     * Results from tool calls during the current turn, drained by [respond] after
     * the text stream completes. Each entry is the full [ToolResult] — card if the
     * tool produced one, message either way. Thread-safe: the runtime may invoke
     * tool methods on a different thread than the flow collector.
     */
    private val completedTools = ConcurrentLinkedQueue<ToolResult>()

    override fun respond(prompt: String): Flow<AssistantReply> = flow {
        ensureLoaded()
        lastPrompt = prompt
        completedTools.clear()
        val responseId = "tensor-${turnCount++}"
        val accumulated = StringBuilder()

        Log.d(TAG, "respond: sending prompt='$prompt', conversation alive=${conversation?.isAlive}")

        // LiteRT-LM streams token deltas; the UI's appendOrUpdateText replaces the bubble.
        // Tool calls happen during this collection (automaticToolCalling = on)
        // and queue results into completedTools; they are emitted after the text completes.
        try {
            conversation!!
                .sendMessageAsync(Message.user(prompt))
                .collect { message ->
                    val text = message.text()
                    Log.d(
                        TAG,
                        "Message: text='$text', toolCalls=${message.toolCalls.size}, " +
                                "contents=${message.contents.contents.map { it::class.simpleName }}",
                    )
                    if (text.isEmpty()) return@collect
                    accumulated.append(text)
                    emit(AssistantReply.Text(accumulated.toString(), id = responseId))
                }
        } catch (e: Exception) {
            Log.e(TAG, "sendMessageAsync threw", e)
            emit(AssistantReply.Text("Error: ${e.message ?: "unknown"}", id = responseId))
            return@flow
        }

        Log.d(
            TAG,
            "Flow complete: accumulated=${accumulated.length} chars, tools=${completedTools.size}"
        )

        // Drain tool results. Cards are emitted as cards; card-less tools (e.g. dial)
        // fall back to their message as text so the user sees something.
        var toolFired = false
        val toolMessages = StringBuilder()
        while (true) {
            val result = completedTools.poll() ?: break
            toolFired = true
            result.card?.let { emit(AssistantReply.Card(it)) }
            if (result.card == null) {
                if (toolMessages.isNotEmpty()) toolMessages.append(' ')
                toolMessages.append(result.message)
            }
        }
        if (accumulated.isEmpty() && toolMessages.isNotEmpty()) {
            emit(AssistantReply.Text(toolMessages.toString(), id = responseId))
        }

        // No text and no tools fired — the model emitted nothing useful.
        if (accumulated.isEmpty() && !toolFired) {
            Log.w(TAG, "Failsafe triggered: no text and no tools fired")
            emit(AssistantReply.Text("I didn't catch that. Could you rephrase?", id = responseId))
        }
    }.flowOn(Dispatchers.IO)

    override fun retry(): Flow<AssistantReply>? = lastPrompt?.let { respond(it) }

    /**
     * Opens a fresh conversation on the shared engine. The engine is created once per
     * process (see [companion][Companion]); only the conversation is per-instance.
     */
    private fun ensureLoaded() {
        if (conversation != null) return
        if (!modelFile.exists()) {
            throw ModelUnavailableException(
                "Model file not found. Place gemma-4-E2B-it_Google_Tensor_G5.litertlm in " +
                        "${modelFile.parentFile?.absolutePath} and try again.",
            )
        }

        try {
            val engine = ensureEngine(context, modelFile, maxNumTokens)
            conversation = engine.createConversation(
                ConversationConfig(
                    systemInstruction = buildSystemInstruction(),
                    // topK/topP/temperature aren't configurable on the NPU accelerator — it
                    // handles sampling internally, so this must stay null on that backend.
                    samplerConfig = null,
                    // automaticToolCalling defaults on: the runtime invokes the matching
                    // @Tool method and feeds the result back without the caller driving the
                    // loop. sendMessageAsync's flow still emits only model text.
                    tools = listOf(tool(AssistantToolSet(context, completedTools))),
                ),
            )
        } catch (e: ModelUnavailableException) {
            throw e
        } catch (e: Exception) {
            throw ModelUnavailableException(
                "Could not start the on-device model: ${e.message ?: e.javaClass.simpleName}. " +
                        "Check logcat (tag: litert) for dispatch backend or TPU errors.",
                e,
            )
        }
    }

    /**
     * Frozen at conversation creation, so anything time-dependent is stale after that. Without
     * a wall-clock reference the model cannot turn "set an alarm for three hours from
     * now" into an hour/minute pair. Also governs confirmation: sensitive actions
     * (calling) must be confirmed through conversation first; non-sensitive tools
     * (alarms, timers, calendar events) are called directly.
     */
    private fun buildSystemInstruction(): Contents {
        val now = LocalDateTime.now()
        val curDateTimeString = now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
        val dayOfWeekString = now.format(DateTimeFormatter.ofPattern("EEEE"))
        return Contents.of(
            listOf(
                "You are a helpful, concise assistant. You can control the device's clock, " +
                        "calendar, and dialer through function calls.",
                "Current date and time: $curDateTimeString ($dayOfWeekString).",
            ).map { Content.Text(it) }
        )
    }

    /** Closes the conversation only. The shared engine persists across Activity destruction. */
    override fun close() {
        conversation?.let { runCatching { it.close() } }
        conversation = null
    }

    /**
     * Bridge from LiteRT-LM's reflection-based tool calling to [ToolRegistry].
     *
     * Each method name converts camelCase → snake_case for the model (`setAlarm` →
     * `set_alarm`). Methods receive typed params, build the arguments map, and
     * delegate to [ToolRegistry.tool] + [Tool.execute][com.mahasvan.karen.tools.Tool.execute],
     * which validates, fires the intent, and returns a [ToolResult]. The full result
     * is queued in [completedTools] for [respond] to emit after the text stream.
     *
     * The method's return value (a `Map<String, String>`) is what LiteRT-LM
     * serializes back to the model as the tool response — a simple
     * `{"status": "success"}` or `{"status": "error", "message": "..."}`. It
     * cannot return [ToolResult] directly because the model would see the internal
     * `card` field (a Compose UI type) in its JSON context. The return value goes
     * to the model; the queued side effect goes to the UI.
     *
     * Methods return an error status rather than throwing: a thrown exception
     * aborts the turn, while a returned error is fed back to the model so it can
     * apologise or retry with corrected arguments.
     */
    @Suppress("unused") // @Tool methods are called via reflection by LiteRT-LM
    class AssistantToolSet(
        private val context: Context,
        private val completedTools: ConcurrentLinkedQueue<ToolResult>,
    ) : ToolSet {

        @Tool(description = "Set an alarm at a specific time of day on the user's device.")
        fun setAlarm(
            @ToolParam(description = "Hour in 24-hour form, 0-23.") hour: Int,
            @ToolParam(description = "Minute past the hour, 0-59. Use 0 if not specified.") minute: Int,
            @ToolParam(description = "Short name shown on the alarm. Use empty string if not specified.") label: String,
        ): Map<String, String> = execute(
            ALARM_TOOL,
            mapOf("hour" to hour.toString(), "minute" to minute.toString(), "label" to label),
        )

        @Tool(description = "Start a countdown timer on the user's device.")
        fun startTimer(
            @ToolParam(description = "Duration in seconds, 1-86400.") seconds: Int,
            @ToolParam(description = "Short name shown on the timer. Use empty string if not specified.") label: String,
        ): Map<String, String> = execute(
            TIMER_TOOL,
            mapOf("seconds" to seconds.toString(), "label" to label),
        )

        @Tool(description = "Add an event to the user's calendar.")
        fun createCalendarEvent(
            @ToolParam(description = "Title of the event.") title: String,
            @ToolParam(description = "Start time in the format YYYY-MM-DDTHH:MM:SS. Defaults to current time if not specified.") startTime: String,
            @ToolParam(description = "End time in the format YYYY-MM-DDTHH:MM:SS. Defaults to start time plus one hour if not specified.") endTime: String,
        ): Map<String, String> = execute(
            CALENDAR_EVENT_TOOL,
            mapOf(
                "title" to title,
                "start_time" to startTime,
                "end_time" to endTime,
            ),
        )

        @Tool(description = "Open the dialer pre-filled with a phone number, or look up a contact by name. Never places the call; the user presses dial.")
        fun dial(
            @ToolParam(description = "A phone number or a contact name.") target: String,
        ): Map<String, String> = execute(
            CALL_TOOL,
            mapOf("target" to target),
        )

        private fun execute(toolName: String, arguments: Map<String, String>): Map<String, String> {
            val tool = ToolRegistry.tool(toolName)
                ?: return mapOf("status" to "error", "message" to "Unknown tool: $toolName")
            // The tool validates, fires the intent, and returns a ToolResult. The full
            // result is queued for the UI; the return value is the simple status the
            // model sees (see class docstring for why these are separate).
            val result = tool.execute(context, arguments)
            completedTools.add(result)
            return if (result.success) {
                mapOf("status" to "success")
            } else {
                mapOf("status" to "error", "message" to result.message)
            }
        }
    }
}
