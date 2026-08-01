package com.messages.app.drive

import androidx.annotation.StringRes
import com.messages.app.R

/**
 * V2-52: which sentence a [DriveBackup.HealthCode] shows.
 *
 * Kept out of the Compose file and off the `when`-inside-a-composable path for
 * two reasons. The mapping is the part with a right and a wrong answer — a code
 * that silently renders as a blank line is a health screen that reassures the
 * user about a broken backup — so it is worth testing directly, and a plain
 * `when` over an enum with no `else` branch makes the compiler refuse to build
 * if a future code is added without copy.
 *
 * Every string names a next step rather than a diagnosis, and none of them can
 * contain message content or key material: they are static resources with no
 * format arguments, so there is nothing for a leak to travel through.
 */
object BackupHealthCopy {

    @StringRes
    fun message(code: DriveBackup.HealthCode): Int = when (code) {
        DriveBackup.HealthCode.OK -> R.string.drive_health_ok
        DriveBackup.HealthCode.STALE -> R.string.drive_health_stale
        DriveBackup.HealthCode.NOT_SIGNED_IN -> R.string.drive_health_not_signed_in
        DriveBackup.HealthCode.NEEDS_REAUTH -> R.string.drive_health_needs_reauth
        DriveBackup.HealthCode.NO_SNAPSHOTS -> R.string.drive_health_no_snapshots
        DriveBackup.HealthCode.UNREADABLE_SNAPSHOT -> R.string.drive_health_unreadable
        DriveBackup.HealthCode.NEEDS_USER_SECRET -> R.string.drive_health_needs_user_secret
        DriveBackup.HealthCode.NEEDS_PASSWORD -> R.string.drive_health_needs_password
        DriveBackup.HealthCode.NO_KEY -> R.string.drive_health_no_key
        DriveBackup.HealthCode.KEY_MISMATCH -> R.string.drive_health_key_mismatch
        DriveBackup.HealthCode.MIXED_CUSTODY -> R.string.drive_health_mixed_custody
        DriveBackup.HealthCode.MULTIPLE_KEYS -> R.string.drive_health_multiple_keys
        DriveBackup.HealthCode.UNREACHABLE -> R.string.drive_health_unreachable
    }

    /**
     * Whether the line should be drawn in the error colour.
     *
     * [DriveBackup.HealthCode.UNREACHABLE] is deliberately not an error: it
     * says the *check* failed, not the backup, and colouring it red would train
     * people to ignore the one colour that has to keep meaning something. The
     * two "we need something from you" codes are not errors either — the backup
     * is fine, it just is not openable unattended.
     */
    fun isAlarming(code: DriveBackup.HealthCode): Boolean = when (code) {
        DriveBackup.HealthCode.OK,
        DriveBackup.HealthCode.STALE,
        DriveBackup.HealthCode.NEEDS_USER_SECRET,
        DriveBackup.HealthCode.NEEDS_PASSWORD,
        DriveBackup.HealthCode.UNREACHABLE,
        -> false

        DriveBackup.HealthCode.NOT_SIGNED_IN,
        DriveBackup.HealthCode.NEEDS_REAUTH,
        DriveBackup.HealthCode.NO_SNAPSHOTS,
        DriveBackup.HealthCode.UNREADABLE_SNAPSHOT,
        DriveBackup.HealthCode.NO_KEY,
        DriveBackup.HealthCode.KEY_MISMATCH,
        DriveBackup.HealthCode.MIXED_CUSTODY,
        DriveBackup.HealthCode.MULTIPLE_KEYS,
        -> true
    }
}
