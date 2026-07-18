package com.personal.detectivedialer.telecom

import android.telecom.Call
import android.telecom.DisconnectCause
import android.util.Log
import com.personal.detectivedialer.data.local.CallLogEntry
import com.personal.detectivedialer.data.repository.CallRepository
import com.personal.detectivedialer.service.ContactsHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Writes a complete call history. Every call that rings through our
 * InCallService — incoming (answered or missed), outgoing, or rejected — is
 * recorded here when it ends, with its duration. Reconciliation with any
 * pre-ring screening row happens in [CallRepository.logCallOutcome], so a single
 * physical call is one history entry.
 *
 * Disposition is decided from what we actually observed (did the call ever go
 * ACTIVE? did the user decline it?) plus the telecom [DisconnectCause] as a
 * tiebreaker — more reliable than any single signal on its own.
 */
@Singleton
class CallLogWriter @Inject constructor(
    private val repository: CallRepository,
    private val contacts: ContactsHelper,
    private val callManager: CallManager,
    private val notifications: CallNotificationManager,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val tracked = LinkedHashMap<String, Track>()

    private class Track(
        val number: String,
        val isIncoming: Boolean,
        val startedAt: Long,
    ) {
        var reachedActive = false
        var connectedAt = 0L
        /**
         * Latest network-verified caller name (CNAP). Often absent at
         * onCallAdded and delivered a moment later via onDetailsChanged, so we
         * keep updating it right up to teardown rather than snapshotting once.
         */
        var cnap: String = ""
    }

    private val callbacks = HashMap<String, Call.Callback>()

    fun onCallAdded(call: Call) {
        val id = CallManager.idFor(call)
        if (tracked.containsKey(id)) return
        val details = call.details
        val number = details?.handle?.schemeSpecificPart.orEmpty()
        @Suppress("DEPRECATION") // Details.getState() needs API 31; minSdk is 29.
        val incoming = details?.callDirection == Call.Details.DIRECTION_INCOMING
        val track = Track(
            number = number,
            isIncoming = incoming,
            startedAt = System.currentTimeMillis(),
        )
        track.cnap = details?.callerDisplayName?.takeIf { it.isNotBlank() }.orEmpty()
        tracked[id] = track

        val callback = object : Call.Callback() {
            @Suppress("DEPRECATION")
            override fun onStateChanged(c: Call, state: Int) {
                if (state == Call.STATE_ACTIVE && !track.reachedActive) {
                    track.reachedActive = true
                    track.connectedAt = System.currentTimeMillis()
                }
            }

            // CNAP frequently lands after the call is already up — capture every
            // update so the name is current when we write history at teardown.
            override fun onDetailsChanged(c: Call, details: Call.Details) {
                details.callerDisplayName?.takeIf { it.isNotBlank() }?.let { track.cnap = it }
            }
        }
        callbacks[id] = callback
        call.registerCallback(callback)
    }

    fun onCallRemoved(call: Call) {
        val id = CallManager.idFor(call)
        val track = tracked.remove(id) ?: return
        callbacks.remove(id)?.let { runCatching { call.unregisterCallback(it) } }

        val causeCode = call.details?.disconnectCause?.code
        val userRejected = callManager.wasUserRejected(id)
        callManager.clearUserRejected(id)

        // Final read of the DISCONNECTED details in case a last CNAP update raced
        // the teardown and never reached our onDetailsChanged callback.
        call.details?.callerDisplayName?.takeIf { it.isNotBlank() }?.let { track.cnap = it }

        val type = disposition(track, causeCode, userRejected)
        val duration = if (track.reachedActive && track.connectedAt > 0) {
            ((System.currentTimeMillis() - track.connectedAt) / 1000).toInt().coerceAtLeast(0)
        } else {
            0
        }

        scope.launch {
            // Priority: saved contact → network CNAP → blank (UI shows "Unknown").
            // Contact wins over CNAP so a personally-saved name beats the carrier's.
            val name = contacts.displayNameFor(track.number)?.takeIf { it.isNotBlank() }
                ?: track.cnap
            runCatching {
                repository.logCallOutcome(
                    number = track.number,
                    callerName = name,
                    type = type,
                    timestamp = track.startedAt,
                    durationSeconds = duration,
                )
            }.onFailure { Log.e(TAG, "Failed to log call outcome", it) }

            if (type == CallLogEntry.TYPE_MISSED && track.number.isNotBlank()) {
                notifications.postMissed(track.number, name)
            }
        }
    }

    private fun disposition(track: Track, causeCode: Int?, userRejected: Boolean): String = when {
        !track.isIncoming -> CallLogEntry.TYPE_OUTGOING
        track.reachedActive -> CallLogEntry.TYPE_INCOMING
        userRejected || causeCode == DisconnectCause.REJECTED -> CallLogEntry.TYPE_REJECTED
        else -> CallLogEntry.TYPE_MISSED // MISSED / REMOTE / CANCELED — caller gave up
    }

    companion object {
        private const val TAG = "CallLogWriter"
    }
}
