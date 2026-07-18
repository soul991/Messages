package com.personal.detectivedialer.data.sms

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TraiSuffixTest {

    @Test fun `promotional suffix parses`() {
        assertEquals(SmsCategory.PROMOTIONAL, TraiSuffix.categoryOf("BX-KWIKLN-P"))
    }

    @Test fun `service suffix parses`() {
        assertEquals(SmsCategory.SERVICE, TraiSuffix.categoryOf("AX-HDFCBK-S"))
    }

    @Test fun `transactional suffix parses`() {
        assertEquals(SmsCategory.TRANSACTIONAL, TraiSuffix.categoryOf("JM-SBIINB-T"))
    }

    @Test fun `government suffix parses`() {
        assertEquals(SmsCategory.GOVERNMENT, TraiSuffix.categoryOf("VD-UIDAI-G"))
    }

    @Test fun `mixed and lower case suffixes parse`() {
        assertEquals(SmsCategory.PROMOTIONAL, TraiSuffix.categoryOf("bx-kwikln-p"))
        assertEquals(SmsCategory.SERVICE, TraiSuffix.categoryOf("Ax-HdfcBk-s"))
    }

    @Test fun `surrounding whitespace is tolerated`() {
        assertEquals(SmsCategory.PROMOTIONAL, TraiSuffix.categoryOf("  BX-KWIKLN-P  "))
    }

    @Test fun `header without a suffix has no category`() {
        assertNull(TraiSuffix.categoryOf("AX-HDFCBK"))
        assertNull(TraiSuffix.categoryOf("HDFCBK"))
    }

    @Test fun `unknown suffix letters are malformed`() {
        assertNull(TraiSuffix.categoryOf("AX-HDFCBK-X"))
        assertNull(TraiSuffix.categoryOf("AX-HDFCBK-9"))
    }

    @Test fun `multi-char tail is not a suffix`() {
        assertNull(TraiSuffix.categoryOf("AX-HDFCBK-PS"))
        assertNull(TraiSuffix.categoryOf("AX-HDFC-BKP"))
    }

    @Test fun `dangling or empty parts are malformed`() {
        assertNull(TraiSuffix.categoryOf("AX-HDFCBK-"))
        assertNull(TraiSuffix.categoryOf("-P"))
        assertNull(TraiSuffix.categoryOf("P"))
        assertNull(TraiSuffix.categoryOf(""))
        assertNull(TraiSuffix.categoryOf(null))
    }

    @Test fun `numeric-only senders are P2P, never categorized`() {
        assertNull(TraiSuffix.categoryOf("+919812345678"))
        assertNull(TraiSuffix.categoryOf("9812345678"))
        assertNull(TraiSuffix.categoryOf("56789"))
        // Formatted numbers whose last dash-part is one digit must not parse.
        assertNull(TraiSuffix.categoryOf("98123-4"))
        assertNull(TraiSuffix.categoryOf("+91 98123 4567-8"))
    }

    @Test fun `suffix letter inside the header body does not count`() {
        assertNull(TraiSuffix.categoryOf("AX-PROMO"))
    }

    @Test fun `isA2pHeader separates headers from numbers`() {
        assertTrue(TraiSuffix.isA2pHeader("AX-HDFCBK-S"))
        assertTrue(TraiSuffix.isA2pHeader("HDFCBK"))
        assertFalse(TraiSuffix.isA2pHeader("+919812345678"))
        assertFalse(TraiSuffix.isA2pHeader("98123-45678"))
        assertFalse(TraiSuffix.isA2pHeader(""))
        assertFalse(TraiSuffix.isA2pHeader(null))
    }
}
