package com.ashareai.app.sync

import com.ashareai.app.HybridApp
import com.ashareai.app.standalone.data.archive.ArchiveMergePlanner
import com.ashareai.app.standalone.data.archive.ArchiveMergePreview
import com.ashareai.app.standalone.data.archive.ArchiveMergeResolution
import com.ashareai.app.standalone.data.archive.ArchiveRevision
import com.ashareai.app.standalone.data.archive.ArchiveScope
import com.ashareai.app.standalone.data.archive.ArchiveTombstone
import com.ashareai.app.standalone.data.archive.LocalArchiveSnapshot
import java.util.UUID

data class WorkspaceSyncPreview(
    val direction: SyncDirection,
    val scopes: Set<String>,
    val local: LocalArchiveSnapshot,
    val connected: ConnectedSnapshotResult,
    val localToConnected: ArchiveMergePreview,
    val connectedToLocal: ArchiveMergePreview,
    val accountBinding: SyncAccountBinding = SyncAccountBinding(),
) {
    val completeForSelectedScopes: Boolean
        get() = connected.incompleteScopes.none { ArchiveScope.includes(scopes, it) }

    val conflicts: List<SyncItem>
        get() = when (direction) {
            SyncDirection.LOCAL_TO_CONNECTED -> localToConnected.conflicts.map(::toSyncItem)
            SyncDirection.CONNECTED_TO_LOCAL -> connectedToLocal.conflicts.map(::toSyncItem)
            SyncDirection.BIDIRECTIONAL -> (localToConnected.conflicts + connectedToLocal.conflicts)
                .distinctBy { "${it.collection}:${it.key}" }
                .map(::toSyncItem)
        }

    private fun toSyncItem(item: com.ashareai.app.standalone.data.archive.ArchiveMergeItem) =
        SyncItem(item.collection, item.key, item.localRevision, item.importedRevision)
}

data class SyncAccountBinding(
    val username: String? = null,
    val baseUrl: String = "",
)

data class WorkspaceSyncOutcome(
    val idempotencyKey: String,
    val appliedLocal: Set<String> = emptySet(),
    val appliedConnected: Set<String> = emptySet(),
    val unsupportedConnected: Set<String> = emptySet(),
    val warnings: List<String> = emptyList(),
    val alreadyApplied: Boolean = false,
)

/**
 * Turns a missing connected record into a delete only after a confirmed baseline exists.
 * A first sync therefore never deletes local data just because the server returned an empty
 * or partial snapshot.
 */
internal object SyncBaselinePlanner {
    fun applyBaselineDeletes(
        current: LocalArchiveSnapshot,
        baseline: LocalArchiveSnapshot?,
        now: Long = System.currentTimeMillis(),
    ): LocalArchiveSnapshot {
        if (baseline == null) return current
        val currentKeys = recordRevisions(current).keys
        val deleted = recordRevisions(baseline)
            .filterKeys { it !in currentKeys }
            .map { (compound, revision) ->
                val parts = compound.split(':', limit = 2)
                ArchiveTombstone(
                    collection = parts[0],
                    recordKey = parts.getOrElse(1) { "" },
                    deletedAt = now,
                    sourceRevision = revision,
                )
            }
        return current.copy(
            tombstones = (current.tombstones + deleted)
                .distinctBy { "${it.collection}:${it.recordKey}" },
        )
    }

    private fun recordRevisions(snapshot: LocalArchiveSnapshot): Map<String, Long> = buildMap {
        fun add(collection: String, key: String, revision: Long) {
            put("$collection:$key", revision)
        }
        snapshot.holdings.forEach { add("holdings", it.symbol, ArchiveRevision.holding(it)) }
        snapshot.watchlist.forEach { add("watchlist", it.symbol, ArchiveRevision.watchlist(it)) }
        snapshot.alerts.forEach { add("alerts", it.id, ArchiveRevision.alert(it)) }
        snapshot.notifications.forEach { add("notifications", it.id, ArchiveRevision.notification(it)) }
        snapshot.researchRuns.forEach { add("researchRuns", it.id, ArchiveRevision.researchRun(it)) }
        snapshot.reports.forEach { add("reports", it.id, ArchiveRevision.report(it)) }
        snapshot.candidates.forEach { add("candidates", it.id, ArchiveRevision.candidate(it)) }
        snapshot.simulationPortfolios.forEach { add("simulationPortfolios", it.id, ArchiveRevision.simulationPortfolio(it)) }
        snapshot.backtests.forEach { add("backtests", it.id, ArchiveRevision.backtest(it)) }
        snapshot.backtestTrades.forEach { add("backtestTrades", it.id, ArchiveRevision.backtestTrade(it)) }
        snapshot.chatSessions.forEach { add("chatSessions", it.id, ArchiveRevision.chatSession(it)) }
        snapshot.chatMessages.forEach { add("chatMessages", it.id, ArchiveRevision.chatMessage(it)) }
    }
}

