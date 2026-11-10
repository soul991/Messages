package com.messages.app.ui.settings

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import com.messages.designsystem.appSwitchColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messages.app.R
import com.messages.designsystem.DesignTokens
import com.messages.designsystem.LocalDarkTheme
import com.messages.designsystem.Motion
import com.messages.designsystem.categoryPalette
import com.messages.designsystem.groupCard
import kotlinx.coroutines.launch

/** Section header: neutral onSurfaceVariant titleSmall on the 20dp grid. */
@Composable
internal fun SettingsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp),
    )
}

/** Section divider: inset to the 20dp grid, calm spacing. */
@Composable
internal fun SettingsSectionDivider() {
    HorizontalDivider(
        Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/** Divider between rows inside a [SettingsGroupCard]: starts at 74dp to align with row titles. */
@Composable
internal fun SettingsCardDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 74.dp, end = 16.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

val LocalSettingsPageAnimated = androidx.compose.runtime.compositionLocalOf { false }

/**
 * Cascading card entrance animation for Settings pages (Phase 4):
 * 30ms stagger per card, capped at 6 cards (0..5), animated with screenSpring.
 * Animate cards only once per page entry, only the first 6 cards, and never on scroll re-entry.
 */
@Composable
fun Modifier.cascadeEntrance(index: Int): Modifier {
    if (!Motion.animationsEnabled()) return this
    if (index >= 6) return this
    if (LocalSettingsPageAnimated.current) return this
    val delayMs = (index * 30).toLong()
    var visible by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (delayMs > 0) kotlinx.coroutines.delay(delayMs)
        visible = true
    }
    val animProgress = animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = Motion.screenSpring(),
        label = "card-cascade-$index",
    )
    return this.graphicsLayer {
        val p = animProgress.value
        alpha = p
        translationY = (1f - p) * 24.dp.toPx()
    }
}

/**
 * Grouped card container for sub-page settings rows:
 * Corner 28dp, fill colorScheme.groupCard, 1dp outlineVariant 50% alpha border (hairline),
 * with optional footer caption below the card.
 */
@Composable
internal fun SettingsGroupCard(
    modifier: Modifier = Modifier,
    cardIndex: Int = 0,
    footer: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .cascadeEntrance(cardIndex),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.groupCard,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                content()
            }
        }
        if (footer != null) {
            Box(
                modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 16.dp, end = 20.dp),
            ) {
                footer()
            }
        }
    }
}

object SettingsTileColors {
    val GreenDark = Color(0xFF30D158)
    val GreenLight = Color(0xFF146C2E)

    val RedDark = Color(0xFFFFB4AB)
    val RedLight = Color(0xFFB3261E)

    val AmberDark = Color(0xFFFFB951)
    val AmberLight = Color(0xFF7A4300)

    val BlueDark = Color(0xFF64B5F6)
    val BlueLight = Color(0xFF004BA0)

    fun forCategory(categoryBadge: String?, isDark: Boolean): Color {
        // Yellow, blue and red are reserved for dock icons only. Use neutral green tinted tiles everywhere else in settings.
        return if (isDark) GreenDark else GreenLight
    }
}

/**
 * SettingsIconTile (§Phase D1):
 * Renders an icon tile with a tinted container and full-contrast glyph.
 * In light mode: container tinted 14% with the icon's color, glyph full contrast (the icon's color).
 * In dark mode: container tinted 24% with the icon's color, glyph full contrast (the icon's color).
 * This eliminates the dark gray (#2C2C2E) "holes" in light mode cards and guarantees WCAG AA 4.5:1.
 */
