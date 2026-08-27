package com.ashareai.app.data

import okhttp3.OkHttpClient

/**
 * Small seam around the Retrofit service. Production uses [ApiClient]; tests can
 * provide an in-memory service without rebuilding the networking stack.
 */
fun interface ApiServiceProvider {
    fun service(): ApiService

    /**
     * Health diagnostics are unauthenticated in production. Test providers can
     * use the default implementation to expose their fake service as health.
     */
    fun healthService(baseUrl: String): HealthApi = service()

    /** Optional transport for streaming endpoints; ordinary fakes can omit it. */
    fun httpClient(): OkHttpClient? = null
}

/** Public portion of the API required before a session exists. */
fun interface HealthApi {
    suspend fun health(): com.ashareai.app.data.model.HealthResponse
}
