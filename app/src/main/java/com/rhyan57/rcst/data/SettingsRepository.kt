package com.rhyan57.rcst.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.rhyan57.rcst.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "rcst_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_THEME_MODE   = stringPreferencesKey("theme_mode")
        private val KEY_MATERIAL_YOU = booleanPreferencesKey("material_you")
        private val KEY_HOME_URL     = stringPreferencesKey("home_url")
        private val KEY_JAVASCRIPT   = booleanPreferencesKey("javascript_enabled")
        private val KEY_DESKTOP_SITE = booleanPreferencesKey("desktop_site")
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        ThemeMode.valueOf(prefs[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.name)
    }

    val materialYou: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_MATERIAL_YOU] ?: true
    }

    val homeUrl: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[KEY_HOME_URL] ?: "https://www.google.com"
    }

    val javascriptEnabled: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_JAVASCRIPT] ?: true
    }

    val desktopSite: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[KEY_DESKTOP_SITE] ?: false
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[KEY_THEME_MODE] = mode.name }
    }

    suspend fun setMaterialYou(enabled: Boolean) {
        context.dataStore.edit { it[KEY_MATERIAL_YOU] = enabled }
    }

    suspend fun setHomeUrl(url: String) {
        context.dataStore.edit { it[KEY_HOME_URL] = url }
    }

    suspend fun setJavascriptEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_JAVASCRIPT] = enabled }
    }

    suspend fun setDesktopSite(enabled: Boolean) {
        context.dataStore.edit { it[KEY_DESKTOP_SITE] = enabled }
    }
}
