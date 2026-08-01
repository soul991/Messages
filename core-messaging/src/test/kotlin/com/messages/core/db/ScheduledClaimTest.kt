package com.messages.core.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * V2-19 regression guard: promoting a SCHEDULED message must be a
 * compare-and-set, not a read-then-check.
 *
 * The bug this locks down: the WorkManager fire and the user's "Send now"
 * button can run concurrently. Both used to read the row, both saw
 * `sendStatus == "SCHEDULED"`, and both proceeded to write a provider row and
 * hand the message to the radio — the recipient got it twice.
 *
 * `claimScheduled` must therefore return 1 for exactly one caller and 0 for
 * every other caller, regardless of ordering.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ScheduledClaimTest {

    private lateinit var db: MessagesDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MessagesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

    private fun scheduled(body: String = "later") = MessageEntity(
        threadId = 42L,
        address = "+919876543210",
        body = body,
        timestamp = 9_999_000L,
        isOutgoing = true,
        read = true,
        sendStatus = "SCHEDULED",
    )

    @Test
    fun `only the first claimant wins`() = runTest {
        val id = db.messages().insert(scheduled())

        // Worker fires and the user taps "Send now" — whichever order.
        val first = db.messages().claimScheduled(id)
        val second = db.messages().claimScheduled(id)

        assertEquals("first claim must win", 1, first)
        assertEquals("second claim must be refused", 0, second)
        assertEquals("CLAIMED", db.messages().byId(id)!!.sendStatus)
    }

    @Test
    fun `a message already sending cannot be claimed`() = runTest {
        val id = db.messages().insert(scheduled())
        db.messages().claimScheduled(id)
        db.messages().setSendStatus(id, "SENDING", null)

        assertEquals(0, db.messages().claimScheduled(id))
        assertEquals("SENDING", db.messages().byId(id)!!.sendStatus)
    }

    @Test
    fun `releasing a claim makes the message schedulable again`() = runTest {
        val id = db.messages().insert(scheduled())
        db.messages().claimScheduled(id)

        db.messages().releaseScheduledClaim(id)

        assertEquals("SCHEDULED", db.messages().byId(id)!!.sendStatus)
        assertEquals("must be re-claimable after release", 1, db.messages().claimScheduled(id))
    }

    @Test
    fun `release never disturbs a message that is genuinely sending`() = runTest {
        val id = db.messages().insert(scheduled())
        db.messages().setSendStatus(id, "SENDING", null)

        db.messages().releaseScheduledClaim(id)

        assertEquals("SENDING", db.messages().byId(id)!!.sendStatus)
    }

    @Test
    fun `startup recovery releases only stale claims`() = runTest {
        val claimed = db.messages().insert(scheduled("claimed"))
        val sending = db.messages().insert(scheduled("sending"))
        val stillScheduled = db.messages().insert(scheduled("waiting"))
        db.messages().claimScheduled(claimed)
        db.messages().setSendStatus(sending, "SENDING", null)

        // Process death happened here; this is what Application.onCreate runs.
        val released = db.messages().releaseAllScheduledClaims()

        assertEquals("exactly the stranded row", 1, released)
        assertEquals("SCHEDULED", db.messages().byId(claimed)!!.sendStatus)
        assertEquals("SENDING", db.messages().byId(sending)!!.sendStatus)
        assertEquals("SCHEDULED", db.messages().byId(stillScheduled)!!.sendStatus)
    }
}
