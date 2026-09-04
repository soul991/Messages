package com.messages.protection

/**
 * Stage 0 — Normalization.
 *
 * Produces the [NormalizedMessage] used by all later pipeline stages. Defeats
 * common obfuscation tricks: zero-width chars, homoglyphs, leet-speak,
 * separator spacing (F.R.E.E), and character repetition (FREEEEE).
 *
 * The original text is never modified — normalization output is used for
 * matching only.
 */
object Normalizer {

    /** Cyrillic/Greek look-alikes mapped to their Latin equivalents. */
    private val HOMOGLYPHS = mapOf(
        // Cyrillic
        'а' to 'a', 'е' to 'e', 'о' to 'o', 'с' to 'c', 'р' to 'p', 'х' to 'x',
        'ѕ' to 's', 'і' to 'i', 'ј' to 'j', 'у' to 'y', 'ԁ' to 'd', 'ɡ' to 'g',
        'һ' to 'h', 'к' to 'k', 'м' to 'm', 'т' to 't', 'в' to 'b', 'н' to 'h',
        'А' to 'a', 'Е' to 'e', 'О' to 'o', 'С' to 'c', 'Р' to 'p', 'Х' to 'x',
        'Ѕ' to 's', 'І' to 'i', 'Ј' to 'j', 'У' to 'y', 'К' to 'k', 'М' to 'm',
        'Т' to 't', 'В' to 'b', 'Н' to 'h',
        // Greek
        'α' to 'a', 'ο' to 'o', 'ε' to 'e', 'ι' to 'i', 'κ' to 'k', 'ν' to 'v',
        'ρ' to 'p', 'τ' to 't', 'υ' to 'u', 'χ' to 'x', 'Α' to 'a', 'Β' to 'b',
        'Ε' to 'e', 'Ζ' to 'z', 'Η' to 'h', 'Ι' to 'i', 'Κ' to 'k', 'Μ' to 'm',
        'Ν' to 'n', 'Ο' to 'o', 'Ρ' to 'p', 'Τ' to 't', 'Υ' to 'y', 'Χ' to 'x',
    )

    /** Leet-speak map, applied only inside alphabetic words (see [deLeet]). */
    private val LEET = mapOf(
        '0' to 'o', '1' to 'i', '3' to 'e', '4' to 'a', '5' to 's',
        '7' to 't', '8' to 'b', '@' to 'a', '$' to 's', '!' to 'i',
    )

    private val ZERO_WIDTH = Regex("[\\u200B-\\u200D\\uFEFF\\u2060\\u00AD]")
    // Scheme-less domains are matched by listing labels then a known TLD directly.
    // Never use lookbehind with * or + here: Android's ICU regex only accepts
    // bounded-length lookbehind and throws PatternSyntaxException at compile
    // time (OpenJDK, where the JVM tests run, accepts it — so tests can't catch it).
    private val URL_REGEX = Regex(
        """(?i)\b(?:https?://|www\.)\S+|\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}(?:/\S*)?|\b(?:[a-z0-9][a-z0-9-]*\.)+(?:com|in|net|org|io|co|me|ly|gl|xyz|top|club|online|site|buzz|icu|vip|rest|click|link|work|loan|men|cyou|cfd|sbs|quest|monster|lol|pw|cc|tk|ml|ga|gq|app|dev|info|biz|shop|store|live|life|apk|to|id|at|gy|gd|ru|li)\b(?:/\S*)?"""
    )
    private val PHONE_REGEX = Regex("""(?<!\d)(?:\+\d{1,3}[\s-]?)?[6-9]\d{9}(?!\d)|(?<!\d)\+\d{7,15}(?!\d)""")
    private val AMOUNT_REGEX = Regex(
        """(?i)(?:₹|৳|rs\.?|inr|\$|usd|টাকা|ট|रु\.?|रू\.?|રૂ\.?|ਰੁ\.?|ரூ\.?|రూ\.?)\s*[\d,]+(?:\.\d+)?(?:\s?(?:lakh|lakhs|crore|cr|k))?|[\d,]+(?:\.\d+)?\s?(?:lakh|lakhs|crore)"""
    )
    private val DIGIT_RUN_REGEX = Regex("""\d{4,}""")

    // "F.R.E.E" / "F R E E" / "F-R-E-E": 3+ single letters joined by the same separator.
    private val SPACED_LETTERS = Regex("""\b(?:[a-zA-Z][.\-_*+ ]){2,}[a-zA-Z]\b""")
    // Letters only — an OTP "500012" or amount "10,000" must keep its digits.
    private val REPEATED_CHARS = Regex("""([a-zA-Z])\1{2,}""")

    fun normalize(text: String): NormalizedMessage {
        // 1. Unicode NFKC + zero-width strip (with Indic digit & danda folding)
        var s = java.text.Normalizer.normalize(IndicNormalizer.fold(text), java.text.Normalizer.Form.NFKC)
        s = ZERO_WIDTH.replace(s, "")

        // 2. Homoglyphs → Latin
        s = buildString(s.length) { s.forEach { append(HOMOGLYPHS[it] ?: it) } }

        // Extract entities from the lightly-cleaned text (before leet mangles digits)
        val urls = URL_REGEX.findAll(s).map { it.value.trimEnd('.', ',', ')') }.toList()
        val phoneNumbers = PHONE_REGEX.findAll(s).map { it.value }.toList()
        val amounts = AMOUNT_REGEX.findAll(s).map { it.value }.toList()
        val digitRuns = DIGIT_RUN_REGEX.findAll(s).map { it.value }.toList()

        // 3. Collapse separator obfuscation: F.R.E.E → FREE
        s = SPACED_LETTERS.replace(s) { m -> m.value.filter { it.isLetter() } }

        // 4. Leet map (word-context only, so "call 1800..." keeps its digits)
        s = deLeet(s)

        // 5. Collapse 3+ repeated chars, lowercase, collapse whitespace
        s = REPEATED_CHARS.replace(s) { it.groupValues[1].repeat(2) }
        s = s.lowercase().replace(Regex("\\s+"), " ").trim()

        return NormalizedMessage(
            original = text,
            normalizedText = s,
            urls = urls,
            phoneNumbers = phoneNumbers,
            amounts = amounts,
            digitRuns = digitRuns,
        )
    }

    /**
     * Applies the leet map per whitespace-delimited token, only when the
     * token has at least as many letters as digits (e.g. "FR33", "W1N",
     * "ca$h"). A mostly-numeric token — an OTP "482913", an amount "₹5,000" —
     * is left untouched so digit-dependent Protected patterns keep matching.
     *
     * Symbol leets (@ $ !) double as ordinary punctuation, so they convert
     * only when followed by a letter (leading "$ale", inner "ca$h") — a
     * trailing "Offer!" keeps its exclamation mark.
     */
    private val SYMBOL_LEETS = setOf('@', '$', '!')

    private fun deLeet(input: String): String =
        input.split(Regex("\\s+")).joinToString(" ") { token ->
            val letters = token.count { it.isLetter() }
            val digits = token.count { it.isDigit() }
            if (letters == 0 || letters < digits) return@joinToString token
            buildString(token.length) {
                token.forEachIndexed { i, c ->
                    val sub = LEET[c]
                    append(
                        when {
                            sub == null -> c
                            c in SYMBOL_LEETS ->
                                if (i + 1 < token.length && token[i + 1].isLetter()) sub else c
                            else -> sub
                        }
                    )
                }
            }
        }
}
