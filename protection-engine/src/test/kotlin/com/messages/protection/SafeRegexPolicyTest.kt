package com.messages.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * R-21 — the regex gate for untrusted patterns.
 *
 * The acceptance cases matter as much as the rejections: this policy runs over
 * the bundled library and over every rule a user has already saved, so being
 * over-strict silently breaks working filters.
 */
class SafeRegexPolicyTest {

    /** Runs [block], returning the rejection message; fails if it was accepted. */
    private inline fun <reified T : Throwable> rejectionMessage(block: () -> Unit): String {
        try {
            block()
        } catch (e: Throwable) {
            if (e is T) return e.message.orEmpty()
            throw e
        }
        fail("expected ${T::class.simpleName} but nothing was thrown")
        return ""
    }

    // ---- rejections -----------------------------------------------------

    @Test
    fun `rejects the classic nested quantifier`() {
        assertFalse(SafeRegexPolicy.accepts("(a+)+"))
        assertFalse(SafeRegexPolicy.accepts("(a*)*"))
        assertFalse(SafeRegexPolicy.accepts("(a+)*"))
        assertFalse(SafeRegexPolicy.accepts("([a-z]+)+$"))
    }

    @Test
    fun `rejects nested quantifiers through an intermediate group`() {
        // ((a+))+ — the inner quantifier is two levels down, but the outer
        // repetition still multiplies it.
        assertFalse(SafeRegexPolicy.accepts("((a+))+"))
    }

    @Test
    fun `rejects a quantified group whose quantifier is a bounded repeat`() {
        assertFalse(SafeRegexPolicy.accepts("(a+){2,50}"))
    }

    @Test
    fun `rejects backreferences`() {
        assertFalse(SafeRegexPolicy.accepts("(abc)\\1"))
        assertFalse(SafeRegexPolicy.accepts("(?<x>a)\\k<x>"))
    }

    @Test
    fun `rejects lookbehind`() {
        assertFalse(SafeRegexPolicy.accepts("(?<=INR )\\d+"))
        assertFalse(SafeRegexPolicy.accepts("(?<!x)abc"))
    }

    @Test
    fun `rejects over-long patterns`() {
        assertFalse(SafeRegexPolicy.accepts("a".repeat(SafeRegexPolicy.MAX_REGEX_LENGTH + 1)))
    }

    @Test
    fun `rejects absurd repetition bounds`() {
        assertFalse(SafeRegexPolicy.accepts("a{1,999999}"))
    }

    @Test
    fun `rejects empty and malformed patterns`() {
        assertFalse(SafeRegexPolicy.accepts(""))
        assertFalse(SafeRegexPolicy.accepts("(unclosed"))
        assertFalse(SafeRegexPolicy.accepts("[unterminated"))
        assertFalse(SafeRegexPolicy.accepts("trailing\\"))
    }

    @Test
    fun `rejection message is fit to show a user`() {
        val message = rejectionMessage<SafeRegexPolicy.Rejected> {
            SafeRegexPolicy.requireAccepted("(a+)+")
        }
        assertTrue("unhelpful message: $message", message.contains("repeats"))
    }

    // ---- acceptances ----------------------------------------------------

    @Test
    fun `accepts ordinary patterns`() {
        assertTrue(SafeRegexPolicy.accepts("otp|one.time.password"))
        assertTrue(SafeRegexPolicy.accepts("\\b\\d{4,8}\\b"))
        assertTrue(SafeRegexPolicy.accepts("(?:kyc|know your customer)"))
        assertTrue(SafeRegexPolicy.accepts("(?i)urgent"))
        assertTrue(SafeRegexPolicy.accepts("win(ner)?"))
        assertTrue(SafeRegexPolicy.accepts("rs\\.?\\s*[0-9,]+"))
    }

    @Test
    fun `accepts lookahead — only lookbehind is refused`() {
        assertTrue(SafeRegexPolicy.accepts("(?=.*bank).*"))
        assertTrue(SafeRegexPolicy.accepts("(?!.*legit).*"))
    }

    @Test
    fun `a quantified group without inner repetition is fine`() {
        assertTrue(SafeRegexPolicy.accepts("(abc)+"))
        assertTrue(SafeRegexPolicy.accepts("(ab|cd)*"))
    }

    /**
     * An outer `?` matches its operand at most once, so there is no input to
     * split between the two quantifiers and no exponential blowup. This is the
     * shape used by `lottery-won-amount` and several other bundled patterns —
     * rejecting it would be a false positive with no security benefit.
     */
    @Test
    fun `an optional group containing a quantifier is accepted`() {
        assertTrue(SafeRegexPolicy.accepts("(\\s?(lakh|lakhs|crore|cr|k))?"))
        assertTrue(SafeRegexPolicy.accepts("(\\.\\d+)?"))
        assertTrue(SafeRegexPolicy.accepts("(a+)?"))
        assertTrue(SafeRegexPolicy.accepts("(a+){0,1}"))
        assertTrue(SafeRegexPolicy.accepts("(a+){1}"))
    }

    @Test
    fun `the real bundled lottery pattern is accepted`() {
        assertTrue(
            SafeRegexPolicy.accepts(
                "\\b(won|win|winner|winning|congratulations?|congrats)\\b.{0,60}" +
                    "(₹|rs\\.?|inr|\\$|usd)\\s*[\\d,]+(\\.\\d+)?(\\s?(lakh|lakhs|crore|cr|k))?"
            )
        )
    }

    @Test
    fun `an expanding outer quantifier is still refused`() {
        assertFalse(SafeRegexPolicy.accepts("(a+){2,}"))
        assertFalse(SafeRegexPolicy.accepts("(a+){2}"))
    }

    @Test
    fun `escaped metacharacters are not mistaken for quantifiers`() {
        assertTrue(SafeRegexPolicy.accepts("\\(\\+91\\)\\*"))
        assertTrue(SafeRegexPolicy.accepts("100% off"))
    }

    @Test
    fun `quantifier-like characters inside a class are literal`() {
        assertTrue(SafeRegexPolicy.accepts("[+*?]+"))
        assertTrue(SafeRegexPolicy.accepts("[()]+"))
    }

    // ---- the runtime budget ---------------------------------------------

    @Test
    fun `budget aborts a pathological match instead of hanging`() {
        // Compiled directly, bypassing the policy — this is the pattern that
        // would otherwise run effectively forever.
        val evil = Regex("(a+)+b")
        val input = "a".repeat(80)

        val elapsed = kotlin.system.measureTimeMillis {
            assertFalse(evil.containsMatchWithin(input, maxReads = 200_000))
        }
        // Without the budget this does not finish in any practical time.
        assertTrue("budgeted match took ${elapsed}ms", elapsed < 5_000)
    }

    @Test
    fun `budget does not disturb ordinary matching`() {
        val ok = Regex("\\b\\d{6}\\b")
        assertTrue(ok.containsMatchWithin("your otp is 483920 do not share"))
        assertFalse(ok.containsMatchWithin("no code here"))
    }

    @Test
    fun `budgeted sequence reports the delegate length and content`() {
        val seq = BudgetedCharSequence("hello", maxReads = 100)
        assertEquals(5, seq.length)
        assertEquals("hello", seq.toString())
        assertEquals('h', seq[0])
    }

    @Test
    fun `budgeted sequence throws once the cap is passed`() {
        val seq = BudgetedCharSequence("abc", maxReads = 2)
        seq[0]
        seq[1]
        rejectionMessage<BudgetedCharSequence.Budget> { seq[2] }
    }
}