internal fun syncAccountKey(username: String?, baseUrl: String): String? =
    "${username.orEmpty().trim()}|${baseUrl.trim().trimEnd('/')}"
        .takeIf { username?.isNotBlank() == true && baseUrl.isNotBlank() }

/** Coordinates a user-confirmed merge between the Room and connected workspaces. */
class WorkspaceSyncCoordinator(private val app: HybridApp) {
    private val connectedAdapter = ConnectedSyncAdapter(app.fusionContainer)

    suspend fun preview(
        direction: SyncDirection,
        scopes: Set<String> = ArchiveScope.all,
    ): WorkspaceSyncPreview {
        require(scopes.isNotEmpty()) { "至少选择一个同步范围" }
        val local = app.localContainer.local.exportArchive()
        val connected = connectedAdapter.readSnapshot()
        val accountBinding = SyncAccountBinding(
            username = app.fusionSettings.currentUsername(),
            baseUrl = app.fusionSettings.currentBaseUrl(),
        )
        val connectedWithBaselineDeletes = SyncBaselinePlanner.applyBaselineDeletes(
            connected.snapshot,
            accountBinding.accountKey()?.let { app.localContainer.local.syncBaseline(it) },
        )
        val connectedForPreview = connected.copy(snapshot = connectedWithBaselineDeletes)
        return WorkspaceSyncPreview(
            direction = direction,
            scopes = scopes,
            local = local,
            connected = connectedForPreview,
            localToConnected = ArchiveMergePlanner.preview(connectedForPreview.snapshot, local, scopes),
            connectedToLocal = ArchiveMergePlanner.preview(local, connectedForPreview.snapshot, scopes),
            accountBinding = accountBinding,
        )
    }

