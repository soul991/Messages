package com.messages.app.ui.settings

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class SettingsIconTileContrastTest {

    private fun relativeLuminance(color: Color): Double {
        fun channel(c: Float): Double {
            return if (c <= 0.03928f) {
                c / 12.92
            } else {
                ((c + 0.055) / 1.055).toDouble().pow(2.4)
            }
        }
        val r = channel(color.red)
        val g = channel(color.green)
        val b = channel(color.blue)
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun composite(fg: Color, bg: Color, alpha: Float): Color {
        val r = alpha * fg.red + (1f - alpha) * bg.red
        val g = alpha * fg.green + (1f - alpha) * bg.green
        val b = alpha * fg.blue + (1f - alpha) * bg.blue
        return Color(r, g, b)
    }

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun `light mode settings icon tiles meet 4_5 to 1 against 14 percent container`() {
        val whiteCard = Color(0xFFFFFFFF)
        val containerTint = 0.14f

        val lightColors = listOf(
            "Green" to SettingsTileColors.GreenLight,
            "Fraud/Red" to SettingsTileColors.RedLight,
            "Promo/Amber" to SettingsTileColors.AmberLight,
            "Review/Blue" to SettingsTileColors.BlueLight,
        )

        for ((name, color) in lightColors) {
            val container = composite(color, whiteCard, containerTint)
            val contrast = contrastRatio(color, container)
            assertTrue(
                "Light mode $name must meet WCAG AA 4.5:1 against 14% container (was $contrast)",
                contrast >= 4.5,
            )
        }
    }

    @Test
    fun `dark mode settings icon tiles meet 4_5 to 1 against 24 percent container`() {
        val darkCard = Color(0xFF121214)
        val containerTint = 0.24f

        val darkColors = listOf(
            "Green" to SettingsTileColors.GreenDark,
            "Fraud/Red" to SettingsTileColors.RedDark,
            "Promo/Amber" to SettingsTileColors.AmberDark,
            "Review/Blue" to SettingsTileColors.BlueDark,
        )

        for ((name, color) in darkColors) {
            val container = composite(color, darkCard, containerTint)
            val contrast = contrastRatio(color, container)
            assertTrue(
                "Dark mode $name must meet WCAG AA 4.5:1 against 24% container (was $contrast)",
                contrast >= 4.5,
            )
        }
    }
}
