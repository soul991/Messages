package com.messages.protection

import kotlinx.serialization.json.Json

/**
 * Stage 4 — Pattern matching over `normalizedText`.
 *
 * Compiles every library regex once at load time and caches it; matching a
 * message is a linear scan over compiled patterns (string ops only, well
 * inside the < 50 ms budget).
 */
class PatternMatcher(val library: PatternLibrary) {

    data class Match(val pattern: Pattern)

    private val compiled: List<Pair<Pattern, Regex>> = library.patterns.map { p ->
        p to Regex(p.regex, setOf(RegexOption.IGNORE_CASE))
    }

    fun matchAll(msg: NormalizedMessage): List<Match> =
        compiled.mapNotNull { (pattern, regex) ->
            if (regex.containsMatchIn(msg.normalizedText)) Match(pattern) else null
        }

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun fromJson(text: String): PatternMatcher =
            PatternMatcher(json.decodeFromString(PatternLibrary.serializer(), text))
    }
}
