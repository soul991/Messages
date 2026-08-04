package com.messages.app.report

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CarrierReportFormatTest {

    private fun millis(y: Int, m: Int, d: Int): Long =
        Calendar.getInstance().apply {
            set(y, m - 1, d, 10, 30, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

    @Test
    fun `trai complaint follows message-sender-date format with dd-mm-yy`() {
        val out = CarrierReportFormat.traiComplaint(
            body = " You WON Rs 25,00,000! Click bit.ly/xyz ",
            sender = "VM-LOTTRY",
            receivedAtMillis = millis(2026, 7, 5),
        )
        assertEquals("You WON Rs 25,00,000! Click bit.ly/xyz, VM-LOTTRY, 05/07/26", out)
    }

    @Test
    fun `gsma report is just the trimmed body`() {
        assertEquals("spam text", CarrierReportFormat.gsmaReport("  spam text \n"))
    }

    @Test
    fun `trai complaint window is three days`() {
        val received = millis(2026, 7, 1)
        val twoDaysLater = received + 2 * 24 * 60 * 60 * 1000L
        val fourDaysLater = received + 4 * 24 * 60 * 60 * 1000L
        assertTrue(CarrierReportFormat.withinTraiComplaintWindow(received, twoDaysLater))
        assertFalse(CarrierReportFormat.withinTraiComplaintWindow(received, fourDaysLater))
    }
}
