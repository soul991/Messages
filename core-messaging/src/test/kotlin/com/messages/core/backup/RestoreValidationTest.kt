package com.messages.core.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * R-11 and R-08 regressions for the restore path.
 *
 * R-11: dedupe used Java's 32-bit String.hashCode over the body, so two
 * different messages could collide and restore would silently DROP one while
 * reporting it as a duplicate.
 *
 * R-08: a backup file is untrusted input; structural limits and media-filename
 * safety must be enforced before anything is written.
 */
class RestoreValidationTest {

    private fun msg(
        address: String = "+10000000000",
        body: String = "hello",
        timestamp: Long = 1_700_000_000_000,
        isOutgoing: Boolean = false,
        mediaFileName: String? = null,
    ) = BackupManager.BackupMessage(
        address = address,
        body = body,
        timestamp = timestamp,
        isOutgoing = isOutgoing,
        read = true,
        category = "INBOX",
        dangerous = false,
        fraudWarning = false,
        // V2-10: "" was never a value a real export writes — the exporter emits
        // `verdict.protectedLabel.name`, and NONE is the absent case.
        protectedLabel = "NONE",
        score = 0,
        matchedPatternIds = "",
        matchedComboIds = "",
        explanations = "",
        starred = false,
        trashed = false,
        trashedAt = null,
        mediaFileName = mediaFileName,
        mediaMimeType = null,
    )

    private fun file(
        messages: List<BackupManager.BackupMessage> = emptyList(),
        media: Map<String, String> = emptyMap(),
    ) = BackupManager.BackupFile(
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
        messages = messages,
        media = media,
    )

    // ---- R-11: dedupe identity ----

    @Test
    fun `bodies that collide under String hashCode get distinct dedupe keys`() {
        // The classic 32-bit collision pair: "Aa".hashCode() == "BB".hashCode().
        assertEquals("Aa".hashCode(), "BB".hashCode())

        val a = BackupManager.messageKey("+10000000000", 1_700_000_000_000, false, "Aa")
        val b = BackupManager.messageKey("+10000000000", 1_700_000_000_000, false, "BB")
        assertNotEquals(a, b)
    }

    @Test
    fun `two colliding messages both survive a restore`() {
        val incoming = listOf(msg(body = "Aa"), msg(body = "BB"))
        val (toInsert, skipped) = BackupManager.dedupeForImport(HashSet(), incoming)

        // Before the fix one of these was dropped as a "duplicate".
        assertEquals(2, toInsert.size)
        assertEquals(0, skipped)
    }

    @Test
    fun `genuine duplicates are still skipped`() {
        val incoming = listOf(msg(body = "same"), msg(body = "same"))
        val (toInsert, skipped) = BackupManager.dedupeForImport(HashSet(), incoming)

        assertEquals(1, toInsert.size)
        assertEquals(1, skipped)
    }

    @Test
    fun `re-importing an already-present message is idempotent`() {
        val existing = hashSetOf(
            BackupManager.messageKey("+10000000000", 1_700_000_000_000, false, "hello")
        )
        val (toInsert, skipped) = BackupManager.dedupeForImport(existing, listOf(msg()))

        assertTrue(toInsert.isEmpty())
        assertEquals(1, skipped)
    }

    // ---- R-08: structural validation ----

    @Test
    fun `a well-formed backup validates`() {
        BackupManager.validate(file(messages = listOf(msg())))
    }

    @Test
    fun `a path-traversal media name is refused`() {
        val hostile = listOf("../evil.png", "../../etc/passwd", "sub/dir.png", "a\\b.png", ".", "..")
        for (name in hostile) {
            assertThrows(
                "should reject media name: $name",
                BackupManager.MalformedBackupException::class.java,
            ) {
                BackupManager.validate(file(media = mapOf(name to "AAAA")))
            }
        }
    }

    @Test
    fun `a traversal name on a message is refused`() {
        assertThrows(BackupManager.MalformedBackupException::class.java) {
            BackupManager.validate(file(messages = listOf(msg(mediaFileName = "../escape.jpg"))))
        }
    }

    @Test
    fun `an ordinary media name is accepted`() {
        BackupManager.validate(file(media = mapOf("42_photo.jpg" to "AAAA")))
    }

    @Test
    fun `an over-long message body is refused`() {
        val huge = "x".repeat(BackupManager.Limits.MAX_FIELD_CHARS + 1)
        assertThrows(BackupManager.MalformedBackupException::class.java) {
            BackupManager.validate(file(messages = listOf(msg(body = huge))))
        }
    }

    @Test
    fun `an over-long address is refused`() {
        val huge = "9".repeat(BackupManager.Limits.MAX_ADDRESS_CHARS + 1)
        assertThrows(BackupManager.MalformedBackupException::class.java) {
            BackupManager.validate(file(messages = listOf(msg(address = huge))))
        }
    }

    @Test
    fun `a negative timestamp is refused`() {
        assertThrows(BackupManager.MalformedBackupException::class.java) {
            BackupManager.validate(file(messages = listOf(msg(timestamp = -1))))
        }
    }

    @Test
    fun `too many media files are refused`() {
        val many = (0..BackupManager.Limits.MAX_MEDIA_FILES).associate { "f$it.jpg" to "AA" }
        assertThrows(BackupManager.MalformedBackupException::class.java) {
            BackupManager.validate(file(media = many))
        }
    }
}
