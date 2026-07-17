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

private val LightScheme = lightColorScheme(
    primary = Color(0xFF00629E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCFE5FF),
    onPrimaryContainer = Color(0xFF001D34),
    secondary = Color(0xFF526070),
    secondaryContainer = Color(0xFFD6E4F7),
    surface = Color(0xFFF8F9FC),
    surfaceVariant = Color(0xFFDEE3EB),
    background = Color(0xFFF8F9FC),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF99CBFF),
    onPrimary = Color(0xFF003355),
    primaryContainer = Color(0xFF004A79),
    onPrimaryContainer = Color(0xFFCFE5FF),
    secondary = Color(0xFFBAC8DA),
    secondaryContainer = Color(0xFF3B4857),
    surface = Color(0xFF101418),
    surfaceVariant = Color(0xFF42474E),
    background = Color(0xFF101418),
)

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
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val darkTheme = when (mode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK, ThemeMode.AMOLED -> true
    }
    val context = LocalContext.current
    val scheme: ColorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val dynamic = if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (mode == ThemeMode.AMOLED) dynamic.copy(surface = Color.Black, background = Color.Black) else dynamic
        }
        mode == ThemeMode.AMOLED -> AmoledScheme
        darkTheme -> DarkScheme
        else -> LightScheme
    }
    CompositionLocalProvider(LocalDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MessagesTypography,
            content = content,
        )
    }
}
