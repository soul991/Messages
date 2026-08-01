package com.messages.app.net

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V2-17 guard.
 *
 * The network policy used to be whatever the targetSdk defaulted to. It is now
 * written down, which only helps if it stays written down — a
 * `cleartextTrafficPermitted="true"` added to unblock one endpoint, or a
 * `<certificates src="user" />` copied out of the debug file, would undo it
 * silently and no build would fail.
 *
 * So this reads the two config files and the manifest as text and holds the
 * three properties that matter: cleartext is denied everywhere, production
 * trusts only the system CA store, and the debug overlay differs from
 * production in the trust anchors and nothing else.
 *
 * Text, not XML parsing, on purpose — a comment that *says* cleartext is denied
 * while an attribute permits it should still fail, and the assertions below are
 * about the attributes.
 */
class NetworkSecurityConfigTest {

    private val production = File("src/main/res/xml/network_security_config.xml")
    private val debug = File("src/debug/res/xml/network_security_config.xml")
    private val manifest = File("src/main/AndroidManifest.xml")

    @Test
    fun `the files this test reads exist`() {
        // Every assertion below is vacuous against a missing file.
        for (file in listOf(production, debug, manifest)) {
            assertTrue("expected ${file.absolutePath}", file.isFile)
        }
    }

    @Test
    fun `the manifest points at the config`() {
        val text = manifest.readText()
        assertTrue(
            "application must set android:networkSecurityConfig",
            text.contains("android:networkSecurityConfig=\"@xml/network_security_config\""),
        )
        assertTrue(
            "application must set android:usesCleartextTraffic=\"false\"",
            text.contains("android:usesCleartextTraffic=\"false\""),
        )
        assertFalse(
            "usesCleartextTraffic was set to true",
            text.contains("android:usesCleartextTraffic=\"true\""),
        )
    }

    @Test
    fun `neither build permits cleartext`() {
        // Including the debug one: a policy that is looser in debug hides the
        // failures you want to catch before shipping.
        for (file in listOf(production, debug)) {
            val attributes = attributeValues(file, "cleartextTrafficPermitted")
            assertEquals(
                "${file.name} should declare cleartextTrafficPermitted exactly once",
                1, attributes.size,
            )
            assertEquals("${file.name} permits cleartext", "false", attributes.single())
        }
    }

    @Test
    fun `production trusts the system store only`() {
        // A user-installed CA is how TLS interception is set up, and a
        // messaging app is worth intercepting.
        assertEquals(listOf("system"), trustAnchors(production))
    }

    @Test
    fun `debug adds the user store and changes nothing else`() {
        assertEquals(listOf("system", "user"), trustAnchors(debug))

        // The two files must be the same policy apart from that one line, so
        // an edit to production cannot leave debug behind — or vice versa.
        val normalise = { file: File ->
            stripComments(file.readText())
                // `tools:` attributes are build-time metadata, not policy.
                .replace(Regex("""\s*(xmlns:tools|tools:\w+)="[^"]*""""), "")
                .lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .filterNot { it.contains("<certificates") }
        }
        assertEquals(
            "debug and production differ by more than the trust anchors",
            normalise(production), normalise(debug),
        )
    }

    @Test
    fun `no domain carves an exception out of the base policy`() {
        // A <domain-config> can re-permit cleartext or add trust anchors for
        // one host. There is no such host: everything in-process is HTTPS by
        // construction, and MMS does not go through this process at all.
        for (file in listOf(production, debug)) {
            assertFalse(
                "${file.name} has a <domain-config>; if one is genuinely needed, " +
                    "widen this test deliberately rather than deleting it",
                stripComments(file.readText()).contains("<domain-config"),
            )
        }
    }

    /** Every `src=` on a `<certificates>` element, in document order. */
    private fun trustAnchors(file: File): List<String> =
        Regex("""<certificates\s+src="([^"]+)"""")
            .findAll(stripComments(file.readText()))
            .map { it.groupValues[1] }
            .toList()

    private fun attributeValues(file: File, name: String): List<String> =
        Regex("""$name="([^"]*)"""")
            .findAll(stripComments(file.readText()))
            .map { it.groupValues[1] }
            .toList()

    /** These files are mostly rationale; the assertions are about the markup. */
    private fun stripComments(xml: String): String =
        xml.replace(Regex("""<!--.*?-->""", RegexOption.DOT_MATCHES_ALL), "")
}
