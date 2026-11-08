package com.messages.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** True when the resolved theme is dark. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * Sent-bubble color role: outgoing message bubble container and onContainer.
 * In Dark mode: #30D158 / #04210E.
 * In Light mode: #34C759 / #04210E.
 */
@Immutable
data class SentBubbleColors(val container: Color, val onContainer: Color)

val LocalSentBubble = staticCompositionLocalOf {
    SentBubbleColors(Color.Unspecified, Color.Unspecified)
}

/**
 * Category palette: icons and badges matching dock icon tokens.
 */
@Immutable
data class CategoryPalette(val tint: Color, val container: Color, val onContainer: Color)

private val FraudLight = CategoryPalette(
    tint = DesignTokens.DockIconColors.Light.Spam,
    container = Color(0x24E5352B),
    onContainer = DesignTokens.DockIconColors.Light.Spam,
)
private val FraudDark = CategoryPalette(
    tint = DesignTokens.DockIconColors.Dark.Spam,
    container = Color(0x29FF453A),
    onContainer = DesignTokens.DockIconColors.Dark.Spam,
)
private val PromoLight = CategoryPalette(
    tint = DesignTokens.DockIconColors.Light.Promotional,
    container = Color(0x24C98A00),
    onContainer = DesignTokens.DockIconColors.Light.Promotional,
)
private val PromoDark = CategoryPalette(
    tint = DesignTokens.DockIconColors.Dark.Promotional,
    container = Color(0x29FFD60A),
    onContainer = DesignTokens.DockIconColors.Dark.Promotional,
)
private val ProtectedLight = CategoryPalette(
    tint = DesignTokens.DockIconColors.Light.Personal,
    container = Color(0x241E9A44),
    onContainer = DesignTokens.DockIconColors.Light.Personal,
)
private val ProtectedDark = CategoryPalette(
    tint = DesignTokens.DockIconColors.Dark.Personal,
    container = Color(0x2930D158),
    onContainer = DesignTokens.DockIconColors.Dark.Personal,
)
private val ReviewLight = CategoryPalette(
    tint = DesignTokens.DockIconColors.Light.Review,
    container = Color(0x24007AFF),
    onContainer = DesignTokens.DockIconColors.Light.Review,
)
private val ReviewDark = CategoryPalette(
    tint = DesignTokens.DockIconColors.Dark.Review,
    container = Color(0x290A84FF),
    onContainer = DesignTokens.DockIconColors.Dark.Review,
)

/** Theme-aware palette for a message/conversation category. */
@Composable
fun categoryPalette(category: String?): CategoryPalette? {
    val dark = LocalDarkTheme.current
    return when (category) {
        "SPAM", "BLOCKED" -> if (dark) FraudDark else FraudLight
        "PROMOTIONS", "OFFERS" -> if (dark) PromoDark else PromoLight
        "TRANSACTIONS", "PERSONAL", "INBOX" -> if (dark) ProtectedDark else ProtectedLight
        "REVIEW" -> if (dark) ReviewDark else ReviewLight
        else -> null
    }
}

// Static hues kept for non-composable callers (WhyFilteredScreen, widgets, notifications)
object CategoryColors {
    val Fraud = DesignTokens.DockIconColors.Dark.Spam
    val FraudContainer = Color(0x29FF453A)
    val Promo = DesignTokens.DockIconColors.Dark.Promotional
    val PromoContainer = Color(0x29FFD60A)
    val Protected = DesignTokens.DockIconColors.Dark.Personal
    val ProtectedContainer = Color(0x2930D158)
    val Review = DesignTokens.DockIconColors.Dark.Review
    val ReviewContainer = Color(0x290A84FF)
}

