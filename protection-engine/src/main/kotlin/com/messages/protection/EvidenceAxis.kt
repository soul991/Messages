package com.messages.protection

/**
 * Layer 4 — the evidence-independence rule.
 *
 * Every layer before this one added evidence. This one subtracts confidence: a
 * Spam verdict that rests on a single kind of evidence is demoted to Review.
 * It is the structural form of the project's "when unsure → Review" guardrail,
 * and the first layer whose job is precision rather than recall — which is the
 * same as saying it is the first layer that can make the shipped engine worse.
 *
 * ## What the corpus said, before any of this was written
 *
 * The literal reading of §10 — *any* single-axis Spam demotes — moves 82 of the
 * 513 corpus messages, and every one of the 82 is labelled `scam`. There is not
 * one false positive among them, because the shipped corpus has no false
 * positives into Spam at all. On this data the literal rule is pure recall loss.
 *
 * The reason is structural, not accidental, and it is visible in the library:
 * of the ~190 ids the engine can emit, 178 are curated text patterns. The axes
 * are not peers. The text axis *is* the product — 178 hand-written regexes whose
 * weights were tuned so that a match convicts. The link axis is eight signals
 * written to *contribute*, three of which happen to reach `spamAt` unaided.
 * Requiring "text plus something" therefore does not tighten borderline cases;
 * it disables the detector.
 *
 * So the rule is scoped to where a single axis genuinely convicts without any
 * curated judgement behind it: **a Spam verdict carried entirely by the link
 * axis.** See [evaluate].
 *
 * ## What it is actually for
 *
 * `LinkAnalyzer` decides brand impersonation with a substring test:
 * `BRANDS.firstOrNull { domain.contains(it) }`, against a 37-name list, cleared
 * only by an exact match in `OFFICIAL_DOMAINS`. `jiomart.com` contains `jio`;
 * `jio.com` is official, `jiomart.com` is not. A genuine JioMart order link is
 * `link-brand-impersonation` (+10), which trips `C6`, which is a fraud combo,
 * which is SPAM·Dangerous — on link structure alone, with no text evidence
 * whatsoever. Same for `dhlparcel.in` (`dhl`), `axisrooms.com` (`axis`),
 * `bobbleapp.me` (`bob`). Under this layer those become Review.
 *
 * ## Shape
 *
 * Post-processor, like [SenderLane] and [LinkStructure]: reads a finished
 * [Verdict], edits no engine file, and [apply] is the identity function when
 * the rule does not bite. Reversal is deleting this file and its two call sites.
 */
object EvidenceAxis {

    /**
     * The four kinds of evidence. A verdict is independent when it rests on
     * more than one.
     */
    enum class Axis {
        /** What the message says: pattern matches, combo wording, message shape. */
        TEXT,

        /** Who sent it: header registration, addressing style. */
        SENDER,

        /** What it links to: URL structure and destination. */
        LINK,

        /** What this sender has done before: first contact, lane and domain novelty. */
        HISTORY,
    }

    /**
     * The synthetic ids `ProtectionEngine` emits inline. They are not patterns
     * and not link signals, so `familiesByPatternId` cannot classify them and
     * they have to be named. Kept here rather than inline in [axisOf] so the
     * completeness test can enumerate them.
     *
     * `protected-lane-override` is deliberately absent: it records that a
     * protected label was withdrawn, which is bookkeeping about a decision, not
     * evidence for one. [axisOf] returns `null` for it — known, and carrying no
     * axis. See [NON_EVIDENCE].
     */
    private val ENGINE_SIGNALS = mapOf(
        "sender-dlt-p" to Axis.SENDER,
        "dear-customer-numeric" to Axis.SENDER,
        "first-contact" to Axis.HISTORY,
    )

    /** Ids this project's own layers emit. */
    private val LAYER_SIGNALS = mapOf(
        SenderLane.SIGNAL_ID to Axis.HISTORY,
        LinkStructure.NOVELTY_ID to Axis.HISTORY,
        // Layer 5's id, written as a literal rather than as `AiLayer.SIGNAL_ID`
        // on purpose: this file is Layer 4's and deleting Layer 5 must not stop
        // it compiling. Layer 5 runs after this rule, so the id is never on a
        // verdict this object inspects — the entry exists so the axis-coverage
        // test stays exhaustive, and it is TEXT because that is what the model
        // reads.
        "ai-ngram-scam" to Axis.TEXT,
    )

