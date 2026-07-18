package com.personal.detectivedialer.telecom

import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.VideoProfile
import android.util.Log
import com.personal.detectivedialer.service.ContactsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Single source of truth for the live telecom call(s). Bridges the
 * [DialerInCallService] (which owns the real [Call] objects) and the Compose
 * in-call UI (which only sees immutable [CallUi] snapshots).
 *
 * Held as a @Singleton so the service and the InCallActivity observe the exact
 * same state. The service registers/unregisters calls; the UI reads [calls] and
 * invokes the action methods, which forward to the underlying telecom Call.
 */
@Singleton
class CallManager @Inject constructor(
    private val contacts: ContactsHelper,
) {

    private val _calls = MutableStateFlow<List<CallUi>>(emptyList())
    /** All current calls, newest last. Empty when nothing is in progress. */
    val calls: StateFlow<List<CallUi>> = _calls.asStateFlow()

    // Off-main-thread contact lookups so publish() never touches the ContactsContract
    // provider on the caller's thread (telecom callbacks run on the main thread).
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Resolved saved-contact names keyed by normalized number. Reuses the Session 1
     * resolver ([ContactsHelper.displayNameFor]) so the live in-call screen honours
     * the same saved-contact → CNAP → Unknown priority as the call log — a contact
     * match always wins over the carrier's CNAP. A key present with a null value
     * means "looked up, no contact" so we don't re-query every publish().
     */
    private val contactNames = HashMap<String, String?>()

    private val _audio = MutableStateFlow(AudioUi())
    val audio: StateFlow<AudioUi> = _audio.asStateFlow()

    /** The bound InCallService, set while it is alive. Needed for audio routing. */
    private var inCallService: InCallService? = null

    // Live telecom calls keyed by our stable id, plus the callbacks we attached
    // so we can detach them on removal.
    private val tracked = LinkedHashMap<String, Tracked>()

    private class Tracked(val call: Call, val callback: Call.Callback)

    /** Verdicts stashed by the screening service, keyed by normalized number (Phase D). */
    private val pendingVerdicts = HashMap<String, VerdictInfo>()

    data class VerdictInfo(
        val decision: String,
        val action: String,
        val reason: String,
        val confidence: Float,
    )

    /**
     * Ids the user explicitly declined (from the in-call UI or the notification
     * action). Lets the call-history writer log a REJECTED — rather than a
     * MISSED — when the call subsequently disconnects.
     */
    private val userRejectedIds = HashSet<String>()

    @Synchronized
    fun wasUserRejected(id: String): Boolean = userRejectedIds.contains(id)

    @Synchronized
    fun clearUserRejected(id: String) { userRejectedIds.remove(id) }

    @Synchronized
    fun attachService(service: InCallService) {
        inCallService = service
    }

    @Synchronized
    fun detachService() {
        inCallService = null
        _audio.value = AudioUi()
    }

    /** Stash a screening verdict so the in-call UI can show it when the call lands. */
    @Synchronized
    fun rememberVerdict(number: String, decision: String, action: String, reason: String, confidence: Float) {
        val key = normalize(number)
        if (key.isNotBlank()) pendingVerdicts[key] = VerdictInfo(decision, action, reason, confidence)
    }

    @Synchronized
    fun onCallAdded(call: Call) {
        val id = idFor(call)
        if (tracked.containsKey(id)) return

        val callback = object : Call.Callback() {
            override fun onStateChanged(c: Call, state: Int) { publish() }
            override fun onDetailsChanged(c: Call, details: Call.Details) {
                resolveContactName(c)
                publish()
            }
        }
        call.registerCallback(callback)
        tracked[id] = Tracked(call, callback)
        Log.d(TAG, "Call added; tracking ${tracked.size}")
        resolveContactName(call)
        publish()
    }

    /**
     * Look up the saved-contact name for a call's number off the main thread and,
     * if found, re-publish so the in-call screen swaps CNAP for the contact name.
     * Cached in [contactNames] (including negative results) so a call's repeated
     * onDetailsChanged updates don't re-hit the contacts provider. Runs on RINGING
     * and on every onDetailsChanged, matching the acceptance criteria.
     */
    @Suppress("DEPRECATION") // handle read; see toUi() for the state-getter note.
    private fun resolveContactName(call: Call) {
        val handle = call.details?.handle?.schemeSpecificPart.orEmpty()
        val key = normalize(handle)
        synchronized(this) {
            if (key.isBlank() || contactNames.containsKey(key)) return
            // Reserve the key so concurrent callbacks don't launch duplicate lookups.
            contactNames[key] = null
        }
        scope.launch {
            val resolved = contacts.displayNameFor(handle)?.takeIf { it.isNotBlank() }
            val changed = synchronized(this@CallManager) {
                if (contactNames[key] != resolved) {
                    contactNames[key] = resolved
                    true
                } else {
                    false
                }
            }
            // Only re-publish when we actually learned a name (avoids a redundant
            // emission for the common no-contact case).
            if (changed && resolved != null) publish()
        }
    }

    @Synchronized
    fun onCallRemoved(call: Call) {
        val id = idFor(call)
        tracked.remove(id)?.let { it.call.unregisterCallback(it.callback) }
        // Drop cached lookups once nothing is live so a number saved as a contact
        // mid-session is re-resolved fresh on its next call.
        if (tracked.isEmpty()) contactNames.clear()
        Log.d(TAG, "Call removed; tracking ${tracked.size}")
        publish()
    }

    @Synchronized
    fun onAudioStateChanged(state: CallAudioState) {
        val btAvailable = state.supportedRouteMask and CallAudioState.ROUTE_BLUETOOTH != 0
        _audio.value = AudioUi(
            muted = state.isMuted,
            route = routeOf(state.route),
            speakerOn = state.route == CallAudioState.ROUTE_SPEAKER,
            bluetoothAvailable = btAvailable,
            wiredHeadsetAvailable = state.supportedRouteMask and CallAudioState.ROUTE_WIRED_HEADSET != 0,
            bluetoothName = if (btAvailable) bluetoothName() else null,
        )
    }

    /**
     * Cycle the output route the way stock dialers do: Earpiece → Speaker →
     * Bluetooth (only when a BT audio device is present) → back to Earpiece.
     */
    fun cycleAudioRoute() {
        val a = _audio.value
        val next = when (a.route) {
            AudioRoute.EARPIECE, AudioRoute.WIRED_HEADSET ->
                AudioRoute.SPEAKER
            AudioRoute.SPEAKER ->
                if (a.bluetoothAvailable) AudioRoute.BLUETOOTH else AudioRoute.EARPIECE
            AudioRoute.BLUETOOTH ->
                AudioRoute.EARPIECE
        }
        setAudioRoute(next)
    }

    /** Route to Bluetooth when a BT device is present and we aren't there yet. */
    fun preferBluetoothIfAvailable() {
        val a = _audio.value
        if (a.bluetoothAvailable && a.route != AudioRoute.BLUETOOTH) {
            setAudioRoute(AudioRoute.BLUETOOTH)
        }
    }

    /**
     * Product name of a connected BT audio output, read from AudioManager so we
     * don't need the BLUETOOTH_CONNECT runtime permission (which reading
     * BluetoothDevice.name would require on API 31+). Null when unknown.
     */
    private fun bluetoothName(): String? {
        val svc = inCallService ?: return null
        return runCatching {
            val am = svc.getSystemService(android.media.AudioManager::class.java) ?: return null
            am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull {
                    it.type == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                        it.type == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET
                }
                ?.productName?.toString()?.trim()?.takeIf { it.isNotBlank() }
        }.getOrNull()
    }

    // ── Actions invoked from the UI ─────────────────────────────────────────
    fun answer(id: String) {
        callOf(id)?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    /** Reject an incoming call. The network then typically routes it to voicemail. */
    @Synchronized
    fun reject(id: String) {
        userRejectedIds.add(id)
        callOf(id)?.reject(false, null)
    }

    /**
     * Reject with a text response ("reply with message"), then decline the call.
     * Sending the SMS itself is done by the caller; here we just decline.
     */
    @Synchronized
    fun rejectWithMessage(id: String) {
        userRejectedIds.add(id)
        callOf(id)?.reject(false, null)
    }

    /**
     * Deflect an incoming call to voicemail. There is no dedicated public
     * InCallService "send to voicemail" API, so — as stock Android does — we
     * reject the call and let the carrier route it to voicemail.
     */
    @Synchronized
    fun sendToVoicemail(id: String) {
        userRejectedIds.add(id)
        callOf(id)?.reject(false, null)
    }

    /** Hang up an active/dialing call. */
    fun hangup(id: String) {
        callOf(id)?.disconnect()
    }

    fun hold(id: String) {
        callOf(id)?.hold()
    }

    fun unhold(id: String) {
        callOf(id)?.unhold()
    }

    fun playDtmf(id: String, digit: Char) {
        callOf(id)?.let {
            it.playDtmfTone(digit)
            it.stopDtmfTone()
        }
    }

    fun setMuted(muted: Boolean) {
        inCallService?.setMuted(muted)
    }

    // setAudioRoute(int) is deprecated for the API-34 CallEndpoint API; we still
    // support API 29, so the classic route call is the correct cross-version path.
    @Suppress("DEPRECATION")
    fun setSpeaker(on: Boolean) {
        inCallService?.setAudioRoute(
            if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE,
        )
    }

    @Suppress("DEPRECATION")
    fun setAudioRoute(route: AudioRoute) {
        val mask = when (route) {
            AudioRoute.EARPIECE -> CallAudioState.ROUTE_EARPIECE
            AudioRoute.SPEAKER -> CallAudioState.ROUTE_SPEAKER
            AudioRoute.BLUETOOTH -> CallAudioState.ROUTE_BLUETOOTH
            AudioRoute.WIRED_HEADSET -> CallAudioState.ROUTE_WIRED_HEADSET
        }
        inCallService?.setAudioRoute(mask)
    }

    // ── Internals ───────────────────────────────────────────────────────────
    @Synchronized
    private fun callOf(id: String): Call? = tracked[id]?.call

    // Synchronized because it snapshots [tracked], which is mutated under the same
    // monitor — and publish() is now also called from the background contact-lookup
    // coroutine, not just the main-thread telecom callbacks.
    @Synchronized
    private fun publish() {
        _calls.value = tracked.values.map { it.call.toUi() }
    }

    // Call.getState() is deprecated for Call.Details.getState(), but the latter
    // needs API 31 and our minSdk is 29 — so the Call-level getter is correct here.
    @Suppress("DEPRECATION")
    private fun Call.toUi(): CallUi {
        val d = details
        val handle = d?.handle?.schemeSpecificPart.orEmpty()
        val number = normalize(handle)
        val cnap = d?.callerDisplayName?.takeIf { it.isNotBlank() }
        // Saved contact → CNAP → (UI falls back to number/"Unknown"). A saved
        // contact always wins over the carrier's CNAP; CNAP shows only when the
        // number matches no contact. Same priority as the call-log resolver.
        val contactName = synchronized(this@CallManager) { contactNames[number] }
        val displayName = contactName ?: cnap
        val verdict = pendingVerdicts[number]
        val connectedAt = if (state == Call.STATE_ACTIVE && d != null && d.connectTimeMillis > 0) {
            d.connectTimeMillis
        } else {
            0L
        }
        return CallUi(
            id = idFor(this),
            number = handle,
            displayName = displayName,
            state = CallState.from(state),
            isIncoming = d?.callDirection == Call.Details.DIRECTION_INCOMING,
            connectedAtMs = connectedAt,
            verdict = verdict?.decision,
            verdictAction = verdict?.action,
            verdictReason = verdict?.reason,
            verdictConfidence = verdict?.confidence ?: 0f,
        )
    }

    private fun routeOf(route: Int): AudioRoute = when (route) {
        CallAudioState.ROUTE_SPEAKER -> AudioRoute.SPEAKER
        CallAudioState.ROUTE_BLUETOOTH -> AudioRoute.BLUETOOTH
        CallAudioState.ROUTE_WIRED_HEADSET -> AudioRoute.WIRED_HEADSET
        else -> AudioRoute.EARPIECE
    }

    companion object {
        private const val TAG = "CallManager"

        /** A stable id for a telecom Call — its identity hash is stable per instance. */
        fun idFor(call: Call): String = System.identityHashCode(call).toString()

        /** Digits + a single leading '+', matching CallRepository.normalize. */
        fun normalize(raw: String): String =
            raw.replace(Regex("[^\\d+]"), "").replace(Regex("(?!^)\\+"), "")
    }
}
