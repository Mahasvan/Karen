package com.mahasvan.karen.settings

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val repository: SettingsRepository) : ViewModel() {

    /**
     * Null until the first read lands. The theme is needed before the first
     * composition, so callers hold the frame rather than render defaults and
     * snap to the stored value.
     */
    val settings: StateFlow<AppSettings?> =
        repository.settings.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun setColorTheme(theme: ColorTheme) {
        viewModelScope.launch { repository.setColorTheme(theme) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as Application
                SettingsViewModel(SettingsRepository(app))
            }
        }
    }
}
