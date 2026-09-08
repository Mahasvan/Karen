package com.mahasvan.karen.assistant

import java.util.Calendar
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Keyword matching standing in for real language understanding. Everything here is
 * throwaway: a LiteRT backend replaces the whole class and only has to keep producing
 * [AssistantReply] values.
 */
class StubAssistantBackend : AssistantBackend {

    override fun respond(prompt: String): Flow<AssistantReply> = flow {
        delay(700)
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

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AssistantReply>.emitDial(text: String) {
        val digits = DIGITS.find(text)?.value
        val target = digits ?: text
            .substringAfter("call", "")
            .substringAfter("dial", "")
            .removePrefix(" to ")
            .trim()
            .ifBlank { "" }
        if (target.isBlank()) {
            emit(AssistantReply.Text("Who would you like to call?"))
            return
        }
        emit(AssistantReply.Text("Opening your dialer for $target"))
        emit(AssistantReply.Handoff(AssistantAction.Dial(target)))
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AssistantReply>.emitTimer(text: String) {
        val match = DURATION.find(text)
        val amount = match?.groupValues?.get(1)?.toIntOrNull() ?: 5
        val seconds = when (match?.groupValues?.get(2)?.take(1)) {
            "h" -> amount * 3600
            "s" -> amount
            else -> amount * 60
        }
        emit(AssistantReply.Text("Timer for ${formatDuration(seconds)}"))
        emit(AssistantReply.Card(RichCard.Timer(label = "Timer", totalSeconds = seconds)))
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AssistantReply>.emitAlarm(text: String) {
        val match = CLOCK_TIME.find(text)
        var hour = match?.groupValues?.get(1)?.toIntOrNull() ?: 7
        val minute = match?.groupValues?.get(2)?.toIntOrNull() ?: 0
        val meridiem = match?.groupValues?.get(3)
        if (meridiem == "pm" && hour < 12) hour += 12
        if (meridiem == "am" && hour == 12) hour = 0
        emit(AssistantReply.Text("Alarm for ${formatClock(hour, minute)}"))
        emit(
            AssistantReply.Card(
                RichCard.Alarm(label = "Alarm", hour = hour, minute = minute, enabled = true)
            )
        )
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<AssistantReply>.emitEvent(prompt: String) {
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
        emit(
            AssistantReply.Card(
                RichCard.CalendarEvent(
                    title = title,
                    startMillis = start,
                    endMillis = start + 3_600_000L,
                )
            )
        )
    }

    override fun transcribe(): Flow<String> = flow {
        val utterance = STUB_UTTERANCES.random()
        val spoken = StringBuilder()
        utterance.split(" ").forEach { word ->
            delay(260)
            if (spoken.isNotEmpty()) spoken.append(' ')
            spoken.append(word)
            emit(spoken.toString())
        }
        delay(400)
    }

    private companion object {
        val DIGITS = Regex("""[+\d][\d\s\-]{5,}""")
        val DURATION = Regex("""(\d+)\s*(hour|hr|minute|min|second|sec)""")
        val CLOCK_TIME = Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""")
        val EVENT_PREFIX = Regex(
            """(?i)\b(schedule|add|create|set up|put)\b|\b(a |an |the )?(meeting|event|reminder)\b|\bto my calendar\b|\bcalendar\b|\bremind me to\b"""
        )

        val STUB_UTTERANCES = listOf(
            "set an alarm for 7 am",
            "set a timer for 10 minutes",
            "schedule a meeting with the design team",
            "call 555 0134",
        )
    }
}

fun formatClock(hour: Int, minute: Int): String {
    val suffix = if (hour < 12) "AM" else "PM"
    val display = when {
        hour % 12 == 0 -> 12
        else -> hour % 12
    }
    return "%d:%02d %s".format(display, minute, suffix)
}

fun formatDuration(totalSeconds: Int): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return when {
        hours > 0 -> "%d:%02d:%02d".format(hours, minutes, seconds)
        else -> "%d:%02d".format(minutes, seconds)
    }
}
