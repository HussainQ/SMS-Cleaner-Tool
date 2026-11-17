package com.example.ui.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.cleanSmsThemeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "cleansms_theme_prefs"
)

/**
 * Persists application-wide Material 3 theme preferences in Jetpack DataStore.
 */
class ThemePreferencesRepository(context: Context) {

    private val dataStore: DataStore<Preferences> = context.applicationContext.cleanSmsThemeDataStore

    val darkModeEnabledFlow: Flow<Boolean?> = dataStore.data
        .map { prefs -> prefs[KEY_DARK_MODE_ENABLED] }
        .distinctUntilChanged()

    suspend fun setDarkModeEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[KEY_DARK_MODE_ENABLED] = enabled
        }
    }

    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.clear()
        }
    }

    companion object {
        val KEY_DARK_MODE_ENABLED = booleanPreferencesKey("material3_dark_mode_enabled")
    }
}
