package com.messages.app.drive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V2-52 guard: the backup-health verdicts and the copy that renders them.
 *
 * A health feature has one failure mode that matters more than the rest — being
 * wrong in the reassuring direction. So what is pinned here is that every code
 * has a sentence, that the sentences never carry a format argument (nothing for
 * message content or key material to travel through), that the check stays
 * bounded and read-only, and that the unwrapped data key is not kept.
 *
 * The app module's unit tests are pure JVM on purpose — no Robolectric — so the
 * Drive round trip itself is not exercised. Its shape is pinned by reading the
 * source, which is the same technique the other app-module guards use.
 */
class BackupHealthCopyTest {

    private val source = File("src/main/kotlin/com/messages/app/drive/DriveBackup.kt").readText()
    private val strings = File("src/main/res/values/strings.xml").readText()

    private fun stringNamed(name: String): String? =
        Regex("""<string name="$name">(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .find(strings)?.groupValues?.get(1)

    @Test
    fun `every health code has copy, and the copy takes no arguments`() {
        // `when` with no else already makes the compiler enforce the first half
        // at build time; this catches the resource going missing, and the
        // no-arguments rule which the compiler cannot see.
        for (code in DriveBackup.HealthCode.entries) {
            assertTrue("no message for $code", BackupHealthCopy.message(code) != 0)
        }
        val names = Regex("""R\.string\.(drive_health_\w+)""")
            .findAll(File("src/main/kotlin/com/messages/app/drive/BackupHealthCopy.kt").readText())
            .map { it.groupValues[1] }
            .toList()
        assertEquals(
            "one string per code",
            DriveBackup.HealthCode.entries.size,
            names.distinct().size,
        )
        for (name in names) {
            val text = stringNamed(name)
            assertTrue("missing string $name", text != null)
            assertFalse(
                "$name must not take a format argument — nothing may be interpolated into a health line",
                Regex("""%\d*\$?[sd]""").containsMatchIn(text!!),
            )
        }
    }

    @Test
    fun `only real problems are coloured as errors`() {
        // UNREACHABLE says the check failed, not the backup. Red here would
        // teach people to ignore red.
        assertFalse(BackupHealthCopy.isAlarming(DriveBackup.HealthCode.UNREACHABLE))
        assertFalse(BackupHealthCopy.isAlarming(DriveBackup.HealthCode.OK))
        assertFalse(BackupHealthCopy.isAlarming(DriveBackup.HealthCode.STALE))
        // These two mean "the backup is fine, it just needs you" — not a fault.
        assertFalse(BackupHealthCopy.isAlarming(DriveBackup.HealthCode.NEEDS_USER_SECRET))
        assertFalse(BackupHealthCopy.isAlarming(DriveBackup.HealthCode.NEEDS_PASSWORD))
        assertTrue(BackupHealthCopy.isAlarming(DriveBackup.HealthCode.KEY_MISMATCH))
        assertTrue(BackupHealthCopy.isAlarming(DriveBackup.HealthCode.MIXED_CUSTODY))
        assertTrue(BackupHealthCopy.isAlarming(DriveBackup.HealthCode.NO_KEY))
    }

    @Test
    fun `restorable means restorable without further input`() {
        val ok = DriveBackup.Health(DriveBackup.HealthCode.OK, checkedAt = 1)
        assertTrue(ok.restorable)
        assertTrue(ok.copy(code = DriveBackup.HealthCode.STALE).restorable)
        // A snapshot that needs a code the user may no longer have is not a
        // backup this device can restore, however healthy it looks.
        assertFalse(ok.copy(code = DriveBackup.HealthCode.NEEDS_USER_SECRET).restorable)
        assertFalse(ok.copy(code = DriveBackup.HealthCode.UNREACHABLE).restorable)
    }

    @Test
    fun `the check stays bounded — it never downloads a payload`() {
        val body = source.substringAfter("private fun checkHealth(")
            .substringBefore("private fun isStale(")
        // Header probes only. `client.download(` is the full-object fetch used
        // by restore; if it ever appears here, a daily background check has
        // become a daily multi-hundred-megabyte download.
        assertFalse(
            "checkHealth must not download whole snapshots",
            body.contains("client.download("),
        )
        assertTrue("checkHealth must read headers via readSnapshot", body.contains("readSnapshot("))
    }

    @Test
    fun `the check is read-only and does not keep the data key`() {
        val section = source.substringAfter("// ---- Backup health (V2-52) ----")
            .substringBefore("// ---- Scheduling ----")
        for (mutation in listOf("client.upload(", "client.delete(", "cacheMasterKey(")) {
            assertFalse(
                "the health check must not $mutation — it may never change what it is checking",
                section.contains(mutation),
            )
        }
        assertTrue(
            "the unwrapped data key must be zeroed rather than retained",
            Regex("""unwrapWithMasterKey[\s\S]{0,400}?fill\(0\)""").containsMatchIn(section),
        )
    }

    @Test
    fun `automatic checks are rate limited and never fail the backup worker`() {
        assertTrue(
            "the automatic check must be capped at one a day",
            source.contains("HEALTH_MIN_INTERVAL_MS = 24 * 60 * 60 * 1000L"),
        )
        // The worker's return value decides retry/failure; the health call must
        // not be able to influence it.
        assertTrue(
            "verifyHealthIfDue must swallow its own failures",
            Regex("""runCatching \{ verifyBackupHealth\(context\) \}""").containsMatchIn(source),
        )
    }
}
