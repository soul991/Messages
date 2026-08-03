package com.messages.core.backup

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import com.messages.core.MessageRepository
import com.messages.core.cleanup.OtpCleanup
import com.messages.core.db.ConversationEntity
import com.messages.core.db.MessageEntity
import com.messages.core.db.SenderReputationEntity
import com.messages.core.db.Spaces
import com.messages.core.db.UserRuleEntity
import com.messages.core.media.MediaRef
import com.messages.core.secret.LockedContent
import com.messages.core.secret.LockedWriteBlockedException
import com.messages.core.secret.SecretSpace
import com.messages.protection.Category
import com.messages.protection.ProtectedLabel
import com.messages.protection.SafeRegexPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Local backup/restore (§8.2): one JSON document with messages (the local
 * index, including categories/labels/explanations), settings, user rules,
 * sender reputations, per-conversation prefs, and any imported pattern pack.
 * Restore is additive and idempotent — existing messages are never
 * overwritten or deleted (§6), duplicates are skipped.
 */
object BackupManager {

    const val FORMAT_VERSION = 1

    /** §8.3 spam-backup mode: everything, nothing, or a hand-picked set. */
    enum class SpamMode { ON, OFF, CUSTOM }

    /**
     * Export shaping (§8.3). Defaults reproduce the full local backup (§8.2):
     * everything, spam included, no media blobs.
     */
    data class ExportOptions(
        /** Checkpoint cut: only messages with timestamp <= upTo. Null = all. */
        val upTo: Long? = null,
        val spamMode: SpamMode = SpamMode.ON,
        /** Message ids to keep when [spamMode] is CUSTOM. */
        val customSpamIds: Set<Long> = emptySet(),
        /** Bundle MMS media files into the backup (separate toggle, §8.3). */
        val includeMedia: Boolean = false,
    )

    @Serializable
    data class BackupMessage(
        val address: String,
        val body: String,
        val timestamp: Long,
        val isOutgoing: Boolean,
        val read: Boolean,
        val category: String,
        val dangerous: Boolean,
        val fraudWarning: Boolean,
        val protectedLabel: String,
        val score: Int,
        val matchedPatternIds: String,
        val matchedComboIds: String,
        val explanations: String,
        val starred: Boolean,
        /** §6.4/§8.3: trash items travel in backups *as trash*, with purge clock intact. */
        val trashed: Boolean = false,
        val trashedAt: Long? = null,
        /** §8.3 media toggle: name of this message's blob in [BackupFile.media]. */
        val mediaFileName: String? = null,
        val mediaMimeType: String? = null,
    )

    @Serializable
    data class BackupConversationPrefs(
        val address: String,
        val pinned: Boolean,
        val archived: Boolean,
        val muted: Boolean,
        val locked: Boolean,
    )

    @Serializable
    data class BackupRule(
        val position: Int,
        val kind: String,
        val target: String,
        val pattern: String,
        val category: String,
    )

    @Serializable
    data class BackupReputation(
        val address: String,
        val score: Int,
        val userMarkedSpamCount: Int,
        val userMarkedNotSpamCount: Int,
    )

    @Serializable
    data class BackupFile(
        val formatVersion: Int,
        val exportedAtMillis: Long,
        /** "settings" prefs worth carrying across devices. */
        val sensitivity: String,
        val otpAutoDelete: Boolean,
        val hidePreviews: Boolean,
        val patternLibraryVersion: Int,
        /** Full imported pattern pack, when one is active (§7.5). */
        val importedPatternPack: String? = null,
        val rules: List<BackupRule>,
        val reputations: List<BackupReputation>,
        val conversationPrefs: List<BackupConversationPrefs>,
        val messages: List<BackupMessage>,
        /** §8.3 media toggle: fileName → base64 file bytes. Empty when off. */
        val media: Map<String, String> = emptyMap(),
        /**
         * Secret locked space: the locked chats travel as a separately-
         * encrypted sub-envelope (base64 of a [BackupCrypto] blob whose data
         * key is wrapped under the locked-space credential's PBKDF2 KEK).
         * Undecryptable without the original secret code — someone with mere
         * account access restores the normal chats but gets only an opaque
         * "locked chats present" state.
         */
        val lockedEnvelope: String? = null,
        /** Serialized [SecretSpace.PendingAuth] — the credential's verifier +
         *  salts travel with the backup so a fresh device can run the prompt. */
        val lockedAuth: String? = null,
    )

    /** Plaintext payload INSIDE the locked sub-envelope. */
    @Serializable
    data class LockedPayload(
        val messages: List<BackupMessage>,
        val conversationPrefs: List<BackupConversationPrefs> = emptyList(),
        /** Every locked conversation's address — recreates the routing rule
         *  even for a "New locked chat" that has no messages yet. */
        val lockedAddresses: List<String> = emptyList(),
        /**
         * R-12: locked MMS attachments, Base64 by file name, carried INSIDE the
         * credential-encrypted sub-envelope. Previously locked rows were
         * serialized with a null media name and no map at all, so users were
         * told their locked chats were backed up while every attachment was
         * silently dropped. Defaulted for backwards compatibility with
         * envelopes written before this field existed.
         */
        val media: Map<String, String> = emptyMap(),
    )

    data class ImportStats(
        val messagesRestored: Int,
        val messagesSkipped: Int,
        val rulesRestored: Int,
        val reputationsRestored: Int,
        /** Locked chats imported straight into the locked space (same credential). */
        val lockedRestored: Int = 0,
        /** A locked envelope is waiting for its secret code to be entered. */
        val lockedPending: Boolean = false,
    )

