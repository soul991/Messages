package com.messages.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import com.messages.designsystem.GlassDepth
import com.messages.designsystem.LiquidGlassCard
import com.messages.designsystem.LocalDarkTheme
import java.util.Locale

/**
 * Phase 5 §4 shared settings list language: one row anatomy (20dp grid, a
 * reserved leading icon slot, bodyLarge title over onSurfaceVariant subtitle)
 * plus one divider, so the 12 settings groups read as chapters in the same
 * book instead of 12 one-off lists.
 */

/** Section header: primary-colored titleSmall uppercase on the 24dp grid. */
@Composable
internal fun SettingsSectionHeader(title: String) {
    Text(
        title.uppercase(Locale.getDefault()),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 20.dp, top = 20.dp, bottom = 6.dp),
    )
}

/** Section divider: inset calm spacing between grouped glass cards. */
@Composable
internal fun SettingsSectionDivider() {
    Spacer(Modifier.height(8.dp))
}

/** Grouped Liquid Glass container for settings sections. */
@Composable
internal fun SettingsGlassGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    LiquidGlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        shape = RoundedCornerShape(20.dp),
        depth = GlassDepth.LOW,
    ) {
        Column(Modifier.fillMaxWidth()) {
            content()
        }
    }
}

/** Hair-line separator between rows inside a SettingsGlassGroup. */
@Composable
internal fun SettingsRowDivider(hasIcon: Boolean = true) {
    val isDark = LocalDarkTheme.current
    HorizontalDivider(
        modifier = Modifier.padding(start = if (hasIcon) 56.dp else 16.dp, end = 16.dp),
        thickness = 0.5.dp,
        color = if (isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.06f),
    )
}

/**
 * Row scaffold every settings row builds on. The icon slot is always
 * reserved when [icon] is non-null anywhere in the section, keeping titles
 * on one vertical line; rows without icons in icon-less sections span from
 * the 20dp edge.
 */
@Composable
internal fun SettingsRow(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    indented: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.5f
    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(enabled = enabled, onClick = onClick) else it }
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(
                icon, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(16.dp))
        } else if (indented) {
            // Child row of an iconed row: align with the parent's title column.
            Spacer(Modifier.width(40.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha),
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            trailing()
        }
    }
}

@Composable
internal fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    SettingsRow(
        icon = icon, title = title, subtitle = subtitle,
        onClick = { if (enabled) onChange(!checked) }, enabled = enabled,
        trailing = { Switch(checked = checked, onCheckedChange = onChange, enabled = enabled) },
    )
}

/** Navigation row — chevron for in-app targets, open-in-new for system sheets. */
@Composable
internal fun SettingsNavRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    icon: ImageVector? = null,
    external: Boolean = false,
    indented: Boolean = false,
) {
    SettingsRow(
        icon = icon, title = title, subtitle = subtitle, onClick = onClick, indented = indented,
        trailing = {
            Icon(
                if (external) Icons.AutoMirrored.Filled.OpenInNew
                else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/** Value-picker row: current value in primary at the trailing edge, dropdown on tap. */
@Composable
internal fun <T> SettingsDropdownRow(
    title: String,
    subtitle: String?,
    value: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    icon: ImageVector? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    SettingsRow(
        icon = icon, title = title, subtitle = subtitle, onClick = { expanded = true },
        trailing = {
            Box {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { (option, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = {
                                expanded = false
                                onSelect(option)
                            },
                        )
                    }
                }
            }
        },
    )
}
