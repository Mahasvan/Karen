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
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.mahasvan.karen.R
import com.mahasvan.karen.ui.theme.AssistantTheme

private val CircleSize = 52.dp
private val RowPitch = 77.dp

data class Suggestion(
    @param:StringRes val label: Int,
    @param:StringRes val prompt: Int,
    val icon: ImageVector,
)

private val Suggestions = listOf(
    Suggestion(
        R.string.suggestion_alarm_label,
        R.string.suggestion_alarm_prompt,
        Icons.Outlined.Alarm,
    ),
    Suggestion(
        R.string.suggestion_timer_label,
        R.string.suggestion_timer_prompt,
        Icons.Outlined.HourglassEmpty,
    ),
    Suggestion(
        R.string.suggestion_event_label,
        R.string.suggestion_event_prompt,
        Icons.Outlined.Event,
    ),
    Suggestion(
        R.string.suggestion_call_label,
        R.string.suggestion_call_prompt,
        Icons.Outlined.Call,
    ),
)

/**
 * The launcher grid from the mockup, now one row wide since the app only supports four
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
        val prompt = stringResource(suggestion.prompt)
        val label = stringResource(suggestion.label)
        Box(
            modifier = Modifier
                .size(CircleSize)
                .background(accent.copy(alpha = 0.15f), CircleShape)
                .clickable { onClick(prompt) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = suggestion.icon,
                contentDescription = label,
                tint = accent,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = label,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            color = AssistantTheme.palette.textSecondary,
            textAlign = TextAlign.Center,
        )
    }
}
