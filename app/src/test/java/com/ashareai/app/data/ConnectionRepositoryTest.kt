package com.ashareai.app.data

import com.ashareai.app.data.model.HealthResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.lang.reflect.Proxy

class ConnectionRepositoryTest {
    @Test
    fun healthTimeoutReturnsPendingWithoutLeakingTransportDetails() = runBlocking {
        val settings = FakeSettings("https://fusion.example.com")
        val repository = repository(settings, healthTimeoutMillis = 10) {
            delay(100)
            fusionHealth()
        }

        val result = repository.probe("https://fusion.example.com")

        assertEquals(ConnectionClassification.PendingConfiguration, result.classification)
        assertTrue(result.message.orEmpty().contains("超时"))
        assertTrue(!result.message.orEmpty().contains("java."))
    }

    @Test
    fun unavailableBridgeIsReducedToAvailabilityOnly() = runBlocking {
        val settings = FakeSettings("https://fusion.example.com")
        val repository = repository(settings) {
            HealthResponse(
                status = "ok",
                execution_mode = "RESEARCH_ONLY",
                quote_bridge = com.ashareai.app.data.model.InfrastructureHealth(
                    status = "unavailable",
                    configured = true,
                ),
                news_bridge = com.ashareai.app.data.model.InfrastructureHealth(
                    healthy = false,
                    status = "error",
                ),
            )
        }

        val result = repository.probe("https://fusion.example.com")

        assertEquals(ConnectionClassification.Fusion, result.classification)
        assertEquals(InfrastructureAvailability.Unavailable, result.infrastructure.quoteBridge)
        assertEquals(InfrastructureAvailability.Unavailable, result.infrastructure.newsBridge)
        assertTrue(result.message.orEmpty().contains("Fusion"))
    }

    @Test
    fun unsupportedExecutionModeExplainsResearchOnlyRequirement() = runBlocking {
        val settings = FakeSettings("https://fusion.example.com")
        val repository = repository(settings) {
            HealthResponse(
                status = "ok",
                execution_mode = "LIVE",
                qmt_enabled = false,
                auto_trading_enabled = false,
            )
        }

        val result = repository.probe("https://fusion.example.com")

        assertEquals(ConnectionClassification.Unsupported, result.classification)
        assertTrue(result.message.orEmpty().contains("RESEARCH_ONLY"))
        assertTrue(result.message.orEmpty().contains("QMT/自动交易"))
        assertTrue(!result.message.orEmpty().contains("fusion.example.com"))
    }

    @Test
    fun switchingAddressPersistsNormalizedValueAndInvalidatesSession() = runBlocking {
        val settings = FakeSettings("https://old.example.com")
        settings.accessToken = "stale"
        var rebuilds = 0
        val repository = repository(
            settings,
            rebuildClient = { rebuilds++ },
        ) { fusionHealth() }

        val result = repository.configure(" 192.168.1.10 ")

        assertEquals("http://192.168.1.10:8000", result.address)
        assertEquals("http://192.168.1.10:8000", settings.url)
        assertEquals(1, settings.clearTokenCalls)
        assertEquals(1, rebuilds)
        assertTrue(result.sessionInvalidated)
    }

    @Test
    fun configuringUnsupportedSameAddressClearsExistingSession() = runBlocking {
        val settings = FakeSettings("https://fusion.example.com")
        settings.accessToken = "stale"
        val repository = repository(settings) {
            HealthResponse(status = "ok", execution_mode = "LIVE")
        }

        val result = repository.configure("https://fusion.example.com")

        assertEquals(ConnectionClassification.Unsupported, result.classification)
        assertEquals(1, settings.clearTokenCalls)
        assertEquals(null, settings.accessToken)
        assertTrue(result.sessionInvalidated)
    }

    @Test
    fun transientHealthFailureCanRecoverOnRetry() = runBlocking {
        val settings = FakeSettings("https://fusion.example.com")
        var attempts = 0
        val repository = repository(settings) {
            if (attempts++ == 0) error("connection reset")
            fusionHealth()
        }

        val first = repository.probe("https://fusion.example.com")
        val second = repository.probe("https://fusion.example.com")

        assertEquals(ConnectionClassification.PendingConfiguration, first.classification)
        assertEquals(ConnectionClassification.Fusion, second.classification)
    }

    @Test
    fun expiredSessionIsClearedByRestore() = runBlocking {
        val settings = FakeSettings("https://fusion.example.com")
        settings.accessToken = "expired"
        val unauthorized = HttpException(Response.error<Any>(401, "expired".toResponseBody()))
        val api = fakeApi { method ->
            if (method == "bootstrap") throw unauthorized
            error("unexpected method $method")
        }
        val repository = SessionRepository(
            settings = settings,
            services = ApiServiceProvider { api },
        )

        runCatching { repository.restore() }

        assertEquals(1, settings.clearTokenCalls)
        assertEquals(null, settings.accessToken)
    }

    private fun repository(
        settings: FakeSettings,
        healthTimeoutMillis: Long = ConnectionRepository.HEALTH_TIMEOUT_MILLIS,
        rebuildClient: () -> Unit = {},
        health: suspend () -> HealthResponse,
    ): ConnectionRepository = ConnectionRepository(
        settings = settings,
        services = ApiServiceProvider { fakeApi { error("service should not be used for health") } },
        healthServiceForAddress = { HealthApi { health() } },
        healthTimeoutMillis = healthTimeoutMillis,
        rebuildClient = rebuildClient,
    )

    private fun fusionHealth() = HealthResponse(
        status = "ok",
        database = "ok",
        execution_mode = "RESEARCH_ONLY",
        qmt_enabled = false,
        auto_trading_enabled = false,
    )

    private fun fakeApi(onCall: (String) -> Any?): ApiService = Proxy.newProxyInstance(
        javaClass.classLoader,
        arrayOf(ApiService::class.java),
    ) { _, method, _ ->
        when (method.name) {
            "toString" -> "FakeApiService"
            else -> onCall(method.name)
        }
    } as ApiService

    private class FakeSettings(
        initialUrl: String,
    ) : ConnectionSettings, SessionSettings {
        var url: String = initialUrl
        var accessToken: String? = null
        var refreshToken: String? = null
        var clearTokenCalls: Int = 0

        override suspend fun currentBaseUrl(): String = url

        override suspend fun setBaseUrl(url: String) {
            this.url = url
        }

        override suspend fun currentAccessToken(): String? = accessToken

        override suspend fun currentRefreshToken(): String? = refreshToken

        override suspend fun saveTokens(
            access: String,
            refresh: String,
            expiresInSeconds: Long,
            username: String?,
            password: String?,
            rememberPassword: Boolean?,
        ) {
            accessToken = access
            refreshToken = refresh
        }

        override suspend fun clearTokens() {
            clearTokenCalls++
            accessToken = null
            refreshToken = null
        }
    }
}
