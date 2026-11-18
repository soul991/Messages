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
    fun `top bar has clean transparent background without dark shade or scrim`() {
        org.junit.Assert.assertEquals(
            "Dark top bar must be transparent to remove black bands under header (G4)",
            Color.Transparent,
            DesignTokens.Dark.TopBar,
        )
        org.junit.Assert.assertEquals(
            "Light top bar must be transparent to remove tinted bands under header (G4)",
            Color.Transparent,
            DesignTokens.Light.TopBar,
        )
        org.junit.Assert.assertEquals(
            "Dark top bar border must be transparent to remove hairline divider (G4)",
            Color.Transparent,
            DesignTokens.Dark.TopBarBorder,
        )
        org.junit.Assert.assertEquals(
            "Light top bar border must be transparent to remove hairline divider (G4)",
            Color.Transparent,
            DesignTokens.Light.TopBarBorder,
        )
    }

    @Test
    fun `liquid glass sheet containers maintain high readability alpha`() {
        assertTrue(
            "Dark Liquid Glass sheet alpha must be >= 0.85f",
            DesignTokens.LiquidGlass.Dark.SheetContainer.alpha >= 0.85f,
        )
        assertTrue(
            "Light Liquid Glass sheet alpha must be >= 0.85f",
            DesignTokens.LiquidGlass.Light.SheetContainer.alpha >= 0.85f,
        )
    }
}
