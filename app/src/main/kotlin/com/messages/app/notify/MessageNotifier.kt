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
            Category.SPAM -> {
                // Phase 4 item 19 (Truecaller rec A4): a persistent red warning
                // for DANGEROUS verdicts only — fraud combos / dangerous
                // threshold. Ordinary spam and promos stay silent forever.
                if (verdict.dangerous &&
                    prefs.getBoolean("warn_dangerous", true)
                ) {
                    postFraudWarning(message, contactName, hidden, conversationLocked)
                }
            }
            Category.BLOCKED -> Unit // silent (§4)
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

        // Phase 5: extracted-datum heroes. Never when the preview is hidden or
        // the chat is locked — the code/amount IS the content (hidden covers
        // both). Deterministic only: OTP needs an extracted code; Transactions
        // need exactly one distinct amount, anything else degrades to plain.
        val otpCode = if (
            verdict.protectedLabel == com.messages.protection.ProtectedLabel.OTP && !hidden
        ) com.messages.protection.OtpExtractor.extract(message.body) else null
        val heroTitle = when {
            otpCode != null -> "$otpCode — $title"
            channel == MessagesApp.CH_TRANSACTIONS && !hidden ->
                com.messages.protection.Normalizer.normalize(message.body)
                    .amounts.distinct().singleOrNull()?.let { "$it — $title" }
            else -> null
        }

        val builder = NotificationCompat.Builder(context, effectiveChannel)
            .setSmallIcon(R.drawable.ic_notif_message)
            .setContentTitle(heroTitle ?: title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .addAction(0, "Mark as read", markRead)

        // MessagingStyle renders its own sender line, which would override the
        // hero title — hero notifications use BigTextStyle instead (the body
        // stays readable, de-emphasized under the code/amount).
        if (heroTitle != null) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(text))
        } else {
            builder.setStyle(style)
        }

        // One-tap OTP copy on the notification itself (Phase 4 item 1).
        if (otpCode != null) {
            val copyOtp = PendingIntent.getBroadcast(
                context, (message.threadId * 10 + 2).toInt(),
                Intent(context, NotificationActionReceiver::class.java).apply {
                    putExtra("action", "copy_otp")
                    putExtra("threadId", message.threadId)
                    putExtra("otp", otpCode)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(0, "Copy $otpCode", copyOtp)
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

    /**
     * Persistent red fraud warning (Phase 4 item 19): does not auto-clear on
     * tap (setAutoCancel false) but IS user-dismissable — never setOngoing.
     * Red is reserved for fraud; ordinary spam never triggers this.
     */
    private fun postFraudWarning(
        message: MessageEntity,
        contactName: String?,
        hidden: Boolean,
        conversationLocked: Boolean,
    ) {
        val sender = if (conversationLocked) "a locked conversation"
        else contactName ?: message.address
        val openIntent = PendingIntent.getActivity(
            context, (FRAUD_ID_BASE - message.threadId).toInt(),
            Intent(context, MainActivity::class.java).apply {
                putExtra("threadId", message.threadId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Advice-first copy (Phase 5): what to do leads, attribution follows.
        val body = if (hidden) {
            "Don't tap links or share codes. A message was flagged as likely " +
                "fraud and filed in Spam."
        } else {
            "Don't tap links, call back, or share OTPs, PINs, or card details. " +
                "Likely fraud from $sender — filed in Spam."
        }
        val builder = NotificationCompat.Builder(context, MessagesApp.CH_FRAUD)
            .setSmallIcon(R.drawable.ic_notif_fraud)
            .setContentTitle("⚠️ Dangerous message blocked")
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openIntent)
            // Persistent until the user swipes it away (Truecaller pattern):
            // tapping opens the chat but the warning stays.
            .setAutoCancel(false)
            .setColor(0xFFD32F2F.toInt())
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        NotificationManagerCompat.from(context)
            .notify((FRAUD_ID_BASE - message.threadId).toInt(), builder.build())
    }

    /** One quiet, batched low-priority notification for the Review folder. */
    private fun postReviewNotification() {        val openIntent = PendingIntent.getActivity(
            context, REVIEW_ID,
            Intent(context, MainActivity::class.java).apply { putExtra("folder", "REVIEW") },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, MessagesApp.CH_REVIEW)
            .setSmallIcon(R.drawable.ic_notif_review)
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

        /** Fraud-warning ids live far below thread-id space (item 19). */
        private const val FRAUD_ID_BASE = -1_000_000L

        /** RemoteInput result key for the inline reply action. */
        const val KEY_REPLY = "key_reply_text"
    }
}
