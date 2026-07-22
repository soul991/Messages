package com.messages.app.notify

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import android.widget.Toast

/**
 * OTP → clipboard, shared by the notification "Copy" action and the opt-in
 * auto-copy on receipt (Phase 4 item 1). The clip is flagged sensitive so
 * keyboards/overlays mask the code; on Android 13+ the SYSTEM shows its own
 * copied-overlay, so our toast only fires on 12 and lower.
 */
object OtpClipboard {

    private const val PREF = "otp_auto_copy"

    fun autoCopyEnabled(context: Context): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean(PREF, false)

    fun setAutoCopy(context: Context, enabled: Boolean) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putBoolean(PREF, enabled).apply()
    }

    fun copy(context: Context, code: String, toast: Boolean) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
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
            Toast.makeText(context, "OTP copied", Toast.LENGTH_SHORT).show()
        }
    }
}
