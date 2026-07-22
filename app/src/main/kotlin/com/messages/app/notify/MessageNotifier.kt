package com.messages.app.notify

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.messages.protection.SenderAnalyzer
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.messages.app.BubbleActivity
import com.messages.app.MessagesApp
import com.messages.app.MainActivity
import com.messages.app.R
import com.messages.app.receiver.NotificationActionReceiver
import com.messages.app.security.AppLock
import com.messages.app.shortcut.ConversationShortcuts
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
        val conversation = MessageRepository.get(context)
            .db.conversations().byThreadId(message.threadId)
        val conversationLocked = conversation?.locked == true

        // OTP auto-copy (Phase 4 item 1, opt-in): runs before the permission
        // gate so it works even with notifications denied. Locked chats are
        // excluded — their content must not leave the app's auth gate.
        if (verdict.protectedLabel == com.messages.protection.ProtectedLabel.OTP &&
            !conversationLocked && OtpClipboard.autoCopyEnabled(context)
        ) {
            com.messages.protection.OtpExtractor.extract(message.body)
                ?.let { OtpClipboard.copy(context, it, toast = false) }
        }

        if (!hasPermission()) return
        // Muted conversations: no alerts of any kind; unread badges still count.
        if (conversation?.muted == true) return
        // Hide previews (§8.2): global setting, or this conversation is locked.
        val hidden = AppLock.hidePreviews(context) || conversationLocked
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val notifyTransactions = prefs.getBoolean("notify_transactions", true)
        val notifyPromotions = prefs.getBoolean("notify_promotions", false)
        val notifyReview = prefs.getBoolean("notify_review", true)

        when (verdict.category) {
            Category.INBOX -> postMessageNotification(
                message, verdict, contactName, MessagesApp.CH_PERSONAL, hidden, conversationLocked,
            )
            Category.TRANSACTIONS -> if (notifyTransactions) {
                postMessageNotification(
                    message, verdict, contactName, MessagesApp.CH_TRANSACTIONS, hidden, conversationLocked,
                )
            }
            Category.REVIEW -> if (notifyReview) {
                postReviewNotification()
            }
            Category.PROMOTIONS -> if (notifyPromotions) {
                postMessageNotification(
                    message, verdict, contactName, MessagesApp.CH_PROMOTIONS, hidden, conversationLocked,
                )
            }
            Category.SPAM, Category.BLOCKED -> Unit // silent (§4)
        }
    }

    /** Contact photo thumbnail as an icon; null for no contact / no photo. */
    private fun contactPhotoIcon(address: String): IconCompat? = try {
        MessageRepository.get(context).lookupContact(address)?.photoUri?.let { uriStr ->
            context.contentResolver.openInputStream(android.net.Uri.parse(uriStr))?.use {
                android.graphics.BitmapFactory.decodeStream(it)
            }?.let { bmp -> IconCompat.createWithBitmap(bmp) }
        }
    } catch (_: Exception) {
        null
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

        val person = Person.Builder().setName(title).apply {
            // Contact photo on the notification (never for locked chats —
            // their identity must not surface outside the app).
            if (!conversationLocked) contactPhotoIcon(message.address)?.let { setIcon(it) }
        }.build()
        val text = when {
            hidden -> "New message"
            verdict.fraudWarningBanner ->
                "⚠️ Caution: contains a suspicious link — ${message.body}"
            else -> message.body
        }
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName("You").build())
            .addMessage(text, message.timestamp, person)

        // Conversation shortcut (§8.2): anchor for launcher shortcuts,
        // direct share, and bubbles. Locked conversations get none — their
        // names must not surface outside the app.
        val shortcutId = if (!conversationLocked) {
            ConversationShortcuts.push(context, message.threadId, title)
        } else null

        // Verified-sender badge (Phase 2): engine-decided; the verdict's
        // dangerous/fraud-warning state suppresses it absolutely, and locked
        // conversations show no sender identity at all.
        val badge = if (conversationLocked) null else com.messages.protection.SenderBadges.badgeFor(
            address = message.address,
            isContact = contactName != null,
            dangerous = verdict.dangerous || verdict.fraudWarningBanner,
            protectedLabel = verdict.protectedLabel.name,
        )

        // Per-conversation channel (Phase 4 item 4): only exists if the user
        // customized this conversation from its detail page. Locked chats
        // always post on the category channel (no identity in system settings).
        val effectiveChannel = if (conversationLocked) channel
        else ConversationChannels.channelFor(context, message.threadId, channel)

        val builder = NotificationCompat.Builder(context, effectiveChannel)
            .setSmallIcon(android.R.drawable.sym_action_chat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(style)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .addAction(0, "Mark as read", markRead)

        // One-tap OTP copy on the notification itself (Phase 4 item 1).
        // Never when the preview is hidden — the code IS the content.
        if (verdict.protectedLabel == com.messages.protection.ProtectedLabel.OTP && !hidden) {
            com.messages.protection.OtpExtractor.extract(message.body)?.let { code ->
                val copyOtp = PendingIntent.getBroadcast(
                    context, (message.threadId * 10 + 2).toInt(),
                    Intent(context, NotificationActionReceiver::class.java).apply {
                        putExtra("action", "copy_otp")
                        putExtra("threadId", message.threadId)
                        putExtra("otp", code)
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                builder.addAction(0, "Copy $code", copyOtp)
            }
        }

        // Inline reply (Phase 4 item 2): only for senders that can actually
        // receive SMS (never DLT/alpha headers — §canReceiveReplies, same rule
        // as the composer) and never for locked conversations. Groups reply
        // only when every recipient is replyable, matching ChatScreen.
        val replyable = !conversationLocked &&
            message.address.split(';').all { it.isNotBlank() && SenderAnalyzer.canReceiveReplies(it) }
        if (replyable) {
            val remoteInput = RemoteInput.Builder(KEY_REPLY).setLabel("Reply").build()
            val replyIntent = PendingIntent.getBroadcast(
                context, (message.threadId * 10 + 3).toInt(),
                Intent(context, NotificationActionReceiver::class.java).apply {
                    putExtra("action", "reply")
                    putExtra("threadId", message.threadId)
                },
                // RemoteInput results are appended by the system → must be mutable.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            builder.addAction(
                NotificationCompat.Action.Builder(0, "Reply", replyIntent)
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(false)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .build()
            )
        }

        if (shortcutId != null) builder.setShortcutId(shortcutId)

        when (badge) {
            com.messages.protection.SenderBadges.Badge.VERIFIED ->
                builder.setSubText("Verified sender ✓")
            com.messages.protection.SenderBadges.Badge.BUSINESS ->
                builder.setSubText("Business")
            null -> Unit
        }

        // Conversation bubbles (§8.2, Android 11+). Skipped while app lock is
        // on (a bubble would bypass the lock screen) and for locked chats.
        if (Build.VERSION.SDK_INT >= 30 && shortcutId != null && !AppLock.isEnabled(context)) {
            val bubbleIntent = PendingIntent.getActivity(
                context, message.threadId.toInt(),
                Intent(context, BubbleActivity::class.java).apply {
                    putExtra("threadId", message.threadId)
                    // Distinct data URI so PendingIntents don't collide across threads.
                    data = android.net.Uri.parse("messages://bubble/${message.threadId}")
                },
                // Bubble intents must be mutable (the system adds bubble extras).
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            builder.bubbleMetadata = NotificationCompat.BubbleMetadata.Builder(
                bubbleIntent,
                IconCompat.createWithResource(context, R.mipmap.ic_launcher),
            )
                .setDesiredHeight(600)
                .build()
        }

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

        /** RemoteInput result key for the inline reply action. */
        const val KEY_REPLY = "key_reply_text"
    }
}
