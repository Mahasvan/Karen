package com.mahasvan.karen.ui.components

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.mahasvan.karen.ui.theme.AppTheme
import com.mahasvan.karen.ui.theme.AssistantTheme

private val CardShape = RoundedCornerShape(25.dp)

/**
 * The informational card every tool shares: leading glyph, title over subtitle.
 * The intent has already fired by the time this card appears. Tools compose this
 * rather than reimplementing it, so a new capability inherits the styling for free.
 */
@Composable
fun ActionCard(
    icon: ImageVector,
    title: String,
    header: String? = null,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val palette = AssistantTheme.palette
    val accent = AssistantTheme.brandColors.first()

    ElevatedCard(
        shape = CardShape,
        modifier = modifier
            .fillMaxWidth()
            .height(120.dp)
    ) {
        Row(
            modifier = Modifier
//                .fillMaxWidth()
                .fillMaxSize()
                .background(palette.cardSurface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(75.dp)
                    .background(accent.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(50.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                if (!header.isNullOrEmpty()) {
                    Text(
                        text = header,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = palette.textSecondary,
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.displayMedium,
                    color = palette.textPrimary,

                )
                if (!subtitle.isNullOrEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = palette.textSecondary,
                    )
                }
            }
        }
    }
}

@Preview
@Composable
fun ActionCardPreview() {
    AppTheme {
        ActionCard(
            icon = Icons.Outlined.Alarm,
            title = "7:00 AM",
            header = "Set Alarm",
            subtitle = "Wake up!",
        )
    }
}