package com.messages.app.mms

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
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
     * Issue #14: the permissions the platform requires on a *genuine* carrier
     * messaging service. A service declaring one of these can only be bound by
     * the system, which is the closest thing to proof of identity available to
     * a normal app — plain intent resolution is not, since any app can publish
     * a service with a matching action.
     */
    private val BIND_PERMISSIONS = setOf(
        "android.permission.BIND_CARRIER_MESSAGING_SERVICE",
        "android.permission.BIND_CARRIER_SERVICES",
    )

    /**
     * Packages that may read/write the PDU.
     *
     * Issue #14: this used to be "everything that resolves a broad action",
     * which meant any installed app could publish a service with
     * `CarrierMessagingService` in its intent filter and collect a temporary
     * grant on MMS content. Resolution alone proves nothing, so a candidate now
     * has to clear two gates:
     *
     * 1. it is a **system** package (`FLAG_SYSTEM` / `FLAG_UPDATED_SYSTEM_APP`),
     *    which an ordinary sideloaded or Play-installed app is not, and
     * 2. for services, the service itself is guarded by one of
     *    [BIND_PERMISSIONS], i.e. only the platform can bind it.
     *
     * The telephony provider's own package is resolved rather than guessed —
     * on AOSP that is the process which actually opens our file URI.
     */
    fun packages(context: Context): Set<String> {
        val pm = context.packageManager
        val found = linkedSetOf<String>()

        // The telephony provider — authoritative, and always a system package.
        runCatching { pm.resolveContentProvider("mms-sms", 0)?.packageName }
            .getOrNull()
            ?.takeIf { isSystemPackage(pm, it) }
            ?.let(found::add)

        // Carrier MMS transports, but only ones the platform itself gatekeeps.
        SERVICE_ACTIONS.forEach { action ->
            val services = runCatching {
                pm.queryIntentServices(Intent(action), PackageManager.MATCH_SYSTEM_ONLY)
            }.getOrDefault(emptyList())
            services.forEach { info ->
                val service = info.serviceInfo ?: return@forEach
                if (service.permission in BIND_PERMISSIONS &&
                    isSystemPackage(pm, service.packageName)
                ) {
                    found += service.packageName
                }
            }
        }

        // AOSP's phone process, when this device actually has it as a system
        // package. Previously added unconditionally.
        if (isSystemPackage(pm, AOSP_PHONE)) found += AOSP_PHONE

        // Floor: if nothing resolved we would silently break MMS on a device
        // whose telephony stack we failed to identify. Falling back to the AOSP
        // name is the old behaviour and no broader than one known package.
        return found.ifEmpty { setOf(AOSP_PHONE) }
    }

    /**
     * True when [pkg] is installed as part of the system image (including a
     * system app updated from the store). Absent packages are not system
     * packages — an uninstalled name would only produce a no-op grant anyway.
     */
    private fun isSystemPackage(pm: PackageManager, pkg: String): Boolean = runCatching {
        val flags = pm.getApplicationInfo(pkg, 0).flags
        flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }.getOrDefault(false)

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
