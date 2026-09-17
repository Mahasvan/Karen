package com.mahasvan.karen.tools

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * One capability the assistant can invoke: setting an alarm, starting a timer,
 * adding a calendar event, placing a call. A tool owns its schema, argument
 * validation, intent firing, and card rendering.
 *
 * [execute] fires the system intent immediately and returns a [ToolResult] with an
 * optional informational card. There is no confirmation step — the card is a
 * visual record, not a gate. Sensitive actions are confirmed by the model through
 * conversation before calling the tool.
 *
 * Adding a capability means adding a directory under `tools/` and one line in
 * [ToolRegistry].
 */
interface Tool {

    /** Function name advertised to a model. Stable: it is persisted inside [ToolCard.toolName]. */
    val name: String

    /** Natural-language description handed to the model when advertising this tool. */
    val description: String

    val parameters: List<ToolParameter>

    /**
     * Validates the model-produced arguments, fires the system intent, and returns
     * a result with an optional card. The intent has already fired by the time
     * this returns.
     *
     * Model output is untrusted — implementations must validate here rather than
     * at the call site.
     */
    fun execute(context: Context, arguments: Map<String, String>): ToolResult

    /**
     * Renders a card this tool produced. [card] is always one of this tool's own
     * types — [ToolRegistry] guarantees the pairing. Tools that don't produce a
     * card need not override this.
     */
    @Composable
    fun CardContent(card: ToolCard) {
    }
}

/** JSON Schema primitive types a tool parameter can advertise. */
enum class ToolParamType { String, Integer, Number, Boolean }

@Immutable
data class ToolParameter(
    val name: String,
    val description: String,
    val required: Boolean,
    val type: ToolParamType = ToolParamType.String,
)

/**
 * The outcome of calling [Tool.execute]. [card] is present only when the tool
 * succeeded and has something to show; it is informational, not confirmational.
 */
@Immutable
data class ToolResult(
    val success: Boolean,
    val message: String,
    val card: ToolCard? = null,
)

/** An informational card rendered into the conversation after a tool fires. */
interface ToolCard {

    /** Which [Tool] produced this card, and therefore which one renders it. */
    val toolName: String
}
