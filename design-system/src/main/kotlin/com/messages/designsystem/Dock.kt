package com.messages.designsystem

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.layout.wrapContentWidth
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
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild
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
 * Bottom Dock styled as a compact floating pill (Telegram-style).
 *
 * Compact floating capsule: centered, width wrapping its content,
 * 62dp height, 31dp corner radius, sits above the gesture bar,
 * true backdrop blur via Haze with >=72% dark tint and near-opaque fallback below API 31,
 * 1dp hairline border, subtle top highlight, and neutral dockActivePill on the active tab.
 */
@Composable
fun BottomDock(
    items: List<DockItem>,
    selectedKey: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
) {
    val isDark = LocalDarkTheme.current
    val dockHeight = 62.dp
    val dockCornerRadius = 31.dp
    val dockShape = RoundedCornerShape(dockCornerRadius)

    val tintColor = if (isDark) DesignTokens.Dark.Dock else DesignTokens.Light.Dock
    val fallbackColor = if (isDark) Color(0xF21C1C1F) else Color(0xF5FFFFFF)

    val dockHazeStyle = remember(isDark, tintColor, fallbackColor) {
        HazeStyle(
            backgroundColor = tintColor,
            tints = listOf(HazeTint(tintColor)),
            blurRadius = 22.dp,
            noiseFactor = 0f,
            fallbackTint = HazeTint(fallbackColor),
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        // Floating Dock Glass Container (62dp height, 31dp corner radius, wrap content width)
        Box(
            modifier = Modifier
                .wrapContentWidth()
                .height(dockHeight),
        ) {
            // Glass background layer with real backdrop blur (Haze) and near-opaque fallback
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clip(dockShape)
                    .then(
                        if (hazeState != null) {
                            Modifier.hazeChild(
                                state = hazeState,
                                style = dockHazeStyle,
                            )
                        } else {
                            Modifier.background(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) tintColor else fallbackColor
                            )
                        }
                    ),
            )

            // Crisp border, subtle top highlight & content layer
            Surface(
                modifier = Modifier
                    .wrapContentWidth()
                    .fillMaxHeight()
                    .drawWithContent {
                        drawContent()
                        // Subtle top highlight hairline across top curve
                        val highlightColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.35f)
                        drawLine(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    highlightColor,
                                    Color.Transparent,
                                ),
                                startX = 0f,
                                endX = size.width,
                            ),
                            start = Offset(x = 24.dp.toPx(), y = 1.dp.toPx()),
                            end = Offset(x = size.width - 24.dp.toPx(), y = 1.dp.toPx()),
                            strokeWidth = 1.dp.toPx(),
                        )
                    },
                shape = dockShape,
                color = Color.Transparent,
                shadowElevation = 0.dp,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.dockBorder),
            ) {
                Row(
                    modifier = Modifier
                        .wrapContentWidth()
                        .fillMaxHeight()
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    items.forEach { item ->
                        val selected = item.key == selectedKey
                        val iconColor = dockIconColor(item.key, isDark)
                        val labelColor = if (selected) {
                            MaterialTheme.colorScheme.onBackground
                        } else {
                            MaterialTheme.colorScheme.textMuted
                        }

                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val tabScale by animateFloatAsState(
                            targetValue = if (isPressed) 0.94f else 1f,
                            animationSpec = Motion.bouncySpring(),
                            label = "tab-scale-${item.key}",
                        )
                        val pillAlpha by animateFloatAsState(
                            targetValue = if (selected) 1f else 0f,
                            animationSpec = Motion.effectsDefault(),
                            label = "pill-alpha-${item.key}",
                        )

                        Box(
                            modifier = Modifier
                                .testTag("dock_${item.key}")
                                .widthIn(min = 66.dp)
                                .graphicsLayer {
                                    scaleX = tabScale
                                    scaleY = tabScale
                                }
                                .clip(RoundedCornerShape(26.dp))
                                .clickable(
                                    interactionSource = interactionSource,
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
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            // Neutral active pill with spring alpha fade
                            if (pillAlpha > 0.01f) {
                                Box(
                                    modifier = Modifier
                                        .matchParentSize()
                                        .graphicsLayer { alpha = pillAlpha }
                                        .background(MaterialTheme.colorScheme.dockActivePill, RoundedCornerShape(26.dp))
                                )
                            }

                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                            ) {
                                Icon(
                                    imageVector = if (selected) item.filledIcon else item.outlinedIcon,
                                    contentDescription = item.title,
                                    tint = iconColor,
                                    modifier = Modifier.size(26.dp),
                                )

                                Text(
                                    text = item.title,
                                    style = TextStyle(
                                        fontFamily = InterFontFamily,
                                        fontSize = 12.sp,
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
}
