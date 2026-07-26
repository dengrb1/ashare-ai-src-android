package com.ashareai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.ChatStreamClient
import com.ashareai.app.data.ChatStreamEvent
import com.ashareai.app.data.model.*
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.components.EmptyPlaceholder
import com.ashareai.app.ui.components.ErrorBanner
import com.ashareai.app.ui.fmtTime
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** AI 股票问答：流式 SSE 对话 + 会话管理 + 模型/思考强度选择。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen(appViewModel: AppViewModel) {
    val scope = rememberCoroutineScope()

    var threads by remember { mutableStateOf<List<AIChatThread>>(emptyList()) }
    var currentThread by remember { mutableStateOf<AIChatThread?>(null) }
    var messages by remember { mutableStateOf<List<AIChatMessage>>(emptyList()) }
    var models by remember { mutableStateOf<AIModelsResponse?>(null) }
    var selectedModel by remember { mutableStateOf<String?>(null) }
    var reasoningEffort by remember { mutableStateOf<String?>(null) }
    var webSearch by remember { mutableStateOf(false) }

    var input by remember { mutableStateOf("") }
    var streaming by remember { mutableStateOf(false) }
    var streamingText by remember { mutableStateOf("") }
    var streamStage by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var showThreadSheet by remember { mutableStateOf(false) }
    var streamJob by remember { mutableStateOf<Job?>(null) }

    val listState = rememberLazyListState()

    suspend fun loadThreads() {
        try {
            threads = ApiClient.api.aiThreadIndex(limit = 50).items
        } catch (e: Exception) {
            error = e.toUserMessage()
        }
    }

    suspend fun loadMessages(threadId: String) {
        try {
            messages = ApiClient.api.aiMessages(threadId)
        } catch (e: Exception) {
            error = e.toUserMessage()
        }
    }

    LaunchedEffect(Unit) {
        loadThreads()
        try {
            val m = ApiClient.api.aiModels()
            models = m
            selectedModel = m.models.firstOrNull { it.default }?.let { it.id ?: it.name }
                ?: m.models.firstOrNull()?.let { it.id ?: it.name }
            reasoningEffort = m.reasoning_efforts.firstOrNull { it == "medium" } ?: m.reasoning_efforts.firstOrNull()
        } catch (_: Exception) {
        }
    }

    LaunchedEffect(messages.size, streamingText) {
        val total = messages.size + (if (streaming) 1 else 0)
        if (total > 0) listState.animateScrollToItem(total - 1)
    }

    fun send() {
        val text = input.trim()
        if (text.isEmpty() || streaming) return
        input = ""
        error = null
        streamJob = scope.launch {
            try {
                val thread = currentThread ?: ApiClient.api.createThread(
                    AIChatThreadCreate(title = text.take(20))
                ).also {
                    currentThread = it
                    loadThreads()
                }
                messages = messages + AIChatMessage(
                    message_id = "local-${System.currentTimeMillis()}",
                    role = "user",
                    content = text,
                )
                streaming = true
                streamingText = ""
                streamStage = null

                ChatStreamClient.stream(
                    appViewModel.settings,
                    thread.thread_id,
                    AIChatSendRequest(
                        content = text,
                        model = selectedModel,
                        reasoning_effort = reasoningEffort,
                        web_search = webSearch,
                    ),
                ).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Delta -> streamingText += event.text
                        is ChatStreamEvent.Stage -> streamStage = stageLabel(event.stage, event.status)
                        is ChatStreamEvent.Error -> error = event.message
                        is ChatStreamEvent.Done, ChatStreamEvent.Closed -> Unit
                        is ChatStreamEvent.Meta -> Unit
                    }
                }
            } catch (e: Exception) {
                error = e.toUserMessage()
            } finally {
                streaming = false
                streamStage = null
                // 拉取持久化后的完整消息（含 sources）
                currentThread?.let { loadMessages(it.thread_id) }
                streamingText = ""
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        CenterAlignedTopAppBar(
            title = { Text(currentThread?.title?.ifBlank { null } ?: "AI 问答", style = MaterialTheme.typography.titleMedium, maxLines = 1) },
            navigationIcon = {
                IconButton(onClick = { showThreadSheet = true }) {
                    Icon(Icons.Outlined.History, contentDescription = "会话列表")
                }
            },
            actions = {
                IconButton(onClick = {
                    streamJob?.cancel()
                    streaming = false
                    currentThread = null
                    messages = emptyList()
                }) {
                    Icon(Icons.Outlined.Add, contentDescription = "新对话")
                }
            },
        )

        error?.let { Box(Modifier.padding(horizontal = 16.dp)) { ErrorBanner(it) { error = null } } }

        // 消息列表
        Box(Modifier.weight(1f)) {
            if (messages.isEmpty() && !streaming) {
                EmptyPlaceholder("问点什么吧\n例如：@600000 浦发银行最近走势如何？")
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(messages, key = { it.message_id }) { msg ->
                        MessageBubble(msg.role == "user", msg.content, msg.sources)
                    }
                    if (streaming) {
                        item(key = "streaming") {
                            Column {
                                streamStage?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 4.dp),
                                    )
                                }
                                MessageBubble(false, streamingText.ifEmpty { "…" }, emptyList())
                            }
                        }
                    }
                }
            }
        }

        // 选项条
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            models?.let { m ->
                if (m.reasoning_efforts.isNotEmpty()) {
                    val effortLabels = mapOf("low" to "低", "medium" to "中", "high" to "高", "xhigh" to "超高")
                    FilterChip(
                        selected = false,
                        onClick = {
                            val list = m.reasoning_efforts
                            val idx = list.indexOf(reasoningEffort)
                            reasoningEffort = list[(idx + 1).mod(list.size)]
                        },
                        label = { Text("思考:${effortLabels[reasoningEffort] ?: reasoningEffort ?: "-"}") },
                    )
                }
                if (m.web_search_available) {
                    FilterChip(
                        selected = webSearch,
                        onClick = { webSearch = !webSearch },
                        label = { Text("联网") },
                    )
                }
            }
        }

        // 输入栏
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("输入问题…") },
                modifier = Modifier.weight(1f),
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
            )
            Spacer(Modifier.width(8.dp))
            if (streaming) {
                FilledIconButton(onClick = {
                    streamJob?.cancel()
                    streaming = false
                }) {
                    Icon(Icons.Outlined.Stop, contentDescription = "停止")
                }
            } else {
                FilledIconButton(onClick = { send() }, enabled = input.isNotBlank()) {
                    Icon(Icons.AutoMirrored.Outlined.Send, contentDescription = "发送")
                }
            }
        }
    }

    if (showThreadSheet) {
        ModalBottomSheet(onDismissRequest = { showThreadSheet = false }) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    "历史会话",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                if (threads.isEmpty()) {
                    EmptyPlaceholder("暂无历史会话")
                } else {
                    LazyColumn {
                        items(threads, key = { it.thread_id }) { t ->
                            ListItem(
                                headlineContent = { Text(t.title.ifBlank { "未命名会话" }, maxLines = 1) },
                                supportingContent = { Text(t.updated_at.fmtTime(), style = MaterialTheme.typography.labelSmall) },
                                trailingContent = {
                                    IconButton(onClick = {
                                        scope.launch {
                                            try {
                                                ApiClient.api.deleteThread(t.thread_id)
                                                if (currentThread?.thread_id == t.thread_id) {
                                                    currentThread = null
                                                    messages = emptyList()
                                                }
                                                loadThreads()
                                            } catch (e: Exception) {
                                                error = e.toUserMessage()
                                            }
                                        }
                                    }) {
                                        Icon(
                                            androidx.compose.material.icons.Icons.Outlined.Delete,
                                            contentDescription = "删除",
                                            modifier = Modifier.size(18.dp),
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                },
                                modifier = Modifier.clickable {
                                    showThreadSheet = false
                                    currentThread = t
                                    scope.launch { loadMessages(t.thread_id) }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun stageLabel(stage: String, status: String?): String {
    val label = when (stage.lowercase()) {
        "retrieval", "context" -> "检索系统数据"
        "market", "quotes" -> "获取行情"
        "news", "search", "web_search" -> "联网搜索"
        "generation", "generating" -> "生成回答"
        else -> stage
    }
    return if (status != null) "$label · $status" else label
}

@Composable
private fun MessageBubble(isUser: Boolean, content: String, sources: List<AIChatSource>) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    if (isUser) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                )
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(
                content,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isUser) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurface,
            )
            if (sources.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "来源 ${sources.size} 条",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                sources.take(3).forEach { s ->
                    Text(
                        "· ${s.title ?: s.url ?: ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
