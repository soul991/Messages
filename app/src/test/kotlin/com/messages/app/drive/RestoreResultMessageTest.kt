package com.messages.app.drive

import com.messages.core.backup.BackupCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Restore-outcome UI states + the legacy-password prompt decision. */
class RestoreResultMessageTest {

    @Test
    fun `re-restore with nothing new shows the nothing-to-restore state`() {
        assertEquals(
            "Nothing to restore — all messages are already on this device",
            DriveBackup.restoreResultMessage(restored = 0, skipped = 1204),
        )
    }

    @Test
    fun `fresh restore reports the real inserted count`() {
        assertEquals(
            "Restored 4569 new messages",
            DriveBackup.restoreResultMessage(restored = 4569, skipped = 0),
        )
        // Partial overlap still reports only what was actually added.
        assertEquals(
            "Restored 12 new messages",
            DriveBackup.restoreResultMessage(restored = 12, skipped = 300),
        )
        assertEquals("Restored 1 new message", DriveBackup.restoreResultMessage(1, 0))
    }

    @Test
    fun `empty backup is reported as such, not as already-restored`() {
        assertEquals(
            "Backup contained no messages",
            DriveBackup.restoreResultMessage(restored = 0, skipped = 0),
        )
    }

    private fun header(vararg methods: String) = BackupCrypto.Header(
        formatVersion = 1, createdAt = 1L, checkpointAt = 1L, nonce = "",
        wrappedKeys = methods.map { BackupCrypto.WrappedKey(it, "", 0, "", "") },
    )

    @Test
    fun `legacy password-wrapped snapshot prompts, account-plain does not`() {
        assertTrue(
            DriveBackup.RemoteSnapshot("id", "n.mbk", 1, header(BackupCrypto.METHOD_PASSWORD))
                .needsPassword,
        )
        assertFalse(
            DriveBackup.RemoteSnapshot("id", "n.mbk", 1, header(BackupCrypto.METHOD_ACCOUNT))
                .needsPassword,
        )
        // A snapshot carrying both methods restores without input.
        assertFalse(
            DriveBackup.RemoteSnapshot(
                "id", "n.mbk", 1,
                header(BackupCrypto.METHOD_PASSWORD, BackupCrypto.METHOD_ACCOUNT),
            ).needsPassword,
        )
    }
}
