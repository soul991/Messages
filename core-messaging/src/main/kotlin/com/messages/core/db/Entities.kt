package com.messages.core.db

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Message/conversation spaces. LOCKED rows belong to the secret locked space:
 * they must never surface in any query that feeds normal UI — list, folders,
 * search/FTS, suggested chips, notifications with content, widgets, dashboard
 * stats, Starred, Trash, export, or multi-select surfaces.
 *
 * V2-6: a LOCKED row's `body`/`normalizedBody` are stored encrypted
 * ([com.messages.core.secret.LockedContent]) and its Telephony copy is deleted
 * when it enters the space, so the plaintext exists in exactly one place —
 * nowhere. `address`, timestamps and state columns stay in the clear because
 * routing, contact resolution and dedupe all join on them; that residual is
 * stated in the locked-chats disclaimer rather than left to inference.
 */
object Spaces {
    const val NORMAL = "NORMAL"
    const val LOCKED = "LOCKED"
}

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
        Index("space"),
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
     * V2-48: how many *automatic* retries this outgoing message has already
     * had. A manual resend from the outbox resets it to 0 — the user pressing
     * the button is a fresh decision, not the continuation of a policy the app
     * was running on its own.
     */
    val retryCount: Int = 0,
    /**
     * V2-48: when the pending automatic retry is due, or null when none is
     * scheduled. Stored rather than left to WorkManager alone so the outbox can
     * say *when* without querying the scheduler, and so a retry that was
     * dropped with its worker is visible as overdue instead of invisible.
     */
    val nextRetryAt: Long? = null,
    /**
     * Trash (§6.4): user deletions remove the Telephony-provider row but keep
     * this index row flagged as trash for 60 days, restorable from the Trash
     * folder. Trashed rows are excluded from every normal query.
     */
    val trashed: Boolean = false,
    /** When the message was trashed; purge happens 60 days later. */
    val trashedAt: Long? = null,
    /** [Spaces.NORMAL] or [Spaces.LOCKED] — see [Spaces]. */
    val space: String = Spaces.NORMAL,
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

/**
 * One row per (threadId, space): "New locked chat" creates a second,
 * LOCKED-space conversation for the same system thread — existing history
 * stays on the NORMAL row, all future incoming messages from that address
 * route to the LOCKED row (never both).
 */
@Entity(
    tableName = "conversations",
    indices = [Index(value = ["threadId", "space"], unique = true)],
)
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
    /**
     * LEGACY (pre-secret-space biometric locked conversations). Superseded by
     * [space]: rows with locked=1 are migrated into the LOCKED space the first
     * time the user completes secret-space setup, then this flag is cleared.
     * Kept in the schema (SQLite column drops are expensive) and still honored
     * by the old auth gate until that migration runs.
     */
    val locked: Boolean = false,
    /** Dual-SIM: subscription ID to send from in this chat; null = system default. */
    val preferredSubId: Int? = null,
    /** [Spaces.NORMAL] or [Spaces.LOCKED] — see [Spaces]. */
    val space: String = Spaces.NORMAL,
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

/**
 * R-05: every Telephony-provider row this app created for one message.
 *
 * A group SMS writes ONE PROVIDER ROW PER RECIPIENT, but [MessageEntity.smsId]
 * only ever held the first — so deleting a group message left the other
 * recipients' rows visible to every other SMS app, outside this app's trash and
 * retention model. The mapping is one-to-many so delete/trash can attempt every
 * URI it created.
 *
 * Deliberately NOT a CASCADE foreign key: a provider deletion that fails
 * (transient provider error, default-SMS role temporarily lost) is kept as
 * [deleteFailed] for retry, and that must outlive the local message row.
 */
@Entity(
    tableName = "provider_rows",
    indices = [Index("messageId"), Index("deleteFailed")],
)
data class ProviderRowEntity(
    /** Full content URI, e.g. `content://sms/1234`. */
    @PrimaryKey val uri: String,
    val messageId: Long,
    val recipient: String,
    /** SMS | MMS */
    val kind: String,
    /** Deletion was attempted and failed — retried by the trash-purge worker. */
    val deleteFailed: Boolean = false,
)

/**
 * R-13: one row per (recipient, part) dispatch of an outgoing SMS.
 *
 * Status used to be message-level, so a single early success marked a whole
 * group/multipart message SENT while other parts were still pending or had
 * already failed, and callbacks could oscillate with broadcast order. Each
 * dispatch now reports into its own row and the message's `sendStatus` is
 * DERIVED from the full set by [com.messages.core.send.SendAggregate] — a pure
 * function of monotonic per-attempt states, hence independent of the order the
 * broadcasts arrive in.
 */
@Entity(tableName = "sms_attempts", indices = [Index("messageId")])
data class SmsAttemptEntity(
    /** "<messageId>:<recipientIndex>:<partIndex>" — stable and unique. */
    @PrimaryKey val attemptId: String,
    val messageId: Long,
    val recipientIndex: Int,
    val partIndex: Int,
    /** [AttemptState] — PENDING until the SENT broadcast for this part lands. */
    val sentState: String = AttemptState.PENDING,
    /** [AttemptState] — only meaningful when [wantDelivery]. */
    val deliveryState: String = AttemptState.PENDING,
    /** Whether a delivery report was requested for this dispatch. */
    val wantDelivery: Boolean = false,
    /** Raw SmsManager.RESULT_* for a failed dispatch. */
    val resultCode: Int? = null,
)

/** Per-attempt lifecycle: PENDING is the only non-terminal state. */
object AttemptState {
    const val PENDING = "PENDING"
    const val OK = "OK"
    const val FAILED = "FAILED"
}

/**
 * R-16: collision-resistant synthetic thread IDs.
 *
 * When `Telephony.Threads.getOrCreateThreadId` is unavailable the intake path
 * used to fall back to `address.hashCode().toLong()` — a 32-bit hash, so two
 * unrelated senders could collide and have their conversations silently merged.
 * Threads are now allocated from this table, keyed by the CANONICAL recipient
 * set, and numbered NEGATIVELY so a synthetic ID can never collide with a real
 * provider thread ID (those are positive).
 */
@Entity(
    tableName = "thread_aliases",
    indices = [Index(value = ["recipientKey"], unique = true)],
)
data class ThreadAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Digits-only, de-duplicated, sorted, ';'-joined recipient set. */
    val recipientKey: String,
    /** Negative synthetic thread ID; 0 only in the instant before allocation. */
    val threadId: Long = 0,
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
