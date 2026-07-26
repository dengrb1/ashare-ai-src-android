package com.ashareai.app.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.model.ArchiveApplyRequest
import com.ashareai.app.data.model.ArchiveExportRequest
import com.ashareai.app.data.model.PersonalArchiveJob
import com.ashareai.app.data.newIdempotencyKey
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.components.*
import com.ashareai.app.ui.isActiveStatus
import com.ashareai.app.ui.statusLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 个人档案：加密导出（下载 .ashare 文件到下载目录）。导入建议在 Web 端操作。 */
@Composable
fun PersonalDataScreen(appViewModel: AppViewModel) {
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    var passphrase by remember { mutableStateOf("") }
    var job by remember { mutableStateOf<PersonalArchiveJob?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }

    // 任务态轮询
    LaunchedEffect(job?.status) {
        val current = job ?: return@LaunchedEffect
        val id = current.export_id ?: current.job_id ?: return@LaunchedEffect
        while (isActiveStatus(job?.status)) {
            delay(1500)
            try {
                job = ApiClient.api.exportStatus(id)
            } catch (e: Exception) {
                error = e.toUserMessage()
                break
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopAppBarSimple(title = "个人档案")

        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AppCard {
                    Text("加密导出", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "导出持仓、自选、研究、报告、回测与文字对话（不含图片与凭据），用口令加密为 .ashare 档案。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("加密口令（至少 8 位）") },
                        singleLine = true,
                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = {
                            if (passphrase.length < 8) {
                                error = "口令至少 8 位"
                                return@Button
                            }
                            working = true
                            error = null
                            scope.launch {
                                try {
                                    job = ApiClient.api.createExport(ArchiveExportRequest(passphrase))
                                    info = "导出任务已提交"
                                } catch (e: Exception) {
                                    error = e.toUserMessage()
                                } finally {
                                    working = false
                                }
                            }
                        },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("发起导出") }
                }
            }

            job?.let { j ->
                item {
                    AppCard {
                        Text("导出任务", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(6.dp))
                        KeyValueRow("状态", statusLabel(j.status))
                        j.progress?.let { KeyValueRow("进度", "$it%") }
                        j.error_message?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                        }
                        if (j.status.uppercase() in setOf("SUCCEEDED", "COMPLETED", "SUCCESS")) {
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = {
                                    val id = j.export_id ?: j.job_id ?: return@Button
                                    working = true
                                    scope.launch {
                                        try {
                                            val body = ApiClient.api.downloadExport(id)
                                            val fileName = "ashare-export-${System.currentTimeMillis()}.ashare"
                                            val values = android.content.ContentValues().apply {
                                                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, fileName)
                                                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                                            }
                                            val resolver = context.contentResolver
                                            val uri = resolver.insert(
                                                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values,
                                            )
                                            if (uri != null) {
                                                resolver.openOutputStream(uri)?.use { out ->
                                                    body.byteStream().use { input -> input.copyTo(out) }
                                                }
                                                info = "已保存到下载目录：$fileName"
                                            } else {
                                                error = "无法写入下载目录"
                                            }
                                        } catch (e: Exception) {
                                            error = e.toUserMessage()
                                        } finally {
                                            working = false
                                        }
                                    }
                                },
                                enabled = !working,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("下载档案") }
                        }
                    }
                }
            }

            item {
                AppCard {
                    Text("档案导入", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "档案导入涉及逐项冲突合并，建议在 Web 端完成。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            error?.let { item { ErrorBanner(it) { error = null } } }
            info?.let {
                item {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}
