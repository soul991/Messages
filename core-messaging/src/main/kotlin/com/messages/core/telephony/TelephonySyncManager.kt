package com.messages.core.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Telephony
import android.util.Log
import com.messages.core.MessageRepository
import com.messages.core.backfill.BackfillWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Manages incremental delta synchronization from Android's Telephony provider.
 *
 * When another app (such as Google Messages) is the default SMS handler,
 * incoming/outgoing SMS and MMS land directly in Telephony without our receivers
 * being notified. This manager tracks the highest known IDs (`lastKnownSmsId`,
 * `lastKnownMmsId`) in SharedPreferences and queries for new rows (`_id > lastKnownId`)
 * when:
 *  1. [MainActivity.onResume] fires
 *  2. Default SMS status changes
 *  3. Telephony [ContentObserver] fires while the application is alive.
 *
 * New messages are indexed via [MessageRepository.indexHistorical] and
 * [MessageRepository.indexHistoricalMms] with `bumpUnread = true`, ensuring
 * they appear in all conversation tabs and are categorized by the protection engine.
 */
object TelephonySyncManager {

    private const val TAG = "TelephonySyncManager"
    const val PREFS_NAME = "telephony_sync"
    const val KEY_LAST_KNOWN_SMS_ID = "last_known_sms_id"
    const val KEY_LAST_KNOWN_MMS_ID = "last_known_mms_id"

    private const val DEBOUNCE_DELAY_MS = 1_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    @Volatile
    private var observerRegistered = false
    private var telephonyObserver: ContentObserver? = null
    private var pendingSyncJob: Job? = null

    data class SyncResult(val smsCount: Int, val mmsCount: Int)

