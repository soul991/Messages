package com.messages.protection

/**
 * V2-51: deterministic summary cards for messages the engine already trusts.
 *
 * A bank or delivery SMS buries three useful facts — how much, which account,
 * by when — in a wall of reference numbers. This turns those into a small card,
 * and it does it the same way the rest of this engine works: allowlisted
 * regexes over the message's own text, on the device, with an explanation for
 * every field. Nothing is looked up, nothing is inferred from a model, and
 * nothing leaves the phone.
 *
 * ## The three rules this is built around
 *
 * **1. A card is a reading aid, never an instruction.** No field is actionable.
 * There is no "Pay now", no tappable link lifted out of the body, no callable
 * number. The single most effective SMS fraud is a message that looks like the
 * bank asking for something; a tidy card with a button would be the app adding
 * its own credibility to whatever the sender wrote. Extraction stops at
 * *reading*, and the raw body stays on screen above the card so the summary can
 * always be checked against the source.
 *
 * **2. Never summarise a message the engine distrusts.** [eligible] refuses
 * SPAM, dangerous and fraud-flagged messages outright. Rendering "₹49,999 —
 * due today" in the same neat card a real bank message gets would launder a
 * scam through the app's own UI. A card is a statement that this message is
 * routine, so it may only appear where the engine already decided that.
 *
 * **3. Say how sure it is, and why.** Every field carries a [Confidence] and a
 * one-line explanation naming the words that produced it. A number next to
 * "debited" is a debit; a bare number next to nothing is a bare number, and the
 * card says so rather than guessing.
 *
 * Pure JVM: no Android, no I/O, no clock. That last one is why dates come back
 * as the words the message used rather than a timestamp — resolving "by the
 * 5th" needs a calendar and a timezone, and a card that silently picks the
 * wrong year is worse than one that shows what the message said.
 */
object CardExtractor {

    /** Bumped when the extractors change, so cached cards are recomputed. */
    const val VERSION = 1

    /**
     * How much a field is trusted, and therefore how it is presented.
     *
     * There is deliberately no LOW. A field the extractor is only vaguely sure
     * about does not get a quieter label — it is not emitted at all, because a
     * hedged wrong number on a financial card is still a wrong number, and the
     * user cannot tell which of the two the app meant. [MEDIUM] means "this is
     * definitely an amount, but the message never says what for"; anything less
     * certain than that never reaches a [Field].
     */
    enum class Confidence { MEDIUM, HIGH }

    /** Direction of a transaction amount (credit, debit, or neutral/balance). */
    enum class Direction { CREDIT, DEBIT, NEUTRAL }

    /**
     * What a field means. Deliberately a small closed set: an extractor that
     * can emit "anything interesting" is one that emits noise, and every kind
     * here has to earn its regex.
     */
    enum class FieldKind {
        /** A monetary amount, with the currency the message used. */
        AMOUNT,

        /** An amount the message called a balance, rather than a transaction. */
        BALANCE,

        /** The last digits of an account or card, as printed. */
        ACCOUNT_TAIL,

        /** A date the message attached a deadline word to. */
        DUE_DATE,

        /** A transaction/booking/order reference. */
        REFERENCE,

        /** A courier tracking number. */
        TRACKING,

        /** Where a delivery has got to, in the message's own words. */
        DELIVERY_STATUS,
    }

    /**
     * One extracted fact.
     *
     * [start]/[end] index into the ORIGINAL body, so the UI can show the user
     * exactly which words a field came from — the honest version of "view
     * original", where the source is pointed at rather than described.
     */
    data class Field(
        val kind: FieldKind,
        /** Exactly the text that matched, unmodified. */
        val raw: String,
        /**
         * Canonical form when one can be derived without guessing (digits only
         * for a reference, `1234.50` for an amount). Null when normalizing
         * would require an assumption — an ambiguous date, for instance.
         */
        val normalized: String?,
        /** Currency symbol/code as written, for [AMOUNT]/[BALANCE] only. */
        val currency: String? = null,
        val confidence: Confidence,
        /** Which words produced this, in plain language. */
        val explanation: String,
        val start: Int,
        val end: Int,
        val direction: Direction = Direction.NEUTRAL,
    )

    /**
     * A card, or null. [fields] is ordered by position in the message so the
     * card reads in the same order as the text it summarises.
     */
    data class Card(
        val fields: List<Field>,
        val version: Int = VERSION,
    ) {
        val direction: Direction
            get() = fields.firstOrNull { it.kind == FieldKind.AMOUNT }?.direction ?: Direction.NEUTRAL
    }

