package com.personal.detectivedialer.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.personal.detectivedialer.data.local.SmsMessage
import com.personal.detectivedialer.data.repository.SmsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * WAP_PUSH_DELIVER receiver — a mandatory component of the default-SMS-app
 * contract. Full MMS retrieval (PDU parse + HTTP fetch from the MMSC) is out
 * of scope for this personal app; we record a placeholder in the thread so
 * the arrival is visible and nothing is silently lost.
 */
@AndroidEntryPoint
class MmsWapPushReceiver : BroadcastReceiver() {

    @Inject lateinit var smsRepository: SmsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        Log.d(TAG, "MMS WAP push received")

        val pending = goAsync()
        scope.launch {
            try {
                smsRepository.handleIncoming(
                    sender = "MMS",
                    body = "[MMS received — open your carrier's MMS or view on another device. " +
                        "Detective Dialer does not download MMS content.]",
                    timestamp = System.currentTimeMillis(),
                )
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to record MMS arrival", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val TAG = "MmsWapPushReceiver"
    }
}
