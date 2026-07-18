package com.personal.detectivedialer.telecom

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.Person
import com.personal.detectivedialer.DetectiveDialerApp
import com.personal.detectivedialer.R
import com.personal.detectivedialer.ui.incall.InCallActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns every notification tied to a live or just-ended call:
 *  - a high-priority full-screen incoming-call notification with Answer/Decline
 *    actions, which also wakes the screen when the phone is idle/locked;
 *  - an ongoing "call in progress" notification so the user can return to (or
 *    hang up) a call after leaving the in-call screen;
 *  - a missed-call notification with a one-tap callback.
 *
 * The action buttons broadcast to [CallActionReceiver] rather than starting an
 * activity, so they work with the app UI backgrounded.
 */
@Singleton
class CallNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val nm get() = context.getSystemService(NotificationManager::class.java)

    /** Post/refresh the full-screen incoming-call notification. */
    fun postIncoming(callId: String, displayName: String, number: String) {
        val person = Person.Builder()
            .setName(displayName.ifBlank { number.ifBlank { "Unknown" } })
            .build()

        val answer = actionIntent(CallActionReceiver.ACTION_ANSWER, callId)
        val decline = actionIntent(CallActionReceiver.ACTION_DECLINE, callId)
        val fullScreen = inCallActivityIntent()

        val notification = NotificationCompat.Builder(context, DetectiveDialerApp.CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(person.name)
            .setContentText(if (number.isNotBlank() && number != person.name) number else "Incoming call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setOngoing(true)
            .setAutoCancel(false)
            .setFullScreenIntent(fullScreen, true)
            .setContentIntent(fullScreen)
            .setStyle(NotificationCompat.CallStyle.forIncomingCall(person, decline, answer))
            .build()

        runCatching { nm?.notify(ACTIVE_CALL_ID, notification) }
    }

    /** Post/refresh the ongoing-call notification (tap to return, action to end). */
    fun postOngoing(callId: String, displayName: String, number: String) {
        val person = Person.Builder()
            .setName(displayName.ifBlank { number.ifBlank { "Unknown" } })
            .build()
        // Must disconnect (not reject) — the call is already past RINGING, where
        // reject() is a no-op, so declining here would leave the call live.
        val hangUp = actionIntent(CallActionReceiver.ACTION_HANGUP, callId)

        val notification = NotificationCompat.Builder(context, DetectiveDialerApp.CHANNEL_INCOMING)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle(person.name)
            .setContentText("Ongoing call")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(inCallActivityIntent())
            .setStyle(NotificationCompat.CallStyle.forOngoingCall(person, hangUp))
            .build()

        runCatching { nm?.notify(ACTIVE_CALL_ID, notification) }
    }

    fun cancelActiveCall() {
        runCatching { nm?.cancel(ACTIVE_CALL_ID) }
    }

    /** Missed-call notification with a callback that opens the dialpad pre-filled. */
    fun postMissed(number: String, displayName: String) {
        val name = displayName.ifBlank { number.ifBlank { "Unknown" } }
        val callBack = PendingIntent.getActivity(
            context,
            ("missed-$number").hashCode(),
            Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null))
                .setPackage(context.packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, DetectiveDialerApp.CHANNEL_MISSED)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("Missed call")
            .setContentText(name)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(callBack)
            .addAction(R.drawable.ic_shield, "Call back", callBack)
            .build()

        runCatching { nm?.notify(("missed-$number").hashCode(), notification) }
    }

    private fun inCallActivityIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, InCallActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun actionIntent(action: String, callId: String): PendingIntent {
        val intent = Intent(context, CallActionReceiver::class.java)
            .setAction(action)
            .putExtra(CallActionReceiver.EXTRA_CALL_ID, callId)
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    companion object {
        /** Only one live call notification at a time — a stable id we reuse. */
        private const val ACTIVE_CALL_ID = 4201
    }
}
