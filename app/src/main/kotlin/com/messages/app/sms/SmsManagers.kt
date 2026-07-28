package com.messages.app.sms

import android.content.Context
import android.os.Build
import android.telephony.SmsManager

/**
 * One place to obtain an [SmsManager], because getting it right depends on the
 * API level in two ways that are easy to miss — and were missed.
 *
 * `minSdk` is 26, but:
 *
 *  - **`SmsManager` only became a system service in API 31.** Before that,
 *    `context.getSystemService(SmsManager::class.java)` returns **null**, so
 *    every send path that used it would have thrown an NPE on Android 8-11.
 *    Lint's API database does not model the service registry, so it never
 *    flagged this — only the companion call below gave it away.
 *  - **`createForSubscriptionId` is API 31.** On older releases the equivalent
 *    is the static `getSmsManagerForSubscriptionId`, which was deprecated in 31
 *    precisely because it was replaced by the instance method.
 *
 * Calling the wrong one raises `NoSuchMethodError` — an `Error`, not an
 * `Exception`, so the `catch (_: Exception)` blocks around the send paths would
 * not have contained it either.
 */
object SmsManagers {

    /**
     * The [SmsManager] for [subId], or the system default when [subId] is null
     * (single-SIM, or the user has expressed no per-chat preference).
     */
    @Suppress("DEPRECATION")
    fun forSubscription(context: Context, subId: Int?): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val base = context.getSystemService(SmsManager::class.java)
            if (subId != null) base.createForSubscriptionId(subId) else base
        } else {
            if (subId != null) SmsManager.getSmsManagerForSubscriptionId(subId)
            else SmsManager.getDefault()
        }

    /** The system default [SmsManager], with the same API-level care. */
    fun default(context: Context): SmsManager = forSubscription(context, null)
}
