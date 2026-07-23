package com.messages.core

import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.room.Room
import com.messages.core.db.ConversationEntity
import com.messages.core.db.MessageEntity
import com.messages.core.db.MessagesDatabase
import com.messages.core.db.SenderReputationEntity
import com.messages.core.mms.MmsPduParser
import com.messages.core.search.MessageSearch
import com.messages.core.trash.TrashRetention
import com.messages.protection.Category
import com.messages.protection.Normalizer
import com.messages.protection.PatternMatcher
import com.messages.protection.ProtectionEngine
import com.messages.protection.SenderAnalyzer
import com.messages.protection.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Single entry point for message storage + classification. The system
 * Telephony provider remains the source of truth for content; Room holds the
 * index (categories, labels, matched patterns, reputation, rules).
 */
class MessageRepository private constructor(private val context: Context) {

    val db: MessagesDatabase = Room.databaseBuilder(
        context, MessagesDatabase::class.java, "messages.db"
    ).addMigrations(*com.messages.core.db.Migrations.ALL)
        // Destruction is allowed ONLY for pre-release dev schemas (<v4). From
        // v4 forward every bump must ship a real migration in Migrations.ALL —
        // a missing one now crashes loudly instead of silently wiping the index.
        .fallbackToDestructiveMigrationFrom(1, 2, 3)
        .build()

    private val settingsPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** §8.5 multi-keyword FTS search. */
    val search: MessageSearch by lazy { MessageSearch(db.messages()) }

    /** Stage-0 normalization for the FTS index (§8.5) — never fails the caller. */
    fun normalizedOf(body: String): String =
        runCatching { Normalizer.normalize(body).normalizedText }
            .getOrDefault(body.lowercase())
            .ifBlank { body.lowercase() }

    val engine: ProtectionEngine by lazy {
        // An imported pattern pack (§7.5) overrides the bundled library.
        val imported = runCatching {
            val f = importedPackFile()
            if (f.exists()) PatternMatcher.fromJson(f.readText()) else null
        }.getOrNull()
        ProtectionEngine(imported ?: bundledMatcher(), currentSensitivity())
    }

    private fun bundledMatcher(): PatternMatcher {
        // patterns.json ships as a JVM resource inside the :protection-engine jar
        // (it has no Android assets) — load it via the classloader, not AssetManager.
        val text = ProtectionEngine::class.java.getResourceAsStream("/patterns.json")!!
            .bufferedReader().readText()
        return PatternMatcher.fromJson(text)
    }

    private fun importedPackFile() = java.io.File(context.filesDir, "patterns_imported.json")

    // ---- Sensitivity slider (§3 Stage 5) ----

    fun sensitivityName(): String = settingsPrefs.getString("sensitivity", "DEFAULT")!!

    private fun currentSensitivity(): ProtectionEngine.Sensitivity = when (sensitivityName()) {
        "RELAXED" -> ProtectionEngine.Sensitivity.RELAXED
        "STRICT" -> ProtectionEngine.Sensitivity.STRICT
        else -> ProtectionEngine.Sensitivity.DEFAULT
    }

    fun setSensitivity(name: String) {
        settingsPrefs.edit().putString("sensitivity", name).apply()
        engine.updateSensitivity(currentSensitivity())
    }

    // ---- Pattern-pack import (§7.5) ----

    fun hasImportedPatternPack(): Boolean = importedPackFile().exists()

