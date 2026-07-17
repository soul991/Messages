package com.personal.detectivedialer.data.screening

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Mirrors backend/test/classifier.test.js — the two matchers must agree.
 */
class ScreeningMatcherTest {

    // ── domesticForm ─────────────────────────────────────────────────────

    @Test fun `plus91 E164 reduces to 10 digits`() {
        assertEquals("9812345678", ScreeningMatcher.domesticForm("+919812345678"))
    }

    @Test fun `0091 international dialing form is domestic`() {
        assertEquals("9812345678", ScreeningMatcher.domesticForm("00919812345678"))
    }

    @Test fun `bare 10-digit subscriber number is domestic`() {
        assertEquals("9812345678", ScreeningMatcher.domesticForm("9812345678"))
    }

    @Test fun `91-prefixed 12-digit without plus is domestic`() {
        assertEquals("9812345678", ScreeningMatcher.domesticForm("919812345678"))
    }

    @Test fun `national trunk zero is stripped`() {
        assertEquals("9812345678", ScreeningMatcher.domesticForm("09812345678"))
    }

    @Test fun `short codes are domestic`() {
        assertEquals("121", ScreeningMatcher.domesticForm("121"))
        assertEquals("56789", ScreeningMatcher.domesticForm("56789"))
    }

    @Test fun `formatting noise is stripped`() {
        assertEquals("9812345678", ScreeningMatcher.domesticForm("+91 98-123 45678"))
    }

    @Test fun `foreign numbers are not domestic`() {
        assertNull(ScreeningMatcher.domesticForm("+14155552671"))
        assertNull(ScreeningMatcher.domesticForm("+442071838750"))
        assertNull(ScreeningMatcher.domesticForm("0014155552671"))
    }

    @Test fun `wrong-length plus91 is not trusted`() {
        assertNull(ScreeningMatcher.domesticForm("+91981234567"))
    }

    @Test fun `blank and null are not domestic`() {
        assertNull(ScreeningMatcher.domesticForm(""))
        assertNull(ScreeningMatcher.domesticForm(null))
    }

    @Test fun `isDomestic mirrors domesticForm`() {
        assertTrue(ScreeningMatcher.isDomestic("+919812345678"))
        assertTrue(ScreeningMatcher.isDomestic("1409812345"))
        assertFalse(ScreeningMatcher.isDomestic("+14155552671"))
    }

    // ── TRAI prefix rules ────────────────────────────────────────────────

    @Test fun `140 series is promotional SPAM`() {
        val rule = ScreeningMatcher.matchPrefix("+911409812345")
        assertNotNull(rule)
        assertEquals("SPAM", rule!!.decision)
        assertTrue(rule.label.contains("140-series promotional"))
    }

    @Test fun `160 series is transactional ALLOW`() {
        val rule = ScreeningMatcher.matchPrefix("1609812345")
        assertNotNull(rule)
        assertEquals("ALLOW", rule!!.decision)
        assertTrue(rule.label.contains("160-series transactional"))
    }

    @Test fun `trunk-0 and 0091 forms still match`() {
        assertEquals("SPAM", ScreeningMatcher.matchPrefix("01409812345")?.decision)
        assertEquals("SPAM", ScreeningMatcher.matchPrefix("00911409812345")?.decision)
    }

    @Test fun `ordinary numbers do not match`() {
        assertNull(ScreeningMatcher.matchPrefix("+919812345678"))
    }

    @Test fun `foreign numbers never match even with a 140 core`() {
        assertNull(ScreeningMatcher.matchPrefix("+11409812345"))
    }

    @Test fun `140 later in the number does not match`() {
        assertNull(ScreeningMatcher.matchPrefix("+919814012345"))
    }

    @Test fun `custom downloaded rules are honoured`() {
        val custom = listOf(PrefixRule("777", "REJECT", "test series"))
        val rule = ScreeningMatcher.matchPrefix("7771234567", custom)
        assertEquals("REJECT", rule?.decision)
        // Defaults no longer apply when a custom table is passed.
        assertNull(ScreeningMatcher.matchPrefix("1409812345", custom))
    }
}
