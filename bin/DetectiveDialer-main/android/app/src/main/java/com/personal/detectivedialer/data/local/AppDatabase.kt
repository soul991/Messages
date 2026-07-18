package com.personal.detectivedialer.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BlockedNumber::class, AllowedNumber::class, CallLogEntry::class,
        SmsMessage::class, SmsSenderPref::class,
    ],
    // v2: unique indices on number in blocked/allowed (destructive migration is configured).
    // v3: call_log.callerName (CNAP network-verified caller name).
    // v4: sms_messages + sms_sender_prefs (default SMS app).
    // v5: call_log.type (call disposition: incoming/outgoing/missed/rejected).
    // v6: call_log.action (policy action: RING/VOICEMAIL/REJECT).
    version = 6,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedNumberDao(): BlockedNumberDao
    abstract fun allowedNumberDao(): AllowedNumberDao
    abstract fun callLogDao(): CallLogDao
    abstract fun smsDao(): SmsDao
    abstract fun smsSenderPrefDao(): SmsSenderPrefDao

    companion object {
        const val NAME = "detectivedialer.db"
    }
}
