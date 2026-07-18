package com.personal.detectivedialer.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Telephony
import android.util.Log
import androidx.core.app.NotificationCompat
import com.personal.detectivedialer.DetectiveDialerApp
import com.personal.detectivedialer.R
import com.personal.detectivedialer.data.local.SmsMessage
import com.personal.detectivedialer.data.repository.SmsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * SMS_DELIVER receiver — invoked ONLY while Detective Dialer is the default
 * SMS app, exactly once per message. We own persistence: route the message
 * (TRAI suffix rules → Blocked folder, else inbox), write inbox messages to
 * the system Telephony provider, and notify. Blocked messages are stored
 * silently in the Blocked folder — never deleted automatically.
 */
@AndroidEntryPoint
class SmsDeliverReceiver : BroadcastReceiver() {

    @Inject lateinit var smsRepository: SmsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return

        val sender = parts.firstOrNull()?.displayOriginatingAddress.orEmpty()
        val body = parts.joinToString(separator = "") { it.messageBody.orEmpty() }
        val timestamp = parts.firstOrNull()?.timestampMillis ?: System.currentTimeMillis()
        if (sender.isBlank() && body.isBlank()) return

        // Keep the process alive while we classify + persist off the main thread.
        val pending = goAsync()
        scope.launch {
            try {
                val stored = smsRepository.handleIncoming(sender, body, timestamp)
                if (stored.folder == SmsMessage.FOLDER_INBOX) {
                    notify(context, stored)
                } else {
                    Log.d(TAG, "SMS routed to Blocked folder (${stored.blockReason})")
                }
            } catch (t: Throwable) {
                Log.e(TAG, "Failed to handle delivered SMS", t)
            } finally {
                pending.finish()
            }
        }
    }

    private fun notify(context: Context, message: SmsMessage) {
        val deepLink = Uri.parse("detectivedialer://sms/${Uri.encode(message.sender)}")
        val intent = Intent(Intent.ACTION_VIEW, deepLink).apply {
            setPackage(context.packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            context,
            message.sender.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, DetectiveDialerApp.CHANNEL_MESSAGES)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(message.sender.ifBlank { "New message" })
            .setContentText(message.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(message.body))
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(message.sender.hashCode(), notification) }
    }

    companion object {
        private const val TAG = "SmsDeliverReceiver"
    }
}
