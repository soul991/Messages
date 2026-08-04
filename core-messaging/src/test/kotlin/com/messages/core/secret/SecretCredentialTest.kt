package com.messages.core.secret

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Secret-space credential crypto + rate-limit policy (pure JVM).
 * Fast tests use a reduced iteration count — the production constant is
 * asserted separately (≥600k, the spec floor).
 */
class SecretCredentialTest {

    private val fastIters = 1_000

    @Test
    fun `production iteration count meets the 600k floor`() {
        assertTrue(SecretCrypto.ITERATIONS >= 600_000)
    }

    @Test
    fun `derive is deterministic per salt and diverges across salts`() {
        val cred = "4711".toCharArray()
        val saltA = SecretCrypto.newSalt()
        val saltB = SecretCrypto.newSalt()
        val a1 = SecretCrypto.derive(cred, saltA, fastIters)
        val a2 = SecretCrypto.derive(cred, saltA, fastIters)
        val b = SecretCrypto.derive(cred, saltB, fastIters)
        assertTrue(a1.contentEquals(a2))
        assertFalse(a1.contentEquals(b))
        assertEquals(32, a1.size) // 256-bit
    }

    @Test
    fun `verify accepts the right credential and rejects a wrong one`() {
        val salt = SecretCrypto.newSalt()
        val verifier = SecretCrypto.derive("secret99".toCharArray(), salt, fastIters)
        assertTrue(SecretCrypto.verify("secret99".toCharArray(), salt, verifier, fastIters))
        assertFalse(SecretCrypto.verify("secret98".toCharArray(), salt, verifier, fastIters))
        assertFalse(SecretCrypto.verify("SECRET99".toCharArray(), salt, verifier, fastIters))
    }

    @Test
    fun `verifier and KEK derivations differ when salts differ`() {
        val cred = "1234".toCharArray()
        val verifier = SecretCrypto.derive(cred, SecretCrypto.newSalt(), fastIters)
        val kek = SecretCrypto.derive(cred, SecretCrypto.newSalt(), fastIters)
        assertFalse(verifier.contentEquals(kek))
    }

    @Test
    fun `pattern normalization is order-sensitive and validated`() {
        val a = SecretCrypto.patternToCredential(listOf(0, 1, 2, 5))
        val b = SecretCrypto.patternToCredential(listOf(5, 2, 1, 0))
        assertNotEquals(String(a), String(b))
        // Too short / repeated / out-of-range all rejected.
        assertThrows { SecretCrypto.patternToCredential(listOf(0, 1, 2)) }
        assertThrows { SecretCrypto.patternToCredential(listOf(0, 1, 1, 2)) }
        assertThrows { SecretCrypto.patternToCredential(listOf(0, 1, 2, 9)) }
    }

    @Test
    fun `setup strength floors`() {
        // V2-7: the PIN floor is 6 digits, the password floor 8 characters.
        assertNull(SecretCrypto.setupError(SecretCrypto.KIND_PIN, "836194".toCharArray()))
        assertEquals(
            SecretCrypto.SetupError.PIN_TOO_SHORT,
            SecretCrypto.setupError(SecretCrypto.KIND_PIN, "8361".toCharArray()),
        )
        assertEquals(
            SecretCrypto.SetupError.PIN_NOT_DIGITS,
            SecretCrypto.setupError(SecretCrypto.KIND_PIN, "83a194".toCharArray()),
        )
        assertNull(SecretCrypto.setupError(SecretCrypto.KIND_PASSWORD, "wrenGravel7".toCharArray()))
        assertEquals(
            SecretCrypto.SetupError.PASSWORD_TOO_SHORT,
            SecretCrypto.setupError(SecretCrypto.KIND_PASSWORD, "wren7".toCharArray()),
        )
    }

    @Test
    fun `setup rejects blocklisted credentials at full length`() {
        // Long enough to clear the floor, still refused — this is the point of
        // V2-7: length alone is not what makes a credential unguessable.
        for (pin in listOf("123456", "111111", "121212", "112233", "010203", "147258")) {
            assertEquals(
                "expected $pin to be refused",
                SecretCrypto.SetupError.PIN_TOO_WEAK,
                SecretCrypto.setupError(SecretCrypto.KIND_PIN, pin.toCharArray()),
            )
        }
        assertEquals(
            SecretCrypto.SetupError.PASSWORD_TOO_WEAK,
            SecretCrypto.setupError(SecretCrypto.KIND_PASSWORD, "Password1".toCharArray()),
        )
    }

    @Test
    fun `setup pattern floor is five dots and refuses straight lines`() {
        val fiveDots = SecretCrypto.patternToCredential(listOf(0, 1, 4, 7, 5))
        assertNull(SecretCrypto.setupError(SecretCrypto.KIND_PATTERN, fiveDots))

        val fourDots = SecretCrypto.patternToCredential(listOf(0, 1, 4, 7))
        assertEquals(
            SecretCrypto.SetupError.PATTERN_TOO_SHORT,
            SecretCrypto.setupError(SecretCrypto.KIND_PATTERN, fourDots),
        )
        // A four-dot pattern is still NORMALIZABLE — an unlock has to keep
        // accepting one created under the old floor. Only setup refuses it.
        assertEquals("0-1-4-7", String(fourDots))
    }

