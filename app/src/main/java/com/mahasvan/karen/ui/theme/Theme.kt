package com.mahasvan.karen.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.colorResource

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
    val suggestionAccent: Color,
)

private fun ColorScheme.toAssistantPalette(darkTheme: Boolean) = AssistantPalette(
    sheetSurface = surface,
    userBubble = surfaceVariant,
    assistantBubble = surfaceContainerHigh,
    textPrimary = onSurface,
    textSecondary = onSurfaceVariant,
    scrim = scrim.copy(alpha = if (darkTheme) 0.65f else 0.55f),
    actionButton = surfaceContainerHighest,
    actionIconTint = onSurfaceVariant,
    cardSurface = surfaceContainer,
    suggestionAccent = if (darkTheme) Color(0xFFA8C7FA) else primary,
)

val LocalAssistantPalette = staticCompositionLocalOf {
    lightColorScheme().toAssistantPalette(darkTheme = false)
}

/**
 * The four accents driving the logo dots, the voice visualizer and the glow bar.
 * Wallpaper-derived when dynamic colour is on, Google brand colours otherwise.
 */
val LocalBrandColors = staticCompositionLocalOf { GoogleBrandColors }

object AssistantTheme {
    val palette: AssistantPalette
        @Composable @ReadOnlyComposable get() = LocalAssistantPalette.current

    val brandColors: List<Color>
        @Composable @ReadOnlyComposable get() = LocalBrandColors.current
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

/**
 * Four tones from the wallpaper palette. The three accent families yield only
 * three hues, so the fourth dot is a lighter tone of accent1 — which is why
 * Material You reads as tonal rather than four-colour.
 */
@Composable
private fun wallpaperBrandColors(darkTheme: Boolean): List<Color> = if (darkTheme) {
    listOf(
        colorResource(android.R.color.system_accent1_100),
        colorResource(android.R.color.system_accent3_200),
        colorResource(android.R.color.system_accent2_200),
        colorResource(android.R.color.system_accent1_400),
    )
} else {
    listOf(
        colorResource(android.R.color.system_accent1_700),
        colorResource(android.R.color.system_accent3_700),
        colorResource(android.R.color.system_accent2_700),
        colorResource(android.R.color.system_accent1_300),
    )
}

@Composable
fun AppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context)
        dynamicColor -> dynamicLightColorScheme(context)
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
    val brandColors =
        if (dynamicColor) wallpaperBrandColors(darkTheme) else GoogleBrandColors

    CompositionLocalProvider(
        LocalAssistantPalette provides colorScheme.toAssistantPalette(darkTheme),
        LocalBrandColors provides brandColors,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content,
        )
    }
}
