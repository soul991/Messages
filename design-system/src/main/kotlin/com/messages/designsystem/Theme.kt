package com.messages.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** True when the resolved theme is dark — for theme-aware category hues. */
val LocalDarkTheme = staticCompositionLocalOf { false }

/**
 * Sent-bubble color role (Phase 5 §2): the outgoing bubble's container/on
 * pair. Defaults to the scheme's primary/onPrimary — Violet is the app's
 * identity (2026 visual refresh), deliberately not the messengers' green or blue.
 * ChatStyle presets (including the green family) override it per chat.
 */
@Immutable
data class SentBubbleColors(val container: Color, val onContainer: Color)

val LocalSentBubble = staticCompositionLocalOf {
    SentBubbleColors(Color.Unspecified, Color.Unspecified)
}

/**
 * Category hue triple (§9: fraud = red, promo = amber, protected = green).
 * `tint` is the icon/accent color, `container`/`onContainer` are an
 * AA-contrast pair for avatar and banner fills in the active theme.
 */
@Immutable
data class CategoryPalette(val tint: Color, val container: Color, val onContainer: Color)

private val FraudLight = CategoryPalette(Color(0xFFBA1A1A), Color(0xFFFFDAD6), Color(0xFF410002))
private val FraudDark = CategoryPalette(Color(0xFFFFB4AB), Color(0xFF93000A), Color(0xFFFFDAD6))
private val PromoLight = CategoryPalette(Color(0xFF7A5900), Color(0xFFFFDF9E), Color(0xFF261A00))
private val PromoDark = CategoryPalette(Color(0xFFEFC047), Color(0xFF5C4300), Color(0xFFFFDF9E))
private val ProtectedLight = CategoryPalette(Color(0xFF1B6C31), Color(0xFFA3F4AF), Color(0xFF00210A))
private val ProtectedDark = CategoryPalette(Color(0xFF88D896), Color(0xFF0F5223), Color(0xFFA3F4AF))
private val ReviewLight = CategoryPalette(Color(0xFF555F71), Color(0xFFD9E3F8), Color(0xFF121C2B))
private val ReviewDark = CategoryPalette(Color(0xFFBDC7DC), Color(0xFF3E4759), Color(0xFFD9E3F8))

/** Theme-aware palette for a message/conversation category, or null for neutral. */
@Composable
fun categoryPalette(category: String?): CategoryPalette? {
    val dark = LocalDarkTheme.current
    return when (category) {
        "SPAM", "BLOCKED" -> if (dark) FraudDark else FraudLight
        "PROMOTIONS" -> if (dark) PromoDark else PromoLight
        "TRANSACTIONS" -> if (dark) ProtectedDark else ProtectedLight
        "REVIEW" -> if (dark) ReviewDark else ReviewLight
        else -> null
    }
}

// Legacy static hues — kept for non-composable callers (widgets, notifications).
object CategoryColors {
    val Fraud = Color(0xFFBA1A1A)
    val FraudContainer = Color(0xFFFFDAD6)
    val Promo = Color(0xFF7A5900)
    val PromoContainer = Color(0xFFFFDF9E)
    val Protected = Color(0xFF1B6C31)
    val ProtectedContainer = Color(0xFFA3F4AF)
    val Review = Color(0xFF555F71)
    val ReviewContainer = Color(0xFFD9E3F8)
}

// ---- Curated accent seeds (Phase 5 §4, Violet added in 2026 visual refresh)
//
// Violet is the default identity; Dynamic and eight further curated seeds
// remain fully selectable choices in Settings. Colors are generated at exact
// CIELAB tones — tone == L*, the same axis M3's HCT uses — so every
// on/container pair lands at the tone distances that make WCAG AA hold by
// construction (T100-on-T40, T10-on-T90, T20-on-T80, T90-on-T30).
// `AccentSchemesContrastTest` in :app asserts the ratios generically.

/** A selectable app accent. DYNAMIC = Material You (falls back to BLUE pre-S). */
enum class AccentSeed(val displayName: String, internal val hue: Double, internal val chroma: Double) {
    DYNAMIC("Dynamic", 0.0, 0.0),
    /**
     * App identity (2026 visual refresh): an unclaimed hue in this category
     * — every major messenger sits on blue or green — chosen clear of the
     * fraud/promo/protected/review category hues below. Hue/chroma land at
     * Tone 40 == #5A41DD.
     */
    VIOLET("Violet", 304.5, 92.0),
    BLUE("Blue", 262.0, 36.0),
    TEAL("Teal", 193.0, 28.0),
    GREEN("Green", 135.0, 42.0),
    AMBER("Amber", 84.0, 46.0),
    CORAL("Coral", 42.0, 46.0),
    PINK("Pink", 356.0, 38.0),
    PURPLE("Purple", 310.0, 38.0),
    GRAPHITE("Graphite", 262.0, 5.0),
}

/** CIELAB (D65) → sRGB; returns null when the color is out of gamut. */
private fun labToSrgbOrNull(l: Double, aStar: Double, bStar: Double): Color? {
    val fy = (l + 16.0) / 116.0
    val fx = fy + aStar / 500.0
    val fz = fy - bStar / 200.0
    fun finv(t: Double): Double {
        val t3 = t * t * t
        return if (t3 > 0.008856) t3 else (t - 16.0 / 116.0) / 7.787
    }
    val x = finv(fx) * 0.95047
    val y = finv(fy) * 1.0
    val z = finv(fz) * 1.08883
    val rl = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z
    val gl = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z
    val bl = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z
    val eps = 1e-4
    if (rl < -eps || rl > 1 + eps || gl < -eps || gl > 1 + eps || bl < -eps || bl > 1 + eps) return null
    fun encode(c: Double): Float {
        val v = c.coerceIn(0.0, 1.0)
        return (if (v <= 0.0031308) v * 12.92 else 1.055 * Math.pow(v, 1 / 2.4) - 0.055).toFloat()
    }
    return Color(encode(rl), encode(gl), encode(bl))
}

