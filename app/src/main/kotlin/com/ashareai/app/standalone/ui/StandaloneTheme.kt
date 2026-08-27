package com.ashareai.app.standalone.ui

import androidx.compose.runtime.Composable
import com.ashareai.app.ui.theme.HybridTheme

/**
 * 本地工作区主题（HybridTheme 别名，向后兼容）。
 */
@Composable
fun StandaloneTheme(content: @Composable () -> Unit) = HybridTheme(content = content)
