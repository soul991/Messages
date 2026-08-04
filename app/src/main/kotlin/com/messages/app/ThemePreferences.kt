package com.messages.app

import android.content.Context
import com.messages.designsystem.AccentSeed
import com.messages.designsystem.ThemeMode

/** Device-local appearance choice. It is intentionally independent of system theme changes. */
object ThemePreferences {
    private const val PREFS = "settings"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_ACCENT_SEED = "accent_seed"

    fun current(context: Context): ThemeMode {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return ThemeMode.values().firstOrNull { it.name == saved } ?: ThemeMode.SYSTEM
    }

    fun set(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME_MODE, mode.name).apply()
    }

    /** Phase 5 §4: app accent — Material You dynamic by default, or a curated seed. */
    fun currentAccent(context: Context): AccentSeed {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_ACCENT_SEED, AccentSeed.DYNAMIC.name)
        return AccentSeed.values().firstOrNull { it.name == saved } ?: AccentSeed.DYNAMIC
    }

    fun setAccent(context: Context, accent: AccentSeed) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACCENT_SEED, accent.name).apply()
    }
}
