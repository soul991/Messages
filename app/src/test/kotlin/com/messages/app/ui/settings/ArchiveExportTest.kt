package com.messages.app.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V2-49 guard: the Settings screen can no longer write a plaintext backup.
 *
 * This is the half of the finding that lives in the UI layer, and it is worth
 * pinning separately from [com.messages.core.backup.LocalArchive]'s own guard:
 * the crypto being available means nothing if a launcher still routes around
 * it. The app module's tests are pure JVM (no Robolectric), so the wiring is
 * checked by reading the source — the same technique the other app-module
 * guards use.
 */
class ArchiveExportTest {

    private val source =
        File("src/main/kotlin/com/messages/app/ui/settings/SettingsScreen.kt").readText()
    private val strings = File("src/main/res/values/strings.xml").readText()

    @Test
    fun `the export path goes through the encrypted archive only`() {
        assertTrue("export must call LocalArchive", source.contains("LocalArchive.export(app, uri, password)"))
        // BackupManager.export returns the payload as plain JSON. If the UI
        // calls it directly again, the plaintext export is back.
        assertFalse(
            "Settings must not export plain JSON",
            source.contains("BackupManager.export("),
        )
        assertFalse(
            "Settings must not write to a URI itself",
            source.contains("openOutputStream("),
        )
    }

    @Test
    fun `the file is not offered as JSON`() {
        assertTrue(
            "CreateDocument must declare the envelope's type",
            source.contains("ActivityResultContracts.CreateDocument(LocalArchive.MIME_TYPE)"),
        )
        assertTrue(
            "the suggested name must come from LocalArchive",
            source.contains("LocalArchive.suggestedName(stamp)"),
        )
    }

    @Test
    fun `a new export password is screened, a restore password is not`() {
        val dialog = source.substringAfter("private fun ArchivePasswordDialog(")
        // Screening at setup is the app's existing policy (V2-7). Screening on
        // import would refuse to open a file the user legitimately owns, which
        // is the one thing a strength rule must never do — so the check is
        // gated on `exporting`.
        assertTrue(
            "a new password must be screened",
            Regex("""exporting &&[\s\S]{0,120}SecretStrength\.isWeakPassword""")
                .containsMatchIn(dialog),
        )
        assertTrue(
            "a new password must be typed twice",
            dialog.contains("password == repeated"),
        )
        assertTrue(
            "restore must only require a non-empty password",
            Regex("""else \{\s*password\.isNotEmpty\(\)\s*\}""").containsMatchIn(dialog),
        )
    }

    @Test
    fun `passwords are zeroed once handed over`() {
        // The CharArray is the only representation this code can clear; the
        // ViewModel is its last owner, so the zeroing belongs in a `finally`
        // there rather than at a call site that a failure can skip.
        assertTrue(
            "the export password must be zeroed even when the export throws",
            Regex("""finally \{\s*password\.fill\('\\u0000'\)\s*\}""").containsMatchIn(source),
        )
        assertTrue(
            "the import password must be zeroed even when the import throws",
            Regex("""finally \{\s*password\?\.fill\('\\u0000'\)\s*\}""").containsMatchIn(source),
        )
        // A String cannot be zeroed at all, so nothing may keep one.
        assertFalse(
            "the password must not be stored as a String outside the text field",
            source.contains("val password: String"),
        )
    }

    @Test
    fun `a plain file is never asked for a password`() {
        val begin = source.substringAfter("fun beginImport(uri: Uri)")
            .substringBefore("fun exportBackup(")
        assertTrue(begin.contains("LocalArchive.isEncrypted(app, uri)"))
        assertTrue("an unreadable file is reported, not prompted", begin.contains("null ->"))
        assertTrue("a plain file imports straight away", begin.contains("false -> importBackup(uri, null)"))
    }

    @Test
    fun `a wrong password is distinguished from a broken file`() {
        assertTrue(
            "wrong-password must be its own message",
            source.contains("is BackupCrypto.WrongPasswordException ->"),
        )
        assertTrue(strings.contains("""name="settings_archive_wrong_password""""))
        // The user is told, before they commit, that the password is the only
        // way back in.
        assertTrue(strings.contains("""name="settings_archive_no_recovery""""))
        assertTrue(
            "the subtitle must not still describe a plain local file",
            Regex("""settings_backup_subtitle">[^<]*encrypted""").containsMatchIn(strings),
        )
    }

    @Test
    fun `every string the dialog names exists`() {
        val dialog = source.substringAfter("private fun ArchivePasswordDialog(")
        val names = Regex("""R\.string\.(settings_archive_\w+)""").findAll(dialog)
            .map { it.groupValues[1] }.toSet()
        assertTrue("the dialog should reference its copy by name", names.size >= 8)
        for (name in names) {
            assertTrue("missing string $name", strings.contains("""name="$name""""))
        }
    }
}
