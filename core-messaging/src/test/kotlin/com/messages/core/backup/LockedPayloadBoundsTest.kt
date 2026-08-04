package com.messages.core.backup

import org.junit.Assert.assertThrows
import org.junit.Test

/**
 * V2-11 regression guard: the credential-encrypted locked payload must clear
 * the SAME hostile-input bounds as the outer backup file.
 *
 * The bug this locks down: `validate()` was only ever applied to [BackupFile].
 * The locked sub-envelope was decrypted and applied directly, so a restore
 * could carry an inner payload with unbounded message counts, oversized bodies,
 * or unsafe media filenames while the outer file passed validation cleanly.
 *
 * Decryption authenticates the *author* of a payload. It says nothing about
 * whether the payload is well-formed, and a backup file can come from anywhere.
 */
class LockedPayloadBoundsTest {

    private fun msg(
        address: String = "+10000000000",
        body: String = "hello",
        timestamp: Long = 1_700_000_000_000,
        mediaFileName: String? = null,
    ) = BackupManager.BackupMessage(
        address = address,
        body = body,
        timestamp = timestamp,
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
        mediaFileName = mediaFileName,
    )

    private fun payload(
        messages: List<BackupManager.BackupMessage> = emptyList(),
        media: Map<String, String> = emptyMap(),
        lockedAddresses: List<String> = emptyList(),
    ) = BackupManager.LockedPayload(
        messages = messages,
        conversationPrefs = emptyList(),
        lockedAddresses = lockedAddresses,
        media = media,
    )

    @Test
    fun `a well-formed locked payload is accepted`() {
        BackupManager.validateLocked(payload(messages = listOf(msg())))
    }

    @Test
    fun `oversized message body is rejected`() {
        val huge = "x".repeat(BackupManager.Limits.MAX_FIELD_CHARS + 1)
        assertThrows(BackupManager.MalformedBackupException::class.java) {
            BackupManager.validateLocked(payload(messages = listOf(msg(body = huge))))
        }
    }

    @Test
    fun `oversized address is rejected`() {
        val huge = "9".repeat(BackupManager.Limits.MAX_ADDRESS_CHARS + 1)
        assertThrows(BackupManager.MalformedBackupException::class.java) {
            BackupManager.validateLocked(payload(messages = listOf(msg(address = huge))))
        }
    }

    @Test
    fun `negative timestamp is rejected`() {
        assertThrows(BackupManager.MalformedBackupException::class.java) {
            BackupManager.validateLocked(payload(messages = listOf(msg(timestamp = -1))))
        }
    }

    @Test
    fun `traversal in a locked media file name is rejected`() {
        assertThrows(BackupManager.MalformedBackupException::class.java) {
            BackupManager.validateLocked(payload(media = mapOf("../../evil.bin" to "AAAA")))
        }
    }

    @Test
    fun `separator in a locked media file name is rejected`() {
        assertThrows(BackupManager.MalformedBackupException::class.java) {
            BackupManager.validateLocked(payload(media = mapOf("a/b.bin" to "AAAA")))
        }
    }

    @Test
    fun `media file name referenced by a locked message is checked too`() {
        assertThrows(BackupManager.MalformedBackupException::class.java) {
            BackupManager.validateLocked(
                payload(messages = listOf(msg(mediaFileName = "../escape.png"))),
            )
        }
    }

    @Test
    fun `too many locked media files is rejected`() {
        val many = (0..BackupManager.Limits.MAX_MEDIA_FILES)
            .associate { "f$it.bin" to "AAAA" }
        assertThrows(BackupManager.MalformedBackupException::class.java) {
            BackupManager.validateLocked(payload(media = many))
        }
    }

    @Test
    fun `oversized locked address list entry is rejected`() {
        val huge = "9".repeat(BackupManager.Limits.MAX_ADDRESS_CHARS + 1)
        assertThrows(BackupManager.MalformedBackupException::class.java) {
            BackupManager.validateLocked(payload(lockedAddresses = listOf(huge)))
        }
    }
}
