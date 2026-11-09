package com.messages.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.messages.app.ui.settings.SettingsTileColors
import com.messages.designsystem.AppSwitchTokens
import com.messages.designsystem.DesignTokens
import com.messages.designsystem.FixedDarkColorScheme
import com.messages.designsystem.FixedLightColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.max
import kotlin.math.min

/**
 * Automated WCAG 2.1 AA Contrast Verification Suite (D5).
 * Validates text and essential UI component contrast across Home, Chat, Settings, and Protection
 * in both Light and Dark themes.
 * Standard text requires >= 4.5:1; large text and UI components require >= 3.0:1.
 */
class WcagContrastTestSuite {

    private fun contrastRatio(fg: Color, bg: Color): Float {
        val lum1 = fg.luminance()
        val lum2 = bg.luminance()
        val l1 = max(lum1, lum2)
        val l2 = min(lum1, lum2)
        return (l1 + 0.05f) / (l2 + 0.05f)
    }

    private fun compositeOver(fg: Color, bg: Color): Color {
        val a = fg.alpha
        val r = fg.red * a + bg.red * (1f - a)
        val g = fg.green * a + bg.green * (1f - a)
        val b = fg.blue * a + bg.blue * (1f - a)
        return Color(r, g, b, 1f)
    }

    // ==========================================
    // 1. HOME SCREEN CONTRAST
    // ==========================================

    @Test
    fun home_primaryText_meetsWcagAA() {
        val darkRatio = contrastRatio(FixedDarkColorScheme.onSurface, FixedDarkColorScheme.surface)
        val lightRatio = contrastRatio(FixedLightColorScheme.onSurface, FixedLightColorScheme.surface)

        assertTrue("Dark Home primary text on surface ($darkRatio:1) must be >= 4.5:1", darkRatio >= 4.5f)
        assertTrue("Light Home primary text on surface ($lightRatio:1) must be >= 4.5:1", lightRatio >= 4.5f)

        val darkBgRatio = contrastRatio(FixedDarkColorScheme.onBackground, FixedDarkColorScheme.background)
        val lightBgRatio = contrastRatio(FixedLightColorScheme.onBackground, FixedLightColorScheme.background)

        assertTrue("Dark Home primary text on background ($darkBgRatio:1) must be >= 4.5:1", darkBgRatio >= 4.5f)
        assertTrue("Light Home primary text on background ($lightBgRatio:1) must be >= 4.5:1", lightBgRatio >= 4.5f)
    }

    @Test
    fun home_snippetText_meetsWcagAA() {
        val darkRatio = contrastRatio(FixedDarkColorScheme.onSurfaceVariant, FixedDarkColorScheme.surface)
        val lightRatio = contrastRatio(FixedLightColorScheme.onSurfaceVariant, FixedLightColorScheme.surface)

        assertTrue("Dark Home snippet on surface ($darkRatio:1) must be >= 4.5:1", darkRatio >= 4.5f)
        assertTrue("Light Home snippet on surface ($lightRatio:1) must be >= 4.5:1", lightRatio >= 4.5f)
    }

    @Test
    fun home_unreadBadge_meetsWcagAA() {
        val darkBadgeText = DesignTokens.Dark.OnGreen
        val darkBadgeBg = DesignTokens.Dark.Green
        val darkRatio = contrastRatio(darkBadgeText, darkBadgeBg)

        val lightBadgeText = DesignTokens.Light.OnGreen
        val lightBadgeBg = DesignTokens.Light.Green
        val lightRatio = contrastRatio(lightBadgeText, lightBadgeBg)

        assertTrue("Dark unread badge text on green ($darkRatio:1) must be >= 4.5:1", darkRatio >= 4.5f)
        assertTrue("Light unread badge text on green ($lightRatio:1) must be >= 4.5:1", lightRatio >= 4.5f)
    }

    // ==========================================
    // 2. CHAT SCREEN CONTRAST
    // ==========================================

