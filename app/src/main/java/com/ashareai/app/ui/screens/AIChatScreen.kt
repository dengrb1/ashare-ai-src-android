package com.ashareai.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.ashareai.app.data.ApiClient
import com.ashareai.app.data.ChatStreamClient
import com.ashareai.app.data.ChatStreamEvent
import com.ashareai.app.data.model.*
import com.ashareai.app.data.toUserMessage
import com.ashareai.app.ui.AppViewModel
import com.ashareai.app.ui.components.CompactTopBar
import com.ashareai.app.ui.components.EmptyPlaceholder
import com.ashareai.app.ui.components.ErrorBanner
import com.ashareai.app.ui.fmtTime
import com.mikepenz.markdown.m3.Markdown
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.UUID

internal val quickQuestions = listOf(
    "解读最新系统研究报告" to "请解读最新系统研究报告，概括市场状态、候选概览和风险结论。",
    "生成个股省流版" to "请生成个股省流版，并说明是否适合继续查看模拟方案。",
    "分析持仓风险" to "请结合我的持仓、最新系统研究和风险结论，分析当前持仓风险。",
    "比较候选股票" to "请比较 @股票A 和 @股票B 的最新系统研究结论、门禁和主要风险。",
)

internal fun appendQuickQuestion(draft: TextFieldValue, question: String): TextFieldValue {
    val existing = draft.text.trimEnd()
    val next = if (existing.isEmpty()) question else "$existing $question"
    return TextFieldValue(next, androidx.compose.ui.text.TextRange(next.length))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIChatScreen(appViewModel: AppViewModel) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val assets by appViewModel.assets.collectAsState()
    val quotes by appViewModel.quotes.collectAsState()

    var threads by remember { mutableStateOf<List<AIChatThread>>(emptyList()) }
    var currentThread by remember { mutableStateOf<AIChatThread?>(null) }
    var messages by remember { mutableStateOf<List<AIChatMessage>>(emptyList()) }
    var models by remember { mutableStateOf<AIModelsResponse?>(null) }
    var selectedModel by remember { mutableStateOf<String?>(null) }
    var reasoningEffort by remember { mutableStateOf("medium") }
    var webSearch by remember { mutableStateOf(true) }
    var costSummary by remember { mutableStateOf<AICostSummary?>(null) }

    var draft by remember { mutableStateOf(TextFieldValue("")) }
    var attachments by remember { mutableStateOf<List<AIChatAttachment>>(emptyList()) }
    var uploading by remember { mutableStateOf(false) }
    var streaming by remember { mutableStateOf(false) }
    var streamingText by remember { mutableStateOf("") }
    var streamJob by remember { mutableStateOf<Job?>(null) }
    var stages by remember { mutableStateOf<Map<String, Pair<String, Boolean>>>(emptyMap()) }
    var streamingMode by remember { mutableStateOf<String?>(null) }
    var dataStatus by remember { mutableStateOf<JsonObject?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    var search by remember { mutableStateOf("") }
    var showArchived by remember { mutableStateOf(false) }
    var selectedThreads by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showThreadSheet by remember { mutableStateOf(false) }
    var editingThread by remember { mutableStateOf<AIChatThread?>(null) }
    var editMode by remember { mutableStateOf<String?>(null) }
    var editText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    val mentionOptions = remember(assets, quotes) {
        val positionNames = assets?.positions.orEmpty().associate { it.symbol to it.name }
        (assets?.positions.orEmpty().map { it.symbol } + assets?.watchlist.orEmpty())
            .distinct()
            .map { symbol -> AIChatMentionRef(symbol, positionNames[symbol].orEmpty().ifBlank { quotes[symbol]?.name ?: symbol }) }
    }
    val mentionMatch = remember(draft) { currentMention(draft.text, draft.selection.start) }
    val mentionCandidates = remember(mentionMatch, mentionOptions) {
        val query = mentionMatch?.query.orEmpty().lowercase()
        if (mentionMatch == null) emptyList() else mentionOptions.filter {
            query.isBlank() || it.name.lowercase().contains(query) || it.symbol.lowercase().contains(query)
        }.take(6)
    }

    suspend fun loadThreads(preferredId: String? = currentThread?.thread_id) {
        val page = ApiClient.api.aiThreadIndex(
            limit = 100,
            archived = showArchived.takeIf { it },
            query = search.trim().takeIf { it.isNotEmpty() },
        )
        threads = page.items
        currentThread = page.items.firstOrNull { it.thread_id == preferredId }
            ?: currentThread?.takeIf { current -> page.items.any { it.thread_id == current.thread_id } }
    }

    suspend fun loadMessages(thread: AIChatThread) {
        messages = ApiClient.api.aiMessages(thread.thread_id)
        costSummary = runCatching { ApiClient.api.aiCosts(threadId = thread.thread_id) }.getOrNull()
    }

    suspend fun createThread(): AIChatThread {
        val created = ApiClient.api.createThread(AIChatThreadCreate("新对话"))
        currentThread = created
        messages = emptyList()
        attachments = emptyList()
        loadThreads(created.thread_id)
        return created
    }

    fun patchThread(thread: AIChatThread, patch: AIChatThreadPatch) = scope.launch {
        runCatching { ApiClient.api.patchThread(thread.thread_id, patch) }
            .onSuccess { loadThreads(it.thread_id) }
            .onFailure { error = it.toUserMessage() }
    }

    fun deleteThread(thread: AIChatThread) = scope.launch {
        runCatching { ApiClient.api.deleteThread(thread.thread_id) }
            .onSuccess {
                if (currentThread?.thread_id == thread.thread_id) {
                    currentThread = null
                    messages = emptyList()
                }
                selectedThreads -= thread.thread_id
                loadThreads()
            }
            .onFailure { error = it.toUserMessage() }
    }

    suspend fun uploadUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (attachments.size + uris.size > 4) {
            error = "每条消息最多 4 张图片"
            return
        }
        val resolver = context.contentResolver
        val payloads = uris.mapNotNull { uri ->
            val length = resolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1
            if (length > 10L * 1024 * 1024) return@mapNotNull null
            val bytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: return@mapNotNull null
            Triple(uri, resolver.getType(uri) ?: "application/octet-stream", bytes)
        }
        val total = payloads.sumOf { it.third.size.toLong() } + attachments.sumOf { it.byte_size }
        if (payloads.size != uris.size || total > 25L * 1024 * 1024) {
            error = "单张不超过 10 MB，每条消息合计不超过 25 MB"
            return
        }
        uploading = true
        try {
            val thread = currentThread ?: createThread()
            val parts = payloads.mapIndexed { index, (_, mime, bytes) ->
                MultipartBody.Part.createFormData(
                    "files",
                    "chat-${UUID.randomUUID()}-$index.${mime.substringAfter('/', "jpg")}",
                    bytes.toRequestBody(mime.toMediaTypeOrNull()),
                )
            }
            val uploaded = ApiClient.api.uploadAttachments(
                parts,
                thread.thread_id.toRequestBody("text/plain".toMediaTypeOrNull()),
            )
            attachments = attachments + uploaded
        } finally {
            uploading = false
        }
    }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        scope.launch { runCatching { uploadUris(uris) }.onFailure { error = it.toUserMessage() } }
    }

    fun send() {
        val content = draft.text.trim().ifEmpty { if (attachments.isNotEmpty()) "请分析这些图片" else "" }
        val model = selectedModel ?: return
        if (content.isEmpty() || streaming || uploading) return
        val usedAttachments = attachments
        val mentions = mentionOptions.filter { content.contains("@${it.name}") || content.contains("@${it.symbol}", ignoreCase = true) }.take(5)
        draft = TextFieldValue("")
        attachments = emptyList()
        streamJob = scope.launch {
            var assistantId = "local-assistant-${System.currentTimeMillis()}"
            val started = java.time.Instant.now().toString()
            try {
                val thread = currentThread ?: createThread()
                messages = messages + AIChatMessage(
                    message_id = "local-user-${System.currentTimeMillis()}",
                    thread_id = thread.thread_id,
                    role = "user",
                    content = content,
                    status = "COMPLETED",
                    mentioned_symbols = mentions.map { it.symbol },
                    mention_refs = mentions,
                    attachment_ids = usedAttachments.map { it.attachment_id },
                    created_at = started,
                )
                streaming = true
                streamingText = ""
                stages = emptyMap()
                streamingMode = null
                dataStatus = null
                ChatStreamClient.stream(
                    appViewModel.settings,
                    thread.thread_id,
                    AIChatSendRequest(
                        content = content,
                        model = model,
                        reasoning_effort = reasoningEffort,
                        web_search = webSearch,
                        attachment_ids = usedAttachments.map { it.attachment_id },
                        mention_refs = mentions,
                    ),
                ).collect { event ->
                    when (event) {
                        is ChatStreamEvent.Delta -> streamingText += event.text
                        is ChatStreamEvent.Stage -> {
                            val hit = event.payload["cache_hit"]?.jsonPrimitive?.booleanOrNull == true
                            stages = stages + (event.stage to ((event.status ?: "STARTED") to hit))
                            if (event.status == "DEGRADED") streamingMode = "DEGRADED"
                        }
                        is ChatStreamEvent.Meta -> {
                            assistantId = event.payload["assistant_message_id"]?.jsonPrimitive?.contentOrNull ?: assistantId
                            dataStatus = event.payload["data_status"]?.let { runCatching { it.jsonObject }.getOrNull() }
                        }
                        is ChatStreamEvent.Done -> {
                            streamingMode = event.payload["streaming_mode"]?.jsonPrimitive?.contentOrNull ?: streamingMode
                        }
                        is ChatStreamEvent.Error -> throw IllegalStateException(event.message)
                        ChatStreamEvent.Closed -> Unit
                    }
                }
                loadMessages(thread)
                loadThreads(thread.thread_id)
            } catch (cancelled: CancellationException) {
                if (streamingText.isNotBlank()) messages = messages + partialMessage(assistantId, currentThread, streamingText, "CANCELLED", started)
            } catch (failure: Exception) {
                if (streamingText.isNotBlank()) messages = messages + partialMessage(assistantId, currentThread, streamingText, "FAILED", started)
                error = failure.toUserMessage()
            } finally {
                streaming = false
                streamingText = ""
            }
        }
    }

    LaunchedEffect(Unit) {
        runCatching { loadThreads() }.onFailure { error = it.toUserMessage() }
        runCatching { ApiClient.api.aiModels() }.onSuccess {
            models = it
            selectedModel = it.models.firstOrNull()
            reasoningEffort = it.reasoning_efforts.firstOrNull { effort -> effort == "medium" }
                ?: it.reasoning_efforts.firstOrNull() ?: "medium"
            webSearch = it.web_search_available
        }
    }
    LaunchedEffect(search, showArchived) {
        kotlinx.coroutines.delay(180)
        runCatching { loadThreads() }.onFailure { error = it.toUserMessage() }
    }
    LaunchedEffect(messages.size, streamingText) {
        val count = messages.size + if (streaming) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    val threadPanel: @Composable () -> Unit = {
        ThreadPanel(
            threads = threads,
            current = currentThread,
            search = search,
            archived = showArchived,
            selected = selectedThreads,
            onSearch = { search = it },
            onArchived = { showArchived = it },
            onToggleSelected = { id -> selectedThreads = selectedThreads.toMutableSet().apply { if (!add(id)) remove(id) } },
            onOpen = { thread -> currentThread = thread; showThreadSheet = false; scope.launch { loadMessages(thread) } },
            onNew = { scope.launch { createThread(); showThreadSheet = false } },
            onRename = { editingThread = it; editMode = "rename"; editText = it.title },
            onGroup = { editingThread = it; editMode = "group"; editText = it.group_label.orEmpty() },
            onPin = { patchThread(it, AIChatThreadPatch(pinned = it.pinned_at == null)) },
            onArchive = { patchThread(it, AIChatThreadPatch(archived = it.archived_at == null)) },
            onDelete = ::deleteThread,
            onBulkDelete = {
                scope.launch {
                    runCatching { ApiClient.api.bulkDeleteThreads(BulkDeleteThreads(selectedThreads.toList())) }
                        .onSuccess { selectedThreads = emptySet(); loadThreads() }
                        .onFailure { error = it.toUserMessage() }
                }
            },
        )
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 840.dp) {
            Row(Modifier.fillMaxSize()) {
                Surface(Modifier.width(310.dp).fillMaxHeight(), tonalElevation = 1.dp) { threadPanel() }
                VerticalDivider()
                ChatBody(
                    modifier = Modifier.weight(1f), currentThread, messages, listState, streaming,
                    streamingText, stages, streamingMode, dataStatus, costSummary, error,
                    onClearError = { error = null }, onOpenThreads = null, onNew = { scope.launch { createThread() } },
                    draft, onDraft = { draft = it }, mentionCandidates, mentionMatch,
                    onMention = { candidate -> draft = insertMention(draft, requireNotNull(mentionMatch), candidate) },
                    attachments, onRemoveAttachment = { id -> attachments = attachments.filterNot { it.attachment_id == id } },
                    onPickImage = { imagePicker.launch("image/*") }, uploading, models, selectedModel,
                    onModel = { selectedModel = it }, reasoningEffort, onEffort = { reasoningEffort = it },
                    webSearch, onWebSearch = { webSearch = it }, onSend = ::send,
                    onStop = { streamJob?.cancel() }, appViewModel = appViewModel,
                )
            }
        } else {
            ChatBody(
                Modifier.fillMaxSize(), currentThread, messages, listState, streaming, streamingText,
                stages, streamingMode, dataStatus, costSummary, error, { error = null },
                { showThreadSheet = true }, { scope.launch { createThread() } }, draft, { draft = it },
                mentionCandidates, mentionMatch, { draft = insertMention(draft, requireNotNull(mentionMatch), it) },
                attachments, { id -> attachments = attachments.filterNot { it.attachment_id == id } },
                { imagePicker.launch("image/*") }, uploading, models, selectedModel, { selectedModel = it },
                reasoningEffort, { reasoningEffort = it }, webSearch, { webSearch = it }, ::send,
                { streamJob?.cancel() }, appViewModel,
            )
        }
    }

    if (showThreadSheet) ModalBottomSheet(onDismissRequest = { showThreadSheet = false }) {
        Box(Modifier.fillMaxWidth().heightIn(max = 620.dp)) { threadPanel() }
    }
    if (editingThread != null && editMode != null) {
        AlertDialog(
            onDismissRequest = { editingThread = null; editMode = null },
            title = { Text(if (editMode == "rename") "重命名对话" else "调整分组") },
            text = { OutlinedTextField(editText, { editText = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    val target = editingThread ?: return@TextButton
                    if (editMode == "rename" && editText.isNotBlank()) patchThread(target, AIChatThreadPatch(title = editText.trim()))
                    if (editMode == "group") patchThread(target, AIChatThreadPatch(group_label = editText.trim().ifBlank { null }))
                    editingThread = null; editMode = null
                }) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { editingThread = null; editMode = null }) { Text("取消") } },
        )
    }
}

