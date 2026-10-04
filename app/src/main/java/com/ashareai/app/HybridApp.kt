package com.ashareai.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.AppContainer as FusionAppContainer
import com.ashareai.app.data.SettingsStore as FusionSettingsStore
import com.ashareai.app.standalone.AppContainer as LocalAppContainer
import com.ashareai.app.workspace.Workspace
import com.ashareai.app.workspace.WorkspaceStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

/**
 * 统一应用入口：同时维护本地工作区和 Fusion 工作区的容器。
 *
 * 本地工作区：Room + EastMoney + 本地研究引擎 + AI Provider + 本地任务
 * Fusion 工作区：Retrofit + FastAPI 服务器 + SSE + MiPush
 *
 * 两套数据、密钥、任务和通知完全隔离，通过 WorkspaceStore 管理当前工作区。
 */
class HybridApp : AShareApp() {
    lateinit var workspaceStore: WorkspaceStore
        private set

    lateinit var localContainer: LocalAppContainer
        private set

    lateinit var fusionSettings: FusionSettingsStore
        private set

    lateinit var fusionContainer: FusionAppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        workspaceStore = WorkspaceStore(this)
        localContainer = LocalAppContainer(this)

        // AShareApp 已初始化连接版容器；保留别名供双工作区代码使用。
        fusionSettings = settings
        fusionContainer = container

        createNotificationChannels()

        if (Application.getProcessName() == packageName) {
            onMainProcessStarted()
        }
    }

    private fun createNotificationChannels() {
        val manager = getSystemService(NotificationManager::class.java)

        // 本地工作区通知通道
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LOCAL_NORMAL,
                "本地普通通知",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "本地持仓常驻盈亏与一般通知"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LOCAL_ALERT,
                "本地重要提醒",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "本地止损、浮盈退出和手动价位提醒 - 显示超级岛"
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_LOCAL_PROGRESS,
                "本地研究进度",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "本地每日与手动研究进度"
                setShowBadge(false)
            }
        )

        // Fusion 工作区通知通道
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FUSION_MONITOR,
                "Fusion 持仓监控",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "Fusion 超级岛持仓盈亏常驻通知"
                setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FUSION_ALERT,
                "Fusion 研究预警",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "Fusion 模拟退出建议与止损预警"
                setShowBadge(true)
                enableVibration(true)
                enableLights(true)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_FUSION_PROGRESS,
                "Fusion 研究进度",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = "Fusion 每日研究任务进度"
                setShowBadge(false)
            }
        )
    }

    private fun onMainProcessStarted() {
        // 恢复本地任务调度
        appScope.launch {
            localContainer.dailyResearchScheduler.schedule()
            localContainer.research.recoverPendingRuns()
        }

        // 检测连接版升级：有效 Fusion 会话时自动切换到 Fusion 工作区
        appScope.launch {
            val hasAccessToken = fusionSettings.currentAccessToken() != null
            val expiresAt = fusionSettings.accessExpiresAt()
            val sessionValid = hasAccessToken && expiresAt > System.currentTimeMillis()

            workspaceStore.markFusionSessionValid(sessionValid)

            // 首次启动且有有效会话：升级自连接版，恢复到 Fusion 工作区
            val state = workspaceStore.workspaceStateValue()
            if (sessionValid && state.current == Workspace.LOCAL && state.lastFusionRoute == null) {
                workspaceStore.setWorkspace(Workspace.FUSION)
            }
        }
    }

    companion object {
        // 本地工作区通知通道
        const val CHANNEL_LOCAL_NORMAL = "local_normal"
        const val CHANNEL_LOCAL_ALERT = "local_alert"
        const val CHANNEL_LOCAL_PROGRESS = "local_progress"

        // Fusion 工作区通知通道
        const val CHANNEL_FUSION_MONITOR = "fusion_monitor"
        const val CHANNEL_FUSION_ALERT = "fusion_alert"
        const val CHANNEL_FUSION_PROGRESS = "fusion_progress"

        lateinit var instance: HybridApp
            private set

        fun from(context: Context): HybridApp =
            context.applicationContext as HybridApp
    }
}
