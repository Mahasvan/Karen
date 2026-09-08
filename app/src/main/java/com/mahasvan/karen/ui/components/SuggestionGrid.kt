package com.mahasvan.karen.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.Event
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mahasvan.karen.ui.theme.AssistantTheme

private val CircleSize = 52.dp
private val RowPitch = 77.dp

data class Suggestion(
    val label: String,
    val prompt: String,
    val icon: ImageVector,
)

private val Suggestions = listOf(
    Suggestion("Alarm", "set an alarm for 7 am", Icons.Outlined.Alarm),
    Suggestion("Timer", "set a timer for 10 minutes", Icons.Outlined.HourglassEmpty),
    Suggestion("Event", "schedule a meeting with the design team", Icons.Outlined.Event),
    Suggestion("Call", "call 555 0134", Icons.Outlined.Call),
)

/**
 * The launcher grid from the mockup, now one row wide since Karen only supports four
 * things. Scrolling is disabled and the height is pinned to the row count, because this
 * sits above a LazyColumn in the same Column.
 */
@Composable
fun SuggestionGrid(
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val brandColors = AssistantTheme.brandColors
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.height(RowPitch * ((Suggestions.size + 3) / 4)),
        verticalArrangement = Arrangement.spacedBy(RowPitch - CircleSize - 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        itemsIndexed(Suggestions, key = { _, item -> item.label }) { index, suggestion ->
            SuggestionItem(suggestion, brandColors[index % brandColors.size], onSuggestionClick)
        }
    }
}

@Composable
private fun SuggestionItem(suggestion: Suggestion, accent: Color, onClick: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(CircleSize)
                .background(accent.copy(alpha = 0.15f), CircleShape)
                .clickable { onClick(suggestion.prompt) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = suggestion.icon,
                contentDescription = suggestion.label,
                tint = accent,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = suggestion.label,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = AssistantTheme.palette.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
