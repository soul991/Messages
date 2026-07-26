package com.messages.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

object TestEngine {
    val matcher: PatternMatcher by lazy {
        val text = javaClass.getResourceAsStream("/patterns.json")!!.bufferedReader().readText()
        PatternMatcher.fromJson(text)
    }

    fun engine() = ProtectionEngine(matcher)

    fun classify(
        body: String,
        sender: String = "VM-OFFERS",
        isContact: Boolean = false,
    ): Verdict = engine().classify(
        ProtectionEngine.Input(body, SenderAnalyzer.analyze(sender, isContact))
    )
}

/** §13 — the named non-negotiable test. */
class OTP_and_bank_alerts_can_never_be_filtered {

    private val otpSamples = listOf(
        "483920 is your OTP for HDFC Bank login. Valid for 10 minutes. Do not share it with anyone.",
        "Your Amazon verification code is 552211. Do not share this OTP.",
        "Use 9021 as your one time password for Swiggy. Valid for 5 minutes.",
        "Your login code is 771234 for IRCTC. 2FA enabled.",
        "OTP: 445566 for txn of Rs 1,200 at Flipkart. Do not share this code.",
    )

    private val bankSamples = listOf(
        "Rs 2,500 debited from A/c XX4421 on 12-Jul via UPI. Avl Bal Rs 18,240.",
        "Rs 40,000 credited to your account XX882 via NEFT ref N19822.",
        "Your EMI of Rs 4,320 is due on 05-Aug. Please maintain balance.",
        "INR 899.00 spent on card XX7788 at NETFLIX. Avl limit Rs 45,000.",
    )

    @Test
    fun otp_always_reaches_inbox_from_any_sender() {
        val senders = listOf("AX-BANKXX-S", "VM-AMZOTP", "9876543210", "+2348012345678", "BZ-UNKNWN")
        for (body in otpSamples) for (sender in senders) {
            val v = TestEngine.classify(body, sender)
            assertTrue(
                "OTP filtered! sender=$sender body=$body → ${v.category}",
                v.category == Category.INBOX || v.category == Category.TRANSACTIONS,
            )
            assertTrue("OTP must notify: $body", v.notify)
        }
    }

    @Test
    fun bank_alerts_from_registered_headers_are_never_filtered() {
        for (body in bankSamples) {
            val v = TestEngine.classify(body, "AX-BANKXX-S")
            assertTrue(
                "Bank alert filtered! body=$body → ${v.category}",
                v.category == Category.INBOX || v.category == Category.TRANSACTIONS,
            )
        }
    }

    @Test
    fun fake_otp_phishing_gets_warning_banner_not_burial() {
        val v = TestEngine.classify(
            "Your OTP is 445566. Confirm at http://hdfc-verify.xyz/login now",
            sender = "9876543210",
        )
        // Still Inbox (never buried) but with the red fraud warning
        assertEquals(Category.INBOX, v.category)
        assertTrue("Expected fraud warning banner", v.fraudWarningBanner)
    }

    @Test
    fun otp_label_is_applied_for_auto_delete_feature() {
        val v = TestEngine.classify(otpSamples[0], "AX-BANKXX-S")
        assertEquals(ProtectedLabel.OTP, v.protectedLabel)
    }
}

class NormalizerTest {

    @Test
    fun defeats_dot_separation() {
        val n = Normalizer.normalize("F.R.E.E recharge offer")
        assertTrue(n.normalizedText.contains("free"))
    }

    @Test
    fun defeats_space_separation() {
        val n = Normalizer.normalize("W I N cash prizes")
        assertTrue(n.normalizedText.contains("win"))
    }

    @Test
    fun defeats_leet_speak() {
        val n = Normalizer.normalize("FR33 R3CHARG3 for you")
        assertTrue(n.normalizedText.contains("free"))
        assertTrue(n.normalizedText.contains("recharge"))
    }

    @Test
    fun defeats_zero_width_chars() {
        val n = Normalizer.normalize("fr​ee mo‌ney")
        assertTrue(n.normalizedText.contains("free"))
        assertTrue(n.normalizedText.contains("money"))
    }

    @Test
    fun defeats_homoglyphs() {
        // Cyrillic а/е/о in "frее" and "won"
        val n = Normalizer.normalize("You just wоn a prizе")
        assertTrue(n.normalizedText.contains("won"))
        assertTrue(n.normalizedText.contains("prize"))
    }

