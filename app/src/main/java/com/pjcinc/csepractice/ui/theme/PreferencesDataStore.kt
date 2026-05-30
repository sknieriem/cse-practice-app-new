package com.pjcinc.csepractice.ui.theme

import android.content.Context
import android.content.res.Configuration
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single source of truth for user preferences. Anything previously stored in
 * SharedPreferences ("app_prefs") has been migrated here.
 *
 * Note: the DataStore file is named "settings" to avoid colliding with the legacy
 * SharedPreferences file ("app_prefs.xml") that may still exist on upgrading installs.
 */
object PreferencesDataStore {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

    private val DARK_MODE_KEY = booleanPreferencesKey("dark_mode")
    private val COLOR_SCHEME_KEY = stringPreferencesKey("color_scheme")
    private val QUESTIONS_VERSION_KEY = intPreferencesKey("questions_version")

    fun darkModeFlow(context: Context): Flow<Boolean> =
        context.dataStore.data.map { prefs ->
            prefs[DARK_MODE_KEY] ?: isSystemInDarkTheme(context)
        }

    fun colorSchemeFlow(context: Context): Flow<String> =
        context.dataStore.data.map { prefs ->
            prefs[COLOR_SCHEME_KEY] ?: "Default"
        }

    fun questionsVersionFlow(context: Context): Flow<Int> =
        context.dataStore.data.map { prefs ->
            prefs[QUESTIONS_VERSION_KEY] ?: 0
        }

    suspend fun setDarkMode(context: Context, isDark: Boolean) {
        context.dataStore.edit { prefs -> prefs[DARK_MODE_KEY] = isDark }
    }

    suspend fun setColorScheme(context: Context, scheme: String) {
        context.dataStore.edit { prefs -> prefs[COLOR_SCHEME_KEY] = scheme }
    }

    suspend fun setQuestionsVersion(context: Context, version: Int) {
        context.dataStore.edit { prefs -> prefs[QUESTIONS_VERSION_KEY] = version }
    }

    private fun isSystemInDarkTheme(context: Context): Boolean {
        val nightModeFlags = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }
}
