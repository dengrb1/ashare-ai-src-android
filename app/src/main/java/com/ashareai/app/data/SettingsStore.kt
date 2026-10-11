package com.ashareai.app.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
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

class SettingsStore(context: Context) : ConnectionSettings, SessionSettings {
    private val context = context.applicationContext

    companion object {
        private val KEY_BASE_URL = stringPreferencesKey("base_url")
        private val KEY_ACCESS_TOKEN = stringPreferencesKey("access_token")
        private val KEY_REFRESH_TOKEN = stringPreferencesKey("refresh_token")
        private val KEY_ACCESS_EXPIRES_AT = longPreferencesKey("access_expires_at")
        private val KEY_USERNAME = stringPreferencesKey("username")
        private val KEY_REMEMBER_PASSWORD = booleanPreferencesKey("remember_password")
        private val KEY_REMEMBERED_PASSWORD = stringPreferencesKey("remembered_password")
        private val KEY_DARK_MODE = stringPreferencesKey("dark_mode") // system | light | dark
        private val KEY_ACCENT_COLOR = stringPreferencesKey("accent_color")
        private val KEY_GLASS_ENABLED = booleanPreferencesKey("glass_enabled")
        private val KEY_FULL_ANIMATIONS_ENABLED = booleanPreferencesKey("full_animations_enabled")
        private val KEY_ISLAND_ENABLED = booleanPreferencesKey("island_enabled")
        private val KEY_FOREGROUND_MARKET_REFRESH_INTERVAL_SECONDS =
            intPreferencesKey("foreground_market_refresh_interval_seconds")
        private val KEY_SEEN_NOTIFICATION_IDS = stringSetPreferencesKey("seen_notification_ids")
        private val KEY_INSTALLATION_ID = stringPreferencesKey("push_installation_id")
        private val KEY_PUSH_DEVICE_ID = stringPreferencesKey("push_device_id")

        /** An empty address is an explicit pending-configuration state. */
        const val DEFAULT_BASE_URL = ""
    }

