package com.messages.protection

/**
 * R-21 — bounds for an imported pattern pack.
 *
 * A pack is untrusted input: the user picks a file, and nothing about it is
 * signed or vouched for. Before R-21 the only check was "does it parse and
 * contain at least one pattern", which let a pack exhaust heap (millions of
 * entries), fill storage (the pack is persisted verbatim), or install a
 * catastrophically backtracking regex on the intake path.
 *
 * Every limit below is at least 4× the bundled library's actual usage, so a
 * legitimate pack has room to grow without anyone revisiting these numbers.
 * Bundled today: 122 patterns, longest regex 291 chars, 67 KB total.
 */
object PatternPackPolicy {

    /** Largest accepted pack file. The bundled library is ~67 KB. */
    const val MAX_PACK_BYTES = 1 shl 20 // 1 MiB

    const val MAX_PATTERNS = 500
    const val MAX_ID_LENGTH = 80
    const val MAX_FAMILY_LENGTH = 64
    const val MAX_DESCRIPTION_LENGTH = 240
    const val MAX_EXAMPLES = 10
    const val MAX_EXAMPLE_LENGTH = 512
    const val MAX_LANGUAGES = 8
    const val MAX_LANGUAGE_LENGTH = 16
    const val MAX_WEIGHT = 100

    class Rejected(message: String) : IllegalArgumentException(message)

    private fun require(condition: Boolean, message: () -> String) {
        if (!condition) throw Rejected(message())
    }

    /**
     * Validates a parsed pack. Throws [Rejected] with a message fit to show in
     * the import status line.
     *
     * Note this runs *after* JSON parsing, so it cannot defend against a huge
     * file on its own — the byte cap has to be applied while reading. See
     * `readUtf8Limited` at the import call site.
     */
    fun validate(library: PatternLibrary) {
        require(library.version >= 1) { "Pack version must be 1 or greater" }
        require(library.patterns.isNotEmpty()) { "Pack contains no patterns" }
        require(library.patterns.size <= MAX_PATTERNS) {
            "Pack contains ${library.patterns.size} patterns; the limit is $MAX_PATTERNS"
        }

        val seen = HashSet<String>(library.patterns.size)
        library.patterns.forEach { pattern ->
            require(pattern.id.isNotBlank() && pattern.id.length <= MAX_ID_LENGTH) {
                "Pattern id must be 1-$MAX_ID_LENGTH characters: '${pattern.id.take(40)}'"
            }
            require(seen.add(pattern.id)) { "Duplicate pattern id: '${pattern.id}'" }
            require(pattern.family.isNotBlank() && pattern.family.length <= MAX_FAMILY_LENGTH) {
                "Pattern '${pattern.id}' has an invalid family"
            }
            require(pattern.weight in 0..MAX_WEIGHT) {
                "Pattern '${pattern.id}' has weight ${pattern.weight}; allowed range is 0..$MAX_WEIGHT"
            }
            require(pattern.description.length <= MAX_DESCRIPTION_LENGTH) {
                "Pattern '${pattern.id}' has an over-long description"
            }
            require(pattern.languages.size <= MAX_LANGUAGES) {
                "Pattern '${pattern.id}' lists too many languages"
            }
            require(pattern.languages.all { it.length <= MAX_LANGUAGE_LENGTH }) {
                "Pattern '${pattern.id}' has an invalid language tag"
            }
            require(pattern.examples.size <= MAX_EXAMPLES && pattern.negativeExamples.size <= MAX_EXAMPLES) {
                "Pattern '${pattern.id}' has more than $MAX_EXAMPLES examples"
            }
            require(
                pattern.examples.all { it.length <= MAX_EXAMPLE_LENGTH } &&
                    pattern.negativeExamples.all { it.length <= MAX_EXAMPLE_LENGTH }
            ) {
                "Pattern '${pattern.id}' has an over-long example"
            }
            try {
                SafeRegexPolicy.requireAccepted(pattern.regex)
            } catch (e: SafeRegexPolicy.Rejected) {
                throw Rejected("Pattern '${pattern.id}' was refused: ${e.message}")
            }
        }
    }
}
