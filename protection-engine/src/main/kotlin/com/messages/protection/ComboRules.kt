package com.messages.protection

/**
 * §5.8 — Combination rules. Certain pattern combinations force a verdict
 * regardless of raw score; this is what makes the filter hard to break.
 */
object ComboRules {

    data class ComboResult(
        val id: String,
        val description: String,
        val category: Category,
        val dangerous: Boolean,
    )

    private val URGENCY = Regex(
        """(?i)\b(urgent|urgently|immediately|hurry|expir\w*|today|tonight|midnight|now|last (chance|day)|final(?: notice)?|within \d+ (hours?|min\w*)|asap|jaldi)\b"""
    )
    private val PRIZE_WORDS = Regex("""(?i)\b(won|winner|winning|lottery|prize|jackpot|lucky draw|inaam|jeet)\b""")
    private val ACCOUNT_THREAT = Regex("""(?i)\b(block\w*|suspend\w*|expir\w*|deactivat\w*|freez\w*|disabl\w*|band)\b""")
    private val PIN_ASK = Regex(
        """(?i)(enter|share|send|give|provide|batao|bhejo).{0,25}\b(pin|otp|cvv|password|passcode|mpin|upi pin)\b|\b(pin|otp|cvv|password|mpin)\b.{0,25}(enter|share|send|give|provide|batao|bhejo)"""
    )
    // "wallet"/"withdraw" added 2026-07-24 from live fake-credit bait
    // ("available in wallet is waiting", "Withdraw before 9PM") — C4 still
    // needs a shortener/suspicious-TLD link on top, so genuine wallet or
    // ATM-withdrawal alerts (official or no links) can't trip it.
    private val SCAM_HOOK = Regex("""(?i)\b(kyc|refund|deposited|credited|prize|won|loan|job|earn|lottery|reward|wallet|withdraw\w*)\b""")
    private val MONEY_JOB_PRIZE_FAMILIES = setOf(
        Families.LOTTERY, Families.FAKE_PAYMENT, Families.LOAN, Families.JOB,
        Families.INVESTMENT, Families.ROMANCE,
    )

    fun evaluate(
        msg: NormalizedMessage,
        sender: SenderInfo,
        patternMatches: List<PatternMatcher.Match>,
        linkSignals: List<LinkAnalyzer.LinkSignal>,
    ): List<ComboResult> {
        val results = mutableListOf<ComboResult>()
        val text = msg.normalizedText
        val families = patternMatches.map { it.pattern.family }.toSet()
        val hasLink = msg.urls.isNotEmpty()
        val hasAmount = msg.amounts.isNotEmpty()
        val registered = SenderAnalyzer.isRegisteredHeader(sender)
        val unknownSender = !sender.isContact && !registered

        // C1: money amount + any link + urgency word, sender unknown
        if (hasAmount && hasLink && URGENCY.containsMatchIn(text) && unknownSender) {
            results += ComboResult("C1", "Money amount + link + urgency from an unknown sender", Category.SPAM, dangerous = true)
        }
        // C2: won/prize/lottery + amount (link not even needed)
        if (PRIZE_WORDS.containsMatchIn(text) && hasAmount) {
            results += ComboResult("C2", "Prize/lottery claim with a money amount", Category.SPAM, dangerous = true)
        }
        // C3: account-threat + link or callback number, sender not registered
        if (ACCOUNT_THREAT.containsMatchIn(text) &&
            Regex("""(?i)\b(account|a/c|card|kyc|upi|wallet|sim|number|net ?banking)\b""").containsMatchIn(text) &&
            (hasLink || msg.phoneNumbers.isNotEmpty()) && !registered && !sender.isContact
        ) {
            results += ComboResult("C3", "Account threat + link/callback from an unregistered sender", Category.SPAM, dangerous = true)
        }
        // C4: shortener or suspicious-TLD link + KYC/refund/deposited/prize/loan/job hook
        if (linkSignals.any { it.isShortener || it.isSuspiciousTld } && SCAM_HOOK.containsMatchIn(text)) {
            results += ComboResult("C4", "Suspicious link + scam hook word (KYC/refund/prize/loan/job)", Category.SPAM, dangerous = true)
        }
        // C5: 10-digit personal number sender + promo match + link
        if (sender.type == SenderType.PERSONAL_NUMBER && Families.PROMO in families && hasLink) {
            results += ComboResult("C5", "Promotional content with link from a personal mobile number", Category.SPAM, dangerous = false)
        }
        // C6: brand name in text + non-official domain link
        if (linkSignals.any { it.isBrandImpersonation }) {
            results += ComboResult("C6", "Brand impersonation link", Category.SPAM, dangerous = true)
        }
        // C7: enter/share PIN|OTP|CVV|password + anything
        if (PIN_ASK.containsMatchIn(text)) {
            results += ComboResult("C7", "Asks you to share a PIN/OTP/CVV/password — always fraud", Category.SPAM, dangerous = true)
        }
        // C8: international sender + money/job/prize family
        if (sender.type == SenderType.INTERNATIONAL_NUMBER &&
            (families.any { it in MONEY_JOB_PRIZE_FAMILIES } || (hasAmount && SCAM_HOOK.containsMatchIn(text)))
        ) {
            results += ComboResult("C8", "International number pushing money/job/prize content", Category.SPAM, dangerous = true)
        }
        // C9: APK link + anything
        if (linkSignals.any { it.isApk }) {
            results += ComboResult("C9", "Direct APK download link — malware distribution", Category.SPAM, dangerous = true)
        }
        // C10: ≥3 distinct promo matches, no protected match → Promotions minimum
        val promoCount = patternMatches.count { it.pattern.family == Families.PROMO }
        if (promoCount >= 3 && families.none { it in Families.PROTECTED }) {
            results += ComboResult("C10", "Multiple promotional patterns", Category.PROMOTIONS, dangerous = false)
        }
        return results
    }
}