    @Test
    fun defeats_char_repetition() {
        val n = Normalizer.normalize("FREEEEEE OFFERRRR")
        assertTrue(n.normalizedText.contains("free"))
        assertTrue(n.normalizedText.contains("offer"))
    }

    @Test
    fun otp_digits_survive_normalization() {
        val n = Normalizer.normalize("483920 is your OTP. Valid for 10 minutes")
        assertTrue(n.normalizedText.contains("483920"))
    }

    @Test
    fun extracts_urls_amounts_phones() {
        val n = Normalizer.normalize("Won ₹5,00,000! Call 9876543210 or visit bit.ly/xyz")
        assertTrue(n.urls.any { it.contains("bit.ly") })
        assertTrue(n.phoneNumbers.any { it.contains("9876543210") })
        assertTrue(n.amounts.isNotEmpty())
    }

    @Test
    fun extracts_schemeless_domains_with_known_tlds() {
        // Regression for the on-device backfill failure: the old URL regex used an
        // unbounded lookbehind that Android's ICU regex rejects at compile time.
        val n = Normalizer.normalize("Claim at amazon-kyc.xyz/win or sbi-verify.online now")
        assertTrue(n.urls.any { it.contains("amazon-kyc.xyz/win") })
        assertTrue(n.urls.any { it.contains("sbi-verify.online") })
        // Near-misses: times and plain sentences must not become URLs.
        val neg = Normalizer.normalize("Meet me at 9.30 tomorrow. That movie was great.")
        assertTrue(neg.urls.isEmpty())
    }
}

class SenderAnalyzerTest {

    @Test
    fun classifies_dlt_headers() {
        assertEquals(SenderType.REGISTERED_TRANSACTIONAL, SenderAnalyzer.analyze("AX-BANKXX-S", false).type)
        assertEquals(SenderType.REGISTERED_PROMOTIONAL, SenderAnalyzer.analyze("VM-MYNTRA-P", false).type)
        assertEquals(SenderType.REGISTERED_GOVERNMENT, SenderAnalyzer.analyze("AD-UIDAI-G", false).type)
    }

    @Test
    fun classifies_numbers() {
        assertEquals(SenderType.PERSONAL_NUMBER, SenderAnalyzer.analyze("9876543210", false).type)
        assertEquals(SenderType.INTERNATIONAL_NUMBER, SenderAnalyzer.analyze("+2348012345678", false).type)
        assertEquals(SenderType.SHORT_CODE, SenderAnalyzer.analyze("57575", false).type)
        assertEquals(SenderType.SAVED_CONTACT, SenderAnalyzer.analyze("9876543210", true).type)
        assertEquals(SenderType.EMAIL_GATEWAY, SenderAnalyzer.analyze("promo@spam.com", false).type)
    }

    @Test
    fun international_scam_multiplier_is_2x() {
        val intl = SenderAnalyzer.analyze("+2348012345678", false)
        assertEquals(2.0, SenderAnalyzer.spamMultiplier(intl, Families.LOTTERY), 0.001)
    }
}

class ScamClassificationTest {

    @Test
    fun lottery_scam_is_dangerous_spam() {
        val v = TestEngine.classify(
            "Congratulations! You have WON ₹25,00,000 in KBC lucky draw. Click bit.ly/kbc-claim to claim your prize",
            sender = "+2348012345678",
        )
        assertEquals(Category.SPAM, v.category)
        assertTrue(v.dangerous)
        assertFalse(v.notify)
    }

    @Test
    fun friend_saying_won_the_match_is_not_spam() {
        val v = TestEngine.classify("we won the match! party at my place tonight", sender = "9812345678")
        assertEquals(Category.INBOX, v.category)
    }

    @Test
    fun friend_asking_should_i_buy_is_inbox() {
        val v = TestEngine.classify("should I buy this? the offer looks good", sender = "9812345678")
        assertEquals(Category.INBOX, v.category)
    }

    @Test
    fun upi_pin_to_receive_is_always_fraud() {
        val v = TestEngine.classify(
            "Paytm: Rs 5,000 payment pending. Enter UPI PIN to receive the amount",
            sender = "9876543210",
        )
        assertEquals(Category.SPAM, v.category)
        assertTrue(v.dangerous)
    }

