package com.ashareai.app.sync

import com.ashareai.app.standalone.data.archive.ArchiveMergePreview
import com.ashareai.app.standalone.data.archive.ArchiveScope
import com.ashareai.app.standalone.data.archive.LocalArchiveSnapshot
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkspaceSyncPreviewTest {
    private fun preview(incompleteScopes: Set<String>, selectedScopes: Set<String>) =
        WorkspaceSyncPreview(
            direction = SyncDirection.BIDIRECTIONAL,
            scopes = selectedScopes,
            local = LocalArchiveSnapshot(createdAt = 1),
            connected = ConnectedSnapshotResult(
                snapshot = LocalArchiveSnapshot(createdAt = 1),
                incompleteScopes = incompleteScopes,
                complete = incompleteScopes.isEmpty(),
            ),
            localToConnected = ArchiveMergePreview(),
            connectedToLocal = ArchiveMergePreview(),
        )

    @Test
    fun incompleteResearchBlocksResearchSync() {
        assertFalse(
            preview(
                setOf(ArchiveScope.TRADING_RESEARCH),
                setOf(ArchiveScope.TRADING_RESEARCH),
            ).completeForSelectedScopes,
        )
    }

    @Test
    fun unrelatedIncompleteScopeDoesNotBlockHoldingsSync() {
        assertTrue(
            preview(
                setOf(ArchiveScope.TRADING_RESEARCH),
                setOf(ArchiveScope.HOLDINGS),
            ).completeForSelectedScopes,
        )
    }
}
