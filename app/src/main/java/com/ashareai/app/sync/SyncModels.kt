package com.ashareai.app.sync

import com.ashareai.app.standalone.data.archive.LocalArchiveSnapshot
import com.ashareai.app.standalone.data.archive.ArchiveScope
import com.ashareai.app.standalone.data.archive.ArchiveRevision

enum class SyncDirection { LOCAL_TO_CONNECTED, CONNECTED_TO_LOCAL, BIDIRECTIONAL }
enum class SyncResolution { KEEP_LOCAL, KEEP_CONNECTED, SKIP }

data class SyncItem(val collection: String, val key: String, val localRevision: Long?, val connectedRevision: Long?)
data class SyncPreview(
    val direction: SyncDirection,
    val additions: List<SyncItem> = emptyList(),
    val updates: List<SyncItem> = emptyList(),
    val conflicts: List<SyncItem> = emptyList(),
    val deletions: List<SyncItem> = emptyList(),
    val serverPreviewRequired: Boolean = false,
    val excludedFields: Set<String> = setOf("access_token", "refresh_token", "api_keys", "push_tokens", "quotes", "candles", "logs"),
    val scopes: Set<String> = ArchiveScope.all,
)

data class SyncResolutionChoice(val collection: String, val key: String, val resolution: SyncResolution)

/** Immutable local side used for previews; encrypted server archives are never decoded here. */
object SyncPlanner {
    fun preview(
        local: LocalArchiveSnapshot,
        connected: LocalArchiveSnapshot?,
        direction: SyncDirection,
        scopes: Set<String> = ArchiveScope.all,
    ): SyncPreview {
        if (connected == null) {
            return SyncPreview(
                direction = direction,
                serverPreviewRequired = true,
                scopes = scopes,
            )
        }
        val localItems = flatten(local, scopes)
        val remoteItems = flatten(connected, scopes)
        val localDeletes = local.tombstones
            .filter { ArchiveScope.includes(scopes, it.collection) }
            .associateBy { "${it.collection}:${it.recordKey}" }
        val remoteDeletes = connected.tombstones
            .filter { ArchiveScope.includes(scopes, it.collection) }
            .associateBy { "${it.collection}:${it.recordKey}" }
        val keys = localItems.keys union remoteItems.keys
        val localOnly = keys.filter { it in localItems && it !in remoteItems }
        val remoteOnly = keys.filter { it !in localItems && it in remoteItems }
        val common = keys.filter { it in localItems && it in remoteItems }
        val additions: List<SyncItem>
        val updates: List<SyncItem>
        val conflicts: List<SyncItem>
        val deletions: List<SyncItem>
        return when (direction) {
            SyncDirection.LOCAL_TO_CONNECTED -> {
                additions = localOnly.map { item(it, localItems[it], null) }
                deletions = localDeletes.asSequence()
                    .filter { (key, tombstone) -> remoteItems[key] != null && (tombstone.sourceRevision == null || remoteItems[key]!! <= tombstone.sourceRevision) }
                    .map { (key, tombstone) -> item(key, tombstone.sourceRevision, remoteItems[key]) }
                    .toList()
                updates = common.filter { localItems[it]!! > remoteItems[it]!! }
                    .map { item(it, localItems[it], remoteItems[it]) }
                conflicts = common.filter { localItems[it]!! < remoteItems[it]!! }
                    .map { item(it, localItems[it], remoteItems[it]) }
                SyncPreview(direction, additions, updates, conflicts, deletions, scopes = scopes)
            }
            SyncDirection.CONNECTED_TO_LOCAL -> {
                additions = remoteOnly.map { item(it, null, remoteItems[it]) }
                deletions = remoteDeletes.asSequence()
                    .filter { (key, tombstone) -> localItems[key] != null && (tombstone.sourceRevision == null || localItems[key]!! <= tombstone.sourceRevision) }
                    .map { (key, tombstone) -> item(key, tombstone.sourceRevision, remoteItems[key]) }
                    .toList()
                updates = common.filter { remoteItems[it]!! > localItems[it]!! }
                    .map { item(it, localItems[it], remoteItems[it]) }
                conflicts = common.filter { remoteItems[it]!! < localItems[it]!! }
                    .map { item(it, localItems[it], remoteItems[it]) }
                SyncPreview(direction, additions, updates, conflicts, deletions, scopes = scopes)
            }
            SyncDirection.BIDIRECTIONAL -> {
                additions = (localOnly + remoteOnly).map { item(it, localItems[it], remoteItems[it]) }
                conflicts = common.filter { localItems[it] != remoteItems[it] }
                    .map { item(it, localItems[it], remoteItems[it]) }
                val deletedByLocal = localDeletes.asSequence()
                    .filter { (key, tombstone) -> remoteItems[key] != null && (tombstone.sourceRevision == null || remoteItems[key]!! <= tombstone.sourceRevision) }
                    .map { (key, tombstone) -> item(key, tombstone.sourceRevision, remoteItems[key]) }
                val deletedByRemote = remoteDeletes.asSequence()
                    .filter { (key, tombstone) -> localItems[key] != null && (tombstone.sourceRevision == null || localItems[key]!! <= tombstone.sourceRevision) }
                    .map { (key, tombstone) -> item(key, localItems[key], tombstone.sourceRevision) }
                SyncPreview(direction, additions = additions, conflicts = conflicts, deletions = (deletedByLocal + deletedByRemote).toList(), scopes = scopes)
            }
        }
    }

    private fun item(key: String, local: Long?, remote: Long?) = key.split(':', limit = 2).let { SyncItem(it[0], it.getOrElse(1) { "" }, local, remote) }
    private fun flatten(snapshot: LocalArchiveSnapshot, scopes: Set<String>): Map<String, Long> = buildMap {
        fun putIfIncluded(collection: String, recordKey: String, revision: Long) {
            if (ArchiveScope.includes(scopes, collection)) put("$collection:$recordKey", revision)
        }
        snapshot.holdings.forEach { putIfIncluded("holdings", it.symbol, ArchiveRevision.holding(it)) }
        snapshot.watchlist.forEach { putIfIncluded("watchlist", it.symbol, ArchiveRevision.watchlist(it)) }
        snapshot.alerts.forEach { putIfIncluded("alerts", it.id, ArchiveRevision.alert(it)) }
        snapshot.notifications.forEach { putIfIncluded("notifications", it.id, ArchiveRevision.notification(it)) }
        snapshot.researchRuns.forEach { putIfIncluded("researchRuns", it.id, ArchiveRevision.researchRun(it)) }
        snapshot.reports.forEach { putIfIncluded("reports", it.id, ArchiveRevision.report(it)) }
        snapshot.candidates.forEach { putIfIncluded("candidates", it.id, ArchiveRevision.candidate(it)) }
        snapshot.simulationPortfolios.forEach { putIfIncluded("simulationPortfolios", it.id, ArchiveRevision.simulationPortfolio(it)) }
        snapshot.backtests.forEach { putIfIncluded("backtests", it.id, ArchiveRevision.backtest(it)) }
        snapshot.backtestTrades.forEach { putIfIncluded("backtestTrades", it.id, ArchiveRevision.backtestTrade(it)) }
        snapshot.chatSessions.forEach { putIfIncluded("chatSessions", it.id, ArchiveRevision.chatSession(it)) }
        snapshot.chatMessages.forEach { putIfIncluded("chatMessages", it.id, ArchiveRevision.chatMessage(it)) }
    }
}
