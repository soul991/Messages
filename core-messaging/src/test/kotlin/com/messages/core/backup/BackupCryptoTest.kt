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
        // R-09: newly sealed envelopes are format version 2 (authenticated
        // header). Version 1 stays readable on restore but is never produced.
        assertEquals(BackupCrypto.FORMAT_VERSION, header.formatVersion)
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

    @Test
    fun `readHeader works on a truncated prefix of the blob`() {
        // The snapshot chooser probes only the first few KB via a Range
        // request — the plaintext header must parse from that prefix alone.
        val blob = sealRoundTrip("""{"messages":[{"body":"large payload elided"}]}""", "pw")
        val headerLen = java.nio.ByteBuffer.wrap(blob, 4, 4).int
        val prefix = blob.copyOfRange(0, minOf(blob.size, 8 + headerLen + 16))
        val header = BackupCrypto.readHeader(prefix)
        assertEquals(BackupCrypto.readHeader(blob).messageCount, header.messageCount)
        assertEquals(BackupCrypto.readHeader(blob).createdAt, header.createdAt)
    }

    // ---- account-plain (WhatsApp-style, key file in Drive appDataFolder) ----

    private fun sealAccountPlain(payload: String, masterKey: ByteArray): ByteArray {
        val dataKey = BackupCrypto.newDataKey()
        return BackupCrypto.seal(
            payloadJson = payload,
            dataKey = dataKey,
            wrappedKeys = listOf(BackupCrypto.wrapWithMasterKey(dataKey, masterKey)),
            createdAt = 1_700_000_000_000,
            checkpointAt = 1_699_999_000_000,
            deviceModel = "TestDevice",
            messageCount = 7,
        )
    }

    @Test
    fun `account-plain seal then open with master key round-trips without any password`() {
        val masterKey = BackupCrypto.newMasterKey()
        val payload = """{"messages":[{"body":"no password needed"}]}"""
        val blob = sealAccountPlain(payload, masterKey)
        assertEquals(payload, BackupCrypto.openWithMasterKey(blob, masterKey))
    }

    @Test
    fun `wrong master key is rejected`() {
        val blob = sealAccountPlain("""{"x":1}""", BackupCrypto.newMasterKey())
        assertThrows(BackupCrypto.WrongMasterKeyException::class.java) {
            BackupCrypto.openWithMasterKey(blob, BackupCrypto.newMasterKey())
        }
    }

    @Test
    fun `account-plain envelope does not require a password`() {
        val blob = sealAccountPlain("""{"x":1}""", BackupCrypto.newMasterKey())
        val header = BackupCrypto.readHeader(blob)
        assertEquals(BackupCrypto.METHOD_ACCOUNT, header.wrappedKeys.single().method)
        assertTrue(!BackupCrypto.requiresPassword(header))
    }

    @Test
    fun `legacy password-only envelope is detected as requiring a password`() {
        val blob = sealRoundTrip("""{"x":1}""", "old password")
        assertTrue(BackupCrypto.requiresPassword(BackupCrypto.readHeader(blob)))
        // And it still opens with that password (backward compatibility).
        assertEquals("""{"x":1}""", BackupCrypto.openWithPassword(blob, "old password".toCharArray()))
    }

    @Test
    fun `envelope carrying both wrap methods opens with either`() {
        val masterKey = BackupCrypto.newMasterKey()
        val dataKey = BackupCrypto.newDataKey()
        val payload = """{"both":"methods"}"""
        val blob = BackupCrypto.seal(
            payloadJson = payload,
            dataKey = dataKey,
            wrappedKeys = listOf(
                BackupCrypto.wrapWithMasterKey(dataKey, masterKey),
                BackupCrypto.wrapWithPassword(dataKey, "belt and braces".toCharArray()),
            ),
            createdAt = 1L, checkpointAt = 1L, deviceModel = "T", messageCount = 1,
        )
        assertTrue(!BackupCrypto.requiresPassword(BackupCrypto.readHeader(blob)))
        assertEquals(payload, BackupCrypto.openWithMasterKey(blob, masterKey))
        assertEquals(payload, BackupCrypto.openWithPassword(blob, "belt and braces".toCharArray()))
    }
}

/** §6/§8.3 restore idempotency: the dedupe core `import()` runs on. */
class RestoreDedupeTest {

    private fun msg(
        address: String = "AX-BANKXX-S",
        body: String = "Your a/c was credited",
        timestamp: Long = 1_700_000_000_000,
        outgoing: Boolean = false,
    ) = BackupManager.BackupMessage(
        address = address, body = body, timestamp = timestamp, isOutgoing = outgoing,
        read = true, category = "TRANSACTIONS", dangerous = false, fraudWarning = false,
        protectedLabel = "BANK", score = 0, matchedPatternIds = "", matchedComboIds = "",
        explanations = "", starred = false,
    )

    private val backup = listOf(
        msg(),
        msg(address = "+919812345678", body = "hi", timestamp = 1_700_000_100_000),
        msg(body = "OTP is 482913", timestamp = 1_700_000_200_000, outgoing = false),
    )

    private fun keysOf(messages: List<BackupManager.BackupMessage>): MutableSet<String> =
        messages.mapTo(HashSet()) {
            BackupManager.messageKey(it.address, it.timestamp, it.isOutgoing, it.body)
        }

    @Test
    fun `first restore into an empty device inserts everything`() {
        val (toInsert, skipped) = BackupManager.dedupeForImport(HashSet(), backup)
        assertEquals(backup, toInsert)
        assertEquals(0, skipped)
    }

    @Test
    fun `double restore inserts zero rows`() {
        // First restore lands all 3 messages on the device…
        val deviceKeys = keysOf(backup)
        // …then the SAME backup is restored again.
        val (toInsert, skipped) = BackupManager.dedupeForImport(deviceKeys, backup)
        assertEquals(emptyList<BackupManager.BackupMessage>(), toInsert)
        assertEquals(backup.size, skipped)
    }

    @Test
    fun `duplicates inside one backup are inserted only once`() {
        val (toInsert, skipped) = BackupManager.dedupeForImport(HashSet(), backup + backup)
        assertEquals(backup, toInsert)
        assertEquals(backup.size, skipped)
    }

    @Test
    fun `dedupe key distinguishes address timestamp direction and body`() {
        val base = msg()
        val variants = listOf(
            base.copy(address = "VM-OTHER"),
            base.copy(timestamp = base.timestamp + 1),
            base.copy(isOutgoing = true),
            base.copy(body = base.body + "!"),
        )
        val (toInsert, skipped) = BackupManager.dedupeForImport(keysOf(listOf(base)), variants)
        assertEquals(variants, toInsert)
        assertEquals(0, skipped)
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
