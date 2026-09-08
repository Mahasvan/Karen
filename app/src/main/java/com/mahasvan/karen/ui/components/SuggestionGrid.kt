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
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Alarm
import androidx.compose.material.icons.outlined.Call
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Navigation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.WbSunny
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
import com.mahasvan.karen.ui.theme.GoogleBlue
import com.mahasvan.karen.ui.theme.GoogleGreen
import com.mahasvan.karen.ui.theme.GoogleRed
import com.mahasvan.karen.ui.theme.GoogleYellow

private val CircleSize = 52.dp
private val RowPitch = 77.dp

data class Suggestion(
    val label: String,
    val prompt: String,
    val icon: ImageVector,
    val color: Color,
)

private val Suggestions = listOf(
    Suggestion("Call", "call ABCXYZ", Icons.Outlined.Call, GoogleBlue),
    Suggestion("Music", "play Bohemian Rhapsody", Icons.Outlined.MusicNote, GoogleRed),
    Suggestion("Alarm", "set an alarm for 7 AM", Icons.Outlined.Alarm, GoogleYellow),
    Suggestion("Weather", "what's the weather like", Icons.Outlined.WbSunny, GoogleGreen),
    Suggestion("Navigate", "navigate home", Icons.Outlined.Navigation, GoogleBlue),
    Suggestion("Translate", "translate hello to French", Icons.Outlined.Translate, GoogleRed),
    Suggestion("Remind", "remind me to call mum", Icons.Outlined.Notifications, GoogleYellow),
    Suggestion("Forecast", "will it rain tomorrow", Icons.Outlined.CloudQueue, GoogleGreen),
)

/**
 * The 4x2 launcher grid from the mockup. Scrolling is disabled and the height is fixed
 * to exactly two rows, because this sits above a LazyColumn in the same Column.
 */
@Composable
fun SuggestionGrid(
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier.height(RowPitch * 2),
        verticalArrangement = Arrangement.spacedBy(RowPitch - CircleSize - 18.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false,
    ) {
        items(Suggestions, key = { it.label }) { suggestion ->
            SuggestionItem(suggestion, onSuggestionClick)
        }
    }
}

@Composable
private fun SuggestionItem(suggestion: Suggestion, onClick: (String) -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(CircleSize)
                .background(suggestion.color.copy(alpha = 0.15f), CircleShape)
                .clickable { onClick(suggestion.prompt) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = suggestion.icon,
                contentDescription = suggestion.label,
                tint = suggestion.color,
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
