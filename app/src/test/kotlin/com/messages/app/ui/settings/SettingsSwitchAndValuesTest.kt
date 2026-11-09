package com.messages.app.ui.settings

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.messages.designsystem.AppSwitchTokens
import com.messages.designsystem.FixedDarkColorScheme
import com.messages.designsystem.FixedLightColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

class SettingsSwitchAndValuesTest {

    private fun contrastRatio(fg: Color, bg: Color): Float {
        val lum1 = fg.luminance()
        val lum2 = bg.luminance()
        val l1 = max(lum1, lum2)
        val l2 = min(lum1, lum2)
        return (l1 + 0.05f) / (l2 + 0.05f)
    }

    @Test
    fun darkMode_offSwitch_trackAndThumbAreVisibleOnDarkSurface() {
        val cardSurface = Color(0xFF121214)
        val track = AppSwitchTokens.DarkTrack
        val thumb = AppSwitchTokens.DarkThumb
        val border = AppSwitchTokens.DarkBorder

        // Thumb against track must be clear (> 4.5:1)
        val thumbToTrackContrast = contrastRatio(thumb, track)
        assertTrue(
            "Dark off switch thumb must have high contrast on track, got $thumbToTrackContrast:1",
            thumbToTrackContrast >= 4.5f,
        )

        // Track and border must be distinct from dark card surface
        val borderToSurface = contrastRatio(border, cardSurface)
        assertTrue(
            "Dark off switch border must be distinct from surface, got $borderToSurface:1",
            borderToSurface >= 1.5f,
        )
    }

    @Test
    fun lightMode_offSwitch_trackAndThumbAreVisibleOnLightSurface() {
        val cardSurface = Color(0xFFFFFFFF)
        val track = AppSwitchTokens.LightTrack
        val thumb = AppSwitchTokens.LightThumb
        val border = AppSwitchTokens.LightBorder

        // Thumb against track must be distinct (> 3.0:1)
        val thumbToTrackContrast = contrastRatio(thumb, track)
        assertTrue(
            "Light off switch thumb must be distinct on track, got $thumbToTrackContrast:1",
            thumbToTrackContrast >= 3.0f,
        )

        // Border against light card surface must be clearly defined (> 3.0:1)
        val borderToSurface = contrastRatio(border, cardSurface)
        assertTrue(
            "Light off switch border must be distinct from surface, got $borderToSurface:1",
            borderToSurface >= 3.0f,
        )
    }

    @Test
    fun settingsSecondaryValues_useNeutralColorNotPrimaryGreen() {
        val darkSecondary = FixedDarkColorScheme.onSurfaceVariant
        val darkPrimary = FixedDarkColorScheme.primary

        val lightSecondary = FixedLightColorScheme.onSurfaceVariant
        val lightPrimary = FixedLightColorScheme.primary

        // Primary is green
        assertTrue("Dark primary should be green (green channel > red channel)", darkPrimary.green > darkPrimary.red)
        assertTrue("Light primary should be green (green channel > red channel)", lightPrimary.green > lightPrimary.red)

        // Secondary / onSurfaceVariant is neutral (channels are close)
        val darkDiff = kotlin.math.abs(darkSecondary.red - darkSecondary.green)
        val lightDiff = kotlin.math.abs(lightSecondary.red - lightSecondary.green)
        assertTrue("Dark onSurfaceVariant should be neutral grey", darkDiff < 0.1f)
        assertTrue("Light onSurfaceVariant should be neutral grey", lightDiff < 0.1f)
    }
}
