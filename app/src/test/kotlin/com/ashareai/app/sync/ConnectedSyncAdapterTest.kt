package com.ashareai.app.sync

import com.ashareai.app.data.ApiService
import com.ashareai.app.data.ApiServiceProvider
import com.ashareai.app.data.MarketRepository
import com.ashareai.app.data.NotificationRepository
import com.ashareai.app.data.ResearchRepository
import com.ashareai.app.data.SimulationRepository
import com.ashareai.app.data.model.AssetState
import com.ashareai.app.data.model.BuyEntryMonitor
import com.ashareai.app.data.model.TradeAdviceMonitor
import com.ashareai.app.standalone.data.archive.ArchiveAlert
import com.ashareai.app.standalone.data.archive.ArchiveScope
import com.ashareai.app.standalone.data.archive.LocalArchiveSnapshot
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectedSyncAdapterTest {
    @Test
    fun alertIdsMapToServerMonitorKindsAndPreserveRetryKey() = runBlocking {
        val assetKeys = mutableListOf<String>()
        val monitorKeys = mutableListOf<String>()
        val api = fakeApi(
            onCall = { method, args ->
                when (method) {
                    "assets" -> AssetState(watchlist = listOf("600000.SH"))
                    "saveAssets" -> {
                        assetKeys += args?.filterIsInstance<String>().orEmpty().first()
                        AssetState(watchlist = listOf("600000.SH"))
                    }
                    "setBuyEntryMonitor" -> {
                        monitorKeys += args?.filterIsInstance<String>().orEmpty().first()
                        emptyList<BuyEntryMonitor>()
                    }
                    "saveTradeAdviceMonitor" -> {
                        monitorKeys += args?.filterIsInstance<String>().orEmpty().first()
                        TradeAdviceMonitor("trade-1", "600000.SH", enabled = true)
                    }
                    else -> error("unexpected call: $method")
                }
            },
        )
        val adapter = adapter(api)
        val snapshot = LocalArchiveSnapshot(
            createdAt = 10,
            watchlist = listOf(com.ashareai.app.standalone.data.archive.ArchiveWatchlistItem("600000.SH", "测试", 10)),
            alerts = listOf(
                alert("buy:local", "600000.SH", "BUY_ENTRY_MONITOR"),
                alert("trade:local", "600000.SH", "TRADE_ADVICE_MONITOR"),
            ),
        )

        val first = adapter.applySnapshot(snapshot, connected(emptyList()), ArchiveScope.all, "sync-1")
        val second = adapter.applySnapshot(snapshot, connected(emptyList()), ArchiveScope.all, "sync-1")

        assertTrue(first.unsupported.isEmpty())
        assertTrue(second.unsupported.isEmpty())
        assertEquals(listOf("sync-1", "sync-1"), assetKeys)
        assertEquals(listOf("sync-1:buy:local", "sync-1:trade:local", "sync-1:buy:local", "sync-1:trade:local"), monitorKeys)
    }

    @Test
    fun alertWithoutExchangeSuffixIsRejectedBeforeWrite() = runBlocking {
        var monitorWrites = 0
        val api = fakeApi(
            onCall = { method, _ ->
                when (method) {
                    "assets" -> AssetState()
                    "setBuyEntryMonitor" -> {
                        monitorWrites++
                        emptyList<BuyEntryMonitor>()
                    }
                    else -> error("unexpected call: $method")
                }
            },
        )
        val result = adapter(api).applySnapshot(
            LocalArchiveSnapshot(
                createdAt = 10,
                alerts = listOf(alert("buy:missing-suffix", "600000", "BUY_ENTRY_MONITOR")),
            ),
            connected(emptyList()),
            setOf(ArchiveScope.ALERTS),
            "sync-2",
        )

        assertTrue(ArchiveScope.ALERTS in result.unsupported)
        assertEquals(0, monitorWrites)
        assertTrue(result.warnings.any { it.contains("缺少交易所后缀") })
    }

    @Test
    fun alertsOnlySyncDoesNotReadAssets() = runBlocking {
        var monitorWrites = 0
        val api = fakeApi(
            onCall = { method, _ ->
                when (method) {
                    "assets" -> error("assets must not be read for alerts-only sync")
                    "setBuyEntryMonitor" -> {
                        monitorWrites++
                        emptyList<BuyEntryMonitor>()
                    }
                    else -> error("unexpected call: $method")
                }
            },
        )
        val result = adapter(api).applySnapshot(
            LocalArchiveSnapshot(
                createdAt = 10,
                alerts = listOf(alert("buy:alerts-only", "600000.SH", "BUY_ENTRY_MONITOR")),
            ),
            connected(emptyList()),
            setOf(ArchiveScope.ALERTS),
            "sync-alerts-only",
        )

        assertEquals(1, monitorWrites)
        assertEquals(setOf(ArchiveScope.ALERTS), result.applied)
        assertTrue(result.unsupported.isEmpty())
    }

    @Test
    fun partialAlertFailureIsReportedAsUnsupportedAndNotApplied() = runBlocking {
        val api = fakeApi(
            onCall = { method, args ->
                when (method) {
                    "setBuyEntryMonitor" -> {
                        val key = args?.filterIsInstance<String>().orEmpty().first()
                        if (key.endsWith("failed")) error("temporary failure")
                        emptyList<BuyEntryMonitor>()
                    }
                    else -> error("unexpected call: $method")
                }
            },
        )
        val result = adapter(api).applySnapshot(
            LocalArchiveSnapshot(
                createdAt = 10,
                alerts = listOf(
                    alert("buy:ok", "600000.SH", "BUY_ENTRY_MONITOR"),
                    alert("buy:failed", "600001.SH", "BUY_ENTRY_MONITOR"),
                ),
            ),
            connected(emptyList()),
            setOf(ArchiveScope.ALERTS),
            "sync-alert-partial",
        )

        assertTrue(ArchiveScope.ALERTS in result.unsupported)
        assertTrue(ArchiveScope.ALERTS !in result.applied)
        assertTrue(result.warnings.any { it.contains("temporary failure") })
    }

    private fun connected(alerts: List<ArchiveAlert>) = ConnectedSnapshotResult(
        snapshot = LocalArchiveSnapshot(createdAt = 1, alerts = alerts),
    )

    private fun alert(id: String, symbol: String, kind: String) = ArchiveAlert(
        id = id,
        symbol = symbol,
        name = symbol,
        kind = kind,
        enabled = true,
        cooldownMinutes = 30,
        configJson = "{}",
    )

    private fun adapter(api: ApiService) = ConnectedSyncAdapter(
        marketRepository = MarketRepository(ApiServiceProvider { api }),
        notificationRepository = NotificationRepository(ApiServiceProvider { api }),
        researchRepository = ResearchRepository(ApiServiceProvider { api }),
        simulationRepository = SimulationRepository(ApiServiceProvider { api }),
    )

    private fun fakeApi(onCall: (String, Array<out Any?>?) -> Any?): ApiService =
        Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(ApiService::class.java),
        ) { _, method, args ->
            when (method.name) {
                "toString" -> "ConnectedSyncFakeApi"
                else -> onCall(method.name, args)
            }
        } as ApiService
}
