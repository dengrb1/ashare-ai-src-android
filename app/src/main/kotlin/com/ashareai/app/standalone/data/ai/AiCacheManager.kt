package com.ashareai.app.standalone.data.ai

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import java.security.MessageDigest

private val Context.aiCacheDataStore by preferencesDataStore(name = "ai_cache")

/**
 * AI响应缓存管理器
 *
 * 缓存策略：
 * - 使用prompt + model的哈希作为key
 * - 支持TTL过期机制
 * - 限制最大缓存条目数
 */
class AiCacheManager(
    private val context: Context,
    private val maxCacheEntries: Int = 100,
    private val defaultTtlMillis: Long = 24 * 60 * 60 * 1000, // 24小时
) {
    private var queryCount = 0L
    private var hitCount = 0L

    /**
     * 获取缓存的响应
     */
    suspend fun get(providerId: String, systemInstruction: String, prompt: String): String? {
        queryCount = (queryCount + 1).coerceAtMost(1_000_000L)
        val key = cacheKey(providerId, systemInstruction, prompt)
        val preferences = context.aiCacheDataStore.data.first()
        val contentKey = stringPreferencesKey("content_$key")
        val timestampKey = longPreferencesKey("timestamp_$key")

        val content = preferences[contentKey]
        val timestamp = preferences[timestampKey] ?: 0L

        if (content == null) return null

        // 检查是否过期
        val now = System.currentTimeMillis()
        if (now - timestamp > defaultTtlMillis) {
            // 过期，删除缓存
            context.aiCacheDataStore.edit {
                it.remove(contentKey)
                it.remove(timestampKey)
            }
            return null
        }

        hitCount = (hitCount + 1).coerceAtMost(1_000_000L)
        return content
    }

    /**
     * 保存响应到缓存
     */
    suspend fun put(providerId: String, systemInstruction: String, prompt: String, response: String) {
        if (response.isBlank()) return

        val key = cacheKey(providerId, systemInstruction, prompt)
        val contentKey = stringPreferencesKey("content_$key")
        val timestampKey = longPreferencesKey("timestamp_$key")
        val now = System.currentTimeMillis()

        context.aiCacheDataStore.edit { preferences ->
            // 检查缓存大小，如果超限则清理最旧的条目
            val allKeys = preferences.asMap().keys
            val timestampKeys = allKeys.filter { it.name.startsWith("timestamp_") }

            if (timestampKeys.size >= maxCacheEntries) {
                // 找到最旧的条目并删除
                val oldestKey = timestampKeys.minByOrNull { key ->
                    preferences[key as androidx.datastore.preferences.core.Preferences.Key<Long>] ?: Long.MAX_VALUE
                }
                if (oldestKey != null) {
                    val suffix = oldestKey.name.removePrefix("timestamp_")
                    preferences.remove(stringPreferencesKey("content_$suffix"))
                    preferences.remove(oldestKey as androidx.datastore.preferences.core.Preferences.Key<Long>)
                }
            }

            preferences[contentKey] = response
            preferences[timestampKey] = now
        }
    }

    /**
     * 清除所有缓存
     */
    suspend fun clearAll() {
        context.aiCacheDataStore.edit { it.clear() }
    }

    /**
     * 清除过期缓存
     */
    suspend fun clearExpired() {
        val now = System.currentTimeMillis()
        context.aiCacheDataStore.edit { preferences ->
            val timestampKeys = preferences.asMap().keys.filter { it.name.startsWith("timestamp_") }
            timestampKeys.forEach { key ->
                val timestamp = preferences[key as androidx.datastore.preferences.core.Preferences.Key<Long>] ?: 0L
                if (now - timestamp > defaultTtlMillis) {
                    val suffix = key.name.removePrefix("timestamp_")
                    preferences.remove(stringPreferencesKey("content_$suffix"))
                    preferences.remove(key)
                }
            }
        }
    }

    /**
     * 获取缓存统计信息
     */
    suspend fun getStats(): CacheStats {
        val preferences = context.aiCacheDataStore.data.first()
        val timestampKeys = preferences.asMap().keys.filter { it.name.startsWith("timestamp_") }
        val now = System.currentTimeMillis()

        var expiredCount = 0
        timestampKeys.forEach { key ->
            val timestamp = preferences[key as androidx.datastore.preferences.core.Preferences.Key<Long>] ?: 0L
            if (now - timestamp > defaultTtlMillis) {
                expiredCount++
            }
        }

        return CacheStats(
            totalEntries = timestampKeys.size,
            expiredEntries = expiredCount,
            validEntries = timestampKeys.size - expiredCount,
            hitRate = if (queryCount == 0L) 0f else hitCount.toFloat() / queryCount,
        )
    }

    private fun cacheKey(providerId: String, systemInstruction: String, prompt: String): String {
        val input = "$providerId|$systemInstruction|$prompt"
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(input.toByteArray())
        return hash.joinToString("") { "%02x".format(it) }.take(32)
    }
}

data class CacheStats(
    val totalEntries: Int,
    val expiredEntries: Int,
    val validEntries: Int,
    val hitRate: Float
)
