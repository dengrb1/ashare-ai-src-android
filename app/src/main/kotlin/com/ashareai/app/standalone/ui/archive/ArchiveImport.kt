package com.ashareai.app.standalone.ui.archive

import android.content.Context
import android.net.Uri
import com.ashareai.app.standalone.data.LocalRepository
import com.ashareai.app.standalone.data.archive.ArchiveImportSummary
import com.ashareai.app.standalone.data.archive.LocalArchiveService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 执行归档导入。
 *
 * @param context Android Context 用于读取文件
 * @param uri 归档文件 URI
 * @param passphrase 解密口令
 * @param localRepository 本地数据仓库
 * @return 导入摘要
 */
suspend fun importArchive(
    context: Context,
    uri: Uri,
    passphrase: String,
    localRepository: LocalRepository,
): ArchiveImportSummary = withContext(Dispatchers.IO) {
    val payload = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        ?: throw IllegalArgumentException("无法读取归档文件")

    val service = LocalArchiveService(localRepository)
    service.import(payload, passphrase.toCharArray())
}
