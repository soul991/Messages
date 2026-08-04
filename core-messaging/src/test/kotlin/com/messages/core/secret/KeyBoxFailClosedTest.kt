package com.messages.core.secret

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * R-18 regression: the secret-space KEK must never be readable at rest.
 *
 * The pre-fix implementation caught every Keystore failure and stored
 * `plain:` + Base64(kek) instead, so an unavailable Keystore silently turned a
 * key-encryption key into plaintext app data. These tests pin the fail-closed
 * contract and the legacy-value purge.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KeyBoxFailClosedTest {

    private lateinit var box: TestKeyBox

    @Before
    fun setUp() {
        box = TestKeyBox.install()
        prefs().edit().clear().apply()
    }

    @After
    fun tearDown() {
        TestKeyBox.uninstall()
        prefs().edit().clear().apply()
    }

    private fun context(): Context = ApplicationProvider.getApplicationContext()

    private fun prefs() =
        context().getSharedPreferences("secret_space", Context.MODE_PRIVATE)

    @Test
    fun `an unavailable keystore throws instead of degrading to plaintext`() {
        box.failing = true
        assertThrows(KeyBoxUnavailableException::class.java) {
            LocalKeyBox.encrypt("super-secret-kek".toByteArray())
        }
    }

    @Test
    fun `setup with a failing keystore stores no key material at all`() {
        box.failing = true
        SecretSpace.setUp(context(), SecretCrypto.KIND_PIN, "2468".toCharArray())

        // The space is usable, but nothing key-shaped was persisted...
        assertTrue(SecretSpace.isSetUp(context()))
        assertNull(SecretSpace.kekOrNull(context()))
        // ...and specifically no plaintext encoding anywhere in the prefs file.
        val dumped = prefs().all.values.joinToString("|") { it.toString() }
        assertFalse(dumped.contains(LocalKeyBox.LEGACY_PLAIN_PREFIX))
    }

    @Test
    fun `a recovered keystore re-wraps the KEK on the next successful unlock`() {
        box.failing = true
        SecretSpace.setUp(context(), SecretCrypto.KIND_PIN, "2468".toCharArray())
        assertNull(SecretSpace.kekOrNull(context()))

        box.failing = false
        assertTrue(
            SecretSpace.attempt(context(), "2468".toCharArray()) is SecretSpace.Attempt.Success
        )

        // The KEK is credential-derived, so a lost cache heals itself.
        assertNotNull(SecretSpace.kekOrNull(context()))
    }

    @Test
    fun `a legacy plaintext KEK is rejected and purged rather than used`() {
        SecretSpace.setUp(context(), SecretCrypto.KIND_PIN, "2468".toCharArray())
        // Simulate a value written by the pre-R-18 build.
        val legacy = LocalKeyBox.LEGACY_PLAIN_PREFIX +
            java.util.Base64.getEncoder().encodeToString("leaked-kek".toByteArray())
        prefs().edit().putString("kek_local", legacy).apply()

        assertTrue(LocalKeyBox.isUnprotected(legacy))
        // Reading it returns nothing AND removes it from disk.
        assertNull(SecretSpace.kekOrNull(context()))
        assertNull(prefs().getString("kek_local", null))
    }

    @Test
    fun `the purge sweep clears a legacy value without a read`() {
        SecretSpace.setUp(context(), SecretCrypto.KIND_PIN, "1357".toCharArray())
        prefs().edit().putString("kek_local", LocalKeyBox.LEGACY_PLAIN_PREFIX + "AAAA").apply()

        SecretSpace.purgeUnprotectedKekCache(context())

        assertNull(prefs().getString("kek_local", null))
    }

    @Test
    fun `wrapped values round-trip and carry the protected prefix`() {
        val secret = "kek-material-32-bytes-long------".toByteArray()
        val stored = LocalKeyBox.encrypt(secret)

        assertTrue(stored.startsWith(LocalKeyBox.KS_PREFIX))
        assertFalse(LocalKeyBox.isUnprotected(stored))
        assertEquals(secret.toList(), LocalKeyBox.decrypt(stored).toList())
    }
}
