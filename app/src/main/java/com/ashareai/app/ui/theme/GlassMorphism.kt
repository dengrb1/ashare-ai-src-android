package com.ashareai.app.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局开关：是否启用玻璃态材质效果。
 * 默认启用，可在主题层级禁用以降级到标准 Material 3 组件。
 */
val LocalGlassEnabled = compositionLocalOf { true }

/** Controls non-essential motion in glass surfaces and shared navigation controls. */
val LocalFullAnimationsEnabled = compositionLocalOf { true }
val LocalPowerSaveMode = compositionLocalOf { false }

/**
 * 玻璃态材质配置：模糊强度、透明度、色彩叠加层。
 */
data class GlassMorphismStyle(
    val blurRadius: Float = 16f,
    val backgroundAlpha: Float = 0.72f,
    val tintAlpha: Float = 0.08f,
    val borderAlpha: Float = 0.2f,
)

object GlassMorphismDefaults {
    val Subtle = GlassMorphismStyle(
        blurRadius = 12f,
        backgroundAlpha = 0.85f,
        tintAlpha = 0.04f,
        borderAlpha = 0.15f,
    )
    val Medium = GlassMorphismStyle(
        blurRadius = 16f,
        backgroundAlpha = 0.72f,
        tintAlpha = 0.08f,
        borderAlpha = 0.2f,
    )
    val Strong = GlassMorphismStyle(
        blurRadius = 24f,
        backgroundAlpha = 0.6f,
        tintAlpha = 0.12f,
        borderAlpha = 0.25f,
    )
}

/**
 * 玻璃态表面：半透明模糊背景 + 主题色彩叠加 + 细边框。
 *
 * 参考 iOS/macOS 毛玻璃效果，使用 BlurEffect 和多层色彩叠加实现深度感知。
 * 自动适配浅色/深色主题，动态响应主题色彩。
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    style: GlassMorphismStyle = GlassMorphismDefaults.Medium,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.medium,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!LocalGlassEnabled.current || LocalPowerSaveMode.current) {
        androidx.compose.material3.Surface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surface,
            content = { Box(content = content) },
        )
        return
    }
    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryColor = MaterialTheme.colorScheme.primary
    val borderColor = MaterialTheme.colorScheme.outline

    Box(modifier = modifier.clip(shape)) {
        // Keep material decoration behind content so labels remain crisp.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(surfaceColor.copy(alpha = style.backgroundAlpha))
                .drawWithContent {
                    drawContent()
                // 主题色彩叠加层：增强色彩深度
                drawRect(
                    color = primaryColor.copy(alpha = style.tintAlpha),
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                )
                // 顶部微光边缘：2dp 渐隐高光
                val edgeHeight = 2.dp.toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to primaryColor.copy(alpha = 0.16f),
                        1f to Color.Transparent,
                    ),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, edgeHeight),
                )
                // 细边框：增强结构感
                val strokeWidth = 1.dp.toPx()
                // 顶边
                drawRect(
                    color = borderColor.copy(alpha = style.borderAlpha),
                    topLeft = Offset(0f, 0f),
                    size = Size(size.width, strokeWidth),
                )
                // 底边
                drawRect(
                    color = borderColor.copy(alpha = style.borderAlpha),
                    topLeft = Offset(0f, size.height - strokeWidth),
                    size = Size(size.width, strokeWidth),
                )
                // 左边
                drawRect(
                    color = borderColor.copy(alpha = style.borderAlpha),
                    topLeft = Offset(0f, 0f),
                    size = Size(strokeWidth, size.height),
                )
                // 右边
                drawRect(
                    color = borderColor.copy(alpha = style.borderAlpha),
                    topLeft = Offset(size.width - strokeWidth, 0f),
                    size = Size(strokeWidth, size.height),
                )
                },
        )
        content()
    }
}

/**
 * 玻璃态卡片：带内边距的玻璃表面，用于内容容器。
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    style: GlassMorphismStyle = GlassMorphismDefaults.Medium,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        style = style,
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(contentPadding),
            content = content,
        )
    }
}

/**
 * 玻璃态对话框背景：强模糊 + 深色叠加，用于模态界面。
 */
@Composable
fun GlassDialog(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        style = GlassMorphismDefaults.Strong,
        shape = RoundedCornerShape(24.dp),
        content = content,
    )
}

/**
 * 玻璃态底部抽屉：顶部圆角 + 中等模糊。
 */
@Composable
fun GlassBottomSheet(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    GlassSurface(
        modifier = modifier,
        style = GlassMorphismDefaults.Medium,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.padding(16.dp),
            content = content,
        )
    }
}
