package com.ashareai.app.ui.theme

import android.os.Build
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.rememberInfiniteTransition
import kotlin.math.sin
import kotlin.math.cos

/**
 * 液体玻璃样式定义
 */
data class LiquidGlassStyle(
    val blurRadius: Dp,
    val backgroundAlpha: Float,
    val borderAlpha: Float,
    val animationEnabled: Boolean,
    val shimmerIntensity: Float,
    /** Blur is intentionally disabled for lightweight, always-visible bars. */
    val blurEnabled: Boolean = true,
)

object LiquidGlassDefaults {
    val Light = LiquidGlassStyle(
        blurRadius = 8.dp,
        backgroundAlpha = 0.5f,
        borderAlpha = 0.3f,
        animationEnabled = true,
        shimmerIntensity = 0.15f,
        blurEnabled = false,
    )

    val Medium = LiquidGlassStyle(
        blurRadius = 12.dp,
        backgroundAlpha = 0.62f,
        borderAlpha = 0.5f,
        animationEnabled = true,
        shimmerIntensity = 0.2f,
        blurEnabled = true,
    )

    val Heavy = LiquidGlassStyle(
        blurRadius = 16.dp,
        backgroundAlpha = 0.74f,
        borderAlpha = 0.65f,
        animationEnabled = true,
        shimmerIntensity = 0.25f,
        blurEnabled = true,
    )

    val PowerSave = LiquidGlassStyle(
        blurRadius = 4.dp,
        backgroundAlpha = 0.92f,
        borderAlpha = 0.7f,
        animationEnabled = false,
        shimmerIntensity = 0f,
        blurEnabled = false,
    )
}

/**
 * 液体玻璃表面组件
 * 只在状态变化时做短时过渡的毛玻璃材质。
 *
 * 玻璃表面位于导航和临时操作层，不能通过无限动画持续唤醒 GPU；
 * 需要持续刷新的状态应由内容本身表达，而不是让整个表面常驻动画。
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    style: LiquidGlassStyle = LiquidGlassDefaults.Medium,
    shape: Shape = MaterialTheme.shapes.medium,
    powerSaveMode: Boolean = false,
    content: @Composable () -> Unit,
) {
    val effectivePowerSave = powerSaveMode || LocalPowerSaveMode.current
    val animationsEnabled = LocalFullAnimationsEnabled.current && !effectivePowerSave
    if (!LocalGlassEnabled.current || effectivePowerSave) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            content = content,
        )
        return
    }

    val activeStyle = style
    val blurRadiusPx = with(LocalDensity.current) { activeStyle.blurRadius.toPx() }

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val primaryColor = MaterialTheme.colorScheme.primary
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    val transitionTarget = if (activeStyle.animationEnabled && animationsEnabled) 1f else 0f
    val transition by animateFloatAsState(
        targetValue = transitionTarget,
        animationSpec = if (animationsEnabled) tween(220, easing = FastOutSlowInEasing) else androidx.compose.animation.core.snap(),
        label = "glass_state_transition",
    )
    val tintAlpha = activeStyle.shimmerIntensity * 0.35f * transition

    Box(
        modifier = modifier
            .clip(shape)
            // Draw the material on the surface so an unconstrained bottom bar
            // keeps its measured height.
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        surfaceColor.copy(alpha = activeStyle.backgroundAlpha),
                        primaryColor.copy(alpha = tintAlpha),
                        surfaceColor.copy(alpha = activeStyle.backgroundAlpha),
                    )
                )
            )
            .border(
                width = 1.dp,
                color = borderColor.copy(alpha = activeStyle.borderAlpha),
                shape = shape,
            ),
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activeStyle.blurEnabled && activeStyle.blurRadius.value > 0f) {
            Box(
                modifier = androidx.compose.ui.Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        renderEffect = BlurEffect(
                            blurRadiusPx,
                            blurRadiusPx,
                            TileMode.Clamp,
                        )
                    }
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                primaryColor.copy(alpha = tintAlpha * 0.75f),
                                Color.White.copy(alpha = tintAlpha * 0.35f),
                                primaryColor.copy(alpha = tintAlpha * 0.55f),
                            ),
                        ),
                    ),
            )
        }
        Box(
            modifier = androidx.compose.ui.Modifier
                .matchParentSize()
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.16f * transition),
                            0.18f to Color.Transparent,
                        ),
                    )
                },
        )
        content()
    }
}

/**
 * 全局液体玻璃动画背景
 * 为整个应用提供微妙的流动动画效果，仅在导航和临时操作层使用
 *
 * 性能优化：
 * - 省电模式自动禁用
 * - 低性能设备自动降级
 * - 使用硬件加速的 Canvas 绘制
 * - 动画状态复用避免重组
 */
