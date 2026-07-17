package com.personal.detectivedialer.data.sms

/**
 * Repliability of an SMS sender address.
 *
 * A "reply" only reaches a human when the sender is a real phone number. Two
 * classes of sender can't be replied to, mirroring how Google Messages treats
 * business/promotional threads:
 *   - Alphanumeric A2P headers ("AD-HDFCBK", "AX-HDFCBK-P", "VM-AMAZON") — bank/
 *     OTP/promo sender IDs. Replies are silently dropped by the operator.
 *   - Numeric short codes ("121", "56161", "1909") — shorter than a real mobile
 *     number; also non-routable for a normal reply.
 *
 * Pure Kotlin (no Android deps) so it is unit-testable on the JVM. Reuses
 * [TraiSuffix.isA2pHeader] for the alphanumeric test so the two stay consistent.
 */
object SmsAddress {

    /**
     * Minimum digit count for a numeric address we treat as repliable.
     *
     * Real mobile numbers are 10 digits (India) — 12–13 with a +country code;
     * international numbers are longer still. Numeric promo/OTP short codes are
     * typically 3–6 digits. 7 sits comfortably above the short-code band and well
     * below any real mobile, so short codes are excluded and genuine numbers kept.
     *
     * NOTE: chosen defensively (bias toward *allowing* reply, since wrongly
     * blocking a real conversation is the worse failure) and validated only
     * against the theoretical pattern — I had no device inbox to sample the
     * user's real senders against. Revisit if a real short code ≥ 7 digits or a
     * legitimate mobile < 7 digits (some countries) shows up in testing.
     */
    private const val MIN_REPLIABLE_DIGITS = 7

    /** True when a reply to [address] can actually reach someone. */
    fun isRepliable(address: String?): Boolean {
        val s = (address ?: "").trim()
        if (s.isEmpty()) return false
        // Any letters → alphanumeric A2P header, never repliable.
        if (TraiSuffix.isA2pHeader(s)) return false
        // Numeric: repliable only if long enough to be a real number, not a short code.
        return s.count { it.isDigit() } >= MIN_REPLIABLE_DIGITS
    }
}
