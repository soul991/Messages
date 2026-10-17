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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.messages.app.R
import com.messages.designsystem.Motion
import com.messages.designsystem.categoryPalette
import com.messages.designsystem.groupCard
import kotlinx.coroutines.launch

/** Section header: primary-colored titleSmall on the 20dp grid. */
@Composable
internal fun SettingsSectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
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

/**
 * Cascading card entrance animation for Settings pages (Phase 4):
 * 30ms stagger per card, capped at 6 cards (0..5), animated with screenSpring.
 */
@Composable
fun Modifier.cascadeEntrance(index: Int): Modifier {
    if (!Motion.animationsEnabled()) return this
    val clampedIndex = index.coerceIn(0, 5) // capped at 6 cards
    val delayMs = (clampedIndex * 30).toLong()
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

/**
 * Row scaffold every settings row builds on.
 * Left icons become squircle badges: 40dp, corner 12dp, fill categoryPalette(...).container,
 * glyph categoryPalette(...).onContainer (contrast >= 4.5:1).
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
    val palette = categoryPalette(categoryBadge)
    val (badgeBg, badgeFg) = if (palette != null) {
        palette.container to palette.onContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
    }

    Row(
        Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(enabled = enabled, onClick = onClick) else it }
            .defaultMinSize(minHeight = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(badgeBg.copy(alpha = alpha)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = badgeFg.copy(alpha = alpha),
                    modifier = Modifier.size(24.dp),
                )
            }
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
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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

/** Value-picker row: current value in primary at the trailing edge, dropdown on tap. */
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

/**
 * Swipe-to-reveal destructive row for Settings (Quick Replies).
 * Swiping left reveals errorContainer background with delete icon and label.
 * Spring-back uses Motion.butterSpring(), commit threshold 42% of row width.
 * GraphicsLayer translation and derivedStateOf for 60fps performance without recompositions.
 */
@Composable
internal fun SwipeToRevealRow(
    onDelete: () -> Unit,
    enabled: Boolean = true,
    deleteLabel: String = stringResource(R.string.action_delete),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val liveOffset = remember { mutableFloatStateOf(0f) }
    val returnOffset = remember { Animatable(0f) }
    var rowWidthPx by remember { mutableFloatStateOf(0f) }

    val willCommit by remember {
        derivedStateOf {
            rowWidthPx > 0f && (liveOffset.floatValue + returnOffset.value) < -rowWidthPx * 0.42f
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .onSizeChanged { rowWidthPx = it.width.toFloat() }
            .semantics {
                if (enabled) {
                    customActions = listOf(
                        CustomAccessibilityAction(label = deleteLabel) {
                            onDelete()
                            true
                        }
                    )
                }
            }
    ) {
        // Destructive action background revealed when swiping left
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.errorContainer)
                .padding(end = 20.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.graphicsLayer {
                    val progress = if (rowWidthPx > 0f) {
                        (-(liveOffset.floatValue + returnOffset.value) / (rowWidthPx * 0.42f)).coerceIn(0f, 1f)
                    } else 0f
                    alpha = progress
                    scaleX = 0.8f + 0.2f * progress
                    scaleY = 0.8f + 0.2f * progress
                }
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = deleteLabel,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Foreground content with left-swipe gesture
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.groupCard)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragStart = {
                            coroutineScope.launch { returnOffset.snapTo(0f) }
                        },
                        onDragEnd = {
                            if (willCommit) {
                                coroutineScope.launch {
                                    liveOffset.floatValue = 0f
                                    returnOffset.snapTo(0f)
                                    onDelete()
                                }
                            } else {
                                coroutineScope.launch {
                                    returnOffset.snapTo(liveOffset.floatValue)
                                    liveOffset.floatValue = 0f
                                    returnOffset.animateTo(0f, Motion.butterSpring())
                                }
                            }
                        },
                        onDragCancel = {
                            coroutineScope.launch {
                                returnOffset.snapTo(liveOffset.floatValue)
                                liveOffset.floatValue = 0f
                                returnOffset.animateTo(0f, Motion.butterSpring())
                            }
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            val minOffset = if (rowWidthPx > 0f) -rowWidthPx else -1000f
                            val newOffset = (liveOffset.floatValue + dragAmount).coerceIn(minOffset, 0f)
                            liveOffset.floatValue = newOffset
                        }
                    )
                }
                .graphicsLayer {
                    translationX = liveOffset.floatValue + returnOffset.value
                }
        ) {
            content()
        }
    }
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
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.width(8.dp))

            Box(
                modifier = Modifier
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
