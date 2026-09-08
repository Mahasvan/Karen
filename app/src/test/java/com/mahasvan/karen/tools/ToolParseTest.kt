package com.mahasvan.karen.tools

import com.mahasvan.karen.tools.calendar.CALENDAR_EVENT_TOOL
import com.mahasvan.karen.tools.calendar.CalendarEventAction
import com.mahasvan.karen.tools.clock.ALARM_TOOL
import com.mahasvan.karen.tools.clock.AlarmAction
import com.mahasvan.karen.tools.clock.TIMER_TOOL
import com.mahasvan.karen.tools.clock.TimerAction
import com.mahasvan.karen.tools.telephony.CALL_TOOL
import com.mahasvan.karen.tools.telephony.CallAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Model output is untrusted input; these pin the validation boundary of every tool's
 * parse() so a real provider swapping in keeps the same contract.
 */
class ToolParseTest {

    private fun parse(tool: String, args: Map<String, String>) =
        ToolRegistry.tool(tool)!!.parse(args)

    @Test
    fun `alarm parses and clamps`() {
        assertEquals(AlarmAction(7, 30, "Gym"), parse(ALARM_TOOL, mapOf("hour" to "7", "minute" to "30", "label" to "Gym")))
        assertEquals(AlarmAction(7, 0, ""), parse(ALARM_TOOL, mapOf("hour" to "7")))
        assertNull(parse(ALARM_TOOL, mapOf("hour" to "25")))
        assertNull(parse(ALARM_TOOL, mapOf("minute" to "30")))
    }

    @Test
    fun `timer requires seconds in range`() {
        assertEquals(TimerAction(600, ""), parse(TIMER_TOOL, mapOf("seconds" to "600")))
        assertNull(parse(TIMER_TOOL, mapOf("seconds" to "0")))
        assertNull(parse(TIMER_TOOL, mapOf("seconds" to "99999")))
    }

    @Test
    fun `event defaults to one hour and rejects inverted range`() {
        val action = parse(CALENDAR_EVENT_TOOL, mapOf("title" to "Lunch", "start_millis" to "1000")) as CalendarEventAction
        assertEquals(1000L, action.startMillis)
        assertEquals(1000L + 3_600_000L, action.endMillis)
        assertNull(parse(CALENDAR_EVENT_TOOL, mapOf("title" to "Lunch", "start_millis" to "2000", "end_millis" to "1000")))
        assertNull(parse(CALENDAR_EVENT_TOOL, mapOf("start_millis" to "1000")))
    }

    @Test
    fun `call requires a non-blank target`() {
        assertEquals(CallAction("555 0134"), parse(CALL_TOOL, mapOf("target" to "555 0134")))
        assertNull(parse(CALL_TOOL, mapOf("target" to "  ")))
    }

    @Test
    fun `only card-carrying tools produce cards`() {
        val alarm = parse(ALARM_TOOL, mapOf("hour" to "7"))!!
        val alarmCard = ToolRegistry.tool(ALARM_TOOL)!!.card(alarm)
        assertTrue(alarmCard != null)
        assertEquals(alarm, alarmCard!!.action())

        val call = parse(CALL_TOOL, mapOf("target" to "555 0134"))!!
        assertNull(ToolRegistry.tool(CALL_TOOL)!!.card(call))
    }
}
