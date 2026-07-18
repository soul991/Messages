package com.personal.detectivedialer.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import androidx.core.content.ContextCompat
import com.personal.detectivedialer.data.sms.SmsAddress
import com.personal.detectivedialer.service.ContactsHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/** One SMS conversation for the Messages list. */
data class SmsConversation(
    val threadId: Long,
    val address: String,
    val displayName: String,
    val snippet: String,
    val date: Long,
    val unread: Int,
    /** Whether a reply to [address] can actually reach someone (real number, not a header/short code). */
    val repliable: Boolean,
)

/** One message inside a conversation. */
data class ProviderSms(
    val id: Long,
    val address: String,
    val body: String,
    val date: Long,
    val incoming: Boolean,
)

/** A page of a thread's messages plus whether older ones remain to load. */
data class ThreadPage(
    val messages: List<ProviderSms>,
    val hasMore: Boolean,
    /** Whether replies to this thread's sender are deliverable. */
    val repliable: Boolean,
)

/**
 * Reads SMS conversations and threads from the system Telephony provider (the
 * real device inbox), so the Messages tab shows everything — not just what this
 * app handled. Backed by a ContentObserver so the UI updates live. Sending is
 * delegated to [SmsRepository] (SmsManager + provider persistence).
 *
 * Performance (bug batch — Messages load delay):
 *  - the observer is debounced (~250ms): the SMS provider fires several changes
 *    per delivered message (INBOX insert, READ, SEEN), so an undebounced re-query
 *    thrashed the provider and the UI;
 *  - contact-name resolution is cached across queries in [nameCache] rather than
 *    rebuilt per observer fire (same cached-resolution pattern as the call log);
 *  - a thread is read by its indexed THREAD_ID and paginated (recent N, load more
 *    on scroll) instead of scanning the entire Sms table on every change;
 *  - [mapLatest] cancels an in-flight query when another change lands, so bursts
 *    don't queue up stale full scans.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@Singleton
class SmsProviderRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val contacts: ContactsHelper,
    private val smsRepository: SmsRepository,
) {
    /**
     * Contact display names keyed by raw address, shared across every query so a
     * provider change doesn't re-hit ContactsContract for names we already know.
     * Concurrent because queries run on [Dispatchers.IO]. Bounded implicitly by the
     * number of distinct SMS senders, which is small.
     */
    private val nameCache = ConcurrentHashMap<String, String>()

    /** Resolved thread ids keyed by address, so we skip the lookup scan after the first hit. */
    private val threadIdCache = ConcurrentHashMap<String, Long>()

    private fun canRead(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED

    fun observeConversations(): Flow<List<SmsConversation>> =
        contentChanges().mapLatest { queryConversations() }.flowOn(Dispatchers.IO)

    /**
     * Observe one conversation, newest-[limit] messages. Re-queries (debounced) on
     * every provider change; raise [limit] to page in older messages.
     */
    fun observeThread(address: String, limit: Int = DEFAULT_PAGE): Flow<ThreadPage> =
        contentChanges().mapLatest { queryThread(address, limit) }.flowOn(Dispatchers.IO)

    suspend fun send(address: String, body: String): Boolean = smsRepository.send(address, body)

    /** Whether replies to [address] are deliverable — drives the composer's presence. */
    fun isRepliable(address: String): Boolean = SmsAddress.isRepliable(address)

    // ── Queries ──────────────────────────────────────────────────────────
    private fun queryConversations(): List<SmsConversation> {
        if (!canRead()) return emptyList()
        val projection = arrayOf(
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.READ,
            Telephony.Sms.TYPE,
        )
        // Rows are newest-first; the first row seen per thread is its latest.
        val latest = LinkedHashMap<Long, SmsConversation>()
        val unreadByThread = HashMap<Long, Int>()

        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI, projection, null, null,
                "${Telephony.Sms.DATE} DESC",
            )?.use { c ->
                val threadIdx = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addrIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val readIdx = c.getColumnIndexOrThrow(Telephony.Sms.READ)
                val typeIdx = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                while (c.moveToNext()) {
                    val threadId = c.getLong(threadIdx)
                    val address = c.getString(addrIdx).orEmpty()
                    val read = c.getInt(readIdx)
                    val type = c.getInt(typeIdx)
                    if (read == 0 && type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                        unreadByThread[threadId] = (unreadByThread[threadId] ?: 0) + 1
                    }
                    if (latest.containsKey(threadId)) continue
                    // Remember the thread id for this address so opening the thread
                    // skips the resolution scan.
                    if (address.isNotBlank()) threadIdCache.putIfAbsent(address, threadId)
                    val name = resolveName(address)
                    latest[threadId] = SmsConversation(
                        threadId = threadId,
                        address = address,
                        displayName = name,
                        snippet = c.getString(bodyIdx).orEmpty(),
                        date = c.getLong(dateIdx),
                        unread = 0,
                        repliable = SmsAddress.isRepliable(address),
                    )
                }
            }
        }
        return latest.values
            .map { it.copy(unread = unreadByThread[it.threadId] ?: 0) }
            .sortedByDescending { it.date }
    }

    /**
     * Newest-[limit] messages of the thread for [address], returned oldest-first
     * for display. Resolves the thread id once (cached) then reads by the indexed
     * THREAD_ID with a LIMIT, so this stays cheap no matter how large the inbox is.
     */
    private fun queryThread(address: String, limit: Int): ThreadPage {
        val empty = ThreadPage(emptyList(), hasMore = false, repliable = SmsAddress.isRepliable(address))
        if (!canRead() || address.isBlank()) return empty
        val threadId = threadIdFor(address) ?: return empty

        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
        )
        // Pull one extra row to detect whether older messages remain for paging.
        val fetch = limit + 1
        val newestFirst = ArrayList<ProviderSms>(fetch)
        runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                "${Telephony.Sms.THREAD_ID} = ?",
                arrayOf(threadId.toString()),
                "${Telephony.Sms.DATE} DESC LIMIT $fetch",
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                val addrIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                while (c.moveToNext()) {
                    val type = c.getInt(typeIdx)
                    newestFirst.add(
                        ProviderSms(
                            id = c.getLong(idIdx),
                            address = c.getString(addrIdx).orEmpty(),
                            body = c.getString(bodyIdx).orEmpty(),
                            date = c.getLong(dateIdx),
                            incoming = type == Telephony.Sms.MESSAGE_TYPE_INBOX,
                        ),
                    )
                }
            }
        }
        val hasMore = newestFirst.size > limit
        val page = if (hasMore) newestFirst.subList(0, limit) else newestFirst
        return ThreadPage(
            messages = page.sortedBy { it.date },
            hasMore = hasMore,
            repliable = SmsAddress.isRepliable(address),
        )
    }

    /**
     * Thread id for an address. Uses the cache populated by the conversation list;
     * on a cache miss (e.g. a thread opened straight from a notification deep link)
     * finds it with a single date-ordered scan that stops at the first number match
     * — [PhoneNumberUtils.compare] tolerates formatting differences the way the old
     * full-thread scan did.
     */
    @Suppress("DEPRECATION") // PhoneNumberUtils.compare(String,String): right cross-version fit for minSdk 29.
    private fun threadIdFor(address: String): Long? {
        threadIdCache[address]?.let { return it }
        val projection = arrayOf(Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS)
        val resolved = runCatching {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI, projection, null, null,
                "${Telephony.Sms.DATE} DESC",
            )?.use { c ->
                val threadIdx = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addrIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                while (c.moveToNext()) {
                    val rowAddress = c.getString(addrIdx).orEmpty()
                    if (PhoneNumberUtils.compare(rowAddress, address)) return@use c.getLong(threadIdx)
                }
                null
            }
        }.getOrNull()
        if (resolved != null) threadIdCache[address] = resolved
        return resolved
    }

    /** Cached contact-name resolution; falls back to the address, then "Unknown". */
    private fun resolveName(address: String): String =
        nameCache.getOrPut(address) {
            contacts.displayNameFor(address) ?: address.ifBlank { "Unknown" }
        }

    /** Emits once immediately, then a debounced Unit whenever the SMS provider changes. */
    private fun contentChanges(): Flow<Unit> = callbackFlow {
        val handler = Handler(Looper.getMainLooper())
        // Coalesce the burst of provider changes a single delivered/sent message
        // fires (INBOX insert + READ + SEEN) into one re-query.
        val emit = Runnable { trySend(Unit) }
        val observer = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                handler.removeCallbacks(emit)
                handler.postDelayed(emit, DEBOUNCE_MS)
            }
        }
        context.contentResolver.registerContentObserver(Telephony.Sms.CONTENT_URI, true, observer)
        trySend(Unit) // first load is immediate; only subsequent changes are debounced
        awaitClose {
            handler.removeCallbacks(emit)
            context.contentResolver.unregisterContentObserver(observer)
        }
    }

    companion object {
        /** Debounce window for coalescing provider-change bursts. */
        private const val DEBOUNCE_MS = 250L

        /** Messages loaded per thread page; older ones page in on scroll-to-top. */
        const val DEFAULT_PAGE = 50
    }
}
