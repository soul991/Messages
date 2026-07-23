package com.messages.core.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

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
            "WHERE trashed = 0 AND isOutgoing = 0 AND timestamp = (" +
            "SELECT MAX(timestamp) FROM messages WHERE threadId = m.threadId " +
            "AND trashed = 0 AND isOutgoing = 0) GROUP BY threadId"
    )
    fun latestIncomingMeta(): Flow<List<LatestIncomingMeta>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE threadId = :threadId AND trashed = 0 ORDER BY timestamp ASC")
    fun messagesForThread(threadId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE threadId = :threadId AND trashed = 0")
    suspend fun listForThread(threadId: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun byId(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE smsId = :smsId LIMIT 1")
    suspend fun bySmsId(smsId: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE mmsTransactionId = :transactionId LIMIT 1")
    suspend fun byMmsTransactionId(transactionId: String): MessageEntity?

    @Query("UPDATE messages SET category = :category, dangerous = 0 WHERE id = :id")
    suspend fun recategorize(id: Long, category: String)

    @Query("UPDATE messages SET read = 1 WHERE threadId = :threadId")
    suspend fun markThreadRead(threadId: Long)

    /** Mark-all-read for one folder (Phase 4 item 12). */
    @Query("UPDATE messages SET read = 1 WHERE category = :category AND trashed = 0")
    suspend fun markCategoryRead(category: String)

    @Query("UPDATE messages SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    @Query("UPDATE messages SET sendStatus = 'FAILED' WHERE id = :id")
    suspend fun markFailed(id: Long)

    @Query("UPDATE messages SET sendStatus = 'SENT' WHERE id = :id AND sendStatus != 'FAILED'")
    suspend fun markSent(id: Long)

    @Query("UPDATE messages SET sendStatus = 'DELIVERED' WHERE id = :id AND sendStatus != 'FAILED'")
    suspend fun markDelivered(id: Long)

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

    @Query("UPDATE messages SET trashed = 1, trashedAt = :at WHERE threadId = :threadId AND trashed = 0")
    suspend fun moveThreadToTrash(threadId: Long, at: Long)

    @Query("SELECT id FROM messages WHERE threadId = :threadId AND trashed = 1 AND trashedAt >= :after")
    suspend fun trashedIdsForThread(threadId: Long, after: Long): List<Long>

    @Query("UPDATE messages SET trashed = 0, trashedAt = NULL WHERE id = :id")
    suspend fun restoreFromTrash(id: Long)

    @Query("SELECT * FROM messages WHERE trashed = 1 ORDER BY trashedAt DESC, timestamp DESC")
    fun trashedMessages(): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE trashed = 1")
    suspend fun allTrashed(): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE trashed = 1 AND trashedAt < :before")
    suspend fun trashExpiredBefore(before: Long): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE trashed = 1")
    fun trashCount(): Flow<Int>

    @Query("SELECT * FROM messages WHERE threadId = :threadId AND trashed = 0 ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestForThread(threadId: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE body LIKE '%' || :query || '%' AND trashed = 0 ORDER BY timestamp DESC LIMIT 100")
    suspend fun search(query: String): List<MessageEntity>

    // ---- §8.5 FTS search ----

    /**
     * One keyword (as an FTS MATCH expression, e.g. `applicat*` or a quoted
     * phrase) → matching live messages, newest first. Multi-keyword match-any
     * ranking is assembled in [com.messages.core.search.MessageSearch] by
     * merging per-keyword result sets — avoids relying on FTS enhanced-query
     * OR syntax, which not every OEM SQLite build enables.
     */
    @Query(
        "SELECT messages.* FROM messages JOIN messages_fts ON messages.id = messages_fts.docid " +
            "WHERE messages_fts MATCH :match AND messages.trashed = 0 " +
            "ORDER BY messages.timestamp DESC LIMIT :limit"
    )
    suspend fun searchFts(match: String, limit: Int): List<MessageEntity>

    @Query("UPDATE messages SET normalizedBody = :normalized WHERE id = :id")
    suspend fun setNormalizedBody(id: Long, normalized: String)

    @Query("SELECT id, body FROM messages WHERE normalizedBody = ''")
    suspend fun rowsNeedingNormalization(): List<IdBody>

    @Query("SELECT COUNT(*) FROM messages WHERE category = :category AND read = 0 AND trashed = 0")
    fun unreadCount(category: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE category IN ('SPAM','BLOCKED') AND timestamp > :since AND trashed = 0")
    suspend fun spamCountSince(since: Long): Int

    // §6.6/§8.2 guarantee lives in this WHERE clause: only OTP-labeled Inbox
    // messages — never filtered folders (Spam/Promotions/Blocked/Review), never
    // other labels, never starred messages the user chose to keep. Trashed
    // OTPs are excluded: they follow the normal 60-day trash purge instead.
    @Query(
        "SELECT * FROM messages WHERE protectedLabel = 'OTP' AND category = 'INBOX' " +
            "AND starred = 0 AND trashed = 0 AND timestamp < :olderThan"
    )
    suspend fun expiredOtps(olderThan: Long): List<MessageEntity>

    // §6.5: SPAM only — Review and Blocked are NEVER auto-cleaned.
    @Query(
        "SELECT * FROM messages WHERE category = 'SPAM' " +
            "AND starred = 0 AND trashed = 0 AND timestamp < :olderThan"
    )
    suspend fun expiredSpam(olderThan: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE starred = 1 AND trashed = 0 ORDER BY timestamp DESC")
    fun starred(): Flow<List<MessageEntity>>

    // ---- Backup/restore (§8.2) ----

    @Query("SELECT * FROM messages ORDER BY timestamp ASC")
    suspend fun allMessages(): List<MessageEntity>

    // ---- Protection-dashboard stats (§8.2) ----

    @Query(
        "SELECT category, COUNT(*) as count FROM messages WHERE category IN " +
            "('SPAM','PROMOTIONS','BLOCKED','REVIEW') AND timestamp >= :since AND trashed = 0 GROUP BY category"
    )
    suspend fun filteredCountsSince(since: Long): List<CategoryCount>

    @Query(
        "SELECT address, COUNT(*) as count FROM messages WHERE category IN ('SPAM','BLOCKED') " +
            "AND timestamp >= :since AND trashed = 0 GROUP BY address ORDER BY count DESC LIMIT :limit"
    )
    suspend fun topFilteredSenders(since: Long, limit: Int): List<SenderCount>

    @Query("SELECT COUNT(*) FROM messages WHERE category IN ('SPAM','BLOCKED','PROMOTIONS') AND trashed = 0")
    suspend fun totalSilenced(): Int

    @Query("SELECT COUNT(*) FROM messages WHERE dangerous = 1 AND timestamp >= :since AND trashed = 0")
    suspend fun dangerousCountSince(since: Long): Int

    /**
     * Phase 4 item 21 (rec B3): count of messages already stored from this
     * exact address (incl. trashed — a trashed history still means the sender
     * is not brand-new). Zero → first-contact multiplier applies.
     */
    @Query("SELECT COUNT(*) FROM messages WHERE address = :address")
    suspend fun countForAddress(address: String): Int

    @Query(
        "SELECT matchedPatternIds FROM messages WHERE category IN " +
            "('SPAM','PROMOTIONS','BLOCKED','REVIEW') AND timestamp >= :since " +
            "AND trashed = 0 AND matchedPatternIds != ''"
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

    @Query("SELECT * FROM conversations WHERE threadId = :threadId LIMIT 1")
    suspend fun byThreadId(threadId: Long): ConversationEntity?

    @Query(
        "SELECT * FROM conversations WHERE category = :category AND archived = 0 " +
            "ORDER BY pinned DESC, lastTimestamp DESC"
    )
    fun byCategory(category: String): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE archived = 1 ORDER BY lastTimestamp DESC")
    fun archived(): Flow<List<ConversationEntity>>

    // §8.5.3: searching "mom" must find the conversation with the contact
    // saved as mom — contact names are not in the message FTS index, so
    // conversations are matched separately by name/number substring.
    @Query(
        "SELECT * FROM conversations WHERE (contactName LIKE '%' || :q || '%' " +
            "OR address LIKE '%' || :q || '%') " +
            "ORDER BY lastTimestamp DESC LIMIT 20"
    )
    suspend fun searchByNameOrAddress(q: String): List<ConversationEntity>

    @Query("UPDATE conversations SET pinned = :pinned WHERE threadId = :threadId")
    suspend fun setPinned(threadId: Long, pinned: Boolean)

    @Query("UPDATE conversations SET archived = :archived WHERE threadId = :threadId")
    suspend fun setArchived(threadId: Long, archived: Boolean)

    @Query("UPDATE conversations SET muted = :muted WHERE threadId = :threadId")
    suspend fun setMuted(threadId: Long, muted: Boolean)

    @Query("UPDATE conversations SET locked = :locked WHERE threadId = :threadId")
    suspend fun setLocked(threadId: Long, locked: Boolean)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE threadId = :threadId")
    suspend fun clearUnread(threadId: Long)

    /** Mark-all-read for one folder (Phase 4 item 12). */
    @Query("UPDATE conversations SET unreadCount = 0 WHERE category = :category")
    suspend fun clearUnreadForCategory(category: String)

    /**
     * Mark-as-unread (Phase 4 item 13): a UI-level unread marker, exactly like
     * Google Messages — message rows stay read; only the badge count changes.
     */
    @Query(
        "UPDATE conversations SET unreadCount = " +
            "CASE WHEN unreadCount = 0 THEN 1 ELSE unreadCount END WHERE threadId = :threadId"
    )
    suspend fun markUnread(threadId: Long)

    @Query("UPDATE conversations SET preferredSubId = :subId WHERE threadId = :threadId")
    suspend fun setPreferredSubId(threadId: Long, subId: Int?)

    @Query("UPDATE conversations SET contactName = :name WHERE threadId = :threadId")
    suspend fun setContactName(threadId: Long, name: String?)

    @Query("DELETE FROM conversations WHERE threadId = :threadId")
    suspend fun deleteByThreadId(threadId: Long)

    @Query("SELECT COUNT(*) FROM conversations WHERE category = :category AND unreadCount > 0 AND archived = 0")
    fun unreadConversationCount(category: String): Flow<Int>

    // ---- One-shot lookups for home-screen widgets (§8.2) ----

    @Query("SELECT COUNT(*) FROM conversations WHERE category = 'INBOX' AND unreadCount > 0 AND archived = 0")
    suspend fun unreadInboxConversations(): Int

    @Query(
        "SELECT * FROM conversations WHERE category = 'INBOX' AND unreadCount > 0 " +
            "AND archived = 0 ORDER BY lastTimestamp DESC LIMIT :limit"
    )
    suspend fun recentUnreadInbox(limit: Int): List<ConversationEntity>

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

@Database(
    entities = [
        MessageEntity::class, ConversationEntity::class,
        SenderReputationEntity::class, UserRuleEntity::class,
        MessageFtsEntity::class,
    ],
    version = 6,
    exportSchema = true,
)
abstract class MessagesDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
    abstract fun conversations(): ConversationDao
    abstract fun reputation(): ReputationDao
    abstract fun userRules(): UserRuleDao
}
