package com.messages.app.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.content.ContextCompat
import com.messages.app.MessagesApp
import com.messages.app.MainActivity
import com.messages.app.receiver.NotificationActionReceiver
import com.messages.app.security.AppLock
import com.messages.core.MessageRepository
import com.messages.core.db.MessageEntity
import com.messages.protection.Category
import com.messages.protection.Verdict

/**
 * §3/§4 notification policy: Inbox/Transactions notify; Promotions, Spam and
 * Blocked are silent (badge only); Review gets one quiet batched notification.
 */
class MessageNotifier(private val context: Context) {

    suspend fun notifyFor(message: MessageEntity, verdict: Verdict, contactName: String?) {
        if (!hasPermission()) return
        // Hide previews (§8.2): global setting, or this conversation is locked.
        val conversationLocked = MessageRepository.get(context)
            .db.conversations().byThreadId(message.threadId)?.locked == true
        val hidden = AppLock.hidePreviews(context) || conversationLocked
        when (verdict.category) {
            Category.INBOX -> postMessageNotification(
                message, verdict, contactName, MessagesApp.CH_PERSONAL, hidden, conversationLocked,
            )
            Category.TRANSACTIONS -> postMessageNotification(
                message, verdict, contactName, MessagesApp.CH_TRANSACTIONS, hidden, conversationLocked,
            )
            Category.REVIEW -> postReviewNotification()
            Category.PROMOTIONS, Category.SPAM, Category.BLOCKED -> Unit // silent (§4)
        }
    }

    private fun postMessageNotification(
        message: MessageEntity,
        verdict: Verdict,
        contactName: String?,
        channel: String,
        hidden: Boolean,
        conversationLocked: Boolean,
    ) {
        // Locked conversations hide the sender too; hide-previews keeps it.
        val title = if (conversationLocked) "Messages" else (contactName ?: message.address)
        val openIntent = PendingIntent.getActivity(
            context, message.threadId.toInt(),
            Intent(context, MainActivity::class.java).apply {
                putExtra("threadId", message.threadId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val markRead = PendingIntent.getBroadcast(
            context, (message.threadId * 10 + 1).toInt(),
            Intent(context, NotificationActionReceiver::class.java).apply {
                putExtra("action", "mark_read")
                putExtra("threadId", message.threadId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val person = Person.Builder().setName(title).build()
        val text = when {
            hidden -> "New message"
            verdict.fraudWarningBanner ->
                "⚠️ Caution: contains a suspicious link — ${message.body}"
            else -> message.body
        }
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
            .addMessage(text, message.timestamp, person)

        val builder = NotificationCompat.Builder(context, channel)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .addAction(0, "Mark as read", markRead)
            .setShortcutId("thread_${message.threadId}")

        NotificationManagerCompat.from(context).notify(message.threadId.toInt(), builder.build())
    }

    /** One quiet, batched low-priority notification for the Review folder. */
    private fun postReviewNotification() {
        val openIntent = PendingIntent.getActivity(
            context, REVIEW_ID,
            Intent(context, MainActivity::class.java).apply { putExtra("folder", "REVIEW") },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, MessagesApp.CH_REVIEW)
            .setSmallIcon(android.R.drawable.sym_action_email)
            .setContentTitle("Messages to review")
            .setContentText("New messages are waiting in your Review folder")
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        NotificationManagerCompat.from(context).notify(REVIEW_ID, builder.build())
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    companion object {
        private const val REVIEW_ID = -100
    }
}
