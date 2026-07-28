package com.messages.core.send

import com.messages.core.db.AttemptState
import com.messages.core.db.SmsAttemptEntity

/**
 * R-13: derives one message-level `sendStatus` from the per-(recipient, part)
 * attempt rows.
 *
 * The old design updated the message row directly from every SENT/DELIVERED
 * broadcast, so a group or multipart send could:
 *   - report SENT while other parts were still in flight,
 *   - report SENT even though a later part failed,
 *   - oscillate depending on which broadcast the system delivered first, and
 *   - downgrade DELIVERED back to SENT on a late SENT callback.
 *
 * This is a PURE function of the attempt set. Because each attempt's own state
 * is monotonic (PENDING is the only state a receiver may leave), the derived
 * result is independent of broadcast order — the property the review asked for,
 * and a stronger one than "monotonic".
 *
 * Deliberately no Android imports: exercised directly by SendAggregateTest.
 */
object SendAggregate {

    const val SENDING = "SENDING"
    const val SENT = "SENT"
    const val DELIVERED = "DELIVERED"
    const val FAILED = "FAILED"

    /** "<messageId>:<recipientIndex>:<partIndex>" — the attempt primary key. */
    fun attemptId(messageId: Long, recipientIndex: Int, partIndex: Int): String =
        "$messageId:$recipientIndex:$partIndex"

    /**
     * A stable, well-spread PendingIntent request code. The old code reused
     * `messageId.toInt()` for every recipient, so the platform collapsed all
     * recipients' PendingIntents into one. (The intents also carry a unique
     * data URI, because PendingIntent equality ignores extras.)
     */
    fun requestCode(messageId: Long, recipientIndex: Int, partIndex: Int): Int {
        var h = messageId.toInt() xor (messageId ushr 32).toInt()
        h = h * 31 + recipientIndex
        h = h * 31 + partIndex
        // Keep it non-negative: some OEM shells log request codes as unsigned.
        return h and 0x7fffffff
    }

    /**
     * The aggregate for [attempts], or null when the message has no attempt
     * rows at all (a legacy message sent before the upgrade, or an MMS — the
     * caller then leaves `sendStatus` alone).
     *
     * Precedence, highest first:
     *  - FAILED    — any dispatch failed; the user needs Resend.
     *  - SENDING   — nothing failed but some dispatch has not reported yet.
     *  - DELIVERED — every dispatch sent AND every REQUESTED delivery report in.
     *  - SENT      — every dispatch sent; delivery either not requested or not
     *                all reports are in. A delivery report that came back
     *                negative leaves the message SENT rather than lying with
     *                DELIVERED or alarming with FAILED — the send did succeed.
     */
    fun of(attempts: List<SmsAttemptEntity>): String? {
        if (attempts.isEmpty()) return null
        if (attempts.any { it.sentState == AttemptState.FAILED }) return FAILED
        if (attempts.any { it.sentState == AttemptState.PENDING }) return SENDING
        val requested = attempts.filter { it.wantDelivery }
        return if (requested.isNotEmpty() && requested.all { it.deliveryState == AttemptState.OK }) {
            DELIVERED
        } else {
            SENT
        }
    }

    /** The first failure's raw SmsManager.RESULT_*, for the user-facing reason. */
    fun failureCode(attempts: List<SmsAttemptEntity>): Int? =
        attempts.firstOrNull { it.sentState == AttemptState.FAILED }?.resultCode

    /**
     * Ordering that keeps the stored status from moving backwards. FAILED sits
     * at the top on purpose: a partially failed send must be able to overwrite
     * an earlier SENT/DELIVERED (one part landing does not make the message
     * sent), and nothing may overwrite FAILED except a fresh send, which
     * rebuilds the attempt set from scratch.
     */
    fun rank(status: String): Int = when (status) {
        "NONE", "SCHEDULED" -> 0
        SENDING -> 1
        SENT -> 2
        DELIVERED -> 3
        FAILED -> 4
        else -> 0
    }

    /** True when [next] may overwrite [current]. */
    fun supersedes(next: String, current: String): Boolean = rank(next) > rank(current)
}
