package com.messages.designsystem

import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

/**
 * Directional Liquid Glass specular rim brush: brighter catch-light along the
 * top edge, tapering smoothly toward the bottom like macOS Liquid Glass.
 */
fun liquidGlassRimBrush(isDark: Boolean): Brush = Brush.verticalGradient(
    colors = if (isDark) {
        listOf(
            DesignTokens.LiquidGlass.Dark.RimTop,
            DesignTokens.LiquidGlass.Dark.RimMid,
            DesignTokens.LiquidGlass.Dark.RimBottom,
        )
    } else {
        listOf(
            DesignTokens.LiquidGlass.Light.RimTop,
            DesignTokens.LiquidGlass.Light.RimMid,
            DesignTokens.LiquidGlass.Light.RimBottom,
        )
    },
)

/**
 * Subtle top-to-bottom specular glass sheen gradient layered over translucent surfaces.
 */
fun liquidGlassSheenBrush(isDark: Boolean): Brush = Brush.verticalGradient(
    colors = if (isDark) {
        listOf(
            DesignTokens.LiquidGlass.Dark.SheenTop,
            Color.Transparent,
            DesignTokens.LiquidGlass.Dark.SheenBottom,
        )
    } else {
        listOf(
            DesignTokens.LiquidGlass.Light.SheenTop,
            Color.Transparent,
            DesignTokens.LiquidGlass.Light.SheenBottom,
        )
    },
)

/**
 * Top bar container: clean top bar with real backdrop blur (Haze) when content
 * scrolls behind it, matching surface background and blending cleanly into AMOLED black.
 */
@Composable
fun GlassTopBar(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    content: @Composable () -> Unit,
) {
    val topBarHazeStyle = remember(isDark) {
        val tint = if (isDark) Color(0xCC101012) else Color(0xD9F7F7F4)
        val fallback = if (isDark) Color(0xF0101012) else Color(0xF5F7F7F4)
        HazeStyle(
            backgroundColor = tint,
            tints = listOf(HazeTint(tint)),
            blurRadius = DesignTokens.LiquidGlass.BlurRadius,
            noiseFactor = 0f,
            fallbackTint = HazeTint(fallback),
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds()
            .then(
                if (hazeState != null) {
                    Modifier.hazeChild(
                        state = hazeState,
                        style = topBarHazeStyle,
                    )
                } else Modifier,
            ),
    ) {
        content()
    }
}

/**
 * Reusable macOS-inspired Liquid Glass surface supporting both live Haze backdrop blur
 * and standalone modal/floating glass cards with specular rim lighting and subtle sheen.
 */
@Composable
fun LiquidGlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    hazeState: HazeState? = null,
    isDark: Boolean = LocalDarkTheme.current,
    tintColor: Color = if (isDark) DesignTokens.LiquidGlass.Dark.PillContainer else DesignTokens.LiquidGlass.Light.PillContainer,
    fallbackColor: Color = if (isDark) DesignTokens.LiquidGlass.Dark.PillFallback else DesignTokens.LiquidGlass.Light.PillFallback,
    borderWidth: Dp = 1.dp,
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    val hazeStyle = remember(isDark, tintColor, fallbackColor) {
        HazeStyle(
            backgroundColor = tintColor,
            tints = listOf(HazeTint(tintColor)),
            blurRadius = DesignTokens.LiquidGlass.BlurRadius,
            noiseFactor = 0f,
            fallbackTint = HazeTint(fallbackColor),
        )
    }
    val rimBrush = remember(isDark) { liquidGlassRimBrush(isDark) }
    val sheenBrush = remember(isDark) { liquidGlassSheenBrush(isDark) }

    Box(
        modifier = modifier
            .then(
                if (shadowElevation > 0.dp) {
                    Modifier.shadow(
                        elevation = shadowElevation,
                        shape = shape,
                        ambientColor = Color.Black.copy(alpha = if (isDark) 0.55f else 0.16f),
                        spotColor = Color.Black.copy(alpha = if (isDark) 0.55f else 0.16f),
                    )
                } else Modifier,
            )
            .clip(shape)
            .then(
                if (hazeState != null) {
                    Modifier.hazeChild(state = hazeState, style = hazeStyle)
                } else {
                    Modifier.background(
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) tintColor else fallbackColor,
                        shape,
                    )
                },
            )
            .background(sheenBrush, shape)
            .border(BorderStroke(borderWidth, rimBrush), shape),
    ) {
        content()
    }
}

