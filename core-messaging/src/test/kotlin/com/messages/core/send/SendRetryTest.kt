package com.messages.core.send

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * V2-48 guard: the automatic-retry policy.
 *
 * The interesting assertions here are the negative ones. Anyone can make a
 * retry loop; the thing worth pinning is what the loop refuses to touch,
 * because every wrong retry spends the user's money on a message they did not
 * ask to send twice. If a later change makes [SendRetry] permissive, these
 * tests are what notices.
 */
class SendRetryTest {

    @Test
    fun `a transient radio failure is retried`() {
        assertTrue(SendRetry.shouldAutoRetry(SendFailure.RESULT_ERROR_RADIO_OFF, 0))
        assertTrue(SendRetry.shouldAutoRetry(SendFailure.RESULT_ERROR_NO_SERVICE, 0))
        assertTrue(SendRetry.shouldAutoRetry(SendFailure.RESULT_RIL_NETWORK_NOT_READY, 2))
    }

    @Test
    fun `the ambiguous generic failure is never retried automatically`() {
        // Carriers report "no balance" and "we sent it but lost the ack" as
        // code 1. Three silent retries against either is money or duplicates
        // spent on the user's behalf, so this one gets a Resend button.
        assertFalse(
            "GENERIC_FAILURE must stay in the outbox for the user",
            SendRetry.shouldAutoRetry(SmsResultCodes.GENERIC_FAILURE, 0),
        )
        assertFalse(SendRetry.RETRYABLE_CODES.contains(SmsResultCodes.GENERIC_FAILURE))
    }

    @Test
    fun `billing-sensitive and permanent failures are never retried`() {
        val neverRetry = listOf(
            SendFailure.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED,
            SendFailure.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED,
            SendFailure.RESULT_ERROR_FDN_CHECK_FAILURE,
            SendFailure.RESULT_ERROR_NULL_PDU,
            SendFailure.RESULT_NO_DEFAULT_SMS_APP,
            SendFailure.RESULT_SMS_BLOCKED_DURING_EMERGENCY,
            SendFailure.RESULT_RIL_INVALID_SMS_FORMAT,
            SendFailure.RESULT_RIL_ENCODING_ERR,
            SendFailure.LOCAL_SEND_ERROR,
        )
        for (code in neverRetry) {
            assertFalse("code $code must not be auto-retried", SendRetry.shouldAutoRetry(code, 0))
        }
    }

    @Test
    fun `an unknown code is not retried`() {
        // The policy is an allowlist, so a platform code nobody has seen yet
        // lands on the safe side without anyone remembering to add it.
        assertFalse(SendRetry.shouldAutoRetry(9_999, 0))
        assertFalse("a failure with no recorded cause is not retried", SendRetry.shouldAutoRetry(null, 0))
    }

    @Test
    fun `the budget is finite`() {
        val code = SendFailure.RESULT_ERROR_RADIO_OFF
        for (attempts in 0 until SendRetry.MAX_AUTO_RETRIES) {
            assertTrue("attempt $attempts should be allowed", SendRetry.shouldAutoRetry(code, attempts))
        }
        assertFalse(
            "the budget must stop at MAX_AUTO_RETRIES",
            SendRetry.shouldAutoRetry(code, SendRetry.MAX_AUTO_RETRIES),
        )
        assertFalse(SendRetry.shouldAutoRetry(code, SendRetry.MAX_AUTO_RETRIES + 5))
    }

    @Test
    fun `backoff grows and is bounded both ways`() {
        assertEquals(SendRetry.BASE_DELAY_MS, SendRetry.delayMs(0))
        assertEquals(SendRetry.BASE_DELAY_MS * 2, SendRetry.delayMs(1))
        assertEquals(SendRetry.BASE_DELAY_MS * 4, SendRetry.delayMs(2))
        // A nonsense argument must never produce a negative or zero wait, which
        // would turn the retry into a tight loop against the radio.
        assertEquals(SendRetry.BASE_DELAY_MS, SendRetry.delayMs(-3))
        // And no shift may overflow past the ceiling.
        assertEquals(SendRetry.MAX_DELAY_MS, SendRetry.delayMs(60))
        assertTrue(SendRetry.delayMs(1_000_000) in SendRetry.BASE_DELAY_MS..SendRetry.MAX_DELAY_MS)
    }

    @Test
    fun `the outbox holds unfinished sends and nothing else`() {
        assertTrue(SendRetry.isPending("SCHEDULED"))
        assertTrue(SendRetry.isPending("CLAIMED"))
        assertTrue(SendRetry.isPending(SendAggregate.SENDING))
        assertTrue(SendRetry.isPending(SendAggregate.FAILED))
        // SENT is finished even when the delivery report never arrives — many
        // carriers never send one, and parking those in the outbox forever
        // would make the screen a list of things that only look broken.
        assertFalse(SendRetry.isPending(SendAggregate.SENT))
        assertFalse(SendRetry.isPending(SendAggregate.DELIVERED))
        assertFalse(SendRetry.isPending("NONE"))
    }

    /** Literal platform values, so the test does not read the constant it checks. */
    private object SmsResultCodes {
        const val GENERIC_FAILURE = 1
    }
}
