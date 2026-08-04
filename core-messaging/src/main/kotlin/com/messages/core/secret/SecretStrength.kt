package com.messages.core.secret

/**
 * V2-7: credential strength policy for the locked space (pure JVM — no
 * Android deps, no resources; this module decides and the UI layer says).
 *
 * ## Why a blocklist and not just a longer floor
 *
 * PBKDF2-HMAC-SHA256 at [SecretCrypto.ITERATIONS] slows a guess; it cannot add
 * entropy to the credential. The verifier is a salted hash and it travels
 * inside a backup ([SecretSpace.authForBackup]), so anyone holding a backup
 * file can enumerate offline at their own pace, with the escalating cooldown
 * ([SecretCooldown]) doing nothing at all for them. Order of magnitude on one
 * modern GPU at 600k iterations — roughly 5–10k guesses per second:
 *
 * | Credential          | Space  | Exhaustive search |
 * |---------------------|--------|-------------------|
 * | 4-digit PIN         | 10^4   | ~2 seconds        |
 * | 6-digit PIN         | 10^6   | ~3 minutes        |
 * | 8-digit PIN         | 10^8   | ~5 hours          |
 * | 8-char mixed passwd | ~10^15 | centuries         |
 *
 * A longer PIN therefore does not make the space safe against someone holding
 * a backup file, and implying otherwise would be the real defect. Two
 * conclusions follow, and both are implemented here:
 *
 *  1. **A blocklist buys more than a digit does.** Markert et al., *"This PIN
 *     Can Be Easily Guessed"* (IEEE S&P 2020), found six-digit PINs barely
 *     outperform four-digit ones in practice because user choice clusters so
 *     hard, and that blocklisting is the effective lever. An attacker does not
 *     start at 000000; they start at 123456. Cutting the dense head off that
 *     distribution is worth more than the 100× the extra digits nominally buy.
 *  2. **The floors move, but nobody is locked out.** NIST SP 800-63B-4 requires
 *     verifiers to screen new secrets against a blocklist and explicitly
 *     *prohibits* forcing rotation without evidence of compromise. So these
 *     floors apply at setup only. An existing credential keeps working, and
 *     [SecretSpace.isCredentialWeak] raises a nudge instead — the nudge is the
 *     whole enforcement mechanism.
 *
 * The honest summary, which the setup copy also states plainly: a numeric PIN
 * is a convenience credential. A password is the one that survives disclosure
 * of the verifier, which is why setup recommends it.
 *
 * Credentials arrive as [CharArray] and are never copied into an immutable
 * String here — every rule below indexes the array in place, so nothing
 * un-zeroable is left on the heap.
 */
object SecretStrength {

    /**
     * Digits required of a new PIN. Six matches the platform convention users
     * already know — iOS moved its default to six in 2015 — so it is not a
     * floor anyone has to be taught.
     */
    const val MIN_PIN_DIGITS = 6

    /**
     * Characters required of a new password. NIST SP 800-63B-4 sets 8 as the
     * floor for a secret that is one factor among several, which is what this
     * is: it gates a space on an already-unlocked, already-authenticated
     * device. The 15-character floor in the same document is for a secret that
     * is the *only* thing between an anonymous remote attacker and an account;
     * applying it here would just push people back to a PIN.
     */
    const val MIN_PASSWORD_CHARS = 8

    /**
     * Dots required of a new pattern. Four dots is the platform convention but
     * it is only 1,624 distinct patterns — fewer than a 4-digit PIN, and the
     * one credential kind whose raw number is indefensible. Five dots reaches
     * 7,152, and longer patterns run into the hundreds of thousands.
     *
     * The *input* minimum (`PATTERN_MIN_DOTS`, in the UI layer) stays at four
     * on purpose: it answers "is this gesture finished", which the unlock
     * screen still needs in order to accept a legacy four-dot pattern. This
     * constant answers "is this strong enough to create", and only setup asks.
     */
    const val MIN_PATTERN_DOTS = 5

    /**
     * PINs refused regardless of length. Everything with structure — repeats,
     * runs, keypad geometry, dates — is caught by the rules below rather than
     * enumerated; this list holds only the ones with no structure to detect,
     * which no rule can see.
     *
     * Sources: the Data Genetics analysis of ~3.4M breached PINs (whose top 20
     * four-digit codes cover roughly 27% of the corpus) and the user-chosen
     * distribution measured in Markert et al.
     */
    private val LISTED_PINS = arrayOf(
        // Four-digit, empirical top-20, minus the ones a rule already catches.
        "1004", "6969", "1313", "4711", "1379", "2580", "1230", "0852",
        // Six-digit, the recurring heads of the same distribution.
        "789456", "159753", "147258", "121314", "142536", "135790",
        "778899", "159357", "753951", "456123", "987123", "258456",
    )

