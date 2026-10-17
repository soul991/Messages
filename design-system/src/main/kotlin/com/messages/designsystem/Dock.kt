package com.messages.designsystem

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Floating Navigation Dock Item.
 */
@Immutable
data class DockItem(
    val key: String,
    val title: String,
    val outlinedIcon: ImageVector,
    val filledIcon: ImageVector,
    val unreadCount: Int = 0,
    val category: String? = null,
    val showDot: Boolean = false,
) {
    // Backward compatibility constructors
    constructor(
        key: String,
        title: String,
        icon: ImageVector,
        unreadCount: Int = 0,
    ) : this(
        key = key,
        title = title,
        outlinedIcon = icon,
        filledIcon = icon,
        unreadCount = unreadCount,
        category = null,
        showDot = false,
    )

    constructor(
        key: String,
        title: String,
        outlinedIcon: ImageVector,
        filledIcon: ImageVector,
        unreadCount: Int,
        category: String?,
    ) : this(
        key = key,
        title = title,
        outlinedIcon = outlinedIcon,
        filledIcon = filledIcon,
        unreadCount = unreadCount,
        category = category,
        showDot = false,
    )
}

/**
 * Category dock (Personal, Bank, Offers, Review, Spam)
 * Floating pill: height 64dp, corner 32dp, horizontal margin 16dp, bottom margin 12dp + navigation-bar inset.
 * Fill surfaceContainerHigh (solid), 1dp outlineVariant at 50% alpha border, shadow elevation 8dp.
 */
@Composable
fun BottomDock(
    items: List<DockItem>,
    selectedKey: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
            .height(64.dp),
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 6.dp, vertical = 6.dp),
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val density = androidx.compose.ui.platform.LocalDensity.current
            val itemCount = items.size
            val selectedIndex = items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)

            // Layout math: active item has weight 1.75f, inactive items have weight 1.0f
            val totalWeight = (itemCount - 1) * 1.0f + 1.75f
            val inactiveWidthPx = totalWidthPx / totalWeight
            val activeWidthPx = inactiveWidthPx * 1.75f

            val targetLeftPx = selectedIndex * inactiveWidthPx
            val activeWidthDp = with(density) { activeWidthPx.toDp() }

            val animLeftPx = animateFloatAsState(
                targetValue = targetLeftPx,
                animationSpec = if (Motion.animationsEnabled()) spring(dampingRatio = 0.82f, stiffness = 380f) else snap(),
                label = "dock-pill-slide",
            )

            // Active sliding pill: BORDERLESS pill filled with primaryContainer, animated via offset lambda
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(animLeftPx.value.roundToInt(), 0) }
                    .width(activeWidthDp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )

            // Interactive Tab Slots
            Row(
                modifier = Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                items.forEachIndexed { index, item ->
                    val selected = index == selectedIndex
                    val weight = if (selected) 1.75f else 1.0f

                    val iconScale = animateFloatAsState(
                        targetValue = if (selected) 1.0f else 0.9f,
                        animationSpec = if (Motion.animationsEnabled()) spring(dampingRatio = 0.82f, stiffness = 380f) else snap(),
                        label = "dock-icon-scale",
                    )

                    Box(
                        modifier = Modifier
                            .testTag("dock_${item.key}")
                            .weight(weight)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(24.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(
                                    bounded = true,
                                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                ),
                                onClick = { onItemSelected(item.key) },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp),
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                Crossfade(
                                    targetState = selected,
                                    animationSpec = tween(durationMillis = 120),
                                    label = "dock-icon-crossfade",
                                ) { isSelected ->
                                    val icon = if (isSelected) item.filledIcon else item.outlinedIcon
                                    val tint = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = item.title,
                                        tint = tint,
                                        modifier = Modifier
                                            .size(24.dp)
                                            .graphicsLayer {
                                                scaleX = iconScale.value
                                                scaleY = iconScale.value
                                            },
                                    )
                                }

                                val ringColor = if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHigh
                                }

                                // Notification Badge
                                if (item.unreadCount > 0) {
                                    val palette = categoryPalette(item.category ?: item.key)
                                    val (badgeBg, badgeText) = when {
                                        palette != null -> palette.container to palette.onContainer
                                        item.category == "SPAM" || item.key == "SPAM" ->
                                            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                                        else ->
                                            MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
                                    }

                                    Box(
                                        modifier = Modifier
                                            .offset(x = 8.dp, y = (-4).dp)
                                            .height(16.dp)
                                            .widthIn(min = 16.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(badgeBg)
                                            .border(
                                                1.5.dp,
                                                ringColor,
                                                RoundedCornerShape(8.dp),
                                            )
                                            .padding(horizontal = 4.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                                            color = badgeText,
                                            style = MaterialTheme.typography.labelSmall.copy(
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                lineHeight = 10.sp,
                                            ),
                                            maxLines = 1,
                                        )
                                    }
                                } else if (item.showDot) {
                                    val palette = categoryPalette(item.category ?: item.key)
                                    val dotColor = palette?.tint
                                        ?: if (item.category == "SPAM" || item.key == "SPAM") {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }

                                    Box(
                                        modifier = Modifier
                                            .offset(x = 6.dp, y = (-2).dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                            .border(
                                                1.5.dp,
                                                ringColor,
                                                CircleShape,
                                            ),
                                    )
                                }
                            }

                            if (selected) {
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
