package com.messages.app.debug

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.messages.core.MessageRepository
import com.messages.core.db.ConversationEntity
import com.messages.core.db.MessageEntity
import com.messages.core.db.Spaces
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * DEBUG/BENCHMARK ONLY: On-demand fictional test data seed BroadcastReceiver (Phase 6).
 *
 * Responds to `com.messages.app.debug.SEED`.
 * If `wipe=true` extra is passed, clears existing database tables before seeding.
 * Seeds:
 * - 500 conversations across 5 categories (100 INBOX, 100 TRANSACTIONS, 100 PROMOTIONS, 100 REVIEW, 100 SPAM).
 * - Fictional 555-range numbers (+15550000001 .. +15550000500).
 * - 100 messages in one chat (threadId 1).
 *
 * Declared ONLY in app/src/debug/AndroidManifest.xml and app/src/benchmark/AndroidManifest.xml.
 * Never present in release builds.
 */
class SeedDataReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SEED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            try {
                val repo = MessageRepository.get(context)
                val wipe = intent.getBooleanExtra(EXTRA_WIPE, false) ||
                    intent.getStringExtra(EXTRA_WIPE)?.toBoolean() == true
                if (wipe) {
                    repo.db.clearAllTables()
                }
                seedFictionalData(repo)
                pendingResult.setResultCode(Activity.RESULT_OK)
                pendingResult.resultData = "Seeded 500 conversations across 5 categories and 100 messages in thread 1"
            } catch (e: Exception) {
                pendingResult.setResultCode(Activity.RESULT_CANCELED)
                pendingResult.resultData = "Seed failed: ${e.message}"
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun seedFictionalData(repo: MessageRepository) {
        val categories = listOf("INBOX", "TRANSACTIONS", "PROMOTIONS", "REVIEW", "SPAM")
        val now = System.currentTimeMillis()
        val baseTime = now - 500 * 60_000L

        // 1. Seed 500 conversations across 5 categories (100 in each)
        for (i in 1..500) {
            val threadId = i.toLong()
            val address = "+1555" + String.format(Locale.US, "%07d", i)
            val category = categories[(i - 1) / 100]
            val contactName = "Fictional Contact $i"
            val timestamp = baseTime + i * 60_000L
            val lastBody = "Latest message $i in category $category"

            val conv = ConversationEntity(
                threadId = threadId,
                address = address,
                contactName = contactName,
                lastMessage = lastBody,
                lastTimestamp = timestamp,
                unreadCount = if (i % 7 == 0) 1 else 0,
                category = category,
                pinned = (i <= 3),
                archived = false,
                muted = false,
                locked = false,
                space = Spaces.NORMAL,
            )
            repo.db.conversations().upsert(conv)

            val msg = MessageEntity(
                threadId = threadId,
                address = address,
                body = lastBody,
                normalizedBody = lastBody.lowercase(Locale.US),
                timestamp = timestamp,
                isOutgoing = (i % 2 == 0),
                read = (i % 7 != 0),
                category = category,
                space = Spaces.NORMAL,
            )
            repo.db.messages().insert(msg)
        }

        // 2. Seed 100 messages in one chat (threadId 1)
        val thread1Address = "+15550000001"
        val thread1BaseTime = now - 100 * 10_000L
        for (m in 1..100) {
            val mTimestamp = thread1BaseTime + m * 10_000L
            val mBody = "Benchmark test message $m for thread 1 performance testing."
            val isOut = (m % 2 == 0)
            val msg = MessageEntity(
                threadId = 1L,
                address = thread1Address,
                body = mBody,
                normalizedBody = mBody.lowercase(Locale.US),
                timestamp = mTimestamp,
                isOutgoing = isOut,
                read = true,
                category = "INBOX",
                space = Spaces.NORMAL,
            )
            repo.db.messages().insert(msg)
        }

        // Update thread 1 conversation's last message to the 100th message
        val conv1 = repo.db.conversations().byThreadId(1L, Spaces.NORMAL)
        if (conv1 != null) {
            repo.db.conversations().upsert(
                conv1.copy(
                    lastMessage = "Benchmark test message 100 for thread 1 performance testing.",
                    lastTimestamp = now,
                )
            )
        }
    }

    companion object {
        const val ACTION_SEED = "com.messages.app.debug.SEED"
        const val EXTRA_WIPE = "wipe"
    }
}
