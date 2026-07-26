package com.messages.core.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The secret-space invisibility contract, enforced at the DAO layer: a
 * LOCKED-space message/conversation must never come back from ANY query that
 * feeds normal UI — home list, folders, FTS + LIKE search, name search,
 * starred, trash, unread badges, widgets, dashboard stats, badge metadata.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpaceInvisibilityTest {

    private lateinit var db: MessagesDatabase

    private val lockedBody = "the hidden locked message zebra"
    private val lockedAddress = "+919999888877"
    private val normalAddress = "+911111222233"

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MessagesDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // One normal thread (id 1) and one locked thread (id 2), mirrored
        // shapes: unread incoming, starred, trashed, spam, dangerous rows.
        db.conversations().upsert(
            ConversationEntity(
                threadId = 1, address = normalAddress, contactName = "Normal Friend",
                lastMessage = "hello", lastTimestamp = 1_000, unreadCount = 1,
            )
        )
        db.conversations().upsert(
            ConversationEntity(
                threadId = 2, address = lockedAddress, contactName = "Secret Friend",
                lastMessage = lockedBody, lastTimestamp = 2_000, unreadCount = 3,
                space = Spaces.LOCKED,
            )
        )
        db.messages().insert(msg(1, normalAddress, "hello zebra", Spaces.NORMAL))
        db.messages().insert(
            msg(2, lockedAddress, lockedBody, Spaces.LOCKED).copy(
                starred = true, dangerous = true, category = "INBOX",
            )
        )
        db.messages().insert(
            msg(2, lockedAddress, "locked spam offer zebra", Spaces.LOCKED)
                .copy(category = "SPAM", matchedPatternIds = "promo-offer", timestamp = 2_100)
        )
        db.messages().insert(
            msg(2, lockedAddress, "locked trashed", Spaces.LOCKED)
                .copy(trashed = true, trashedAt = 2_200, timestamp = 2_200)
        )
    }

    private fun msg(threadId: Long, address: String, body: String, space: String) = MessageEntity(
        threadId = threadId, address = address, body = body,
        normalizedBody = body.lowercase(), timestamp = 2_000, isOutgoing = false,
        read = false, space = space,
    )

    @After
    fun tearDown() = db.close()

    @Test
    fun `home list and folders exclude locked conversations`() = runTest {
        val inbox = db.conversations().byCategory("INBOX").first()
        assertEquals(listOf(1L), inbox.map { it.threadId })
        // The locked space's own list sees only its rows.
        val lockedInbox = db.conversations().byCategory("INBOX", Spaces.LOCKED).first()
        assertEquals(listOf(2L), lockedInbox.map { it.threadId })
    }

    @Test
    fun `name and address search exclude locked conversations`() = runTest {
        assertTrue(db.conversations().searchByNameOrAddress("Secret").isEmpty())
        assertTrue(db.conversations().searchByNameOrAddress("9999").isEmpty())
        assertEquals(1, db.conversations().searchByNameOrAddress("Normal").size)
    }

    @Test
    fun `FTS and LIKE search exclude locked messages`() = runTest {
        val fts = db.messages().searchFts("zebra*", 100)
        assertEquals(listOf("hello zebra"), fts.map { it.body })
        val like = db.messages().search("zebra")
        assertEquals(listOf("hello zebra"), like.map { it.body })
        // The locked body never appears even queried verbatim.
        assertTrue(db.messages().search(lockedBody).isEmpty())
    }

    @Test
    fun `starred, trash, unread badges exclude locked rows`() = runTest {
        assertTrue(db.messages().starred().first().isEmpty()) // locked star invisible
        assertTrue(db.messages().trashedMessages().first().isEmpty())
        assertEquals(0, db.messages().trashCount().first())
        assertEquals(1, db.messages().unreadCount("INBOX").first()) // normal only
        // Normal-space conversation badge count excludes the locked thread…
        assertEquals(1, db.conversations().unreadConversationCount("INBOX").first())
        // …while the locked space's own badges see their rows.
        assertEquals(1, db.conversations().unreadConversationCount("INBOX", Spaces.LOCKED).first())
        assertEquals(1, db.messages().unreadCount("INBOX", Spaces.LOCKED).first())
    }

    @Test
    fun `widgets and dashboard stats exclude locked rows`() = runTest {
        assertEquals(1, db.conversations().unreadInboxConversations())
        assertEquals(listOf(1L), db.conversations().recentUnreadInbox(5).map { it.threadId })
        assertEquals(0, db.messages().spamCountSince(0))
        assertTrue(db.messages().filteredCountsSince(0).isEmpty())
        assertTrue(db.messages().topFilteredSenders(0, 10).isEmpty())
        assertEquals(0, db.messages().totalSilenced())
        assertEquals(0, db.messages().dangerousCountSince(0))
        assertTrue(db.messages().filteredPatternIdsSince(0).isEmpty())
    }

    @Test
    fun `badge metadata excludes locked threads`() = runTest {
        val meta = db.messages().latestIncomingMeta().first()
        assertEquals(setOf(1L), meta.map { it.threadId }.toSet())
    }

    @Test
    fun `chat queries are space-scoped`() = runTest {
        // Same-thread separation: a NORMAL query on the locked thread is empty.
        assertTrue(db.messages().messagesForThread(2).first().isEmpty())
        assertEquals(2, db.messages().messagesForThread(2, Spaces.LOCKED).first().size)
        assertNull(db.conversations().byThreadId(2))
        assertNotNull(db.conversations().byThreadId(2, Spaces.LOCKED))
    }

    @Test
    fun `moving a thread between spaces carries live AND trashed rows`() = runTest {
        db.messages().setThreadSpace(2, Spaces.LOCKED, Spaces.NORMAL)
        assertEquals(2, db.messages().messagesForThread(2).first().size)
        assertTrue(db.messages().messagesForThread(2, Spaces.LOCKED).first().isEmpty())
        // The trashed locked row moved too — it is now (and only now) in normal Trash.
        assertEquals(listOf("locked trashed"), db.messages().trashedMessages().first().map { it.body })
    }

    @Test
    fun `both-space unique index allows one conversation per space per thread`() = runTest {
        // "New locked chat": same threadId, second row in the LOCKED space.
        db.conversations().upsert(
            ConversationEntity(threadId = 1, address = normalAddress, space = Spaces.LOCKED)
        )
        assertNotNull(db.conversations().byThreadId(1))
        assertNotNull(db.conversations().byThreadId(1, Spaces.LOCKED))
        assertEquals(2, db.conversations().allConversations().count { it.threadId == 1L })
    }
}
