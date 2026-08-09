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
import com.ashareai.app.standalone.StandaloneApp
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
        if (!currentSettings.dailyResearchEnabled) {
            WorkManager.getInstance(context).cancelUniqueWork(DAILY_WORK_NAME)
            return
        }
        val delay = Duration.between(now(), calendar.nextDailyResearchAt(now())).toMillis().coerceAtLeast(0)
        val request = OneTimeWorkRequestBuilder<DailyResearchWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
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
        val app = applicationContext as StandaloneApp
        val calendar = app.container.calendar
        if (calendar.isTradingDay(java.time.LocalDate.now(ShanghaiTradingCalendar.ZONE))) {
            app.container.research.enqueueDaily()
        }
        app.container.dailyResearchScheduler.schedule()
        return Result.success()
    }
}

class ResearchFallbackWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val runId = inputData.getString(INPUT_RUN_ID) ?: return Result.failure()
        val app = applicationContext as StandaloneApp
        app.container.research.run(runId)
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
                val app = context.applicationContext as StandaloneApp
                app.container.dailyResearchScheduler.schedule()
                app.container.research.recoverPendingRuns()
                app.container.monitoringFallbackScheduler.schedule()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
