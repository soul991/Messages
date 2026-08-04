package com.messages.protection

/**
 * R-21 — the gate every untrusted regex passes before it is allowed anywhere
 * near message intake.
 *
 * Imported pattern packs and user-authored custom rules are attacker-reachable:
 * a pack arrives as a file the user was talked into opening, and a rule can be
 * pasted from a "tips" post. Java's `java.util.regex` is a backtracking engine,
 * so a pattern like `(a+)+$` against a long line takes exponential time. That
 * does not merely stall one message — [ProtectionEngine] classifies on the
 * intake path, so a poisoned pattern stalls *every* future message, and the
 * work is redone on each one.
 *
 * Two independent defences, because neither is sufficient alone:
 *
 *  1. **This policy — reject the shapes that make blowup possible.** Static
 *     rejection is cheap and catches the classic constructions.
 *  2. **[BudgetedCharSequence] — bound the work at match time.** Static analysis
 *     of regex complexity is undecidable in general, so we also cap how many
 *     character reads a single match may perform. This is what actually makes
 *     the guarantee hold: coroutine cancellation cannot interrupt a regex, but
 *     a `CharSequence` that refuses to be read further can.
 */
object SafeRegexPolicy {

    /** Longest accepted pattern source. The bundled library's longest is 291. */
    const val MAX_REGEX_LENGTH = 512

    /** Largest accepted `{n,m}` bound; beyond this, expansion gets expensive. */
    const val MAX_REPETITION = 1_000

    /** Deepest accepted group nesting. */
    const val MAX_GROUP_DEPTH = 12

    class Rejected(message: String) : IllegalArgumentException(message)

    /** True when [regex] is safe to compile and run against untrusted input. */
    fun accepts(regex: String): Boolean = runCatching { requireAccepted(regex) }.isSuccess

    /**
     * Throws [Rejected] with a user-showable reason if [regex] uses a construct
     * we refuse to run, or if it fails to compile at all.
     */
    fun requireAccepted(regex: String) {
        if (regex.isEmpty()) throw Rejected("Pattern is empty")
        if (regex.length > MAX_REGEX_LENGTH) {
            throw Rejected("Pattern is longer than $MAX_REGEX_LENGTH characters")
        }
        scan(regex)
        // Compiling last means a pattern rejected above never reaches the
        // engine, and a structurally-fine but syntactically-invalid pattern
        // still gets a clear message instead of blowing up at match time.
        try {
            Regex(regex)
        } catch (e: Exception) {
            throw Rejected("Pattern is not valid: ${e.message}")
        }
    }

    /**
     * Single pass over the pattern source, tracking escape state, character
     * classes and group nesting.
     *
     * The rejection that matters is the last one: a group that contains a
     * quantifier and is itself repeated *more than once* — `(a+)+`, `(\d*)*`,
     * `((ab)+)*`, `(a+){2,50}`. That shape lets the engine split the same input
     * between the inner and outer quantifier in exponentially many ways, which
     * is the whole ReDoS family.
     *
     * An outer `?` is deliberately allowed: `(\s?(lakh|crore))?` matches at most
     * once, so there is nothing to split and no blowup. Refusing it would reject
     * a good third of the bundled library for no security gain.
     */
    private fun scan(regex: String) {
        var i = 0
        var inClass = false
        // Per open group: has a quantifier been seen at this nesting level?
        val quantifierInGroup = ArrayDeque<Boolean>()

        fun markQuantifierSeen() {
            if (quantifierInGroup.isNotEmpty()) {
                quantifierInGroup[quantifierInGroup.lastIndex] = true
            }
        }

        while (i < regex.length) {
            val c = regex[i]

            // --- escapes -------------------------------------------------
            if (c == '\\') {
                val next = regex.getOrNull(i + 1)
                    ?: throw Rejected("Pattern ends with a dangling backslash")
                if (next in '1'..'9') {
                    throw Rejected("Backreferences (\\$next) are not allowed in custom patterns")
                }
                if (next == 'k') {
                    throw Rejected("Named backreferences (\\k) are not allowed in custom patterns")
                }
                i += 2
                continue
            }

            // --- character classes ---------------------------------------
            if (inClass) {
                if (c == ']') inClass = false
                i++
                continue
            }
            if (c == '[') {
                inClass = true
                i++
                continue
            }

            // --- groups ---------------------------------------------------
            if (c == '(') {
                val kind = groupKindAt(regex, i)
                if (kind == "(?<=" || kind == "(?<!") {
                    throw Rejected("Lookbehind is not allowed in custom patterns")
                }
                quantifierInGroup.addLast(false)
                if (quantifierInGroup.size > MAX_GROUP_DEPTH) {
                    throw Rejected("Pattern nests groups more than $MAX_GROUP_DEPTH deep")
                }
                i += kind?.length ?: 1
                continue
            }

            if (c == ')') {
                if (quantifierInGroup.isEmpty()) throw Rejected("Unbalanced ')' in pattern")
                val innerHadQuantifier = quantifierInGroup.removeLast()
                val quantifier = quantifierAt(regex, i + 1)
                if (quantifier != null) {
                    if (innerHadQuantifier && quantifier.expanding) {
                        throw Rejected(
                            "Pattern repeats a group that already repeats — this can hang " +
                                "the filter on some messages"
                        )
                    }
                    markQuantifierSeen()
                    i += 1 + quantifier.length
                    continue
                }
                // A group that contained a quantifier still counts as
                // "quantified content" for its parent, so `((a+))+` is caught.
                if (innerHadQuantifier) markQuantifierSeen()
                i++
                continue
            }

            // --- quantifiers ----------------------------------------------
            val quantifier = quantifierAt(regex, i)
            if (quantifier != null) {
                markQuantifierSeen()
                i += quantifier.length
                continue
            }

            i++
        }

        if (inClass) throw Rejected("Unterminated character class in pattern")
        if (quantifierInGroup.isNotEmpty()) throw Rejected("Unbalanced '(' in pattern")
    }

