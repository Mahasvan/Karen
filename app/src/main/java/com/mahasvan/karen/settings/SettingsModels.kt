package com.mahasvan.karen.settings

import androidx.compose.runtime.Immutable

/**
 * Which palette drives the app. [MaterialYou] derives surfaces and the four
 * brand accents from the wallpaper; [Google] uses the fixed Google Assistant
 * brand colours.
 */
enum class ColorTheme { MaterialYou, Google }

/** Where generation runs: the on-device LiteRT model, or a remote HTTP endpoint. */
enum class BackendChoice { OnDevice, Remote }

/**
 * Connection details for an OpenAI-compatible chat-completions endpoint. Any
 * provider speaking that wire format works, so [baseUrl] is free-form and
 * [apiKey] is optional — local servers usually need no auth.
 */
@Immutable
data class RemoteConfig(
    val baseUrl: String = "https://api.openai.com/v1",
    val apiKey: String = "",
    val model: String = "",
) {
    /** A turn cannot run without somewhere to send it and a model to name. */
    val isComplete: Boolean get() = baseUrl.isNotBlank() && model.isNotBlank()
}

/**
 * All persisted settings, read as one snapshot so the UI collects a single flow.
 * Defaults here are the values used before the user changes anything.
 */
@Immutable
data class AppSettings(
    val colorTheme: ColorTheme = ColorTheme.MaterialYou,
    val backend: BackendChoice = BackendChoice.OnDevice,
    val remoteBackendConfig: RemoteConfig = RemoteConfig(),
)
