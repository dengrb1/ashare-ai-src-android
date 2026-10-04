package com.ashareai.app.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class LiquidGlassBottomBarTest {
    @Test
    fun dragDistanceSnapsToNearestTab() {
        assertEquals(0, nearestTabIndex(startIndex = 1, dragDistancePx = -51f, tabWidthPx = 100f, tabCount = 5))
        assertEquals(2, nearestTabIndex(startIndex = 1, dragDistancePx = 51f, tabWidthPx = 100f, tabCount = 5))
        assertEquals(1, nearestTabIndex(startIndex = 1, dragDistancePx = -49f, tabWidthPx = 100f, tabCount = 5))
    }

    @Test
    fun dragDistanceClampsAtFirstAndLastTab() {
        assertEquals(0, nearestTabIndex(startIndex = 0, dragDistancePx = -500f, tabWidthPx = 100f, tabCount = 4))
        assertEquals(3, nearestTabIndex(startIndex = 3, dragDistancePx = 500f, tabWidthPx = 100f, tabCount = 4))
    }

    @Test
    fun invalidTabWidthAndSingleTabResolveToFirstTab() {
        assertEquals(0, nearestTabIndex(startIndex = 2, dragDistancePx = 100f, tabWidthPx = 0f, tabCount = 4))
        assertEquals(0, nearestTabIndex(startIndex = 0, dragDistancePx = 100f, tabWidthPx = 100f, tabCount = 1))
    }
}
