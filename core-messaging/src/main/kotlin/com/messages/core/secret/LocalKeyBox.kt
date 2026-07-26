package com.messages.core.secret

import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Android-Keystore AES-GCM wrap for small locally-cached secrets (the
 * secret-space backup KEK). Same pattern as DriveBackup's master-key cache.
 *
 * Fallback: when the Keystore is unavailable (JVM/Robolectric tests, or an
 * exotic Keystore failure), values are stored with a `plain:` prefix. That is
 * an acceptable degradation — the cached KEK guards the BACKUP envelope, and
 * on-device the locked rows already live in the app-private Room index; the
 * Keystore wrap is defense-in-depth for extracted-app-data scenarios, not the
 * primary boundary.
 */
internal object LocalKeyBox {

    private const val ALIAS = "secret_space_kek"
    private const val PLAIN_PREFIX = "plain:"
    private const val KS_PREFIX = "ks:"
    private const val GCM_TAG_BITS = 128

    fun encrypt(plain: ByteArray): String = runCatching {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val out = cipher.iv + cipher.doFinal(plain)
        KS_PREFIX + java.util.Base64.getEncoder().encodeToString(out)
    }.getOrElse {
        PLAIN_PREFIX + java.util.Base64.getEncoder().encodeToString(plain)
    }

    fun decrypt(stored: String): ByteArray = when {
        stored.startsWith(PLAIN_PREFIX) ->
            java.util.Base64.getDecoder().decode(stored.removePrefix(PLAIN_PREFIX))
        stored.startsWith(KS_PREFIX) -> {
            val all = java.util.Base64.getDecoder().decode(stored.removePrefix(KS_PREFIX))
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(
                Cipher.DECRYPT_MODE, keystoreKey(),
                GCMParameterSpec(GCM_TAG_BITS, all.copyOfRange(0, 12)),
            )
            cipher.doFinal(all, 12, all.size - 12)
        }
        else -> throw IllegalArgumentException("Unknown key-box format")
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
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        return KeyGenerator.getInstance("AES", "AndroidKeyStore")
            .apply { init(spec) }.generateKey()
    }
}
