package com.ashareai.app.standalone.data.ai

import com.ashareai.app.standalone.domain.AiProvider
import kotlinx.serialization.Serializable

/**
 * AI Agent角色定义
 *
 * 类似qmt项目的多Agent架构，为不同任务分配专门的Agent
 */
enum class AiAgentRole(
    val displayName: String,
    val taskSummary: String,
    val outputHint: String,
    val systemPrompt: String,
    val preferredModels: List<String> = emptyList(),
    val recommendedRetries: Int = 2,
    val recommendedTimeoutSeconds: Int = 60,
    val recommendedCache: Boolean = true,
) {
    RESEARCH_ANALYST(
        displayName = "研究分析师",
        taskSummary = "把指标和行情数据整理成可执行的研究结论",
        outputHint = "结论 · 依据 · 风险 · 下一步",
        systemPrompt = """你是A股本地研究助手。职责：
1. 解读技术指标（MACD、RSI、ATR、均线）
2. 分析K线形态和趋势
3. 评估风险收益比
4. 给出明确的买入/持有/观望建议

约束：
- 不虚构基本面数据、事件或价格
- 不修改本地规则计算的止损止盈门槛
- 承认不知道的信息（如未来业绩、宏观政策）
- 使用简洁专业的语言""",
        preferredModels = listOf("gpt-5-mini", "claude-sonnet-4", "deepseek-chat"),
        recommendedTimeoutSeconds = 90,
    ),

    CHAT_ASSISTANT(
        displayName = "对话助手",
        taskSummary = "回答行情、报告和持仓问题，适合连续追问",
        outputHint = "先给答案，再给数据依据和风险提示",
        systemPrompt = """你是A股本地助手，为用户提供投资咨询。职责：
1. 回答股票行情和技术指标问题
2. 解释本地研究报告内容
3. 提供投资建议和风险提示
4. 帮助理解持仓状态

约束：
- 基于提供的行情和技术数据回答
- 不虚构数据或预测
- 不能修改风险门槛
- 保持客观中立""",
        preferredModels = listOf("gpt-5-mini", "claude-haiku-4-5", "deepseek-chat"),
        recommendedRetries = 1,
        recommendedTimeoutSeconds = 45,
    ),

    REPORT_SUMMARIZER(
        displayName = "报告摘要",
        taskSummary = "把确定性研究结果压缩成易读的摘要",
        outputHint = "重点结论 · 市场概况 · 风险提醒（200-500字）",
        systemPrompt = """你是研究报告摘要生成器。职责：
1. 将确定性技术分析结果转化为易读的中文摘要
2. 突出重点候选股票和风险提示
3. 总结市场整体状况
4. 格式化为Markdown

约束：
- 严格基于提供的数据
- 不添加主观判断
- 保持简洁（200-500字）""",
        preferredModels = listOf("gpt-5-mini", "claude-haiku-4-5", "deepseek-chat"),
        recommendedRetries = 1,
        recommendedTimeoutSeconds = 60,
    ),

    EXIT_ADVISOR(
        displayName = "卖出顾问",
        taskSummary = "解释退出信号和持仓风险，帮助复核卖出决定",
        outputHint = "触发条件 · 当前风险 · 建议动作 · 失效条件",
        systemPrompt = """你是卖出策略顾问。职责：
1. 解释止损止盈逻辑
2. 分析当前持仓风险
3. 建议是否需要调整仓位
4. 提醒市场风险

约束：
- 不能修改ATR计算的止损线
- 不能修改浮盈退出门槛
- 只能建议，不能执行""",
        preferredModels = listOf("gpt-5-mini", "claude-sonnet-4", "deepseek-chat"),
        recommendedTimeoutSeconds = 75,
    )
}

/**
 * Agent配置
 */
