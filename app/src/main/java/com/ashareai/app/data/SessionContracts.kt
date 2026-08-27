package com.ashareai.app.data

/** Minimal settings surface needed by connection diagnostics. */
interface ConnectionSettings {
    suspend fun currentBaseUrl(): String
    suspend fun setBaseUrl(url: String)
    suspend fun clearTokens()
}

/** Minimal token surface needed by the session repository. */
interface SessionSettings {
    suspend fun currentAccessToken(): String?
    suspend fun currentRefreshToken(): String?

    suspend fun saveTokens(
        access: String,
        refresh: String,
        expiresInSeconds: Long,
        username: String?,
        password: String?,
        rememberPassword: Boolean?,
    )

    suspend fun clearTokens()
}