    // internal so serializer round-trip tests use the exact production config.
    internal val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun export(
        context: Context,
        options: ExportOptions = ExportOptions(),
        onMessageProgress: ((done: Int, total: Int) -> Unit)? = null,
    ): String = withContext(Dispatchers.IO) {
        val repo = MessageRepository.get(context)
        val db = repo.db
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        val importedPack = java.io.File(context.filesDir, "patterns_imported.json")
            .takeIf { it.exists() }?.readText()

        val (lockedRows, normalRows) = db.messages().allMessages()
            // Never export an unsent scheduled draft as if it were history.
            .filter { it.sendStatus != "SCHEDULED" }
            // §8.3 checkpoint cut: deterministic content per checkpoint.
            .filter { options.upTo == null || it.timestamp <= options.upTo }
            // Locked-space rows leave the plaintext payload entirely — they
            // only ever travel inside the credential-encrypted sub-envelope.
            .partition { it.space == Spaces.LOCKED }
        val included = normalRows
            // §8.3 spam-backup mode (Spam folder only — Blocked always travels).
            .filter {
                it.category != "SPAM" || when (options.spamMode) {
                    SpamMode.ON -> true
                    SpamMode.OFF -> false
                    SpamMode.CUSTOM -> it.id in options.customSpamIds
                }
            }

        // §8.3 media toggle: bundle each included message's media file.
        val media = LinkedHashMap<String, String>()
        val mediaNames = HashMap<Long, String>()
        if (options.includeMedia) {
            included.forEach { msg ->
                val ref = msg.mediaUri ?: return@forEach
                val (fileName, bytes) = readMedia(context, ref) ?: return@forEach
                val name = "${msg.id}_$fileName"
                media[name] = java.util.Base64.getEncoder().encodeToString(bytes)
                mediaNames[msg.id] = name
            }
        }

        val backup = BackupFile(
            formatVersion = FORMAT_VERSION,
            exportedAtMillis = System.currentTimeMillis(),
            sensitivity = prefs.getString("sensitivity", "DEFAULT")!!,
            otpAutoDelete = prefs.getBoolean("otp_auto_delete", false),
            hidePreviews = prefs.getBoolean("hide_previews", false),
            patternLibraryVersion = repo.engine.libraryVersion,
            importedPatternPack = importedPack,
            rules = db.userRules().all().map {
                BackupRule(it.position, it.kind, it.target, it.pattern, it.category)
            },
            reputations = db.reputation().all().map {
                BackupReputation(it.address, it.score, it.userMarkedSpamCount, it.userMarkedNotSpamCount)
            },
            conversationPrefs = db.conversations().allConversations()
                .filter { it.space == Spaces.NORMAL }
                .filter { it.pinned || it.archived || it.muted || it.locked }
                .map { BackupConversationPrefs(it.address, it.pinned, it.archived, it.muted, it.locked) },
            messages = included.mapIndexed { index, it ->
                onMessageProgress?.invoke(index + 1, included.size)
                toBackupMessage(it, mediaNames[it.id])
            },
            media = media,
            lockedEnvelope = lockedEnvelope(context, lockedRows)?.let {
                java.util.Base64.getEncoder().encodeToString(it)
            },
            lockedAuth = (SecretSpace.authForBackup(context) ?: SecretSpace.pendingAuth(context))
                ?.serialize(),
        )
        json.encodeToString(BackupFile.serializer(), backup)
    }

    /**
     * V2-25: `mediaUri` names either a file we own (live send/receive) or a
     * `content://mms/part/…` row the historical backfill referenced in place.
     * Both belong in the snapshot, but only the file form can be measured with
     * `File.length()` — provider-backed media goes through the bounded reader,
     * which stops at the ceiling instead of allocating first. Returns the name
     * to store it under and its bytes, or null when it is missing, too large,
     * or unreadable.
     */
    private fun readMedia(context: Context, ref: String): Pair<String, ByteArray>? {
        MediaRef.asFile(ref)?.let { file ->
            if (!file.exists() || file.length() >= MAX_MEDIA_FILE_BYTES) return null
            return runCatching { file.name to file.readBytes() }.getOrNull()
        }
        val uri = runCatching { android.net.Uri.parse(ref) }.getOrNull() ?: return null
        val bytes = com.messages.core.io.BoundedRead
            .readUri(context, uri, MAX_MEDIA_FILE_BYTES.toInt()) ?: return null
        // Provider parts have no file name of their own; the part id keeps the
        // bundled entry unique alongside the message id prefix.
        return (uri.lastPathSegment?.takeIf { it.isNotBlank() } ?: "part") to bytes
    }

    private fun toBackupMessage(it: MessageEntity, mediaName: String?) = BackupMessage(
        it.address, it.body, it.timestamp, it.isOutgoing, it.read,
        it.category, it.dangerous, it.fraudWarning, it.protectedLabel,
        it.score, it.matchedPatternIds, it.matchedComboIds, it.explanations,
        it.starred, it.trashed, it.trashedAt,
        mediaFileName = mediaName,
        mediaMimeType = if (mediaName != null) it.mediaMimeType else null,
    )

