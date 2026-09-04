package com.messages.protection

/**
 * Layer 3 — structural link analysis.
 *
 * The gap this closes: a DLT-registered header pointing at a *clean-looking*
 * custom domain rides the trusted lane. `statements-hdfc.co.in/j/7761` is not a
 * shortener, not a suspicious TLD, not an IP literal — the shipped [LinkAnalyzer]
 * sees nothing in it — and a bank-worded message carrying it is delivered to
 * Transactions with no banner at all. URL structure is language-independent,
 * which is worth more than usual in a five-language inbox.
 *
 * Two arms, and they are not equally strong. Read the honesty section of
 * `work/STAGE3_REPORT.md` before assuming either one is load-bearing.
 *
 *  - **Structure** ([analyze]). What the shape of a URL says without resolving
 *    it. No DNS, no WHOIS, no fetch, in any flag state — every rule here is a
 *    string operation over the URL the [Normalizer] already extracted.
 *  - **Novelty** ([novelty]). The first time a sender with an established
 *    history points at a domain it has never used before. Offline, per-sender,
 *    and the arm that actually reaches the gap above — a clean domain is clean
 *    precisely because its structure gives nothing away.
 *
 * ### Why this needs no `ProtectionEngine` edit
 *
 * Same architecture as [SenderLane]: a post-processor over a finished [Verdict].
 * Signals are emitted as [LinkAnalyzer.LinkSignal], the shipped type, so ids and
 * descriptions flow into `matchedPatternIds` and `explanations` exactly as the
 * engine's own link signals do and the "Why?" screen needs no change. Reversal
 * is deleting this file, dropping one table and removing one call.
 *
 * ### Four safety properties, all arithmetic rather than hope
 *
 *  1. **Cold-start safe on the novelty arm.** Below [MIN_DOMAIN_OBSERVATIONS]
 *     recorded domains a sender has no domain history, so [novelty] returns
 *     `null`. A fresh install has seen no domain from anyone, so the arm is
 *     inert until history exists — the failure mode it is designed against.
 *  2. **A new first-party domain is a nudge, not a punishment.**
 *     [NOVELTY_WEIGHT] (3) is below the engine's `reviewAt` (5), so the first
 *     sighting of `hdfcbank.com → hdfc-statements.com` cannot move a message on
 *     its own. It needs structural corroboration. And it is *recoverable*: the
 *     domain is recorded on delivery, so the second message from it is silent.
 *  3. **This layer can never reach Spam.** The additive arm acts only on
 *     verdicts scoring below `reviewAt`, and contributes at most
 *     [MAX_CONTRIBUTION]. `(reviewAt - 1) + MAX_CONTRIBUTION` = 9, below the
 *     shipped `spamAt` of 10. That is an arithmetic ceiling, not a clamp, and
 *     `the_layer_can_never_reach_spam` asserts the inequality directly.
 *  4. **Protected is never filtered.** On a verdict that is Protected — or that
 *     merely *reads* as protected, which is Layer 2's hard-won distinction —
 *     the only change permitted is raising [Verdict.fraudWarningBanner].
 *     Category and score are untouched.
 */
object LinkStructure {

    /**
     * The most this layer may add to any score.
     *
     * Chosen so that `(reviewAt - 1) + MAX_CONTRIBUTION < spamAt` on the shipped
     * Sensitivity — 4 + 5 = 9 < 10. A message this layer touches can therefore
     * reach Review and stop there, whatever combination of signals fires.
     * Promotion to Spam requires independent evidence axes, which is Layer 4's
     * rule, not this one's.
     */
    const val MAX_CONTRIBUTION = 5

    /**
     * Additive evidence for a domain this sender has never used. Below the
     * engine's default `reviewAt = 5` **on purpose** — see property 2. This is
     * the number that keeps a genuine bank migrating to a new domain out of
     * Review on its first message.
     */
    const val NOVELTY_WEIGHT = 3

    /**
     * Distinct domain observations a sender needs before "a domain I have never
     * seen from you" means anything. Below this the novelty arm emits nothing.
     *
     * Deliberately the same number as [SenderLane.MIN_OBSERVATIONS]; the two
     * files stay independent so either layer can be reverted alone, and
     * `the_two_cold_start_thresholds_agree` fails the build if they drift.
     */
    const val MIN_DOMAIN_OBSERVATIONS = 5

