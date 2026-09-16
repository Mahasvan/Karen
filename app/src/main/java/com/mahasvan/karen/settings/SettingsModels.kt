package com.mahasvan.karen.settings

import androidx.compose.runtime.Immutable

/**
 * Which palette drives the app. [MaterialYou] derives surfaces and the four
 * brand accents from the wallpaper; [Google] uses the fixed Google Assistant
 * brand colours.
 */
enum class ColorTheme { MaterialYou, Google }

/**
 * All persisted settings, read as one snapshot so the UI collects a single flow.
 * Defaults here are the values used before the user changes anything.
 */
@Immutable
data class AppSettings(
    val colorTheme: ColorTheme = ColorTheme.MaterialYou,
)
