package com.mahasvan.karen.provider.tensor

import com.google.ai.edge.litertlm.ToolParam
import com.mahasvan.karen.tools.ToolRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import com.google.ai.edge.litertlm.Tool as ToolAnnotation

/**
 * [TensorAssistantBackend.AssistantToolSet]'s `@Tool`/`@ToolParam` annotations are
 * a hand-written copy of [ToolRegistry]'s schema — annotations are compile-time
 * and cannot be generated from data (see [com.mahasvan.karen.tools.OpenAiToolSchema]
 * for the copy that can). Nothing else catches the two drifting: `Map<String, String>`
 * arguments compile clean whether or not the keys agree with what the tool reads.
 *
 * This test is that guard. It reflects over every `@Tool` method exactly the way
 * LiteRT-LM's `ReflectionTool` does at runtime — same camelCase-to-snake_case name
 * derivation, same `KFunction.parameters` walk — so a mismatch here is a mismatch
 * the model would actually see.
 */
class AssistantToolSetParityTest {

    @Test
    fun everyToolMethodMatchesItsRegistryEntry() {
        val methods = TensorAssistantBackend.AssistantToolSet::class.declaredMemberFunctions
            .filter { it.findAnnotation<ToolAnnotation>() != null }

        assertTrue("Expected at least one @Tool method", methods.isNotEmpty())

        for (method in methods) {
            val toolName = camelToSnakeCase(method.name)
            val toolAnnotation = method.findAnnotation<ToolAnnotation>()!!
            val canonical = ToolRegistry.tool(toolName)
                ?: error("AssistantToolSet.${method.name} has no ToolRegistry entry named '$toolName'")

            assertEquals(
                "description mismatch for ${method.name}",
                canonical.description,
                toolAnnotation.description,
            )

            // Parameter 0 is the instance receiver; the rest are the declared arguments.
            // Parameter names go through the same snake_case conversion as the method
            // name (LiteRT-LM's ReflectionTool.toModelParamName), so `startTime` is
            // compared against the model-facing `start_time`, not the raw Kotlin name.
            val params = method.parameters.drop(1)
            val modelParamNames = params.map { camelToSnakeCase(it.name!!) }
            assertEquals(
                "parameter name/order mismatch for ${method.name}",
                canonical.parameters.map { it.name },
                modelParamNames,
            )

            params.forEach { param ->
                val modelName = camelToSnakeCase(param.name!!)
                val expected = canonical.parameters.first { it.name == modelName }
                val annotation = param.findAnnotation<ToolParam>()
                    ?: error("AssistantToolSet.${method.name}(${param.name}) is missing @ToolParam")
                assertEquals(
                    "description mismatch for ${method.name}(${param.name})",
                    expected.description,
                    annotation.description,
                )
            }
        }
    }
}

/**
 * Mirrors LiteRT-LM's private `ReflectionTool.camelToSnakeCase` exactly (decompiled
 * from `litertlm-android`), so this asserts against the name the runtime will
 * actually advertise to the model, not an approximation of it. Applied to both
 * method and parameter names — `ReflectionTool.toModelParamName` uses the same
 * conversion, gated on `ExperimentalFlags.convertCamelToSnakeCaseInToolDescription`,
 * which defaults `true` and is never overridden by [TensorAssistantBackend].
 */
private fun camelToSnakeCase(value: String): String =
    Regex("(?<=[a-zA-Z])[A-Z]").replace(value) { "_${it.value}" }.lowercase()
