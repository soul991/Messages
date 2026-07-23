package com.messages.core.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression guard for the failed-send-reasons feature: a send failure must
 * only UPDATE the outgoing message row (status + raw result code) — it must
 * never insert additional rows, and the human-readable reason must never be
 * persisted as message content (it is derived from the code at render time).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FailedSendRowsTest {

    private lateinit var db: MessagesDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MessagesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun rowCount(): Long =
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM messages").use {
            it.moveToFirst(); it.getLong(0)
        }

    private fun outgoing(body: String) = MessageEntity(
        threadId = 42L,
        address = "+919876543210",
        body = body,
        timestamp = 1_000L,
        isOutgoing = true,
        read = true,
        sendStatus = "SENDING",
    )

    @Test
    fun `failed send creates zero additional message rows`() = runTest {
        val id = db.messages().insert(outgoing("hi"))
        assertEquals(1L, rowCount())

        // The exact write SmsSentReceiver / SmsRadio perform on failure.
        db.messages().markFailed(id, 2)

        assertEquals(1L, rowCount())
        val row = db.messages().byId(id)!!
        assertEquals("FAILED", row.sendStatus)
        assertEquals(2, row.sendResultCode)
        assertEquals("hi", row.body) // content untouched
    }

    @Test
    fun `failure reason text is never persisted as message content`() = runTest {
        val id = db.messages().insert(outgoing("hello there"))
        db.messages().markFailed(id, 4)

        val bodies = db.openHelper.readableDatabase
            .query("SELECT body FROM messages").use { c ->
                buildList { while (c.moveToNext()) add(c.getString(0)) }
            }
        assertEquals(listOf("hello there"), bodies)
    }

    @Test
    fun `marking sent after failed stays failed and adds no rows`() = runTest {
        // A late-arriving success broadcast for one multipart segment must not
        // resurrect a message another segment already failed (existing markSent
        // guard) — and none of these transitions may create rows.
        val id = db.messages().insert(outgoing("multipart"))
        db.messages().markFailed(id, 1)
        db.messages().markSent(id)

        assertEquals(1L, rowCount())
        assertEquals("FAILED", db.messages().byId(id)!!.sendStatus)
    }

    @Test
    fun `successful send keeps null result code`() = runTest {
        val id = db.messages().insert(outgoing("ok"))
        db.messages().markSent(id)
        assertEquals(1L, rowCount())
        val row = db.messages().byId(id)!!
        assertEquals("SENT", row.sendStatus)
        assertNull(row.sendResultCode)
    }
}
