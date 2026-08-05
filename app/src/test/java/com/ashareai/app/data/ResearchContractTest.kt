package com.ashareai.app.data

import com.ashareai.app.data.model.ResearchRequest
import com.ashareai.app.data.model.Run
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchContractTest {
    @Test
    fun encodesSupremeModeAsAnExplicitResearchFlag() {
        val payload = ApiClient.json.encodeToString(
            ResearchRequest.serializer(),
            ResearchRequest(
                trading_date = "2026-08-05",
                scope = "MARKET",
                supreme_mode = true,
            ),
        )

        assertTrue(payload.contains("\"supreme_mode\":true"))
    }

    @Test
    fun decodesExecutionProfileAndKeepsLegacyDefaults() {
        val run = ApiClient.json.decodeFromString<Run>(
            """{
                "run_id":"run-1",
                "status":"RUNNING",
                "supreme_mode":true,
                "data_readiness_state":"WAITING_FOR_BENCHMARKS",
                "execution_profile":{
                    "policy_version":"supreme-mode-v1",
                    "mode":"SUPREME",
                    "data_fetch_workers":8,
                    "model_agent_max_concurrency":2,
                    "model_concurrency_changed":false,
                    "resource_scope":"CONTAINER",
                    "logical_cores":4,
                    "cpu_percent":31.5,
                    "available_memory_bytes":1048576000,
                    "memory_budget_bytes":880803840,
                    "resource_level":"NORMAL",
                    "reason_codes":["SUPREME_MODE_ACTIVE"]
                }
            }""",
        )

        assertTrue(run.supreme_mode)
        assertEquals("WAITING_FOR_BENCHMARKS", run.data_readiness_state)
        assertEquals(8, run.execution_profile?.data_fetch_workers)
        assertEquals("CONTAINER", run.execution_profile?.resource_scope)
        assertEquals(listOf("SUPREME_MODE_ACTIVE"), run.execution_profile?.reason_codes)

        val legacy = ApiClient.json.decodeFromString<Run>("""{"run_id":"legacy"}""")
        assertFalse(legacy.supreme_mode)
        assertEquals(null, legacy.execution_profile)
    }
}
