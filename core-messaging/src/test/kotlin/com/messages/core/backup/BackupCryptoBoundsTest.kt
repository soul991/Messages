package com.messages.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R-09 regression: an envelope is attacker-supplied (a file picked in the
 * restore chooser, or a tampered Drive object), so BackupCrypto must enforce
 * every structural bound BEFORE doing the work that bound protects.
 *
 * Pure JVM — BackupCrypto touches no Android APIs.
 */
class BackupCryptoBoundsTest {

    private val dataKey = BackupCrypto.newDataKey()

    private fun seal(payload: String = """{"messages":[]}"""): ByteArray =
        BackupCrypto.seal(
            payloadJson = payload,
            dataKey = dataKey,
            wrappedKeys = listOf(BackupCrypto.wrapWithPassword(dataKey, "pw".toCharArray())),
            createdAt = 1_700_000_000_000,
            checkpointAt = 1_699_999_000_000,
            deviceModel = "TestDevice",
            messageCount = 0,
        )

    /** Rebuild a blob with a mutated header, preserving the length-prefix shape. */
    private fun withHeaderJson(blob: ByteArray, transform: (String) -> String): ByteArray {
        val headerLen = ((blob[4].toInt() and 0xff) shl 24) or
            ((blob[5].toInt() and 0xff) shl 16) or
            ((blob[6].toInt() and 0xff) shl 8) or
            (blob[7].toInt() and 0xff)
        val header = String(blob, 8, headerLen, Charsets.UTF_8)
        val newHeader = transform(header).toByteArray(Charsets.UTF_8)
        val out = java.io.ByteArrayOutputStream()
        out.write(blob, 0, 4)
        out.write(
            byteArrayOf(
                (newHeader.size ushr 24).toByte(), (newHeader.size ushr 16).toByte(),
                (newHeader.size ushr 8).toByte(), newHeader.size.toByte(),
            )
        )
        out.write(newHeader)
        out.write(blob, 8 + headerLen, blob.size - 8 - headerLen)
        return out.toByteArray()
    }

    @Test
    fun `a sealed envelope round-trips`() {
        val blob = seal("""{"hello":"world"}""")
        assertEquals(
            """{"hello":"world"}""",
            BackupCrypto.openWithPassword(blob, "pw".toCharArray()),
        )
    }

    @Test
    fun `new envelopes authenticate the header as AAD`() {
        val blob = seal()
        // Tampering with plaintext metadata must break the GCM tag, not be
        // silently trusted (R-09).
        val tampered = withHeaderJson(blob) {
            it.replace("\"messageCount\":0", "\"messageCount\":9999")
        }
        assertThrows(Exception::class.java) {
            BackupCrypto.openWithPassword(tampered, "pw".toCharArray())
        }
    }

    @Test
    fun `an absurd iteration count is refused before any PBKDF2 work`() {
        val blob = seal()
        val hostile = withHeaderJson(blob) {
            it.replace(
                "\"iterations\":${BackupCrypto.PBKDF2_ITERATIONS}",
                "\"iterations\":2000000000",
            )
        }
        val e = assertThrows(BackupCrypto.MalformedBackupException::class.java) {
            BackupCrypto.readHeader(hostile)
        }
        assertTrue(e.message!!.contains("PBKDF2"))
    }

    @Test
    fun `an unsupported format version is refused`() {
        val blob = seal()
        val hostile = withHeaderJson(blob) {
            it.replace("\"formatVersion\":2", "\"formatVersion\":99")
        }
        assertThrows(BackupCrypto.MalformedBackupException::class.java) {
            BackupCrypto.readHeader(hostile)
        }
    }

    @Test
    fun `a bogus magic or truncated blob is refused`() {
        assertThrows(BackupCrypto.MalformedBackupException::class.java) {
            BackupCrypto.readHeader(ByteArray(4))
        }
        assertThrows(BackupCrypto.MalformedBackupException::class.java) {
            BackupCrypto.readHeader("NOPE1234567890".toByteArray())
        }
    }

    @Test
    fun `a header length beyond the blob is refused`() {
        val hostile = seal().copyOf()
        // Claim a header far larger than the envelope.
        hostile[4] = 0x7f
        hostile[5] = 0xff.toByte()
        hostile[6] = 0xff.toByte()
        hostile[7] = 0xff.toByte()
        assertThrows(BackupCrypto.MalformedBackupException::class.java) {
            BackupCrypto.readHeader(hostile)
        }
    }

    @Test
    fun `an empty unlock-method list is refused`() {
        val blob = seal()
        val hostile = withHeaderJson(blob) {
            it.replace(Regex("\"wrappedKeys\":\\[.*\\]"), "\"wrappedKeys\":[]")
        }
        assertThrows(BackupCrypto.MalformedBackupException::class.java) {
            BackupCrypto.readHeader(hostile)
        }
    }

    @Test
    fun `a gzip bomb is stopped at the expansion limit`() {
        // 8 MB of zeroes compresses to a few KB. Driving the bounded reader
        // directly with a small limit proves the guard without allocating a
        // realistic bomb (which would OOM the test JVM before reaching it).
        val gz = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(gz).use { it.write(ByteArray(8 * 1024 * 1024)) }
        val compressed = gz.toByteArray()
        assertTrue("fixture should be highly compressible", compressed.size < 64 * 1024)

        val e = assertThrows(BackupCrypto.MalformedBackupException::class.java) {
            BackupCrypto.gunzipBounded(compressed, limit = 1024 * 1024)
        }
        assertTrue(e.message!!.contains("expands"))
    }

    @Test
    fun `decompression within the limit still succeeds`() {
        val payload = "x".repeat(64 * 1024).toByteArray()
        val gz = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(gz).use { it.write(payload) }

        val out = BackupCrypto.gunzipBounded(gz.toByteArray(), limit = 1024 * 1024)
        assertEquals(payload.size, out.size)
    }
}
