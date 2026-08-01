package com.messages.app.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import com.messages.app.MessagesApp
import com.messages.app.R

/**
 * Per-conversation notification channels (Phase 4 item 4). A channel is
 * created ONLY when the user asks to customize a conversation's notifications
 * from ContactDetailScreen — never eagerly — so system notification settings
 * don't fill up with one channel per thread. The system channel sheet is then
 * the tone/vibration/importance picker; [MessageNotifier] posts on the thread
 * channel whenever one exists.
 *
 * Locked conversations never get a channel (the channel name would leak the
 * sender into system settings); locking a conversation deletes its channel.
 */
object ConversationChannels {

    fun channelId(threadId: Long) = "thread_$threadId"

    fun exists(context: Context, threadId: Long): Boolean =
        manager(context).getNotificationChannel(channelId(threadId)) != null

    /**
     * Creates the channel named after the conversation and returns its id.
     * On Android 11+ it is linked to the conversation shortcut so the system
     * surfaces it in the Conversations section rather than as a loose channel.
     */
    fun ensure(context: Context, threadId: Long, displayName: String): String {
        val id = channelId(threadId)
        if (manager(context).getNotificationChannel(id) == null) {
            val channel = NotificationChannel(
                id, displayName, NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = context.getString(R.string.notif_channel_conversation_desc)
                if (Build.VERSION.SDK_INT >= 30) {
                    setConversationId(MessagesApp.CH_PERSONAL, id)
                }
            }
            manager(context).createNotificationChannel(channel)
        }
        return id
    }

    /** Removes the customization; the conversation falls back to its category channel. */
    fun remove(context: Context, threadId: Long) {
        manager(context).deleteNotificationChannel(channelId(threadId))
    }

    /** The channel to post on: the thread's custom channel if the user made one. */
    fun channelFor(context: Context, threadId: Long, categoryChannel: String): String =
        if (exists(context, threadId)) channelId(threadId) else categoryChannel

    private fun manager(context: Context): NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
