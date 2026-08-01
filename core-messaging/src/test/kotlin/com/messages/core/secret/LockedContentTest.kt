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
    fun `with no keystore the message is still stored, in the clear, and said so`() {
        // Degrading to the pre-V2-6 behaviour is correct; refusing to store an
        // arriving SMS because the Keystore is unhappy is not.
        val box = TestKeyBox.install()
        LockedContent.destroyKey(context)
        box.failing = true
        assertFalse(LockedContent.available(context))
        val row = LockedContent.seal(context, locked("arrived anyway"))
        assertEquals("arrived anyway", row.body)
        assertFalse(LockedContent.isSealed(row.body))
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
