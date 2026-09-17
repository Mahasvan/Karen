package com.mahasvan.karen.tools

import android.content.Context

/**
 * The validate → execute → return-status sequence every model-driven tool loop
 * needs, factored out of the LiteRT reflection shim so an OpenAI-style manual
 * loop can call the same path instead of re-implementing it.
 *
 * Looks [toolName] up in [ToolRegistry], executes it, and returns the simple
 * status map a model sees (`{"status": "success"}` or `{"status": "error", ...}`).
 * The full [ToolResult] — including its card — goes to [onResult] so the caller
 * can queue or emit it however its own transport needs; it never reaches the
 * model, which would otherwise see a Compose UI type in its JSON context.
 */
object ToolExecutor {

    fun execute(
        context: Context,
        toolName: String,
        arguments: Map<String, String>,
        onResult: (ToolResult) -> Unit,
    ): Map<String, String> {
        val tool = ToolRegistry.tool(toolName)
            ?: return mapOf("status" to "error", "message" to "Unknown tool: $toolName")
        val result = tool.execute(context, arguments)
        onResult(result)
        return if (result.success) {
            mapOf("status" to "success")
        } else {
            mapOf("status" to "error", "message" to result.message)
        }
    }
}
