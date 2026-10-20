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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
 * Bottom Dock styled after Telegram's tab bar (Figma reference 0:3677 / 0:7424).
 * Bottom-docked bar, full width, 52dp content height + navigation bar inset,
 * subtle top hairline divider, 5 labeled tabs with active pill/tint.
 */
@Composable
fun BottomDock(
    items: List<DockItem>,
    selectedKey: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkTheme.current
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shadowElevation = 0.dp,
        border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding(),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                val totalWidthPx = constraints.maxWidth.toFloat()
                val density = androidx.compose.ui.platform.LocalDensity.current
                val itemCount = items.size.coerceAtLeast(1)
                val tabWidthPx = totalWidthPx / itemCount
                val selectedIndex = items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)

                val targetLeftPx = selectedIndex * tabWidthPx
                val tabWidthDp = with(density) { tabWidthPx.toDp() }

                val animLeftPx = animateFloatAsState(
                    targetValue = targetLeftPx,
                    animationSpec = if (Motion.animationsEnabled()) spring(dampingRatio = 0.82f, stiffness = 380f) else snap(),
                    label = "dock-pill-slide",
                )

                // Active tab tint indicator: subtle rounded pill behind the active tab (Figma active tab indicator)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(animLeftPx.value.roundToInt(), 0) }
                        .width(tabWidthDp)
                        .fillMaxHeight()
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (isDark) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            }
                        ),
                )

                // Tab slots
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        val selected = index == selectedIndex
                        val iconScale = animateFloatAsState(
                            targetValue = if (selected) 1.0f else 0.92f,
                            animationSpec = if (Motion.animationsEnabled()) spring(dampingRatio = 0.82f, stiffness = 380f) else snap(),
                            label = "dock-icon-scale",
                        )

                        Box(
                            modifier = Modifier
                                .testTag("dock_${item.key}")
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(
                                        bounded = true,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    ),
                                    onClick = { onItemSelected(item.key) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.fillMaxSize(),
                            ) {
                                Box(contentAlignment = Alignment.TopEnd) {
                                    Crossfade(
                                        targetState = selected,
                                        animationSpec = tween(durationMillis = 120),
                                        label = "dock-icon-crossfade",
                                    ) { isSelected ->
                                        val icon = if (isSelected) item.filledIcon else item.outlinedIcon
                                        val tint = if (isSelected) {
                                            MaterialTheme.colorScheme.primary
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

                                    // Notification Badge (Figma 0:3693 & 0:7441)
                                    if (item.unreadCount > 0) {
                                        val badgeBg = if (isDark) {
                                            MaterialTheme.colorScheme.surface
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        }
                                        val badgeText = if (isDark) {
                                            MaterialTheme.colorScheme.onSurface
                                        } else {
                                            MaterialTheme.colorScheme.onError
                                        }
                                        val ringColor = MaterialTheme.colorScheme.surfaceContainer

                                        Box(
                                            modifier = Modifier
                                                .offset(x = 10.dp, y = (-4).dp)
                                                .height(18.dp)
                                                .widthIn(min = 18.dp)
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
                                        val dotColor = if (item.category == "SPAM" || item.key == "SPAM") {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        }
                                        val ringColor = MaterialTheme.colorScheme.surfaceContainer

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

                                Spacer(Modifier.height(2.dp))

                                val labelColor = if (selected) {
                                    if (isDark) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }

                                Text(
                                    text = item.title,
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Medium,
                                        letterSpacing = 0.1.sp,
                                    ),
                                    color = labelColor,
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
