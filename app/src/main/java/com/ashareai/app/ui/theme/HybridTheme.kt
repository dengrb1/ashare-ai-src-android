package com.ashareai.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 统一混合主题：本地和 Fusion 工作区共享的 Material3 主题。
 *
 * 配色方案：
 * - 主色（Primary）：青绿色系，表示稳健与增长
 * - 次要色（Secondary）：灰蓝色系，表示信息与辅助
 * - 强调色（Tertiary）：靛蓝色，表示交互与焦点
 * - 涨跌配色：红涨绿跌（A股约定）
 */

// 强调色对：浅色主题用 Indigo，深色主题用 IndigoDark（tertiary）
val Indigo = Color(0xFF3D5AFE)
val IndigoDark = Color(0xFF8C9EFF)

// A股约定：红涨绿跌
val StockUp = Color(0xFFE53935)
val StockDown = Color(0xFF00A86B)
val StockFlat = Color(0xFF9E9E9E)

private val LightColors = lightColorScheme(
    primary = Color(0xFF006B5F),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5F3EC),
    onPrimaryContainer = Color(0xFF003730),
    secondary = Color(0xFF5C6070),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE2E6F0),
    onSecondaryContainer = Color(0xFF3A4158),
    tertiary = Indigo,
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE0E2FF),
    onTertiaryContainer = Color(0xFF212A63),
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1A1B20),
    surface = Color.White,
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFF2F3F7),
    onSurfaceVariant = Color(0xFF6B6E7A),
    outline = Color(0xFFD9DBE3),
    outlineVariant = Color(0xFFECEDF2),
    error = Color(0xFFD32F2F),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F5FA),
    surfaceContainer = Color(0xFFEDF0F6),
    surfaceContainerHigh = Color(0xFFE8EBF2),
    surfaceContainerHighest = Color(0xFFE2E5EC),
    inverseSurface = Color(0xFF1A1B20),
    inverseOnSurface = Color(0xFFF4F4F8),
    inversePrimary = Color(0xFF4DD6C0),
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
    tertiary = IndigoDark,
    onTertiary = Color(0xFF1A1E4F),
    tertiaryContainer = Color(0xFF3A46C8),
    onTertiaryContainer = Color(0xFFE0E3FF),
    background = Color(0xFF0E1524),
    onBackground = Color(0xFFE4E8F2),
    surface = Color(0xFF182130),
    onSurface = Color(0xFFE4E8F2),
    surfaceVariant = Color(0xFF202A3E),
    onSurfaceVariant = Color(0xFFA6AFC6),
    outline = Color(0xFF46526E),
    outlineVariant = Color(0xFF26324A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF5E2025),
    onErrorContainer = Color(0xFFFFDAD6),
    surfaceContainerLowest = Color(0xFF0E1524),
    surfaceContainerLow = Color(0xFF141C2C),
    surfaceContainer = Color(0xFF182130),
    surfaceContainerHigh = Color(0xFF1E2940),
    surfaceContainerHighest = Color(0xFF243150),
    inverseSurface = Color(0xFFE4E8F2),
    inverseOnSurface = Color(0xFF262C3D),
    inversePrimary = Color(0xFF006B5F),
)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(8.dp),
    extraLarge = RoundedCornerShape(8.dp),
)

val AppTypography = Typography(
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

/**
 * 统一混合主题：本地和 Fusion 工作区共享。
 *
 * @param darkModePref 深色模式偏好："light", "dark", "system"
 * @param glassEnabled 是否启用玻璃态材质效果（默认启用）
 */
@Composable
fun HybridTheme(
    darkModePref: String = "system",
    accentColor: String = "#006B5F",
    glassEnabled: Boolean = true,
    fullAnimationsEnabled: Boolean = true,
    powerSaveMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = when (darkModePref) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    val accent = parseAccentColor(accentColor)
    val scheme = (if (dark) DarkColors else LightColors).withAccent(accent, dark)
    androidx.compose.runtime.CompositionLocalProvider(
        LocalGlassEnabled provides glassEnabled,
        LocalFullAnimationsEnabled provides fullAnimationsEnabled,
        LocalPowerSaveMode provides powerSaveMode,
    ) {
        MaterialTheme(
            colorScheme = scheme,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
        )
    }
}

/**
 * Fusion 工作区主题（AShareTheme 别名）。
 */
@Composable
fun AShareTheme(
    darkModePref: String = "system",
    accentColor: String = "#006B5F",
    glassEnabled: Boolean = true,
    fullAnimationsEnabled: Boolean = true,
    powerSaveMode: Boolean = false,
    content: @Composable () -> Unit,
) = HybridTheme(
    darkModePref = darkModePref,
    accentColor = accentColor,
    glassEnabled = glassEnabled,
    fullAnimationsEnabled = fullAnimationsEnabled,
    powerSaveMode = powerSaveMode,
    content = content,
)

/** 涨跌配色：>0 红，<0 绿，0/缺失 灰。 */
fun changeColor(value: Double?): Color = when {
    value == null -> StockFlat
    value > 0 -> StockUp
    value < 0 -> StockDown
    else -> StockFlat
}

private fun parseAccentColor(value: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(value))
}.getOrDefault(Color(0xFF006B5F))

private fun androidx.compose.material3.ColorScheme.withAccent(accent: Color, dark: Boolean): androidx.compose.material3.ColorScheme {
    // Resolve the transparent container over the active surface before selecting its
    // foreground. This keeps custom accents readable in both theme modes.
    val containerAlpha = if (dark) 0.32f else 0.16f
    val containerSurface = if (dark) DarkColors.surface else LightColors.surface
    val accentContainer = accent.copy(alpha = containerAlpha).compositeOver(containerSurface)
    val containerContent = accentContainer.contentColor()
    return copy(
        primary = accent,
        onPrimary = accent.contentColor(),
        primaryContainer = accentContainer,
        onPrimaryContainer = containerContent,
        inversePrimary = accent,
        tertiary = accent,
        onTertiary = accent.contentColor(),
        tertiaryContainer = accentContainer,
        onTertiaryContainer = containerContent,
    )
}

private fun Color.contentColor(): Color = if (luminance() > 0.52f) Color.Black else Color.White
