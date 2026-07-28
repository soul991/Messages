package com.messages.app.report

import android.content.Context
import android.telephony.SmsManager
import android.telephony.TelephonyManager

/** Android side of carrier spam reporting: SIM-country routing + the send. */
object CarrierReport {

    /** True when any SIM (or the network) says we're on an Indian carrier. */
    fun isIndia(context: Context): Boolean = try {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val sim = tm?.simCountryIso?.lowercase()
        val net = tm?.networkCountryIso?.lowercase()
        sim == "in" || (sim.isNullOrBlank() && net == "in")
    } catch (_: Exception) {
        false
    }

    /**
     * Sends the report SMS. Deliberately fire-and-forget (no provider row —
     * a report to a short code is not a conversation the user needs to keep).
     * Returns false when the radio refused synchronously.
     */
    fun send(context: Context, shortCode: String, text: String, subId: Int?): Boolean = try {
        val sms = com.messages.app.sms.SmsManagers.forSubscription(context, subId)
        val parts = sms.divideMessage(text)
        if (parts.size == 1) sms.sendTextMessage(shortCode, null, text, null, null)
        else sms.sendMultipartTextMessage(shortCode, null, parts, null, null)
        true
    } catch (_: Exception) {
        false
    }
}
