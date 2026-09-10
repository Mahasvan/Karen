package com.mahasvan.karen.tools.clock

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import com.mahasvan.karen.tools.Tool
import com.mahasvan.karen.tools.ToolCard
import com.mahasvan.karen.tools.ToolParameter
import com.mahasvan.karen.tools.ToolResult
import com.mahasvan.karen.ui.components.ActionCard

const val TIMER_TOOL = "start_timer"

/** A day is well past anything the clock app treats as a timer rather than an alarm. */
private val TIMER_RANGE = 1..86_400

object TimerTool : Tool {

    override val name = TIMER_TOOL

    override val description = "Start a countdown timer on the user's device."

    override val parameters = listOf(
        ToolParameter("seconds", "Duration in seconds, 1-86400.", required = true),
        ToolParameter("label", "Short name shown on the timer.", required = false),
    )

    override fun execute(context: Context, arguments: Map<String, String>): ToolResult {
        val seconds = arguments["seconds"]?.toIntOrNull()?.takeIn(TIMER_RANGE)
            ?: return ToolResult(false, "Invalid duration")
        val label = arguments["label"].orEmpty()
        return try {
            context.startActivity(
                Intent(AlarmClock.ACTION_SET_TIMER).apply {
                    putExtra(AlarmClock.EXTRA_LENGTH, seconds)
                    putExtra(AlarmClock.EXTRA_MESSAGE, label)
                    putExtra(AlarmClock.EXTRA_SKIP_UI, true)
                }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            ToolResult(true, "Timer started", TimerCard(seconds, label))
        } catch (_: ActivityNotFoundException) {
            ToolResult(false, "No clock app available")
        }
    }

    @Composable
    override fun CardContent(card: ToolCard) {
        val timer = card as TimerCard
        ActionCard(
            icon = Icons.Outlined.HourglassEmpty,
            title = formatDuration(timer.totalSeconds),
            header = "Timer Started",
            subtitle = timer.label
        )
    }
}

@Immutable
data class TimerCard(
    val totalSeconds: Int,
    val label: String,
) : ToolCard {
    override val toolName = TIMER_TOOL
}
