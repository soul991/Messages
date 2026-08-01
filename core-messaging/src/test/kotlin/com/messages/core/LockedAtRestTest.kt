package com.messages.core

import android.content.ContentValues
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.messages.core.db.MessageEntity
import com.messages.core.db.Spaces
import com.messages.core.secret.LockedContent
import com.messages.core.secret.TestKeyBox
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * V2-6 end-to-end: what a locked message actually leaves behind on disk.
 *
 * The codex finding was that "locked" meant hidden, not protected — the body
 * sat in this app's index as ordinary TEXT, it was mirrored into the FTS
 * table, and a second readable copy stayed in the Telephony provider where any
 * app holding the SMS role could read it. Three places, none of them
 * encrypted. This pins all three shut, and pins the two directions of the
 * space boundary: sealing on the way in, and un-sealing on the way out (a row
 * left as ciphertext after being unlocked would be permanently unreadable once
 * the content key was gone).
 *
 * One test method per concern, but the repository is a process singleton and
 * Robolectric rebuilds the application per method — so each method uses its
 * own address.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LockedAtRestTest {

    private lateinit var context: Context
    private lateinit var repo: MessageRepository
    private lateinit var provider: FakeSmsProvider

    @Before
    fun setUp() {
        MessageRepository.resetForTests()
        TestKeyBox.install()
        context = ApplicationProvider.getApplicationContext()
        provider = Robolectric.buildContentProvider(FakeSmsProvider::class.java).create("sms").get()
        // Registration probe — Robolectric fabricates insert Uris for
        // unregistered authorities, which would fake out every assertion here.
        context.contentResolver.insert(
            android.provider.Telephony.Sms.Inbox.CONTENT_URI,
            ContentValues().apply { put("body", "probe") },
        )
        assertTrue("Fake sms provider not wired", provider.rows.isNotEmpty())
        repo = MessageRepository.get(context)
        assertTrue("key box not in force", LockedContent.available(context))
    }

    @After
    fun tearDown() = TestKeyBox.uninstall()

    /**
     * Hits in the FTS4 mirror itself, with no `space = 'NORMAL'` join filter.
     *
     * The DAO's search queries do filter, but a filter is invisibility, not
     * confidentiality — it protects nothing from anything reading the database
     * file, and it is one forgotten WHERE clause away from failing. The claim
     * V2-6 makes is stronger and is about the index's contents: `messages_fts`
     * is external-content over `body`/`normalizedBody`, so sealing those
     * columns is what leaves the search index with nothing to give up. That is
     * what this measures.
     */
    private fun ftsHits(word: String): Int =
        repo.db.openHelper.readableDatabase
            .query("SELECT docid FROM messages_fts WHERE messages_fts MATCH '$word'")
            .use { it.count }

    @Test
    fun `a message received into the locked space is unreadable everywhere it is stored`() =
        runTest {
            val friend = "+919876522221"
            val db = repo.db
            val (seed, _) = repo.onIncomingSms(friend, "ordinary opener", 1_000)
            repo.createLockedConversation(seed.threadId)

            val secret = "the flat is on Wednesday, kaleidoscope"
            val (routed, _) = repo.onIncomingSms(friend, secret, 2_000)
            assertEquals(Spaces.LOCKED, routed.space)

            // 1) The index row is ciphertext, in both mirrored columns.
            val stored = db.messages().byId(routed.id)!!
            assertTrue(LockedContent.isSealed(stored.body))
            assertTrue(LockedContent.isSealed(stored.normalizedBody))
            assertFalse(stored.body.contains("kaleidoscope"))

            // 2) …so the search index has nothing to give up either.
            assertEquals(0, ftsHits("kaleidoscope"))

            // 3) No Telephony copy survives.
            assertFalse(
                "the shared message store still holds the locked message",
                provider.rows.values.any { it.getAsString("body") == secret },
            )

            // 4) And the app can still read it back exactly.
            assertEquals(secret, LockedContent.open(context, stored).body)
        }

    @Test
    fun `locking an existing thread seals its history and deletes the shared copies`() = runTest {
        val friend = "+919876522222"
        val db = repo.db
        val (a, _) = repo.onIncomingSms(friend, "history one periwinkle", 1_000)
        val (b, _) = repo.onIncomingSms(friend, "history two periwinkle", 2_000)
        assertEquals(Spaces.NORMAL, a.space)
        assertTrue(provider.rows.containsKey(a.smsId!!))
        assertEquals(2, ftsHits("periwinkle"))

        repo.moveThreadToSpace(a.threadId, Spaces.NORMAL, Spaces.LOCKED)

        db.messages().allInSpace(Spaces.LOCKED).also { rows ->
            assertEquals(2, rows.size)
            rows.forEach { assertTrue(LockedContent.isSealed(it.body)) }
        }
        assertEquals(0, ftsHits("periwinkle"))
        listOfNotNull(a.smsId, b.smsId).forEach {
            assertFalse("provider copy survived locking", provider.rows.containsKey(it))
        }
    }

    @Test
    fun `unlocking a thread returns it to plaintext, so it survives losing the key`() = runTest {
        val friend = "+919876522223"
        val db = repo.db
        val (a, _) = repo.onIncomingSms(friend, "marmalade morning", 1_000)
        repo.moveThreadToSpace(a.threadId, Spaces.NORMAL, Spaces.LOCKED)
        assertTrue(LockedContent.isSealed(db.messages().byId(a.id)!!.body))

        repo.moveThreadToSpace(a.threadId, Spaces.LOCKED, Spaces.NORMAL)

        val back = db.messages().byId(a.id)!!
        assertEquals(Spaces.NORMAL, back.space)
        assertFalse(
            "an unlocked row left as ciphertext would die with the content key",
            LockedContent.isSealed(back.body),
        )
        assertEquals("marmalade morning", back.body)
        // Searchable again — the FTS mirror follows the source columns.
        assertEquals(1, ftsHits("marmalade"))
    }

    @Test
    fun `the backlog repair seals rows that predate encryption, and is idempotent`() = runTest {
        val friend = "+919876522224"
        val db = repo.db
        // A row exactly as a pre-V2-6 build would have left it: locked space,
        // plaintext body, plaintext normalized mirror.
        val id = db.messages().insert(
            MessageEntity(
                threadId = 4_242, address = friend, body = "legacy thistle",
                normalizedBody = "legacy thistle", timestamp = 1_000,
                isOutgoing = false, space = Spaces.LOCKED,
            )
        )
        // Exactly the exposure the finding described: locked, and sitting in
        // the search index in the clear.
        assertEquals(1, ftsHits("thistle"))

        assertEquals("one legacy row to repair", 1, repo.sealLockedBacklog())

        val sealed = db.messages().byId(id)!!
        assertTrue(LockedContent.isSealed(sealed.body))
        assertEquals("legacy thistle", LockedContent.open(context, sealed).body)
        assertEquals("the repair must empty the index too", 0, ftsHits("thistle"))

        // Resumable/idempotent, and this runs on every cold start — a second
        // pass must find nothing to do AND must not rewrite the row. Sealing
        // draws a fresh nonce, so a repair that decided by comparing
        // before/after ciphertext would churn the whole locked space (and its
        // FTS triggers) on every launch while reporting work it did not need
        // to do. Pinning the stored bytes is what catches that.
        assertEquals("second pass must be a no-op", 0, repo.sealLockedBacklog())
        val after = db.messages().byId(id)!!
        assertEquals("an already-sealed row must not be re-sealed", sealed.body, after.body)
        assertEquals("legacy thistle", LockedContent.open(context, after).body)
    }
}