    /**
     * Whether a message may be summarised at all.
     *
     * The gate is the engine's own verdict, not a re-classification: this
     * module must never be the thing that decides a scam is routine. Category
     * TRANSACTIONS, or one of the protected labels that means "an institution
     * is telling you about your own account" — and never when the engine
     * raised either danger flag.
     */
    fun eligible(
        category: Category,
        protectedLabel: ProtectedLabel,
        dangerous: Boolean,
        fraudWarning: Boolean,
    ): Boolean {
        if (dangerous || fraudWarning) return false
        if (category == Category.SPAM || category == Category.BLOCKED ||
            category == Category.PROMOTIONS || category == Category.REVIEW
        ) return false
        return category == Category.TRANSACTIONS || protectedLabel in SUMMARISABLE_LABELS
    }

    private val SUMMARISABLE_LABELS = setOf(
        ProtectedLabel.BANK,
        ProtectedLabel.BILL,
        ProtectedLabel.DELIVERY,
        ProtectedLabel.TRAVEL,
    )

    /**
     * Extract a card from [body], or null when nothing survived the confidence
     * floor. Callers must gate on [eligible] first — this function does not
     * re-check, because the verdict it would need is the caller's to supply.
     *
     * Every regex runs against a [BudgetedCharSequence]: the body is
     * attacker-controlled, and the same reasoning that bounds the pattern
     * matcher applies to a message someone crafted to be expensive to read.
     */
    fun extract(body: String): Card? {
        if (body.isEmpty() || body.length > MAX_BODY_CHARS) return null
        val budgeted = BudgetedCharSequence(body)
        val fields = try {
            buildList<Field> {
                addAll(amounts(budgeted))
                addAll(accountTails(budgeted))
                addAll(dueDates(budgeted))
                addAll(references(budgeted))
                addAll(tracking(budgeted))
                addAll(deliveryStatus(budgeted))
            }
        } catch (_: BudgetedCharSequence.Budget) {
            // A body that costs too much to read gets no card. It is a
            // convenience feature; refusing is free and always correct.
            return null
        }
        val kept = dedupe(fields).sortedBy { it.start }
        return if (kept.isEmpty()) null else Card(kept)
    }

    /**
     * Longest body worth scanning. A genuine transactional SMS is a few hundred
     * characters; past this it is a newsletter, and summarising a newsletter
     * produces a card of coincidences.
     */
    const val MAX_BODY_CHARS = 2_000

    // ---- Amounts -------------------------------------------------------

    /**
     * `₹1,234.50`, `Rs. 500`, `INR 20000`, `$45.99`. The currency must be
     * present: a bare number is a reference, an account tail, a PIN or a date
     * far more often than it is money, and guessing wrong on a *financial*
     * field is the one place this feature must not be clever.
     *
     * The leading `(?:^|[^A-Za-z])` stops `rs`/`inr` matching inside a word —
     * without it "cars 500" reads as five hundred rupees. It consumes a
     * character, so the field's span comes from the capture groups rather than
     * the whole match.
     *
     * The digits are matched as a flat `[\d,]` run and validated afterwards in
     * [groupedNumber] instead of by a nested `(?:,\d{2,3})+`. Both accept the
     * same strings, but only the flat form is free of a quantifier inside a
     * quantified group — the shape [SafeRegexPolicy] rejects in imported packs,
     * and there is no reason to write one by hand here either.
     */
    private val AMOUNT = Regex(
        """(?i)(?:^|[^A-Za-z])(₹|rs\.?|inr|\$|usd|€|eur|£|gbp)\s?(\d[\d,]{0,18}(?:\.\d{1,2})?)"""
    )

    /**
     * Canonical digits for a matched amount, or null when the run is not a
     * plausible number — a trailing comma, a comma group of the wrong width, or
     * nothing but separators. Accepts both Indian (`1,23,456`) and Western
     * (`1,234,567`) grouping, and an ungrouped run.
     */
    internal fun groupedNumber(text: String): String? {
        val (whole, fraction) = text.substringBefore('.') to text.substringAfter('.', "")
        if (whole.isEmpty() || whole.startsWith(',') || whole.endsWith(',')) return null
        val groups = whole.split(',')
        if (groups.any { it.isEmpty() }) return null
        if (groups.size > 1) {
            if (groups[0].length !in 1..3) return null
            // Every group after the first is 2 (Indian) or 3 (Western) digits.
            if (groups.drop(1).any { it.length != 2 && it.length != 3 }) return null
        }
        val digits = whole.replace(",", "")
        if (digits.isEmpty() || digits.any { !it.isDigit() }) return null
        return if (fraction.isEmpty()) digits else "$digits.$fraction"
    }

