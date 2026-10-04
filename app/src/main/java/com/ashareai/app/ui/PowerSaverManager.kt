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
    private var started = false

    private val _isPowerSaveMode = MutableStateFlow(false)
    val isPowerSaveMode: StateFlow<Boolean> = _isPowerSaveMode.asStateFlow()

    private val _batteryLevel = MutableStateFlow(100)
    val batteryLevel: StateFlow<Int> = _batteryLevel.asStateFlow()

    private val _isCharging = MutableStateFlow(false)
    val isCharging: StateFlow<Boolean> = _isCharging.asStateFlow()

    private val _screenInteractive = MutableStateFlow(true)
    val screenInteractive: StateFlow<Boolean> = _screenInteractive.asStateFlow()

    private val _thermalStatus = MutableStateFlow(0)
    val thermalStatus: StateFlow<Int> = _thermalStatus.asStateFlow()

    private val powerSaveReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                PowerManager.ACTION_POWER_SAVE_MODE_CHANGED -> {
                    updatePowerSaveMode()
                }
                Intent.ACTION_BATTERY_CHANGED -> {
                    updateBatteryLevel(intent)
                    updateCharging(intent)
                }
                Intent.ACTION_SCREEN_ON -> _screenInteractive.value = true
                Intent.ACTION_SCREEN_OFF -> _screenInteractive.value = false
            }
        }
    }

    private val thermalListener = if (android.os.Build.VERSION.SDK_INT >= 29) {
        PowerManager.OnThermalStatusChangedListener { status ->
            _thermalStatus.value = status
        }
    } else {
        null
    }

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED)
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        context.registerReceiver(powerSaveReceiver, filter)

        // 初始状态
        updatePowerSaveMode()
        requestBatteryLevel()
        _screenInteractive.value = powerManager.isInteractive
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            _thermalStatus.value = powerManager.currentThermalStatus
            thermalListener?.let { listener ->
                powerManager.addThermalStatusListener(context.mainExecutor, listener)
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        try {
            context.unregisterReceiver(powerSaveReceiver)
        } catch (e: IllegalArgumentException) {
            // 已经注销过了
        }
        if (android.os.Build.VERSION.SDK_INT >= 29) {
            thermalListener?.let(powerManager::removeThermalStatusListener)
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

    private fun updateCharging(intent: Intent) {
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        _isCharging.value = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun requestBatteryLevel() {
        val batteryStatus = context.registerReceiver(
            null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        batteryStatus?.let {
            updateBatteryLevel(it)
            updateCharging(it)
        }
    }
}
