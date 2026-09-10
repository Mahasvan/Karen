package com.mahasvan.karen.tools

import com.mahasvan.karen.tools.ToolRegistry.tool
import com.mahasvan.karen.tools.ToolRegistry.tools
import com.mahasvan.karen.tools.calendar.CalendarEventTool
import com.mahasvan.karen.tools.clock.AlarmTool
import com.mahasvan.karen.tools.clock.TimerTool
import com.mahasvan.karen.tools.telephony.CallTool

/**
 * Single registration point for all tools. A new capability is a new directory
 * under `tools/` plus one entry here. Backends advertise [tools] to their model
 * and route the model's chosen function name back through [tool]; the UI
 * resolves a card's renderer the same way.
 */
object ToolRegistry {

    val tools: List<Tool> = listOf(
        AlarmTool,
        TimerTool,
        CalendarEventTool,
        CallTool,
    )

    private val byName: Map<String, Tool> = tools.associateBy { it.name }

    fun tool(name: String): Tool? = byName[name]

    fun tool(card: ToolCard): Tool? = byName[card.toolName]
}
