package com.ashareai.app.ui.theme

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCanvasBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight

/** Optical presets for navigation and controls, never for data surfaces. */
data class GlassMaterial(val blur: Dp = 8.dp, val opacity: Float = .68f, val lensHeight: Dp = 12.dp)

data class LiquidGlassStyle(
    val blurRadius: Dp,
    val backgroundAlpha: Float,
    val borderAlpha: Float,
    val animationEnabled: Boolean,
    val shimmerIntensity: Float,
    val blurEnabled: Boolean = true,
)

object LiquidGlassDefaults {
    val Light = LiquidGlassStyle(4.dp, .76f, .22f, true, 0f)
    val Medium = LiquidGlassStyle(8.dp, .64f, .28f, true, 0f)
    val Heavy = LiquidGlassStyle(12.dp, .80f, .32f, true, 0f)
    val PowerSave = LiquidGlassStyle(0.dp, 1f, .3f, false, 0f, false)
}

private val LocalControlBackdrop = compositionLocalOf<Backdrop?> { null }
val LocalContentBackdrop = compositionLocalOf<LayerBackdrop?> { null }

/** Capture only page content, separate from the navigation that samples it. */
@Composable
fun Modifier.glassBackdropSource(): Modifier {
    val backdrop = LocalContentBackdrop.current
    return if (backdrop == null) this else this.layerBackdrop(backdrop)
}

@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    style: LiquidGlassStyle = LiquidGlassDefaults.Medium,
    shape: Shape = MaterialTheme.shapes.medium,
    powerSaveMode: Boolean = false,
    sampleContent: Boolean = false,
    content: @Composable () -> Unit,
) {
    val enabled = LocalGlassEnabled.current && !powerSaveMode && !LocalPowerSaveMode.current
    val surface = MaterialTheme.colorScheme.surfaceContainer
    val outline = MaterialTheme.colorScheme.outlineVariant
    val backdrop = if (sampleContent) LocalContentBackdrop.current ?: LocalControlBackdrop.current else LocalControlBackdrop.current
    // The same Box and content call remain composed when the material is disabled.
    // Never switch between Surface/Box branches around stateful page content.
    val materialModifier = if (enabled && backdrop != null && Build.VERSION.SDK_INT >= 31) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = {
                vibrancy()
                if (style.blurEnabled) blur(style.blurRadius.toPx())
                if (shape is CornerBasedShape) lens(12.dp.toPx(), 20.dp.toPx(), depthEffect = true)
            },
            highlight = { Highlight.Default.copy(alpha = .38f) },
            shadow = null,
            onDrawSurface = { drawRect(surface.copy(alpha = style.backgroundAlpha)) },
        )
    } else Modifier.background(surface, shape)
    Box(modifier.clip(shape).then(materialModifier).border(.5.dp, outline.copy(alpha = .7f), shape)) {
        content()
    }
}

/** Source-compatible adapter used by the existing K-line controls. */
@Composable
fun BackdropLiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.medium,
    blurRadius: Dp = 8.dp,
    content: @Composable () -> Unit,
) = LiquidGlassSurface(modifier, LiquidGlassDefaults.Medium.copy(blurRadius = blurRadius), shape, content = content)

/** A stable host. No idle animations, and no conditionally recreated content tree. */
@Composable
fun LiquidGlassBackground(
    modifier: Modifier = Modifier,
    intensity: Float = .15f,
    content: @Composable () -> Unit,
) {
    val background = MaterialTheme.colorScheme.background
    val tint = MaterialTheme.colorScheme.primaryContainer
    val controlBackdrop = rememberCanvasBackdrop {
        drawRect(background)
        drawRect(Brush.linearGradient(listOf(tint.copy(alpha = intensity), background)))
    }
    val contentBackdrop = rememberLayerBackdrop()
    CompositionLocalProvider(LocalControlBackdrop provides controlBackdrop, LocalContentBackdrop provides contentBackdrop) {
        Box(modifier.background(background)) { content() }
    }
}

@Composable
fun EnhancedLiquidGlassSurface(
    modifier: Modifier = Modifier,
    style: LiquidGlassStyle = LiquidGlassDefaults.Medium,
    shape: Shape = MaterialTheme.shapes.medium,
    powerSaveMode: Boolean = false,
    enableDynamicLight: Boolean = true,
    content: @Composable () -> Unit,
) = LiquidGlassSurface(modifier, style, shape, powerSaveMode, content = content)
