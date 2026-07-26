package com.messages.protection

/**
 * Stage 3 — Sender analysis.
 *
 * Classifies the raw sender address into a [SenderType] and exposes the
 * context multipliers the scoring engine applies per pattern family.
 */
object SenderAnalyzer {

    // Indian DLT header: 2-letter operator/circle prefix + 6-char entity ID,
    // e.g. AX-BANKXX or AX-BANKXX-S with the category suffix.
    private val DLT_HEADER = Regex("""^[A-Z]{2}-[A-Z0-9]{3,9}(-[SPTG])?$""", RegexOption.IGNORE_CASE)
    private val ALPHA_HEADER = Regex("""^(?=.*[A-Za-z])[A-Za-z0-9\-_. ]{2,15}$""")
    private val INDIAN_MOBILE = Regex("""^(?:\+?91)?[6-9]\d{9}$""")
    private val SHORT_CODE = Regex("""^\d{4,6}$""")
    private val INTERNATIONAL = Regex("""^\+(?!91)\d{7,15}$""")

    fun analyze(
        rawAddress: String,
        isContact: Boolean,
        reputationScore: Int = 0,
        homeCountryCode: String = "+91",
        firstContact: Boolean = false,
    ): SenderInfo {
        val addr = rawAddress.trim()
        val type = when {
            isContact -> SenderType.SAVED_CONTACT
            addr.contains('@') -> SenderType.EMAIL_GATEWAY
            dltSuffix(addr) == 'P' -> SenderType.REGISTERED_PROMOTIONAL
            dltSuffix(addr) == 'G' -> SenderType.REGISTERED_GOVERNMENT
            dltSuffix(addr) in listOf('S', 'T') -> SenderType.REGISTERED_TRANSACTIONAL
            DLT_HEADER.matches(addr) -> SenderType.REGISTERED_TRANSACTIONAL
            SHORT_CODE.matches(addr) -> SenderType.SHORT_CODE
            INTERNATIONAL.matches(addr.replace(" ", "")) &&
                !addr.replace(" ", "").startsWith(homeCountryCode) -> SenderType.INTERNATIONAL_NUMBER
            INDIAN_MOBILE.matches(addr.replace(Regex("[\\s-]"), "")) -> SenderType.PERSONAL_NUMBER
            ALPHA_HEADER.matches(addr) -> SenderType.ALPHANUMERIC_UNKNOWN
            else -> SenderType.ALPHANUMERIC_UNKNOWN
        }
        return SenderInfo(addr, type, isContact, reputationScore, firstContact)
    }

    private fun dltSuffix(addr: String): Char? {
        val m = Regex("""-([SPTG])$""", RegexOption.IGNORE_CASE).find(addr.trim())
        return m?.groupValues?.get(1)?.uppercase()?.firstOrNull()
    }

    /** Multiplier applied to promo/scam pattern weights for this sender. */
    fun spamMultiplier(info: SenderInfo, family: String): Double {
        val base = when (info.type) {
            SenderType.PERSONAL_NUMBER ->
                if (family == Families.PROMO || family in Families.SCAM) 1.5 else 1.0
            SenderType.INTERNATIONAL_NUMBER ->
                if (family in Families.SCAM) 2.0 else 1.0
            SenderType.EMAIL_GATEWAY -> 1.5
            SenderType.REGISTERED_TRANSACTIONAL, SenderType.REGISTERED_GOVERNMENT -> 0.75
            else -> 1.0
        }
        // Local reputation: marked-spam-twice senders get heavier weights,
        // always-opened senders get a trust discount.
        val reputation = when {
            info.reputationScore <= -2 -> 1.5
            info.reputationScore >= 3 -> 0.5
            else -> 1.0
        }
        return base * reputation * firstContactMultiplier(info, family)
    }

    /**
     * Phase 4 item 21 (Truecaller rec B3): ×1.25 on SCAM-family weights when
     * the sender has zero prior messages in the local index. Never applies to
     * promo or protected families — a first newsletter must not be punished,
     * and Protected can't be filtered anyway. Never for saved contacts.
     */
    fun firstContactMultiplier(info: SenderInfo, family: String): Double =
        if (info.firstContact && !info.isContact && family in Families.SCAM) 1.25 else 1.0

    /** True for DLT-registered / known business headers (trust for Stage 2). */
    fun isRegisteredHeader(info: SenderInfo): Boolean = when (info.type) {
        SenderType.REGISTERED_TRANSACTIONAL,
        SenderType.REGISTERED_GOVERNMENT,
        SenderType.REGISTERED_PROMOTIONAL -> true
        else -> false
    }

    /**
     * Can this sender receive an SMS reply? Alphanumeric sender IDs (DLT
     * headers like `VM-BANKXX`, unregistered alpha headers, email gateways)
     * are one-way — the network cannot route a reply back, so the chat UI
     * hides the composer and notifications must not offer inline reply.
     * Numeric senders (personal, international, short codes) are replyable.
     * Group addresses are the caller's concern (check each recipient).
     */
    fun canReceiveReplies(rawAddress: String): Boolean =
        when (analyze(rawAddress, isContact = false).type) {
            SenderType.PERSONAL_NUMBER,
            SenderType.INTERNATIONAL_NUMBER,
            SenderType.SHORT_CODE -> true
            // Odd-but-numeric formats (10-digit long codes starting 1–5,
            // '+'-less international) bucket as ALPHANUMERIC_UNKNOWN for
            // scoring but are still routable destinations.
            else -> Regex("""^\+?\d{3,15}$""")
                .matches(rawAddress.trim().replace(Regex("""[\s\-()]"""), ""))
        }
}
