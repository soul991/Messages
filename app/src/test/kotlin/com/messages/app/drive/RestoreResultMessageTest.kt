package com.messages.app.drive

import com.messages.app.drive.DriveBackup.LockedNote
import com.messages.app.drive.DriveBackup.RestoreOutcome
import com.messages.core.backup.BackupCrypto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Restore-outcome UI states + the legacy-password prompt decision.
 *
 * V2-36. These assert on the outcome [DriveBackup.restoreOutcome] picks, not on
 * the sentence it produces — the sentences live in the resource table now, and
 * the thing that must not regress is which of them applies. A re-restore that
 * adds nothing has to read as "already here", never as an empty backup.
 */
class RestoreResultMessageTest {

    @Test
    fun `re-restore with nothing new shows the nothing-to-restore state`() {
        assertEquals(
            RestoreOutcome.NOTHING_NEW to LockedNote.NONE,
            DriveBackup.restoreOutcome(restored = 0, skipped = 1204),
        )
    }

    @Test
    fun `fresh restore is the counted state, whatever the overlap`() {
        assertEquals(
            RestoreOutcome.RESTORED to LockedNote.NONE,
            DriveBackup.restoreOutcome(restored = 4569, skipped = 0),
        )
        // Partial overlap still reports only what was actually added.
        assertEquals(
            RestoreOutcome.RESTORED to LockedNote.NONE,
            DriveBackup.restoreOutcome(restored = 12, skipped = 300),
        )
        assertEquals(
            RestoreOutcome.RESTORED to LockedNote.NONE,
            DriveBackup.restoreOutcome(1, 0),
        )
    }

    @Test
    fun `empty backup is reported as such, not as already-restored`() {
        assertEquals(
            RestoreOutcome.EMPTY_BACKUP to LockedNote.NONE,
            DriveBackup.restoreOutcome(restored = 0, skipped = 0),
        )
    }

    @Test
    fun `pending locked chats surface the opaque enter-your-code state`() {
        assertEquals(
            RestoreOutcome.RESTORED to LockedNote.PENDING,
            DriveBackup.restoreOutcome(restored = 120, skipped = 0, lockedPending = true),
        )
        // A locked-only backup (all normal rows deduped) still explains itself,
        // and must not fall through to the empty-backup wording.
        assertEquals(
            RestoreOutcome.LOCKED_ONLY to LockedNote.PENDING,
            DriveBackup.restoreOutcome(restored = 0, skipped = 0, lockedPending = true),
        )
    }

    @Test
    fun `same-credential restore reports locked chats placed silently`() {
        assertEquals(
            RestoreOutcome.RESTORED to LockedNote.RESTORED,
            DriveBackup.restoreOutcome(
                restored = 10, skipped = 2, lockedPending = false, lockedRestored = 4,
            ),
        )
        // Pending outranks restored: if anything is still locked, that is what
        // the user needs told, and the count of what landed stays out of it.
        assertEquals(
            LockedNote.PENDING,
            DriveBackup.restoreOutcome(
                restored = 10, skipped = 0, lockedPending = true, lockedRestored = 4,
            ).second,
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
