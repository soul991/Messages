package com.messages.core

import android.content.ContentValues
import android.content.Context
import android.provider.ContactsContract
import android.provider.Telephony
import androidx.room.Room
import com.messages.core.db.AttemptState
import com.messages.core.db.ConversationEntity
import com.messages.core.db.MessageEntity
import com.messages.core.db.MessagesDatabase
import com.messages.core.db.ProviderRowEntity
import com.messages.core.db.SenderReputationEntity
import com.messages.core.db.SmsAttemptEntity
import com.messages.core.db.Spaces
import com.messages.core.db.ThreadAliasEntity
import com.messages.core.media.MediaRef
import com.messages.core.mms.MmsPduParser
import com.messages.core.search.MessageSearch
import com.messages.core.secret.LockedContent
import com.messages.core.secret.LockedSealException
import com.messages.core.secret.LockedWriteBlockedException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.messages.core.send.SendAggregate
import com.messages.core.send.SendRetry
import com.messages.core.trash.TrashRetention
import com.messages.protection.Category
import com.messages.protection.Normalizer
import com.messages.protection.PatternMatcher
import com.messages.protection.PatternPackPolicy
import com.messages.protection.ProtectionEngine
import com.messages.protection.SafeRegexPolicy
import com.messages.protection.containsMatchWithin
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
        // R-25: NO destructive fallback at any version. Every schema from v1
        // forward has a real migration in Migrations.ALL, so a missing one is a
        // loud crash in testing rather than a silent wipe of a user's
        // categories, rules, reputation and per-conversation preferences —
        // none of which the Telephony provider can give back.
        .build()

    private val settingsPrefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    /** §8.5 multi-keyword FTS search. */
    val search: MessageSearch by lazy { MessageSearch(db.messages()) }

    // ---- V2-6: locked-space text is sealed on the way in, opened on the way out ----
    //
    // These two are deliberately unconditional and total. `sealed()` decides for
    // itself whether the row is locked, and `opened()` is a no-op for anything
    // not carrying the marker — so a write path that forgets one is the only bug
    // shape possible here, and it shows up immediately as a base64 blob in the
    // chat rather than as a silent plaintext leak nobody notices.

    // open-then-seal, not seal: a row moving OUT of the locked space arrives
    // here still carrying ciphertext, and a plain seal() would leave it that way
    // forever because the row is no longer locked. Open first and the same call
    // is correct in both directions and idempotent in both.

    private fun MessageEntity.sealed(): MessageEntity =
        LockedContent.seal(context, LockedContent.open(context, this))

    private fun MessageEntity.opened(): MessageEntity = LockedContent.open(context, this)

    private fun ConversationEntity.sealed(): ConversationEntity =
        LockedContent.seal(context, LockedContent.open(context, this))

    private fun ConversationEntity.opened(): ConversationEntity = LockedContent.open(context, this)

    /**
     * V2-6b: user-initiated locked-space writes are refused while the content
     * key is unavailable, rather than accepted at the degraded pending grade.
     * The user is present to see the error and retry; arriving mail — which
     * nobody can refuse — is the only thing that degrades (see [onIncomingSms]).
     */
    private fun requireLockedWrites() {
        if (!LockedContent.available(context)) throw LockedWriteBlockedException()
    }

    /** Stage-0 normalization for the FTS index (§8.5) — never fails the caller. */
    fun normalizedOf(body: String): String =
        runCatching { Normalizer.normalize(body).normalizedText }
            .getOrDefault(body.lowercase())
            .ifBlank { body.lowercase() }

    val engine: ProtectionEngine by lazy {
        // An imported pattern pack (§7.5) overrides the bundled library.
        //
        // R-21: re-validate on every load, not just at import time. A pack that
        // predates the policy — or that was written when the limits were looser
        // — would otherwise keep being applied on the intake path forever, and
        // this is the only place that can catch it. A pack that fails now is
        // dropped and the bundled library takes over, which is the same outcome
        // as the user never having imported it.
        val imported = runCatching {
            val f = importedPackFile()
            if (!f.exists()) return@runCatching null
            if (f.length() > PatternPackPolicy.MAX_PACK_BYTES) {
                f.delete()
                return@runCatching null
            }
            PatternMatcher.fromJson(f.readText(), validate = true)
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
                // R-21: validate BEFORE persisting. A pack that fails policy
                // must not reach disk — otherwise it is reloaded and re-applied
                // on the next cold start, past the point where we can report it.
                val matcher = PatternMatcher.fromJson(jsonText, validate = true)
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

    /**
     * Outcome of an intake. Destructures as `(message, verdict)` like the Pair
     * it replaces; [isNew] is false when this exact message was already
     * indexed, which tells the caller NOT to notify again (R-16).
     */
    data class Intake(
        val message: MessageEntity,
        val verdict: Verdict,
        val isNew: Boolean = true,
    )

    /** Classify + store an incoming message. Never drops anything (§6). */
    suspend fun onIncomingSms(
        address: String,
        body: String,
        timestamp: Long,
        subId: Int? = null,
    ): Intake =
        withContext(Dispatchers.IO) {
            // R-16: a redelivered SMS_DELIVER broadcast carries the same sender,
            // carrier timestamp and body but earns a fresh provider _id, so
            // neither the smsId unique index nor the provider stops it. Detect
            // it up front: no second provider row, no second index row, no
            // inflated unread count, no duplicate notification.
            val existing = db.messages().findIncomingDuplicate(address, timestamp, body)
            // V2-6: sealed locked bodies cannot be matched by the SQL above.
            // V2-6b: a TOMBSTONE (apocalypse-corner row, text withheld) cannot
            // body-compare at all — match it by identity instead: same sender,
            // same carrier millisecond, an SMS row with no text.
                ?: db.messages().lockedIncomingAt(address, timestamp)
                    .firstOrNull {
                        it.opened().body == body || (it.smsId != null && it.body.isEmpty())
                    }
            if (existing != null) {
                return@withContext Intake(
                    existing,
                    classifyIncomingOrInbox(address, body, source = "SMS"),
                    isNew = false,
                )
            }

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
                syntheticThreadId(address) // R-16: never a 32-bit hash
            }

            // §14.2: a classification failure must never make a message invisible.
            // Fall back to Inbox with a normal notification — annoying at worst,
            // never lost. This is shared with MMS and backfill so every intake
            // path preserves the same guarantee.
            val verdict = classifyIncomingOrInbox(address, body, source = "SMS")
            // Secret-space routing rule: once a LOCKED conversation exists for
            // this thread, ALL incoming messages from the address go to it —
            // never the normal thread (a visible incoming message would betray
            // the space). The classifier ran normally above: spam from a
            // locked sender files into the locked space's own folders.
            val space = spaceForIncoming(threadId)
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
                space = space,
            )
            // V2-6b: sealing is fail-closed. In the worst corner — Keystore
            // down AND the fallback key unusable — the row is stored as a
            // TOMBSTONE: empty text, provider copy kept as the only copy for
            // sealLockedBacklog to recover from. Never plaintext.
            val sealedEntity = try {
                entity.sealed()
            } catch (_: LockedSealException) {
                null
            }
            val id = db.messages().insert(
                sealedEntity ?: entity.copy(body = "", normalizedBody = "")
            )
            // R-16: IGNORE-on-conflict returns -1 when the smsId unique index
            // rejected the row. Nothing was stored, so nothing may be counted.
            if (id == -1L) {
                val stored = smsId?.let { db.messages().bySmsId(it)?.opened() }
                return@withContext Intake(stored ?: entity, verdict, isNew = false)
            }
            if (smsId != null) {
                db.providerRows().upsert(
                    ProviderRowEntity("content://sms/$smsId", id, address, "SMS")
                )
            }
            // The mapping is written first even for a locked message: the purge
            // below deletes through it, and if the purge fails the mapping is
            // what lets retryFailedProviderDeletions try again later.
            // A tombstone skips the purge — its provider row IS the message.
            if (sealedEntity != null) purgeProviderCopyIfLocked(entity.copy(id = id))
            val preview = if (sealedEntity != null) body else ""
            try {
                updateConversation(threadId, address, preview, timestamp, verdict.category.name, incrementUnread = true, space = space)
            } catch (_: LockedSealException) {
                // Same corner racing the preview seal: record activity, not content.
                updateConversation(threadId, address, "", timestamp, verdict.category.name, incrementUnread = true, space = space)
            }
            Intake(entity.copy(id = id), verdict)
        }

    /**
     * The routing rule's decision point: a LOCKED conversation row for this
     * thread claims every incoming message. Cheap fast path — the COUNT query
     * short-circuits to nothing on devices that never set up the space.
     */
    /**
     * V2-6: a message that arrived directly into the locked space was written to
     * the Telephony provider before anything knew where it belonged (the SMS
     * role obliges us to store it). Remove that copy now.
     */
    private suspend fun purgeProviderCopyIfLocked(message: MessageEntity) {
        if (message.space == Spaces.LOCKED) deleteProviderRows(message)
    }

    private suspend fun spaceForIncoming(threadId: Long): String =
        if (db.conversations().lockedConversationCount() > 0 &&
            db.conversations().byThreadId(threadId, Spaces.LOCKED) != null
        ) Spaces.LOCKED else Spaces.NORMAL

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
        indexHistoricalRow(
            smsId = smsId, mmsId = null, threadId = threadId, address = address, body = body,
            timestamp = timestamp, isOutgoing = isOutgoing, read = read,
            mediaRef = null, mediaMimeType = null,
        )
    }

    /**
     * V2-25: the MMS half of the backfill. Same contract as [indexHistorical] —
     * no provider write, no unread bump, no notification — keyed on the
     * provider's MMS `_id` for dedupe.
     *
     * [mediaRef] is a `content://mms/part/<id>` URI, not a local file: the
     * bytes already exist in the Telephony provider and copying an entire MMS
     * history into app storage could mean gigabytes. Rows written by the live
     * receive path still carry a real file path — see `MediaRef`, which is what
     * every consumer uses to tell the two apart.
     */
    suspend fun indexHistoricalMms(
        mmsId: Long,
        threadId: Long,
        address: String,
        body: String,
        timestamp: Long,
        isOutgoing: Boolean,
        read: Boolean,
        mediaRef: String?,
        mediaMimeType: String?,
    ): Boolean = withContext(Dispatchers.IO) {
        if (db.messages().byMmsId(mmsId) != null) return@withContext false
        indexHistoricalRow(
            smsId = null, mmsId = mmsId, threadId = threadId, address = address, body = body,
            timestamp = timestamp, isOutgoing = isOutgoing, read = read,
            mediaRef = mediaRef, mediaMimeType = mediaMimeType,
        )
    }

    private suspend fun indexHistoricalRow(
        smsId: Long?,
        mmsId: Long?,
        threadId: Long,
        address: String,
        body: String,
        timestamp: Long,
        isOutgoing: Boolean,
        read: Boolean,
        mediaRef: String?,
        mediaMimeType: String?,
    ): Boolean {
        // Same never-lose fallback as onIncomingSms: a broken classifier must
        // not keep history out of the index (it made the whole backfill vanish
        // on-device once). Backfilled fallbacks stay quiet — no notification.
        val verdict = if (isOutgoing) null else classifyIncomingOrInbox(address, body, source = "backfill")
        val entity = MessageEntity(
            smsId = smsId,
            mmsId = mmsId,
            mediaUri = mediaRef,
            mediaMimeType = mediaMimeType,
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
        val inserted = db.messages().insert(entity.sealed()) != -1L
        if (inserted) {
            // Backfill always indexes into the NORMAL space: it walks the
            // shared Telephony provider (historical rows predate any locking;
            // rows already claimed by the locked space are skipped above by
            // their smsId/mmsId). New arrivals route through onIncomingSms instead.
            val existing = db.conversations().byThreadId(threadId)
            if (existing == null || timestamp > existing.lastTimestamp) {
                db.conversations().upsert(
                    ConversationEntity(
                        id = existing?.id ?: 0,
                        threadId = threadId,
                        address = address,
                        contactName = existing?.contactName ?: displayNameFor(address),
                        // A picture message with no caption gets the same
                        // "Photo"/"Attachment" summary the live path uses,
                        // instead of an empty Home row.
                        lastMessage = body.ifBlank { mediaPreview(mediaMimeType) },
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
        return inserted
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
        // Same secret-space routing rule as SMS — an incoming MMS from a
        // locked address must never surface in the normal thread either.
        val space = spaceForIncoming(threadId)
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
            space = space,
        )
        // V2-6b: fail-closed, same as SMS — the apocalypse corner stores a
        // TOMBSTONE (empty text, provider copy kept), never plaintext.
        val sealedEntity = try {
            entity.sealed()
        } catch (_: LockedSealException) {
            null
        }
        val id = db.messages().insert(
            sealedEntity ?: entity.copy(body = "", normalizedBody = "")
        )
        // R-16: an IGNOREd insert stored nothing, so nothing may be counted or
        // notified for it — the same rule the SMS intake follows.
        if (id == -1L) return@withContext null
        // R-05: the provider row this message owns, so a later delete can reach
        // it even if `mmsId` is ever cleared.
        mmsId?.let {
            db.providerRows().upsert(
                ProviderRowEntity(
                    uri = "content://mms/$it",
                    messageId = id,
                    recipient = senderAddress,
                    kind = "MMS",
                )
            )
        }
        val preview =
            if (sealedEntity != null) textBody.ifBlank { mediaPreview(media?.mimeType) } else ""
        if (sealedEntity != null) purgeProviderCopyIfLocked(entity.copy(id = id))
        try {
            updateConversation(threadId, address, preview, timestamp, verdict.category.name, incrementUnread = true, space = space)
        } catch (_: LockedSealException) {
            updateConversation(threadId, address, "", timestamp, verdict.category.name, incrementUnread = true, space = space)
        }
        entity.copy(id = id) to verdict
    }

    /**
     * Write the retrieved MMS into the Telephony MMS provider (pdu + parts + addr).
     *
     * R-16: this is a multi-step write. If any step after the PDU insert fails,
     * the PDU — and whatever parts/addresses did land — is rolled back: a
     * half-built MMS row is worse than none, because the system MMS UI would
     * show a message with no content and no sender. If the rollback itself
     * fails, the orphan is journalled into `provider_rows` so
     * [retryFailedProviderDeletions] can finish the job later.
     */
    private suspend fun storeMmsInProvider(
        address: String,
        textBody: String,
        timestamp: Long,
        threadId: Long,
        transactionId: String?,
        attachments: List<MmsPduParser.Attachment>,
    ): Long? {
        var createdMmsId: Long? = null
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
            val mmsId = uri.lastPathSegment?.toLongOrNull()
            if (mmsId == null) {
                // The PDU exists but we cannot address it by id; delete via the
                // returned URI so it does not linger as a contentless row.
                rollbackPartialMms(uri.toString(), address)
                return null
            }
            createdMmsId = mmsId
            val partUri = android.net.Uri.parse("content://mms/$mmsId/part")

            if (textBody.isNotBlank()) {
                val textPart = resolver.insert(
                    partUri,
                    ContentValues().apply {
                        put(Telephony.Mms.Part.MSG_ID, mmsId)
                        put(Telephony.Mms.Part.CONTENT_TYPE, "text/plain")
                        put(Telephony.Mms.Part.CHARSET, 106) // utf-8
                        put(Telephony.Mms.Part.TEXT, textBody)
                    },
                )
                // A PDU whose body never landed would render as an empty
                // message in every MMS client — roll the whole thing back.
                if (textPart == null) {
                    rollbackPartialMms("content://mms/$mmsId", address)
                    return null
                }
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
                if (part == null) {
                    rollbackPartialMms("content://mms/$mmsId", address)
                    return null
                }
                // The part row exists but carries no bytes unless this succeeds.
                val wrote = resolver.openOutputStream(part)?.use { it.write(att.data); true } ?: false
                if (!wrote) {
                    rollbackPartialMms("content://mms/$mmsId", address)
                    return null
                }
            }
            val addr = resolver.insert(
                android.net.Uri.parse("content://mms/$mmsId/addr"),
                ContentValues().apply {
                    put(Telephony.Mms.Addr.MSG_ID, mmsId)
                    put(Telephony.Mms.Addr.ADDRESS, address)
                    put(Telephony.Mms.Addr.TYPE, 137) // PduHeaders.FROM
                    put(Telephony.Mms.Addr.CHARSET, 106)
                },
            )
            // Without the FROM address the message has no sender in the system
            // provider; that is a partial record, not a usable one.
            if (addr == null) {
                rollbackPartialMms("content://mms/$mmsId", address)
                return null
            }
            return mmsId
        } catch (_: Exception) {
            // Room row is still written by the caller — the message is never
            // lost; only the incomplete provider record goes away.
            createdMmsId?.let { rollbackPartialMms("content://mms/$it", address) }
            return null
        }
    }

    /**
     * R-16: delete an MMS PDU that could not be completed. Deleting the PDU
     * cascades to its parts and addresses in the platform provider. A failure
     * here is journalled (deleteFailed = 1, messageId = -1 for "no message owns
     * this row") so the trash-purge sweep retries it instead of leaking.
     */
    private suspend fun rollbackPartialMms(uri: String, address: String) {
        val deleted = try {
            context.contentResolver.delete(android.net.Uri.parse(uri), null, null)
            true
        } catch (_: Exception) {
            false
        }
        if (!deleted) {
            db.providerRows().upsert(
                ProviderRowEntity(
                    uri = uri,
                    messageId = ORPHAN_MESSAGE_ID,
                    recipient = address,
                    kind = "MMS",
                    deleteFailed = true,
                )
            )
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

    private suspend fun resolveThreadId(address: String): Long {
        val recipients = recipientsOf(address)
        return try {
            if (recipients.size > 1) {
                Telephony.Threads.getOrCreateThreadId(context, recipients.toSet())
            } else {
                Telephony.Threads.getOrCreateThreadId(context, address)
            }
        } catch (_: Exception) {
            syntheticThreadId(address)
        }
    }

    /**
     * R-16: a collision-free thread ID for when the Telephony provider can't
     * give us one (no default-SMS role yet, provider unavailable, test env).
     *
     * The old fallback was `address.hashCode().toLong()` — a 32-bit hash, so
     * two unrelated senders could land on the same thread and have their
     * conversations MERGED, which is a privacy failure, not just a UI bug.
     * Aliases are allocated from a table keyed by the canonical recipient set
     * and numbered negatively, so they can never collide with each other or
     * with a real (positive) provider thread ID.
     */
    private suspend fun syntheticThreadId(address: String): Long {
        val key = canonicalRecipientKey(address)
        // V2-20: allocation is transactional inside the DAO now. The old
        // caller-side insert/assign/re-read could return a POSITIVE id — on
        // conflict `insert` yields -1 and the code negated it into thread 1,
        // merging this conversation into a real provider thread.
        val id = db.threadAliases().allocate(key)
        check(id < 0L) { "synthetic thread id must be negative, got $id" }
        return id
    }

    /** Digits-only, de-duplicated, sorted, ';'-joined — order-independent. */
    internal fun canonicalRecipientKey(address: String): String =
        recipientsOf(address)
            .map { r -> r.filter { it.isDigit() }.ifEmpty { r.lowercase() } }
            .distinct()
            .sorted()
            .joinToString(";")

    suspend fun classify(address: String, body: String): Verdict {
        val isContact = lookupContactName(address) != null
        val reputation = db.reputation().forSender(address)?.score ?: 0
        val firstContact = db.messages().countForAddress(address) == 0
        val senderInfo = SenderAnalyzer.analyze(address, isContact, reputation, firstContact = firstContact)
        val rules = db.userRules().all()
        val allow = rules.any { it.kind == "ALLOW" && matchesRule(it.pattern, address) }
        val block = rules.any { it.kind == "BLOCK" && matchesRule(it.pattern, address) }
        // V2-10: a rule's category is read back by name, and `Category.valueOf`
        // THROWS on an unknown one. That exception escaped the whole classify
        // call, and the caller's safety net routed the message to Inbox — so a
        // single malformed rule row disabled every OTHER rule too, including
        // BLOCK. Backups are now validated at the boundary; this handles rows
        // that predate that check by dropping just the unusable rule. Leaving a
        // message in Inbox is the recoverable direction; hiding it is not.
        val custom = rules.filter { it.kind == "CUSTOM" }.mapNotNull { rule ->
            val category = Category.entries.firstOrNull { it.name == rule.category }
            if (category == null) {
                android.util.Log.w(
                    "MessageRepository",
                    "Skipping custom rule ${rule.id}: unknown category '${rule.category}'",
                )
                return@mapNotNull null
            }
            ProtectionEngine.UserRule(
                rule.id, rule.pattern,
                if (rule.target == "TEXT") ProtectionEngine.UserRule.Target.TEXT
                else ProtectionEngine.UserRule.Target.SENDER,
                category,
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

    /**
     * Allow/block-list matching. R-21: budgeted, so a rule whose regex
     * backtracks cannot stall intake. An uncompilable pattern still falls back
     * to a literal comparison — that behaviour predates R-21 and is what makes
     * a rule typed as plain text (`+9198…`) work at all.
     */
    private fun matchesRule(pattern: String, address: String): Boolean =
        runCatching {
            Regex(pattern, RegexOption.IGNORE_CASE).containsMatchWithin(address)
        }.getOrDefault(pattern.equals(address, ignoreCase = true))

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
        space: String = Spaces.NORMAL,
    ): MessageEntity = withContext(Dispatchers.IO) {
        // V2-6b: refuse a locked send while the content key is down.
        if (space == Spaces.LOCKED) requireLockedWrites()
        val recipients = recipientsOf(address)
        val threadId = resolveThreadId(address)
        var createdMmsId: Long? = null
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
            createdMmsId = id
            if (id != null) {
                val partUri = android.net.Uri.parse("content://mms/$id/part")
                // R-16: every step below is required for a usable outbox PDU.
                // A partial one would be handed to the carrier stack with no
                // body, no attachment bytes, or no recipient.
                var complete = true
                if (textBody.isNotBlank()) {
                    complete = resolver.insert(
                        partUri,
                        ContentValues().apply {
                            put(Telephony.Mms.Part.MSG_ID, id)
                            put(Telephony.Mms.Part.CONTENT_TYPE, "text/plain")
                            put(Telephony.Mms.Part.CHARSET, 106) // utf-8
                            put(Telephony.Mms.Part.TEXT, textBody)
                        },
                    ) != null
                }
                if (complete && attachment != null) {
                    val part = resolver.insert(
                        partUri,
                        ContentValues().apply {
                            put(Telephony.Mms.Part.MSG_ID, id)
                            put(Telephony.Mms.Part.CONTENT_TYPE, attachment.mimeType)
                            if (attachment.name != null) put(Telephony.Mms.Part.NAME, attachment.name)
                        },
                    )
                    complete = part != null &&
                        (resolver.openOutputStream(part)?.use { it.write(attachment.data); true } ?: false)
                }
                if (complete) {
                    complete = recipients.all { recipient ->
                        resolver.insert(
                            android.net.Uri.parse("content://mms/$id/addr"),
                            ContentValues().apply {
                                put(Telephony.Mms.Addr.MSG_ID, id)
                                put(Telephony.Mms.Addr.ADDRESS, recipient)
                                put(Telephony.Mms.Addr.TYPE, 151) // PduHeaders.TO
                                put(Telephony.Mms.Addr.CHARSET, 106)
                            },
                        ) != null
                    }
                }
                if (!complete) {
                    rollbackPartialMms("content://mms/$id", address)
                    createdMmsId = null
                }
            }
            createdMmsId
        } catch (_: Exception) {
            // Room row below still renders the message; drop the half-written
            // provider record rather than shipping it to the carrier stack.
            createdMmsId?.let { rollbackPartialMms("content://mms/$it", address) }
            null
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
            space = space,
        )
        val id = db.messages().insert(entity.sealed())
        // R-05: one mapping per provider row this message owns, so deletion
        // reaches it without depending on the `mmsId` column.
        mmsId?.let {
            db.providerRows().upsert(
                ProviderRowEntity(
                    uri = "content://mms/$it",
                    messageId = id,
                    recipient = address,
                    kind = "MMS",
                )
            )
        }
        val preview = textBody.ifBlank { mediaPreview(attachment?.mimeType) }
        purgeProviderCopyIfLocked(entity.copy(id = id))
        updateConversation(threadId, address, preview, timestamp, category = null, incrementUnread = false, space = space)
        entity.copy(id = id)
    }

    /** Move a sent/failed outgoing MMS to the right provider box + index status. */
    suspend fun onMmsSendResult(messageId: Long, success: Boolean) = withContext(Dispatchers.IO) {
        val msg = db.messages().byId(messageId)?.opened() ?: return@withContext
        db.messages().update(msg.copy(sendStatus = if (success) "SENT" else "FAILED").sealed())
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
        space: String = Spaces.NORMAL,
    ): MessageEntity =
        withContext(Dispatchers.IO) {
            // V2-6b: refuse a locked send while the content key is down.
            if (space == Spaces.LOCKED) requireLockedWrites()
            val threadId = resolveThreadId(address)
            val entity = MessageEntity(
                threadId = threadId,
                address = address,
                body = body,
                normalizedBody = normalizedOf(body),
                timestamp = timestamp,
                isOutgoing = true,
                read = true,
                sendStatus = "SENDING",
                subId = subId,
                space = space,
            )
            // R-05: the index row is created FIRST so every provider row we
            // then write can be mapped back to it. (Provider-first ordering is
            // the never-lose rule for INCOMING messages; for outgoing, the row
            // the user is looking at is the local one.)
            val id = db.messages().insert(entity.sealed())
            val firstSmsId =
                writeOutgoingSmsToProvider(address, body, timestamp, threadId, subId, id)
            val stored = entity.copy(id = id, smsId = firstSmsId)
            if (firstSmsId != null) db.messages().update(stored.sealed())
            purgeProviderCopyIfLocked(stored)
            updateConversation(threadId, address, body, timestamp, category = null, incrementUnread = false, space = space)
            stored
        }

    /**
     * Group SMS: one provider Sent row per recipient, all pinned to the thread.
     *
     * [skipRecipients] are recipients whose provider row is known to still
     * exist (an undo after a failed deletion) — writing a second row for them
     * would leave the recipient duplicated in every other SMS app.
     */
    private suspend fun writeOutgoingSmsToProvider(
        address: String,
        body: String,
        timestamp: Long,
        threadId: Long,
        subId: Int?,
        messageId: Long?,
        skipRecipients: Set<String> = emptySet(),
    ): Long? {
        var firstSmsId: Long? = null
        recipientsOf(address).filterNot { it in skipRecipients }.forEach { recipient ->
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
            val smsId = uri?.lastPathSegment?.toLongOrNull()
            // R-05: remember EVERY row we created, not just the first. Deleting
            // a group message used to orphan the other recipients' rows, which
            // stayed visible to other SMS apps forever.
            if (smsId != null && messageId != null) {
                db.providerRows().upsert(
                    ProviderRowEntity(
                        uri = "content://sms/$smsId",
                        messageId = messageId,
                        recipient = recipient,
                        kind = "SMS",
                    )
                )
            }
            if (firstSmsId == null) firstSmsId = smsId
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
        space: String = Spaces.NORMAL,
    ): MessageEntity = withContext(Dispatchers.IO) {
        // V2-6b: refuse a locked scheduled send while the content key is down.
        if (space == Spaces.LOCKED) requireLockedWrites()
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
            space = space,
        )
        val id = db.messages().insert(entity.sealed())
        updateConversation(threadId, address, body, sendAt, category = null, incrementUnread = false, space = space)
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
            // V2-19: claim the row FIRST, atomically. The read-then-check this
            // replaced let the worker's fire and the user's "Send now" both
            // observe SCHEDULED and both send the same message. Only the caller
            // whose compare-and-set actually updated a row may proceed.
            if (db.messages().claimScheduled(messageId) != 1) return@withContext null
            val msg = db.messages().byId(messageId)?.opened() ?: return@withContext null
            // V2-6b: never hand ciphertext to the radio. A locked scheduled row
            // whose body cannot be opened (content key unavailable) releases
            // its claim and stays SCHEDULED until the key returns.
            if (msg.space == Spaces.LOCKED && LockedContent.isSealed(msg.body)) {
                db.messages().releaseScheduledClaim(messageId)
                return@withContext null
            }
            val now = System.currentTimeMillis()
            val smsId = try {
                writeOutgoingSmsToProvider(
                    msg.address, msg.body, now, msg.threadId, msg.subId, msg.id,
                )
            } catch (t: Throwable) {
                // Never strand a claimed row: without this the message would sit
                // in CLAIMED forever, invisible to both the worker and the user.
                db.messages().releaseScheduledClaim(messageId)
                throw t
            }
            val updated = msg.copy(smsId = smsId, timestamp = now, sendStatus = "SENDING")
            db.messages().update(updated.sealed())
            purgeProviderCopyIfLocked(updated)
            updateConversation(
                msg.threadId, msg.address, msg.body, now,
                category = null, incrementUnread = false, space = msg.space,
            )
            updated
        }

    /**
     * V2-19: startup recovery for scheduled sends. A process killed between the
     * atomic claim and the provider write leaves the row CLAIMED, where neither
     * the worker nor the user can reach it. At app start no send can be in
     * flight, so every surviving claim is stale by definition.
     */
    suspend fun releaseStaleScheduledClaims(): Int = withContext(Dispatchers.IO) {
        db.messages().releaseAllScheduledClaims()
    }

    /** Cancel a scheduled message: remove its index row (it was never in the provider). */
    suspend fun cancelScheduled(messageId: Long) = withContext(Dispatchers.IO) {
        val msg = db.messages().byId(messageId)?.opened() ?: return@withContext
        if (msg.sendStatus != "SCHEDULED") return@withContext
        db.messages().userDelete(messageId)
        refreshConversationSummary(msg.threadId, msg.space)
    }

    // ---- V2-48: the outbox ---------------------------------------------

    /**
     * Everything outgoing that has not finished, newest first. NORMAL space
     * only — see the DAO for why.
     */
    fun observeOutbox(): Flow<List<MessageEntity>> = db.messages().outbox()

    /** Badge count for the outbox entry point. */
    fun outboxCount(): Flow<Int> = db.messages().outboxCount()

    /** Per-(recipient, part) rows behind one outbox entry. */
    suspend fun sendAttemptsFor(messageId: Long): List<SmsAttemptEntity> =
        withContext(Dispatchers.IO) { db.smsAttempts().forMessage(messageId) }

    /**
     * V2-48: edit a message that has not been sent.
     *
     * Only SCHEDULED and FAILED rows are editable, and the guard is the DAO's
     * compare-and-set rather than a read-then-write: a scheduled message can
     * fire while the edit dialog is open, and the edit must lose that race
     * rather than rewrite the text of a message already on its way.
     *
     * Returns the updated row, or null when the message moved on first.
     */
    suspend fun editUnsentMessage(
        messageId: Long,
        body: String,
        sendAt: Long? = null,
    ): MessageEntity? = withContext(Dispatchers.IO) {
        val current = db.messages().byId(messageId)?.opened() ?: return@withContext null
        // V2-6b: editing rewrites the body — a locked write like any other.
        if (current.space == Spaces.LOCKED) requireLockedWrites()
        if (current.sendStatus != "SCHEDULED" && current.sendStatus != SendAggregate.FAILED) {
            return@withContext null
        }
        // Re-claiming a SCHEDULED row is what makes this safe: if the worker
        // took it a moment ago the claim fails and the edit is abandoned.
        if (current.sendStatus == "SCHEDULED" && db.messages().claimScheduled(messageId) != 1) {
            return@withContext null
        }
        val updated = current.copy(
            body = body,
            normalizedBody = normalizedOf(body),
            timestamp = sendAt ?: current.timestamp,
            // An edited message is a new decision: the automatic retry budget
            // it accumulated belonged to the old text.
            retryCount = 0,
            nextRetryAt = null,
            sendStatus = current.sendStatus,
        )
        db.messages().update(updated.sealed())
        if (current.sendStatus == "SCHEDULED") db.messages().releaseScheduledClaim(messageId)
        updateConversation(
            updated.threadId, updated.address, body, updated.timestamp,
            category = null, incrementUnread = false, space = updated.space,
        )
        updated
    }

    /**
     * V2-48: change the SIM a not-yet-sent message will go out on. False when
     * the message had already been dispatched, in which case the stored
     * subscription still describes what actually happened.
     */
    suspend fun changeSendSubId(messageId: Long, subId: Int?): Boolean =
        withContext(Dispatchers.IO) {
            val changed = db.messages().setSendSubId(messageId, subId) == 1
            changed
        }

    /**
     * V2-48: claim a FAILED message for exactly one resend, from the outbox
     * button or from the retry worker.
     *
     * The compare-and-set is the whole point — an automatic retry firing at the
     * same moment the user presses Resend must not produce two messages. The
     * loser gets null and does nothing. Returns the row to hand to the radio.
     */
    suspend fun claimFailedForResend(messageId: Long): MessageEntity? =
        withContext(Dispatchers.IO) {
            if (db.messages().claimFailedForResend(messageId) != 1) return@withContext null
            val msg = db.messages().byId(messageId)?.opened()
            msg?.let { refreshConversationSummary(it.threadId, it.space) }
            msg
        }

    /** V2-48: the user took over, so the automatic budget starts again. */
    suspend fun resetRetryBudget(messageId: Long) = withContext(Dispatchers.IO) {
        db.messages().resetRetry(messageId)
    }

    /**
     * V2-48: decide whether this failure earns an automatic retry and, if so,
     * record when it is due. Returns the delay in milliseconds for the caller
     * to hand to the scheduler, or null when the message stays in the outbox
     * for the user.
     *
     * The decision lives in [SendRetry] — a pure allowlist — and the *effect*
     * lives here, so the policy can be argued with in a unit test rather than
     * on a device. Scheduling the actual work is the app layer's job: this
     * module has no business knowing what a WorkManager is.
     */
    suspend fun armAutoRetry(messageId: Long): Long? = withContext(Dispatchers.IO) {
        val msg = db.messages().byId(messageId) ?: return@withContext null
        if (msg.sendStatus != SendAggregate.FAILED) return@withContext null
        if (!SendRetry.shouldAutoRetry(msg.sendResultCode, msg.retryCount)) return@withContext null
        val delay = SendRetry.delayMs(msg.retryCount)
        val due = System.currentTimeMillis() + delay
        // armRetry is guarded on FAILED too: between the read above and here the
        // user may have pressed Resend, and their send must not be shadowed by
        // a retry arming itself behind it.
        if (db.messages().armRetry(messageId, msg.retryCount + 1, due) != 1) return@withContext null
        delay
    }

    // ---- R-13: per-(recipient, part) send tracking ----

    /**
     * Declare the dispatch matrix for an outgoing SMS before handing it to the
     * radio: one PENDING attempt per (recipient, part).
     *
     * Message-level status used to be written straight from each broadcast, so
     * one early success could mark a whole group/multipart message SENT while
     * other parts were still in flight or had already failed. The message's
     * status is now DERIVED from this set, so it can only claim SENT once every
     * dispatch reported success. A resend clears the previous matrix.
     */
    suspend fun recordSendAttempts(
        messageId: Long,
        recipientCount: Int,
        partCount: Int,
        wantDelivery: Boolean,
    ) = withContext(Dispatchers.IO) {
        db.smsAttempts().deleteForMessage(messageId)
        for (r in 0 until recipientCount) {
            for (p in 0 until partCount) {
                db.smsAttempts().upsert(
                    SmsAttemptEntity(
                        attemptId = SendAggregate.attemptId(messageId, r, p),
                        messageId = messageId,
                        recipientIndex = r,
                        partIndex = p,
                        wantDelivery = wantDelivery,
                    )
                )
            }
        }
        applyAggregate(messageId)
    }

    /** A SENT broadcast for ONE dispatch. [attemptId] identifies which. */
    suspend fun settleSendAttempt(
        messageId: Long,
        attemptId: String?,
        success: Boolean,
        resultCode: Int?,
    ) = withContext(Dispatchers.IO) {
        if (attemptId != null && db.smsAttempts().byId(attemptId) != null) {
            db.smsAttempts().settleSent(
                attemptId,
                if (success) AttemptState.OK else AttemptState.FAILED,
                resultCode,
            )
            applyAggregate(messageId)
        } else {
            // Legacy row (sent before this version) or an attempt row that was
            // wiped by a resend: fall back to the message-level DAO guards.
            if (success) db.messages().markSent(messageId)
            else db.messages().markFailed(messageId, resultCode)
        }
        db.messages().byId(messageId)?.let { refreshConversationSummary(it.threadId, it.space) }
    }

    /** A delivery report for ONE dispatch. */
    suspend fun settleDeliveryAttempt(
        messageId: Long,
        attemptId: String?,
        delivered: Boolean,
    ) = withContext(Dispatchers.IO) {
        if (attemptId != null && db.smsAttempts().byId(attemptId) != null) {
            db.smsAttempts().settleDelivery(
                attemptId,
                if (delivered) AttemptState.OK else AttemptState.FAILED,
            )
            applyAggregate(messageId)
        } else if (delivered) {
            db.messages().markDelivered(messageId)
        }
    }

    /**
     * The radio call itself threw, so some or all dispatches never happened and
     * will never call back. Fail every still-PENDING attempt (and the message)
     * rather than leaving the row stuck in SENDING with no Resend affordance.
     */
    suspend fun failAllSendAttempts(messageId: Long, resultCode: Int?) =
        withContext(Dispatchers.IO) {
            db.smsAttempts().forMessage(messageId).forEach {
                db.smsAttempts().settleSent(it.attemptId, AttemptState.FAILED, resultCode)
            }
            db.messages().markFailed(messageId, resultCode)
            db.messages().byId(messageId)?.opened()
                ?.let { refreshConversationSummary(it.threadId, it.space) }
        }

    /**
     * Recompute `sendStatus` from the whole attempt set. The derived value is a
     * pure function of the set, so broadcast order cannot change the outcome;
     * [SendAggregate.supersedes] additionally refuses to move the stored status
     * backwards for messages whose matrix is still filling in.
     */
    private suspend fun applyAggregate(messageId: Long) {
        val attempts = db.smsAttempts().forMessage(messageId)
        val derived = SendAggregate.of(attempts) ?: return
        val current = db.messages().byId(messageId)?.sendStatus ?: return
        if (current == "SCHEDULED") return // not dispatched yet
        if (derived == current) return
        if (!SendAggregate.supersedes(derived, current)) return
        db.messages().setSendStatus(messageId, derived, SendAggregate.failureCode(attempts))
    }

    private suspend fun updateConversation(
        threadId: Long,
        address: String,
        lastMessage: String,
        timestamp: Long,
        category: String?,
        incrementUnread: Boolean,
        space: String = Spaces.NORMAL,
    ) {
        val existing = db.conversations().byThreadId(threadId, space)?.opened()
        val name = displayNameFor(address)
        // R-06: a late-arriving OLD message (delayed carrier delivery, backfill)
        // must not roll the preview backwards. Unread still counts — the message
        // is genuinely unread — but preview/timestamp/category only move forward.
        // A placeholder row ("new locked chat") carries a CREATION stamp, not a
        // message stamp, so its blank preview must always yield to the first
        // real message even when the carrier timestamp predates creation.
        val isPlaceholder = existing != null && existing.lastMessage.isEmpty()
        val isNewer = existing == null || isPlaceholder || timestamp >= existing.lastTimestamp
        db.conversations().upsert(
            ConversationEntity(
                id = existing?.id ?: 0,
                threadId = threadId,
                address = if (isNewer) address else existing.address,
                contactName = if (isNewer) name else existing.contactName,
                lastMessage = if (isNewer) lastMessage else existing.lastMessage,
                lastTimestamp = if (isNewer) timestamp else existing.lastTimestamp,
                unreadCount = (existing?.unreadCount ?: 0) + if (incrementUnread) 1 else 0,
                category = if (isNewer) (category ?: existing?.category ?: "INBOX")
                else existing.category,
                pinned = existing?.pinned ?: false,
                archived = false, // new message unarchives
                muted = existing?.muted ?: false,
                locked = existing?.locked ?: false,
                preferredSubId = existing?.preferredSubId,
                space = space,
            ).sealed()
        )
    }

    /**
     * V2-28: the batched, cached, off-main-thread form of [displayNameFor] —
     * what every list of rows should use.
     *
     * A search result set of 200 rows typically covers a couple of dozen
     * distinct correspondents; this resolves each of those once, reuses
     * anything another screen already resolved, and does the remaining provider
     * work on IO. [com.messages.core.contacts.ContactSync] invalidates the
     * cache when contacts change, so a rename shows up without waiting for a
     * ViewModel to die.
     */
    suspend fun displayNamesFor(addresses: Collection<String>): Map<String, String?> =
        withContext(Dispatchers.IO) {
            com.messages.core.contacts.ContactNameCache.resolveAll(addresses) { displayNameFor(it) }
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
                db.conversations().setContactName(conv.threadId, fresh, conv.space)
                changed++
            }
        }
        changed
    }

    /** "Not spam / Move to Inbox" — reclassify + local trust boost (§6.3). */
    suspend fun moveToInbox(messageId: Long) = withContext(Dispatchers.IO) {
        val msg = db.messages().byId(messageId)?.opened() ?: return@withContext
        // R-15: clear the FULL classifier verdict, not just the category. Leaving
        // fraudWarning/dangerous/score/matched-IDs behind meant a message the
        // user explicitly rescued still rendered as fraudulent, and its red
        // warning notification stayed on screen.
        db.messages().clearClassifierVerdict(messageId, "INBOX")
        adjustReputation(msg.address, delta = +2, notSpam = true)
        // R-06: the conversation's category is derived, never patched — the
        // thread only moves if this was the latest message.
        refreshConversationSummary(msg.threadId, msg.space)
    }

    /** "Mark spam" — reclassify + distrust the sender. */
    suspend fun moveToSpam(messageId: Long) = withContext(Dispatchers.IO) {
        val msg = db.messages().byId(messageId)?.opened() ?: return@withContext
        db.messages().recategorize(messageId, "SPAM")
        adjustReputation(msg.address, delta = -2, notSpam = false)
        refreshConversationSummary(msg.threadId, msg.space)
    }

    // ---- Secret locked space ----

    /**
     * "New locked chat": create a LOCKED-space conversation for this thread.
     * Existing history stays on the NORMAL row; from now on the routing rule
     * sends every incoming message from this address to the locked row.
     * [address] enables creation for a brand-new recipient with no normal
     * conversation at all (compose-from-inside-the-locked-space).
     */
    suspend fun createLockedConversation(threadId: Long, address: String? = null) =
        withContext(Dispatchers.IO) {
            if (db.conversations().byThreadId(threadId, Spaces.LOCKED) != null) return@withContext
            // V2-6b: creating the routing row makes every future incoming
            // message from this thread a locked write — refuse to start that
            // while the content key is down.
            requireLockedWrites()
            val normal = db.conversations().byThreadId(threadId, Spaces.NORMAL)
            val resolvedAddress = normal?.address ?: address ?: return@withContext
            db.conversations().upsert(
                ConversationEntity(
                    threadId = threadId,
                    address = resolvedAddress,
                    contactName = normal?.contactName ?: displayNameFor(resolvedAddress),
                    lastMessage = "",
                    lastTimestamp = System.currentTimeMillis(),
                    unreadCount = 0,
                    category = "INBOX",
                    muted = normal?.muted ?: false,
                    preferredSubId = normal?.preferredSubId,
                    space = Spaces.LOCKED,
                ).sealed()
            )
        }

    /**
     * Secret-space RESET: destruction without revelation. Hard-deletes every
     * locked-space row — Room index, local media, AND the Telephony-provider
     * rows — deliberately BYPASSING Trash (routing a locked message through
     * the normal Trash screen would leak it). Removing the LOCKED
     * conversation rows also reverts the routing rule: future incoming from
     * those addresses files into the normal space again. Never displays,
     * exports, or moves any locked content. Returns how many messages died.
     */
    suspend fun wipeLockedSpace(): Int = withContext(Dispatchers.IO) {
        val locked = db.messages().allInSpace(Spaces.LOCKED) // trash included
        locked.forEach { msg ->
            deleteProviderRows(msg)
            deleteLocalMedia(msg)
            db.messages().userDelete(msg.id)
        }
        db.conversations().allConversations()
            .filter { it.space == Spaces.LOCKED }
            .forEach { db.conversations().deleteByThreadId(it.threadId, Spaces.LOCKED) }
        // V2-6: nothing sealed under this key remains, so the key goes too. If a
        // row escaped the delete above — a failed provider delete, a row written
        // by a concurrent intake — destroying the key is what makes it
        // unreadable rather than merely deleted.
        LockedContent.destroyKey(context)
        locked.size
    }

    /**
     * "Move entire chat" (NORMAL→LOCKED) and "Unlock chat" (LOCKED→NORMAL):
     * every message row of the thread (trash included — a locked chat's trash
     * must not surface in the normal Trash screen) plus the conversation row
     * change spaces. When a row already exists on the target side (New-locked-
     * chat first, Move-entire-chat later — or unlocking one), they merge.
     */
    suspend fun moveThreadToSpace(threadId: Long, from: String, to: String) =
        withContext(Dispatchers.IO) {
            // V2-6b: BOTH directions need the content key. Moving in must seal;
            // moving out must open — unlocking while the key is down would
            // strand real-sealed ciphertext in the normal space, where the
            // backlog repair (locked-space-scoped) could never find it again.
            if (from == Spaces.LOCKED || to == Spaces.LOCKED) requireLockedWrites()
            val source = db.conversations().byThreadId(threadId, from) ?: return@withContext
            db.messages().setThreadSpace(threadId, from, to)
            // V2-6: setThreadSpace is a bulk UPDATE of one column — it cannot
            // re-encrypt anything. Every row it just moved has to be re-encoded
            // in the direction it moved, trash included.
            recodeThread(threadId, to)
            if (to == Spaces.LOCKED) purgeProviderCopies(threadId, to)
            val target = db.conversations().byThreadId(threadId, to)
            if (target == null) {
                // REPLACE-by-PK flips the same row's space in place.
                db.conversations().upsert(source.copy(space = to, locked = false).sealed())
            } else {
                db.conversations().upsert(
                    target.copy(unreadCount = target.unreadCount + source.unreadCount).sealed()
                )
                db.conversations().deleteByThreadId(threadId, from)
            }
            refreshConversationSummary(threadId, to)
        }

    /**
     * Re-encode every row of one thread for the space it is now in: sealed on
     * the way into the locked space, opened on the way out. Rows already in the
     * right form are left alone rather than rewritten, so this is cheap to call
     * on a thread that does not need it.
     */
    private suspend fun recodeThread(threadId: Long, space: String) {
        db.messages().allForThreadInSpace(threadId, space).forEach { row ->
            // isCanonical, not a before/after comparison: sealing draws a fresh
            // nonce every time, so a re-sealed row never equals its stored self
            // and "cheap to call on a thread that does not need it" would be
            // false — every row would be rewritten on every move.
            if (LockedContent.isCanonical(row)) return@forEach
            val recoded = row.sealed()
            if (recoded != row) db.messages().update(recoded)
        }
    }

    /**
     * V2-6: drop the Telephony-provider copy of every message in a thread that
     * has entered the locked space.
     *
     * This is the half of the fix that encryption alone cannot deliver. Sealing
     * the Room index is pointless while an identical plaintext row sits in
     * `content://sms`, readable by anything holding the SMS role and by any
     * forensic tool that knows where mmssms.db lives.
     *
     * The cost is real and is stated in the setup disclaimer: once a chat is
     * locked, its messages exist only in this app's database. Making another app
     * the default SMS app will not show them, and uninstalling this app without
     * a backup destroys them. That is the same bargain the Reset flow already
     * makes, and it is the only way "locked" can mean more than "hidden".
     */
    private suspend fun purgeProviderCopies(threadId: Long, space: String) {
        db.messages().allForThreadInSpace(threadId, space).forEach { deleteProviderRows(it) }
    }

    /**
     * V2-6 backlog repair: seal locked rows written before this app version and
     * drop their provider copies. Idempotent, resumable, and safe to call on
     * every cold start — a fully-sealed space costs one indexed query.
     *
     * Deliberately not a Room migration. Sealing needs the Android Keystore,
     * which a `SupportSQLiteDatabase` migration has no business reaching into,
     * and a migration that fails halfway leaves the user unable to open the app
     * at all. Rows are updated one at a time, so an interruption leaves a
     * partially-sealed space that the next run simply finishes — the marker
     * prefix means both halves read correctly meanwhile.
     */
    /** V2-6b: serializes backlog runs — cold start, entering the locked space
     *  and the banner's Retry could otherwise race a tombstone repair against
     *  its own provider-row delete. */
    private val backlogMutex = Mutex()

    suspend fun sealLockedBacklog(): Int = withContext(Dispatchers.IO) {
        backlogMutex.withLock {
            if (!LockedContent.available(context)) return@withLock 0
            var changed = 0
            val repairedThreads = mutableSetOf<Long>()
            db.messages().allInSpace(Spaces.LOCKED).forEach { row ->
                var current = row
                // V2-6b: a TOMBSTONE — text withheld by the apocalypse corner,
                // provider copy kept as the only copy. Its mapping is
                // deliberately NOT flagged deleteFailed (flagged mappings are
                // ordinary purge retries for rows that already hold their text),
                // which is what tells the two apart from an attachment-only MMS
                // whose purge failed.
                val isTombstone = row.body.isEmpty() &&
                    db.providerRows().forMessage(row.id).any { !it.deleteFailed }
                if (isTombstone) {
                    val text = readProviderBody(row)
                        ?: return@forEach // provider unreadable: KEEP the only copy
                    if (text.isNotBlank()) {
                        current = try {
                            row.copy(body = text, normalizedBody = normalizedOf(text)).sealed()
                        } catch (_: LockedSealException) {
                            return@forEach // still can't seal: keep the only copy
                        }
                        db.messages().update(current)
                        repairedThreads += row.threadId
                        changed++
                    }
                    // Blank text: nothing was withheld — fall through and let
                    // the provider copy go.
                } else if (!LockedContent.isCanonical(row)) {
                    // isCanonical decides this, NOT a before/after comparison of
                    // the ciphertext. Sealing draws a fresh nonce every time, so
                    // a re-sealed row never equals its stored self — a comparison
                    // would report every locked row as repaired on every cold
                    // start, rewriting the whole space and re-firing the FTS
                    // triggers for each row, forever. Pending-grade rows are
                    // non-canonical, so this is also where they upgrade to the
                    // real seal.
                    val recoded = try {
                        row.sealed()
                    } catch (_: LockedSealException) {
                        return@forEach // stays non-canonical; the next run retries
                    }
                    if (recoded != row) {
                        db.messages().update(recoded)
                        changed++
                    }
                    current = recoded
                }
                // A row sealed by an earlier run may still have a provider copy
                // if that run was interrupted between the two; deleteProviderRows
                // is a no-op once the mapping is gone. Runs strictly AFTER any
                // tombstone repair — never before, or it destroys the only copy.
                deleteProviderRows(current)
            }
            db.conversations().allConversations()
                .filter { it.space == Spaces.LOCKED && !LockedContent.isCanonical(it) }
                .forEach { conv ->
                    runCatching { db.conversations().upsert(conv.sealed()) }
                }
            // A repaired tombstone has real text now; let its conversation row
            // say so (the guard in refreshConversationSummary keeps this safe).
            repairedThreads.forEach { refreshConversationSummary(it, Spaces.LOCKED) }
            changed
        }
    }

    /**
     * V2-6b: recover the text of a tombstoned locked row from its surviving
     * Telephony-provider copy. Null means "could not read" — the caller must
     * then leave the provider row alone, because it is the only copy. An empty
     * string means "read fine, nothing there" (or the row is gone — nothing
     * left to recover), which lets the caller proceed.
     */
    private suspend fun readProviderBody(msg: MessageEntity): String? {
        val mapped = db.providerRows().forMessage(msg.id)
        mapped.firstOrNull { it.kind == "SMS" }?.let { row ->
            return try {
                context.contentResolver.query(
                    android.net.Uri.parse(row.uri),
                    arrayOf(Telephony.Sms.BODY), null, null, null,
                )?.use { c ->
                    if (c.moveToFirst()) c.getString(0).orEmpty() else ""
                }
            } catch (_: Exception) {
                null
            }
        }
        mapped.firstOrNull { it.kind == "MMS" }?.let { row ->
            val mmsId = row.uri.substringAfterLast('/').toLongOrNull() ?: return ""
            return try {
                context.contentResolver.query(
                    android.net.Uri.parse("content://mms/part"),
                    arrayOf(Telephony.Mms.Part.CONTENT_TYPE, Telephony.Mms.Part.TEXT),
                    "${Telephony.Mms.Part.MSG_ID} = ?", arrayOf(mmsId.toString()), null,
                )?.use { c ->
                    buildString {
                        while (c.moveToNext()) {
                            if (c.getString(0) == "text/plain") append(c.getString(1).orEmpty())
                        }
                    }
                }
            } catch (_: Exception) {
                null
            }
        }
        return ""
    }

    /**
     * First secret-space setup: legacy biometric-locked conversations move
     * into the locked space (told to the user in the setup flow), and the old
     * per-chat flag is cleared. Returns how many threads moved.
     */
    suspend fun migrateLegacyLockedConversations(): Int = withContext(Dispatchers.IO) {
        val legacy = db.conversations().legacyLockedConversations()
        legacy.forEach { conv ->
            moveThreadToSpace(conv.threadId, Spaces.NORMAL, Spaces.LOCKED)
            db.conversations().byThreadId(conv.threadId, Spaces.LOCKED)?.let {
                if (it.locked) db.conversations().upsert(it.copy(locked = false))
            }
        }
        legacy.size
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
        val msg = db.messages().byId(messageId)?.opened() ?: return@withContext
        if (msg.sendStatus == "SCHEDULED") {
            db.messages().userDelete(messageId)
        } else {
            deleteProviderRows(msg)
            db.messages().moveToTrash(messageId, System.currentTimeMillis())
        }
        refreshConversationSummary(msg.threadId, msg.space)
    }

    /**
     * Undo for a just-trashed conversation (swipe-delete snackbar): restore
     * every message of the thread trashed at/after [trashedAfter].
     */
    suspend fun restoreThreadFromTrash(threadId: Long, trashedAfter: Long = 0L, space: String = Spaces.NORMAL) =
        withContext(Dispatchers.IO) {
            try {
                val toRestore = if (trashedAfter > 0L) {
                    db.messages().trashedIdsForThread(threadId, trashedAfter, space)
                } else {
                    db.messages().allTrashedForThread(threadId, space).map { it.id }
                }
                toRestore.forEach { messageId ->
                    try {
                        restoreFromTrash(messageId)
                    } catch (e: Exception) {
                        android.util.Log.e("MessageRepository", "Failed to restore message $messageId", e)
                    }
                }
                // Ensure conversation row exists in conversations table
                val latest = db.messages().latestForThread(threadId, space)?.opened()
                if (latest != null) {
                    val conv = db.conversations().byThreadId(threadId, space)?.opened()
                    db.conversations().upsert(
                        ConversationEntity(
                            id = conv?.id ?: 0,
                            threadId = threadId,
                            address = conv?.address ?: latest.address,
                            contactName = conv?.contactName ?: displayNameFor(latest.address),
                            lastMessage = latest.body.ifBlank { mediaPreview(latest.mediaMimeType) },
                            lastTimestamp = latest.timestamp,
                            unreadCount = conv?.unreadCount ?: 0,
                            category = conv?.category ?: latest.category,
                            pinned = conv?.pinned ?: false,
                            archived = conv?.archived ?: false,
                            muted = conv?.muted ?: false,
                            locked = conv?.locked ?: false,
                            preferredSubId = conv?.preferredSubId,
                            space = space,
                        ).sealed()
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("MessageRepository", "restoreThreadFromTrash failed for $threadId", e)
            }
        }

    /** Delete a whole conversation to Trash (§6.4). V2-24: `space` is explicit
     *  — the same threadId names a different conversation in each space. */
    suspend fun moveThreadToTrash(threadId: Long, space: String) =
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            db.messages().listForThread(threadId, space).forEach { msg ->
                if (msg.sendStatus == "SCHEDULED") db.messages().userDelete(msg.id)
                else deleteProviderRows(msg)
            }
            db.messages().moveThreadToTrash(threadId, now, space)
            refreshConversationSummary(threadId, space) // no live messages left → row is dropped
        }

    /**
     * Restore from Trash within the 60-day window: clear the flag and write
     * the message back into the Telephony provider with its original
     * timestamp (best-effort — needs the default-SMS role; the index row is
     * restored either way). MMS rows come back index-only: their local media
     * file was kept, the provider PDU was not.
     */
    suspend fun restoreFromTrash(messageId: Long) = withContext(Dispatchers.IO) {
        val msg = db.messages().byId(messageId)?.opened() ?: return@withContext
        if (!msg.trashed) return@withContext
        var restored = msg.copy(trashed = false, trashedAt = null)
        // R-05: split the old mapping in two.
        //
        //  - Rows we really deleted are gone from the provider: drop the stale
        //    mapping, then re-create the row (and its mapping) below.
        //  - Rows flagged `deleteFailed` are STILL in the provider — the
        //    deletion never landed. Re-adopt those (clear the flag, keep the
        //    URI) instead of writing a second copy, or an undo would leave the
        //    recipient duplicated in every other SMS app AND strand the entry
        //    in the retry sweep forever.
        val mappings = db.providerRows().forMessage(msg.id)
        val surviving = mappings.filter { it.deleteFailed }
        mappings.filterNot { it.deleteFailed }
            .forEach { db.providerRows().deleteByUri(it.uri) }
        surviving.forEach { db.providerRows().upsert(it.copy(deleteFailed = false)) }
        val survivingRecipients = surviving.map { it.recipient }.toSet()
        val survivingSmsId = surviving.firstOrNull { it.kind == "SMS" }
            ?.uri?.substringAfterLast('/')?.toLongOrNull()
        if (msg.mmsId == null && msg.mmsTransactionId == null) {
            // V2-6b: a locked message is never re-published to the Telephony
            // provider — the space's bargain is that its rows live only in this
            // app's database. Surviving failed-deletion rows are purged below
            // instead of being re-adopted.
            val smsId = if (msg.space == Spaces.LOCKED) {
                null
            } else if (msg.isOutgoing) {
                // Group SMS: restore one row per recipient that no longer has
                // one, exactly as the original send did, and re-map each.
                writeOutgoingSmsToProvider(
                    msg.address, msg.body, msg.timestamp, msg.threadId, msg.subId, msg.id,
                    skipRecipients = survivingRecipients,
                ) ?: survivingSmsId
            } else if (surviving.isNotEmpty()) {
                survivingSmsId // the Inbox row was never actually removed
            } else {
                restoreIncomingProviderRow(msg)
            }
            restored = restored.copy(smsId = smsId)
        } else {
            // MMS: the provider PDU was destroyed on trash, so the row comes
            // back index-only — UNLESS its deletion had failed, in which case
            // the PDU is still there and keeps its id.
            restored = restored.copy(
                mmsId = surviving.firstOrNull { it.kind == "MMS" }
                    ?.uri?.substringAfterLast('/')?.toLongOrNull(),
            )
        }
        db.messages().update(restored.sealed())
        // V2-6b: a restored locked message must leave no plaintext copy in the
        // provider — this also finally deletes any surviving failed-deletion
        // rows the restore would previously have re-adopted.
        purgeProviderCopyIfLocked(restored)
        // Rebuild the conversation row (it may have been dropped when the
        // thread emptied) without disturbing unread counts. Space-scoped: a
        // locked message restores into the locked conversation, never normal.
        // V2-6b: an unopenable latest body (content key down) must never become
        // the preview — it would sit in the preview column as body-AAD
        // ciphertext under the real marker: canonical to the repair pass,
        // unopenable to every reader, forever.
        val conv = db.conversations().byThreadId(msg.threadId, msg.space)?.opened()
        val latest = db.messages().latestForThread(msg.threadId, msg.space)?.opened()
        if (latest != null && !LockedContent.isSealed(latest.body) &&
            (conv == null || latest.timestamp >= conv.lastTimestamp)
        ) {
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
                    space = msg.space,
                ).sealed()
            )
        }
    }

    /** Undo-from-trash for an INCOMING SMS: one Inbox row, mapped for R-05. */
    private suspend fun restoreIncomingProviderRow(msg: MessageEntity): Long? {
        val smsId = try {
            context.contentResolver.insert(
                Telephony.Sms.Inbox.CONTENT_URI,
                ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, msg.address)
                    put(Telephony.Sms.BODY, msg.body)
                    put(Telephony.Sms.DATE, msg.timestamp)
                    put(Telephony.Sms.READ, if (msg.read) 1 else 0)
                    put(Telephony.Sms.THREAD_ID, msg.threadId)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                    if (msg.subId != null) put(Telephony.Sms.SUBSCRIPTION_ID, msg.subId)
                },
            )?.lastPathSegment?.toLongOrNull()
        } catch (_: Exception) {
            null
        }
        if (smsId != null) {
            db.providerRows().upsert(
                ProviderRowEntity("content://sms/$smsId", msg.id, msg.address, "SMS")
            )
        }
        return smsId
    }

    /** "Delete forever" from within Trash — immediate permanent deletion. */
    suspend fun deleteForever(messageId: Long) = withContext(Dispatchers.IO) {
        val msg = db.messages().byId(messageId)?.opened() ?: return@withContext
        deleteLocalMedia(msg)
        // R-05: a row whose deletion never succeeded stays in provider_rows for
        // the retry sweep; everything already gone is dropped with the message.
        db.providerRows().forMessage(messageId)
            .filter { !it.deleteFailed }
            .forEach { db.providerRows().deleteByUri(it.uri) }
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

    /**
     * R-05: remove EVERY Telephony-provider row this message owns.
     *
     * A group SMS wrote one row per recipient; deleting only `msg.smsId` left
     * the rest visible to other SMS apps and outside this app's retention
     * model. Mapped rows are walked first; `smsId`/`mmsId` remain the fallback
     * for messages indexed before the mapping table existed.
     *
     * A deletion that fails is KEPT and flagged, so
     * [retryFailedProviderDeletions] can try again — silently discarding it
     * would leave a permanent orphan.
     */
    private suspend fun deleteProviderRows(msg: MessageEntity) {
        val mapped = db.providerRows().forMessage(msg.id)
        // (uri to recipient) — the recipient MUST be carried through. Re-flagging
        // a failed deletion with the whole ';'-joined group address instead of
        // the individual recipient made the row unmatchable on restore, so an
        // undo wrote a second provider row for someone who already had one.
        val targets: List<Pair<String, String>> =
            if (mapped.isNotEmpty()) {
                mapped.map { it.uri to it.recipient }
            } else {
                // Legacy rows (indexed before the mapping table existed) only
                // ever recorded the FIRST recipient's provider id.
                val first = recipientsOf(msg.address).firstOrNull() ?: msg.address
                listOfNotNull(
                    msg.smsId?.let { "content://sms/$it" },
                    msg.mmsId?.let { "content://mms/$it" },
                ).map { it to first }
            }
        targets.forEach { (uri, recipient) ->
            // V2-26: the delete COUNT is the only evidence the row actually
            // went away. The previous code discarded it and reported success
            // for any call that merely didn't throw — which is exactly what
            // happens when the provider refuses the delete without an
            // exception: the row survived, the mapping was dropped, and
            // retryFailedProviderDeletions could never find it again.
            //
            // 0 rows affected is treated as "already gone" only when we can
            // confirm it is gone; otherwise it is a failure to retry.
            val deleted = try {
                val count = context.contentResolver.delete(android.net.Uri.parse(uri), null, null)
                count > 0 || !providerRowExists(uri)
            } catch (_: Exception) {
                // Needs the default-SMS role; the index-side trash flag still applies.
                false
            }
            if (deleted) db.providerRows().deleteByUri(uri)
            else db.providerRows().upsert(
                ProviderRowEntity(uri, msg.id, recipient, kindOf(uri), deleteFailed = true)
            )
        }
    }

    /**
     * V2-26: does this provider row still exist? Used to tell "the delete
     * affected 0 rows because it was already gone" (success — nothing to retry)
     * apart from "the delete affected 0 rows because it was refused" (failure —
     * must be retried). A query that itself fails is reported as "still there",
     * because assuming success is the outcome that loses the row forever.
     */
    private fun providerRowExists(uri: String): Boolean = try {
        context.contentResolver.query(
            android.net.Uri.parse(uri), arrayOf("_id"), null, null, null,
        )?.use { it.moveToFirst() } ?: true
    } catch (_: Exception) {
        true
    }

    private fun kindOf(uri: String): String = if (uri.startsWith("content://mms")) "MMS" else "SMS"

    /**
     * R-05: retry provider deletions that failed earlier (the default-SMS role
     * can be lost and regained, and the provider can be transiently busy).
     * Run by the trash-purge worker. Returns how many rows were finally freed.
     */
    suspend fun retryFailedProviderDeletions(limit: Int = 200): Int = withContext(Dispatchers.IO) {
        var freed = 0
        db.providerRows().pendingDeletions(limit).forEach { row ->
            val ok = try {
                context.contentResolver.delete(android.net.Uri.parse(row.uri), null, null)
                true
            } catch (_: Exception) {
                false
            }
            if (ok) {
                db.providerRows().deleteByUri(row.uri)
                freed++
            }
        }
        freed
    }

    private fun deleteLocalMedia(msg: MessageEntity) {
        // V2-25: only files we own. A backfilled row references a
        // `content://mms/part/…` in the shared Telephony provider — those bytes
        // belong to the system store (the provider row deletion handles them),
        // and `File("content://…")` would be a stray relative path anyway.
        MediaRef.asFile(msg.mediaUri)?.let { file -> runCatching { file.delete() } }
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
            deleteProviderRows(msg)
            db.messages().userDelete(msg.id)
        }
        expired.map { it.threadId to it.space }.distinct()
            .forEach { (t, s) -> refreshConversationSummary(t, s) }
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
            deleteProviderRows(msg)
            db.messages().moveToTrash(msg.id, now)
        }
        expired.map { it.threadId to it.space }.distinct()
            .forEach { (t, s) -> refreshConversationSummary(t, s) }
        expired.size
    }

    /** Recompute a conversation's summary after deletions; drop it if empty. */
    /**
     * The single authoritative recomputation of a conversation's derived state
     * (R-06). Preview, timestamp, address and CATEGORY are all derived from the
     * latest live message — never patched independently by callers.
     *
     * Category matters most: it used to be omitted here and written ad-hoc by
     * recategorization instead, so folder membership could disagree with the
     * newest message in the thread.
     */
    suspend fun refreshConversationSummary(threadId: Long, space: String = Spaces.NORMAL) {
        val conv = db.conversations().byThreadId(threadId, space)?.opened() ?: return
        val latest = db.messages().latestForThread(threadId, space)?.opened()
        if (latest == null) {
            db.conversations().deleteByThreadId(threadId, space)
            return
        }
        // V2-6b: an unopenable body (content key down) must never become the
        // preview — body-AAD ciphertext stored under the real marker in the
        // preview column would be canonical to the repair pass and unopenable
        // to every reader, forever. Keep the existing preview until it opens.
        if (LockedContent.isSealed(latest.body)) return
        val preview = latest.body.ifBlank { mediaPreview(latest.mediaMimeType) }
        if (latest.timestamp != conv.lastTimestamp ||
            preview != conv.lastMessage ||
            latest.category != conv.category ||
            latest.address != conv.address
        ) {
            db.conversations().upsert(
                conv.copy(
                    address = latest.address,
                    contactName = displayNameFor(latest.address) ?: conv.contactName,
                    lastMessage = preview,
                    lastTimestamp = latest.timestamp,
                    category = latest.category,
                ).sealed()
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
        /**
         * R-16: `provider_rows.messageId` for a journalled orphan — a provider
         * row no Room message owns, left behind by a rolled-back MMS write.
         * Negative so it can never collide with a real autoincrement id.
         */
        const val ORPHAN_MESSAGE_ID = -1L

        @Volatile private var instance: MessageRepository? = null
        fun get(context: Context): MessageRepository =
            instance ?: synchronized(this) {
                instance ?: MessageRepository(context.applicationContext).also { instance = it }
            }

        /**
         * TESTS ONLY. Robolectric shares one instrumented classloader across
         * test classes with identical config, so this singleton — and the
         * application context + ContentResolver it captured — leaks from one
         * test class into the next while each class gets a FRESH application.
         * Repo-level test classes must drop it in @Before so their Room db and
         * any registered fake providers bind to their own application.
         */
        @androidx.annotation.VisibleForTesting
        fun resetForTests() = synchronized(this) {
            runCatching { instance?.db?.close() }
            instance = null
        }
    }
}
