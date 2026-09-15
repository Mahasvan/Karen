package com.mahasvan.karen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mahasvan.karen.assistant.AssistantViewModel
import com.mahasvan.karen.settings.ColorTheme
import com.mahasvan.karen.settings.SettingsViewModel
import com.mahasvan.karen.ui.AssistantOverlay
import com.mahasvan.karen.ui.SettingsScreen
import com.mahasvan.karen.ui.theme.AppTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settingsViewModel: SettingsViewModel =
                viewModel(factory = SettingsViewModel.Factory)
            // Null until DataStore's first read lands. Holding the frame here
            // avoids rendering the default palette and snapping to the stored one.
            val settings by settingsViewModel.settings.collectAsStateWithLifecycle()
            val loaded = settings ?: return@setContent

            AppTheme(dynamicColor = loaded.colorTheme == ColorTheme.MaterialYou) {
                var showSettings by rememberSaveable { mutableStateOf(false) }
                // Activity-scoped, so the conversation survives a trip to settings.
                val viewModel: AssistantViewModel =
                    viewModel(factory = AssistantViewModel.Factory)

                if (showSettings) {
                    BackHandler { showSettings = false }
                    SettingsScreen(
                        settings = loaded,
                        onColorThemeChange = settingsViewModel::setColorTheme,
                        onBack = { showSettings = false },
                    )
                } else {
                    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                    AssistantOverlay(
                        uiState = uiState,
                        onMicClick = viewModel::toggleMicrophone,
                        onKeyboardClick = viewModel::toggleKeyboard,
                        onSubmit = viewModel::submit,
                        onRetry = viewModel::retry,
                        onDismiss = { finish() },
                        onSettingsClick = { showSettings = true },
                    )
                }
            }
        }
    }
}
