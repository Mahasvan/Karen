package com.mahasvan.karen.tools

import android.content.Context

/**
 * Looks the tool name up in [ToolRegistry], executes it, and returns the
 * status map a model sees (`{"status": "success"}` or `{"status": "error", ...}`).
 * The full [ToolResult] goes to `onResult` callback
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
