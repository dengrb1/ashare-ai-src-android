package com.ashareai.app.performance

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutomaticTrainingPolicyTest {
    private fun snapshot(
        battery: Int = 100,
        charging: Boolean = false,
        powerSave: Boolean = false,
        thermal: Int = 0,
        network: Boolean = true,
    ) = ResourceSnapshot(
        availableMemoryBytes = 2L * 1024 * 1024 * 1024,
        lowMemory = false,
        powerSave = powerSave,
        thermalStatus = thermal,
        batteryPercent = battery,
        charging = charging,
        networkAvailable = network,
    )

    @Test fun batteryBelowThresholdWithoutChargingIsBlocked() {
        assertFalse(AutomaticTrainingPolicy.evaluate(snapshot(battery = 82)).allowed)
    }

    @Test fun thresholdBatteryIsAllowed() {
        assertTrue(AutomaticTrainingPolicy.evaluate(snapshot(battery = 83)).allowed)
    }

    @Test fun chargingOverridesLowBattery() {
        assertTrue(AutomaticTrainingPolicy.evaluate(snapshot(battery = 20, charging = true)).allowed)
    }

    @Test fun powerSaveThermalAndNetworkBlock() {
        assertFalse(AutomaticTrainingPolicy.evaluate(snapshot(powerSave = true)).allowed)
        assertFalse(AutomaticTrainingPolicy.evaluate(snapshot(thermal = 4)).allowed)
        assertFalse(AutomaticTrainingPolicy.evaluate(snapshot(network = false)).allowed)
    }
}
