package com.messages.core.util

/**
 * Maps raw alphanumeric/DLT sender IDs into friendly, human-readable brand names
 * (e.g. AV-AIRTEL-S -> Airtel, VA-PNBSMS-S -> PNB, AD-AIRBNK-S -> Airtel Payments Bank).
 * Falls back to the raw address if no match is found.
 */
object FriendlySenderNames {

    private val MAPPINGS = mapOf(
        "AIRTEL" to "Airtel",
        "AIRTL" to "Airtel",
        "AIRTELX" to "Airtel",
        "AIRTELB" to "Airtel",
        "AIRBNK" to "Airtel Payments Bank",
        "PNB" to "PNB",
        "PNBSMS" to "PNB",
        "PNBBNK" to "PNB",
        "SBI" to "SBI",
        "SBISMS" to "SBI",
        "SBIN" to "SBI",
        "SBIINB" to "SBI",
        "SBIYONO" to "SBI YONO",
        "YONO" to "SBI YONO",
        "TATA1MG" to "Tata 1mg",
        "1MG" to "Tata 1mg",
        "MAMAEARTH" to "Mamaearth",
        "MAMAER" to "Mamaearth",
        "HDFC" to "HDFC Bank",
        "HDFCBK" to "HDFC Bank",
        "HDFCCC" to "HDFC Bank",
        "HDFCLO" to "HDFC Bank",
        "ICICI" to "ICICI Bank",
        "ICICIB" to "ICICI Bank",
        "ICICIC" to "ICICI Bank",
        "AXIS" to "Axis Bank",
        "AXISBK" to "Axis Bank",
        "AXISCC" to "Axis Bank",
        "KOTAK" to "Kotak Bank",
        "KOTAKB" to "Kotak Bank",
        "KMBANK" to "Kotak Bank",
        "ZOMATO" to "Zomato",
        "SWIGGY" to "Swiggy",
        "AMAZON" to "Amazon",
        "AMZN" to "Amazon",
        "FLPKRT" to "Flipkart",
        "FLIPKT" to "Flipkart",
        "JIO" to "Jio",
        "JIOMART" to "JioMart",
        "JIOFIB" to "JioFiber",
        "JIOMOB" to "Jio",
        "JIOINF" to "Jio",
        "UBER" to "Uber",
        "OLA" to "Ola",
        "PAYTM" to "Paytm",
        "PYTM" to "Paytm",
        "GPAY" to "Google Pay",
        "GOOGLE" to "Google",
        "PHONEPE" to "PhonePe",
        "PHNPE" to "PhonePe",
        "MYNTRA" to "Myntra",
        "NYKAA" to "Nykaa",
        "BLINKIT" to "Blinkit",
        "ZEPTO" to "Zepto",
        "BIGBSK" to "BigBasket",
        "INDUS" to "IndusInd Bank",
        "INDUSB" to "IndusInd Bank",
        "YESBNK" to "Yes Bank",
        "CANARA" to "Canara Bank",
        "UNIONB" to "Union Bank",
        "BOB" to "Bank of Baroda",
        "BOBTXN" to "Bank of Baroda",
        "IDFC" to "IDFC First Bank",
        "IDFCFB" to "IDFC First Bank",
        "CREDT" to "CRED",
        "CRED" to "CRED",
    )

    private val DLT_REGEX = Regex("""^[A-Za-z]{2}-([A-Za-z0-9]+)(?:-[SPTG])?$""", RegexOption.IGNORE_CASE)
    private val HYPHEN_SUFFIX_REGEX = Regex("""-([SPTG])$""", RegexOption.IGNORE_CASE)

    /**
     * Resolves a raw sender address/header into a friendly brand name, or falls back to rawAddress.
     */
    fun format(rawAddress: String?): String {
        if (rawAddress.isNullOrBlank()) return ""
        val trimmed = rawAddress.trim()

        // 1. Exact match in lookup table
        MAPPINGS[trimmed.uppercase()]?.let { return it }

        // 2. Standard Indian TRAI DLT header format: e.g. "AV-AIRTEL-S", "VA-PNBSMS-S", "AD-AIRBNK-S"
        val dltMatch = DLT_REGEX.matchEntire(trimmed)
        if (dltMatch != null) {
            val entity = dltMatch.groupValues[1].uppercase()
            MAPPINGS[entity]?.let { return it }

            // Substring / prefix search in entity (e.g. "AIRTEL" in "AIRTELX")
            for ((key, name) in MAPPINGS) {
                if (entity.startsWith(key) || entity.contains(key)) {
                    return name
                }
            }
            return entity
        }

        // 3. Strip single-character TRAI DLT suffix (e.g. "-S", "-P", "-T", "-G")
        val stripped = HYPHEN_SUFFIX_REGEX.replace(trimmed, "")
        if (stripped != trimmed) {
            MAPPINGS[stripped.uppercase()]?.let { return it }
        }

        // 4. Substring search if raw address contains known entity keyword
        val upper = trimmed.uppercase()
        for ((key, name) in MAPPINGS) {
            if (upper.contains(key)) {
                return name
            }
        }

        // Fall back to the raw ID
        return rawAddress
    }
}
