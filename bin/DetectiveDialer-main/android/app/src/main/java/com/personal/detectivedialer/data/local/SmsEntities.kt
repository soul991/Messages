package com.personal.detectivedialer.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * An SMS handled by the app while it is the default SMS app. Every message is
 * also persisted to the system Telephony provider (except blocked ones, which
 * live only here until restored) — this table is what the in-app UI reads.
 *
 * folder: INBOX | BLOCKED | SENT
 * Blocked messages are kept indefinitely; nothing is deleted without an
 * explicit user action.
 */
@Entity(
    tableName = "sms_messages",
    indices = [Index(value = ["sender"]), Index(value = ["folder"])],
)
data class SmsMessage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val timestamp: Long,
    val folder: String = FOLDER_INBOX,
    /** Why the message was blocked (e.g. "TRAI -P promotional suffix"). Blank when not blocked. */
    val blockReason: String = "",
    /** Telephony provider row URI when the message was persisted there. */
    val providerUri: String = "",
    val read: Boolean = false,
) {
    companion object {
        const val FOLDER_INBOX = "INBOX"
        const val FOLDER_BLOCKED = "BLOCKED"
        const val FOLDER_SENT = "SENT"
    }
}

/**
 * Per-sender override. NEVER_BLOCK wins over every automatic rule (set when
 * the user restores a blocked message or taps "never block").
 */
@Entity(tableName = "sms_sender_prefs", indices = [Index(value = ["sender"], unique = true)])
data class SmsSenderPref(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    /** NEVER_BLOCK is the only policy today; the column leaves room for more. */
    val policy: String = POLICY_NEVER_BLOCK,
    val addedAt: Long = System.currentTimeMillis(),
) {
    companion object {
        const val POLICY_NEVER_BLOCK = "NEVER_BLOCK"
    }
}
