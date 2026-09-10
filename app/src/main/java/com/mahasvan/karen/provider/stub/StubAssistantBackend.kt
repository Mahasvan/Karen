package com.mahasvan.karen.provider.stub

import android.content.Context
import com.mahasvan.karen.assistant.AssistantBackend
import com.mahasvan.karen.assistant.AssistantReply
import com.mahasvan.karen.tools.ToolRegistry
import com.mahasvan.karen.tools.calendar.CALENDAR_EVENT_TOOL
import com.mahasvan.karen.tools.clock.ALARM_TOOL
import com.mahasvan.karen.tools.clock.TIMER_TOOL
import com.mahasvan.karen.tools.clock.formatClock
import com.mahasvan.karen.tools.clock.formatDuration
import com.mahasvan.karen.tools.telephony.CALL_TOOL
import java.util.Calendar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.milliseconds

/**
 * Keyword matching standing in for real language understanding. Each branch picks
 * a tool name and arguments, then hands them to [ToolRegistry] — exactly as a
 * real backend would. The tool fires the intent and returns a card; this backend
 * emits the card into the conversation.
 */
@Suppress("unused") // retained for testing and offline development
class StubAssistantBackend(
    private val context: Context,
) : AssistantBackend {

    /**
     * Committed turns, oldest first. Keyword matching never reads this; the list
     * exercises the same commit-on-completion contract a real backend depends on.
     */
    private val history = mutableListOf<HistoryEntry>()
    private var lastPrompt: String? = null

    override fun respond(prompt: String): Flow<AssistantReply> = flow {
        lastPrompt = prompt
        val pending = mutableListOf<HistoryEntry>(HistoryEntry.User(prompt))
        produceReplies(prompt).collect { reply ->
            emit(reply)
            pending += HistoryEntry.Reply(reply)
        }
        // Reached only on normal completion: a failed or cancelled turn leaves history
        // exactly the way the retry that follows it needs.
        history += pending
    }

    override fun retry(): Flow<AssistantReply>? = lastPrompt?.let { respond(it) }

    override fun close() {
        // Nothing to release — the stub holds no native resources.
    }

    private fun produceReplies(prompt: String): Flow<AssistantReply> = flow {
        delay(700.milliseconds)
        val text = prompt.lowercase()
        when {
            "call" in text || "dial" in text || "phone" in text -> emitDial(text)
            "timer" in text -> emitTimer(text)
            "alarm" in text || "wake me" in text -> emitAlarm(text)
            "meeting" in text || "event" in text || "schedule" in text ||
                "calendar" in text || "remind" in text -> emitEvent(prompt)

            else -> emit(
                AssistantReply.Text(
                    "I can only set alarms and timers, add calendar events, or place a call."
                )
            )
        }
    }

    private suspend fun FlowCollector<AssistantReply>.emitDial(text: String) {
        val digits = DIGITS.find(text)?.value
        val target = digits ?: text
            .substringAfter("call", "")
            .substringAfter("dial", "")
            .removePrefix(" to ")
            .trim()
        if (target.isBlank()) {
            emit(AssistantReply.Text("Who would you like to call?"))
            return
        }
        emit(AssistantReply.Text("Opening your dialer for $target"))
        route(CALL_TOOL, mapOf("target" to target))
    }

    private suspend fun FlowCollector<AssistantReply>.emitTimer(text: String) {
        val match = DURATION.find(text)
        val amount = match?.groupValues?.get(1)?.toIntOrNull() ?: 5
        val seconds = when (match?.groupValues?.get(2)?.take(1)) {
            "h" -> amount * 3600
            "s" -> amount
            else -> amount * 60
        }
        emit(AssistantReply.Text("Timer for ${formatDuration(seconds)}"))
        route(TIMER_TOOL, mapOf("seconds" to seconds.toString(), "label" to "Timer"))
    }

    private suspend fun FlowCollector<AssistantReply>.emitAlarm(text: String) {
        val match = CLOCK_TIME.find(text)
        var hour = match?.groupValues?.get(1)?.toIntOrNull() ?: 7
        val minute = match?.groupValues?.get(2)?.toIntOrNull() ?: 0
        val meridiem = match?.groupValues?.get(3)
        if (meridiem == "pm" && hour < 12) hour += 12
        if (meridiem == "am" && hour == 12) hour = 0
        emit(AssistantReply.Text("Alarm for ${formatClock(hour, minute)}"))
        route(
            ALARM_TOOL,
            mapOf(
                "hour" to hour.toString(),
                "minute" to minute.toString(),
                "label" to "Alarm",
            )
        )
    }

    private suspend fun FlowCollector<AssistantReply>.emitEvent(prompt: String) {
        val title = prompt
            .replace(EVENT_PREFIX, "")
            .trim()
            .ifBlank { "New event" }
            .replaceFirstChar(Char::uppercase)
        val start = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, 1)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }.timeInMillis
        emit(AssistantReply.Text("Here's the event"))
        route(
            CALENDAR_EVENT_TOOL,
            mapOf(
                "title" to title,
                "start_millis" to start.toString(),
                "end_millis" to (start + ONE_HOUR_MILLIS).toString(),
            )
        )
    }

    /**
     * Look the tool up, execute it (which fires the intent and returns a card),
     * and emit the card if the tool produced one.
     */
    private suspend fun FlowCollector<AssistantReply>.route(
        toolName: String,
        arguments: Map<String, String>,
    ) {
        val tool = ToolRegistry.tool(toolName) ?: return
        val result = tool.execute(context, arguments)
        result.card?.let { emit(AssistantReply.Card(it)) }
    }

    private sealed interface HistoryEntry {
        data class User(val text: String) : HistoryEntry
        data class Reply(val reply: AssistantReply) : HistoryEntry
    }

    private companion object {
        const val ONE_HOUR_MILLIS = 3_600_000L

        val DIGITS = Regex("""[+\d][\d\s\-]{5,}""")
        val DURATION = Regex("""(\d+)\s*(hour|hr|minute|min|second|sec)""")
        val CLOCK_TIME = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""")
        val EVENT_PREFIX = Regex(
            """(?i)\b(schedule|add|create|set up|put)\b|\b(a |an |the )?(meeting|event|reminder)\b|\bto my calendar\b|\bcalendar\b|\bremind me to\b"""
        )
    }
}
