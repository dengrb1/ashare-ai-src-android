package com.ashareai.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SymbolComparisonTest {
    @Test fun sixDigitAndSuffixedSymbolsMatch() {
        assertTrue(sameSymbol("000001", "000001.SZ"))
        assertTrue(sameSymbol("600000.SH", "600000"))
    }

    @Test fun differentSymbolsDoNotMatch() {
        assertFalse(sameSymbol("000001", "600000.SH"))
    }
}
