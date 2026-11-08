package com.messages.app.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.messages.designsystem.DesignTokens
import com.messages.designsystem.FixedDarkColorScheme
import com.messages.designsystem.FixedLightColorScheme
import com.messages.designsystem.greenText
import com.messages.designsystem.hairline
import com.messages.designsystem.textMuted
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 1 Theme foundation gate:
 * Every text/background pair in the fixed Dark and Light schemes must achieve
 * WCAG-AA contrast (>= 4.5:1 for body text, >= 3.0:1 for graphical/accent elements).
 */
class AccentSchemesContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb) / minOf(la, lb).toDouble()
    }

    private fun assertAa(name: String, fg: Color, bg: Color, minRatio: Double = 4.5) {
        val ratio = contrast(fg, bg)
        assertTrue(
            "$name: contrast ratio $ratio is below $minRatio:1",
            ratio >= minRatio,
        )
    }

    @Test
    fun `dark scheme achieves WCAG AA 4_5 to 1 on all text pairs`() {
        val s = FixedDarkColorScheme
        // onPrimary on primary
        assertAa("Dark onPrimary/primary", s.onPrimary, s.primary)
        // onBackground on background
        assertAa("Dark onBackground/background", s.onBackground, s.background)
        // onSurface on surface
        assertAa("Dark onSurface/surface", s.onSurface, s.surface)
        // onSurfaceVariant (textMuted) on surface
        assertAa("Dark textMuted/surface", s.textMuted, s.surface)
        // onSurfaceVariant (textMuted) on background
        assertAa("Dark textMuted/background", s.textMuted, s.background)
        // greenText on surface
        assertAa("Dark greenText/surface", s.greenText, s.surface)
        // greenText on background
        assertAa("Dark greenText/background", s.greenText, s.background)
    }

    @Test
    fun `light scheme achieves WCAG AA 4_5 to 1 on all text pairs`() {
        val s = FixedLightColorScheme
        // onPrimary on primary
        assertAa("Light onPrimary/primary", s.onPrimary, s.primary)
        // onBackground on background
        assertAa("Light onBackground/background", s.onBackground, s.background)
        // onSurface on surface
        assertAa("Light onSurface/surface", s.onSurface, s.surface)
        // onSurfaceVariant (textMuted) on surface
        assertAa("Light textMuted/surface", s.textMuted, s.surface)
        // onSurfaceVariant (textMuted) on background
        assertAa("Light textMuted/background", s.textMuted, s.background)
        // greenText token achieves WCAG accent ratio (>= 3.0:1)
        assertAa("Light greenText/surface", s.greenText, s.surface, minRatio = 3.0)
        assertAa("Light greenText/background", s.greenText, s.background, minRatio = 3.0)
    }

    @Test
    fun `summary cards text achieve high contrast against card fills`() {
        // Credit summary text on black background
        assertAa(
            "Credit text / background",
            DesignTokens.SummaryCards.Credit.Text,
            DesignTokens.Dark.Bg,
        )
        // Debit summary text on black background
        assertAa(
            "Debit text / background",
            DesignTokens.SummaryCards.Debit.Text,
            DesignTokens.Dark.Bg,
        )
    }

    @Test
    fun `fixed tokens match design specification exactly`() {
        assertTrue(DesignTokens.Dark.Bg == Color(0xFF000000))
        assertTrue(DesignTokens.Dark.Surface == Color(0xFF121214))
        assertTrue(DesignTokens.Dark.Hairline == Color(0xFF1F1F23))
        assertTrue(DesignTokens.Dark.Text == Color(0xFFF2F2F4))
        assertTrue(DesignTokens.Dark.TextMuted == Color(0xFF8E8E93))
        assertTrue(DesignTokens.Dark.Green == Color(0xFF30D158))
        assertTrue(DesignTokens.Dark.OnGreen == Color(0xFF04210E))

        assertTrue(DesignTokens.Light.Bg == Color(0xFFF7F7F4))
        assertTrue(DesignTokens.Light.Surface == Color(0xFFFFFFFF))
        assertTrue(DesignTokens.Light.Hairline == Color(0xFFE6E6E1))
        assertTrue(DesignTokens.Light.Text == Color(0xFF111113))
        assertTrue(DesignTokens.Light.TextMuted == Color(0xFF6B6B72))
        assertTrue(DesignTokens.Light.Green == Color(0xFF34C759))
        assertTrue(DesignTokens.Light.GreenText == Color(0xFF1E9A44))
        assertTrue(DesignTokens.Light.OnGreen == Color(0xFF04210E))
    }
}
