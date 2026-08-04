package com.messages.core.backfill

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.messages.core.MessageRepository

/**
 * First-run backfill (§10): classify the existing SMS **and MMS** history from
 * the Telephony provider, newest-first, resumable.
 *
 * Two phases, each with its own checkpoint: SMS first (the bulk of most
 * histories), then MMS. Resumability: keyset pagination on (date DESC, _id
 * DESC) with the cursor position checkpointed to SharedPreferences after every
 * batch — if the process dies mid-run, WorkManager re-runs the worker and it
 * continues from the checkpoint instead of starting over. Already-indexed
 * messages (e.g. received live before the backfill reached them) are skipped
 * via the unique smsId/mmsId indexes, so a re-run is additive, never
 * duplicating.
 *
 * V2-25: MMS rows carry their attachment as a `content://mms/part/<id>`
 * reference rather than a copy — see `MediaRef`. An MMS history can run to
 * gigabytes, and those bytes already live in the shared Telephony store.
 *
 * Backfilled messages never notify and never bump unread counts — they are
 * history, not news.
 */
class BackfillWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (ctx.checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            // Scheduled before the grant landed; next launch re-enqueues.
            return Result.failure()
        }
        val prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // V2-25: KEY_MMS_DONE gates the second phase. A device that finished
        // the SMS-only backfill before this version has KEY_DONE set and would
        // otherwise never see its MMS history, so completion now needs both.
        if (prefs.getBoolean(KEY_DONE, false) && prefs.getBoolean(KEY_MMS_DONE, false)) {
            return Result.success()
        }

        val repo = MessageRepository.get(ctx)
        var processed = prefs.getInt(KEY_PROCESSED, 0)

        // Totals are counted once and cached. On pre-V2-25 prefs KEY_TOTAL held
        // the SMS count alone; adopt it as the SMS total so the counter doesn't
        // jump backwards mid-import, then widen KEY_TOTAL to cover both phases.
        val smsTotal = prefs.getInt(KEY_SMS_TOTAL, -1).takeIf { it >= 0 }
            ?: prefs.getInt(KEY_TOTAL, -1).takeIf { it >= 0 }
            ?: countSms(ctx)
        val mmsTotal = prefs.getInt(KEY_MMS_TOTAL, -1).takeIf { it >= 0 } ?: countMms(ctx)
        val total = smsTotal + mmsTotal
        prefs.edit()
            .putInt(KEY_SMS_TOTAL, smsTotal)
            .putInt(KEY_MMS_TOTAL, mmsTotal)
            .putInt(KEY_TOTAL, total)
            .apply()

        val failedIds = readFailedIds(prefs).toMutableSet()
        val failedMmsIds = readFailedMmsIds(prefs).toMutableSet()

        // ---- Phase 1: SMS ----
        var checkpointDate = prefs.getLong(KEY_CHECKPOINT_DATE, Long.MAX_VALUE)
        var checkpointId = prefs.getLong(KEY_CHECKPOINT_ID, Long.MAX_VALUE)
        try {
            while (true) {
                val batch = queryBatch(ctx, checkpointDate, checkpointId)
                if (batch.isEmpty()) break
                for (row in batch) {
                    if (row.address.isNotBlank() && row.body.isNotBlank()) {
                        try {
                            repo.indexHistorical(
                                smsId = row.id,
                                threadId = row.threadId,
                                address = row.address,
                                body = row.body,
                                timestamp = row.date,
                                isOutgoing = row.type == Telephony.Sms.MESSAGE_TYPE_SENT,
                                read = row.read,
                            )
                        } catch (t: Throwable) {
                            // Cancellation is not a row failure: WorkManager is
                            // stopping us; the checkpoint lets the retry resume.
                            if (t is kotlin.coroutines.cancellation.CancellationException) throw t
                            // One poison message must not kill the whole import
                            // (§14.2 never-lose). Log it, keep going.
                            Log.e(TAG, "indexHistorical failed for sms ${row.id}", t)
                            failedIds.add(row.id)
                        }
                    }
                }
                processed += batch.size
                checkpointDate = batch.last().date
                checkpointId = batch.last().id
                prefs.edit()
                    .putLong(KEY_CHECKPOINT_DATE, checkpointDate)
                    .putLong(KEY_CHECKPOINT_ID, checkpointId)
                    .putInt(KEY_PROCESSED, processed)
                    .apply()
                setProgress(workDataOf(KEY_PROCESSED to processed, KEY_TOTAL to total))
            }
        } catch (t: Throwable) {
            // Let cancellation propagate so a stopped worker actually stops
            // instead of being reported as a retryable batch failure.
            if (t is kotlin.coroutines.cancellation.CancellationException) throw t
            // Throwable, not Exception: an Error here previously marked the work
            // FAILED with no retry and the import silently never happened.
            Log.e(TAG, "backfill batch failed at checkpoint $checkpointDate/$checkpointId — retrying", t)
            return Result.retry() // resumes from the checkpoint
        }

        // ---- Phase 2: MMS (V2-25) ----
        var mmsCheckpointDate = prefs.getLong(KEY_MMS_CHECKPOINT_DATE, Long.MAX_VALUE)
        var mmsCheckpointId = prefs.getLong(KEY_MMS_CHECKPOINT_ID, Long.MAX_VALUE)
        try {
            while (true) {
                val batch = queryMmsBatch(ctx, mmsCheckpointDate, mmsCheckpointId)
                if (batch.isEmpty()) break
                for (row in batch) {
                    try {
                        indexMms(ctx, repo, row)
                    } catch (t: Throwable) {
                        if (t is kotlin.coroutines.cancellation.CancellationException) throw t
                        Log.e(TAG, "indexHistoricalMms failed for mms ${row.id}", t)
                        failedMmsIds.add(row.id)
                    }
                }
                processed += batch.size
                mmsCheckpointDate = batch.last().date
                mmsCheckpointId = batch.last().id
                prefs.edit()
                    .putLong(KEY_MMS_CHECKPOINT_DATE, mmsCheckpointDate)
                    .putLong(KEY_MMS_CHECKPOINT_ID, mmsCheckpointId)
                    .putInt(KEY_PROCESSED, processed)
                    .apply()
                setProgress(workDataOf(KEY_PROCESSED to processed, KEY_TOTAL to total))
            }
        } catch (t: Throwable) {
            if (t is kotlin.coroutines.cancellation.CancellationException) throw t
            Log.e(TAG, "mms backfill batch failed at $mmsCheckpointDate/$mmsCheckpointId — retrying", t)
            return Result.retry() // resumes from the MMS checkpoint
        }

        // R-29: persist which rows failed so the UI can surface them and the
        // worker can retry them on the next run instead of silently declaring
        // the import complete while messages are missing.
        val edit = prefs.edit()
        if (failedIds.isEmpty() && failedMmsIds.isEmpty()) {
            edit.putBoolean(KEY_DONE, true).putBoolean(KEY_MMS_DONE, true)
                .remove(KEY_FAILED_IDS).remove(KEY_FAILED_MMS_IDS).remove(KEY_INCOMPLETE)
            edit.apply()
            return Result.success()
        }
        Log.w(
            TAG,
            "backfill finished with ${failedIds.size + failedMmsIds.size} unindexed messages of $total",
        )
        edit.putString(KEY_FAILED_IDS, failedIds.joinToString(","))
            .putString(KEY_FAILED_MMS_IDS, failedMmsIds.joinToString(","))
            .putBoolean(KEY_INCOMPLETE, true)
            .apply()
        // Cap retries so permanently-poison rows stop blocking the import.
        // After MAX_ATTEMPTS the worker marks done-with-failures so the user
        // can see the exception list and decide whether to accept it.
        return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else {
            prefs.edit().putBoolean(KEY_DONE, true).putBoolean(KEY_MMS_DONE, true).apply()
            Result.success()
        }
    }

    private data class Row(
        val id: Long,
        val threadId: Long,
        val address: String,
        val body: String,
        val date: Long,
        val type: Int,
        val read: Boolean,
    )

    /** Next page strictly after the checkpoint in (date DESC, _id DESC) order. */
    private fun queryBatch(ctx: Context, beforeDate: Long, beforeId: Long): List<Row> {
        val rows = mutableListOf<Row>()
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(
                Telephony.Sms._ID, Telephony.Sms.THREAD_ID, Telephony.Sms.ADDRESS,
                Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE, Telephony.Sms.READ,
            ),
            "(${Telephony.Sms.DATE} < ? OR (${Telephony.Sms.DATE} = ? AND ${Telephony.Sms._ID} < ?)) " +
                "AND ${Telephony.Sms.TYPE} IN (?, ?)",
            arrayOf(
                beforeDate.toString(), beforeDate.toString(), beforeId.toString(),
                Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
                Telephony.Sms.MESSAGE_TYPE_SENT.toString(),
            ),
            "${Telephony.Sms.DATE} DESC, ${Telephony.Sms._ID} DESC LIMIT $BATCH_SIZE",
        )?.use { c ->
            while (c.moveToNext()) {
                rows += Row(
                    id = c.getLong(0),
                    threadId = c.getLong(1),
                    address = c.getString(2) ?: "",
                    body = c.getString(3) ?: "",
                    date = c.getLong(4),
                    type = c.getInt(5),
                    read = c.getInt(6) == 1,
                )
            }
        }
        return rows
    }

    private fun countSms(ctx: Context): Int =
        ctx.contentResolver.query(
            Telephony.Sms.CONTENT_URI,
            arrayOf(Telephony.Sms._ID),
            "${Telephony.Sms.TYPE} IN (?, ?)",
            arrayOf(
                Telephony.Sms.MESSAGE_TYPE_INBOX.toString(),
                Telephony.Sms.MESSAGE_TYPE_SENT.toString(),
            ),
            null,
        )?.use { it.count } ?: 0

    // ---------------- MMS phase (V2-25) ----------------

    private data class MmsRow(
        val id: Long,
        val threadId: Long,
        /** Provider units — seconds on every conforming device. */
        val date: Long,
        val box: Int,
        val read: Boolean,
    )

    /** The text body and first real attachment of one MMS. */
    private data class MmsContent(
        val body: String,
        val mediaRef: String?,
        val mediaMimeType: String?,
    )

    /** Next page of inbox/sent MMS strictly after the checkpoint. */
    private fun queryMmsBatch(ctx: Context, beforeDate: Long, beforeId: Long): List<MmsRow> {
        val rows = mutableListOf<MmsRow>()
        ctx.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(
                Telephony.Mms._ID, Telephony.Mms.THREAD_ID, Telephony.Mms.DATE,
                Telephony.Mms.MESSAGE_BOX, Telephony.Mms.READ,
            ),
            "(${Telephony.Mms.DATE} < ? OR (${Telephony.Mms.DATE} = ? AND ${Telephony.Mms._ID} < ?)) " +
                "AND ${Telephony.Mms.MESSAGE_BOX} IN (?, ?)",
            arrayOf(
                beforeDate.toString(), beforeDate.toString(), beforeId.toString(),
                Telephony.Mms.MESSAGE_BOX_INBOX.toString(),
                Telephony.Mms.MESSAGE_BOX_SENT.toString(),
            ),
            "${Telephony.Mms.DATE} DESC, ${Telephony.Mms._ID} DESC LIMIT $BATCH_SIZE",
        )?.use { c ->
            while (c.moveToNext()) {
                rows += MmsRow(
                    id = c.getLong(0),
                    threadId = c.getLong(1),
                    date = c.getLong(2),
                    box = c.getInt(3),
                    read = c.getInt(4) == 1,
                )
            }
        }
        return rows
    }

    private fun countMms(ctx: Context): Int =
        ctx.contentResolver.query(
            Telephony.Mms.CONTENT_URI,
            arrayOf(Telephony.Mms._ID),
            "${Telephony.Mms.MESSAGE_BOX} IN (?, ?)",
            arrayOf(
                Telephony.Mms.MESSAGE_BOX_INBOX.toString(),
                Telephony.Mms.MESSAGE_BOX_SENT.toString(),
            ),
            null,
        )?.use { it.count } ?: 0

    /**
     * Assemble one MMS from its side tables and hand it to the repository.
     * Rows with no usable sender, or with neither text nor media, are skipped:
     * they are notification/SMIL shells, not messages the user ever saw.
     */
    private suspend fun indexMms(ctx: Context, repo: MessageRepository, row: MmsRow) {
        val isOutgoing = row.box == Telephony.Mms.MESSAGE_BOX_SENT
        val address = mmsAddress(ctx, row.id, isOutgoing)
        if (address.isBlank()) return
        val content = mmsContent(ctx, row.id)
        if (content.body.isBlank() && content.mediaRef == null) return
        repo.indexHistoricalMms(
            mmsId = row.id,
            threadId = row.threadId,
            address = address,
            body = content.body,
            timestamp = mmsDateToMillis(row.date),
            isOutgoing = isOutgoing,
            read = row.read,
            mediaRef = content.mediaRef,
            mediaMimeType = content.mediaMimeType,
        )
    }

    /**
     * The other party's number, from `content://mms/<id>/addr`. For a received
     * message that is the FROM address; for a sent one, the first recipient.
     * Devices are inconsistent enough here that a wrong-typed-but-real address
     * beats none at all, so an unmatched row is kept as a fallback.
     */
    private fun mmsAddress(ctx: Context, mmsId: Long, isOutgoing: Boolean): String {
        val wanted = if (isOutgoing) PDU_ADDR_TO else PDU_ADDR_FROM
        var fallback = ""
        val uri = Telephony.Mms.CONTENT_URI.buildUpon()
            .appendPath(mmsId.toString()).appendPath("addr").build()
        runCatching {
            ctx.contentResolver.query(
                uri,
                arrayOf(Telephony.Mms.Addr.ADDRESS, Telephony.Mms.Addr.TYPE),
                null, null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val addr = c.getString(0)?.trim().orEmpty()
                    // The provider stores this literal placeholder for "this
                    // device" in sent messages; it is not a phone number.
                    if (addr.isBlank() || addr.equals(SELF_ADDRESS_TOKEN, ignoreCase = true)) continue
                    if (c.getInt(1) == wanted) return addr
                    if (fallback.isBlank()) fallback = addr
                }
            }
        }
        return fallback
    }

    /**
     * Concatenate the `text/plain` parts and pick the first displayable
     * attachment. SMIL and HTML parts are presentation scaffolding, not
     * content, so they are dropped rather than shown as an attachment.
     *
     * The attachment is stored as a `content://mms/part/<id>` reference — see
     * `MediaRef` and the class doc for why the bytes are not copied.
     */
    private fun mmsContent(ctx: Context, mmsId: Long): MmsContent {
        val text = StringBuilder()
        var mediaRef: String? = null
        var mediaMimeType: String? = null
        runCatching {
            ctx.contentResolver.query(
                android.net.Uri.parse(PART_URI),
                arrayOf(PART_ID, PART_CONTENT_TYPE, PART_TEXT),
                "$PART_MSG_ID = ?", arrayOf(mmsId.toString()),
                "$PART_ID ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val partId = c.getLong(0)
                    val ct = c.getString(1)?.trim()?.lowercase().orEmpty()
                    when {
                        ct == "text/plain" -> {
                            if (text.length >= MAX_BODY_CHARS) continue
                            // The `text` column holds the decoded body on
                            // virtually every device; when it doesn't, the
                            // bytes are behind the part URI. Read that through
                            // the bounded reader — a carrier-supplied part is
                            // not to be trusted with its own size.
                            val part = c.getString(2)
                                ?: readTextPart(ctx, partId)
                                ?: continue
                            if (part.isBlank()) continue
                            if (text.isNotEmpty()) text.append('\n')
                            text.append(part.trim())
                        }
                        // Layout/scaffolding parts, and rows with no declared
                        // type at all: nothing a user would call an attachment.
                        ct.isBlank() || ct.startsWith("application/smil") || ct == "text/html" -> Unit
                        mediaRef == null -> {
                            mediaRef = "$PART_URI/$partId"
                            mediaMimeType = ct
                        }
                    }
                }
            }
        }
        return MmsContent(text.toString().trim().take(MAX_BODY_CHARS), mediaRef, mediaMimeType)
    }

    private fun readTextPart(ctx: Context, partId: Long): String? =
        com.messages.core.io.BoundedRead
            .readUri(ctx, android.net.Uri.parse("$PART_URI/$partId"), MAX_TEXT_PART_BYTES)
            ?.toString(Charsets.UTF_8)

    companion object {
        private const val TAG = "BackfillWorker"
        const val PREFS = "backfill"
        const val KEY_DONE = "done"
        const val KEY_PROCESSED = "processed"
        const val KEY_TOTAL = "total"
        private const val KEY_CHECKPOINT_DATE = "checkpointDate"
        private const val KEY_CHECKPOINT_ID = "checkpointId"
        const val KEY_FAILED_IDS = "failedIds"
        const val KEY_INCOMPLETE = "incomplete"
        private const val MAX_ATTEMPTS = 3
        private const val BATCH_SIZE = 200

        // V2-25: MMS phase state, kept separate from the SMS phase's — the two
        // tables have independent _id spaces, so sharing a checkpoint or a
        // failure list would conflate unrelated rows.
        const val KEY_MMS_DONE = "mmsDone"
        const val KEY_FAILED_MMS_IDS = "failedMmsIds"
        private const val KEY_SMS_TOTAL = "smsTotal"
        private const val KEY_MMS_TOTAL = "mmsTotal"
        private const val KEY_MMS_CHECKPOINT_DATE = "mmsCheckpointDate"
        private const val KEY_MMS_CHECKPOINT_ID = "mmsCheckpointId"

        /** PDU address types (`content://mms/<id>/addr`): sender and recipient. */
        private const val PDU_ADDR_FROM = 137
        private const val PDU_ADDR_TO = 151
        private const val SELF_ADDRESS_TOKEN = "insert-address-token"

        // `Telephony.Mms.Part` is API 31+; the column names below have been
        // stable since the provider existed, so address them by name. Kept as
        // a String, not a parsed Uri: `Uri.parse` is stubbed to throw in JVM
        // unit tests, and a companion-object field would fail class init there.
        private const val PART_URI = "content://mms/part"
        private const val PART_ID = "_id"
        private const val PART_MSG_ID = "mid"
        private const val PART_CONTENT_TYPE = "ct"
        private const val PART_TEXT = "text"

        /** A body longer than this is a payload, not a message someone typed. */
        private const val MAX_BODY_CHARS = 32_000
        private const val MAX_TEXT_PART_BYTES = 256 * 1024

        fun readFailedIds(prefs: android.content.SharedPreferences): Set<Long> =
            readIds(prefs, KEY_FAILED_IDS)

        fun readFailedMmsIds(prefs: android.content.SharedPreferences): Set<Long> =
            readIds(prefs, KEY_FAILED_MMS_IDS)

        private fun readIds(prefs: android.content.SharedPreferences, key: String): Set<Long> =
            prefs.getString(key, null)
                ?.split(",")?.mapNotNull { it.toLongOrNull() }?.toSet()
                ?: emptySet()

        /**
         * MMS timestamps are seconds since the epoch, unlike SMS. A handful of
         * devices have been seen writing milliseconds; anything past ~year 5138
         * in seconds is one of those, and multiplying it would land the message
         * tens of thousands of years in the future.
         */
        internal fun mmsDateToMillis(date: Long): Long =
            if (date > MMS_DATE_MILLIS_THRESHOLD) date else date * 1000L

        private const val MMS_DATE_MILLIS_THRESHOLD = 100_000_000_000L
    }
}

