package com.messages.protection

/**
 * The deterministic protection pipeline (§3). First decisive stage wins.
 *
 * Pure Kotlin, no Android dependencies — fully unit-testable on the JVM.
 */
class ProtectionEngine(
    private var matcher: PatternMatcher,
    private var sensitivity: Sensitivity = Sensitivity.DEFAULT,
) {

    /** Tunable thresholds behind the Sensitivity slider (§3 Stage 5). */
    data class Sensitivity(
        val dangerousAt: Int = 15,
        val spamAt: Int = 10,
        val reviewAt: Int = 5,
    ) {
        companion object {
            val DEFAULT = Sensitivity()
            val RELAXED = Sensitivity(dangerousAt = 18, spamAt = 13, reviewAt = 7)
            val STRICT = Sensitivity(dangerousAt = 12, spamAt = 8, reviewAt = 4)
        }
    }

    /** Stage 1 user rules, evaluated in user-defined order. */
    data class UserRule(
        val id: Long,
        /** Regex applied to sender OR normalized text depending on [target]. */
        val pattern: String,
        val target: Target,
        val category: Category,
    ) {
        enum class Target { SENDER, TEXT }
        val compiled: Regex by lazy { Regex(pattern, RegexOption.IGNORE_CASE) }
    }

    data class Input(
        val body: String,
        val sender: SenderInfo,
        val allowListed: Boolean = false,
        val blockListed: Boolean = false,
        val userRules: List<UserRule> = emptyList(),
    )

    /** Hot-reload on pattern-pack import. */
    fun updateLibrary(newMatcher: PatternMatcher) {
        matcher = newMatcher
    }

    /** Hot-apply the Sensitivity slider (§3 Stage 5). */
    fun updateSensitivity(newSensitivity: Sensitivity) {
        sensitivity = newSensitivity
    }

    val libraryVersion: Int get() = matcher.library.version
    val patternCount: Int get() = matcher.library.patterns.size

    /** Pattern id → family, for stats grouping (protection dashboard §8.2). */
    val familiesByPatternId: Map<String, String>
        get() = matcher.library.patterns.associate { it.id to it.family }

    fun classify(input: Input): Verdict {
        // Stage 0 — normalization (always)
        val msg = Normalizer.normalize(input.body)
        val sender = input.sender

        // Stage 1 — user rules (absolute, top priority)
        if (input.allowListed) {
            return Verdict(Category.INBOX, explanations = listOf("Sender is on your allow list"), notify = true)
        }
        if (input.blockListed) {
            return Verdict(Category.BLOCKED, explanations = listOf("Sender is on your block list"), notify = false)
        }
        for (rule in input.userRules) {
            val target = if (rule.target == UserRule.Target.SENDER) sender.rawAddress else msg.normalizedText
            // R-21: budgeted match — a user rule is untrusted input too.
            if (rule.compiled.containsMatchWithin(target)) {
                return Verdict(
                    rule.category,
                    explanations = listOf("Matched your custom rule"),
                    notify = rule.category == Category.INBOX || rule.category == Category.TRANSACTIONS,
                )
            }
        }

        // Rule 4: Carrier spam warnings (e.g. "[SPAM]" or "[FRAUD]" prepended by carrier) -> directly to Spam
        val isCarrierSpam = Regex("""(?i)^\s*(\[(?:SPAM|POSSIBLE\s+SPAM|SUSPECTED\s+SPAM|FRAUD)\]|\((?:SPAM|FRAUD)\)|SPAM:\s*)""")
            .containsMatchIn(input.body) ||
            Regex("""(?i)^\s*(\[(?:SPAM|POSSIBLE\s+SPAM|SUSPECTED\s+SPAM|FRAUD)\]|\((?:SPAM|FRAUD)\)|SPAM:\s*)""")
                .containsMatchIn(msg.normalizedText)
        if (isCarrierSpam) {
            return Verdict(
                Category.SPAM,
                dangerous = true,
                score = 15,
                matchedPatternIds = listOf("carrier-spam-warning"),
                explanations = listOf("Carrier marked as spam/fraud"),
                notify = false,
            )
        }

        val patternMatches = matcher.matchAll(msg)
        val linkSignals = LinkAnalyzer.analyze(msg)
        val combos = ComboRules.evaluate(msg, sender, patternMatches, linkSignals)
        val fraudCombos = combos.filter { it.dangerous }

        // Rule 2: Any message from a saved contact or with user reply history MUST stay in Personal
        // unless a critical fraud combo (e.g. compromised account sending UPI drain) matches
        if ((sender.isContact || sender.hasUserReplyHistory) && fraudCombos.isEmpty()) {
            val expl = if (sender.isContact) "From a saved contact" else "From a conversation with your reply history"
            return Verdict(Category.INBOX, explanations = listOf(expl), notify = true)
        }

        val isUnknownSender = !sender.isContact && !sender.hasUserReplyHistory

        // Rule 5: Crypto/NFT/lottery keywords from unknown senders must route to Spam
        val isCryptoOrNft = Regex("""(?i)\b(crypto|bitcoin|btc|eth|ethereum|usdt|binance|airdrop|nft|web3|solana|coinbase|trust\s*wallet|metamask)\b""")
            .containsMatchIn(msg.normalizedText)
        val isLottery = Regex("""(?i)\b(lottery|lucky\s*draw|bumper\s*prize|cash\s*prize|kbc\s*(?:lottery|prize|winner)|won\s+(?:Rs\.?|INR|₹|\$)\s*[\d,]+|congratulations.*won.*(?:prize|cash|car|draw))\b""")
            .containsMatchIn(msg.normalizedText)
        if (isUnknownSender && (isCryptoOrNft || isLottery)) {
            val reason = if (isCryptoOrNft) "Crypto/NFT solicitation from unknown sender" else "Lottery/prize solicitation from unknown sender"
            val isDangerous = isLottery || linkSignals.any { it.isShortener || it.isPhishy } || fraudCombos.isNotEmpty()
            val allPatternIds = (patternMatches.map { it.pattern.id } + listOf(if (isCryptoOrNft) "crypto-nft-unknown" else "lottery-unknown")).distinct()
            val allExplanations = patternMatches.map { it.pattern.description } + reason
            return Verdict(
                Category.SPAM,
                dangerous = isDangerous,
                score = 15,
                matchedPatternIds = allPatternIds,
                explanations = allExplanations,
                notify = false,
            )
        }

        // Rule 3: Messages with urgency + links/phone numbers from unknown 10-digit numbers must route to Review or Spam, not Personal
        val isUnknown10Digit = isUnknownSender &&
            (sender.type == SenderType.PERSONAL_NUMBER || sender.type == SenderType.INTERNATIONAL_NUMBER ||
             sender.rawAddress.filter { it.isDigit() }.length >= 10)
        val urgencyRegex = Regex(
            """(?i)\b(urgent|urgently|immediately|action\s*required|account\s*(?:blocked|suspended|deactivated|restricted)|verify\s*(?:now|immediately|kyc|pan)|update\s*(?:kyc|pan|aadhaar)|within\s*\d+\s*(?:hours?|mins?|days?)|today\s*only|last\s*chance|final\s*notice|threat|expire[ds]?|suspended|terminate[d]?)\b"""
        )
        val hasUrgency = urgencyRegex.containsMatchIn(msg.normalizedText)
        val hasLink = msg.urls.isNotEmpty() || linkSignals.isNotEmpty()
        val hasPhoneAction = Regex("""(?i)\b(?:call|contact|reach|helpline|whatsapp)[:\s]*(\+?\d[\d\s-]{7,}\d)""").containsMatchIn(msg.normalizedText)

        if (isUnknown10Digit && hasUrgency && (hasLink || hasPhoneAction)) {
            val isSeverePhishing = linkSignals.any { it.isShortener || it.isPhishy } ||
                fraudCombos.isNotEmpty() ||
                patternMatches.any { it.pattern.family in Families.SCAM }
            val allPatternIds = (patternMatches.map { it.pattern.id } + listOf(if (isSeverePhishing) "urgent-link-numeric-spam" else "urgent-link-numeric-review")).distinct()
            val allExplanations = patternMatches.map { it.pattern.description } + if (isSeverePhishing) {
                "Urgent request with suspicious link from unknown 10-digit number"
            } else {
                "Urgent request with link/phone from unknown 10-digit number"
            }
            return if (isSeverePhishing) {
                Verdict(
                    Category.SPAM,
                    dangerous = true,
                    score = 15,
                    matchedPatternIds = allPatternIds,
                    explanations = allExplanations,
                    notify = false,
                )
            } else {
                Verdict(
                    Category.REVIEW,
                    score = 8,
                    matchedPatternIds = allPatternIds,
                    explanations = allExplanations,
                    notify = false,
                )
            }
        }

        // Stage 2 — Protected patterns (can NEVER be filtered).
        // §5.7: OTP protection is absolute; bank/delivery/bill/travel protection
        // applies from registered headers or contacts — a random number saying
        // "your parcel shipped, pay fee" must not ride the protected lane.
        val registeredOrContact = SenderAnalyzer.isRegisteredHeader(sender) ||
            sender.isContact || sender.type == SenderType.SHORT_CODE ||
            sender.type == SenderType.ALPHANUMERIC_UNKNOWN
        val protectedMatches = patternMatches.filter {
            it.pattern.family == Families.PROTECTED_OTP ||
                (it.pattern.family in Families.PROTECTED && registeredOrContact)
        }
        var protectedOverridden = false
        if (protectedMatches.isNotEmpty()) {
            // Registered-header abuse (real-world, 2026-07): DLT-registered
            // headers sending "Rs X credited — withdraw now: bit.ly/…" bait.
            // The bank-alert wording matches PROTECTED_BANK, but the message
            // also carries hard scam evidence. OTP protection stays absolute;
            // for the other protected families the lane is overridden ONLY
            // when a PHISHY link (shortener/suspicious TLD/impersonation/IP/
            // insecure) is present alongside a fraud combo or a scam-family
            // pattern match. Textual evidence alone (e.g. C7 firing on a
            // genuine "never share your OTP/PIN" footer) or an official-
            // domain link on a real alert never overrides.
            val otpProtected = protectedMatches.any { it.pattern.family == Families.PROTECTED_OTP }
            val phishyLink = linkSignals.any { it.isPhishy }
            val scamEvidence = fraudCombos.isNotEmpty() ||
                patternMatches.any { it.pattern.family in Families.SCAM }
            if (otpProtected || !phishyLink || !scamEvidence) {
                return protectedVerdict(msg, sender, protectedMatches, patternMatches, linkSignals)
            }
            // else: fall through to scoring; the scam evidence decides.
            protectedOverridden = true
        }

        // Rule 1 check: Transactional patterns from verified sender formats MUST file under Personal, NEVER under Promo
        val isVerifiedSenderFormat = SenderAnalyzer.isRegisteredHeader(sender) ||
            SenderAnalyzer.isDltCommercialHeader(sender) ||
            sender.type == SenderType.SHORT_CODE ||
            sender.type == SenderType.ALPHANUMERIC_UNKNOWN ||
            sender.type == SenderType.REGISTERED_TRANSACTIONAL ||
            sender.type == SenderType.REGISTERED_GOVERNMENT

        val transactionalKeywords = Regex(
            """(?i)\b(debited|credited|otp\b|one[\s-]*time\s*password|verification\s*code|ac\s*bal|account\s*balance|avail(?:able)?\s*bal(?:ance)?|bal:\s*(?:inr|rs\.?|₹)|order\s+placed|(?:order|booking|ticket|txn|transaction)\s*(?:of|for|id|#)?\s*(?:inr|rs\.?|₹|\d+)|\b(?:a/c|account)\b.*?\b(?:debited|credited|balance)\b|\b(?:debited|credited)\b.*?\b(?:inr|rs\.?|₹)\b|\b(?:inr|rs\.?|₹)\b[\d,]+.*?\b(?:debited|credited)\b)\b"""
        )
        val hasTransactionalPattern = patternMatches.any {
            it.pattern.family == Families.PROTECTED_OTP || it.pattern.family == Families.PROTECTED_BANK
        } || (isVerifiedSenderFormat && transactionalKeywords.containsMatchIn(msg.normalizedText))

        // Stage 4 — pattern scoring with sender-context multipliers
        var score = 0.0
        val explanations = mutableListOf<String>()
        val matchedIds = mutableListOf<String>()
        var firstContactApplied = false
        for (m in patternMatches) {
            val w = m.pattern.weight * SenderAnalyzer.spamMultiplier(sender, m.pattern.family)
            score += w
            matchedIds += m.pattern.id
            explanations += m.pattern.description
            if (SenderAnalyzer.firstContactMultiplier(sender, m.pattern.family) > 1.0) {
                firstContactApplied = true
            }
        }
        // Phase 4 item 21 (rec B3): surface the first-contact bump once, plainly.
        if (firstContactApplied) {
            matchedIds += "first-contact"
            explanations += "First message from this sender"
        }
        if (protectedOverridden) {
            matchedIds += "protected-lane-override"
            explanations += "Worded like a transaction alert, but scam signals outweigh it"
        }
        for (s in linkSignals) {
            score += s.weight
            matchedIds += s.id
            explanations += s.description
        }
        // Registered promotional header → Promotions by default (India mandates -P marking)
        if (sender.type == SenderType.REGISTERED_PROMOTIONAL) {
            score += 5.0
            matchedIds += "sender-dlt-p"
            explanations += "Sender uses a registered promotional (-P) header"
        } else if (sender.type == SenderType.COMMERCIAL_PROMOTIONAL) {
            score += 2.0
            matchedIds += "sender-commercial"
            explanations += "Sender is an identified commercial entity"
        }
        // "dear customer" from a numeric sender + threat/link (§5.4 last row)
        if (Regex("""(?i)dear (customer|user|consumer)""").containsMatchIn(msg.normalizedText) &&
            (sender.type == SenderType.PERSONAL_NUMBER || sender.type == SenderType.INTERNATIONAL_NUMBER) &&
            (msg.urls.isNotEmpty() || Regex("""(?i)(block|suspend|expir|verify|urgent)""").containsMatchIn(msg.normalizedText))
        ) {
            score += 4.0
            matchedIds += "dear-customer-numeric"
            explanations += "\"Dear customer\" from a personal number — banks use registered headers"
        }

        val comboIds = combos.map { it.id }
        combos.forEach { explanations += it.description }
        val finalScore = score.toInt()

        // Stage 5 — verdict thresholds
        // Fraud combo triggered OR score ≥ dangerous threshold
        if (fraudCombos.isNotEmpty() || finalScore >= sensitivity.dangerousAt) {
            return Verdict(
                Category.SPAM, dangerous = true, score = finalScore,
                matchedPatternIds = matchedIds, matchedComboIds = comboIds,
                explanations = explanations, notify = false,
            )
        }

        // Promotional routing: messages matching promo patterns from commercial DLT senders route to PROMOTIONS
        // Guard 1: Must be a DLT-formatted header (XX-NAME) or known suffix; plain alphanumeric names (e.g. "AIRTEL")
        // do not take this direct lane.
        // Guard 2: Shortener/phishy links disqualify from direct promo lane.
        // Guard 3: Rule 1 — Transactional patterns from verified senders MUST file under Personal, NEVER under Promo.
        val promoPatternIds = setOf("promo-offer-words", "promo-discount", "promo-sale", "promo-generic-offer")
        val matchesPromoPattern = patternMatches.any { it.pattern.id in promoPatternIds || it.pattern.family == Families.PROMO } ||
            Regex("""(?i)\b(offers?|discounts?|flat\s+\d+|cashbacks?|deals?|sales?|recharge|coupons?|vouchers?|save\s+\d+%?)\b""").containsMatchIn(msg.normalizedText)
        val isDltCommercial = SenderAnalyzer.isDltCommercialHeader(sender)
        val hasShortenerOrPhishyLink = linkSignals.any { it.isShortener || it.isPhishy }

        if (hasTransactionalPattern && isVerifiedSenderFormat && !hasShortenerOrPhishyLink) {
            return Verdict(
                Category.INBOX, score = finalScore, matchedPatternIds = matchedIds,
                matchedComboIds = comboIds, explanations = explanations + "Transactional message from verified sender — filed under Personal", notify = true,
            )
        }

        val transactionalGuard = Regex(
            """(?i)\b(recharge\s+(successful|done|confirmed|completed|processed)|payment\s+(successful|received|done|confirmed|processed)|order\s+(delivered|confirmed|shipped|dispatched|placed|cancelled)|booking\s+(confirmed|cancelled)|refund\s+(processed|initiated|credited|successful)|delivery\s+(confirmed|completed|scheduled|arriving|delivered|attempted)|pnr|ticket\s+(booked|confirmed|cancelled)|trip\s+(completed|receipt|summary)|successful(ly)?|credited|debited|confirmed)\b"""
        )
        val explicitOfferSignal = Regex(
            """(?i)\b(offers?|discounts?|cashbacks?|coupons?|sales?|flat\s+\d+\s*%?\s*off|save\s+\d+%?|deal|promo\s*code|voucher|free(?:bie)?|buy\s+\d+\s+get)\b"""
        )
        val isTransactionalBody = transactionalGuard.containsMatchIn(msg.normalizedText)
        val hasExplicitOffer = explicitOfferSignal.containsMatchIn(msg.normalizedText)

        if (matchesPromoPattern && isDltCommercial && !hasShortenerOrPhishyLink) {
            // If the body reads like a transactional notification (recharge success, order
            // delivered, trip receipt…), it must stay in INBOX and notify — unless it also
            // carries an explicit offer/discount/coupon, in which case it is still a promo.
            if (isTransactionalBody && !hasExplicitOffer) {
                return Verdict(
                    Category.INBOX, score = finalScore, matchedPatternIds = matchedIds,
                    matchedComboIds = comboIds, explanations = explanations + "Transactional message from commercial sender — not a promotion", notify = true,
                )
            }
            return Verdict(
                Category.PROMOTIONS, score = finalScore, matchedPatternIds = matchedIds,
                matchedComboIds = comboIds, explanations = explanations, notify = false,
            )
        }

        // Non-dangerous combos can still force a category (C5 → Spam, C10 → Promotions)
        combos.firstOrNull { it.category == Category.SPAM }?.let {
            return Verdict(
                Category.SPAM, score = finalScore, matchedPatternIds = matchedIds,
                matchedComboIds = comboIds, explanations = explanations, notify = false,
            )
        }
        if (finalScore >= sensitivity.spamAt) {
            return Verdict(
                Category.SPAM, score = finalScore, matchedPatternIds = matchedIds,
                matchedComboIds = comboIds, explanations = explanations, notify = false,
            )
        }

        val hasPromo = matchesPromoPattern ||
            combos.any { it.id == "C10" } || sender.type == SenderType.REGISTERED_PROMOTIONAL ||
            sender.type == SenderType.COMMERCIAL_PROMOTIONAL
        if (finalScore >= sensitivity.reviewAt) {
            return if (hasPromo && !hasShortenerOrPhishyLink) {
                Verdict(
                    Category.PROMOTIONS, score = finalScore, matchedPatternIds = matchedIds,
                    matchedComboIds = comboIds, explanations = explanations, notify = false,
                )
            } else {
                Verdict(
                    Category.REVIEW, score = finalScore, matchedPatternIds = matchedIds,
                    matchedComboIds = comboIds, explanations = explanations, notify = false,
                )
            }
        }
        // C10 fires even when the raw score stays under the review threshold
        if (combos.any { it.id == "C10" } && !hasShortenerOrPhishyLink) {
            return Verdict(
                Category.PROMOTIONS, score = finalScore, matchedPatternIds = matchedIds,
                matchedComboIds = comboIds, explanations = explanations, notify = false,
            )
        }

        // Transactional patterns → Transactions folder
        val transactional = patternMatches.filter { it.pattern.family == Families.PROTECTED_BANK }
        if (transactional.isNotEmpty()) {
            return Verdict(
                Category.TRANSACTIONS, protectedLabel = ProtectedLabel.BANK, score = finalScore,
                matchedPatternIds = matchedIds, explanations = explanations, notify = true,
            )
        }

        return Verdict(
            Category.INBOX, score = finalScore, matchedPatternIds = matchedIds,
            matchedComboIds = comboIds, explanations = explanations, notify = true,
        )
    }

    /**
     * Stage 2 verdict. Protected always reaches the Inbox — but a phishing
     * link from an unregistered sender adds a red fraud-warning banner
     * (fake-OTP phishing exists).
     */
    private fun protectedVerdict(
        msg: NormalizedMessage,
        sender: SenderInfo,
        protectedMatches: List<PatternMatcher.Match>,
        allMatches: List<PatternMatcher.Match>,
        linkSignals: List<LinkAnalyzer.LinkSignal>,
    ): Verdict {
        val label = when (protectedMatches.first().pattern.family) {
            Families.PROTECTED_OTP -> ProtectedLabel.OTP
            Families.PROTECTED_BANK -> ProtectedLabel.BANK
            Families.PROTECTED_DELIVERY -> ProtectedLabel.DELIVERY
            Families.PROTECTED_TRAVEL -> ProtectedLabel.TRAVEL
            Families.PROTECTED_GOV -> ProtectedLabel.GOVERNMENT
            Families.PROTECTED_BILL -> ProtectedLabel.BILL
            else -> ProtectedLabel.NONE
        }
        val phishy = linkSignals.any { it.isPhishy }
        val registered = SenderAnalyzer.isRegisteredHeader(sender) || sender.isContact
        val warn = phishy && !registered

        val category = when (label) {
            ProtectedLabel.BANK, ProtectedLabel.BILL -> Category.TRANSACTIONS
            else -> Category.INBOX
        }
        val ids = protectedMatches.map { it.pattern.id } +
            (if (warn) linkSignals.filter { it.isPhishy }.map { it.id } else emptyList())
        val expl = protectedMatches.map { it.pattern.description }.toMutableList()
        if (warn) {
            expl += "Contains a suspicious link from an unregistered sender — treat with caution"
            expl += linkSignals.filter { it.isPhishy }.map { it.description }
        }
        return Verdict(
            category = category,
            fraudWarningBanner = warn,
            protectedLabel = label,
            matchedPatternIds = ids,
            explanations = expl,
            notify = true,
        )
    }
}
