package com.messages.app.ui.common

import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.messages.designsystem.LocalDarkTheme

/**
 * V2-37, the second half of the DayNight fix.
 *
 * The window theme (res/values + res/values-night) is what paints before the
 * first composition, so it can only follow the *system* night setting. That is
 * right whenever the app theme is SYSTEM, and wrong the moment the user picks an
 * explicit LIGHT/DARK/AMOLED mode that disagrees with the system — the status
 * and navigation bar icons would then be tinted for the wrong background and
 * become near-invisible.
 *
 * This runs inside the theme, where the resolved scheme is finally known, and
 * corrects the bar icon appearance to match it. Call it as the first thing
 * inside [com.messages.designsystem.MessagesTheme]'s content lambda.
 *
 * It deliberately touches only icon appearance: the bar colors themselves stay
 * transparent (set by the theme), so this cannot fight the edge-to-edge layout.
 */
@Composable
fun SyncSystemBars() {
    val view = LocalView.current
    val dark = LocalDarkTheme.current
    if (view.isInEditMode) return
    SideEffect {
        val window = view.context.findActivity()?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

/**
 * A composable's context is not guaranteed to be the Activity itself — Compose
 * may hand back a wrapper (theme overlays, ContextThemeWrapper from a fragment
 * host). Unwrap rather than casting, which would silently no-op.
 */
private fun android.content.Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
