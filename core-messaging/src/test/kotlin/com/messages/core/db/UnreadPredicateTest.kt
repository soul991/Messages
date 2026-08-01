package com.messages.core.db

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The ONE unread predicate (`conversations.unreadCount > 0`) shared by row
 * badges, the folder-chip counts, and the Home "Unread" filter. This test is
 * the drift guard: the filter's DAO results must equal exactly the
 * conversations whose badge would render, and the chip count must equal that
 * list's size — for the selected folder, live across read / mark-unread.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UnreadPredicateTest {

    private lateinit var db: MessagesDatabase

    @Before
    fun setUp() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, MessagesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // The full zoo: unread / read / archived / other-folder / muted /
        // pinned / locked-space — only genuine INBOX badges may pass.
        db.conversations().upsert(conv(1, "INBOX", unread = 2, ts = 100))
        db.conversations().upsert(conv(2, "INBOX", unread = 0, ts = 90))
        db.conversations().upsert(conv(3, "INBOX", unread = 1, ts = 80).copy(archived = true))
        db.conversations().upsert(conv(4, "PROMOTIONS", unread = 1, ts = 70))
        db.conversations().upsert(conv(5, "INBOX", unread = 3, ts = 60).copy(muted = true))
        db.conversations().upsert(conv(6, "INBOX", unread = 1, ts = 50).copy(pinned = true))
        db.conversations().upsert(conv(7, "INBOX", unread = 5, ts = 40).copy(space = Spaces.LOCKED))
    }

    private fun conv(threadId: Long, category: String, unread: Int, ts: Long) =
        ConversationEntity(
            threadId = threadId, address = "+9190000000$threadId",
            category = category, unreadCount = unread, lastTimestamp = ts,
        )

    @After
    fun tearDown() = db.close()

    /** The invariant the user asked for: filter results == badge-bearing rows. */
    @Test
    fun `filter equals conversations with a badge, and the chip count matches`() = runTest {
        val filtered = db.conversations().byCategoryUnread("INBOX").first()
        val badged = db.conversations().byCategory("INBOX").first().filter { it.unreadCount > 0 }
        assertEquals(badged.map { it.threadId }, filtered.map { it.threadId })
        // Pinned-first, then recency — same ordering as the unfiltered list.
        assertEquals(listOf(6L, 1L, 5L), filtered.map { it.threadId })
        // Folder-chip badge = size of exactly that list.
        assertEquals(filtered.size, db.conversations().unreadConversationCount("INBOX").first())
    }

    @Test
    fun `folder scoping and space scoping hold`() = runTest {
        assertEquals(listOf(4L), db.conversations().byCategoryUnread("PROMOTIONS").first().map { it.threadId })
        // The locked thread never leaks into the normal filter…
        assertTrue(db.conversations().byCategoryUnread("INBOX").first().none { it.threadId == 7L })
        // …and the locked space's own filter/count see only theirs.
        assertEquals(
            listOf(7L),
            db.conversations().byCategoryUnread("INBOX", Spaces.LOCKED).first().map { it.threadId },
        )
        assertEquals(1, db.conversations().unreadConversationCount("INBOX", Spaces.LOCKED).first())
    }

    @Test
    fun `reading a chat drops it live and mark-as-unread adds it live`() = runTest {
        // Open chat 1 → clearUnread → drops out of filter AND chip count together.
        db.conversations().clearUnread(1, Spaces.NORMAL)
        assertEquals(listOf(6L, 5L), db.conversations().byCategoryUnread("INBOX").first().map { it.threadId })
        assertEquals(2, db.conversations().unreadConversationCount("INBOX").first())

        // Mark chat 2 as unread (badge-level marker; message rows stay read —
        // exactly the case the old message-level chip count missed).
        db.conversations().markUnread(2, Spaces.NORMAL)
        val after = db.conversations().byCategoryUnread("INBOX").first().map { it.threadId }
        assertTrue(2L in after)
        assertEquals(3, db.conversations().unreadConversationCount("INBOX").first())
    }
}
