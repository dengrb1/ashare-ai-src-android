package com.ashareai.app.standalone.data.archive

import java.security.MessageDigest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/** A user-visible classification for an import preview. */
enum class ArchiveMergeStatus { ADDITION, UPDATE, CONFLICT, DELETION }
enum class ArchiveMergeResolution { KEEP_LOCAL, KEEP_IMPORTED, SKIP }

data class ArchiveMergeItem(
    val collection: String,
    val key: String,
    val localRevision: Long?,
    val importedRevision: Long?,
    val status: ArchiveMergeStatus,
)

data class ArchiveMergePreview(
    val additions: List<ArchiveMergeItem> = emptyList(),
    val updates: List<ArchiveMergeItem> = emptyList(),
    val conflicts: List<ArchiveMergeItem> = emptyList(),
    val deletions: List<ArchiveMergeItem> = emptyList(),
) {
    val total: Int get() = additions.size + updates.size + conflicts.size + deletions.size
}

/**
 * Compares an encrypted archive after it has been decoded. Missing records are not deletes;
 * deletes are represented only by explicit archive tombstones.
 */
object ArchiveMergePlanner {
    private val json = Json { encodeDefaults = true; explicitNulls = true }
    fun preview(
        local: LocalArchiveSnapshot,
        imported: LocalArchiveSnapshot,
        scopes: Set<String> = ArchiveScope.all,
    ): ArchiveMergePreview {
        val localItems = flatten(local, scopes)
        val importedItems = flatten(imported, scopes)
        val importedDeletes = imported.tombstones
            .filter { ArchiveScope.includes(scopes, it.collection) }
            .associateBy { key(it.collection, it.recordKey) }
        val additions = mutableListOf<ArchiveMergeItem>()
        val updates = mutableListOf<ArchiveMergeItem>()
        val conflicts = mutableListOf<ArchiveMergeItem>()
        val deletions = mutableListOf<ArchiveMergeItem>()

        importedItems.forEach { (key, importedRecord) ->
            val localRecord = localItems[key]
            val localRevision = localRecord?.revision
            val importedRevision = importedRecord.revision
            val item = item(key, localRevision, importedRevision)
            when {
                localRecord == null -> additions += item.copy(status = ArchiveMergeStatus.ADDITION)
                importedRevision > localRecord.revision -> updates += item.copy(status = ArchiveMergeStatus.UPDATE)
                importedRevision < localRecord.revision -> conflicts += item.copy(status = ArchiveMergeStatus.CONFLICT)
                localRecord.fingerprint != importedRecord.fingerprint ->
                    conflicts += item.copy(status = ArchiveMergeStatus.CONFLICT)
            }
        }
        importedDeletes.forEach { (key, tombstone) ->
            val localRevision = localItems[key]?.revision
            if (localRevision != null && (tombstone.sourceRevision == null || localRevision <= tombstone.sourceRevision)) {
                deletions += item(key, localRevision, tombstone.sourceRevision).copy(status = ArchiveMergeStatus.DELETION)
            }
        }
        return ArchiveMergePreview(additions, updates, conflicts, deletions)
    }

    private fun key(collection: String, recordKey: String) = "$collection:$recordKey"
    private fun item(key: String, local: Long?, imported: Long?): ArchiveMergeItem {
        val parts = key.split(':', limit = 2)
        return ArchiveMergeItem(parts[0], parts.getOrElse(1) { "" }, local, imported, ArchiveMergeStatus.UPDATE)
    }

    private data class VersionedRecord(val revision: Long, val fingerprint: String)

    private fun flatten(snapshot: LocalArchiveSnapshot, scopes: Set<String>): Map<String, VersionedRecord> = buildMap {
        fun putIfIncluded(collection: String, recordKey: String, revision: Long, record: Any) {
            if (ArchiveScope.includes(scopes, collection)) {
                put(key(collection, recordKey), VersionedRecord(revision, fingerprint(record)))
            }
        }
        snapshot.holdings.forEach { putIfIncluded("holdings", it.symbol, ArchiveRevision.holding(it), it) }
        snapshot.watchlist.forEach { putIfIncluded("watchlist", it.symbol, ArchiveRevision.watchlist(it), it) }
        snapshot.alerts.forEach { putIfIncluded("alerts", it.id, ArchiveRevision.alert(it), it) }
        snapshot.notifications.forEach { putIfIncluded("notifications", it.id, ArchiveRevision.notification(it), it) }
        snapshot.researchRuns.forEach { putIfIncluded("researchRuns", it.id, ArchiveRevision.researchRun(it), it) }
        snapshot.reports.forEach { putIfIncluded("reports", it.id, ArchiveRevision.report(it), it) }
        snapshot.candidates.forEach { putIfIncluded("candidates", it.id, ArchiveRevision.candidate(it), it) }
        snapshot.simulationPortfolios.forEach { putIfIncluded("simulationPortfolios", it.id, ArchiveRevision.simulationPortfolio(it), it) }
        snapshot.backtests.forEach { putIfIncluded("backtests", it.id, ArchiveRevision.backtest(it), it) }
        snapshot.backtestTrades.forEach {
            putIfIncluded("backtestTrades", it.id, ArchiveRevision.backtestTrade(it), it)
        }
        snapshot.chatSessions.forEach { putIfIncluded("chatSessions", it.id, ArchiveRevision.chatSession(it), it) }
        snapshot.chatMessages.forEach { putIfIncluded("chatMessages", it.id, ArchiveRevision.chatMessage(it), it) }
    }

    /** Explicit JSON serialization keeps fingerprints stable if Kotlin's toString changes. */
    private fun fingerprint(record: Any): String {
        val canonical = when (record) {
            is ArchiveHolding -> json.encodeToString(record)
            is ArchiveWatchlistItem -> json.encodeToString(record)
            is ArchiveAlert -> json.encodeToString(record)
            is ArchiveNotification -> json.encodeToString(record)
            is ArchiveResearchRun -> json.encodeToString(record)
            is ArchiveReport -> json.encodeToString(record)
            is ArchiveCandidate -> json.encodeToString(record)
            is ArchiveSimulationPortfolio -> json.encodeToString(record)
            is ArchiveBacktest -> json.encodeToString(record)
            is ArchiveBacktestTrade -> json.encodeToString(record)
            is ArchiveChatSession -> json.encodeToString(record)
            is ArchiveChatMessage -> json.encodeToString(record)
            else -> error("Unsupported archive record: ${record::class.qualifiedName}")
        }
        return MessageDigest.getInstance("SHA-256")
        .digest(canonical.toByteArray(Charsets.UTF_8))
        .joinToString("") { byte -> "%02x".format(byte) }
    }
}