    @Test
    fun kyc_threat_is_dangerous() {
        val v = TestEngine.classify(
            "Dear customer your SBI account KYC is pending. Account will be suspended today. Update at http://sbi-kyc.xyz",
            sender = "9988776655",
        )
        assertEquals(Category.SPAM, v.category)
        assertTrue(v.dangerous)
    }

    @Test
    fun electricity_disconnection_scam_caught() {
        val v = TestEngine.classify(
            "Dear consumer your electricity power will be disconnected tonight at 9.30pm because your previous bill was not updated. Contact officer 9911223344",
            sender = "9911223344",
        )
        assertEquals(Category.SPAM, v.category)
    }

    @Test
    fun anydesk_mention_is_dangerous() {
        val v = TestEngine.classify(
            "For your refund install AnyDesk app and share the 9 digit code with our executive",
            sender = "8877665544",
        )
        assertEquals(Category.SPAM, v.category)
        assertTrue(v.dangerous)
    }

    @Test
    fun apk_link_is_dangerous() {
        val v = TestEngine.classify(
            "Update your bank app here: http://secure-bank.top/update.apk",
            sender = "9876501234",
        )
        assertEquals(Category.SPAM, v.category)
        assertTrue(v.dangerous)
    }

    @Test
    fun job_scam_from_international_number() {
        val v = TestEngine.classify(
            "Work from home! Earn Rs 5000 daily by liking videos. Join t.me/easyearn",
            sender = "+8613712345678",
        )
        assertEquals(Category.SPAM, v.category)
        assertTrue(v.dangerous)
    }

    @Test
    fun promo_from_dlt_p_header_goes_to_promotions() {
        val v = TestEngine.classify(
            "Mega Sale! FLAT 60% OFF on all fashion. Use code STYLE60. Shop now. T&C apply",
            sender = "VM-MYNTRA-P",
        )
        assertTrue(
            "Expected PROMOTIONS or SPAM, got ${v.category}",
            v.category == Category.PROMOTIONS || v.category == Category.SPAM,
        )
        assertFalse(v.notify)
    }

    @Test
    fun obfuscated_spam_still_caught() {
        val v = TestEngine.classify(
            "F.R.E.E recharge! W1N upto ₹10,000 cash. Click bit.ly/free-win hurry!!",
            sender = "9876543210",
        )
        assertTrue(
            "Obfuscated spam reached ${v.category}",
            v.category == Category.SPAM || v.category == Category.PROMOTIONS,
        )
    }

    @Test
    fun digital_arrest_is_dangerous() {
        val v = TestEngine.classify(
            "This is CBI officer. You are under digital arrest. Call immediately or FIR will be registered, legal action within 24 hours",
            sender = "+919000000001",
        )
        assertEquals(Category.SPAM, v.category)
        assertTrue(v.dangerous)
    }

    @Test
    fun contact_sending_scam_combo_still_flagged() {
        // Contacts get compromised too — fraud combos outrank contact trust
        val v = TestEngine.classify(
            "Enter your UPI PIN to receive Rs 2,000 cashback: bit.ly/upi-cash",
            sender = "9812345678",
            isContact = true,
        )
        assertEquals(Category.SPAM, v.category)
    }

    @Test
    fun plain_personal_chat_is_inbox() {
        val samples = listOf(
            "Are you coming to office tomorrow?",
            "Mom asked you to call her back",
            "The meeting got moved to 3pm",
            "Can you send me the notes from class?",
            "Happy birthday! Have a great year ahead",
        )
        for (s in samples) {
            val v = TestEngine.classify(s, sender = "9812345678")
            assertEquals("'$s' misfiled to ${v.category}", Category.INBOX, v.category)
            assertTrue(v.notify)
        }
    }

    @Test
    fun verdicts_are_explainable() {
        val v = TestEngine.classify(
            "You WON ₹5,00,000! Claim your prize at bit.ly/xyz",
            sender = "+2348012345678",
        )
        assertTrue(v.matchedPatternIds.isNotEmpty())
        assertTrue(v.explanations.isNotEmpty())
    }
}

class ComboRulesTest {

    private fun combos(body: String, sender: String): List<ComboRules.ComboResult> {
        val msg = Normalizer.normalize(body)
        val info = SenderAnalyzer.analyze(sender, false)
        val matches = TestEngine.matcher.matchAll(msg)
        val links = LinkAnalyzer.analyze(msg)
        return ComboRules.evaluate(msg, info, matches, links)
    }

