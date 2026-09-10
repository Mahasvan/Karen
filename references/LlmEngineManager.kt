package com.example.llmtest.llm

import android.content.Context
import android.util.Log
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Capabilities
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.ExperimentalApi
import com.google.ai.edge.litertlm.ExperimentalFlags
import com.google.ai.edge.litertlm.LogSeverity
import com.google.ai.edge.litertlm.Message
import com.google.ai.edge.litertlm.ToolProvider
import java.io.File
import kotlinx.coroutines.flow.Flow

private const val TAG = "LlmEngineManager"

/**
 * Owns the LiteRT-LM [Engine]/[Conversation] lifecycle for a single model, running on the
 * Tensor TPU via [Backend.NPU] ("TPU" is a Pixel-only UI label for the same NPU backend).
 *
 * The `_Google_Tensor_G5.litertlm` build of Gemma-4-E2B is a pre-compiled Edge TPU model: its
 * container carries a `tf_lite_aux` section and `darwinn_external_parameter_*` tensors, which is
 * what `llm_litert_npu_compiled_model_executor.cc` consumes. Running it on the TPU additionally
 * requires `libLiteRtDispatch_GoogleTensor.so` in `jniLibs/arm64-v8a` (see the `packaging` block
 * in `app/build.gradle.kts`) - LiteRT scans the app's native library directory for a dispatch
 * backend, and without one every signature falls back to XNNPACK and the compiled `main` subgraph
 * fails to prepare with "Failed to allocate tensors".
 *
 * The container also ships MTP (speculative decoding) graphs - `tf_lite_mtp_aux` /
 * `tf_lite_mtp_drafter` - so [ExperimentalFlags.enableSpeculativeDecoding] is set from what the
 * model actually advertises rather than left on its "auto" default.
 *
 * Only one engine/conversation may be alive at a time - [load] always closes the previous
 * instance first so two models' worth of native memory are never resident at once.
 */
class LlmEngineManager(private val context: Context) {

    private var engine: Engine? = null
    private var conversation: Conversation? = null

    fun isReady(): Boolean = conversation != null

    /**
     * Initializes the engine against [modelFile] on the Tensor TPU and opens a fresh conversation.
     * This is a blocking native call (can take several seconds) - call it from a background thread.
     */
    @OptIn(ExperimentalApi::class)
    fun load(
        modelFile: File,
        maxNumTokens: Int,
        systemInstruction: Contents,
        tools: List<ToolProvider>,
    ) {
        close()

        // Routes the native runtime's own diagnostics into logcat. On an NPU load failure the
        // useful lines are "No dispatch library found in ", "Detected NPU prefill size: " and
        // "Passed in max_num_tokens (...) with NPU model max sequence length: (...)".
        Engine.setNativeMinLogSeverity(LogSeverity.VERBOSE)

        val supportsSpeculativeDecoding = readSpeculativeDecodingSupport(modelFile)

        val engineConfig = EngineConfig(
            modelPath = modelFile.absolutePath,
            // The Tensor TPU is accessed through LiteRT-LM's NPU backend; there is no separate
            // TPU delegate. nativeLibraryDir is where LiteRT looks for the dispatch backend.
            backend = Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir),
            maxNumTokens = maxNumTokens,
            // Left to default, the NPU JIT/compilation cache is written next to the model, which
            // lives on FUSE-mounted external storage. Keep it on internal storage instead.
            cacheDir = context.filesDir.absolutePath,
        )

        // Pinned explicitly around engine creation rather than left null ("auto"), and restored
        // afterwards so the flag stays process-global-neutral.
        val previousSpeculativeDecoding = ExperimentalFlags.enableSpeculativeDecoding
        ExperimentalFlags.enableSpeculativeDecoding = supportsSpeculativeDecoding
        val newEngine = Engine(engineConfig)
        try {
            newEngine.initialize()
        } finally {
            ExperimentalFlags.enableSpeculativeDecoding = previousSpeculativeDecoding
        }

        val newConversation = newEngine.createConversation(
            ConversationConfig(
                systemInstruction = systemInstruction,
                // topK/topP/temperature aren't configurable on the NPU/TPU accelerator - it
                // handles sampling internally, so this must stay null on that backend.
                samplerConfig = null,
                // ConversationConfig.automaticToolCalling defaults on, so the runtime invokes the
                // matching @Tool method and feeds its return value back as a tool response without
                // the caller driving the loop. sendMessage's flow still only emits model text.
                tools = tools,
            )
        )

        engine = newEngine
        conversation = newConversation
    }

    /**
     * Reads the model container's declared capabilities before the engine touches it, so a
     * mismatched or truncated file reports something legible instead of a native RET_CHECK.
     * Falls back to leaving speculative decoding off if the container can't be read.
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
                        "Model does not advertise function calling; the clock tools will be " +
                            "ignored or hallucinated rather than invoked.",
                    )
                }
                capabilities.hasSpeculativeDecodingSupport()
            }
        }.getOrElse {
            Log.w(TAG, "Could not read model capabilities", it)
            false
        }

    /** Streams the model's response to [text] chunk by chunk. Call from a background coroutine. */
    fun sendMessage(text: String): Flow<Message> {
        val activeConversation = checkNotNull(conversation) { "Model is not loaded yet." }
        return activeConversation.sendMessageAsync(Message.user(text))
    }

    /** Releases native engine/conversation resources. Safe to call when nothing is loaded. */
    fun close() {
        conversation?.let { runCatching { it.close() } }
        conversation = null
        engine?.let { runCatching { it.close() } }
        engine = null
    }
}
