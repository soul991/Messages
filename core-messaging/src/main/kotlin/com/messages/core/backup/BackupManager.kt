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
import com.messages.core.secret.SecretSpace
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
    private suspend fun lockedEnvelope(context: Context, lockedRows: List<MessageEntity>): ByteArray? {
        val db = MessageRepository.get(context).db
        val lockedConvs = db.conversations().allConversations().filter { it.space == Spaces.LOCKED }
        val kek = SecretSpace.kekOrNull(context)
        val saltK = SecretSpace.saltK(context)
        if (kek == null || saltK == null || (lockedRows.isEmpty() && lockedConvs.isEmpty())) {
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
        val payload = LockedPayload(
            messages = lockedRows.map { toBackupMessage(it, null) },
            conversationPrefs = lockedConvs.map {
                BackupConversationPrefs(it.address, it.pinned, it.archived, it.muted, locked = false)
            },
            lockedAddresses = lockedConvs.map { it.address },
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
                    val kek = SecretSpace.kekOrNull(context)
                    val opened = kek?.let {
                        runCatching {
                            BackupCrypto.open(
                                lockedBlob,
                                BackupCrypto.unwrapWithKek(BackupCrypto.readHeader(lockedBlob), it),
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
            }
        }

    /**
     * Import an opened locked payload into the LOCKED space. Additive +
     * idempotent like the normal path; the dedupe key set spans BOTH spaces so
     * a message never duplicates across them. Provider rows are written
     * best-effort exactly like the normal path — SMS lives in shared storage
     * either way (stated in the locked-chats disclaimer); invisibility inside
     * THIS app comes from the space column.
     */
    suspend fun importLockedPayload(context: Context, payloadJson: String): Int =
        withContext(Dispatchers.IO) {
            val payload = json.decodeFromString(LockedPayload.serializer(), payloadJson)
            val repo = MessageRepository.get(context)
            val db = repo.db
            val existingKeys = db.messages().allMessages()
                .mapTo(HashSet()) { messageKey(it.address, it.timestamp, it.isOutgoing, it.body) }
            val (toInsert, _) = dedupeForImport(existingKeys, payload.messages)
            var restored = 0
            toInsert.forEach { m ->
                val threadId = repo.threadIdFor(m.address)
                val smsId = if (m.trashed) null else try {
                    context.contentResolver.insert(
                        if (m.isOutgoing) Telephony.Sms.Sent.CONTENT_URI else Telephony.Sms.Inbox.CONTENT_URI,
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
                        sendStatus = if (m.isOutgoing) "SENT" else "NONE",
                        space = Spaces.LOCKED,
                    )
                )
                restored++
            }
            rebuildConversations(repo)
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
        val kek = SecretSpace.kekOrNull(context) ?: return@withContext null
        val blob = runCatching { SecretSpace.pendingBlobFile(context).readBytes() }.getOrNull()
            ?: return@withContext null
        val opened = runCatching {
            BackupCrypto.open(blob, BackupCrypto.unwrapWithKek(BackupCrypto.readHeader(blob), kek))
        }.getOrNull() ?: return@withContext null
        val restored = importLockedPayload(context, opened)
        SecretSpace.clearPendingRestore(context)
        restored
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
        // Space-aware: a thread can have one row per space (New locked chat).
        val latestByThreadSpace = db.messages().allMessages()
            .filter { !it.trashed } // trash never resurfaces a conversation (§6.4)
            .groupBy { it.threadId to it.space }
            .mapValues { (_, msgs) -> msgs.maxBy { it.timestamp } }
        latestByThreadSpace.forEach { (key, latest) ->
            val (threadId, space) = key
            val existing = db.conversations().byThreadId(threadId, space)
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
                        space = space,
                    )
                )
            }
        }
    }
}
