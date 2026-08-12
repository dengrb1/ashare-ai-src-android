package com.ashareai.app.data

import com.ashareai.app.data.model.AIChatAttachment
import com.ashareai.app.data.model.AIChatMessage
import com.ashareai.app.data.model.AIChatThreadPage
import com.ashareai.app.data.model.AICostSummary
import com.ashareai.app.data.model.AIModelsResponse
import com.ashareai.app.data.model.FinancialSearchResult
import com.ashareai.app.ui.screens.sanitizeMarkdown
import kotlinx.serialization.decodeFromString
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AIContractTest {
    private val json = ApiClient.json

    @Test
    fun decodesBackendThreadMessageAttachmentAndCostContracts() {
        val models = json.decodeFromString<AIModelsResponse>(
            """{"models":["gpt-test"],"reasoning_efforts":["medium"],"web_search_available":true,"cache_enabled":true}""",
        )
        assertEquals("gpt-test", models.models.single())

        val threads = json.decodeFromString<AIChatThreadPage>(
            """{"items":[{"thread_id":"t1","user_id":"u1","title":"研究","group_mode":"MANUAL","group_type":"SINGLE","group_label":"贵州茅台","cumulative_mentions":[{"symbol":"600519.SH","name":"贵州茅台"}],"pinned_at":"2026-07-27T00:00:00Z","archived_at":null,"created_at":"2026-07-27T00:00:00Z","updated_at":"2026-07-27T00:00:00Z"}],"next_cursor":null}""",
        )
        assertEquals("贵州茅台", threads.items.single().group_label)

        val message = json.decodeFromString<AIChatMessage>(
            """{"message_id":"m1","thread_id":"t1","role":"assistant","content":"ok","status":"COMPLETED","trading_date":"2026-07-27","decision_at":"2026-07-27T00:00:00Z","available_at":"2026-07-27T00:00:00Z","mentioned_symbols":[],"mention_refs":[],"attachment_ids":[],"model_name":"gpt-test","reasoning_effort":"medium","sources":[],"context_sha256":null,"response_sha256":null,"cache_hit":true,"input_tokens":10,"cached_input_tokens":5,"cache_write_tokens":0,"output_tokens":2,"reasoning_tokens":0,"cache_policy":"OPENAI","context_budget_status":"WITHIN_BUDGET","error_code":null,"request_id":"r1","streaming_mode":"CACHED","data_status":{},"response_id":"resp1","created_at":"2026-07-27T00:00:00Z"}""",
        )
        assertEquals("CACHED", message.streaming_mode)

        val attachment = json.decodeFromString<AIChatAttachment>(
            """{"attachment_id":"a1","thread_id":"t1","mime_type":"image/png","byte_size":10,"width":1,"height":1,"uploaded_at":"2026-07-27T00:00:00Z","expires_at":"2026-08-03T00:00:00Z","deleted_at":null,"deletion_reason":null}""",
        )
        assertEquals(10, attachment.byte_size)

        val costs = json.decodeFromString<AICostSummary>(
            """{"days":30,"items":[],"next_cursor":null,"totals":{"requests":2,"cache_hits":1,"input_tokens":10,"cached_input_tokens":5,"cache_write_tokens":0,"uncached_input_tokens":5,"output_tokens":3,"estimated_spend_usd":0.1,"estimated_savings_usd":0.2},"current_turn":null}""",
        )
        assertEquals(2, costs.totals.requests)

        val search = json.decodeFromString<FinancialSearchResult>(
            """{"query":"沪深300今年走势","provider":"ai-intent-deterministic-data","upstream":"tencent","mode":"direct","searched_at":"2026-07-27T00:00:00Z","elapsed_ms":12,"entities":[{"name":"沪深300","code":"000300.SH"}],"recalls":[{"type":"kline","desc":"沪深300 K线","content":"[]"}],"raw_sha256":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","outcome":{"kind":"kline"},"interpretation":"确定性行情","sources":[{"source":"tencent","uri":"https://gu.qq.com/"}],"warnings":[],"live_data_isolated_from_snapshots":true}""",
        )
        assertEquals("000300.SH", search.entities.single().code)
        assertEquals("沪深300 K线", search.recalls.single().desc)
    }

    @Test
    fun markdownRemovesRemoteImagesHtmlAndInlineLinks() {
        val safe = sanitizeMarkdown("<script>x</script> ![secret](https://x/a.png) [click](javascript:alert(1))")
        assertFalse(safe.contains("<script>"))
        assertFalse(safe.contains("javascript:"))
        assertEquals("x [图片已隐藏] click)", safe)
    }
}
