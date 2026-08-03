package com.messages.core

import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.messages.core.backup.BackupManager
import com.messages.core.db.Spaces
import com.messages.core.secret.SecretCrypto
import com.messages.core.secret.SecretSpace
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Minimal in-memory stand-in for the system SMS provider so the test can
 * assert that Reset actually destroys the Telephony rows (Robolectric has no
 * real provider — inserts would silently return null otherwise).
 */
class FakeSmsProvider : ContentProvider() {
    val rows = LinkedHashMap<Long, ContentValues>()
    private var nextId = 1L

    override fun onCreate() = true

    override fun insert(uri: Uri, values: ContentValues?): Uri {
        val id = nextId++
        rows[id] = ContentValues(values)
        return Uri.parse("content://sms/$id")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        val id = uri.lastPathSegment?.toLongOrNull() ?: return 0
        return if (rows.remove(id) != null) 1 else 0
    }

    override fun query(
        uri: Uri, projection: Array<out String>?, selection: String?,
        selectionArgs: Array<out String>?, sortOrder: String?,
    ): Cursor {
        // V2-6b: honor a body projection against a specific row so the
        // tombstone-recovery path (readProviderBody) is exercisable. The
        // legacy _id-only shape is preserved for every other caller —
        // providerRowExists depends on it.
        val id = uri.lastPathSegment?.toLongOrNull()
        if (id != null && projection?.contains("body") == true) {
            return MatrixCursor(arrayOf("body")).apply {
                rows[id]?.let { addRow(arrayOf(it.getAsString("body"))) }
            }
        }
        return MatrixCursor(arrayOf("_id")).apply { rows.keys.forEach { addRow(arrayOf(it)) } }
    }

    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?) = 0
    override fun getType(uri: Uri): String? = null
}

