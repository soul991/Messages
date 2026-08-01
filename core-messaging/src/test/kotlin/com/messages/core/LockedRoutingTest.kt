package com.messages.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.messages.core.db.Spaces
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The secret-space routing rule, end-to-end through the real
 * [MessageRepository.onIncomingSms] pipeline (classification included):
 * once a LOCKED conversation exists for an address, EVERY incoming message
 * from it lands in the locked space — never the normal thread — and the
 * classifier still runs (spam files into the locked space's own folders).
 *
 * Single test method by design: MessageRepository is a process singleton and
 * Robolectric rebuilds the application per method — one method, one world.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LockedRoutingTest {

    @org.junit.Before
    fun freshSingleton() {
        MessageRepository.resetForTests()
        // V2-6: the real key box fails closed under Robolectric, which would
        // make every locked row here fall back to plaintext and quietly stop
        // this test from covering encryption at all.
        com.messages.core.secret.TestKeyBox.install()
    }

    @org.junit.After
    fun restoreKeyBox() = com.messages.core.secret.TestKeyBox.uninstall()

    @Test
    fun `incoming messages route to the locked conversation once it exists`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = MessageRepository.get(context)
        val db = repo.db
        val friend = "+919876543210"

        // 1) No locked space at all → normal routing.
        val (before, _) = repo.onIncomingSms(friend, "hey, lunch tomorrow?", 1_000)
        assertEquals(Spaces.NORMAL, before.space)
        val threadId = before.threadId
        assertNotNull(db.conversations().byThreadId(threadId))

        // 2) "New locked chat": history stays, locked conversation appears.
        repo.createLockedConversation(threadId)
        assertNotNull(db.conversations().byThreadId(threadId, Spaces.LOCKED))
        assertNotNull(db.conversations().byThreadId(threadId)) // normal row intact

        // 3) THE routing rule: every new incoming from that address is LOCKED.
        val (after, _) = repo.onIncomingSms(friend, "see you at 8", 2_000)
        assertEquals(Spaces.LOCKED, after.space)
        assertEquals(threadId, after.threadId)

        // Normal surfaces never see it: not in the normal chat query, not in
        // the normal conversation summary, not in search.
        val normalBodies = db.messages().listForThread(threadId).map { it.body }
        assertTrue("see you at 8" !in normalBodies)
        assertEquals("hey, lunch tomorrow?", db.conversations().byThreadId(threadId)!!.lastMessage)
        assertTrue(db.messages().search("see you at 8").isEmpty())
        // The locked conversation carries it (summary + unread) — sealed at
        // rest, so the stored preview is ciphertext and only opens on read.
        val lockedConv = db.conversations().byThreadId(threadId, Spaces.LOCKED)!!
        assertTrue(
            "the locked preview must not sit in the clear in the conversation row",
            com.messages.core.secret.LockedContent.isSealed(lockedConv.lastMessage),
        )
        assertEquals(
            "see you at 8",
            com.messages.core.secret.LockedContent.open(context, lockedConv).lastMessage,
        )
        assertEquals(1, lockedConv.unreadCount)

        // 4) Classifier still runs: obvious scam text from the locked address
        // files as SPAM *inside the locked space*, never the normal Spam.
        val (scam, verdict) = repo.onIncomingSms(
            friend, "Congratulations! You WON Rs 25,00,000 claim now bit.ly/xyz", 3_000,
        )
        assertEquals(Spaces.LOCKED, scam.space)
        assertEquals("SPAM", verdict.category.name)
        assertEquals(0, db.messages().spamCountSince(0)) // normal-space stat untouched

        // 5) Another sender stays normal — the rule is per-address.
        val (other, _) = repo.onIncomingSms("+911234512345", "unrelated", 4_000)
        assertEquals(Spaces.NORMAL, other.space)

        // 6) Unlock (move back): everything returns to the normal thread.
        repo.moveThreadToSpace(threadId, Spaces.LOCKED, Spaces.NORMAL)
        assertNull(db.conversations().byThreadId(threadId, Spaces.LOCKED))
        val normalAfter = db.messages().listForThread(threadId).map { it.body }
        assertTrue("see you at 8" in normalAfter)
        // And with no locked conversation left, routing reverts to normal.
        val (postUnlock, _) = repo.onIncomingSms(friend, "back to normal", 5_000)
        assertEquals(Spaces.NORMAL, postUnlock.space)
    }
}
