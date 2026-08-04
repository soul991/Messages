package com.messages.core.secret

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Secret locked-space credential crypto (pure JVM — no Android deps).
 *
 * The credential (PIN, pattern, or password — all normalized to a char
 * sequence) is NEVER stored. Two independent derivations, each with its own
 * random salt:
 *
 *  - VERIFIER: PBKDF2-HMAC-SHA256, 600k iterations → 256-bit hash, stored to
 *    check entries at the prompt. Deliberately slow; combined with the
 *    escalating cooldown ([SecretCooldown]) brute force is impractical.
 *  - KEK (key-encryption key): same primitive, separate salt → 256-bit AES
 *    key that wraps the backup sub-envelope's data key. Deriving it with a
 *    different salt means the stored verifier reveals nothing about the KEK.
 *
 * There is no recovery path by design: forgetting the credential means the
 *  locked space stays locked. No reset, no backdoor.
 */
object SecretCrypto {

    const val ITERATIONS = 600_000
    const val KEY_BITS = 256
    private const val SALT_LEN = 16

    /** Credential kinds — drives which input UI the prompt shows. */
    const val KIND_PIN = "PIN"
    const val KIND_PATTERN = "PATTERN"
    const val KIND_PASSWORD = "PASSWORD"

    fun newSalt(): ByteArray = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }

    /** PBKDF2-HMAC-SHA256 — used for both the verifier and the KEK. */
    fun derive(credential: CharArray, salt: ByteArray, iterations: Int = ITERATIONS): ByteArray {
        require(credential.isNotEmpty()) { "Empty credential" }
        val spec = PBEKeySpec(credential, salt, iterations, KEY_BITS)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
    }

    /** Constant-time verifier comparison. */
    fun verify(
        credential: CharArray,
        salt: ByteArray,
        expectedVerifier: ByteArray,
        iterations: Int = ITERATIONS,
    ): Boolean = MessageDigest.isEqual(derive(credential, salt, iterations), expectedVerifier)

    /**
     * Normalize a pattern (sequence of 3×3 grid cell indices, 0..8) into the
     * credential char sequence fed to PBKDF2. Distinct cells, ≥4 of them —
     * matching the system pattern-lock convention.
     *
     * This floor stays at four even though [SecretStrength.MIN_PATTERN_DOTS]
     * raised the *setup* floor to five: an unlock has to keep accepting a
     * four-dot pattern that was created under the old rule, and normalization
     * is on the unlock path too.
     */
    fun patternToCredential(cells: List<Int>): CharArray {
        require(cells.size >= 4) { "Pattern needs at least 4 dots" }
        require(cells.all { it in 0..8 }) { "Pattern cell out of range" }
        require(cells.toSet().size == cells.size) { "Pattern repeats a dot" }
        return cells.joinToString("-").toCharArray()
    }

    /** Why a credential was rejected at setup. */
    enum class SetupError {
        PIN_TOO_SHORT,
        PIN_NOT_DIGITS,
        PIN_TOO_WEAK,
        PASSWORD_TOO_SHORT,
        PASSWORD_TOO_WEAK,
        PATTERN_TOO_SHORT,
        PATTERN_TOO_WEAK,
        UNKNOWN_KIND,
    }

    /**
     * Strength floors enforced at setup. Returns null when acceptable.
     *
     * V2-36. This module has no resources and no Context; it decides, and the
     * UI layer says. The floors are what the tests pin — the sentence shown to
     * the user is a translation concern, not a crypto one.
     *
     * V2-7. The floors live in [SecretStrength] along with the reasoning for
     * each number and the blocklist that does most of the actual work. They
     * apply to *creation only*: an existing credential is never invalidated,
     * because NIST SP 800-63B-4 prohibits forcing rotation absent evidence of
     * compromise. Calling this on a stored credential is how
     * [SecretSpace.isCredentialWeak] decides whether to nudge.
     */
    fun setupError(kind: String, credential: CharArray): SetupError? = when (kind) {
        KIND_PIN -> when {
            credential.size < SecretStrength.MIN_PIN_DIGITS -> SetupError.PIN_TOO_SHORT
            !credential.all { it.isDigit() } -> SetupError.PIN_NOT_DIGITS
            SecretStrength.isWeakPin(credential) -> SetupError.PIN_TOO_WEAK
            else -> null
        }
        KIND_PASSWORD -> when {
            credential.size < SecretStrength.MIN_PASSWORD_CHARS -> SetupError.PASSWORD_TOO_SHORT
            SecretStrength.isWeakPassword(credential) -> SetupError.PASSWORD_TOO_WEAK
            else -> null
        }
        // "a-b-c-d-e" — n cells joined by n-1 dashes, so 2n-1 chars.
        KIND_PATTERN -> when {
            credential.size < SecretStrength.MIN_PATTERN_DOTS * 2 - 1 -> SetupError.PATTERN_TOO_SHORT
            SecretStrength.isWeakPatternCredential(credential) -> SetupError.PATTERN_TOO_WEAK
            else -> null
        }
        else -> SetupError.UNKNOWN_KIND
    }
}
