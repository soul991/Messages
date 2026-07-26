package com.messages.core.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.messages.core.MessageRepository
import com.messages.core.db.Spaces
import com.messages.core.secret.SecretCrypto
import com.messages.core.secret.SecretSpace
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Locked-chats backup sub-envelope, end-to-end:
 *  - export separates locked content into an encrypted envelope (plaintext
 *    backup JSON must not contain locked bodies/addresses),
 *  - restore on a "fresh device" (wiped prefs) leaves the envelope pending,
 *  - the wrong credential does not open it (cooldown-free early attempts),
 *  - the right credential unlocks + places chats in the LOCKED space,
 *  - re-import is idempotent and dedupe spans both spaces.
 *
 * Single test method: MessageRepository is a process singleton (see
 * LockedRoutingTest for the rationale).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LockedBackupTest {

    @Test
    fun `locked chats travel encrypted and restore behind the credential`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = MessageRepository.get(context)
        val db = repo.db
        val secretBody = "meet me at the usual place zebra"
        val secretAddress = "+919999000011"

        // Device state: secret space set up, one locked chat + one normal chat.
        SecretSpace.setUp(context, SecretCrypto.KIND_PIN, "2468".toCharArray())
        val (normalMsg, _) = repo.onIncomingSms("+911111000022", "normal hello", 1_000)
        val (lockedSeed, _) = repo.onIncomingSms(secretAddress, "seed", 2_000)
        repo.createLockedConversation(lockedSeed.threadId)
        repo.moveThreadToSpace(lockedSeed.threadId, Spaces.NORMAL, Spaces.LOCKED)
        val (routed, _) = repo.onIncomingSms(secretAddress, secretBody, 3_000)
        assertEquals(Spaces.LOCKED, routed.space)

        // ---- Export ----
        val exported = BackupManager.export(context)
        // Plaintext payload leaks nothing from the locked space…
        assertFalse(exported.contains(secretBody))
        assertFalse(exported.contains("seed"))
        // …but carries the envelope + travelling auth.
        val parsed = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(BackupManager.BackupFile.serializer(), exported)
        assertNotNull(parsed.lockedEnvelope)
        assertNotNull(parsed.lockedAuth)
        assertTrue(exported.contains("normal hello")) // normal path unaffected

        // The envelope itself only opens with the right credential.
        val blob = java.util.Base64.getDecoder().decode(parsed.lockedEnvelope!!)
        try {
            BackupCrypto.openWithPassword(blob, "9999".toCharArray())
            throw AssertionError("Wrong credential must not open the locked envelope")
        } catch (_: BackupCrypto.WrongPasswordException) {
            // expected
        }
        val openedDirect = BackupCrypto.openWithPassword(blob, "2468".toCharArray())
        assertTrue(openedDirect.contains(secretBody))

        // ---- Simulate a fresh install: wipe index + secret-space state ----
        db.openHelper.writableDatabase.execSQL("DELETE FROM messages")
        db.openHelper.writableDatabase.execSQL("DELETE FROM conversations")
        SecretSpace.prefs(context).edit().clear().commit()
        SecretSpace.pendingBlobFile(context).delete()

        // ---- Restore ----
        val stats = BackupManager.import(context, exported).getOrThrow()
        assertTrue(stats.messagesRestored > 0)
        assertEquals(0, stats.lockedRestored) // no credential yet
        assertTrue(stats.lockedPending)      // opaque "locked chats present" state
        assertTrue(SecretSpace.hasPendingRestore(context))
        // Nothing locked is visible anywhere in the normal space.
        assertTrue(db.messages().search("zebra").isEmpty())
        assertTrue(db.messages().allMessages().none { it.space == Spaces.LOCKED })

        // Wrong credential at the prompt: rejected, envelope stays pending.
        val wrong = SecretSpace.attempt(context, "1357".toCharArray())
        assertTrue(wrong is SecretSpace.Attempt.Wrong)
        assertTrue(SecretSpace.hasPendingRestore(context))

        // Right credential: verified against the carried auth, KEK derived,
        // envelope imported into the LOCKED space.
        val right = SecretSpace.attempt(context, "2468".toCharArray())
        assertTrue(right is SecretSpace.Attempt.Success)
        val restoredCount = BackupManager.completeLockedRestore(context)
        assertNotNull(restoredCount)
        assertTrue(restoredCount!! > 0)
        assertFalse(SecretSpace.hasPendingRestore(context))

        val lockedRows = db.messages().allMessages().filter { it.space == Spaces.LOCKED }
        assertTrue(lockedRows.any { it.body == secretBody })
        // Locked conversation recreated (routing rule restored) and invisible
        // to the normal list.
        val lockedThread = lockedRows.first().threadId
        assertNotNull(db.conversations().byThreadId(lockedThread, Spaces.LOCKED))
        assertTrue(db.messages().search("zebra").isEmpty())

        // ---- Idempotency + cross-space dedupe ----
        val counts = db.messages().allMessages().size
        val second = BackupManager.import(context, exported).getOrThrow()
        assertEquals(0, second.messagesRestored)
        // The locked envelope opens immediately now (same credential, cached
        // KEK) and its rows all dedupe against the already-restored copies.
        assertEquals(0, second.lockedRestored)
        assertFalse(second.lockedPending)
        assertEquals(counts, db.messages().allMessages().size)
    }
}
