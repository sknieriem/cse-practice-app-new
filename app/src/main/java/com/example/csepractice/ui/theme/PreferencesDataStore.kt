package com.example.csepractice.ui.theme

import android.content.Context
import android.util.Log  // For debugging
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

object PreferencesDataStore {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_prefs")

    private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
    private val COLOR_SCHEME_KEY = stringPreferencesKey("color_scheme")

    fun darkModeFlow(context: Context): Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[DARK_MODE_KEY] ?: isSystemInDarkTheme(context)
    }

    fun colorSchemeFlow(context: Context): Flow<String> = context.dataStore.data.map { prefs ->
        prefs[COLOR_SCHEME_KEY] ?: "Default"
    }

    suspend fun setDarkMode(context: Context, isDark: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[DARK_MODE_KEY] = isDark
            Log.d("DataStoreDebug", "Set dark mode to $isDark")
        }
    }

    suspend fun setColorScheme(context: Context, scheme: String) {
        context.dataStore.edit { prefs ->
            prefs[COLOR_SCHEME_KEY] = scheme
            Log.d("DataStoreDebug", "Set color scheme to $scheme")
        }
    }

    private fun isSystemInDarkTheme(context: Context): Boolean {
        // Implement system dark mode check if needed
        return false  // Default to light
    }
}