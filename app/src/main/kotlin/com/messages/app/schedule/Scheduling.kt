package com.messages.app.schedule

import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
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
import com.messages.app.receiver.SmsSentReceiver
import com.messages.core.MessageRepository
import com.messages.core.db.MessageEntity
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
            val base = context.getSystemService(SmsManager::class.java)
            val sms = entity.subId?.let { base.createForSubscriptionId(it) } ?: base
            val parts = sms.divideMessage(entity.body)
            val sentIntent = PendingIntent.getBroadcast(
                context, entity.id.toInt(),
                Intent(context, SmsSentReceiver::class.java).putExtra("messageId", entity.id),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            repo.recipientsOf(entity.address).forEach { recipient ->
                if (parts.size == 1) {
                    sms.sendTextMessage(recipient, null, entity.body, sentIntent, null)
                } else {
                    sms.sendMultipartTextMessage(
                        recipient, null, parts,
                        ArrayList(parts.map { sentIntent }), null,
                    )
                }
            }
        } catch (_: Exception) {
            repo.db.messages().update(entity.copy(sendStatus = "FAILED"))
        }
    }
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
        val msg = repo.db.messages().byId(messageId) ?: return Result.success() // deleted meanwhile
        if (ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return Result.success()

        val name = repo.displayNameFor(msg.address) ?: msg.address
        val openIntent = PendingIntent.getActivity(
            ctx, msg.threadId.toInt(),
            Intent(ctx, MainActivity::class.java).apply {
                putExtra("threadId", msg.threadId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(ctx, MessagesApp.CH_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle("Reminder · $name")
            .setContentText(msg.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg.body))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(ctx).notify(SNOOZE_TAG, messageId.toInt(), notification)
        return Result.success()
    }

    companion object {
        const val SNOOZE_TAG = "snooze"
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
