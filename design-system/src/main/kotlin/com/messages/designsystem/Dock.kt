package com.messages.designsystem

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Bottom navigation dock item.
 */
@Immutable
data class DockItem(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val unreadCount: Int = 0,
    val accentColor: Color? = null,
)

/**
 * Floating bottom navigation dock (solid surface, hairline border).
 */
@Composable
fun BottomDock(
    items: List<DockItem>,
    selectedKey: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(32.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(68.dp),
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEach { item ->
                val selected = item.key == selectedKey
                val animatedScale by animateFloatAsState(
                    targetValue = if (selected) 1.05f else 0.95f,
                    animationSpec = spring(dampingRatio = 0.86f, stiffness = 380f),
                    label = "dock-scale",
                )

                val primaryColor = MaterialTheme.colorScheme.primary
                val targetAccent = item.accentColor ?: primaryColor
                val iconColor by animateColorAsState(
                    targetValue = if (selected) targetAccent else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "dock-icon-color",
                )

                val pillBackground by animateColorAsState(
                    targetValue = if (selected) targetAccent.copy(alpha = 0.16f) else Color.Transparent,
                    label = "dock-pill-bg",
                )

                val pillBorder = if (selected) {
                    androidx.compose.ui.graphics.SolidColor(targetAccent.copy(alpha = 0.35f))
                } else {
                    androidx.compose.ui.graphics.SolidColor(Color.Transparent)
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .scale(animatedScale)
                        .clip(RoundedCornerShape(20.dp))
                        .background(pillBackground)
                        .border(1.dp, pillBorder, RoundedCornerShape(20.dp))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = ripple(bounded = true, color = targetAccent.copy(alpha = 0.25f)),
                            onClick = { onItemSelected(item.key) },
                        )
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.title,
                                tint = iconColor,
                                modifier = Modifier.size(24.dp),
                            )
                            if (item.unreadCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .offset(x = 6.dp, y = (-4).dp)
                                        .size(if (item.unreadCount > 99) 16.dp else 12.dp)
                                        .clip(CircleShape)
                                        .background(item.accentColor ?: MaterialTheme.colorScheme.error)
                                        .border(1.dp, Color.White.copy(alpha = 0.85f), CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (item.unreadCount > 9) {
                                        Text(
                                            text = if (item.unreadCount > 99) "99+" else item.unreadCount.toString(),
                                            color = Color.White,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = item.title,
                            color = iconColor,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
