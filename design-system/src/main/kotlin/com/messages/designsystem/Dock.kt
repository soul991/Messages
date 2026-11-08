package com.messages.designsystem

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

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
 * Bottom Dock styled as a floating pill (Tokens / Design Reference truth).
 *
 * Floating capsule: 16dp side margins, 24dp from bottom (+ navigation bar insets),
 * 64dp height, 32dp corner radius (dockRadius), translucent dockBackground,
 * 1px dockBorder, and neutral dockActivePill on the active tab.
 */
@Composable
fun BottomDock(
    items: List<DockItem>,
    selectedKey: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkTheme.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.dockBackground,
            shadowElevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.dockBorder),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                items.forEach { item ->
                    val selected = item.key == selectedKey
                    val iconColor = dockIconColor(item.key, isDark)
                    val labelColor = if (selected) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.textMuted
                    }
                    val pillBg = if (selected) MaterialTheme.colorScheme.dockActivePill else Color.Transparent

                    Box(
                        modifier = Modifier
                            .testTag("dock_${item.key}")
                            .weight(1f)
                            .clip(RoundedCornerShape(26.dp))
                            .background(pillBg)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = ripple(
                                    bounded = true,
                                    color = iconColor.copy(alpha = 0.15f),
                                ),
                                onClick = {
                                    if (!selected) {
                                        onItemSelected(item.key)
                                    }
                                },
                            )
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Icon(
                                imageVector = if (selected) item.filledIcon else item.outlinedIcon,
                                contentDescription = item.title,
                                tint = iconColor,
                                modifier = Modifier.size(22.dp),
                            )

                            Text(
                                text = item.title,
                                style = TextStyle(
                                    fontFamily = InterFontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    letterSpacing = 0.sp,
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
