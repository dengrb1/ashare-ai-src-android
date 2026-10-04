package com.ashareai.app.workspace

import org.junit.Assert.assertTrue
import org.junit.Test

class SharedDataStoreAppearanceTest {
    @Test
    fun appearanceSharingDefaultsToEnabled() {
        val settings = SharedDataStore.SharedSettings()
        val values = SharedDataStore.SharedValues()

        assertTrue(settings.shareGlassEffect)
        assertTrue(settings.shareFullAnimations)
        assertTrue(values.glassEffectEnabled)
        assertTrue(values.fullAnimationsEnabled)
    }

    @Test
    fun appearanceSharingCanBeDisabledIndependently() {
        val settings = SharedDataStore.SharedSettings(
            shareGlassEffect = false,
            shareFullAnimations = true,
        )

        assertTrue(!settings.shareGlassEffect)
        assertTrue(settings.shareFullAnimations)
    }
}
