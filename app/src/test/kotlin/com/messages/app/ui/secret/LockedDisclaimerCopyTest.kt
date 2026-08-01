package com.messages.app.ui.secret

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V2-6 anti-decay guard for the sentence that tells the user what "locked"
 * actually means.
 *
 * The disclaimer is the only place the bargain is stated: bodies encrypted on
 * this device, the shared-storage copy deleted, the number and the timestamps
 * still readable, and — the part people are entitled to know BEFORE they lock a
 * chat — that a locked message then exists nowhere but this app. Every clause
 * is a claim about behaviour implemented elsewhere, so this test pins the copy
 * to the code that has to keep it true.
 *
 * The failure this prevents is one-directional and quiet. Code that stops
 * deleting the provider copy, or stops sealing, breaks a core-messaging test
 * loudly. Copy that overstates protection breaks nothing at all — it just
 * becomes a promise the app no longer keeps, on the one screen where the user
 * is deciding how much to trust it.
 */
class LockedDisclaimerCopyTest {

    private val strings = File("src/main/res/values/strings.xml")
    private val setup = File("src/main/kotlin/com/messages/app/ui/secret/SecretSetupScreen.kt")

    private fun stringValue(name: String): String {
        val m = Regex("""<string name="$name">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(strings.readText())
        assertTrue("missing string resource: $name", m != null)
        return m!!.groupValues[1]
    }

    private val point by lazy { stringValue("secret_disclaimer_point_sms_storage").lowercase() }

    @Test
    fun `the storage disclaimer states encryption, deletion, and the metadata residual`() {
        assertTrue("must say the words are encrypted", point.contains("encrypted"))
        assertTrue(
            "must say the copy in shared message storage is deleted",
            point.contains("deleted") && point.contains("storage"),
        )
        // LockedContent seals body/normalizedBody/lastMessage and nothing else:
        // address and timestamps stay clear because routing, contact lookup and
        // dedupe all join on them. Claiming otherwise would be the single most
        // consequential lie this screen could tell.
        assertTrue(
            "must not hide the metadata residual — who and when stay readable",
            point.contains("who") && point.contains("when"),
        )
    }

    @Test
    fun `the storage disclaimer warns that a locked message leaves no other copy`() {
        // purgeProviderCopies() is the half of V2-6 that encryption cannot
        // deliver, and it costs the user something real: no other SMS app will
        // ever show these messages, and they reach a new phone only through
        // this app's backup. That cost is disclosed here or nowhere.
        assertTrue(
            "must say locked messages live only in this app",
            point.contains("only in this app"),
        )
        assertTrue("must mention other SMS apps", point.contains("sms app"))
        assertTrue("must point at backup as the only way across devices", point.contains("backup"))
    }

    @Test
    fun `the disclaimer does not promise protection the design does not provide`() {
        // Sealing is at rest, under a key this device's Keystore holds. It is
        // not end-to-end, the carrier still sees the SMS, and code running as
        // this app on an unlocked phone can ask the Keystore to decrypt.
        for (phrase in listOf("end-to-end", "end to end", "nobody can", "no one can", "unbreakable")) {
            assertTrue(
                "the disclaimer must not claim \"$phrase\"",
                !point.contains(phrase),
            )
        }
    }

    @Test
    fun `the disclaimer is actually shown`() {
        val text = setup.readText()
        for (res in listOf(
            "secret_disclaimer_lead_sms_storage",
            "secret_disclaimer_point_sms_storage",
        )) {
            assertTrue("$res is declared but never rendered", text.contains("R.string.$res"))
        }
    }
}
