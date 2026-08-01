package com.messages.app.drive

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V2-5 / V2-46 anti-decay guard.
 *
 * Two kinds of fact are pinned here, and neither can be caught by the compiler.
 *
 * The first is a *behaviour* that lives in one statement: enabling user-held
 * custody deletes the plain key file from the appDataFolder. Sealing the master
 * key while a plaintext copy stays in the same folder closes nothing at all, so
 * a refactor that drops that one line would leave a feature that reports
 * success, passes every round-trip test, and protects nothing.
 *
 * The second is *copy*. The recovery code is the only secret in this app that
 * genuinely cannot be reset, and the whole design depends on the user believing
 * that at the moment they are shown it. Hedged wording ("if you lose it,
 * contact support") would be worse than no wording.
 */
class KeyCustodyCopyTest {

    private val strings = File("src/main/res/values/strings.xml").readText()
    private val backup = File("src/main/kotlin/com/messages/app/drive/DriveBackup.kt").readText()
    private val ui = File("src/main/kotlin/com/messages/app/ui/drivebackup/DriveKeyProtection.kt").readText()

    private fun stringValue(name: String): String {
        val m = Regex("""<string name="$name">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(strings)
        assertTrue("missing string resource: $name", m != null)
        return m!!.groupValues[1]
    }

    @Test
    fun `enabling protection deletes the plain key file`() {
        val body = backup.substringAfter("suspend fun enableUserHeldKey")
            .substringBefore("suspend fun changeUserHeldSecret")
        assertTrue(
            "enableUserHeldKey must delete the plain key file — otherwise the vault protects nothing",
            Regex("""findByName\(KEY_FILE_NAME\)\s*\.forEach\s*\{[^}]*delete""").containsMatchIn(body),
        )
        assertTrue(
            "the vault must be published (and therefore verified) before the plain key goes",
            body.indexOf("publishVault") < body.indexOf("KEY_FILE_NAME"),
        )
    }

    @Test
    fun `a new vault is proven readable before the old one is dropped`() {
        // V2-46: "verify recovery before enabling scheduled backups". A vault
        // Drive holds but nothing can open strands every future backup behind
        // ensureMasterKey's mixed-custody guard.
        val body = backup.substringAfter("private fun publishVault").substringBefore("suspend fun enableUserHeldKey")
        assertTrue("publishVault must re-download what Drive stored", body.contains("client.download(newId"))
        assertTrue("publishVault must test-open the stored bytes", body.contains("MasterKeyVault.open(stored"))
        assertTrue(
            "a vault that fails verification must be deleted again",
            body.contains("onFailure") && body.contains("client.delete(newId)"),
        )
        assertTrue(
            "superseded vaults may only go after verification succeeds",
            body.indexOf("MasterKeyVault.open(stored") < body.lastIndexOf("superseded.forEach"),
        )
    }

    @Test
    fun `disabling puts the key back before the vault is removed`() {
        // The reverse order would leave the master key in the local Keystore
        // cache alone — one factory reset from unrecoverable.
        val body = backup.substringAfter("suspend fun disableUserHeldKey")
            .substringBefore("suspend fun unlockUserHeldKey")
        assertTrue(
            "the plain key must be uploaded and verified before the vault is deleted",
            body.indexOf("drive_error_corrupt_key") < body.indexOf("findByName(VAULT_FILE_NAME)"),
        )
    }

    @Test
    fun `the recovery code is presented as unrecoverable`() {
        val body = stringValue("drive_recovery_code_body").lowercase()
        assertTrue("the code screen must say it is the only way in", body.contains("only way"))
        assertTrue("the code screen must name who cannot recover it", body.contains("no one"))
        for (hedge in listOf("support", "we can", "reset it for", "recover it for")) {
            assertTrue("the code screen must not imply recovery: found \"$hedge\"", !body.contains(hedge))
        }
        assertTrue(
            "the acknowledgement must be an action the user takes, not a passive OK",
            stringValue("drive_recovery_code_saved").lowercase().contains("written it down"),
        )
    }

    @Test
    fun `both custody modes state their actual cost`() {
        // Account mode is a legitimate default, not the "insecure" option — but
        // the thing it trades away has to be on screen next to it.
        val account = stringValue("drive_key_custody_account_subtitle").lowercase()
        assertTrue("account mode must say account access is backup access", account.contains("account"))
        assertTrue("account mode must say what an intruder reaches", account.contains("anyone who"))

        val userHeld = stringValue("drive_key_custody_user_held_subtitle").lowercase()
        assertTrue(
            "user-held mode must say the account alone is not enough",
            userHeld.contains("account alone cannot"),
        )
    }

    @Test
    fun `the password option states the offline-guessing caveat`() {
        // Same honesty as V2-7: with no server there is no OPRF-backed rate
        // limit, so a password here is attackable at PBKDF2 speed by anyone
        // holding a backup file. Copy that skipped this would sell the weaker
        // option as equivalent to the stronger one.
        val note = stringValue("drive_key_method_password_note").lowercase()
        assertTrue("the password note must mention guessing", note.contains("guess"))
        assertTrue("the password note must mention offline attack", note.contains("offline"))
        assertTrue(
            "the password note must be shown where the method is chosen",
            ui.contains("R.string.drive_key_method_password_note"),
        )
    }

    @Test
    fun `neither mode is described as leaving backups unencrypted`() {
        // Both modes are AES-256-GCM. Copy implying otherwise would be a lie
        // that also makes the default look negligent.
        val note = stringValue("drive_key_protection_note").lowercase()
        assertTrue("the section note must say backups are always encrypted", note.contains("always encrypted"))
        assertFalse(
            "the section note must not imply one mode is unencrypted",
            note.contains("unencrypted") || note.contains("not encrypted"),
        )
    }

    @Test
    fun `every string the custody UI asks for exists`() {
        val declared = Regex("""<string name="(\w+)"""").findAll(strings).map { it.groupValues[1] }.toSet()
        val used = Regex("""R\.string\.(\w+)""").findAll(ui).map { it.groupValues[1] }.toSet() +
            Regex("""R\.string\.(drive_\w+)""").findAll(backup).map { it.groupValues[1] }.toSet()
        for (name in used) {
            assertTrue("no string resource for R.string.$name", name in declared)
        }
    }
}
