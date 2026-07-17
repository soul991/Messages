package com.personal.detectivedialer.telecom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.util.Log
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Places outgoing calls through Telecom. As the default dialer we use
 * [TelecomManager.placeCall] so the platform routes the call through our own
 * InCallService (and handles emergency numbers correctly). Shared by the
 * dialpad, the contacts list, and contact detail so they behave identically.
 */
@Singleton
class PhoneCaller @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** True if CALL_PHONE is granted (callers can pre-check before requesting it). */
    fun canPlaceCalls(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) ==
            PackageManager.PERMISSION_GRANTED

    /** Returns false when CALL_PHONE is missing or the number is blank/placement fails. */
    fun placeCall(number: String): Boolean {
        val n = number.trim()
        if (n.isEmpty() || !canPlaceCalls()) return false
        return runCatching {
            val tm = context.getSystemService(TelecomManager::class.java)
            tm.placeCall(Uri.fromParts("tel", n, null), Bundle())
            true
        }.onFailure { Log.e(TAG, "placeCall failed", it) }.getOrDefault(false)
    }

    companion object {
        private const val TAG = "PhoneCaller"
    }
}
