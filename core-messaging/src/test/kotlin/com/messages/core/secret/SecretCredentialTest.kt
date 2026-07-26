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
        assertNull(SecretCrypto.setupError(SecretCrypto.KIND_PIN, "1234".toCharArray()))
        assertNotNull(SecretCrypto.setupError(SecretCrypto.KIND_PIN, "123".toCharArray()))
        assertNotNull(SecretCrypto.setupError(SecretCrypto.KIND_PIN, "12a4".toCharArray()))
        assertNull(SecretCrypto.setupError(SecretCrypto.KIND_PASSWORD, "abcd".toCharArray()))
        assertNotNull(SecretCrypto.setupError(SecretCrypto.KIND_PASSWORD, "abc".toCharArray()))
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

    private fun assertNotNull(v: Any?) = assertTrue(v != null)

    private inline fun assertThrows(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected an exception")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }
}
