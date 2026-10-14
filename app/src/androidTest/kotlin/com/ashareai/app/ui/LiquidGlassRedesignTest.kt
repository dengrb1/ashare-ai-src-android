package com.ashareai.app.ui

import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import com.ashareai.app.MainActivity
import com.ashareai.app.ui.components.GlassTopBar
import com.ashareai.app.ui.components.LocalWorkspaceChrome
import com.ashareai.app.ui.components.WorkspaceChrome
import com.ashareai.app.workspace.Workspace
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class LiquidGlassRedesignTest {
    @get:Rule
    val activityRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun togglingGlassPreservesSettingsScrollPosition() {
        activityRule.onNodeWithTag("nav.settings").performClick()
        activityRule.onNodeWithTag("settings.category.appearance").performClick()

        val scroll = activityRule.onNodeWithTag("settings.scroll")
        scroll.performTouchInput { swipeUp() }
        activityRule.waitForIdle()

        val before = activityRule.onNodeWithTag("settings.appearance.anchor")
            .getUnclippedBoundsInRoot()
        activityRule.onNodeWithTag("settings.glass").assertIsDisplayed().performClick()
        activityRule.waitForIdle()
        val afterOff = activityRule.onNodeWithTag("settings.appearance.anchor")
            .getUnclippedBoundsInRoot()
        activityRule.onNodeWithTag("settings.glass").performClick()
        activityRule.waitForIdle()
        val afterOn = activityRule.onNodeWithTag("settings.appearance.anchor")
            .getUnclippedBoundsInRoot()

        assertClose(before, afterOff)
        assertClose(before, afterOn)
    }

    private fun assertClose(expected: DpRect, actual: DpRect) {
        assertTrue("settings content jumped: $expected -> $actual", kotlin.math.abs((expected.top - actual.top).value) <= 2f)
    }

    private fun overlaps(first: DpRect, second: DpRect): Boolean =
        first.left < second.right && second.left < first.right &&
            first.top < second.bottom && second.top < first.bottom

    @Test
    fun sharedTopBarKeepsTitleWorkspaceAndNotificationsSeparated() {
        activityRule.setContent {
            MaterialTheme {
                CompositionLocalProvider(
                    LocalWorkspaceChrome provides WorkspaceChrome(
                        workspace = Workspace.LOCAL,
                        onSelect = {},
                        onNotifications = {},
                        unread = 1,
                    ),
                ) {
                    GlassTopBar("设置")
                }
            }
        }
        activityRule.waitForIdle()

        val title = activityRule.onNodeWithTag("chrome.title").assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val workspace = activityRule.onNodeWithTag("chrome.workspace").assertIsDisplayed()
            .getUnclippedBoundsInRoot()
        val notifications = activityRule.onNodeWithTag("chrome.notifications").assertIsDisplayed()
            .getUnclippedBoundsInRoot()

        assertFalse("title overlaps workspace switcher", overlaps(title, workspace))
        assertFalse("workspace switcher overlaps notification button", overlaps(workspace, notifications))
    }
}
