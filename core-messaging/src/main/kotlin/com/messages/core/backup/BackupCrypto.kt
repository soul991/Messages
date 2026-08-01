package com.messages.core.backup

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * §8.3 backup encryption: the blob is always encrypted on-device; Drive only
 * ever stores ciphertext. A random 256-bit AES data key encrypts the payload
 * (AES-256-GCM, fresh nonce per backup); the data key itself is carried
 * wrapped inside the envelope by one or more unlock methods (`wrappedKeys[]`).
 *
 * Methods:
 *  - "account-plain": the data key is wrapped (AES-256-GCM) under a master
 *    key that lives in a key file in the same Drive appDataFolder as the
 *    snapshots. Access to the Google account IS the access control
 *    (WhatsApp-style): restore needs no user input. The payload stays
 *    end-to-end AES-256-GCM — only the key custody model changes.
 *  - "password": PBKDF2-HMAC-SHA256, ≥600k iterations, random salt. No
 *    longer produced for new backups, but old password-wrapped envelopes
 *    are still restorable (detected via [requiresPassword]).
 *  - "passkey-prf": reserved in the format for the Credential Manager PRF
 *    wrap; needs a hosted RP domain (see docs/ops/DRIVE_BACKUP_SETUP.md) and is
 *    not produced yet. The versioned envelope lets it be added without
 *    breaking existing backups.
 *
 * Blob layout: "MBK1" magic · 4-byte big-endian header length · header JSON
 * (the ONLY plaintext metadata) · ciphertext.
 */
object BackupCrypto {

    /**
     * Version 2 authenticates the plaintext header as AES-GCM additional
     * authenticated data (R-09). Version 1 envelopes stay readable — their
     * header is unauthenticated, which is exactly the weakness v2 closes, so v1
     * is accepted for restore only and never produced.
     */
    const val FORMAT_VERSION = 2
    const val LEGACY_FORMAT_VERSION = 1
    private val SUPPORTED_VERSIONS = setOf(LEGACY_FORMAT_VERSION, FORMAT_VERSION)

    const val PBKDF2_ITERATIONS = 600_000
    const val METHOD_PASSWORD = "password"
    const val METHOD_ACCOUNT = "account-plain"
    private const val MAGIC = "MBK1"
    private const val GCM_TAG_BITS = 128
    private const val NONCE_LEN = 12

    // ---- R-09 hostile-input bounds -------------------------------------
    // Each bound is checked BEFORE any allocation, PBKDF2 derivation or
    // decompression driven by the value it bounds. A backup blob is
    // attacker-supplied (a malicious file chosen in the restore picker, or a
    // tampered Drive object), so "the header said so" is never sufficient
    // reason to do unbounded work.
    private const val MAX_BLOB = 512L * 1024 * 1024
    private const val MAX_HEADER = 64 * 1024
    private const val MAX_EXPANDED = 128 * 1024 * 1024
    private const val MIN_ITERATIONS = 100_000
    private const val MAX_ITERATIONS = 2_000_000
    private const val MAX_WRAPPED_KEYS = 8
    private const val WRAPPED_KEY_BYTES = 48 // 32-byte data key + 16-byte GCM tag
    private const val MIN_SALT_BYTES = 16
    private const val MAX_SALT_BYTES = 64

    /** Raised when an envelope violates a structural bound. */
    class MalformedBackupException(message: String) : Exception(message)

    class WrongPasswordException : Exception("No unlock method accepted this password")

    class WrongMasterKeyException :
        Exception("The account key file does not unlock this backup")

    @Serializable
    data class Header(
        val formatVersion: Int,
        val createdAt: Long,
        val checkpointAt: Long,
        val deviceModel: String = "",
        val messageCount: Int = 0,
        /** Base64 GCM nonce for the payload ciphertext. */
        val nonce: String,
        val wrappedKeys: List<WrappedKey>,
    )

    @Serializable
    data class WrappedKey(
        val method: String, // "account-plain" | "password" | "passkey-prf"
        val salt: String,
        val iterations: Int,
        val nonce: String,
        val wrapped: String,
    )

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun newDataKey(): ByteArray = ByteArray(32).also { SecureRandom().nextBytes(it) }

    /** The Drive key-file master key (same shape as a data key). */
    fun newMasterKey(): ByteArray = newDataKey()