    /**
     * The group-opening token at [at] — `(`, `(?:`, `(?=`, `(?<=`, `(?<name>` …
     * Returned so the caller can skip it whole; a `?` inside it must never be
     * mistaken for a quantifier.
     */
    private fun groupKindAt(regex: String, at: Int): String? {
        if (regex.getOrNull(at + 1) != '?') return null
        val third = regex.getOrNull(at + 2) ?: return "(?"
        return when {
            third == '<' -> when (regex.getOrNull(at + 3)) {
                '=' -> "(?<="
                '!' -> "(?<!"
                // Named group `(?<name>` — skip through the closing '>'.
                else -> {
                    val end = regex.indexOf('>', at + 3)
                    if (end < 0) "(?<" else regex.substring(at, end + 1)
                }
            }
            else -> "(?$third"
        }
    }

    /**
     * A quantifier token.
     *
     * @param length characters consumed by the token.
     * @param expanding whether it can match its operand MORE THAN ONCE. `*`,
     * `+` and `{2,}` are expanding; `?`, `{0,1}` and `{1}` are not. Only an
     * expanding quantifier wrapped around already-quantified content creates
     * the exponential split that makes ReDoS possible.
     */
    private class Quantifier(val length: Int, val expanding: Boolean)

    /**
     * The quantifier starting at [at], or null if there is none. Handles `*`,
     * `+`, `?`, `{n}`, `{n,}`, `{n,m}` and a trailing lazy `?` / possessive `+`.
     */
    private fun quantifierAt(regex: String, at: Int): Quantifier? {
        val c = regex.getOrNull(at) ?: return null
        var length: Int
        var expanding: Boolean
        when (c) {
            '*', '+' -> {
                length = 1
                expanding = true
            }
            '?' -> {
                length = 1
                expanding = false
            }
            '{' -> {
                val close = regex.indexOf('}', at + 1)
                if (close < 0) return null
                val body = regex.substring(at + 1, close)
                if (body.isEmpty() || !body.all { it.isDigit() || it == ',' }) return null
                val bounds = body.split(',')
                bounds.forEach { part ->
                    val bound = part.trim().toIntOrNull() ?: return@forEach
                    if (bound > MAX_REPETITION) {
                        throw Rejected("Pattern repeats more than $MAX_REPETITION times")
                    }
                }
                // `{n}` / `{n,m}` expands when the upper bound exceeds 1, and
                // an open `{n,}` always does.
                val upper = when {
                    bounds.size == 1 -> bounds[0].trim().toIntOrNull()
                    bounds[1].isBlank() -> Int.MAX_VALUE
                    else -> bounds[1].trim().toIntOrNull()
                }
                length = close - at + 1
                expanding = (upper ?: Int.MAX_VALUE) > 1
            }
            else -> return null
        }
        // Lazy (`*?`) or possessive (`*+`) suffix. Laziness changes match order,
        // not the size of the search space, so `expanding` is unaffected.
        if (regex.getOrNull(at + length) == '?' || regex.getOrNull(at + length) == '+') length++
        return Quantifier(length, expanding)
    }
}

/**
 * A [CharSequence] that refuses to be read more than [maxReads] times.
 *
 * This is the runtime half of the R-21 defence. `java.util.regex` reads the
 * input through [get] as it backtracks, so a pattern that would otherwise spin
 * for minutes instead throws [Budget] after a bounded amount of work. Nothing
 * else can stop it: the matcher does not check interrupts, so neither thread
 * interruption nor coroutine cancellation can end a runaway match.
 *
 * [maxReads] is deliberately generous — it is a circuit breaker for pathological
 * patterns, not a tight budget for ordinary ones. A linear scan of a 1 000-char
 * SMS over 122 patterns reads far less than this.
 */
class BudgetedCharSequence(
    private val delegate: CharSequence,
    private val maxReads: Long = DEFAULT_MAX_READS,
) : CharSequence {

    class Budget(message: String) : RuntimeException(message)

    private var reads = 0L

    override val length: Int get() = delegate.length

    override fun get(index: Int): Char {
        if (++reads > maxReads) {
            throw Budget("Pattern matching exceeded its budget of $maxReads character reads")
        }
        return delegate[index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence =
        delegate.subSequence(startIndex, endIndex)

    override fun toString(): String = delegate.toString()

    companion object {
        const val DEFAULT_MAX_READS = 2_000_000L
    }
}

/**
 * `containsMatchIn` with a work budget. Returns false — never throws — when the
 * budget is exhausted: a pattern that cannot be evaluated in reasonable time is
 * treated as "did not match", because a filter outage must never swallow a
 * message. Same principle as [ProtectionEngine]'s classify-or-inbox fallback.
 */
fun Regex.containsMatchWithin(input: CharSequence, maxReads: Long = BudgetedCharSequence.DEFAULT_MAX_READS): Boolean =
    try {
        containsMatchIn(BudgetedCharSequence(input, maxReads))
    } catch (_: BudgetedCharSequence.Budget) {
        false
    }
