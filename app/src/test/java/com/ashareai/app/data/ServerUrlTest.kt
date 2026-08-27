package com.ashareai.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerUrlTest {
    @Test
    fun normalizesLanAddressAndAddsDefaultApiPort() {
        assertEquals(
            "http://192.168.1.10:8000",
            normalizeServerUrl(" 192.168.1.10 ").getOrThrow(),
        )
        assertEquals(
            "https://api.example.com/service",
            normalizeServerUrl("https://api.example.com/service/").getOrThrow(),
        )
        assertEquals(
            "https://fusion.example.com",
            normalizeServerUrl("fusion.example.com").getOrThrow(),
        )
    }

    @Test
    fun rejectsLoopbackAddressesThatPointToThePhoneItself() {
        listOf(
            "http://127.0.0.1:8000",
            "http://127.20.30.40:8000",
            "http://localhost:8000",
            "http://[::1]:8000",
            "http://[0:0:0:0:0:0:0:1]:8000",
            "http://[0:0:0:0:0:ffff:7f00:1]:8000",
            "http://[::ffff:127.0.0.1]:8000",
        ).forEach { input ->
            val result = normalizeServerUrl(input)
            assertTrue("expected loopback address to fail: $input", result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("局域网 IP"))
        }
    }

    @Test
    fun rejectsServerBindAddressAsClientDestination() {
        listOf("http://0.0.0.0:8000", "http://[::]:8000").forEach { input ->
            val result = normalizeServerUrl(input)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("服务器监听") ||
                result.exceptionOrNull()?.message.orEmpty().contains("局域网 IP"))
        }
    }

    @Test
    fun rejectsCredentialsQueryAndUnsupportedScheme() {
        listOf(
            "http://user:secret@192.168.1.10:8000",
            "http://192.168.1.10:8000?token=secret",
            "ftp://192.168.1.10",
        ).forEach { input ->
            assertTrue("expected invalid server URL to fail: $input", normalizeServerUrl(input).isFailure)
        }
    }

    @Test
    fun doesNotTreatMalformedHostSegmentsAsPrivateIpv4() {
        assertTrue(normalizeServerUrl("http://192.example.168.1:8000").isFailure)
        assertTrue(normalizeServerUrl("http://127.example.0.1:8000").isFailure)
    }

    @Test
    fun rejectsPublicPlaintextAndInternalBridgePorts() {
        listOf(
            "http://fusion.example.com",
            "http://8.8.8.8:8000",
            "http://192.168.1.10:8787",
            "https://fusion.example.com:8081",
            "https://fusion.example.com:8082",
        ).forEach { input ->
            assertTrue("expected restricted destination to fail: $input", normalizeServerUrl(input).isFailure)
        }
    }

    @Test
    fun recognizesOldLoopbackAddressesForPendingConfigurationMigration() {
        assertTrue("http://127.0.0.1:8000".isLegacyLoopbackAddress())
        assertTrue("127.0.0.1:8000".isLegacyLoopbackAddress())
        assertTrue("http://localhost:8000".isLegacyLoopbackAddress())
        assertTrue("http://0.0.0.0:8000".isLegacyLoopbackAddress())
        assertTrue(!"https://fusion.example.com".isLegacyLoopbackAddress())
    }
}
