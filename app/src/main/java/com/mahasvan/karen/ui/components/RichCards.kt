package com.mahasvan.karen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.mahasvan.karen.R
import com.mahasvan.karen.assistant.RichCard
import com.mahasvan.karen.assistant.formatClock
import com.mahasvan.karen.assistant.formatDuration
import com.mahasvan.karen.ui.theme.AssistantTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val CardShape = RoundedCornerShape(16.dp)
private val EventFormat = SimpleDateFormat("EEE d MMM, h:mm a", Locale.getDefault())

@Composable
fun RichCardContent(
    card: RichCard,
    onConfirm: () -> Unit,
    onAlarmToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (card) {
        is RichCard.Alarm -> ActionCard(
            icon = Icons.Outlined.Alarm,
            title = formatClock(card.hour, card.minute),
            subtitle = card.label,
            confirmLabel = stringResource(R.string.card_confirm_alarm),
            onConfirm = onConfirm,
            modifier = modifier,
            trailing = {
                Switch(checked = card.enabled, onCheckedChange = onAlarmToggle)
            },
        )

        is RichCard.Timer -> ActionCard(
            icon = Icons.Outlined.HourglassEmpty,
            title = formatDuration(card.totalSeconds),
            subtitle = card.label,
            confirmLabel = stringResource(R.string.card_confirm_timer),
            onConfirm = onConfirm,
            modifier = modifier,
        )

        is RichCard.CalendarEvent -> ActionCard(
            icon = Icons.Outlined.Event,
            title = card.title,
            subtitle = EventFormat.format(Date(card.startMillis)),
            confirmLabel = stringResource(R.string.card_confirm_event),
            onConfirm = onConfirm,
            modifier = modifier,
        )
    }
}

/**
 * One shape for all three cards: leading glyph, title over subtitle, optional trailing
 * control, and a footer button that hands the card off to the relevant system app.
 */
@Composable
private fun ActionCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    val haptics = LocalHapticFeedback.current
    val palette = AssistantTheme.palette
    val accent = AssistantTheme.brandColors.first()

    ElevatedCard(shape = CardShape, modifier = modifier.fillMaxWidth()) {
        Column(modifier = Modifier.background(palette.cardSurface)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(accent.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = palette.textPrimary,
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = palette.textSecondary,
                    )
                }
                trailing?.invoke()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onConfirm()
                    }
                ) {
                    Text(confirmLabel)
                }
            }
        }
    }
}
