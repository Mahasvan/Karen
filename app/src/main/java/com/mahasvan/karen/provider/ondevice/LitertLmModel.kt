package com.mahasvan.karen.provider.ondevice

import android.content.Context
import android.os.Build
import android.util.Log
import com.google.adk.kt.models.LlmRequest
import com.google.adk.kt.models.LlmResponse
import com.google.adk.kt.models.Model
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
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
import com.google.ai.edge.litertlm.SupportedModalities
import com.google.ai.edge.litertlm.tool
import com.mahasvan.karen.tools.ToolRegistry
import com.mahasvan.karen.assistant.ModelUnavailableException
import com.mahasvan.karen.assistant.systemInstructionLines
import com.mahasvan.karen.tools.ToolResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.util.concurrent.ConcurrentLinkedQueue
import com.google.ai.edge.litertlm.Content as LitertContent

private const val TAG = "LitertLmModel"

/** Extracts the concatenated text content from a [Message]'s [Contents]. */
private fun Message.text(): String =
    contents.contents.filterIsInstance<LitertContent.Text>().joinToString("") { it.text }

/**
 * Google LitertLm on-device model exposed as an ADK [Model], so the same
 * [AdkAssistantBackend] that fronts `ChatCompletions` can also drive on-device
 * inference.
 *
 * [generateContent] takes the last user message from [LlmRequest.contents] and
 * sends it to a [Conversation], streaming each text chunk back as an ADK
 * [LlmResponse]. LiteRT-LM handles tool calling internally (automaticToolCalling),
 * so no function call/response bridging is needed — the flow is text only.
 *
 * History lives in two places intentionally: the LiteRT-LM [Conversation] (for
 * the KV cache) and ADK's session (for ADK's agent orchestration). [close] shuts
 * the conversation but not the shared [Engine].
 */
