package com.ashareai.app.standalone.ui.archive

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ashareai.app.standalone.data.LocalRepository
import com.ashareai.app.standalone.data.archive.ArchiveImportSummary
import kotlinx.coroutines.launch

/**
 * 归档导入流程。
 *
 * 用于从旧独立版 (com.ashareai.app.standalone) 导入加密归档。
 */
@Composable
fun ArchiveImportFlow(
    localRepository: LocalRepository,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var passphrase by remember { mutableStateOf("") }
    var isImporting by remember { mutableStateOf(false) }
    var importResult by remember { mutableStateOf<Result<ArchiveImportSummary>?>(null) }
    val scope = rememberCoroutineScope()

    val filePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        selectedUri = uri
        importResult = null
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "导入旧独立版数据",
            style = MaterialTheme.typography.headlineSmall,
        )

        Text(
            text = "从旧设备导出的 .ashare-local 加密归档文件导入自选、持仓、提醒、研究、报告、候选、模拟组合、对话。API Key 不导入，需重新配置。",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = { filePicker.launch("*/*") },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(Icons.Default.Upload, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                text = selectedUri?.let { "已选择：${it.lastPathSegment}" }
                    ?: "选择归档文件",
            )
        }

        OutlinedTextField(
            value = passphrase,
            onValueChange = { passphrase = it },
            label = { Text("归档口令") },
            visualTransformation = PasswordVisualTransformation(),
            enabled = !isImporting,
            modifier = Modifier.fillMaxWidth(),
        )

        importResult?.let { result ->
            result.fold(
                onSuccess = { summary ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "✓ 导入成功",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = "持仓 ${summary.holdings} 条",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "自选 ${summary.watchlist} 只",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "报告 ${summary.reports} 篇",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                text = "对话 ${summary.messages} 条",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                },
                onFailure = { error ->
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = "✗ 导入失败",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                            Text(
                                text = error.message ?: "未知错误",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.weight(1f),
            ) {
                Text("取消")
            }

            Button(
                onClick = {
                    val uri = selectedUri ?: return@Button
                    scope.launch {
                        isImporting = true
                        importResult = runCatching {
                            importArchive(context, uri, passphrase, localRepository)
                        }
                        isImporting = false
                    }
                },
                enabled = selectedUri != null && passphrase.isNotBlank() && !isImporting,
                modifier = Modifier.weight(1f),
            ) {
                if (isImporting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("导入")
            }
        }
    }
}
