package com.ashareai.app.standalone.monitor

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ashareai.app.standalone.StandaloneApp
import java.util.concurrent.TimeUnit

class MonitoringFallbackScheduler(
    private val context: Context,
) {
    fun schedule() {
        val request = PeriodicWorkRequestBuilder<MonitorFallbackWorker>(15, TimeUnit.MINUTES)
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun cancel() {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private companion object {
        const val WORK_NAME = "standalone-monitor-fallback"
    }
}

class MonitorFallbackWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val app = applicationContext as StandaloneApp
        app.container.monitoring.checkOnce()
        return Result.success()
    }
}
