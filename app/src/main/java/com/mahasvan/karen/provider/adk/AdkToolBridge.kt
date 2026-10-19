package com.mahasvan.karen.provider.adk

import android.content.Context
import com.google.adk.kt.tools.BaseTool
import com.google.adk.kt.tools.ToolContext
import com.google.adk.kt.types.FunctionDeclaration
import com.google.adk.kt.types.Schema
import com.google.adk.kt.types.Type
import com.mahasvan.karen.tools.Tool
import com.mahasvan.karen.tools.ToolExecutor
import com.mahasvan.karen.tools.ToolParamType
import com.mahasvan.karen.tools.ToolRegistry
import com.mahasvan.karen.tools.ToolResult

/**
 * Maps [ToolRegistry.tools] onto ADK [BaseTool]s for the remote (ADK) backend,
 * reading the registry as the single source of truth for names, descriptions
 * and parameter schemas. ADK drives the tool loop itself and takes
 * declarations from here.
 *
 * [onResult] receives the full [ToolResult] of every execution — the card for
 * the UI, the message either way. The model only sees the `{"status": ...}`
 * map [ToolExecutor] returns.
 */
fun adkTools(context: Context, onResult: (ToolResult) -> Unit): List<BaseTool> =
    ToolRegistry.tools.map { AdkTool(it, context, onResult) }

private class AdkTool(
    private val tool: Tool,
    private val androidContext: Context,
    private val onResult: (ToolResult) -> Unit,
) : BaseTool(name = tool.name, description = tool.description) {

    override fun declaration(): FunctionDeclaration = FunctionDeclaration(
        name = tool.name,
        description = tool.description,
        parameters = Schema(
            type = Type.OBJECT,
            properties = tool.parameters.associate { param ->
                param.name to Schema(type = param.type.toAdkType(), description = param.description)
            },
            required = tool.parameters.filter { it.required }.map { it.name },
        ),
    )

    override suspend fun run(context: ToolContext, args: Map<String, Any?>): Any =
        ToolExecutor.execute(
            androidContext,
            tool.name,
            args.mapValues { it.value?.toString().orEmpty() },
            onResult,
        )

    private fun ToolParamType.toAdkType(): Type = when (this) {
        ToolParamType.String -> Type.STRING
        ToolParamType.Integer -> Type.INTEGER
        ToolParamType.Number -> Type.NUMBER
        ToolParamType.Boolean -> Type.BOOLEAN
    }
}