private data class MentionMatch(val query: String, val start: Int, val end: Int)

private fun currentMention(value: String, caret: Int): MentionMatch? {
    val before = value.take(caret.coerceIn(0, value.length))
    val match = Regex("(?:^|\\s)@([^\\s@]*)$").find(before) ?: return null
    val query = match.groupValues[1]
    return MentionMatch(query, before.length - query.length - 1, before.length)
}

private fun insertMention(value: TextFieldValue, match: MentionMatch, mention: AIChatMentionRef): TextFieldValue {
    val inserted = "@${mention.name} "
    val next = value.text.replaceRange(match.start, match.end, inserted)
    val caret = match.start + inserted.length
    return TextFieldValue(next, androidx.compose.ui.text.TextRange(caret))
}

private fun partialMessage(id: String, thread: AIChatThread?, content: String, status: String, created: String) =
    AIChatMessage(
        message_id = id,
        thread_id = thread?.thread_id,
        role = "assistant",
        content = content,
        status = status,
        created_at = created,
    )

@Composable
private fun ThreadPanel(
    threads: List<AIChatThread>, current: AIChatThread?, search: String, archived: Boolean,
    selected: Set<String>, onSearch: (String) -> Unit, onArchived: (Boolean) -> Unit,
    onToggleSelected: (String) -> Unit, onOpen: (AIChatThread) -> Unit, onNew: () -> Unit,
    onRename: (AIChatThread) -> Unit, onGroup: (AIChatThread) -> Unit,
    onPin: (AIChatThread) -> Unit, onArchive: (AIChatThread) -> Unit,
    onDelete: (AIChatThread) -> Unit, onBulkDelete: () -> Unit,
) {
    Column(Modifier.fillMaxSize().padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("对话记录", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            FilledIconButton(onClick = onNew, modifier = Modifier.size(40.dp)) { Icon(Icons.Outlined.Add, "新对话") }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            search, onSearch, modifier = Modifier.fillMaxWidth(), singleLine = true,
            leadingIcon = { Icon(Icons.Outlined.Search, null) }, placeholder = { Text("搜索对话或分组") },
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(archived, onCheckedChange = onArchived)
            Text("已归档", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.weight(1f))
            if (selected.isNotEmpty()) TextButton(onClick = onBulkDelete) { Text("删除 ${selected.size} 项", color = MaterialTheme.colorScheme.error) }
        }
        if (threads.isEmpty()) EmptyPlaceholder(if (archived) "暂无已归档对话" else "暂无对话") else LazyColumn {
            items(threads, key = { it.thread_id }) { thread ->
                var menu by remember { mutableStateOf(false) }
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp))
                        .background(if (thread.thread_id == current?.thread_id) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface)
                        .clickable { onOpen(thread) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(thread.thread_id in selected, { onToggleSelected(thread.thread_id) })
                    Column(Modifier.weight(1f)) {
                        Text((if (thread.pinned_at != null) "◆ " else "") + thread.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${thread.group_label ?: "综合问答"} · ${thread.updated_at.fmtTime()}", style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "对话操作") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("重命名") }, { menu = false; onRename(thread) })
                            DropdownMenuItem({ Text("调整分组") }, { menu = false; onGroup(thread) })
                            DropdownMenuItem({ Text(if (thread.pinned_at == null) "置顶" else "取消置顶") }, { menu = false; onPin(thread) })
                            DropdownMenuItem({ Text(if (thread.archived_at == null) "归档" else "恢复") }, { menu = false; onArchive(thread) })
                            DropdownMenuItem({ Text("删除", color = MaterialTheme.colorScheme.error) }, { menu = false; onDelete(thread) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBody(
    modifier: Modifier, currentThread: AIChatThread?, messages: List<AIChatMessage>,
    listState: androidx.compose.foundation.lazy.LazyListState, streaming: Boolean, streamingText: String,
    stages: Map<String, Pair<String, Boolean>>, streamingMode: String?, dataStatus: JsonObject?,
    costSummary: AICostSummary?, error: String?, onClearError: () -> Unit,
    onOpenThreads: (() -> Unit)?, onNew: () -> Unit, draft: TextFieldValue,
    onDraft: (TextFieldValue) -> Unit, mentionCandidates: List<AIChatMentionRef>, mentionMatch: MentionMatch?,
    onMention: (AIChatMentionRef) -> Unit, attachments: List<AIChatAttachment>,
    onRemoveAttachment: (String) -> Unit, onPickImage: () -> Unit, uploading: Boolean,
    models: AIModelsResponse?, selectedModel: String?, onModel: (String) -> Unit,
    effort: String, onEffort: (String) -> Unit, webSearch: Boolean, onWebSearch: (Boolean) -> Unit,
    onSend: () -> Unit, onStop: () -> Unit, appViewModel: AppViewModel,
) {
    Column(modifier.imePadding()) {
        CompactTopBar(
            title = currentThread?.title ?: "AI 问答",
            navigation = onOpenThreads?.let { action -> { IconButton(onClick = action) { Icon(Icons.Outlined.History, "会话列表") } } },
            actions = { IconButton(onClick = onNew) { Icon(Icons.Outlined.Add, "新对话") } },
        )
        error?.let { Box(Modifier.padding(horizontal = 12.dp)) { ErrorBanner(it, onClearError) } }
        StageStrip(stages)
        if (streamingMode == "DEGRADED") {
            Text("流式已降级：当前模型网关返回一次性结果", modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(8.dp), style = MaterialTheme.typography.bodySmall)
        }
        if (dataStatus != null && dataStatus.isNotEmpty()) {
            Text("数据状态已同步", modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        CostStrip(costSummary)
        Box(Modifier.weight(1f)) {
            if (messages.isEmpty() && !streaming) EmptyPlaceholder("问点什么吧") else LazyColumn(
                state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages, key = { it.message_id }) { MessageBubble(it, appViewModel) }
                if (streaming) item("streaming") { MessageBubble(partialMessage("stream", currentThread, streamingText.ifEmpty { "…" }, "STREAMING", ""), appViewModel) }
            }
        }
        if (attachments.isNotEmpty()) PendingAttachments(attachments, onRemoveAttachment, appViewModel)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            quickQuestions.forEach { (label, value) -> AssistChip(onClick = { onDraft(appendQuickQuestion(draft, value)) }, label = { Text(label) }) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            models?.models?.takeIf { it.isNotEmpty() }?.let { options ->
                AssistChip(onClick = { onModel(options[(options.indexOf(selectedModel) + 1).mod(options.size)]) }, label = { Text(selectedModel ?: "模型", maxLines = 1) })
            }
            models?.reasoning_efforts?.takeIf { it.isNotEmpty() }?.let { options ->
                AssistChip(onClick = { onEffort(options[(options.indexOf(effort) + 1).mod(options.size)]) }, label = { Text("思考 $effort") })
            }
            if (models?.web_search_available == true) FilterChip(webSearch, { onWebSearch(!webSearch) }, label = { Text("联网") })
        }
        Box {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = onPickImage, enabled = !streaming && !uploading && attachments.size < 4) {
                    Icon(if (uploading) Icons.Outlined.HourglassTop else Icons.Outlined.AddPhotoAlternate, "添加图片")
                }
                OutlinedTextField(
                    draft, onDraft, modifier = Modifier.weight(1f), maxLines = 4,
                    placeholder = { Text("输入 @ 可选择持仓或自选股票") }, shape = RoundedCornerShape(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                FilledIconButton(onClick = if (streaming) onStop else onSend, enabled = streaming || ((draft.text.isNotBlank() || attachments.isNotEmpty()) && selectedModel != null && !uploading)) {
                    Icon(if (streaming) Icons.Outlined.Stop else Icons.AutoMirrored.Outlined.Send, if (streaming) "停止" else "发送")
                }
            }
            if (mentionMatch != null && mentionCandidates.isNotEmpty()) {
                Surface(Modifier.align(Alignment.BottomStart).padding(start = 56.dp, bottom = 74.dp).widthIn(min = 230.dp, max = 320.dp), shadowElevation = 6.dp) {
                    Column { mentionCandidates.forEach { option -> ListItem(
                        headlineContent = { Text(option.name) }, supportingContent = { Text(option.symbol) },
                        modifier = Modifier.clickable { onMention(option) },
                    ) } }
                }
            }
        }
    }
}

@Composable
private fun StageStrip(stages: Map<String, Pair<String, Boolean>>) {
    if (stages.isEmpty()) return
    val names = linkedMapOf("retrieval" to "检索", "market" to "行情", "news" to "新闻", "generation" to "生成")
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        names.forEach { (key, label) ->
            val state = stages[key]
            val status = when (state?.first) { "COMPLETED" -> "完成"; "CACHED" -> "缓存"; "DEGRADED" -> "降级"; "STARTED" -> "进行中"; else -> "等待" }
            SuggestionChip(onClick = {}, label = { Text("$label：$status${if (state?.second == true) " · 命中" else ""}") })
        }
    }
}

@Composable
private fun CostStrip(summary: AICostSummary?) {
    summary ?: return
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("本轮 $${"%.6f".format(summary.current_turn?.estimated_spend_usd ?: 0.0)}", style = MaterialTheme.typography.labelSmall)
        Text("30天 $${"%.6f".format(summary.totals.estimated_spend_usd)}", style = MaterialTheme.typography.labelSmall)
        Text("缓存 ${summary.totals.cache_hits}/${summary.totals.requests}", style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun MessageBubble(message: AIChatMessage, appViewModel: AppViewModel) {
    val isUser = message.role == "user"
    val uriHandler = LocalUriHandler.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 680.dp).clip(RoundedCornerShape(8.dp))
                .background(if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                .padding(12.dp),
        ) {
            if (!isUser) Markdown(sanitizeMarkdown(message.content)) else Text(message.content)
            if (message.status !in listOf(null, "COMPLETED", "STREAMING")) Text(if (message.status == "CANCELLED") "未完成" else "回复失败", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
            if (message.cache_hit || message.streaming_mode != "STREAMING") Text(listOfNotNull(if (message.cache_hit) "缓存命中" else null, message.streaming_mode.takeIf { it != "STREAMING" }).joinToString(" · "), style = MaterialTheme.typography.labelSmall)
            if (message.attachment_ids.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState())) { message.attachment_ids.forEach { AttachmentImage(it, appViewModel) } }
            if (message.sources.isNotEmpty()) {
                Spacer(Modifier.height(6.dp)); Text("数据来源", style = MaterialTheme.typography.labelMedium)
                message.sources.take(8).forEach { source ->
                    val link = safeLink(source.uri ?: source.url)
                    Text(
                        source.title ?: source.symbol ?: source.source ?: link ?: "系统数据",
                        modifier = if (link != null) Modifier.clickable { uriHandler.openUri(link) } else Modifier,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (link != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PendingAttachments(items: List<AIChatAttachment>, onRemove: (String) -> Unit, appViewModel: AppViewModel) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items.forEach { item -> Box { AttachmentImage(item.attachment_id, appViewModel); IconButton(onClick = { onRemove(item.attachment_id) }, modifier = Modifier.align(Alignment.TopEnd).size(28.dp)) { Icon(Icons.Outlined.Close, "移除") } } }
    }
}

@Composable
private fun AttachmentImage(id: String, appViewModel: AppViewModel) {
    var baseUrl by remember { mutableStateOf<String?>(null) }
    var token by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    LaunchedEffect(id) { baseUrl = appViewModel.settings.currentBaseUrl(); token = appViewModel.settings.currentAccessToken() }
    if (failed) {
        Box(Modifier.size(96.dp).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) { Text("图片已过期", style = MaterialTheme.typography.labelSmall) }
    } else baseUrl?.let { base ->
        AsyncImage(
            model = ImageRequest.Builder(context).data("${base.trimEnd('/')}/api/v1/ai/chat/attachments/$id/content")
                .apply { token?.let { addHeader("Authorization", "Bearer $it") } }.build(),
            contentDescription = "对话附图", modifier = Modifier.size(96.dp).clip(RoundedCornerShape(6.dp)),
            onError = { failed = true },
        )
    }
}

private fun safeLink(value: String?): String? = value?.takeIf { it.startsWith("https://") || it.startsWith("http://") }

internal fun sanitizeMarkdown(content: String): String = content
    .replace(Regex("<[^>]+>"), "")
    .replace(Regex("!\\[([^]]*)]\\([^)]*\\)"), "[图片已隐藏]")
    .replace(Regex("\\[([^]]+)]\\(([^)]+)\\)"), "$1")
