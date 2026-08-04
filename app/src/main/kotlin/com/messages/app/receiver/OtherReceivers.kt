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
        val pdu = intent.getByteArrayExtra("data")
        // V2-34: a failure OR an overrun of the receiver's window still leaves
        // the never-lose trace — the WAP PDU arrived, so something must be
        // visible to the user either way.
        ReceiverWork.launch(
            goAsync(), TAG,
            onFailure = {
                storeUndownloadable(context, null, null, MmsReceiveFallback.PARSE_FAILURE)
            },
        ) {
            if (pdu == null) {
                storeUndownloadable(context, null, null, MmsReceiveFallback.PARSE_FAILURE)
                return@launch
            }
            val notification = MmsPduParser.parseNotificationInd(pdu)
            val location = notification?.contentLocation
            if (notification == null || location.isNullOrBlank()) {
                // A carrier-specific or malformed WAP PDU must still leave
                // a visible, recoverable trace instead of disappearing.
                storeUndownloadable(
                    context,
                    notification?.from,
                    notification?.transactionId,
                    MmsReceiveFallback.PARSE_FAILURE,
                )
            } else {
                downloadOrFallback(context, notification, location)
            }
        }
    }

    private suspend fun downloadOrFallback(
        context: Context,
        notification: MmsPduParser.NotificationInd,
        location: String,
    ) {
        try {
            val dir = com.messages.app.mms.MmsTransactions.pduDir(context).apply { mkdirs() }
            val pduName = "mms_${System.currentTimeMillis()}_${location.hashCode()}.pdu"
            val file = File(dir, pduName)
            val contentUri = FileProvider.getUriForFile(
                context, "${context.packageName}.fileprovider", file,
            )
            // #15: only an opaque one-shot token travels in the callback. The
            // path we later read and delete, the URI we later revoke, and the
            // sender we fall back to are all resolved from our own record.
            val token = com.messages.app.mms.MmsTransactions.register(
                context, pduName, contentUri,
                address = notification.from,
                transactionId = notification.transactionId,
            )
            val resultIntent = Intent(context, MmsDownloadReceiver::class.java)
                .putExtra(com.messages.app.mms.MmsTransactions.EXTRA_TOKEN, token)
            val pi = PendingIntent.getBroadcast(
                context,
                notification.transactionId?.hashCode() ?: location.hashCode(),
                resultIntent,
                // MUTABLE: the platform appends EXTRA_MMS_HTTP_STATUS to the result
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            // R-17: the telephony process writes the retrieved PDU through this
            // grant — and it is not always `com.android.phone`. Grant to every
            // resolved handler; MmsDownloadReceiver revokes them afterwards.
            com.messages.app.mms.TelephonyGrants.grant(
                context, contentUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            com.messages.app.sms.SmsManagers.default(context)
                .downloadMultimediaMessage(context, location, contentUri, null, pi)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            // V2-34: the budget expired mid-registration. ReceiverWork's
            // recovery path writes the fallback; swallowing it here would
            // report a download that was never started as started.
            throw cancelled
        } catch (t: Throwable) {
            android.util.Log.e(TAG, "MMS download could not start", t)
            storeUndownloadable(
                context,
                notification.from,
                notification.transactionId,
                MmsReceiveFallback.DOWNLOAD_FAILURE,
            )
        }
    }

    private companion object {
        const val TAG = "MmsDeliverReceiver"
    }
}

/** Result of SmsManager.downloadMultimediaMessage — parse, store, classify, notify. */
class MmsDownloadReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // #15: the token is the authority. Consuming it here makes the callback
        // one-shot — a platform retry or a replay by any other holder of the
        // mutable PendingIntent resolves to nothing.
        val record = com.messages.app.mms.MmsTransactions.consume(
            context, intent.getStringExtra(com.messages.app.mms.MmsTransactions.EXTRA_TOKEN),
        )
        val ok = resultCode == Activity.RESULT_OK
        // Display fallbacks only — the error path below needs them too, so they
        // are read out before the try. Never treated as authority.
        val fallbackAddress = record?.address
        val transactionId = record?.transactionId
        ReceiverWork.launch(
            goAsync(), TAG,
            onFailure = {
                storeUndownloadable(
                    context,
                    fallbackAddress,
                    transactionId,
                    MmsReceiveFallback.PROCESSING_FAILURE,
                )
            },
        ) {
            if (record == null) {
                // No record: we have no authority for a path or a URI, so we
                // touch neither. A delivery did arrive though, so the
                // never-lose trace is still written.
                android.util.Log.w(TAG, "unknown or already-settled MMS token")
                storeUndownloadable(
                    context, null, null, MmsReceiveFallback.PROCESSING_FAILURE,
                )
                return@launch
            }
            // Resolved from our own record and containment-checked against
            // cache/mms; null means "no readable PDU", never "use the path
            // from the intent".
            val file = com.messages.app.mms.MmsTransactions.pduFile(context, record)
            // R-17: the carrier controls this file's size. Read it through
            // the bounded reader (null past the PDU ceiling) instead of
            // pulling the whole download into the heap first.
            val conf = if (ok && file != null && file.exists()) {
                com.messages.core.io.BoundedRead
                    .readFile(file, MmsPduParser.MAX_PDU_BYTES)
                    ?.let { runCatching { MmsPduParser.parseRetrieveConf(it) }.getOrNull() }
            } else null
            // The transaction is over: delete the temp PDU and drop the
            // grants we handed telephony.
            com.messages.app.mms.MmsTransactions.settle(context, record)

            if (conf == null) {
                storeUndownloadable(
                    context,
                    fallbackAddress,
                    transactionId,
                    MmsReceiveFallback.DOWNLOAD_FAILURE,
                )
                return@launch
            }
            val sender = MmsReceiveFallback.addressOrUnknown(conf.from ?: fallbackAddress)
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
        }
    }

    private companion object {
        const val TAG = "MmsDownloadReceiver"
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

/** Never-lose fallback: record an MMS even when its metadata or body is unavailable. */
private suspend fun storeUndownloadable(
    context: Context,
    address: String?,
    transactionId: String?,
    reason: String,
) {
    val safeAddress = MmsReceiveFallback.addressOrUnknown(address)
    try {
        val repo = MessageRepository.get(context)
        val result = repo.onIncomingMms(
            safeAddress,
            MmsReceiveFallback.placeholderBody(reason),
            System.currentTimeMillis(),
            transactionId,
            emptyList(),
        ) ?: return
        MessageNotifier(context).notifyFor(result.first, result.second, repo.lookupContactName(safeAddress))
    } catch (t: Throwable) {
        // This is the final line of defense. The raw WAP PDU was received, so
        // log loudly for diagnostics rather than crashing the telephony process.
        android.util.Log.e("MmsReceiveFallback", "Could not index MMS fallback", t)
    }
}

/** Pure, testable fallback policy for malformed/unavailable MMS deliveries. */
internal object MmsReceiveFallback {
    const val UNKNOWN_SENDER = "Unknown MMS sender"
    const val PARSE_FAILURE = "notification could not be read"
    const val DOWNLOAD_FAILURE = "couldn't be downloaded"
    const val PROCESSING_FAILURE = "couldn't be processed"

    fun addressOrUnknown(address: String?): String =
        address?.trim()?.takeIf { it.isNotEmpty() } ?: UNKNOWN_SENDER

    fun placeholderBody(reason: String): String = "[MMS message — $reason]"
}

/** Result of SmsManager.sendMultimediaMessage — finalize status + provider box. */
class MmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // #15: one-shot opaque token → our own record. The message id, the temp
        // PDU to delete and the URI to revoke all come from here, never from a
        // mutable callback extra.
        val record = com.messages.app.mms.MmsTransactions.consume(
            context, intent.getStringExtra(com.messages.app.mms.MmsTransactions.EXTRA_TOKEN),
        )
        if (record == null) {
            android.util.Log.w("MmsSentReceiver", "unknown or already-settled MMS token")
            return
        }
        val ok = resultCode == Activity.RESULT_OK
        // Deletes the temp send PDU (containment-checked) and revokes the
        // telephony grants now that the send is finished.
        com.messages.app.mms.MmsTransactions.settle(context, record)
        val messageId = record.messageId
        if (messageId <= 0L) return
        // V2-34: this scope had no SupervisorJob and no catch — a throw from the
        // repository reached the default handler and took the process down from
        // a send-result callback.
        ReceiverWork.launch(goAsync(), TAG) {
            MessageRepository.get(context).onMmsSendResult(messageId, ok)
        }
    }

    private companion object {
        const val TAG = "MmsSentReceiver"
    }
}

