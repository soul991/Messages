package com.messages.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.messages.core.cleanup.OtpCleanup
import com.messages.core.cleanup.SpamCleanup
import com.messages.core.search.FtsBackfill
import com.messages.core.trash.TrashRetention

class MessagesApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        com.messages.app.ui.home.SwipeActions.init(this)
        com.messages.app.ui.common.DraftStore.init(this)
        OtpCleanup.ensureScheduled(this)
        SpamCleanup.ensureScheduled(this)
        TrashRetention.ensureScheduled(this)
        FtsBackfill.ensureScheduled(this)
        com.messages.app.drive.DriveBackup.reschedule(this)
        com.messages.core.contacts.ContactSync.ensureObserver(this)
    }

    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_PERSONAL, "Personal & important", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Messages from people and OTPs"
                },
                NotificationChannel(CH_TRANSACTIONS, "Transactions", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Bank alerts, receipts, bills"
                },
                NotificationChannel(CH_REVIEW, "Review folder", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Batched quiet notification for gray-zone messages"
                },
                NotificationChannel(CH_REMINDERS, "Reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Snoozed message reminders"
                },
                NotificationChannel(CH_PROMOTIONS, "Promotions", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Optional alerts for promotional messages"
                },
                // Phase 4 item 19 (Truecaller report rec A4): fraud warnings are
                // the ONE exception to "filtered folders stay silent" — ordinary
                // spam still never notifies; this channel is only for Dangerous
                // verdicts and is governed by the default-on warn setting.
                NotificationChannel(CH_FRAUD, "Fraud warnings", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Warnings about dangerous, likely-fraudulent messages"
                },
                // Spam/Blocked have NO channel — they are silent, badge only (§4)
            )
        )
    }

    companion object {
        const val CH_PERSONAL = "personal"
        const val CH_TRANSACTIONS = "transactions"
        const val CH_PROMOTIONS = "promotions"
        const val CH_REVIEW = "review"
        const val CH_REMINDERS = "reminders"
        const val CH_FRAUD = "fraud_warnings"
    }
}
