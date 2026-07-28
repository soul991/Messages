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
 * First-run backfill (§10): classify the existing SMS history from the
 * Telephony provider, newest-first, resumable.
 *
 * Resumability: keyset pagination on (date DESC, _id DESC) with the cursor
 * position checkpointed to SharedPreferences after every batch — if the
 * process dies mid-run, WorkManager re-runs the worker and it continues from
 * the checkpoint instead of starting over. Already-indexed messages (e.g.
 * received live before the backfill reached them) are skipped via the unique
 * smsId index.
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
        if (prefs.getBoolean(KEY_DONE, false)) return Result.success()

        val repo = MessageRepository.get(ctx)
        var checkpointDate = prefs.getLong(KEY_CHECKPOINT_DATE, Long.MAX_VALUE)
        var checkpointId = prefs.getLong(KEY_CHECKPOINT_ID, Long.MAX_VALUE)
        var processed = prefs.getInt(KEY_PROCESSED, 0)

        var total = prefs.getInt(KEY_TOTAL, -1)
        if (total == -1) {
            total = countAll(ctx)
            prefs.edit().putInt(KEY_TOTAL, total).apply()
        }

        val failedIds = readFailedIds(prefs).toMutableSet()
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

        // R-29: persist which rows failed so the UI can surface them and the
        // worker can retry them on the next run instead of silently declaring
        // the import complete while messages are missing.
        val edit = prefs.edit()
        if (failedIds.isEmpty()) {
            edit.putBoolean(KEY_DONE, true).remove(KEY_FAILED_IDS).remove(KEY_INCOMPLETE)
            edit.apply()
            return Result.success()
        }
        Log.w(TAG, "backfill finished with ${failedIds.size} unindexed messages of $total")
        edit.putString(KEY_FAILED_IDS, failedIds.joinToString(","))
            .putBoolean(KEY_INCOMPLETE, true)
            .apply()
        // Cap retries so permanently-poison rows stop blocking the import.
        // After MAX_ATTEMPTS the worker marks done-with-failures so the user
        // can see the exception list and decide whether to accept it.
        return if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else {
            prefs.edit().putBoolean(KEY_DONE, true).apply()
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

    private fun countAll(ctx: Context): Int =
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

        fun readFailedIds(prefs: android.content.SharedPreferences): Set<Long> =
            prefs.getString(KEY_FAILED_IDS, null)
                ?.split(",")?.mapNotNull { it.toLongOrNull() }?.toSet()
                ?: emptySet()
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
        if (prefs.getBoolean(BackfillWorker.KEY_DONE, false)) return
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

    /** Provider _id values that could not be indexed (empty when none). */
    fun unindexedIds(context: Context): Set<Long> =
        BackfillWorker.readFailedIds(
            context.getSharedPreferences(BackfillWorker.PREFS, Context.MODE_PRIVATE),
        )

    /**
     * User explicitly accepts the partial import: mark done and clear the
     * failure list so the UI stops surfacing the warning.
     */
    fun acceptIncomplete(context: Context) {
        context.getSharedPreferences(BackfillWorker.PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(BackfillWorker.KEY_DONE, true)
            .remove(BackfillWorker.KEY_FAILED_IDS)
            .remove(BackfillWorker.KEY_INCOMPLETE)
            .apply()
    }
}
