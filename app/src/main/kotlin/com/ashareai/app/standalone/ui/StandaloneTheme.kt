package com.ashareai.app.standalone.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F3EC),
    onPrimaryContainer = Color(0xFF003730),
    secondary = Color(0xFF5C6070),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E6F0),
    onSecondaryContainer = Color(0xFF3A4158),
    tertiary = Color(0xFF3D5AFE),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1A1B20),
    surface = Color.White,
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFF2F3F7),
    onSurfaceVariant = Color(0xFF6B6E7A),
    surfaceContainerLow = Color(0xFFF3F5FA),
    surfaceContainer = Color(0xFFEDF0F6),
    surfaceContainerHigh = Color(0xFFE8EBF2),
    outline = Color(0xFFD9DBE3),
    outlineVariant = Color(0xFFECEDF2),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF4DD6C0),
    onPrimary = Color(0xFF00332C),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFF8FF3E4),
    secondary = Color(0xFFB4BDD4),
    onSecondary = Color(0xFF262C3D),
    secondaryContainer = Color(0xFF2E3650),
    onSecondaryContainer = Color(0xFFD6DCEE),
    tertiary = Color(0xFF8C9EFF),
    background = Color(0xFF0E1524),
    onBackground = Color(0xFFE4E8F2),
    surface = Color(0xFF182130),
    onSurface = Color(0xFFE4E8F2),
    surfaceVariant = Color(0xFF202A3E),
    onSurfaceVariant = Color(0xFFA6AFC6),
    surfaceContainerLow = Color(0xFF141C2C),
    surfaceContainer = Color(0xFF182130),
    surfaceContainerHigh = Color(0xFF1E2940),
    outline = Color(0xFF46526E),
    outlineVariant = Color(0xFF26324A),
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

private val AppTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 32.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 15.sp),
)

@Composable
fun StandaloneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (androidx.compose.foundation.isSystemInDarkTheme()) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}
