package com.ashareai.app.performance

import android.app.ActivityManager
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.content.Context
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager

enum class DeviceResourceLevel { LOW, NORMAL, HIGH }

/** 描述任务是否可以延后，以及它对交互和持续监控的优先级。 */
enum class ResourceTaskPriority {
    INTERACTIVE,
    USER_INITIATED,
    MONITORING,
    DEFERRED,
}

data class ResourceSnapshot(
    val availableMemoryBytes: Long,
    val lowMemory: Boolean,
    val powerSave: Boolean,
    val thermalStatus: Int = 0,
    val logicalCores: Int = Runtime.getRuntime().availableProcessors(),
    val batteryPercent: Int = 100,
    val charging: Boolean = false,
    val appForeground: Boolean = true,
    val screenInteractive: Boolean = true,
    val networkAvailable: Boolean = true,
)

data class ResourceBudget(
    val level: DeviceResourceLevel,
    val maxParallelTasks: Int,
    val chunkSize: Int,
    val cacheEntryLimit: Int,
    val reason: String,
    /** 非实时后台任务使用此倍率降低唤醒频率。 */
    val refreshIntervalMultiplier: Float = 1f,
    /** 是否允许在应用不可见时继续执行该类任务。 */
    val allowBackground: Boolean = true,
)

/** Bounded, cancellable-friendly scheduler policy. It never keys decisions on a SoC name. */
object DeviceResourcePolicy {
    fun budget(
        snapshot: ResourceSnapshot,
        priority: ResourceTaskPriority = ResourceTaskPriority.DEFERRED,
    ): ResourceBudget {
        // A broken or mocked runtime can report zero processors. Keep work cancellable,
        // but never return a budget that cannot schedule a task.
        val cores = snapshot.logicalCores.coerceAtLeast(1)
        val thermal = snapshot.thermalStatus >= 3
        val criticalThermal = snapshot.thermalStatus >= 4
        val lowBattery = snapshot.batteryPercent in 0..15 && !snapshot.charging
        val memoryConstrained = snapshot.lowMemory || snapshot.availableMemoryBytes < 768L * 1024 * 1024
        val backgroundDeferred = !snapshot.appForeground && priority == ResourceTaskPriority.DEFERRED
        val constrained = memoryConstrained || snapshot.powerSave || thermal || lowBattery || backgroundDeferred
        val high = !constrained && snapshot.appForeground && snapshot.screenInteractive &&
            cores >= 8 && snapshot.availableMemoryBytes >= 3L * 1024 * 1024 * 1024
        val multiplier = when {
            criticalThermal || memoryConstrained || lowBattery -> 4f
            snapshot.powerSave || backgroundDeferred || !snapshot.screenInteractive -> 2.5f
            !snapshot.networkAvailable -> 2f
            else -> 1f
        }
        val reason = when {
            criticalThermal -> "严重热状态限制"
            lowBattery -> "低电量且未充电"
            memoryConstrained -> "低内存限制"
            snapshot.powerSave -> "系统省电模式"
            backgroundDeferred -> "应用在后台，延后非必要任务"
            !snapshot.networkAvailable -> "网络不可用，等待恢复"
            high -> "前台资源充足"
            else -> "标准资源预算"
        }
        return when {
            constrained -> ResourceBudget(
                DeviceResourceLevel.LOW,
                1,
                128,
                128,
                reason,
                refreshIntervalMultiplier = multiplier,
                allowBackground = priority == ResourceTaskPriority.MONITORING,
            )
            high -> ResourceBudget(
                DeviceResourceLevel.HIGH,
                minOf(4, (cores / 2).coerceAtLeast(1)),
                512,
                512,
                reason,
                refreshIntervalMultiplier = multiplier,
            )
            else -> ResourceBudget(
                DeviceResourceLevel.NORMAL,
                minOf(2, cores).coerceAtLeast(1),
                256,
                256,
                reason,
                refreshIntervalMultiplier = multiplier,
            )
        }
    }

    fun from(
        context: Context,
        priority: ResourceTaskPriority = ResourceTaskPriority.DEFERRED,
        appForeground: Boolean = AppVisibilityState.isForeground,
    ): ResourceBudget = budget(snapshot(context, appForeground), priority)

    fun snapshot(
        context: Context,
        appForeground: Boolean = AppVisibilityState.isForeground,
    ): ResourceSnapshot {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val memory = ActivityManager.MemoryInfo().also { activityManager?.getMemoryInfo(it) }
        val power = context.getSystemService(PowerManager::class.java)
        val thermal = if (Build.VERSION.SDK_INT >= 29) power?.currentThermalStatus ?: 0 else 0
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPercent = if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100) else 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        val networkAvailable = connectivity?.activeNetwork?.let { network ->
            connectivity.getNetworkCapabilities(network)?.run {
                hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    (hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) ||
                        hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR))
            }
        } == true
        return ResourceSnapshot(
            availableMemoryBytes = memory.availMem,
            lowMemory = memory.lowMemory,
            powerSave = power?.isPowerSaveMode == true,
            thermalStatus = thermal,
            batteryPercent = batteryPercent,
            charging = charging,
            appForeground = appForeground,
            screenInteractive = power?.isInteractive != false,
            networkAvailable = networkAvailable,
        )
    }
}

/** Activity 可见性由统一入口更新，供服务进程中的资源策略读取。 */
object AppVisibilityState {
    @Volatile
    var isForeground: Boolean = true
}
