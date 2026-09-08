package com.mahasvan.karen.tools

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable

/**
 * One capability the assistant can invoke: setting an alarm, starting a timer, adding a
 * calendar event, placing a call. A tool owns everything about itself — the schema it
 * advertises to a model, how it turns that model's arguments into an action, the system
 * intent that fulfils the action, and how its card renders.
 *
 * Adding a capability means adding a directory under `tools/` and one line in
 * [ToolRegistry]. Nothing else in the app changes.
 *
 * These types are deliberately **not** sealed. An open set of tools and a compiler-checked
 * exhaustive `when` are mutually exclusive, and independent extension is the point here, so
 * dispatch is a registry lookup instead. The cost is that a malformed [ToolCard]/[Tool]
 * pairing fails at runtime rather than at compile time; [ToolRegistry.tool] is the only
 * place that can happen.
 */
interface Tool {

    /** Function name advertised to a model. Stable: it is persisted inside [ToolCard.toolName]. */
    val name: String

    /** Natural-language description handed to the model when advertising this tool. */
    val description: String

    val parameters: List<ToolParameter>

    /**
     * Builds an action from the arguments a model produced, or null when they are unusable.
     *
     * Model output is untrusted input — a prompt-injected model can propose any arguments it
     * likes — so implementations must validate here rather than at the call site.
     */
    fun parse(arguments: Map<String, String>): ToolAction?

    /**
     * The confirmation card for [action], or null when the action should go straight to the
     * platform. The tool decides whether its own capability warrants a confirmation step, so
     * backends never need to know which capabilities are confirmable.
     */
    fun card(action: ToolAction): ToolCard? = null

    /**
     * Renders a card this tool produced. [card] is always one of this tool's own types, so
     * the cast inside is safe; [ToolRegistry] guarantees the pairing.
     *
     * Tools that only hand off and never produce a card need not override this.
     */
    @Composable
    fun CardContent(card: ToolCard, onConfirm: () -> Unit, onUpdate: (ToolCard) -> Unit) {
    }
}

@Immutable
data class ToolParameter(
    val name: String,
    val description: String,
    val required: Boolean,
)

/** Work that leaves the app. Karen builds the intent; the platform owns what happens next. */
interface ToolAction {
    fun intent(context: Context): Intent
}

/** A confirmable result rendered into the conversation. */
interface ToolCard {

    /** Which [Tool] produced this card, and therefore which one renders it. */
    val toolName: String

    /** The action committed when the user confirms the card. */
    fun action(): ToolAction
}
