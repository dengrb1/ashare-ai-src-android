package com.ashareai.app.standalone.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFFB3261E),
    secondary = Color(0xFF4B6354),
    tertiary = Color(0xFF3F5F90),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFFFB4AB),
    secondary = Color(0xFFB2CCB6),
    tertiary = Color(0xFFB0C6FF),
)

@Composable
fun StandaloneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) DarkColors else LightColors,
        content = content,
    )
}
