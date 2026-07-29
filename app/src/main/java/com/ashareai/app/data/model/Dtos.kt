package com.ashareai.app.data.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

// 后端字段为 snake_case，属性名直接对齐，配合 Json { ignoreUnknownKeys, isLenient } 解析。
// Decimal 字段后端可能输出字符串，isLenient 模式下可直接解析为 Double。

@Serializable
data class TokenResponse(
    val access_token: String,
    val token_type: String = "bearer",
    val expires_in: Long = 900,
    val refresh_token: String,
    val refresh_expires_in: Long = 2_592_000,
)

@Serializable
data class UserResponse(
    val user_id: String,
    val username: String,
    val role: String = "USER",
    val enabled: Boolean = true,
    val created_at: String? = null,
    val updated_at: String? = null,
)

@Serializable
data class LoginRequest(val username: String, val password: String)

@Serializable
data class RefreshRequest(val refresh_token: String)

@Serializable
data class HealthResponse(
    val status: String = "",
    val version: String? = null,
    val database: String? = null,
)

// ---------- 资产 ----------

@Serializable
data class PaperPosition(
    val symbol: String,
    val name: String? = null,
    val quantity: Double,
    val cost: Double,
    val target_weight: Double? = null,
    val acquired_on: String? = null,
    val profit_trigger_amount: Double? = null,
    val exit_trigger_price: Double? = null,
    val stop_loss_price: Double? = null,
    val stop_loss_mode: String? = null,
    val stop_loss_enabled: Boolean = true,
)

@Serializable
data class AssetState(
    val watchlist: List<String> = emptyList(),
    val positions: List<PaperPosition> = emptyList(),
    val total_assets: Double? = null,
    val exit_monitor_enabled: Boolean = false,
    val default_profit_trigger: Double? = null,
    val stop_loss_monitor_enabled: Boolean = false,
    val buy_monitor_enabled: Boolean = false,
    val market_refresh_interval_seconds: Int = 15,
    val updated_at: String? = null,
)

@Serializable
data class AssetStateRequest(
    val watchlist: List<String>,
    val positions: List<PaperPosition>,
    val total_assets: Double? = null,
    val exit_monitor_enabled: Boolean = false,
    val default_profit_trigger: Double? = null,
    val stop_loss_monitor_enabled: Boolean = false,
    val buy_monitor_enabled: Boolean = false,
    val market_refresh_interval_seconds: Int = 15,
)

@Serializable
data class ExitMonitorRequest(
    val exit_monitor_enabled: Boolean,
    val default_profit_trigger: Double? = null,
    val stop_loss_monitor_enabled: Boolean? = null,
    val buy_monitor_enabled: Boolean? = null,
)

@Serializable
data class MarketRefreshRequest(val market_refresh_interval_seconds: Int)

// ---------- 行情 ----------

@Serializable
data class MarketDataStatus(
    val source: String? = null,
    val collected_at: String? = null,
    val cached_at: String? = null,
    val delayed: Boolean = false,
    val stale: Boolean = false,
    val message: String? = null,
)

@Serializable
data class Quote(
    val symbol: String,
    val name: String? = null,
    val price: Double? = null,
    val change: Double? = null,
    val change_percent: Double? = null,
    val open: Double? = null,
    val high: Double? = null,
    val low: Double? = null,
    val previous_close: Double? = null,
    val volume: Double? = null,
    val amount: Double? = null,
    val status: MarketDataStatus? = null,
)

@Serializable
data class KlineBar(
    val timestamp: String,
    val open: Double,
    val high: Double,
    val low: Double,
    val close: Double,
    val volume: Double = 0.0,
    val amount: Double? = null,
    val turnover_rate: Double? = null,
)

@Serializable
data class KlineResponse(
    val symbol: String,
    val period: String,
    val adjustment: String? = null,
    val bars: List<KlineBar> = emptyList(),
    val status: MarketDataStatus? = null,
)

@Serializable
data class MarketSession(
    val state: String? = null,
    val message: String? = null,
)

@Serializable
data class MarketStatusResponse(
    val market_session: MarketSession? = null,
    val source: String? = null,
    val healthy: Boolean? = null,
)

// ---------- 通知 ----------

