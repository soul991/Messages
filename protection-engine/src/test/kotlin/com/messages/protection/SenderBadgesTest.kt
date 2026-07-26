package com.messages.protection

import com.messages.protection.SenderBadges.Badge
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SenderBadgesTest {

    private fun badge(
        address: String,
        isContact: Boolean = false,
        dangerous: Boolean = false,
        protectedLabel: String? = null,
    ) = SenderBadges.badgeFor(address, isContact, dangerous, protectedLabel)

    // ---- blue check: DLT -S/-T/-G + registered header format ----

    @Test
    fun `dlt transactional and govt headers are verified`() {
        assertEquals(Badge.VERIFIED, badge("AX-BANKXX-S"))
        assertEquals(Badge.VERIFIED, badge("JV-TELCOX-S"))
        assertEquals(Badge.VERIFIED, badge("VM-IRCTCI-T"))
        assertEquals(Badge.VERIFIED, badge("JK-REGINF-G"))
        // Plain registered format without a suffix.
        assertEquals(Badge.VERIFIED, badge("AX-BANKXX"))
    }

    @Test
    fun `protected-lane bank courier govt labels elevate unregistered alpha headers`() {
        assertEquals(Badge.VERIFIED, badge("HDFCBANK", protectedLabel = "BANK"))
        assertEquals(Badge.VERIFIED, badge("BLUEDART", protectedLabel = "DELIVERY"))
        assertEquals(Badge.VERIFIED, badge("EPFOHO", protectedLabel = "GOV"))
        // Other labels don't.
        assertEquals(Badge.BUSINESS, badge("MYSHOP", protectedLabel = "BILL"))
        assertEquals(Badge.BUSINESS, badge("MYSHOP", protectedLabel = "NONE"))
    }

    // ---- neutral Business tag ----

    @Test
    fun `promo headers and other alpha headers get the business tag`() {
        assertEquals(Badge.BUSINESS, badge("JD-620014-P"))
        // Note: "VM-OFFERS"-style IDs match the DLT header FORMAT and are
        // classified registered by SenderAnalyzer → VERIFIED, not BUSINESS.
        // Business is for alpha IDs outside the registered format:
        assertEquals(Badge.BUSINESS, badge("MYSHOP"))
        assertEquals(Badge.BUSINESS, badge("OFFERS123"))
        assertEquals(Badge.BUSINESS, badge("AmazonIN"))
    }

    // ---- nothing ----

    @Test
    fun `personal international short-code and gateway senders get nothing`() {
        assertNull(badge("+919876543210"))
        assertNull(badge("9876543210"))
        assertNull(badge("+14155552671"))
        assertNull(badge("56161"))
        assertNull(badge("promo@shop.example"))
        // Odd numeric formats that bucket as ALPHANUMERIC_UNKNOWN are still numbers.
        assertNull(badge("1234567890"))
    }

    @Test
    fun `saved contacts groups and blanks get nothing`() {
        assertNull(badge("AX-BANKXX-S", isContact = true))
        assertNull(badge("+919876543210;+919876543211"))
        assertNull(badge(""))
    }

    // ---- THE absolute rule ----

    @Test
    fun `fraud suppression beats every badge including verified bank headers`() {
        // A dangerous message from a "bank" header — no trust chrome, period.
        assertNull(badge("AX-BANKXX-S", dangerous = true))
        assertNull(badge("JK-REGINF-G", dangerous = true))
        assertNull(badge("JD-620014-P", dangerous = true))
        assertNull(badge("HDFCBANK", dangerous = true, protectedLabel = "BANK"))
    }

    @Test
    fun `explanations are one-liners`() {
        assertEquals(
            "Registered business sender (DLT header)",
            SenderBadges.explanation(Badge.VERIFIED),
        )
        assertEquals(
            "Business sender (alphanumeric ID) — not a personal number",
            SenderBadges.explanation(Badge.BUSINESS),
        )
    }
}
