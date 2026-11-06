package com.messages.core.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.messages.core.cleanup.SyntheticCleanup
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SyntheticCleanupTest {

    private lateinit var db: MessagesDatabase
    private lateinit var context: Context

    private val targetThreadId = 100L
    private val syntheticAddress = "+1 (555) 019-2834"
    private val differentAddress = "+1 (555) 888-9999"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MessagesDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        context.getSharedPreferences(SyntheticCleanup.PREFS_SETTINGS, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()

        context.getSharedPreferences(SyntheticCleanup.PREFS_REACTIONS, Context.MODE_PRIVATE)
            .edit()
            .putString("1", "👍")
            .commit()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `cleanup removes only synthetic signature rows in thread 100 and preserves real rows`() = runTest {
        // (a) 100 synthetic-signature rows: threadId == 100, address == "+1 (555) 019-2834", smsId 900001..900100
        for (i in 1..100) {
            db.messages().insert(
                MessageEntity(
                    smsId = 900000L + i,
                    threadId = targetThreadId,
                    address = syntheticAddress,
                    body = "synthetic message $i",
                    normalizedBody = "synthetic message $i",
                    timestamp = 1000L + i,
                    isOutgoing = (i % 2 == 0),
                    read = true,
                    space = Spaces.NORMAL,
                )
            )
        }

        // (b) 3 real rows in thread 100 with a different address
        val realDifferentAddressIds = listOf(5001L, 5002L, 5003L)
        for (smsId in realDifferentAddressIds) {
            db.messages().insert(
                MessageEntity(
                    smsId = smsId,
                    threadId = targetThreadId,
                    address = differentAddress,
                    body = "real message different address smsId $smsId",
                    normalizedBody = "real message different address smsId $smsId",
                    timestamp = 2000L + smsId,
                    isOutgoing = false,
                    read = true,
                    space = Spaces.NORMAL,
                )
            )
        }

        // (c) 2 rows with the synthetic address but smsId outside the 900001..900100 range
        val outsideRangeIds = listOf(900000L, 900101L)
        for (smsId in outsideRangeIds) {
            db.messages().insert(
                MessageEntity(
                    smsId = smsId,
                    threadId = targetThreadId,
                    address = syntheticAddress,
                    body = "real message outside range smsId $smsId",
                    normalizedBody = "real message outside range smsId $smsId",
                    timestamp = 3000L + smsId,
                    isOutgoing = false,
                    read = true,
                    space = Spaces.NORMAL,
                )
            )
        }

        val totalBefore = db.messages().listForThread(targetThreadId, Spaces.NORMAL)
        assertEquals(105, totalBefore.size)

        // Perform cleanup
        val deleted = SyntheticCleanup.perform(context, db)
        assertEquals(100, deleted)

        // After cleanup: (a) gone, (b) and (c) untouched
        val remaining = db.messages().listForThread(targetThreadId, Spaces.NORMAL)
        assertEquals(5, remaining.size)

        // Verify (b) untouched
        val remainingDifferentAddress = remaining.filter { it.address == differentAddress }
        assertEquals(3, remainingDifferentAddress.size)
        assertEquals(realDifferentAddressIds.toSet(), remainingDifferentAddress.map { it.smsId }.toSet())

        // Verify (c) untouched
        val remainingSyntheticAddress = remaining.filter { it.address == syntheticAddress }
        assertEquals(2, remainingSyntheticAddress.size)
        assertEquals(outsideRangeIds.toSet(), remainingSyntheticAddress.map { it.smsId }.toSet())

        // Verify SharedPreferences flag is set
        val settingsPrefs = context.getSharedPreferences(SyntheticCleanup.PREFS_SETTINGS, Context.MODE_PRIVATE)
        assertTrue(settingsPrefs.getBoolean(SyntheticCleanup.FLAG_CLEANUP, false))

        // Verify reaction preferences removed / cleared
        val reactionPrefs = context.getSharedPreferences(SyntheticCleanup.PREFS_REACTIONS, Context.MODE_PRIVATE)
        assertTrue(reactionPrefs.all.isEmpty())

        // Verify idempotency: subsequent cleanup does nothing
        val secondDeleted = SyntheticCleanup.perform(context, db)
        assertEquals(0, secondDeleted)
        assertEquals(5, db.messages().listForThread(targetThreadId, Spaces.NORMAL).size)
    }
}
