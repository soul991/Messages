package com.messages.designsystem

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Liquid Glass & Frosted Translucency primitives for the modern Messages UI.
 *
 * Provides layered depth, specular light refraction borders, and hardware-accelerated
 * backdrop blur on supported Android versions (API 31+).
 */

enum class GlassDepth {
    /** Subtle glass for list items, cards, and quiet containers. */
    LOW,
    /** Standard glass for floating navigation bars, bottom docks, and search bars. */
    MEDIUM,
    /** Elevated glass for dialogs, bottom sheets, and popovers. */
    HIGH,
    /** Obsidian / Vault deep glass for locked spaces and high-contrast surfaces. */
    OBSIDIAN,
}

@Immutable
data class GlassColors(
    val background: Color,
    val specularBorder: Brush,
    val borderWidth: Dp,
    val blurRadius: Dp,
    val shadowElevation: Dp,
    val glowColor: Color = Color.Transparent,
)

object GlassTokens {

    @Composable
    fun resolve(depth: GlassDepth = GlassDepth.MEDIUM): GlassColors {
        val dark = LocalDarkTheme.current
        val primary = MaterialTheme.colorScheme.primary

        return when (depth) {
            GlassDepth.LOW -> if (dark) {
                GlassColors(
                    background = Color(0x3820232E),
                    specularBorder = Brush.linearGradient(
                        0.0f to Color(0x45FFFFFF),
                        0.4f to Color(0x1CFFFFFF),
                        1.0f to Color(0x06FFFFFF),
                    ),
                    borderWidth = 1.dp,
                    blurRadius = 16.dp,
                    shadowElevation = 2.dp,
                )
            } else {
                GlassColors(
                    background = Color(0xB8FFFFFF),
                    specularBorder = Brush.linearGradient(
                        0.0f to Color(0xD0FFFFFF),
                        0.5f to Color(0x75FFFFFF),
                        1.0f to Color(0x18000000),
                    ),
                    borderWidth = 1.dp,
                    blurRadius = 16.dp,
                    shadowElevation = 3.dp,
                )
            }

            GlassDepth.MEDIUM -> if (dark) {
                GlassColors(
                    background = Color(0xEE161822),
                    specularBorder = Brush.linearGradient(
                        0.0f to Color(0x65FFFFFF),
                        0.4f to Color(0x28FFFFFF),
                        1.0f to Color(0x0A000000),
                    ),
                    borderWidth = 1.25.dp,
                    blurRadius = 24.dp,
                    shadowElevation = 8.dp,
                    glowColor = primary.copy(alpha = 0.12f),
                )
            } else {
                GlassColors(
                    background = Color(0xF2F4F7FC),
                    specularBorder = Brush.linearGradient(
                        0.0f to Color(0xFFFFFFFF),
                        0.4f to Color(0xB0FFFFFF),
                        1.0f to Color(0x22000000),
                    ),
                    borderWidth = 1.25.dp,
                    blurRadius = 24.dp,
                    shadowElevation = 8.dp,
                    glowColor = primary.copy(alpha = 0.10f),
                )
            }

            GlassDepth.HIGH -> if (dark) {
                GlassColors(
                    background = Color(0xF61A1D28),
                    specularBorder = Brush.linearGradient(
                        0.0f to Color(0x75FFFFFF),
                        0.5f to Color(0x35FFFFFF),
                        1.0f to Color(0x12000000),
                    ),
                    borderWidth = 1.5.dp,
                    blurRadius = 32.dp,
                    shadowElevation = 16.dp,
                    glowColor = primary.copy(alpha = 0.16f),
                )
            } else {
                GlassColors(
                    background = Color(0xFAFFFFFF),
                    specularBorder = Brush.linearGradient(
                        0.0f to Color(0xFFFFFFFF),
                        0.5f to Color(0xC5FFFFFF),
                        1.0f to Color(0x30000000),
                    ),
                    borderWidth = 1.5.dp,
                    blurRadius = 32.dp,
                    shadowElevation = 16.dp,
                    glowColor = primary.copy(alpha = 0.14f),
                )
            }

            GlassDepth.OBSIDIAN -> GlassColors(
                background = Color(0xF80D0F16),
                specularBorder = Brush.linearGradient(
                    0.0f to Color(0x55818CF8),
                    0.5f to Color(0x20818CF8),
                    1.0f to Color(0x05000000),
                ),
                borderWidth = 1.25.dp,
                blurRadius = 28.dp,
                shadowElevation = 12.dp,
                glowColor = Color(0x25818CF8),
            )
        }
    }
}

/**
 * Modifier extension applying a high-fidelity Liquid Glass surface with
 * optional hardware blur, specular border highlight, and elevation.
 */
fun Modifier.liquidGlass(
    shape: Shape = RoundedCornerShape(20.dp),
    depth: GlassDepth = GlassDepth.MEDIUM,
    enableBlur: Boolean = true,
): Modifier = this.then(
    Modifier
        .graphicsLayer {
            this.shape = shape
            this.clip = true
            if (enableBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val blurPx = (depth.ordinal * 8 + 16).dp.toPx()
                renderEffect = android.graphics.RenderEffect
                    .createBlurEffect(blurPx, blurPx, android.graphics.Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
            }
        }
)

/**
 * Premium container composable with Liquid Glass finish and optical specular sheen.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    depth: GlassDepth = GlassDepth.MEDIUM,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = GlassTokens.resolve(depth)
    val dark = LocalDarkTheme.current
    val interactionSource = remember { MutableInteractionSource() }

    // Specular inner sheen gradient (light catch from top edge)
    val sheenBrush = remember(dark) {
        Brush.verticalGradient(
            0.0f to (if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.35f)),
            0.28f to (if (dark) Color.White.copy(alpha = 0.02f) else Color.White.copy(alpha = 0.10f)),
            1.0f to Color.Transparent,
        )
    }

    val baseModifier = modifier
        .shadow(
            elevation = tokens.shadowElevation,
            shape = shape,
            clip = false,
            ambientColor = tokens.glowColor.takeIf { it != Color.Transparent } ?: Color.Black.copy(0.10f),
            spotColor = tokens.glowColor.takeIf { it != Color.Transparent } ?: Color.Black.copy(0.10f),
        )
        .clip(shape)
        .background(tokens.background, shape)
        .background(sheenBrush, shape)
        .border(tokens.borderWidth, tokens.specularBorder, shape)

    val clickableModifier = if (onClick != null) {
        baseModifier.clickable(
            interactionSource = interactionSource,
            indication = ripple(color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            onClick = onClick,
        )
    } else {
        baseModifier
    }

    Box(
        modifier = clickableModifier,
        content = content,
    )
}

/**
 * Floating Liquid Glass Bottom Navigation Dock Item.
 */
@Immutable
data class GlassDockItem(
    val key: String,
    val title: String,
    val icon: ImageVector,
    val unreadCount: Int = 0,
    val accentColor: Color? = null,
)

/**
 * Floating Liquid Glass Bottom Navigation Dock.
 */
@Composable
fun LiquidGlassBottomDock(
    items: List<GlassDockItem>,
    selectedKey: String,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(32.dp)

    LiquidGlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .height(68.dp),
        shape = shape,
        depth = GlassDepth.MEDIUM,
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
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
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
                    androidx.compose.ui.graphics.Brush.linearGradient(
                        listOf(
                            targetAccent.copy(alpha = 0.45f),
                            targetAccent.copy(alpha = 0.12f),
                        )
                    )
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
