package com.ashareai.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "ashare_settings")

class SettingsStore(private val context: Context) {

    companion object {
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_ACCESS_EXPIRES_AT = longPreferencesKey("access_expires_at")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_DARK_MODE = stringPreferencesKey("dark_mode") // system | light | dark
        private val KEY_ISLAND_ENABLED = booleanPreferencesKey("island_enabled")

        const val DEFAULT_BASE_URL = "http://127.0.0.1:8000"
    }

    val baseUrl: Flow<String> = context.dataStore.data.map { it[KEY_BASE_URL] ?: DEFAULT_BASE_URL }
    val accessToken: Flow<String?> = context.dataStore.data.map { it[KEY_ACCESS_TOKEN] }
    val refreshToken: Flow<String?> = context.dataStore.data.map { it[KEY_REFRESH_TOKEN] }
    val username: Flow<String?> = context.dataStore.data.map { it[KEY_USERNAME] }
    val darkMode: Flow<String> = context.dataStore.data.map { it[KEY_DARK_MODE] ?: "system" }
    val islandEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_ISLAND_ENABLED] ?: true }

    suspend fun currentBaseUrl(): String = baseUrl.first()
    suspend fun currentAccessToken(): String? = accessToken.first()
    suspend fun currentRefreshToken(): String? = refreshToken.first()
    suspend fun accessExpiresAt(): Long = context.dataStore.data.map { it[KEY_ACCESS_EXPIRES_AT] ?: 0L }.first()

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = url.trimEnd('/') }
    }

    suspend fun setDarkMode(mode: String) {
        context.dataStore.edit { it[KEY_DARK_MODE] = mode }
    }

    suspend fun setIslandEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ISLAND_ENABLED] = enabled }
    }

    suspend fun saveTokens(access: String, refresh: String, expiresInSeconds: Long, username: String? = null) {
        context.dataStore.edit {
            it[KEY_ACCESS_TOKEN] = access
            it[KEY_REFRESH_TOKEN] = refresh
            it[KEY_ACCESS_EXPIRES_AT] = System.currentTimeMillis() + expiresInSeconds * 1000
            if (username != null) it[KEY_USERNAME] = username
        }
    }

    suspend fun clearTokens() {
        context.dataStore.edit {
            it.remove(KEY_ACCESS_TOKEN)
            it.remove(KEY_REFRESH_TOKEN)
            it.remove(KEY_ACCESS_EXPIRES_AT)
        }
    }
}
