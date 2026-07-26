package com.messages.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real-world false negatives (2026-07-24, user's own inbox): DLT-registered
 * headers sending fake-credit / fake-loan bait worded like bank alerts. The
 * bank-alert phrasing matches PROTECTED_BANK, which used to ride the
 * protected lane straight into Transactions with a verified badge. The
 * protected-lane override (link-backed scam evidence only) must catch these
 * — while textual evidence alone (a genuine "never share your OTP" footer)
 * must never override the lane.
 */
class RegisteredHeaderAbuseTest {

    private val liveSamples = listOf(
        "AD-PLUTUS-S" to "Jio Alert : SPAM\nRESOLVEDOnServer Rs 3,850 has been credited to your account 9812309876. Claim immediately: bit.ly/43xeXQv valid for 24 hours only! ThresholdatPineLabs",
        "AD-PLUTUS-S" to "Jio Alert : SPAM\nRESOLVEDOnServer Hi Account Transfer Received: Rs 1850 Amount 810****789 available in wallet is waiting. Click to allow bit.ly/4ehw5i0 ThresholdatPineLabs",
        "CP-CAPSTK-S" to "Dear User: Rs.12,269 credited to account(810***789) successfully :MCXSELLFUTCOM Withdraw before 9PM @ cutt.ly/NtZYEWU5 . --Capstocks.",
        "JM-SMCSEC-S" to "Dear,SwitchOTPis Rs.12,275 credited for Account 810***4789. Withdraw before 9am today by Click in bit.ly/3QLxZ2e for.SMC",
        "JK-RAMfcC-P" to "Dear 81013XX, Rs.50,000* loan is ready to be credited to your Bank A/c on 14.04.2026. Check your eligibility now http://hu2.in/RfcKL/rlNxUX - Ramfincorp",
        "AD-INFATL-P" to "Infant Success! 3888 credited to your account. Ready for withdrawal or top-up. Activate now: cutt.ly/jtDGMDl9 InfantTechnology",
        "VA-RfcKL-P" to "Congrats 81013XX, Rs. 50,000* loan is ready to be credited to your Bank A/c. Click here to Apply http://hu2.in/RfcKL/gUkA5H - RamFincorp",
    )

    @Test
    fun `fake credit and loan bait from registered headers is dangerous spam`() {
        for ((sender, body) in liveSamples) {
            val v = TestEngine.classify(body, sender)
            assertEquals("$sender: $body", Category.SPAM, v.category)
            assertTrue("$sender should be dangerous", v.dangerous)
        }
    }

    @Test
    fun `override is explained in the verdict`() {
        val v = TestEngine.classify(liveSamples[2].second, liveSamples[2].first)
        assertTrue(v.matchedPatternIds.contains("protected-lane-override"))
    }

    @Test
    fun `genuine credit alert with share-OTP footer keeps the protected lane`() {
        // C7 (PIN_ASK) matches "share your OTP" — textual evidence alone must
        // not knock a real bank alert off the protected lane.
        val v = TestEngine.classify(
            "Rs 2,500 credited to A/c XX4421 on 12-Jul via IMPS. Avl bal Rs 18,240. Never share your OTP or PIN with anyone. -HDFC Bank",
            "VD-BANKXX-S",
        )
        // The OTP-family pattern on the footer routes this to Inbox with an
        // OTP label (pre-existing lane-ordering behavior) — the property that
        // matters is that it is never filtered and never overridden to Spam.
        assertTrue(v.category == Category.TRANSACTIONS || v.category == Category.INBOX)
        assertTrue(v.protectedLabel != ProtectedLabel.NONE)
        assertTrue(!v.dangerous)
    }

    @Test
    fun `genuine credit alert with official link keeps the protected lane`() {
        val v = TestEngine.classify(
            "Rs 40,000 credited to your account XX882 via NEFT ref N19822. Details: https://www.hdfcbank.com/statements",
            "VD-BANKXX-S",
        )
        assertEquals(Category.TRANSACTIONS, v.category)
        assertEquals(ProtectedLabel.BANK, v.protectedLabel)
    }
}
