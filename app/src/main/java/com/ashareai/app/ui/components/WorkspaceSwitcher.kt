package com.ashareai.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudQueue
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.ashareai.app.ui.theme.LiquidGlassDefaults
import com.ashareai.app.ui.theme.LiquidGlassSurface
import com.ashareai.app.workspace.Workspace

/** Compact, always-available workspace control shared by both app surfaces. */
@Composable
fun WorkspaceSwitcher(
    workspace: Workspace,
    onWorkspaceSelected: (Workspace) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    LiquidGlassSurface(
        modifier = modifier,
        style = LiquidGlassDefaults.Light,
        shape = RoundedCornerShape(18.dp),
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WorkspaceChoice(
                selected = workspace == Workspace.LOCAL,
                enabled = enabled,
                label = "独立",
                icon = Icons.Outlined.PhoneAndroid,
                onClick = { onWorkspaceSelected(Workspace.LOCAL) },
            )
            WorkspaceChoice(
                selected = workspace == Workspace.FUSION,
                enabled = enabled,
                label = "连接",
                icon = Icons.Outlined.CloudQueue,
                onClick = { onWorkspaceSelected(Workspace.FUSION) },
            )
        }
    }
}

@Composable
private fun WorkspaceChoice(
    selected: Boolean,
    enabled: Boolean,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    val containerColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else androidx.compose.ui.graphics.Color.Transparent,
        animationSpec = tween(220, easing = FastOutSlowInEasing),
        label = "workspace_choice_color",
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(containerColor)
            .clickable(enabled = enabled, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(26.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
