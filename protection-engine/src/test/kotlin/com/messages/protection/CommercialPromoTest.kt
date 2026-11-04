package com.messages.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommercialPromoTest {

    private val engine = TestEngine.engine()

    @Test
    fun testCommercialSenderIdentification() {
        val commercialHeaders = listOf(
            "AD-AIRTEL",
            "JM-AIRTEL",
            "AX-FLIPKT",
            "BP-SWIGGY",
            "AMAZON",
            "ZOMATO",
            "VK-JIO",
            "CP-MYNTRA",
            "AMAZON-IN",
            "AIRTEL-5G",
            "AX-AMZN",
            "VK-JIOMOB",
        )

        for (header in commercialHeaders) {
            val info = SenderAnalyzer.analyze(header, isContact = false)
            assertEquals("Expected $header to be COMMERCIAL_PROMOTIONAL", SenderType.COMMERCIAL_PROMOTIONAL, info.type)
            assertTrue("Expected $header to be commercial sender", SenderAnalyzer.isCommercialSender(info))
            assertTrue("Expected $header to be registered header", SenderAnalyzer.isRegisteredHeader(info))
        }
    }

    @Test
    fun testCommercialPromoMessagesClassifiedIntoPromotions() {
        val testCases = listOf(
            Pair("AD-AIRTEL", "Mega Offer! Recharge with Rs 299 and get 1.5GB/day with unlimited calls."),
            Pair("JM-AIRTEL", "Special offer just for you! Flat 50% discount on your next recharge."),
            Pair("AX-FLIPKT", "Flash Sale is live now! Get up to 70% off on electronics and fashion."),
            Pair("BP-SWIGGY", "Exclusive offer! Flat 100 off on your food order today. Use code SWIGGY100."),
            Pair("AMAZON", "Great offer! Extra 20% discount on all home appliances during Great Indian Festival."),
            Pair("ZOMATO", "Season sale ends tonight! 50% discount on top restaurants near you."),
            Pair("AMAZON-IN", "Big Billion Deals! Save 30% on top smartphones today."),
            Pair("AIRTEL-5G", "Recharge now with ₹479 to enjoy unlimited 5G data for 56 days."),
            Pair("AX-AMZN", "Festive Sale is live! Get Rs 500 cashback on your order."),
        )

        for ((sender, body) in testCases) {
            val verdict = engine.classify(
                ProtectionEngine.Input(body, SenderAnalyzer.analyze(sender, isContact = false))
            )
            assertEquals(
                "Expected promo message from $sender to land in PROMOTIONS (was: ${verdict.category}, score: ${verdict.score})",
                Category.PROMOTIONS,
                verdict.category,
            )
        }
    }

    @Test
    fun testCommercialSenderOtpStillReachesInbox() {
        val otpBody = "483920 is your OTP for Airtel Thanks login. Valid for 10 minutes. Do not share it."
        val verdict = engine.classify(
            ProtectionEngine.Input(otpBody, SenderAnalyzer.analyze("AD-AIRTEL", isContact = false))
        )
        assertEquals("OTP must still reach INBOX", Category.INBOX, verdict.category)
        assertEquals("OTP must have ProtectedLabel.OTP", ProtectedLabel.OTP, verdict.protectedLabel)
        assertTrue("OTP must notify", verdict.notify)
    }

    @Test
    fun testBankingSenderNotClassifiedAsCommercial() {
        val bankingHeaders = listOf("AD-HDFCBK", "VM-SBIINB", "AX-ICICIB", "VK-AXISBK")
        for (header in bankingHeaders) {
            val info = SenderAnalyzer.analyze(header, isContact = false)
            assertEquals("Expected $header to be REGISTERED_TRANSACTIONAL", SenderType.REGISTERED_TRANSACTIONAL, info.type)
        }
    }
}
