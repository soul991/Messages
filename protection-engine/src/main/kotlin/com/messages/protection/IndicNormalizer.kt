package com.messages.protection

/**
 * Layer 1 — Indic script folding, applied before Stage 0 normalization.
 *
 * Integration: one line in [Normalizer.normalize].
 *
 * ```kotlin
 * // was
 * var s = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFKC)
 * // becomes
 * var s = java.text.Normalizer.normalize(IndicNormalizer.fold(text), java.text.Normalizer.Form.NFKC)
 * ```
 *
 * `NormalizedMessage.original` still holds the untouched message — the fold
 * applies to the matching text only, exactly like every other normalization step.
 *
 * ## What it fixes, and why NFKC does not already fix it
 *
 * NFKC folds compatibility characters. Indic digits are **not** compatibility
 * variants of ASCII digits — `५` (U+096B) and `5` are distinct characters with
 * no equivalence relation between them, so NFKC leaves them alone. Meanwhile
 * every digit-dependent rule in the engine is ASCII-only: `\d` in Java's regex
 * defaults to `[0-9]`, so `protect-otp-code` (`\d{4,8}` near "otp"),
 * `DIGIT_RUN_REGEX`, `PHONE_REGEX` and `AMOUNT_REGEX` all miss an OTP written
 * `५५२३१०`.
 *
 * The danda `।` is the Indic full stop. Folding it to `.` is not cosmetic: it
 * makes sentence breaks visible to the `.{0,N}` proximity windows the pattern
 * library is built on, and it stops a danda from being treated as part of a word.
 *
 * ## Why this is a fold and not a translation
 *
 * Nothing here is language-aware. It is a character-level mapping over five
 * closed Unicode ranges — the same class of operation as the existing homoglyph
 * map, and just as reversible to reason about. There is no model, no lookup of
 * words, and no behaviour that depends on which language the message is in.
 *
 * ## Latin messages are bit-for-bit untouched
 *
 * [fold] scans for a character in one of the five ranges and returns the *same
 * String instance* when there is none. Every one of the 513 corpus entries is
 * pure Latin, so this is what makes the flag-off / regression proof exact rather
 * than approximate: for those messages the function is identity.
 */
object IndicNormalizer {

    /** Danda and double danda — the Indic sentence terminators. */
    private const val DANDA = '।'
    private const val DOUBLE_DANDA = '॥'

    /**
     * First code point of each script's digit block. Digits run contiguously
     * from `zero` to `zero + 9` in every Indic block, which is what makes this
     * an offset subtraction rather than a table.
     */
    private val DIGIT_ZEROS = intArrayOf(
        0x0966, // Devanagari ०
        0x09E6, // Bengali ০
        0x0A66, // Gurmukhi ੦
        0x0AE6, // Gujarati ૦
        0x0B66, // Odia ୦
        0x0BE6, // Tamil ௦
        0x0C66, // Telugu ౦
        0x0CE6, // Kannada ೦
        0x0D66, // Malayalam ൦
    )

    /** The ASCII digit for [c], or null when [c] is not an Indic digit. */
    private fun asciiDigit(c: Char): Char? {
        for (zero in DIGIT_ZEROS) {
            if (c.code in zero..(zero + 9)) return ('0' + (c.code - zero))
        }
        return null
    }

    private fun needsFolding(c: Char): Boolean =
        c == DANDA || c == DOUBLE_DANDA || asciiDigit(c) != null

    /**
     * Returns [text] with Indic digits mapped to ASCII and dandas mapped to
     * full stops. Returns the input unchanged — same instance — when there is
     * nothing to fold.
     */
    fun fold(text: String): String {
        var i = 0
        while (i < text.length) {
            if (needsFolding(text[i])) break
            i++
        }
        if (i == text.length) return text

        return buildString(text.length) {
            append(text, 0, i)
            while (i < text.length) {
                val c = text[i]
                when {
                    c == DANDA || c == DOUBLE_DANDA -> append('.')
                    else -> append(asciiDigit(c) ?: c)
                }
                i++
            }
        }
    }
}