    /**
     * Passwords refused regardless of length — the perennial head of every
     * breach corpus, in the forms that clear an 8-character floor.
     */
    private val LISTED_PASSWORDS = arrayOf(
        "password", "password1", "password12", "password123", "passw0rd",
        "p@ssword", "p@ssw0rd", "qwertyui", "qwerty123", "qwertyuiop",
        "iloveyou", "sunshine", "princess", "football", "baseball",
        "welcome1", "welcome123", "abc12345", "letmein1", "monkey12",
        "dragon12", "trustno1", "superman", "starwars", "whatever",
        "computer", "michael1", "shadow12", "master12", "changeme",
        "secret12", "admin123", "administrator",
    )

    // ---- Public policy ----

    /**
     * Whether a PIN is refused at setup: below the floor, non-numeric, or
     * somewhere an attacker reaches early.
     */
    fun isWeakPin(pin: CharArray): Boolean {
        if (pin.size < MIN_PIN_DIGITS) return true
        if (!pin.all { it.isDigit() }) return true
        return isGuessablePin(pin)
    }

    /**
     * The distribution rules alone, with no length requirement. Split out from
     * [isWeakPin] so an all-digit password answers to the same rules without
     * also inheriting the PIN length floor.
     */
    fun isGuessablePin(pin: CharArray): Boolean {
        if (pin.isEmpty()) return false
        if (isListed(LISTED_PINS, pin, ignoreCase = false)) return true
        if (isSingleRepeatedChar(pin)) return true
        if (isConsecutiveRun(pin)) return true
        if (isRepeatedBlock(pin)) return true
        if (isKeypadLine(pin)) return true
        if (isDateLike(pin)) return true
        return false
    }

    /** Whether a password is refused at setup. */
    fun isWeakPassword(password: CharArray): Boolean {
        if (password.size < MIN_PASSWORD_CHARS) return true
        if (isListed(LISTED_PASSWORDS, password, ignoreCase = true)) return true
        // An all-digit password is a PIN wearing a different hat, so it answers
        // to the PIN rules rather than escaping them by being typed into the
        // other field.
        if (password.all { it.isDigit() } && isGuessablePin(password)) return true
        if (isSingleRepeatedChar(password)) return true
        if (isConsecutiveRun(password)) return true
        if (isRepeatedBlock(password)) return true
        return false
    }

    /**
     * Whether a pattern (3×3 grid cell indices 0..8, in draw order) is refused
     * at setup.
     *
     * Note what is *not* here: a straight-line check. Three collinear dots is
     * the longest line a 3×3 grid holds, so [MIN_PATTERN_DOTS] already makes a
     * pure line impossible — a separate rule for it would never fire. What is
     * still reachable at five-plus dots, and is what people actually draw, is
     * the *simple shape*: one or two strokes (a line, an L, a V), plus the two
     * exhaustive sweeps. Løge's analysis of ~4,000 collected patterns
     * (PASSWORDS '15) found exactly this clustering, along with a strong bias
     * toward starting in the top-left corner.
     */
    fun isWeakPattern(cells: List<Int>): Boolean {
        if (cells.size < MIN_PATTERN_DOTS) return true
        // One or two strokes: an L, a V, a hook. Trivial to shoulder-surf and
        // trivial to read off a smudged screen.
        if (directionChanges(cells) <= 1) return true
        // The serpentine (0-1-2-5-4-3-6-7-8) and its reverse: every dot, in
        // reading order. The most-drawn "complex" pattern there is.
        val serpentine = listOf(0, 1, 2, 5, 4, 3, 6, 7, 8)
        if (cells == serpentine || cells == serpentine.asReversed()) return true
        // A pure perimeter walk, from any corner, in either direction.
        val ring = listOf(0, 1, 2, 5, 8, 7, 6, 3)
        if (cells.size == ring.size && isRotationOf(cells, ring)) return true
        return false
    }

    /**
     * The same policy applied to a pattern already normalized to its credential
     * form ("0-1-4-7-8"), which is how [SecretCrypto.setupError] sees it and how
     * it arrives back at an unlock. Malformed input is not judged here — it
     * cannot have been created by [SecretCrypto.patternToCredential], so
     * refusing it as "weak" would report the wrong reason.
     */
    fun isWeakPatternCredential(credential: CharArray): Boolean {
        val cells = ArrayList<Int>(credential.size / 2 + 1)
        var i = 0
        while (i < credential.size) {
            val ch = credential[i]
            if (!ch.isDigit()) return false
            cells.add(ch - '0')
            i++
            if (i < credential.size) {
                if (credential[i] != '-') return false
                i++
                if (i >= credential.size) return false
            }
        }
        return isWeakPattern(cells)
    }

    // ---- Structural rules (index-only; no substring allocation) ----

    private fun isListed(list: Array<String>, c: CharArray, ignoreCase: Boolean): Boolean =
        list.any { entry ->
            entry.length == c.size &&
                (0 until entry.length).all { i -> entry[i].equals(c[i], ignoreCase) }
        }

