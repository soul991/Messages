package com.personal.detectivedialer.telecom

import android.content.Context
import android.os.PowerManager
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Screen-off-at-ear during an active call, the way every stock dialer behaves.
 *
 * Wraps a [PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK]: while held, the OS turns
 * the display off whenever the proximity sensor is covered (phone at the ear) and
 * back on when it clears. This is the ONLY wake lock that darkens the screen from
 * proximity — a plain FLAG_KEEP_SCREEN_ON (which [ui.incall.InCallActivity] sets)
 * keeps the panel lit and would let a cheek tap a control mid-call.
 *
 * Acquire/release is driven entirely by the [DialerInCallService] off real call
 * state and audio-route changes — never polled. See that service for the policy:
 * acquire on ACTIVE + earpiece, release on end or when audio leaves the earpiece.
 *
 * @Singleton so a single lock is shared across the call; every method is safe to
 * call redundantly and on a device whose sensor doesn't support the level.
 */
@Singleton
class ProximityScreenLock @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val powerManager: PowerManager? =
        context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    /**
     * Whether this device can turn the screen off from the proximity sensor.
     * False on hardware without the sensor (or an OS that rejects the level) —
     * callers then simply skip the feature, no crash.
     */
    private val supported: Boolean =
        powerManager?.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) == true

    private var wakeLock: PowerManager.WakeLock? = null

    /** True while the proximity lock is held (screen may be off at the ear). */
    val isHeld: Boolean get() = wakeLock?.isHeld == true

    /**
     * Start screen-off-at-ear. No-op when the level is unsupported or the lock is
     * already held, so it's safe to call on every route change that lands on the
     * earpiece. The lock itself never times out — we hold it for the call and
     * release explicitly.
     */
    @Synchronized
    fun acquire() {
        if (!supported) return
        val pm = powerManager ?: return
        if (wakeLock?.isHeld == true) return
        val lock = wakeLock ?: runCatching {
            pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, TAG)
        }.getOrNull()?.also { it.setReferenceCounted(false); wakeLock = it } ?: return
        runCatching { lock.acquire() }
            .onFailure { Log.w(TAG, "Failed to acquire proximity lock", it) }
    }

    /** Release the proximity lock if held (screen returns to normal). Idempotent. */
    @Synchronized
    fun release() {
        val lock = wakeLock ?: return
        if (lock.isHeld) {
            runCatching { lock.release() }
                .onFailure { Log.w(TAG, "Failed to release proximity lock", it) }
        }
    }

    private companion object {
        private const val TAG = "ProximityScreenLock"
    }
}
