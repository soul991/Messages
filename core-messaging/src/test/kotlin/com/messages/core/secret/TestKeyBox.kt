package com.messages.core.secret

/**
 * In-memory stand-in for the Android Keystore (R-18). Production code fails
 * closed when the Keystore is unavailable, and the JVM/Robolectric "Keystore" is
 * not a real one — so tests that exercise the secret-space KEK cache install
 * this instead of relying on a plaintext production fallback.
 *
 * It reproduces the shape the production key box guarantees: values carry the
 * `ks:` prefix, so [LocalKeyBox.isUnprotected] treats them as protected and the
 * legacy-plaintext purge path is not triggered.
 */
internal class TestKeyBox : KeyBox {

    /** Set to make encrypt() behave like an unavailable Keystore. */
    var failing: Boolean = false

    override fun encrypt(plain: ByteArray): String {
        if (failing) throw KeyBoxUnavailableException(null)
        return LocalKeyBox.KS_PREFIX + java.util.Base64.getEncoder().encodeToString(plain)
    }

    override fun decrypt(stored: String): ByteArray {
        require(stored.startsWith(LocalKeyBox.KS_PREFIX)) { "Unprotected key-box value rejected" }
        return java.util.Base64.getDecoder().decode(stored.removePrefix(LocalKeyBox.KS_PREFIX))
    }

    companion object {
        /** Install a fresh fake for the duration of a test. */
        fun install(): TestKeyBox = TestKeyBox().also { LocalKeyBox.installForTests(it) }

        fun uninstall() = LocalKeyBox.installForTests(null)
    }
}
