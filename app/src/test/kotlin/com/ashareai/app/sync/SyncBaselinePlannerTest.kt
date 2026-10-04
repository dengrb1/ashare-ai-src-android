package com.ashareai.app.sync

import com.ashareai.app.standalone.data.archive.ArchiveHolding
import com.ashareai.app.standalone.data.archive.ArchiveTombstone
import com.ashareai.app.standalone.data.archive.LocalArchiveSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncBaselinePlannerTest {
    private val holding = ArchiveHolding(
        symbol = "600000",
        name = "测试",
        quantity = 10.0,
        averageCost = 12.0,
        updatedAt = 10L,
    )

    @Test
    fun firstSyncDoesNotTreatMissingRecordAsDeletion() {
        val current = LocalArchiveSnapshot(createdAt = 20L)

        // A null baseline is the state before the first confirmed sync.
        val result = SyncBaselinePlanner.applyBaselineDeletes(current, null, now = 30L)

        assertTrue(result.tombstones.isEmpty())
        assertEquals(current, result)
    }

    @Test
    fun confirmedBaselineTurnsMissingRecordIntoTombstone() {
        val current = LocalArchiveSnapshot(createdAt = 20L)
        val baseline = LocalArchiveSnapshot(createdAt = 10L, holdings = listOf(holding))

        val result = SyncBaselinePlanner.applyBaselineDeletes(current, baseline, now = 30L)

        assertEquals(
            listOf(ArchiveTombstone("holdings", "600000", 30L, 10L)),
            result.tombstones,
        )
    }

    @Test
    fun existingRecordIsNotMarkedDeleted() {
        val current = LocalArchiveSnapshot(createdAt = 20L, holdings = listOf(holding.copy(updatedAt = 20L)))
        val baseline = LocalArchiveSnapshot(createdAt = 10L, holdings = listOf(holding))

        val result = SyncBaselinePlanner.applyBaselineDeletes(current, baseline, now = 30L)

        assertTrue(result.tombstones.isEmpty())
    }

    @Test
    fun accountKeySeparatesUsersAndServerAddresses() {
        assertEquals("alice|https://one.example", syncAccountKey(" alice ", "https://one.example/"))
        assertEquals("alice|https://two.example", syncAccountKey("alice", "https://two.example"))
        assertEquals(null, syncAccountKey(null, "https://one.example"))
        assertEquals(null, syncAccountKey("alice", ""))
    }
}
