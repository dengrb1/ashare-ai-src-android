package com.ashareai.app.standalone.data.ai

import com.ashareai.app.standalone.domain.AiProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AiAgentRouterTest {
    private val provider = AiProvider(
        id = "cloud",
        name = "Cloud",
        baseUrl = "https://example.invalid",
        model = "model",
        organization = null,
        project = null,
        enabled = true,
        createdAt = 1L,
    )

    @Test
    fun weakNetworkWithLocalCapabilityUsesLocalExecution() {
        val decision = AiAgentRouter.route(
            runtime = AiRuntimeSnapshot(
                task = AiTaskType.CHAT,
                network = LocalInferenceDetector.NetworkType.THREE_G,
                localInferenceAvailable = true,
            ),
            agents = emptyList(),
            providers = listOf(provider),
        )

        assertEquals(AiExecutionTarget.LOCAL, decision.target)
        assertEquals(AiAgentRole.CHAT_ASSISTANT, decision.role)
    }

    @Test
    fun unavailableProviderFallsBackToCache() {
        val decision = AiAgentRouter.route(
            runtime = AiRuntimeSnapshot(task = AiTaskType.REPORT_SUMMARY, batteryPercent = 5),
            agents = listOf(
                AiAgentConfig("summary", AiAgentRole.REPORT_SUMMARIZER, providerId = "cloud", enableCache = true),
            ),
            providers = listOf(provider.copy(enabled = false)),
        )

        assertEquals(AiExecutionTarget.CACHE, decision.target)
        assertNull(decision.provider)
    }

    @Test
    fun explicitProviderWinsWhenAvailable() {
        val first = provider.copy(id = "first")
        val second = provider.copy(id = "second")
        val decision = AiAgentRouter.route(
            runtime = AiRuntimeSnapshot(task = AiTaskType.RESEARCH_EXPLANATION, network = LocalInferenceDetector.NetworkType.WIFI),
            agents = emptyList(),
            providers = listOf(first, second),
            explicitProviderId = "second",
        )

        assertEquals("second", decision.provider?.id)
        assertEquals(AiExecutionTarget.CLOUD, decision.target)
    }

    @Test
    fun runtimeDisabledAndCoolingProvidersAreSkipped() {
        val decision = AiAgentRouter.route(
            runtime = AiRuntimeSnapshot(task = AiTaskType.CHAT, network = LocalInferenceDetector.NetworkType.WIFI),
            agents = emptyList(),
            providers = listOf(provider, provider.copy(id = "backup")),
            providerStates = listOf(
                AiProviderRuntimeState("cloud", enabled = false),
                AiProviderRuntimeState("backup", temporarilyUnavailable = true),
            ),
        )

        assertEquals(AiExecutionTarget.CACHE, decision.target)
        assertNull(decision.provider)
    }

    @Test
    fun localInferenceRequiresEnoughRuntimeMemory() {
        val decision = AiAgentRouter.route(
            runtime = AiRuntimeSnapshot(
                task = AiTaskType.CHAT,
                network = LocalInferenceDetector.NetworkType.NONE,
                localInferenceAvailable = true,
                memoryAvailableMb = 256,
            ),
            agents = emptyList(),
            providers = listOf(provider),
        )

        assertEquals(AiExecutionTarget.CLOUD, decision.target)
    }

    @Test
    fun healthCooldownExpires() {
        var now = 100L
        val tracker = ProviderHealthTracker(cooldownMillis = 50L) { now }
        tracker.markFailure("cloud")
        assertEquals(true, tracker.snapshot().single().temporarilyUnavailable)
        now = 151L
        assertEquals(emptyList<AiProviderRuntimeState>(), tracker.snapshot())
    }
}
