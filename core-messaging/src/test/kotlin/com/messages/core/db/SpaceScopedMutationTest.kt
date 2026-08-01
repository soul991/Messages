package com.messages.core.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * V2-24: "New locked chat" gives one system thread TWO conversation rows —
 * one per space — with the same `threadId`. Every mutation that names a
 * threadId must therefore also name a space.
 *
 * The bug this locks down: the DAO carried `space: String = Spaces.NORMAL`
 * defaults, so a call that forgot the argument did not fail — it quietly wrote
 * to the *other* person's conversation. `ChatViewModel.selectSim` was exactly
 * that call: choosing a SIM inside a locked chat re-pointed the normal-space
 * row's `preferredSubId`, so the next normal-space message went out on a SIM
 * the user never picked for it.
 *
 * The defaults are gone now, which makes the omission a compile error rather
 * than a silent cross-space write. These tests assert the other half: that
 * each mutation, given a space, touches only that space's row.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpaceScopedMutationTest {

    private lateinit var db: MessagesDatabase

    /** The same system thread, present on both sides of the wall. */
    private val threadId = 42L

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MessagesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        db.conversations().upsert(conv(Spaces.NORMAL))
        db.conversations().upsert(conv(Spaces.LOCKED))
    }

    private fun conv(space: String) = ConversationEntity(
        threadId = threadId,
        address = "+919000000042",
        category = "INBOX",
        unreadCount = 3,
        lastTimestamp = 100,
        space = space,
    )

    private fun msg(space: String, body: String) = MessageEntity(
        threadId = threadId,
        address = "+919000000042",
        body = body,
        timestamp = 100,
        isOutgoing = false,
        read = false,
        space = space,
    )

    @After
    fun tearDown() = db.close()

    private suspend fun normal() = db.conversations().byThreadId(threadId, Spaces.NORMAL)
    private suspend fun locked() = db.conversations().byThreadId(threadId, Spaces.LOCKED)

    // ---- the reported defect ----

    @Test
    fun `choosing a SIM in the locked chat leaves the normal row alone`() = runTest {
        db.conversations().setPreferredSubId(threadId, 2, Spaces.LOCKED)

        assertEquals(2, locked()!!.preferredSubId)
        assertNull("normal-space SIM choice must not move", normal()!!.preferredSubId)
    }

    @Test
    fun `the two rows hold independent SIM choices`() = runTest {
        db.conversations().setPreferredSubId(threadId, 1, Spaces.NORMAL)
        db.conversations().setPreferredSubId(threadId, 2, Spaces.LOCKED)

        assertEquals(1, normal()!!.preferredSubId)
        assertEquals(2, locked()!!.preferredSubId)
    }

    // ---- every other space-sensitive conversation mutation ----

    @Test
    fun `pin archive and mute stay on their own side of the wall`() = runTest {
        db.conversations().setPinned(threadId, true, Spaces.LOCKED)
        db.conversations().setArchived(threadId, true, Spaces.LOCKED)
        db.conversations().setMuted(threadId, true, Spaces.LOCKED)

        assertTrue(locked()!!.pinned)
        assertTrue(locked()!!.archived)
        assertTrue(locked()!!.muted)
        assertFalse(normal()!!.pinned)
        assertFalse(normal()!!.archived)
        assertFalse(normal()!!.muted)
    }

    @Test
    fun `unread state is per space`() = runTest {
        db.conversations().clearUnread(threadId, Spaces.LOCKED)

        assertEquals(0, locked()!!.unreadCount)
        assertEquals(3, normal()!!.unreadCount)

        db.conversations().clearUnread(threadId, Spaces.NORMAL)
        db.conversations().markUnread(threadId, Spaces.NORMAL)

        assertEquals(1, normal()!!.unreadCount)
        assertEquals(0, locked()!!.unreadCount)
    }

    @Test
    fun `renaming a conversation renames one row`() = runTest {
        db.conversations().setContactName(threadId, "Vault", Spaces.LOCKED)

        assertEquals("Vault", locked()!!.contactName)
        assertNull(normal()!!.contactName)
    }

    @Test
    fun `deleting a conversation deletes one row`() = runTest {
        db.conversations().deleteByThreadId(threadId, Spaces.LOCKED)

        assertNull(locked())
        assertNotNull("the normal-space conversation must survive", normal())
    }

    // ---- message-level mutations ----

    @Test
    fun `marking a thread read marks only that space's messages`() = runTest {
        db.messages().insert(msg(Spaces.NORMAL, "normal"))
        db.messages().insert(msg(Spaces.LOCKED, "locked"))

        db.messages().markThreadRead(threadId, Spaces.LOCKED)

        assertTrue(db.messages().listForThread(threadId, Spaces.LOCKED).all { it.read })
        assertTrue(db.messages().listForThread(threadId, Spaces.NORMAL).none { it.read })
    }

    @Test
    fun `trashing a thread trashes only that space's messages`() = runTest {
        db.messages().insert(msg(Spaces.NORMAL, "normal"))
        db.messages().insert(msg(Spaces.LOCKED, "locked"))

        db.messages().moveThreadToTrash(threadId, 1_700_000_000_000, Spaces.LOCKED)

        // listForThread returns live rows only, so the locked side goes empty
        // while the normal-space conversation is untouched.
        assertTrue(db.messages().listForThread(threadId, Spaces.LOCKED).isEmpty())
        assertEquals(
            listOf("normal"),
            db.messages().listForThread(threadId, Spaces.NORMAL).map { it.body },
        )
    }
}
