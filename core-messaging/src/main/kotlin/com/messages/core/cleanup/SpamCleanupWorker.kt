package com.messages.core.cleanup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.messages.core.MessageRepository
import java.util.concurrent.TimeUnit

/**
 * Optional auto-clean of Spam > 90 days old (§6.5): OFF by default, cleaned
 * spam goes through Trash like any user deletion (60-day restore window),
 * and only the Spam folder is touched — never Review or Blocked.
 */
class SpamCleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!SpamCleanup.isEnabled(ctx)) return Result.success()
        runCatching { MessageRepository.get(ctx).cleanupExpiredSpam(SpamCleanup.TTL_MS) }
            .onFailure { t ->
                if (t is kotlin.coroutines.cancellation.CancellationException) throw t
                return Result.retry()
            }
        return Result.success()
    }
}

object SpamCleanup {
    const val WORK_NAME = "spam_cleanup"
    const val TTL_MS = 90L * 24L * 60L * 60L * 1000L // 90 days
    private const val PREFS = "settings"
    private const val KEY_ENABLED = "spam_auto_delete_90d"
    private const val INTERVAL_DAYS = 7L // checks weekly

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false)

    /** Toggle the setting and (un)schedule the periodic worker to match. */
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
        if (enabled) schedule(context, ExistingPeriodicWorkPolicy.UPDATE)
        else WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** App-start safety net: make sure the worker exists iff the setting is on. */
    fun ensureScheduled(context: Context) {
        if (isEnabled(context)) schedule(context, ExistingPeriodicWorkPolicy.KEEP)
    }

    private fun schedule(context: Context, policy: ExistingPeriodicWorkPolicy) {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            policy,
            PeriodicWorkRequestBuilder<SpamCleanupWorker>(INTERVAL_DAYS, TimeUnit.DAYS).build(),
        )
    }
}
