package com.ashareai.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class StockSortSelectorTest {
    private data class Stock(
        val symbol: String,
        val name: String? = null,
        val score: Double? = null,
        val rank: Int? = null,
    )

    private val stocks = listOf(
        Stock(symbol = "B", name = "Beta", score = 85.0, rank = 2),
        Stock(symbol = "A", name = "Alpha", score = 92.0, rank = 3),
        Stock(symbol = "C", name = "Gamma", score = null, rank = 1),
        Stock(symbol = "D", name = "Delta", score = 92.0, rank = 1),
    )

    @Test
    fun `score descending is the default ranking and places missing scores last`() {
        val result = stocks.sortedForStockDisplay(
            option = StockSortOption.SCORE_DESC,
            scoreOf = Stock::score,
            rankOf = Stock::rank,
            nameOf = Stock::name,
            symbolOf = Stock::symbol,
        )

        assertEquals(listOf("D", "A", "B", "C"), result.map(Stock::symbol))
    }

    @Test
    fun `name sort is deterministic`() {
        val result = stocks.sortedForStockDisplay(
            option = StockSortOption.NAME_ASC,
            scoreOf = Stock::score,
            rankOf = Stock::rank,
            nameOf = Stock::name,
            symbolOf = Stock::symbol,
        )

        assertEquals(listOf("A", "B", "D", "C"), result.map(Stock::symbol))
    }
}
