package com.ashareai.app.standalone.research

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.ashareai.app.HybridApp
import com.ashareai.app.performance.AutomaticTrainingPolicy
import com.ashareai.app.performance.DeviceResourcePolicy
import com.ashareai.app.standalone.notifications.NotificationRepository
import com.ashareai.app.standalone.work.ResearchFallbackWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class ResearchService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var runJob: Job? = null
    private var activeRunId: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val runId = intent?.getStringExtra(EXTRA_RUN_ID) ?: return START_NOT_STICKY
        activeRunId = runId
        val app = HybridApp.from(this)
        startForeground(
            NotificationRepository.RESEARCH_ACTIVITY_NOTIFICATION_ID,
            app.localContainer.notifications.researchProgressNotification("本地研究", "正在准备研究任务"),
        )
        runJob?.cancel()
        runJob = serviceScope.launch {
            val queuedRun = app.localContainer.local.researchRun(runId)
            val notificationTitle = queuedRun?.automaticReportSlot?.let { "自动研究报告 $it" } ?: "本地研究"
            if (queuedRun?.triggerSource == com.ashareai.app.standalone.domain.ResearchTriggerSource.AUTO) {
                val eligibility = AutomaticTrainingPolicy.evaluate(
                    DeviceResourcePolicy.snapshot(this@ResearchService, appForeground = false),
                )
                if (!eligibility.allowed) {
                    app.localContainer.notifications.publish(
                        title = "自动研究已延后",
                        body = eligibility.reason ?: "设备资源暂不可用",
                        priority = com.ashareai.app.standalone.domain.NotificationPriority.WARNING,
                        deepLink = "research",
                        systemNotificationId = NotificationRepository.RESEARCH_ACTIVITY_NOTIFICATION_ID,
                    )
                    app.localContainer.dailyResearchScheduler.schedule()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf(startId)
                    return@launch
                }
            }
            app.localContainer.notifications.showResearchProgress(notificationTitle, "正在准备研究任务")
            app.localContainer.research.run(runId) { run ->
                val body = "已完成 " + run.completedCount + " / " + run.totalCount + " 只股票"
                val progress = if (run.totalCount <= 0) 0 else run.completedCount * 100 / run.totalCount
                app.localContainer.notifications.showResearchProgress(notificationTitle, body, progress)
            }
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onTimeout(startId: Int, fgsType: Int) {
        activeRunId?.let { ResearchFallbackWorker.enqueue(this, it) }
        runJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf(startId)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_RUN_ID = "research_run_id"
        fun start(context: Context, runId: String) {
            val intent = Intent(context, ResearchService::class.java).putExtra(EXTRA_RUN_ID, runId)
            runCatching { ContextCompat.startForegroundService(context, intent) }
                .onFailure { ResearchFallbackWorker.enqueue(context, runId) }
        }
    }
}
