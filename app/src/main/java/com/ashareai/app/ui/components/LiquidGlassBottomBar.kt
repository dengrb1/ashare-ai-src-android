package com.ashareai.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.ashareai.app.ui.theme.LiquidGlassDefaults
import com.ashareai.app.ui.theme.LiquidGlassSurface
import com.ashareai.app.ui.theme.LocalFullAnimationsEnabled
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

data class LiquidGlassTab(
    val key: String,
    val label: String,
    val icon: ImageVector,
    val badgeCount: Int = 0,
)

fun nearestTabIndex(startIndex: Int, dragDistancePx: Float, tabWidthPx: Float, tabCount: Int): Int {
    if (tabCount <= 1 || tabWidthPx <= 0f) return 0
    return (startIndex + (dragDistancePx / tabWidthPx).roundToInt()).coerceIn(0, tabCount - 1)
}

@Composable
fun LiquidGlassBottomBar(
    tabs: List<LiquidGlassTab>,
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    powerSaveMode: Boolean = false,
) {
    if (tabs.isEmpty()) return
    val selectedIndex = tabs.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)
    val effectivePowerSave = powerSaveMode || com.ashareai.app.ui.theme.LocalPowerSaveMode.current
    val motionEnabled = LocalFullAnimationsEnabled.current && !effectivePowerSave
    val glassEnabled = com.ashareai.app.ui.theme.LocalGlassEnabled.current && !effectivePowerSave
    val capsulePosition = remember { Animatable(selectedIndex.toFloat()) }
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragPosition by remember { mutableFloatStateOf(selectedIndex.toFloat()) }

    LaunchedEffect(selectedIndex, motionEnabled) {
        dragging = false
        dragPosition = selectedIndex.toFloat()
        if (motionEnabled) capsulePosition.animateTo(selectedIndex.toFloat(), tween(180))
        else capsulePosition.snapTo(selectedIndex.toFloat())
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            modifier = Modifier.fillMaxWidth(),
            style = LiquidGlassDefaults.Medium,
            shape = RoundedCornerShape(24.dp),
            powerSaveMode = effectivePowerSave,
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(5.dp)
                    .pointerInput(tabs.size, selectedIndex, motionEnabled) {
                        var dragStart = selectedIndex.toFloat()
                        var accumulatedPx = 0f
                        detectDragGestures(
                            onDragStart = {
                                dragStart = if (dragging) dragPosition else capsulePosition.value
                                accumulatedPx = 0f
                                dragging = true
                                dragPosition = dragStart
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                accumulatedPx += amount.x
                                val cellWidthPx = size.width.toFloat() / tabs.size
                                val preview = (dragStart + accumulatedPx / cellWidthPx).coerceIn(0f, (tabs.size - 1).toFloat())
                                dragPosition = preview
                                scope.launch { capsulePosition.snapTo(preview) }
                            },
                            onDragEnd = {
                                val target = nearestTabIndex(
                                    startIndex = dragStart.roundToInt(),
                                    dragDistancePx = accumulatedPx,
                                    tabWidthPx = size.width.toFloat() / tabs.size,
                                    tabCount = tabs.size,
                                )
                                dragging = false
                                onSelect(tabs[target].key)
                            },
                            onDragCancel = {
                                dragging = false
                                scope.launch {
                                    if (motionEnabled) capsulePosition.animateTo(selectedIndex.toFloat(), tween(180))
                                    else capsulePosition.snapTo(selectedIndex.toFloat())
                                }
                            },
                        )
                    },
            ) {
                val cellWidth = maxWidth / tabs.size
                Box(
                    modifier = Modifier
                        .offset(x = cellWidth * (if (dragging) dragPosition else capsulePosition.value))
                        .width(cellWidth)
                        .height(54.dp)
                        .zIndex(0f)
                        .clip(RoundedCornerShape(20.dp))
                        .then(
                            if (glassEnabled) {
                                Modifier.background(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                                    RoundedCornerShape(20.dp),
                                )
                            } else {
                                Modifier.background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(20.dp))
                            },
                        ),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    tabs.forEachIndexed { index, tab ->
                        val selected = index == selectedIndex
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(54.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .semantics { this.selected = selected }
                                .clickable(role = Role.Tab) { onSelect(tab.key) }
                                .padding(horizontal = 2.dp, vertical = 5.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            BadgedBox(badge = {
                                if (tab.badgeCount > 0) {
                                    Badge { Text(if (tab.badgeCount > 99) "99+" else tab.badgeCount.toString()) }
                                }
                            }) {
                                Icon(
                                    imageVector = tab.icon,
                                    contentDescription = null,
                                    tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.height(21.dp),
                                )
                            }
                            Text(
                                text = tab.label,
                                maxLines = 1,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
