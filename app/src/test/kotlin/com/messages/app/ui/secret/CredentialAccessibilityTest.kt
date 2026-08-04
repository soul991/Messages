package com.messages.app.ui.secret

import com.messages.core.secret.SecretCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V2-50 guard: the credential chooser's accessibility advice.
 *
 * Two things are pinned. First that the advice is *conditional on a screen
 * reader actually being active* — shown to everyone it becomes copy people
 * learn to skip, which is the same as not having it. Second that it stays
 * advice: the recommendation must not be able to quietly become a restriction,
 * because someone who already has a pattern credential and turns TalkBack on
 * today would find their own method missing from the change-code screen.
 *
 * The app module's unit tests are pure JVM on purpose — no Robolectric — so the
 * `AccessibilityManager` call itself is not exercised here. That call is three
 * lines inside a `runCatching` that defaults to false; what is worth pinning is
 * the decision it feeds, and that lives in this module's source and resources.
 */
class CredentialAccessibilityTest {

    private val inputs = File("src/main/kotlin/com/messages/app/ui/secret/SecretInputs.kt")
    private val strings = File("src/main/res/values/strings.xml")

    @Test
    fun `the recommended methods are the text-entry ones, and pattern is not excluded`() {
        assertEquals(
            listOf(SecretCrypto.KIND_PIN, SecretCrypto.KIND_PASSWORD),
            CredentialAccessibility.RECOMMENDED,
        )
        assertTrue(CredentialAccessibility.isRecommended(SecretCrypto.KIND_PIN))
        assertTrue(CredentialAccessibility.isRecommended(SecretCrypto.KIND_PASSWORD))
        assertFalse(CredentialAccessibility.isRecommended(SecretCrypto.KIND_PATTERN))
    }

    @Test
    fun `all three methods stay offered — the recommendation never filters the chooser`() {
        // CREDENTIAL_KINDS is the single list both setup and change-code render.
        // If a future edit ever filters it by isRecommended, this fails: a user
        // whose existing credential is a pattern must still be able to pick one.
        assertEquals(
            listOf(SecretCrypto.KIND_PIN, SecretCrypto.KIND_PATTERN, SecretCrypto.KIND_PASSWORD),
            CREDENTIAL_KINDS.map { it.first },
        )
        assertFalse(
            "the chooser must not filter its options by the accessibility recommendation",
            Regex("""CREDENTIAL_KINDS\s*\.?\s*\n?\s*\.(filter|filterNot)""")
                .containsMatchIn(inputs.readText()),
        )
    }

    @Test
    fun `the note is shown only when a screen reader is driving the UI`() {
        // Unconditional advice is advice nobody reads. The gate is the platform's
        // own answer, not a preference of ours.
        val text = inputs.readText()
        val gate = text.indexOf("CredentialAccessibility.touchExplorationOn")
        val usage = text.indexOf("R.string.secret_setup_accessible_methods_note")
        assertTrue("the note must be gated on touchExplorationOn", gate in 0 until usage)
    }

    @Test
    fun `the note is honest about being advice and does not overclaim`() {
        val note = Regex(
            """<string name="secret_setup_accessible_methods_note">(.*?)</string>""",
            RegexOption.DOT_MATCHES_ALL,
        ).find(strings.readText())?.groupValues?.get(1)?.lowercase()
        assertTrue("missing secret_setup_accessible_methods_note", note != null)
        assertTrue("the note must say the other methods still work", note!!.contains("still work"))
        for (word in listOf("cannot", "not supported", "unavailable", "must use")) {
            assertTrue("the note must not read as a restriction: \"$word\"", !note.contains(word))
        }
    }
}