    suspend fun apply(
        preview: WorkspaceSyncPreview,
        resolutions: Map<String, SyncResolution> = emptyMap(),
        idempotencyKey: String = UUID.randomUUID().toString(),
    ): WorkspaceSyncOutcome {
        require(preview.scopes.isNotEmpty()) { "至少选择一个同步范围" }
        require(idempotencyKey.isNotBlank()) { "同步幂等键不能为空" }
        val currentBinding = SyncAccountBinding(
            username = app.fusionSettings.currentUsername(),
            baseUrl = app.fusionSettings.currentBaseUrl(),
        )
        check(currentBinding == preview.accountBinding) { "连接版账号或服务地址已变化，请重新生成同步预览" }
        check(preview.completeForSelectedScopes) { "选中的连接版数据范围不完整，请等待网络恢复后重新生成预览" }
        val local = app.localContainer.local
        if (local.syncOperation(idempotencyKey)?.state == "COMPLETED") {
            return WorkspaceSyncOutcome(idempotencyKey, alreadyApplied = true)
        }
        val now = System.currentTimeMillis()
        local.saveSyncOperation(idempotencyKey, preview.direction.name, preview.scopes.joinToString(","), "APPLYING", now = now)
        val warnings = preview.connected.warnings.toMutableList()
        var localApplied = emptySet<String>()
        var connectedApplied = emptySet<String>()
        val unsupported = mutableSetOf<String>()
        try {
            when (preview.direction) {
                SyncDirection.LOCAL_TO_CONNECTED -> {
                    val target = mergeTarget(
                        base = preview.connected.snapshot,
                        incoming = preview.local,
                        preview = preview.localToConnected,
                        scopes = preview.scopes,
                        resolutions = resolutions,
                        incomingResolution = SyncResolution.KEEP_LOCAL,
                    )
                    val result = connectedAdapter.applySnapshot(target, preview.connected, preview.scopes, idempotencyKey)
                    connectedApplied = result.applied
                    unsupported += result.unsupported
                    warnings += result.warnings
                }
                SyncDirection.CONNECTED_TO_LOCAL -> {
                    val target = mergeTarget(
                        base = preview.local,
                        incoming = preview.connected.snapshot,
                        preview = preview.connectedToLocal,
                        scopes = preview.scopes,
                        resolutions = resolutions,
                        incomingResolution = SyncResolution.KEEP_CONNECTED,
                    )
                    applyLocalTarget(target, local, idempotencyKey, preview.scopes)
                    localApplied = preview.scopes
                }
                SyncDirection.BIDIRECTIONAL -> {
                    val localTarget = mergeTarget(
                        base = preview.local,
                        incoming = preview.connected.snapshot,
                        preview = preview.connectedToLocal,
                        scopes = preview.scopes,
                        resolutions = resolutions,
                        incomingResolution = SyncResolution.KEEP_CONNECTED,
                    )
                    applyLocalTarget(localTarget, local, "$idempotencyKey-local", preview.scopes)
                    localApplied = preview.scopes

                    val connectedTarget = mergeTarget(
                        base = preview.connected.snapshot,
                        incoming = preview.local,
                        preview = preview.localToConnected,
                        scopes = preview.scopes,
                        resolutions = resolutions,
                        incomingResolution = SyncResolution.KEEP_LOCAL,
                    )
                    val result = connectedAdapter.applySnapshot(connectedTarget, preview.connected, preview.scopes, idempotencyKey)
                    connectedApplied = result.applied
                    unsupported += result.unsupported
                    warnings += result.warnings
                }
            }
            local.saveSyncOperation(
                idempotencyKey,
                preview.direction.name,
                preview.scopes.joinToString(","),
                "COMPLETED",
                errorMessage = unsupported.takeIf { it.isNotEmpty() }?.joinToString(),
                now = System.currentTimeMillis(),
            )
            val baselineResult = runCatching {
                val refreshed = connectedAdapter.readSnapshot()
                val baselineScopes = when (preview.direction) {
                    SyncDirection.CONNECTED_TO_LOCAL -> preview.scopes
                    SyncDirection.LOCAL_TO_CONNECTED, SyncDirection.BIDIRECTIONAL -> connectedApplied
                }
                val accountKey = preview.accountBinding.accountKey()
                if (accountKey != null && baselineScopes.isNotEmpty() &&
                    refreshed.incompleteScopes.none { incomplete ->
                        baselineScopes.any { selected -> ArchiveScope.includes(setOf(selected), incomplete) }
                    }
                ) {
                    val previous = local.syncBaseline(accountKey)
                    local.saveSyncBaseline(
                        accountKey,
                        overlaySnapshot(previous ?: LocalArchiveSnapshot(createdAt = 0L), refreshed.snapshot, baselineScopes),
                    )
                } else {
                    warnings += "连接版快照不完整或没有可写范围，本次不更新同步基线"
                }
            }.onFailure { error ->
                warnings += "同步已应用，但保存连接版基线失败：${error.message ?: "读取失败"}"
            }
            baselineResult.getOrNull()
            return WorkspaceSyncOutcome(idempotencyKey, localApplied, connectedApplied, unsupported, warnings)
        } catch (error: Throwable) {
            local.saveSyncOperation(
                idempotencyKey,
                preview.direction.name,
                preview.scopes.joinToString(","),
                "FAILED",
                errorMessage = error.message ?: error::class.simpleName,
                now = System.currentTimeMillis(),
            )
            throw error
        }
    }

    private suspend fun applyLocalTarget(
        target: LocalArchiveSnapshot,
        local: com.ashareai.app.standalone.data.LocalRepository,
        idempotencyKey: String,
        scopes: Set<String>,
    ) {
        val current = local.exportArchive()
        val targetPreview = local.previewImportArchive(target, scopes)
        val resolutions = targetPreview.conflicts.associate { "${it.collection}:${it.key}" to ArchiveMergeResolution.KEEP_IMPORTED }
        local.applyImportArchive(target, targetPreview, resolutions, idempotencyKey, scopes)
        check(local.exportArchive().createdAt >= current.createdAt) { "本地同步快照未更新" }
    }

