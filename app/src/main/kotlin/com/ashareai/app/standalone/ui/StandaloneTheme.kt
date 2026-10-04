package com.ashareai.app.standalone.ui

import androidx.compose.runtime.Composable
import com.ashareai.app.ui.theme.HybridTheme

/**
 * 本地工作区主题（HybridTheme 别名，向后兼容）。
 */
@Composable
fun StandaloneTheme(
    darkModePref: String = "system",
    glassEnabled: Boolean = true,
    fullAnimationsEnabled: Boolean = true,
    powerSaveMode: Boolean = false,
    content: @Composable () -> Unit,
) = HybridTheme(
    darkModePref = darkModePref,
    glassEnabled = glassEnabled,
    fullAnimationsEnabled = fullAnimationsEnabled,
    powerSaveMode = powerSaveMode,
    content = content,
)
