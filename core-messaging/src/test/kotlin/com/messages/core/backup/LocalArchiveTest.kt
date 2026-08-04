package com.messages.core.backup

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V2-49 guard: the local archive is encrypted, and it is verified before it is
 * called a backup.
 *
 * The envelope probe is exercised for real. The rest is pinned by reading the
 * source, because everything else in [LocalArchive] needs a `Context`, a
 * `ContentResolver` and a populated database — and the properties that matter
 * here are structural ("no plaintext write path exists") rather than
 * behavioural, so source is the honest place to check them. `BackupRoundTripTest`
 * already covers the payload itself.
 */
class LocalArchiveTest {

    private val source =
        File("src/main/kotlin/com/messages/core/backup/LocalArchive.kt").readText()

    @Test
    fun `the envelope probe recognises an envelope and nothing else`() {
        val dataKey = BackupCrypto.newDataKey()
        val blob = BackupCrypto.seal(
            payloadJson = """{"messages":[]}""",
            dataKey = dataKey,
            wrappedKeys = listOf(BackupCrypto.wrapWithMasterKey(dataKey, BackupCrypto.newMasterKey())),
            createdAt = 1_700_000_000_000,
            checkpointAt = 1_700_000_000_000,
            deviceModel = "TestDevice",
            messageCount = 0,
        )
        assertTrue(BackupCrypto.looksLikeEnvelope(blob))
        assertTrue(BackupCrypto.looksLikeEnvelope(blob.copyOf(BackupCrypto.MAGIC_BYTES)))

        // A plain JSON backup — the legacy format import still has to accept.
        assertFalse(BackupCrypto.looksLikeEnvelope("""{"version":1,"me""".toByteArray()))
        // Truncated below the magic: unknowable, so not claimed as an envelope.
        assertFalse(BackupCrypto.looksLikeEnvelope("MBK".toByteArray()))
        assertFalse(BackupCrypto.looksLikeEnvelope(ByteArray(0)))
    }

    @Test
    fun `export has no plaintext path`() {
        val export = source.substringAfter("suspend fun export(")
            .substringBefore("private fun verifyWritten(")
        // The only thing written to the chosen URI is the sealed blob. If a
        // branch ever writes `payload` (or anything else) to the output stream,
        // the export has silently become the plaintext export this finding
        // retired.
        val writes = Regex("""out\.write\(([A-Za-z]+)""").findAll(export)
            .map { it.groupValues[1] }.toSet()
        assertTrue("export must write only the sealed blob, wrote $writes", writes == setOf("blob"))
        assertTrue("the payload must be sealed", export.contains("BackupCrypto.seal("))
        assertTrue("the data key must be zeroed", export.contains("dataKey.fill(0)"))
        // Nothing may be staged on disk on the way out.
        for (staging in listOf("cacheDir", "createTempFile", "File(")) {
            assertFalse("export must not stage anything on disk ($staging)", export.contains(staging))
        }
    }

    @Test
    fun `success is reported only after the written document is read back`() {
        val export = source.substringAfter("suspend fun export(")
            .substringBefore("private fun verifyWritten(")
        // Order matters: verification has to sit between the write and the
        // summary, or "saved" is a claim about a return value rather than about
        // a file.
        val verifyAt = export.indexOf("verifyWritten(")
        val summaryAt = export.indexOf("ExportSummary(bytesWritten")
        assertTrue("export must verify what it wrote", verifyAt > 0)
        assertTrue("verification must precede success", verifyAt < summaryAt)

        val verify = source.substringAfter("private fun verifyWritten(")
            .substringBefore("fun isEncrypted(")
        // Re-read through the URI, not from the in-memory blob: what is being
        // checked is what the provider actually stored.
        assertTrue("verification must re-read the URI", verify.contains("BoundedRead.readUri(context, uri"))
        assertTrue("verification must decrypt", verify.contains("BackupCrypto.openWithPassword"))
        assertTrue(
            "digests must be compared in constant time",
            verify.contains("MessageDigest.isEqual("),
        )
        // A failed verification is a failed export, not a warning.
        assertFalse("a failed verification must not be logged and swallowed", verify.contains("Log.w"))
        assertTrue(verify.contains("throw VerificationFailed"))
    }

    @Test
    fun `reads are bounded on both sides`() {
        val importer = source.substringAfter("suspend fun import(")
        assertTrue(
            "the import read must be bounded by the device budget",
            importer.contains("RestoreBudget.forDevice(context)") &&
                importer.contains("BoundedRead.readUri(context, uri, budget.maxExpandedBytes)"),
        )
        assertFalse(
            "the import must never read the whole file unbounded",
            importer.contains("readBytes()") || importer.contains("readText()"),
        )
    }

    @Test
    fun `import still accepts plain JSON`() {
        val importer = source.substringAfter("suspend fun import(")
        // The asymmetry is deliberate: export is encrypted always, import keeps
        // reading the files earlier versions wrote. Losing this branch would
        // strand every archive already on a user's disk.
        assertTrue(importer.contains("BackupManager.readBackupText(context, uri)"))
    }

    @Test
    fun `a suggested name carries the envelope extension`() {
        val name = LocalArchive.suggestedName("2026-01-31")
        assertTrue(name.endsWith(".${LocalArchive.EXTENSION}"))
        assertTrue(name.contains("2026-01-31"))
        // Not a JSON claim: the file is ciphertext and saying otherwise invites
        // other apps to open it and show the user a parse error.
        assertFalse(name.endsWith(".json"))
        assertNull(Regex("""\.json""").find(LocalArchive.MIME_TYPE))
    }
}
