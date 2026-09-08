package com.mahasvan.karen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class AssistantPalette(
    val sheetSurface: Color,
    val userBubble: Color,
    val assistantBubble: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val scrim: Color,
    val actionButton: Color,
    val actionIconTint: Color,
    val cardSurface: Color,
)

private val LightPalette = AssistantPalette(
    sheetSurface = Color(0xFFFFFFFF),
    userBubble = Color(0xFFF1F3F4),
    assistantBubble = Color(0xFFFFFFFF),
    textPrimary = Color(0xFF202124),
    textSecondary = Color(0xFF5F6368),
    scrim = Color(0x8C000000),
    actionButton = Color(0xFFFFFFFF),
    actionIconTint = Color(0xFF5F6368),
    cardSurface = Color(0xFFF8F9FA),
)

private val DarkPalette = AssistantPalette(
    sheetSurface = Color(0xFF1F1F1F),
    userBubble = Color(0xFF3C4043),
    assistantBubble = Color(0xFF2D2F31),
    textPrimary = Color(0xFFE8EAED),
    textSecondary = Color(0xFF9AA0A6),
    scrim = Color(0xA6000000),
    actionButton = Color(0xFF2D2F31),
    actionIconTint = Color(0xFFE8EAED),
    cardSurface = Color(0xFF2D2F31),
)

val LocalAssistantPalette = staticCompositionLocalOf { LightPalette }

object AssistantTheme {
    val palette: AssistantPalette
        @Composable @ReadOnlyComposable get() = LocalAssistantPalette.current
}

private val DarkColorScheme = darkColorScheme(
    primary = GoogleBlue,
    secondary = GoogleGreen,
    tertiary = GoogleYellow,
)

private val LightColorScheme = lightColorScheme(
    primary = GoogleBlue,
    secondary = GoogleGreen,
    tertiary = GoogleYellow,
)

@Composable
fun KarenTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAssistantPalette provides if (darkTheme) DarkPalette else LightPalette
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            content = content,
        )
    }
}
