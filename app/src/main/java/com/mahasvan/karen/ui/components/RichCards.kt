package com.mahasvan.karen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.outlined.Dialpad
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.MicOff
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mahasvan.karen.assistant.MusicProvider
import com.mahasvan.karen.assistant.RichCard
import com.mahasvan.karen.ui.theme.AssistantTheme
import com.mahasvan.karen.ui.theme.GoogleRed

private val CardShape = RoundedCornerShape(16.dp)

@Composable
fun RichCardContent(
    card: RichCard,
    onEndCall: () -> Unit,
    onProviderSelected: (MusicProvider) -> Unit,
    onAlarmToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (card) {
        is RichCard.Calling -> CallingCard(card, onEndCall, modifier)
        is RichCard.MusicPicker -> MusicPickerCard(card, onProviderSelected, modifier)
        is RichCard.Alarm -> AlarmCard(card, onAlarmToggle, modifier)
    }
}

private val DialerActions = listOf(
    "Mute" to Icons.Outlined.MicOff,
    "Keypad" to Icons.Outlined.Dialpad,
    "Speaker" to Icons.AutoMirrored.Outlined.VolumeUp,
    "Add call" to Icons.Outlined.PersonAdd,
    "Hold" to Icons.Outlined.Pause,
    "Record" to Icons.Outlined.FiberManualRecord,
)

@Composable
private fun CallingCard(card: RichCard.Calling, onEndCall: () -> Unit, modifier: Modifier) {
    val haptics = LocalHapticFeedback.current
    ElevatedCard(shape = CardShape, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF303134), Color(0xFF1F1F1F)))
                )
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Calling\u2026",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF9AA0A6),
                )
                Text(
                    text = card.contact,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE8EAED),
                )
            }

            DialerActions.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                    row.forEach { (label, icon) ->
                        DialerAction(label, icon)
                    }
                }
            }

            Box(
                modifier = Modifier
                    .size(63.dp)
                    .background(GoogleRed, CircleShape)
                    .clickable {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEndCall()
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.CallEnd,
                    contentDescription = "End call",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun DialerAction(label: String, icon: ImageVector) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(45.dp)
                .background(Color(0xFF3C4043), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = Color(0xFFE8EAED),
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = Color(0xFF9AA0A6),
        )
    }
}

private val ProviderColors = mapOf(
    MusicProvider.Spotify to Color(0xFF1DB954),
    MusicProvider.YouTubeMusic to Color(0xFFFF0000),
    MusicProvider.AppleMusic to Color(0xFFFA243C),
)

@Composable
private fun MusicPickerCard(
    card: RichCard.MusicPicker,
    onProviderSelected: (MusicProvider) -> Unit,
    modifier: Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val palette = AssistantTheme.palette
    ElevatedCard(shape = CardShape, modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .background(palette.cardSurface)
                .padding(vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            card.providers.forEach { provider ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onProviderSelected(provider)
                        }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(47.dp)
                            .background(ProviderColors.getValue(provider), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = provider.displayName.take(1),
                            style = MaterialTheme.typography.titleMedium,
                            color = Color.White,
                        )
                    }
                    Column {
                        Text(
                            text = provider.displayName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = palette.textPrimary,
                        )
                        Text(
                            text = card.track,
                            style = MaterialTheme.typography.bodyMedium,
                            color = palette.textSecondary,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlarmCard(
    card: RichCard.Alarm,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val palette = AssistantTheme.palette
    ElevatedCard(shape = CardShape, modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .background(palette.cardSurface)
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = card.time,
                    style = MaterialTheme.typography.titleMedium,
                    color = palette.textPrimary,
                )
                Text(
                    text = card.label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                )
            }
            Switch(
                checked = card.enabled,
                onCheckedChange = {
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    onToggle(it)
                },
            )
        }
    }
}
