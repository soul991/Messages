package com.messages.app.ui.home

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Swipe-action customization (§8.2): the user picks what left/right swipes do
 * on conversation rows. Persisted in `settings` prefs; exposed as process-wide
 * StateFlows so the Home list reacts to Settings changes immediately.
 */
object SwipeActions {
    const val ARCHIVE = "archive"
    const val DELETE = "delete"
    const val PIN = "pin"
    const val READ = "read"
    const val MUTE = "mute"
    const val NONE = "none"

    /** id → label, in the order shown in Settings. */
    val options = listOf(
        ARCHIVE to "Archive",
        DELETE to "Delete",
        PIN to "Pin / Unpin",
        READ to "Mark read",
        MUTE to "Mute / Unmute",
        NONE to "Nothing",
    )

    fun label(id: String): String = options.firstOrNull { it.first == id }?.second ?: "Nothing"

    /** Action for a left-to-right (start→end) swipe. */
    val right = MutableStateFlow(ARCHIVE)

    /** Action for a right-to-left (end→start) swipe. */
    val left = MutableStateFlow(DELETE)

    private const val KEY_RIGHT = "swipe_right"
    private const val KEY_LEFT = "swipe_left"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        right.value = prefs(ctx).getString(KEY_RIGHT, ARCHIVE) ?: ARCHIVE
        left.value = prefs(ctx).getString(KEY_LEFT, DELETE) ?: DELETE
    }

    fun setRight(ctx: Context, id: String) {
        prefs(ctx).edit().putString(KEY_RIGHT, id).apply()
        right.value = id
    }

    fun setLeft(ctx: Context, id: String) {
        prefs(ctx).edit().putString(KEY_LEFT, id).apply()
        left.value = id
    }
}
