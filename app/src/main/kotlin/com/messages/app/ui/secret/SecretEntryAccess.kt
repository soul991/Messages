package com.messages.app.ui.secret

import android.content.Context
import androidx.annotation.StringRes
import com.messages.app.R

/**
 * V2-39. Whether the locked space offers an entry point that assistive
 * technology can actually reach.
 *
 * The only way in was a 1.5-second timed press on the Home title, implemented
 * as raw pointer input with no semantic long-click and no action. TalkBack,
 * Switch Access, keyboard and D-pad users cannot produce that gesture, and
 * neither can many people with motor impairments — the feature was not merely
 * hidden from them, it was unreachable.
 *
 * ## Why this is a setting and not simply always on
 *
 * The gesture's lack of affordance is the point: nothing on screen hints the
 * space exists. A permanent, labelled "Open locked space" accessibility action
 * would announce it to anyone who turns on a screen reader, which is exactly
 * the concealment the feature is built around.
 *
 * So the trade is the user's to make, and the switch that makes it is:
 *
 * - **Present on every install, whether or not a locked space exists.** A row
 *   that appears only for users who have one would itself be the disclosure.
 *   As shipped it reveals that the *app* has the feature — which its own
 *   documentation does anyway — and nothing about this user.
 * - **Off by default**, so concealment is what you get unless you ask for
 *   something else.
 * - **Never a bypass.** Turning it on adds a route to the credential prompt,
 *   not through it. The authentication gate is untouched.
 */
object SecretEntryAccess {

    private const val PREFS = "settings"
    private const val KEY = "secret_accessible_entry"

    /**
     * Label used by both the accessibility action and the Settings row. One
     * resource, two surfaces: if they drift, a user who reads the row in
     * Settings hears something else from TalkBack and cannot tell they are the
     * same door.
     */
    @StringRes
    val ACTION_LABEL: Int = R.string.secret_entry_action

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun setEnabled(context: Context, on: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY, on).apply()
    }
}
