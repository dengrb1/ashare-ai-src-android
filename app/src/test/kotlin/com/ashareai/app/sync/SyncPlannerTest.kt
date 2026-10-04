package com.ashareai.app.sync

import com.ashareai.app.standalone.data.archive.ArchiveHolding
import com.ashareai.app.standalone.data.archive.ArchiveAlert
import com.ashareai.app.standalone.data.archive.ArchiveReport
import com.ashareai.app.standalone.data.archive.ArchiveTombstone
import com.ashareai.app.standalone.data.archive.LocalArchiveSnapshot
import com.ashareai.app.standalone.data.archive.ArchiveScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncPlannerTest {
    private fun snapshot(time: Long, symbol: String = "600000") = LocalArchiveSnapshot(
        createdAt = time,
        holdings = listOf(ArchiveHolding(symbol, "测试", 10.0, 12.0, time)),
        reports = listOf(ArchiveReport("r1", "run", "报告", "确定性结果", null, time)),
    )

    @Test
    fun localNewRecordIsAdditionForLocalToConnected() {
        val preview = SyncPlanner.preview(snapshot(20), snapshot(10, "000001"), SyncDirection.LOCAL_TO_CONNECTED)
        assertTrue(preview.additions.any { it.collection == "holdings" && it.key == "600000" })
        assertTrue(preview.deletions.isEmpty())
    }

    @Test
    fun explicitTombstoneIsDeletion() {
        val local = snapshot(10, "000001")
        val connected = snapshot(10, "000001").copy(
            holdings = emptyList(),
            tombstones = listOf(ArchiveTombstone("holdings", "000001", 20, 10)),
        )
        val preview = SyncPlanner.preview(local, connected, SyncDirection.CONNECTED_TO_LOCAL)
        assertTrue(preview.deletions.any { it.collection == "holdings" && it.key == "000001" })
    }

    @Test
    fun localExplicitTombstoneIsDeletionForLocalToConnected() {
        val local = snapshot(10, "000001").copy(
            holdings = emptyList(),
            tombstones = listOf(ArchiveTombstone("holdings", "000001", 20, 10)),
        )
        val connected = snapshot(10, "000001")
        val preview = SyncPlanner.preview(local, connected, SyncDirection.LOCAL_TO_CONNECTED)
        assertTrue(preview.deletions.any { it.collection == "holdings" && it.key == "000001" })
    }

    @Test
    fun newerRemoteRecordIsUpdateForConnectedToLocal() {
        val preview = SyncPlanner.preview(snapshot(10), snapshot(20), SyncDirection.CONNECTED_TO_LOCAL)
        assertEquals(1, preview.updates.count { it.collection == "reports" })
    }

    @Test
    fun missingRemoteSnapshotRequiresServerPreview() {
        val selected = setOf(ArchiveScope.HOLDINGS)
        val preview = SyncPlanner.preview(snapshot(10), null, SyncDirection.BIDIRECTIONAL, selected)
        assertTrue(preview.serverPreviewRequired)
        assertTrue(preview.excludedFields.contains("api_keys"))
        assertEquals(selected, preview.scopes)
    }

    @Test
    fun emptyScopeDoesNotImplicitlySelectEverything() {
        val preview = SyncPlanner.preview(snapshot(20), snapshot(10), SyncDirection.BIDIRECTIONAL, emptySet())
        assertTrue(preview.additions.isEmpty())
        assertTrue(preview.updates.isEmpty())
        assertTrue(preview.conflicts.isEmpty())
        assertTrue(preview.deletions.isEmpty())
    }

    @Test
    fun alertRevisionUsesLastTriggeredAt() {
        fun alert(triggeredAt: Long) = ArchiveAlert(
            id = "alert-1",
            symbol = "600000.SH",
            name = "测试",
            kind = "PRICE",
            enabled = true,
            cooldownMinutes = 30,
            lastTriggeredAt = triggeredAt,
            configJson = "{}",
        )
        val local = LocalArchiveSnapshot(createdAt = 1, alerts = listOf(alert(20)))
        val connected = LocalArchiveSnapshot(createdAt = 1, alerts = listOf(alert(10)))
        val preview = SyncPlanner.preview(local, connected, SyncDirection.LOCAL_TO_CONNECTED, setOf(ArchiveScope.ALERTS))
        assertEquals(1, preview.updates.size)
        assertEquals("alerts", preview.updates.single().collection)
    }
}
