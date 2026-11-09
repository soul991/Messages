package com.messages.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.messages.designsystem.DesignTokens
import com.messages.designsystem.dockBackground
import com.messages.designsystem.topBarBackground
import org.junit.Assert.assertTrue
import org.junit.Test

class GlassTokensTest {

    @Test
    fun `dark dock background alpha is at least 72 percent`() {
        val darkAlpha = DesignTokens.Dark.Dock.alpha
        assertTrue(
            "Dark dock background alpha must be >= 0.72f to prevent text readability underneath, got $darkAlpha",
            darkAlpha >= 0.72f,
        )
    }

    @Test
    fun `light dock background alpha is at least 72 percent`() {
        val lightAlpha = DesignTokens.Light.Dock.alpha
        assertTrue(
            "Light dock background alpha must be >= 0.72f, got $lightAlpha",
            lightAlpha >= 0.72f,
        )
    }

    @Test
    fun `dark top bar background alpha is at least 72 percent`() {
        val darkTopBarAlpha = DesignTokens.Dark.TopBar.alpha
        assertTrue(
            "Dark top bar background alpha must be >= 0.72f, got $darkTopBarAlpha",
            darkTopBarAlpha >= 0.72f,
        )
    }
}
