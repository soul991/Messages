package com.messages.protection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Layer 5 — the character n-gram classifier.
 *
 * ## The seam, which is not where the brief says it is
 *
 * `Project_Handoff.md` records "the AI seam is `ProtectionEngine.kt:177`, the
 * mandated interface name for the ML hook is `AiScorer`", and §11 says to use
 * it. Neither `AiScorer` nor `AI_LAYER_ENABLED` exists anywhere in the shipped
 * engine — `grep` across `inputs/` returns nothing for either. What exists is
 * the *location*: line 177 is `val finalScore = score.toInt()`, immediately
 * above the verdict-threshold block, which is indeed where an AI contribution
 * would have to land if it were to be folded into the score. The interface is a
 * name to be created, and this file creates it.
 *
 * It does **not** take the engine-edit form, for a reason that is about blast
 * radius rather than about tidiness. Adding an AI term to `score` before the
 * thresholds run gives a statistical model the ability to push a message all
 * the way to SPAM·Dangerous, and to change the `score` field of verdicts that
 * were already correct. As a post-processor with a hard ceiling of Review, the
 * worst this layer can do is put an unfiltered message in front of the user with
 * a reason attached. Layers 2, 3 and 4 all took the post-processor form and the
 * brief's own instruction is not to edit an engine file if one will do. One
 * will. `ProtectionEngine.kt` is untouched.
 *
 * ## What it is for, measured before it was written
 *
 * The corpus catch rate is already 100%, so recall is not the gap.
 * `layer5-headroom` measured the real one: drop the single highest-weighted
 * matched pattern from each caught scam message — the cost of a scammer
 * rewording one phrase — and 43 of 220 fall out of a filtered folder and into
 * the Inbox. That is 19.5%, and it is what this layer exists for.
 *
 * ## What it is allowed to do
 *
 * Inbox → Review, and nothing else. Never Spam, never `dangerous`, never a
 * fraud banner, and it does not touch a message that reads as protected at all.
 * Layer 3 raises a banner on protected traffic because it has a structural fact
 * about a URL; this layer has a mean log-likelihood ratio over character
 * n-grams, which is not the sort of evidence that should make anyone's genuine
 * bank alert look alarming. When in doubt it does nothing, and [AiLayer.apply]
 * is the identity function when it does nothing — which is what makes the
 * flag-off proof exact rather than approximate.
 */

/**
 * The mandated hook. One method, because a classifier that needs more than
 * "here is a message, how scam-like is it" is not the thing §11 describes.
 *
 * @return a signed score; positive means more scam-like than ham-like. `0.0`
 *   means *abstain* — no opinion — and implementations must return it rather
 *   than a small number when they have no basis for one.
 */
interface AiScorer {
    fun score(text: String): Double

    /** Above this the score is treated as a positive. Never negative; see [NGramScorer]. */
    val threshold: Double

    /**
     * What the shadow log records as the provenance of these scores.
     *
     * Defaults to [AiLayer.MODEL_TAG] so a test double needs no asset, and is
     * overridden by [NGramScorer.fromJson] with a hash of the bytes it actually
     * parsed. A build-time *name* cannot tell you the asset was swapped, which
     * is the only question the field data will need this field to answer.
     */
    val modelTag: String get() = AiLayer.MODEL_TAG
}

/**
 * Multinomial naive Bayes over character n-grams, collapsed to one weight per
 * gram: `log P(gram|scam) - log P(gram|ham)`. Scoring is therefore a sum of map
 * lookups divided by a count, with no maths beyond addition — which is the
 * whole reason this model shape was chosen over anything requiring a runtime.
 *
 * The training side is `work/tools/train_ngram.py` and its `score()` is a
 * line-for-line mirror of the one here. If you change either, change both; the
 * harness test `the_kotlin_scorer_agrees_with_the_python_trainer` fails if they
 * drift.
 */
