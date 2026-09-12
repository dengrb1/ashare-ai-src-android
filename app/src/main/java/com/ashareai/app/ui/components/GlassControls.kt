package com.ashareai.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ashareai.app.ui.theme.GlassMorphismDefaults
import com.ashareai.app.ui.theme.LocalGlassEnabled
import kotlin.math.roundToInt

/**
 * 玻璃态开关：带模糊背景和流畅过渡动画的 Switch 组件。
 *
 * 特性：
 * - 半透明轨道背景（关闭时暗色，开启时主题色）
 * - 模糊滑块，带内阴影和边缘高光
 * - 300ms 流畅过渡动画
 */
@Composable
fun GlassSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val glassEnabled = LocalGlassEnabled.current

    if (!glassEnabled) {
        // 降级到标准 Material 3 Switch
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = modifier,
            enabled = enabled,
        )
        return
    }

    val trackColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            checked -> MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        },
        animationSpec = tween(300),
        label = "trackColor",
    )

    val thumbColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
            checked -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surface
        },
        animationSpec = tween(300),
        label = "thumbColor",
    )

    val thumbOffset by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(300),
        label = "thumbOffset",
    )

    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = tween(100),
        label = "scale",
    )

    Box(
        modifier = modifier
            .scale(scale)
            .width(52.dp)
            .height(32.dp)
            .clip(CircleShape)
            .graphicsLayer {
                renderEffect = BlurEffect(4f, 4f, TileMode.Clamp)
            }
            .background(trackColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                role = Role.Switch,
                onClick = { onCheckedChange(!checked) },
            ),
    ) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .offset {
                    IntOffset((20.dp.toPx() * thumbOffset).roundToInt(), 0)
                }
                .align(Alignment.CenterStart)
                .padding(2.dp)
                .clip(CircleShape)
                .background(thumbColor)
                .drawWithContent {
                    drawContent()
                    // 内阴影效果
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to Color.Black.copy(alpha = 0.1f),
                            1f to Color.Transparent,
                        ),
                        radius = size.minDimension / 2,
                    )
                    // 顶部高光
                    drawCircle(
                        brush = Brush.verticalGradient(
                            0f to Color.White.copy(alpha = 0.2f),
                            0.5f to Color.Transparent,
                        ),
                        radius = size.minDimension / 2,
                        center = Offset(size.width / 2, size.height / 3),
                    )
                },
        )
    }
}

/**
 * 玻璃态滑杆：带渐变轨道和毛玻璃滑块。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
) {
    val glassEnabled = LocalGlassEnabled.current

    if (!glassEnabled) {
        Slider(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
        )
        return
    }

    Slider(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        enabled = enabled,
        valueRange = valueRange,
        steps = steps,
        colors = SliderDefaults.colors(
            thumbColor = MaterialTheme.colorScheme.primaryContainer,
            activeTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
            inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
        ),
        thumb = {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .graphicsLayer {
                        renderEffect = BlurEffect(6f, 6f, TileMode.Clamp)
                    }
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .drawWithContent {
                        drawContent()
                        // 边缘高光
                        drawCircle(
                            brush = Brush.radialGradient(
                                0.6f to Color.White.copy(alpha = 0.3f),
                                1f to Color.Transparent,
                            ),
                            radius = size.minDimension / 2,
                        )
                    },
            )
        },
    )
}

/**
 * 玻璃态筛选芯片：半透明背景 + 微模糊。
 */
@Composable
fun GlassFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    val glassEnabled = LocalGlassEnabled.current

    if (!glassEnabled) {
        FilterChip(
            selected = selected,
            onClick = onClick,
            label = label,
            modifier = modifier,
            enabled = enabled,
            leadingIcon = leadingIcon,
        )
        return
    }

    val backgroundColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            selected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            else -> MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
        },
        animationSpec = tween(200),
        label = "chipBackground",
    )

    val borderColor by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
            selected -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
        },
        animationSpec = tween(200),
        label = "chipBorder",
    )

    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier
            .graphicsLayer {
                renderEffect = BlurEffect(3f, 3f, TileMode.Clamp)
            },
        enabled = enabled,
        leadingIcon = leadingIcon,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = backgroundColor,
            selectedContainerColor = backgroundColor,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = borderColor,
            selectedBorderColor = borderColor,
            borderWidth = 1.dp,
        ),
    )
}
