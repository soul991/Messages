package com.personal.detectivedialer.data.repository

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import android.util.Log
import com.personal.detectivedialer.data.local.SmsDao
import com.personal.detectivedialer.data.local.SmsMessage
import com.personal.detectivedialer.data.local.SmsSenderPref
import com.personal.detectivedialer.data.local.SmsSenderPrefDao
import com.personal.detectivedialer.data.local.SmsThreadSummary
import com.personal.detectivedialer.data.prefs.SettingsRepository
import com.personal.detectivedialer.data.sms.SmsCategory
import com.personal.detectivedialer.data.sms.TraiSuffix
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMS pipeline for when Detective Dialer is the default SMS app.
 *
 * Routing for an incoming message:
 *   1. Sender marked never-block → inbox.
 *   2. TRAI suffix (-P/-S/-T/-G) + the user's per-class toggles → Blocked folder
 *      (default: only -P promotional is blocked).
 *   3. Suffix-less sender + content-fallback opt-in → POST /screen-sms; SPAM
 *      verdict → Blocked folder.
 *   4. Everything else → inbox.
 *
 * Inbox/sent messages are persisted to the system Telephony provider (the
 * default-SMS-app contract) *and* mirrored in Room for the in-app UI. Blocked
 * messages live only in Room — kept indefinitely, visible in the Blocked tab,
 * and only ever deleted by an explicit user action. Restoring a blocked
 * message writes it to the provider inbox and whitelists its sender.
 */
@Singleton
class SmsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val smsDao: SmsDao,
    private val senderPrefDao: SmsSenderPrefDao,
    private val settings: SettingsRepository,
    private val callRepository: CallRepository,
) {
    // ── Observers for the UI ─────────────────────────────────────────────
    fun observeThreads(): Flow<List<SmsThreadSummary>> = smsDao.observeThreads()
    fun observeThread(sender: String): Flow<List<SmsMessage>> = smsDao.observeThread(sender)
    fun observeBlocked(): Flow<List<SmsMessage>> = smsDao.observeBlocked()
    fun observeBlockedUnreadCount(): Flow<Int> = smsDao.observeBlockedUnreadCount()

    /** Verdict for an incoming message: null folder reason means inbox. */
    data class Routed(val blocked: Boolean, val reason: String)

    /** Pure routing decision (provider/Room untouched). */
    suspend fun route(sender: String, body: String): Routed {
        if (senderPrefDao.isNeverBlock(sender) > 0) {
            return Routed(blocked = false, reason = "sender marked never-block")
        }

        val s = settings.settings.first()
        val category = TraiSuffix.categoryOf(sender)
        if (category != null) {
            val block = when (category) {
                SmsCategory.PROMOTIONAL -> s.smsBlockPromotional
                SmsCategory.SERVICE -> s.smsBlockService
                SmsCategory.TRANSACTIONAL -> s.smsBlockTransactional
                SmsCategory.GOVERNMENT -> s.smsBlockGovernment
            }
            return Routed(
                blocked = block,
                reason = "TRAI -${category.suffix} ${category.label.lowercase()} suffix",
            )
        }

        // Suffix-less sender: optional content classification (opt-in, OFF by default).
        if (s.smsContentFallback) {
            val verdict = runCatching { callRepository.screenSms(sender, body) }.getOrNull()
            if (verdict != null && verdict.verdict.equals("SPAM", ignoreCase = true)) {
                return Routed(blocked = true, reason = "content filter: ${verdict.reason}")
            }
        }
        return Routed(blocked = false, reason = "")
    }

    /**
     * Handle a delivered SMS. Returns the stored message (so the caller can
     * decide whether to notify — blocked messages stay silent).
     */
    suspend fun handleIncoming(sender: String, body: String, timestamp: Long): SmsMessage {
        val routed = route(sender, body)
        val message = SmsMessage(
            sender = sender,
            body = body,
            timestamp = timestamp,
            folder = if (routed.blocked) SmsMessage.FOLDER_BLOCKED else SmsMessage.FOLDER_INBOX,
            blockReason = if (routed.blocked) routed.reason else "",
        )
        val id = smsDao.insert(message)
        var stored = message.copy(id = id)

        if (!routed.blocked) {
            writeToProviderInbox(stored)?.let { uri ->
                smsDao.setProviderUri(id, uri)
                stored = stored.copy(providerUri = uri)
            }
        }
        return stored
    }

    /** Restore a blocked message to the inbox and whitelist its sender. */
    suspend fun restore(id: Long) {
        val message = smsDao.getById(id) ?: return
        smsDao.setFolder(id, SmsMessage.FOLDER_INBOX, "")
        writeToProviderInbox(message)?.let { smsDao.setProviderUri(id, it) }
        // Restoring means "this sender is fine" — stop blocking them.
        neverBlock(message.sender)
    }

    suspend fun neverBlock(sender: String) {
        senderPrefDao.upsert(SmsSenderPref(sender = sender))
    }

    /** Permanently delete one message (explicit user action only). */
    suspend fun delete(id: Long) {
        smsDao.delete(id)
    }

    suspend fun markRead(id: Long) = smsDao.markRead(id)
    suspend fun markThreadRead(sender: String) = smsDao.markThreadRead(sender)

    /** Send a text and persist it to the provider Sent box + Room. */
    suspend fun send(destination: String, body: String): Boolean {
        val sent = runCatching {
            val manager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                context.getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            } ?: return false
            val parts = manager.divideMessage(body)
            if (parts.size > 1) {
                manager.sendMultipartTextMessage(destination, null, parts, null, null)
            } else {
                manager.sendTextMessage(destination, null, body, null, null)
            }
            true
        }.getOrDefault(false)
        if (!sent) return false

        val timestamp = System.currentTimeMillis()
        val id = smsDao.insert(
            SmsMessage(
                sender = destination,
                body = body,
                timestamp = timestamp,
                folder = SmsMessage.FOLDER_SENT,
                read = true,
            ),
        )
        runCatching {
            val values = ContentValues().apply {
                put(Telephony.Sms.ADDRESS, destination)
                put(Telephony.Sms.BODY, body)
                put(Telephony.Sms.DATE, timestamp)
                put(Telephony.Sms.READ, 1)
            }
            context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
        }.getOrNull()?.let { smsDao.setProviderUri(id, it.toString()) }
        return true
    }

    /** Default-SMS-app duty: persist delivered messages to the system provider. */
    private fun writeToProviderInbox(message: SmsMessage): String? = runCatching {
        val values = ContentValues().apply {
            put(Telephony.Sms.ADDRESS, message.sender)
            put(Telephony.Sms.BODY, message.body)
            put(Telephony.Sms.DATE, message.timestamp)
            put(Telephony.Sms.READ, 0)
            put(Telephony.Sms.SEEN, 0)
        }
        context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)?.toString()
    }.onFailure { Log.w(TAG, "Provider inbox write failed", it) }.getOrNull()

    companion object {
        private const val TAG = "SmsRepository"
    }
}
