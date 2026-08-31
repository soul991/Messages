package com.messages.app.update

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.messages.app.MessagesApp
import com.messages.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Weekly "is there a newer release?" check.
 *
 * Off by default. Runs only when the user has switched on automatic checking,
 * and only on an **unmetered** network — [NetworkType.UNMETERED] is a WorkManager
 * constraint, so the job is not merely skipped on mobile data, it is never run
 * by the system in the first place. That is the enforcement point; the worker
 * body does not re-check because it cannot be reached otherwise.
 *
 * Posts at most one notification per release tag (see
 * [UpdateCheck.lastNotifiedTag]) so a user who chooses not to upgrade is not
 * reminded every week.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // The toggle can be switched off between scheduling and running.
        if (!UpdateCheck.autoCheckEnabled(applicationContext)) return Result.success()

        val result = withContext(Dispatchers.IO) { UpdateCheck.check() }

        // Offline / rate-limited / malformed: nothing to say. Not a retry —
        // the next weekly pass is soon enough, and retrying an IP-based quota
        // failure only spends more of it.
        val available = result as? UpdateCheck.UpdateResult.Available
            ?: return Result.success()

        if (!UpdateCheck.notifyEnabled(applicationContext)) return Result.success()
        if (UpdateCheck.lastNotifiedTag(applicationContext) == available.version) {
            return Result.success()
        }

        notify(available.version, available.pageUrl)
        UpdateCheck.setLastNotifiedTag(applicationContext, available.version)
        return Result.success()
    }

    private fun notify(version: String, pageUrl: String) {
        val granted = ContextCompat.checkSelfPermission(
            applicationContext, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) return

        val open = PendingIntent.getActivity(
            applicationContext,
            REQUEST_CODE,
            Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = NotificationCompat.Builder(applicationContext, MessagesApp.CH_UPDATES)
            .setSmallIcon(R.drawable.ic_notif_message)
            .setContentTitle(
                applicationContext.getString(R.string.update_notif_title, version)
            )
            .setContentText(applicationContext.getString(R.string.update_notif_body))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, builder.build())
    }

    companion object {
        private const val WORK_NAME = "update_check_periodic"
        private const val NOTIFICATION_ID = -300
        private const val REQUEST_CODE = 9_310

        /**
         * Enqueue or cancel the weekly job to match the current setting.
         * Safe to call repeatedly — [ExistingPeriodicWorkPolicy.UPDATE] keeps
         * one job and refreshes its constraints.
         */
        fun reschedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            if (!UpdateCheck.autoCheckEnabled(context)) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            wm.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<UpdateCheckWorker>(7, TimeUnit.DAYS)
                    .setConstraints(
                        Constraints.Builder()
                            // Never on metered data. Not a preference: the
                            // system will not run the job at all otherwise.
                            .setRequiredNetworkType(NetworkType.UNMETERED)
                            .build(),
                    )
                    .build(),
            )
        }
    }
}
