package com.messages.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

class MessagesApp : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
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
                // Promotions/Spam/Blocked have NO channel — they are silent, badge only (§4)
            )
        )
    }

    companion object {
        const val CH_PERSONAL = "personal"
        const val CH_TRANSACTIONS = "transactions"
        const val CH_REVIEW = "review"
    }
}
