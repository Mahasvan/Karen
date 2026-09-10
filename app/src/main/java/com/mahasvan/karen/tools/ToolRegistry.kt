package com.mahasvan.karen.tools

import com.mahasvan.karen.tools.calendar.CalendarEventTool
import com.mahasvan.karen.tools.clock.AlarmTool
import com.mahasvan.karen.tools.clock.TimerTool
import com.mahasvan.karen.tools.telephony.CallTool

/**
 * The tools the app exposes. This list is the single registration point: a new capability is
 * a new directory under `tools/` plus one entry here.
 *
 * A backend advertises [tools] to its model and routes the model's chosen function name
 * back through [tool]; the UI resolves a card's renderer the same way.
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
