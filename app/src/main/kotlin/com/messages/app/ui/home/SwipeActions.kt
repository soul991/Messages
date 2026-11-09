package com.messages.app.ui.home

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Swipe-action customization (§8.2 / Phase C2): single "Archive shortcut".
 * The user picks between Swipe right, Swipe left, or Off.
 * The other direction does nothing.
 * Default: unselected/null so both directions do nothing until the user makes a deliberate choice.
 * Persisted in `settings` prefs under [KEY_ARCHIVE_SHORTCUT]; exposed as process-wide
 * StateFlows so the Home list reacts to Settings changes immediately.
 */
object SwipeActions {
    const val ARCHIVE = "archive"
    const val PIN = "pin"
    const val READ = "read"
    const val MUTE = "mute"
    const val NONE = "none"

    const val SHORTCUT_SWIPE_RIGHT = "swipe_right"
    const val SHORTCUT_SWIPE_LEFT = "swipe_left"
    const val SHORTCUT_OFF = "off"

    const val KEY_ARCHIVE_SHORTCUT = "archive_shortcut"
    private const val KEY_RIGHT = "swipe_right"
    private const val KEY_LEFT = "swipe_left"

    /** Selected archive shortcut: SHORTCUT_SWIPE_RIGHT, SHORTCUT_SWIPE_LEFT, SHORTCUT_OFF, or null if unchosen. */
    val archiveShortcut = MutableStateFlow<String?>(null)

    /** Action for a left-to-right (start→end) swipe. Defaults to NONE until chosen. */
    val right = MutableStateFlow(NONE)

    /** Action for a right-to-left (end→start) swipe. Defaults to NONE until chosen. */
    val left = MutableStateFlow(NONE)

    /** Legacy options list kept for backward compatibility if referenced. */
    val options = listOf(
        ARCHIVE to "Archive",
        PIN to "Pin / Unpin",
        READ to "Mark read",
        MUTE to "Mute / Unmute",
        NONE to "Nothing",
    )

    fun label(id: String): String = options.firstOrNull { it.first == id }?.second ?: "Nothing"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        val p = prefs(ctx)
        if (p.contains(KEY_ARCHIVE_SHORTCUT)) {
            val saved = p.getString(KEY_ARCHIVE_SHORTCUT, null)
            applyShortcut(saved)
        } else {
            // Check legacy preferences:
            // If the user previously had chosen an action other than Archive (pin, read, mute, delete),
            // discrete status toggles don't belong on fast swipes where accidental gestures trigger unwanted state changes.
            // Migrate them cleanly to Off.
            val oldRight = p.getString(KEY_RIGHT, null)
            val oldLeft = p.getString(KEY_LEFT, null)
            val hadNonArchive = (oldRight in listOf(PIN, READ, MUTE, "delete")) || (oldLeft in listOf(PIN, READ, MUTE, "delete"))

            if (hadNonArchive) {
                p.edit().putString(KEY_ARCHIVE_SHORTCUT, SHORTCUT_OFF).apply()
                applyShortcut(SHORTCUT_OFF)
            } else {
                // Default: unselected/empty so the user is forced to make a deliberate choice.
                // If the user hasn't chosen yet, both directions do nothing.
                applyShortcut(null)
            }
        }
    }

    private fun applyShortcut(shortcut: String?) {
        archiveShortcut.value = shortcut
        when (shortcut) {
            SHORTCUT_SWIPE_RIGHT -> {
                right.value = ARCHIVE
                left.value = NONE
            }
            SHORTCUT_SWIPE_LEFT -> {
                right.value = NONE
                left.value = ARCHIVE
            }
            SHORTCUT_OFF -> {
                right.value = NONE
                left.value = NONE
            }
            else -> {
                right.value = NONE
                left.value = NONE
            }
        }
    }

    fun setArchiveShortcut(ctx: Context, shortcut: String) {
        prefs(ctx).edit().putString(KEY_ARCHIVE_SHORTCUT, shortcut).apply()
        applyShortcut(shortcut)
    }

    fun setRight(ctx: Context, id: String) {
        val cleanId = if (id == "delete") NONE else id
        prefs(ctx).edit().putString(KEY_RIGHT, cleanId).apply()
        right.value = cleanId
    }

    fun setLeft(ctx: Context, id: String) {
        val cleanId = if (id == "delete") NONE else id
        prefs(ctx).edit().putString(KEY_LEFT, cleanId).apply()
        left.value = cleanId
    }
}
