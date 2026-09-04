package com.messages.protection

/**
 * §5.6 — Link & format rules. These operate on the extracted URLs and the
 * message shape, not just the normalized text, so they live in code rather
 * than patterns.json. Each hit is reported as a synthetic pattern match with
 * a stable ID so the "Why?" screen can explain it.
 */
object LinkAnalyzer {

    data class LinkSignal(
        val id: String,
        val weight: Int,
        val description: String,
        /** Flags consumed by combo rules. */
        val isShortener: Boolean = false,
        val isSuspiciousTld: Boolean = false,
        val isBrandImpersonation: Boolean = false,
        val isApk: Boolean = false,
        val isPhishy: Boolean = false,
    )

    private val SHORTENERS = setOf(
        "bit.ly", "tinyurl.com", "cutt.ly", "t.ly", "rb.gy", "is.gd", "tiny.cc",
        "shorturl.at", "rebrand.ly", "ow.ly", "goo.gl", "s.id", "lnkd.in",
        "t.co", "surl.li", "u.to", "v.gd", "clck.ru", "bit.do", "shorte.st",
        "adf.ly", "soo.gd", "zpr.io", "qr.ae", "rblx.co", "smarturl.it",
        // seen in live Indian loan spam (Ramfincorp bait), 2026-07
        "hu2.in",
    )

    private val SUSPICIOUS_TLDS = setOf(
        "xyz", "top", "club", "online", "site", "buzz", "icu", "vip", "rest",
        "click", "link", "work", "loan", "men", "cyou", "cfd", "sbs", "quest",
        "monster", "lol", "pw", "cc", "tk", "ml", "ga", "gq", "zip", "mov",
        "bond", "fun", "host", "space", "store", "website",
    )

    /** Known-legit domains never flagged despite suspicious TLD. */
    private val TLD_WHITELIST = setOf("wa.link")

    private val BRANDS = listOf(
        "sbi", "hdfc", "icici", "axis", "kotak", "pnb", "bob", "canara", "union",
        "paytm", "phonepe", "gpay", "googlepay", "bhim", "amazon", "flipkart",
        "myntra", "meesho", "airtel", "jio", "vodafone", "irctc", "indiapost",
        "bluedart", "delhivery", "dhl", "fedex", "dtdc", "ekart", "netflix",
        "hotstar", "swiggy", "zomato", "fastag", "npci", "uidai", "incometax",
    )

    private val OFFICIAL_DOMAINS = setOf(
        "sbi.co.in", "onlinesbi.sbi", "sbicard.com", "hdfcbank.com", "icicibank.com",
        "axisbank.com", "kotak.com", "kotakbank.com", "pnbindia.in", "bankofbaroda.in",
        "canarabank.com", "unionbankofindia.co.in", "paytm.com", "phonepe.com",
        "pay.google.com", "google.com", "amazon.in", "amazon.com",
        "flipkart.com", "myntra.com", "meesho.com", "airtel.in", "airtelbank.com",
        "jio.com", "jiomart.com", "tataneu.com", "myvi.in", "vodafoneidea.com",
        "irctc.co.in", "indiapost.gov.in", "bluedart.com", "delhivery.com",
        "dhl.com", "fedex.com", "dtdc.in", "ekartlogistics.com", "netflix.com",
        "hotstar.com", "swiggy.com", "zomato.com", "npci.org.in", "bhimupi.org.in",
        "uidai.gov.in", "incometax.gov.in", "parivahan.gov.in",
    )

    private val IP_URL = Regex("""(?:https?://)?\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""")
    private val URGENCY = Regex(
        """(?i)\b(urgent|immediately|hurry|expire|today|tonight|now|last chance|final|within \d+ (hours?|min))\b"""
    )
    private val MONEY_WORD = Regex("""(?i)(₹|rs\.?|inr|\$|rupee|cash|money|amount|paise|payment)""")

