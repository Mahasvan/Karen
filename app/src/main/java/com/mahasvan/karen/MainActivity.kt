package com.mahasvan.karen

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.mahasvan.karen.assistant.AssistantViewModel
import com.mahasvan.karen.assistant.perform
import com.mahasvan.karen.ui.AssistantOverlay
import com.mahasvan.karen.ui.theme.KarenTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KarenTheme {
                val context = LocalContext.current
                val viewModel: AssistantViewModel =
                    viewModel(factory = AssistantViewModel.Factory)
                val uiState by viewModel.uiState.collectAsStateWithLifecycle()

                LaunchedEffect(viewModel) {
                    viewModel.actions.collect { context.perform(it) }
                }

                AssistantOverlay(
                    uiState = uiState,
                    onMicClick = viewModel::toggleMicrophone,
                    onKeyboardClick = viewModel::toggleKeyboard,
                    onSubmit = viewModel::submit,
                    onConfirmCard = viewModel::confirmCard,
                    onAlarmToggle = viewModel::setAlarmEnabled,
                    onRetry = viewModel::retry,
                    onDismiss = { finish() },
                )
            }
        }
    }
}
