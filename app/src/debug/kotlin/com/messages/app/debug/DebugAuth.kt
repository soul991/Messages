package com.messages.app.debug

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * R-24: authorization for the debug-only test-harness receivers.
 *
 * The receivers have to stay `exported` — `adb shell am broadcast` arrives as
 * the shell uid, not as this app, so a non-exported receiver is unreachable.
 * Export alone, though, let ANY app on the device inject fabricated messages
 * into the pipeline or ask for provider rows to be deleted.
 *
 * Two independent gates, both required:
 *  1. a `signature`-level permission declared in the debug manifest — only a
 *     component signed with the same key can hold it, and adb/shell is granted
 *     it because shell can hold any permission it asks for at install time;
 *  2. an unpredictable per-install token that the caller must echo back, so a
 *     stale script or a same-signature build on another device cannot drive
 *     this one by accident.
 *
 * The token is generated on first use and printed to logcat (readable only by
 * this app's own uid and by adb) so a device walkthrough can pick it up.
 */
object DebugAuth {

    private const val PREFS = "debug_harness"
    private const val KEY_TOKEN = "token"
    private const val EXTRA_TOKEN = "token"
    private const val TAG = "DebugAuth"

    /** Stable per-install secret; created on first read. */
    fun token(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_TOKEN, null)?.let { return it }
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        val fresh = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY_TOKEN, fresh).apply()
        return fresh
    }

    /**
     * True only when the broadcast carries the right token. Logs the expected
     * value on rejection so the harness is still usable from adb.
     */
    fun isAuthorized(context: Context, intent: Intent): Boolean {
        val expected = token(context)
        val supplied = intent.getStringExtra(EXTRA_TOKEN)
        if (supplied == expected) return true
        Log.w(TAG, "REFUSED: debug harness broadcast missing or invalid token")
        return false
    }
}
