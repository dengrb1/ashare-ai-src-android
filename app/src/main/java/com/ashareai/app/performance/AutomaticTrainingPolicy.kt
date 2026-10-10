package com.ashareai.app.performance

/** Pure eligibility result used by background automatic research. */
data class AutomaticTrainingEligibility(
    val allowed: Boolean,
    val reason: String? = null,
)

object AutomaticTrainingPolicy {
    const val MIN_BATTERY_PERCENT = 83

    fun evaluate(snapshot: ResourceSnapshot): AutomaticTrainingEligibility {
        if (!snapshot.networkAvailable) return AutomaticTrainingEligibility(false, "网络不可用")
        if (snapshot.powerSave) return AutomaticTrainingEligibility(false, "系统省电模式已开启")
        if (snapshot.thermalStatus >= 4) return AutomaticTrainingEligibility(false, "设备温度过高")
        if (!snapshot.charging && snapshot.batteryPercent < MIN_BATTERY_PERCENT) {
            return AutomaticTrainingEligibility(false, "电量低于 ${MIN_BATTERY_PERCENT}% 且未充电")
        }
        return AutomaticTrainingEligibility(true)
    }
}
