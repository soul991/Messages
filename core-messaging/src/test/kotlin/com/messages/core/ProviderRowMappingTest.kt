package com.messages.core

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * In-memory stand-in for the system SMS provider, with deletion failure
 * injection so the retry path (R-05) can be exercised. Robolectric has no real
 * Telephony provider — without this, inserts return fabricated Uris and every
 * provider-row assertion below would pass vacuously.
 */
class FailableSmsProvider : ContentProvider() {
    val rows = LinkedHashMap<Long, ContentValues>()

    /** While true, every delete throws — the "default-SMS role lost" case. */
    var deletesFail = false

    private var nextId = 1L

    override fun onCreate() = true

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val id = nextId++
        rows[id] = ContentValues(values)
        return Uri.parse("content://sms/$id")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        if (deletesFail) throw SecurityException("no default-SMS role")
        val id = uri.lastPathSegment?.toLongOrNull() ?: return 0
        return if (rows.remove(id) != null) 1 else 0
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor = MatrixCursor(arrayOf("_id")).apply { rows.keys.forEach { addRow(arrayOf(it)) } }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun getType(uri: Uri): String? = null
}

/**
 * R-05: a group SMS writes ONE Telephony row per recipient. The message row
 * only ever remembered the first, so deleting a group message orphaned the
 * others — permanently visible to every other SMS app and outside this app's
 * trash/retention model.
 *
 * Covers the four behaviours the review requires:
 *  1. two- and three-recipient sends produce a mapping per recipient;
 *  2. trash/delete attempts EVERY mapped provider URI;
 *  3. a provider deletion that fails is retained and later retried;
 *  4. undo/restore does not duplicate provider rows.
 *
 * R-06 lives here too: the conversation summary must follow the latest live
 * message, not the last one that happened to arrive.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ProviderRowMappingTest {

    private lateinit var provider: FailableSmsProvider
    private lateinit var repo: MessageRepository

    @Before
    fun setUp() {
        MessageRepository.resetForTests()
        val context = ApplicationProvider.getApplicationContext<Context>()
        provider = Robolectric.buildContentProvider(FailableSmsProvider::class.java)
            .create("sms").get()
        // Registration probe — see FailableSmsProvider's docs.
        context.contentResolver.insert(
            android.provider.Telephony.Sms.Inbox.CONTENT_URI,
            ContentValues().apply { put("body", "probe") },
        )
        assertTrue("Fake sms provider not wired", provider.rows.isNotEmpty())
        provider.rows.clear()
        repo = MessageRepository.get(context)
    }

    @Test
    fun `two- and three-recipient sends map every provider row`() = runTest {
        val pair = repo.storeOutgoing("+15550301;+15550302", "group hello", 1_000)
        val pairRows = repo.db.providerRows().forMessage(pair.id)
        assertEquals(2, pairRows.size)
        assertEquals(
            setOf("+15550301", "+15550302"),
            pairRows.map { it.recipient }.toSet(),
        )
        assertTrue(pairRows.all { it.kind == "SMS" && !it.deleteFailed })
        // The message column still holds only the first id — which is exactly
        // why the mapping table has to exist.
        assertNotNull(pair.smsId)

        val trio = repo.storeOutgoing("+15550311;+15550312;+15550313", "trio hello", 2_000)
        val trioRows = repo.db.providerRows().forMessage(trio.id)
        assertEquals(3, trioRows.size)
        assertEquals(
            setOf("+15550311", "+15550312", "+15550313"),
            trioRows.map { it.recipient }.toSet(),
        )
        // Every mapped URI is distinct and really exists in the provider.
        assertEquals(3, trioRows.map { it.uri }.toSet().size)
        trioRows.forEach {
            assertTrue(provider.rows.containsKey(it.uri.substringAfterLast('/').toLong()))
        }
    }

    @Test
    fun `trash deletes every recipient's provider row, not just the first`() = runTest {
        val msg = repo.storeOutgoing("+15550321;+15550322;+15550323", "group hello", 1_000)
        val ids = repo.db.providerRows().forMessage(msg.id)
            .map { it.uri.substringAfterLast('/').toLong() }
        assertEquals(3, ids.size)
        assertTrue(ids.all { provider.rows.containsKey(it) })

        repo.moveToTrash(msg.id)

        // The pre-fix behaviour left ids[1] and ids[2] behind forever.
        assertTrue(
            "Orphaned provider rows: ${ids.filter { provider.rows.containsKey(it) }}",
            ids.none { provider.rows.containsKey(it) },
        )
        assertTrue(repo.db.providerRows().forMessage(msg.id).isEmpty())
    }

    @Test
    fun `a failed provider deletion is retained and retried`() = runTest {
        val msg = repo.storeOutgoing("+15550331;+15550332", "group hello", 1_000)
        val ids = repo.db.providerRows().forMessage(msg.id)
            .map { it.uri.substringAfterLast('/').toLong() }

        provider.deletesFail = true
        repo.moveToTrash(msg.id)

        // Nothing was discarded: both rows are still mapped, both flagged.
        val flagged = repo.db.providerRows().forMessage(msg.id)
        assertEquals(2, flagged.size)
        assertTrue(flagged.all { it.deleteFailed })
        assertEquals(2, repo.db.providerRows().pendingDeletions(100).size)
        assertTrue(ids.all { provider.rows.containsKey(it) })

        // Role regained → the sweep finally frees them.
        provider.deletesFail = false
        assertEquals(2, repo.retryFailedProviderDeletions())
        assertTrue(ids.none { provider.rows.containsKey(it) })
        assertTrue(repo.db.providerRows().pendingDeletions(100).isEmpty())
        assertTrue(repo.db.providerRows().forMessage(msg.id).isEmpty())
    }

    @Test
    fun `undo after a successful delete restores one row per recipient, no duplicates`() = runTest {
        val msg = repo.storeOutgoing("+15550341;+15550342", "group hello", 1_000)
        repo.moveToTrash(msg.id)
        assertTrue(provider.rows.isEmpty())

        repo.restoreFromTrash(msg.id)

        val restored = repo.db.providerRows().forMessage(msg.id)
        assertEquals(2, restored.size)
        assertEquals(
            setOf("+15550341", "+15550342"),
            restored.map { it.recipient }.toSet(),
        )
        assertEquals(2, restored.map { it.uri }.toSet().size)
        assertEquals(2, provider.rows.size)
        assertFalse(repo.db.messages().byId(msg.id)!!.trashed)
    }

    @Test
    fun `undo after a failed delete re-adopts the surviving rows instead of duplicating them`() =
        runTest {
            val msg = repo.storeOutgoing("+15550351;+15550352", "group hello", 1_000)
            val originalUris = repo.db.providerRows().forMessage(msg.id).map { it.uri }.toSet()

            provider.deletesFail = true
            repo.moveToTrash(msg.id) // rows survive, mapping flagged for retry
            provider.deletesFail = false

            repo.restoreFromTrash(msg.id)

            // Two recipients, two provider rows — the SAME two. Writing fresh
            // rows here would have left each recipient duplicated in every
            // other SMS app, and stranded the flagged entries in the sweep.
            val mapped = repo.db.providerRows().forMessage(msg.id)
            assertEquals(2, mapped.size)
            assertEquals(originalUris, mapped.map { it.uri }.toSet())
            assertEquals(2, provider.rows.size)
            // The restored message is live again, so nothing is queued for
            // deletion any more.
            assertTrue(mapped.none { it.deleteFailed })
            assertTrue(repo.db.providerRows().pendingDeletions(100).isEmpty())
        }

    @Test
    fun `conversation summary follows the latest live message, not the last write`() = runTest {
        val friend = "+15550361"
        val (first, _) = repo.onIncomingSms(friend, "older", 5_000)
        repo.onIncomingSms(friend, "newest", 9_000)
        // R-06: a late-arriving OLD message must not roll the preview backward.
        repo.onIncomingSms(friend, "late but old", 1_000)

        val conv = repo.db.conversations().byThreadId(first.threadId)
        assertNotNull(conv)
        assertEquals("newest", conv!!.lastMessage)
        assertEquals(9_000L, conv.lastTimestamp)

        // Deleting the newest hands the preview back to the next-latest LIVE
        // message — not to whatever was written most recently.
        val newest = repo.db.messages().listForThread(first.threadId)
            .first { it.body == "newest" }
        repo.moveToTrash(newest.id)
        val after = repo.db.conversations().byThreadId(first.threadId)
        assertEquals("older", after!!.lastMessage)
        assertEquals(5_000L, after.lastTimestamp)
    }

    @Test
    fun `a duplicate SMS delivery is not indexed twice`() = runTest {
        val friend = "+15550371"
        val (first, _) = repo.onIncomingSms(friend, "only once", 4_000)
        assertNotNull(first.smsId)
        // Same provider row redelivered (the platform can repeat a broadcast).
        val second = repo.onIncomingSms(friend, "only once", 4_000)
        assertFalse("duplicate was indexed as new", second.isNew)
        assertEquals(
            1,
            repo.db.messages().listForThread(first.threadId).count { it.body == "only once" },
        )
    }
}
