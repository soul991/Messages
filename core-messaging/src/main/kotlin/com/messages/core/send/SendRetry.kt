package com.messages.core.send

/**
 * V2-48: which send failures may be retried automatically, and how long to wait
 * before each attempt.
 *
 * ## The rule this is built around
 *
 * An SMS costs money and, once it reaches the network, cannot be recalled. So
 * the question is not "might this work if we try again" — most failures might.
 * It is "if we are wrong about the failure, what did we just do to the user".
 * Retrying a transient radio error costs a few seconds. Retrying a send that
 * actually left the device costs a duplicate message and a second charge, and
 * retrying a premium short code costs a subscription the user did not agree to
 * twice.
 *
 * The policy therefore is an **allowlist**, not a blocklist: a code is
 * retryable only if it is named here, and anything unrecognised — including
 * `RESULT_ERROR_GENERIC_FAILURE`, which is where carriers put "no balance" —
 * stays in the outbox for the user to send. The default is the safe direction,
 * and new platform codes land on the safe side by construction rather than by
 * someone remembering to add them.
 *
 * ## Why not just retry everything a few times
 *
 * `RESULT_ERROR_GENERIC_FAILURE` is the interesting case, because it is the
 * most common failure and it is genuinely ambiguous — no coverage, no balance,
 * carrier rejection, and "the message went out but the acknowledgement did
 * not" all arrive as code 1. Three automatic retries against a low balance is
 * three chances to spend money the user does not have; against a lost
 * acknowledgement it is three duplicate messages the recipient sees. Neither is
 * something to do behind someone's back, so code 1 gets a **Resend button**
 * instead — the same outcome, chosen by the person who pays for it.
 *
 * ## Backoff
 *
 * Exponential with a fixed base, because the failures on the allowlist are
 * exactly the ones that resolve on their own given a little time: the radio
 * comes back, the network releases the rate limit, the call ends. There is no
 * jitter: unlike a server fleet there is one device sending to one carrier, so
 * there is no thundering herd to spread out, and a predictable "next try in
 * about a minute" is something the outbox can honestly display.
 *
 * Pure JVM by design — no Android imports — so the policy is decided by tests
 * rather than by a device.
 */
object SendRetry {

    /**
     * How many automatic attempts follow the first failure. Three is the point
     * where the backoff has covered roughly a quarter of an hour; a failure
     * that survives that is not transient, and continuing to try it silently
     * drains battery for a message the user should be told about.
     */
    const val MAX_AUTO_RETRIES = 3

    /** First backoff step. Doubles each attempt: 1 min, 2 min, 4 min. */
    const val BASE_DELAY_MS = 60_000L

    /** Ceiling on a single wait, so a future larger [MAX_AUTO_RETRIES] cannot drift into hours. */
    const val MAX_DELAY_MS = 30 * 60_000L

    /**
     * Failures that are known to be about the *link*, not the message: nothing
     * left the device, and the condition typically clears by itself.
     *
     * Deliberately absent, and each for its own reason:
     *  - `RESULT_ERROR_GENERIC_FAILURE` — ambiguous; see the class doc.
     *  - short-code codes (7, 8) — a retry is a second billing attempt.
     *  - `RESULT_ERROR_FDN_CHECK_FAILURE` — a policy decision on the SIM; it
     *    will not change in four minutes and retrying looks like probing it.
     *  - `RESULT_ERROR_NULL_PDU`, encoding/format errors — the message itself
     *    is wrong, so every retry fails identically.
     *  - `RESULT_NO_DEFAULT_SMS_APP` — needs a user action, not a wait.
     *  - `RESULT_SMS_BLOCKED_DURING_EMERGENCY` — retrying into an emergency
     *    call is precisely the wrong instinct.
     *  - `LOCAL_SEND_ERROR` — the exception happened before the radio; the same
     *    call will throw the same way.
     */
    val RETRYABLE_CODES: Set<Int> = setOf(
        SendFailure.RESULT_ERROR_RADIO_OFF,
        SendFailure.RESULT_ERROR_NO_SERVICE,
        SendFailure.RESULT_ERROR_LIMIT_EXCEEDED,
        SendFailure.RESULT_RADIO_NOT_AVAILABLE,
        SendFailure.RESULT_NETWORK_ERROR,
        SendFailure.RESULT_SMS_SEND_RETRY_FAILED,
        SendFailure.RESULT_RIL_RADIO_NOT_AVAILABLE,
        SendFailure.RESULT_RIL_SMS_SEND_FAIL_RETRY,
        SendFailure.RESULT_RIL_REQUEST_RATE_LIMITED,
        SendFailure.RESULT_RIL_MODEM_ERR,
        SendFailure.RESULT_RIL_NETWORK_ERR,
        SendFailure.RESULT_RIL_NETWORK_NOT_READY,
        SendFailure.RESULT_RIL_NO_NETWORK_FOUND,
        SendFailure.RESULT_RIL_SIMULTANEOUS_SMS_AND_CALL_NOT_ALLOWED,
        SendFailure.RESULT_RIL_BLOCKED_DUE_TO_CALL,
    )

    /**
     * Whether the [attempts]-th automatic retry of a failure with [code] may
     * happen. [attempts] is how many automatic retries have already been made,
     * so the first call after a failure passes 0.
     *
     * A null code means the failure was recorded without one — a legacy row, or
     * an MMS. Unknown cause, so no automatic retry.
     */
    fun shouldAutoRetry(code: Int?, attempts: Int): Boolean =
        code != null && attempts < MAX_AUTO_RETRIES && code in RETRYABLE_CODES

    /**
     * Milliseconds to wait before retry number [attempts] (0-based). Clamped
     * both ways: never negative from a nonsense argument, never above
     * [MAX_DELAY_MS].
     */
    fun delayMs(attempts: Int): Long {
        if (attempts <= 0) return BASE_DELAY_MS
        // shl rather than pow: exact, and the shift is bounded by the clamp
        // below long before it could overflow.
        val shifted = BASE_DELAY_MS shl attempts.coerceAtMost(20)
        return shifted.coerceIn(BASE_DELAY_MS, MAX_DELAY_MS)
    }

    /**
     * Whether a message in this state belongs in the outbox — i.e. it is
     * outgoing and has not finished.
     *
     * `SENT` is deliberately out. A sent message with an outstanding delivery
     * report is not stuck; it is done, and the report may never arrive at all
     * on many carriers. Keeping it in the outbox would turn a working feature
     * into a permanent list of things that look unfinished.
     */
    fun isPending(sendStatus: String): Boolean = sendStatus in PENDING_STATES

    val PENDING_STATES: Set<String> = setOf("SCHEDULED", "CLAIMED", SendAggregate.SENDING, SendAggregate.FAILED)
}
