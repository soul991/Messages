package com.messages.app

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.messages.designsystem.DesignTokens
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Instrumented Accessibility Contrast Test (Item 5).
 *
 * Implements Google's Accessibility Test Framework (ATF) ColorContrastCheck algorithm
 * based on W3C WCAG 2.1 relative luminance standards:
 * - Normal body text (< 18pt or < 14pt bold): minimum 4.5:1
 * - Large text (>= 18pt or >= 14pt bold) and essential graphical icons: minimum 3.0:1
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityContrastInstrumentedTest {

    private fun channelLuminance(channel8Bit: Int): Double {
        val s = channel8Bit / 255.0
        return if (s <= 0.04045) s / 12.92 else ((s + 0.055) / 1.055).pow(2.4)
    }

    private fun relativeLuminance(colorArgb: Int): Double {
        val r = channelLuminance((colorArgb shr 16) and 0xFF)
        val g = channelLuminance((colorArgb shr 8) and 0xFF)
        val b = channelLuminance(colorArgb and 0xFF)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = relativeLuminance(fg.toArgb())
        val l2 = relativeLuminance(bg.toArgb())
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun testDarkModeBodyTextContrastCompliance() {
        val background = DesignTokens.Dark.Bg
        val textPrimary = DesignTokens.Dark.Text
        val textMuted = DesignTokens.Dark.TextMuted

        val primaryRatio = contrastRatio(textPrimary, background)
        val mutedRatio = contrastRatio(textMuted, background)

        println("Dark Mode Primary Text Contrast: ${String.format("%.2f", primaryRatio)}:1")
        println("Dark Mode Muted Text Contrast: ${String.format("%.2f", mutedRatio)}:1")

        assertTrue("Dark mode primary text must exceed 4.5:1 (got $primaryRatio)", primaryRatio >= 4.5)
        assertTrue("Dark mode muted text must exceed 4.5:1 (got $mutedRatio)", mutedRatio >= 4.5)
    }

    @Test
    fun testLightModeBodyTextContrastCompliance() {
        val background = DesignTokens.Light.Bg
        val textPrimary = DesignTokens.Light.Text
        val textMuted = DesignTokens.Light.TextMuted

        val primaryRatio = contrastRatio(textPrimary, background)
        val mutedRatio = contrastRatio(textMuted, background)

        println("Light Mode Primary Text Contrast: ${String.format("%.2f", primaryRatio)}:1")
        println("Light Mode Muted Text Contrast: ${String.format("%.2f", mutedRatio)}:1")

        assertTrue("Light mode primary text must exceed 4.5:1 (got $primaryRatio)", primaryRatio >= 4.5)
        assertTrue("Light mode muted text must exceed 4.5:1 (got $mutedRatio)", mutedRatio >= 4.5)
    }

    @Test
    fun testDockAndJumpToTopIconContrastCompliance() {
        // Dock glass background and neutral arrow icon
        val darkDockBg = Color(0xFF1C1C1F) // Fully resolved opaque equivalent
        val darkIcon = DesignTokens.Dark.Text // #F2F2F4
        val lightDockBg = Color(0xFFFFFFFF)
        val lightIcon = DesignTokens.Light.Text // #111113

        val darkIconRatio = contrastRatio(darkIcon, darkDockBg)
        val lightIconRatio = contrastRatio(lightIcon, lightDockBg)

        println("Dark Dock / JumpToTop Icon Contrast: ${String.format("%.2f", darkIconRatio)}:1")
        println("Light Dock / JumpToTop Icon Contrast: ${String.format("%.2f", lightIconRatio)}:1")

        assertTrue("Dark dock/jump-to-top icon must exceed 3.0:1 (got $darkIconRatio)", darkIconRatio >= 3.0)
        assertTrue("Light dock/jump-to-top icon must exceed 3.0:1 (got $lightIconRatio)", lightIconRatio >= 3.0)
    }

    @Test
    fun testTransientSnackbarAndDialogContrastCompliance() {
        val snackbarSurface = Color(0xFF1C1C1E)
        val snackbarText = Color.White
        val snackbarRatio = contrastRatio(snackbarText, snackbarSurface)

        println("Snackbar Text Contrast: ${String.format("%.2f", snackbarRatio)}:1")
        assertTrue("Snackbar text must exceed 4.5:1 (got $snackbarRatio)", snackbarRatio >= 4.5)

        // Summary card alert/debit text contrast against dark surface
        val alertText = DesignTokens.SummaryCards.Debit.Text
        val alertContainer = DesignTokens.Dark.Surface
        val alertRatio = contrastRatio(alertText, alertContainer)

        println("Dark Alert Text Contrast: ${String.format("%.2f", alertRatio)}:1")
        assertTrue("Alert text must exceed 4.5:1 (got $alertRatio)", alertRatio >= 4.5)
    }
}
