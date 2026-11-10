package com.messages.app

import android.content.Context

/**
 * User preferences for app theme and launcher icon.
 * Dark and Light strictly follow system theme.
 */
object ThemePreferences {
    private const val PREFS = "settings"
    private const val KEY_LIGHT_ICON = "light_launcher_icon"
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

    fun isLightIconEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_LIGHT_ICON, false)

    fun setLightIconEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_LIGHT_ICON, enabled).apply()

        runCatching {
            val pm = context.packageManager
            val darkAlias = android.content.ComponentName(context, "com.messages.app.MainActivityAliasDark")
            val lightAlias = android.content.ComponentName(context, "com.messages.app.MainActivityAliasLight")

            val lightState = if (enabled) {
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            val darkState = if (enabled) {
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            } else {
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            }

            pm.setComponentEnabledSetting(lightAlias, lightState, android.content.pm.PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(darkAlias, darkState, android.content.pm.PackageManager.DONT_KILL_APP)
        }
    }
}