    @Test fun c2_prize_plus_amount() {
        assertTrue(combos("You won Rs 5 lakh in lottery", "9876543210").any { it.id == "C2" })
    }

    @Test fun c4_shortener_plus_hook() {
        assertTrue(combos("Your KYC is pending, update at bit.ly/kyc-upd", "9876543210").any { it.id == "C4" })
    }

    @Test fun c7_pin_ask() {
        assertTrue(combos("Please share your OTP to complete refund", "9876543210").any { it.id == "C7" })
    }

    @Test fun c9_apk() {
        assertTrue(combos("Get bonus, download http://win.top/game.apk", "9876543210").any { it.id == "C9" })
    }

    @Test fun c10_promo_pileup() {
        assertTrue(
            combos("Mega sale! Flat 50% off. Use code SAVE50. Hurry, ends tonight! Shop now", "VM-SHOPSY")
                .any { it.id == "C10" }
        )
    }

    @Test fun no_combos_on_normal_chat() {
        assertTrue(combos("lunch at 1? the usual place", "9812345678").isEmpty())
    }
}

class PatternLibraryIntegrityTest {

    @Test
    fun every_pattern_compiles_and_has_description() {
        for (p in TestEngine.matcher.library.patterns) {
            Regex(p.regex) // throws on invalid
            assertTrue("${p.id} missing description", p.description.isNotBlank())
        }
    }

    @Test
    fun every_pattern_matches_its_positive_examples() {
        for (p in TestEngine.matcher.library.patterns) {
            for (ex in p.examples) {
                val n = Normalizer.normalize(ex)
                assertTrue(
                    "${p.id} failed its own positive example: $ex",
                    Regex(p.regex, RegexOption.IGNORE_CASE).containsMatchIn(n.normalizedText),
                )
            }
        }
    }

    @Test
    fun no_pattern_matches_its_negative_examples() {
        for (p in TestEngine.matcher.library.patterns) {
            for (ex in p.negativeExamples) {
                val n = Normalizer.normalize(ex)
                assertFalse(
                    "${p.id} wrongly matches its negative example: $ex",
                    Regex(p.regex, RegexOption.IGNORE_CASE).containsMatchIn(n.normalizedText),
                )
            }
        }
    }

    @Test
    fun every_non_protected_pattern_has_positive_and_negative_examples() {
        for (p in TestEngine.matcher.library.patterns) {
            assertTrue("${p.id} lacks a positive example", p.examples.isNotEmpty())
            if (p.family !in Families.PROTECTED && p.id != "appdl-screenshare") {
                assertTrue("${p.id} lacks a negative example", p.negativeExamples.isNotEmpty())
            }
        }
    }
}

/** §3 Stage 5 — the Sensitivity slider hot-applies without rebuilding the engine. */
class SensitivityUpdateTest {

    @Test
    fun update_sensitivity_hot_applies_thresholds() {
        val engine = TestEngine.engine()
        val input = ProtectionEngine.Input(
            "Claim your reward now",
            SenderAnalyzer.analyze("VM-UPDATE", isContact = false),
        )
        val baseline = engine.classify(input)
        assertTrue("test message must score > 0", baseline.score > 0)
        assertTrue("test message must not trip a combo", baseline.matchedComboIds.isEmpty())
        val score = baseline.score

        // All thresholds above the score → clean pass to Inbox.
        engine.updateSensitivity(
            ProtectionEngine.Sensitivity(dangerousAt = score + 3, spamAt = score + 2, reviewAt = score + 1)
        )
        assertEquals(Category.INBOX, engine.classify(input).category)

        // Dangerous threshold at the score → Spam with Dangerous label.
        engine.updateSensitivity(
            ProtectionEngine.Sensitivity(dangerousAt = score, spamAt = score, reviewAt = score)
        )
        val strict = engine.classify(input)
        assertEquals(Category.SPAM, strict.category)
        assertTrue(strict.dangerous)
    }

    @Test
    fun library_info_is_exposed_and_tracks_updates() {
        val engine = TestEngine.engine()
        assertEquals(TestEngine.matcher.library.version, engine.libraryVersion)
        assertEquals(TestEngine.matcher.library.patterns.size, engine.patternCount)
    }
}
