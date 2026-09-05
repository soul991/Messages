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

    private const val KEY_THEME_VERSION = "theme_version"
    private const val CURRENT_THEME_VERSION = 2 // v1: green default (v1.4.1), v2: violet 2026 refresh (v1.5.1)

    /** App accent — Violet by default (2026 identity), Dynamic and 8 more seeds selectable. */
    fun currentAccent(context: Context): AccentSeed {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val version = prefs.getInt(KEY_THEME_VERSION, 0)
        if (version < CURRENT_THEME_VERSION) {
            val saved = prefs.getString(KEY_ACCENT_SEED, null)
            // Migrate users from previous Green default (or unset) to 2026 Violet identity
            if (saved == null || saved == AccentSeed.GREEN.name) {
                prefs.edit()
                    .putString(KEY_ACCENT_SEED, AccentSeed.VIOLET.name)
                    .putInt(KEY_THEME_VERSION, CURRENT_THEME_VERSION)
                    .apply()
                return AccentSeed.VIOLET
            }
            prefs.edit().putInt(KEY_THEME_VERSION, CURRENT_THEME_VERSION).apply()
        }
        val saved = prefs.getString(KEY_ACCENT_SEED, AccentSeed.VIOLET.name)
        return AccentSeed.values().firstOrNull { it.name == saved } ?: AccentSeed.VIOLET
    }

    fun setAccent(context: Context, accent: AccentSeed) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_ACCENT_SEED, accent.name).apply()
    }

    private const val KEY_CUSTOM_BRIGHTNESS = "custom_brightness_enabled"
    private const val KEY_BRIGHTNESS_LEVEL = "custom_brightness_level"
    const val DEFAULT_BRIGHTNESS = 0.70f

    fun isCustomBrightnessEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_CUSTOM_BRIGHTNESS, false)

    fun setCustomBrightnessEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_CUSTOM_BRIGHTNESS, enabled).apply()
    }

    fun getBrightnessLevel(context: Context): Float =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getFloat(KEY_BRIGHTNESS_LEVEL, DEFAULT_BRIGHTNESS)

    fun setBrightnessLevel(context: Context, level: Float) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putFloat(KEY_BRIGHTNESS_LEVEL, level.coerceIn(0.05f, 1.0f)).apply()
    }
}