    /**
     * Past this many distinct domains a sender has no meaningful domain lane —
     * a marketing gateway that fans out over dozens of tracking hosts is not
     * telling you anything by using one more. [novelty] goes silent rather than
     * firing on every message, and the store stops inserting.
     *
     * This is also the row cap the `sender_lane` table did not need: families
     * are bounded by the shipped pattern library, domains are bounded by
     * whatever an attacker cares to send.
     */
    const val MAX_DOMAINS_PER_SENDER = 32

    const val NOVELTY_ID = "link-domain-novel"

    /**
     * Second-level suffixes that are registry namespaces rather than
     * registrable names, so `statements-hdfc.co.in` reads as
     * `statements-hdfc` and not as `co`.
     *
     * ponytail: a hand-list, not the Public Suffix List. It covers the Indian
     * and common-Commonwealth namespaces this inbox actually sees. The failure
     * mode of a miss is mild and one-directional — an unlisted suffix makes the
     * registrable label look like `co`-shaped noise, which is short and
     * hyphen-free, so signals go quiet rather than loud. Upgrade path: ship a
     * trimmed PSL as a resource if the miss rate ever shows up in the held-out
     * set.
     */
    private val REGISTRY_SUFFIXES = setOf(
        "co", "gov", "nic", "ac", "org", "net", "com", "edu", "mil",
        "res", "gen", "firm", "ind",
    )

    /**
     * A path segment that looks like a generated base62 key: `RfcKL`, `gUkA5H`.
     * Mixed case is the discriminator and it is checked in code rather than in
     * the regex, both to keep the regex trivially safe and because a
     * `\b`-free, lookaround-free pattern behaves the same on Android ICU as it
     * does on the OpenJDK this is tested on.
     */
    private val TOKEN_SHAPE = Regex("""^[A-Za-z0-9_-]{5,16}$""")

    /** An opaque numeric path code: `7761`, `638812`. */
    private val DIGIT_SEGMENT = Regex("""^\d{4,}$""")

    /** Exposed so a test can push both through the shipped [SafeRegexPolicy]. */
    val REGEXES: List<String> = listOf(TOKEN_SHAPE.pattern, DIGIT_SEGMENT.pattern)

    /** A URL taken apart by string operations only. Nothing is resolved. */
    data class Parts(
        val host: String,
        /** The registrable label: `statements-hdfc` in `statements-hdfc.co.in`. */
        val registrable: String,
        /** Labels to the left of the registrable one. */
        val subdomains: List<String>,
        val pathSegments: List<String>,
        val hasUserInfo: Boolean,
        val port: String?,
    )

    fun parse(url: String): Parts? {
        val noScheme = url.substringAfter("://", url)
        val authority = noScheme.substringBefore('/')
        val path = noScheme.substringAfter('/', "")
        // userinfo lives in the authority only. `medium.com/@rahul/...` is a
        // path with an '@' in it and is a perfectly ordinary link — a real
        // corpus message uses exactly that shape.
        val hasUserInfo = '@' in authority
        val hostPort = authority.substringAfterLast('@')
        val host = hostPort.substringBefore(':').lowercase().removePrefix("www.").trimEnd('.')
        val port = hostPort.substringAfter(':', "").ifBlank { null }
        if (host.isBlank()) return null

        val labels = host.split('.').filter { it.isNotBlank() }
        val registrableIndex = when {
            labels.size >= 3 && labels[labels.size - 2] in REGISTRY_SUFFIXES &&
                labels[labels.size - 2].length <= 3 -> labels.size - 3
            labels.size >= 2 -> labels.size - 2
            else -> 0
        }
        return Parts(
            host = host,
            registrable = labels.getOrElse(registrableIndex) { "" },
            subdomains = labels.take(maxOf(registrableIndex, 0)),
            pathSegments = path.split('/').filter { it.isNotBlank() },
            hasUserInfo = hasUserInfo,
            port = port,
        )
    }

