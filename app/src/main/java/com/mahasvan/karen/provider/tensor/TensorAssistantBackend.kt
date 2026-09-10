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
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolProvider
import com.google.ai.edge.litertlm.ToolSet
import com.google.ai.edge.litertlm.tool
import com.mahasvan.karen.assistant.AssistantBackend
import com.mahasvan.karen.assistant.AssistantReply
import com.mahasvan.karen.assistant.ModelUnavailableException
import com.mahasvan.karen.tools.ToolCard
import com.mahasvan.karen.tools.ToolRegistry
import com.mahasvan.karen.tools.calendar.CALENDAR_EVENT_TOOL
import com.mahasvan.karen.tools.clock.ALARM_TOOL
import com.mahasvan.karen.tools.clock.TIMER_TOOL
import com.mahasvan.karen.tools.telephony.CALL_TOOL
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentLinkedQueue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

private const val TAG = "TensorAssistantBackend"

/** Extracts the concatenated text content from a [Message]'s [Contents]. */
private fun Message.text(): String =
    contents.contents.filterIsInstance<Content.Text>().joinToString("") { it.text }

/**
 * On-device [AssistantBackend] backed by a LiteRT-LM model running on the Google Tensor TPU
 * (reached through [Backend.NPU] — "TPU" is a Pixel UI label for the same NPU API).
 *
 * The model is a pre-compiled Edge TPU build (`gemma-4-E2B-it_Google_Tensor_G5.litertlm`):
 * its container carries `tf_lite_aux` and `darwinn_external_parameter_*` sections that the
 * NPU executor consumes directly. Running it additionally requires
 * `libLiteRtDispatch_GoogleTensor.so` in `jniLibs/arm64-v8a/` — LiteRT scans the app's
 * native library directory for a dispatch backend, and without one every signature falls
 * back to XNNPACK and the compiled `main` subgraph fails to prepare with
 * "Failed to allocate tensors". See `references/TENSOR_TPU_NOTES.md` §1.
 *
 * Tool calling uses LiteRT-LM's built-in [ToolSet] / [ToolProvider] with
 * `automaticToolCalling = on` (the default): the runtime invokes the matching `@Tool`
 * method and feeds its return value back as a tool response without the caller driving the
 * loop. The `@Tool` method delegates to [ToolRegistry.execute], which fires the system
 * intent immediately and returns a [com.mahasvan.karen.tools.ToolResult] with an optional
 * card. Cards are queued during the model's generation and emitted as [AssistantReply.Card]
 * after the text stream completes — `sendMessageAsync`'s flow only emits model text, so the
 * card has to be buffered.
 *
 * There is no app-level confirmation step. Sensitive actions (e.g. calling) are confirmed by
 * the model through conversation before it calls the tool — the system prompt governs that.
 *
 * The LiteRT-LM [Conversation] owns the chat history. [retry] replays the last prompt
 * against that history; a failed turn may leave a dangling user message in the conversation
 * (the runtime adds it before streaming), so a retry after a failure can produce a duplicate
 * user turn — a known limitation of not being able to roll back the conversation.
 */
