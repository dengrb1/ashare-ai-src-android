package com.ashareai.app.data

import com.ashareai.app.data.model.HealthResponse
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class ConnectionContractTest {
    @Test
    fun fusionHealthIsAcceptedAndSummarizesBridgeAvailability() {
        val health = health(
            """{
                "status":"ok",
                "database":"ok",
                "execution_mode":"RESEARCH_ONLY",
                "qmt_enabled":false,
                "auto_trading_enabled":false,
                "quote_bridge":{"status":"ok","url":"http://127.0.0.1:8081"},
                "news_bridge":{"status":"unavailable","error":"private detail"},
                "gateway":{"healthy":true,"url":"http://127.0.0.1:8787"}
            }""",
        )

        assertEquals(ConnectionClassification.Fusion, HealthConnectionClassifier.classify(health))
        val summary = HealthConnectionClassifier.summarize(health)
        assertEquals(InfrastructureAvailability.Available, summary.api)
        assertEquals(InfrastructureAvailability.Available, summary.quoteBridge)
        assertEquals(InfrastructureAvailability.Unavailable, summary.newsBridge)
        assertEquals(InfrastructureAvailability.Available, summary.modelGateway)
    }

    @Test
    fun legacyHealthRemainsCompatible() {
        val legacy = health("""{"status":"ok","version":"1.0","database":"ok"}""")

        assertEquals(ConnectionClassification.LegacyCompatible, HealthConnectionClassifier.classify(legacy))
    }

    @Test
    fun blankExecutionModeIsTreatedAsLegacyCompatibility() {
        assertEquals(
            ConnectionClassification.LegacyCompatible,
            HealthConnectionClassifier.classify(HealthResponse(status = "ok", execution_mode = "  ")),
        )
    }

    @Test
    fun explicitUnsafeModesAreRejected() {
        listOf(
            health("""{"execution_mode":"LIVE","qmt_enabled":false,"auto_trading_enabled":false}"""),
            health("""{"execution_mode":"RESEARCH_ONLY","qmt_enabled":true,"auto_trading_enabled":false}"""),
            health("""{"execution_mode":"RESEARCH_ONLY","qmt_enabled":false,"auto_trading_enabled":true}"""),
        ).forEach { response ->
            assertEquals(ConnectionClassification.Unsupported, HealthConnectionClassifier.classify(response))
        }
    }

    @Test
    fun apiClientServiceProviderCanUseFakeApiService() = runBlocking {
        val fake = fakeApi(health("""{"status":"ok","execution_mode":"RESEARCH_ONLY"}"""))
        ApiClient.setServiceProvider(ApiServiceProvider { fake })
        try {
            assertEquals("RESEARCH_ONLY", ApiClient.api.health().execution_mode)
            assertTrue(HealthConnectionClassifier.classify(ApiClient.api.health()) == ConnectionClassification.Fusion)
            assertEquals("RESEARCH_ONLY", ApiClient.healthService("https://fusion.example.com").health().execution_mode)
        } finally {
            ApiClient.setServiceProvider(null)
        }
    }

    @Test
    fun pendingProbeNeverEstablishesSession() {
        val pending = ConnectionProbe(classification = ConnectionClassification.PendingConfiguration)
        val unsupported = ConnectionProbe(classification = ConnectionClassification.Unsupported)

        assertFalse(pending.canEstablishSession)
        assertFalse(unsupported.canEstablishSession)
    }

    private fun health(payload: String): HealthResponse = ApiClient.json.decodeFromString(payload)

    private fun fakeApi(health: HealthResponse): ApiService {
        return Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(ApiService::class.java),
        ) { _, method, _ ->
            when (method.name) {
                "health" -> health
                "toString" -> "FakeApiService"
                else -> throw UnsupportedOperationException("FakeApiService does not handle ${method.name}")
            }
        } as ApiService
    }
}
