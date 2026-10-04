package com.ashareai.app.standalone.data.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveMergePlannerTest {
    private fun snapshot(revision: Long?) = LocalArchiveSnapshot(
        createdAt = 1,
        holdings = revision?.let { listOf(ArchiveHolding("600000", "浦发银行", 10.0, 12.0, it)) } ?: emptyList(),
    )

    @Test
    fun importedRecordIsAdditionWhenLocalIsMissing() {
        val preview = ArchiveMergePlanner.preview(snapshot(null), snapshot(20))
        assertEquals(1, preview.additions.size)
        assertEquals(ArchiveMergeStatus.ADDITION, preview.additions.single().status)
    }

    @Test
    fun newerImportedRecordIsUpdate() {
        val preview = ArchiveMergePlanner.preview(snapshot(10), snapshot(20))
        assertEquals(1, preview.updates.size)
        assertEquals(ArchiveMergeStatus.UPDATE, preview.updates.single().status)
    }

    @Test
    fun sameRevisionIsSkipped() {
        val preview = ArchiveMergePlanner.preview(snapshot(10), snapshot(10))
        assertEquals(0, preview.total)
    }

    @Test
    fun sameRevisionWithDifferentContentIsConflict() {
        val imported = snapshot(10).copy(
            holdings = listOf(ArchiveHolding("600000", "浦发银行", 11.0, 12.0, 10)),
        )
        val preview = ArchiveMergePlanner.preview(snapshot(10), imported)
        assertEquals(1, preview.conflicts.size)
        assertEquals(ArchiveMergeStatus.CONFLICT, preview.conflicts.single().status)
    }

    @Test
    fun olderImportedRecordIsConflict() {
        val preview = ArchiveMergePlanner.preview(snapshot(20), snapshot(10))
        assertEquals(1, preview.conflicts.size)
        assertEquals(ArchiveMergeStatus.CONFLICT, preview.conflicts.single().status)
    }

    @Test
    fun explicitTombstoneDeletesOnlyWhenItDoesNotLoseNewerLocalData() {
        val imported = snapshot(null).copy(
            tombstones = listOf(ArchiveTombstone("holdings", "600000", 30, 20)),
        )
        val deletePreview = ArchiveMergePlanner.preview(snapshot(20), imported)
        assertTrue(deletePreview.deletions.any { it.collection == "holdings" && it.key == "600000" })

        val keepPreview = ArchiveMergePlanner.preview(snapshot(21), imported)
        assertTrue(keepPreview.deletions.isEmpty())
    }

    @Test
    fun selectedScopeExcludesHoldingsFromPreview() {
        val local = snapshot(10).copy(
            watchlist = listOf(ArchiveWatchlistItem("600000", "浦发银行", 10)),
        )
        val imported = snapshot(20).copy(
            watchlist = listOf(ArchiveWatchlistItem("600000", "浦发银行", 20)),
        )
        val preview = ArchiveMergePlanner.preview(local, imported, setOf(ArchiveScope.WATCHLIST))
        assertEquals(0, preview.conflicts.size)
        assertEquals(1, preview.updates.size)
        assertEquals("watchlist", preview.updates.single().collection)
    }

    @Test
    fun backtestRecordsParticipateInBacktestScope() {
        val local = LocalArchiveSnapshot(
            createdAt = 1,
            backtests = listOf(
                ArchiveBacktest("bt-1", "2024-01-01", "2024-01-02", 1000.0, "000300", feeRate = 0.001, status = "SUCCEEDED", createdAt = 10),
            ),
        )
        val imported = local.copy(backtests = local.backtests.map { it.copy(createdAt = 20) })
        val preview = ArchiveMergePlanner.preview(local, imported, setOf(ArchiveScope.BACKTESTS))
        assertEquals(1, preview.updates.size)
        assertEquals("backtests", preview.updates.single().collection)
    }

    @Test
    fun userScopeNamesIncludeInternalArchiveCollections() {
        assertTrue(ArchiveScope.includes(setOf(ArchiveScope.TRADING_RESEARCH), "researchRuns"))
        assertTrue(ArchiveScope.includes(setOf(ArchiveScope.TRADING_RESEARCH), ArchiveScope.TRADING_RESEARCH))
        assertTrue(ArchiveScope.includes(setOf(ArchiveScope.SIMULATION_PORTFOLIOS), "simulationPortfolios"))
        assertTrue(ArchiveScope.includes(setOf(ArchiveScope.BACKTESTS), "backtestTrades"))
    }

    @Test
    fun emptyScopeExcludesAllCollections() {
        assertTrue(!ArchiveScope.includes(emptySet(), "holdings"))
        assertTrue(!ArchiveScope.includes(emptySet(), "researchRuns"))
    }
}
