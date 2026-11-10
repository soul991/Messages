package com.messages.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MisfilingRulesTest {

    private val engine = TestEngine.engine()

    private fun classify(
        body: String,
        address: String,
        isContact: Boolean = false,
        hasUserReplyHistory: Boolean = false,
    ): Verdict {
        val sender = SenderAnalyzer.analyze(
            rawAddress = address,
            isContact = isContact,
            reputationScore = 0,
            firstContact = false,
            hasUserReplyHistory = hasUserReplyHistory,
        )
        return engine.classify(ProtectionEngine.Input(body, sender))
    }

    // =========================================================================
    // Rule 1: Transactional patterns from verified sender formats MUST file
    // under Personal (INBOX/TRANSACTIONS), NEVER under Promo.
    // =========================================================================
    @Test
    fun rule1_transactionalPatterns_neverFileUnderPromo() {
        // Bank debit alert with promo/cashback footer
        val debitMsg = "Your A/c ending 1234 is debited by Rs 500.00 on 05-Nov-26. Avail exciting 10% cashback offers on debit card: hdfcbank.com"
        val debitVerdict = classify(debitMsg, "VM-HDFCBK")
        assertNotEquals("Debit alert must NEVER be filed under PROMOTIONS", Category.PROMOTIONS, debitVerdict.category)
        assertTrue("Debit alert must file under Personal/Inbox", debitVerdict.category == Category.INBOX || debitVerdict.category == Category.TRANSACTIONS)
        assertTrue("Debit alert must notify", debitVerdict.notify)

        // Order placed with cashback mention
        val orderMsg = "Order Placed: Hi ISTAK AHAMED, Thank You for choosing Mamaearth. Your order of Rs 386 has been successfully placed. Cashback will be credited soon."
        val orderVerdict = classify(orderMsg, "AD-MAMAER")
        assertNotEquals("Order placed must NEVER be filed under PROMOTIONS", Category.PROMOTIONS, orderVerdict.category)
        assertTrue("Order placed must file under Personal/Inbox", orderVerdict.category == Category.INBOX || orderVerdict.category == Category.TRANSACTIONS)

        // OTP with discount advertisement
        val otpMsg = "552310 is your OTP for transaction of Rs 1,200. Valid for 10 mins. Get flat 20% off on your next purchase using code FESTIVE."
        val otpVerdict = classify(otpMsg, "VK-SBIINB")
        assertNotEquals("OTP must NEVER be filed under PROMOTIONS", Category.PROMOTIONS, otpVerdict.category)
        assertTrue("OTP must file under Personal/Inbox", otpVerdict.category == Category.INBOX || otpVerdict.category == Category.TRANSACTIONS)

        // Account balance alert with loan offer
        val balMsg = "Avail Bal in A/c 5678 is INR 24,500.50. Apply for instant personal loan at 9.9% with zero processing fee."
        val balVerdict = classify(balMsg, "AX-ICICIB")
        assertNotEquals("Account balance alert must NEVER be filed under PROMOTIONS", Category.PROMOTIONS, balVerdict.category)
        assertTrue("Balance alert must file under Personal/Inbox", balVerdict.category == Category.INBOX || balVerdict.category == Category.TRANSACTIONS)
    }

    // =========================================================================
    // Rule 2: Any message from a saved contact or with user reply history
    // MUST stay in Personal unless explicitly marked Spam by user.
    // =========================================================================
    @Test
    fun rule2_savedContactOrReplyHistory_staysInPersonal() {
        // Saved contact sending urgent message or link — must NOT be spammed
        val contactMsg = "Hey, can you help me urgently? My account is temporarily suspended, check this link: https://example.com/info"
        val contactVerdict = classify(contactMsg, "+919876543210", isContact = true)
        assertEquals("Message from saved contact must stay in INBOX (Personal)", Category.INBOX, contactVerdict.category)
        assertTrue("Message from saved contact must notify", contactVerdict.notify)

        // Unknown 10-digit number, BUT user has sent replies to it in the past
        val replyHistoryMsg = "Can you send me tomorrow's schedule? Call me when you get this."
        val replyVerdict = classify(replyHistoryMsg, "+919123456789", isContact = false, hasUserReplyHistory = true)
        assertEquals("Message with user reply history must stay in INBOX (Personal)", Category.INBOX, replyVerdict.category)
        assertTrue("Message with user reply history must notify", replyVerdict.notify)
    }

    // =========================================================================
    // Rule 3: Messages with urgency + links/phone numbers from unknown
    // 10-digit numbers must route to Review or Spam, not Personal.
    // =========================================================================
    @Test
    fun rule3_urgencyPlusLinkOrPhoneFromUnknownNumeric_routesToReviewOrSpam() {
        // Urgent PAN/KYC with suspicious shortener link from unknown 10-digit number -> SPAM
        val phishingMsg = "URGENT: Your bank account will be blocked today! Update your KYC immediately at: https://bit.ly/bank-kyc-update"
        val phishingVerdict = classify(phishingMsg, "9876543210", isContact = false, hasUserReplyHistory = false)
        assertEquals("Urgent phishing from unknown number must route to SPAM", Category.SPAM, phishingVerdict.category)

        // Urgent electricity disconnection with helpline number from unknown number -> REVIEW or SPAM
        val helplineMsg = "Final notice: Electricity power will be disconnected tonight at 9 PM due to unpaid bill. Contact helpline immediately: +919812345678"
        val helplineVerdict = classify(helplineMsg, "9812345678", isContact = false, hasUserReplyHistory = false)
        assertTrue(
            "Urgent disconnection notice with phone from unknown number must be REVIEW or SPAM, got ${helplineVerdict.category}",
            helplineVerdict.category == Category.REVIEW || helplineVerdict.category == Category.SPAM,
        )
        assertNotEquals("Must NOT route to Personal (INBOX)", Category.INBOX, helplineVerdict.category)

        // Deactivation threat with link from unknown 10-digit number
        val deactivationMsg = "Your SIM card will be deactivated within 24 hours. Verify now: https://verify-service.net/sim"
        val deactivationVerdict = classify(deactivationMsg, "8899001122", isContact = false, hasUserReplyHistory = false)
        assertTrue(
            "Deactivation threat from unknown number must be REVIEW or SPAM, got ${deactivationVerdict.category}",
            deactivationVerdict.category == Category.REVIEW || deactivationVerdict.category == Category.SPAM,
        )
        assertNotEquals("Must NOT route to Personal (INBOX)", Category.INBOX, deactivationVerdict.category)
    }

    // =========================================================================
    // Rule 4: Carrier spam warnings (e.g. "[SPAM]" prepended) must route directly to Spam.
    // =========================================================================
    @Test
    fun rule4_carrierSpamWarnings_routeDirectlyToSpam() {
        val carrierSpam1 = "[SPAM] Claim your exclusive lottery reward today! Click here to redeem."
        val verdict1 = classify(carrierSpam1, "9876543210")
        assertEquals("[SPAM] prefix must route directly to SPAM", Category.SPAM, verdict1.category)

        val carrierSpam2 = "[Possible SPAM] Dear customer, your card is blocked, verify now."
        val verdict2 = classify(carrierSpam2, "AD-UNKNOWN")
        assertEquals("[Possible SPAM] prefix must route directly to SPAM", Category.SPAM, verdict2.category)

        val carrierSpam3 = "SPAM: Special loan offer of Rs 5,00,000 approved without documents."
        val verdict3 = classify(carrierSpam3, "+919988776655")
        assertEquals("SPAM: prefix must route directly to SPAM", Category.SPAM, verdict3.category)

        val carrierSpam4 = "[FRAUD] Unauthorized transaction attempted on your card."
        val verdict4 = classify(carrierSpam4, "9811223344")
        assertEquals("[FRAUD] prefix must route directly to SPAM", Category.SPAM, verdict4.category)
    }

    // =========================================================================
    // Rule 5: Crypto/NFT/lottery keywords from unknown senders must route to Spam.
    // =========================================================================
    @Test
    fun rule5_cryptoNftLottery_fromUnknownSenders_routesToSpam() {
        // Crypto bonus from unknown sender
        val cryptoMsg = "Deposit 100 USDT and get 500 USDT bonus on Binance trading. Start earning now."
        val cryptoVerdict = classify(cryptoMsg, "9876543210", isContact = false, hasUserReplyHistory = false)
        assertEquals("Crypto solicitation from unknown number must route to SPAM", Category.SPAM, cryptoVerdict.category)

        // NFT mint from unknown sender
        val nftMsg = "Exclusive NFT mint pass is live! Connect your Metamask wallet at open-mint.xyz"
        val nftVerdict = classify(nftMsg, "9812345678", isContact = false, hasUserReplyHistory = false)
        assertEquals("NFT solicitation from unknown number must route to SPAM", Category.SPAM, nftVerdict.category)

        // KBC lottery winner from unknown sender
        val kbcMsg = "Congratulations! Your mobile number won Rs 25,00,000 in KBC lucky draw. Contact WhatsApp 9876543210."
        val kbcVerdict = classify(kbcMsg, "9800112233", isContact = false, hasUserReplyHistory = false)
        assertEquals("KBC lottery from unknown number must route to SPAM", Category.SPAM, kbcVerdict.category)

        // Bumper cash prize from unknown sender
        val lotteryMsg = "You are the bumper prize winner of 10 Lakh cash prize in national draw. Claim today."
        val lotteryVerdict = classify(lotteryMsg, "+919876501234", isContact = false, hasUserReplyHistory = false)
        assertEquals("Lottery solicitation from unknown number must route to SPAM", Category.SPAM, lotteryVerdict.category)

        // Item X5: MATLIA / NSETrade... BOUGHT SIGILUM NFT Trading Platform
        val matliaMsg = "MATLIA / NSETrade... BOUGHT SIGILUM NFT Trading Platform"
        val matliaVerdict = classify(matliaMsg, "MATLIA", isContact = false, hasUserReplyHistory = false)
        assertEquals("MATLIA NFT trading spam must route to SPAM", Category.SPAM, matliaVerdict.category)

        val nseTradeMsg = "NSETrade: Strong Buy call on SIGILUM Trading Platform. 200% profit guaranteed. Join VIP group."
        val nseVerdict = classify(nseTradeMsg, "VM-NSETRD", isContact = false, hasUserReplyHistory = false)
        assertEquals("NSETrade trading solicitation must route to SPAM", Category.SPAM, nseVerdict.category)

        val multibaggerMsg = "Multibagger stock tip: buy SIGILUM shares today before target is reached on trading platform"
        val multibaggerVerdict = classify(multibaggerMsg, "9876543210", isContact = false, hasUserReplyHistory = false)
        assertEquals("Multibagger trading tip from unknown sender must route to SPAM", Category.SPAM, multibaggerVerdict.category)
    }

    // =========================================================================
    // Rule 6: OTP and bank alerts mentioning crypto/trading remain strictly protected.
    // =========================================================================
    @Test
    fun rule6_otpAndBankAlerts_mentioningCryptoOrTrading_remainProtected() {
        // OTP mentioning Crypto Trading Platform from registered header
        val otpCrypto = "482910 is your OTP for transaction at CoinDCX Crypto Trading Platform. Valid for 10 mins. Never share your OTP."
        val otpVerdict = classify(otpCrypto, "VM-HDFCBK", isContact = false)
        assertNotEquals("OTP must NEVER route to SPAM", Category.SPAM, otpVerdict.category)
        assertEquals("OTP must have ProtectedLabel.OTP", ProtectedLabel.OTP, otpVerdict.protectedLabel)
        assertTrue("OTP must notify", otpVerdict.notify)

        // Bank debit alert mentioning NSETrade / Trading
        val bankTrade = "Your A/c ending 1234 debited by INR 5,000.00 for order at Zerodha NSETrade. Avail Bal: INR 45,000."
        val bankVerdict = classify(bankTrade, "AX-HDFCBK", isContact = false)
        assertNotEquals("Bank alert must NEVER route to SPAM", Category.SPAM, bankVerdict.category)
        assertEquals("Bank alert must have ProtectedLabel.BANK", ProtectedLabel.BANK, bankVerdict.protectedLabel)
        assertTrue("Bank alert must notify", bankVerdict.notify)

        // Card OTP for Binance Crypto
        val cardOtp = "Your SBI Card OTP is 654321 for transaction of Rs 2,500 at Binance Trading. Do not share OTP."
        val cardVerdict = classify(cardOtp, "VK-SBICRD", isContact = false)
        assertNotEquals("Card OTP must NEVER route to SPAM", Category.SPAM, cardVerdict.category)
        assertEquals("Card OTP must have ProtectedLabel.OTP", ProtectedLabel.OTP, cardVerdict.protectedLabel)
        assertTrue("Card OTP must notify", cardVerdict.notify)
    }
}
