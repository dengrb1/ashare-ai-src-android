package com.ashareai.app.standalone.research

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.ashareai.app.standalone.StandaloneApp
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
        val app = application as StandaloneApp
        startForeground(
            NotificationRepository.RESEARCH_ACTIVITY_NOTIFICATION_ID,
            app.container.notifications.researchProgressNotification("本地研究", "正在准备研究任务"),
        )
        runJob?.cancel()
        runJob = serviceScope.launch {
            val queuedRun = app.container.local.researchRun(runId)
            val notificationTitle = queuedRun?.automaticReportSlot?.let { "自动研究报告 $it" } ?: "本地研究"
            app.container.notifications.showResearchProgress(notificationTitle, "正在准备研究任务")
            app.container.research.run(runId) { run ->
                val body = "已完成 " + run.completedCount + " / " + run.totalCount + " 只股票"
                val progress = if (run.totalCount <= 0) 0 else run.completedCount * 100 / run.totalCount
                app.container.notifications.showResearchProgress(notificationTitle, body, progress)
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
