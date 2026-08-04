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

    /**
     * Set to make decrypt() behave like a Keystore whose key was invalidated
     * underneath us (screen-lock removed, factory reset of the secure element,
     * restore onto different hardware). Separate from [failing] because the
     * interesting V2-6 case is exactly the asymmetric one: a key can still be
     * written while previously-wrapped values have become unreadable.
     */
    var failingDecrypt: Boolean = false

    override fun encrypt(plain: ByteArray): String {
        if (failing) throw KeyBoxUnavailableException(null)
        return LocalKeyBox.KS_PREFIX + java.util.Base64.getEncoder().encodeToString(plain)
    }

    override fun decrypt(stored: String): ByteArray {
        if (failingDecrypt) throw KeyBoxUnavailableException(null)
        require(stored.startsWith(LocalKeyBox.KS_PREFIX)) { "Unprotected key-box value rejected" }
        return java.util.Base64.getDecoder().decode(stored.removePrefix(LocalKeyBox.KS_PREFIX))
    }

    companion object {
        /**
         * Install a fresh fake for the duration of a test.
         *
         * The [LockedContent] key cache is dropped on both install and
         * uninstall: it is a process-wide static holding a key unwrapped by
         * whichever box was in force at the time, so leaving it in place made
         * whether a test encrypted at all depend on which test ran first.
         */
        fun install(): TestKeyBox = TestKeyBox().also {
            LocalKeyBox.installForTests(it)
            LockedContent.resetCacheForTests()
        }

        fun uninstall() {
            LocalKeyBox.installForTests(null)
            LockedContent.resetCacheForTests()
        }
    }
}
