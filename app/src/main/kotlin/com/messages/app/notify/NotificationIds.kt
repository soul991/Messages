package com.messages.app.notify

/**
 * V2-31: notification and `PendingIntent` identity for a 64-bit thread id.
 *
 * Everything here used to be `threadId.toInt()`, which keeps the low 32 bits
 * and throws the rest away. Two things collide as a result:
 *
 *  - **Notifications.** Provider thread ids are large on a device with a long
 *    history, and this app also mints negative synthetic ids. Two threads whose
 *    ids differ only above bit 32 — say `1` and `2^32 + 1` — truncate to the
 *    same int, so one conversation's notification silently replaces another's.
 *    The fraud-warning id (`FRAUD_ID_BASE - threadId`) truncates into the same
 *    flat space, and so could land on an unrelated thread's message
 *    notification, or on the fixed Review / locked-space ids.
 *  - **PendingIntents.** Identity is `(requestCode, Intent)` where the Intent
 *    is compared with `filterEquals` — which ignores extras. The action intents
 *    differed only by their extras and a truncated request code, so a collision
 *    handed one conversation an action carrying **another conversation's**
 *    `threadId`: "mark as read" on the wrong thread, or a reply sent to the
 *    wrong recipient.
 *
 * The fix is to stop compressing a 64-bit identity into 32 bits at all:
 *
 *  - Notifications are posted with a **tag** carrying the full id and a small
 *    constant id per kind. `(tag, id)` is the platform's identity pair, so
 *    distinct threads are distinct by construction, and thread notifications
 *    can no longer collide with the fixed-id ones (which carry no tag).
 *  - Action `PendingIntent`s carry a distinct `data` URI per thread and action,
 *    so `filterEquals` separates them regardless of what the request code does.
 *    The request code is still spread across the full 64 bits rather than
 *    truncated, but correctness no longer depends on it.
 *
 * Pure and free of Android types so the collision cases can be tested directly.
 */
internal object NotificationIds {

    /** Per-kind constants — unique only within a tag. */
    const val ID_MESSAGE = 1
    const val ID_FRAUD = 2
    const val ID_REMINDER = 3

    /** Scheme for the per-action data URIs that keep PendingIntents distinct. */
    const val SCHEME = "messages"

    /** The notification tag for a conversation — the full id, never truncated. */
    fun threadTag(threadId: Long): String = "thread:$threadId"

    /**
     * Snoozed reminders are per-*message*, not per-thread, so they get their own
     * tag space. Row ids are 64-bit for the same reason thread ids are.
     */
    fun reminderTag(messageId: Long): String = "reminder:$messageId"

    /**
     * A distinct `data` URI per (thread, action). This is what actually
     * separates two action PendingIntents, since `filterEquals` ignores extras.
     */
    fun actionUri(threadId: Long, action: String): String =
        "$SCHEME://thread/$threadId/$action"

    /**
     * A stable, non-negative request code that mixes the whole 64-bit id
     * instead of keeping its low half. Collisions are no longer a correctness
     * problem (the data URI decides identity), but spreading the bits keeps
     * `FLAG_UPDATE_CURRENT` churn down.
     */
    fun requestCode(threadId: Long, action: String): Int {
        var h = threadId * 31 + action.hashCode()
        h = h xor (h ushr 32)
        return (h.toInt() and 0x7fff_ffff)
    }

    /**
     * The ids this app posted before tags existed. Cancelling these too means an
     * upgrade clears notifications the previous version left on screen, instead
     * of stranding them forever.
     */
    fun legacyMessageId(threadId: Long): Int = threadId.toInt()

    fun legacyFraudId(threadId: Long): Int = (LEGACY_FRAUD_BASE - threadId).toInt()

    private const val LEGACY_FRAUD_BASE = -1_000_000L
}
