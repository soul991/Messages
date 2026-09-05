package com.messages.app.schedule

import com.messages.app.R
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.telephony.SmsManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.messages.app.MainActivity
import com.messages.app.MessagesApp
import com.messages.app.notify.NotificationAvatarGenerator
import com.messages.app.receiver.SmsSentReceiver
import com.messages.core.MessageRepository
import com.messages.core.db.MessageEntity
import com.messages.core.send.SendAggregate
import java.util.concurrent.TimeUnit

/**
 * Shared SMS radio send: divide → fan out to every recipient → status via
 * SmsSentReceiver. Used by the live composer path (ChatViewModel) and the
 * scheduled-send worker. Marks the message FAILED on any throw so the chat
 * shows Resend.
 */
object SmsRadio {
    suspend fun send(context: Context, repo: MessageRepository, entity: MessageEntity) {
        try {
            val sms = com.messages.app.sms.SmsManagers.forSubscription(context, entity.subId)
            val parts = sms.divideMessage(entity.body)
            val wantDelivery = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
                .getBoolean("delivery_reports", true)
            val recipients = repo.recipientsOf(entity.address)
            // R-13: declare the whole dispatch matrix before the radio call, so
            // the message-level status is derived from every (recipient, part)
            // instead of from whichever callback happens to arrive first.
            repo.recordSendAttempts(
                messageId = entity.id,
                recipientCount = recipients.size.coerceAtLeast(1),
                partCount = parts.size.coerceAtLeast(1),
                wantDelivery = wantDelivery,
            )
            recipients.forEachIndexed { recipientIndex, recipient ->
                val (sentIntents, deliveredIntents) =
                    statusIntents(context, entity.id, recipientIndex, parts.size, wantDelivery)
                if (parts.size == 1) {
                    sms.sendTextMessage(
                        recipient, null, entity.body,
                        sentIntents.first(), deliveredIntents?.first(),
                    )
                } else {
                    sms.sendMultipartTextMessage(
                        recipient, null, parts,
                        ArrayList(sentIntents),
                        deliveredIntents?.let { ArrayList(it) },
                    )
                }
            }
        } catch (_: Exception) {
            // Nothing (or only part of the matrix) reached the radio: fail every
            // attempt so the derived status is FAILED, not a half-SENDING row.
            repo.failAllSendAttempts(
                entity.id, com.messages.core.send.SendFailure.LOCAL_SEND_ERROR,
            )
        }
    }

    /**
     * One (sent, delivered) PendingIntent per part, each addressing exactly one
     * attempt. PendingIntent equality ignores extras, so the distinct request
     * code AND the distinct data URI are both required — otherwise the platform
     * collapses every recipient/part into a single intent and one callback
     * decides the whole message's fate.
     */
    private fun statusIntents(
        context: Context,
        messageId: Long,
        recipientIndex: Int,
        partCount: Int,
        wantDelivery: Boolean,
    ): Pair<List<PendingIntent>, List<PendingIntent>?> {
        val sent = ArrayList<PendingIntent>(partCount)
        val delivered = if (wantDelivery) ArrayList<PendingIntent>(partCount) else null
        for (partIndex in 0 until partCount.coerceAtLeast(1)) {
            val attemptId = SendAggregate.attemptId(messageId, recipientIndex, partIndex)
            val code = SendAggregate.requestCode(messageId, recipientIndex, partIndex)
            sent.add(
                statusIntent(
                    context, SmsSentReceiver::class.java, messageId, attemptId, code, "sent",
                )
            )
            delivered?.add(
                statusIntent(
                    context, com.messages.app.receiver.SmsDeliveredReceiver::class.java,
                    messageId, attemptId, code, "delivered",
                )
            )
        }
        return sent to delivered
    }

    private fun statusIntent(
        context: Context,
        receiver: Class<*>,
        messageId: Long,
        attemptId: String,
        requestCode: Int,
        kind: String,
    ): PendingIntent = PendingIntent.getBroadcast(
        context, requestCode,
        Intent(context, receiver)
            .setData(Uri.parse("messages://$kind/$attemptId"))
            .putExtra("messageId", messageId)
            .putExtra("attemptId", attemptId),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
}

/**
 * Fires at the scheduled time: promote the index-only SCHEDULED row into the
 * Telephony provider, then radio-send. No-ops when the message was cancelled
 * or already sent via "Send now" (promote returns null).
 */
class ScheduledSendWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val messageId = inputData.getLong(Scheduler.KEY_MESSAGE_ID, -1)
        if (messageId == -1L) return Result.failure()
        val repo = MessageRepository.get(applicationContext)
        val entity = repo.promoteScheduledToSending(messageId) ?: return Result.success()
        SmsRadio.send(applicationContext, repo, entity)
        return Result.success()
    }
}

/**
 * V2-48: one automatic retry of a failed send.
 *
 * The claim is the safety property. `claimFailedForResend` is a compare-and-set
 * on FAILED, so if the user pressed Resend while this worker was waiting — or
 * if two workers somehow raced — exactly one send happens and the rest return
 * success having done nothing. That is why this returns `Result.success()` on a
 * null claim rather than retrying: there is nothing left to do, and a
 * WorkManager retry here would be a second attempt at a message someone else
 * already took.
 *
 * A failure that happens *during* this send arms the next retry through the
 * normal receiver path, so the budget is enforced in one place
 * ([SendRetry.MAX_AUTO_RETRIES]) rather than by this worker counting.
 */
class SendRetryWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val messageId = inputData.getLong(Scheduler.KEY_MESSAGE_ID, -1)
        if (messageId == -1L) return Result.failure()
        val repo = MessageRepository.get(applicationContext)
        val entity = repo.claimFailedForResend(messageId) ?: return Result.success()
        SmsRadio.send(applicationContext, repo, entity)
        return Result.success()
    }
}

/**
 * Snooze / remind-me-about-this-message (§8.2): re-surface the message as a
 * reminder notification at the chosen time.
 */
class SnoozeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val messageId = inputData.getLong(Scheduler.KEY_MESSAGE_ID, -1)
        if (messageId == -1L) return Result.failure()
        val repo = MessageRepository.get(ctx)
        // V2-6: deleted meanwhile → nothing to remind about. open() is a no-op
        // unless the row is a sealed locked one, in which case `hidden` below
        // suppresses the body anyway — but the name lookup and the fallback
        // paths both read it, so it is opened once here rather than guessed at.
        val msg = repo.db.messages().byId(messageId)
            ?.let { com.messages.core.secret.LockedContent.open(ctx, it) }
            ?: return Result.success()
        if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val name = repo.displayNameFor(msg.address) ?: msg.address
        // Secret locked space: reminders for locked-space messages carry no
        // sender, no content, and no deep link into the chat.
        val inLockedSpace = msg.space == com.messages.core.db.Spaces.LOCKED
        // Respect hide-previews / legacy locked conversations (§8.2)
        val conversationLocked = inLockedSpace ||
            repo.db.conversations().byThreadId(msg.threadId)?.locked == true
        val hidden = com.messages.app.security.AppLock.hidePreviews(ctx) || conversationLocked
        val title = if (conversationLocked) ctx.getString(R.string.reminder_title)
        else ctx.getString(R.string.reminder_title_named, name)
        val body = if (hidden) ctx.getString(R.string.reminder_body_hidden) else msg.body
        // V2-31: `threadId.toInt()` truncated the id and the intent differed
        // from the message notification's only by extras, which `filterEquals`
        // ignores — so a reminder could open the wrong conversation. Distinct
        // data URI, request code over the whole 64 bits.
        val openIntent = PendingIntent.getActivity(
            ctx,
            com.messages.app.notify.NotificationIds.requestCode(messageId, "snooze"),
            Intent(ctx, MainActivity::class.java).apply {
                if (!inLockedSpace) putExtra("threadId", msg.threadId)
                data = android.net.Uri.parse(
                    com.messages.app.notify.NotificationIds.actionUri(messageId, "snooze")
                )
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(ctx, MessagesApp.CH_REMINDERS)
            .setSmallIcon(com.messages.app.R.drawable.ic_notif_reminder)
            .setLargeIcon(NotificationAvatarGenerator.getAppIconBitmap(ctx))
            .setColor(0xFF5A41DD.toInt())
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        // The tag used to be a single shared "snooze" with `messageId.toInt()`
        // as the id, so two messages whose ids differ above bit 32 replaced
        // each other's reminder. Tag per message, constant id.
        NotificationManagerCompat.from(ctx).notify(
            com.messages.app.notify.NotificationIds.reminderTag(messageId),
            com.messages.app.notify.NotificationIds.ID_REMINDER,
            notification,
        )
        return Result.success()
    }
}

object Scheduler {
    const val KEY_MESSAGE_ID = "messageId"

    private fun app(context: Context) = context.applicationContext as Application

    /** Queue the send worker for [entity] at [sendAt] (unique per message). */
    fun scheduleSend(context: Context, messageId: Long, sendAt: Long) {
        val delay = (sendAt - System.currentTimeMillis()).coerceAtLeast(0)
        WorkManager.getInstance(app(context)).enqueueUniqueWork(
            "scheduled_send_$messageId",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<ScheduledSendWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_MESSAGE_ID to messageId))
                .build(),
        )
    }

    /** Cancel a pending scheduled send (the DB row is handled by the caller). */
    fun cancelSend(context: Context, messageId: Long) {
        WorkManager.getInstance(app(context)).cancelUniqueWork("scheduled_send_$messageId")
    }

    /**
     * V2-48: queue one automatic retry of a failed send, [delayMs] from now.
     *
     * Unique per message and REPLACE, so an arriving failure for a second part
     * of the same message cannot stack a second worker on top of the first —
     * two workers would both claim, and only one would win, but the loser would
     * still have spun up the radio path for nothing.
     *
     * No network constraint: the failures on [SendRetry]'s allowlist are radio
     * and carrier conditions, and WorkManager's connectivity signal describes
     * data, not the SMS bearer. Constraining on it would postpone retries on a
     * device that can send perfectly well over 2G with mobile data off.
     */
    fun scheduleRetry(context: Context, messageId: Long, delayMs: Long) {
        WorkManager.getInstance(app(context)).enqueueUniqueWork(
            "send_retry_$messageId",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SendRetryWorker>()
                .setInitialDelay(delayMs.coerceAtLeast(0), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_MESSAGE_ID to messageId))
                .build(),
        )
    }

    /** V2-48: the user took over — drop any automatic retry still queued. */
    fun cancelRetry(context: Context, messageId: Long) {
        WorkManager.getInstance(app(context)).cancelUniqueWork("send_retry_$messageId")
    }

    /** Remind me about this message at [remindAt]. */
    fun snooze(context: Context, messageId: Long, remindAt: Long) {
        val delay = (remindAt - System.currentTimeMillis()).coerceAtLeast(0)
        WorkManager.getInstance(app(context)).enqueueUniqueWork(
            "snooze_$messageId",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<SnoozeWorker>()
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_MESSAGE_ID to messageId))
                .build(),
        )
    }
}