/** Tracks SENT result for outgoing SMS (delivery status / resend on failure). */
class SmsSentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra("messageId", -1L)
        if (messageId == -1L) return
        val ok = resultCode == Activity.RESULT_OK
        // On failure the resultCode IS the SmsManager.RESULT_* value — stored
        // raw for debugging, mapped to the user-facing reason by SendFailure.
        val failureCode = if (ok) null else resultCode
        // R-13: this callback settles ONE (recipient, part) attempt; the
        // message-level status is derived from the whole matrix afterwards, so
        // it can no longer flip to SENT while other parts are in flight/failed.
        val attemptId = intent.getStringExtra("attemptId")
        ReceiverWork.launch(goAsync(), TAG) {
            val repo = com.messages.core.MessageRepository.get(context)
            repo.settleSendAttempt(messageId, attemptId, ok, failureCode)
            // V2-48: arm an automatic retry only if the whole matrix now reads
            // FAILED and the cause is on SendRetry's allowlist — armAutoRetry
            // decides both. Asked after the settle, not instead of it: a
            // multipart send whose first part failed is not retried while the
            // rest are still in flight, because resending then would duplicate
            // the parts that are about to succeed.
            if (!ok) {
                repo.armAutoRetry(messageId)?.let { delay ->
                    com.messages.app.schedule.Scheduler.scheduleRetry(context, messageId, delay)
                }
            }
        }
    }

    private companion object {
        const val TAG = "SmsSentReceiver"
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
        val delivered = resultCode == Activity.RESULT_OK
        val attemptId = intent.getStringExtra("attemptId")
        // R-13: record the report against its own attempt even when negative —
        // DELIVERED is only claimed once EVERY requested report came back OK.
        // Legacy rows (no attempt matrix) keep the old "ignore failures" path.
        if (!delivered && attemptId == null) return
        ReceiverWork.launch(goAsync(), TAG) {
            com.messages.core.MessageRepository.get(context)
                .settleDeliveryAttempt(messageId, attemptId, delivered)
        }
    }

    private companion object {
        const val TAG = "SmsDeliveredReceiver"
    }
}

