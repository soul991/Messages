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
import com.messages.protection.Category
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
    ).fallbackToDestructiveMigration().build()

    private val settingsPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

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
    suspend fun onIncomingSms(address: String, body: String, timestamp: Long): Pair<MessageEntity, Verdict> =
        withContext(Dispatchers.IO) {
            // Write to the Telephony provider first — zero message loss.
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.READ, 0)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
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

            val verdict = classify(address, body)
            val entity = MessageEntity(
                smsId = smsId,
                threadId = threadId,
                address = address,
                body = body,
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
        val verdict = if (isOutgoing) null else classify(address, body)
        val entity = MessageEntity(
            smsId = smsId,
            threadId = threadId,
            address = address,
            body = body,
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
                        contactName = existing?.contactName ?: lookupContactName(address),
                        lastMessage = body,
                        lastTimestamp = timestamp,
                        unreadCount = existing?.unreadCount ?: 0,
                        category = verdict?.category?.name ?: existing?.category ?: "INBOX",
                        pinned = existing?.pinned ?: false,
                        archived = existing?.archived ?: false,
                        muted = existing?.muted ?: false,
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
    ): Pair<MessageEntity, Verdict>? = withContext(Dispatchers.IO) {
        if (transactionId != null && db.messages().byMmsTransactionId(transactionId) != null) {
            return@withContext null
        }
        val threadId = try {
            Telephony.Threads.getOrCreateThreadId(context, address)
        } catch (_: Exception) {
            address.hashCode().toLong()
        }
        // Provider write first — zero message loss.
        val mmsId = storeMmsInProvider(address, textBody, timestamp, threadId, transactionId, attachments)

        // Keep the first displayable attachment as a local file for the chat UI.
        val media = attachments.firstOrNull {
            it.mimeType.startsWith("image/") || it.mimeType.startsWith("video/") ||
                it.mimeType.startsWith("audio/")
        } ?: attachments.firstOrNull()
        val mediaPath = media?.let { saveAttachmentFile(it, timestamp) }

        val verdict = classify(address, textBody)
        val entity = MessageEntity(
            smsId = null,
            mmsId = mmsId,
            mmsTransactionId = transactionId,
            threadId = threadId,
            address = address,
            body = textBody,
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
        val file = java.io.File(dir, "${timestamp}_${att.data.size}.$ext")
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
     */
    suspend fun threadIdFor(address: String): Long = withContext(Dispatchers.IO) {
        try {
            Telephony.Threads.getOrCreateThreadId(context, address)
        } catch (_: Exception) {
            address.hashCode().toLong()
        }
    }

    suspend fun classify(address: String, body: String): Verdict {
        val isContact = lookupContactName(address) != null
        val reputation = db.reputation().forSender(address)?.score ?: 0
        val senderInfo = SenderAnalyzer.analyze(address, isContact, reputation)
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
        val threadId = try {
            Telephony.Threads.getOrCreateThreadId(context, address)
        } catch (_: Exception) {
            address.hashCode().toLong()
        }
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
                resolver.insert(
                    android.net.Uri.parse("content://mms/$id/addr"),
                    ContentValues().apply {
                        put(Telephony.Mms.Addr.MSG_ID, id)
                        put(Telephony.Mms.Addr.ADDRESS, address)
                        put(Telephony.Mms.Addr.TYPE, 151) // PduHeaders.TO
                        put(Telephony.Mms.Addr.CHARSET, 106)
                    },
                )
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

    suspend fun storeOutgoing(address: String, body: String, timestamp: Long): MessageEntity =
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, address)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.READ, 1)
                put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
            }
            val uri = try {
                context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
            } catch (_: Exception) {
                null
            }
            val threadId = try {
                Telephony.Threads.getOrCreateThreadId(context, address)
            } catch (_: Exception) {
                address.hashCode().toLong()
            }
            val entity = MessageEntity(
                smsId = uri?.lastPathSegment?.toLongOrNull(),
                threadId = threadId,
                address = address,
                body = body,
                timestamp = timestamp,
                isOutgoing = true,
                read = true,
                sendStatus = "SENDING",
            )
            val id = db.messages().insert(entity)
            updateConversation(threadId, address, body, timestamp, category = null, incrementUnread = false)
            entity.copy(id = id)
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
        val name = lookupContactName(address)
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
            )
        )
    }

    fun lookupContactName(address: String): String? = try {
        val uri = android.net.Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            android.net.Uri.encode(address),
        )
        context.contentResolver.query(
            uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
        )?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    } catch (_: Exception) {
        null
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
