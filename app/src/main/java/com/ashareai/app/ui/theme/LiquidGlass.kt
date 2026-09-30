package com.ashareai.app.ui.theme

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * 液体玻璃样式定义
 */
data class LiquidGlassStyle(
    val blurRadius: Dp,
    val backgroundAlpha: Float,
    val borderAlpha: Float,
    val animationEnabled: Boolean,
    val shimmerIntensity: Float,
)

object LiquidGlassDefaults {
    val Light = LiquidGlassStyle(
        blurRadius = 8.dp,
        backgroundAlpha = 0.5f,
        borderAlpha = 0.3f,
        animationEnabled = true,
        shimmerIntensity = 0.15f,
    )

    val Medium = LiquidGlassStyle(
        blurRadius = 12.dp,
        backgroundAlpha = 0.72f,
        borderAlpha = 0.5f,
        animationEnabled = true,
        shimmerIntensity = 0.2f,
    )

    val Heavy = LiquidGlassStyle(
        blurRadius = 16.dp,
        backgroundAlpha = 0.85f,
        borderAlpha = 0.65f,
        animationEnabled = true,
        shimmerIntensity = 0.25f,
    )

    val PowerSave = LiquidGlassStyle(
        blurRadius = 4.dp,
        backgroundAlpha = 0.92f,
        borderAlpha = 0.7f,
        animationEnabled = false,
        shimmerIntensity = 0f,
    )
}

/**
 * 液体玻璃表面组件
 * 带有动态波纹和流光效果的毛玻璃材质
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    style: LiquidGlassStyle = LiquidGlassDefaults.Medium,
    shape: Shape = MaterialTheme.shapes.medium,
    powerSaveMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val activeStyle = if (powerSaveMode) LiquidGlassDefaults.PowerSave else style

    val infiniteTransition = rememberInfiniteTransition(label = "liquidGlass")

    val shimmerOffset by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmerOffset"
    )

    val wavePhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wavePhase"
    )

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val primaryColor = MaterialTheme.colorScheme.primary
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    // 波纹效果：在省电模式下静止
    val wave1 = if (activeStyle.animationEnabled) {
        sin(wavePhase) * 0.03f + 0.97f
    } else {
        1f
    }

    val wave2 = if (activeStyle.animationEnabled) {
        sin(wavePhase + PI.toFloat() * 0.5f) * 0.02f + 0.98f
    } else {
        1f
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = wave1
                scaleY = wave2
                // 省电模式下禁用模糊
                if (!powerSaveMode) {
                    renderEffect = androidx.compose.ui.graphics.BlurEffect(
                        radiusX = activeStyle.blurRadius.toPx() * 0.3f,
                        radiusY = activeStyle.blurRadius.toPx() * 0.3f,
                    )
                }
            }
            .clip(shape)
            .background(
                brush = if (activeStyle.animationEnabled) {
                    Brush.linearGradient(
                        0f to surfaceColor.copy(alpha = activeStyle.backgroundAlpha),
                        shimmerOffset * 0.3f to surfaceColor
                            .copy(alpha = activeStyle.backgroundAlpha + activeStyle.shimmerIntensity),
                        shimmerOffset * 0.5f to primaryColor
                            .copy(alpha = activeStyle.shimmerIntensity * 0.5f),
                        shimmerOffset * 0.7f to surfaceColor
                            .copy(alpha = activeStyle.backgroundAlpha + activeStyle.shimmerIntensity * 0.8f),
                        1f to surfaceColor.copy(alpha = activeStyle.backgroundAlpha),
                    )
                } else {
                    Brush.linearGradient(
                        listOf(
                            surfaceColor.copy(alpha = activeStyle.backgroundAlpha),
                            surfaceColor.copy(alpha = activeStyle.backgroundAlpha),
                        )
                    )
                }
            )
            .border(
                width = 1.dp,
                color = borderColor.copy(alpha = activeStyle.borderAlpha),
                shape = shape,
            )
    ) {
        content()
    }
}
