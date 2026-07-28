package com.messages.core.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

// Secret-space invisibility contract (audited by SpaceInvisibilityTest):
// every query feeding NORMAL-space UI pins `space = 'NORMAL'` (home list,
// folders, FTS search, name search, starred, trash, stats, widgets, badges).
// Queries that serve the chat screen take a :space parameter because the chat
// UI is shared by both spaces. Background-maintenance queries (trash purge,
// OTP/spam cleanup, FTS renormalize) deliberately span both spaces — they
// never surface content.

@Dao
interface MessageDao {
    /** Latest INCOMING message's badge-relevant fields per thread (verified-
     *  sender badges: fraud suppression + protected-lane elevation). */
    data class LatestIncomingMeta(
        val threadId: Long,
        val dangerous: Boolean,
        val fraudWarning: Boolean,
        val protectedLabel: String,
    )

    @Query(
        "SELECT threadId, dangerous, fraudWarning, protectedLabel FROM messages m " +
            "WHERE trashed = 0 AND isOutgoing = 0 AND space = 'NORMAL' AND timestamp = (" +
            "SELECT MAX(timestamp) FROM messages WHERE threadId = m.threadId " +
            "AND trashed = 0 AND isOutgoing = 0 AND space = 'NORMAL') GROUP BY threadId"
    )
    fun latestIncomingMeta(): Flow<List<LatestIncomingMeta>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query(
        "SELECT * FROM messages WHERE threadId = :threadId AND space = :space " +
            "AND trashed = 0 ORDER BY timestamp ASC"
    )
    fun messagesForThread(threadId: Long, space: String = Spaces.NORMAL): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE threadId = :threadId AND space = :space AND trashed = 0")
    suspend fun listForThread(threadId: Long, space: String = Spaces.NORMAL): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun byId(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE smsId = :smsId LIMIT 1")
    suspend fun bySmsId(smsId: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE mmsTransactionId = :transactionId LIMIT 1")
    suspend fun byMmsTransactionId(transactionId: String): MessageEntity?

    /**
     * R-16: logical identity of an incoming SMS — the carrier's millisecond
     * timestamp plus sender plus body. A duplicate SMS_DELIVER broadcast (the
     * platform can redeliver, and OEM shells sometimes do) would otherwise be
     * indexed again, inflating the unread badge and re-notifying, because each
     * redelivery gets its own provider `_id`.
     */
    @Query(
        "SELECT * FROM messages WHERE address = :address AND timestamp = :timestamp " +
            "AND body = :body AND isOutgoing = 0 LIMIT 1"
    )
    suspend fun findIncomingDuplicate(address: String, timestamp: Long, body: String): MessageEntity?

    @Query("UPDATE messages SET category = :category, dangerous = 0 WHERE id = :id")
    suspend fun recategorize(id: Long, category: String)

    /**
     * R-15: a user override ("Not spam") must erase the WHOLE classifier verdict.
     * Clearing only `category`/`dangerous` left fraudWarning, the score and the
     * matched pattern IDs in place, so the message still rendered as fraudulent
     * and its red warning stayed visible.
     */
    @Query(
        "UPDATE messages SET category = :category, dangerous = 0, fraudWarning = 0, " +
            "score = 0, matchedPatternIds = '', matchedComboIds = '', " +
            "explanations = 'User marked as not spam' WHERE id = :id"
    )
    suspend fun clearClassifierVerdict(id: Long, category: String)

    @Query("UPDATE messages SET read = 1 WHERE threadId = :threadId AND space = :space")
    suspend fun markThreadRead(threadId: Long, space: String = Spaces.NORMAL)

    /** Mark-all-read for one folder (Phase 4 item 12) — Home surface, NORMAL only. */
    @Query("UPDATE messages SET read = 1 WHERE category = :category AND trashed = 0 AND space = 'NORMAL'")
    suspend fun markCategoryRead(category: String)

    @Query("UPDATE messages SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    @Query("UPDATE messages SET sendStatus = 'FAILED', sendResultCode = :resultCode WHERE id = :id")
    suspend fun markFailed(id: Long, resultCode: Int?)

    /**
     * R-13: `sendStatus != 'DELIVERED'` stops a late SENT broadcast from
     * downgrading a message the carrier already confirmed delivered. Retained
     * for MMS and for legacy rows with no attempt set; SMS status now comes
     * from [setSendStatus], driven by [com.messages.core.send.SendAggregate].
     */
    @Query(
        "UPDATE messages SET sendStatus = 'SENT' WHERE id = :id " +
            "AND sendStatus != 'FAILED' AND sendStatus != 'DELIVERED'"
    )
    suspend fun markSent(id: Long)

    @Query("UPDATE messages SET sendStatus = 'DELIVERED' WHERE id = :id AND sendStatus != 'FAILED'")
    suspend fun markDelivered(id: Long)

    /** R-13: apply an aggregate computed over a message's whole attempt set. */
    @Query("UPDATE messages SET sendStatus = :status, sendResultCode = :resultCode WHERE id = :id")
    suspend fun setSendStatus(id: Long, status: String, resultCode: Int?)

    /**
     * Secret space: move a whole thread's live messages between spaces
     * ("Move entire chat" / "Unlock chat"). Trash rows move too — a locked
     * chat's deletions must not resurface in the normal Trash screen.
     */
    @Query("UPDATE messages SET space = :to WHERE threadId = :threadId AND space = :from")
    suspend fun setThreadSpace(threadId: Long, from: String, to: String)

    /** Every row of one space, trash included — feeds the locked-space Reset
     *  wipe (hard delete; locked content must never pass through Trash). */
    @Query("SELECT * FROM messages WHERE space = :space")
    suspend fun allInSpace(space: String): List<MessageEntity>

    // User-initiated only — the filter itself never calls delete (§6). Normal
    // user deletions go through the Trash flags below (§6.4); the permitted
    // hard-delete callers are: "Delete forever" in Trash, the 60-day trash
    // purge, the user-ENABLED OTP cleanup (§6.6 — bypasses Trash), and
    // cancelling a scheduled draft (never sent, nothing to retain).
    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun userDelete(id: Long)

    // ---- Trash (§6.4) ----

    @Query("UPDATE messages SET trashed = 1, trashedAt = :at WHERE id = :id")
    suspend fun moveToTrash(id: Long, at: Long)

    @Query(
        "UPDATE messages SET trashed = 1, trashedAt = :at " +
            "WHERE threadId = :threadId AND space = :space AND trashed = 0"
    )
    suspend fun moveThreadToTrash(threadId: Long, at: Long, space: String = Spaces.NORMAL)

    @Query(
        "SELECT id FROM messages WHERE threadId = :threadId AND space = :space " +
            "AND trashed = 1 AND trashedAt >= :after"
    )
    suspend fun trashedIdsForThread(threadId: Long, after: Long, space: String = Spaces.NORMAL): List<Long>

    @Query("UPDATE messages SET trashed = 0, trashedAt = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: Long)

    /** Trash screen (normal surface): locked-space trash never appears here. */
    @Query(
        "SELECT * FROM messages WHERE trashed = 1 AND space = 'NORMAL' " +
            "ORDER BY trashedAt DESC, timestamp DESC"
    )
    fun trashedMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE trashed = 1 AND space = 'NORMAL'")
    suspend fun allTrashed(): List<MessageEntity>

    /** 60-day purge spans both spaces (background maintenance, no UI surface). */
    @Query("SELECT * FROM messages WHERE trashed = 1 AND trashedAt < :before")
    suspend fun trashExpiredBefore(before: Long): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE trashed = 1 AND space = 'NORMAL'")
    fun trashCount(): Flow<Int>

    @Query(
        "SELECT * FROM messages WHERE threadId = :threadId AND space = :space " +
            "AND trashed = 0 ORDER BY timestamp DESC LIMIT 1"
    )
    suspend fun latestForThread(threadId: Long, space: String = Spaces.NORMAL): MessageEntity?

    @Query(
        "SELECT * FROM messages WHERE body LIKE '%' || :query || '%' AND trashed = 0 " +
            "AND space = 'NORMAL' ORDER BY timestamp DESC LIMIT 100"
    )
    suspend fun search(query: String): List<MessageEntity>

    // ---- §8.5 FTS search ----

    /**
     * One keyword (as an FTS MATCH expression, e.g. `applicat*` or a quoted
     * phrase) → matching live messages, newest first. Multi-keyword match-any
     * ranking is assembled in [com.messages.core.search.MessageSearch] by
     * merging per-keyword result sets — avoids relying on FTS enhanced-query
     * OR syntax, which not every OEM SQLite build enables. The FTS table has
     * no space column, so the join filter enforces locked-space invisibility.
     */
    @Query(
        "SELECT messages.* FROM messages JOIN messages_fts ON messages.id = messages_fts.docid " +
            "WHERE messages_fts MATCH :match AND messages.trashed = 0 " +
            "AND messages.space = 'NORMAL' " +
            "ORDER BY messages.timestamp DESC LIMIT :limit"
    )
    suspend fun searchFts(match: String, limit: Int): List<MessageEntity>

    @Query("UPDATE messages SET normalizedBody = :normalized WHERE id = :id")
    suspend fun setNormalizedBody(id: Long, normalized: String)

    @Query("SELECT id, body FROM messages WHERE normalizedBody = ''")
    suspend fun rowsNeedingNormalization(): List<IdBody>

    @Query(
        "SELECT COUNT(*) FROM messages WHERE category = :category AND read = 0 " +
            "AND trashed = 0 AND space = :space"
    )
    fun unreadCount(category: String, space: String = Spaces.NORMAL): Flow<Int>

    @Query(
        "SELECT COUNT(*) FROM messages WHERE category IN ('SPAM','BLOCKED') " +
            "AND timestamp > :since AND trashed = 0 AND space = 'NORMAL'"
    )
    suspend fun spamCountSince(since: Long): Int

    // §6.6/§8.2 guarantee lives in this WHERE clause: only OTP-labeled Inbox
    // messages — never filtered folders (Spam/Promotions/Blocked/Review), never
    // other labels, never starred messages the user chose to keep. Trashed
    // OTPs are excluded: they follow the normal 60-day trash purge instead.
    // Both spaces on purpose: cleanup applies inside the locked space too.
    @Query(
        "SELECT * FROM messages WHERE protectedLabel = 'OTP' AND category = 'INBOX' " +
            "AND starred = 0 AND trashed = 0 AND timestamp < :olderThan"
    )
    suspend fun expiredOtps(olderThan: Long): List<MessageEntity>

    // §6.5: SPAM only — Review and Blocked are NEVER auto-cleaned. Both spaces.
    @Query(
        "SELECT * FROM messages WHERE category = 'SPAM' " +
            "AND starred = 0 AND trashed = 0 AND timestamp < :olderThan"
    )
    suspend fun expiredSpam(olderThan: Long): List<MessageEntity>

    /** Starred screen (normal surface): starred locked messages stay invisible. */
    @Query(
        "SELECT * FROM messages WHERE starred = 1 AND trashed = 0 AND space = 'NORMAL' " +
            "ORDER BY timestamp DESC"
    )
    fun starred(): Flow<List<MessageEntity>>

    // Phase 5 §4: ContactDetail "Starred messages" row scopes to one thread.
    @Query(
        "SELECT * FROM messages WHERE starred = 1 AND trashed = 0 AND space = 'NORMAL' " +
            "AND threadId = :threadId ORDER BY timestamp DESC"
    )
    fun starredForThread(threadId: Long): Flow<List<MessageEntity>>

    // ---- Backup/restore (§8.2) ----

    /** BOTH spaces — the backup layer splits them (locked rows go into the
     *  credential-encrypted sub-envelope) and the dedupe key set must span
     *  spaces so a restore never duplicates a message across them. */
    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun allMessages(): List<MessageEntity>

    // ---- Protection-dashboard stats (§8.2) — normal space only ----

    @Query(
        "SELECT category, COUNT(*) as count FROM messages WHERE category IN " +
            "('SPAM','PROMOTIONS','BLOCKED','REVIEW') AND timestamp >= :since " +
            "AND trashed = 0 AND space = 'NORMAL' GROUP BY category"
    )
    suspend fun filteredCountsSince(since: Long): List<CategoryCount>

    @Query(
        "SELECT address, COUNT(*) as count FROM messages WHERE category IN ('SPAM','BLOCKED') " +
            "AND timestamp >= :since AND trashed = 0 AND space = 'NORMAL' " +
            "GROUP BY address ORDER BY count DESC LIMIT :limit"
    )
    suspend fun topFilteredSenders(since: Long, limit: Int): List<SenderCount>

    @Query(
        "SELECT COUNT(*) FROM messages WHERE category IN ('SPAM','BLOCKED','PROMOTIONS') " +
            "AND trashed = 0 AND space = 'NORMAL'"
    )
    suspend fun totalSilenced(): Int

    @Query(
        "SELECT COUNT(*) FROM messages WHERE dangerous = 1 AND timestamp >= :since " +
            "AND trashed = 0 AND space = 'NORMAL'"
    )
    suspend fun dangerousCountSince(since: Long): Int

    /**
     * Phase 4 item 21 (rec B3): count of messages already stored from this
     * exact address (incl. trashed — a trashed history still means the sender
     * is not brand-new). Zero → first-contact multiplier applies. Both spaces:
     * a sender with locked history is not a first contact.
     */
    @Query("SELECT COUNT(*) FROM messages WHERE address = :address")
    suspend fun countForAddress(address: String): Int

    @Query(
        "SELECT matchedPatternIds FROM messages WHERE category IN " +
            "('SPAM','PROMOTIONS','BLOCKED','REVIEW') AND timestamp >= :since " +
            "AND trashed = 0 AND space = 'NORMAL' AND matchedPatternIds != ''"
    )
    suspend fun filteredPatternIdsSince(since: Long): List<String>
}

data class CategoryCount(val category: String, val count: Int)
data class SenderCount(val address: String, val count: Int)
data class IdBody(val id: Long, val body: String)

@Dao
interface ConversationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(conversation: ConversationEntity): Long

    @Query("SELECT * FROM conversations WHERE threadId = :threadId AND space = :space LIMIT 1")
    suspend fun byThreadId(threadId: Long, space: String = Spaces.NORMAL): ConversationEntity?

    @Query(
        "SELECT * FROM conversations WHERE category = :category AND archived = 0 " +
            "AND space = :space ORDER BY pinned DESC, lastTimestamp DESC"
    )
    fun byCategory(category: String, space: String = Spaces.NORMAL): Flow<List<ConversationEntity>>

    /**
     * The Home "Unread" filter — SAME shape as [byCategory] plus the ONE
     * unread predicate (`unreadCount > 0`) shared by row badges, folder-chip
     * counts ([unreadConversationCount]) and this filter. Conversation-level
     * by design: incoming messages increment it, opening a chat clears it,
     * and mark-as-unread sets it (message `read` flags stay untouched there
     * — Google Messages semantics). Keeping all three surfaces on this single
     * column is what stops the definitions drifting again.
     */
    @Query(
        "SELECT * FROM conversations WHERE category = :category AND archived = 0 " +
            "AND unreadCount > 0 AND space = :space ORDER BY pinned DESC, lastTimestamp DESC"
    )
    fun byCategoryUnread(category: String, space: String = Spaces.NORMAL): Flow<List<ConversationEntity>>

    @Query(
        "SELECT * FROM conversations WHERE archived = 1 AND space = 'NORMAL' " +
            "ORDER BY lastTimestamp DESC"
    )
    fun archived(): Flow<List<ConversationEntity>>

    // §8.5.3: searching "mom" must find the conversation with the contact
    // saved as mom — contact names are not in the message FTS index, so
    // conversations are matched separately by name/number substring. NORMAL
    // only: this also feeds the forward picker and global search.
    @Query(
        "SELECT * FROM conversations WHERE (contactName LIKE '%' || :q || '%' " +
            "OR address LIKE '%' || :q || '%') AND space = 'NORMAL' " +
            "ORDER BY lastTimestamp DESC LIMIT 20"
    )
    suspend fun searchByNameOrAddress(q: String): List<ConversationEntity>

    @Query("UPDATE conversations SET pinned = :pinned WHERE threadId = :threadId AND space = :space")
    suspend fun setPinned(threadId: Long, pinned: Boolean, space: String = Spaces.NORMAL)

    @Query("UPDATE conversations SET archived = :archived WHERE threadId = :threadId AND space = :space")
    suspend fun setArchived(threadId: Long, archived: Boolean, space: String = Spaces.NORMAL)

    @Query("UPDATE conversations SET muted = :muted WHERE threadId = :threadId AND space = :space")
    suspend fun setMuted(threadId: Long, muted: Boolean, space: String = Spaces.NORMAL)

    /** LEGACY biometric locked-conversation flag — normal space only. */
    @Query("UPDATE conversations SET locked = :locked WHERE threadId = :threadId AND space = 'NORMAL'")
    suspend fun setLocked(threadId: Long, locked: Boolean)

    /** Legacy rows to migrate into the LOCKED space at first secret-space setup. */
    @Query("SELECT * FROM conversations WHERE locked = 1 AND space = 'NORMAL'")
    suspend fun legacyLockedConversations(): List<ConversationEntity>

    @Query("UPDATE conversations SET unreadCount = 0 WHERE threadId = :threadId AND space = :space")
    suspend fun clearUnread(threadId: Long, space: String = Spaces.NORMAL)

    /** Mark-all-read for one folder (Phase 4 item 12) — Home surface. */
    @Query("UPDATE conversations SET unreadCount = 0 WHERE category = :category AND space = 'NORMAL'")
    suspend fun clearUnreadForCategory(category: String)

    /**
     * Mark-as-unread (Phase 4 item 13): a UI-level unread marker, exactly like
     * Google Messages — message rows stay read; only the badge count changes.
     */
    @Query(
        "UPDATE conversations SET unreadCount = " +
            "CASE WHEN unreadCount = 0 THEN 1 ELSE unreadCount END " +
            "WHERE threadId = :threadId AND space = :space"
    )
    suspend fun markUnread(threadId: Long, space: String = Spaces.NORMAL)

    @Query("UPDATE conversations SET preferredSubId = :subId WHERE threadId = :threadId AND space = :space")
    suspend fun setPreferredSubId(threadId: Long, subId: Int?, space: String = Spaces.NORMAL)

    @Query("UPDATE conversations SET contactName = :name WHERE threadId = :threadId AND space = :space")
    suspend fun setContactName(threadId: Long, name: String?, space: String = Spaces.NORMAL)

    @Query("DELETE FROM conversations WHERE threadId = :threadId AND space = :space")
    suspend fun deleteByThreadId(threadId: Long, space: String = Spaces.NORMAL)

    @Query(
        "SELECT COUNT(*) FROM conversations WHERE category = :category " +
            "AND unreadCount > 0 AND archived = 0 AND space = :space"
    )
    fun unreadConversationCount(category: String, space: String = Spaces.NORMAL): Flow<Int>

    /** Any locked-space conversation rows at all? Drives the routing rule check. */
    @Query("SELECT COUNT(*) FROM conversations WHERE space = 'LOCKED'")
    suspend fun lockedConversationCount(): Int

    // ---- One-shot lookups for home-screen widgets (§8.2) — normal space ----

    /**
     * R-03: `locked = 0` excludes LEGACY biometric-locked normal-space rows at
     * the DAO layer, not in widget UI code. A launcher widget is an
     * unauthenticated surface, so exclusion must not depend on a caller
     * remembering to filter.
     */
    @Query(
        "SELECT COUNT(*) FROM conversations WHERE category = 'INBOX' AND unreadCount > 0 " +
            "AND archived = 0 AND space = 'NORMAL' AND locked = 0"
    )
    suspend fun unreadInboxConversations(): Int

    @Query(
        "SELECT * FROM conversations WHERE category = 'INBOX' AND unreadCount > 0 " +
            "AND archived = 0 AND space = 'NORMAL' AND locked = 0 " +
            "ORDER BY lastTimestamp DESC LIMIT :limit"
    )
    suspend fun recentUnreadInbox(limit: Int): List<ConversationEntity>

    /** BOTH spaces — backup split + contact-name healing handle space themselves. */
    @Query("SELECT * FROM conversations")
    suspend fun allConversations(): List<ConversationEntity>
}

@Dao
interface ReputationDao {
    @Query("SELECT * FROM sender_reputation WHERE address = :address LIMIT 1")
    suspend fun forSender(address: String): SenderReputationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SenderReputationEntity)

    @Query("SELECT * FROM sender_reputation")
    suspend fun all(): List<SenderReputationEntity>
}

@Dao
interface UserRuleDao {
    @Query("SELECT * FROM user_rules ORDER BY position ASC")
    suspend fun all(): List<UserRuleEntity>

    @Query("SELECT * FROM user_rules ORDER BY position ASC")
    fun observeAll(): Flow<List<UserRuleEntity>>

    @Insert
    suspend fun insert(rule: UserRuleEntity): Long

    @Query("DELETE FROM user_rules WHERE id = :id")
    suspend fun delete(id: Long)
}

/**
 * R-05: the provider rows one message owns. A group SMS owns one per
 * recipient, so delete/trash must walk this list rather than a single smsId.
 */
@Dao
interface ProviderRowDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(row: ProviderRowEntity)

    @Query("SELECT * FROM provider_rows WHERE messageId = :messageId")
    suspend fun forMessage(messageId: Long): List<ProviderRowEntity>

    /** Deletions that failed once, for the purge worker's retry sweep. */
    @Query("SELECT * FROM provider_rows WHERE deleteFailed = 1 LIMIT :limit")
    suspend fun pendingDeletions(limit: Int): List<ProviderRowEntity>

    @Query("UPDATE provider_rows SET deleteFailed = 1 WHERE uri = :uri")
    suspend fun markDeleteFailed(uri: String)

    @Query("DELETE FROM provider_rows WHERE uri = :uri")
    suspend fun deleteByUri(uri: String)

    @Query("DELETE FROM provider_rows WHERE messageId = :messageId")
    suspend fun deleteForMessage(messageId: Long)

    @Query("SELECT COUNT(*) FROM provider_rows WHERE messageId = :messageId")
    suspend fun countForMessage(messageId: Long): Int
}

/** R-13: per-(recipient, part) send/delivery state for outgoing SMS. */
@Dao
interface SmsAttemptDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(attempt: SmsAttemptEntity)

