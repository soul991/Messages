package com.messages.core.secret

/**
 * Escalating rate limit for secret-space credential attempts (pure JVM).
 *
 * The first [FREE_ATTEMPTS] consecutive failures are free (fat fingers).
 * From the 5th failure on, every further attempt must wait out a cooldown
 * that escalates with the consecutive-failure count. A successful entry
 * resets the counter. The cooldown is enforced from the time of the LAST
 * failure; waiting does not decay the counter (only success does).
 */
object SecretCooldown {

    const val FREE_ATTEMPTS = 5

    /** Cooldown owed AFTER [failCount] consecutive failures. */
    fun cooldownMs(failCount: Int): Long = when {
        failCount < FREE_ATTEMPTS -> 0L
        failCount == 5 -> 30_000L
        failCount == 6 -> 60_000L
        failCount == 7 -> 5 * 60_000L
        failCount == 8 -> 15 * 60_000L
        else -> 60 * 60_000L
    }

    /**
     * Milliseconds until the next attempt is allowed; 0 = allowed now.
     * [lastFailAt]/[now] share any monotonic-enough clock (wall clock is fine
     * — turning the clock forward only shortens the wait, and the counter
     * itself never resets without a successful entry).
     */
    fun remainingMs(failCount: Int, lastFailAt: Long, now: Long): Long {
        val cooldown = cooldownMs(failCount)
        if (cooldown == 0L) return 0L
        return (lastFailAt + cooldown - now).coerceAtLeast(0L)
    }
}
