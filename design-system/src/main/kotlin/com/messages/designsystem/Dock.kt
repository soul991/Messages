package com.messages.designsystem

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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
 * Bottom Dock styled as a floating liquid-glass capsule (Phase 5 / Orchid Refresh).
 *
 * Floating capsule: 12dp side margin, 8dp above navigation bar inset, 60dp height,
 * full-round capsule shape, surfaceContainer at 0.92 alpha with 0.5dp outlineVariant hairline,
 * and 6dp soft shadow elevation.
 *
 * Indicator is ONE composable (GlassIndicator) driven by Animatable<Float> position and lift,
 * with critically damped glide, lift spring dynamics, drag tracking, and reduced motion fallback.
 */
@Composable
fun BottomDock(
    items: List<DockItem>,
    selectedKey: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkTheme.current
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f),
            shadowElevation = 6.dp,
            border = BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                val totalWidthPx = constraints.maxWidth.toFloat()
                val density = LocalDensity.current
                val itemCount = items.size.coerceAtLeast(1)
                val tabWidthPx = totalWidthPx / itemCount
                val tabWidthDp = with(density) { tabWidthPx.toDp() }
                val selectedIndex = items.indexOfFirst { it.key == selectedKey }.coerceAtLeast(0)

                val xAnim = remember { Animatable(selectedIndex * tabWidthPx) }
                val liftAnim = remember { Animatable(0f) }
                var glideJob by remember { mutableStateOf<Job?>(null) }
                var isDragging by remember { mutableStateOf(false) }
                var dragTabIndex by remember { mutableIntStateOf(selectedIndex) }

                // Synchronize target position when selectedKey changes externally
                LaunchedEffect(selectedIndex, tabWidthPx) {
                    if (!isDragging) {
                        val targetPx = selectedIndex * tabWidthPx
                        if (kotlin.math.abs(xAnim.value - targetPx) > 1f) {
                            glideJob?.cancel()
                            glideJob = launch {
                                if (Motion.animationsEnabled()) {
                                    launch {
                                        liftAnim.animateTo(1f, spring(dampingRatio = 1f, stiffness = 420f))
                                    }
                                    xAnim.animateTo(targetPx, spring(dampingRatio = 1f, stiffness = 290f))
                                    liftAnim.animateTo(0f, spring(dampingRatio = 1f, stiffness = 210f))
                                } else {
                                    xAnim.snapTo(targetPx)
                                    liftAnim.snapTo(0f)
                                }
                            }
                        }
                    }
                }

                // Reusable Liquid-Glass Indicator (0 recompositions per frame)
                GlassIndicator(
                    xAnim = xAnim,
                    liftAnim = liftAnim,
                    widthDp = tabWidthDp,
                )

                // Tab slots with drag gesture detector
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(itemCount, tabWidthPx) {
                            detectHorizontalDragGestures(
                                onDragStart = { offset ->
                                    isDragging = true
                                    dragTabIndex = (offset.x / tabWidthPx).toInt().coerceIn(0, itemCount - 1)
                                    glideJob?.cancel()
                                    coroutineScope.launch {
                                        if (Motion.animationsEnabled()) {
                                            liftAnim.animateTo(1f, spring(dampingRatio = 1f, stiffness = 420f))
                                        } else {
                                            liftAnim.snapTo(1f)
                                        }
                                    }
                                },
                                onHorizontalDrag = { change, dragAmount ->
                                    change.consume()
                                    coroutineScope.launch {
                                        val newX = (xAnim.value + dragAmount).coerceIn(0f, (itemCount - 1) * tabWidthPx)
                                        xAnim.snapTo(newX)
                                        val nearest = (newX / tabWidthPx).roundToInt().coerceIn(0, itemCount - 1)
                                        if (nearest != dragTabIndex) {
                                            dragTabIndex = nearest
                                            Haptics.tick(view)
                                        }
                                    }
                                },
                                onDragEnd = {
                                    isDragging = false
                                    val finalIndex = dragTabIndex
                                    onItemSelected(items[finalIndex].key)
                                    glideJob?.cancel()
                                    glideJob = coroutineScope.launch {
                                        val targetPx = finalIndex * tabWidthPx
                                        if (Motion.animationsEnabled()) {
                                            xAnim.animateTo(targetPx, spring(dampingRatio = 1f, stiffness = 290f))
                                            liftAnim.animateTo(0f, spring(dampingRatio = 1f, stiffness = 210f))
                                        } else {
                                            xAnim.snapTo(targetPx)
                                            liftAnim.snapTo(0f)
                                        }
                                    }
                                },
                                onDragCancel = {
                                    isDragging = false
                                    glideJob?.cancel()
                                    glideJob = coroutineScope.launch {
                                        val targetPx = selectedIndex * tabWidthPx
                                        if (Motion.animationsEnabled()) {
                                            xAnim.animateTo(targetPx, spring(dampingRatio = 1f, stiffness = 290f))
                                            liftAnim.animateTo(0f, spring(dampingRatio = 1f, stiffness = 210f))
                                        } else {
                                            xAnim.snapTo(targetPx)
                                            liftAnim.snapTo(0f)
                                        }
                                    }
                                },
                            )
                        },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items.forEachIndexed { index, item ->
                        val selected = index == selectedIndex
                        val iconScale by animateFloatAsState(
                            targetValue = if (selected) 1.0f else 0.92f,
                            animationSpec = Motion.spatialFast(),
                            label = "dock-icon-scale",
                        )

                        Box(
                            modifier = Modifier
                                .testTag("dock_${item.key}")
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = ripple(
                                        bounded = true,
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                                    ),
                                    onClick = {
                                        if (!selected) {
                                            onItemSelected(item.key)
                                            glideJob?.cancel()
                                            glideJob = coroutineScope.launch {
                                                val targetPx = index * tabWidthPx
                                                if (Motion.animationsEnabled()) {
                                                    launch {
                                                        liftAnim.animateTo(1f, spring(dampingRatio = 1f, stiffness = 420f))
                                                    }
                                                    xAnim.animateTo(targetPx, spring(dampingRatio = 1f, stiffness = 290f))
                                                    liftAnim.animateTo(0f, spring(dampingRatio = 1f, stiffness = 210f))
                                                } else {
                                                    xAnim.snapTo(targetPx)
                                                    liftAnim.snapTo(0f)
                                                }
                                            }
                                        }
                                    },
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
                                        animationSpec = Motion.effectsDefault(),
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
                                                    scaleX = iconScale
                                                    scaleY = iconScale
                                                },
                                        )
                                    }

                                    // Notification Badge (Coral tertiary spark)
                                    if (item.unreadCount > 0) {
                                        val badgeBg = MaterialTheme.colorScheme.tertiary
                                        val badgeText = MaterialTheme.colorScheme.onTertiary
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
                                            MaterialTheme.colorScheme.tertiary
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