class TensorAssistantBackend(
    private val context: Context,
    private val modelFile: File,
    private val maxNumTokens: Int = 1024,
) : AssistantBackend {

    private var engine: Engine? = null
    private var conversation: Conversation? = null
    private var lastPrompt: String? = null
    private var turnCount = 0

    /**
     * Cards produced by tool calls during the current turn, waiting to be emitted after the
     * model's text stream completes. The [AssistantToolSet] writes here during
     * `sendMessageAsync`'s internal tool-calling loop; [respond] drains it after the flow
     * finishes. Thread-safe because the runtime may invoke tool methods on a different
     * thread than the flow collector.
     */
    private val pendingCards = ConcurrentLinkedQueue<ToolCard>()

    override fun respond(prompt: String): Flow<AssistantReply> = flow {
        ensureLoaded()
        lastPrompt = prompt
        pendingCards.clear()
        val responseId = "tensor-${turnCount++}"
        val accumulated = StringBuilder()
        // LiteRT-LM streams token deltas; accumulate into the growing reply so the UI's
        // appendOrUpdateText replaces one bubble instead of one-per-token. Tool calls happen
        // during this collection (automaticToolCalling = on) and queue cards into
        // pendingCards; they are emitted after the text completes.
        conversation!!
            .sendMessageAsync(Message.user(prompt))
            .collect { message ->
                accumulated.append(message.text())
                emit(AssistantReply.Text(accumulated.toString(), id = responseId))
            }
        // Drain any cards the tools produced during this turn.
        while (true) {
            val card = pendingCards.poll() ?: break
            emit(AssistantReply.Card(card))
        }
    }.flowOn(Dispatchers.IO)

    override fun retry(): Flow<AssistantReply>? = lastPrompt?.let { respond(it) }

    /**
     * Lazily initializes the engine and opens a fresh conversation on first use. This is a
     * blocking native call (several seconds); [respond] runs on [Dispatchers.IO] via
     * [flowOn], so the block is safe here.
     */
    @OptIn(ExperimentalApi::class)
    private fun ensureLoaded() {
        if (conversation != null) return
        if (!modelFile.exists()) {
            throw ModelUnavailableException(
                "Model file not found. Place gemma-4-E2B-it_Google_Tensor_G5.litertlm in " +
                    "${modelFile.parentFile?.absolutePath} and try again.",
            )
        }

        // Routes the native runtime's diagnostics into logcat. On an NPU load failure the
        // useful lines are "No dispatch library found in ", "Detected NPU prefill size: "
        // and "Passed in max_num_tokens (...) with NPU model max sequence length: (...)".
        Engine.setNativeMinLogSeverity(LogSeverity.VERBOSE)

        val supportsSpeculativeDecoding = readSpeculativeDecodingSupport(modelFile)

        val engineConfig = EngineConfig(
            modelPath = modelFile.absolutePath,
            // The Tensor TPU is accessed through LiteRT-LM's NPU backend; there is no
            // separate TPU delegate. nativeLibraryDir is where LiteRT looks for the
            // dispatch backend (libLiteRtDispatch_GoogleTensor.so).
            backend = Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir),
            maxNumTokens = maxNumTokens,
            // Left to default, the NPU JIT cache is written next to the model, which lives
            // on FUSE-mounted external storage. Keep it on internal storage instead.
            cacheDir = context.filesDir.absolutePath,
        )

        // Pinned explicitly around engine creation rather than left null ("auto"), and
        // restored afterwards so the flag stays process-global-neutral.
        val previous = ExperimentalFlags.enableSpeculativeDecoding
        ExperimentalFlags.enableSpeculativeDecoding = supportsSpeculativeDecoding
        try {
            val newEngine = Engine(engineConfig).also { it.initialize() }
            val newConversation = newEngine.createConversation(
                ConversationConfig(
                    systemInstruction = buildSystemInstruction(),
                    // topK/topP/temperature aren't configurable on the NPU accelerator — it
                    // handles sampling internally, so this must stay null on that backend.
                    samplerConfig = null,
                    // automaticToolCalling defaults on: the runtime invokes the matching
                    // @Tool method and feeds the result back without the caller driving the
                    // loop. sendMessageAsync's flow still emits only model text.
                    tools = listOf(tool(AssistantToolSet(context, pendingCards))),
                ),
            )
            engine = newEngine
            conversation = newConversation
        } catch (e: Exception) {
            throw ModelUnavailableException(
                "Could not start the on-device model: ${e.message ?: e.javaClass.simpleName}. " +
                    "Check logcat (tag: litert) for dispatch backend or TPU errors.",
                e,
            )
        } finally {
            ExperimentalFlags.enableSpeculativeDecoding = previous
        }
    }

    /**
     * Reads the model container's declared capabilities before the engine touches it, so a
     * mismatched or truncated file reports something legible instead of a native RET_CHECK.
     * Falls back to leaving speculative decoding off if the container can't be read.
     *
     * Per `references/TOOL_CALLING_NOTES.md` §1, `supportsFunctionCalling()` returns `false`
     * and tool calling works anyway — so the probe is a warning, never a hard gate.
     */
    private fun readSpeculativeDecodingSupport(modelFile: File): Boolean =
        runCatching {
            Capabilities(modelFile.absolutePath).use { capabilities ->
                Log.i(
                    TAG,
                    "Model capabilities: speculativeDecoding=" +
                        "${capabilities.hasSpeculativeDecodingSupport()}, " +
                        "thinking=${capabilities.supportsThinking()}, " +
                        "functionCalling=${capabilities.supportsFunctionCalling()}, " +
                        "modalities=${capabilities.inputModalities()}",
                )
                if (!capabilities.supportsFunctionCalling()) {
                    Log.w(
                        TAG,
                        "Model does not advertise function calling; tools may be " +
                            "ignored or hallucinated rather than invoked.",
                    )
                }
                capabilities.hasSpeculativeDecodingSupport()
            }
        }.getOrElse {
            Log.w(TAG, "Could not read model capabilities", it)
            false
        }

    /**
     * The system instruction is frozen at model load, so anything time-dependent is stale
     * after that. Without a wall-clock reference the model cannot turn "set an alarm for
     * three hours from now" into an hour/minute pair. See TOOL_CALLING_NOTES.md §4.
     *
     * The prompt also governs confirmation: the model is told to confirm sensitive actions
     * (placing a call) through conversation before calling the tool, and to call non-sensitive
     * tools (alarms, timers, calendar events) directly without asking.
     */
    private fun buildSystemInstruction(): Contents {
        val now = LocalDateTime.now()
        return Contents.of(
            "You are a helpful, concise assistant that can control the device's clock, " +
                "calendar, and dialer through function calls.\n" +
                "Current date and time: ${now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))} " +
                "(${now.format(DateTimeFormatter.ofPattern("EEEE"))}).\n" +
                "Use the provided tools when the user asks to set alarms, start timers, " +
                "create calendar events, or place calls. " +
                "Android's clock API is write-only: you cannot list existing alarms or " +
                "timers. Never invent alarms or timers you did not set.\n" +
                "For sensitive actions like placing a call, confirm with the user first " +
                "(e.g. \"Should I call 555-0134?\") and wait for their reply before " +
                "calling the dial tool. For non-sensitive actions like setting alarms, " +
                "starting timers, and creating calendar events, call the tool directly " +
                "without asking."
        )
    }

    /** Releases native engine/conversation resources. Safe to call when nothing is loaded. */
    fun close() {
        conversation?.let { runCatching { it.close() } }
        conversation = null
        engine?.let { runCatching { it.close() } }
        engine = null
    }

    /**
     * Bridge from LiteRT-LM's reflection-based tool calling to [ToolRegistry].
     *
     * Each method's name converts to the snake_case tool name the model sees (`setAlarm` →
     * `set_alarm`, etc. — see TOOL_CALLING_NOTES.md §3). The method receives typed
     * parameters, builds the arguments map, and delegates to [ToolRegistry.execute], which
     * validates, fires the system intent immediately, and returns a result with an optional
     * card. The card is queued for [respond] to emit after the text stream.
     *
     * Every method returns `status: "success" | "error"` rather than throwing: a thrown
     * exception aborts the turn, while a returned error is fed back to the model so it can
     * apologise or retry with corrected arguments (TOOL_CALLING_NOTES.md §5).
     */
    class AssistantToolSet(
        private val context: Context,
        private val pendingCards: ConcurrentLinkedQueue<ToolCard>,
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
            @ToolParam(description = "Start time as a Unix epoch timestamp in milliseconds.") startMillis: String,
            @ToolParam(description = "End time in milliseconds. Use start + 3600000 if not specified.") endMillis: String,
        ): Map<String, String> = execute(
            CALENDAR_EVENT_TOOL,
            mapOf(
                "title" to title,
                "start_millis" to startMillis,
                "end_millis" to endMillis,
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
            // The tool validates, fires the intent, and returns a result with an optional
            // card. The card is queued for respond() to emit after the text stream.
            val result = tool.execute(context, arguments)
            result.card?.let { pendingCards.add(it) }
            return if (result.success) {
                mapOf("status" to "success")
            } else {
                mapOf("status" to "error", "message" to result.message)
            }
        }
    }

    companion object {
        private const val MODEL_FILENAME = "gemma-4-E2B-it_Google_Tensor_G5.litertlm"

        /** Default model location: app-private internal storage (avoids FUSE permission issues). */
        fun defaultModelFile(context: Context): File =
            File(context.filesDir, MODEL_FILENAME)
    }
}
