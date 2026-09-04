package com.messages.protection

/**
 * Layer 2 — sender-lane binding.
 *
 * A sender operates in a *lane*. A registered DLT transactional header
 * (`-S`/`-T`/`-G`) sends OTPs and bank alerts; a `-P` header sends promotions; a
 * 10-digit personal number holds conversations. A message that does not fit the
 * lane its sender has historically used is evidence — `HDFCBK` suddenly sending
 * prize wording, or a number that has only ever chatted suddenly sending a KYC
 * threat.
 *
 * ### Why this needs no `ProtectionEngine` edit
 *
 * Everything here reads a finished [Verdict] plus one map the engine already
 * exposes publicly (`ProtectionEngine.familiesByPatternId`). The layer is a
 * post-processor: `apply(engine.classify(input), …)`. Reversal is deleting this
 * file, dropping the table and removing one call.
 *
 * ### Three safety properties, all arithmetic rather than hope
 *
 *  1. **Cold-start safe.** Below [MIN_OBSERVATIONS] recorded messages a sender
 *     has no established lane, so [evaluate] returns `null` and [apply] is the
 *     identity function. Not a weak signal, not a default one — none. On a fresh
 *     install every sender is unknown, and this layer is inert until history
 *     exists. That is the failure mode it is designed against.
 *  2. **Never decisive alone.** [ANOMALY_WEIGHT] (4) is deliberately below the
 *     engine's `reviewAt` threshold (5), so a lane anomaly on a message with no
 *     other evidence cannot move it anywhere. It needs at least one point from
 *     another axis.
 *  3. **Protected is never filtered.** On a verdict that is Protected — or that
 *     merely *reads* as protected, claiming the transactional lane without any
 *     scam marker — the only change permitted is raising
 *     [Verdict.fraudWarningBanner], the shipped mechanism for "delivered, but
 *     treat with caution". Category and score are untouched.
 *
 * The score may end up at or above `spamAt` while the category stays `REVIEW`.
 * That gap is deliberate: promotion to Spam requires independent evidence axes,
 * which is Layer 4's rule, not this one's.
 */
object SenderLane {

    /**
     * Messages a sender must have delivered before it has a lane at all.
     * Below this the layer emits nothing.
     */
    const val MIN_OBSERVATIONS = 5

    /**
     * Additive evidence for an off-lane message. Below the engine's default
     * `reviewAt = 5` **on purpose** — see property 2 above. Raising this to 5 or
     * more would make a lone lane anomaly decisive and break cold-start-adjacent
     * safety for senders whose history is merely narrow.
     */
    const val ANOMALY_WEIGHT = 4

    /** Appears in `matchedPatternIds`. Not a pattern id; nothing else emits it. */
    const val SIGNAL_ID = "sender-lane-anomaly"

    /**
     * Stored in the `family` column for a message that matched no content
     * family — an ordinary conversational SMS. Deliberately not a
     * [Families] constant so it can never collide with one.
     */
    const val CONVERSATIONAL_FAMILY = "conversational"

    /** The four lanes. Derived from shipped families; no new taxonomy. */
    enum class Lane { TRANSACTIONAL, PROMOTIONAL, SCAM, CONVERSATIONAL }

    /** One `sender_lane` row. */
    data class Observation(val family: String, val count: Int, val lastSeenMillis: Long)

    /** All rows for one sender. Built by the DAO; see the schema spec. */
    data class Profile(val senderId: String, val observations: List<Observation>) {
        val total: Int = observations.sumOf { it.count }
        val lanes: Set<Lane> = observations.map { laneOf(it.family) }.toSet()

        /** False on a fresh install, and for every rarely-heard-from sender. */
        val established: Boolean = total >= MIN_OBSERVATIONS

        companion object {
            fun empty(senderId: String) = Profile(senderId, emptyList())
        }
    }

    data class Anomaly(
        val claimed: Set<Lane>,
        val established: Set<Lane>,
        val observations: Int,
    ) {
        /** The "Why?" line. Reads as a sentence because a user reads it. */
        val reason: String =
            "This sender has only ever sent ${phrase(established)} " +
                "($observations messages); this one reads like ${phrase(claimed)}"
    }

    fun laneOf(family: String): Lane = when {
        family in Families.PROTECTED -> Lane.TRANSACTIONAL
        family == Families.PROMO -> Lane.PROMOTIONAL
        family in Families.SCAM -> Lane.SCAM
        else -> Lane.CONVERSATIONAL
    }