    @Query("SELECT * FROM sms_attempts WHERE attemptId = :attemptId")
    suspend fun byId(attemptId: String): SmsAttemptEntity?

    @Query("SELECT * FROM sms_attempts WHERE messageId = :messageId ORDER BY recipientIndex, partIndex")
    suspend fun forMessage(messageId: Long): List<SmsAttemptEntity>

    /**
     * PENDING is the only state an attempt may leave — a receiver can never
     * overwrite a state another broadcast already settled, which is what makes
     * the derived aggregate independent of broadcast order.
     */
    @Query(
        "UPDATE sms_attempts SET sentState = :state, resultCode = :resultCode " +
            "WHERE attemptId = :attemptId AND sentState = 'PENDING'"
    )
    suspend fun settleSent(attemptId: String, state: String, resultCode: Int?)

    @Query(
        "UPDATE sms_attempts SET deliveryState = :state " +
            "WHERE attemptId = :attemptId AND deliveryState = 'PENDING'"
    )
    suspend fun settleDelivery(attemptId: String, state: String)

    /** A resend rebuilds the attempt set from scratch. */
    @Query("DELETE FROM sms_attempts WHERE messageId = :messageId")
    suspend fun deleteForMessage(messageId: Long)
}

/** R-16: collision-free synthetic thread IDs when the provider has none. */
@Dao
interface ThreadAliasDao {
    @Query("SELECT * FROM thread_aliases WHERE recipientKey = :key LIMIT 1")
    suspend fun byKey(key: String): ThreadAliasEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(alias: ThreadAliasEntity): Long

    /** Synthetic IDs are negative so they can never hit a real provider ID. */
    @Query("UPDATE thread_aliases SET threadId = -id WHERE id = :id")
    suspend fun assignThreadId(id: Long)
}

@Database(
    entities = [
        MessageEntity::class, ConversationEntity::class,
        SenderReputationEntity::class, UserRuleEntity::class,
        MessageFtsEntity::class,
        ProviderRowEntity::class, SmsAttemptEntity::class, ThreadAliasEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
abstract class MessagesDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
    abstract fun conversations(): ConversationDao
    abstract fun reputation(): ReputationDao
    abstract fun userRules(): UserRuleDao
    abstract fun providerRows(): ProviderRowDao
    abstract fun smsAttempts(): SmsAttemptDao
    abstract fun threadAliases(): ThreadAliasDao
}
