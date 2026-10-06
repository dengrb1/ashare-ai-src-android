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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && activeStyle.blurRadius.value > 0f) {
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