    /**
     * Seal the locked space's content under its credential-derived KEK.
     * Locked chats are ALWAYS fully backed up (no spam-mode shaping — the
     * user manages that space from inside it). When this device itself holds
     * a still-locked restored envelope (restore → backup chain before the
     * code was ever entered), that envelope is carried forward verbatim so
     * the chain never drops locked data.
     */
    private suspend fun lockedEnvelope(context: Context, sealedRows: List<MessageEntity>): ByteArray? {
        val db = MessageRepository.get(context).db
        // V2-6: locked rows are stored sealed under a key that lives in THIS
        // device's Keystore and travels nowhere. Writing that ciphertext into
        // the envelope would produce a snapshot no other device could ever
        // restore — and one this device could not restore either after a
        // Keystore wipe. Open here; the envelope's own credential-derived KEK
        // is what protects the payload in transit and at rest on Drive.
        val lockedRows = LockedContent.open(context, sealedRows)
        val lockedConvs = db.conversations().allConversations()
            .filter { it.space == Spaces.LOCKED }
            .let { LockedContent.open(context, it) }
        // V2-6b: a row that did not open (content key down) would ship as
        // device-bound ciphertext inside the envelope — restored anywhere, it
        // is unreadable forever, because the Keystore key does not travel.
        // Degrade exactly like the kek == null branch below: this snapshot
        // ships without locked chats; the next one after recovery carries them.
        // (Pending-grade rows open via the fallback key and export normally.)
        val unopenable = lockedRows.any { LockedContent.isSealed(it.body) } ||
            lockedConvs.any { LockedContent.isSealed(it.lastMessage) }
        val kek = SecretSpace.kekOrNull(context)
        val saltK = SecretSpace.saltK(context)
        if (unopenable || kek == null || saltK == null ||
            (lockedRows.isEmpty() && lockedConvs.isEmpty())
        ) {
            // Not set up (or nothing locked): pass through a pending envelope
            // if one exists. Degenerate corner: locked rows exist but the KEK
            // cache was lost (Keystore wipe) — we cannot encrypt without the
            // credential, so THIS snapshot ships without locked chats; the
            // next successful unlock re-caches the KEK (attempt() refreshes)
            // and the following snapshot carries them again. Never plaintext.
            return if (SecretSpace.hasPendingRestore(context)) {
                runCatching { SecretSpace.pendingBlobFile(context).readBytes() }.getOrNull()
            } else null
        }
        // R-12: bundle locked MMS attachments into the sub-envelope, under the
        // same per-file and total-size limits as normal media. They stay inside
        // the credential-encrypted payload — never in the outer plaintext map.
        val lockedMedia = LinkedHashMap<String, String>()
        val lockedMediaNames = HashMap<Long, String>()
        var lockedMediaBytes = 0L
        lockedRows.forEach { msg ->
            val ref = msg.mediaUri ?: return@forEach
            val (fileName, bytes) = readMedia(context, ref) ?: return@forEach
            if (lockedMediaBytes + bytes.size > Limits.MAX_MEDIA_BYTES) return@forEach
            if (lockedMedia.size >= Limits.MAX_MEDIA_FILES) return@forEach
            val name = "${msg.id}_$fileName"
            lockedMedia[name] = java.util.Base64.getEncoder().encodeToString(bytes)
            lockedMediaNames[msg.id] = name
            lockedMediaBytes += bytes.size
        }
        val payload = LockedPayload(
            messages = lockedRows.map { toBackupMessage(it, lockedMediaNames[it.id]) },
            conversationPrefs = lockedConvs.map {
                BackupConversationPrefs(it.address, it.pinned, it.archived, it.muted, locked = false)
            },
            lockedAddresses = lockedConvs.map { it.address },
            media = lockedMedia,
        )
        val dataKey = BackupCrypto.newDataKey()
        return BackupCrypto.seal(
            payloadJson = json.encodeToString(LockedPayload.serializer(), payload),
            dataKey = dataKey,
            wrappedKeys = listOf(
                BackupCrypto.wrapWithKek(dataKey, kek, saltK, SecretSpace.iterations(context))
            ),
            createdAt = System.currentTimeMillis(),
            checkpointAt = System.currentTimeMillis(),
            deviceModel = android.os.Build.MODEL ?: "",
            messageCount = lockedRows.size,
        )
    }

    /**
     * R-08 hostile-input bounds. A backup file is chosen by the user from
     * arbitrary storage (or fetched from Drive), so its declared contents are
     * untrusted. Everything is validated BEFORE any mutation begins — a restore
     * that fails validation must leave the device exactly as it was, rather than
     * partially applying settings and rows and then throwing.
     */
    internal object Limits {
        const val MAX_JSON_CHARS = 128 * 1024 * 1024
        const val MAX_MESSAGES = 500_000
        const val MAX_RULES = 5_000
        const val MAX_REPUTATIONS = 100_000
        const val MAX_CONVERSATION_PREFS = 100_000
        const val MAX_MEDIA_FILES = 20_000
        const val MAX_MEDIA_BYTES = 2L * 1024 * 1024 * 1024
        const val MAX_FIELD_CHARS = 100_000
        const val MAX_ADDRESS_CHARS = 512
        const val MAX_FILENAME_CHARS = 255

        /** V2-10: a MIME type is shown as text and used as an Intent type. */
        const val MAX_MIME_CHARS = 255

        /**
         * V2-10: scores are engine output, not free numbers. The widest the
         * engine produces is well inside this; the point is to refuse a value
         * that would overflow arithmetic downstream or render as nonsense.
         */
        val SCORE_RANGE = -10_000..10_000
        val REPUTATION_RANGE = -1_000_000..1_000_000
        const val MAX_MARK_COUNT = 1_000_000
    }

    /**
     * V2-10: the vocabularies a restored value must belong to.
     *
     * These are derived from the enums themselves rather than written out, so
     * adding a [Category] cannot leave the restore path silently rejecting it.
     */
    internal object Vocab {
        val CATEGORIES: Set<String> = Category.entries.mapTo(HashSet()) { it.name }
        val PROTECTED_LABELS: Set<String> = ProtectedLabel.entries.mapTo(HashSet()) { it.name }

        /** Mirrors [MessageRepository.currentSensitivity]'s three named presets. */
        val SENSITIVITIES: Set<String> = setOf("RELAXED", "DEFAULT", "STRICT")

        /** Mirrors [UserRuleEntity]'s documented kind/target vocabularies. */
        val RULE_KINDS: Set<String> = setOf("ALLOW", "BLOCK", "CUSTOM")
        val RULE_TARGETS: Set<String> = setOf("SENDER", "TEXT")

        /** RFC 2045 token grammar, loosely: `type/subtype` with optional params. */
        private val MIME = Regex("""^[A-Za-z0-9!#$&^_.+-]+/[A-Za-z0-9!#$&^_.+-]+(;.*)?$""")

        fun isMimeType(value: String): Boolean =
            value.length <= Limits.MAX_MIME_CHARS && MIME.matches(value)
    }

    /** Per-file media ceiling, shared by the normal and locked export paths. */
    private const val MAX_MEDIA_FILE_BYTES = 5L * 1024 * 1024

    /** Raised when a backup violates a structural bound (R-08). */
    class MalformedBackupException(message: String) : Exception(message)

