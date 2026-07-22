package com.messages.protection

/**
 * Verified-sender badges (work-order Phase 2). Pure eligibility logic on top
 * of [SenderAnalyzer]'s sender-type classification — the UI layers render
 * whatever this returns and never re-detect anything themselves.
 *
 * No server exists, so this is strictly offline evidence: DLT registration
 * is carried in the sender ID itself (India's mandated header format), and
 * an alphanumeric header at least proves a business SMS route rather than a
 * personal SIM. Personal numbers and saved contacts get nothing — we cannot
 * verify who they are, and contacts need no trust chrome.
 *
 * ABSOLUTE RULE: when the sender's current message triggered a fraud combo
 * or a Dangerous verdict (incl. the fake-OTP fraud-warning banner), no badge
 * renders anywhere — a scam impersonating a bank must never borrow trust.
 */
object SenderBadges {

    enum class Badge { VERIFIED, BUSINESS }

    /** Protected-lane labels that elevate an unregistered alpha header to
     *  VERIFIED (§5.7 bank/courier/govt lanes ride only trusted routes). */
    private val VERIFYING_LABELS = setOf("BANK", "DELIVERY", "GOV")

    /**
     * @param address raw sender address (single recipient; groups get none).
     * @param isContact saved contacts get no badge.
     * @param dangerous the CURRENT message hit a fraud combo / Dangerous
     *   verdict / fraud-warning banner — absolute suppression.
     * @param protectedLabel the current message's protected-lane label
     *   ("BANK"/"DELIVERY"/"GOV"/…) or null/"NONE" — elevates an otherwise
     *   unregistered alphanumeric header to VERIFIED.
     */
    fun badgeFor(
        address: String,
        isContact: Boolean,
        dangerous: Boolean,
        protectedLabel: String? = null,
    ): Badge? {
        if (dangerous) return null
        if (isContact) return null
        if (address.isBlank() || address.contains(';')) return null
        return when (SenderAnalyzer.analyze(address, isContact = false).type) {
            // DLT -S/-T suffixes and the plain registered-header format.
            SenderType.REGISTERED_TRANSACTIONAL,
            // DLT -G government suffix.
            SenderType.REGISTERED_GOVERNMENT,
            -> Badge.VERIFIED

            // DLT -P: registered, but a marketing route — business, not
            // "verified" trust chrome.
            SenderType.REGISTERED_PROMOTIONAL -> Badge.BUSINESS

            SenderType.ALPHANUMERIC_UNKNOWN -> when {
                // The classifier's else-branch also catches odd numeric
                // formats — those are numbers, not business headers.
                address.none { it.isLetter() } -> null
                protectedLabel?.uppercase() in VERIFYING_LABELS -> Badge.VERIFIED
                else -> Badge.BUSINESS
            }

            // Personal / international / short codes / email gateways:
            // nothing — offline name verification is impossible (no server).
            else -> null
        }
    }

    /** One-line explanation for the badge tap sheet. */
    fun explanation(badge: Badge): String = when (badge) {
        Badge.VERIFIED -> "Registered business sender (DLT header)"
        Badge.BUSINESS -> "Business sender (alphanumeric ID) — not a personal number"
    }
}