    fun analyze(msg: NormalizedMessage): List<LinkSignal> {
        val signals = mutableListOf<LinkSignal>()
        val text = msg.normalizedText

        for (url in msg.urls) {
            val domain = extractDomain(url) ?: continue

            if (SHORTENERS.any { domain == it || domain.endsWith(".$it") }) {
                signals += LinkSignal(
                    "link-shortener", 6, "Shortened link ($domain) hides the real destination",
                    isShortener = true, isPhishy = true,
                )
            }
            val tld = domain.substringAfterLast('.')
            if (tld in SUSPICIOUS_TLDS && domain !in TLD_WHITELIST) {
                signals += LinkSignal(
                    "link-suspicious-tld", 6, "Link uses a TLD common in scams (.$tld)",
                    isSuspiciousTld = true, isPhishy = true,
                )
            }
            val brand = BRANDS.firstOrNull { domain.contains(it) }
            if (brand != null && OFFICIAL_DOMAINS.none { domain == it || domain.endsWith(".$it") }) {
                signals += LinkSignal(
                    "link-brand-impersonation", 10,
                    "Link pretends to be $brand but is not the official domain",
                    isBrandImpersonation = true, isPhishy = true,
                )
            }
            if (IP_URL.containsMatchIn(url)) {
                signals += LinkSignal(
                    "link-ip-literal", 8, "Link points to a raw IP address", isPhishy = true,
                )
            }
            if (url.startsWith("http://") && MONEY_WORD.containsMatchIn(text) &&
                URGENCY.containsMatchIn(text)
            ) {
                signals += LinkSignal(
                    "link-insecure-http", 5, "Insecure http link with money/urgency wording",
                    isPhishy = true,
                )
            }
            if (domain.contains("xn--")) {
                signals += LinkSignal(
                    "link-punycode", 8, "Link uses punycode look-alike characters", isPhishy = true,
                )
            }
            if (Regex("""(?i)\.apk(?=[/?#]|$)""").containsMatchIn(url)) {
                signals += LinkSignal(
                    "link-apk-download", 12, "Direct APK download link — likely malware",
                    isApk = true, isPhishy = true,
                )
            }
            if ((domain == "wa.me" || domain == "t.me" || domain.endsWith(".t.me"))) {
                val moneyJob = Regex("""(?i)(earn|job|income|profit|invest|loan|paise|kamao)""")
                if (moneyJob.containsMatchIn(text)) {
                    signals += LinkSignal(
                        "link-messenger-redirect", 6,
                        "WhatsApp/Telegram link combined with money or job wording",
                        isPhishy = true,
                    )
                }
            }
        }

        // Format signals (§5.6 tail) — computed on the ORIGINAL text where casing matters.
        val original = msg.original
        if (original.length > 40) {
            val letters = original.filter { it.isLetter() }
            if (letters.isNotEmpty() && letters.count { it.isUpperCase() } * 2 > letters.length) {
                signals += LinkSignal("format-caps", 3, "Mostly-capitals message")
            }
        }
        if (Regex("!{2,}").containsMatchIn(original)) {
            signals += LinkSignal("format-exclaim", 2, "Repeated exclamation marks")
        }
        val first20 = original.take(20)
        val emojiCount = first20.codePoints().filter { it in 0x1F300..0x1FAFF || it in 0x2600..0x27BF }.count()
        if (emojiCount >= 3) {
            signals += LinkSignal("format-emoji", 2, "Emoji-stuffed opening")
        }
        if (msg.amounts.isNotEmpty() && msg.phoneNumbers.isNotEmpty() &&
            Regex("""(?i)call now""").containsMatchIn(text)
        ) {
            signals += LinkSignal("format-callnow", 5, "Money amount plus 'call now' callback number")
        }
        if (Regex("""(?i)(share|forward).{0,20}(to|with).{0,15}\d+.{0,10}(groups?|people|friends)""")
                .containsMatchIn(text)
        ) {
            signals += LinkSignal("format-forward-chain", 6, "Asks you to forward to groups — chain scam")
        }

        return signals
    }

    fun extractDomain(url: String): String? {
        var u = url.lowercase().removePrefix("https://").removePrefix("http://").removePrefix("www.")
        u = u.substringBefore('/').substringBefore('?').substringBefore(':')
        return u.ifBlank { null }
    }
}
