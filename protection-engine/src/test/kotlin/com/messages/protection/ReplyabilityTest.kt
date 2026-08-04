package com.messages.protection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Bug C: alphanumeric sender IDs cannot receive SMS — the chat UI hides the
 * composer for them and notifications must never offer inline reply.
 */
class ReplyabilityTest {

    @Test
    fun `dlt headers are not replyable`() {
        assertFalse(SenderAnalyzer.canReceiveReplies("VM-BANKXX"))
        assertFalse(SenderAnalyzer.canReceiveReplies("AX-BANKXX-S"))
        assertFalse(SenderAnalyzer.canReceiveReplies("JD-620014-P"))
        assertFalse(SenderAnalyzer.canReceiveReplies("JK-REGINF-G"))
        assertFalse(SenderAnalyzer.canReceiveReplies("AD-EDUOTP-S"))
    }

    @Test
    fun `unregistered alphanumeric headers are not replyable`() {
        assertFalse(SenderAnalyzer.canReceiveReplies("OFFERS"))
        assertFalse(SenderAnalyzer.canReceiveReplies("MyShop"))
    }

    @Test
    fun `email gateways are not replyable`() {
        assertFalse(SenderAnalyzer.canReceiveReplies("promo@shop.example"))
    }

    @Test
    fun `personal numbers are replyable`() {
        assertTrue(SenderAnalyzer.canReceiveReplies("+919876543210"))
        assertTrue(SenderAnalyzer.canReceiveReplies("9876543210"))
        assertTrue(SenderAnalyzer.canReceiveReplies("98765 43210"))
    }

    @Test
    fun `international numbers are replyable`() {
        assertTrue(SenderAnalyzer.canReceiveReplies("+14155552671"))
        assertTrue(SenderAnalyzer.canReceiveReplies("+447911123456"))
    }

    @Test
    fun `short codes are replyable`() {
        assertTrue(SenderAnalyzer.canReceiveReplies("56161"))
        assertTrue(SenderAnalyzer.canReceiveReplies("1909"))
    }

    @Test
    fun `odd numeric formats are still replyable`() {
        // 10-digit long code starting below 6 — outside INDIAN_MOBILE's
        // [6-9] first digit, but still a routable numeric destination.
        assertTrue(SenderAnalyzer.canReceiveReplies("1234567890"))
        // '+'-less international with formatting.
        assertTrue(SenderAnalyzer.canReceiveReplies("00 1 (415) 555-2671"))
    }

    @Test
    fun `blank is not replyable`() {
        assertFalse(SenderAnalyzer.canReceiveReplies(""))
        assertFalse(SenderAnalyzer.canReceiveReplies("   "))
    }
}
