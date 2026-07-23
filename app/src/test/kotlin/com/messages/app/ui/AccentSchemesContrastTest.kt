package com.messages.app.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.messages.designsystem.AccentSeed
import com.messages.designsystem.accentTone
import com.messages.designsystem.schemeForSeed
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 5 §4 gate: every curated accent seed must produce WCAG-AA text pairs
 * in BOTH light and dark schemes — the palette ships only because these hold.
 */
class AccentSchemesContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val la = a.luminance() + 0.05
        val lb = b.luminance() + 0.05
        return maxOf(la, lb) / minOf(la, lb).toDouble()
    }

    private fun assertAa(seed: AccentSeed, dark: Boolean, name: String, fg: Color, bg: Color) {
        val ratio = contrast(fg, bg)
        assertTrue(
            "$seed ${if (dark) "dark" else "light"} $name: $ratio < 4.5",
            ratio >= 4.5,
        )
    }

    private fun checkScheme(seed: AccentSeed, dark: Boolean, s: ColorScheme) {
        assertAa(seed, dark, "onPrimary/primary", s.onPrimary, s.primary)
        assertAa(seed, dark, "onPrimaryContainer/primaryContainer", s.onPrimaryContainer, s.primaryContainer)
        assertAa(seed, dark, "onSecondary/secondary", s.onSecondary, s.secondary)
        assertAa(seed, dark, "onSecondaryContainer/secondaryContainer", s.onSecondaryContainer, s.secondaryContainer)
        assertAa(seed, dark, "onTertiary/tertiary", s.onTertiary, s.tertiary)
        assertAa(seed, dark, "onTertiaryContainer/tertiaryContainer", s.onTertiaryContainer, s.tertiaryContainer)
        assertAa(seed, dark, "onSurface/surface", s.onSurface, s.surface)
        // primary is used as text/accent on surface throughout settings rows.
        assertAa(seed, dark, "primary/surface", s.primary, s.surface)
    }

    @Test
    fun `every seed is AA in light and dark`() {
        AccentSeed.values().filter { it != AccentSeed.DYNAMIC }.forEach { seed ->
            checkScheme(seed, dark = false, s = schemeForSeed(seed, dark = false))
            checkScheme(seed, dark = true, s = schemeForSeed(seed, dark = true))
        }
    }

    @Test
    fun `tone generator hits the requested lightness`() {
        // Tone == L* is the whole AA argument; verify the generator is honest
        // across hues, including out-of-gamut chroma requests that must
        // desaturate rather than shift tone.
        listOf(0.0, 42.0, 135.0, 262.0, 310.0).forEach { hue ->
            listOf(10.0, 40.0, 80.0, 90.0).forEach { tone ->
                val c = accentTone(hue, 60.0, tone)
                // Convert luminance back to L*.
                val y = c.luminance().toDouble()
                val l = if (y > 0.008856) 116.0 * Math.cbrt(y) - 16.0 else 903.3 * y
                assertTrue(
                    "hue=$hue tone=$tone landed at L*=$l",
                    Math.abs(l - tone) < 2.0,
                )
            }
        }
    }

    @Test
    fun `blue seed reproduces the pre-accent brand scheme family`() {
        // BLUE is documented as the static-scheme hue; it must stay in the
        // same tonal family so existing screenshots/branding don't shift.
        val light = schemeForSeed(AccentSeed.BLUE, dark = false)
        assertTrue("blue light primary should be a mid-tone blue", light.primary.luminance() < 0.25)
        val dark = schemeForSeed(AccentSeed.BLUE, dark = true)
        assertTrue("blue dark primary should be a light blue", dark.primary.luminance() > 0.4)
    }
}
