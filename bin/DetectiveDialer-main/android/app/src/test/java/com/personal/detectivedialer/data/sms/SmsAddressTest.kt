package com.personal.detectivedialer.data.sms

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsAddressTest {

    @Test fun `real mobile numbers are repliable`() {
        assertTrue(SmsAddress.isRepliable("9812345678"))
        assertTrue(SmsAddress.isRepliable("+919812345678"))
        assertTrue(SmsAddress.isRepliable("+1 (415) 555-2671"))
        assertTrue(SmsAddress.isRepliable("098123 45678"))
    }

    @Test fun `alphanumeric A2P headers are not repliable`() {
        assertFalse(SmsAddress.isRepliable("AD-HDFCBK"))
        assertFalse(SmsAddress.isRepliable("AX-HDFCBK-P"))
        assertFalse(SmsAddress.isRepliable("VM-AMAZON"))
        assertFalse(SmsAddress.isRepliable("HDFCBK"))
    }

    @Test fun `numeric short codes are not repliable`() {
        assertFalse(SmsAddress.isRepliable("121"))
        assertFalse(SmsAddress.isRepliable("1909"))
        assertFalse(SmsAddress.isRepliable("56161"))
        assertFalse(SmsAddress.isRepliable("555555"))
    }

    @Test fun `blank or null is not repliable`() {
        assertFalse(SmsAddress.isRepliable(""))
        assertFalse(SmsAddress.isRepliable("   "))
        assertFalse(SmsAddress.isRepliable(null))
    }

    @Test fun `seven-digit boundary is repliable`() {
        // At/above the threshold: kept (bias toward allowing reply).
        assertTrue(SmsAddress.isRepliable("1234567"))
        // Just below: excluded.
        assertFalse(SmsAddress.isRepliable("123456"))
    }
}
