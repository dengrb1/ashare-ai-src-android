package com.ashareai.app.performance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceResourcePolicyTest {
    private fun snapshot(
        cores: Int = 8,
        memory: Long = 4L * 1024 * 1024 * 1024,
        lowMemory: Boolean = false,
        powerSave: Boolean = false,
        thermal: Int = 0,
        batteryPercent: Int = 100,
        charging: Boolean = true,
        appForeground: Boolean = true,
        screenInteractive: Boolean = true,
        networkAvailable: Boolean = true,
    ) = ResourceSnapshot(
        memory,
        lowMemory,
        powerSave,
        thermal,
        cores,
        batteryPercent,
        charging,
        appForeground,
        screenInteractive,
        networkAvailable,
    )

    @Test
    fun zeroReportedCoresStillSchedulesOneTask() {
        val budget = DeviceResourcePolicy.budget(snapshot(cores = 0, memory = 1L * 1024 * 1024 * 1024))
        assertTrue(budget.maxParallelTasks >= 1)
    }

    @Test
    fun lowMemoryPowerSaveAndThermalUseConstrainedBudget() {
        listOf(
            snapshot(memory = 512L * 1024 * 1024),
            snapshot(powerSave = true),
            snapshot(thermal = 3),
        ).forEach { current ->
            val budget = DeviceResourcePolicy.budget(current)
            assertEquals(DeviceResourceLevel.LOW, budget.level)
            assertEquals(1, budget.maxParallelTasks)
        }
    }

    @Test
    fun highResourcesUseBoundedParallelism() {
        val budget = DeviceResourcePolicy.budget(snapshot(cores = 16))
        assertEquals(DeviceResourceLevel.HIGH, budget.level)
        assertEquals(4, budget.maxParallelTasks)
    }

    @Test
    fun lowBatteryWithoutChargingReducesBatchAndPolling() {
        val budget = DeviceResourcePolicy.budget(
            snapshot(batteryPercent = 10, charging = false),
            ResourceTaskPriority.DEFERRED,
        )
        assertEquals(DeviceResourceLevel.LOW, budget.level)
        assertEquals(1, budget.maxParallelTasks)
        assertTrue(budget.refreshIntervalMultiplier >= 4f)
    }

    @Test
    fun backgroundDeferredWorkIsLimitedButMonitoringCanContinue() {
        val background = snapshot(appForeground = false, screenInteractive = false)
        val deferred = DeviceResourcePolicy.budget(background, ResourceTaskPriority.DEFERRED)
        val monitoring = DeviceResourcePolicy.budget(background, ResourceTaskPriority.MONITORING)
        assertEquals(DeviceResourceLevel.LOW, deferred.level)
        assertTrue(deferred.refreshIntervalMultiplier >= 2f)
        assertTrue(monitoring.allowBackground)
    }
}
