package com.messages.designsystem

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/**
 * Top bar glass container (Phase 5).
 * - Blur behind on Android 12+ (API 31+) RenderEffect blur radius 20dp
 * - Translucent background: rgba(0,0,0,0.72) dark, rgba(247,247,244,0.80) light
 * - 1px hairline border at the bottom: rgba(255,255,255,0.09) dark, rgba(0,0,0,0.07) light
 * - Child icons, titles and actions remain crisp and unblurred on top.
 */
@Composable
fun GlassTopBar(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val bg = if (isDark) DesignTokens.Dark.TopBar else DesignTokens.Light.TopBar
    val border = if (isDark) DesignTokens.Dark.TopBarBorder else DesignTokens.Light.TopBarBorder

    Box(modifier = modifier.fillMaxWidth()) {
        // Background glass layer with RenderEffect blur on API 31+
        Box(
            modifier = Modifier
                .matchParentSize()
                .then(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        Modifier.graphicsLayer {
                            val blurPx = 20.dp.toPx()
                            renderEffect = RenderEffect
                                .createBlurEffect(blurPx, blurPx, Shader.TileMode.CLAMP)
                                .asComposeRenderEffect()
                        }
                    } else Modifier
                )
                .background(bg)
        )

        // Content layer with 1px hairline bottom border
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    val strokeWidth = 1.dp.toPx()
                    drawLine(
                        color = border,
                        start = Offset(0f, size.height - strokeWidth / 2f),
                        end = Offset(size.width, size.height - strokeWidth / 2f),
                        strokeWidth = strokeWidth,
                    )
                }
        ) {
            content()
        }
    }
}
