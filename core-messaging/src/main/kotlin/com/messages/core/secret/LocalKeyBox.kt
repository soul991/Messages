package com.messages.core.secret

import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Raised when the Keystore cannot protect a value. Never degrade to plaintext. */
internal class KeyBoxUnavailableException(cause: Throwable?) :
    IllegalStateException("Android Keystore unavailable; refusing to store key material", cause)

internal interface KeyBox {
    fun encrypt(plain: ByteArray): String
    fun decrypt(stored: String): ByteArray
}

/**
 * Android-Keystore AES-GCM wrap for small locally-cached secrets (the
 * secret-space backup KEK).
 *
 * FAILS CLOSED (R-18). An earlier revision caught every Keystore failure and
 * stored `plain:` + Base64(secret) instead, so a missing, invalidated or
 * malfunctioning Keystore silently turned the backup key-encryption key into
 * plaintext app data — extractable, and (before R-01) copyable by platform
 * backup. There is no longer any plaintext form:
 *
 *  - encrypt() throws [KeyBoxUnavailableException] rather than degrading;
 *  - decrypt() accepts ONLY the `ks:` form and rejects legacy `plain:` values.
 *
 * Losing the wrapped KEK is recoverable and cheap: it is credential-derived, so
 * SecretSpace.attempt() re-derives and re-wraps it on the next successful
 * unlock. Callers treat a null KEK as "ask for the credential", never as
 * "proceed unprotected".
 *
 * Tests inject a fake via [installForTests] — the JVM/Robolectric Keystore is
 * not a real one, and that must not be a production code path.
 */
internal object LocalKeyBox : KeyBox {

    internal const val LEGACY_PLAIN_PREFIX = "plain:"
    internal const val KS_PREFIX = "ks:"

    @Volatile
    private var testBox: KeyBox? = null

    /** Install a fake key box for tests; pass null to restore production behaviour. */
    internal fun installForTests(box: KeyBox?) {
        testBox = box
    }

    private fun delegate(): KeyBox = testBox ?: AndroidKeyBox

    override fun encrypt(plain: ByteArray): String = delegate().encrypt(plain)

    override fun decrypt(stored: String): ByteArray = delegate().decrypt(stored)

    /** True for values this key box refuses to trust (legacy plaintext, junk). */
    internal fun isUnprotected(stored: String): Boolean = !stored.startsWith(KS_PREFIX)

    private object AndroidKeyBox : KeyBox {

        private const val ALIAS = "secret_space_kek"
        private const val GCM_TAG_BITS = 128
        private const val NONCE_BYTES = 12

        override fun encrypt(plain: ByteArray): String = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
            val out = cipher.iv + cipher.doFinal(plain)
            KS_PREFIX + java.util.Base64.getEncoder().encodeToString(out)
        } catch (e: KeyBoxUnavailableException) {
            throw e
        } catch (e: Throwable) {
            throw KeyBoxUnavailableException(e)
        }

        override fun decrypt(stored: String): ByteArray {
            // Legacy `plain:` values land here and are deliberately rejected;
            // SecretSpace purges them and re-wraps after authentication.
            require(stored.startsWith(KS_PREFIX)) { "Unprotected key-box value rejected" }
            val all = java.util.Base64.getDecoder().decode(stored.removePrefix(KS_PREFIX))
            require(all.size > NONCE_BYTES) { "Truncated key-box value" }
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, keystoreKey(),
                GCMParameterSpec(GCM_TAG_BITS, all.copyOfRange(0, NONCE_BYTES)),
            )
            return cipher.doFinal(all, NONCE_BYTES, all.size - NONCE_BYTES)
        }

        private fun keystoreKey(): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            val spec = android.security.keystore.KeyGenParameterSpec.Builder(
                ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(
                    android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE
                )
                .setKeySize(256)
                .build()
            return KeyGenerator.getInstance("AES", "AndroidKeyStore")
                .apply { init(spec) }.generateKey()
        }
    }
}
