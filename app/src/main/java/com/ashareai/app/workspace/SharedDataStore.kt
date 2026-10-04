package com.ashareai.app.workspace

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 工作区间共享数据管理：可选择性地在连接版和独立版之间同步设置。
 *
 * 可共享项：
 * - 主题设置（深色模式偏好）
 * - 超级岛开关
 * - AI 提供商配置
 * - 通知偏好
 * - 玻璃态材质和完整动画开关
 *
 * 不共享项：
 * - 持仓数据
 * - 研究记录
 * - 交易历史
 * - 登录凭证
 */
class SharedDataStore(private val context: Context) {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "workspace_shared_prefs")

    companion object {
        // 共享开关：控制每个设置项是否在工作区间同步
        private val SHARE_THEME = booleanPreferencesKey("share_theme")
        private val SHARE_ISLAND = booleanPreferencesKey("share_island")
        private val SHARE_AI_CONFIG = booleanPreferencesKey("share_ai_config")
        private val SHARE_NOTIFICATIONS = booleanPreferencesKey("share_notifications")
        private val SHARE_GLASS_EFFECT = booleanPreferencesKey("share_glass_effect")
        private val SHARE_FULL_ANIMATIONS = booleanPreferencesKey("share_full_animations")

        // 共享数据值
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val ISLAND_ENABLED = booleanPreferencesKey("island_enabled")
        private val AI_PROVIDER = stringPreferencesKey("ai_provider")
        private val AI_MODEL = stringPreferencesKey("ai_model")
        private val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        private val GLASS_EFFECT_ENABLED = booleanPreferencesKey("glass_effect_enabled")
        private val FULL_ANIMATIONS_ENABLED = booleanPreferencesKey("full_animations_enabled")
        private val AUTO_OPTIMIZE_IN_POWER_SAVER = booleanPreferencesKey("auto_optimize_in_power_saver")
    }

    /**
     * 共享设置状态
     */
    data class SharedSettings(
        val shareTheme: Boolean = true,
        val shareIsland: Boolean = true,
        val shareAiConfig: Boolean = false, // AI 配置默认不共享（连接版可能有不同提供商）
        val shareNotifications: Boolean = true,
        val shareGlassEffect: Boolean = true,
        val shareFullAnimations: Boolean = true,
        val autoOptimizeInPowerSaver: Boolean = true, // 省电模式自动优化
    )

    /**
     * 共享数据值
     */
    data class SharedValues(
        val themeMode: String = "system",
        val islandEnabled: Boolean = false,
        val aiProvider: String? = null,
        val aiModel: String? = null,
        val notificationsEnabled: Boolean = true,
        val glassEffectEnabled: Boolean = true,
        val fullAnimationsEnabled: Boolean = true,
    )

    /**
     * 获取共享开关配置
     */
    val sharedSettings: Flow<SharedSettings> = context.dataStore.data.map { prefs ->
        SharedSettings(
            shareTheme = prefs[SHARE_THEME] ?: true,
            shareIsland = prefs[SHARE_ISLAND] ?: true,
            shareAiConfig = prefs[SHARE_AI_CONFIG] ?: false,
            shareNotifications = prefs[SHARE_NOTIFICATIONS] ?: true,
            shareGlassEffect = prefs[SHARE_GLASS_EFFECT] ?: true,
            shareFullAnimations = prefs[SHARE_FULL_ANIMATIONS] ?: true,
            autoOptimizeInPowerSaver = prefs[AUTO_OPTIMIZE_IN_POWER_SAVER] ?: true,
        )
    }

    /**
     * 获取共享数据值
     */
    val sharedValues: Flow<SharedValues> = context.dataStore.data.map { prefs ->
        SharedValues(
            themeMode = prefs[THEME_MODE] ?: "system",
            islandEnabled = prefs[ISLAND_ENABLED] ?: false,
            aiProvider = prefs[AI_PROVIDER],
            aiModel = prefs[AI_MODEL],
            notificationsEnabled = prefs[NOTIFICATIONS_ENABLED] ?: true,
            glassEffectEnabled = prefs[GLASS_EFFECT_ENABLED] ?: true,
            fullAnimationsEnabled = prefs[FULL_ANIMATIONS_ENABLED] ?: true,
        )
    }

    /**
     * 更新共享开关配置
     */
    suspend fun updateSharedSettings(settings: SharedSettings) {
        context.dataStore.edit { prefs ->
            prefs[SHARE_THEME] = settings.shareTheme
            prefs[SHARE_ISLAND] = settings.shareIsland
            prefs[SHARE_AI_CONFIG] = settings.shareAiConfig
            prefs[SHARE_NOTIFICATIONS] = settings.shareNotifications
            prefs[SHARE_GLASS_EFFECT] = settings.shareGlassEffect
            prefs[SHARE_FULL_ANIMATIONS] = settings.shareFullAnimations
            prefs[AUTO_OPTIMIZE_IN_POWER_SAVER] = settings.autoOptimizeInPowerSaver
        }
    }

    /**
     * 更新共享数据值
     */
    suspend fun updateSharedValues(values: SharedValues) {
        context.dataStore.edit { prefs ->
            prefs[THEME_MODE] = values.themeMode
            prefs[ISLAND_ENABLED] = values.islandEnabled
            values.aiProvider?.let { prefs[AI_PROVIDER] = it }
            values.aiModel?.let { prefs[AI_MODEL] = it }
            prefs[NOTIFICATIONS_ENABLED] = values.notificationsEnabled
            prefs[GLASS_EFFECT_ENABLED] = values.glassEffectEnabled
            prefs[FULL_ANIMATIONS_ENABLED] = values.fullAnimationsEnabled
        }
    }

    /**
     * 同步特定设置项到共享存储
     */
    suspend fun syncThemeMode(mode: String) {
        context.dataStore.edit { prefs ->
            if (prefs[SHARE_THEME] != false) {
                prefs[THEME_MODE] = mode
            }
        }
    }

    suspend fun syncIslandEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            if (prefs[SHARE_ISLAND] != false) {
                prefs[ISLAND_ENABLED] = enabled
            }
        }
    }

    suspend fun syncAiConfig(provider: String, model: String) {
        context.dataStore.edit { prefs ->
            if (prefs[SHARE_AI_CONFIG] == true) {
                prefs[AI_PROVIDER] = provider
                prefs[AI_MODEL] = model
            }
        }
    }

    suspend fun syncNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            if (prefs[SHARE_NOTIFICATIONS] != false) {
                prefs[NOTIFICATIONS_ENABLED] = enabled
            }
        }
    }

    suspend fun syncGlassEffectEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            if (prefs[SHARE_GLASS_EFFECT] != false) {
                prefs[GLASS_EFFECT_ENABLED] = enabled
            }
        }
    }

    suspend fun syncFullAnimationsEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            if (prefs[SHARE_FULL_ANIMATIONS] != false) {
                prefs[FULL_ANIMATIONS_ENABLED] = enabled
            }
        }
    }

    suspend fun setAutoOptimizeInPowerSaver(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[AUTO_OPTIMIZE_IN_POWER_SAVER] = enabled
        }
    }

    suspend fun syncAppearance(glassEnabled: Boolean, fullAnimationsEnabled: Boolean) {
        context.dataStore.edit { prefs ->
            if (prefs[SHARE_GLASS_EFFECT] != false) prefs[GLASS_EFFECT_ENABLED] = glassEnabled
            if (prefs[SHARE_FULL_ANIMATIONS] != false) prefs[FULL_ANIMATIONS_ENABLED] = fullAnimationsEnabled
        }
    }
}
