package com.personal.detectivedialer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** One row per conversation partner, newest message first. */
data class SmsThreadSummary(
    val sender: String,
    val body: String,
    val timestamp: Long,
    val unread: Int,
    val total: Int,
)

@Dao
interface SmsDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(message: SmsMessage): Long

    @Query("SELECT * FROM sms_messages WHERE id = :id")
    suspend fun getById(id: Long): SmsMessage?

    @Query("UPDATE sms_messages SET folder = :folder, blockReason = :blockReason WHERE id = :id")
    suspend fun setFolder(id: Long, folder: String, blockReason: String)

    @Query("UPDATE sms_messages SET providerUri = :uri WHERE id = :id")
    suspend fun setProviderUri(id: Long, uri: String)

    @Query("DELETE FROM sms_messages WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE sms_messages SET read = 1 WHERE sender = :sender")
    suspend fun markThreadRead(sender: String)

    // Conversation list: the latest non-blocked message per sender.
    @Query(
        """
        SELECT sender,
               body,
               MAX(timestamp) AS timestamp,
               SUM(CASE WHEN read = 0 THEN 1 ELSE 0 END) AS unread,
               COUNT(*) AS total
        FROM sms_messages
        WHERE folder != 'BLOCKED'
        GROUP BY sender
        ORDER BY timestamp DESC
        """,
    )
    fun observeThreads(): Flow<List<SmsThreadSummary>>

    @Query(
        "SELECT * FROM sms_messages WHERE sender = :sender AND folder != 'BLOCKED' ORDER BY timestamp ASC",
    )
    fun observeThread(sender: String): Flow<List<SmsMessage>>

    @Query("SELECT * FROM sms_messages WHERE folder = 'BLOCKED' ORDER BY timestamp DESC")
    fun observeBlocked(): Flow<List<SmsMessage>>

    @Query("SELECT COUNT(*) FROM sms_messages WHERE folder = 'BLOCKED' AND read = 0")
    fun observeBlockedUnreadCount(): Flow<Int>

    @Query("UPDATE sms_messages SET read = 1 WHERE id = :id")
    suspend fun markRead(id: Long)
}

@Dao
interface SmsSenderPrefDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(pref: SmsSenderPref): Long

    @Query("SELECT COUNT(*) FROM sms_sender_prefs WHERE sender = :sender AND policy = 'NEVER_BLOCK'")
    suspend fun isNeverBlock(sender: String): Int

    @Query("DELETE FROM sms_sender_prefs WHERE sender = :sender")
    suspend fun remove(sender: String)
}
