package com.messages.app

import android.content.Context
import com.messages.designsystem.ThemeMode

/** Device-local appearance choice. It is intentionally independent of system theme changes. */
object ThemePreferences {
    private const val PREFS = "settings"
    private const val KEY_THEME_MODE = "theme_mode"

    fun current(context: Context): ThemeMode {
        val saved = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_THEME_MODE, ThemeMode.SYSTEM.name)
        return ThemeMode.values().firstOrNull { it.name == saved } ?: ThemeMode.SYSTEM
    }

    fun set(context: Context, mode: ThemeMode) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_THEME_MODE, mode.name).apply()
    }
}
