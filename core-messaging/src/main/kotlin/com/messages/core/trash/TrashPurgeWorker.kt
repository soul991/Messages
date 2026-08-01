package com.messages.core.trash

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.messages.core.MessageRepository
import java.util.concurrent.TimeUnit

/**
 * The 60-day Trash purge (§6.4): a daily WorkManager job that permanently
 * deletes messages trashed more than [TrashRetention.RETENTION_MS] ago. One of
 * only two auto-deletes in the app (§6.6) — it touches exclusively rows the
 * user already deleted to Trash.
 */
class TrashPurgeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    /**
     * R-29: cancellation is not a failure. `runCatching` caught
     * [kotlinx.coroutines.CancellationException] as well, so a run the system
     * stopped came back as `retry()` and got rescheduled; rethrow it so
     * WorkManager sees the stop it asked for.
     */
    override suspend fun doWork(): Result = try {
        val repo = MessageRepository.get(applicationContext)
        repo.purgeExpiredTrash()
        // R-05: the same daily cadence sweeps provider rows whose deletion
        // failed earlier (default-SMS role lost, provider transiently busy).
        repo.retryFailedProviderDeletions()
        Result.success()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        Result.retry()
    }
}

object TrashRetention {
    /**
     * §6.4: trashed messages are restorable for this long, then purged.
     *
     * V2-36: the Trash screen tells the user this number, so it is declared
     * once and read from there. A retention window that changes in code but
     * not in the sentence explaining it is worse than no sentence.
     */
    const val RETENTION_DAYS = 60
    const val RETENTION_MS = RETENTION_DAYS * 24L * 60 * 60 * 1000
    const val WORK_NAME = "trash_purge"

    /** Always-on (unlike OTP cleanup) — idempotent app-start scheduling. */
    fun ensureScheduled(context: Context) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TrashPurgeWorker>(1, TimeUnit.DAYS).build(),
        )
    }

    fun purgeCountdownDays(trashedAt: Long?, now: Long = System.currentTimeMillis()): Int {
        if (trashedAt == null) return RETENTION_DAYS
        val left = trashedAt + RETENTION_MS - now
        return (left / (24L * 60 * 60 * 1000)).toInt().coerceAtLeast(0)
    }
}
