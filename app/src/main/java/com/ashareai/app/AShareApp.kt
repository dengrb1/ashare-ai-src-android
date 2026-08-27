package com.ashareai.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.AppContainer
import com.ashareai.app.data.SettingsStore
import kotlinx.coroutines.runBlocking

class AShareApp : Application() {

    lateinit var settings: SettingsStore
        private set

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        settings = SettingsStore(this)
        runBlocking { settings.migrateLegacyServerAddress() }
        ApiClient.init(settings)
        container = AppContainer(settings)
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_MONITOR, "持仓监控",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "超级岛持仓盈亏常驻通知" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT, "研究预警",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "模拟退出建议与止损预警" }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS, "研究进度",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "每日研究任务进度" }
        )
    }

    companion object {
        const val CHANNEL_MONITOR = "monitor"
        const val CHANNEL_ALERT = "alert"
        const val CHANNEL_PROGRESS = "progress"

        lateinit var instance: AShareApp
            private set
    }
}