    /**
     * The families that say what a message *is*. `link-format` and
     * `format-signal` are excluded on purpose: an all-caps message with a
     * shortener is a signal about presentation, not about which lane the sender
     * is operating in, and treating it as content would let a single link turn
     * every sender conversational.
     */
    private fun contentFamilies(verdict: Verdict, familiesByPatternId: Map<String, String>): List<String> =
        verdict.matchedPatternIds
            .mapNotNull { familiesByPatternId[it] }
            .filter { it in Families.PROTECTED || it == Families.PROMO || it in Families.SCAM }

    /** The lanes this message claims. Empty content → conversational. */
    fun lanesOf(verdict: Verdict, familiesByPatternId: Map<String, String>): Set<Lane> =
        contentFamilies(verdict, familiesByPatternId).map(::laneOf).toSet()
            .ifEmpty { setOf(Lane.CONVERSATIONAL) }

    /**
     * The `family` value to record for this verdict, or `null` if it must not be
     * recorded at all.
     *
     * Only messages the engine let through are recorded. Without this a spoofer
     * could establish a lane by sending five caught scams and then walk the
     * sixth past the signal — history that includes filtered messages is history
     * an attacker writes.
     */
    fun observationFamily(verdict: Verdict, familiesByPatternId: Map<String, String>): String? {
        val trusted = !verdict.dangerous &&
            verdict.category != Category.SPAM &&
            verdict.category != Category.REVIEW &&
            verdict.category != Category.BLOCKED
        if (!trusted) return null
        return contentFamilies(verdict, familiesByPatternId).firstOrNull() ?: CONVERSATIONAL_FAMILY
    }

    /**
     * The signal. `null` means "say nothing" — which is the answer for every
     * sender on a fresh install, and for every sender whose lane this message
     * fits.
     */
    fun evaluate(
        verdict: Verdict,
        profile: Profile,
        familiesByPatternId: Map<String, String>,
    ): Anomaly? {
        if (!profile.established) return null
        val claimed = lanesOf(verdict, familiesByPatternId)
        // Overlap of one lane is enough to be in-lane. A header that sends both
        // OTPs and promotions has both, and neither is anomalous for it.
        if (claimed.any { it in profile.lanes }) return null
        return Anomaly(claimed, profile.lanes, profile.total)
    }

    /**
     * Folds the signal into a verdict. Identity when [anomaly] is `null`, which
     * is what makes the flag-off / no-history proof exact.
     *
     * @param reviewAt the engine's current `Sensitivity.reviewAt`, so the
     *   Sensitivity slider keeps working rather than being second-guessed here.
     */
    fun apply(verdict: Verdict, anomaly: Anomaly?, reviewAt: Int): Verdict {
        if (anomaly == null) return verdict
        // Already at or past the ceiling this layer may push to.
        if (verdict.category == Category.SPAM || verdict.category == Category.BLOCKED) return verdict

        val ids = verdict.matchedPatternIds + SIGNAL_ID
        val explanations = verdict.explanations + anomaly.reason

        // Warn, never filter — for anything that *reads* as protected, not only
        // for what the engine labelled protected.
        //
        // The distinction cost a real message. The engine withholds a protected
        // label on sender type: a train PNR confirmation from a 10-digit number
        // matches the travel family but is denied the TRAVEL label, because a
        // personal number is not a railway. Keying this branch on the label alone
        // let that message fall through to the additive branch and be pushed to
        // Review by a lane mismatch it could not avoid. Keying it on the claimed
        // lane instead means a bank-, travel- or delivery-shaped message is never
        // penalised here — it is warned about, which is all this signal has ever
        // been entitled to do to a message a user may be waiting for.
        //
        // The SCAM exclusion keeps the exemption from being a way in: a message
        // wearing both a protected shape and scam markers gets no free pass.
        val readsAsProtected = verdict.protectedLabel != ProtectedLabel.NONE ||
            (Lane.TRANSACTIONAL in anomaly.claimed && Lane.SCAM !in anomaly.claimed)
        if (readsAsProtected) {
            return verdict.copy(
                fraudWarningBanner = true,
                matchedPatternIds = ids,
                explanations = explanations,
            )
        }

        val score = verdict.score + ANOMALY_WEIGHT
        val category =
            if (verdict.category == Category.INBOX && score >= reviewAt) Category.REVIEW
            else verdict.category
        return verdict.copy(
            category = category,
            score = score,
            matchedPatternIds = ids,
            explanations = explanations,
            notify = if (category == Category.REVIEW) false else verdict.notify,
        )
    }

    private fun phrase(lanes: Set<Lane>): String =
        lanes.sorted().joinToString(" and ") {
            when (it) {
                Lane.TRANSACTIONAL -> "transaction alerts"
                Lane.PROMOTIONAL -> "promotions"
                Lane.SCAM -> "a scam"
                Lane.CONVERSATIONAL -> "ordinary conversation"
            }
        }

    private fun Set<Lane>.sorted(): List<Lane> = sortedBy { it.ordinal }
}
