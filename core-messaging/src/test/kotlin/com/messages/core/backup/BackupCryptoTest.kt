package com.messages.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class BackupCryptoTest {

    // Tiny iteration count for test speed; production uses PBKDF2_ITERATIONS.
    private fun sealRoundTrip(payload: String, password: String): ByteArray {
        val dataKey = BackupCrypto.newDataKey()
        val wrap = BackupCrypto.wrapWithPassword(dataKey, password.toCharArray())
        return BackupCrypto.seal(
            payloadJson = payload,
            dataKey = dataKey,
            wrappedKeys = listOf(wrap),
            createdAt = 1_700_000_000_000,
            checkpointAt = 1_699_999_000_000,
            deviceModel = "TestDevice",
            messageCount = 42,
        )
    }

    @Test
    fun `seal then open with correct password round-trips`() {
        val payload = """{"messages":[{"body":"hello"}]}"""
        val blob = sealRoundTrip(payload, "correct horse battery staple")
        val out = BackupCrypto.openWithPassword(blob, "correct horse battery staple".toCharArray())
        assertEquals(payload, out)
    }

    @Test
    fun `wrong password is rejected`() {
        val blob = sealRoundTrip("""{"x":1}""", "right")
        assertThrows(BackupCrypto.WrongPasswordException::class.java) {
            BackupCrypto.openWithPassword(blob, "wrong".toCharArray())
        }
    }

    @Test
    fun `header is readable without any password`() {
        val blob = sealRoundTrip("""{"x":1}""", "pw")
        val header = BackupCrypto.readHeader(blob)
        assertEquals(1, header.formatVersion)
        assertEquals(1_700_000_000_000, header.createdAt)
        assertEquals(1_699_999_000_000, header.checkpointAt)
        assertEquals("TestDevice", header.deviceModel)
        assertEquals(42, header.messageCount)
        assertEquals("password", header.wrappedKeys.single().method)
        assertEquals(BackupCrypto.PBKDF2_ITERATIONS, header.wrappedKeys.single().iterations)
    }

    @Test
    fun `payload is not stored in plaintext`() {
        val blob = sealRoundTrip("""{"secret":"NOLEAK_MARKER"}""", "pw")
        assertTrue(String(blob, Charsets.ISO_8859_1).indexOf("NOLEAK_MARKER") == -1)
    }
}

class CheckpointsTest {

    private val utc = TimeZone.getTimeZone("UTC")

    private fun at(y: Int, mo: Int, d: Int, h: Int, min: Int = 0): Long =
        Calendar.getInstance(utc).run {
            clear(); set(y, mo - 1, d, h, min, 0); timeInMillis
        }

    @Test
    fun `daily checkpoint is this morning after 6am`() {
        assertEquals(
            at(2026, 7, 17, 6),
            Checkpoints.lastCheckpoint(at(2026, 7, 17, 14), Checkpoints.Frequency.DAILY, utc),
        )
    }

    @Test
    fun `daily checkpoint is yesterday before 6am`() {
        assertEquals(
            at(2026, 7, 16, 6),
            Checkpoints.lastCheckpoint(at(2026, 7, 17, 5, 59), Checkpoints.Frequency.DAILY, utc),
        )
    }

    @Test
    fun `weekly checkpoint is most recent monday 6am`() {
        // 2026-07-17 is a Friday; the previous Monday is 2026-07-13.
        assertEquals(
            at(2026, 7, 13, 6),
            Checkpoints.lastCheckpoint(at(2026, 7, 17, 14), Checkpoints.Frequency.WEEKLY, utc),
        )
        // Monday before 6am → previous week's Monday.
        assertEquals(
            at(2026, 7, 6, 6),
            Checkpoints.lastCheckpoint(at(2026, 7, 13, 5), Checkpoints.Frequency.WEEKLY, utc),
        )
    }

    @Test
    fun `monthly checkpoint is the first of the month 6am`() {
        assertEquals(
            at(2026, 7, 1, 6),
            Checkpoints.lastCheckpoint(at(2026, 7, 17, 14), Checkpoints.Frequency.MONTHLY, utc),
        )
        // 1st before 6am → previous month.
        assertEquals(
            at(2026, 6, 1, 6),
            Checkpoints.lastCheckpoint(at(2026, 7, 1, 3), Checkpoints.Frequency.MONTHLY, utc),
        )
    }

    @Test
    fun `manual checkpoint is now`() {
        val now = at(2026, 7, 17, 14, 30)
        assertEquals(now, Checkpoints.lastCheckpoint(now, Checkpoints.Frequency.MANUAL, utc))
    }
}
