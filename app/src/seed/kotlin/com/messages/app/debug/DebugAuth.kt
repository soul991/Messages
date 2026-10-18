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
 *     component signed with the same key can hold it. NOTE: adb/shell does NOT
 *     get it. Shell (uid 2000) auto-holds only permissions declared in its OWN
 *     manifest and cannot declare an app-defined one, so a bare
 *     `adb shell am broadcast` is refused outright by BroadcastQueue before the
 *     receiver ever runs. Reaching these receivers means acting as this app's
 *     uid via `run-as` — see [InjectSmsReceiver] for the full invocation;
 *  2. an unpredictable per-install token that the caller must echo back, so a
 *     stale script or a same-signature build on another device cannot drive
 *     this one by accident.
 *
 * The token is generated lazily on first use and stored in SharedPreferences.
 * It is NOT logged: [isAuthorized] records only THAT a call was refused, never
 * the expected value, so the secret never lands in a buffer any `adb logcat`
 * reader can scrape. Retrieve it under the app's own uid instead:
 *   adb shell run-as com.messages.app.debug \
 *     cat /data/data/com.messages.app.debug/shared_prefs/debug_harness.xml
 * (The file exists only after a first — refused — attempt has created it.)
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
     * True only when the broadcast carries the right token.
     *
     * Rejection logs the FACT of refusal and nothing else — deliberately not
     * the expected value, which would put the secret into logcat. Read the
     * token from the prefs file via `run-as` instead (see the class KDoc).
     * Calling [token] here is what lazily creates that file on the first
     * refused attempt.
     */
    fun isAuthorized(context: Context, intent: Intent): Boolean {
        val expected = token(context)
        val supplied = intent.getStringExtra(EXTRA_TOKEN)
        if (supplied == expected) return true
        Log.w(TAG, "REFUSED: debug harness broadcast missing or invalid token")
        return false
    }
}
