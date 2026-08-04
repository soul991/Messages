package com.messages.app.notify

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PersistableBundle
import android.widget.Toast
import com.messages.app.R

/**
 * OTP → clipboard, shared by the notification "Copy" action and the opt-in
 * auto-copy on receipt (Phase 4 item 1). The clip is flagged sensitive so
 * keyboards/overlays mask the code; on Android 13+ the SYSTEM shows its own
 * copied-overlay, so our toast only fires on 12 and lower.
 *
 * R-30: a one-time authentication code sitting in the clipboard is readable by
 * whatever the user pastes into next, and — below Android 10 — by any app with
 * a foreground window. Three mitigations, all here:
 *  - auto-copy stays OFF by default and is never applied to locked, dangerous
 *    or fraud-flagged messages (enforced by the callers);
 *  - the notification's explicit Copy action is always available, so the user
 *    never has to enable auto-copy to get one-tap convenience;
 *  - the clip is CLEARED after [AUTO_CLEAR_MS] — but only if the clipboard
 *    still holds the same code, so a later copy by the user is never destroyed.
 */
object OtpClipboard {

    private const val PREF = "otp_auto_copy"

    /**
     * How long an auto-copied code may live in the clipboard. Long enough to
     * switch apps and paste, short enough that it isn't still there hours later.
     */
    const val AUTO_CLEAR_MS = 60_000L

    private val handler = Handler(Looper.getMainLooper())

    /** The pending clear, so a second OTP re-arms rather than double-schedules. */
    private var pendingClear: Runnable? = null

    fun autoCopyEnabled(context: Context): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean(PREF, false)

    fun setAutoCopy(context: Context, enabled: Boolean) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putBoolean(PREF, enabled).apply()
    }

    fun copy(context: Context, code: String, toast: Boolean) {
        val app = context.applicationContext
        val cm = app.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("OTP", code).apply {
            if (Build.VERSION.SDK_INT >= 24) {
                description.extras = PersistableBundle().apply {
                    if (Build.VERSION.SDK_INT >= 33) {
                        putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                    } else {
                        putBoolean("android.content.extra.IS_SENSITIVE", true)
                    }
                }
            }
        }
        cm.setPrimaryClip(clip)
        if (toast && Build.VERSION.SDK_INT < 33) {
            Toast.makeText(app, app.getString(R.string.notif_otp_copied), Toast.LENGTH_SHORT).show()
        }
        scheduleClear(app, cm, code)
    }

    /**
     * Clear the clipboard once the code has had its useful life — unless the
     * user has copied something else since, in which case we must not touch it.
     */
    private fun scheduleClear(context: Context, cm: ClipboardManager, code: String) {
        pendingClear?.let(handler::removeCallbacks)
        val clear = Runnable {
            pendingClear = null
            if (!holds(cm, code)) return@Runnable
            try {
                if (Build.VERSION.SDK_INT >= 28) {
                    cm.clearPrimaryClip()
                } else {
                    // No clearPrimaryClip before P: overwrite with an empty,
                    // still-sensitive clip so the code is gone either way.
                    cm.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            } catch (_: Exception) {
                // A clipboard the system won't let us touch is not a failure
                // worth crashing a notification path over.
            }
        }
        pendingClear = clear
        handler.postDelayed(clear, AUTO_CLEAR_MS)
    }

    /** True when the primary clip is still exactly the code we put there. */
    private fun holds(cm: ClipboardManager, code: String): Boolean = try {
        val item = cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)
        item?.text?.toString() == code
    } catch (_: Exception) {
        false
    }
}