@Serializable
data class Notification(
    val notification_id: String,
    val notification_type: String? = null,
    val severity: String = "INFO",
    val title: String = "",
    val body: String = "",
    val resource_type: String? = null,
    val resource_id: String? = null,
    val payload: JsonObject = JsonObject(emptyMap()),
    val resource_url: String? = null,
    val read_at: String? = null,
    val created_at: String? = null,
)

@Serializable
data class NotificationPage(
    val items: List<Notification> = emptyList(),
    val next_cursor: String? = null,
)

@Serializable
data class NotificationSummary(
    val unread_count: Int = 0,
    val high_risk_unread_count: Int = 0,
    val latest: List<Notification> = emptyList(),
)

@Serializable
data class NotificationReadRequest(val notification_ids: List<String>)

@Serializable
data class PushDeviceRequest(
    val installation_id: String,
    val registration_id: String,
    val provider: String = "MIPUSH",
    val app_version: String? = null,
    val os_version: String? = null,
    val device_model: String? = null,
)

@Serializable
data class PushDevice(
    val device_id: String,
    val installation_id: String,
    val provider: String = "MIPUSH",
)

@Serializable
data class PushDeliveryReceipt(
    val notification_id: String,
    val status: String = "DELIVERED",
)

// ---------- 卖出建议 / 买入监控 ----------

@Serializable
data class ExitAdvice(
    val advice_id: String,
    val symbol: String,
    val status: String = "",
    val action: String? = null,
    val decision_at: String? = null,
    val current_price: Double? = null,
    val unrealized_profit: Double? = null,
    val trigger_amount: Double? = null,
    val trigger_type: String? = null,
    val trigger_price: Double? = null,
    val result: JsonElement? = null,
    val model_name: String? = null,
    val cache_hit: Boolean = false,
    val created_at: String? = null,
    val completed_at: String? = null,
)

@Serializable
data class ManualExitRequest(val symbol: String)

@Serializable
data class BuyEntryMonitor(
    val monitor_id: String,
    val symbol: String,
    val status: String = "",
    val effective_date: String? = null,
    val expires_at: String? = null,
    val entry_low: Double? = null,
    val entry_high: Double? = null,
    val rationale: String? = null,
    val triggered_at: String? = null,
    val error_code: String? = null,
    val created_at: String? = null,
)

@Serializable
data class BuyEntryMonitorRequest(val symbol: String, val enabled: Boolean)

data class TradeAdviceMonitor(
    val monitor_id: String,
    val symbol: String,
    val enabled: Boolean,
    val manual_buy_price: Double? = null,
    val manual_sell_price: Double? = null,
    val ai_buy_price: Double? = null,
    val ai_sell_price: Double? = null,
    val stop_loss_price: Double? = null,
    val rationale: Map<String, Any?> = emptyMap(),
    val generated_at: String? = null,
    val last_alert_types: List<String> = emptyList(),
    val error_code: String? = null,
)

data class TradeAdviceMonitorRequest(
    val symbol: String,
    val enabled: Boolean,
    val manual_buy_price: Double? = null,
    val manual_sell_price: Double? = null,
)

// ---------- 研究 / 运行 ----------

@Serializable
data class ResearchRequest(
    val trading_date: String,
    val scope: String = "WATCHLIST",
    val symbols: List<String>? = null,
    val total_budget: Double? = null,
    val per_symbol_budget: Double? = null,
    val max_stock_price: Double? = null,
)

@Serializable
data class Run(
    val run_id: String,
    val run_type: String? = null,
    val trading_date: String? = null,
    val requested_date: String? = null,
    val decision_at: String? = null,
    val status: String = "",
    val started_at: String? = null,
    val created_at: String? = null,
    val completed_at: String? = null,
    val error_message: String? = null,
    val phase: String? = null,
    val progress: Int? = null,
    val report_id: String? = null,
    val report_type: String? = null,
    val research_scope: String? = null,
    val target_symbols: List<String> = emptyList(),
    val total_budget: Double? = null,
    val per_symbol_budget: Double? = null,
    val portfolio_requested: Boolean? = null,
    val portfolio_generated: Boolean? = null,
    val reason_code: String? = null,
    val reason_message: String? = null,
    val trigger_source: String? = null,
    val automatic_report_slot: String? = null,
    val next_retry_at: String? = null,
    val formal_eligible_count: Int? = null,
    val excluded_symbol_count: Int? = null,
    val portfolio_reason_message: String? = null,
    val reused: Boolean = false,
)

