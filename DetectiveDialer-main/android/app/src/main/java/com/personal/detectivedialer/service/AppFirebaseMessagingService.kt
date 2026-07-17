package com.personal.detectivedialer.service

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.personal.detectivedialer.DetectiveDialerApp
import com.personal.detectivedialer.R
import com.personal.detectivedialer.data.local.CallLogEntry
import com.personal.detectivedialer.data.prefs.SettingsRepository
import com.personal.detectivedialer.data.repository.CallRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Receives call-summary pushes from the backend. The payload is strictly
 * data-only (the backend sends no `notification` block so delivery works when
 * the app is backgrounded):
 *   caller, category, summary, duration, hasRecording, callId
 * We cache a CallLogEntry locally and raise a notification that deep-links to
 * the Call Detail screen.
 */
@AndroidEntryPoint
class AppFirebaseMessagingService : FirebaseMessagingService() {

    @Inject lateinit var repository: CallRepository
    @Inject lateinit var settings: SettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        scope.launch {
            settings.setFcmToken(token)
            // Best-effort: push the rotated token to the backend so pushes keep working.
            if (!repository.registerDevice(token)) {
                Log.w(TAG, "Backend device registration failed; will retry on next app start")
            }
        }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val caller = data["caller"] ?: "Unknown"
        val callerName = data["callerName"].orEmpty()
        val category = data["category"] ?: "PERSONAL"
        val action = data["action"].orEmpty()
        val summary = data["summary"] ?: ""
        val duration = data["duration"]?.toIntOrNull() ?: 0
        val callId = data["callId"]?.takeIf { it.isNotBlank() }
            ?: "fcm-$caller-${System.currentTimeMillis()}"
        val hasRecording = data["hasRecording"]?.toBoolean() ?: false

        scope.launch {
            // Merge into any entry the on-device screening already wrote for this
            // call (same callId) instead of clobbering it.
            val existing = repository.getCall(callId)
            repository.upsertCall(
                CallLogEntry(
                    id = callId,
                    number = caller,
                    callerName = callerName.ifBlank { existing?.callerName ?: "" },
                    timestamp = existing?.timestamp ?: System.currentTimeMillis(),
                    category = category,
                    action = action.ifBlank { existing?.action ?: "" },
                    summary = summary.ifBlank { existing?.summary ?: "" },
                    transcript = existing?.transcript ?: "",
                    recordingUrl = if (hasRecording) "pending" else existing?.recordingUrl ?: "",
                    duration = if (duration > 0) duration else existing?.duration ?: 0,
                    confidence = existing?.confidence ?: 0f,
                ),
            )
            // Pull the full record (transcript + recording URL) in the background.
            runCatching { repository.refreshCall(callId) }
        }

        showNotification(callerName.ifBlank { caller }, category, summary, callId)
    }

    private fun showNotification(caller: String, category: String, summary: String, callId: String) {
        val urgent = category == "URGENT"
        val icon = categoryIcon(category)

        val deepLink = Uri.parse("detectivedialer://call/$callId")
        val intent = Intent(Intent.ACTION_VIEW, deepLink).apply {
            setPackage(packageName)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pending = PendingIntent.getActivity(
            this,
            callId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val channel = if (urgent) DetectiveDialerApp.CHANNEL_URGENT else DetectiveDialerApp.CHANNEL_SCREENED
        val notification = NotificationCompat.Builder(this, channel)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("$icon $category · $caller")
            .setContentText(summary.ifBlank { "New screened call" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(summary))
            .setPriority(if (urgent) NotificationCompat.PRIORITY_MAX else NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(if (urgent) NotificationCompat.CATEGORY_CALL else NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(callId.hashCode(), notification) }
    }

    private fun categoryIcon(category: String): String = when (category) {
        "SPAM" -> "🚫"
        "DELIVERY" -> "🚚"
        "VENDOR" -> "🔧"
        "PERSONAL" -> "👤"
        "URGENT" -> "⚠️"
        else -> "📞"
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AppFCMService"
    }
}
