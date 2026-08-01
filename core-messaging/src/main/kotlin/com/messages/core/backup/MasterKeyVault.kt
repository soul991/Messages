package com.messages.core.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * V2-5 / V2-46: the user-held wrap of the Drive backup master key.
 *
 * ## What was wrong
 *
 * The default backup mode is "account-plain": a random master key sits in a
 * file in the same Drive appDataFolder as the snapshots it decrypts, so signing
 * in to the Google account is the whole of the access control. That is
 * WhatsApp's pre-2021 model and it is a legitimate default — it is the only one
 * where restore needs nothing the user has to keep. But it is *account* access
 * control, not end-to-end protection: whoever reaches the app-data set reaches
 * both halves at once.
 *
 * ## What this adds
 *
 * An opt-in second custody mode. The master key stops living in the clear and
 * starts living inside a [Sealed] envelope wrapped under a secret the user
 * holds and Drive never sees. The plain key file is then **deleted** — leaving
 * it in place would close nothing at all, which is the part of this change that
 * actually does the work.
 *
 * Deliberately, the *master key itself does not change*. Every snapshot already
 * on Drive stays readable, and `BackupCrypto`'s `wrappedKeys[]` needs no new
 * method: only the custody of the key changed, not the envelope format. That is
 * also what makes rotation cheap — reseal the same master key under a new
 * secret and no snapshot is touched.
 *
 * ## The two secrets, and why one of them is honest about its limits
 *
 * WhatsApp offers exactly this choice (see the 2021 E2EE-backup design): a
 * 64-hex-digit random key the user keeps, or a password whose guessing is
 * rate-limited by an HSM-backed key vault using an OPRF. This app has no
 * server, so the second half of that design is not reachable and a password
 * here is offline-attackable at PBKDF2 speed — the same caveat V2-7 documents
 * for the locked space. Both are offered anyway, because a recovery code the
 * user loses protects nothing either:
 *
 *  - [METHOD_RECOVERY_CODE] — generated here, 160 bits, unguessable. The
 *    recommended choice, and the only one whose strength does not depend on the
 *    user.
 *  - [METHOD_PASSWORD] — screened at setup by `SecretStrength`, and paired with
 *    copy that says plainly what it is worth.
 *
 * ## Recovery-code format
 *
 * Crockford Base32 — 32 symbols, shown in 8 groups of 4. WhatsApp's precedent
 * is 64 hex digits; this diverges on purpose. The realistic failure of a
 * written-down key is mistranscription, and Crockford's alphabet exists for
 * exactly that: no I, L, O or U, and decoding folds I/L to 1 and O to 0 so the
 * usual hand-copying slips still open the vault. 160 bits at half the
 * transcription length is the better trade; nothing is brute-forcing 2^160.
 */
object MasterKeyVault {

    const val FORMAT_VERSION = 1

    /** Matches [BackupCrypto.PBKDF2_ITERATIONS] — one KDF cost across the app. */
    const val ITERATIONS = 600_000

    const val METHOD_RECOVERY_CODE = "recovery-code"
    const val METHOD_PASSWORD = "password"

    /** 160 bits — 32 Base32 symbols exactly, no padding, no leftover bits. */
    const val RECOVERY_CODE_BYTES = 20
    const val RECOVERY_CODE_CHARS = RECOVERY_CODE_BYTES * 8 / 5
    const val RECOVERY_CODE_GROUP = 4

    private const val KDF = "PBKDF2-HMAC-SHA256"
    private const val GCM_TAG_BITS = 128
    private const val NONCE_LEN = 12
    private const val SALT_BYTES = 16
    private const val MASTER_KEY_BYTES = 32

    /** Crockford Base32: the digits plus the letters, minus I, L, O and U. */
    private const val ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    // ---- Hostile-input bounds -------------------------------------------
    // The vault object is fetched from Drive, so it is attacker-supplied in
    // exactly the same sense a backup blob is. Every bound is checked before
    // the value it guards drives an allocation or a key derivation.
    /** A sealed vault is a few hundred bytes; this is generous. */
    const val MAX_VAULT_BYTES = 8 * 1024
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 2_000_000
    private const val MIN_SALT_BYTES = 16
    private const val MAX_SALT_BYTES = 64
    private const val WRAPPED_BYTES = MASTER_KEY_BYTES + 16 // key + GCM tag

