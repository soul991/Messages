package com.messages.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase 4 item 21 (Truecaller report rec B3): first-contact ×1.25 on
 * scam-family scores for senders with zero prior messages in the index.
 * Deterministic, local, surfaced in the Why? screen.
 */
class FirstContactMultiplierTest {

    private val scamFamilies = Families.SCAM
    private val nonScam = listOf(Families.PROMO, Families.PROTECTED_OTP, Families.PROTECTED_BANK)

    private fun info(firstContact: Boolean, isContact: Boolean = false) =
        SenderInfo("9812345678", SenderType.PERSONAL_NUMBER, isContact, 0, firstContact)

    @Test
    fun `multiplier is exactly 1_25 on scam families for a first-contact sender`() {
        scamFamilies.forEach { family ->
            assertEquals(
                "family=$family",
                1.25,
                SenderAnalyzer.firstContactMultiplier(info(firstContact = true), family),
                0.0001,
            )
        }
    }

    @Test
    fun `multiplier is 1_0 for a known sender`() {
        scamFamilies.forEach { family ->
            assertEquals(
                1.0,
                SenderAnalyzer.firstContactMultiplier(info(firstContact = false), family),
                0.0001,
            )
        }
    }

    @Test
    fun `multiplier never touches promo or protected families`() {
        nonScam.forEach { family ->
            assertEquals(
                "family=$family must be unaffected",
                1.0,
                SenderAnalyzer.firstContactMultiplier(info(firstContact = true), family),
                0.0001,
            )
        }
    }

    @Test
    fun `saved contacts are exempt even on first contact`() {
        scamFamilies.forEach { family ->
            assertEquals(
                1.0,
                SenderAnalyzer.firstContactMultiplier(
                    info(firstContact = true, isContact = true), family,
                ),
                0.0001,
            )
        }
    }

    @Test
    fun `multiplier is deterministic across repeated calls`() {
        val a = SenderAnalyzer.firstContactMultiplier(info(firstContact = true), Families.LOAN)
        val b = SenderAnalyzer.firstContactMultiplier(info(firstContact = true), Families.LOAN)
        assertEquals(a, b, 0.0)
    }

    @Test
    fun `first-contact scam scores higher than repeat-contact and shows the explanation`() {
        val engine = TestEngine.engine()
        // A borderline loan-fraud style message from an unknown personal number.
        val body = "Congratulations! Your loan of Rs 500000 is pre-approved. " +
            "No documents needed. Click http://bit.ly/loan-now to claim instantly."
        val sender = "9800011122"

        val first = engine.classify(
            ProtectionEngine.Input(body, SenderAnalyzer.analyze(sender, isContact = false, firstContact = true)),
        )
        val repeat = engine.classify(
            ProtectionEngine.Input(body, SenderAnalyzer.analyze(sender, isContact = false, firstContact = false)),
        )

        assertTrue("first-contact score should be >= repeat", first.score >= repeat.score)
        assertTrue(
            "first-contact verdict must explain the bump",
            first.explanations.any { it.contains("First message from this sender") },
        )
        assertFalse(
            "repeat-contact verdict must NOT show the first-contact line",
            repeat.explanations.any { it.contains("First message from this sender") },
        )
    }

    @Test
    fun `genuine first-contact message is never pushed to Spam by the multiplier`() {
        val engine = TestEngine.engine()
        // A plain personal message with no scam patterns — multiplier has nothing
        // to act on, so a brand-new sender saying hello stays in the Inbox.
        val body = "Hi, this is Ramesh from the plumbing service. I'll reach by 4pm today."
        val sender = "9765432100"
        val v = engine.classify(
            ProtectionEngine.Input(body, SenderAnalyzer.analyze(sender, isContact = false, firstContact = true)),
        )
        assertFalse("genuine first-contact must not be Spam/Blocked",
            v.category == Category.SPAM || v.category == Category.BLOCKED)
    }
}