    /** Words that make an amount a transaction rather than a balance. */
    private val DEBIT_WORDS = Regex(
        """(?i)\b(debited|debit|spent|paid|payment|purchase|withdrawn|charged|deducted|sent|transferred|txn|due|bill|emi|premium|recharge)\b"""
    )
    private val CREDIT_WORDS = Regex("""(?i)\b(credited|credit|received|refund|deposited|cashback)\b""")
    private val BALANCE_WORDS = Regex(
        """(?i)\b(avl\.?\s?bal|available\s+balance|avail\.?\s?bal|balance|bal)\b"""
    )

    private fun amounts(body: CharSequence): List<Field> =
        AMOUNT.findAll(body).mapNotNull { m ->
            val currencyGroup = m.groups[1] ?: return@mapNotNull null
            val digitGroup = m.groups[2] ?: return@mapNotNull null
            val normalized = groupedNumber(digitGroup.value) ?: return@mapNotNull null
            val start = currencyGroup.range.first
            val end = digitGroup.range.last + 1
            val raw = body.subSequence(start, end).toString()
            // A window either side decides what the number is *about*. Small
            // and fixed: a claim sourced from forty characters away is a guess
            // wearing a confidence label.
            val context = window(body, start, end - 1)
            val balance = BALANCE_WORDS.containsMatchIn(context)
            val debit = DEBIT_WORDS.containsMatchIn(context)
            val credit = CREDIT_WORDS.containsMatchIn(context)
            when {
                balance && !debit && !credit -> Field(
                    kind = FieldKind.BALANCE,
                    raw = raw, normalized = normalized, currency = currencyGroup.value,
                    confidence = Confidence.HIGH,
                    explanation = "The message calls this a balance",
                    start = start, end = end,
                    direction = Direction.NEUTRAL,
                )
                debit || credit -> {
                    val isCredit = credit && !debit
                    Field(
                        kind = FieldKind.AMOUNT,
                        raw = raw, normalized = normalized, currency = currencyGroup.value,
                        confidence = Confidence.HIGH,
                        explanation = if (isCredit) "Money in, by the message's own wording"
                        else "Money out, by the message's own wording",
                        start = start, end = end,
                        direction = if (isCredit) Direction.CREDIT else Direction.DEBIT,
                    )
                }
                else -> Field(
                    kind = FieldKind.AMOUNT,
                    raw = raw, normalized = normalized, currency = currencyGroup.value,
                    confidence = Confidence.MEDIUM,
                    explanation = "An amount, but the message doesn't say what it was for",
                    start = start, end = end,
                    direction = Direction.NEUTRAL,
                )
            }
        }.toList()

    // ---- Account tails -------------------------------------------------

    /**
     * `A/c XX1234`, `card ending 5678`, `acct **9012`. Only ever the masked
     * tail as printed — this never reconstructs, joins or stores a full number,
     * and there is no extractor for one.
     */
    private val ACCOUNT_TAIL = Regex(
        """(?i)\b(?:a/?c(?:count)?|acct|card|xxxx?|ending(?:\s+(?:in|with))?)\s*(?:no\.?|number|is)?\s*[:#]?\s*(?:x{1,6}|\*{1,6})?(\d{3,6})\b"""
    )

    private fun accountTails(body: CharSequence): List<Field> =
        ACCOUNT_TAIL.findAll(body).map { m ->
            Field(
                kind = FieldKind.ACCOUNT_TAIL,
                raw = m.value.trim(),
                normalized = m.groupValues[1],
                confidence = Confidence.HIGH,
                explanation = "Follows an account or card label in the message",
                start = m.range.first, end = m.range.last + 1,
            )
        }.toList()

    // ---- Due dates -----------------------------------------------------

    /**
     * A date is only interesting here when a deadline word points at it. "on
     * 12-08-2026" in a receipt is when something happened; "due by 12-08-2026"
     * is something the user has to act on, and only the second belongs on a
     * card.
     *
     * [normalized] stays null for every one of these. Turning "5th Aug" into a
     * timestamp needs a year and a timezone the message did not supply, and a
     * card that quietly picks next year for a bill due last week is worse than
     * one that shows the words.
     */
    private val DUE_DATE = Regex(
        """(?i)\b(due(?:\s+(?:on|by|date))?|pay\s+by|payable\s+by|last\s+date|expires?\s+on|valid\s+(?:till|until|upto))\b[\s:]*""" +
            """((?:\d{1,2}[-/.]\d{1,2}[-/.]\d{2,4})|""" +
            """(?:\d{1,2}(?:st|nd|rd|th)?\s+(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*(?:\s+\d{2,4})?)|""" +
            """(?:(?:jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\s+\d{1,2}(?:st|nd|rd|th)?(?:,?\s+\d{2,4})?)|""" +
            """(?:today|tomorrow))"""
    )

