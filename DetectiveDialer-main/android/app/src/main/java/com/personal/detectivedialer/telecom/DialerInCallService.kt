package com.personal.detectivedialer.telecom

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import com.personal.detectivedialer.ui.incall.InCallActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * The default-dialer's window into the Telecom framework. Bound by the OS when
 * this app holds ROLE_DIALER and a call exists. It owns no UI itself — it feeds
 * every call into the shared [CallManager], drives the call notifications
 * ([CallNotificationManager]), records history ([CallLogWriter]), and launches
 * [InCallActivity] to render the in-call experience.
 *
 * Per the ROLE_DIALER contract this must never return a null binding and must
 * handle *all* calls without assuming they're SIM telephony.
 */
@AndroidEntryPoint
class DialerInCallService : InCallService() {

    @Inject lateinit var callManager: CallManager
    @Inject lateinit var notifications: CallNotificationManager
    @Inject lateinit var logWriter: CallLogWriter
    @Inject lateinit var proximity: ProximityScreenLock

    private val callbacks = HashMap<String, Call.Callback>()
    private val autoRoutedBt = HashSet<String>()

    // Latest audio route, tracked from onCallAudioStateChanged so proximity can be
    // re-evaluated the moment the user toggles speaker/earpiece — never polled.
    @Suppress("DEPRECATION")
    private var audioRoute: Int = CallAudioState.ROUTE_EARPIECE

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        callManager.attachService(this)
        callManager.onCallAdded(call)
        logWriter.onCallAdded(call)

        val id = CallManager.idFor(call)
        val callback = object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) = updateForState(c)
            override fun onDetailsChanged(c: Call, details: Call.Details) = updateForState(c)
        }
        callbacks[id] = callback
        call.registerCallback(callback)

        updateForState(call)

        // For incoming calls the full-screen-intent notification is the reliable
        // way to surface the UI over a locked/idle screen; still try a direct
        // launch for the already-unlocked / in-app case.
        launchInCallUi()
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        val id = CallManager.idFor(call)
        callbacks.remove(id)?.let { runCatching { call.unregisterCallback(it) } }
        autoRoutedBt.remove(id)

        logWriter.onCallRemoved(call)
        callManager.onCallRemoved(call)

        if (calls.isEmpty()) {
            notifications.cancelActiveCall()
            proximity.release()
        } else {
            // A call ended but another remains (e.g. hung up the active one while a
            // second was held) — re-evaluate proximity for what's left.
            updateProximity()
        }
    }

    // onCallAudioStateChanged is deprecated for the API-34 CallEndpoint API, but
    // that needs API 34 and our minSdk is 29 — CallAudioState remains correct.
    @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        super.onCallAudioStateChanged(audioState)
        callManager.onAudioStateChanged(audioState)
        // React to route switches in real time (Session 3 Stage C): a switch to
        // speaker/BT releases the proximity lock, a switch back to earpiece
        // re-acquires it — no polling.
        audioRoute = audioState.route
        updateProximity()
    }

    override fun onBringToForeground(showDialpad: Boolean) {
        super.onBringToForeground(showDialpad)
        launchInCallUi()
    }

    override fun onDestroy() {
        notifications.cancelActiveCall()
        proximity.release()
        callManager.detachService()
        super.onDestroy()
    }

    /**
     * React to a call's current state: drive the notification, auto-route BT, and
     * the proximity screen-off lock.
     *
     * Notification lifecycle (PART 1): the Answer/Decline CallStyle notification is
     * only for a RINGING incoming call. The instant the call leaves RINGING we swap
     * it for a minimal ongoing notification (tap-to-return + hang-up, no
     * Answer/Decline) while the call is live, and cancel it outright once the call
     * is disconnecting/ended — so the Answer/Decline buttons vanish the moment the
     * call is answered or rejected rather than lingering until teardown. (We keep an
     * ongoing notification on ACTIVE because the app can be backgrounded mid-call;
     * it carries no Answer/Decline actions.)
     */
    @Suppress("DEPRECATION") // Call.getState() — Details.getState() needs API 31; minSdk is 29.
    private fun updateForState(call: Call) {
        val details = call.details
        val number = details?.handle?.schemeSpecificPart.orEmpty()
        val name = details?.callerDisplayName?.takeIf { it.isNotBlank() } ?: number
        val id = CallManager.idFor(call)
        val incoming = details?.callDirection == Call.Details.DIRECTION_INCOMING

        when (call.state) {
            Call.STATE_RINGING -> if (incoming) notifications.postIncoming(id, name, number)
            Call.STATE_ACTIVE -> {
                notifications.postOngoing(id, name, number)
                if (autoRoutedBt.add(id)) callManager.preferBluetoothIfAvailable()
            }
            Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_HOLDING ->
                notifications.postOngoing(id, name, number)
            // Disconnecting/disconnected: the call is over. Cancel immediately so the
            // Answer/Decline (or ongoing) notification doesn't outlive the call. The
            // final teardown/cleanup still runs in onCallRemoved.
            Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED ->
                if (calls.size <= 1) notifications.cancelActiveCall()
            else -> Unit
        }

        updateProximity()
    }

    /**
     * Acquire/release the proximity screen-off lock off the live call + audio state
     * (PART 3). Screen-off-at-ear is wanted only for a genuinely in-progress call on
     * the earpiece: hold the lock when some call is ACTIVE and audio is routed to the
     * earpiece; release it otherwise — during ringing/dialing (slide-to-answer needs
     * the screen), once the call ends, or the moment speaker/BT/headset engages (the
     * user has likely put the phone down). Re-acquired automatically if audio returns
     * to the earpiece mid-call, since this runs on every route change too.
     */
    @Suppress("DEPRECATION")
    private fun updateProximity() {
        val hasActive = calls.any { it.state == Call.STATE_ACTIVE }
        val onEarpiece = audioRoute == CallAudioState.ROUTE_EARPIECE
        if (hasActive && onEarpiece) proximity.acquire() else proximity.release()
    }

    private fun launchInCallUi() {
        runCatching {
            startActivity(
                Intent(this, InCallActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }.onFailure { Log.e(TAG, "Failed to launch in-call UI", it) }
    }

    companion object {
        private const val TAG = "DialerInCallService"
    }
}
