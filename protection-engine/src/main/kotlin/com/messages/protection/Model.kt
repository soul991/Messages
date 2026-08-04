package com.messages.protection

import kotlinx.serialization.Serializable

/** Output of Stage 0 normalization; input to every later stage. */
data class NormalizedMessage(
    val original: String,
    val normalizedText: String,
    val urls: List<String>,
    val phoneNumbers: List<String>,
    val amounts: List<String>,
    val digitRuns: List<String>,
)

/** Sender classification from Stage 3. */
enum class SenderType {
    SAVED_CONTACT,
    REGISTERED_TRANSACTIONAL,   // DLT suffix -S / -T
    REGISTERED_GOVERNMENT,      // DLT suffix -G
    REGISTERED_PROMOTIONAL,     // DLT suffix -P
    ALPHANUMERIC_UNKNOWN,       // alphanumeric header, no recognized suffix
    PERSONAL_NUMBER,            // random 10-digit mobile, not in contacts
    INTERNATIONAL_NUMBER,       // unexpected country code
    SHORT_CODE,                 // 4–6 digits
    EMAIL_GATEWAY,              // contains @
}

/** Everything the engine needs to know about the sender. */
data class SenderInfo(
    val rawAddress: String,
    val type: SenderType,
    val isContact: Boolean = false,
    /** Local reputation from the user's own actions. Negative = distrusted. */
    val reputationScore: Int = 0,
    /**
     * Phase 4 item 21 (Truecaller report rec B3): true when the local index
     * holds ZERO prior messages from this sender. A first-time sender pushing
     * scam-family content is riskier than a long-history one; scam-family
     * pattern weights get ×1.25. Deterministic, local, and explained in the
     * Why? screen ("First message from this sender").
     */
    val firstContact: Boolean = false,
)

/** Where a classified message lands. */
enum class Category { INBOX, TRANSACTIONS, PROMOTIONS, SPAM, REVIEW, BLOCKED }

/** Sub-labels for Protected messages (search chips, OTP auto-delete). */
enum class ProtectedLabel { OTP, BANK, DELIVERY, TRAVEL, BILL, GOVERNMENT, NONE }

/** Final engine output. Every field feeds the "Why?" screen. */
data class Verdict(
    val category: Category,
    val dangerous: Boolean = false,
    /** Inbox + phishing link + unregistered sender → warn instead of trust. */
    val fraudWarningBanner: Boolean = false,
    val protectedLabel: ProtectedLabel = ProtectedLabel.NONE,
    val score: Int = 0,
    val matchedPatternIds: List<String> = emptyList(),
    val matchedComboIds: List<String> = emptyList(),
    /** Human-readable one-liner per match, for the Why? screen. */
    val explanations: List<String> = emptyList(),
    val notify: Boolean = category == Category.INBOX || category == Category.TRANSACTIONS,
)

/** One entry in patterns.json. */
@Serializable
data class Pattern(
    val id: String,
    val family: String,
    val regex: String,
    val weight: Int,
    val languages: List<String> = listOf("en"),
    val description: String,
    val examples: List<String> = emptyList(),
    val negativeExamples: List<String> = emptyList(),
)

@Serializable
data class PatternLibrary(
    val version: Int,
    val patterns: List<Pattern>,
)

/** Pattern family names used across scoring and combo rules. */
object Families {
    const val PROMO = "promotional"
    const val LOTTERY = "lottery-prize"
    const val FAKE_PAYMENT = "fake-payment"
    const val KYC_THREAT = "kyc-account-threat"
    const val LOAN = "loan-fraud"
    const val JOB = "job-fraud"
    const val INVESTMENT = "investment-fraud"
    const val DELIVERY_SCAM = "delivery-customs-scam"
    const val IMPERSONATION = "impersonation-threat"
    const val SUBSCRIPTION = "subscription-trap"
    const val CHARITY = "charity-scam"
    const val ROMANCE = "romance-gift-scam"
    const val CHALLAN = "fastag-challan-scam"
    const val APP_DOWNLOAD = "fake-app-download"
    const val WRONG_NUMBER = "wrong-number-opener"
    const val SCREEN_SHARE = "screen-share-fraud"
    const val LINK = "link-format"
    const val FORMAT = "format-signal"
    const val PROTECTED_OTP = "protected-otp"
    const val PROTECTED_BANK = "protected-bank"
    const val PROTECTED_DELIVERY = "protected-delivery"
    const val PROTECTED_TRAVEL = "protected-travel"
    const val PROTECTED_GOV = "protected-government"
    const val PROTECTED_BILL = "protected-bill"

    val PROTECTED = setOf(
        PROTECTED_OTP, PROTECTED_BANK, PROTECTED_DELIVERY,
        PROTECTED_TRAVEL, PROTECTED_GOV, PROTECTED_BILL,
    )
    val SCAM = setOf(
        LOTTERY, FAKE_PAYMENT, KYC_THREAT, LOAN, JOB, INVESTMENT,
        DELIVERY_SCAM, IMPERSONATION, SUBSCRIPTION, CHARITY, ROMANCE,
        CHALLAN, APP_DOWNLOAD, WRONG_NUMBER, SCREEN_SHARE,
    )
}
