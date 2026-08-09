package com.ashareai.app.standalone.data.archive

import java.nio.ByteBuffer
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object ArchiveCrypto {
    private val magic = byteArrayOf(0x41, 0x53, 0x4c, 0x31)
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val ITERATIONS = 210_000
    private const val KEY_BITS = 256

    fun encrypt(plainText: ByteArray, passphrase: CharArray, random: SecureRandom = SecureRandom()): ByteArray {
        require(passphrase.isNotEmpty()) { "档案口令不能为空" }
        val salt = ByteArray(SALT_SIZE).also(random::nextBytes)
        val iv = ByteArray(IV_SIZE).also(random::nextBytes)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv))
        val encrypted = cipher.doFinal(plainText)
        return ByteBuffer.allocate(magic.size + 2 + salt.size + iv.size + encrypted.size)
            .put(magic)
            .put(salt.size.toByte())
            .put(iv.size.toByte())
            .put(salt)
            .put(iv)
            .put(encrypted)
            .array()
    }

    fun decrypt(payload: ByteArray, passphrase: CharArray): ByteArray {
        require(passphrase.isNotEmpty()) { "档案口令不能为空" }
        require(payload.size > magic.size + 2 + SALT_SIZE + IV_SIZE) { "档案数据不完整" }
        val buffer = ByteBuffer.wrap(payload)
        val receivedMagic = ByteArray(magic.size).also(buffer::get)
        require(receivedMagic.contentEquals(magic)) { "不是 .ashare-local 档案" }
        val saltSize = buffer.get().toInt() and 0xff
        val ivSize = buffer.get().toInt() and 0xff
        require(saltSize == SALT_SIZE && ivSize == IV_SIZE && buffer.remaining() > saltSize + ivSize) {
            "档案格式无效"
        }
        val salt = ByteArray(saltSize).also(buffer::get)
        val iv = ByteArray(ivSize).also(buffer::get)
        val encrypted = ByteArray(buffer.remaining()).also(buffer::get)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(passphrase, salt), GCMParameterSpec(128, iv))
        return cipher.doFinal(encrypted)
    }

    private fun key(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS)
        return try {
            SecretKeySpec(
                SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded,
                "AES",
            )
        } finally {
            spec.clearPassword()
        }
    }
}
