package com.ashareai.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ashareai.app.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun GlassSwitch(checked: Boolean, onCheckedChange: (Boolean) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val motion = LocalFullAnimationsEnabled.current && !LocalPowerSaveMode.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val offset by animateFloatAsState(if (checked) 1f else 0f, if (motion) tween(180) else snap(), label = "switch.thumb")
    val scale by animateFloatAsState(if (pressed && motion) .96f else 1f, if (motion) tween(100) else snap(), label = "switch.press")
    Box(modifier.size(64.dp, 48.dp).toggleable(checked, enabled = enabled, role = Role.Switch, interactionSource = source, indication = null, onValueChange = onCheckedChange), contentAlignment = Alignment.Center) {
        LiquidGlassSurface(Modifier.size(56.dp, 32.dp).scale(scale), shape = CircleShape) {
            Box(Modifier.fillMaxSize().background(if (checked) MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) .85f else .35f) else MaterialTheme.colorScheme.outlineVariant.copy(alpha = .5f))) {
                Box(Modifier.align(Alignment.CenterStart).offset { IntOffset((24.dp.toPx() * offset + 2.dp.toPx()).roundToInt(), 0) }.size(28.dp).clip(CircleShape).background(if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.surface))
            }
        }
    }
}

@Composable
fun GlassButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(50),
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    elevation: ButtonElevation? = null,
    border: androidx.compose.foundation.BorderStroke? = null,
    contentPadding: PaddingValues = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val source = interactionSource ?: remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val motion = LocalFullAnimationsEnabled.current && !LocalPowerSaveMode.current
    val scale by animateFloatAsState(if (pressed && motion) .97f else 1f, if (motion) tween(100) else snap(), label = "button.press")
    val foreground = if (enabled) colors.contentColor else colors.disabledContentColor
    val background = if (enabled) colors.containerColor else colors.disabledContainerColor
    LiquidGlassSurface(modifier.scale(scale).clip(shape).clickable(source, indication = androidx.compose.foundation.LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick), shape = shape) {
        CompositionLocalProvider(LocalContentColor provides foreground) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                Row(Modifier.background(background.copy(alpha = if (LocalGlassEnabled.current) .88f else 1f)).heightIn(min = 48.dp).padding(contentPadding), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically, content = content)
            }
        }
    }
}

@Composable
fun GlassFilterChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, leadingIcon: @Composable (() -> Unit)? = null) {
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
    LiquidGlassSurface(modifier.clip(RoundedCornerShape(50)).selectable(selected = selected, enabled = enabled, role = Role.RadioButton, onClick = onClick), shape = RoundedCornerShape(50)) {
        CompositionLocalProvider(LocalContentColor provides foreground.copy(alpha = if (enabled) 1f else .45f)) {
            ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                Row(Modifier.background(if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent).heightIn(min = 48.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    leadingIcon?.invoke()
                    label()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSlider(value: Float, onValueChange: (Float) -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, valueRange: ClosedFloatingPointRange<Float> = 0f..1f, steps: Int = 0) {
    Slider(value, onValueChange, modifier, enabled, valueRange = valueRange, steps = steps, thumb = {
        LiquidGlassSurface(Modifier.size(28.dp), shape = CircleShape) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .72f)))
        }
    })
}