// Fixed Color Schemes mapping tokens/design-tokens.json
val FixedDarkColorScheme: ColorScheme = darkColorScheme(
    primary = DesignTokens.Dark.Green, // #30D158
    onPrimary = DesignTokens.Dark.OnGreen, // #04210E
    primaryContainer = DesignTokens.Dark.GreenTint16,
    onPrimaryContainer = DesignTokens.Dark.Green,
    inversePrimary = DesignTokens.Light.Green,
    surfaceTint = DesignTokens.Dark.Green,

    secondary = DesignTokens.Dark.TextMuted, // #8E8E93
    onSecondary = DesignTokens.Dark.Bg,
    secondaryContainer = DesignTokens.Dark.Surface,
    onSecondaryContainer = DesignTokens.Dark.Text,

    tertiary = DesignTokens.Dark.Green,
    onTertiary = DesignTokens.Dark.OnGreen,
    tertiaryContainer = DesignTokens.Dark.GreenTint16,
    onTertiaryContainer = DesignTokens.Dark.Green,

    background = DesignTokens.Dark.Bg, // #000000
    onBackground = DesignTokens.Dark.Text, // #F2F2F4
    surface = DesignTokens.Dark.Surface, // #121214
    onSurface = DesignTokens.Dark.Text, // #F2F2F4
    surfaceVariant = DesignTokens.Dark.Surface, // #121214
    onSurfaceVariant = DesignTokens.Dark.TextMuted, // #8E8E93
    outline = DesignTokens.Dark.Hairline, // #1F1F23
    outlineVariant = DesignTokens.Dark.Hairline, // #1F1F23

    surfaceContainerLowest = DesignTokens.Dark.Bg, // #000000
    surfaceContainerLow = DesignTokens.Dark.Surface, // #121214
    surfaceContainer = DesignTokens.Dark.Surface, // #121214
    surfaceContainerHigh = Color(0xFF1C1C1F),
    surfaceContainerHighest = Color(0xFF242428),
)

val FixedLightColorScheme: ColorScheme = lightColorScheme(
    primary = DesignTokens.Light.Green, // #34C759
    onPrimary = DesignTokens.Light.OnGreen, // #04210E
    primaryContainer = DesignTokens.Dark.GreenTint16,
    onPrimaryContainer = DesignTokens.Light.GreenText, // #1E9A44
    inversePrimary = DesignTokens.Dark.Green,
    surfaceTint = DesignTokens.Light.Green,

    secondary = DesignTokens.Light.TextMuted, // #6B6B72
    onSecondary = DesignTokens.Light.Surface,
    secondaryContainer = DesignTokens.Light.Surface,
    onSecondaryContainer = DesignTokens.Light.Text,

    tertiary = DesignTokens.Light.GreenText, // #1E9A44
    onTertiary = DesignTokens.Light.OnGreen,
    tertiaryContainer = DesignTokens.Dark.GreenTint16,
    onTertiaryContainer = DesignTokens.Light.GreenText,

    background = DesignTokens.Light.Bg, // #F7F7F4
    onBackground = DesignTokens.Light.Text, // #111113
    surface = DesignTokens.Light.Surface, // #FFFFFF
    onSurface = DesignTokens.Light.Text, // #111113
    surfaceVariant = DesignTokens.Light.Bg, // #F7F7F4
    onSurfaceVariant = DesignTokens.Light.TextMuted, // #6B6B72
    outline = DesignTokens.Light.Hairline, // #E6E6E1
    outlineVariant = DesignTokens.Light.Hairline, // #E6E6E1

    surfaceContainerLowest = DesignTokens.Light.Surface, // #FFFFFF
    surfaceContainerLow = DesignTokens.Light.Bg, // #F7F7F4
    surfaceContainer = DesignTokens.Light.Surface, // #FFFFFF
    surfaceContainerHigh = Color(0xFFF0F0EC),
    surfaceContainerHighest = Color(0xFFE8E8E3),
)

// Semantic accessors matching design-tokens.json
val ColorScheme.hairline: Color
    get() = if (surface.luminance() < 0.5f) DesignTokens.Dark.Hairline else DesignTokens.Light.Hairline

val ColorScheme.greenText: Color
    get() = if (surface.luminance() < 0.5f) DesignTokens.Dark.Green else DesignTokens.Light.GreenText

val ColorScheme.selectedRow: Color
    get() = if (surface.luminance() < 0.5f) DesignTokens.Dark.SelectedRow else DesignTokens.Light.SelectedRow

