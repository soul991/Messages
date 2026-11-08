package com.messages.app

import android.content.Context

/**
 * User preferences for app theme and launcher icon.
 * Dark and Light strictly follow system theme.
 */
object ThemePreferences {
    private const val PREFS = "settings"
    private const val KEY_LIGHT_ICON = "light_launcher_icon"

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
