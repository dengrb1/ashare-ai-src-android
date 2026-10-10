package com.ashareai.app.standalone.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.ashareai.app.HybridApp
import com.ashareai.app.performance.AutomaticTrainingPolicy
import com.ashareai.app.performance.DeviceResourcePolicy
import com.ashareai.app.standalone.data.settings.SettingsStore
import java.time.Duration
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DailyResearchScheduler(
    private val context: Context,
    private val settings: SettingsStore,
    private val calendar: ShanghaiTradingCalendar = ShanghaiTradingCalendar(),
    private val now: () -> ZonedDateTime = { ZonedDateTime.now(ShanghaiTradingCalendar.ZONE) },
) {
    suspend fun schedule() {
        val currentSettings = settings.settings.first()
        if (!currentSettings.dailyResearchEnabled || currentSettings.automaticReports.none { it.enabled }) {
            WorkManager.getInstance(context).cancelUniqueWork(DAILY_WORK_NAME)
            return
        }
        val delay = Duration.between(now(), calendar.nextDailyResearchAt(now())).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<DailyResearchWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .build(),
            )
            .setInitialDelay(delay, TimeUnit.MILLISECONDS)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            DAILY_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            request,
        )
        settings.setLastDailyScheduleAt(System.currentTimeMillis())
    }

    private companion object {
        const val DAILY_WORK_NAME = "standalone-daily-research"
    }
}

class DailyResearchWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as HybridApp
        val local = app.localContainer
        val eligibility = AutomaticTrainingPolicy.evaluate(DeviceResourcePolicy.snapshot(applicationContext, appForeground = false))
        if (!eligibility.allowed) {
            local.notifications.publish(
                title = "自动研究已延后",
                body = eligibility.reason ?: "设备资源暂不可用",
                priority = com.ashareai.app.standalone.domain.NotificationPriority.WARNING,
                deepLink = "research",
                systemNotificationId = com.ashareai.app.standalone.notifications.NotificationRepository.RESEARCH_ACTIVITY_NOTIFICATION_ID,
            )
            local.dailyResearchScheduler.schedule()
            return Result.success()
        }
        val calendar = local.calendar
        if (calendar.isTradingDay(java.time.LocalDate.now(ShanghaiTradingCalendar.ZONE))) {
            local.settings.settings.first().automaticReports
                .filter { it.enabled }
                .sortedBy { it.slot }
                .forEach { config ->
                    runCatching {
                        val run = local.research.enqueueAutomatic(config, startImmediately = false)
                        local.notifications.showResearchProgress(
                            title = "自动研究报告 ${config.slot}",
                            body = "正在准备研究任务",
                        )
                        local.research.run(run.id) { progressRun ->
                            val progress = if (progressRun.totalCount <= 0) {
                                0
                            } else {
                                progressRun.completedCount * 100 / progressRun.totalCount
                            }
                            local.notifications.showResearchProgress(
                                title = "自动研究报告 ${config.slot}",
                                body = "已完成 ${progressRun.completedCount} / ${progressRun.totalCount} 只股票",
                                progress = progress,
                            )
                        }
                    }.onFailure { error ->
                        local.notifications.publish(
                            title = "自动报告 ${config.slot} 未运行",
                            body = error.message ?: "自动报告配置或研究范围不可用",
                            priority = com.ashareai.app.standalone.domain.NotificationPriority.WARNING,
                            deepLink = "research",
                            systemNotificationId = com.ashareai.app.standalone.notifications.NotificationRepository
                                .RESEARCH_ACTIVITY_NOTIFICATION_ID,
                        )
                    }
                }
        }
        local.dailyResearchScheduler.schedule()
        return Result.success()
    }
}

class ResearchFallbackWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val runId = inputData.getString(INPUT_RUN_ID) ?: return Result.failure()
        val app = applicationContext as HybridApp
        val run = app.localContainer.local.researchRun(runId) ?: return Result.failure()
        if (run.triggerSource == com.ashareai.app.standalone.domain.ResearchTriggerSource.AUTO) {
            val eligibility = AutomaticTrainingPolicy.evaluate(DeviceResourcePolicy.snapshot(applicationContext, appForeground = false))
            if (!eligibility.allowed) {
                app.localContainer.notifications.publish(
                    title = "自动研究已延后",
                    body = eligibility.reason ?: "设备资源暂不可用",
                    priority = com.ashareai.app.standalone.domain.NotificationPriority.WARNING,
                    deepLink = "research",
                    systemNotificationId = com.ashareai.app.standalone.notifications.NotificationRepository.RESEARCH_ACTIVITY_NOTIFICATION_ID,
                )
                app.localContainer.dailyResearchScheduler.schedule()
                return Result.success()
            }
        }
        app.localContainer.research.run(runId)
        return Result.success()
    }

    companion object {
        private const val INPUT_RUN_ID = "input_run_id"

        fun enqueue(context: Context, runId: String) {
            val request = OneTimeWorkRequestBuilder<ResearchFallbackWorker>()
                .setInputData(workDataOf(INPUT_RUN_ID to runId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "standalone-research-fallback-" + runId,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as HybridApp
                app.localContainer.dailyResearchScheduler.schedule()
                app.localContainer.research.recoverPendingRuns()
                app.localContainer.monitoringFallbackScheduler.schedule()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
