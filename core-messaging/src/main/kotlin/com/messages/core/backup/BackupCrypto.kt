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
 *    wrap; needs a hosted RP domain (see docs/DRIVE_BACKUP_SETUP.md) and is
 *    not produced yet. The versioned envelope lets it be added without
 *    breaking existing backups.
 *
 * Blob layout: "MBK1" magic · 4-byte big-endian header length · header JSON
 * (the ONLY plaintext metadata) · ciphertext.
 */
object BackupCrypto {

    const val FORMAT_VERSION = 1
    const val PBKDF2_ITERATIONS = 600_000
    const val METHOD_PASSWORD = "password"
    const val METHOD_ACCOUNT = "account-plain"
    private const val MAGIC = "MBK1"
    private const val GCM_TAG_BITS = 128
    private const val NONCE_LEN = 12

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
    fun openWithMasterKey(blob: ByteArray, masterKey: ByteArray): String =
        open(blob, unwrapWithMasterKey(readHeader(blob), masterKey))

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
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.ENCRYPT_MODE, SecretKeySpec(dataKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, nonce),
        )
        val ciphertext = cipher.doFinal(gzip(payloadJson.toByteArray(Charsets.UTF_8)))

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

        val out = ByteArrayOutputStream()
        out.write(MAGIC.toByteArray(Charsets.US_ASCII))
        out.write(intToBytes(header.size))
        out.write(header)
        out.write(ciphertext)
        return out.toByteArray()
    }

    /** Read the plaintext header without decrypting anything. */
    fun readHeader(blob: ByteArray): Header {
        require(blob.size > 8 && String(blob, 0, 4, Charsets.US_ASCII) == MAGIC) {
            "Not a Messages backup"
        }
        val headerLen = bytesToInt(blob, 4)
        require(headerLen in 1..(blob.size - 8)) { "Corrupt backup header" }
        return json.decodeFromString(
            Header.serializer(), String(blob, 8, headerLen, Charsets.UTF_8),
        )
    }

    /** Decrypt a whole blob with an already-unwrapped data key. */
    fun open(blob: ByteArray, dataKey: ByteArray): String {
        val header = readHeader(blob)
        val headerLen = bytesToInt(blob, 4)
        val offset = 8 + headerLen
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE, SecretKeySpec(dataKey, "AES"),
            GCMParameterSpec(GCM_TAG_BITS, unb64(header.nonce)),
        )
        val plain = cipher.doFinal(blob, offset, blob.size - offset)
        return String(gunzip(plain), Charsets.UTF_8)
    }

    /** Convenience: password → payload JSON. */
    fun openWithPassword(blob: ByteArray, password: CharArray): String =
        open(blob, unwrapWithPassword(readHeader(blob), password))

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

    private fun gunzip(bytes: ByteArray): ByteArray =
        GZIPInputStream(ByteArrayInputStream(bytes)).use { it.readBytes() }

    private fun intToBytes(v: Int) = byteArrayOf(
        (v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte(),
    )

    private fun bytesToInt(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0xFF) shl 24) or ((b[off + 1].toInt() and 0xFF) shl 16) or
            ((b[off + 2].toInt() and 0xFF) shl 8) or (b[off + 3].toInt() and 0xFF)

    private fun b64(b: ByteArray): String = java.util.Base64.getEncoder().encodeToString(b)
    private fun unb64(s: String): ByteArray = java.util.Base64.getDecoder().decode(s)
}