    /** The supplied secret did not open the vault. */
    class WrongSecretException : Exception("That code does not unlock the backup key")

    /** The vault object is not a vault, or violates a structural bound. */
    class MalformedVaultException(message: String) : Exception(message)

    @Serializable
    data class Sealed(
        val formatVersion: Int,
        /** [METHOD_RECOVERY_CODE] or [METHOD_PASSWORD] — drives which prompt to show. */
        val method: String,
        val kdf: String,
        val salt: String,
        val iterations: Int,
        val nonce: String,
        val wrapped: String,
        val createdAt: Long,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    // ---- Recovery codes -------------------------------------------------

    /**
     * A fresh 160-bit recovery code, already normalized (no separators).
     * [formatForDisplay] adds the grouping.
     */
    fun newRecoveryCode(): CharArray =
        base32(ByteArray(RECOVERY_CODE_BYTES).also { SecureRandom().nextBytes(it) })

    /**
     * The grouped form the user reads and writes down.
     *
     * This is the one place the code becomes an immutable String, which is
     * unavoidable — it has to be rendered and it has to be copyable. Everywhere
     * else it stays a [CharArray] the caller can wipe.
     */
    fun formatForDisplay(code: CharArray): String = buildString(
        code.size + code.size / RECOVERY_CODE_GROUP,
    ) {
        for (i in code.indices) {
            if (i > 0 && i % RECOVERY_CODE_GROUP == 0) append('-')
            append(code[i])
        }
    }

    /**
     * Fold what the user typed back to canonical form: separators and
     * whitespace dropped, case ignored, and Crockford's transcription aliases
     * applied (I and L read as 1, O reads as 0). Nothing else is corrected —
     * a genuinely wrong symbol should fail, not be guessed at.
     */
    fun normalizeRecoveryCode(input: CharArray): CharArray {
        val out = CharArray(input.size)
        var n = 0
        for (raw in input) {
            if (raw == '-' || raw == '_' || raw.isWhitespace()) continue
            out[n++] = when (val upper = raw.uppercaseChar()) {
                'O' -> '0'
                'I', 'L' -> '1'
                else -> upper
            }
        }
        return out.copyOf(n)
    }

    /** Whether [normalized] could be a code this app generated. */
    fun isWellFormedRecoveryCode(normalized: CharArray): Boolean =
        normalized.size == RECOVERY_CODE_CHARS && normalized.all { it in ALPHABET }

    // ---- Seal / open ----------------------------------------------------

    /**
     * Wrap [masterKey] under [secret] and return the bytes to store on Drive.
     *
     * Every call mints a fresh salt AND a fresh nonce, so resealing the same
     * master key under a new secret — which is exactly what rotation does —
     * can never reuse a (key, nonce) pair.
     */
    fun seal(
        masterKey: ByteArray,
        secret: CharArray,
        method: String,
        createdAt: Long,
    ): ByteArray {
        require(masterKey.size == MASTER_KEY_BYTES) { "A backup master key is 32 bytes" }
        require(method == METHOD_RECOVERY_CODE || method == METHOD_PASSWORD) {
            "Unknown vault method: $method"
        }
        require(secret.isNotEmpty()) { "An empty secret protects nothing" }

        val salt = ByteArray(SALT_BYTES).also { SecureRandom().nextBytes(it) }
        val nonce = ByteArray(NONCE_LEN).also { SecureRandom().nextBytes(it) }
        val saltB64 = b64(salt)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE, SecretKeySpec(derive(secret, salt, ITERATIONS), "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        // The KDF parameters are plaintext and therefore tamperable. Binding
        // them in as AAD means an attacker who rewrites `iterations` down to
        // the floor gets a tag failure rather than a cheaper vault to attack.
        cipher.updateAAD(aad(FORMAT_VERSION, method, saltB64, ITERATIONS, createdAt))

        return json.encodeToString(
            Sealed.serializer(),
            Sealed(
                formatVersion = FORMAT_VERSION,
                method = method,
                kdf = KDF,
                salt = saltB64,
                iterations = ITERATIONS,
                nonce = b64(nonce),
                wrapped = b64(cipher.doFinal(masterKey)),
                createdAt = createdAt,
            ),
        ).toByteArray(Charsets.UTF_8)
    }

    /** Which secret this vault expects, without attempting to open it. */
    fun methodOf(vaultBytes: ByteArray): String = parse(vaultBytes).method

    /** When this vault was written — shown so the user can spot a stale one. */
    fun createdAtOf(vaultBytes: ByteArray): Long = parse(vaultBytes).createdAt

    /**
     * Recover the master key. Throws [WrongSecretException] when [secret] is
     * wrong and [MalformedVaultException] when the object is not a usable
     * vault — the two are separated because only the first is the user's
     * problem to fix.
     */
    fun open(vaultBytes: ByteArray, secret: CharArray): ByteArray {
        val sealed = parse(vaultBytes)
        val key = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(derive(secret, unb64(sealed.salt), sealed.iterations), "AES"),
                GCMParameterSpec(GCM_TAG_BITS, unb64(sealed.nonce)),
            )
            cipher.updateAAD(
                aad(
                    sealed.formatVersion, sealed.method, sealed.salt,
                    sealed.iterations, sealed.createdAt,
                ),
            )
            cipher.doFinal(unb64(sealed.wrapped))
        } catch (_: Exception) {
            throw WrongSecretException()
        }
        // Length is already pinned by the WRAPPED_BYTES bound, but a wrapped
        // key that authenticates and is still the wrong size would mean the
        // format changed underneath us — fail rather than hand it onward.
        if (key.size != MASTER_KEY_BYTES) {
            throw MalformedVaultException("Unwrapped key is ${key.size} bytes, expected $MASTER_KEY_BYTES")
        }
        return key
    }