class NGramScorer(
    private val weights: Map<String, Double>,
    override val threshold: Double,
    private val ngramMin: Int = 2,
    private val ngramMax: Int = 5,
    private val minWords: Int = 6,
    override val modelTag: String = AiLayer.MODEL_TAG,
) : AiScorer {

    val featureCount: Int get() = weights.size

    /**
     * Mean log-likelihood ratio over every n-gram in the message.
     *
     * Divided by the *total* gram count and not by the number of grams the
     * model recognised: a message built mostly of vocabulary the model has
     * never seen should land near zero, rather than being decided by the two
     * fragments it happens to know.
     */
    override fun score(text: String): Double {
        val tokens = tokenize(text)
        // Abstain rather than guess. A scam has to make a demand and name a
        // channel and it cannot do both in five words; below that there is not
        // enough text to reason from. Measured, not assumed — without this the
        // model called the corpus's genuine "Sorry wrong number." a scam off
        // three matched grams.
        if (tokens.size < minWords) return 0.0

        var total = 0
        var sum = 0.0
        for (token in tokens) {
            val padded = " ${token.lowercase()} "
            for (n in ngramMin..ngramMax) {
                if (padded.length < n) continue
                for (i in 0..padded.length - n) {
                    total++
                    weights[padded.substring(i, i + n)]?.let { sum += it }
                }
            }
        }
        return if (total == 0) 0.0 else sum / total
    }

    /**
     * Splits on `Char.isWhitespace`, deliberately not on `Regex("\\s+")`.
     * Java's `\s` is ASCII-only, so a non-breaking space — which real SMS
     * traffic contains — would glue two words into one token and change every
     * n-gram across the join. Python's `str.split()`, which the trainer uses,
     * splits on Unicode whitespace, and the two sides have to agree.
     */
    private fun tokenize(text: String): List<String> {
        val out = ArrayList<String>()
        val sb = StringBuilder()
        for (ch in text) {
            if (ch.isWhitespace()) {
                if (sb.isNotEmpty()) { out.add(sb.toString()); sb.setLength(0) }
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) out.add(sb.toString())
        return out
    }

    companion object {
        /** Hard cap on the model asset, checked before anything is parsed. */
        const val MAX_MODEL_BYTES = 2 * 1024 * 1024

        /**
         * Parses `deliverable/model/ngram_model.json`.
         *
         * The model file is an app asset, not user input, but it is also the
         * one thing in this layer that a pattern-pack import mechanism could
         * one day be pointed at — so it is size-checked and structurally
         * validated on the same reasoning as `PatternPackPolicy.validate`.
         * A malformed model must fail loudly at load, not silently score zero
         * on every message for the life of the install.
         */
        fun fromJson(text: String): NGramScorer {
            require(text.length <= MAX_MODEL_BYTES) {
                "model is ${text.length} bytes, max $MAX_MODEL_BYTES"
            }
            val root = Json.parseToJsonElement(text).jsonObject
            val weights = root.getValue("weights").jsonObject
                .mapValues { it.value.jsonPrimitive.double }
            require(weights.isNotEmpty()) { "model has no weights" }
            val threshold = root.getValue("threshold").jsonPrimitive.double
            // A negative threshold would mean "escalate messages the model
            // believes are innocent", which is never what anyone meant.
            require(threshold >= 0.0) { "threshold $threshold is negative" }
            return NGramScorer(
                weights = weights,
                threshold = threshold,
                ngramMin = root.getValue("ngramMin").jsonPrimitive.int,
                ngramMax = root.getValue("ngramMax").jsonPrimitive.int,
                minWords = root.getValue("minWords").jsonPrimitive.int,
                modelTag = tagOf(text),
            )
        }

        /**
         * `ngram-<first 12 hex of SHA-256 of the asset>`.
         *
         * Twelve, not sixty-four: this is a histogram key in a log line, not a
         * security boundary. Nobody is attacking it — the asset ships inside the
         * APK — and 48 bits is many orders of magnitude past the point where two
         * builds of *our own* model collide. The full digest would dominate every
         * row of the shadow log and get truncated by whoever reads it anyway.
         *
         * Hashes the asset text as loaded, so an asset whose line endings changed
         * gets a new tag. That is the correct reading: the bytes on the device
         * changed, and this field's whole job is to say so.
         */
        fun tagOf(assetText: String): String =
            java.security.MessageDigest.getInstance("SHA-256")
                .digest(assetText.toByteArray(Charsets.UTF_8))
                .take(6).joinToString("") { "%02x".format(it) }
                .let { "ngram-$it" }
    }
}

/**
 * Layer 5's post-processor: the flag, the latency guard, the shadow record and
 * the one verdict edit it is permitted to make.
 */
object AiLayer {

    /**
     * **Ships false and stays false.** §11 is explicit that Layers 5 and 6 ship
     * dormant, and nothing measured in Stage 5 authorises flipping this. The
     * held-out recovery figure is real (38 of 43 messages the engine loses to a
     * reword, from a model that never read them) and the held-out false-positive
     * figure is 3.4%, which is far too high to route real mail on. The gate is
     * `deliverable/evalset/heldout.jsonl`, which does not exist yet.
     *
     * With this false, [apply] returns its input by identity and the whole
     * layer is arithmetically invisible — the same code path as "layer deleted".
     */
    const val AI_LAYER_ENABLED = false

    /**
     * Skip the model above this and fall back to pattern-only. §11's budget.
     * The guard exists because the failure it prevents is not a slow message,
     * it is a slow *inbox*: SMS arrives in bursts after a network outage and a
     * per-message cost that is fine at one message is not fine at four hundred.
     */
    const val LATENCY_BUDGET_MS = 25L

    /** The id this layer puts on a verdict. Enumerated by Layer 4's axis map. */
    const val SIGNAL_ID = "ai-ngram-scam"

    /** Layer 4 needs to know which axis this is. It is what the message says. */
    val AXIS_HINT = EvidenceAxis.Axis.TEXT

    /**
     * One shadow-log row. Structured fields only — §11's requirement, and the
     * reason one obvious field is missing.
     *
     * [matchedAiPatterns] does **not** contain the n-grams that drove the score,
     * which is the implementation everyone reaches for first. Those n-grams are
     * two-to-five character substrings *of the message*. Logging the top ten is
     * logging up to fifty characters of the user's SMS, one fragment at a time,
     * and "it is only a substring" is not a defence — ` upi`, ` lakh`, ` otp`
     * and a bank name reconstruct the message's subject perfectly well. The
     * model's grams being public (they ship in the asset) makes it worse, not
     * better: the reader knows exactly which dictionary to match against.
     *
     * What it carries instead is the model tag and the score decile, which is
     * enough to histogram the layer's behaviour in the field, tell a drifting
     * model from a stable one, and identify which decile the disagreements live
     * in — without a single character of message content leaving the device.
     */
    data class Shadow(
        /** The app's own message id. Never the message, never a hash of it. */
        val messageId: String,
        val existingEngineVerdict: Category,
        val aiShadowVerdict: Category,
        val wouldHaveChangedRouting: Boolean,
        val matchedAiPatterns: List<String>,
        val latencyMs: Long,
        /** True when the latency guard tripped and the model did not run. */
        val skipped: Boolean,
    )

    /**
     * Runs the scorer under the latency budget and builds the shadow record.
     * Returns `null` when the layer has nothing to say, which is the common
     * case and the one that keeps [apply] an identity function.
     *
     * @param nanoTime injected so the test can trip the budget without a sleep.
     */
    fun evaluate(
        verdict: Verdict,
        text: String,
        messageId: String,
        scorer: AiScorer,
        readsAsProtected: Boolean = false,
        nanoTime: () -> Long = System::nanoTime,
    ): Shadow? {
        // The ceiling, checked before the model runs rather than after, so a
        // message this layer could not act on costs nothing to skip.
        if (!eligible(verdict, readsAsProtected)) return null

        val start = nanoTime()
        val score = scorer.score(text)
        val elapsedMs = (nanoTime() - start) / 1_000_000

        if (elapsedMs > LATENCY_BUDGET_MS) {
            // Log the miss and fall back to pattern-only. Deliberately reported
            // as `skipped` with no verdict change even though the score is in
            // hand: a budget that is honoured only when convenient is not a
            // budget, and the field data has to show how often it bites.
            return Shadow(
                messageId = messageId,
                existingEngineVerdict = verdict.category,
                aiShadowVerdict = verdict.category,
                wouldHaveChangedRouting = false,
                matchedAiPatterns = listOf(scorer.modelTag, "latency-miss"),
                latencyMs = elapsedMs,
                skipped = true,
            )
        }

        val positive = score > scorer.threshold
        return Shadow(
            messageId = messageId,
            existingEngineVerdict = verdict.category,
            aiShadowVerdict = if (positive) Category.REVIEW else verdict.category,
            wouldHaveChangedRouting = positive,
            matchedAiPatterns = listOf(scorer.modelTag, decile(score)),
            latencyMs = elapsedMs,
            skipped = false,
        )
    }

    /**
     * The ceiling. Inbox only.
     *
     * Everything else is either already filtered (Spam, Review, Blocked — this
     * layer cannot push further and has no business pulling back), or is
     * protected traffic the user is waiting for. Transactions is excluded by
     * the same reasoning as the protected check: a statistical score is not
     * grounds for moving a bank alert, and an OTP that arrives in Review is a
     * worse outcome than a scam that arrives in the Inbox.
     */
    fun eligible(verdict: Verdict, readsAsProtected: Boolean): Boolean =
        verdict.category == Category.INBOX &&
            !readsAsProtected &&
            verdict.protectedLabel == ProtectedLabel.NONE

    /**
     * Folds the shadow decision into the verdict. Identity when the flag is off,
     * when [shadow] is `null`, when the guard tripped, and when the model said
     * no — four ways to do nothing and one way to act, which is the ratio this
     * layer should have.
     *
     * The score field is deliberately left alone. This layer moves a message; it
     * does not claim to have added points to a deterministic total, and a score
     * that no longer equals the sum of the matched patterns would make the
     * "Why?" screen lie.
     */
    /**
     * @param enabled defaults to [AI_LAYER_ENABLED], so every production call
     *   site reads the constant and cannot accidentally opt in. It is a
     *   parameter at all so `layer5-verify` can run the flag-on stack and diff
     *   it against the flag-off one in a single process — the alternative was a
     *   mutable `var` flag, which is a thing that can be left on.
     */
    fun apply(verdict: Verdict, shadow: Shadow?, enabled: Boolean = AI_LAYER_ENABLED): Verdict {
        if (!enabled) return verdict
        if (shadow == null || shadow.skipped || !shadow.wouldHaveChangedRouting) return verdict
        return verdict.copy(
            category = Category.REVIEW,
            matchedPatternIds = verdict.matchedPatternIds + SIGNAL_ID,
            explanations = verdict.explanations + REASON,
            notify = false,
        )
    }

    // ------------------------------------------------------------- Layer 6
    //
    // The joint ceiling. Layer 6 turned out not to be a second scorer — §11's
    // embedding does not fit and its reference-vector mechanism, run in the
    // representation that does, buys one message out of 43 at double the
    // false-positive rate (work/STAGE6_REPORT.md). What Stage 6 ships instead is
    // the thing Stage 5 deferred: an answer to what happens when more than one
    // non-deterministic signal wants to nudge the same message.

    /**
     * Signal ids that are not a deterministic function of the message text.
     *
     * Two kinds, here for different reasons. `sender-lane-anomaly` and
     * `link-domain-novel` depend on per-install history, so the same message
     * classifies differently on two phones and differently on the same phone
     * next month. [SIGNAL_ID] is a statistical score. What they share is that a
     * field report of "this message was filed for review" is uninterpretable
     * without knowing which of them fired — which is the gap the Stage 6 hand-off
     * named, and it is a logging gap rather than a routing one.
     *
     * The structural link ids (`link-punycode`, `link-userinfo`, and the rest)
     * are deliberately absent: they are stateless functions of the URL and
     * behave identically on every device, so they need no annotation.
     *
     * Written as string literals, like `EvidenceAxis.LAYER_SIGNALS`, so that
     * deleting Layer 2 or Layer 3 does not stop this file compiling.
     */
    val NON_DETERMINISTIC_SIGNALS: Set<String> =
        setOf("sender-lane-anomaly", "link-domain-novel", SIGNAL_ID)

    /**
     * Which non-deterministic signals are already on this verdict.
     *
     * Read off the ids the verdict carries rather than plumbed through as new
     * state: the pipeline has already written down everything worth recording,
     * and a parallel channel would be one more thing to keep in sync.
     */
    fun concurring(verdict: Verdict): List<String> =
        verdict.matchedPatternIds.filter { it in NON_DETERMINISTIC_SIGNALS }

    /**
     * The merge point for N shadows evaluated against one base verdict, and the
     * assertion that goes with it:
     *
     * **N concurring non-deterministic signals route exactly where one does.**
     *
     * The tempting alternative is that agreement is evidence — two independent
     * scorers both flagging a message ought to justify Spam. Measured on the
     * held-out set, the two candidate scorers' false positives were *disjoint*:
     * two each, four together, zero in common. There is no observed agreement on
     * ham to calibrate an escalation against, and a rule fitted to n=0 is a guess
     * wearing a number. On the catch side agreement is redundant — a message both
     * flag is one either would have flagged alone. So the ceiling is a property
     * of the *class* of signal and not a budget that accumulates.
     *
     * This function exists rather than a fold because the fold is wrong. Two
     * scorers evaluated against the same base verdict are both eligible —
     * eligibility is a property of that base — and
     * `shadows.fold(verdict, ::apply)` then appends [SIGNAL_ID] **twice**:
     * [apply] re-checks `wouldHaveChangedRouting` but not [eligible], so the
     * second call cannot tell the message is already in Review. The user sees a
     * duplicated line on the "Why?" screen and an id list that no longer matches
     * what the engine matched. Taking the decision once, from the base verdict,
     * makes that unrepresentable.
     */
    fun applyAll(
        verdict: Verdict,
        shadows: List<Shadow?>,
        enabled: Boolean = AI_LAYER_ENABLED,
    ): Verdict {
        if (!enabled) return verdict
        val acting = shadows.firstOrNull {
            it != null && !it.skipped && it.wouldHaveChangedRouting
        } ?: return verdict
        return apply(verdict, acting, enabled = true)
    }

    /**
     * The fallback provenance tag, for a scorer that has no asset to hash — test
     * doubles, and any future scorer that is code rather than data. The shipped
     * path never uses it: [NGramScorer.fromJson] sets [AiScorer.modelTag] from
     * the asset's own digest, so a model swapped in the field shows up in the
     * shadow log as a tag nobody recognises rather than as `ngram-v1` forever.
     */
    const val MODEL_TAG = "ngram-v1"

    /**
     * Worded as an admission rather than an accusation. The user is being shown
     * a message the deterministic rules had no complaint about, on the strength
     * of a statistical resemblance, and the sentence should say so.
     */
    const val REASON =
        "Filed for review: nothing in this message matched a known scam rule, " +
            "but its wording closely resembles messages that are scams"

    /** Score decile, clamped. Coarse on purpose — see [Shadow]. */
    private fun decile(score: Double): String {
        val d = (score * 10).toInt().coerceIn(-9, 9)
        return "score-d$d"
    }
}
