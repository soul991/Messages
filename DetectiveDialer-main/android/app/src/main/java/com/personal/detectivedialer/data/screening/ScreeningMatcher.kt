package com.personal.detectivedialer.data.screening

/**
 * A local screening rule: numbers whose domestic form starts with [prefix] get
 * [decision] (ALLOW | REJECT | SPAM) and [action] (RING | VOICEMAIL | REJECT),
 * with [label] as the history reason. Serialized as-is by Moshi (reflection) —
 * the backend serves the same shape from GET /api/screening-rules. [action] is
 * blank on a legacy backend; use [ScreeningMatcher.actionFor] to fill it.
 */
data class PrefixRule(
    val prefix: String = "",
    val decision: String = "",
    val action: String = "",
    val label: String = "",
)

data class ScreeningRulesResponse(val rules: List<PrefixRule> = emptyList())

/**
 * Deterministic, offline screening tier. Pure Kotlin (no Android deps) so it
 * is unit-testable on the JVM. Mirrors backend/src/services/classifier.js —
 * keep the two in sync.
 */
object ScreeningMatcher {

    /**
     * Built-in TRAI number-series rules. TRAI mandates registered promotional
     * calls originate from the 140 series and transactional/service calls from
     * the 160 series, so the prefix alone is a verdict. Refreshed from the
     * backend at runtime; these are the offline defaults.
     */
    val DEFAULT_RULES = listOf(
        PrefixRule("140", "SPAM", "VOICEMAIL", "TRAI 140-series promotional caller"),
        PrefixRule("160", "ALLOW", "RING", "TRAI 160-series transactional caller"),
    )

    /**
     * Policy actions the app branches on. Mirrors backend classifier.js ACTIONS.
     *   RING      → ring through, bypass any screening delay
     *   VOICEMAIL → don't ring, divert to the carrier's voicemail box
     *   REJECT    → drop silently, no ring and no voicemail
     */
    const val ACTION_RING = "RING"
    const val ACTION_VOICEMAIL = "VOICEMAIL"
    const val ACTION_REJECT = "REJECT"

    /**
     * Safe policy action for a decision when none was provided (legacy backend or
     * an unrecognized value). Mirrors backend classifier.js actionForDecision:
     * ALLOW→RING, SPAM→VOICEMAIL, REJECT→REJECT.
     */
    fun actionFor(decision: String?, action: String? = null): String {
        val a = action?.trim()?.uppercase()
        if (a == ACTION_RING || a == ACTION_VOICEMAIL || a == ACTION_REJECT) return a
        return when (decision?.trim()?.uppercase()) {
            "SPAM" -> ACTION_VOICEMAIL
            "REJECT" -> ACTION_REJECT
            else -> ACTION_RING
        }
    }

    /**
     * Reduce an Indian number to its domestic form (no +91/0091/trunk-0), or
     * null when the number is not recognizably domestic. Short codes and bare
     * 10-digit subscriber numbers are domestic.
     */
    fun domesticForm(raw: String?): String? {
        val number = (raw ?: "")
            .replace(Regex("[^\\d+]"), "")
            .replace(Regex("(?!^)\\+"), "")
        if (number.isEmpty()) return null
        return when {
            number.startsWith("+") ->
                if (number.startsWith("+91") && number.length == 13) number.substring(3) else null
            number.startsWith("0091") ->
                if (number.length == 14) number.substring(4) else null
            number.startsWith("00") -> null // international dialing prefix
            number.startsWith("0") -> number.substring(1).ifEmpty { null } // national trunk prefix
            number.length == 12 && number.startsWith("91") ->
                number.substring(2) // some carriers deliver 91XXXXXXXXXX without the +
            number.length <= 10 -> number // 10-digit subscriber or short code
            else -> null
        }
    }

    fun isDomestic(raw: String?): Boolean = domesticForm(raw) != null

    /** First rule matching the number's domestic form, or null. */
    fun matchPrefix(raw: String?, rules: List<PrefixRule> = DEFAULT_RULES): PrefixRule? {
        val domestic = domesticForm(raw) ?: return null
        return rules.firstOrNull { it.prefix.isNotEmpty() && domestic.startsWith(it.prefix) }
    }
}
