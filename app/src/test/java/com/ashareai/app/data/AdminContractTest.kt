package com.ashareai.app.data

import com.ashareai.app.data.model.EdgeGatewayConfiguration
import com.ashareai.app.data.model.ModelSettings
import com.ashareai.app.data.model.SystemResources
import com.ashareai.app.data.model.SystemSettings
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminContractTest {
    private val json = ApiClient.json

    @Test
    fun decodesVersionedModelSettingsWithoutReturningApiSecret() {
        val settings = json.decodeFromString<ModelSettings>(
            """{
                "configuration_id":"cfg-1","version":3,"config_sha256":"hash","source":"database","provider":"openai-compatible",
                "base_url":"https://gateway.example.com/v1","api_key_configured":true,
                "search_model":"gpt-search","search_reasoning_effort":"low","research_model":"gpt-research","research_reasoning_effort":"high",
                "model_profiles":[{"model":"gpt-research","cache_policy":"OPENAI","context_window_tokens":128000,"output_token_reserve":8192,"reasoning_token_reserve":1024,"input_price_per_million":1,"cached_input_price_per_million":0.1,"cache_write_price_per_million":0,"output_price_per_million":4}],
                "timeout_seconds":90,"enabled":true,"configured":true,"reachable":true,"degraded":false,"status_message":"ok","checked_at":"2026-08-12T00:00:00Z","structured_output_supported":true,"streaming_supported":true
            }""",
        )

        assertTrue(settings.api_key_configured)
        assertEquals("gpt-research", settings.model_profiles.single().model)
        assertEquals(1024, settings.model_profiles.single().reasoning_token_reserve)
    }

    @Test
    fun decodesSystemResourcesAndVersionedOverrides() {
        val resources = json.decodeFromString<SystemResources>(
            """{"collected_at":"2026-08-12T00:00:00Z","scope":"CONTAINER","scope_label":"容器","memory":{"total_bytes":1000,"used_bytes":400,"available_bytes":600,"percent":40},"cpu":{"percent":20,"logical_cores":4},"disk":{"total_bytes":2000,"used_bytes":500,"available_bytes":1500,"percent":25},"services":[{"service_id":"api","role":"api","healthy":true,"memory_used_bytes":100,"memory_cache_bytes":10,"memory_limit_bytes":800,"cpu_percent":5}],"topology_estimate":{"worker_replicas":1,"estimate_source":"fallback","typical_per_worker_bytes":100,"typical_increment_bytes":100,"maximum_increment_bytes":200,"projected_available_bytes":500,"level":"NORMAL","messages":[]},"level":"NORMAL","warnings":[]}""",
        )
        val settings = json.decodeFromString<SystemSettings>(
            """{"configuration_id":"sys-1","version":2,"config_sha256":"hash","source":"database","values":{"api_runtime_mode":"LIGHTWEIGHT","energy_saving_enabled":true},"sources":{"api_runtime_mode":"database"},"secret_configured":{"tushare_token":true},"secret_sources":{"tushare_token":"database"},"read_only_environment":{},"topology_sha256":"topology","actual_loaded_mode":"SERIAL","restart_required":false,"workers":[],"queues":{},"compose_restart_command":"docker compose restart"}""",
        )

        assertEquals(600, resources.memory.available_bytes)
        assertEquals("LIGHTWEIGHT", settings.values["api_runtime_mode"]?.jsonPrimitive?.content)
        assertTrue(settings.secret_configured["tushare_token"] == true)
    }

    @Test
    fun decodesEdgeGatewayMetadataBeforeUnlock() {
        val gateway = json.decodeFromString<EdgeGatewayConfiguration>(
            """{"configuration_id":"edge-1","version":7,"enabled":true,"validation_mode":"STRICT","proxy_hosts":[{"id":"host-1","name":"web","domains":["research.example.com"],"forward_scheme":"http","forward_host":"web","forward_port":80,"ssl_enabled":true,"websocket_support":true,"enabled":true,"notes":""}],"config_sha256":"abc","apply_status":"APPLIED","apply_message":"ok","applied_at":"2026-08-12T00:00:00Z","source_sync":true}""",
        )

        assertEquals("APPLIED", gateway.apply_status)
        assertEquals("research.example.com", gateway.proxy_hosts.single().domains.single())
        assertFalse(gateway.frpc_toml.isNotEmpty())
    }
}
