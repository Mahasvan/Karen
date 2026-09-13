package com.mahasvan.karen.ui.components.actioncards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ElevatedCard
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.mahasvan.karen.ui.theme.AssistantTheme


private val CardShape = RoundedCornerShape(25.dp) // Same as SimpleActionCard


@Composable
fun BoundedActionCard(
    modifier: Modifier = Modifier,
    maxHeight: Dp = 280.dp,
    content: @Composable () -> Unit,
) {
    val palette = AssistantTheme.palette
    ElevatedCard(
        shape = CardShape,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxHeight),
    ) {
        Box(Modifier
            .fillMaxSize()
            .background(palette.cardSurface)) {
            content()
        }
    }
}