object Backfill {
    const val WORK_NAME = "first_run_backfill"

    /**
     * Enqueue the backfill once READ_SMS is granted. Idempotent: KEEP policy
     * dedupes while it's pending/running, the "done" flag makes re-enqueues
     * after completion a no-op inside the worker.
     */
    fun ensureScheduled(context: Context) {
        val prefs = context.getSharedPreferences(BackfillWorker.PREFS, Context.MODE_PRIVATE)
        // V2-25: both phases must have finished. On an install that completed
        // the SMS-only backfill, this is what re-enqueues the worker once so it
        // can import the MMS history it never saw.
        if (prefs.getBoolean(BackfillWorker.KEY_DONE, false) &&
            prefs.getBoolean(BackfillWorker.KEY_MMS_DONE, false)
        ) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<BackfillWorker>().build(),
        )
    }

    /** Live (processed, total) for the onboarding counter (§9). */
    fun progressFlow(context: Context) =
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkFlow(WORK_NAME)

    /**
     * Re-import messages: clear the done flag + checkpoint and start over.
     * Already-indexed messages are skipped via the unique smsId index, so
     * re-running is additive and idempotent — never a data risk.
     */
    fun reimport(context: Context) {
        context.getSharedPreferences(BackfillWorker.PREFS, Context.MODE_PRIVATE)
            .edit().clear().apply()
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<BackfillWorker>().build(),
        )
    }

    /** True when the last run finished but left some rows unindexed. */
    fun isIncomplete(context: Context): Boolean =
        context.getSharedPreferences(BackfillWorker.PREFS, Context.MODE_PRIVATE)
            .getBoolean(BackfillWorker.KEY_INCOMPLETE, false)

    /**
     * Provider _id values that could not be indexed (empty when none). SMS and
     * MMS ids come from different tables and may collide numerically — this is
     * a count/diagnostic for the user, not a lookup key.
     */
    fun unindexedIds(context: Context): Set<Long> {
        val prefs = context.getSharedPreferences(BackfillWorker.PREFS, Context.MODE_PRIVATE)
        return BackfillWorker.readFailedIds(prefs) + BackfillWorker.readFailedMmsIds(prefs)
    }

    /**
     * User explicitly accepts the partial import: mark done and clear the
     * failure list so the UI stops surfacing the warning.
     */
    fun acceptIncomplete(context: Context) {
        context.getSharedPreferences(BackfillWorker.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(BackfillWorker.KEY_DONE, true)
            .putBoolean(BackfillWorker.KEY_MMS_DONE, true)
            .remove(BackfillWorker.KEY_FAILED_IDS)
            .remove(BackfillWorker.KEY_FAILED_MMS_IDS)
            .remove(BackfillWorker.KEY_INCOMPLETE)
            .apply()
    }
}
