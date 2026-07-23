package com.messages.core.send

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SendFailureTest {

    @Test
    fun `no service maps to no network signal`() {
        assertEquals("No network signal", SendFailure.reasonFor(SendFailure.RESULT_ERROR_NO_SERVICE))
        assertEquals("No network signal", SendFailure.reasonFor(SendFailure.RESULT_RIL_NO_NETWORK_FOUND))
    }

    @Test
    fun `radio off maps to airplane mode wording`() {
        val expected = "Airplane mode is on or the radio is off"
        assertEquals(expected, SendFailure.reasonFor(SendFailure.RESULT_ERROR_RADIO_OFF))
        assertEquals(expected, SendFailure.reasonFor(SendFailure.RESULT_RADIO_NOT_AVAILABLE))
        assertEquals(expected, SendFailure.reasonFor(SendFailure.RESULT_RIL_RADIO_NOT_AVAILABLE))
    }

    @Test
    fun `limit exceeded maps to sending limit wording`() {
        assertEquals(
            "Sending limit reached — try again later",
            SendFailure.reasonFor(SendFailure.RESULT_ERROR_LIMIT_EXCEEDED),
        )
        assertEquals(
            "Sending limit reached — try again later",
            SendFailure.reasonFor(SendFailure.RESULT_RIL_REQUEST_RATE_LIMITED),
        )
    }

    @Test
    fun `generic failure wording is honest — mentions balance only as a possibility`() {
        val reason = SendFailure.reasonFor(SendFailure.RESULT_ERROR_GENERIC_FAILURE)
        assertEquals("Couldn't send — check network coverage or account balance", reason)
        // Never claim to KNOW it's a balance problem — carriers don't report it.
        assertFalse(reason.lowercase().startsWith("insufficient"))
        assertFalse(reason.lowercase().contains("out of balance"))
    }

    @Test
    fun `ril codes map to specific reasons`() {
        assertEquals("No SIM card", SendFailure.reasonFor(SendFailure.RESULT_RIL_SIM_ABSENT))
        assertEquals(
            "The network rejected the message",
            SendFailure.reasonFor(SendFailure.RESULT_RIL_NETWORK_REJECT),
        )
        assertEquals(
            "Network problem — try again",
            SendFailure.reasonFor(SendFailure.RESULT_RIL_SMS_SEND_FAIL_RETRY),
        )
        assertEquals(
            "Network problem — try again",
            SendFailure.reasonFor(SendFailure.RESULT_RIL_NETWORK_ERR),
        )
        assertEquals(
            "Message centre number problem — check SIM settings",
            SendFailure.reasonFor(SendFailure.RESULT_RIL_INVALID_SMSC_ADDRESS),
        )
    }

    @Test
    fun `platform constant values match SmsManager`() {
        // Literals are duplicated from android.telephony.SmsManager so the
        // mapper stays JVM-pure — pin the load-bearing ones.
        assertEquals(1, SendFailure.RESULT_ERROR_GENERIC_FAILURE)
        assertEquals(2, SendFailure.RESULT_ERROR_RADIO_OFF)
        assertEquals(4, SendFailure.RESULT_ERROR_NO_SERVICE)
        assertEquals(5, SendFailure.RESULT_ERROR_LIMIT_EXCEEDED)
        assertEquals(100, SendFailure.RESULT_RIL_RADIO_NOT_AVAILABLE)
        assertEquals(120, SendFailure.RESULT_RIL_SIM_ABSENT)
    }

    @Test
    fun `unknown and null codes fall back to the honest generic wording`() {
        val generic = "Couldn't send — check network coverage or account balance"
        assertEquals(generic, SendFailure.reasonFor(9999))
        assertEquals(generic, SendFailure.reasonFor(null))
    }

    @Test
    fun `local exception code never collides with platform codes and has its own wording`() {
        assertTrue(SendFailure.LOCAL_SEND_ERROR < 0)
        assertEquals(
            "Couldn't send — check the SIM and the number",
            SendFailure.reasonFor(SendFailure.LOCAL_SEND_ERROR),
        )
    }

    @Test
    fun `detail includes raw code for debugging, but only when one exists`() {
        assertEquals("No network signal (code 4)", SendFailure.detailFor(4))
        assertEquals(
            "Couldn't send — check network coverage or account balance",
            SendFailure.detailFor(null),
        )
    }
}
