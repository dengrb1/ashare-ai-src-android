package com.ashareai.app.standalone.search

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

data class WebSearchHit(
    val title: String,
    val snippet: String,
    val url: String,
    val source: String,
)

/** Bounded, unauthenticated web search used only as external context for an agent. */
class WebSearchRepository(
    private val httpClient: OkHttpClient,
    private val enabled: () -> Boolean = { true },
    private val maxResults: Int = DEFAULT_MAX_RESULTS,
) {
    suspend fun search(query: String): List<WebSearchHit> = withContext(Dispatchers.IO) {
        if (!enabled() || query.isBlank()) return@withContext emptyList()
        val url = SEARCH_URL.toHttpUrl().newBuilder()
            .addQueryParameter("q", query.take(MAX_QUERY_LENGTH))
            .build()
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "text/html")
            .get()
            .build()
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use emptyList()
                val body = response.body ?: return@use emptyList()
                val html = body.byteStream().use { input ->
                    val output = ByteArrayOutputStream()
                    val buffer = ByteArray(8 * 1024)
                    var remaining = MAX_RESPONSE_BYTES
                    while (remaining > 0) {
                        val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                        if (count <= 0) break
                        output.write(buffer, 0, count)
                        remaining -= count
                    }
                    output.toByteArray().toString(Charsets.UTF_8)
                }
                parseResults(html, maxResults.coerceIn(0, DEFAULT_MAX_RESULTS))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun searchMany(queries: List<String>): List<WebSearchHit> = coroutineScope {
        if (!enabled()) return@coroutineScope emptyList()
        queries.asSequence().map(String::trim).filter(String::isNotBlank).take(DEFAULT_MAX_RESULTS).toList()
            .map { query -> async(Dispatchers.IO) { search(query).firstOrNull() } }
            .awaitAll()
            .filterNotNull()
            .distinctBy(WebSearchHit::url)
            .take(DEFAULT_MAX_RESULTS)
    }

    companion object {
        const val DEFAULT_MAX_RESULTS = 3
        private const val MAX_QUERY_LENGTH = 180
        private const val MAX_RESPONSE_BYTES = 512_000
        private const val SEARCH_URL = "https://html.duckduckgo.com/html/"
        private const val USER_AGENT = "AShareAI-Research/1.0 (Android)"

        fun boundedClient(base: OkHttpClient): OkHttpClient = base.newBuilder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(8, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.SECONDS)
            .build()

        internal fun parseResults(html: String, limit: Int): List<WebSearchHit> {
            if (limit <= 0 || html.isBlank()) return emptyList()
            return Jsoup.parse(html)
                .select(".result")
                .mapNotNull { result ->
                    val anchor = result.selectFirst("a.result__a") ?: return@mapNotNull null
                    val title = anchor.text().trim().take(200).takeIf(String::isNotBlank) ?: return@mapNotNull null
                    val target = anchor.attr("href").let(::resolveTarget)
                        ?.takeIf { it.startsWith("https://") || it.startsWith("http://") }
                        ?: return@mapNotNull null
                    val snippet = result.selectFirst(".result__snippet")?.text()?.trim()?.take(500).orEmpty()
                    val source = target.toHttpUrlOrNullSafe()?.host.orEmpty()
                    WebSearchHit(title, snippet, target, source)
                }
                .distinctBy(WebSearchHit::url)
                .take(limit.coerceAtMost(DEFAULT_MAX_RESULTS))
        }

        private fun resolveTarget(href: String): String? {
            val normalized = if (href.startsWith("//")) "https:$href" else href
            val parsed = normalized.toHttpUrlOrNullSafe() ?: return null
            if (parsed.host != "duckduckgo.com" && parsed.host != "www.duckduckgo.com") return parsed.toString()
            val redirect = parsed.queryParameter("uddg") ?: return null
            return redirect.toHttpUrlOrNullSafe()?.toString()
        }

        private fun String.toHttpUrlOrNullSafe() =
            toHttpUrlOrNull()
    }
}