    @Test
    fun chat_sentBubbleText_meetsWcagAA() {
        val darkText = Color(0xFF04210E)
        val darkBubble = DesignTokens.Dark.Green
        val darkRatio = contrastRatio(darkText, darkBubble)

        val lightText = Color(0xFF04210E)
        val lightBubble = DesignTokens.Light.Green
        val lightRatio = contrastRatio(lightText, lightBubble)

        assertTrue("Dark sent bubble text ($darkRatio:1) must be >= 4.5:1", darkRatio >= 4.5f)
        assertTrue("Light sent bubble text ($lightRatio:1) must be >= 4.5:1", lightRatio >= 4.5f)
    }

    @Test
    fun chat_receivedBubbleText_meetsWcagAA() {
        val darkText = FixedDarkColorScheme.onSurface
        val darkBubble = FixedDarkColorScheme.surface
        val darkRatio = contrastRatio(darkText, darkBubble)

        val lightText = FixedLightColorScheme.onSurface
        val lightBubble = FixedLightColorScheme.surface
        val lightRatio = contrastRatio(lightText, lightBubble)

        assertTrue("Dark received bubble text ($darkRatio:1) must be >= 4.5:1", darkRatio >= 4.5f)
        assertTrue("Light received bubble text ($lightRatio:1) must be >= 4.5:1", lightRatio >= 4.5f)
    }

    // ==========================================
    // 3. SETTINGS SCREEN CONTRAST
    // ==========================================

    @Test
    fun settings_rowTexts_meetWcagAA() {
        val darkSurface = FixedDarkColorScheme.surface
        val lightSurface = FixedLightColorScheme.surface

        val darkTitleRatio = contrastRatio(FixedDarkColorScheme.onSurface, darkSurface)
        val lightTitleRatio = contrastRatio(FixedLightColorScheme.onSurface, lightSurface)
        assertTrue("Dark settings title ($darkTitleRatio:1) must be >= 4.5:1", darkTitleRatio >= 4.5f)
        assertTrue("Light settings title ($lightTitleRatio:1) must be >= 4.5:1", lightTitleRatio >= 4.5f)

        val darkSubtitleRatio = contrastRatio(FixedDarkColorScheme.onSurfaceVariant, darkSurface)
        val lightSubtitleRatio = contrastRatio(FixedLightColorScheme.onSurfaceVariant, lightSurface)
        assertTrue("Dark settings subtitle ($darkSubtitleRatio:1) must be >= 4.5:1", darkSubtitleRatio >= 4.5f)
        assertTrue("Light settings subtitle ($lightSubtitleRatio:1) must be >= 4.5:1", lightSubtitleRatio >= 4.5f)

        val darkHeaderRatio = contrastRatio(FixedDarkColorScheme.onSurfaceVariant, FixedDarkColorScheme.background)
        val lightHeaderRatio = contrastRatio(FixedLightColorScheme.onSurfaceVariant, FixedLightColorScheme.background)
        assertTrue("Dark section header ($darkHeaderRatio:1) must be >= 4.5:1", darkHeaderRatio >= 4.5f)
        assertTrue("Light section header ($lightHeaderRatio:1) must be >= 4.5:1", lightHeaderRatio >= 4.5f)
    }

    @Test
    fun settings_iconTiles_allGlyphsMeetWcagAA() {
        val darkCard = FixedDarkColorScheme.surface
        val lightCard = FixedLightColorScheme.surface

        val roles = listOf(
            "Green" to (SettingsTileColors.GreenDark to SettingsTileColors.GreenLight),
            "Red" to (SettingsTileColors.RedDark to SettingsTileColors.RedLight),
            "Amber" to (SettingsTileColors.AmberDark to SettingsTileColors.AmberLight),
            "Blue" to (SettingsTileColors.BlueDark to SettingsTileColors.BlueLight),
        )

        for ((role, pair) in roles) {
            val (darkColor, lightColor) = pair

            // Dark: 24% tint container over dark card
            val darkContainer = compositeOver(darkColor.copy(alpha = 0.24f), darkCard)
            val darkRatio = contrastRatio(darkColor, darkContainer)
            assertTrue("Dark $role icon tile glyph ($darkRatio:1) must be >= 4.5:1", darkRatio >= 4.5f)

            // Light: 14% tint container over light card
            val lightContainer = compositeOver(lightColor.copy(alpha = 0.14f), lightCard)
            val lightRatio = contrastRatio(lightColor, lightContainer)
            assertTrue("Light $role icon tile glyph ($lightRatio:1) must be >= 4.5:1", lightRatio >= 4.5f)
        }
    }

