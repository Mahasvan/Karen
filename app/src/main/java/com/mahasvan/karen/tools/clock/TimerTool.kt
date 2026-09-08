package com.mahasvan.karen.tools.clock

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.mahasvan.karen.R
import com.mahasvan.karen.tools.Tool
import com.mahasvan.karen.tools.ToolAction
import com.mahasvan.karen.tools.ToolCard
import com.mahasvan.karen.tools.ToolParameter
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

    override fun parse(arguments: Map<String, String>): ToolAction? {
        val seconds = arguments["seconds"]?.toIntOrNull()?.takeIn(TIMER_RANGE) ?: return null
        return TimerAction(seconds, arguments["label"].orEmpty())
    }

    override fun card(action: ToolAction): ToolCard? {
        val timer = action as? TimerAction ?: return null
        return TimerCard(timer.seconds, timer.label)
    }

    @Composable
    override fun CardContent(
        card: ToolCard,
        onConfirm: () -> Unit,
        onUpdate: (ToolCard) -> Unit,
    ) {
        val timer = card as TimerCard
        ActionCard(
            icon = Icons.Outlined.HourglassEmpty,
            title = formatDuration(timer.totalSeconds),
            subtitle = timer.label,
            confirmLabel = stringResource(R.string.card_confirm_timer),
            onConfirm = onConfirm,
        )
    }
}

@Immutable
data class TimerAction(
    val seconds: Int,
    val label: String,
) : ToolAction {
    override fun intent(context: Context): Intent =
        Intent(AlarmClock.ACTION_SET_TIMER).apply {
            putExtra(AlarmClock.EXTRA_LENGTH, seconds)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
}

@Immutable
data class TimerCard(
    val totalSeconds: Int,
    val label: String,
) : ToolCard {
    override val toolName = TIMER_TOOL
    override fun action(): ToolAction = TimerAction(totalSeconds, label)
}
