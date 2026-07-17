package com.personal.detectivedialer.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Brand palette. Blue drives chrome (top bars, FAB, active tab, links),
 * red marks spam/blocked, green marks verified-safe.
 */
object Brand {
    val Blue = Color(0xFF0099FF)
    val BlueDeep = Color(0xFF0077D6)      // gradient bottom / pressed
    val BlueLight = Color(0xFF66C2FF)     // dark-theme primary
    val BlueContainer = Color(0xFFD6EEFF)

    val SpamRed = Color(0xFFE53935)
    val SpamRedDeep = Color(0xFFB71C1C)
    val SpamRedLight = Color(0xFFFF6E6A)  // dark-theme spam accent
    val SpamRedContainer = Color(0xFFFFE1E0)

    val SafeGreen = Color(0xFF2E9E5B)
    val SafeGreenLight = Color(0xFF6FD294)
    val SafeGreenContainer = Color(0xFFDBF3E4)
}

/** Theme-aware accents that aren't part of the M3 color scheme. */
object DialerColors {
    val spam: Color @Composable get() =
        if (isSystemInDarkTheme()) Brand.SpamRedLight else Brand.SpamRed
    val safe: Color @Composable get() =
        if (isSystemInDarkTheme()) Brand.SafeGreenLight else Brand.SafeGreen
    val spamContainer: Color @Composable get() =
        if (isSystemInDarkTheme()) Color(0xFF4A1F1E) else Brand.SpamRedContainer
    val safeContainer: Color @Composable get() =
        if (isSystemInDarkTheme()) Color(0xFF1C3A28) else Brand.SafeGreenContainer
}

private val LightColors = lightColorScheme(
    primary = Brand.Blue,
    onPrimary = Color.White,
    primaryContainer = Brand.BlueContainer,
    onPrimaryContainer = Color(0xFF00344F),
    secondary = Brand.BlueDeep,
    onSecondary = Color.White,
    tertiary = Brand.SafeGreen,
    onTertiary = Color.White,
    error = Brand.SpamRed,
    onError = Color.White,
    errorContainer = Brand.SpamRedContainer,
    onErrorContainer = Color(0xFF5F1210),
    background = Color(0xFFF4F6F8),       // near-white, cards float on it
    onBackground = Color(0xFF171C20),
    surface = Color.White,
    onSurface = Color(0xFF171C20),
    surfaceVariant = Color(0xFFE9EEF2),
    onSurfaceVariant = Color(0xFF5C6670),
    outline = Color(0xFFC4CCD4),
)

private val DarkColors = darkColorScheme(
    primary = Brand.BlueLight,
    onPrimary = Color(0xFF00344F),
    primaryContainer = Color(0xFF00466E),
    onPrimaryContainer = Brand.BlueContainer,
    secondary = Brand.BlueLight,
    onSecondary = Color(0xFF00344F),
    tertiary = Brand.SafeGreenLight,
    onTertiary = Color(0xFF0D2A18),
    error = Brand.SpamRedLight,
    onError = Color(0xFF400807),
    errorContainer = Color(0xFF4A1F1E),
    onErrorContainer = Color(0xFFFFDAD8),
    background = Color(0xFF101418),       // true dark
    onBackground = Color(0xFFE2E6EA),
    surface = Color(0xFF181D22),
    onSurface = Color(0xFFE2E6EA),
    surfaceVariant = Color(0xFF232A31),
    onSurfaceVariant = Color(0xFFA6B0BA),
    outline = Color(0xFF4C565F),
)

/** Rounded 12–16dp corners across cards, dialogs, and sheets. */
private val DialerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun DetectiveDialerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    // Brand-fixed palette: dynamic (wallpaper) color is intentionally off.
    val colorScheme = if (darkTheme) DarkColors else LightColors

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            // Edge-to-edge: headers (blue/red) draw behind a transparent
            // status bar, so its icons must stay light in both themes.
            window.statusBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography(),
        shapes = DialerShapes,
        content = content,
    )
}
