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
 * Top bar container: clean, non-scrimming top bar matching the surface background.
 * Removes dark gradient bands, tints and hairline dividers under the header (G4),
 * ensuring the header blends seamlessly into the AMOLED black or off-white background.
 */
@Composable
fun GlassTopBar(
    isDark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clipToBounds(),
    ) {
        content()
    }
}