    /**
     * Known ids that carry no evidence at all. Classified, not unclassified.
     * Protected-family pattern ids are the other such group, handled in
     * [axisOf] off the library rather than listed here.
     */
    val NON_EVIDENCE = setOf("protected-lane-override")

    /**
     * Which axes each shipped combo draws on, read off `ComboRules.evaluate`.
     * A combo is not a fifth axis — it is a conjunction of facts that already
     * belong to axes, so it is credited to its constituents.
     *
     * This matters. Sixteen corpus messages look link-only on their pattern ids
     * alone and are full of scam wording ("Winner winner! bumper prize 12 lakh")
     * that the *patterns* missed and the *combo regexes* caught. Ignoring combos
     * would have called those single-axis and demoted them. Combos carry text
     * evidence that never surfaces as a pattern id.
     */
    private val COMBO_AXES = mapOf(
        // urgency + money + phishy link + unregistered sender
        "C1" to setOf(Axis.TEXT, Axis.LINK, Axis.SENDER),
        // prize wording + money, no sender or link component
        "C2" to setOf(Axis.TEXT),
        // account threat + unregistered sender
        "C3" to setOf(Axis.TEXT, Axis.SENDER),
        // scam hook + shortened link
        "C4" to setOf(Axis.TEXT, Axis.LINK),
        "C5" to setOf(Axis.TEXT, Axis.LINK, Axis.SENDER),
        // brand-impersonating domain, structural only
        "C6" to setOf(Axis.LINK),
        // asks for PIN/OTP/CVV — pure wording
        "C7" to setOf(Axis.TEXT),
        "C8" to setOf(Axis.TEXT, Axis.SENDER),
        // direct .apk download link
        "C9" to setOf(Axis.LINK),
        // promotional wording only
        "C10" to setOf(Axis.TEXT),
    )

    /**
     * Ids allowed to convict alone.
     *
     * The failure mode of an exception list is that it is longer than it needs
     * to be and re-admits everything the rule was meant to hold back, so this
     * one contains only ids that (a) can actually occur under the trigger in
     * [evaluate] and (b) are decisive with no corroboration by construction.
     * Two qualify:
     *
     *  - `link-apk-download` — a direct `.apk` URL in an SMS. Play Store
     *    distribution never uses one; there is no legitimate sender of this.
     *  - `C9` — the combo form of the same fact, so the exception holds whether
     *    the engine reports the signal, the combo, or both.
     *
     * Considered and rejected:
     *
     *  - `C7` (PIN/OTP/CVV ask) — the brief's own example, and genuinely
     *    always fraud, but it is a TEXT-axis combo and can never appear on a
     *    link-only verdict. Sanctioning it would be dead weight in the list.
     *  - `C6` (brand impersonation) — this is the mechanism the layer exists to
     *    catch, not an exception to it.
     *  - `link-ip-literal`, `link-punycode` (both +8) — strong, but neither
     *    reaches `spamAt` alone, and a raw IP or punycode host with no other
     *    evidence is exactly the borderline case Review is for.
     *  - `link-suspicious-tld` — would exempt six of the seven link-only Spam
     *    verdicts in the corpus and reduce the rule to nothing.
     */
    val SANCTIONED = setOf("link-apk-download", "C9")

    /** Why a verdict was demoted, for the "Why?" screen and for the reports. */
    data class Demotion(val axis: Axis, val ids: List<String>) {
        val reason: String
            get() = "Filed for review, not spam: the only evidence is the link " +
                "(${ids.joinToString(", ")}) — nothing in the message text or the " +
                "sender backs it up."
    }

