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
 * The user-enabled OTP auto-delete (§6.5/§8.2): every few hours, delete
 * OTP-labeled Inbox messages older than 24 hours. Opt-in — this worker is only
 * scheduled while the setting is on, and re-checks the flag on each run in
 * case a cancel raced a queued run. The repository/DAO layer enforces the
 * hard scope guarantee (OTP label + Inbox only, never filtered folders).
 */
class OtpCleanupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    /**
     * R-29: `runCatching` swallowed [kotlinx.coroutines.CancellationException]
     * too, so work the system had STOPPED reported itself as a transient
     * failure and was rescheduled. Cancellation must propagate — only real
     * failures earn a retry.
     */
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!OtpCleanup.isEnabled(ctx)) return Result.success()
        return try {
            MessageRepository.get(ctx).cleanupExpiredOtps(OtpCleanup.TTL_MS)
            Result.success()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            Result.retry()
        }
    }
}

object OtpCleanup {
    const val WORK_NAME = "otp_cleanup"
    const val TTL_MS = 24L * 60 * 60 * 1000
    private const val PREFS = "settings"
    private const val KEY_ENABLED = "otp_auto_delete"
    private const val INTERVAL_HOURS = 6L // checks 4×/day; deletes land 24–30h after receipt

    fun isEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, false) // opt-in: OFF by default (§8.2)

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
            PeriodicWorkRequestBuilder<OtpCleanupWorker>(INTERVAL_HOURS, TimeUnit.HOURS).build(),
        )
    }
}