@Composable
internal fun SettingsIconTile(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    alpha: Float = 1f,
    size: Dp = 40.dp,
    iconSize: Dp = 22.dp,
    shape: Shape = RoundedCornerShape(12.dp),
) {
    val isDark = LocalDarkTheme.current
    val containerTint = if (isDark) 0.24f else 0.14f
    val containerColor = color.copy(alpha = containerTint * alpha)
    val glyphColor = color.copy(alpha = alpha)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(containerColor),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = glyphColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

/**
 * Row scaffold every settings row builds on.
 * Left icons become squircle badges: 40dp, corner 12dp, fill 14%/24% tint of icon color,
 * glyph full contrast (contrast >= 4.5:1).
 * Rows padded 16h x 12v, single-line ellipsized titles and subtitles.
 */
@Composable
internal fun SettingsRow(
    icon: ImageVector?,
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    indented: Boolean = false,
    categoryBadge: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val alpha = if (enabled) 1f else 0.5f
    val isDark = LocalDarkTheme.current
    val iconColor = SettingsTileColors.forCategory(categoryBadge, isDark)

    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(enabled = enabled, onClick = onClick) else it }
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            SettingsIconTile(
                icon = icon,
                color = iconColor,
                alpha = alpha,
                size = 40.dp,
                iconSize = 22.dp,
            )
            Spacer(Modifier.width(18.dp))
        } else if (indented) {
            // Align with row titles (16dp row padding + 40dp badge + 18dp spacer = 74dp)
            Spacer(Modifier.width(58.dp))
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
    categoryBadge: String? = null,
) {
    SettingsRow(
        icon = icon, title = title, subtitle = subtitle,
        onClick = { if (enabled) onChange(!checked) }, enabled = enabled,
        categoryBadge = categoryBadge,
        trailing = {
            Switch(
                checked = checked,
                onCheckedChange = onChange,
                enabled = enabled,
                colors = appSwitchColors(),
            )
        },
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
    categoryBadge: String? = null,
) {
    SettingsRow(
        icon = icon, title = title, subtitle = subtitle, onClick = onClick, indented = indented,
        categoryBadge = categoryBadge,
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

/** Value-picker row: current value in neutral onSurfaceVariant at the trailing edge, dropdown on tap. */
@Composable
internal fun <T> SettingsDropdownRow(
    title: String,
    subtitle: String?,
    value: String,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    icon: ImageVector? = null,
    categoryBadge: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    SettingsRow(
        icon = icon, title = title, subtitle = subtitle, onClick = { expanded = true },
        categoryBadge = categoryBadge,
        trailing = {
            Box {
                Text(
                    value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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



/**
 * Integrated Quick Reply Input Pill:
 * Height 52dp, corner 28dp, fill surfaceContainer, hairline border, placeholder text,
 * trailing 40dp circular "+" button with spring scale and 90° rotation on tap.
 */
@Composable
internal fun QuickReplyInputPill(
    onAdd: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    val isEnabled = text.isNotBlank()
    val scale = animateFloatAsState(
        targetValue = if (isEnabled) 1.0f else 0.9f,
        animationSpec = Motion.screenSpring(),
        label = "pill-button-scale",
    )
    val rotation = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (text.isEmpty()) {
                    Text(
                        text = stringResource(R.string.settings_new_quick_reply),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("quick_reply_input_field"),
                )
            }

            Spacer(Modifier.width(8.dp))

            Box(
                modifier = Modifier
                    .testTag("quick_reply_add_button")
                    .size(40.dp)
                    .graphicsLayer {
                        scaleX = scale.value
                        scaleY = scale.value
                    }
                    .clip(CircleShape)
                    .background(
                        if (isEnabled) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                    )
                    .clickable(
                        enabled = isEnabled,
                        onClick = {
                            val toAdd = text.trim()
                            if (toAdd.isNotEmpty()) {
                                scope.launch {
                                    if (Motion.animationsEnabled()) {
                                        rotation.animateTo(
                                            targetValue = rotation.value + 90f,
                                            animationSpec = Motion.screenSpring(),
                                        )
                                    } else {
                                        rotation.snapTo(rotation.value + 90f)
                                    }
                                }
                                onAdd(toAdd)
                                text = ""
                            }
                        },
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = stringResource(R.string.settings_add_template),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .size(20.dp)
                        .graphicsLayer {
                            rotationZ = rotation.value
                        },
                )
            }
        }
    }
}
