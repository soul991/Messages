package com.messages.protection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class IndicClassificationAndAiTest {

    private val engine: ProtectionEngine by lazy {
        val stream = javaClass.getResourceAsStream("/patterns.json")!!
        val text = stream.bufferedReader().readText()
        ProtectionEngine(PatternMatcher.fromJson(text))
    }

    private val aiScorer: NGramScorer by lazy {
        val stream = javaClass.getResourceAsStream("/ngram_model.json")!!
        val text = stream.bufferedReader().readText()
        NGramScorer.fromJson(text)
    }

    // -------------------------------------------------------------------------
    // 1. IndicNormalizer & Script Folding
    // -------------------------------------------------------------------------

    @Test
    fun fold_maps_indic_digits_to_ascii_in_all_nine_scripts() {
        assertEquals("5523104", IndicNormalizer.fold("५५२३१०४")) // Devanagari
        assertEquals("5523104", IndicNormalizer.fold("৫৫২৩১০৪")) // Bengali
        assertEquals("5523104", IndicNormalizer.fold("੫੫੨੩੧੦੪")) // Gurmukhi
        assertEquals("5523104", IndicNormalizer.fold("૫૫૨૩૧૦૪")) // Gujarati
        assertEquals("5523104", IndicNormalizer.fold("୫୫୨୩୧୦୪")) // Odia
        assertEquals("5523104", IndicNormalizer.fold("௫௫௨௩௧௦௪")) // Tamil
        assertEquals("5523104", IndicNormalizer.fold("౫౫౨౩౧౦౪")) // Telugu
        assertEquals("5523104", IndicNormalizer.fold("೫೫೨೩೧೦೪")) // Kannada
        assertEquals("5523104", IndicNormalizer.fold("൫൫൨൩൧൦൪")) // Malayalam
    }

    @Test
    fun fold_maps_danda_to_a_full_stop() {
        assertEquals("आपका ओटीपी. धन्यवाद.", IndicNormalizer.fold("आपका ओटीपी। धन्यवाद॥"))
    }

    @Test
    fun fold_is_identity_on_latin_text() {
        val latin = "483920 is your OTP for HDFC Bank login. Valid for 10 minutes."
        assertSame(latin, IndicNormalizer.fold(latin))
    }

    @Test
    fun official_domains_are_never_flagged_as_brand_impersonation() {
        val parivahanSignals = LinkAnalyzer.analyze(Normalizer.normalize("Check challan at https://parivahan.gov.in/vahan"))
        assertFalse("parivahan.gov.in must not be flagged as brand impersonation",
            parivahanSignals.any { it.id == "link-brand-impersonation" })

        val jioMartSignals = LinkAnalyzer.analyze(Normalizer.normalize("Your JioMart order has shipped: https://jiomart.com/order/123"))
        assertFalse("jiomart.com must not be flagged as brand impersonation",
            jioMartSignals.any { it.id == "link-brand-impersonation" })
    }

    @Test
    fun apk_with_query_params_or_paths_is_caught_as_apk_download() {
        val signals = LinkAnalyzer.analyze(Normalizer.normalize("Install update from http://fake-echallan.apk?version=2&dl=true"))
        assertTrue("Expected link-apk-download signal", signals.any { it.id == "link-apk-download" })
    }

    // -------------------------------------------------------------------------
    // 2. Multilingual Indic Protected & Scam Patterns
    // -------------------------------------------------------------------------

    @Test
    fun devanagari_otp_and_bank_alerts_are_protected() {
        // Devanagari OTP
        val otpVerdict = engine.classify(
            ProtectionEngine.Input("५५२३१० आपका ओटीपी है, १० मिनट तक वैध है", SenderAnalyzer.analyze("AX-HDFCBK-S", false))
        )
        assertEquals(Category.INBOX, otpVerdict.category)
        assertEquals(ProtectedLabel.OTP, otpVerdict.protectedLabel)

        // Devanagari Bank Transaction
        val bankVerdict = engine.classify(
            ProtectionEngine.Input("आपके खाते XX4421 से ₹२,५०० डेबिट किए गए", SenderAnalyzer.analyze("VM-SBIINB-S", false))
        )
        assertEquals(Category.TRANSACTIONS, bankVerdict.category)
        assertEquals(ProtectedLabel.BANK, bankVerdict.protectedLabel)
    }

    @Test
    fun bengali_otp_and_bank_alerts_are_protected() {
        // Bengali OTP
        val otpVerdict = engine.classify(
            ProtectionEngine.Input("আপনার ওটিপি হলো ১২৩৪৫৬, ১০ মিনিট বৈধ", SenderAnalyzer.analyze("AX-AXISBK-S", false))
        )
        assertEquals(Category.INBOX, otpVerdict.category)
        assertEquals(ProtectedLabel.OTP, otpVerdict.protectedLabel)

        // Bengali Bank alert
        val bankVerdict = engine.classify(
            ProtectionEngine.Input("আপনার অ্যাকাউন্ট XX8890 থেকে ₹৫০০ কাটা হয়েছে", SenderAnalyzer.analyze("AD-ICICIB-S", false))
        )
        assertEquals(Category.TRANSACTIONS, bankVerdict.category)
        assertEquals(ProtectedLabel.BANK, bankVerdict.protectedLabel)
    }

    @Test
    fun gujarati_and_punjabi_otps_are_protected() {
        // Gujarati OTP
        val gujVerdict = engine.classify(
            ProtectionEngine.Input("તમારો ઓટીપી ૪૯૧૮૨૦ છે, કોઈની સાથે શેર કરશો નહીં", SenderAnalyzer.analyze("AX-KOTAKB-S", false))
        )
        assertEquals(Category.INBOX, gujVerdict.category)
        assertEquals(ProtectedLabel.OTP, gujVerdict.protectedLabel)

        // Punjabi OTP
        val guruVerdict = engine.classify(
            ProtectionEngine.Input("ਤੁਹਾਡਾ ਓਟੀਪੀ ੬੨੯੧੦੪ ਹੈ, ਕਿਸੇ ਨਾਲ ਸਾਂਝਾ ਨਾ ਕਰੋ", SenderAnalyzer.analyze("VM-PNBSMS-S", false))
        )
        assertEquals(Category.INBOX, guruVerdict.category)
        assertEquals(ProtectedLabel.OTP, guruVerdict.protectedLabel)
    }

    // -------------------------------------------------------------------------
    // 3. Modern India Threat Patterns
    // -------------------------------------------------------------------------

    @Test
    fun electricity_disconnection_scam_is_caught() {
        val verdict = engine.classify(
            ProtectionEngine.Input(
                "Dear consumer your electricity power will be disconnected tonight at 9:30 PM from electricity office because your previous month bill was not updated. Please immediately contact our electricity officer at 9876543210",
                SenderAnalyzer.analyze("9876543210", false)
            )
        )
        assertTrue("Expected SPAM or REVIEW, got " + verdict.category, verdict.category == Category.SPAM || verdict.category == Category.REVIEW)
        assertTrue(verdict.matchedPatternIds.any { it.contains("electricity") || it.contains("scam") || it.contains("in-deva") })
    }

    @Test
    fun echallan_scam_is_caught() {
        val verdict = engine.classify(
            ProtectionEngine.Input(
                "Notice: Traffic police challan pending for vehicle DL01AB1234, fine Rs 1000. Pay within 24 hours to avoid court summons or clear via app http://echallan-fake.apk",
                SenderAnalyzer.analyze("9876012345", false)
            )
        )
        assertTrue("Expected SPAM, got " + verdict.category, verdict.category == Category.SPAM)
        assertTrue(verdict.matchedPatternIds.contains("scam-echallan"))
    }

    @Test
    fun digital_arrest_threat_is_caught() {
        val verdict = engine.classify(
            ProtectionEngine.Input(
                "CBI notice: Arrest warrant issued against your Aadhaar by cyber crime cell. Call inspector immediately on 9876543210",
                SenderAnalyzer.analyze("9876543210", false)
            )
        )
        assertTrue("Expected SPAM, got " + verdict.category, verdict.category == Category.SPAM)
        assertTrue(verdict.matchedPatternIds.contains("scam-digital-arrest"))
    }

    @Test
    fun part_time_job_task_fraud_is_caught() {
        val verdict = engine.classify(
            ProtectionEngine.Input(
                "Work from home: Like YouTube videos & review hotels on Google maps to earn Rs 3000 daily. Payout on UPI. WhatsApp 9876543210",
                SenderAnalyzer.analyze("9876543210", false)
            )
        )
        assertTrue("Expected SPAM, got " + verdict.category, verdict.category == Category.SPAM)
        assertTrue(verdict.matchedPatternIds.contains("scam-job-task"))
    }

    // -------------------------------------------------------------------------
    // 4. CardExtractor with Indic Numerals
    // -------------------------------------------------------------------------

    @Test
    fun card_extractor_parses_indic_digits_after_folding() {
        val text = "आपके खाते XX4421 से ₹२,५०० डेबिट किए गए"
        val normalized = Normalizer.normalize(text)
        assertTrue("Expected 2,500 in amounts, got: " + normalized.amounts, normalized.amounts.any { it.contains("2,500") || it.contains("2500") })

        val bengText = "আপনার অ্যাকাউন্ট থেকে ৳৫,০০০ ডেবিট করা হয়েছে"
        val bengNormalized = Normalizer.normalize(bengText)
        assertTrue("Expected 5,000 in amounts, got: " + bengNormalized.amounts, bengNormalized.amounts.any { it.contains("5,000") || it.contains("5000") })
    }

    // -------------------------------------------------------------------------
    // 5. AI Scorer & Layer 5 Review Ceiling
    // -------------------------------------------------------------------------

    @Test
    fun ai_scorer_loads_weights_and_scores_scam_higher_than_ham() {
        assertNotNull(aiScorer)
        assertTrue("Expected > 10,000 features, got: " + aiScorer.featureCount, aiScorer.featureCount > 10000)

        val scamScore = aiScorer.score("Congratulations you won lottery prize money 25 lakh call WhatsApp manager to claim")
        val hamScore = aiScorer.score("Reached office, will call you in the evening after meeting. Take care.")

        assertTrue("Scam score should be > ham score", scamScore > hamScore)
        assertTrue("Scam score should exceed threshold", scamScore > aiScorer.threshold)
    }

    @Test
    fun ai_layer_respects_review_ceiling_and_never_promotes_to_spam() {
        val baseVerdict = Verdict(
            category = Category.INBOX,
            score = 2,
            matchedPatternIds = emptyList(),
            explanations = emptyList(),
            notify = true
        )

        val shadow = AiLayer.evaluate(
            verdict = baseVerdict,
            text = "Pre-approved loan of 5 lakh ready with zero cibil check remit processing fee to claim today",
            messageId = "1001",
            scorer = aiScorer,
            readsAsProtected = false
        )

        assertNotNull(shadow)
        assertTrue(shadow!!.wouldHaveChangedRouting)
        assertEquals(Category.REVIEW, shadow.aiShadowVerdict)

        val applied = AiLayer.apply(baseVerdict, shadow, enabled = true)
        assertEquals("AI layer must only promote to REVIEW, never SPAM", Category.REVIEW, applied.category)
        assertFalse("AI layer must never set dangerous", applied.dangerous)
        assertFalse("AI layer review verdict must silence notification", applied.notify)
    }

    @Test
    fun ai_layer_never_filters_protected_traffic() {
        val protectedVerdict = Verdict(
            category = Category.INBOX,
            protectedLabel = ProtectedLabel.OTP,
            score = 0,
            matchedPatternIds = listOf("protect-otp-code"),
            explanations = listOf("OTP verification code"),
            notify = true
        )

        val shadow = AiLayer.evaluate(
            verdict = protectedVerdict,
            text = "483920 is your OTP for transaction of Rs 5000 at Amazon. Do not share with anyone.",
            messageId = "1002",
            scorer = aiScorer,
            readsAsProtected = true
        )

        assertEquals(null, shadow)
        val applied = AiLayer.apply(protectedVerdict, shadow, enabled = true)
        assertSame("Protected verdict must be returned untouched", protectedVerdict, applied)
    }
}
