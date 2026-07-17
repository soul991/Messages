package com.messages.app.receiver

import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.FileProvider
import com.messages.app.notify.MessageNotifier
import com.messages.core.MessageRepository
import com.messages.core.mms.MmsPduParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Default-SMS mandatory component: WAP_PUSH_DELIVER (MMS). Parses the
 * m-notification-ind and asks the platform to download the full message;
 * [MmsDownloadReceiver] finishes the pipeline. If anything fails we still
 * store a placeholder from the notification — nothing is silently dropped.
 */
class MmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.WAP_PUSH_DELIVER_ACTION) return
        val pdu = intent.getByteArrayExtra("data") ?: return
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val notification = MmsPduParser.parseNotificationInd(pdu)
                val location = notification?.contentLocation
                if (notification == null || location.isNullOrBlank()) return@launch
                downloadOrFallback(context, notification, location)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun downloadOrFallback(
        context: Context,
        notification: MmsPduParser.NotificationInd,
        location: String,
    ) {
        try {
            val dir = File(context.cacheDir, "mms").apply { mkdirs() }
            val file = File(dir, "mms_${System.currentTimeMillis()}_${location.hashCode()}.pdu")
            val contentUri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file,
            )
            val resultIntent = Intent(context, MmsDownloadReceiver::class.java)
                .putExtra("filePath", file.absolutePath)
                .putExtra("address", notification.from)
                .putExtra("transactionId", notification.transactionId)
            val pi = PendingIntent.getBroadcast(
                context,
                notification.transactionId?.hashCode() ?: location.hashCode(),
                resultIntent,
                // MUTABLE: the platform appends EXTRA_MMS_HTTP_STATUS to the result
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            // The telephony process writes the retrieved PDU through this grant.
            context.grantUriPermission(
                "com.android.phone", contentUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            context.getSystemService(SmsManager::class.java)
                .downloadMultimediaMessage(context, location, contentUri, null, pi)
        } catch (_: Exception) {
            storeUndownloadable(context, notification.from, notification.transactionId)
        }
    }
}

/** Result of SmsManager.downloadMultimediaMessage — parse, store, classify, notify. */
class MmsDownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val filePath = intent.getStringExtra("filePath") ?: return
        val fallbackAddress = intent.getStringExtra("address")
        val transactionId = intent.getStringExtra("transactionId")
        val ok = resultCode == Activity.RESULT_OK
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val file = File(filePath)
                val conf = if (ok && file.exists()) {
                    runCatching { MmsPduParser.parseRetrieveConf(file.readBytes()) }.getOrNull()
                } else null
                file.delete()

                if (conf == null) {
                    storeUndownloadable(context, fallbackAddress, transactionId)
                    return@launch
                }
                val sender = conf.from ?: fallbackAddress ?: return@launch
                // Group MMS: other recipients besides us → thread on the whole
                // group (sender + co-recipients), matching the send-side convention.
                val ownNumbers = ownNumbers(context)
                val coRecipients = conf.to
                    .map { it.trim() }
                    .filter { it.isNotBlank() && ownNumbers.none { own -> sameNumber(own, it) } }
                val threadAddress =
                    if (coRecipients.isEmpty()) sender
                    else (listOf(sender) + coRecipients).distinct().joinToString(";")
                val body = listOfNotNull(
                    conf.subject?.takeIf { it.isNotBlank() },
                    conf.textBody.takeIf { it.isNotBlank() },
                ).joinToString("\n")
                val repo = MessageRepository.get(context)
                val result = repo.onIncomingMms(
                    threadAddress, body, System.currentTimeMillis(), transactionId,
                    conf.attachments, senderAddress = sender,
                ) ?: return@launch // duplicate delivery
                MessageNotifier(context).notifyFor(result.first, result.second, repo.lookupContactName(sender))
                com.messages.app.widget.WidgetUpdater.requestUpdate(context)
            } finally {
                pending.finish()
            }
        }
    }
}

/** Our own line numbers (all active SIMs) — used to drop ourselves from group threads. */
private fun ownNumbers(context: Context): List<String> = try {
    context.getSystemService(android.telephony.SubscriptionManager::class.java)
        ?.activeSubscriptionInfoList.orEmpty()
        .mapNotNull { it.number?.takeIf { n -> n.isNotBlank() } }
} catch (_: Exception) {
    emptyList()
}

/** Loose phone-number equality: compare the last 10 digits (or fewer). */
private fun sameNumber(a: String, b: String): Boolean {
    val da = a.filter { it.isDigit() }.takeLast(10)
    val db = b.filter { it.isDigit() }.takeLast(10)
    return da.isNotEmpty() && da == db
}

/** Never-lose fallback: record that an MMS arrived even when we can't fetch it. */
private suspend fun storeUndownloadable(context: Context, address: String?, transactionId: String?) {
    if (address.isNullOrBlank()) return // sender unknown — nothing actionable to store
    val repo = MessageRepository.get(context)
    val result = repo.onIncomingMms(
        address,
        "[MMS message — couldn't be downloaded]",
        System.currentTimeMillis(),
        transactionId,
        emptyList(),
    ) ?: return
    MessageNotifier(context).notifyFor(result.first, result.second, repo.lookupContactName(address))
}

/** Result of SmsManager.sendMultimediaMessage — finalize status + provider box. */
class MmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra("messageId", -1L)
        if (messageId == -1L) return
        val ok = resultCode == Activity.RESULT_OK
        intent.getStringExtra("filePath")?.let { File(it).delete() } // temp send PDU
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                MessageRepository.get(context).onMmsSendResult(messageId, ok)
            } finally {
                pending.finish()
            }
        }
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

/**
 * Delivery report (§8.1): the carrier acknowledged handset delivery. Only
 * upgrades SENT → DELIVERED; never downgrades a FAILED message.
 */
class SmsDeliveredReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra("messageId", -1L)
        if (messageId == -1L) return
        if (resultCode != Activity.RESULT_OK) return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = com.messages.core.MessageRepository.get(context).db
                val msg = db.messages().byId(messageId)
                if (msg != null && msg.sendStatus != "FAILED") {
                    db.messages().update(msg.copy(sendStatus = "DELIVERED"))
                }
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
                com.messages.app.widget.WidgetUpdater.requestUpdate(context)
            } finally {
                pending.finish()
            }
        }
    }
}