@Serializable
data class AiAgentConfig(
    val id: String,
    val role: AiAgentRole,
    val providerId: String? = null,
    val fallbackProviderIds: List<String> = emptyList(),
    val enabled: Boolean = true,
    val maxRetries: Int = 2,
    val timeoutSeconds: Int = 60,
    val temperature: Double? = null, // null则使用默认
    val enableCache: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Agent分配策略
 */
object AiAgentRouter {
    /**
     * 根据任务类型选择合适的Agent
     */
    fun selectAgent(task: AiTaskType, agents: List<AiAgentConfig>): AiAgentConfig? {
        val role = when (task) {
            AiTaskType.RESEARCH_EXPLANATION -> AiAgentRole.RESEARCH_ANALYST
            AiTaskType.CHAT -> AiAgentRole.CHAT_ASSISTANT
            AiTaskType.REPORT_SUMMARY -> AiAgentRole.REPORT_SUMMARIZER
            AiTaskType.EXIT_ANALYSIS -> AiAgentRole.EXIT_ADVISOR
        }

        // 优先选择启用的、角色匹配的Agent
        return agents
            .filter { it.enabled && it.role == role }
            .maxByOrNull { it.createdAt } // 使用最新配置
    }

    /**
     * 获取后备Agent（如果首选Agent失败）
     */
    fun getFallbackAgent(task: AiTaskType, agents: List<AiAgentConfig>, excludeId: String): AiAgentConfig? {
        return agents
            .filter { it.enabled && it.id != excludeId }
            .maxByOrNull { it.createdAt }
    }

    fun route(
        runtime: AiRuntimeSnapshot,
        agents: List<AiAgentConfig>,
        providers: List<AiProvider>,
        providerStates: List<AiProviderRuntimeState> = emptyList(),
        explicitProviderId: String? = null,
    ): AiRoutingDecision {
        val role = roleFor(runtime.task)
        val stateById = providerStates.associateBy { it.providerId }
        val usableProviders = providers.filter { provider ->
            provider.enabled && stateById[provider.id]?.enabled != false &&
                stateById[provider.id]?.temporarilyUnavailable != true
        }
        val configuredAgent = agents
            .filter { it.enabled && it.role == role }
            .maxByOrNull { it.createdAt }
        val preferredIds = buildList {
            add(explicitProviderId ?: configuredAgent?.providerId)
            addAll(configuredAgent?.fallbackProviderIds.orEmpty())
        }.filterNotNull().distinct()
        val selectedProvider = preferredIds
            .asSequence()
            .mapNotNull { id -> usableProviders.firstOrNull { it.id == id } }
            .firstOrNull()
            ?: usableProviders.minByOrNull { it.createdAt }
        val fallbackProviders = buildList {
            preferredIds.forEach { id -> usableProviders.firstOrNull { it.id == id }?.let(::add) }
            usableProviders.sortedBy { it.createdAt }.forEach(::add)
        }.distinctBy { it.id }.filterNot { it.id == selectedProvider?.id }

        if (runtime.localInferenceAvailable && runtime.memoryAvailableMb >= 512 && runtime.batteryPercent >= 20 && runtime.thermalStatus < 3 &&
            runtime.network in setOf(
                LocalInferenceDetector.NetworkType.NONE,
                LocalInferenceDetector.NetworkType.TWO_G,
                LocalInferenceDetector.NetworkType.THREE_G,
            )
        ) {
            return AiRoutingDecision(
                task = runtime.task,
                role = role,
                agent = configuredAgent,
                provider = selectedProvider,
                target = AiExecutionTarget.LOCAL,
                reason = "弱网或离线，端侧推理可用",
                fallbackProviders = fallbackProviders,
            )
        }
        if (selectedProvider != null && runtime.batteryPercent >= 15 && runtime.thermalStatus < 4) {
            val reason = when {
                runtime.batteryPercent < 30 && !runtime.charging -> "低电量，选择可用云端 Provider"
                runtime.network == LocalInferenceDetector.NetworkType.WIFI -> "Wi-Fi 与资源状态良好"
                else -> "按任务自动选择可用 Provider"
            }
            return AiRoutingDecision(runtime.task, role, configuredAgent, selectedProvider, AiExecutionTarget.CLOUD, reason, fallbackProviders)
        }
        return AiRoutingDecision(
            task = runtime.task,
            role = role,
            agent = configuredAgent,
            provider = selectedProvider,
            target = if (configuredAgent?.enableCache != false) AiExecutionTarget.CACHE else AiExecutionTarget.DETERMINISTIC,
            reason = if (selectedProvider == null) "没有可用 Provider，回退本地缓存或确定性结果" else "设备资源受限，优先缓存",
            fallbackProviders = fallbackProviders,
        )
    }

    private fun roleFor(task: AiTaskType): AiAgentRole = when (task) {
        AiTaskType.RESEARCH_EXPLANATION -> AiAgentRole.RESEARCH_ANALYST
        AiTaskType.CHAT -> AiAgentRole.CHAT_ASSISTANT
        AiTaskType.REPORT_SUMMARY -> AiAgentRole.REPORT_SUMMARIZER
        AiTaskType.EXIT_ANALYSIS -> AiAgentRole.EXIT_ADVISOR
    }
}

/**
 * AI任务类型
 */
enum class AiTaskType {
    RESEARCH_EXPLANATION, // 研究报告AI解释
    CHAT, // 用户对话
    REPORT_SUMMARY, // 报告摘要生成
    EXIT_ANALYSIS // 卖出分析
}

enum class AiExecutionTarget {
    LOCAL,
    CLOUD,
    CACHE,
    DETERMINISTIC,
}

data class AiRuntimeSnapshot(
    val task: AiTaskType,
    val batteryPercent: Int = 100,
    val charging: Boolean = false,
    val network: LocalInferenceDetector.NetworkType = LocalInferenceDetector.NetworkType.NONE,
    val localInferenceAvailable: Boolean = false,
    val thermalStatus: Int = 0,
    val memoryAvailableMb: Long = Long.MAX_VALUE,
)

data class AiProviderRuntimeState(
    val providerId: String,
    val enabled: Boolean = true,
    val temporarilyUnavailable: Boolean = false,
    val unavailableUntil: Long? = null,
)

data class AiRoutingDecision(
    val task: AiTaskType,
    val role: AiAgentRole,
    val agent: AiAgentConfig?,
    val provider: AiProvider?,
    val target: AiExecutionTarget,
    val reason: String,
    val fallbackProviders: List<AiProvider> = emptyList(),
)

/**
 * Provider能力标签
 */
enum class ProviderCapability {
    CHAT_COMPLETION, // 基础对话
    STREAMING, // 流式输出
    FUNCTION_CALLING, // 函数调用
    VISION, // 视觉理解
    LONG_CONTEXT // 长上下文
}

/**
 * Provider模板
 */
object ProviderTemplates {
    val OPENAI = AiProviderDraft(
        name = "OpenAI",
        baseUrl = "https://api.openai.com",
        apiKey = "",
        model = "gpt-4.1-mini",
        organization = null,
        project = null
    )

    val ANTHROPIC = AiProviderDraft(
        name = "Anthropic",
        baseUrl = "https://api.anthropic.com",
        apiKey = "",
        model = "claude-3-haiku-20240307",
        organization = null,
        project = null
    )

    val DEEPSEEK = AiProviderDraft(
        name = "DeepSeek",
        baseUrl = "https://api.deepseek.com",
        apiKey = "",
        model = "deepseek-chat",
        organization = null,
        project = null
    )

    val MOONSHOT = AiProviderDraft(
        name = "Moonshot",
        baseUrl = "https://api.moonshot.cn",
        apiKey = "",
        model = "moonshot-v1-8k",
        organization = null,
        project = null
    )

    val ZHIPU = AiProviderDraft(
        name = "智谱AI",
        baseUrl = "https://open.bigmodel.cn",
        apiKey = "",
        model = "glm-4",
        organization = null,
        project = null
    )

    fun all() = listOf(OPENAI, ANTHROPIC, DEEPSEEK, MOONSHOT, ZHIPU)
}
