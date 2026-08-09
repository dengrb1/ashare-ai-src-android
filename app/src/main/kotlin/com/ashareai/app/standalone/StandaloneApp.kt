package com.ashareai.app.standalone

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class StandaloneApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannels()
        if (Application.getProcessName() == packageName) {
            container.onMainProcessStarted()
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_NORMAL,
                "普通通知",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "持仓常驻盈亏与一般本地通知"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERT,
                "交易预警",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "止损、浮盈退出和手动价位提醒"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_PROGRESS,
                "研究进度",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "每日与手动本地研究进度"
            },
        )
    }

    companion object {
        const val CHANNEL_NORMAL = "standalone_normal"
        const val CHANNEL_ALERT = "standalone_alert"
        const val CHANNEL_PROGRESS = "standalone_progress"

        fun from(context: Context): StandaloneApp =
            context.applicationContext as StandaloneApp
    }
}
