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
    }

    @Test
    fun rejectsLoopbackAddressesThatPointToThePhoneItself() {
        listOf(
            "http://127.0.0.1:8000",
            "http://127.20.30.40:8000",
            "http://localhost:8000",
            "http://[::1]:8000",
        ).forEach { input ->
            val result = normalizeServerUrl(input)
            assertTrue("expected loopback address to fail: $input", result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("局域网 IP"))
        }
    }

    @Test
    fun rejectsServerBindAddressAsClientDestination() {
        val result = normalizeServerUrl("http://0.0.0.0:8000")

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("服务器监听"))
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
}
