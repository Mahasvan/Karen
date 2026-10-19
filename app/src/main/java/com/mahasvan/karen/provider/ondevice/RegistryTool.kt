package com.mahasvan.karen.provider.ondevice

import android.content.Context
import com.google.ai.edge.litertlm.OpenApiTool
import com.mahasvan.karen.tools.Tool
import com.mahasvan.karen.tools.ToolExecutor
import com.mahasvan.karen.tools.ToolParamType
import com.mahasvan.karen.tools.ToolResult
import org.json.JSONObject
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Adapts a [Tool] to LiteRT-LM's data-driven [OpenApiTool] interface, so the
 * schema the model sees is generated from the same [Tool.parameters] the
 * remote backend uses — `ToolRegistry` stays the single source of truth and no
 * hand-copied `@Tool`/`@ToolParam` annotations are needed.
 *
 * `execute` receives the model's raw argument JSON; it is flattened to strings
 * (the contract [ToolExecutor] already enforces) and its `{"status": ...}`
 * result serialized back for the model. The full [ToolResult] is queued for
 * the card drain.
 */
internal class RegistryTool(
    private val tool: Tool,
    private val context: Context,
    private val completedTools: ConcurrentLinkedQueue<ToolResult>,
) : OpenApiTool {

    override fun getToolDescriptionJsonString(): String = JSONObject()
        .put("name", tool.name)
        .put("description", tool.description)
        .put(
            "parameters",
            JSONObject()
                .put("type", "object")
                .put(
                    "properties",
                    tool.parameters.fold(JSONObject()) { acc, p ->
                        acc.put(
                            p.name,
                            JSONObject()
                                .put("type", p.type.jsonType)
                                .put("description", p.description),
                        )
                    },
                )
                .put("required", tool.parameters.filter { it.required }.map { it.name }),
        )
        .toString()

    override fun execute(jsonArgs: String): String {
        val args = JSONObject(jsonArgs)
        val result = ToolExecutor.execute(
            context,
            tool.name,
            args.keys().asSequence().associate { it to args.optString(it) },
            onResult = completedTools::add,
        )
        return JSONObject(result as Map<*, *>).toString()
    }

    private val ToolParamType.jsonType: String
        get() = when (this) {
            ToolParamType.String -> "string"
            ToolParamType.Integer -> "integer"
            ToolParamType.Number -> "number"
            ToolParamType.Boolean -> "boolean"
        }
}
