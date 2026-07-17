package com.messages.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.messages.app.notify.MessageNotifier
import com.messages.core.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = MessageRepository.get(context)
                val (entity, verdict) = repo.onIncomingSms(address, body, timestamp)
                MessageNotifier(context).notifyFor(entity, verdict, repo.lookupContactName(address))
            } finally {
                pending.finish()
            }
        }
    }
}