    /**
     * Validate a decoded backup before it is allowed to change anything.
     *
     * Checks counts, per-field lengths, media sizes and filename safety. Media
     * filenames matter most: they are concatenated into a path under filesDir,
     * so a name containing a separator or `..` could otherwise escape the media
     * directory (path traversal).
     */
    internal fun validate(
        backup: BackupFile,
        budget: RestoreBudget = RestoreBudget.STRUCTURAL,
    ) {
        fun fail(reason: String): Nothing = throw MalformedBackupException(reason)

        if (backup.rules.size > Limits.MAX_RULES) fail("Backup declares too many rules")
        if (backup.reputations.size > Limits.MAX_REPUTATIONS) {
            fail("Backup declares too many senders")
        }

        // V2-10: settings restore straight into SharedPreferences and are read
        // back by name, so an unknown sensitivity would silently pin the engine
        // to DEFAULT for good.
        if (backup.sensitivity !in Vocab.SENSITIVITIES) {
            fail("Backup declares an unknown sensitivity: ${backup.sensitivity}")
        }
        if (backup.patternLibraryVersion < 0) fail("Backup declares a negative pattern version")
        if (backup.exportedAtMillis < 0) fail("Backup declares a negative export time")

        backup.rules.forEachIndexed { i, r -> validateRule(i, r) }

        backup.reputations.forEach { r ->
            if (r.address.isBlank()) fail("Sender reputation has an empty address")
            if (r.address.length > Limits.MAX_ADDRESS_CHARS) fail("Sender address is too long")
            if (r.score !in Limits.REPUTATION_RANGE) {
                fail("Sender reputation score is out of range: ${r.score}")
            }
            if (r.userMarkedSpamCount !in 0..Limits.MAX_MARK_COUNT ||
                r.userMarkedNotSpamCount !in 0..Limits.MAX_MARK_COUNT
            ) {
                fail("Sender reputation counts are out of range")
            }
        }

        backup.conversationPrefs.forEach { p ->
            if (p.address.length > Limits.MAX_ADDRESS_CHARS) {
                fail("Conversation preference address is too long")
            }
        }

        validateMessagesAndMedia(
            backup.messages, backup.media, backup.conversationPrefs.size, budget,
        )
    }

    /**
     * V2-10: a restored rule must clear the SAME bar as one typed into
     * Settings — the restore path is simply another way for a rule to reach
     * intake, and it is the *easier* way to reach it, since a backup file can
     * be handed to the user wholesale.
     *
     * The regex screening deliberately mirrors `SettingsViewModel.addRule`: a
     * pattern that does not compile is fine (that is how a plain-text rule like
     * `+9198…` works — [MessageRepository.matchesRule] falls back to a literal
     * comparison); what is refused is one that DOES compile and can backtrack
     * pathologically. Getting this wrong in the restore direction would mean a
     * shared backup could poison every future classification.
     */
    private fun validateRule(index: Int, r: BackupRule) {
        fun fail(reason: String): Nothing =
            throw MalformedBackupException("Rule ${index + 1}: $reason")

        if (r.kind !in Vocab.RULE_KINDS) fail("unknown kind '${r.kind}'")
        if (r.target !in Vocab.RULE_TARGETS) fail("unknown target '${r.target}'")
        // Every rule carries a category, not only CUSTOM ones — the column is
        // non-null and MessageRepository.classify reads it back by name.
        if (r.category !in Vocab.CATEGORIES) fail("unknown category '${r.category}'")
        if (r.position < 0) fail("position is negative")
        if (r.pattern.isBlank()) fail("pattern is empty")
        if (r.pattern.length > SafeRegexPolicy.MAX_REGEX_LENGTH) {
            fail("pattern is longer than ${SafeRegexPolicy.MAX_REGEX_LENGTH} characters")
        }
        val compiles = runCatching { Regex(r.pattern) }.isSuccess
        if (compiles && !SafeRegexPolicy.accepts(r.pattern)) {
            val why = runCatching { SafeRegexPolicy.requireAccepted(r.pattern) }
                .exceptionOrNull()?.message ?: "pattern isn't allowed"
            fail(why)
        }
    }

    /**
     * V2-11: the bounds that every message/media bearing container must clear —
     * the outer [BackupFile] *and* the credential-encrypted [LockedPayload].
     *
     * Decryption authenticates a payload; it does not make it well-formed. The
     * locked payload used to be decoded and applied with none of these checks,
     * so a backup file the user restored could carry an unbounded locked
     * envelope — the outer file passed validation while the inner one walked
     * straight past every ceiling.
     */
    private fun validateMessagesAndMedia(
        messages: List<BackupMessage>,
        media: Map<String, String>,
        conversationPrefsCount: Int,
        budget: RestoreBudget,
    ) {
        fun fail(reason: String): Nothing = throw MalformedBackupException(reason)

        if (messages.size > Limits.MAX_MESSAGES) fail("Backup declares too many messages")
        if (conversationPrefsCount > Limits.MAX_CONVERSATION_PREFS) {
            fail("Backup declares too many conversation preferences")
        }
        if (media.size > Limits.MAX_MEDIA_FILES) fail("Backup declares too many media files")

        messages.forEach { m ->
            if (m.address.length > Limits.MAX_ADDRESS_CHARS) fail("Message address is too long")
            if (m.body.length > Limits.MAX_FIELD_CHARS) fail("Message body is too long")
            if (m.timestamp < 0) fail("Message timestamp is negative")
            m.mediaFileName?.let { if (!isSafeMediaName(it)) fail("Unsafe media file name") }

            // V2-10: the classification fields are engine vocabulary, and they
            // are read back by name — `Category.valueOf` on a restored value
            // used to throw at classify time, which the caller caught by
            // routing the message to Inbox. That turned one bad row in a
            // backup into a silent filtering outage, so the value is refused
            // here instead, while nothing has been written yet.
            if (m.category !in Vocab.CATEGORIES) fail("Unknown message category: ${m.category}")
            if (m.protectedLabel !in Vocab.PROTECTED_LABELS) {
                fail("Unknown protected label: ${m.protectedLabel}")
            }
            if (m.score !in Limits.SCORE_RANGE) fail("Message score is out of range: ${m.score}")
            if (m.matchedPatternIds.length > Limits.MAX_FIELD_CHARS ||
                m.matchedComboIds.length > Limits.MAX_FIELD_CHARS ||
                m.explanations.length > Limits.MAX_FIELD_CHARS
            ) {
                fail("Message explanation fields are too long")
            }
            m.trashedAt?.let { if (it < 0) fail("Message trash timestamp is negative") }
            // A MIME type reaches an Intent type and the attachment chip label,
            // so it must look like one rather than be arbitrary text.
            m.mediaMimeType?.let {
                if (!Vocab.isMimeType(it)) fail("Unsupported media type: $it")
            }
        }

        var totalMedia = 0L
        media.forEach { (name, b64) ->
            if (!isSafeMediaName(name)) fail("Unsafe media file name")
            // Base64 expands 4 chars → 3 bytes; check the declared size before
            // decoding so a huge blob is refused rather than allocated.
            val bytes = b64.length / 4L * 3L
            // V2-12: the aggregate ceiling alone permits ONE file that is the
            // whole aggregate, and that file is decoded in a single
            // allocation — so the per-file bound is the one that keeps
            // Base64.decode from being handed an arbitrary size.
            if (bytes > budget.maxMediaFileBytes) fail("Backup attachment '$name' is too large")
            totalMedia += bytes
            if (totalMedia > budget.maxMediaBytes) {
                fail("Backup media exceeds what this device can store")
            }
        }
    }