    private fun hasSmsPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /**
     * Register a [ContentObserver] on Telephony.Sms and Telephony.Mms while the app is alive.
     * Idempotent and thread-safe. Retains strong reference to prevent GC collection.
     */
    fun ensureObserver(context: Context) {
        if (observerRegistered || !hasSmsPermission(context)) return
        val appContext = context.applicationContext
        try {
            val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                override fun onChange(selfChange: Boolean, uri: Uri?) {
                    super.onChange(selfChange, uri)
                    scheduleDebouncedSync(appContext)
                }
            }
            telephonyObserver = observer
            appContext.contentResolver.registerContentObserver(
                Telephony.Sms.CONTENT_URI,
                true,
                observer,
            )
            appContext.contentResolver.registerContentObserver(
                Telephony.Mms.CONTENT_URI,
                true,
                observer,
            )
            observerRegistered = true
            Log.d(TAG, "Telephony ContentObserver registered")
        } catch (e: Exception) {
            Log.w(TAG, "Failed to register Telephony ContentObserver", e)
        }
    }

    private fun scheduleDebouncedSync(context: Context) {
        pendingSyncJob?.cancel()
        pendingSyncJob = scope.launch {
            delay(DEBOUNCE_DELAY_MS)
            sync(context)
        }
    }

    /**
     * Trigger a background sync immediately.
     */
    fun triggerSync(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            sync(appContext)
        }
    }

    /**
     * Perform the delta sync on Dispatchers.IO.
     */
    suspend fun sync(context: Context): SyncResult = syncMutex.withLock {
        if (!hasSmsPermission(context)) {
            return SyncResult(0, 0)
        }
        val appContext = context.applicationContext
        val repo = MessageRepository.get(appContext)
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

        val smsCount = syncSmsDelta(appContext, repo, prefs)
        val mmsCount = syncMmsDelta(appContext, repo, prefs)

        if (smsCount > 0 || mmsCount > 0) {
            Log.i(TAG, "Synced delta messages: $smsCount SMS, $mmsCount MMS")
        }
        SyncResult(smsCount, mmsCount)
    }

    private suspend fun syncSmsDelta(
        context: Context,
        repo: MessageRepository,
        prefs: android.content.SharedPreferences,
    ): Int {
        var lastKnownId = prefs.getLong(KEY_LAST_KNOWN_SMS_ID, -1L)
        if (lastKnownId < 0L) {
            // First time tracking: initialize from max smsId in Room DB
            val maxDbId = repo.db.messages().maxSmsId() ?: 0L
            lastKnownId = maxDbId
            prefs.edit().putLong(KEY_LAST_KNOWN_SMS_ID, lastKnownId).apply()
        }

        var count = 0
        var highestIdSeen = lastKnownId
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.THREAD_ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE,
            Telephony.Sms.READ,
        )
        val selection = "${Telephony.Sms._ID} > ? AND ${Telephony.Sms.TYPE} IN (?, ?)"
        val selectionArgs = arrayOf(
            lastKnownId.toString(),
            Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
            Telephony.Sms.MESSAGE_TYPE_SENT.toString(),
        )
        val sortOrder = "${Telephony.Sms._ID} ASC"

        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder,
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(Telephony.Sms._ID)
                val threadIdIdx = c.getColumnIndexOrThrow(Telephony.Sms.THREAD_ID)
                val addrIdx = c.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
                val bodyIdx = c.getColumnIndexOrThrow(Telephony.Sms.BODY)
                val dateIdx = c.getColumnIndexOrThrow(Telephony.Sms.DATE)
                val typeIdx = c.getColumnIndexOrThrow(Telephony.Sms.TYPE)
                val readIdx = c.getColumnIndexOrThrow(Telephony.Sms.READ)

                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val rawThreadId = c.getLong(threadIdIdx)
                    val address = c.getString(addrIdx) ?: ""
                    val body = c.getString(bodyIdx) ?: ""
                    val date = c.getLong(dateIdx)
                    val type = c.getInt(typeIdx)
                    val read = c.getInt(readIdx) == 1
                    val isOutgoing = type == Telephony.Sms.MESSAGE_TYPE_SENT

                    val effectiveThreadId = if (rawThreadId > 0) rawThreadId else {
                        try {
                            Telephony.Threads.getOrCreateThreadId(context, address)
                        } catch (_: Exception) { 0L }
                    }

                    val effectiveAddress = address.ifBlank { "Unknown" }
                    val inserted = repo.indexHistorical(
                        smsId = id,
                        threadId = effectiveThreadId,
                        address = effectiveAddress,
                        body = body,
                        timestamp = date,
                        isOutgoing = isOutgoing,
                        read = read,
                        bumpUnread = true,
                    )
                    if (inserted) {
                        count++
                        com.messages.core.audit.ReceiveAudit.record(
                            com.messages.core.audit.ReceiveAudit.Stage.STORED,
                            effectiveAddress, body, "sync delta smsId=$id"
                        )
                    }

                    if (id > highestIdSeen) {
                        highestIdSeen = id
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying SMS delta from Telephony", e)
        }

        if (highestIdSeen > lastKnownId) {
            prefs.edit().putLong(KEY_LAST_KNOWN_SMS_ID, highestIdSeen).apply()
        }
        return count
    }

    private suspend fun syncMmsDelta(
        context: Context,
        repo: MessageRepository,
        prefs: android.content.SharedPreferences,
    ): Int {
        var lastKnownId = prefs.getLong(KEY_LAST_KNOWN_MMS_ID, -1L)
        if (lastKnownId < 0L) {
            val maxDbId = repo.db.messages().maxMmsId() ?: 0L
            lastKnownId = maxDbId
            prefs.edit().putLong(KEY_LAST_KNOWN_MMS_ID, lastKnownId).apply()
        }

        var count = 0
        var highestIdSeen = lastKnownId
        val projection = arrayOf(
            Telephony.Mms._ID,
            Telephony.Mms.THREAD_ID,
            Telephony.Mms.DATE,
            Telephony.Mms.MESSAGE_BOX,
            Telephony.Mms.READ,
        )
        val selection = "${Telephony.Mms._ID} > ? AND ${Telephony.Mms.MESSAGE_BOX} IN (?, ?)"
        val selectionArgs = arrayOf(
            lastKnownId.toString(),
            Telephony.Mms.MESSAGE_BOX_INBOX.toString(),
            Telephony.Mms.MESSAGE_BOX_SENT.toString(),
        )
        val sortOrder = "${Telephony.Mms._ID} ASC"

        try {
            context.contentResolver.query(
                Telephony.Mms.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder,
            )?.use { c ->
                val idIdx = c.getColumnIndexOrThrow(Telephony.Mms._ID)
                val threadIdIdx = c.getColumnIndexOrThrow(Telephony.Mms.THREAD_ID)
                val dateIdx = c.getColumnIndexOrThrow(Telephony.Mms.DATE)
                val boxIdx = c.getColumnIndexOrThrow(Telephony.Mms.MESSAGE_BOX)
                val readIdx = c.getColumnIndexOrThrow(Telephony.Mms.READ)

                while (c.moveToNext()) {
                    val id = c.getLong(idIdx)
                    val threadId = c.getLong(threadIdIdx)
                    val date = c.getLong(dateIdx)
                    val box = c.getInt(boxIdx)
                    val read = c.getInt(readIdx) == 1
                    val isOutgoing = box == Telephony.Mms.MESSAGE_BOX_SENT

                    if (id > highestIdSeen) {
                        highestIdSeen = id
                    }

                    val address = BackfillWorker.mmsAddress(context, id, isOutgoing)
                    if (address.isNotBlank()) {
                        val content = BackfillWorker.mmsContent(context, id)
                        if (content.body.isNotBlank() || content.mediaRef != null) {
                            val inserted = repo.indexHistoricalMms(
                                mmsId = id,
                                threadId = threadId,
                                address = address,
                                body = content.body,
                                timestamp = BackfillWorker.mmsDateToMillis(date),
                                isOutgoing = isOutgoing,
                                read = read,
                                mediaRef = content.mediaRef,
                                mediaMimeType = content.mediaMimeType,
                                bumpUnread = true,
                            )
                            if (inserted) count++
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error querying MMS delta from Telephony", e)
        }

        if (highestIdSeen > lastKnownId) {
            prefs.edit().putLong(KEY_LAST_KNOWN_MMS_ID, highestIdSeen).apply()
        }
        return count
    }
}