    @Test
    fun settings_switchOffState_isClearlyVisible() {
        val darkThumbToTrack = contrastRatio(AppSwitchTokens.DarkThumb, AppSwitchTokens.DarkTrack)
        val lightThumbToTrack = contrastRatio(AppSwitchTokens.LightThumb, AppSwitchTokens.LightTrack)

        assertTrue("Dark switch off thumb to track ($darkThumbToTrack:1) must be >= 3.0:1", darkThumbToTrack >= 3.0f)
        assertTrue("Light switch off thumb to track ($lightThumbToTrack:1) must be >= 3.0:1", lightThumbToTrack >= 3.0f)
    }

    // ==========================================
    // 4. PROTECTION DASHBOARD CONTRAST
    // ==========================================

    @Test
    fun protection_heroCard_meetsWcagAA() {
        val darkCard = FixedDarkColorScheme.surface
        val lightCard = FixedLightColorScheme.surface

        val darkCountRatio = contrastRatio(FixedDarkColorScheme.onSurface, darkCard)
        val lightCountRatio = contrastRatio(FixedLightColorScheme.onSurface, lightCard)

        assertTrue("Dark protection hero count ($darkCountRatio:1) must be >= 3.0:1 (large text)", darkCountRatio >= 3.0f)
        assertTrue("Light protection hero count ($lightCountRatio:1) must be >= 3.0:1 (large text)", lightCountRatio >= 3.0f)

        val darkSubtitleRatio = contrastRatio(FixedDarkColorScheme.onSurfaceVariant, darkCard)
        val lightSubtitleRatio = contrastRatio(FixedLightColorScheme.onSurfaceVariant, lightCard)

        assertTrue("Dark protection hero subtitle ($darkSubtitleRatio:1) must be >= 4.5:1", darkSubtitleRatio >= 4.5f)
        assertTrue("Light protection hero subtitle ($lightSubtitleRatio:1) must be >= 4.5:1", lightSubtitleRatio >= 4.5f)
    }

    @Test
    fun protection_statTiles_numbersMeetWcagAA() {
        val darkCard = FixedDarkColorScheme.surface
        val lightCard = FixedLightColorScheme.surface

        // Spam count
        val darkSpamRatio = contrastRatio(SettingsTileColors.RedDark, darkCard)
        val lightSpamRatio = contrastRatio(SettingsTileColors.RedLight, lightCard)
        assertTrue("Dark spam stat count ($darkSpamRatio:1) must be >= 4.5:1", darkSpamRatio >= 4.5f)
        assertTrue("Light spam stat count ($lightSpamRatio:1) must be >= 4.5:1", lightSpamRatio >= 4.5f)

        // Promos count
        val darkPromoRatio = contrastRatio(SettingsTileColors.AmberDark, darkCard)
        val lightPromoRatio = contrastRatio(SettingsTileColors.AmberLight, lightCard)
        assertTrue("Dark promo stat count ($darkPromoRatio:1) must be >= 4.5:1", darkPromoRatio >= 4.5f)
        assertTrue("Light promo stat count ($lightPromoRatio:1) must be >= 4.5:1", lightPromoRatio >= 4.5f)

        // Blocked count
        val darkBlockedRatio = contrastRatio(SettingsTileColors.BlueDark, darkCard)
        val lightBlockedRatio = contrastRatio(SettingsTileColors.BlueLight, lightCard)
        assertTrue("Dark blocked stat count ($darkBlockedRatio:1) must be >= 4.5:1", darkBlockedRatio >= 4.5f)
        assertTrue("Light blocked stat count ($lightBlockedRatio:1) must be >= 4.5:1", lightBlockedRatio >= 4.5f)
    }
}
