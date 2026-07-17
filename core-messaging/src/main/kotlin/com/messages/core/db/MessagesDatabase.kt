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
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(message: MessageEntity): Long

    @Update
    suspend fun update(message: MessageEntity)

    @Query("SELECT * FROM messages WHERE threadId = :threadId ORDER BY timestamp ASC")
    fun messagesForThread(threadId: Long): Flow<List<MessageEntity>>

    @Query("SELECT * FROM messages WHERE id = :id")
    suspend fun byId(id: Long): MessageEntity?

    @Query("SELECT * FROM messages WHERE smsId = :smsId LIMIT 1")
    suspend fun bySmsId(smsId: Long): MessageEntity?

    @Query("UPDATE messages SET category = :category, dangerous = 0 WHERE id = :id")
    suspend fun recategorize(id: Long, category: String)

    @Query("UPDATE messages SET read = 1 WHERE threadId = :threadId")
    suspend fun markThreadRead(threadId: Long)

    @Query("UPDATE messages SET starred = :starred WHERE id = :id")
    suspend fun setStarred(id: Long, starred: Boolean)

    // User-initiated only — the filter itself never calls delete (§6).
    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun userDelete(id: Long)

    @Query("SELECT * FROM messages WHERE body LIKE '%' || :query || '%' ORDER BY timestamp DESC LIMIT 100")
    suspend fun search(query: String): List<MessageEntity>

    @Query("SELECT COUNT(*) FROM messages WHERE category = :category AND read = 0")
    fun unreadCount(category: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM messages WHERE category IN ('SPAM','BLOCKED') AND timestamp > :since")
    suspend fun spamCountSince(since: Long): Int

    @Query("SELECT * FROM messages WHERE protectedLabel = 'OTP' AND category = 'INBOX' AND timestamp < :olderThan")
    suspend fun expiredOtps(olderThan: Long): List<MessageEntity>

    @Query("SELECT * FROM messages WHERE starred = 1 ORDER BY timestamp DESC")
    fun starred(): Flow<List<MessageEntity>>
}

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

    @Query("UPDATE conversations SET pinned = :pinned WHERE threadId = :threadId")
    suspend fun setPinned(threadId: Long, pinned: Boolean)

    @Query("UPDATE conversations SET archived = :archived WHERE threadId = :threadId")
    suspend fun setArchived(threadId: Long, archived: Boolean)

    @Query("UPDATE conversations SET muted = :muted WHERE threadId = :threadId")
    suspend fun setMuted(threadId: Long, muted: Boolean)

    @Query("UPDATE conversations SET unreadCount = 0 WHERE threadId = :threadId")
    suspend fun clearUnread(threadId: Long)

    @Query("SELECT COUNT(*) FROM conversations WHERE category = :category AND unreadCount > 0 AND archived = 0")
    fun unreadConversationCount(category: String): Flow<Int>
}

@Dao
interface ReputationDao {
    @Query("SELECT * FROM sender_reputation WHERE address = :address LIMIT 1")
    suspend fun forSender(address: String): SenderReputationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SenderReputationEntity)
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
    ],
    version = 1,
    exportSchema = false,
)
abstract class MessagesDatabase : RoomDatabase() {
    abstract fun messages(): MessageDao
    abstract fun conversations(): ConversationDao
    abstract fun reputation(): ReputationDao
    abstract fun userRules(): UserRuleDao
}
