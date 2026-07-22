package com.messages.app.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DriveBackupProgressTest {

    @Test
    fun `zero or negative total is indeterminate`() {
        assertNull(DriveBackup.BackupProgress(DriveBackup.BackupStage.ENCRYPTING).fraction)
        assertNull(
            DriveBackup.BackupProgress(DriveBackup.BackupStage.PREPARING, done = 5, total = 0).fraction,
        )
    }

    @Test
    fun `fraction is the done over total ratio`() {
        val progress = DriveBackup.BackupProgress(DriveBackup.BackupStage.UPLOADING, done = 25, total = 100)
        assertEquals(0.25f, progress.fraction!!, 0.0001f)
    }

    @Test
    fun `fraction is coerced into 0 to 1 even if done overshoots total`() {
        val progress = DriveBackup.BackupProgress(DriveBackup.BackupStage.UPLOADING, done = 150, total = 100)
        assertEquals(1f, progress.fraction!!, 0.0001f)
    }

    // ---- restore progress (download % → decrypt → import) ----

    @Test
    fun `restore progress with unknown content length is indeterminate`() {
        assertNull(DriveBackup.RestoreProgress(DriveBackup.RestoreStage.DECRYPTING).fraction)
        assertNull(
            DriveBackup.RestoreProgress(
                DriveBackup.RestoreStage.DOWNLOADING, done = 4096, total = -1,
            ).fraction,
        )
    }

    @Test
    fun `restore download fraction and clamp`() {
        assertEquals(
            0.5f,
            DriveBackup.RestoreProgress(
                DriveBackup.RestoreStage.DOWNLOADING, done = 50, total = 100,
            ).fraction!!,
            0.0001f,
        )
        assertEquals(
            1f,
            DriveBackup.RestoreProgress(
                DriveBackup.RestoreStage.DOWNLOADING, done = 999, total = 100,
            ).fraction!!,
            0.0001f,
        )
    }
}
