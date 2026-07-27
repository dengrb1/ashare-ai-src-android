package com.ashareai.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import java.util.UUID

private val Context.dataStore by preferencesDataStore(name = "ashare_settings")

class SettingsStore(context: Context) {
    private val context = context.applicationContext

    companion object {
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_ACCESS_EXPIRES_AT = longPreferencesKey("access_expires_at")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_DARK_MODE = stringPreferencesKey("dark_mode") // system | light | dark
        private val KEY_ISLAND_ENABLED = booleanPreferencesKey("island_enabled")
        private val KEY_SEEN_NOTIFICATION_IDS = stringSetPreferencesKey("seen_notification_ids")
        private val KEY_INSTALLATION_ID = stringPreferencesKey("push_installation_id")
        private val KEY_PUSH_DEVICE_ID = stringPreferencesKey("push_device_id")

        const val DEFAULT_BASE_URL = "http://127.0.0.1:8000"
    }

    val baseUrl: Flow<String> = context.dataStore.data.map { it[KEY_BASE_URL] ?: DEFAULT_BASE_URL }
    val username: Flow<String?> = context.dataStore.data.map { it[KEY_USERNAME] }
    val darkMode: Flow<String> = context.dataStore.data.map { it[KEY_DARK_MODE] ?: "system" }
    val islandEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_ISLAND_ENABLED] ?: false }

    suspend fun currentBaseUrl(): String = baseUrl.first()
    suspend fun currentAccessToken(): String? = readToken(KEY_ACCESS_TOKEN)
    suspend fun currentRefreshToken(): String? = readToken(KEY_REFRESH_TOKEN)
    suspend fun accessExpiresAt(): Long = context.dataStore.data.map { it[KEY_ACCESS_EXPIRES_AT] ?: 0L }.first()
    suspend fun installationId(): String {
        context.dataStore.data.map { it[KEY_INSTALLATION_ID] }.first()?.let { return it }
        val value = UUID.randomUUID().toString()
        context.dataStore.edit { it[KEY_INSTALLATION_ID] = value }
        return value
    }
    suspend fun currentPushDeviceId(): String? = context.dataStore.data.map { it[KEY_PUSH_DEVICE_ID] }.first()

    suspend fun setPushDeviceId(deviceId: String?) {
        context.dataStore.edit {
            if (deviceId == null) it.remove(KEY_PUSH_DEVICE_ID) else it[KEY_PUSH_DEVICE_ID] = deviceId
        }
    }

    suspend fun setBaseUrl(url: String) {
        context.dataStore.edit { it[KEY_BASE_URL] = url.trimEnd('/') }
    }

    suspend fun setDarkMode(mode: String) {
        context.dataStore.edit { it[KEY_DARK_MODE] = mode }
    }

    suspend fun setIslandEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ISLAND_ENABLED] = enabled }
    }

    /** Atomically claims unseen IDs so process restarts cannot duplicate alerts. */
    suspend fun claimUnseenNotificationIds(ids: List<String>): Set<String> {
        val safeIds = ids.filter { it.length in 1..128 && it.none(Char::isWhitespace) }.distinct()
        var unseen = emptySet<String>()
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_SEEN_NOTIFICATION_IDS].orEmpty()
            unseen = safeIds.filterNot(current::contains).toSet()
            preferences[KEY_SEEN_NOTIFICATION_IDS] = (current.toList().takeLast(400) + safeIds).takeLast(500).toSet()
        }
        return unseen
    }

    suspend fun saveTokens(access: String, refresh: String, expiresInSeconds: Long, username: String? = null) {
        context.dataStore.edit {
            it[KEY_ACCESS_TOKEN] = TokenCipher.encrypt(access)
            it[KEY_REFRESH_TOKEN] = TokenCipher.encrypt(refresh)
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

    private suspend fun readToken(key: androidx.datastore.preferences.core.Preferences.Key<String>): String? {
        val stored = context.dataStore.data.map { it[key] }.first() ?: return null
        if (stored.startsWith(TokenCipher.PREFIX)) return TokenCipher.decrypt(stored)

        // One-time migration from versions that stored tokens as plaintext.
        context.dataStore.edit { it[key] = TokenCipher.encrypt(stored) }
        return stored
    }
}

private object TokenCipher {
    const val PREFIX = "keystore:v1:"
    private const val KEY_ALIAS = "ashare_api_tokens_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    fun decrypt(value: String): String? = runCatching {
        val payload = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
        require(payload.size > 12) { "Invalid encrypted token" }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, payload.copyOfRange(0, 12)))
        cipher.doFinal(payload.copyOfRange(12, payload.size)).toString(Charsets.UTF_8)
    }.getOrNull()

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setRandomizedEncryptionRequired(true)
                    .build(),
            )
            generateKey()
        }
    }
}