    /**
     * True when this envelope can only be opened with a user password —
     * i.e. it predates the account-plain access model. Drives the legacy
     * password prompt on restore.
     */
    fun requiresPassword(header: Header): Boolean =
        header.wrappedKeys.none { it.method == METHOD_ACCOUNT }

    /** Wrap [dataKey] under the Drive key-file [masterKey] (AES-GCM). */
    fun wrapWithMasterKey(dataKey: ByteArray, masterKey: ByteArray): WrappedKey {
        val nonce = ByteArray(NONCE_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE, SecretKeySpec(masterKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        return WrappedKey(METHOD_ACCOUNT, "", 0, b64(nonce), b64(cipher.doFinal(dataKey)))
    }

    /** Unwrap the data key with the Drive key-file [masterKey]. */
    fun unwrapWithMasterKey(header: Header, masterKey: ByteArray): ByteArray {
        for (wk in header.wrappedKeys.filter { it.method == METHOD_ACCOUNT }) {
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE, SecretKeySpec(masterKey, "AES"),
                    GCMParameterSpec(GCM_TAG_BITS, unb64(wk.nonce)),
                )
                return cipher.doFinal(unb64(wk.wrapped))
            } catch (_: Exception) {
                // stale key file for this wrap — try the next
            }
        }
        throw WrongMasterKeyException()
    }

    /** Convenience: key-file master key → payload JSON. */
    fun openWithMasterKey(
        blob: ByteArray,
        masterKey: ByteArray,
        maxExpanded: Int = MAX_EXPANDED,
    ): String = open(blob, unwrapWithMasterKey(readHeader(blob), masterKey), maxExpanded)

