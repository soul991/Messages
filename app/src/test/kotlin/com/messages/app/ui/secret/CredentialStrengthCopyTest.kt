package com.messages.app.ui.secret

import com.messages.core.secret.SecretCrypto
import com.messages.core.secret.SecretStrength
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V2-7 anti-decay guard.
 *
 * The strength floors live in [SecretStrength], but the sentences the user
 * reads quote the numbers ("at least 6 digits"). Those are two copies of one
 * fact, and the copy in strings.xml is the one nothing else would catch when it
 * drifts: a floor raised in Kotlin while the string still says 6 produces an
 * app that rejects a code for a reason it just told the user was fine.
 *
 * Also pinned here: every [SecretCrypto.SetupError] has a sentence. A new enum
 * value with no branch in `setupErrorRes` is a compile error, but a branch
 * pointing at a string that was never written is not.
 */
class CredentialStrengthCopyTest {

    private val strings = File("src/main/res/values/strings.xml")
    private val inputs = File("src/main/kotlin/com/messages/app/ui/secret/SecretInputs.kt")

    private fun stringValue(name: String): String {
        val m = Regex("""<string name="$name">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(strings.readText())
        assertTrue("missing string resource: $name", m != null)
        return m!!.groupValues[1]
    }

    @Test
    fun `the rejection copy quotes the floors that are actually enforced`() {
        assertTrue(
            "PIN error text must quote MIN_PIN_DIGITS (${SecretStrength.MIN_PIN_DIGITS})",
            stringValue("secret_error_pin_too_short")
                .contains("${SecretStrength.MIN_PIN_DIGITS} digits"),
        )
        assertTrue(
            "password error text must quote MIN_PASSWORD_CHARS (${SecretStrength.MIN_PASSWORD_CHARS})",
            stringValue("secret_error_password_too_short")
                .contains("${SecretStrength.MIN_PASSWORD_CHARS} characters"),
        )
        assertTrue(
            "pattern error text must quote MIN_PATTERN_DOTS (${SecretStrength.MIN_PATTERN_DOTS})",
            stringValue("secret_error_pattern_too_short")
                .contains("${SecretStrength.MIN_PATTERN_DOTS} dots"),
        )
    }

    @Test
    fun `every SetupError has a sentence`() {
        val text = inputs.readText()
        val declared = Regex("""<string name="(secret_error_\w+)">""")
            .findAll(strings.readText()).map { it.groupValues[1] }.toSet()
        for (error in SecretCrypto.SetupError.entries) {
            val res = "secret_error_${error.name.lowercase()}"
            assertTrue("no branch in setupErrorRes for $error", text.contains("SetupError.${error.name} ->"))
            assertTrue("no string resource $res for $error", res in declared)
        }
    }

    @Test
    fun `setup states the offline-guessing caveat rather than implying strength`() {
        // The verifier travels inside backups, so a PIN is attackable offline
        // where the cooldown cannot reach. If this note is ever dropped, the
        // lock silently starts implying a protection it does not provide.
        val note = stringValue("secret_setup_strength_note").lowercase()
        assertTrue("the strength note must mention backups", note.contains("backup"))
        assertTrue("the strength note must mention offline guessing", note.contains("offline"))
        assertTrue(
            "the strength note must be shown by the shared creation component",
            inputs.readText().contains("R.string.secret_setup_strength_note"),
        )
    }

    @Test
    fun `the weak-credential nudge does not claim anything stops working`() {
        // V2-7 grandfathers existing credentials on purpose (NIST SP 800-63B-4
        // prohibits forced rotation absent evidence of compromise). Copy that
        // implied an expiry would make the nudge a lie.
        val body = stringValue("secret_weak_credential_body").lowercase()
        assertTrue("the nudge must say the existing code still works", body.contains("still works"))
        for (word in listOf("must", "expire", "required", "no longer")) {
            assertTrue("the nudge must not imply enforcement: found \"$word\"", !body.contains(word))
        }
    }
}
