package com.messages.core.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local index over the system Telephony provider (which stays the source of
 * truth for message content). Adds category, labels, matched-pattern IDs —
 * everything the protection engine and folders need.
 */
@Entity(
    tableName = "messages",
    indices = [
        Index("threadId"),
        Index("category"),
        Index("timestamp"),
        Index(value = ["smsId"], unique = true),
        Index(value = ["mmsId"], unique = true),
        Index("mmsTransactionId"),
        Index("trashed"),
    ],
)
data class MessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** _id in the Telephony SMS provider; null when unknown or for MMS rows. */
    val smsId: Long? = null,
    /** _id in the Telephony MMS provider (pdu table); null for SMS rows. */
    val mmsId: Long? = null,
    /** X-Mms-Transaction-Id — dedupes carrier redelivery of the same MMS. */
    val mmsTransactionId: String? = null,
    val threadId: Long,
    val address: String,
    val body: String,
    /**
     * §8.5: Stage-0-normalized text (leet/homoglyph/separator obfuscation
     * undone) — indexed by FTS so obfuscated spam is searchable by its real
     * words. Populated on insert; historical rows are re-normalized once by
     * the FTS backfill.
     */
    val normalizedBody: String = "",
    val timestamp: Long,
    val isOutgoing: Boolean,
    val read: Boolean = false,
    /** Category name from the protection engine verdict. */
    val category: String = "INBOX",
    val dangerous: Boolean = false,
    val fraudWarning: Boolean = false,
    /** OTP / BANK / DELIVERY / TRAVEL / BILL / GOVERNMENT / NONE */
    val protectedLabel: String = "NONE",
    val score: Int = 0,
    /** Comma-separated matched pattern IDs — powers the "Why?" screen. */
    val matchedPatternIds: String = "",
    val matchedComboIds: String = "",
    /** Human-readable explanations, newline-separated. */
    val explanations: String = "",
    /** Local file path of the first MMS media attachment, if any. */
    val mediaUri: String? = null,
    val mediaMimeType: String? = null,
    val starred: Boolean = false,
    val archived: Boolean = false,
    val sendStatus: String = "NONE", // NONE | SENDING | SENT | FAILED
    /**
     * Raw platform result code (SmsManager.RESULT_*) when sendStatus is
     * FAILED — powers the human-readable failure reason (SendFailure) and
     * kept for debugging. Null for successful sends and legacy rows.
     */
    val sendResultCode: Int? = null,
    /** Dual-SIM: subscription this message was sent/received on, when known. */
    val subId: Int? = null,
    /**
     * Trash (§6.4): user deletions remove the Telephony-provider row but keep
     * this index row flagged as trash for 60 days, restorable from the Trash
     * folder. Trashed rows are excluded from every normal query.
     */
    val trashed: Boolean = false,
    /** When the message was trashed; purge happens 60 days later. */
    val trashedAt: Long? = null,
)

/**
 * §8.5: FTS4 mirror of [MessageEntity] (external content — Room keeps it in
 * sync with triggers). Indexes original body, normalized body, and sender so
 * incremental multi-keyword search stays instant at 100k+ messages.
 */
@Fts4(contentEntity = MessageEntity::class)
@Entity(tableName = "messages_fts")
data class MessageFtsEntity(
    val body: String,
    val normalizedBody: String,
    val address: String,
)

@Entity(tableName = "conversations", indices = [Index(value = ["threadId"], unique = true)])
data class ConversationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val threadId: Long,
    val address: String,
    val contactName: String? = null,
    val lastMessage: String = "",
    val lastTimestamp: Long = 0,
    val unreadCount: Int = 0,
    /** Dominant category of the latest message — decides which folder chip shows it. */
    val category: String = "INBOX",
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val muted: Boolean = false,
    /** Locked conversation (§8.2): opening requires app-lock auth; previews hidden. */
    val locked: Boolean = false,
    /** Dual-SIM: subscription ID to send from in this chat; null = system default. */
    val preferredSubId: Int? = null,
)

@Entity(tableName = "sender_reputation", indices = [Index(value = ["address"], unique = true)])
data class SenderReputationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val address: String,
    /** Positive = trusted (opened/replied), negative = distrusted (marked spam). */
    val score: Int = 0,
    val userMarkedSpamCount: Int = 0,
    val userMarkedNotSpamCount: Int = 0,
)

@Entity(tableName = "user_rules")
data class UserRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val position: Int,
    /** ALLOW | BLOCK | CUSTOM */
    val kind: String,
    /** SENDER | TEXT */
    val target: String = "SENDER",
    val pattern: String,
    /** Category to route to, for CUSTOM rules. */
    val category: String = "INBOX",
)
