package com.ashareai.app.standalone.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.ashareai.app.MainActivity
import org.junit.Rule
import org.junit.Test

class FirstLocalLaunchTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    @Test
    fun opensDirectlyIntoLocalHomeInsteadOfLogin() {
        rule.onNodeWithText("本地总览").assertIsDisplayed()
        rule.onNodeWithText("欢迎使用霁衡智研").assertIsDisplayed()
    }
}
