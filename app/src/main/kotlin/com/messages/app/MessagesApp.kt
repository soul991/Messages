package com.messages.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.messages.core.cleanup.OtpCleanup
import com.messages.core.cleanup.SpamCleanup
import com.messages.core.search.FtsBackfill
import com.messages.core.trash.TrashRetention
import com.messages.app.shortcut.ConversationShortcuts
import kotlinx.coroutines.launch

class MessagesApp : Application(), androidx.work.Configuration.Provider {

    override val workManagerConfiguration: androidx.work.Configuration
        get() = androidx.work.Configuration.Builder()
            .setMinimumLoggingLevel(android.util.Log.INFO)
            .build()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
        com.messages.app.ui.home.SwipeActions.init(this)
        com.messages.app.ui.common.DraftStore.init(this)
        com.messages.core.contacts.ContactSync.ensureObserver(this)
        com.messages.core.telephony.TelephonySyncManager.ensureObserver(this)

        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        ).launch {
            runCatching {
                OtpCleanup.ensureScheduled(this@MessagesApp)
                SpamCleanup.ensureScheduled(this@MessagesApp)
                TrashRetention.ensureScheduled(this@MessagesApp)
                FtsBackfill.ensureScheduled(this@MessagesApp)
                com.messages.app.drive.DriveBackup.reschedule(this@MessagesApp)
                com.messages.core.telephony.TelephonySyncManager.triggerSync(this@MessagesApp)
            }
        }
        releaseStaleScheduledClaims()
        settleAbandonedMmsTransactions()
        sealLockedBacklog()
        cleanupSyntheticData()
        checkAndHandleVersionUpgrade()
    }

    /**
     * Purges and refreshes system shortcut cache on version upgrade (§Phase N2).
     * Prevents legacy 2.4.x cached launcher icon bitmaps in ShortcutService from
     * lingering as notification avatar badges.
     */
    private fun checkAndHandleVersionUpgrade() {
        val prefs = getSharedPreferences("app_version_state", Context.MODE_PRIVATE)
        val lastVersionCode = prefs.getInt("last_seen_version_code", 0)
        val currentVersionCode = BuildConfig.VERSION_CODE
        if (lastVersionCode < currentVersionCode) {
            android.util.Log.i("MessagesApp", "Version upgrade detected ($lastVersionCode -> $currentVersionCode); refreshing shortcuts")
            kotlinx.coroutines.CoroutineScope(
                kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
            ).launch {
                ConversationShortcuts.purgeAndRefreshShortcuts(this@MessagesApp)
            }
            prefs.edit().putInt("last_seen_version_code", currentVersionCode).apply()
        }
    }

    /**
     * One-time maintenance cleanup for 2.1.0 synthetic rows and preferences.
     */
    private fun cleanupSyntheticData() {
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        ).launch {
            runCatching {
                val repo = com.messages.core.MessageRepository.get(this@MessagesApp)
                val deleted = com.messages.core.cleanup.SyntheticCleanup.perform(this@MessagesApp, repo.db)
                if (deleted > 0) {
                    android.util.Log.i("MessagesApp", "cleaned up $deleted synthetic message(s)")
                }
            }
        }
    }

    /**
     * V2-6: rows that entered the locked space before encryption-at-rest
     * existed — and rows a previous interrupted pass left half-done — are
     * sealed here, and their Telephony copies deleted.
     *
     * Deliberately NOT a Room migration. Sealing needs the Android Keystore,
     * which a `SupportSQLiteDatabase` migration has no business reaching into,
     * and a migration that throws part-way leaves the user with an app that
     * will not open at all. This pass is idempotent and resumable instead: it
     * re-reads what is left every start, changes only what still needs
     * changing, and a kill at any point costs nothing but the next run.
     */
    private fun sealLockedBacklog() {
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        ).launch {
            runCatching {
                val sealed = com.messages.core.MessageRepository.get(this@MessagesApp)
                    .sealLockedBacklog()
                if (sealed > 0) {
                    android.util.Log.i("MessagesApp", "sealed $sealed locked message(s) at rest")
                }
            }
        }
    }

    /**
     * V2-14/15: an MMS the platform never reported back on would leave its temp
     * PDU on disk and its telephony URI grants outstanding. Settling expired
     * transactions at startup gives every grant a bounded lifetime. Off the main
     * thread — this touches SharedPreferences and the cache directory.
     */
    private fun settleAbandonedMmsTransactions() {
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        ).launch {
            runCatching { com.messages.app.mms.MmsTransactions.pruneExpired(this@MessagesApp) }
        }
    }

    /**
     * V2-19: a scheduled send claimed by a process that was then killed would
     * sit in CLAIMED forever. Nothing can be mid-send at app start, so release
     * every outstanding claim back to SCHEDULED here. Off the main thread:
     * onCreate must not touch the database synchronously.
     */
    private fun releaseStaleScheduledClaims() {
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        ).launch {
            runCatching {
                val released = com.messages.core.MessageRepository.get(this@MessagesApp)
                    .releaseStaleScheduledClaims()
                if (released > 0) {
                    android.util.Log.i("MessagesApp", "released $released stale scheduled claim(s)")
                }
            }
        }
    }

    /**
     * V2-36. Channel names and descriptions are read in the system's Settings
     * app, not ours, so they have to come from the resource table like any
     * other user-visible string. Android caches them per channel id; this runs
     * on every start, so a language change is picked up the next time the app
     * launches — which is also when the system re-reads them.
     */
    private fun createNotificationChannels() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Safe migration: check if review channel was previously created with IMPORTANCE_LOW.
        // Android prevents increasing channel importance via createNotificationChannel if already registered.
        // We recreate it with IMPORTANCE_DEFAULT while preserving user-configured sound and vibration.
        val existingReview = nm.getNotificationChannel(CH_REVIEW)
        if (existingReview != null && existingReview.importance < NotificationManager.IMPORTANCE_DEFAULT) {
            val sound = existingReview.sound
            val audioAttrs = existingReview.audioAttributes
            val shouldVibrate = existingReview.shouldVibrate()
            val pattern = existingReview.vibrationPattern
            val lights = existingReview.shouldShowLights()

            nm.deleteNotificationChannel(CH_REVIEW)
            val updatedReview = NotificationChannel(
                CH_REVIEW,
                getString(R.string.channel_review_name),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = getString(R.string.channel_review_desc)
                if (sound != null && audioAttrs != null) {
                    setSound(sound, audioAttrs)
                }
                enableVibration(shouldVibrate)
                if (pattern != null) {
                    vibrationPattern = pattern
                }
                enableLights(lights)
            }
            nm.createNotificationChannel(updatedReview)
        }

        // Safe migration: Bank merged into Personal in UI redesign.
        // If the legacy "transactions" channel exists, clean it up so it does not
        // linger in system settings.
        val existingTransactions = nm.getNotificationChannel(CH_TRANSACTIONS)
        if (existingTransactions != null) {
            nm.deleteNotificationChannel(CH_TRANSACTIONS)
        }

        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CH_PERSONAL, getString(R.string.channel_personal_name), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = getString(R.string.channel_personal_desc)
                },
                NotificationChannel(CH_PROMOTIONS, getString(R.string.channel_promotions_name), NotificationManager.IMPORTANCE_LOW).apply {
                    description = getString(R.string.channel_promotions_desc)
                },
                NotificationChannel(CH_SPAM, getString(R.string.channel_spam_name), NotificationManager.IMPORTANCE_MIN).apply {
                    description = getString(R.string.channel_spam_desc)
                    enableLights(false)
                    enableVibration(false)
                    setShowBadge(false)
                },
                NotificationChannel(CH_REVIEW, getString(R.string.channel_review_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = getString(R.string.channel_review_desc)
                },
                NotificationChannel(CH_REMINDERS, getString(R.string.channel_reminders_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = getString(R.string.channel_reminders_desc)
                },
                // Phase 4 item 19 (Truecaller report rec A4): fraud warnings are
                // the ONE exception to "filtered folders stay silent" — ordinary
                // spam still never notifies; this channel is only for Dangerous
                // verdicts and is governed by the default-on warn setting.
                NotificationChannel(CH_FRAUD, getString(R.string.channel_fraud_name), NotificationManager.IMPORTANCE_HIGH).apply {
                    description = getString(R.string.channel_fraud_desc)
                },
                NotificationChannel(CH_UPDATES, "App Updates", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Notifications when a new version of Messages is available"
                },
                NotificationChannel(CH_DOWNLOAD, "Download Progress", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Shows download progress when updating the app"
                    setShowBadge(false)
                },
            )
        )
    }

    companion object {
        const val CH_PERSONAL = "personal"
        const val CH_TRANSACTIONS = "transactions"
        const val CH_PROMOTIONS = "promotions"
        const val CH_SPAM = "spam"
        const val CH_REVIEW = "review"
        const val CH_REMINDERS = "reminders"
        const val CH_FRAUD = "fraud_warnings"
        const val CH_UPDATES = "app_updates"
        const val CH_DOWNLOAD = "download_progress"
    }
}
