package com.personal.detectivedialer

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class DetectiveDialerApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val screened = NotificationChannel(
            CHANNEL_SCREENED,
            "Screened calls",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Summaries of calls handled by your AI assistant" }

        val urgent = NotificationChannel(
            CHANNEL_URGENT,
            "Urgent calls",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Possible emergencies — shown immediately" }

        val messages = NotificationChannel(
            CHANNEL_MESSAGES,
            "Messages",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply { description = "Incoming SMS (when Detective Dialer is the default SMS app)" }

        // High-importance channel for the ringing/ongoing call notification. The
        // full-screen intent on this channel wakes the screen for incoming calls.
        val incoming = NotificationChannel(
            CHANNEL_INCOMING,
            "Incoming calls",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = "Ringing and ongoing calls"
            setShowBadge(false)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val missed = NotificationChannel(
            CHANNEL_MISSED,
            "Missed calls",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = "Calls you missed, with a one-tap callback" }

        // Delete the channel left behind by the old keep-alive foreground service.
        nm.deleteNotificationChannel("screening_service")
        nm.createNotificationChannels(listOf(screened, urgent, messages, incoming, missed))
    }

    companion object {
        const val CHANNEL_SCREENED = "screened_calls"
        const val CHANNEL_URGENT = "urgent_calls"
        const val CHANNEL_MESSAGES = "messages"
        const val CHANNEL_INCOMING = "incoming_calls"
        const val CHANNEL_MISSED = "missed_calls"
    }
}
