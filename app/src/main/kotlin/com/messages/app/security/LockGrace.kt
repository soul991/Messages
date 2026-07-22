package com.messages.app.security

/**
 * Pure decision logic for the app-lock "Lock after" grace period (§8.2).
 * Kept free of Android deps so the relock policy is JVM-testable.
 *
 * Semantics: "Immediately" (grace 0) re-locks on every background trip —
 * the pre-grace behavior. A positive grace keeps the session unlocked
 * across short round trips (camera capture, photo picker, share sheet)
 * and re-locks only once the app has been backgrounded for that long.
 */
object LockGrace {

    const val IMMEDIATELY = 0L
    const val ONE_MINUTE = 60_000L
    const val FIVE_MINUTES = 300_000L

    /** How long an app-initiated external round trip (camera, pickers, system
     *  dialogs) suppresses re-locking. Long enough for a real photo session,
     *  short enough that an abandoned trip still locks. */
    const val EXTERNAL_RESULT_TIMEOUT_MS = 300_000L

    /**
     * True while an app-initiated activity-for-result launch should suppress
     * re-locking: the user is mid-flow (camera capture, photo/document picker,
     * permission or role dialog), not walking away from the app. This
     * overrides "Lock after: Immediately" — flow continuation is not an
     * unlock bypass. [armedAtMs] <= 0 means no launch in flight.
     */
    fun externalTripActive(
        armedAtMs: Long,
        nowMs: Long,
        timeoutMs: Long = EXTERNAL_RESULT_TIMEOUT_MS,
    ): Boolean = armedAtMs > 0L && nowMs - armedAtMs < timeoutMs

    /** Ordered options for the Settings picker: value → label. */
    val options: List<Pair<Long, String>> = listOf(
        IMMEDIATELY to "Immediately",
        ONE_MINUTE to "After 1 minute",
        FIVE_MINUTES to "After 5 minutes",
    )

    fun label(graceMs: Long): String =
        options.firstOrNull { it.first == graceMs }?.second ?: "Immediately"

    /**
     * Should an unlocked session re-lock when the app returns to the
     * foreground? [backgroundedAtMs]/[nowMs] are elapsedRealtime-style
     * monotonic stamps; [backgroundedAtMs] <= 0 means "no recorded
     * background trip" and fails safe to re-locking.
     */
    fun shouldRelock(graceMs: Long, backgroundedAtMs: Long, nowMs: Long): Boolean {
        if (graceMs <= 0L) return true
        if (backgroundedAtMs <= 0L) return true
        return nowMs - backgroundedAtMs >= graceMs
    }
}