    val baseUrl: Flow<String> = context.dataStore.data.map { it[KEY_BASE_URL] ?: DEFAULT_BASE_URL }
    val username: Flow<String?> = context.dataStore.data.map { it[KEY_USERNAME] }
    val rememberPassword: Flow<Boolean> = context.dataStore.data.map { it[KEY_REMEMBER_PASSWORD] ?: false }
    val darkMode: Flow<String> = context.dataStore.data.map { it[KEY_DARK_MODE] ?: "system" }
    val accentColor: Flow<String> = context.dataStore.data.map { it[KEY_ACCENT_COLOR] ?: "#006B5F" }
    val glassEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_GLASS_ENABLED] ?: true }
    val fullAnimationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_FULL_ANIMATIONS_ENABLED] ?: true }
    val islandEnabled: Flow<Boolean> = context.dataStore.data.map { it[KEY_ISLAND_ENABLED] ?: false }
    val foregroundMarketRefreshIntervalSeconds: Flow<Int> = context.dataStore.data.map {
        it[KEY_FOREGROUND_MARKET_REFRESH_INTERVAL_SECONDS] ?: 5
    }

    override suspend fun currentBaseUrl(): String = baseUrl.first()
    suspend fun currentUsername(): String? = username.first()
    suspend fun isRememberPasswordEnabled(): Boolean = rememberPassword.first()
    suspend fun currentRememberedPassword(): String? = readSecret(KEY_REMEMBERED_PASSWORD, CredentialCipher)
    override suspend fun currentAccessToken(): String? = readToken(KEY_ACCESS_TOKEN)
    override suspend fun currentRefreshToken(): String? = readToken(KEY_REFRESH_TOKEN)
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

    override suspend fun setBaseUrl(url: String) {
        val normalized = normalizeServerUrl(url).getOrThrow()
        context.dataStore.edit { it[KEY_BASE_URL] = normalized }
    }

    /**
     * Old connected builds defaulted to the phone's own loopback address. It can
     * never reach a desktop Fusion service, so migrate it to an explicit setup
     * state and invalidate its session tokens.
     */
    suspend fun migrateLegacyServerAddress() {
        context.dataStore.edit { preferences ->
            val stored = preferences[KEY_BASE_URL] ?: return@edit
            if (stored.isLegacyLoopbackAddress()) {
                preferences.remove(KEY_BASE_URL)
                preferences.remove(KEY_ACCESS_TOKEN)
                preferences.remove(KEY_REFRESH_TOKEN)
                preferences.remove(KEY_ACCESS_EXPIRES_AT)
            }
        }
    }

    suspend fun setDarkMode(mode: String) {
        context.dataStore.edit { it[KEY_DARK_MODE] = mode }
    }

    suspend fun setAccentColor(hex: String) {
        require(hex.matches(Regex("#[0-9A-Fa-f]{6}")))
        context.dataStore.edit { it[KEY_ACCENT_COLOR] = hex.uppercase() }
    }

    suspend fun setGlassEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_GLASS_ENABLED] = enabled }
    }

    suspend fun setFullAnimationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_FULL_ANIMATIONS_ENABLED] = enabled }
    }

    suspend fun setIslandEnabled(enabled: Boolean) {
        context.dataStore.edit { it[KEY_ISLAND_ENABLED] = enabled }
    }

    suspend fun setForegroundMarketRefreshIntervalSeconds(seconds: Int) {
        require(seconds > 0) { "刷新间隔必须为正数" }
        context.dataStore.edit { it[KEY_FOREGROUND_MARKET_REFRESH_INTERVAL_SECONDS] = seconds }
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

    override suspend fun saveTokens(
        access: String,
        refresh: String,
        expiresInSeconds: Long,
        username: String?,
        password: String?,
        rememberPassword: Boolean?,
    ) {
        context.dataStore.edit {
            it[KEY_ACCESS_TOKEN] = TokenCipher.encrypt(access)
            it[KEY_REFRESH_TOKEN] = TokenCipher.encrypt(refresh)
            it[KEY_ACCESS_EXPIRES_AT] = System.currentTimeMillis() + expiresInSeconds * 1000
            if (username != null) it[KEY_USERNAME] = username
            if (rememberPassword != null) {
                it[KEY_REMEMBER_PASSWORD] = rememberPassword
                if (rememberPassword && password != null) {
                    it[KEY_REMEMBERED_PASSWORD] = CredentialCipher.encrypt(password)
                } else {
                    it.remove(KEY_REMEMBERED_PASSWORD)
                }
            }
        }
    }

    /** Compatibility overload for callers that only refresh an existing session. */
    suspend fun saveTokens(access: String, refresh: String, expiresInSeconds: Long) {
        saveTokens(access, refresh, expiresInSeconds, null, null, null)
    }

    suspend fun clearRememberedPassword() {
        context.dataStore.edit {
            it[KEY_REMEMBER_PASSWORD] = false
            it.remove(KEY_REMEMBERED_PASSWORD)
        }
    }

    override suspend fun clearTokens() {
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

    private suspend fun readSecret(
        key: androidx.datastore.preferences.core.Preferences.Key<String>,
        cipher: SecretCipher,
    ): String? {
        val stored = context.dataStore.data.map { it[key] }.first() ?: return null
        return cipher.decrypt(stored)
    }
}

private interface SecretCipher {
    fun encrypt(value: String): String
    fun decrypt(value: String): String?
}

private object TokenCipher : SecretCipher {
    const val PREFIX = "keystore:v1:"
    private const val KEY_ALIAS = "ashare_api_tokens_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    override fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    override fun decrypt(value: String): String? = runCatching {
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

private object CredentialCipher : SecretCipher {
    private const val PREFIX = "keystore:credentials:v1:"
    private const val KEY_ALIAS = "ashare_login_credentials_v1"
    private const val TRANSFORMATION = "AES/GCM/NoPadding"

    override fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val payload = cipher.iv + cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.encodeToString(payload, Base64.NO_WRAP)
    }

    override fun decrypt(value: String): String? = runCatching {
        require(value.startsWith(PREFIX)) { "Invalid credential cipher text" }
        val payload = Base64.decode(value.removePrefix(PREFIX), Base64.NO_WRAP)
        require(payload.size > 12) { "Invalid credential cipher text" }
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
