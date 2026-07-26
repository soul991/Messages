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
     */
    fun patternToCredential(cells: List<Int>): CharArray {
        require(cells.size >= 4) { "Pattern needs at least 4 dots" }
        require(cells.all { it in 0..8 }) { "Pattern cell out of range" }
        require(cells.toSet().size == cells.size) { "Pattern repeats a dot" }
        return cells.joinToString("-").toCharArray()
    }

    /** Basic strength floors enforced at setup. Returns null when acceptable. */
    fun setupError(kind: String, credential: CharArray): String? = when (kind) {
        KIND_PIN -> when {
            credential.size < 4 -> "PIN must be at least 4 digits"
            !credential.all { it.isDigit() } -> "PIN can only contain digits"
            else -> null
        }
        KIND_PASSWORD -> if (credential.size < 4) "Password must be at least 4 characters" else null
        KIND_PATTERN -> if (credential.size < 7) "Pattern needs at least 4 dots" else null // "a-b-c-d"
        else -> "Unknown credential type"
    }
}
