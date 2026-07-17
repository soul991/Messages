package com.messages.designsystem

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

// Category hues (§9): fraud = red, promo = amber, protected = green
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
    MaterialTheme(colorScheme = scheme, content = content)
}
