package com.messages.core.backup

import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2-10: a backup can be structurally perfect and still carry values the app
 * has no meaning for — an unknown category, a rule targeting nothing, a regex
 * that would stall intake.
 *
 * The old failure mode was not a crash but a *silent downgrade*:
 * `Category.valueOf` threw at classify time, the caller caught it, and the
 * message went to Inbox — so one bad row in a restored file switched
 * classification off for everything. The rule here is that the file is refused
 * before the first write, with a reason a person can act on.
 */
class RestoreSemanticsTest {

    private fun msg(
        category: String = "INBOX",
        protectedLabel: String = "NONE",
        score: Int = 0,
        mediaMimeType: String? = null,
        trashedAt: Long? = null,
    ) = BackupManager.BackupMessage(
        address = "+10000000000",
        body = "hello",
        timestamp = 1_700_000_000_000,
        isOutgoing = false,
        read = true,
        category = category,
        dangerous = false,
        fraudWarning = false,
        protectedLabel = protectedLabel,
        score = score,
        matchedPatternIds = "",
        matchedComboIds = "",
        explanations = "",
        starred = false,
        trashedAt = trashedAt,
        mediaMimeType = mediaMimeType,
    )

    private fun file(
        sensitivity: String = "DEFAULT",
        patternLibraryVersion: Int = 1,
        rules: List<BackupManager.BackupRule> = emptyList(),
        reputations: List<BackupManager.BackupReputation> = emptyList(),
        messages: List<BackupManager.BackupMessage> = emptyList(),
    ) = BackupManager.BackupFile(
        formatVersion = 1,
        exportedAtMillis = 1_700_000_000_000,
        sensitivity = sensitivity,
        otpAutoDelete = false,
        hidePreviews = false,
        patternLibraryVersion = patternLibraryVersion,
        importedPatternPack = null,
        rules = rules,
        reputations = reputations,
        conversationPrefs = emptyList(),
        messages = messages,
    )

    private fun rule(
        kind: String = "CUSTOM",
        target: String = "SENDER",
        pattern: String = "SPAMCO",
        category: String = "SPAM",
        position: Int = 0,
    ) = BackupManager.BackupRule(position, kind, target, pattern, category)

    private fun rejects(expectedFragment: String, backup: BackupManager.BackupFile) {
        val e = assertThrows(BackupManager.MalformedBackupException::class.java) {
            BackupManager.validate(backup)
        }
        assertTrue(
            "message should say what was wrong, got: ${e.message}",
            e.message.orEmpty().contains(expectedFragment, ignoreCase = true),
        )
    }

    // ---- the shape a real export produces still passes ----

    @Test
    fun `a well-formed backup validates`() {
        BackupManager.validate(
            file(
                rules = listOf(rule(), rule(kind = "BLOCK", position = 1)),
                reputations = listOf(BackupManager.BackupReputation("+10000000000", -3, 1, 0)),
                messages = listOf(
                    msg(),
                    msg(category = "TRANSACTIONS", protectedLabel = "OTP", score = 4),
                    msg(mediaMimeType = "image/jpeg"),
                    msg(mediaMimeType = "text/plain; charset=utf-8"),
                ),
            )
        )
    }

    // ---- message vocabularies ----

    @Test
    fun `an unknown message category is refused`() {
        rejects("category", file(messages = listOf(msg(category = "MY_OWN_FOLDER"))))
        rejects("category", file(messages = listOf(msg(category = "inbox"))))
        rejects("category", file(messages = listOf(msg(category = ""))))
    }

    @Test
    fun `an unknown protected label is refused`() {
        rejects("label", file(messages = listOf(msg(protectedLabel = "Bank"))))
        rejects("label", file(messages = listOf(msg(protectedLabel = ""))))
    }

    @Test
    fun `an out-of-range score is refused`() {
        rejects("score", file(messages = listOf(msg(score = Int.MAX_VALUE))))
        rejects("score", file(messages = listOf(msg(score = Int.MIN_VALUE))))
    }

    @Test
    fun `a negative trash timestamp is refused`() {
        rejects("trash", file(messages = listOf(msg(trashedAt = -1))))
    }

    @Test
    fun `a media type that is not a mime type is refused`() {
        // The value reaches an Intent type and the attachment label.
        rejects("media type", file(messages = listOf(msg(mediaMimeType = "not a mime"))))
        rejects("media type", file(messages = listOf(msg(mediaMimeType = "image"))))
        rejects("media type", file(messages = listOf(msg(mediaMimeType = "a/" + "x".repeat(300)))))
    }

    // ---- settings ----

    @Test
    fun `an unknown sensitivity is refused rather than silently defaulted`() {
        // currentSensitivity() falls through to DEFAULT for anything it does not
        // recognise, so without this check the slider would read as DEFAULT
        // forever with no indication anything was dropped.
        rejects("sensitivity", file(sensitivity = "PARANOID"))
        rejects("sensitivity", file(sensitivity = "default"))
    }

    @Test
    fun `a negative pattern library version is refused`() {
        rejects("pattern version", file(patternLibraryVersion = -1))
    }

    // ---- rules: the same bar as a rule typed into Settings ----

    @Test
    fun `an unknown rule kind or target is refused`() {
        rejects("kind", file(rules = listOf(rule(kind = "DELETE"))))
        rejects("target", file(rules = listOf(rule(target = "BODY"))))
    }

    @Test
    fun `an unknown rule category is refused`() {
        rejects("category", file(rules = listOf(rule(category = "ARCHIVE"))))
    }

    @Test
    fun `an empty or overlong rule pattern is refused`() {
        rejects("empty", file(rules = listOf(rule(pattern = "   "))))
        rejects("longer than", file(rules = listOf(rule(pattern = "a".repeat(9_999)))))
    }

    @Test
    fun `a catastrophically backtracking rule pattern is refused`() {
        // This is the whole point of validating rules on restore: a shared
        // backup must not be able to install a pattern that stalls every
        // future message on the intake path.
        rejects("Rule 1", file(rules = listOf(rule(pattern = "(a+)+$"))))
    }

    @Test
    fun `a plain-text rule that does not compile as a regex is still accepted`() {
        // matchesRule falls back to a literal comparison, which is how a rule
        // like "+9198…" works at all — refusing these would break real backups.
        BackupManager.validate(file(rules = listOf(rule(pattern = "+919812345678"))))
    }

    @Test
    fun `the failure names which rule was bad`() {
        rejects(
            "Rule 2",
            file(rules = listOf(rule(), rule(kind = "NOPE", position = 1))),
        )
    }

    // ---- reputations ----

    @Test
    fun `out-of-range reputation values are refused`() {
        rejects(
            "score is out of range",
            file(reputations = listOf(BackupManager.BackupReputation("+1", Int.MAX_VALUE, 0, 0))),
        )
        rejects(
            "counts are out of range",
            file(reputations = listOf(BackupManager.BackupReputation("+1", 0, -1, 0))),
        )
        rejects(
            "empty address",
            file(reputations = listOf(BackupManager.BackupReputation("  ", 0, 0, 0))),
        )
    }
}