    private fun mergeTarget(
        base: LocalArchiveSnapshot,
        incoming: LocalArchiveSnapshot,
        preview: ArchiveMergePreview,
        scopes: Set<String>,
        resolutions: Map<String, SyncResolution>,
        incomingResolution: SyncResolution,
    ): LocalArchiveSnapshot {
        fun resolution(collection: String, key: String): SyncResolution =
            resolutions["$collection:$key"] ?: incomingResolution

        fun <T> merge(
            collection: String,
            baseItems: List<T>,
            incomingItems: List<T>,
            keyOf: (T) -> String,
        ): List<T> {
            if (!ArchiveScope.includes(scopes, collection)) return baseItems
            val baseByKey = baseItems.associateBy(keyOf)
            val incomingByKey = incomingItems.associateBy(keyOf)
            val keys = baseByKey.keys + incomingByKey.keys
            return keys.mapNotNull { key ->
                val baseItem = baseByKey[key]
                val incomingItem = incomingByKey[key]
                when {
                    baseItem == null -> incomingItem
                    incomingItem == null -> {
                        val tombstone = incoming.tombstones.firstOrNull { it.collection == collection && it.recordKey == key }
                        if (tombstone != null && resolution(collection, key) == incomingResolution) null else baseItem
                    }
                    baseItem == incomingItem -> baseItem
                    preview.conflicts.any { it.collection == collection && it.key == key } ->
                        if (resolution(collection, key) == incomingResolution) incomingItem else baseItem
                    else -> incomingItem
                }
            }
        }
        fun tombstones(): List<ArchiveTombstone> = base.tombstones.filterNot { tombstone ->
            ArchiveScope.includes(scopes, tombstone.collection) &&
                incoming.tombstones.none { it.collection == tombstone.collection && it.recordKey == tombstone.recordKey }
        } + incoming.tombstones.filter { ArchiveScope.includes(scopes, it.collection) }

        return base.copy(
            createdAt = System.currentTimeMillis(),
            holdings = merge("holdings", base.holdings, incoming.holdings) { it.symbol },
            watchlist = merge("watchlist", base.watchlist, incoming.watchlist) { it.symbol },
            alerts = merge("alerts", base.alerts, incoming.alerts) { it.id },
            notifications = merge("notifications", base.notifications, incoming.notifications) { it.id },
            researchRuns = merge("researchRuns", base.researchRuns, incoming.researchRuns) { it.id },
            reports = merge("reports", base.reports, incoming.reports) { it.id },
            candidates = merge("candidates", base.candidates, incoming.candidates) { it.id },
            simulationPortfolios = merge("simulationPortfolios", base.simulationPortfolios, incoming.simulationPortfolios) { it.id },
            backtests = merge("backtests", base.backtests, incoming.backtests) { it.id },
            backtestTrades = merge("backtestTrades", base.backtestTrades, incoming.backtestTrades) { it.id },
            chatSessions = merge("chatSessions", base.chatSessions, incoming.chatSessions) { it.id },
            chatMessages = merge("chatMessages", base.chatMessages, incoming.chatMessages) { it.id },
            tombstones = tombstones(),
        )
    }

    /**
     * The connected API returns current records but has no delete feed. A missing record is a
     * delete only when it existed in this account's last confirmed baseline. The baseline is
     * deliberately account/server scoped and is never populated on an incomplete read.
     */
    private fun SyncAccountBinding.accountKey(): String? =
        syncAccountKey(username, baseUrl)

    private fun overlaySnapshot(
        base: LocalArchiveSnapshot,
        current: LocalArchiveSnapshot,
        scopes: Set<String>,
    ): LocalArchiveSnapshot = base.copy(
        createdAt = current.createdAt,
        holdings = if (ArchiveScope.includes(scopes, ArchiveScope.HOLDINGS)) current.holdings else base.holdings,
        watchlist = if (ArchiveScope.includes(scopes, ArchiveScope.WATCHLIST)) current.watchlist else base.watchlist,
        alerts = if (ArchiveScope.includes(scopes, ArchiveScope.ALERTS)) current.alerts else base.alerts,
        notifications = if (ArchiveScope.includes(scopes, ArchiveScope.TRADING_RESEARCH)) current.notifications else base.notifications,
        researchRuns = if (ArchiveScope.includes(scopes, ArchiveScope.TRADING_RESEARCH)) current.researchRuns else base.researchRuns,
        reports = if (ArchiveScope.includes(scopes, ArchiveScope.REPORTS)) current.reports else base.reports,
        candidates = if (ArchiveScope.includes(scopes, ArchiveScope.TRADING_RESEARCH)) current.candidates else base.candidates,
        simulationPortfolios = if (ArchiveScope.includes(scopes, ArchiveScope.SIMULATION_PORTFOLIOS)) current.simulationPortfolios else base.simulationPortfolios,
        backtests = if (ArchiveScope.includes(scopes, ArchiveScope.BACKTESTS)) current.backtests else base.backtests,
        backtestTrades = if (ArchiveScope.includes(scopes, ArchiveScope.BACKTESTS)) current.backtestTrades else base.backtestTrades,
        chatSessions = if (ArchiveScope.includes(scopes, ArchiveScope.TRADING_RESEARCH)) current.chatSessions else base.chatSessions,
        chatMessages = if (ArchiveScope.includes(scopes, ArchiveScope.TRADING_RESEARCH)) current.chatMessages else base.chatMessages,
        tombstones = if (scopes.contains(ArchiveScope.ALL) || scopes.isNotEmpty()) {
            base.tombstones.filterNot { ArchiveScope.includes(scopes, it.collection) } +
                current.tombstones.filter { ArchiveScope.includes(scopes, it.collection) }
        } else base.tombstones,
    )

}
