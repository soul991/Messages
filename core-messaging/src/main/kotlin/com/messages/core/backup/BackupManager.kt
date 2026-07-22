package com.messages.core.backup

import android.content.ContentValues
import android.content.Context
import android.provider.Telephony
import com.messages.core.MessageRepository
import com.messages.core.cleanup.OtpCleanup
import com.messages.core.db.ConversationEntity
import com.messages.core.db.MessageEntity
import com.messages.core.db.SenderReputationEntity
import com.messages.core.db.UserRuleEntity
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
    )

    data class ImportStats(
        val messagesRestored: Int,
        val messagesSkipped: Int,
        val rulesRestored: Int,
        val reputationsRestored: Int,
    )

    private val json = Json {
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

        val included = db.messages().allMessages()
            // Never export an unsent scheduled draft as if it were history.
            .filter { it.sendStatus != "SCHEDULED" }
            // §8.3 checkpoint cut: deterministic content per checkpoint.
            .filter { options.upTo == null || it.timestamp <= options.upTo }
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
                val path = msg.mediaUri ?: return@forEach
                runCatching {
                    val file = java.io.File(path)
                    if (file.exists() && file.length() < 5L * 1024 * 1024) {
                        val name = "${msg.id}_${file.name}"
                        media[name] = java.util.Base64.getEncoder().encodeToString(file.readBytes())
                        mediaNames[msg.id] = name
                    }
                }
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
                .filter { it.pinned || it.archived || it.muted || it.locked }
                .map { BackupConversationPrefs(it.address, it.pinned, it.archived, it.muted, it.locked) },
            messages = included.mapIndexed { index, it ->
                onMessageProgress?.invoke(index + 1, included.size)
                BackupMessage(
                    it.address, it.body, it.timestamp, it.isOutgoing, it.read,
                    it.category, it.dangerous, it.fraudWarning, it.protectedLabel,
                    it.score, it.matchedPatternIds, it.matchedComboIds, it.explanations,
                    it.starred, it.trashed, it.trashedAt,
                    mediaFileName = mediaNames[it.id],
                    mediaMimeType = if (mediaNames[it.id] != null) it.mediaMimeType else null,
                )
            },
            media = media,
        )
        json.encodeToString(BackupFile.serializer(), backup)
    }

    suspend fun import(context: Context, text: String): Result<ImportStats> =
        withContext(Dispatchers.IO) {
            runCatching {
                val backup = json.decodeFromString(BackupFile.serializer(), text)
                require(backup.formatVersion <= FORMAT_VERSION) {
                    "Backup was made by a newer app version"
                }
                val repo = MessageRepository.get(context)
                val db = repo.db

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
                val existingKeys = db.messages().allMessages()
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
                        context.contentResolver.insert(
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
                        )?.lastPathSegment?.toLongOrNull()
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
                                f.writeBytes(java.util.Base64.getDecoder().decode(b64))
                                mediaUri = f.absolutePath
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
                rebuildConversations(repo)
                backup.conversationPrefs.forEach { p ->
                    val conv = db.conversations().allConversations()
                        .firstOrNull { it.address == p.address } ?: return@forEach
                    db.conversations().upsert(
                        conv.copy(
                            pinned = p.pinned, archived = p.archived,
                            muted = p.muted, locked = p.locked,
                        )
                    )
                }

                ImportStats(restored, skipped, rulesRestored, reputationsRestored)
            }
        }

    /** §6 dedupe key: address + timestamp + direction + body-hash. */
    internal fun messageKey(address: String, timestamp: Long, isOutgoing: Boolean, body: String) =
        "$address|$timestamp|$isOutgoing|${body.hashCode()}"

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

    private suspend fun rebuildConversations(repo: MessageRepository) {
        val db = repo.db
        val latestByThread = db.messages().allMessages()
            .filter { !it.trashed } // trash never resurfaces a conversation (§6.4)
            .groupBy { it.threadId }
            .mapValues { (_, msgs) -> msgs.maxBy { it.timestamp } }
        latestByThread.forEach { (threadId, latest) ->
            val existing = db.conversations().byThreadId(threadId)
            if (existing == null || latest.timestamp > existing.lastTimestamp) {
                db.conversations().upsert(
                    ConversationEntity(
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
                    )
                )
            }
        }
    }
}