    /** V2-11: same hostile-input bounds, applied to a decrypted locked payload. */
    internal fun validateLocked(
        payload: LockedPayload,
        budget: RestoreBudget = RestoreBudget.STRUCTURAL,
    ) {
        if (payload.lockedAddresses.size > Limits.MAX_CONVERSATION_PREFS) {
            throw MalformedBackupException("Backup declares too many locked conversations")
        }
        payload.lockedAddresses.forEach {
            if (it.length > Limits.MAX_ADDRESS_CHARS) {
                throw MalformedBackupException("Locked conversation address is too long")
            }
        }
        validateMessagesAndMedia(
            payload.messages, payload.media, payload.conversationPrefs.size, budget,
        )
    }

    /** No separators, no traversal, no hidden/empty names (R-08). */
    private fun isSafeMediaName(name: String): Boolean =
        name.isNotBlank() &&
            name.length <= Limits.MAX_FILENAME_CHARS &&
            !name.contains('/') &&
            !name.contains('\\') &&
            name != "." && name != ".." &&
            !name.startsWith(".")

    /**
     * V2-12: read backup text from [uri] without letting the file's own size
     * choose our allocation.
     *
     * The picker hands back a URI to arbitrary storage, and `readText()` on it
     * commits to whatever is there before anything has had a chance to object.
     * The declared length is a cheap first refusal; the streaming read is the
     * one that actually holds, since a provider may decline to declare one.
     *
     * Returns null when the file cannot be read at all; throws
     * [MalformedBackupException] when it is readable but too large, so the
     * caller can tell the user which of the two happened.
     */
    fun readBackupText(context: Context, uri: android.net.Uri): String? {
        val limit = RestoreBudget.forDevice(context)
        fun tooLarge(): Nothing = throw MalformedBackupException(
            "This backup is too large to restore on this device " +
                "(the limit here is about ${limit.jsonMegabytes()} MB of backup text)",
        )
        com.messages.core.io.BoundedRead.declaredSize(context.contentResolver, uri)?.let {
            // Bytes, against a character limit: backup JSON is ASCII, and a
            // file whose BYTES already exceed the limit cannot have fewer
            // characters than that.
            if (it > limit.maxJsonChars) tooLarge()
        }
        val stream = runCatching { context.contentResolver.openInputStream(uri) }
            .getOrNull() ?: return null
        return stream.bufferedReader().use { reader ->
            val out = StringBuilder()
            val buf = CharArray(64 * 1024)
            while (true) {
                val n = reader.read(buf)
                if (n < 0) break
                if (out.length + n > limit.maxJsonChars) tooLarge()
                out.appendRange(buf, 0, n)
            }
            out.toString()
        }
    }