    /**
     * Wrap [dataKey] under an ALREADY-DERIVED PBKDF2 key (the secret locked
     * space's cached KEK). Produces a standard METHOD_PASSWORD wrap carrying
     * [salt]/[iterations] — so the restore side needs only the original
     * credential and the untouched [unwrapWithPassword]/[openWithPassword]
     * path. Used because scheduled backups run without the credential in hand;
     * the KEK cache stands in for it (see SecretSpace docs).
     */
    fun wrapWithKek(dataKey: ByteArray, kek: ByteArray, salt: ByteArray, iterations: Int): WrappedKey {
        val nonce = ByteArray(NONCE_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        return WrappedKey(METHOD_PASSWORD, b64(salt), iterations, b64(nonce), b64(cipher.doFinal(dataKey)))
    }

    /** Wrap [dataKey] under a user password (PBKDF2 → AES-GCM). */
    fun wrapWithPassword(dataKey: ByteArray, password: CharArray): WrappedKey {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val kek = deriveKek(password, salt, PBKDF2_ITERATIONS)
        val nonce = ByteArray(NONCE_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE, SecretKeySpec(kek, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        return WrappedKey(METHOD_PASSWORD, b64(salt), PBKDF2_ITERATIONS, b64(nonce), b64(cipher.doFinal(dataKey)))
    }

    /** Counterpart of [wrapWithKek]: unwrap with the derived key directly
     *  (device already holds the locked-space KEK cache — no prompt needed). */
    fun unwrapWithKek(header: Header, kek: ByteArray): ByteArray {
        for (wk in header.wrappedKeys.filter { it.method == METHOD_PASSWORD }) {
            try {
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"),
                    GCMParameterSpec(GCM_TAG_BITS, unb64(wk.nonce)),
                )
                return cipher.doFinal(unb64(wk.wrapped))
            } catch (_: Exception) {
                // wrong key for this wrap — try the next
            }
        }
        throw WrongPasswordException()
    }

    /** Try to unwrap the data key with [password] against every password wrap. */
    fun unwrapWithPassword(header: Header, password: CharArray): ByteArray {
        for (wk in header.wrappedKeys.filter { it.method == METHOD_PASSWORD }) {
            try {
                val kek = deriveKek(password, unb64(wk.salt), wk.iterations)
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(
                    Cipher.DECRYPT_MODE, SecretKeySpec(kek, "AES"),
                    GCMParameterSpec(GCM_TAG_BITS, unb64(wk.nonce)),
                )
                return cipher.doFinal(unb64(wk.wrapped))
            } catch (_: Exception) {
                // wrong password for this wrap — try the next
            }
        }
        throw WrongPasswordException()
    }

    /** Encrypt [payloadJson] (gzipped) into a full envelope blob. */
    fun seal(
        payloadJson: String,
        dataKey: ByteArray,
        wrappedKeys: List<WrappedKey>,
        createdAt: Long,
        checkpointAt: Long,
        deviceModel: String,
        messageCount: Int,
    ): ByteArray {
        require(wrappedKeys.isNotEmpty()) { "At least one unlock method is required" }
        val nonce = ByteArray(NONCE_LEN).also { SecureRandom().nextBytes(it) }

        // R-09: the header is serialized BEFORE the payload is encrypted so its
        // exact bytes can be bound in as AAD. Anything the header claims is
        // therefore covered by the GCM tag.
        val header = json.encodeToString(
            Header.serializer(),
            Header(
                formatVersion = FORMAT_VERSION,
                createdAt = createdAt,
                checkpointAt = checkpointAt,
                deviceModel = deviceModel,
                messageCount = messageCount,
                nonce = b64(nonce),
                wrappedKeys = wrappedKeys,
            ),
        ).toByteArray(Charsets.UTF_8)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE, SecretKeySpec(dataKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        cipher.updateAAD(header)
        val ciphertext = cipher.doFinal(gzip(payloadJson.toByteArray(Charsets.UTF_8)))

        val out = ByteArrayOutputStream()
        out.write(MAGIC.toByteArray(Charsets.US_ASCII))
        out.write(intToBytes(header.size))
        out.write(header)
        out.write(ciphertext)
        return out.toByteArray()
    }

    /**
     * Read the plaintext header without decrypting anything.
     *
     * R-09: this is the first code to touch an untrusted blob, so it enforces
     * every structural bound before the value it guards is used — envelope and
     * header size, format version, wrapped-key count, exact nonce/wrapped-key
     * lengths, salt size, and above all the PBKDF2 iteration count (an
     * unbounded value here is attacker-chosen CPU work, multiplied by the
     * number of password wraps).
     */
    fun readHeader(blob: ByteArray): Header = parseHeader(blob).first

    /** Bytes needed by [looksLikeEnvelope]. */
    const val MAGIC_BYTES = 4

    /**
     * V2-49: whether these leading bytes are an encrypted envelope rather than
     * a plain JSON backup.
     *
     * A magic-number probe, not a validation: it exists so the import path can
     * route a file to the right reader before committing to reading all of it.
     * Answering true says nothing about whether the envelope is well-formed —
     * [readHeader] decides that, and it is the code with the bounds.
     */
    fun looksLikeEnvelope(head: ByteArray): Boolean =
        head.size >= MAGIC_BYTES &&
            String(head, 0, MAGIC_BYTES, Charsets.US_ASCII) == MAGIC

    /** Header plus the exact bytes it was decoded from (needed as v2 AAD). */
    private fun parseHeader(blob: ByteArray): Pair<Header, ByteArray> {
        if (blob.size.toLong() > MAX_BLOB) throw MalformedBackupException("Backup is too large")
        if (blob.size <= 8 || String(blob, 0, 4, Charsets.US_ASCII) != MAGIC) {
            throw MalformedBackupException("Not a Messages backup")
        }
        val headerLen = bytesToInt(blob, 4)
        if (headerLen !in 1..minOf(MAX_HEADER, blob.size - 8)) {
            throw MalformedBackupException("Corrupt backup header")
        }
        val headerBytes = blob.copyOfRange(8, 8 + headerLen)
        val header = try {
            json.decodeFromString(Header.serializer(), headerBytes.toString(Charsets.UTF_8))
        } catch (e: Exception) {
            throw MalformedBackupException("Unreadable backup header: ${e.message}")
        }
        validate(header)
        return header to headerBytes
    }

    private fun validate(header: Header) {
        if (header.formatVersion !in SUPPORTED_VERSIONS) {
            throw MalformedBackupException("Unsupported backup version ${header.formatVersion}")
        }
        if (header.wrappedKeys.isEmpty() || header.wrappedKeys.size > MAX_WRAPPED_KEYS) {
            throw MalformedBackupException("Bad unlock-method count")
        }
        if (decodedSize(header.nonce) != NONCE_LEN) {
            throw MalformedBackupException("Bad payload nonce")
        }
        header.wrappedKeys.forEach { wk ->
            if (wk.method !in setOf(METHOD_ACCOUNT, METHOD_PASSWORD)) {
                throw MalformedBackupException("Unsupported unlock method")
            }
            if (decodedSize(wk.nonce) != NONCE_LEN) {
                throw MalformedBackupException("Bad wrap nonce")
            }
            if (decodedSize(wk.wrapped) != WRAPPED_KEY_BYTES) {
                throw MalformedBackupException("Bad wrapped key")
            }
            if (wk.method == METHOD_PASSWORD) {
                if (wk.iterations !in MIN_ITERATIONS..MAX_ITERATIONS) {
                    throw MalformedBackupException("Refusing attacker-chosen PBKDF2 work")
                }
                if (decodedSize(wk.salt) !in MIN_SALT_BYTES..MAX_SALT_BYTES) {
                    throw MalformedBackupException("Bad wrap salt")
                }
            }
        }
    }

    /** Strict Base64 length probe; -1 when the field is not valid Base64. */
    private fun decodedSize(field: String): Int =
        runCatching { unb64(field).size }.getOrElse { -1 }

    /**
     * Decrypt a whole blob with an already-unwrapped data key.
     *
     * V2-12: [maxExpanded] defaults to the structural ceiling, which is a
     * multiple of what a phone's heap holds — opening produces the expanded
     * bytes AND the String built from them, so the real cost is about three
     * times this number. Restore paths pass a device-derived figure
     * ([RestoreBudget.maxExpandedBytes]) so an over-large envelope is refused
     * mid-decompression instead of after it.
     */
    fun open(blob: ByteArray, dataKey: ByteArray, maxExpanded: Int = MAX_EXPANDED): String {
        val (header, headerBytes) = parseHeader(blob)
        val offset = 8 + headerBytes.size
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE, SecretKeySpec(dataKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, unb64(header.nonce)),
        )
        // R-09: from v2 the header is bound into the ciphertext as AAD, so
        // tampering with createdAt/messageCount/deviceModel — or swapping a
        // wrapped key between envelopes — fails the GCM tag instead of being
        // silently trusted. v1 envelopes carry no AAD and stay readable.
        if (header.formatVersion >= FORMAT_VERSION) cipher.updateAAD(headerBytes)
        val plain = cipher.doFinal(blob, offset, blob.size - offset)
        return String(
            gunzipBounded(plain, maxExpanded.coerceIn(1, MAX_EXPANDED)),
            Charsets.UTF_8,
        )
    }

    /** Convenience: password → payload JSON. */
    fun openWithPassword(
        blob: ByteArray,
        password: CharArray,
        maxExpanded: Int = MAX_EXPANDED,
    ): String = open(blob, unwrapWithPassword(readHeader(blob), password), maxExpanded)

    private fun deriveKek(password: CharArray, salt: ByteArray, iterations: Int): ByteArray {
        val spec = PBEKeySpec(password, salt, iterations, 256)
        return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        GZIPOutputStream(out).use { it.write(bytes) }
        return out.toByteArray()
    }

    /**
     * R-09: GZIP expands, so a small envelope can decompress to an unbounded
     * amount of heap ("zip bomb"). Stop at [limit] rather than calling
     * readBytes() and discovering the size afterwards. The limit is checked
     * before each chunk is accumulated, so the buffer never exceeds it.
     *
     * Internal rather than private so tests can drive it with a small limit
     * instead of allocating a realistic bomb.
     */
    internal fun gunzipBounded(bytes: ByteArray, limit: Int): ByteArray {
        val out = ByteArrayOutputStream(minOf(bytes.size * 2, 1 shl 20))
        GZIPInputStream(ByteArrayInputStream(bytes)).use { input ->
            val buffer = ByteArray(32 * 1024)
            var total = 0L
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                total += n
                if (total > limit) {
                    throw MalformedBackupException("Backup expands beyond the allowed size")
                }
                out.write(buffer, 0, n)
            }
        }
        return out.toByteArray()
    }

    private fun intToBytes(v: Int) = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte(),
    )

    private fun bytesToInt(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or (b[off + 3].toInt() and 0xFF)

    private fun b64(b: ByteArray): String = java.util.Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String): ByteArray = java.util.Base64.getDecoder().decode(s)
}
