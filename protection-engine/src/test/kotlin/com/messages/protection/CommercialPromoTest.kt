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
            Pair("VM-AMAZON", "Great offer! Extra 20% discount on all home appliances during Great Indian Festival."),
            Pair("AD-ZOMATO", "Season sale ends tonight! 50% discount on top restaurants near you."),
            Pair("AX-AMAZON", "Big Billion Deals! Save 30% on top smartphones today."),
            Pair("AD-AIRTEL", "Recharge now with ₹479 to enjoy unlimited 5G data for 56 days."),
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

    // ---- Transactional-guard tests: commercial sender, transactional wording → INBOX ----

    @Test
    fun testJioRechargeSuccessfulStaysInInbox() {
        val body = "Your recharge of Rs 239 is successful. Your new validity is till 28-Dec-2026. Enjoy unlimited calls and 1.5GB/day data."
        val verdict = engine.classify(
            ProtectionEngine.Input(body, SenderAnalyzer.analyze("VK-JIO", isContact = false))
        )
        assertEquals("Jio recharge successful must stay in INBOX", Category.INBOX, verdict.category)
        assertTrue("Jio recharge successful must notify", verdict.notify)
    }

    @Test
    fun testAirtelRechargeSuccessfulStaysInInbox() {
        val body = "Recharge successful! Rs 299 recharge done for 9876543210. Validity: 28 days. Thank you for choosing Airtel."
        val verdict = engine.classify(
            ProtectionEngine.Input(body, SenderAnalyzer.analyze("AD-AIRTEL", isContact = false))
        )
        assertEquals("Airtel recharge successful must stay in INBOX", Category.INBOX, verdict.category)
        assertTrue("Airtel recharge successful must notify", verdict.notify)
    }

    @Test
    fun testSwiggyOrderDeliveredStaysInInbox() {
        val body = "Your order #SW-87234 has been delivered. Thank you for ordering with Swiggy! Rate your experience."
        val verdict = engine.classify(
            ProtectionEngine.Input(body, SenderAnalyzer.analyze("BP-SWIGGY", isContact = false))
        )
        assertEquals("Swiggy order delivered must stay in INBOX", Category.INBOX, verdict.category)
        assertTrue("Swiggy order delivered must notify", verdict.notify)
    }

    @Test
    fun testIrctcTicketBookedStaysInInbox() {
        val body = "Ticket booked successfully! PNR: 2847193650, Train: 12301 Rajdhani Exp, 15-Dec-2026, SL, NDLS to HWH. Boarding at NDLS."
        val verdict = engine.classify(
            ProtectionEngine.Input(body, SenderAnalyzer.analyze("AX-IRCTC", isContact = false))
        )
        assertEquals("IRCTC ticket booked must stay in INBOX", Category.INBOX, verdict.category)
        assertTrue("IRCTC ticket booked must notify", verdict.notify)
    }

    @Test
    fun testUberTripReceiptStaysInInbox() {
        val body = "Trip completed. Your Uber trip from Koramangala to Indiranagar cost Rs 142. Payment debited from Paytm wallet. Thank you for riding with us."
        val verdict = engine.classify(
            ProtectionEngine.Input(body, SenderAnalyzer.analyze("UBER", isContact = false))
        )
        assertEquals("Uber trip receipt must stay in INBOX", Category.INBOX, verdict.category)
        assertTrue("Uber trip receipt must notify", verdict.notify)
    }

    // ---- Verify promos with explicit offer signals still go to PROMOTIONS ----

    @Test
    fun testPromoWithOfferSignalStillGoesToPromotions() {
        // Transactional wording + explicit offer signal → still PROMOTIONS
        val body = "Your recharge of Rs 199 is successful. Special offer: Recharge with Rs 599 and get 50% cashback! Use code AIRTEL50."
        val verdict = engine.classify(
            ProtectionEngine.Input(body, SenderAnalyzer.analyze("AD-AIRTEL", isContact = false))
        )
        assertEquals("Recharge confirmation with offer must still go to PROMOTIONS", Category.PROMOTIONS, verdict.category)
    }

    @Test
    fun testPurePromoMessagesStillReachPromotions() {
        val testCases = listOf(
            Pair("AD-AIRTEL", "Mega Offer! Recharge with Rs 299 and get 1.5GB/day with unlimited calls."),
            Pair("AX-FLIPKT", "Flash Sale is live now! Get up to 70% off on electronics and fashion."),
            Pair("BP-SWIGGY", "Exclusive offer! Flat 100 off on your food order today. Use code SWIGGY100."),
            Pair("AD-ZOMATO", "Season sale ends tonight! 50% discount on top restaurants near you."),
        )
        for ((sender, body) in testCases) {
            val verdict = engine.classify(
                ProtectionEngine.Input(body, SenderAnalyzer.analyze(sender, isContact = false))
            )
            assertEquals(
                "Pure promo from $sender must still reach PROMOTIONS",
                Category.PROMOTIONS,
                verdict.category,
            )
        }
    }

    // ---- Tests: commercial header + promo word + shortener link + urgency stays SPAM or REVIEW, not PROMOTIONS ----

    @Test
    fun testCommercialHeaderWithPromoWordShortenerLinkAndUrgencyStaysSpamOrReviewNotPromotions() {
        val testCases = listOf(
            Pair("AD-AIRTEL", "Hurry! Limited time mega offer: Recharge with 50% discount today only: http://bit.ly/airtel-offer"),
            Pair("BP-SWIGGY", "Flash deal! Flat 100 off on your food order today, offer ends soon! Click http://tinyurl.com/swiggy100 to order now."),
            Pair("AX-FLIPKT", "Don't miss! Huge discount sale ending midnight, grab your deal: https://is.gd/fkdeals now!"),
            Pair("AIRTEL", "Special offer! 50% discount on recharge today: http://bit.ly/airtelofr")
        )
        for ((sender, body) in testCases) {
            val verdict = engine.classify(
                ProtectionEngine.Input(body, SenderAnalyzer.analyze(sender, isContact = false))
            )
            assertTrue(
                "Expected message from $sender with shortener link to stay SPAM or REVIEW, but was ${verdict.category}",
                verdict.category == Category.SPAM || verdict.category == Category.REVIEW
            )
            org.junit.Assert.assertNotEquals(
                "Shortened link with urgency from $sender must NEVER route to PROMOTIONS",
                Category.PROMOTIONS,
                verdict.category
            )
        }
    }
}
