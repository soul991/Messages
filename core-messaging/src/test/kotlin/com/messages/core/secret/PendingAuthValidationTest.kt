package com.messages.core.secret

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * R-19 regression: `lockedAuth` travels inside a restored backup, so every field
 * is attacker-controllable. Parsing must reject anything malformed BEFORE the
 * value reaches the PBKDF2 derivation boundary — an unbounded iteration count is
 * attacker-chosen CPU work executed on every unlock attempt, and bad Base64
 * would otherwise throw deep inside the crypto path.
 *
 * Pure JVM: PendingAuth.parse touches no Android APIs.
 */
class PendingAuthValidationTest {

    private fun b64(size: Int) = Base64.getEncoder().encodeToString(ByteArray(size))

    /** A well-formed value: 16-byte saltV, 32-byte verifier, 16-byte saltK. */
    private fun valid(
        iterations: Int = SecretCrypto.ITERATIONS,
        kind: String = SecretCrypto.KIND_PIN,
    ) = "${b64(16)}|${b64(32)}|${b64(16)}|$iterations|$kind"

    @Test
    fun `a well-formed value parses and round-trips`() {
        val parsed = SecretSpace.PendingAuth.parse(valid())
        assertNotNull(parsed)
        assertEquals(SecretCrypto.ITERATIONS, parsed!!.iterations)
        assertEquals(SecretCrypto.KIND_PIN, parsed.kind)
        assertEquals(valid(), parsed.serialize())
    }

    @Test
    fun `every supported credential kind is accepted`() {
        for (kind in listOf(
            SecretCrypto.KIND_PIN, SecretCrypto.KIND_PATTERN, SecretCrypto.KIND_PASSWORD,
        )) {
            assertNotNull(
                "kind $kind should parse",
                SecretSpace.PendingAuth.parse(valid(kind = kind)),
            )
        }
    }

    @Test
    fun `an absurd iteration count is refused instead of becoming CPU work`() {
        assertNull(SecretSpace.PendingAuth.parse(valid(iterations = Int.MAX_VALUE)))
        assertNull(SecretSpace.PendingAuth.parse(valid(iterations = 2_000_001)))
    }

    @Test
    fun `an iteration count below the security floor is refused`() {
        assertNull(SecretSpace.PendingAuth.parse(valid(iterations = 1)))
        assertNull(SecretSpace.PendingAuth.parse(valid(iterations = 99_999)))
        assertNull(SecretSpace.PendingAuth.parse(valid(iterations = 0)))
        assertNull(SecretSpace.PendingAuth.parse(valid(iterations = -1)))
    }

    @Test
    fun `the exact iteration bounds are inclusive`() {
        assertNotNull(SecretSpace.PendingAuth.parse(valid(iterations = 100_000)))
        assertNotNull(SecretSpace.PendingAuth.parse(valid(iterations = 2_000_000)))
    }

    @Test
    fun `an unknown credential kind is refused`() {
        assertNull(SecretSpace.PendingAuth.parse(valid(kind = "BIOMETRIC")))
        assertNull(SecretSpace.PendingAuth.parse(valid(kind = "")))
    }

    @Test
    fun `malformed base64 is refused rather than thrown at the crypto boundary`() {
        assertNull(SecretSpace.PendingAuth.parse("!!!!|${b64(32)}|${b64(16)}|600000|PIN"))
        assertNull(SecretSpace.PendingAuth.parse("${b64(16)}|@@@@|${b64(16)}|600000|PIN"))
    }

    @Test
    fun `wrong decoded field sizes are refused`() {
        // saltV must be 16 bytes, verifier 32, saltK 16.
        assertNull(SecretSpace.PendingAuth.parse("${b64(8)}|${b64(32)}|${b64(16)}|600000|PIN"))
        assertNull(SecretSpace.PendingAuth.parse("${b64(16)}|${b64(16)}|${b64(16)}|600000|PIN"))
        assertNull(SecretSpace.PendingAuth.parse("${b64(16)}|${b64(32)}|${b64(64)}|600000|PIN"))
    }

    @Test
    fun `wrong field counts are refused`() {
        assertNull(SecretSpace.PendingAuth.parse(""))
        assertNull(SecretSpace.PendingAuth.parse("${b64(16)}|${b64(32)}|${b64(16)}|600000"))
        assertNull(SecretSpace.PendingAuth.parse(valid() + "|extra"))
    }

    @Test
    fun `a non-numeric iteration field is refused`() {
        assertNull(SecretSpace.PendingAuth.parse("${b64(16)}|${b64(32)}|${b64(16)}|lots|PIN"))
    }

    @Test
    fun `an oversized value is refused before any field work`() {
        val huge = "${b64(16)}|${b64(32)}|${b64(16)}|600000|" + "P".repeat(4096)
        assertNull(SecretSpace.PendingAuth.parse(huge))
    }
}