    /**
     * The axis an id belongs to, or `null` if the id carries no evidence.
     *
     * Pattern ids are resolved through [familiesByPatternId] — the shipped
     * library's own family assignment — rather than a hand-written list, so
     * adding a pattern to `patterns.json` classifies it automatically instead of
     * silently falling off the map. `Families.LINK` and `Families.FORMAT` are
     * honoured even though no shipped pattern uses them today.
     */
    fun axisOf(id: String, familiesByPatternId: Map<String, String>): Axis? {
        val family = familiesByPatternId[id]
        if (family != null) return when {
            // A protected-family match is evidence *against* filtering, not for
            // it, and it carries weight 0. Counting it as text evidence for a
            // Spam verdict inverts its meaning.
            //
            // This is not hypothetical. A genuine JioMart delivery SMS matches
            // `protect-delivery-status`, has the label withdrawn by the
            // protected-lane override, and is then convicted by
            // `link-brand-impersonation` alone — the exact case this layer
            // exists for. Without this branch the withdrawn protected match
            // counted as a second axis and the message stayed in Spam.
            family in Families.PROTECTED -> null
            family == Families.LINK -> Axis.LINK
            else -> Axis.TEXT
        }
        ENGINE_SIGNALS[id]?.let { return it }
        LAYER_SIGNALS[id]?.let { return it }
        if (id in NON_EVIDENCE) return null
        // Synthetic ids from LinkAnalyzer and LinkStructure. `format-*` is
        // message shape — all-caps, exclamation runs, emoji — which is what the
        // message says, not what it links to.
        if (id.startsWith("format-")) return Axis.TEXT
        if (id.startsWith("link-")) return Axis.LINK
        return null
    }

    /** True when [id] is one this object knows about. Used by the coverage test. */
    fun isClassified(id: String, familiesByPatternId: Map<String, String>): Boolean =
        id in familiesByPatternId || id in NON_EVIDENCE || id in COMBO_AXES ||
            axisOf(id, familiesByPatternId) != null

    /** Every axis a verdict's evidence touches, combos credited to their parts. */
    fun axesOf(verdict: Verdict, familiesByPatternId: Map<String, String>): Set<Axis> {
        val axes = mutableSetOf<Axis>()
        verdict.matchedPatternIds.forEach { id ->
            axisOf(id, familiesByPatternId)?.let { axes += it }
        }
        verdict.matchedComboIds.forEach { id -> COMBO_AXES[id]?.let { axes += it } }
        return axes
    }

    /**
     * The rule. Returns the demotion to apply, or `null` when the verdict stands.
     *
     * Bites only when all of:
     *  - the verdict is SPAM (BLOCKED is a user block list, not an inference);
     *  - its evidence lies on exactly the LINK axis and nothing else;
     *  - no [SANCTIONED] id is present.
     *
     * The TEXT axis is not subject to the rule. That is not an exception, it is
     * what the library is: 178 curated patterns, each weighted to convict, which
     * is the judgement this project is not entitled to overrule from the outside.
     * The SENDER axis cannot reach `spamAt` alone (its two signals total +9), and
     * the HISTORY axis cannot appear on a Spam verdict at all, because both
     * `SenderLane.apply` and `LinkStructure.apply` return early on
     * `Category.SPAM`. So LINK is not merely where the rule is aimed — it is the
     * only axis where the question can arise.
     */
    fun evaluate(verdict: Verdict, familiesByPatternId: Map<String, String>): Demotion? {
        if (verdict.category != Category.SPAM) return null
        if (verdict.matchedPatternIds.any { it in SANCTIONED }) return null
        if (verdict.matchedComboIds.any { it in SANCTIONED }) return null
        if (axesOf(verdict, familiesByPatternId) != setOf(Axis.LINK)) return null
        val ids = verdict.matchedPatternIds.filter {
            axisOf(it, familiesByPatternId) == Axis.LINK
        }
        return Demotion(Axis.LINK, ids)
    }

    /**
     * Folds the demotion into the verdict. Identity when [demotion] is `null`,
     * which is what makes the flag-off proof exact.
     *
     * Demotion only ever loosens: SPAM → REVIEW, dangerous cleared. It can never
     * move a message *into* a filtered folder, so it cannot filter a Protected
     * message no matter what the rest of the stack does. The fraud banner is
     * raised so the user still sees the warning that the score earned.
     */
    fun apply(verdict: Verdict, demotion: Demotion?): Verdict {
        if (demotion == null) return verdict
        return verdict.copy(
            category = Category.REVIEW,
            dangerous = false,
            fraudWarningBanner = true,
            explanations = verdict.explanations + demotion.reason,
            notify = false,
        )
    }
}
