package com.messages.app.sms

import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.messages.app.R

/**
 * The SIMs a message may be sent from.
 *
 * Extracted for V2-48: the outbox lets a queued message change SIM, and the
 * chat composer already had this logic. Two copies of "which SIMs exist, and
 * when is the choice worth showing" would drift, and the permission check is
 * exactly the part you do not want drifting — reading it without
 * READ_PHONE_STATE throws on some OEM builds rather than returning empty.
 */
object SimChoices {

    data class Choice(val subId: Int, val slotIndex: Int, val displayName: String)

    /**
     * Active subscriptions, or an empty list when there is no choice to make.
     *
     * Empty means "do not offer a picker", not "no SIM": with one SIM the
     * platform default is the only correct answer and a one-item picker is
     * noise. Also empty when the permission is missing, because the app cannot
     * name the SIMs and picking blind is worse than the system default.
     */
    fun active(context: Context): List<Choice> {
        if (ContextCompat.checkSelfPermission(
                context, android.Manifest.permission.READ_PHONE_STATE,
            ) != PackageManager.PERMISSION_GRANTED
        ) return emptyList()
        val subs = try {
            context.getSystemService(SubscriptionManager::class.java)
                ?.activeSubscriptionInfoList.orEmpty()
        } catch (_: Exception) {
            emptyList()
        }
        if (subs.size < 2) return emptyList()
        return subs.map {
            Choice(
                subId = it.subscriptionId,
                slotIndex = it.simSlotIndex,
                displayName = it.displayName?.toString()
                    ?: context.getString(R.string.chat_sim_slot, it.simSlotIndex + 1),
            )
        }
    }
}
