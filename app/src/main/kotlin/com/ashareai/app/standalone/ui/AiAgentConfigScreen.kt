package com.ashareai.app.standalone.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.SmartToy
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ashareai.app.standalone.data.ai.AiAgentConfig
import com.ashareai.app.standalone.data.ai.AiAgentRole
import com.ashareai.app.standalone.data.ai.ProviderTemplates
import com.ashareai.app.standalone.domain.AiProvider

/**
 * AI Agent配置屏幕
 *
 * 支持多模型、多供应商的Agent分配
 */
@Composable
fun AiAgentConfigScreen(
    providers: List<AiProvider>,
    agents: List<AiAgentConfig>,
    onSaveAgent: (AiAgentConfig) -> Unit,
    onDeleteAgent: (String) -> Unit,
    onNavigateToProviders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var selectedRole by rememberSaveable { mutableStateOf(AiAgentRole.CHAT_ASSISTANT) }
    var selectedProviderId by rememberSaveable { mutableStateOf<String?>(null) }
    var enableCache by rememberSaveable { mutableStateOf(true) }
    var maxRetries by rememberSaveable { mutableStateOf("2") }
    var timeoutSeconds by rememberSaveable { mutableStateOf("60") }
    var showTemplates by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // 说明卡片
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.SmartToy,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "AI Agent 系统",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "为不同任务分配专门的AI模型和供应商，支持多模型协作、自动缓存和失败重试。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        // Provider快捷入口
        if (providers.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("尚未配置 AI Provider", fontWeight = FontWeight.SemiBold)
                    Text("请先添加至少一个 AI Provider，然后再配置 Agent。", style = MaterialTheme.typography.bodySmall)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = onNavigateToProviders, modifier = Modifier.fillMaxWidth()) {
                        Text("前往配置 Provider")
                    }
                    if (!showTemplates) {
                        Spacer(Modifier.height(8.dp))
                        OutlinedButton(onClick = { showTemplates = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("查看 Provider 模板")
                        }
                    }
                }
            }
        }

        // Provider模板展示
        if (showTemplates) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("常用 Provider 模板", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        TextButton(onClick = { showTemplates = false }) { Text("收起") }
                    }
                    ProviderTemplates.all().forEach { template ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text(template.name, fontWeight = FontWeight.SemiBold)
                                Text("Base URL: ${template.baseUrl}", style = MaterialTheme.typography.bodySmall)
                                Text("默认模型: ${template.model}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }

        // Agent配置表单
        Text("新建 Agent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        Text("Agent 角色", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            AiAgentRole.entries.forEach { role ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedRole == role) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surface
                        },
                    ),
                    border = BorderStroke(
                        1.dp,
                        if (selectedRole == role) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    ),
                    onClick = { selectedRole = role },
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text(role.displayName, fontWeight = FontWeight.SemiBold)
                        Text(
                            role.systemPrompt.lines().first().take(60) + "...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (role.preferredModels.isNotEmpty()) {
                            Text(
                                "推荐模型: ${role.preferredModels.take(2).joinToString(", ")}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }
        }

        Text("选择 Provider", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (providers.isEmpty()) {
            Text("请先添加 Provider", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                providers.filter { it.enabled }.forEach { provider ->
                    FilterChip(
                        selected = selectedProviderId == provider.id,
                        onClick = { selectedProviderId = provider.id },
                        label = { Text("${provider.name} · ${provider.model}") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        Text("高级选项", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("启用响应缓存（24小时）")
            Switch(checked = enableCache, onCheckedChange = { enableCache = it })
        }

        OutlinedTextField(
            value = maxRetries,
            onValueChange = { maxRetries = it.filter { c -> c.isDigit() }.take(1) },
            label = { Text("失败重试次数（0-3）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = timeoutSeconds,
            onValueChange = { timeoutSeconds = it.filter { c -> c.isDigit() }.take(3) },
            label = { Text("超时时间（秒）") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Button(
            onClick = {
                selectedProviderId?.let { providerId ->
                    onSaveAgent(
                        AiAgentConfig(
                            id = "${selectedRole.name}_${System.currentTimeMillis()}",
                            role = selectedRole,
                            providerId = providerId,
                            enabled = true,
                            maxRetries = maxRetries.toIntOrNull()?.coerceIn(0, 3) ?: 2,
                            timeoutSeconds = timeoutSeconds.toIntOrNull()?.coerceIn(10, 300) ?: 60,
                            enableCache = enableCache,
                        ),
                    )
                    // 重置表单
                    selectedProviderId = null
                    enableCache = true
                    maxRetries = "2"
                    timeoutSeconds = "60"
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = selectedProviderId != null,
        ) {
            Icon(Icons.Outlined.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("保存 Agent")
        }

        // 已配置的Agent列表
        Text("已配置的 Agent", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        if (agents.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            ) {
                Text(
                    "暂无配置的 Agent",
                    modifier = Modifier.padding(24.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            agents.forEach { agent ->
                val provider = providers.firstOrNull { it.id == agent.providerId }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (agent.enabled) {
                            MaterialTheme.colorScheme.surface
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                agent.role.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                provider?.let { "${it.name} · ${it.model}" } ?: "Provider 已删除",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                "缓存: ${if (agent.enableCache) "开启" else "关闭"} · 重试: ${agent.maxRetries}次 · 超时: ${agent.timeoutSeconds}秒",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { onDeleteAgent(agent.id) }) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }

        // 缓存统计
        Text("系统状态", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Agent 分配策略", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("研究解释 → ${agents.firstOrNull { it.role == AiAgentRole.RESEARCH_ANALYST && it.enabled }?.let { providers.firstOrNull { p -> p.id == it.providerId }?.name } ?: "未配置"}")
                Text("对话助手 → ${agents.firstOrNull { it.role == AiAgentRole.CHAT_ASSISTANT && it.enabled }?.let { providers.firstOrNull { p -> p.id == it.providerId }?.name } ?: "未配置"}")
                Text("报告摘要 → ${agents.firstOrNull { it.role == AiAgentRole.REPORT_SUMMARIZER && it.enabled }?.let { providers.firstOrNull { p -> p.id == it.providerId }?.name } ?: "未配置"}")
                Text("卖出顾问 → ${agents.firstOrNull { it.role == AiAgentRole.EXIT_ADVISOR && it.enabled }?.let { providers.firstOrNull { p -> p.id == it.providerId }?.name } ?: "未配置"}")
            }
        }
    }
}
