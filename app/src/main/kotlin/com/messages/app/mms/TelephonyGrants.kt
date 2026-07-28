package com.messages.app.mms

import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * R-17: URI grants for the MMS send/download handoff.
 *
 * Both paths used to grant to the literal package `com.android.phone`. That is
 * AOSP's telephony package, not a guarantee — OEM builds (and devices with their
 * own carrier-messaging implementation) host the MMS stack elsewhere, so the
 * grant missed the process that actually opens the PDU and the send or download
 * failed with no diagnosable cause.
 *
 * We resolve the eligible handlers instead, grant to each, and revoke once the
 * result callback has run so the temp PDU is not left readable indefinitely.
 */
object TelephonyGrants {

    /** AOSP's telephony process — kept as a floor, no longer as the answer. */
    private const val AOSP_PHONE = "com.android.phone"

    /** Carrier implementations of the MMS transport bind through these. */
    private val SERVICE_ACTIONS = listOf(
        "android.service.carrier.CarrierMessagingService",
        "android.telephony.action.CARRIER_MESSAGING_CLIENT_SERVICE",
    )

    /**
     * Every package that could plausibly need to read/write the PDU: the
     * telephony provider's own package, any resolved carrier-messaging service,
     * and the AOSP phone package as a fallback.
     */
    fun packages(context: Context): Set<String> {
        val pm = context.packageManager
        val found = linkedSetOf<String>()
        runCatching {
            pm.resolveContentProvider("mms-sms", 0)?.packageName?.let(found::add)
        }
        SERVICE_ACTIONS.forEach { action ->
            runCatching {
                pm.queryIntentServices(Intent(action), 0).forEach { info ->
                    info.serviceInfo?.packageName?.let(found::add)
                }
            }
        }
        found += AOSP_PHONE
        return found
    }

    /** Grant [flags] on [uri] to every resolved telephony handler. */
    fun grant(context: Context, uri: Uri, flags: Int) {
        packages(context).forEach { pkg ->
            runCatching { context.grantUriPermission(pkg, uri, flags) }
        }
    }

    /**
     * Drop the grants again. Called from the result receivers: the temp PDU is
     * deleted there too, but a stale grant on a re-created path would otherwise
     * outlive the transaction.
     */
    fun revoke(context: Context, uri: Uri) {
        runCatching {
            context.revokeUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
    }
}
