package com.mahasvan.karen.tools.calendar

import android.content.Context
import android.content.Intent
import android.provider.CalendarContract
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Event
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.mahasvan.karen.R
import com.mahasvan.karen.tools.Tool
import com.mahasvan.karen.tools.ToolAction
import com.mahasvan.karen.tools.ToolCard
import com.mahasvan.karen.tools.ToolParameter
import com.mahasvan.karen.ui.components.ActionCard
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

const val CALENDAR_EVENT_TOOL = "create_calendar_event"

private const val ONE_HOUR_MILLIS = 3_600_000L

object CalendarEventTool : Tool {

    override val name = CALENDAR_EVENT_TOOL

    override val description = "Add an event to the user's calendar."

    override val parameters = listOf(
        ToolParameter("title", "Title of the event.", required = true),
        ToolParameter("start_millis", "Start time as a Unix epoch timestamp in milliseconds.", required = true),
        ToolParameter("end_millis", "End time in milliseconds. Defaults to one hour after the start.", required = false),
    )

    override fun parse(arguments: Map<String, String>): ToolAction? {
        val title = arguments["title"]?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val start = arguments["start_millis"]?.toLongOrNull() ?: return null
        val end = arguments["end_millis"]?.toLongOrNull() ?: (start + ONE_HOUR_MILLIS)
        if (end < start) return null
        return CalendarEventAction(title, start, end)
    }

    override fun card(action: ToolAction): ToolCard? {
        val event = action as? CalendarEventAction ?: return null
        return CalendarEventCard(event.title, event.startMillis, event.endMillis)
    }

    @Composable
    override fun CardContent(
        card: ToolCard,
        onConfirm: () -> Unit,
        onUpdate: (ToolCard) -> Unit,
    ) {
        val event = card as CalendarEventCard
        ActionCard(
            icon = Icons.Outlined.Event,
            title = event.title,
            subtitle = formatEventStart(event.startMillis),
            confirmLabel = stringResource(R.string.card_confirm_event),
            onConfirm = onConfirm,
        )
    }
}

/**
 * Resolved per call rather than cached in a field, so a locale or time-zone change while the
 * process is alive is picked up instead of being frozen at class-init.
 */
private fun formatEventStart(startMillis: Long): String =
    DateTimeFormatter.ofPattern("EEE d MMM, h:mm a")
        .withZone(ZoneId.systemDefault())
        .format(Instant.ofEpochMilli(startMillis))

@Immutable
data class CalendarEventAction(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
) : ToolAction {
    override fun intent(context: Context): Intent =
        Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis)
        }
}

@Immutable
data class CalendarEventCard(
    val title: String,
    val startMillis: Long,
    val endMillis: Long,
) : ToolCard {
    override val toolName = CALENDAR_EVENT_TOOL
    override fun action(): ToolAction = CalendarEventAction(title, startMillis, endMillis)
}
