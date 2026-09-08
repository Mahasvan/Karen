package com.mahasvan.karen.tools.clock

import android.content.Context
import android.content.Intent
import android.provider.AlarmClock
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.res.stringResource
import com.mahasvan.karen.R
import com.mahasvan.karen.tools.Tool
import com.mahasvan.karen.tools.ToolAction
import com.mahasvan.karen.tools.ToolCard
import com.mahasvan.karen.tools.ToolParameter
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

    override fun parse(arguments: Map<String, String>): ToolAction? {
        val hour = arguments["hour"]?.toIntOrNull()?.takeIn(0..23) ?: return null
        val minute = arguments["minute"]?.toIntOrNull()?.takeIn(0..59) ?: 0
        return AlarmAction(hour, minute, arguments["label"].orEmpty())
    }

    override fun card(action: ToolAction): ToolCard? {
        val alarm = action as? AlarmAction ?: return null
        return AlarmCard(alarm.hour, alarm.minute, alarm.label, enabled = true)
    }

    @Composable
    override fun CardContent(
        card: ToolCard,
        onConfirm: () -> Unit,
        onUpdate: (ToolCard) -> Unit,
    ) {
        val alarm = card as AlarmCard
        ActionCard(
            icon = Icons.Outlined.Alarm,
            title = formatClock(alarm.hour, alarm.minute),
            subtitle = alarm.label,
            confirmLabel = stringResource(R.string.card_confirm_alarm),
            onConfirm = onConfirm,
            trailing = {
                Switch(
                    checked = alarm.enabled,
                    onCheckedChange = { onUpdate(alarm.copy(enabled = it)) },
                )
            },
        )
    }
}

@Immutable
data class AlarmAction(
    val hour: Int,
    val minute: Int,
    val label: String,
) : ToolAction {
    override fun intent(context: Context): Intent =
        Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            putExtra(AlarmClock.EXTRA_MESSAGE, label)
            putExtra(AlarmClock.EXTRA_SKIP_UI, false)
        }
}

@Immutable
data class AlarmCard(
    val hour: Int,
    val minute: Int,
    val label: String,
    val enabled: Boolean,
) : ToolCard {
    override val toolName = ALARM_TOOL
    override fun action(): ToolAction = AlarmAction(hour, minute, label)
}

internal fun Int.takeIn(range: IntRange): Int? = takeIf { it in range }
