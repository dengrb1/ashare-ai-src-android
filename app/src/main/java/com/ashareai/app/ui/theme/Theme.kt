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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// 工作台风格：中性色承载高密度信息，青色仅用于关键操作与状态。
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
    background = Color(0xFFF7F8FA),
    onBackground = Color(0xFF1A1B20),
    surface = Color.White,
    onSurface = Color(0xFF1A1B20),
    surfaceVariant = Color(0xFFF2F3F7),
    onSurfaceVariant = Color(0xFF6B6E7A),
    outline = Color(0xFFD9DBE3),
    outlineVariant = Color(0xFFECEDF2),
    error = Color(0xFFD32F2F),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF72D7C8),
    onPrimary = Color(0xFF003731),
    primaryContainer = Color(0xFF005047),
    onPrimaryContainer = Color(0xFF9AF3E5),
    secondary = Color(0xFFAAAEBB),
    background = Color(0xFF121317),
    onBackground = Color(0xFFE3E4EA),
    surface = Color(0xFF1B1C22),
    onSurface = Color(0xFFE3E4EA),
    surfaceVariant = Color(0xFF24252D),
    onSurfaceVariant = Color(0xFF9DA0AC),
    outline = Color(0xFF3A3C46),
    outlineVariant = Color(0xFF2B2D35),
    error = Color(0xFFEF9A9A),
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

@Composable
fun AShareTheme(
    darkModePref: String = "system",
    content: @Composable () -> Unit,
) {
    val dark = when (darkModePref) {
        "dark" -> true
        "light" -> false
        else -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        shapes = AppShapes,
        typography = AppTypography,
        content = content,
    )
}

/** 涨跌配色：>0 红，<0 绿，0/缺失 灰。 */
fun changeColor(value: Double?): Color = when {
    value == null -> StockFlat
    value > 0 -> StockUp
    value < 0 -> StockDown
    else -> StockFlat
}
