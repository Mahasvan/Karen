package com.mahasvan.karen.tools

/**
 * Maps [ToolRegistry.tools] onto the OpenAI chat-completions `tools` array
 * (a list of `{"type": "function", "function": {...}}` entries), reading
 * [Tool.parameters] as the single source of truth. This is the only tool schema
 * built from data; the LiteRT reflection shim's `@Tool`/`@ToolParam` annotations
 * are a separate, hand-written copy guarded by a parity test, because
 * annotations are compile-time and cannot be generated from a list.
 *
 * Returned as a plain `Map`/`List` tree rather than a serialized string — the
 * OpenAI backend that eventually sends this picks its own JSON library; this
 * function only owns the shape.
 */
object OpenAiToolSchema {

    fun functions(): List<Map<String, Any>> = ToolRegistry.tools.map { it.toFunctionSchema() }

    private fun Tool.toFunctionSchema(): Map<String, Any> = mapOf(
        "type" to "function",
        "function" to mapOf(
            "name" to name,
            "description" to description,
            "parameters" to mapOf(
                "type" to "object",
                "properties" to parameters.associate { it.name to it.toPropertySchema() },
                "required" to parameters.filter { it.required }.map { it.name },
            ),
        ),
    )

    private fun ToolParameter.toPropertySchema(): Map<String, Any> = mapOf(
        "type" to type.jsonSchemaType,
        "description" to description,
    )

    private val ToolParamType.jsonSchemaType: String
        get() = when (this) {
            ToolParamType.String -> "string"
            ToolParamType.Integer -> "integer"
            ToolParamType.Number -> "number"
            ToolParamType.Boolean -> "boolean"
        }
}