@Serializable
data class RunActivity(
    val run_id: String,
    val run_type: String? = null,
    val activity_type: String? = null,
    val trading_date: String? = null,
    val status: String = "",
    val title: String? = null,
    val subtitle: String? = null,
    val started_at: String? = null,
    val completed_at: String? = null,
    val created_at: String? = null,
    val error_message: String? = null,
    val resource_url: String? = null,
)

@Serializable
data class RunActivityPage(
    val items: List<RunActivity> = emptyList(),
    val next_cursor: String? = null,
)

@Serializable
data class AuditEvent(
    val event_type: String? = null,
    val severity: String? = null,
    val message: String? = null,
    val created_at: String? = null,
    val details: JsonElement? = null,
)

@Serializable
data class AutomaticResearchReportSettings(
    val slot: String,
    val enabled: Boolean = false,
    val scope: String = "MARKET",
    val symbols: List<String> = emptyList(),
    val total_budget: Double = 1_000_000.0,
    val per_symbol_budget: Double = 80_000.0,
    val max_stock_price: Double? = null,
    val config_version: Int = 1,
)

@Serializable
data class ResearchSettings(
    val auto_enabled: Boolean = false,
    val updated_at: String? = null,
    val automatic_scope: String = "MARKET",
    val automatic_total_budget: Double = 1_000_000.0,
    val automatic_per_symbol_budget: Double = 80_000.0,
    val automatic_max_stock_price: Double? = null,
    val automatic_reports: List<AutomaticResearchReportSettings> = emptyList(),
    val schedule_timezone: String = "Asia/Shanghai",
    val schedule_time: String = "15:05",
    val snapshot_mode: String = "SYSTEM_ENFORCED",
    val portfolio_target_count: Int = 15,
)

@Serializable
data class ResearchSettingsRequest(
    val automatic_reports: List<AutomaticResearchReportSettings>,
)

// ---------- 评分 / 候选 / 组合 / 报告 ----------

@Serializable
data class Score(
    val symbol: String,
    val trading_date: String? = null,
    val fundamental_score: Double? = null,
    val technical_score: Double? = null,
    val sentiment_score: Double? = null,
    val quality_confidence_score: Double? = null,
    val base_total_score: Double? = null,
    val dividend_bonus: Double? = null,
    val event_risk_multiplier: Double? = null,
    val total_score: Double? = null,
    val formula_version: String? = null,
)

@Serializable
data class Candidate(
    val symbol: String,
    val name: String? = null,
    val trading_date: String? = null,
    val rank: Int? = null,
    val total_score: Double? = null,
    val base_total_score: Double? = null,
    val dividend_bonus: Double? = null,
    val prediction_percentile: Double? = null,
    val industry_code: String? = null,
    val industry_name: String? = null,
    val event_risk_multiplier: Double? = null,
)

@Serializable
data class ComponentSummaries(
    val fundamental: String? = null,
    val technical: String? = null,
    val sentiment: String? = null,
)

@Serializable
data class ReportSymbol(
    val symbol: String,
    val name: String? = null,
    val research_status: String? = null,
    val advice_eligible: Boolean = false,
    val recommendation: String? = null,
    val exclusion_reasons: List<String> = emptyList(),
    val score: Score? = null,
    val rank: Int? = null,
    val prediction_percentile: Double? = null,
    val industry_name: String? = null,
    val plain_language_summary: String? = null,
    val component_summaries: ComponentSummaries? = null,
)

@Serializable
data class Report(
    val report_id: String? = null,
    val run_id: String? = null,
    val trading_date: String? = null,
    val report_type: String? = null,
    val created_at: String? = null,
    val status: String? = null,
)

@Serializable
data class ReportContent(
    val content: String? = null,
    val body: String? = null,
)

@Serializable
data class Portfolio(
    val portfolio_id: String? = null,
    val run_id: String? = null,
    val trading_date: String? = null,
    val status: String? = null,
    val expected_turnover: Double? = null,
    val cash_weight: Double? = null,
    val positions: List<JsonObject> = emptyList(),
    val rejection_reasons: JsonElement? = null,
    val observation_only: Boolean = false,
    val research_only: Boolean = false,
    val message: String? = null,
    val excluded_symbols: List<JsonElement> = emptyList(),
)

