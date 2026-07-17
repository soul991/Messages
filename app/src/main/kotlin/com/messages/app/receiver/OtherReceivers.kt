package com.messages.app.receiver

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Default-SMS mandatory component: WAP_PUSH_DELIVER (MMS). Minimal handling; stored for later parse. */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // MMS PDU parsing is carrier-variable; MVP stores notification-of-MMS.
        // Full MMS download is milestone M5 polish.
    }
}

/** Tracks SENT result for outgoing SMS (delivery status / resend on failure). */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra("messageId", -1L)
        if (messageId == -1L) return
        val ok = resultCode == Activity.RESULT_OK
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = com.messages.core.MessageRepository.get(context).db
                val msg = db.messages().byId(messageId)
                if (msg != null) db.messages().update(msg.copy(sendStatus = if (ok) "SENT" else "FAILED"))
            } finally {
                pending.finish()
            }
        }
    }
}

/** Handles notification inline actions: mark read, move to inbox/spam. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra("messageId", -1L)
        val threadId = intent.getLongExtra("threadId", -1L)
        val action = intent.getStringExtra("action") ?: return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repo = com.messages.core.MessageRepository.get(context)
                when (action) {
                    "mark_read" -> if (threadId != -1L) {
                        repo.db.messages().markThreadRead(threadId)
                        repo.db.conversations().clearUnread(threadId)
                    }
                    "not_spam" -> if (messageId != -1L) repo.moveToInbox(messageId)
                    "spam" -> if (messageId != -1L) repo.moveToSpam(messageId)
                }
                androidx.core.app.NotificationManagerCompat.from(context).cancel(threadId.toInt())
            } finally {
                pending.finish()
            }
        }
    }
}
