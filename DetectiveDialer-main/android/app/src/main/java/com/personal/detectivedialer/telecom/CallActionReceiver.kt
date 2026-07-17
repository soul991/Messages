package com.personal.detectivedialer.telecom

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.personal.detectivedialer.ui.incall.InCallActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Handles the Answer / Decline actions from the full-screen incoming-call
 * notification. These arrive as broadcasts so they work even when the app UI is
 * not in the foreground (e.g. the phone is locked and the user acts straight
 * from the notification). Both forward to the same singleton [CallManager] the
 * [DialerInCallService] feeds, so the live telecom call is answered/rejected.
 */
@AndroidEntryPoint
class CallActionReceiver : BroadcastReceiver() {

    @Inject lateinit var callManager: CallManager
    @Inject lateinit var notifications: CallNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_CALL_ID).orEmpty()
        when (intent.action) {
            ACTION_ANSWER -> {
                callManager.answer(id)
                // Bring up the in-call surface so the user sees the live call
                // (over the lock screen when the phone was locked). The call
                // going ACTIVE swaps the same notification to the ongoing style,
                // so we deliberately don't cancel it here.
                runCatching {
                    context.startActivity(
                        Intent(context, InCallActivity::class.java)
                            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                    )
                }.onFailure { Log.w(TAG, "Failed to launch in-call UI on answer", it) }
            }
            ACTION_DECLINE -> {
                callManager.reject(id)
                notifications.cancelActiveCall()
            }
            // Ending a live (active/dialing/holding) call: reject() is only valid for
            // a RINGING call and is a silent no-op otherwise, so we must disconnect().
            // The disconnect drives the call to DISCONNECTED, which tears the
            // notification down via the service's state handling — no manual cancel.
            ACTION_HANGUP -> callManager.hangup(id)
            else -> Unit
        }
    }

    companion object {
        private const val TAG = "CallActionReceiver"
        const val ACTION_ANSWER = "com.personal.detectivedialer.ANSWER_CALL"
        const val ACTION_DECLINE = "com.personal.detectivedialer.DECLINE_CALL"
        const val ACTION_HANGUP = "com.personal.detectivedialer.HANGUP_CALL"
        const val EXTRA_CALL_ID = "call_id"
    }
}