@Serializable
data class TradePlanRequest(
    val symbols: List<String>,
    val objective: String = "RISK_ADJUSTED_RETURN",
)

@Serializable
data class TradePlan(
    val plan_id: String,
    val report_id: String? = null,
    val run_id: String? = null,
    val trading_date: String? = null,
    val status: String = "",
    val objective: String? = null,
    val symbols: List<String> = emptyList(),
    val deterministic_result: JsonElement? = null,
    val ai_explanation: JsonElement? = null,
    val created_at: String? = null,
    val completed_at: String? = null,
    val error_message: String? = null,
)

@Serializable
data class ExecutionStatus(
    val sellable: JsonElement? = null,
    val restrictions: JsonElement? = null,
    val items: List<JsonObject> = emptyList(),
)

// ---------- AI 对话 ----------

@Serializable
data class AIModelsResponse(
    val models: List<String> = emptyList(),
    val reasoning_efforts: List<String> = emptyList(),
    val web_search_available: Boolean = false,
    val cache_enabled: Boolean = false,
)

@Serializable
data class AIChatThread(
    val thread_id: String,
    val user_id: String? = null,
    val title: String = "",
    val group_mode: String = "AUTO",
    val group_type: String = "GENERAL",
    val group_label: String? = null,
    val cumulative_mentions: List<AIChatMentionRef> = emptyList(),
    val pinned_at: String? = null,
    val archived_at: String? = null,
    val created_at: String? = null,
    val updated_at: String? = null,
)

@Serializable
data class AIChatThreadPage(
    val items: List<AIChatThread> = emptyList(),
    val next_cursor: String? = null,
)

@Serializable
data class AIChatSource(
    val title: String? = null,
    val uri: String? = null,
    val url: String? = null,
    val source: String? = null,
    val symbol: String? = null,
    val snippet: String? = null,
)

@Serializable
data class AIChatMessage(
    val message_id: String,
    val thread_id: String? = null,
    val role: String = "user",
    val content: String = "",
    val status: String? = null,
    val trading_date: String? = null,
    val decision_at: String? = null,
    val available_at: String? = null,
    val mentioned_symbols: List<String> = emptyList(),
    val mention_refs: List<AIChatMentionRef> = emptyList(),
    val sources: List<AIChatSource> = emptyList(),
    val model_name: String? = null,
    val reasoning_effort: String? = null,
    val cache_hit: Boolean = false,
    val input_tokens: Long = 0,
    val cached_input_tokens: Long = 0,
    val cache_write_tokens: Long = 0,
    val output_tokens: Long = 0,
    val context_budget_status: String = "WITHIN_BUDGET",
    val streaming_mode: String = "STREAMING",
    val data_status: JsonObject = JsonObject(emptyMap()),
    val error_code: String? = null,
    val created_at: String? = null,
    val attachment_ids: List<String> = emptyList(),
)

@Serializable
data class AIChatThreadCreate(val title: String)

@Serializable
data class AIChatThreadPatch(
    val title: String? = null,
    val pinned: Boolean? = null,
    val archived: Boolean? = null,
    val group_label: String? = null,
)

@Serializable
data class BulkDeleteThreads(val thread_ids: List<String>)

@Serializable
data class AIChatAttachment(
    val attachment_id: String,
    val thread_id: String? = null,
    val mime_type: String,
    val byte_size: Long,
    val width: Int,
    val height: Int,
    val uploaded_at: String,
    val expires_at: String,
    val deleted_at: String? = null,
    val deletion_reason: String? = null,
)

@Serializable
data class AIChatMentionRef(val symbol: String, val name: String)

@Serializable
data class AIChatSendRequest(
    val content: String,
    val model: String? = null,
    val reasoning_effort: String? = null,
    val web_search: Boolean = false,
    val attachment_ids: List<String> = emptyList(),
    val mention_refs: List<AIChatMentionRef> = emptyList(),
    val decision_at: String? = null,
)

@Serializable
data class AICostSummary(
    val days: Int = 30,
    val items: List<AICostValue> = emptyList(),
    val next_cursor: String? = null,
    val totals: AICostValue = AICostValue(),
    val current_turn: AICostTurn? = null,
)