    suspend fun import(context: Context, text: String): Result<ImportStats> =
        withContext(Dispatchers.IO) {
            runCatching {
                // V2-12: the structural ceiling is a multiple of what any
                // phone's heap holds, so it never fired before the OOM did.
                // The bound that decides is the device's.
                val budget = RestoreBudget.forDevice(context)
                if (text.length > budget.maxJsonChars) {
                    throw MalformedBackupException(
                        "This backup is too large to restore on this device " +
                            "(the limit here is about ${budget.jsonMegabytes()} MB of backup text)",
                    )
                }
                val backup = json.decodeFromString(BackupFile.serializer(), text)
                require(backup.formatVersion <= FORMAT_VERSION) {
                    "Backup was made by a newer app version"
                }
                // R-08: validate the WHOLE file before the first mutation.
                // V2-12: including whether its media fits this device's disk.
                validate(backup, budget)
                val repo = MessageRepository.get(context)
                val db = repo.db

                // V2-27: a restore touches four stores that cannot share one
                // transaction — SharedPreferences, the Room index, the
                // Telephony provider, and files on disk. A failure partway used
                // to leave all four in whatever state it got to, with no way
                // back. There is now an explicit recovery boundary: settings
                // are snapshotted, and provider rows and media files written
                // during this attempt are recorded so they can be undone.
                val undo = RestoreUndo(context)
                undo.snapshotSettings()
                try {

                // Settings (app lock is deliberately NOT restored — device-specific).
                context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                    .putString("sensitivity", backup.sensitivity)
                    .putBoolean("otp_auto_delete", backup.otpAutoDelete)
                    .putBoolean("hide_previews", backup.hidePreviews)
                    .apply()
                repo.setSensitivity(backup.sensitivity)
                OtpCleanup.ensureScheduled(context)
                if (backup.importedPatternPack != null) {
                    repo.importPatternPack(backup.importedPatternPack)
                }

                // Rules: replace-by-content dedupe (same kind+pattern+target).
                val existingRules = db.userRules().all()
                var rulesRestored = 0
                backup.rules.forEach { r ->
                    val dupe = existingRules.any {
                        it.kind == r.kind && it.target == r.target && it.pattern == r.pattern
                    }
                    if (!dupe) {
                        db.userRules().insert(
                            UserRuleEntity(
                                position = r.position, kind = r.kind, target = r.target,
                                pattern = r.pattern, category = r.category,
                            )
                        )
                        rulesRestored++
                    }
                }

                // Reputations: merge, keeping whichever signal is stronger.
                var reputationsRestored = 0
                backup.reputations.forEach { r ->
                    val existing = db.reputation().forSender(r.address)
                    if (existing == null) {
                        db.reputation().upsert(
                            SenderReputationEntity(
                                address = r.address, score = r.score,
                                userMarkedSpamCount = r.userMarkedSpamCount,
                                userMarkedNotSpamCount = r.userMarkedNotSpamCount,
                            )
                        )
                        reputationsRestored++
                    }
                }

                // Messages: skip anything already present (address+time+direction+body).
                // V2-6: opened before keying — see importLockedPayload.
                val existingKeys = LockedContent.open(context, db.messages().allMessages())
                    .mapTo(HashSet()) { messageKey(it.address, it.timestamp, it.isOutgoing, it.body) }
                val (toInsert, skipped) = dedupeForImport(existingKeys, backup.messages)
                var restored = 0
                toInsert.forEach { m ->
                    val threadId = repo.threadIdFor(m.address)
                    // Best-effort provider write (needs default-SMS role); the
                    // index row below keeps the message either way. Trash items
                    // (§6.4) stay OUT of the provider — they were deleted from
                    // it on this or the source device; only the index row with
                    // its purge clock is restored.
                    val smsId = if (m.trashed) null else try {
                        val uri = if (m.isOutgoing) Telephony.Sms.Sent.CONTENT_URI
                        else Telephony.Sms.Inbox.CONTENT_URI
                        val inserted = context.contentResolver.insert(
                            uri,
                            ContentValues().apply {
                                put(Telephony.Sms.ADDRESS, m.address)
                                put(Telephony.Sms.BODY, m.body)
                                put(Telephony.Sms.DATE, m.timestamp)
                                put(Telephony.Sms.READ, if (m.read) 1 else 0)
                                put(Telephony.Sms.THREAD_ID, threadId)
                                put(
                                    Telephony.Sms.TYPE,
                                    if (m.isOutgoing) Telephony.Sms.MESSAGE_TYPE_SENT
                                    else Telephony.Sms.MESSAGE_TYPE_INBOX,
                                )
                            },
                        )
                        // V2-27: remember it so a later failure can roll it back.
                        inserted?.let(undo::recordProviderRow)
                        inserted?.lastPathSegment?.toLongOrNull()
                    } catch (_: Exception) {
                        null
                    }
                    // §8.3 media toggle: write the bundled blob back to local storage.
                    var mediaUri: String? = null
                    if (m.mediaFileName != null) {
                        backup.media[m.mediaFileName]?.let { b64 ->
                            runCatching {
                                val dir = java.io.File(context.filesDir, "mms_media").apply { mkdirs() }
                                val f = java.io.File(dir, "restored_${m.timestamp}_${m.mediaFileName}")
                                // R-08: validate() already rejects separators and
                                // traversal in media names, but re-assert the
                                // result stays inside the media directory — the
                                // canonical path is the only thing that actually
                                // proves containment.
                                if (f.canonicalPath.startsWith(dir.canonicalPath + java.io.File.separator)) {
                                    f.writeBytes(java.util.Base64.getDecoder().decode(b64))
                                    mediaUri = f.absolutePath
                                    undo.recordMediaFile(f) // V2-27
                                }
                            }
                        }
                    }
                    db.messages().insert(
                        MessageEntity(
                            smsId = smsId,
                            threadId = threadId,
                            address = m.address,
                            body = m.body,
                            normalizedBody = repo.normalizedOf(m.body),
                            timestamp = m.timestamp,
                            isOutgoing = m.isOutgoing,
                            read = m.read,
                            category = m.category,
                            dangerous = m.dangerous,
                            fraudWarning = m.fraudWarning,
                            protectedLabel = m.protectedLabel,
                            score = m.score,
                            matchedPatternIds = m.matchedPatternIds,
                            matchedComboIds = m.matchedComboIds,
                            explanations = m.explanations,
                            starred = m.starred,
                            trashed = m.trashed,
                            trashedAt = m.trashedAt,
                            mediaUri = mediaUri,
                            mediaMimeType = if (mediaUri != null) m.mediaMimeType else null,
                            sendStatus = if (m.isOutgoing) "SENT" else "NONE",
                        )
                    )
                    restored++
                }

                // Rebuild conversation summaries for every thread we touched,
                // then apply carried-over prefs by address.
                rebuildConversations(context, repo)
                backup.conversationPrefs.forEach { p ->
                    val conv = db.conversations().allConversations()
                        .firstOrNull { it.address == p.address && it.space == Spaces.NORMAL }
                        ?: return@forEach
                    db.conversations().upsert(
                        conv.copy(
                            pinned = p.pinned, archived = p.archived,
                            muted = p.muted, locked = p.locked,
                        )
                    )
                }

                // Secret locked space: try the local KEK first (same credential
                // as this device) — otherwise the envelope waits, opaque, for
                // the user to enter the original secret code.
                var lockedRestored = 0
                var lockedPending = false
                val lockedBlob = backup.lockedEnvelope
                    ?.let { runCatching { java.util.Base64.getDecoder().decode(it) }.getOrNull() }
                if (lockedBlob != null) {
                    // V2-6b: with the content key down, importLockedPayload
                    // could not seal what it stores — and its throw would roll
                    // back the ENTIRE restore. Park the envelope as pending
                    // instead; it completes after the key recovers.
                    val kek = if (LockedContent.available(context)) {
                        SecretSpace.kekOrNull(context)
                    } else {
                        null
                    }
                    val opened = kek?.let {
                        runCatching {
                            BackupCrypto.open(
                                lockedBlob,
                                BackupCrypto.unwrapWithKek(BackupCrypto.readHeader(lockedBlob), it),
                                maxExpanded = budget.maxExpandedBytes, // V2-12
                            )
                        }.getOrNull()
                    }
                    if (opened != null) {
                        lockedRestored = importLockedPayload(context, opened)
                    } else {
                        val auth = backup.lockedAuth?.let(SecretSpace.PendingAuth::parse)
                        if (auth != null) {
                            SecretSpace.storePendingRestore(context, lockedBlob, auth)
                            lockedPending = true
                        }
                    }
                }

                ImportStats(restored, skipped, rulesRestored, reputationsRestored, lockedRestored, lockedPending)

                } catch (t: Throwable) {
                    // V2-27: undo what this attempt wrote outside the index —
                    // provider rows and media files — and put settings back.
                    // Index rows inserted so far are left in place on purpose:
                    // they are content-deduped, so re-running the restore
                    // converges rather than duplicating, and deleting real
                    // messages during error handling is the more dangerous move.
                    undo.rollback()
                    throw t
                }
            }
        }

