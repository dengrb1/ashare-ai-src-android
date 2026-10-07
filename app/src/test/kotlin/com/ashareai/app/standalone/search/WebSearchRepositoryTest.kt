package com.ashareai.app.standalone.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WebSearchRepositoryTest {
    @Test
    fun parsesAndBoundsSearchResultsWithSourceUrls() {
        val html = """
            <div class="result">
              <a class="result__a" href="https://example.com/news">Company filing</a>
              <a class="result__snippet">Quarterly filing published</a>
            </div>
            <div class="result">
              <a class="result__a" href="https://second.example/news">Market update</a>
              <a class="result__snippet">Company announces update</a>
            </div>
        """.trimIndent()

        val results = WebSearchRepository.parseResults(html, limit = 1)

        assertEquals(1, results.size)
        assertEquals("Company filing", results.single().title)
        assertEquals("example.com", results.single().source)
        assertTrue(results.single().url.startsWith("https://"))
    }

    @Test
    fun rejectsNonHttpRedirectTargetsAndNonPositiveLimits() {
        val html = """
            <div class="result"><a class="result__a" href="javascript:alert(1)">Bad</a></div>
        """.trimIndent()

        assertEquals(emptyList<WebSearchHit>(), WebSearchRepository.parseResults(html, limit = 3))
        assertEquals(emptyList<WebSearchHit>(), WebSearchRepository.parseResults(html, limit = 0))
    }
}
