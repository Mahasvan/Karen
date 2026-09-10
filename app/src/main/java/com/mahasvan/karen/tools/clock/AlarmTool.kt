package com.mahasvan.karen.tools.clock

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.mahasvan.karen.tools.Tool
import com.mahasvan.karen.tools.ToolCard
import com.mahasvan.karen.tools.ToolParameter
import com.mahasvan.karen.tools.ToolResult
import com.mahasvan.karen.ui.components.ActionCard

const val ALARM_TOOL = "set_alarm"

object AlarmTool : Tool {

    override val name = ALARM_TOOL

    override val description =
        "Set an alarm at a specific time of day on the user's device."

    override val parameters = listOf(
        ToolParameter("hour", "Hour in 24-hour form, 0-23.", required = true),
        ToolParameter("minute", "Minute past the hour, 0-59. Defaults to 0.", required = false),
        ToolParameter("label", "Short name shown on the alarm.", required = false),
    )

    override fun execute(context: Context, arguments: Map<String, String>): ToolResult {
        val hour = arguments["hour"]?.toIntOrNull()?.takeIn(0..23)
            ?: return ToolResult(false, "Invalid hour")
        val minute = arguments["minute"]?.toIntOrNull()?.takeIn(0..59) ?: 0
        val label = arguments["label"].orEmpty()
        return try {
            context.startActivity(
                Intent(AlarmClock.ACTION_SET_ALARM).apply {
                    putExtra(AlarmClock.EXTRA_HOUR, hour)
                    putExtra(AlarmClock.EXTRA_MINUTES, minute)
                    putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, false)
                }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            ToolResult(true, "Alarm set", AlarmCard(hour, minute, label))
        } catch (_: ActivityNotFoundException) {
            ToolResult(false, "No clock app available")
        }
    }

    @Composable
    override fun CardContent(card: ToolCard) {
        val alarm = card as AlarmCard
        ActionCard(
            icon = Icons.Outlined.Alarm,
            title = formatClock(alarm.hour, alarm.minute),
            subtitle = alarm.label,
        )
    }
}

@Immutable
data class AlarmCard(
    val hour: Int,
    val minute: Int,
    val label: String,
) : ToolCard {
    override val toolName = ALARM_TOOL
}

internal fun Int.takeIn(range: IntRange): Int? = takeIf { it in range }