class LitertLmModel(
    private val context: Context,
    private val modelFile: File,
    private val completedTools: ConcurrentLinkedQueue<ToolResult>,
    private val maxNumTokens: Int = MAX_NUM_TOKENS,
) : Model, AutoCloseable {

    override val name: String = "litertlm"

    private var conversation: Conversation? = null

    override fun generateContent(request: LlmRequest, stream: Boolean): Flow<LlmResponse> = flow {
        val conversation = conversation ?: createConversation()

        val prompt = request.contents.lastOrNull()?.text()
            ?: throw ModelUnavailableException("Nothing to send to the on-device model.")
        Log.d(TAG, "generateContent: prompt='$prompt', conversation alive=${conversation.isAlive}")

        val aggregated = StringBuilder()
        conversation.sendMessageAsync(Message.user(prompt)).collect { message ->
            Log.d(
                TAG,
                "chunk: text=${message.text().length} chars, " +
                        "toolCalls=${message.toolCalls.size}, " +
                        "parts=${message.contents.contents.map { it::class.simpleName }}",
            )
            val text = message.text()
            if (text.isNotBlank()) {
                aggregated.append(text)
                if (stream) {
                    emit(
                        LlmResponse(
                            content = Content(Role.MODEL, listOf(Part(text = text))),
                            partial = true,
                        ),
                    )
                }
            }
        }

        Log.d(
            TAG,
            "turn complete: accumulated=${aggregated.length} chars, tools=${completedTools.size}",
        )

        // Turn boundary: the aggregate carries the turn's full text. This is what
        // AdkAssistantBackend uses to replace the bubble at round boundaries.
        emit(
            LlmResponse(
                content = Content(Role.MODEL, listOf(Part(text = aggregated.toString()))),
                partial = false,
            ),
        )
    }.flowOn(Dispatchers.IO)

    /**
     * Opens a fresh conversation on the shared engine, with the same
     * configuration the old direct backend used: frozen system instruction,
     * no sampler config (NPU handles sampling internally), and LiteRT-LM's
     * native tool loop over [AssistantToolSet].
     */
    @OptIn(ExperimentalApi::class)
    private fun createConversation(): Conversation =
        ensureEngine(context, modelFile, maxNumTokens).createConversation(
            ConversationConfig(
                systemInstruction = Contents.of(systemInstructionLines().map { LitertContent.Text(it) }),
                samplerConfig = null,
                tools = ToolRegistry.tools.map { tool(RegistryTool(it, context, completedTools)) },
            ),
        ).also { conversation = it }

    /** Closes the conversation only. The shared engine survives. */
    override fun close() {
        conversation?.let { runCatching { it.close() } }
        conversation = null
    }


    companion object {
        private const val MODEL_FILENAME = "gemma-4-E2B-it_Google_Tensor_G5.litertlm"
        private const val MAX_NUM_TOKENS = 4096

        private var sharedEngine: Engine? = null

        fun defaultModelFile(context: Context): File = File(context.filesDir, MODEL_FILENAME)

        /**
         * Process-wide [Engine] singleton: created once, kept alive across
         * Activity destruction to avoid re-reading weights and re-compiling
         * for the NPU on every invocation.
         */
        @OptIn(ExperimentalApi::class)
        private fun ensureEngine(context: Context, modelFile: File, maxNumTokens: Int): Engine {
            sharedEngine?.let { return it }

            // On NPU load failure, the useful lines are:
            // `No dispatch library found in `, `Detected NPU prefill size: `
            // and `Passed in max_num_tokens (...) with NPU model max sequence length: (...)`.
            Engine.setNativeMinLogSeverity(LogSeverity.VERBOSE)
            logNpuEnvironment(context)

            val engineConfig = EngineConfig(
                modelPath = modelFile.absolutePath,
                // nativeLibraryDir is where LiteRT looks for the dispatch
                // backend (libLiteRtDispatch_GoogleTensor.so).
                backend = Backend.NPU(nativeLibraryDir = context.applicationInfo.nativeLibraryDir),
                maxNumTokens = maxNumTokens,
                // Keep the NPU JIT cache on internal storage instead of
                // FUSE-mounted external storage for faster subsequent loads.
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
                val engine = Engine(engineConfig).also { it.initialize() }
                sharedEngine = engine
                engine
            } catch (e: Exception) {
                throw ModelUnavailableException(
                    "Could not start the on-device model: ${e.message ?: e.javaClass.simpleName}. " +
                            "Check logcat (tag: litert) for dispatch backend or TPU errors.",
                    e,
                )
            }
        }

        /**
         * Two facts that are otherwise invisible until the engine fails.
         *
         * [Build.SOC_MODEL] (API 31+, and minSdk is 31) is the key an AOT model
         * allowlist is indexed by — see `docs/TENSOR_DEPLOYMENT_NOTES.md` §12.
         *
         * The dispatch libraries present in the native library dir decide whether
         * [Backend.NPU] can initialise at all.
         */
        private fun logNpuEnvironment(context: Context) {
            Log.i(
                TAG,
                "Device: model=${Build.MODEL}, soc=${Build.SOC_MODEL}, " +
                        "socManufacturer=${Build.SOC_MANUFACTURER}",
            )
            val libDir = File(context.applicationInfo.nativeLibraryDir)
            val dispatchLibs = libDir
                .listFiles { file -> file.name.startsWith("libLiteRtDispatch_") }
                ?.map { it.name }
                .orEmpty()
            when {
                dispatchLibs.isEmpty() -> Log.w(
                    TAG,
                    "No libLiteRtDispatch_*.so in ${libDir.absolutePath} — NPU cannot " +
                            "initialise. The runtime library was not packaged or delivered.",
                )

                dispatchLibs.size > 1 -> Log.w(
                    TAG,
                    "Multiple dispatch libraries $dispatchLibs — LiteRT loads the first " +
                            "match, so vendor selection is ambiguous.",
                )

                else -> Log.i(TAG, "Dispatch library: ${dispatchLibs.single()}")
            }
        }

        /**
         * Probed before the engine touches the model file, so a mismatched or
         * truncated model reports something legible. `functionCalling` passing
         * `false` for a prompt-driven tool model is a warning, not a gate.
         */
        private data class ModelCapabilities(
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
                ModelCapabilities(
                    speculativeDecoding = false,
                    thinking = false,
                    functionCalling = false,
                    inputModalities = SupportedModalities(
                        text = true,
                        vision = false,
                        audio = false,
                        video = false,
                    ),
                )
            }
    }
}
