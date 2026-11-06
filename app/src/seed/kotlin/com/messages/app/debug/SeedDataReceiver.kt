package com.messages.app.debug

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.messages.core.MessageRepository
import com.messages.core.db.ConversationEntity
import com.messages.core.db.MessageEntity
import com.messages.core.db.Spaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
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
        if (!DebugAuth.isAuthorized(context, intent)) return
        runBlocking(Dispatchers.IO) {
            try {
                val repo = MessageRepository.get(context)
                val wipe = intent.getBooleanExtra(EXTRA_WIPE, false) ||
                    intent.getStringExtra(EXTRA_WIPE)?.toBoolean() == true
                val confirm = intent.getStringExtra(EXTRA_CONFIRM)
                if (wipe && confirm == CONFIRM_WIPE_VALUE) {
                    repo.db.clearAllTables()
                }
                context.getSharedPreferences("onboarding", Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean("done", true)
                    .apply()
                seedFictionalData(repo)
                setResultCode(Activity.RESULT_OK)
                resultData = "Seeded 500 conversations across 5 categories, 100 messages in thread 1, and 1000 messages in thread 2"
            } catch (e: Exception) {
                android.util.Log.e("SeedDataReceiver", "Seed failed", e)
                setResultCode(Activity.RESULT_CANCELED)
                resultData = "Seed failed: ${e.message}"
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
            val catIndex = (i - 1) / 100
            val category = categories[catIndex]
            val address = when (i) {
                101 -> "JK-PNBSMS-S"
                102 -> "VM-SBIUPI-S"
                else -> "+1555" + String.format(Locale.US, "%07d", i)
            }
            val contactName = when (i) {
                101 -> "JK-PNBSMS-S"
                102 -> "VM-SBIUPI-S"
                else -> when (category) {
                    "INBOX" -> "${inboxNames[(i - 1) % inboxNames.size]} $i"
                    "TRANSACTIONS" -> "${bankNames[(i - 1) % bankNames.size]} $i"
                    "PROMOTIONS" -> "${promoNames[(i - 1) % promoNames.size]} $i"
                    "REVIEW" -> "${reviewNames[(i - 1) % reviewNames.size]} $i"
                    "SPAM" -> "${spamNames[(i - 1) % spamNames.size]} $i"
                    else -> "Generic Contact $i"
                }
            }
            val timestamp = baseTime + i * 60_000L
            val lastBody = when {
                i == 101 -> "Rs.2,500.00 debited from A/c XX1234 on 18-10-26. UPI Ref: 329104829104. Avl Bal Rs.18,450.00"
                i == 102 -> "Rs.750.00 credited to A/c XX5678 on 19-10-26 via UPI from merchant. Avl Bal Rs.12,300.20"
                category == "TRANSACTIONS" -> "Rs.1,250.00 debited from A/c XX4321 on 15-10-26. Avl Bal Rs.9,870.55"
                category == "PROMOTIONS" -> "Save 20% on your next order with code DEMO20 at https://example.com/deals"
                category == "REVIEW" -> "Verification code 849201 requested for device login."
                category == "SPAM" -> "You won a $1000 gift card! Claim now at https://example.com/claim"
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

            // Thread 1 and 2 messages are inserted in dedicated loops below
            if (i > 2) {
                val isOut = if (i == 101 || i == 102) false else (i % 2 == 0)
                val msg = MessageEntity(
                    threadId = threadId,
                    address = address,
                    body = lastBody,
                    normalizedBody = lastBody.lowercase(Locale.US),
                    timestamp = timestamp,
                    isOutgoing = isOut,
                    read = (i % 7 != 0),
                    category = category,
                    space = Spaces.NORMAL,
                )
                repo.db.messages().insert(msg)
            }
        }

        // 2. Seed exactly 100 messages in thread 1
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
                // OTP messages
                m == 15 -> "Your login OTP is 482910. Valid for 10 minutes. Do not share with anyone." to "REVIEW"
                m == 30 -> "Use code 918234 to verify your transaction of Rs.1,250.00." to "TRANSACTIONS"
                m == 55 -> "739102 is your verification code for ExampleBank." to "REVIEW"
                m == 78 -> "Your one-time passcode for account recovery is 654321." to "REVIEW"
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

        // 3. Seed 1,000 messages in thread 2 for 1,000+ message scroll, jump-to-bottom & overscroll verification
        val thread2Address = "+15550000002"
        val thread2BaseTime = now - 1000 * 10_000L
        for (m in 1..1000) {
            val mTimestamp = thread2BaseTime + m * 10_000L
            val isOut = (m % 2 == 0)
            val mBody = "Message $m in 1000-message thread: scroll and jump-to-bottom stress testing."
            val msg = MessageEntity(
                threadId = 2L,
                address = thread2Address,
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
        val conv2 = repo.db.conversations().byThreadId(2L, Spaces.NORMAL)
        if (conv2 != null) {
            repo.db.conversations().upsert(
                conv2.copy(
                    contactName = "Taylor Lee 2",
                    lastMessage = "Message 1000 in 1000-message thread: scroll and jump-to-bottom stress testing.",
                    lastTimestamp = now,
                )
            )
        }
    }

    companion object {
        const val ACTION_SEED = "com.messages.app.debug.SEED"
        const val EXTRA_WIPE = "wipe"
        const val EXTRA_CONFIRM = "confirm"
        const val CONFIRM_WIPE_VALUE = "WIPE_ALL_DATA"
    }
}
