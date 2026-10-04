package com.ashareai.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.ProfileDataViewModel
import com.ashareai.app.ui.ScreenState
import com.ashareai.app.ui.components.*
import com.ashareai.app.ui.isActiveStatus
import com.ashareai.app.ui.statusLabel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

private const val MAX_ARCHIVE_BYTES = 512L * 1024L * 1024L

/** Server archive export/import. Local .ashare-local files are handled by the local workspace. */
@Composable
fun PersonalDataScreen(appViewModel: AppViewModel) {
    val profileViewModel: ProfileDataViewModel = viewModel()
    val profileState by profileViewModel.state.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = appViewModel.screenContext()
    val scope = rememberCoroutineScope()
    var passphrase by remember { mutableStateOf("") }
    var importPassphrase by remember { mutableStateOf("") }
    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    var totalAssetsChoice by remember { mutableStateOf("CURRENT") }
    var positionsChoice by remember { mutableStateOf("CURRENT") }
    var applied by remember { mutableStateOf(false) }

    val content = when (val state = profileState) {
        is ScreenState.Content -> state.value
        is ScreenState.Error -> state.previous
        else -> null
    }
    val exportJob = content?.job
    val importJob = content?.importJob
    val stateError = (profileState as? ScreenState.Error)?.message

    LaunchedEffect(lifecycleOwner, exportJob?.status, importJob?.status) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                delay(1500)
                exportJob?.takeIf { isActiveStatus(it.status) }?.archiveId?.let(profileViewModel::refreshExport)
                importJob?.takeIf { isActiveStatus(it.status) }?.archiveId?.let(profileViewModel::refreshImport)
            }
        }
    }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        selectedUri = uri
        error = null
        applied = false
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
                    Text(
                        "导出持仓、自选、研究、报告、回测与文字对话（不含图片与凭据）。服务器档案只能在连接工作区预览和导入。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("加密口令（至少 8 位）") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (passphrase.length < 8) error = "口令至少 8 位" else {
                                working = true
                                profileViewModel.createExport(passphrase) { message ->
                                    if (message == null) info = "导出任务已提交" else error = message
                                    working = false
                                }
                            }
                        },
                        enabled = !working,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("发起导出") }
                }
            }

            exportJob?.let { job ->
                item {
                    AppCard {
                        Text("导出任务", style = MaterialTheme.typography.titleSmall)
                        KeyValueRow("状态", statusLabel(job.status))
                        job.progress?.let { KeyValueRow("进度", "$it%") }
                        job.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        if (job.status.uppercase() in setOf("SUCCEEDED", "COMPLETED", "SUCCESS")) {
                            Button(
                                onClick = {
                                    val id = job.archiveId ?: return@Button
                                    working = true
                                    scope.launch {
                                        try {
                                            val body = profileViewModel.downloadExport(id)
                                            val name = "ashare-export-${System.currentTimeMillis()}.ashare"
                                            val values = android.content.ContentValues().apply {
                                                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, name)
                                                put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
                                            }
                                            val uri = context.contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                                            requireNotNull(uri) { "无法写入下载目录" }
                                            context.contentResolver.openOutputStream(uri)?.use { out ->
                                                body.byteStream().use { input -> input.copyTo(out) }
                                            }
                                            info = "已保存到下载目录：$name"
                                        } catch (t: Throwable) {
                                            error = t.toUserMessage()
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
                    Text("导入服务器档案", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "选择服务器导出的 .ashare 文件。上传后服务端会解密并生成逐项预览；未完成预览前不会写入数据。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { filePicker.launch("application/octet-stream") }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Outlined.FileOpen, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(selectedUri?.lastPathSegment ?: "选择 .ashare 档案")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = importPassphrase,
                        onValueChange = { importPassphrase = it },
                        label = { Text("档案口令") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val uri = selectedUri ?: return@Button
                            if (importPassphrase.length < 8) {
                                error = "口令至少 8 位"
                                return@Button
                            }
                            working = true
                            scope.launch {
                                try {
                                    val bytes = readLimited(context, uri)
                                    profileViewModel.createImport(bytes, importPassphrase) { message ->
                                        if (message == null) info = "导入预览已提交" else error = message
                                    }
                                } catch (t: Throwable) {
                                    error = t.toUserMessage()
                                } finally {
                                    working = false
                                }
                            }
                        },
                        enabled = selectedUri != null && importPassphrase.isNotBlank() && importJob == null && !working,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("上传并生成预览") }
                }
            }

            importJob?.let { job ->
                item {
                    AppCard {
                        Text("导入预览", style = MaterialTheme.typography.titleSmall)
                        KeyValueRow("状态", statusLabel(job.status))
                        job.progress?.let { KeyValueRow("进度", "$it%") }
                        job.errorMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                        job.previewOrResult?.let {
                            Spacer(Modifier.height(6.dp))
                            Text("服务端差异", style = MaterialTheme.typography.labelLarge)
                            Text(it.toString(), style = MaterialTheme.typography.bodySmall)
                        }
                        if (job.status.uppercase() in setOf("SUCCEEDED", "COMPLETED", "SUCCESS") && !applied) {
                            Spacer(Modifier.height(8.dp))
                            Text("冲突处理", style = MaterialTheme.typography.labelLarge)
                            ChoiceRow("总资产保留当前", totalAssetsChoice == "CURRENT") { totalAssetsChoice = "CURRENT" }
                            ChoiceRow("总资产使用导入", totalAssetsChoice == "IMPORTED") { totalAssetsChoice = "IMPORTED" }
                            ChoiceRow("持仓冲突保留当前", positionsChoice == "CURRENT") { positionsChoice = "CURRENT" }
                            ChoiceRow("持仓冲突使用导入", positionsChoice == "IMPORTED") { positionsChoice = "IMPORTED" }
                            Button(
                                onClick = {
                                    val id = job.archiveId ?: return@Button
                                    working = true
                                    profileViewModel.applyImport(
                                        id,
                                        JsonObject(
                                            mapOf(
                                                "total_assets" to JsonPrimitive(totalAssetsChoice),
                                                "positions" to JsonPrimitive(positionsChoice),
                                            ),
                                        ),
                                    ) { message ->
                                        working = false
                                        if (message == null) {
                                            applied = true
                                            info = "档案已提交合并，服务端将保持幂等"
                                        } else error = message
                                    }
                                },
                                enabled = !working,
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("确认合并") }
                        } else if (applied) {
                            Text("合并任务已提交；重复点击不会重复创建记录。", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }

            (stateError ?: error)?.let { message -> item { ErrorBanner(message) { error = null } } }
            info?.let { message -> item { Text(message, color = MaterialTheme.colorScheme.primary) } }
        }
    }
}

@Composable
private fun ChoiceRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall)
        RadioButton(selected = selected, onClick = onClick)
    }
}

private suspend fun readLimited(context: android.content.Context, uri: Uri): ByteArray =
    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(32 * 1024)
            var total = 0L
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                total += read
                require(total <= MAX_ARCHIVE_BYTES) { "档案超过 512 MB 上限" }
                output.write(buffer, 0, read)
            }
            output.toByteArray()
        } ?: error("无法读取归档文件")
    }
