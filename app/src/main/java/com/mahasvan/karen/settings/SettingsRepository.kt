package com.mahasvan.karen.settings

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** One DataStore for the whole app; the delegate must live at file scope. */
private val Context.settingsDataStore by preferencesDataStore(name = "settings")

/**
 * Flat key-value settings backed by DataStore Preferences. Reads are exposed as
 * a [Flow] so the UI recomposes when a value changes; writes are suspending.
 *
 * Add a setting by adding a key to [Keys], a field to [AppSettings], and a
 * setter below.
 */
class SettingsRepository(context: Context) {

    // Application context: this outlives any Activity.
    private val dataStore = context.applicationContext.settingsDataStore

    val settings: Flow<AppSettings> = dataStore.data
        // A corrupt or unreadable file should fall back to defaults rather than
        // taking the whole UI down; anything else is a real bug.
        .catch { e -> if (e is IOException) emit(emptyPreferences()) else throw e }
        .map { prefs ->
            val defaults = RemoteConfig()
            AppSettings(
                colorTheme = prefs.readEnum(Keys.COLOR_THEME, ColorTheme.MaterialYou),
                backend = prefs.readEnum(Keys.BACKEND, BackendChoice.OnDevice),
                remote = RemoteConfig(
                    baseUrl = prefs[Keys.REMOTE_BASE_URL] ?: defaults.baseUrl,
                    apiKey = prefs[Keys.REMOTE_API_KEY] ?: defaults.apiKey,
                    model = prefs[Keys.REMOTE_MODEL] ?: defaults.model,
                ),
            )
        }

    suspend fun setColorTheme(theme: ColorTheme) {
        dataStore.edit { it[Keys.COLOR_THEME] = theme.name }
    }

    suspend fun setBackend(backend: BackendChoice) {
        dataStore.edit { it[Keys.BACKEND] = backend.name }
    }

    /** Written as a unit: a half-applied endpoint change is never a valid config. */
    suspend fun setRemoteEndpoint(baseUrl: String, apiKey: String) {
        dataStore.edit {
            it[Keys.REMOTE_BASE_URL] = baseUrl
            it[Keys.REMOTE_API_KEY] = apiKey
        }
    }

    suspend fun setRemoteModel(model: String) {
        dataStore.edit { it[Keys.REMOTE_MODEL] = model }
    }

    private object Keys {
        val COLOR_THEME = stringPreferencesKey("color_theme")
        val BACKEND = stringPreferencesKey("backend")
        val REMOTE_BASE_URL = stringPreferencesKey("remote_base_url")
        val REMOTE_API_KEY = stringPreferencesKey("remote_api_key")
        val REMOTE_MODEL = stringPreferencesKey("remote_model")
    }
}

/**
 * Enums are stored by name, so a constant that was renamed or removed would make
 * `valueOf` throw on an old install. Fall back to the default instead.
 */
private inline fun <reified T : Enum<T>> Preferences.readEnum(
    key: Preferences.Key<String>,
    default: T,
): T = this[key]?.let { stored -> runCatching { enumValueOf<T>(stored) }.getOrNull() } ?: default
