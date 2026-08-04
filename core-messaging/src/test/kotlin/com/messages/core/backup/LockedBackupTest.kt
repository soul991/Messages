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

    @org.junit.Before
    fun freshSingleton() {
        MessageRepository.resetForTests()
        // R-18: production fails closed when the Keystore is unavailable, and
        // Robolectric has no real Keystore. Install the in-memory key box so
        // this test exercises the real KEK-cache path instead of relying on a
        // plaintext fallback that no longer exists.
        com.messages.core.secret.TestKeyBox.install()
    }

    @org.junit.After
    fun restoreKeyBox() = com.messages.core.secret.TestKeyBox.uninstall()

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
        // V2-6: restored locked rows are sealed at rest, so the stored column
        // must NOT equal the plaintext — and must open back to it. Asserting
        // both directions is the point: a seal that silently no-ops would
        // still satisfy the second assertion on its own.
        assertTrue(
            "a restored locked body must not be stored in the clear",
            lockedRows.none { it.body == secretBody },
        )
        assertTrue(
            lockedRows.any {
                com.messages.core.secret.LockedContent.open(context, it).body == secretBody
            },
        )
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

    /**
     * V2-6b: backup behaviour under a Keystore outage, both directions.
     *
     *  - RESTORE waits rather than degrades: the pending envelope survives a
     *    credential entry that happens while the content key is down, and
     *    completes after recovery — never a plaintext (or pending-grade) write.
     *  - EXPORT never bakes unopenable device-bound ciphertext into the
     *    envelope: a snapshot taken while locked rows cannot be opened ships
     *    without locked chats, exactly like the kek-less degradation branch.
     */
    @Test
    fun `locked restore waits and export degrades while the keystore is down`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repo = MessageRepository.get(context)
        val db = repo.db
        val secretBody = "the second safehouse, xylophone"
        val secretAddress = "+919999000033"

        // Healthy device: secret space + one locked chat, exported.
        SecretSpace.setUp(context, SecretCrypto.KIND_PIN, "2468".toCharArray())
        repo.onIncomingSms("+911111000044", "normal hello", 1_000)
        val (lockedSeed, _) = repo.onIncomingSms(secretAddress, "seed", 2_000)
        repo.createLockedConversation(lockedSeed.threadId)
        repo.moveThreadToSpace(lockedSeed.threadId, Spaces.NORMAL, Spaces.LOCKED)
        val (routed, _) = repo.onIncomingSms(secretAddress, secretBody, 3_000)
        assertEquals(Spaces.LOCKED, routed.space)
        val exported = BackupManager.export(context)

        // ---- Fresh install, then a restore that lands during an outage ----
        db.openHelper.writableDatabase.execSQL("DELETE FROM messages")
        db.openHelper.writableDatabase.execSQL("DELETE FROM conversations")
        SecretSpace.prefs(context).edit().clear().commit()
        SecretSpace.pendingBlobFile(context).delete()

        val box = com.messages.core.secret.TestKeyBox.install()
        box.failingDecrypt = true
        assertFalse(
            "outage must be in force",
            com.messages.core.secret.LockedContent.available(context),
        )

        val stats = BackupManager.import(context, exported).getOrThrow()
        assertTrue(stats.messagesRestored > 0) // normal chats restore regardless
        assertEquals(0, stats.lockedRestored)
        assertTrue(stats.lockedPending)
        assertTrue(SecretSpace.hasPendingRestore(context))

        // The right credential arrives while the key is still down: the
        // envelope must WAIT — not import at a degraded grade, not be cleared.
        val right = SecretSpace.attempt(context, "2468".toCharArray())
        assertTrue(right is SecretSpace.Attempt.Success)
        org.junit.Assert.assertNull(
            "restore must wait for the content key, not degrade",
            BackupManager.completeLockedRestore(context),
        )
        assertTrue("the pending envelope must survive", SecretSpace.hasPendingRestore(context))
        assertTrue(db.messages().allMessages().none { it.space == Spaces.LOCKED })

        // ---- Recovery: the same pending envelope now completes ----
        box.failingDecrypt = false
        com.messages.core.secret.LockedContent.resetCacheForTests()
        assertTrue(com.messages.core.secret.LockedContent.available(context))
        val again = SecretSpace.attempt(context, "2468".toCharArray())
        assertTrue(again is SecretSpace.Attempt.Success)
        val restoredCount = BackupManager.completeLockedRestore(context)
        assertNotNull(restoredCount)
        assertTrue(restoredCount!! > 0)
        assertFalse(SecretSpace.hasPendingRestore(context))
        val lockedRows = db.messages().allMessages().filter { it.space == Spaces.LOCKED }
        assertTrue(lockedRows.none { it.body == secretBody })
        assertTrue(
            lockedRows.any {
                com.messages.core.secret.LockedContent.open(context, it).body == secretBody
            },
        )

        // ---- Export half: unopenable locked rows must degrade the envelope ----
        // The asymmetric shape the belt-and-braces check exists for: the KEK
        // still unwraps, but the CONTENT key is gone (corrupt prefs entry), so
        // locked rows cannot be opened and must not ship as device-bound
        // ciphertext no other install could ever read.
        context.getSharedPreferences("locked_content", Context.MODE_PRIVATE)
            .edit().putString("content_key", "ks:!!corrupt!!").commit()
        com.messages.core.secret.LockedContent.resetCacheForTests()
        assertFalse(com.messages.core.secret.LockedContent.available(context))
        assertNotNull("the KEK half must still work for this pin to bite",
            SecretSpace.kekOrNull(context))

        val degraded = BackupManager.export(context)
        assertFalse(degraded.contains(secretBody))
        val parsedDegraded = kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
            .decodeFromString(BackupManager.BackupFile.serializer(), degraded)
        org.junit.Assert.assertNull(
            "an outage snapshot must ship without a locked envelope, never with unopenable ciphertext",
            parsedDegraded.lockedEnvelope,
        )
        assertTrue(degraded.contains("normal hello")) // normal path unaffected
    }
}
