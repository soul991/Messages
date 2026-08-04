package com.messages.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.messages.app.notify.MessageNotifier
import com.messages.app.widget.WidgetUpdater
import com.messages.core.MessageRepository

/**
 * Default-SMS mandatory component: SMS_DELIVER. Runs the full
 * receive → store → classify → notify pipeline. goAsync() keeps the process
 * alive for the pipeline; classification itself is < 50 ms.
 */
class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (messages.isEmpty()) return

        // Multipart SMS arrives as several PDUs of one logical message.
        val address = messages.first().displayOriginatingAddress ?: return
        val body = messages.joinToString("") { it.displayMessageBody ?: "" }
        val timestamp = messages.first().timestampMillis
        // Dual-SIM: which subscription received this (extra name predates the S constant).
        val subId = intent.getIntExtra("android.telephony.extra.SUBSCRIPTION_INDEX",
            intent.getIntExtra("subscription", -1)).takeIf { it >= 0 }

        // V2-34: supervised, time-budgeted, and finished exactly once. The
        // provider write inside onIncomingSms happens first, so the message
        // itself is safe if the rest fails; the process is never crashed over
        // notification or widget work.
        ReceiverWork.launch(goAsync(), TAG) {
            val repo = MessageRepository.get(context)
            val intake = repo.onIncomingSms(address, body, timestamp, subId)
            // R-16: a redelivered broadcast stored nothing the second time.
            // Notifying again would double-alert for one message.
            if (!intake.isNew) return@launch
            MessageNotifier(context)
                .notifyFor(intake.message, intake.verdict, repo.lookupContactName(address))
            WidgetUpdater.requestUpdate(context)
        }
    }

    private companion object {
        const val TAG = "SmsDeliverReceiver"
    }
}
