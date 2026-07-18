package com.personal.detectivedialer.telecom

import android.telecom.Call

/**
 * Immutable UI snapshot of a single telecom call. The InCallService keeps the
 * live [android.telecom.Call] objects; the UI only ever sees these snapshots so
 * Compose can diff them and we never touch telecom internals from the UI layer.
 */
data class CallUi(
    /** Stable id for the lifetime of the call (see CallManager). */
    val id: String,
    val number: String,
    /** Network-verified CNAP / contact display name, when the OS has one yet. */
    val displayName: String?,
    val state: CallState,
    val isIncoming: Boolean,
    /** Wall-clock ms when the call went ACTIVE, or 0 if it hasn't yet. */
    val connectedAtMs: Long,
    /** Screening verdict carried over from the pre-ring CallScreeningService (Phase D). */
    val verdict: String? = null,
    /** Policy action (RING | VOICEMAIL | REJECT) from screening; null when unscreened. */
    val verdictAction: String? = null,
    val verdictReason: String? = null,
    val verdictConfidence: Float = 0f,
) {
    val isRinging: Boolean get() = state == CallState.RINGING
    val isOnHold: Boolean get() = state == CallState.HOLDING
    val isActive: Boolean get() = state == CallState.ACTIVE
    /** Name to show, falling back to the raw number. */
    val label: String get() = displayName?.takeIf { it.isNotBlank() } ?: number.ifBlank { "Unknown" }
}

/** Simplified, UI-facing projection of the many android.telecom.Call states. */
enum class CallState {
    NEW,
    DIALING,        // outgoing, not yet connected
    RINGING,        // incoming, awaiting answer/reject
    ACTIVE,         // connected, talking
    HOLDING,        // connected but on hold
    DISCONNECTING,
    DISCONNECTED,
    CONNECTING,
    OTHER;

    companion object {
        fun from(telecomState: Int): CallState = when (telecomState) {
            Call.STATE_NEW -> NEW
            // CONNECTING and DIALING are deliberately kept distinct so an
            // outgoing call can show "Calling…" (we're setting the call up) then
            // "Ringing…" (the remote party's phone is now ringing). Collapsing
            // them — as we used to — lost that transition.
            Call.STATE_CONNECTING -> CONNECTING
            Call.STATE_DIALING, Call.STATE_SELECT_PHONE_ACCOUNT -> DIALING
            Call.STATE_RINGING -> RINGING
            Call.STATE_ACTIVE -> ACTIVE
            Call.STATE_HOLDING -> HOLDING
            Call.STATE_DISCONNECTING -> DISCONNECTING
            Call.STATE_DISCONNECTED -> DISCONNECTED
            else -> OTHER
        }
    }
}

/** Where the call audio is currently routed. Mirrors CallAudioState route bits. */
enum class AudioRoute { EARPIECE, SPEAKER, BLUETOOTH, WIRED_HEADSET }

/** Snapshot of the current call audio state (shared for the whole InCallService). */
data class AudioUi(
    val muted: Boolean = false,
    val route: AudioRoute = AudioRoute.EARPIECE,
    val speakerOn: Boolean = false,
    val bluetoothAvailable: Boolean = false,
    val wiredHeadsetAvailable: Boolean = false,
    /** Product name of the active/available Bluetooth audio device, when known. */
    val bluetoothName: String? = null,
) {
    /** Short human label for the current output route. */
    val routeLabel: String
        get() = when (route) {
            AudioRoute.EARPIECE -> "Earpiece"
            AudioRoute.SPEAKER -> "Speaker"
            AudioRoute.BLUETOOTH -> bluetoothName ?: "Bluetooth"
            AudioRoute.WIRED_HEADSET -> "Headset"
        }
}