    /**
     * Validate + hot-reload + persist a pattern pack. Returns (version, count)
     * on success. User rules always outrank library patterns, so a bad pack
     * can never override an allow/block decision.
     */
    suspend fun importPatternPack(jsonText: String): Result<Pair<Int, Int>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val matcher = PatternMatcher.fromJson(jsonText)
                require(matcher.library.patterns.isNotEmpty()) { "Pack contains no patterns" }
                importedPackFile().writeText(jsonText)
                engine.updateLibrary(matcher)
                matcher.library.version to matcher.library.patterns.size
            }
        }

    /** Drop the imported pack and hot-reload the bundled library. */
    suspend fun revertToBundledPatterns() = withContext(Dispatchers.IO) {
        importedPackFile().delete()
        engine.updateLibrary(bundledMatcher())
    }

    /** Classify + store an incoming message. Never drops anything (§6). */
    suspend fun onIncomingSms(
        address: String,
        body: String,
        timestamp: Long,
        subId: Int? = null,
    ): Pair<MessageEntity, Verdict> =
        withContext(Dispatchers.IO) {
            // Write to the Telephony provider first — zero message loss.
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                if (subId != null) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
            }
            val uri = try {
                context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
            } catch (_: Exception) {
                null
            }
            val smsId = uri?.lastPathSegment?.toLongOrNull()
            val threadId = try {
                Telephony.Threads.getOrCreateThreadId(context, address)
            } catch (_: Exception) {
                address.hashCode().toLong()
            }

            // §14.2: a classification failure must never make a message invisible.
            // Fall back to Inbox with a normal notification — annoying at worst,
            // never lost. This is shared with MMS and backfill so every intake
            // path preserves the same guarantee.
            val verdict = classifyIncomingOrInbox(address, body, source = "SMS")
            val entity = MessageEntity(
                smsId = smsId,
                threadId = threadId,
                address = address,
                body = body,
                normalizedBody = normalizedOf(body),
                timestamp = timestamp,
                isOutgoing = false,
                subId = subId,
                category = verdict.category.name,
                dangerous = verdict.dangerous,
                fraudWarning = verdict.fraudWarningBanner,
                protectedLabel = verdict.protectedLabel.name,
                score = verdict.score,
                matchedPatternIds = verdict.matchedPatternIds.joinToString(","),
                matchedComboIds = verdict.matchedComboIds.joinToString(","),
                explanations = verdict.explanations.joinToString("\n"),
            )
            val id = db.messages().insert(entity)
            updateConversation(threadId, address, body, timestamp, verdict.category.name, incrementUnread = true)
            entity.copy(id = id) to verdict
        }

    /**
     * Index one pre-existing message from the Telephony provider (first-run
     * backfill, §10). Unlike [onIncomingSms]: no provider write (it's already
     * there), no unread increment, no notification, and the conversation
     * summary is only touched when this message is newer than what's recorded
     * — the backfill walks newest-first, so the first message seen per thread
     * is its latest. Returns false if the smsId was already indexed.
     */
    suspend fun indexHistorical(
        smsId: Long,
        threadId: Long,
        address: String,
        body: String,
        timestamp: Long,
        isOutgoing: Boolean,
        read: Boolean,
    ): Boolean = withContext(Dispatchers.IO) {
        if (db.messages().bySmsId(smsId) != null) return@withContext false
        // Same never-lose fallback as onIncomingSms: a broken classifier must
        // not keep history out of the index (it made the whole backfill vanish
        // on-device once). Backfilled fallbacks stay quiet — no notification.
        val verdict = if (isOutgoing) null else classifyIncomingOrInbox(address, body, source = "backfill")
        val entity = MessageEntity(
            smsId = smsId,
            threadId = threadId,
            address = address,
            body = body,
            normalizedBody = normalizedOf(body),
            timestamp = timestamp,
            isOutgoing = isOutgoing,
            read = read,
            category = verdict?.category?.name ?: "INBOX",
            dangerous = verdict?.dangerous ?: false,
            fraudWarning = verdict?.fraudWarningBanner ?: false,
            protectedLabel = verdict?.protectedLabel?.name ?: "NONE",
            score = verdict?.score ?: 0,
            matchedPatternIds = verdict?.matchedPatternIds?.joinToString(",") ?: "",
            matchedComboIds = verdict?.matchedComboIds?.joinToString(",") ?: "",
            explanations = verdict?.explanations?.joinToString("\n") ?: "",
            sendStatus = if (isOutgoing) "SENT" else "NONE",
        )
        val inserted = db.messages().insert(entity) != -1L
        if (inserted) {
            val existing = db.conversations().byThreadId(threadId)
            if (existing == null || timestamp > existing.lastTimestamp) {
                db.conversations().upsert(
                    ConversationEntity(
                        id = existing?.id ?: 0,
                        threadId = threadId,
                        address = address,
                        contactName = existing?.contactName ?: displayNameFor(address),
                        lastMessage = body,
                        lastTimestamp = timestamp,
                        unreadCount = existing?.unreadCount ?: 0,
                        category = verdict?.category?.name ?: existing?.category ?: "INBOX",
                        pinned = existing?.pinned ?: false,
                        archived = existing?.archived ?: false,
                        muted = existing?.muted ?: false,
                        locked = existing?.locked ?: false,
                        preferredSubId = existing?.preferredSubId,
                    )
                )
            }
        }
        inserted
    }

    /**
     * Classify + store an incoming MMS (parsed by the receiver). Same
     * guarantees as [onIncomingSms]: provider write first, never drops
     * anything. Returns null when this transaction ID was already stored
     * (carriers redeliver un-acked notifications).
     */
    suspend fun onIncomingMms(
        address: String,
        textBody: String,
        timestamp: Long,
        transactionId: String?,
        attachments: List<MmsPduParser.Attachment>,
        /** Actual sender; differs from [address] for group MMS (address = whole group). */
        senderAddress: String = address,
    ): Pair<MessageEntity, Verdict>? = withContext(Dispatchers.IO) {
        if (transactionId != null && db.messages().byMmsTransactionId(transactionId) != null) {
            return@withContext null
        }
        val threadId = resolveThreadId(address)
        // Provider write first — zero message loss.
        val mmsId = storeMmsInProvider(senderAddress, textBody, timestamp, threadId, transactionId, attachments)

        // Keep the first displayable attachment as a local file for the chat UI.
        val media = attachments.firstOrNull {
            it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/") ||
                it.mimeType.startsWith("audio/")
        } ?: attachments.firstOrNull()
        val mediaPath = media?.let { saveAttachmentFile(it, timestamp) }

        // MMS must have the exact same failure posture as SMS. The provider
        // write above already preserved the raw message; this fallback also
        // ensures the Room index, folders, and notification stay reachable if
        // a pattern-pack or classifier bug appears at runtime.
        val verdict = classifyIncomingOrInbox(senderAddress, textBody, source = "MMS")
        val entity = MessageEntity(
            smsId = null,
            mmsId = mmsId,
            mmsTransactionId = transactionId,
            threadId = threadId,
            address = senderAddress,
            body = textBody,
            normalizedBody = normalizedOf(textBody),
            timestamp = timestamp,
            isOutgoing = false,
            category = verdict.category.name,
            dangerous = verdict.dangerous,
            fraudWarning = verdict.fraudWarningBanner,
            protectedLabel = verdict.protectedLabel.name,
            score = verdict.score,
            matchedPatternIds = verdict.matchedPatternIds.joinToString(","),
            matchedComboIds = verdict.matchedComboIds.joinToString(","),
            explanations = verdict.explanations.joinToString("\n"),
            mediaUri = mediaPath,
            mediaMimeType = media?.mimeType,
        )
        val id = db.messages().insert(entity)
        val preview = textBody.ifBlank { mediaPreview(media?.mimeType) }
        updateConversation(threadId, address, preview, timestamp, verdict.category.name, incrementUnread = true)
        entity.copy(id = id) to verdict
    }

    /** Write the retrieved MMS into the Telephony MMS provider (pdu + parts + addr). */
    private fun storeMmsInProvider(
        address: String,
        textBody: String,
        timestamp: Long,
        threadId: Long,
        transactionId: String?,
        attachments: List<MmsPduParser.Attachment>,
    ): Long? {
        try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(Telephony.Mms.THREAD_ID, threadId)
                put(Telephony.Mms.DATE, timestamp / 1000) // MMS provider dates are in seconds
                put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_INBOX)
                put(Telephony.Mms.READ, 0)
                put(Telephony.Mms.SEEN, 0)
                put(Telephony.Mms.MESSAGE_TYPE, 132) // m-retrieve-conf
                put(Telephony.Mms.MMS_VERSION, 0x12)
                put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.related")
                if (transactionId != null) put(Telephony.Mms.TRANSACTION_ID, transactionId)
            }
            val uri = resolver.insert(Telephony.Mms.Inbox.CONTENT_URI, values) ?: return null
            val mmsId = uri.lastPathSegment?.toLongOrNull() ?: return null
            val partUri = android.net.Uri.parse("content://mms/$mmsId/part")

            if (textBody.isNotBlank()) {
                resolver.insert(
                    partUri,
                    ContentValues().apply {
                        put(Telephony.Mms.Part.MSG_ID, mmsId)
                        put(Telephony.Mms.Part.CONTENT_TYPE, "text/plain")
                        put(Telephony.Mms.Part.CHARSET, 106) // utf-8
                        put(Telephony.Mms.Part.TEXT, textBody)
                    },
                )
            }
            attachments.forEach { att ->
                val part = resolver.insert(
                    partUri,
                    ContentValues().apply {
                        put(Telephony.Mms.Part.MSG_ID, mmsId)
                        put(Telephony.Mms.Part.CONTENT_TYPE, att.mimeType)
                        if (att.name != null) put(Telephony.Mms.Part.NAME, att.name)
                    },
                )
                if (part != null) resolver.openOutputStream(part)?.use { it.write(att.data) }
            }
            resolver.insert(
                android.net.Uri.parse("content://mms/$mmsId/addr"),
                ContentValues().apply {
                    put(Telephony.Mms.Addr.MSG_ID, mmsId)
                    put(Telephony.Mms.Addr.ADDRESS, address)
                    put(Telephony.Mms.Addr.TYPE, 137) // PduHeaders.FROM
                    put(Telephony.Mms.Addr.CHARSET, 106)
                },
            )
            return mmsId
        } catch (_: Exception) {
            return null // Room row is still written by the caller — never lost
        }
    }

    private fun saveAttachmentFile(att: MmsPduParser.Attachment, timestamp: Long): String? = try {
        val ext = when {
            att.mimeType.startsWith("image/jpeg") -> "jpg"
            att.mimeType.startsWith("image/png") -> "png"
            att.mimeType.startsWith("image/gif") -> "gif"
            att.mimeType.startsWith("video/") -> "mp4"
            att.mimeType.startsWith("audio/") -> "bin"
            else -> "bin"
        }
        val dir = java.io.File(context.filesDir, "mms_media").apply { mkdirs() }
        // Timestamp + byte-count collides for two attachments received in the
        // same millisecond. A random suffix preserves both files.
        val file = java.io.File(dir, "${timestamp}_${java.util.UUID.randomUUID()}.$ext")
        file.writeBytes(att.data)
        file.absolutePath
    } catch (_: Exception) {
        null
    }

    private fun mediaPreview(mimeType: String?): String = when {
        mimeType == null -> "MMS message"
        mimeType.startsWith("image/") -> "Photo"
        mimeType.startsWith("video/") -> "Video"
        mimeType.startsWith("audio/") -> "Audio message"
        else -> "Attachment"
    }

    /**
     * Resolve (or create) the system thread for an address — used by the
     * new-message compose flow before any message exists on the thread.
     * Group threads: pass addresses joined with ';' (our group convention).
     */
    suspend fun threadIdFor(address: String): Long = withContext(Dispatchers.IO) {
        resolveThreadId(address)
    }

    /** Split a (possibly ';'-joined group) address into individual recipients. */
    fun recipientsOf(address: String): List<String> =
        address.split(';').map { it.trim() }.filter { it.isNotEmpty() }

    private fun resolveThreadId(address: String): Long {
        val recipients = recipientsOf(address)
        return try {
            if (recipients.size > 1) {
                Telephony.Threads.getOrCreateThreadId(context, recipients.toSet())
            } else {
                Telephony.Threads.getOrCreateThreadId(context, address)
            }
        } catch (_: Exception) {
            address.hashCode().toLong()
        }
    }

    suspend fun classify(address: String, body: String): Verdict {
        val isContact = lookupContactName(address) != null
        val reputation = db.reputation().forSender(address)?.score ?: 0
        val firstContact = db.messages().countForAddress(address) == 0
        val senderInfo = SenderAnalyzer.analyze(address, isContact, reputation, firstContact = firstContact)
        val rules = db.userRules().all()
        val allow = rules.any { it.kind == "ALLOW" && matchesRule(it.pattern, address) }
        val block = rules.any { it.kind == "BLOCK" && matchesRule(it.pattern, address) }
        val custom = rules.filter { it.kind == "CUSTOM" }.map {
            ProtectionEngine.UserRule(
                it.id, it.pattern,
                if (it.target == "TEXT") ProtectionEngine.UserRule.Target.TEXT
                else ProtectionEngine.UserRule.Target.SENDER,
                Category.valueOf(it.category),
            )
        }
        return engine.classify(ProtectionEngine.Input(body, senderInfo, allow, block, custom))
    }

    /**
     * A filter outage must never hide a received message. This deliberately
     * catches [Throwable] as engine initialization can fail before Kotlin has
     * an [Exception] (for example, an Android-only regex compilation error).
     */
    private suspend fun classifyIncomingOrInbox(address: String, body: String, source: String): Verdict =
        runCatching { classify(address, body) }.getOrElse { t ->
            android.util.Log.e("MessageRepository", "$source classification failed — defaulting to Inbox", t)
            Verdict(Category.INBOX, explanations = listOf("Classification unavailable — defaulted to Inbox"))
        }

    private fun matchesRule(pattern: String, address: String): Boolean =
        runCatching { Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(address) }
            .getOrDefault(pattern.equals(address, ignoreCase = true))

    /**
     * Store an outgoing MMS before handing it to SmsManager: Telephony MMS
     * provider first (Outbox; moved to Sent by the send-status receiver), then
     * the Room index row the chat UI renders. Mirrors [storeOutgoing] for SMS.
     */
    suspend fun storeOutgoingMms(
        address: String,
        textBody: String,
        timestamp: Long,
        transactionId: String,
        attachment: MmsPduParser.Attachment?,
    ): MessageEntity = withContext(Dispatchers.IO) {
        val recipients = recipientsOf(address)
        val threadId = resolveThreadId(address)
        val mmsId = try {
            val resolver = context.contentResolver
            val values = ContentValues().apply {
                put(Telephony.Mms.THREAD_ID, threadId)
                put(Telephony.Mms.DATE, timestamp / 1000) // MMS provider dates are in seconds
                put(Telephony.Mms.MESSAGE_BOX, Telephony.Mms.MESSAGE_BOX_OUTBOX)
                put(Telephony.Mms.READ, 1)
                put(Telephony.Mms.SEEN, 1)
                put(Telephony.Mms.MESSAGE_TYPE, 128) // m-send-req
                put(Telephony.Mms.MMS_VERSION, 0x12)
                put(Telephony.Mms.CONTENT_TYPE, "application/vnd.wap.multipart.mixed")
                put(Telephony.Mms.TRANSACTION_ID, transactionId)
            }
            val uri = resolver.insert(Telephony.Mms.Outbox.CONTENT_URI, values)
            val id = uri?.lastPathSegment?.toLongOrNull()
            if (id != null) {
                val partUri = android.net.Uri.parse("content://mms/$id/part")
                if (textBody.isNotBlank()) {
                    resolver.insert(
                        partUri,
                        ContentValues().apply {
                            put(Telephony.Mms.Part.MSG_ID, id)
                            put(Telephony.Mms.Part.CONTENT_TYPE, "text/plain")
                            put(Telephony.Mms.Part.CHARSET, 106) // utf-8
                            put(Telephony.Mms.Part.TEXT, textBody)
                        },
                    )
                }
                if (attachment != null) {
                    val part = resolver.insert(
                        partUri,
                        ContentValues().apply {
                            put(Telephony.Mms.Part.MSG_ID, id)
                            put(Telephony.Mms.Part.CONTENT_TYPE, attachment.mimeType)
                            if (attachment.name != null) put(Telephony.Mms.Part.NAME, attachment.name)
                        },
                    )
                    if (part != null) resolver.openOutputStream(part)?.use { it.write(attachment.data) }
                }
                recipients.forEach { recipient ->
                    resolver.insert(
                        android.net.Uri.parse("content://mms/$id/addr"),
                        ContentValues().apply {
                            put(Telephony.Mms.Addr.MSG_ID, id)
                            put(Telephony.Mms.Addr.ADDRESS, recipient)
                            put(Telephony.Mms.Addr.TYPE, 151) // PduHeaders.TO
                            put(Telephony.Mms.Addr.CHARSET, 106)
                        },
                    )
                }
            }
            id
        } catch (_: Exception) {
            null // Room row below still renders the message
        }

        val mediaPath = attachment?.let { saveAttachmentFile(it, timestamp) }
        val entity = MessageEntity(
            smsId = null,
            mmsId = mmsId,
            mmsTransactionId = transactionId,
            threadId = threadId,
            address = address,
            body = textBody,
            normalizedBody = normalizedOf(textBody),
            timestamp = timestamp,
            isOutgoing = true,
            read = true,
            mediaUri = mediaPath,
            mediaMimeType = attachment?.mimeType,
            sendStatus = "SENDING",
        )
        val id = db.messages().insert(entity)
        val preview = textBody.ifBlank { mediaPreview(attachment?.mimeType) }
        updateConversation(threadId, address, preview, timestamp, category = null, incrementUnread = false)
        entity.copy(id = id)
    }

    /** Move a sent/failed outgoing MMS to the right provider box + index status. */
    suspend fun onMmsSendResult(messageId: Long, success: Boolean) = withContext(Dispatchers.IO) {
        val msg = db.messages().byId(messageId) ?: return@withContext
        db.messages().update(msg.copy(sendStatus = if (success) "SENT" else "FAILED"))
        val mmsId = msg.mmsId ?: return@withContext
        try {
            context.contentResolver.update(
                android.net.Uri.parse("content://mms/$mmsId"),
                ContentValues().apply {
                    put(
                        Telephony.Mms.MESSAGE_BOX,
                        if (success) Telephony.Mms.MESSAGE_BOX_SENT
                        else Telephony.Mms.MESSAGE_BOX_FAILED,
                    )
                },
                null, null,
            )
        } catch (_: Exception) {
            // provider box update is best-effort; the index row carries status
        }
    }

    suspend fun storeOutgoing(
        address: String,
        body: String,
        timestamp: Long,
        subId: Int? = null,
    ): MessageEntity =
        withContext(Dispatchers.IO) {
            val threadId = resolveThreadId(address)
            val firstSmsId = writeOutgoingSmsToProvider(address, body, timestamp, threadId, subId)
            val entity = MessageEntity(
                smsId = firstSmsId,
                threadId = threadId,
                address = address,
                body = body,
                normalizedBody = normalizedOf(body),
                timestamp = timestamp,
                isOutgoing = true,
                read = true,
                sendStatus = "SENDING",
                subId = subId,
            )
            val id = db.messages().insert(entity)
            updateConversation(threadId, address, body, timestamp, category = null, incrementUnread = false)
            entity.copy(id = id)
        }

    /** Group SMS: one provider Sent row per recipient, all pinned to the thread. */
    private fun writeOutgoingSmsToProvider(
        address: String,
        body: String,
        timestamp: Long,
        threadId: Long,
        subId: Int?,
    ): Long? {
        var firstSmsId: Long? = null
        recipientsOf(address).forEach { recipient ->
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, recipient)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                put(Telephony.Sms.THREAD_ID, threadId)
                if (subId != null) put(Telephony.Sms.SUBSCRIPTION_ID, subId)
            }
            val uri = try {
                context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            } catch (_: Exception) {
                null
            }
            if (firstSmsId == null) firstSmsId = uri?.lastPathSegment?.toLongOrNull()
        }
        return firstSmsId
    }

    /**
     * Scheduled send (§8.2), step 1: store the message in the local index only
     * — NOT the Telephony provider (it hasn't been sent; other SMS apps must
     * not see it as sent). `timestamp` = the scheduled fire time so the bubble
     * sits at the right place in the chat; the provider row is written when
     * [promoteScheduledToSending] fires.
     */
    suspend fun storeScheduledSms(
        address: String,
        body: String,
        sendAt: Long,
        subId: Int? = null,
    ): MessageEntity = withContext(Dispatchers.IO) {
        val threadId = resolveThreadId(address)
        val entity = MessageEntity(
            threadId = threadId,
            address = address,
            body = body,
            normalizedBody = normalizedOf(body),
            timestamp = sendAt,
            isOutgoing = true,
            read = true,
            sendStatus = "SCHEDULED",
            subId = subId,
        )
        val id = db.messages().insert(entity)
        updateConversation(threadId, address, body, sendAt, category = null, incrementUnread = false)
        entity.copy(id = id)
    }

    /**
     * Scheduled send, step 2 (worker fire time or "Send now"): write the
     * provider Sent row(s), flip the index row to SENDING with the real send
     * time. Returns the updated entity for the radio send, or null when the
     * message was cancelled/already promoted meanwhile.
     */
    suspend fun promoteScheduledToSending(messageId: Long): MessageEntity? =
        withContext(Dispatchers.IO) {
            val msg = db.messages().byId(messageId) ?: return@withContext null
            if (msg.sendStatus != "SCHEDULED") return@withContext null
            val now = System.currentTimeMillis()
            val smsId = writeOutgoingSmsToProvider(msg.address, msg.body, now, msg.threadId, msg.subId)
            val updated = msg.copy(smsId = smsId, timestamp = now, sendStatus = "SENDING")
            db.messages().update(updated)
            updateConversation(
                msg.threadId, msg.address, msg.body, now,
                category = null, incrementUnread = false,
            )
            updated
        }

    /** Cancel a scheduled message: remove its index row (it was never in the provider). */
    suspend fun cancelScheduled(messageId: Long) = withContext(Dispatchers.IO) {
        val msg = db.messages().byId(messageId) ?: return@withContext
        if (msg.sendStatus != "SCHEDULED") return@withContext
        db.messages().userDelete(messageId)
        refreshConversationSummary(msg.threadId)
    }

    private suspend fun updateConversation(
        threadId: Long,
        address: String,
        lastMessage: String,
        timestamp: Long,
        category: String?,
        incrementUnread: Boolean,
    ) {
        val existing = db.conversations().byThreadId(threadId)
        val name = displayNameFor(address)
        db.conversations().upsert(
            ConversationEntity(
                id = existing?.id ?: 0,
                threadId = threadId,
                address = address,
                contactName = name,
                lastMessage = lastMessage,
                lastTimestamp = timestamp,
                unreadCount = (existing?.unreadCount ?: 0) + if (incrementUnread) 1 else 0,
                category = category ?: existing?.category ?: "INBOX",
                pinned = existing?.pinned ?: false,
                archived = false, // new message unarchives
                muted = existing?.muted ?: false,
                locked = existing?.locked ?: false,
                preferredSubId = existing?.preferredSubId,
            )
        )
    }

    /** Group addresses (';'-joined) resolve each member; singles use PhoneLookup. */
    fun displayNameFor(address: String): String? {
        val recipients = recipientsOf(address)
        if (recipients.size <= 1) return lookupContactName(address)
        return recipients.joinToString(", ") { lookupContactName(it) ?: it }
    }

    fun lookupContactName(address: String): String? = lookupContact(address)?.name

    /** Name + photo + lookup key in one PhoneLookup query (photo for avatars,
     *  lookup key for the "View in Contacts" intent on the detail page). */
    data class ContactHit(val name: String, val photoUri: String?, val lookupKey: String?)

    fun lookupContact(address: String): ContactHit? = try {
        val uri = android.net.Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(address),
        )
        context.contentResolver.query(
            uri,
            arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME,
                ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
                ContactsContract.PhoneLookup.LOOKUP_KEY,
            ),
            null, null, null,
        )?.use { c ->
            if (c.moveToFirst() && c.getString(0) != null) {
                ContactHit(c.getString(0), c.getString(1), c.getString(2))
            } else null
        }
    } catch (_: Exception) {
        null
    }

    /**
     * Re-resolve contact names for every conversation row (newly saved,
     * renamed, or deleted contacts must update existing threads). Called on
     * app foreground and from the ContactsContract observer. Names cached on
     * rows before READ_CONTACTS was effective (or before the contacts-provider
     * visibility fix) heal here. Returns the number of rows changed.
     */
    suspend fun refreshContactNames(): Int = withContext(Dispatchers.IO) {
        var changed = 0
        for (conv in db.conversations().allConversations()) {
            val fresh = try {
                displayNameFor(conv.address)
            } catch (_: Exception) {
                continue
            }
            if (fresh != conv.contactName) {
                db.conversations().setContactName(conv.threadId, fresh)
                changed++
            }
        }
        changed
    }

    /** "Not spam / Move to Inbox" — reclassify + local trust boost (§6.3). */
    suspend fun moveToInbox(messageId: Long) = withContext(Dispatchers.IO) {
        val msg = db.messages().byId(messageId) ?: return@withContext
        db.messages().recategorize(messageId, "INBOX")
        adjustReputation(msg.address, delta = +2, notSpam = true)
        val conv = db.conversations().byThreadId(msg.threadId)
        if (conv != null) db.conversations().upsert(conv.copy(category = "INBOX"))
    }

    /** "Mark spam" — reclassify + distrust the sender. */
    suspend fun moveToSpam(messageId: Long) = withContext(Dispatchers.IO) {
        val msg = db.messages().byId(messageId) ?: return@withContext
        db.messages().recategorize(messageId, "SPAM")
        adjustReputation(msg.address, delta = -2, notSpam = false)
        val conv = db.conversations().byThreadId(msg.threadId)
        if (conv != null) db.conversations().upsert(conv.copy(category = "SPAM"))
    }

    // ---- Trash (§6.4) ----

    /**
     * User delete: remove the Telephony-provider row (the message disappears
     * from the phone normally) but keep the index row flagged as trash,
     * restorable for [TrashRetention.RETENTION_MS]. Scheduled drafts are the
     * exception — they were never sent or in the provider, so cancelling one
     * deletes it outright (nothing to retain).
     */
    suspend fun moveToTrash(messageId: Long) = withContext(Dispatchers.IO) {
        val msg = db.messages().byId(messageId) ?: return@withContext
        if (msg.sendStatus == "SCHEDULED") {
            db.messages().userDelete(messageId)
        } else {
            deleteProviderRow(msg)
            db.messages().moveToTrash(messageId, System.currentTimeMillis())
        }
        refreshConversationSummary(msg.threadId)
    }

    /**
     * Undo for a just-trashed conversation (swipe-delete snackbar): restore
     * every message of the thread trashed at/after [trashedAfter].
     */
    suspend fun restoreThreadFromTrash(threadId: Long, trashedAfter: Long) =
        withContext(Dispatchers.IO) {
            db.messages().trashedIdsForThread(threadId, trashedAfter).forEach {
                restoreFromTrash(it)
            }
        }

    /** Delete a whole conversation to Trash (§6.4). */
    suspend fun moveThreadToTrash(threadId: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        db.messages().listForThread(threadId).forEach { msg ->
            if (msg.sendStatus == "SCHEDULED") db.messages().userDelete(msg.id)
            else deleteProviderRow(msg)
        }
        db.messages().moveThreadToTrash(threadId, now)
        refreshConversationSummary(threadId) // no live messages left → row is dropped
    }

    /**
     * Restore from Trash within the 60-day window: clear the flag and write
     * the message back into the Telephony provider with its original
     * timestamp (best-effort — needs the default-SMS role; the index row is
     * restored either way). MMS rows come back index-only: their local media
     * file was kept, the provider PDU was not.
     */
    suspend fun restoreFromTrash(messageId: Long) = withContext(Dispatchers.IO) {
        val msg = db.messages().byId(messageId) ?: return@withContext
        if (!msg.trashed) return@withContext
        var restored = msg.copy(trashed = false, trashedAt = null)
        if (msg.mmsId == null && msg.mmsTransactionId == null) {
            val smsId = try {
                val uri = if (msg.isOutgoing) Telephony.Sms.Sent.CONTENT_URI
                else Telephony.Sms.Inbox.CONTENT_URI
                context.contentResolver.insert(
                    uri,
                    ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, msg.address)
                        put(Telephony.Sms.BODY, msg.body)
                        put(Telephony.Sms.DATE, msg.timestamp)
                        put(Telephony.Sms.READ, if (msg.read) 1 else 0)
                        put(Telephony.Sms.THREAD_ID, msg.threadId)
                        put(
                            Telephony.Sms.TYPE,
                            if (msg.isOutgoing) Telephony.Sms.MESSAGE_TYPE_SENT
                            else Telephony.Sms.MESSAGE_TYPE_INBOX,
                        )
                        if (msg.subId != null) put(Telephony.Sms.SUBSCRIPTION_ID, msg.subId)
                    },
                )?.lastPathSegment?.toLongOrNull()
            } catch (_: Exception) {
                null
            }
            restored = restored.copy(smsId = smsId)
        } else {
            restored = restored.copy(mmsId = null) // old provider row is gone
        }
        db.messages().update(restored)
        // Rebuild the conversation row (it may have been dropped when the
        // thread emptied) without disturbing unread counts.
        val conv = db.conversations().byThreadId(msg.threadId)
        val latest = db.messages().latestForThread(msg.threadId)
        if (latest != null && (conv == null || latest.timestamp >= conv.lastTimestamp)) {
            db.conversations().upsert(
                ConversationEntity(
                    id = conv?.id ?: 0,
                    threadId = msg.threadId,
                    address = conv?.address ?: msg.address,
                    contactName = conv?.contactName ?: displayNameFor(msg.address),
                    lastMessage = latest.body.ifBlank { mediaPreview(latest.mediaMimeType) },
                    lastTimestamp = latest.timestamp,
                    unreadCount = conv?.unreadCount ?: 0,
                    category = conv?.category ?: latest.category,
                    pinned = conv?.pinned ?: false,
                    archived = conv?.archived ?: false,
                    muted = conv?.muted ?: false,
                    locked = conv?.locked ?: false,
                    preferredSubId = conv?.preferredSubId,
                )
            )
        }
    }

    /** "Delete forever" from within Trash — immediate permanent deletion. */
    suspend fun deleteForever(messageId: Long) = withContext(Dispatchers.IO) {
        val msg = db.messages().byId(messageId) ?: return@withContext
        deleteLocalMedia(msg)
        db.messages().userDelete(messageId)
    }

    /** The 60-day purge (§6.4), run by [com.messages.core.trash.TrashPurge]. */
    suspend fun purgeExpiredTrash(): Int = withContext(Dispatchers.IO) {
        val expired = db.messages().trashExpiredBefore(
            System.currentTimeMillis() - TrashRetention.RETENTION_MS
        )
        expired.forEach { msg ->
            deleteLocalMedia(msg)
            db.messages().userDelete(msg.id)
        }
        expired.size
    }

    /** Remove the message's row from the system SMS/MMS provider (best-effort). */
    private fun deleteProviderRow(msg: MessageEntity) {
        try {
            when {
                msg.smsId != null -> context.contentResolver.delete(
                    android.net.Uri.parse("content://sms/${msg.smsId}"), null, null
                )
                msg.mmsId != null -> context.contentResolver.delete(
                    android.net.Uri.parse("content://mms/${msg.mmsId}"), null, null
                )
            }
        } catch (_: Exception) {
            // Needs the default-SMS role; the index-side trash flag still applies.
        }
    }

    private fun deleteLocalMedia(msg: MessageEntity) {
        msg.mediaUri?.let { path -> runCatching { java.io.File(path).delete() } }
    }

    /**
     * User-ENABLED OTP cleanup (§6.6/§8.2): delete OTP-labeled Inbox messages
     * older than [olderThanMs]. Deliberately BYPASSES Trash (expired OTPs have
     * no recovery value, per §6.6); the DAO query itself restricts it to
     * OTP + Inbox + unstarred + untrashed, so filtered folders and the Trash
     * can never be touched. Removes the Telephony provider row too
     * (best-effort — requires default-SMS role) and keeps conversation
     * summaries consistent. Returns how many messages were deleted.
     */
    suspend fun cleanupExpiredOtps(olderThanMs: Long): Int = withContext(Dispatchers.IO) {
        val expired = db.messages().expiredOtps(System.currentTimeMillis() - olderThanMs)
        expired.forEach { msg ->
            deleteProviderRow(msg)
            db.messages().userDelete(msg.id)
        }
        expired.map { it.threadId }.distinct().forEach { refreshConversationSummary(it) }
        expired.size
    }

    /**
     * User-ENABLED Spam cleanup: delete Spam/Blocked messages older than [olderThanMs].
     * Bypasses Trash, permanently deleting them.
     */
    /**
     * §6.5 auto-clean: expired Spam goes THROUGH TRASH like any user deletion
     * (60-day restore window), never straight to oblivion. The DAO query is
     * scoped to SPAM only — Review/Blocked untouchable — and skips starred.
     */
    suspend fun cleanupExpiredSpam(olderThanMs: Long): Int = withContext(Dispatchers.IO) {
        val expired = db.messages().expiredSpam(System.currentTimeMillis() - olderThanMs)
        val now = System.currentTimeMillis()
        expired.forEach { msg ->
            deleteProviderRow(msg)
            db.messages().moveToTrash(msg.id, now)
        }
        expired.map { it.threadId }.distinct().forEach { refreshConversationSummary(it) }
        expired.size
    }

    /** Recompute a conversation's summary after deletions; drop it if empty. */
    suspend fun refreshConversationSummary(threadId: Long) {
        val conv = db.conversations().byThreadId(threadId) ?: return
        val latest = db.messages().latestForThread(threadId)
        if (latest == null) {
            db.conversations().deleteByThreadId(threadId)
        } else if (latest.timestamp != conv.lastTimestamp || latest.body != conv.lastMessage) {
            db.conversations().upsert(
                conv.copy(
                    lastMessage = latest.body.ifBlank { mediaPreview(latest.mediaMimeType) },
                    lastTimestamp = latest.timestamp,
                )
            )
        }
    }

    private suspend fun adjustReputation(address: String, delta: Int, notSpam: Boolean) {
        val current = db.reputation().forSender(address)
        db.reputation().upsert(
            SenderReputationEntity(
                id = current?.id ?: 0,
                address = address,
                score = (current?.score ?: 0) + delta,
                userMarkedSpamCount = (current?.userMarkedSpamCount ?: 0) + if (!notSpam) 1 else 0,
                userMarkedNotSpamCount = (current?.userMarkedNotSpamCount ?: 0) + if (notSpam) 1 else 0,
            )
        )
    }

    companion object {
        @Volatile private var instance: MessageRepository? = null
        fun get(context: Context): MessageRepository =
            instance ?: synchronized(this) {
                instance ?: MessageRepository(context.applicationContext).also { instance = it }
            }
    }
}
