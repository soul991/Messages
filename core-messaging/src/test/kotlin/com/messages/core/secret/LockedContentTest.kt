package com.messages.core.secret

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.messages.core.db.ConversationEntity
import com.messages.core.db.MessageEntity
import com.messages.core.db.Spaces
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * V2-6 unit-level contract for [LockedContent].
 *
 * The integration behaviour (what the repository writes, what the provider
 * keeps) is pinned in `LockedAtRestTest`; this file pins the primitive those
 * assertions rest on, including the two failure modes that would be silent:
 * a value moved between columns, and a missing Keystore.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LockedContentTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        TestKeyBox.install()
        LockedContent.destroyKey(context)
    }

    @After
    fun tearDown() {
        LockedContent.destroyKey(context)
        TestKeyBox.uninstall()
        LockedContent.resetCacheForTests()
    }

    private fun locked(body: String) = MessageEntity(
        threadId = 1, address = "+911111100001", body = body,
        normalizedBody = body.lowercase(), timestamp = 1_000,
        isOutgoing = false, space = Spaces.LOCKED,
    )

    @Test
    fun `sealing a locked message hides the text and opening restores it exactly`() {
        val plain = "meet me at 7, bring the passport 🔑"
        val sealed = LockedContent.seal(context, locked(plain))

        assertNotEquals("body must not be stored in the clear", plain, sealed.body)
        assertTrue(LockedContent.isSealed(sealed.body))
        assertTrue(LockedContent.isSealed(sealed.normalizedBody))
        assertFalse(
            "no substring of the plaintext may survive in the stored value",
            sealed.body.contains("passport"),
        )
        assertEquals(plain, LockedContent.open(context, sealed).body)
        assertEquals(plain.lowercase(), LockedContent.open(context, sealed).normalizedBody)
    }

    @Test
    fun `sealing is randomised, so identical messages are not identical rows`() {
        val a = LockedContent.seal(context, locked("same text"))
        val b = LockedContent.seal(context, locked("same text"))
        assertNotEquals(
            "a deterministic seal would leak equality between locked messages",
            a.body, b.body,
        )
    }

    @Test
    fun `a normal-space row is never touched`() {
        val normal = locked("nothing secret").copy(space = Spaces.NORMAL)
        assertEquals(normal, LockedContent.seal(context, normal))
    }

    @Test
    fun `open is a no-op on plaintext, so every read path can call it blindly`() {
        val plain = locked("just a message")
        assertEquals(plain, LockedContent.open(context, plain))
        assertEquals("hello", LockedContent.openText(context, "body", "hello"))
        assertEquals("", LockedContent.openText(context, "body", ""))
    }

    @Test
    fun `sealing twice does not double-seal`() {
        val once = LockedContent.seal(context, locked("payload"))
        val twice = LockedContent.seal(context, once)
        assertEquals(once.body, twice.body)
        assertEquals("payload", LockedContent.open(context, twice).body)
    }

    @Test
    fun `a ciphertext moved to another column will not open there`() {
        // AAD binding. Without it, anyone with database access could copy a
        // body ciphertext into the conversation preview column and read the
        // start of a locked message from the conversation list.
        val bodyCipher = LockedContent.sealText(context, "body", "the actual secret")
        val asPreview = ConversationEntity(
            threadId = 1, address = "+911111100001",
            lastMessage = bodyCipher, lastTimestamp = 1_000, space = Spaces.LOCKED,
        )
        assertEquals(
            "a body ciphertext must stay opaque in the preview column",
            bodyCipher, LockedContent.open(context, asPreview).lastMessage,
        )
    }

    @Test
    fun `a truncated or corrupted value is returned unchanged, never thrown on`() {
        val sealed = LockedContent.sealText(context, "body", "important")
        val truncated = sealed.substring(0, LockedContent.MARKER.length + 4)
        assertEquals(truncated, LockedContent.openText(context, "body", truncated))
        val garbage = LockedContent.MARKER + "!!!not base64!!!"
        assertEquals(garbage, LockedContent.openText(context, "body", garbage))
        // Bit-flip the tail: GCM must reject it rather than return plaintext.
        val flipped = sealed.dropLast(2) + if (sealed.endsWith("A=")) "B=" else "A="
        assertEquals(flipped, LockedContent.openText(context, "body", flipped))
    }

    @Test
    fun `with no keystore the message is stored sealed at pending grade, and said so`() {
        // V2-6b: fail-closed. The old contract stored the row in the CLEAR here
        // ("degrade to the pre-V2-6 behaviour"); the pending grade removes the
        // dilemma that argument rested on — the arriving mail is still stored
        // and still readable, but what reaches the database is ciphertext under
        // the fallback key, upgraded to the real seal by the backlog once the
        // Keystore returns. Plaintext-at-rest is no longer a reachable outcome.
        val box = TestKeyBox.install()
        LockedContent.destroyKey(context)
        box.failing = true
        // Inverse vacuity guard: with a healthy box everything real-seals and
        // this test would pass without pinning the failure path at all.
        assertFalse(
            "keystore must be failing for this pin to mean anything",
            LockedContent.available(context),
        )
        val row = LockedContent.seal(context, locked("arrived anyway"))
        assertNotEquals("never plaintext, even with no keystore", "arrived anyway", row.body)
        assertTrue(LockedContent.isSealed(row.body))
        assertTrue(LockedContent.isPending(row.body))
        assertTrue(LockedContent.isPending(row.normalizedBody))
        assertFalse(
            "no substring of the plaintext may survive in the stored value",
            row.body.contains("arrived"),
        )
        assertEquals(
            "the space must stay readable meanwhile",
            "arrived anyway", LockedContent.open(context, row).body,
        )
        assertFalse(
            "pending grade must not report availability",
            LockedContent.available(context),
        )
    }

    @Test
    fun `a pending row upgrades to the real seal once the keystore returns`() {
        val box = TestKeyBox.install()
        LockedContent.destroyKey(context)
        box.failing = true
        val pendingRow = LockedContent.seal(context, locked("waited it out"))
        assertTrue(LockedContent.isPending(pendingRow.body))

        box.failing = false
        LockedContent.resetCacheForTests()
        assertTrue(LockedContent.available(context))

        // The repository's `.sealed()` composition: open, then seal — exactly
        // what the backlog repair runs over every non-canonical row.
        val upgraded = LockedContent.seal(context, LockedContent.open(context, pendingRow))
        assertTrue(LockedContent.isSealed(upgraded.body))
        assertFalse(LockedContent.isPending(upgraded.body))
        assertTrue(LockedContent.isCanonical(upgraded))
        assertEquals("waited it out", LockedContent.open(context, upgraded).body)
    }

    @Test
    fun `a cipher failure with a live key degrades to pending, never plaintext`() {
        // The other half of the old fail-open: the catch around the cipher.
        // The key itself is obtainable here — the guard proves it, so this pin
        // is about cipher failure, not key absence.
        assertTrue(
            "key must be live for this pin to mean anything",
            LockedContent.available(context),
        )
        LockedContent.failRealCipherForTests = true
        try {
            val row = LockedContent.seal(context, locked("cipher hiccup"))
            assertNotEquals("cipher hiccup", row.body)
            assertTrue(LockedContent.isPending(row.body))
            assertEquals("cipher hiccup", LockedContent.open(context, row).body)
        } finally {
            LockedContent.failRealCipherForTests = false
        }
    }

    @Test
    fun `when even the fallback cannot seal, sealing throws instead of leaking`() {
        val box = TestKeyBox.install()
        LockedContent.destroyKey(context)
        box.failing = true
        LockedContent.failPendingSealForTests = true
        try {
            LockedContent.sealText(context, "body", "must not leak")
            fail("sealText must throw rather than return plaintext")
        } catch (_: LockedSealException) {
            // Expected: the typed failure the intake path answers with a
            // tombstone and every user-initiated path surfaces.
        } finally {
            LockedContent.failPendingSealForTests = false
        }
    }

    @Test
    fun `a pending ciphertext moved to another column will not open there`() {
        // AAD binding holds at the pending grade too.
        val box = TestKeyBox.install()
        LockedContent.destroyKey(context)
        box.failing = true
        val bodyCipher = LockedContent.sealText(context, "body", "the actual secret")
        assertTrue(LockedContent.isPending(bodyCipher))
        val asPreview = ConversationEntity(
            threadId = 1, address = "+911111100001",
            lastMessage = bodyCipher, lastTimestamp = 1_000, space = Spaces.LOCKED,
        )
        assertEquals(
            "a pending body ciphertext must stay opaque in the preview column",
            bodyCipher, LockedContent.open(context, asPreview).lastMessage,
        )
    }

    @Test
    fun `truncated or corrupted pending values are returned unchanged, never thrown on`() {
        val box = TestKeyBox.install()
        LockedContent.destroyKey(context)
        box.failing = true
        val sealed = LockedContent.sealText(context, "body", "important")
        val truncated = sealed.substring(0, LockedContent.PENDING_MARKER.length + 4)
        assertEquals(truncated, LockedContent.openText(context, "body", truncated))
        val garbage = LockedContent.PENDING_MARKER + "!!!not base64!!!"
        assertEquals(garbage, LockedContent.openText(context, "body", garbage))
        val flipped = sealed.dropLast(2) + if (sealed.endsWith("A=")) "B=" else "A="
        assertEquals(flipped, LockedContent.openText(context, "body", flipped))
    }

    @Test
    fun `pending rows are not canonical, so the backlog repair will revisit them`() {
        val box = TestKeyBox.install()
        LockedContent.destroyKey(context)
        box.failing = true
        val pendingRow = LockedContent.seal(context, locked("upgrade me"))
        assertTrue(LockedContent.isPending(pendingRow.body))
        assertFalse(
            "pending must not be canonical in the locked space",
            LockedContent.isCanonical(pendingRow),
        )
        assertFalse(
            "pending must not be canonical in the normal space either",
            LockedContent.isCanonical(pendingRow.copy(space = Spaces.NORMAL)),
        )
    }

    @Test
    fun `losing the key leaves old rows opaque rather than minting a new one`() {
        val sealed = LockedContent.seal(context, locked("older message"))
        // Keystore wiped underneath us: the wrapped key no longer unwraps.
        LockedContent.resetCacheForTests()
        val box = TestKeyBox.install()
        box.failingDecrypt = true
        // The wrapped key is still on disk but no longer unwraps, so the row
        // must come back as-is — NOT be re-sealed under a freshly minted key,
        // which would make every existing locked message unrecoverable for
        // good the moment the Keystore hiccuped once.
        val reopened = LockedContent.open(context, sealed)
        assertTrue(LockedContent.isSealed(reopened.body))
    }
}