val ColorScheme.textMuted: Color
    get() = if (surface.luminance() < 0.5f) DesignTokens.Dark.TextMuted else DesignTokens.Light.TextMuted

val ColorScheme.groupCard: Color
    get() = if (surface.luminance() < 0.5f) DesignTokens.Dark.Surface else DesignTokens.Light.Surface

val ColorScheme.dockBackground: Color
    get() = if (surface.luminance() < 0.5f) DesignTokens.Dark.Dock else DesignTokens.Light.Dock

val ColorScheme.dockBorder: Color
    get() = if (surface.luminance() < 0.5f) DesignTokens.Dark.DockBorder else DesignTokens.Light.DockBorder

val ColorScheme.dockActivePill: Color
    get() = if (surface.luminance() < 0.5f) DesignTokens.Dark.DockActivePill else DesignTokens.Light.DockActivePill

val ColorScheme.topBarBackground: Color
    get() = if (surface.luminance() < 0.5f) DesignTokens.Dark.TopBar else DesignTokens.Light.TopBar

val ColorScheme.topBarBorder: Color
    get() = if (surface.luminance() < 0.5f) DesignTokens.Dark.TopBarBorder else DesignTokens.Light.TopBarBorder

fun dockIconColor(category: String, isDark: Boolean): Color = when (category.uppercase()) {
    "INBOX", "PERSONAL" -> if (isDark) DesignTokens.DockIconColors.Dark.Personal else DesignTokens.DockIconColors.Light.Personal
    "PROMOTIONS", "PROMOTIONAL", "OFFERS" -> if (isDark) DesignTokens.DockIconColors.Dark.Promotional else DesignTokens.DockIconColors.Light.Promotional
    "SPAM" -> if (isDark) DesignTokens.DockIconColors.Dark.Spam else DesignTokens.DockIconColors.Light.Spam
    "REVIEW" -> if (isDark) DesignTokens.DockIconColors.Dark.Review else DesignTokens.DockIconColors.Light.Review
    else -> if (isDark) DesignTokens.Dark.TextMuted else DesignTokens.Light.TextMuted
}

val InterFontFamily = FontFamily(Font(R.font.inter_variable))

private val MessagesTypography = Typography().let { base ->
    Typography(
        displayLarge = base.displayLarge.copy(fontFamily = InterFontFamily),
        displayMedium = base.displayMedium.copy(fontFamily = InterFontFamily),
        displaySmall = base.displaySmall.copy(fontFamily = InterFontFamily),
        headlineLarge = base.headlineLarge.copy(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp,
        ),
        headlineMedium = base.headlineMedium.copy(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp,
        ),
        headlineSmall = base.headlineSmall.copy(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
        ),
        titleLarge = base.titleLarge.copy(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
        ),
        titleMedium = base.titleMedium.copy(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.SemiBold,
        ),
        titleSmall = base.titleSmall.copy(fontFamily = InterFontFamily),
        bodyLarge = base.bodyLarge.copy(fontFamily = InterFontFamily),
        bodyMedium = base.bodyMedium.copy(fontFamily = InterFontFamily),
        bodySmall = base.bodySmall.copy(
            fontFamily = InterFontFamily,
            fontFeatureSettings = "tnum",
        ),
        labelLarge = base.labelLarge.copy(
            fontFamily = InterFontFamily,
            fontWeight = FontWeight.Medium,
        ),
        labelMedium = base.labelMedium.copy(
            fontFamily = InterFontFamily,
            fontFeatureSettings = "tnum",
        ),
        labelSmall = base.labelSmall.copy(
            fontFamily = InterFontFamily,
            fontFeatureSettings = "tnum",
        ),
    )
}

@Composable
fun MessagesTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val scheme = if (darkTheme) FixedDarkColorScheme else FixedLightColorScheme
    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalSentBubble provides SentBubbleColors(
            container = if (darkTheme) DesignTokens.Dark.Green else DesignTokens.Light.Green,
            onContainer = DesignTokens.Dark.OnGreen,
        ),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MessagesTypography,
            shapes = MessagesShapes,
            content = content,
        )
    }
}