/**
 * Color at an exact tone (L*), desaturating toward gray until sRGB can
 * represent it — tone is never sacrificed, so contrast pairs stay honest.
 */
fun accentTone(hue: Double, chroma: Double, tone: Double): Color {
    val rad = Math.toRadians(hue)
    var c = chroma
    while (c >= 1.0) {
        labToSrgbOrNull(tone, c * Math.cos(rad), c * Math.sin(rad))?.let { return it }
        c -= 1.0
    }
    return labToSrgbOrNull(tone, 0.0, 0.0) ?: Color.Black
}

/** Full light/dark scheme for a non-dynamic seed, M3 tone mapping throughout. */
fun schemeForSeed(seed: AccentSeed, dark: Boolean): ColorScheme {
    val h = seed.hue
    val c = seed.chroma
    val sec = c / 3.0          // M3: secondary is the seed hue at a third chroma
    val terH = (h + 60.0) % 360.0
    val terC = c * 0.6
    fun t(chroma: Double, tone: Double, hue: Double = h) = accentTone(hue, chroma, tone)
    return if (!dark) LightBase.copy(
        primary = t(c, 40.0), onPrimary = Color.White,
        primaryContainer = t(c, 90.0), onPrimaryContainer = t(c, 10.0),
        inversePrimary = t(c, 80.0), surfaceTint = t(c, 40.0),
        secondary = t(sec, 40.0), onSecondary = Color.White,
        secondaryContainer = t(sec, 90.0), onSecondaryContainer = t(sec, 10.0),
        tertiary = t(terC, 40.0, terH), onTertiary = Color.White,
        tertiaryContainer = t(terC, 90.0, terH), onTertiaryContainer = t(terC, 10.0, terH),
    ) else DarkBase.copy(
        primary = t(c, 80.0), onPrimary = t(c, 20.0),
        primaryContainer = t(c, 30.0), onPrimaryContainer = t(c, 90.0),
        inversePrimary = t(c, 40.0), surfaceTint = t(c, 80.0),
        secondary = t(sec, 80.0), onSecondary = t(sec, 20.0),
        secondaryContainer = t(sec, 30.0), onSecondaryContainer = t(sec, 90.0),
        tertiary = t(terC, 80.0, terH), onTertiary = t(terC, 20.0, terH),
        tertiaryContainer = t(terC, 30.0, terH), onTertiaryContainer = t(terC, 90.0, terH),
    )
}

// Shared neutral surfaces — accents restyle the color roles, not the canvas,
// so switching accents never shifts the app's background feel.
private val LightBase = lightColorScheme(
    primary = Color(0xFF00629E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE5FF),
    onPrimaryContainer = Color(0xFF001D34),
    secondary = Color(0xFF526070),
    secondaryContainer = Color(0xFFD6E4F7),
    // 2026 visual refresh: warm paper canvas replaces the cooler blue-gray —
    // reads calmer and softer, per the 2026 visual spec.
    surface = Color(0xFFFAF7F2),
    surfaceVariant = Color(0xFFE8E2D6),
    background = Color(0xFFFAF7F2),
)

private val DarkBase = darkColorScheme(
    primary = Color(0xFF99CBFF),
    onPrimary = Color(0xFF003355),
    primaryContainer = Color(0xFF004A79),
    onPrimaryContainer = Color(0xFFCFE5FF),
    secondary = Color(0xFFBAC8DA),
    secondaryContainer = Color(0xFF3B4857),
    // 2026 visual refresh: warm violet-tinted charcoal, ties the "Dark"
    // tier to the new accent without touching the "AMOLED" tier below.
    surface = Color(0xFF17151F),
    surfaceVariant = Color(0xFF4A4450),
    background = Color(0xFF17151F),
)

// The pre-accent static schemes were the BLUE seed's values; BLUE reproduces
// them via the generator, and these bases remain the pre-S dynamic fallback.
private val LightScheme = LightBase
private val DarkScheme = DarkBase

private val AmoledScheme = DarkScheme.copy(
    surface = Color.Black,
    background = Color.Black,
)

/**
 * §9 craft bar: clear hierarchy on the default (correct-for-M3) Roboto —
 * headlines carry weight and tight tracking; titles are semi-bold so
 * conversation names read as anchors; labels are calm, never shouty.
 */
private val MessagesTypography = Typography().let { base ->
    base.copy(
        headlineLarge = base.headlineLarge.copy(
            fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp,
        ),
        headlineMedium = base.headlineMedium.copy(
            fontWeight = FontWeight.Bold, letterSpacing = (-0.25).sp,
        ),
        headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Medium),
    )
}

enum class ThemeMode { SYSTEM, LIGHT, DARK, AMOLED }

@Composable
fun MessagesTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    accent: AccentSeed = AccentSeed.VIOLET,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val context = LocalContext.current
    val base: ColorScheme = when {
        accent == AccentSeed.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        accent == AccentSeed.DYNAMIC ->
            if (darkTheme) DarkScheme else LightScheme
        else -> schemeForSeed(accent, darkTheme)
    }
    // AMOLED tier (deliberate differentiator — refs' dark is #1C1C1E, ours is
    // true black) overlays every accent path identically.
    val scheme = if (mode == ThemeMode.AMOLED) {
        base.copy(surface = Color.Black, background = Color.Black)
    } else base
    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalSentBubble provides SentBubbleColors(scheme.primary, scheme.onPrimary),
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MessagesTypography,
            shapes = MessagesShapes,
            content = content,
        )
    }
}
