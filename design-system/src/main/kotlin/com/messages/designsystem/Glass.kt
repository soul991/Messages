package com.messages.designsystem

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeChild

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
        val tint = if (isDark) Color(0xD1121214) else Color(0xD9FFFFFF)
        val fallback = if (isDark) Color(0xF2121214) else Color(0xF5FFFFFF)
        HazeStyle(
            backgroundColor = tint,
            tints = listOf(HazeTint(tint)),
            blurRadius = 20.dp,
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
                } else Modifier
            ),
    ) {
        content()
    }
}