    /**
     * V2-27: recovery boundary for [import].
     *
     * A restore writes to four stores and only one of them (Room) has
     * transactions. This records the side effects that a failure would
     * otherwise leave stranded — Telephony rows visible to every other SMS app,
     * media files occupying disk with nothing referencing them — and puts the
     * settings snapshot back. Every rollback step is individually guarded: a
     * rollback that throws would mask the original failure, which is the more
     * useful error to surface.
     */
    private class RestoreUndo(private val context: Context) {
        private val providerRows = mutableListOf<android.net.Uri>()
        private val mediaFiles = mutableListOf<java.io.File>()
        private var settings: Triple<String?, Boolean, Boolean>? = null

        fun snapshotSettings() {
            val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            settings = Triple(
                prefs.getString("sensitivity", null),
                prefs.getBoolean("otp_auto_delete", false),
                prefs.getBoolean("hide_previews", false),
            )
        }

        fun recordProviderRow(uri: android.net.Uri) {
            providerRows += uri
        }

        fun recordMediaFile(file: java.io.File) {
            mediaFiles += file
        }

        fun rollback() {
            providerRows.forEach { uri ->
                runCatching { context.contentResolver.delete(uri, null, null) }
            }
            mediaFiles.forEach { f -> runCatching { f.delete() } }
            settings?.let { (sensitivity, otpAutoDelete, hidePreviews) ->
                runCatching {
                    context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit()
                        .apply {
                            if (sensitivity == null) remove("sensitivity")
                            else putString("sensitivity", sensitivity)
                        }
                        .putBoolean("otp_auto_delete", otpAutoDelete)
                        .putBoolean("hide_previews", hidePreviews)
                        .apply()
                }
            }
        }
    }

    /**
     * Import an opened locked payload into the LOCKED space. Additive +
     * idempotent like the normal path; the dedupe key set spans BOTH spaces so
     * a message never duplicates across them.
     *
     * V2-6: unlike the normal path, **no Telephony row is written** and the
     * body is sealed before it is stored. Restoring a locked chat used to
     * re-publish its text into shared storage, which meant a restore quietly
     * reversed the protection a lock is for.
     */
    suspend fun importLockedPayload(context: Context, payloadJson: String): Int =
        withContext(Dispatchers.IO) {
            // V2-6b: hard backstop — restoring locked rows is a user-initiated
            // locked write; sealing an entire space at the degraded pending
            // grade on a restore path would silently downgrade its protection.
            if (!LockedContent.available(context)) throw LockedWriteBlockedException()
            // V2-11: bound the payload BEFORE decoding it and again before
            // applying it. Being inside an authenticated envelope proves who
            // wrote it, not that it is sane — and a restored backup can be one
            // the user obtained from anywhere.
            val budget = RestoreBudget.forDevice(context)
            if (payloadJson.length > budget.maxJsonChars) {
                throw MalformedBackupException(
                    "The locked part of this backup is too large to restore on this device",
                )
            }
            val payload = json.decodeFromString(LockedPayload.serializer(), payloadJson)
            validateLocked(payload, budget)
            val repo = MessageRepository.get(context)
            val db = repo.db
            // V2-6: opened before keying. The dedupe key includes the body, and
            // a sealed locked body would never match the incoming plaintext —
            // so re-importing the same backup would duplicate every locked row,
            // and cross-space dedupe would stop working.
            val existingKeys = LockedContent.open(context, db.messages().allMessages())
                .mapTo(HashSet()) { messageKey(it.address, it.timestamp, it.isOutgoing, it.body) }
            val (toInsert, _) = dedupeForImport(existingKeys, payload.messages)
            var restored = 0
            toInsert.forEach { m ->
                val threadId = repo.threadIdFor(m.address)
                // V2-6: NO provider row. This used to write the restored locked
                // body into Telephony "best-effort exactly like the normal
                // path", which handed every restored locked message straight
                // back to any app holding the SMS role — undoing on restore the
                // exact exposure the locked space now closes on receive. A
                // locked message has no smsId by construction; the cost is that
                // it is invisible to other SMS apps and does not survive a
                // switch away from this app, which is what locking now means.
                val smsId: Long? = null
                // R-12: restore the locked attachment that travelled inside the
                // sub-envelope. Same containment check as the normal path.
                var mediaUri: String? = null
                if (m.mediaFileName != null && isSafeMediaName(m.mediaFileName)) {
                    payload.media[m.mediaFileName]?.let { b64 ->
                        runCatching {
                            val dir = java.io.File(context.filesDir, "mms_media").apply { mkdirs() }
                            val f = java.io.File(dir, "restored_${m.timestamp}_${m.mediaFileName}")
                            if (f.canonicalPath.startsWith(dir.canonicalPath + java.io.File.separator)) {
                                f.writeBytes(java.util.Base64.getDecoder().decode(b64))
                                mediaUri = f.absolutePath
                            }
                        }
                    }
                }
                db.messages().insert(
                    // V2-6: sealed on the way in — this is the one insert path
                    // that bypasses MessageRepository's sealing helpers.
                    LockedContent.seal(context, MessageEntity(
                        smsId = smsId,
                        threadId = threadId,
                        address = m.address,
                        body = m.body,
                        normalizedBody = repo.normalizedOf(m.body),
                        timestamp = m.timestamp,
                        isOutgoing = m.isOutgoing,
                        read = m.read,
                        category = m.category,
                        dangerous = m.dangerous,
                        fraudWarning = m.fraudWarning,
                        protectedLabel = m.protectedLabel,
                        score = m.score,
                        matchedPatternIds = m.matchedPatternIds,
                        matchedComboIds = m.matchedComboIds,
                        explanations = m.explanations,
                        starred = m.starred,
                        trashed = m.trashed,
                        trashedAt = m.trashedAt,
                        mediaUri = mediaUri,
                        mediaMimeType = if (mediaUri != null) m.mediaMimeType else null,
                        sendStatus = if (m.isOutgoing) "SENT" else "NONE",
                        space = Spaces.LOCKED,
                    ))
                )
                restored++
            }
            rebuildConversations(context, repo)
            // Routing rows: every locked address gets its LOCKED conversation
            // even when it held no messages yet ("New locked chat").
            payload.lockedAddresses.forEach { address ->
                val threadId = repo.threadIdFor(address)
                if (db.conversations().byThreadId(threadId, Spaces.LOCKED) == null) {
                    db.conversations().upsert(
                        ConversationEntity(
                            threadId = threadId,
                            address = address,
                            contactName = repo.lookupContactName(address),
                            lastTimestamp = System.currentTimeMillis(),
                            space = Spaces.LOCKED,
                        )
                    )
                }
            }
            payload.conversationPrefs.forEach { p ->
                val conv = db.conversations().allConversations()
                    .firstOrNull { it.address == p.address && it.space == Spaces.LOCKED }
                    ?: return@forEach
                db.conversations().upsert(conv.copy(pinned = p.pinned, muted = p.muted))
            }
            restored
        }

