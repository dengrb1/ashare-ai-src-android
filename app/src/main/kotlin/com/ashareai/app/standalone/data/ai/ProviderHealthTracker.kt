package com.ashareai.app.standalone.data.ai

import java.util.concurrent.ConcurrentHashMap

/** Keeps a small, process-local cooldown for providers that recently failed. */
class ProviderHealthTracker(
    private val cooldownMillis: Long = DEFAULT_COOLDOWN_MILLIS,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val unavailableUntil = ConcurrentHashMap<String, Long>()

    fun snapshot(): List<AiProviderRuntimeState> {
        val now = clock()
        return unavailableUntil.mapNotNull { (providerId, until) ->
            if (until > now) {
                AiProviderRuntimeState(providerId, temporarilyUnavailable = true, unavailableUntil = until)
            } else {
                unavailableUntil.remove(providerId, until)
                null
            }
        }
    }

    fun markFailure(providerId: String) {
        unavailableUntil[providerId] = clock() + cooldownMillis
    }

    fun markSuccess(providerId: String) {
        unavailableUntil.remove(providerId)
    }

    private companion object {
        const val DEFAULT_COOLDOWN_MILLIS = 60_000L
    }
}
