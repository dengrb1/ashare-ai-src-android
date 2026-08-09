package com.ashareai.app.standalone.data.archive

import com.ashareai.app.standalone.data.LocalRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class LocalArchiveService(
    private val local: LocalRepository,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    suspend fun export(passphrase: CharArray): ByteArray = try {
        val serialized = json.encodeToString(local.exportArchive())
        ArchiveCrypto.encrypt(serialized.toByteArray(Charsets.UTF_8), passphrase)
    } finally {
        passphrase.fill('\u0000')
    }

    suspend fun import(payload: ByteArray, passphrase: CharArray): ArchiveImportSummary = try {
        val plainText = ArchiveCrypto.decrypt(payload, passphrase).toString(Charsets.UTF_8)
        val archive = json.decodeFromString<LocalArchiveSnapshot>(plainText)
        require(archive.formatVersion == 1) { "不支持的 .ashare-local 档案版本" }
        local.importArchive(archive)
    } finally {
        passphrase.fill('\u0000')
    }
}