    /**
     * Called after the user's first successful credential entry when a
     * restored envelope is pending: opens it with the freshly-cached KEK and
     * places the locked chats. Returns the restored count, or null when the
     * envelope could not be opened (corrupt / mismatched).
     */
    suspend fun completeLockedRestore(context: Context): Int? = withContext(Dispatchers.IO) {
        if (!SecretSpace.hasPendingRestore(context)) return@withContext null
        // V2-6b: without the content key the payload cannot be sealed for
        // storage. Return null WITHOUT clearing the pending envelope — the
        // next successful unlock after Keystore recovery retries it.
        if (!LockedContent.available(context)) return@withContext null
        val kek = SecretSpace.kekOrNull(context) ?: return@withContext null
        val budget = RestoreBudget.forDevice(context)
        // V2-12: the pending envelope came in from a backup file, so its size
        // is no more trustworthy here than it was on the way in.
        val blob = com.messages.core.io.BoundedRead
            .readFile(SecretSpace.pendingBlobFile(context), budget.maxExpandedBytes)
            ?: return@withContext null
        val opened = runCatching {
            BackupCrypto.open(
                blob,
                BackupCrypto.unwrapWithKek(BackupCrypto.readHeader(blob), kek),
                maxExpanded = budget.maxExpandedBytes,
            )
        }.getOrNull() ?: return@withContext null
        val restored = importLockedPayload(context, opened)
        SecretSpace.clearPendingRestore(context)
        restored
    }

    /**
     * §6 dedupe key: address + timestamp + direction + body digest.
     *
     * R-11: this used Java's [String.hashCode], a 32-bit non-cryptographic hash
     * with trivial collisions ("Aa" and "BB" hash identically). Two different
     * messages to the same address in the same millisecond in the same direction
     * would collide, and restore would SILENTLY DROP the second — counting it as
     * a duplicate. A SHA-256 digest of the body removes any realistic collision.
     *
     * Truncated to 128 bits: still collision-free for this purpose, and it halves
     * the in-memory key-set footprint on large restores. The key is derived at
     * runtime and never persisted, so changing it needs no migration.
     */
    internal fun messageKey(address: String, timestamp: Long, isOutgoing: Boolean, body: String) =
        "$address|$timestamp|$isOutgoing|${bodyDigest(body)}"

    private fun bodyDigest(body: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
            .digest(body.toByteArray(Charsets.UTF_8))
        val sb = StringBuilder(32)
        for (i in 0 until 16) sb.append("%02x".format(digest[i]))
        return sb.toString()
    }

    /**
     * Pure restore-idempotency core (JVM-testable): given the keys of every
     * message already on the device, split [incoming] into the messages to
     * insert and the count skipped as duplicates. Also dedupes within the
     * backup itself. A second import of the same backup yields zero inserts.
     */
    internal fun dedupeForImport(
        existingKeys: MutableSet<String>,
        incoming: List<BackupMessage>,
    ): Pair<List<BackupMessage>, Int> {
        val toInsert = ArrayList<BackupMessage>(incoming.size)
        var skipped = 0
        incoming.forEach { m ->
            if (existingKeys.add(messageKey(m.address, m.timestamp, m.isOutgoing, m.body))) {
                toInsert.add(m)
            } else {
                skipped++
            }
        }
        return toInsert to skipped
    }

    private suspend fun rebuildConversations(context: Context, repo: MessageRepository) {
        val db = repo.db
        // Space-aware: a thread can have one row per space (New locked chat).
        // V2-6: opened first. `lastMessage` below is taken from `latest.body`,
        // and a locked body is sealed under the "body" AAD while the preview
        // column is sealed under "lastMessage" — copying the ciphertext across
        // would produce a preview that can never be opened. Open here and let
        // the seal() on the way out re-seal it under the right label.
        val latestByThreadSpace = LockedContent.open(context, db.messages().allMessages())
            .filter { !it.trashed } // trash never resurfaces a conversation (§6.4)
            .groupBy { it.threadId to it.space }
            .mapValues { (_, msgs) -> msgs.maxBy { it.timestamp } }
        latestByThreadSpace.forEach { (key, latest) ->
            val (threadId, space) = key
            val existing = db.conversations().byThreadId(threadId, space)
            if (existing == null || latest.timestamp > existing.lastTimestamp) {
                db.conversations().upsert(
                    LockedContent.seal(context, ConversationEntity(
                        id = existing?.id ?: 0,
                        threadId = threadId,
                        address = existing?.address ?: latest.address,
                        contactName = existing?.contactName
                            ?: repo.lookupContactName(latest.address),
                        lastMessage = latest.body,
                        lastTimestamp = latest.timestamp,
                        unreadCount = existing?.unreadCount ?: 0,
                        category = existing?.category ?: latest.category,
                        pinned = existing?.pinned ?: false,
                        archived = existing?.archived ?: false,
                        muted = existing?.muted ?: false,
                        locked = existing?.locked ?: false,
                        preferredSubId = existing?.preferredSubId,
                        space = space,
                    ))
                )
            }
        }
    }
}