@Serializable
data class AICostValue(
    val bucket_date: String? = null,
    val requests: Long = 0,
    val cache_hits: Long = 0,
    val input_tokens: Long = 0,
    val cached_input_tokens: Long = 0,
    val cache_write_tokens: Long = 0,
    val uncached_input_tokens: Long = 0,
    val output_tokens: Long = 0,
    val estimated_spend_usd: Double = 0.0,
    val estimated_savings_usd: Double = 0.0,
)

@Serializable
data class AICostTurn(
    val requests: Long = 0,
    val cache_hit: Boolean = false,
    val input_tokens: Long = 0,
    val cached_input_tokens: Long = 0,
    val cache_write_tokens: Long = 0,
    val uncached_input_tokens: Long = 0,
    val output_tokens: Long = 0,
    val estimated_spend_usd: Double = 0.0,
    val estimated_savings_usd: Double = 0.0,
)

// ---------- 证券解析 / 搜索 ----------

@Serializable
data class ResolvedSecurity(
    val symbol: String? = null,
    val name: String? = null,
)

@Serializable
data class SecurityResolveResponse(
    val status: String = "",
    val query: String? = null,
    val match: ResolvedSecurity? = null,
    val candidates: List<ResolvedSecurity> = emptyList(),
)

@Serializable
data class FinancialSearchEntity(
    val name: String? = null,
    val code: String? = null,
    val type: String? = null,
)

@Serializable
data class FinancialSearchRecall(
    val type: String? = null,
    val desc: String? = null,
    val content: String? = null,
)

@Serializable
data class FinancialSearchSource(
    val source: String? = null,
    val uri: String? = null,
    val fetched_at: String? = null,
)

@Serializable
data class FinancialSearchResult(
    val query: String? = null,
    val provider: String? = null,
    val upstream: String? = null,
    val mode: String? = null,
    val searched_at: String? = null,
    val elapsed_ms: Long? = null,
    val interpretation: String? = null,
    val entities: List<FinancialSearchEntity> = emptyList(),
    val recalls: List<FinancialSearchRecall> = emptyList(),
    val outcome: JsonObject = JsonObject(emptyMap()),
    val sources: List<FinancialSearchSource> = emptyList(),
    val warnings: List<String> = emptyList(),
)

@Serializable
data class FinancialSearchStatus(
    val available: Boolean = false,
    val provider: String? = null,
    val message: String? = null,
)

// ---------- 回测 / 快照 ----------

@Serializable
data class Snapshot(
    val snapshot_id: String,
    val dataset: String? = null,
    val trading_date: String? = null,
    val created_at: String? = null,
    val executable: Boolean? = null,
    val description: String? = null,
)

@Serializable
data class BacktestRequest(
    val name: String,
    val start_date: String,
    val end_date: String,
    val snapshot_ids: List<String> = emptyList(),
    val config: JsonObject? = null,
)

@Serializable
data class Backtest(
    val backtest_id: String,
    val name: String? = null,
    val status: String = "",
    val start_date: String? = null,
    val end_date: String? = null,
    val created_at: String? = null,
    val completed_at: String? = null,
    val error_message: String? = null,
    val metrics: JsonObject? = null,
)

// ---------- 个人档案 ----------

@Serializable
data class ArchiveExportRequest(val passphrase: String)

@Serializable
data class PersonalArchiveJob(
    val job_id: String? = null,
    val export_id: String? = null,
    val import_id: String? = null,
    val status: String = "",
    val progress: Int? = null,
    val error_message: String? = null,
    val created_at: String? = null,
    val completed_at: String? = null,
    val preview: JsonElement? = null,
)

@Serializable
data class ArchiveApplyRequest(val merge_options: JsonObject? = null)

// ---------- Bootstrap ----------

@Serializable
data class AppCapabilities(
    val max_watchlist_symbols: Int = 100,
    val max_research_symbols: Int = 100,
    val max_trade_plan_symbols: Int = 15,
)

@Serializable
data class AppBootstrap(
    val server_time: String? = null,
    val user: UserResponse? = null,
    val assets: AssetState? = null,
    val capabilities: AppCapabilities? = null,
)

@Serializable
data class ApiErrorBody(
    val detail: JsonElement? = null,
    val code: String? = null,
)
