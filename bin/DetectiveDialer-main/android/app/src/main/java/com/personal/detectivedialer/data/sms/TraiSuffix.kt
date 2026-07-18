package com.personal.detectivedialer.data.sms

/**
 * TRAI sender-ID suffix parser (TCCCPR 2025, in force since May 2025).
 *
 * A2P sender IDs carry an operator-appended suffix naming the content class:
 *   -P promotional · -S service · -T transactional · -G government
 * e.g. "AX-HDFCBK-S" (older headers may look like "AX-HDFCBK" — no suffix).
 *
 * Pure Kotlin (no Android deps) so it is unit-testable on the JVM.
 */
enum class SmsCategory(val suffix: Char, val label: String) {
    PROMOTIONAL('P', "Promotional"),
    SERVICE('S', "Service"),
    TRANSACTIONAL('T', "Transactional"),
    GOVERNMENT('G', "Government"),
}

object TraiSuffix {

    /**
     * Category encoded in an A2P sender header's TRAI suffix, or null when the
     * sender is a phone number, has no suffix, or the suffix is malformed.
     *
     * Person-to-person senders (phone numbers) never carry a suffix — only
     * alphanumeric A2P headers do — so anything digit-only (with formatting or
     * a leading +) is rejected before suffix parsing.
     */
    fun categoryOf(sender: String?): SmsCategory? {
        val s = (sender ?: "").trim()
        if (s.length < 3) return null

        // Numeric-only senders (incl. +91…, spaces, dashes) are P2P numbers, not headers.
        if (s.replace(Regex("[\\s()-]"), "").matches(Regex("^\\+?\\d+$"))) return null

        // The suffix is the final single letter after the last separator.
        val sep = s.lastIndexOf('-')
        if (sep < 0 || sep != s.length - 2) return null
        val head = s.substring(0, sep)
        if (head.isBlank()) return null

        val c = s.last().uppercaseChar()
        return SmsCategory.entries.firstOrNull { it.suffix == c }
    }

    /** True when the sender is an A2P header (letters involved) rather than a phone number. */
    fun isA2pHeader(sender: String?): Boolean {
        val s = (sender ?: "").trim()
        if (s.isEmpty()) return false
        return !s.replace(Regex("[\\s()-]"), "").matches(Regex("^\\+?\\d+$"))
    }
}