    /**
     * The structural signals for one message's URLs.
     *
     * Every rule here was written after sweeping the 147 URLs in the shipped
     * corpus, which is why several are conjunctions rather than the simpler
     * rules the brief suggests. A genuine RedBus ticket confirmation in that
     * corpus reads `redbus.in/t/638812` — a short domain with a short numeric
     * path code, structurally indistinguishable from `statements-hdfc.co.in/j/7761`
     * on path shape alone. Path shape therefore never fires by itself; it only
     * amplifies a domain that is already odd. See the report.
     */
    fun analyze(urls: List<String>): List<LinkAnalyzer.LinkSignal> {
        val signals = mutableListOf<LinkAnalyzer.LinkSignal>()
        for (url in urls) {
            val p = parse(url) ?: continue
            val hyphens = p.registrable.count { it == '-' }
            val tiny = p.registrable.length <= 4
            val digitInLabel = p.registrable.any { it.isDigit() }
            val opaque = p.pathSegments.any { seg ->
                TOKEN_SHAPE.matches(seg) && seg.any { it.isUpperCase() } && seg.any { it.isLowerCase() }
            }

            if (p.hasUserInfo) {
                signals += LinkAnalyzer.LinkSignal(
                    "link-userinfo", 4,
                    "Link hides its real destination behind a \"user@\" prefix",
                    isPhishy = true,
                )
            }
            if (p.port != null && p.port != "80" && p.port != "443") {
                signals += LinkAnalyzer.LinkSignal(
                    "link-nonstandard-port", 3,
                    "Link points at an unusual port (:${p.port}) — real services do not",
                    isPhishy = true,
                )
            }
            if (hyphens >= 1) {
                signals += LinkAnalyzer.LinkSignal(
                    "link-hyphen-domain", if (hyphens >= 2) 3 else 2,
                    "Domain name is stitched together with hyphens (${p.registrable})",
                )
            }
            if (p.subdomains.size >= 3) {
                signals += LinkAnalyzer.LinkSignal(
                    "link-deep-subdomain", 3,
                    "Link buries the real domain behind ${p.subdomains.size} sub-domains",
                    isPhishy = true,
                )
            }
            if (opaque) {
                signals += LinkAnalyzer.LinkSignal(
                    "link-opaque-path", 3,
                    "Link path is a generated code, not a readable page name",
                )
            }
            // The hu2.in/RfcKL shape. hu2.in reached the shipped shortener list
            // only because a human saw it in live loan spam in 2026-07 and added
            // it by hand; this rule is what recognises the *next* one without
            // waiting for that.
            if (tiny && (digitInLabel || opaque)) {
                signals += LinkAnalyzer.LinkSignal(
                    "link-tiny-domain-code", 4,
                    "Very short domain (${p.host}) serving a code — an unlisted link shortener",
                    isShortener = true, isPhishy = true,
                )
            }
            // Digit-heavy paths only count against a domain that is already
            // odd. Alone this rule flags real ticket and tracking links.
            if ((hyphens >= 1 || tiny || digitInLabel) &&
                p.pathSegments.any { DIGIT_SEGMENT.matches(it) }
            ) {
                signals += LinkAnalyzer.LinkSignal(
                    "link-digit-path", 2,
                    "Odd domain with a numeric-code path",
                )
            }
        }
        return signals
    }

    /** One `sender_domain` row. */
    data class Observation(val domain: String, val count: Int, val lastSeenMillis: Long)

    /** All rows for one sender. Built by the DAO; see the schema spec. */
    data class DomainProfile(val senderId: String, val observations: List<Observation>) {
        val domains: Set<String> = observations.map { it.domain }.toSet()
        val total: Int = observations.sumOf { it.count }

        /**
         * False on a fresh install, for every rarely-heard-from sender, and for
         * any sender that has reached [MAX_DOMAINS_PER_SENDER] distinct domains
         * — the store stops inserting at the cap, so a sender sitting *on* it
         * has an incomplete history and must not be reasoned from.
         */
        val established: Boolean =
            total >= MIN_DOMAIN_OBSERVATIONS && domains.size < MAX_DOMAINS_PER_SENDER

        companion object {
            fun empty(senderId: String) = DomainProfile(senderId, emptyList())
        }
    }

    data class Novelty(val domain: String, val known: Set<String>, val observations: Int) {
        /** The "Why?" line. Reads as a sentence because a user reads it. */
        val reason: String =
            "This sender has always linked to ${known.sorted().take(3).joinToString(", ")}" +
                (if (known.size > 3) " and ${known.size - 3} more" else "") +
                " ($observations messages); this one links to $domain for the first time"
    }