/**
 * Reset = destruction without revelation:
 *  - every locked-space row dies (Room index, incl. trash, AND provider rows),
 *  - never via the normal Trash,
 *  - routing reverts (future incoming from the address goes NORMAL again),
 *  - credential/rate-limit state cleared, next backup has no locked envelope,
 *  - an OLD backup's envelope is undecryptable garbage that a restored app
 *    ignores gracefully (no crash, nothing placed, opaque pending at most).
 *
 * Single test method: MessageRepository is a process singleton.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LockedResetTest {

    @org.junit.Before
    fun freshSingleton() {
        MessageRepository.resetForTests()
        // V2-6: without a working key box the locked rows below would be
        // stored in the clear, and the encryption assertions would pass
        // vacuously for the wrong reason.
        com.messages.core.secret.TestKeyBox.install()
    }

    @org.junit.After
    fun restoreKeyBox() = com.messages.core.secret.TestKeyBox.uninstall()

    @Test
    fun `reset wipes locked rows and provider rows, reverts routing, old envelope stays sealed`() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val provider = Robolectric.buildContentProvider(FakeSmsProvider::class.java)
                .create("sms").get()
            // Registration probe: fail fast if the fake isn't actually wired
            // (Robolectric's shadow resolver silently fabricates insert Uris
            // for unregistered authorities, which would fake out every
            // provider-row assertion below).
            context.contentResolver.insert(
                android.provider.Telephony.Sms.Inbox.CONTENT_URI,
                ContentValues().apply { put("body", "probe") },
            )
            assertTrue("Fake sms provider not wired", provider.rows.isNotEmpty())
            val repo = MessageRepository.get(context)
            val db = repo.db
            val friend = "+919876511111" // unique per test class — see resetForTests docs

            // Establish the space + a locked chat with content (live + trashed).
            SecretSpace.setUp(context, SecretCrypto.KIND_PIN, "1122".toCharArray())
            val (seed, _) = repo.onIncomingSms(friend, "normal history", 1_000)
            repo.createLockedConversation(seed.threadId)
            val (routedA, _) = repo.onIncomingSms(friend, "locked alpha", 2_000)
            val (routedB, _) = repo.onIncomingSms(friend, "locked beta", 3_000)
            assertEquals(Spaces.LOCKED, routedA.space)
            repo.moveToTrash(routedB.id) // locked trash must die too, never resurface
            val lockedProviderIds = listOfNotNull(routedA.smsId, routedB.smsId)
            assertEquals(2, lockedProviderIds.size) // fake provider gave real ids
            // V2-6: this used to assert A's provider row still EXISTED here —
            // the locked space concealed a message inside this app while
            // leaving a readable copy in shared storage for any app with the
            // SMS role. Both copies must be gone the moment a message lands
            // locked, so there is nothing left for Reset to find.
            assertFalse(
                "a locked message must leave no Telephony copy behind",
                provider.rows.containsKey(routedA.smsId!!),
            )
            assertFalse(provider.rows.containsKey(routedB.smsId!!))
            // And the body it left in this app's own index is ciphertext.
            db.messages().allInSpace(Spaces.LOCKED).forEach {
                assertTrue(
                    "locked row stored in the clear: ${it.body}",
                    com.messages.core.secret.LockedContent.isSealed(it.body),
                )
            }

            // A backup taken BEFORE the reset (envelope sealed under "1122").
            val oldBackup = BackupManager.export(context)
            assertTrue(oldBackup.contains("lockedEnvelope"))

            // ---- RESET (exactly what the prompt's confirm dialog runs) ----
            repo.wipeLockedSpace()
            SecretSpace.clearAll(context)

            // 1) Locked rows gone from Room — trash included, and never via
            //    the normal Trash surface.
            assertTrue(db.messages().allInSpace(Spaces.LOCKED).isEmpty())
            assertTrue(db.messages().trashedMessages().first().none { it.body.startsWith("locked") })
            assertTrue(db.conversations().allConversations().none { it.space == Spaces.LOCKED })
            // Normal history untouched.
            assertTrue(db.messages().listForThread(seed.threadId).any { it.body == "normal history" })

            // 2) Provider rows for locked messages destroyed; the normal one lives.
            assertTrue(lockedProviderIds.none { provider.rows.containsKey(it) })
            assertTrue(provider.rows.containsKey(seed.smsId!!))

            // 3) Credential + rate-limit state forgotten.
            assertFalse(SecretSpace.isSetUp(context))
            assertFalse(SecretSpace.hasPendingRestore(context))
            assertNull(SecretSpace.kekOrNull(context))

            // 4) Routing reverted: new incoming from the address is NORMAL.
            val (afterReset, _) = repo.onIncomingSms(friend, "post-reset hello", 4_000)
            assertEquals(Spaces.NORMAL, afterReset.space)

            // 5) The next backup carries no locked envelope at all.
            val newBackup = BackupManager.export(context)
            val parsedNew = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString(BackupManager.BackupFile.serializer(), newBackup)
            assertNull(parsedNew.lockedEnvelope)
            assertNull(parsedNew.lockedAuth)

            // 6) Fresh start with a NEW code; restoring the OLD backup is
            //    handled gracefully: nothing placed, nothing revealed — the
            //    old envelope can't be opened by the new KEK and parks as an
            //    (undecryptable) pending state.
            SecretSpace.setUp(context, SecretCrypto.KIND_PIN, "9988".toCharArray())
            val stats = BackupManager.import(context, oldBackup).getOrThrow()
            assertEquals(0, stats.lockedRestored)
            assertTrue(db.messages().allInSpace(Spaces.LOCKED).isEmpty())
            assertTrue(db.messages().search("locked alpha").isEmpty())
            // Even after a "successful" new-code entry, completing the locked
            // restore returns null (sealed under the forgotten credential) and
            // the app carries on — no crash, no garbage rows.
            assertTrue(SecretSpace.attempt(context, "9988".toCharArray()) is SecretSpace.Attempt.Success)
            assertNull(BackupManager.completeLockedRestore(context))
            assertTrue(db.messages().allInSpace(Spaces.LOCKED).isEmpty())
        }
}
