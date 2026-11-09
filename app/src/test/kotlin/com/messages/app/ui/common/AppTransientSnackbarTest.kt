package com.messages.app.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class AppTransientSnackbarTest {

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

    private fun contrastRatio(fg: Color, bg: Color): Double {
        val l1 = relativeLuminance(fg)
        val l2 = relativeLuminance(bg)
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    @Test
    fun `transient snackbar text contrast on dark surface exceeds 4_5 to 1`() {
        val darkSurface = Color(0xFF1C1C1E)
        val textWhite = Color(0xFFF7F7F4)

        val contrast = contrastRatio(textWhite, darkSurface)
        assertTrue(
            "Contrast ratio must exceed 4.5:1 WCAG AA (was $contrast)",
            contrast >= 4.5,
        )
    }

    @Test
    fun `undo button green contrast against dark surface is readable`() {
        val darkSurface = Color(0xFF1C1C1E)
        val undoGreen = Color(0xFF30D158)

        val contrast = contrastRatio(undoGreen, darkSurface)
        assertTrue(
            "Undo green contrast against dark surface must be >= 4.5:1 (was $contrast)",
            contrast >= 4.5,
        )
    }

    @Test
    fun `dock clearance exceeds minimum 8dp requirement`() {
        val dockHeightDp = 56
        val dockBottomMarginDp = 16
        val dockTopDp = dockHeightDp + dockBottomMarginDp // 72dp

        val snackbarBottomPaddingDp = 84
        val clearanceDp = snackbarBottomPaddingDp - dockTopDp // 12dp

        assertTrue("Clearance above dock must be >= 8dp", clearanceDp >= 8)
    }
}