    private fun isSingleRepeatedChar(c: CharArray): Boolean =
        c.size > 1 && c.all { it == c[0] }

    /** "123456", "654321", "abcdef" — each character one step from the last. */
    private fun isConsecutiveRun(c: CharArray): Boolean {
        if (c.size < 3) return false
        val step = c[1] - c[0]
        if (step != 1 && step != -1) return false
        for (i in 2 until c.size) if (c[i] - c[i - 1] != step) return false
        return true
    }

    /** "121212", "123123", "112233" — a short block tiled to fill the length. */
    private fun isRepeatedBlock(c: CharArray): Boolean {
        for (block in 1..c.size / 2) {
            if (c.size % block != 0) continue
            var tiled = true
            for (i in block until c.size) {
                if (c[i] != c[i % block]) {
                    tiled = false
                    break
                }
            }
            if (tiled) return true
        }
        // "112233" / "111222" — an ascending run with each element doubled.
        if (c.size >= 4 && c.size % 2 == 0) {
            var doubled = true
            for (i in c.indices step 2) {
                if (c[i] != c[i + 1]) {
                    doubled = false
                    break
                }
            }
            if (doubled) {
                val step = c[2] - c[0]
                if (step == 1 || step == -1) {
                    var run = true
                    for (i in 4 until c.size step 2) {
                        if (c[i] - c[i - 2] != step) {
                            run = false
                            break
                        }
                    }
                    if (run) return true
                }
            }
        }
        return false
    }

    /**
     * Straight lines traced on the phone keypad — the shapes a finger makes
     * when the brain is not choosing digits. "2580" is the middle column and is
     * among the most-chosen PINs there is, precisely because it does not *look*
     * like a pattern once written down.
     */
    private fun isKeypadLine(pin: CharArray): Boolean {
        if (pin.size < 3) return false
        // Keypad geometry: 1..9 in a 3×3 grid, 0 sitting below the 8.
        val rows = IntArray(pin.size)
        val cols = IntArray(pin.size)
        for (i in pin.indices) {
            val ch = pin[i]
            when {
                ch == '0' -> {
                    rows[i] = 3; cols[i] = 1
                }
                ch in '1'..'9' -> {
                    val n = ch - '1'
                    rows[i] = n / 3; cols[i] = n % 3
                }
                else -> return false
            }
        }
        val dr = rows[1] - rows[0]
        val dc = cols[1] - cols[0]
        if (dr == 0 && dc == 0) return false
        for (i in 2 until pin.size) {
            if (rows[i] - rows[i - 1] != dr || cols[i] - cols[i - 1] != dc) return false
        }
        return true
    }

    /**
     * Dates — where the 19xx/20xx band in every PIN corpus comes from. Covers a
     * bare year and the six-digit DDMMYY / MMDDYY / YYYYMM forms. A birthday is
     * the single most common source of a "random" six-digit code, and unlike a
     * random one it is *knowable* about the target rather than guessable.
     */
    private fun isDateLike(pin: CharArray): Boolean {
        fun two(at: Int) = (pin[at] - '0') * 10 + (pin[at + 1] - '0')
        if (!pin.all { it.isDigit() }) return false
        return when (pin.size) {
            4 -> two(0) * 100 + two(2) in 1900..2099
            6 -> {
                val a = two(0)
                val b = two(2)
                val ddmm = a in 1..31 && b in 1..12
                val mmdd = a in 1..12 && b in 1..31
                val yyyymm = a * 100 + b in 1900..2099 && two(4) in 1..12
                ddmm || mmdd || yyyymm
            }
            else -> false
        }
    }

    /**
     * How many times the drawing hand changes direction. Zero is a straight
     * line, one is an L or a V. Steps are reduced to their primitive direction
     * (a two-cell hop north counts as the same heading as a one-cell hop), so
     * "kept going the same way" means geometrically, not by cell count.
     */
    private fun directionChanges(cells: List<Int>): Int {
        if (cells.size < 3) return 0
        var changes = 0
        var prR = 0
        var prC = 0
        for (i in 1 until cells.size) {
            var dr = cells[i] / 3 - cells[i - 1] / 3
            var dc = cells[i] % 3 - cells[i - 1] % 3
            val g = gcd(kotlin.math.abs(dr), kotlin.math.abs(dc))
            if (g > 1) {
                dr /= g
                dc /= g
            }
            if (i > 1 && (dr != prR || dc != prC)) changes++
            prR = dr
            prC = dc
        }
        return changes
    }

    private tailrec fun gcd(a: Int, b: Int): Int = if (b == 0) a else gcd(b, a % b)

    /** Whether [cells] is [ring] started at a different point, in either direction. */
    private fun isRotationOf(cells: List<Int>, ring: List<Int>): Boolean {
        fun matches(order: List<Int>): Boolean {
            val doubled = order + order
            return ring.indices.any { start ->
                doubled.subList(start, start + ring.size) == cells
            }
        }
        return matches(ring) || matches(ring.asReversed())
    }
}