    private fun dueDates(body: CharSequence): List<Field> =
        DUE_DATE.findAll(body).map { m ->
            Field(
                kind = FieldKind.DUE_DATE,
                raw = m.groupValues[2].trim(),
                // Deliberately not normalized — see the KDoc above.
                normalized = null,
                confidence = Confidence.HIGH,
                explanation = "The message calls this a deadline (“${m.groupValues[1].trim()}”)",
                start = m.range.first, end = m.range.last + 1,
            )
        }.toList()

    // ---- References ----------------------------------------------------

    /**
     * A reference only counts when the message labels it. Every transactional
     * SMS is full of digit runs, and picking one at random and calling it "your
     * reference" is exactly the kind of confident wrongness this feature has to
     * avoid.
     */
    private val REFERENCE = Regex(
        """(?i)\b(?:ref(?:erence)?|txn|transaction|utr|order|booking|pnr|invoice|receipt|bill)\s*(?:no\.?|id|number|#)?\s*[:#-]?\s*([A-Z0-9][A-Z0-9-]{4,23})\b"""
    )

    private fun references(body: CharSequence): List<Field> =
        REFERENCE.findAll(body).mapNotNull { m ->
            val value = m.groupValues[1]
            // All-letters is a word that followed the label, not an identifier.
            if (value.none { it.isDigit() }) return@mapNotNull null
            Field(
                kind = FieldKind.REFERENCE,
                raw = value,
                normalized = value.uppercase(),
                confidence = Confidence.HIGH,
                explanation = "Labelled as a reference in the message",
                start = m.range.first, end = m.range.last + 1,
            )
        }.toList()

    // ---- Delivery ------------------------------------------------------

    private val TRACKING = Regex(
        """(?i)\b(?:awb|tracking|consignment|docket|shipment|lr)\s*(?:no\.?|id|number|#)?\s*[:#-]?\s*([A-Z0-9][A-Z0-9-]{5,23})\b"""
    )

    private fun tracking(body: CharSequence): List<Field> =
        TRACKING.findAll(body).mapNotNull { m ->
            val value = m.groupValues[1]
            if (value.none { it.isDigit() }) return@mapNotNull null
            Field(
                kind = FieldKind.TRACKING,
                raw = value,
                normalized = value.uppercase(),
                confidence = Confidence.HIGH,
                explanation = "Labelled as a tracking number in the message",
                start = m.range.first, end = m.range.last + 1,
            )
        }.toList()

    /**
     * The courier's own status wording. Reported verbatim rather than mapped
     * onto a status enum: "out for delivery" and "arriving today" are not the
     * same promise, and flattening them into one label invents certainty.
     */
    private val DELIVERY_STATUS = Regex(
        """(?i)\b(out for delivery|delivered|shipped|dispatched|in transit|arriving today|arriving tomorrow|delivery attempted|returned to (?:origin|sender)|ready for pickup|picked up)\b"""
    )

    private fun deliveryStatus(body: CharSequence): List<Field> =
        DELIVERY_STATUS.findAll(body).map { m ->
            Field(
                kind = FieldKind.DELIVERY_STATUS,
                raw = m.value,
                normalized = m.value.lowercase(),
                confidence = Confidence.HIGH,
                explanation = "The courier's own wording, quoted as written",
                start = m.range.first, end = m.range.last + 1,
            )
        }.toList()

    // ---- Shared --------------------------------------------------------

    private const val CONTEXT_CHARS = 28

    /** The text immediately around a match, used to decide what it refers to. */
    private fun window(body: CharSequence, start: Int, end: Int): String =
        body.subSequence(
            (start - CONTEXT_CHARS).coerceAtLeast(0),
            (end + 1 + CONTEXT_CHARS).coerceAtMost(body.length),
        ).toString()

    /**
     * Two extractors can claim overlapping text — an account tail sitting
     * inside a reference match, say. The higher-confidence field wins, and on a
     * tie the earlier one does, so the result is stable for a given body rather
     * than dependent on which extractor happened to run first.
     */
    private fun dedupe(fields: List<Field>): List<Field> {
        val kept = ArrayList<Field>(fields.size)
        for (field in fields.sortedWith(compareByDescending<Field> { it.confidence.ordinal }.thenBy { it.start })) {
            val clashes = kept.any { field.start < it.end && it.start < field.end }
            if (!clashes) kept.add(field)
        }
        return kept
    }
}
