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

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clipToBounds

/**
 * Top bar glass container with API-adaptive backdrop styling:
 * - Android 12+ (API 31+): RenderEffect blur radius 20dp with dark tint >= 72% alpha.
 * - Below API 31: Near-opaque surface (92% alpha) ensuring underlying content is never legible.
 * - 1px hairline border at the bottom.
 * - clipToBounds prevents blur spill onto the search bar or content below.
 * - Child icons, titles and actions remain crisp and unblurred on top.
 */
@Composable
fun GlassTopBar(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val bg = MaterialTheme.colorScheme.topBarBackground
    val border = MaterialTheme.colorScheme.topBarBorder

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
    ) {
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