    // ---- Internals ------------------------------------------------------

    private fun parse(vaultBytes: ByteArray): Sealed {
        if (vaultBytes.isEmpty() || vaultBytes.size > MAX_VAULT_BYTES) {
            throw MalformedVaultException("Not a backup key vault")
        }
        val sealed = try {
            json.decodeFromString(Sealed.serializer(), vaultBytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw MalformedVaultException("Unreadable backup key vault: ${e.message}")
        }
        if (sealed.formatVersion != FORMAT_VERSION) {
            throw MalformedVaultException("Unsupported vault version ${sealed.formatVersion}")
        }
        if (sealed.method != METHOD_RECOVERY_CODE && sealed.method != METHOD_PASSWORD) {
            throw MalformedVaultException("Unsupported vault method")
        }
        if (sealed.kdf != KDF) throw MalformedVaultException("Unsupported vault KDF")
        if (sealed.iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
            throw MalformedVaultException("Refusing attacker-chosen PBKDF2 work")
        }
        if (decodedSize(sealed.salt) !in MIN_SALT_BYTES..MAX_SALT_BYTES) {
            throw MalformedVaultException("Bad vault salt")
        }
        if (decodedSize(sealed.nonce) != NONCE_LEN) throw MalformedVaultException("Bad vault nonce")
        if (decodedSize(sealed.wrapped) != WRAPPED_BYTES) {
            throw MalformedVaultException("Bad wrapped key")
        }
        return sealed
    }

    private fun aad(
        version: Int,
        method: String,
        saltB64: String,
        iterations: Int,
        createdAt: Long,
    ): ByteArray = "$version|$method|$saltB64|$iterations|$createdAt".toByteArray(Charsets.UTF_8)

    private fun derive(secret: CharArray, salt: ByteArray, iterations: Int): ByteArray =
        SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(PBEKeySpec(secret, salt, iterations, 256)).encoded

    private fun base32(bytes: ByteArray): CharArray {
        val out = CharArray(bytes.size * 8 / 5)
        var buffer = 0L
        var bits = 0
        var i = 0
        for (b in bytes) {
            buffer = (buffer shl 8) or (b.toLong() and 0xFF)
            bits += 8
            while (bits >= 5) {
                out[i++] = ALPHABET[((buffer ushr (bits - 5)) and 0x1FL).toInt()]
                bits -= 5
            }
        }
        return out
    }

    private fun decodedSize(field: String): Int =
        runCatching { unb64(field).size }.getOrElse { -1 }

    private fun b64(b: ByteArray): String = java.util.Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String): ByteArray = java.util.Base64.getDecoder().decode(s)
}
