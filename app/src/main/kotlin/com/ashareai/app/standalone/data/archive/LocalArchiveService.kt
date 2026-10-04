package com.ashareai.app.standalone.data.archive

import com.ashareai.app.standalone.data.LocalRepository
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

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

    suspend fun import(
        payload: ByteArray,
        passphrase: CharArray,
        scopes: Set<String> = ArchiveScope.all,
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): ArchiveImportSummary = try {
        val archive = decode(payload, passphrase)
        local.applyImportArchive(
            archive = archive,
            preview = local.previewImportArchive(archive, scopes),
            scopes = scopes,
            idempotencyKey = idempotencyKey,
        )
    } finally {
        passphrase.fill('\u0000')
    }

    suspend fun preview(
        payload: ByteArray,
        passphrase: CharArray,
        scopes: Set<String> = ArchiveScope.all,
    ): ArchiveMergePreview = try {
        local.previewImportArchive(decode(payload, passphrase), scopes)
    } finally {
        passphrase.fill('\u0000')
    }

    suspend fun apply(
        payload: ByteArray,
        passphrase: CharArray,
        preview: ArchiveMergePreview,
        resolutions: Map<String, ArchiveMergeResolution> = emptyMap(),
        idempotencyKey: String = UUID.randomUUID().toString(),
        scopes: Set<String> = ArchiveScope.all,
    ): ArchiveImportSummary = try {
        local.applyImportArchive(decode(payload, passphrase), preview, resolutions, idempotencyKey, scopes)
    } finally {
        passphrase.fill('\u0000')
    }

    private fun decode(payload: ByteArray, passphrase: CharArray): LocalArchiveSnapshot {
        val plainText = ArchiveCrypto.decrypt(payload, passphrase).toString(Charsets.UTF_8)
        val archive = json.decodeFromString<LocalArchiveSnapshot>(plainText)
        require(archive.formatVersion == 1) { "不支持的 .ashare-local 档案版本" }
        return archive
    }
}
