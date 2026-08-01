package com.messages.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2-12. The structural ceilings in [BackupManager.Limits] were written to be
 * unreachable by a real backup, which meant they never fired before the heap
 * did: a file of 128 million characters carrying 2 GB of media satisfies every
 * one of them, and then kills the process. These tests pin the device-derived
 * budget that decides instead, and the per-file media bound that stops one
 * attachment from being the whole aggregate in a single allocation.
 */
class RestoreBudgetTest {

    private val mb = 1024L * 1024

    // ---- Derivation ----

    @Test
    fun `a typical phone heap yields a ceiling far below the structural one`() {
        val budget = RestoreBudget.of(maxHeapBytes = 256 * mb, usableDiskBytes = 32L * 1024 * mb)
        assertEquals((256 * mb / 24).toInt(), budget.maxJsonChars)
        assertTrue(budget.maxJsonChars < BackupManager.Limits.MAX_JSON_CHARS)
        // The number that matters: this is what actually gets restored, and it
        // is ~11 MB of text, not 128 MB.
        assertEquals(10, budget.jsonMegabytes())
    }

    @Test
    fun `a bigger heap earns a proportionally bigger ceiling`() {
        val small = RestoreBudget.of(256 * mb, 32L * 1024 * mb)
        val large = RestoreBudget.of(512 * mb, 32L * 1024 * mb)
        // Integer division loses at most one character on the way.
        assertTrue(large.maxJsonChars - small.maxJsonChars * 2 in 0..1)
    }

    @Test
    fun `a tiny heap still permits an ordinary text history`() {
        // A restore must not become impossible on a low-memory device — the
        // floor is what a few tens of thousands of plain messages need.
        val budget = RestoreBudget.of(maxHeapBytes = 32 * mb, usableDiskBytes = 32L * 1024 * mb)
        assertEquals(RestoreBudget.MIN_JSON_CHARS, budget.maxJsonChars)
    }

    @Test
    fun `an enormous heap is still capped by the structural ceiling`() {
        val budget = RestoreBudget.of(maxHeapBytes = 64L * 1024 * mb, usableDiskBytes = Long.MAX_VALUE)
        assertEquals(BackupManager.Limits.MAX_JSON_CHARS, budget.maxJsonChars)
    }

    @Test
    fun `media is bounded by free disk, not by the 2 GB structural figure`() {
        // 400 MB free: a 2 GB restore would fail partway through with the
        // device half-written, so it is refused up front.
        val budget = RestoreBudget.of(256 * mb, usableDiskBytes = 400 * mb)
        assertEquals(200 * mb, budget.maxMediaBytes)
        assertTrue(budget.maxMediaBytes < BackupManager.Limits.MAX_MEDIA_BYTES)
    }

    @Test
    fun `an unreported free-space figure falls back rather than refusing everything`() {
        val budget = RestoreBudget.of(256 * mb, usableDiskBytes = -1)
        assertEquals(BackupManager.Limits.MAX_MEDIA_BYTES, budget.maxMediaBytes)
        // The per-file bound is unaffected — it is what keeps a single decode
        // bounded, and it never depends on the filesystem answering.
        assertEquals(RestoreBudget.MAX_MEDIA_FILE_BYTES, budget.maxMediaFileBytes)
    }

    @Test
    fun `the expansion ceiling tracks the text ceiling`() {
        val budget = RestoreBudget.of(256 * mb, 32L * 1024 * mb)
        assertEquals(budget.maxJsonChars, budget.maxExpandedBytes)
    }

    // ---- Enforcement ----

    private fun msg() = BackupManager.BackupMessage(
        address = "+10000000000",
        body = "hello",
        timestamp = 1_700_000_000_000,
        isOutgoing = false,
        read = true,
        category = "INBOX",
        dangerous = false,
        fraudWarning = false,
        protectedLabel = "NONE",
        score = 0,
        matchedPatternIds = "",
        matchedComboIds = "",
        explanations = "",
        starred = false,
        trashed = false,
        trashedAt = null,
        mediaFileName = "a.jpg",
        mediaMimeType = "image/jpeg",
    )

    private fun file(media: Map<String, String>) = BackupManager.BackupFile(
        formatVersion = 1,
        exportedAtMillis = 1_700_000_000_000,
        sensitivity = "DEFAULT",
        otpAutoDelete = false,
        hidePreviews = false,
        patternLibraryVersion = 1,
        importedPatternPack = null,
        rules = emptyList(),
        reputations = emptyList(),
        conversationPrefs = emptyList(),
        messages = listOf(msg()),
        media = media,
    )

    /** A Base64 field whose DECLARED decoded size is [bytes], without allocating it. */
    private fun b64Declaring(bytes: Long): String = "A".repeat((bytes / 3L * 4L).toInt())

    @Test
    fun `one attachment larger than the per-file ceiling is refused`() {
        val huge = b64Declaring(RestoreBudget.MAX_MEDIA_FILE_BYTES + 4 * mb)
        val e = assertThrows(BackupManager.MalformedBackupException::class.java) {
            BackupManager.validate(file(mapOf("a.jpg" to huge)))
        }
        assertTrue(e.message!!.contains("a.jpg"))
    }

    @Test
    fun `media that fits the structural limit but not this device's disk is refused`() {
        // Six 4 MB attachments: comfortably inside MAX_MEDIA_BYTES, and
        // comfortably outside a device with 16 MB of usable space.
        val media = (1..6).associate { "f$it.jpg" to b64Declaring(4 * mb) }
        val budget = RestoreBudget.of(256 * mb, usableDiskBytes = 16 * mb)
        assertThrows(BackupManager.MalformedBackupException::class.java) {
            BackupManager.validate(file(media), budget)
        }
        // The same file is accepted where there is room for it.
        BackupManager.validate(file(media), RestoreBudget.of(256 * mb, 32L * 1024 * mb))
    }

    @Test
    fun `an attachment at the per-file ceiling is still accepted`() {
        // The bound must not be so tight that ordinary MMS attachments — which
        // export at up to 5 MB — stop restoring.
        BackupManager.validate(file(mapOf("a.jpg" to b64Declaring(5 * mb))))
    }
}