    @Test
    fun `simple pattern shapes are refused at any length`() {
        // An L: top row, then down the right column. Two strokes.
        assertTrue(SecretStrength.isWeakPattern(listOf(0, 1, 2, 5, 8)))
        // A V: down the left column, then diagonally back up. Two strokes.
        assertTrue(SecretStrength.isWeakPattern(listOf(0, 3, 6, 4, 2)))
        // The exhaustive sweeps.
        assertTrue(SecretStrength.isWeakPattern(listOf(0, 1, 2, 5, 4, 3, 6, 7, 8)))
        assertTrue(SecretStrength.isWeakPattern(listOf(8, 7, 6, 3, 4, 5, 2, 1, 0)))
        // The perimeter ring, and the same ring entered at another corner.
        assertTrue(SecretStrength.isWeakPattern(listOf(0, 1, 2, 5, 8, 7, 6, 3)))
        assertTrue(SecretStrength.isWeakPattern(listOf(8, 7, 6, 3, 0, 1, 2, 5)))
        // Three or more direction changes is a real pattern, not a shape.
        assertFalse(SecretStrength.isWeakPattern(listOf(0, 4, 1, 7, 5)))
        assertFalse(SecretStrength.isWeakPattern(listOf(3, 1, 5, 6, 2, 7)))
        // And the same verdicts through the setup entry point.
        assertEquals(
            SecretCrypto.SetupError.PATTERN_TOO_WEAK,
            SecretCrypto.setupError(
                SecretCrypto.KIND_PATTERN,
                SecretCrypto.patternToCredential(listOf(0, 1, 2, 5, 8)),
            ),
        )
        assertNull(
            SecretCrypto.setupError(
                SecretCrypto.KIND_PATTERN,
                SecretCrypto.patternToCredential(listOf(0, 4, 1, 7, 5)),
            ),
        )
    }

    @Test
    fun `strength rules do not refuse genuinely unpredictable credentials`() {
        // Guard against an over-eager blocklist: these must all pass, or the
        // policy starts rejecting good choices and users route around it.
        for (pin in listOf("836194", "405271", "628037", "915482", "70394186")) {
            assertNull(
                "expected $pin to be accepted",
                SecretCrypto.setupError(SecretCrypto.KIND_PIN, pin.toCharArray()),
            )
        }
        for (pw in listOf("wrenGravel7", "th1cket-lantern", "Qz8#marmot", "correct horse battery")) {
            assertNull(
                "expected $pw to be accepted",
                SecretCrypto.setupError(SecretCrypto.KIND_PASSWORD, pw.toCharArray()),
            )
        }
    }

    @Test
    fun `date-shaped PINs are refused because they are knowable not guessable`() {
        for (pin in listOf("120589", "310178", "199504", "062291")) {
            assertEquals(
                "expected $pin to be refused",
                SecretCrypto.SetupError.PIN_TOO_WEAK,
                SecretCrypto.setupError(SecretCrypto.KIND_PIN, pin.toCharArray()),
            )
        }
    }

    @Test
    fun `an all-digit password answers to the PIN rules`() {
        // Otherwise "123456789" escapes the blocklist by being typed into the
        // password field instead of the PIN field.
        assertEquals(
            SecretCrypto.SetupError.PASSWORD_TOO_WEAK,
            SecretCrypto.setupError(SecretCrypto.KIND_PASSWORD, "123456789".toCharArray()),
        )
    }

    // ---- Cooldown policy ----

    @Test
    fun `first five failures are free`() {
        for (fails in 0..4) {
            assertEquals(0L, SecretCooldown.cooldownMs(fails))
            assertEquals(0L, SecretCooldown.remainingMs(fails, lastFailAt = 1_000L, now = 1_001L))
        }
    }

    @Test
    fun `cooldown escalates from the fifth failure and caps at an hour`() {
        assertEquals(30_000L, SecretCooldown.cooldownMs(5))
        assertEquals(60_000L, SecretCooldown.cooldownMs(6))
        assertEquals(5 * 60_000L, SecretCooldown.cooldownMs(7))
        assertEquals(15 * 60_000L, SecretCooldown.cooldownMs(8))
        assertEquals(60 * 60_000L, SecretCooldown.cooldownMs(9))
        assertEquals(60 * 60_000L, SecretCooldown.cooldownMs(50))
    }

    @Test
    fun `remaining counts down from the last failure and floors at zero`() {
        val lastFail = 100_000L
        assertEquals(30_000L, SecretCooldown.remainingMs(5, lastFail, now = lastFail))
        assertEquals(10_000L, SecretCooldown.remainingMs(5, lastFail, now = lastFail + 20_000))
        assertEquals(0L, SecretCooldown.remainingMs(5, lastFail, now = lastFail + 30_000))
        assertEquals(0L, SecretCooldown.remainingMs(5, lastFail, now = lastFail + 999_999))
    }

    private inline fun assertThrows(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected an exception")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