@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    intensity: Float = 0.15f,
    content: @Composable () -> Unit,
) {
    val effectivePowerSave = LocalPowerSaveMode.current
    val glassEnabled = LocalGlassEnabled.current
    val animationsEnabled = LocalFullAnimationsEnabled.current

    // 省电模式或动画关闭时直接渲染内容，零开销
    if (effectivePowerSave || !glassEnabled || !animationsEnabled) {
        Box(modifier = modifier) {
            content()
        }
        return
    }

    // 创建无限循环动画，用于流动效果
    val infiniteTransition = rememberInfiniteTransition(label = "liquid_glass_background")

    // 三个不同速度的动画波形，创造自然的流动感
    val wave1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 8000,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave1",
    )

    val wave2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 12000,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave2",
    )

    val wave3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(
                durationMillis = 15000,
                easing = LinearEasing,
            ),
            repeatMode = RepeatMode.Restart,
        ),
        label = "wave3",
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier
            .drawWithContent {
                drawContent()

                // 使用三个波形叠加创造流动效果
                val w1Rad = Math.toRadians(wave1.toDouble())
                val w2Rad = Math.toRadians(wave2.toDouble())
                val w3Rad = Math.toRadians(wave3.toDouble())

                // 计算动态透明度
                val alpha1 = (sin(w1Rad) * 0.5 + 0.5).toFloat()
                val alpha2 = (sin(w2Rad * 1.3) * 0.5 + 0.5).toFloat()
                val alpha3 = (cos(w3Rad * 0.7) * 0.5 + 0.5).toFloat()

                // 绘制多层渐变，创造深度感
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = alpha1 * intensity * 0.08f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.3f, size.height * 0.2f),
                        radius = size.width * 0.6f,
                    ),
                )

                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = alpha2 * intensity * 0.06f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.7f, size.height * 0.5f),
                        radius = size.width * 0.5f,
                    ),
                )

                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = alpha3 * intensity * 0.05f),
                            Color.Transparent,
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.8f),
                        radius = size.width * 0.7f,
                    ),
                )
            },
    ) {
        content()
    }
}

/**
 * 增强版液体玻璃表面，添加动态光影效果
 */
@Composable
fun EnhancedLiquidGlassSurface(
    modifier: Modifier = Modifier,
    style: LiquidGlassStyle = LiquidGlassDefaults.Medium,
    shape: Shape = MaterialTheme.shapes.medium,
    powerSaveMode: Boolean = false,
    enableDynamicLight: Boolean = true,
    content: @Composable () -> Unit,
) {
    val effectivePowerSave = powerSaveMode || LocalPowerSaveMode.current
    val animationsEnabled = LocalFullAnimationsEnabled.current && !effectivePowerSave

    if (!LocalGlassEnabled.current || effectivePowerSave) {
        Surface(
            modifier = modifier,
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainer,
            content = content,
        )
        return
    }

    val activeStyle = style
    val blurRadiusPx = with(LocalDensity.current) { activeStyle.blurRadius.toPx() }

    val surfaceColor = MaterialTheme.colorScheme.surfaceContainerHigh
    val primaryColor = MaterialTheme.colorScheme.primary
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    val transitionTarget = if (activeStyle.animationEnabled && animationsEnabled) 1f else 0f
    val transition by animateFloatAsState(
        targetValue = transitionTarget,
        animationSpec = if (animationsEnabled) tween(220, easing = FastOutSlowInEasing) else androidx.compose.animation.core.snap(),
        label = "glass_state_transition",
    )

    // 动态光影动画
    val lightPhase = if (enableDynamicLight && animationsEnabled) {
        val infiniteTransition = rememberInfiniteTransition(label = "light_phase")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = androidx.compose.animation.core.tween(3000, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "light",
        ).value
    } else {
        0f
    }

    val tintAlpha = activeStyle.shimmerIntensity * 0.35f * transition
    val dynamicTint = tintAlpha * (0.7f + 0.3f * lightPhase)

    Box(
        modifier = modifier
            .clip(shape)
            .background(
                brush = Brush.linearGradient(
                    listOf(
                        surfaceColor.copy(alpha = activeStyle.backgroundAlpha),
                        primaryColor.copy(alpha = dynamicTint),
                        surfaceColor.copy(alpha = activeStyle.backgroundAlpha),
                    )
                )
            )
            .border(
                width = 1.dp,
                color = borderColor.copy(alpha = activeStyle.borderAlpha),
                shape = shape,
            ),
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activeStyle.blurEnabled && activeStyle.blurRadius.value > 0f) {
            Box(
                modifier = androidx.compose.ui.Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        renderEffect = BlurEffect(
                            blurRadiusPx,
                            blurRadiusPx,
                            TileMode.Clamp,
                        )
                    }
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                primaryColor.copy(alpha = dynamicTint * 0.75f),
                                Color.White.copy(alpha = dynamicTint * 0.35f),
                                primaryColor.copy(alpha = dynamicTint * 0.55f),
                            ),
                        ),
                    ),
            )
        }
        Box(
            modifier = androidx.compose.ui.Modifier
                .matchParentSize()
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.16f * transition),
                            0.18f to Color.Transparent,
                        ),
                    )
                },
        )
        content()
    }
}