/**
 * Circular Liquid Glass icon button: 48dp accessible touch target with a 40dp
 * floating glass visual disc, specular rim light, and spring press scale.
 */
@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    hazeState: HazeState? = null,
    visualSize: Dp = 40.dp,
    content: @Composable () -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scaleState = animateFloatAsState(
        targetValue = if (pressed) 0.90f else 1f,
        animationSpec = Motion.butterSpring(),
        label = "liquid-glass-btn-scale",
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = visualSize / 2),
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        LiquidGlassSurface(
            modifier = Modifier
                .size(visualSize)
                .graphicsLayer {
                    val s = scaleState.value
                    scaleX = s
                    scaleY = s
                },
            shape = CircleShape,
            hazeState = hazeState,
            isDark = isDark,
        ) {
            Box(
                modifier = Modifier.size(visualSize),
                contentAlignment = Alignment.Center,
            ) {
                content()
            }
        }
    }
}

/**
 * macOS / WhatsApp-Mac style floating Liquid Glass action sheet dialog.
 *
 * Outer 32dp translucent glass shell with luminous rim border, optional header
 * (avatar + contact name), and floating inner grouped action cards (20dp radius)
 * inset by 12dp for concentric corners (32 - 12 = 20).
 */
@Composable
fun LiquidGlassSheetDialog(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    header: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val outerShape = remember { RoundedCornerShape(26.dp) }
    val sheetFill = if (isDark) DesignTokens.LiquidGlass.Dark.SheetContainer else DesignTokens.LiquidGlass.Light.SheetContainer
    val sheetFallback = if (isDark) Color(0xF5161719) else Color(0xFAF2F2F5)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val dialogView = LocalView.current
            SideEffect {
                (dialogView.parent as? DialogWindowProvider)?.window?.let { w ->
                    w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND)
                    w.attributes = w.attributes.apply {
                        blurBehindRadius = 48
                    }
                }
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp),
            contentAlignment = Alignment.Center,
        ) {
            LiquidGlassSurface(
                modifier = modifier
                    .fillMaxWidth()
                    .widthIn(max = 360.dp),
                shape = outerShape,
                isDark = isDark,
                tintColor = sheetFill,
                fallbackColor = sheetFallback,
                borderWidth = 1.dp,
                shadowElevation = 24.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (header != null) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 4.dp),
                        ) {
                            header()
                        }
                    }
                    content()
                }
            }
        }
    }
}

/**
 * Floating inner rounded card inside [LiquidGlassSheetDialog] that groups related
 * actions (e.g. conversation controls in one card, destructive actions in another).
 */
@Composable
fun LiquidGlassActionGroup(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val isDark = LocalDarkTheme.current
    val groupShape = remember { RoundedCornerShape(18.dp) }
    val groupBg = if (isDark) DesignTokens.LiquidGlass.Dark.SheetGroup else DesignTokens.LiquidGlass.Light.SheetGroup
    val groupBorder = if (isDark) Color(0x18FFFFFF) else Color(0x0F000000)

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = groupShape,
        color = groupBg,
        border = BorderStroke(0.75.dp, groupBorder),
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            content()
        }
    }
}

/** Subtle inset hairline divider between rows inside a [LiquidGlassActionGroup]. */
@Composable
fun LiquidGlassActionDivider() {
    val isDark = LocalDarkTheme.current
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 16.dp),
        thickness = 0.5.dp,
        color = if (isDark) DesignTokens.LiquidGlass.Dark.Divider else DesignTokens.LiquidGlass.Light.Divider,
    )
}

/**
 * Action row inside a [LiquidGlassActionGroup]: crisp icon, Medium 16sp label,
 * >= 52dp height (accessible touch target), spring press scale, and optional
 * destructive semantic red styling.
 */
@Composable
fun LiquidGlassActionRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val isDark = LocalDarkTheme.current
    val contentColor = if (destructive) {
        if (isDark) DesignTokens.LiquidGlass.Dark.Destructive else DesignTokens.LiquidGlass.Light.Destructive
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scaleState = animateFloatAsState(
        targetValue = if (pressed) 0.97f else 1f,
        animationSpec = Motion.butterSpring(),
        label = "glass-action-row-scale",
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clickable(
                interactionSource = interactionSource,
                indication = ripple(color = contentColor.copy(alpha = 0.14f)),
                role = Role.Button,
                onClick = onClick,
            )
            .graphicsLayer {
                val s = scaleState.value
                scaleX = s
                scaleY = s
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = label,
                fontFamily = InterFontFamily,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    fontFamily = InterFontFamily,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}
