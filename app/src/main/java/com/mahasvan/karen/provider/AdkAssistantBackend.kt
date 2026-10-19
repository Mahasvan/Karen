package com.mahasvan.karen.provider

import android.content.Context
import android.util.Log
import com.google.adk.kt.agents.Instruction
import com.google.adk.kt.agents.LlmAgent
import com.google.adk.kt.agents.RunConfig
import com.google.adk.kt.agents.StreamingMode
import com.google.adk.kt.models.Model
import com.google.adk.kt.models.openai.ChatCompletionsApiException
import com.google.adk.kt.runners.InMemoryRunner
import com.google.adk.kt.sessions.SessionKey
import com.google.adk.kt.types.Content
import com.google.adk.kt.types.Part
import com.google.adk.kt.types.Role
import com.mahasvan.karen.R
import com.mahasvan.karen.assistant.AssistantBackend
import com.mahasvan.karen.assistant.AssistantReply
import com.mahasvan.karen.assistant.ModelUnavailableException
import com.mahasvan.karen.assistant.systemInstructionLines
import com.mahasvan.karen.tools.ToolResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.IOException
import java.util.concurrent.ConcurrentLinkedQueue

private const val TAG = "AdkAssistantBackend"

/**
 * The single [AssistantBackend] for every provider, over Google ADK.
 *
 * The caller supplies a [Model] factory and chooses how tools are wired:
 * remote passes `ChatCompletions` plus [registeredAdkTools] = true (ADK drives
 * the tool loop through [adkTools]); on-device passes a LiteRT-LM-backed model
 * with
 * `registeredAdkTools = false` (LiteRT-LM's `Conversation` drives its own tool
 * loop internally). Either way, tool results land in [completedTools] — the
 * same queue, passed in as [sharedToolResults] for the on-device path — and
 * are drained to cards after the text stream.
 *
 * The factory runs lazily on the first turn so engine init/network setup stays
 * off the main thread. History lives in the runner's in-memory session,
 * created lazily for this instance's lifetime. [retry] replays the last prompt
 * as a new user turn; a cancelled or failed mid-run turn may leave a dangling
 * user message in the session — the same limitation the old backends had.
 */
class AdkAssistantBackend(
    private val modelFactory: () -> Model,
    context: Context,
    registeredAdkTools: Boolean = true,
    sharedToolResults: ConcurrentLinkedQueue<ToolResult>? = null,
) : AssistantBackend {

    private val completedTools = sharedToolResults ?: ConcurrentLinkedQueue()
    private val useAdkTools = registeredAdkTools
    private var lastPrompt: String? = null
    private var turnCount = 0

    private val appContext = context.applicationContext

    // Model, agent, runner and session are built on the first turn, inside the
    // respond flow on Dispatchers.IO: the model factory may do network or file
    // work and must not run on the main thread, which is where the provider
    // constructs backends.
    private var model: Model? = null
    private var runner: InMemoryRunner? = null
    private var sessionId: String? = null

    override fun respond(prompt: String): Flow<AssistantReply> = flow {
        val runner = runner ?: createRunner()
        val sessionId = sessionId ?: createSession(runner)
        lastPrompt = prompt
        completedTools.clear()
        val responseId = "adk-${turnCount++}"
        val accumulated = StringBuilder()
        // A turn can run several model rounds (one per tool batch). The reply
        // bubble shows the text of one round at a time, so a partial arriving
        // after a round boundary starts the bubble over.
        var roundBoundary = false

        Log.d(TAG, "respond: prompt='$prompt'")

        try {
            runner.runAsync(
                userId = USER_ID,
                sessionId = sessionId,
                newMessage = Content(role = Role.USER, parts = listOf(Part(text = prompt))),
                runConfig = RunConfig(streamingMode = StreamingMode.SSE),
            ).collect { event ->
                if (event.partial) {
                    val chunk = event.content?.text().orEmpty()
                    if (chunk.isNotEmpty()) {
                        if (roundBoundary) {
                            accumulated.setLength(0)
                            roundBoundary = false
                        }
                        accumulated.append(chunk)
                        emit(AssistantReply.Text(accumulated.toString(), id = responseId))
                    }
                } else {
                    // SSE yields partial chunks plus an aggregated final event per
                    // round. The aggregate holds the round's full text: emitting it
                    // is a no-op when the round streamed, and the whole reply when
                    // it did not.
                    val final = event.content?.text().orEmpty()
                    if (final.isNotEmpty()) {
                        accumulated.setLength(0)
                        accumulated.append(final)
                        emit(AssistantReply.Text(final, id = responseId))
                    }
                    roundBoundary = true
                }
                event.errorCode?.let { code ->
                    throw IOException("Model returned $code: ${event.errorMessage.orEmpty()}")
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ModelUnavailableException) {
            throw e
        } catch (e: IOException) {
            throw e
        } catch (e: ChatCompletionsApiException) {
            throw e.toBackendException()
        } catch (e: Exception) {
            Log.e(TAG, "generation failed", e)
            throw IOException(e.message ?: e.javaClass.simpleName, e)
        }

        // Drain tool results: cards as cards, card-less tool messages as text so
        // the user sees something when the model stayed silent.
        Log.d(TAG, "flow complete: accumulated=${accumulated.length} chars, tools=${completedTools.size}")
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
        if (accumulated.isEmpty() && !toolFired) {
            Log.w(TAG, "Failsafe: no text and no tools fired")
            emit(AssistantReply.Text(appContext.getString(R.string.assistant_fallback_reply), id = responseId))
        }
    }.flowOn(Dispatchers.IO)

    override fun retry(): Flow<AssistantReply>? = lastPrompt?.let { respond(it) }

    /** Closes the model if it is `AutoCloseable` (LitertLmModel's conversation; `ChatCompletions` is a no-op). */
    override fun close() {
        (model as? AutoCloseable)?.let { runCatching { it.close() } }
        model = null
        runner = null
        sessionId = null
    }

    private fun createRunner(): InMemoryRunner {
        val model = modelFactory().also { this.model = it }
        // Instruction.Provider resolves per turn, so the clock line stays
        // current: ChatCompletions sends it on every request. LitertLmModel
        // ignores it — the Conversation's instruction is frozen at creation.
        val agent = LlmAgent(
            name = "assistant",
            model = model,
            instruction = Instruction {
                Content(parts = listOf(Part(text = systemInstructionLines().joinToString(" "))))
            },
            // When false, LiteRT-LM drives its own tool loop inside the
            // Conversation; there is nothing for ADK to dispatch.
            tools = if (useAdkTools) adkTools(appContext, completedTools::add) else emptyList(),
        )
        return InMemoryRunner(agent, appName = "karen").also { runner = it }
    }

    private suspend fun createSession(runner: InMemoryRunner): String {
        val session = runner.sessionService.createSession(SessionKey("karen", USER_ID, id = null))
        return checkNotNull(session.key.id).also { sessionId = it }
    }

    /** Mirrors the error split the old OpenAiClient made from HTTP status codes. */
    private fun ChatCompletionsApiException.toBackendException(): Exception = when (statusCode) {
        401, 403 -> ModelUnavailableException("Endpoint rejected the API key: $message", this)
        404 -> ModelUnavailableException("Not found — check the base URL and model: $message", this)
        400, 422 -> ModelUnavailableException("Endpoint rejected the request: $message", this)
        else -> IOException(message ?: "HTTP $statusCode", this)
    }

    private companion object {
        const val USER_ID = "user"
    }
}
