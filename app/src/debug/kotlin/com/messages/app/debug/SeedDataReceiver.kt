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
 * - Fictional 555-range numbers (+15550000001 .. +15550000500) and generic names (e.g. "ExampleBank", "ApexCreditUnion").
 * - 100 messages in thread 1 with varied lengths, links, emoji-only, and bank/bill summary cards.
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

        val inboxNames = listOf("Alex Morgan", "Taylor Lee", "Jordan Casey", "Chris Parker", "Sam Rivera")
        val bankNames = listOf("ExampleBank", "ApexCreditUnion", "FirstNationalSavings", "MetroPayService", "CommunityTrustBank")
        val promoNames = listOf("ExampleDeals", "SampleRetailer", "DemoStoreOffers", "MegaSaleDirect", "ShopSmartUpdates")
        val reviewNames = listOf("VerifyDesk", "LoginSecurityNotice", "UnknownSenderAlert", "SystemVerification", "ServiceNoticeHub")
        val spamNames = listOf("ClaimRewardNow", "LotteryWinnerNotice", "FreePrizeHub", "UrgentGiftCard", "SpecialBonusAlert")

        // 1. Seed 500 conversations across 5 categories (100 in each)
        for (i in 1..500) {
            val threadId = i.toLong()
            val address = "+1555" + String.format(Locale.US, "%07d", i)
            val catIndex = (i - 1) / 100
            val category = categories[catIndex]
            val contactName = when (category) {
                "INBOX" -> "${inboxNames[(i - 1) % inboxNames.size]} $i"
                "TRANSACTIONS" -> "${bankNames[(i - 1) % bankNames.size]} $i"
                "PROMOTIONS" -> "${promoNames[(i - 1) % promoNames.size]} $i"
                "REVIEW" -> "${reviewNames[(i - 1) % reviewNames.size]} $i"
                "SPAM" -> "${spamNames[(i - 1) % spamNames.size]} $i"
                else -> "Generic Contact $i"
            }
            val timestamp = baseTime + i * 60_000L
            val lastBody = when (category) {
                "TRANSACTIONS" -> "Rs.1,250.00 debited from A/c XX4321 on 15-10-26. Avl Bal Rs.9,870.55"
                "PROMOTIONS" -> "Save 20% on your next order with code DEMO20 at https://example.com/deals"
                "REVIEW" -> "Verification code 849201 requested for device login."
                "SPAM" -> "You won a $1000 gift card! Claim now at https://example.com/claim"
                else -> "Hey, let's catch up later today!"
            }

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
        val thread1BaseTime = now - 100 * 15_000L
        for (m in 1..100) {
            val mTimestamp = thread1BaseTime + m * 15_000L
            val isOut = (m % 2 == 0)
            val (mBody, mCategory) = when {
                // Bank/bill summary cards (eligible for CardExtractor)
                m == 10 -> "Rs.1,250.00 debited from A/c XX4321 on 15-10-26. Avl Bal Rs.9,870.55" to "TRANSACTIONS"
                m == 25 -> "Rs.5,000.00 credited to A/c XX4321 on 16-10-26. Avl Bal Rs.14,870.55" to "TRANSACTIONS"
                m == 40 -> "Your bill of Rs.840.00 for account ending in 4321 is due on 28-10-26." to "TRANSACTIONS"
                m == 65 -> "Rs.320.50 debited from A/c XX4321 at ExampleStore on 17-10-26. Avl Bal Rs.14,550.05" to "TRANSACTIONS"
                m == 85 -> "Your utility bill payment of Rs.1,100.00 was successful. Ref: 987654321." to "TRANSACTIONS"
                // Emoji-only
                m % 17 == 0 -> "👍🎉🚀" to "INBOX"
                m % 23 == 0 -> "❤️🙌" to "INBOX"
                m % 31 == 0 -> "👋😄" to "INBOX"
                m % 47 == 0 -> "🔥💯" to "INBOX"
                // Links
                m % 9 == 0 -> "Check out the project notes at https://example.com/project-updates and let me know your thoughts." to "INBOX"
                m % 13 == 0 -> "Receipt for transaction is available at https://example.org/receipt/88492" to "INBOX"
                m % 19 == 0 -> "Track your delivery package at https://example.com/tracking/pkg-9481" to "INBOX"
                // Short messages
                m % 3 == 0 -> "Sounds good to me!" to "INBOX"
                m % 5 == 0 -> "Will do, thanks." to "INBOX"
                m % 7 == 0 -> "On my way now." to "INBOX"
                // Long multi-sentence messages
                m % 8 == 0 -> "We finished reviewing the UI motion specifications. All spring parameters now use spatial and effects curves consistently, and all non-token corner radii have been normalized to the design system scale. The benchmark receiver will simulate real scrolling loads." to "INBOX"
                else -> "Benchmark conversation test message $m for thread 1 performance verification." to "INBOX"
            }

            val msg = MessageEntity(
                threadId = 1L,
                address = thread1Address,
                body = mBody,
                normalizedBody = mBody.lowercase(Locale.US),
                timestamp = mTimestamp,
                isOutgoing = isOut,
                read = true,
                category = mCategory,
                space = Spaces.NORMAL,
            )
            repo.db.messages().insert(msg)
        }

        // Update thread 1 conversation's last message to the 100th message
        val conv1 = repo.db.conversations().byThreadId(1L, Spaces.NORMAL)
        if (conv1 != null) {
            repo.db.conversations().upsert(
                conv1.copy(
                    contactName = "Alex Morgan 1",
                    lastMessage = "Benchmark conversation test message 100 for thread 1 performance verification.",
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