    /**
     * The novelty signal. `null` means "say nothing" — the answer for every
     * sender on a fresh install, for every link-free message, and for every
     * domain this sender has used before.
     *
     * Only the *first* URL is considered. A message with several links is
     * already unusual enough that the structural arm will have spoken; scoring
     * novelty once per link would let a single message stack the weight.
     */
    fun novelty(urls: List<String>, profile: DomainProfile): Novelty? {
        if (!profile.established) return null
        val domain = urls.firstNotNullOfOrNull { parse(it)?.host } ?: return null
        if (domain in profile.domains) return null
        return Novelty(domain, profile.domains, profile.total)
    }

    /**
     * The domain to record for this verdict, or `null` if it must not be
     * recorded at all.
     *
     * Only messages the engine let through are recorded, on the same reasoning
     * as [SenderLane.observationFamily]: without it a spoofer establishes a
     * domain history out of five caught scams and walks the sixth past the
     * signal. History an attacker can write is not history.
     */
    fun observationDomain(verdict: Verdict, urls: List<String>): String? {
        val trusted = !verdict.dangerous &&
            verdict.category != Category.SPAM &&
            verdict.category != Category.REVIEW &&
            verdict.category != Category.BLOCKED
        if (!trusted) return null
        return urls.firstNotNullOfOrNull { parse(it)?.host }
    }

    /**
     * Folds both arms into a verdict. Identity when nothing fired, which is what
     * makes the flag-off and no-history proofs exact rather than approximate.
     *
     * @param readsAsProtected Layer 2's distinction, passed in rather than
     *   recomputed so the two files stay independently revertible. The engine
     *   withholds a protected label on sender type — a train PNR confirmation
     *   from a 10-digit number matches the travel family but is denied the
     *   `TRAVEL` label — and a message a user is waiting for must not be
     *   filtered just because the engine declined to label it. When Layer 2 is
     *   not applied, pass `false`; [Verdict.protectedLabel] still protects.
     * @param reviewAt the engine's current `Sensitivity.reviewAt`, so the
     *   Sensitivity slider keeps governing rather than being second-guessed here.
     */
    fun apply(
        verdict: Verdict,
        signals: List<LinkAnalyzer.LinkSignal>,
        novelty: Novelty?,
        reviewAt: Int,
        readsAsProtected: Boolean = false,
    ): Verdict {
        if (signals.isEmpty() && novelty == null) return verdict
        // Already at or past the ceiling this layer may push to.
        if (verdict.category == Category.SPAM || verdict.category == Category.BLOCKED) return verdict

        val ids = verdict.matchedPatternIds +
            signals.map { it.id } + (if (novelty != null) listOf(NOVELTY_ID) else emptyList())
        val explanations = verdict.explanations +
            signals.map { it.description } + listOfNotNull(novelty?.reason)

        // Warn, never filter — for anything that reads as protected, not only
        // for what the engine labelled protected. This is the arm that closes
        // the gap in the brief: a registered bank header pointing at a
        // structurally odd domain still reaches Transactions, but wearing the
        // shipped fraud banner instead of arriving silently trusted.
        //
        // Novelty alone is deliberately not enough to raise it. A bank running
        // a campaign microsite, or migrating domains, is the commonest cause of
        // a first sighting, and a fraud banner on a genuine statement is the
        // alarming-rather-than-useful failure Stage 2 flagged in its own banner
        // arm. The banner needs something about the link itself to be wrong.
        if (readsAsProtected || verdict.protectedLabel != ProtectedLabel.NONE) {
            if (signals.isEmpty()) return verdict
            return verdict.copy(
                fraudWarningBanner = true,
                matchedPatternIds = ids,
                explanations = explanations,
            )
        }

        // The engine has already routed anything at or above reviewAt. The only
        // direction left from there is Spam, which this layer is not entitled to
        // reach, so it adds nothing rather than adding an unusable point.
        if (verdict.score >= reviewAt) return verdict

        val added = minOf(
            signals.sumOf { it.weight } + (if (novelty != null) NOVELTY_WEIGHT else 0),
            MAX_CONTRIBUTION,
        )
        val score = verdict.score + added
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
}
