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
    /**
     * 获取缓存的响应
     */
    suspend fun get(providerId: String, systemInstruction: String, prompt: String): String? {
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
        var hitCount = 0
        timestampKeys.forEach { key ->
            val timestamp = preferences[key as androidx.datastore.preferences.core.Preferences.Key<Long>] ?: 0L
            if (now - timestamp > defaultTtlMillis) {
                expiredCount++
            } else {
                hitCount++
            }
        }

        return CacheStats(
            totalEntries = timestampKeys.size,
            expiredEntries = expiredCount,
            validEntries = timestampKeys.size - expiredCount,
            hitRate = if (Companion.totalQueries > 0) hitCount.toFloat() / Companion.totalQueries else 0f
        )
    }

    /**
     * 预测性加载：根据用户历史查询，预加载常见分析结果。
     *
     * 策略：
     * - 维护访问频率统计（最近 30 天）
     * - 预加载访问频率前 20 的股票分析
     * - 后台异步执行，不阻塞 UI
     *
     * @param symbols 候选股票列表
     * @param onPreload 预加载回调，用于执行实际的 AI 请求
     */
    suspend fun predictiveLoad(
        symbols: List<String>,
        onPreload: suspend (symbol: String) -> Unit
    ) {
        // 获取热门股票（访问频率前 20）
        val hotSymbols = getHotSymbols(symbols, limit = 20)

        // 异步预加载
        hotSymbols.forEach { symbol ->
            // 检查是否已缓存
            val cached = get("default", "分析", symbol)
            if (cached == null) {
                // 执行预加载
                runCatching { onPreload(symbol) }
            }
        }
    }

    /**
     * 记录访问：用于预测性加载的频率统计。
     */
    suspend fun recordAccess(symbol: String) {
        val key = stringPreferencesKey("access_count_$symbol")
        val timestampKey = longPreferencesKey("access_timestamp_$symbol")
        val now = System.currentTimeMillis()

        context.aiCacheDataStore.edit { preferences ->
            val count = preferences[key]?.toIntOrNull() ?: 0
            preferences[key] = (count + 1).toString()
            preferences[timestampKey] = now
        }

        Companion.totalQueries++
    }

    /**
     * 获取热门股票：根据访问频率排序。
     */
    private suspend fun getHotSymbols(symbols: List<String>, limit: Int): List<String> {
        val preferences = context.aiCacheDataStore.data.first()
        val now = System.currentTimeMillis()
        val thirtyDaysAgo = now - 30L * 24 * 60 * 60 * 1000

        val symbolCounts = symbols.mapNotNull { symbol ->
            val key = stringPreferencesKey("access_count_$symbol")
            val timestampKey = longPreferencesKey("access_timestamp_$symbol")
            val count = preferences[key]?.toIntOrNull() ?: 0
            val timestamp = preferences[timestampKey] ?: 0L

            // 仅统计最近 30 天的访问
            if (timestamp >= thirtyDaysAgo && count > 0) {
                symbol to count
            } else {
                null
            }
        }

        return symbolCounts
            .sortedByDescending { it.second }
            .take(limit)
            .map { it.first }
    }

    companion object {
        // 全局查询计数，用于计算命中率
        private var totalQueries = 0
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
