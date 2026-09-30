package com.ashareai.app.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.PowerManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 省电模式管理器
 * 监听系统省电模式状态和电池电量，自动优化应用性能
 */
class PowerSaverManager(private val context: Context) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val _isPowerSaveMode = MutableStateFlow(false)
    val isPowerSaveMode: StateFlow<Boolean> = _isPowerSaveMode.asStateFlow()

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    updatePowerSaveMode()
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    updateBatteryLevel(intent)
                }
            }
        }
    }

    fun start() {
        val filter = IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
        }
        context.registerReceiver(powerSaveReceiver, filter)

        // 初始状态
        updatePowerSaveMode()
        requestBatteryLevel()
    }

    fun stop() {
        try {
            context.unregisterReceiver(powerSaveReceiver)
        } catch (e: IllegalArgumentException) {
            // 已经注销过了
        }
    }

    private fun updatePowerSaveMode() {
        _isPowerSaveMode.value = powerManager.isPowerSaveMode
    }

    private fun updateBatteryLevel(intent: Intent) {
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) {
            _batteryLevel.value = (level * 100 / scale)
        }
    }

    private fun requestBatteryLevel() {
        val batteryStatus = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        batteryStatus?.let { updateBatteryLevel(it) }
    }
}
