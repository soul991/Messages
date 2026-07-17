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

    val engine: ProtectionEngine by lazy {
        val text = context.assets.open("patterns.json").bufferedReader().readText()
        ProtectionEngine(PatternMatcher.fromJson(text))
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
            val smsId = uri?.lastPathSegment?.toLongOrNull() ?: -1L
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
                smsId = uri?.lastPathSegment?.toLongOrNull() ?: -1L,
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
