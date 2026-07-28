package com.messages.protection

import kotlinx.serialization.json.Json

/**
 * Stage 4 — Pattern matching over `normalizedText`.
 *
 * Compiles every library regex once at load time and caches it; matching a
 * message is a linear scan over compiled patterns (string ops only, well
 * inside the < 50 ms budget).
 *
 * R-21: matching runs against a [BudgetedCharSequence], so a pattern that
 * backtracks pathologically is abandoned after a bounded amount of work rather
 * than stalling message intake for every message that follows. Imported packs
 * are additionally screened by [PatternPackPolicy] before they get this far.
 */
class PatternMatcher(val library: PatternLibrary) {

    data class Match(val pattern: Pattern)

    private val compiled: List<Pair<Pattern, Regex>> = library.patterns.map { p ->
        p to Regex(p.regex, setOf(RegexOption.IGNORE_CASE))
    }

    fun matchAll(msg: NormalizedMessage): List<Match> =
        compiled.mapNotNull { (pattern, regex) ->
            if (regex.containsMatchWithin(msg.normalizedText)) Match(pattern) else null
        }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        /**
         * @param validate apply [PatternPackPolicy] before accepting. Off for
         * the bundled library — it ships with the app and is covered by
         * `BundledPackPolicyTest` instead, so we don't pay the scan on every
         * cold start. Must be **on** for anything the user imports.
         */
        fun fromJson(text: String, validate: Boolean = false): PatternMatcher {
            val library = json.decodeFromString(PatternLibrary.serializer(), text)
            if (validate) PatternPackPolicy.validate(library)
            return PatternMatcher(library)
        }
    }
}
