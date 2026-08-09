package com.ashareai.app.standalone.data.archive

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.fail
import org.junit.Test

class ArchiveCryptoTest {
    @Test
    fun encryptsAndDecryptsAsharesLocalPayload() {
        val plainText = "本地档案，不含 API Key".toByteArray()
        val encrypted = ArchiveCrypto.encrypt(plainText, "correct horse".toCharArray())

        assertArrayEquals(plainText, ArchiveCrypto.decrypt(encrypted, "correct horse".toCharArray()))
    }

    @Test
    fun rejectsWrongPassphraseAndTampering() {
        val encrypted = ArchiveCrypto.encrypt("payload".toByteArray(), "secret".toCharArray())
        try {
            ArchiveCrypto.decrypt(encrypted, "wrong".toCharArray())
            fail("wrong passphrase must fail")
        } catch (_: Exception) {
            // AES-GCM authentication failure is expected.
        }
    }
}
