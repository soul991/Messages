package com.personal.detectivedialer.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface BlockedNumberDao {
    @Query("SELECT * FROM blocked_numbers ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<BlockedNumber>>

    @Query("SELECT COUNT(*) FROM blocked_numbers WHERE number = :number")
    suspend fun countByNumber(number: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: BlockedNumber): Long

    @Query("DELETE FROM blocked_numbers WHERE number = :number")
    suspend fun deleteByNumber(number: String)
}

@Dao
interface AllowedNumberDao {
    @Query("SELECT * FROM allowed_numbers ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<AllowedNumber>>

    @Query("SELECT COUNT(*) FROM allowed_numbers WHERE number = :number")
    suspend fun countByNumber(number: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: AllowedNumber): Long

    @Query("DELETE FROM allowed_numbers WHERE number = :number")
    suspend fun deleteByNumber(number: String)
}

@Dao
interface CallLogDao {
    @Query("SELECT * FROM call_log ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<CallLogEntry>>

    @Query("SELECT * FROM call_log WHERE id = :id")
    fun observeById(id: String): Flow<CallLogEntry?>

    @Query("SELECT * FROM call_log WHERE id = :id")
    suspend fun getById(id: String): CallLogEntry?

    /**
     * Complete history for a set of numbers, newest-first. One number for an
     * unknown caller; the full set of a saved contact's numbers when a person's
     * calls are merged into a single timeline (unified detail page).
     */
    @Query("SELECT * FROM call_log WHERE number IN (:numbers) ORDER BY timestamp DESC")
    fun observeByNumbers(numbers: List<String>): Flow<List<CallLogEntry>>

    /**
     * Most recent row for a number within a time window — used by the telecom
     * layer to reconcile a real call outcome with the pre-ring screening row for
     * the same call, so one physical call is a single history entry.
     */
    @Query(
        "SELECT * FROM call_log WHERE number = :number AND timestamp BETWEEN :from AND :to " +
            "ORDER BY timestamp DESC LIMIT 1",
    )
    suspend fun findRecentByNumber(number: String, from: Long, to: Long): CallLogEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: CallLogEntry)

    /** Stamp a freshly-resolved display name onto every history row for a number. */
    @Query("UPDATE call_log SET callerName = :name WHERE number = :number")
    suspend fun updateCallerName(number: String, name: String)

    /** Distinct numbers whose rows still have no display name — backfill candidates. */
    @Query("SELECT DISTINCT number FROM call_log WHERE callerName = '' AND number != '' LIMIT :limit")
    suspend fun numbersMissingName(limit: Int): List<String>

    @Query("SELECT COUNT(*) FROM call_log WHERE timestamp >= :since")
    fun observeCountSince(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM call_log WHERE category = :category AND timestamp >= :since")
    fun observeCountByCategorySince(category: String, since: Long): Flow<Int>
}
