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
 * The outcome of the notification routing decision for a non-locked message.
 * Returned by [MessageNotifier.routingAction]; used by [MessageNotifier.notifyFor]
 * and tested independently of Android platform machinery.
 */
internal sealed class NotifyAction {
    /** No alert, no sound, no heads-up — badge/shade update only (driven by Room). */
    object Silent : NotifyAction()
    /** Post a full message notification on [channel]. */
    data class Post(val channel: String) : NotifyAction()
    /** Aggregate-batch the Review folder count (IMPORTANCE_LOW, no sound/heads-up). */
    object ReviewBatch : NotifyAction()
    /**
     * Fraud warning on [MessagesApp.CH_FRAUD] (IMPORTANCE_HIGH, persistent).
     * Applies only to SPAM verdicts that carry [Verdict.dangerous] == true
     * and when the user has the fraud-warning toggle on (default on).
     * Phase 4 item 19 / Truecaller rec A4 — the ONE exception to
     * "filtered folders stay silent."
     */
    object FraudWarning : NotifyAction()
}

/**
 * §3/§4 notification policy: Inbox/Transactions notify; Promotions, Spam and
 * Blocked are silent (badge only); Review gets one quiet batched notification.
 */
class MessageNotifier(private val context: Context) {

    suspend fun notifyFor(message: MessageEntity, verdict: Verdict, contactName: String?) {
        // Secret locked space: a message routed to the locked space must not
        // produce ANY identifying notification — no sender, no content, no
        // per-thread id, no shortcut/bubble/reply, no channel of its own.
        // Either one generic "New message" (default) or nothing at all,
        // per the setting inside the locked folder. Fraud warnings are
        // suppressed too: a red warning naming the thread would betray it.
        if (message.space == com.messages.core.db.Spaces.LOCKED) {
            postLockedSpaceNotification(message, verdict)
            return
        }
        val conversation = MessageRepository.get(context)
            .db.conversations().byThreadId(message.threadId)
        val conversationLocked = conversation?.locked == true

        // OTP auto-copy (Phase 4 item 1, opt-in): runs before the permission
        // gate so it works even with notifications denied.
        if (mayAutoCopyOtp(verdict, conversationLocked)) {
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

        when (val action = routingAction(
            verdict.category,
            dangerous = verdict.dangerous,
            notifyTransactions = notifyTransactions,
            notifyPromotions = notifyPromotions,
            notifyReview = notifyReview,
            warnDangerous = prefs.getBoolean("warn_dangerous", true),
        )) {
            NotifyAction.Silent -> Unit
            is NotifyAction.Post -> postMessageNotification(
                message, verdict, contactName, action.channel, hidden, conversationLocked,
            )
            NotifyAction.ReviewBatch -> postReviewNotification()
            NotifyAction.FraudWarning -> postFraudWarning(message, contactName, hidden, conversationLocked)
        }
    }

    /**
     * R-30: whether this message may have its OTP put on the global clipboard
     * without the user asking. Auto-copy happens *before* the user has looked
     * at the message, so the decision has to be made from the verdict alone.
     *
     * Refused for:
     *  - locked-space messages — handled earlier by the [postLockedSpaceNotification]
     *    return, so they never reach here;
     *  - legacy-locked conversations ([conversationLocked]) — content behind the
     *    auth gate must not leave it;
     *  - dangerous / fraud-warning verdicts — a "code" inside a phishing lure is
     *    the attacker's payload, and pre-filling the clipboard with it is exactly
     *    the assist they want;
     *  - anything not in a trusted category — an OTP-shaped string in a Spam or
     *    Blocked message is not an OTP we should act on.
     *
     * The notification's explicit Copy action stays available in every case, so
     * refusing here costs the user one tap, never the code itself.
     */
    private fun mayAutoCopyOtp(verdict: Verdict, conversationLocked: Boolean): Boolean {
        if (verdict.protectedLabel != com.messages.protection.ProtectedLabel.OTP) return false
        if (conversationLocked) return false
        if (verdict.dangerous || verdict.fraudWarningBanner) return false
        if (verdict.category != Category.INBOX && verdict.category != Category.TRANSACTIONS) return false
        return OtpClipboard.autoCopyEnabled(context)
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
        val title = if (conversationLocked) context.getString(R.string.app_name)
        else (contactName ?: message.address)
        // V2-31: every PendingIntent below carries a per-(thread, action) data
        // URI. `Intent.filterEquals` ignores extras, so without it two threads'
        // action intents are the same intent, and a request-code collision
        // hands one conversation an action carrying the other's threadId.
        val openIntent = PendingIntent.getActivity(
            context, NotificationIds.requestCode(message.threadId, "open"),
            Intent(context, MainActivity::class.java).apply {
                putExtra("threadId", message.threadId)
                data = android.net.Uri.parse(NotificationIds.actionUri(message.threadId, "open"))
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val markRead = PendingIntent.getBroadcast(
            context, NotificationIds.requestCode(message.threadId, "mark_read"),
            Intent(context, NotificationActionReceiver::class.java).apply {
                putExtra("action", "mark_read")
                putExtra("threadId", message.threadId)
                data = android.net.Uri.parse(
                    NotificationIds.actionUri(message.threadId, "mark_read")
                )
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val person = Person.Builder().setName(title).apply {
            // Contact photo on the notification (never for locked chats —
            // their identity must not surface outside the app).
            if (!conversationLocked) contactPhotoIcon(message.address)?.let { setIcon(it) }
        }.build()
        val text = when {
            hidden -> context.getString(R.string.notif_new_message)
            verdict.fraudWarningBanner ->
                context.getString(R.string.notif_suspicious_link, message.body)
            else -> message.body
        }
        val style = NotificationCompat.MessagingStyle(Person.Builder().setName(context.getString(R.string.notif_you)).build())
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

        val notifColor = when (verdict.category) {
            Category.TRANSACTIONS -> 0xFF10B981.toInt() // Lush Emerald Green
            Category.PROMOTIONS -> 0xFFD97706.toInt()   // Amber
            Category.SPAM -> 0xFFD32F2F.toInt()         // Red
            Category.REVIEW -> 0xFF4B5563.toInt()       // Slate
            Category.INBOX, Category.BLOCKED -> 0xFF5A41DD.toInt() // Brand signature Violet
        }

        val builder = NotificationCompat.Builder(context, effectiveChannel)
            .setSmallIcon(R.drawable.ic_notif_message)
            .setColor(notifColor)
            .setContentTitle(heroTitle ?: title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .addAction(R.drawable.ic_notif_mark_read, context.getString(R.string.notif_mark_as_read), markRead)

        // Set high-resolution circular avatar (photo or branded monogram)
        if (!conversationLocked) {
            val photoBitmap = try {
                MessageRepository.get(context).lookupContact(message.address)?.photoUri?.let { uriStr ->
                    context.contentResolver.openInputStream(android.net.Uri.parse(uriStr))?.use {
                        android.graphics.BitmapFactory.decodeStream(it)
                    }
                }
            } catch (_: Exception) { null }

            val largeBitmap = photoBitmap ?: NotificationAvatarGenerator.generateBitmap(
                context, message.address, contactName, verdict.category,
            )
            builder.setLargeIcon(largeBitmap)
        }

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
                context, NotificationIds.requestCode(message.threadId, "copy_otp"),
                Intent(context, NotificationActionReceiver::class.java).apply {
                    putExtra("action", "copy_otp")
                    putExtra("threadId", message.threadId)
                    putExtra("otp", otpCode)
                    data = android.net.Uri.parse(
                        NotificationIds.actionUri(message.threadId, "copy_otp")
                    )
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            builder.addAction(R.drawable.ic_notif_copy, context.getString(R.string.notif_copy_otp, otpCode), copyOtp)
        }

        // Inline reply (Phase 4 item 2): only for senders that can actually
        // receive SMS (never DLT/alpha headers — §canReceiveReplies, same rule
        // as the composer) and never for locked conversations. Groups reply
        // only when every recipient is replyable, matching ChatScreen.
        val replyable = !conversationLocked &&
            message.address.split(';').all { it.isNotBlank() && SenderAnalyzer.canReceiveReplies(it) }
        if (replyable) {
            val remoteInput = RemoteInput.Builder(KEY_REPLY).setLabel(context.getString(R.string.notif_reply)).build()
            val replyIntent = PendingIntent.getBroadcast(
                context, NotificationIds.requestCode(message.threadId, "reply"),
                Intent(context, NotificationActionReceiver::class.java).apply {
                    putExtra("action", "reply")
                    putExtra("threadId", message.threadId)
                    data = android.net.Uri.parse(
                        NotificationIds.actionUri(message.threadId, "reply")
                    )
                },
                // RemoteInput results are appended by the system → must be mutable.
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            builder.addAction(
                NotificationCompat.Action.Builder(R.drawable.ic_notif_reply, context.getString(R.string.notif_reply), replyIntent)
                    .addRemoteInput(remoteInput)
                    .setAllowGeneratedReplies(false)
                    .setSemanticAction(NotificationCompat.Action.SEMANTIC_ACTION_REPLY)
                    .build()
            )
        }

        if (shortcutId != null) builder.setShortcutId(shortcutId)

        when (badge) {
            com.messages.protection.SenderBadges.Badge.VERIFIED ->
                builder.setSubText(context.getString(R.string.notif_verified_sender))
            com.messages.protection.SenderBadges.Badge.BUSINESS ->
                builder.setSubText(context.getString(R.string.notif_business))
            null -> Unit
        }

        // Conversation bubbles (§8.2, Android 11+). Skipped while app lock is
        // on (a bubble would bypass the lock screen) and for locked chats.
        if (Build.VERSION.SDK_INT >= 30 && shortcutId != null && !AppLock.isEnabled(context)) {
            val bubbleIntent = PendingIntent.getActivity(
                context, NotificationIds.requestCode(message.threadId, "bubble"),
                Intent(context, BubbleActivity::class.java).apply {
                    putExtra("threadId", message.threadId)
                    // Distinct data URI so PendingIntents don't collide across threads.
                    data = android.net.Uri.parse(
                        NotificationIds.actionUri(message.threadId, "bubble")
                    )
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

        // V2-31: tag carries the whole 64-bit thread id; the id is a per-kind
        // constant. `(tag, id)` is the platform's identity pair, so two threads
        // can never share a notification and none of them can land on the
        // fixed-id Review / locked-space notifications (which carry no tag).
        NotificationManagerCompat.from(context).notify(
            NotificationIds.threadTag(message.threadId),
            NotificationIds.ID_MESSAGE,
            builder.build(),
        )
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
        val sender = if (conversationLocked) context.getString(R.string.notif_locked_sender)
        else contactName ?: message.address
        val openIntent = PendingIntent.getActivity(
            context, NotificationIds.requestCode(message.threadId, "fraud"),
            Intent(context, MainActivity::class.java).apply {
                putExtra("threadId", message.threadId)
                data = android.net.Uri.parse(NotificationIds.actionUri(message.threadId, "fraud"))
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        // Advice-first copy (Phase 5): what to do leads, attribution follows.
        val body = if (hidden) {
            context.getString(R.string.notif_fraud_body_hidden)
        } else {
            context.getString(R.string.notif_fraud_body, sender)
        }
        val builder = NotificationCompat.Builder(context, MessagesApp.CH_FRAUD)
            .setSmallIcon(R.drawable.ic_notif_fraud)
            .setContentTitle(context.getString(R.string.notif_fraud_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(openIntent)
            // Persistent until the user swipes it away (Truecaller pattern):
            // tapping opens the chat but the warning stays.
            .setAutoCancel(false)
            .setColor(0xFFD32F2F.toInt())
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
        NotificationManagerCompat.from(context).notify(
            NotificationIds.threadTag(message.threadId),
            NotificationIds.ID_FRAUD,
            builder.build(),
        )
    }

    /**
     * Secret locked space: one fixed-id, content-free notification. Says only
     * "New message"; tapping opens the app at Home (never deep-links into the
     * locked chat — the space's own credential gate is the only way in).
     * Categories that are silent in the normal space stay silent here too
     * (spam/promos/review/blocked never notify from the locked space), and
     * the per-space setting can silence even the generic ping entirely.
     */
    private suspend fun postLockedSpaceNotification(message: MessageEntity, verdict: Verdict) {
        if (!hasPermission()) return
        if (com.messages.core.secret.SecretSpace.notifyMode(context) ==
            com.messages.core.secret.SecretSpace.NOTIFY_OFF
        ) return
        val conversation = MessageRepository.get(context)
            .db.conversations().byThreadId(message.threadId, com.messages.core.db.Spaces.LOCKED)
        if (conversation?.muted == true) return
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val shouldNotify = when (verdict.category) {
            Category.INBOX -> true
            Category.TRANSACTIONS -> prefs.getBoolean("notify_transactions", true)
            else -> false // Promotions/Spam/Review/Blocked: silent, and no fraud banner either
        }
        if (!shouldNotify) return
        val openIntent = PendingIntent.getActivity(
            context, LOCKED_SPACE_ID,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, MessagesApp.CH_PERSONAL)
            .setSmallIcon(R.drawable.ic_notif_message)
            .setColor(0xFF5A41DD.toInt())
            .setContentTitle(context.getString(R.string.app_name))
            .setContentText(context.getString(R.string.notif_new_message))
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
        NotificationManagerCompat.from(context).notify(LOCKED_SPACE_ID, builder.build())
    }

    /** One quiet, batched low-priority notification for the Review folder. */
    private fun postReviewNotification() {        val openIntent = PendingIntent.getActivity(
            context, REVIEW_ID,
            Intent(context, MainActivity::class.java).apply { putExtra("folder", "REVIEW") },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(context, MessagesApp.CH_REVIEW)
            .setSmallIcon(R.drawable.ic_notif_review)
            .setColor(0xFF4B5563.toInt())
            .setContentTitle(context.getString(R.string.notif_review_batch_title))
            .setContentText(context.getString(R.string.notif_review_batch_body))
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

        /**
         * Pure routing function — no Android context, no I/O, no side effects.
         *
         * Maps a classification verdict to the [NotifyAction] that
         * [notifyFor] should take. Extracted for testability: all routing
         * logic lives here; the suspending public method only handles the
         * locked-space early return, OTP auto-copy, and Android side effects.
         *
         * [warnDangerous] reflects the "warn about dangerous messages" toggle
         * (default on). It is consulted only inside the SPAM branch — other
         * categories have their own independent toggles ([notifyTransactions],
         * [notifyPromotions], [notifyReview]).
         */
        internal fun routingAction(
            category: Category,
            dangerous: Boolean,
            notifyTransactions: Boolean,
            notifyPromotions: Boolean,
            notifyReview: Boolean,
            warnDangerous: Boolean,
        ): NotifyAction = when (category) {
            Category.INBOX -> NotifyAction.Post(MessagesApp.CH_PERSONAL)
            Category.TRANSACTIONS ->
                if (notifyTransactions) NotifyAction.Post(MessagesApp.CH_TRANSACTIONS)
                else NotifyAction.Silent
            Category.REVIEW ->
                if (notifyReview) NotifyAction.ReviewBatch
                else NotifyAction.Silent
            Category.PROMOTIONS ->
                if (notifyPromotions) NotifyAction.Post(MessagesApp.CH_PROMOTIONS)
                else NotifyAction.Silent
            Category.SPAM ->
                if (dangerous && warnDangerous) NotifyAction.FraudWarning
                else NotifyAction.Silent
            Category.BLOCKED -> NotifyAction.Silent
        }

        /** Single shared id for ALL locked-space pings — never per-thread.
         *  Public: the Reset flow cancels it during the wipe. */
        const val LOCKED_SPACE_ID = -200

        /** RemoteInput result key for the inline reply action. */
        const val KEY_REPLY = "key_reply_text"

        /**
         * Cancel EVERY notification a thread can own (R-15).
         *
         * The fraud warning is a second notification under the same tag, so
         * callers that cancelled only the message left the red banner on screen
         * after the user marked the message as not spam. One API so no call site
         * has to know the id scheme.
         *
         * V2-31: the legacy numeric ids are cancelled too. Notifications posted
         * by the previous version are on screen across the upgrade, and nothing
         * else would ever clear them.
         */
        fun cancelThread(context: Context, threadId: Long) {
            NotificationManagerCompat.from(context).apply {
                val tag = NotificationIds.threadTag(threadId)
                cancel(tag, NotificationIds.ID_MESSAGE)
                cancel(tag, NotificationIds.ID_FRAUD)
                cancel(NotificationIds.legacyMessageId(threadId))
                cancel(NotificationIds.legacyFraudId(threadId))
            }
        }
    }
}