/** Handles notification actions: mark read, copy OTP, inline reply, move to inbox/spam. */
class NotificationActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra("messageId", -1L)
        val threadId = intent.getLongExtra("threadId", -1L)
        val action = intent.getStringExtra("action") ?: return

        // Clipboard + toast need the main thread and no coroutine hop; the
        // notification stays up (the code may be needed again).
        if (action == "copy_otp") {
            intent.getStringExtra("otp")?.let {
                com.messages.app.notify.OtpClipboard.copy(context, it, toast = true)
            }
            return
        }

        // Inline reply text arrives via RemoteInput on the fired intent.
        val replyText = androidx.core.app.RemoteInput.getResultsFromIntent(intent)
            ?.getCharSequence(com.messages.app.notify.MessageNotifier.KEY_REPLY)
            ?.toString()?.trim()

        ReceiverWork.launch(goAsync(), TAG) {
            val repo = com.messages.core.MessageRepository.get(context)
            // V2-24: these actions only ever reach normal-space threads.
            // The locked space posts one content-free notification with a
            // fixed id, no threadId and no action buttons, so nothing here
            // can be fired against a LOCKED row.
            val space = com.messages.core.db.Spaces.NORMAL
            when (action) {
                "mark_read" -> if (threadId != -1L) {
                    repo.db.messages().markThreadRead(threadId, space)
                    repo.db.conversations().clearUnread(threadId, space)
                }
                "reply" -> if (threadId != -1L && !replyText.isNullOrBlank()) {
                    val conv = repo.db.conversations().byThreadId(threadId)
                    if (conv != null) {
                        // Same path as the composer: provider-first store,
                        // then radio send on the conversation's chosen SIM.
                        val entity = repo.storeOutgoing(
                            conv.address, replyText,
                            System.currentTimeMillis(), conv.preferredSubId,
                        )
                        com.messages.app.schedule.SmsRadio.send(context, repo, entity)
                        repo.db.messages().markThreadRead(threadId, space)
                        repo.db.conversations().clearUnread(threadId, space)
                    }
                }
                "not_spam" -> if (messageId != -1L) repo.moveToInbox(messageId)
                "spam" -> if (messageId != -1L) repo.moveToSpam(messageId)
            }
            // R-15: cancels the thread notification AND the separate fraud
            // warning, which previously survived a "Not spam" action.
            com.messages.app.notify.MessageNotifier.cancelThread(context, threadId)
            com.messages.app.widget.WidgetUpdater.requestUpdate(context)
        }
    }

    private companion object {
        const val TAG = "NotificationActionReceiver"
    }
}
