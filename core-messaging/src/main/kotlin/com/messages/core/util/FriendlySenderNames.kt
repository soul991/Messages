package com.messages.core.util

/**
 * Maps raw alphanumeric/DLT sender IDs into friendly, human-readable brand names
 * (e.g. VM-HDFCBK -> HDFC Bank, AX-SWIGGY -> Swiggy, AV-AIRTEL-S -> Airtel, VA-PNBSMS-S -> PNB).
 *
 * In India and elsewhere, short codes carry 2-letter operator/circle routing prefixes (e.g. "VM-", "AX-", "AD-").
 * When confident, returns the friendly brand name.
 * When not confident, strips the 2-letter prefix and returns the cleaned short code (e.g. "VM-MYCORP" -> "MYCORP").
 * Raw phone numbers are preserved as-is.
 */
object FriendlySenderNames {

    private val MAPPINGS = mapOf(
        // Banks - HDFC
        "HDFC" to "HDFC Bank",
        "HDFCBK" to "HDFC Bank",
        "HDFCCC" to "HDFC Bank",
        "HDFCLO" to "HDFC Bank",
        "HDFCAL" to "HDFC Bank",
        "HDFCSEC" to "HDFC Bank",
        "HDFCMF" to "HDFC Bank",

        // Banks - SBI
        "SBI" to "SBI",
        "SBISMS" to "SBI",
        "SBIN" to "SBI",
        "SBIINB" to "SBI",
        "SBIYONO" to "SBI YONO",
        "YONO" to "SBI YONO",
        "SBICRD" to "SBI Card",
        "SBICARD" to "SBI Card",
        "SBILFE" to "SBI Life",
        "SBIMF" to "SBI Mutual Fund",

        // Banks - ICICI
        "ICICI" to "ICICI Bank",
        "ICICIB" to "ICICI Bank",
        "ICICIC" to "ICICI Bank",
        "ICICPR" to "ICICI Bank",
        "ICICIS" to "ICICI Bank",
        "ICICIL" to "ICICI Bank",

        // Banks - Axis
        "AXIS" to "Axis Bank",
        "AXISBK" to "Axis Bank",
        "AXISCC" to "Axis Bank",
        "AXISMF" to "Axis Bank",
        "AXISBN" to "Axis Bank",

        // Banks - Kotak
        "KOTAK" to "Kotak Bank",
        "KOTAKB" to "Kotak Bank",
        "KMBANK" to "Kotak Bank",
        "KOTAKM" to "Kotak Bank",
        "KOTAKS" to "Kotak Bank",

        // Banks - PNB
        "PNB" to "PNB",
        "PNBSMS" to "PNB",
        "PNBBNK" to "PNB",
        "PNBRTD" to "PNB",

        // Banks - Others
        "BOB" to "Bank of Baroda",
        "BOBTXN" to "Bank of Baroda",
        "BOBSMS" to "Bank of Baroda",
        "BARODA" to "Bank of Baroda",
        "CANARA" to "Canara Bank",
        "CANBNK" to "Canara Bank",
        "CANARABK" to "Canara Bank",
        "UNIONB" to "Union Bank",
        "UBIN" to "Union Bank",
        "UNION" to "Union Bank",
        "IDFC" to "IDFC First Bank",
        "IDFCFB" to "IDFC First Bank",
        "IDFCFIRST" to "IDFC First Bank",
        "IDFCBK" to "IDFC First Bank",
        "INDUS" to "IndusInd Bank",
        "INDUSB" to "IndusInd Bank",
        "INDUSIND" to "IndusInd Bank",
        "YES" to "Yes Bank",
        "YESBNK" to "Yes Bank",
        "YESBK" to "Yes Bank",
        "FEDBNK" to "Federal Bank",
        "FEDERAL" to "Federal Bank",
        "FEDBK" to "Federal Bank",
        "RBL" to "RBL Bank",
        "RBLBNK" to "RBL Bank",
        "RBLCRD" to "RBL Bank",
        "BOI" to "Bank of India",
        "BOISMS" to "Bank of India",
        "BOITXN" to "Bank of India",
        "CENTRAL" to "Central Bank of India",
        "CBISMS" to "Central Bank of India",
        "CBIN" to "Central Bank of India",
        "INDIAN" to "Indian Bank",
        "INDBNK" to "Indian Bank",
        "IDBSMS" to "Indian Bank",
        "IOB" to "Indian Overseas Bank",
        "IOBSMS" to "Indian Overseas Bank",
        "IOBNK" to "Indian Overseas Bank",
        "UCO" to "UCO Bank",
        "UCOBNK" to "UCO Bank",
        "UCOMS" to "UCO Bank",
        "AUBANK" to "AU Small Finance Bank",
        "AUSFB" to "AU Small Finance Bank",
        "SCBANK" to "Standard Chartered",
        "SCBL" to "Standard Chartered",
        "CITIBK" to "Citibank",
        "CITI" to "Citibank",
        "HSBC" to "HSBC",
        "HSBCIN" to "HSBC",

        // Food & Grocery
        "SWIGGY" to "Swiggy",
        "SWIGY" to "Swiggy",
        "ZOMATO" to "Zomato",
        "ZOMAT" to "Zomato",
        "BLINKIT" to "Blinkit",
        "BLINKT" to "Blinkit",
        "ZEPTO" to "Zepto",
        "BIGBSK" to "BigBasket",
        "BIGBASKET" to "BigBasket",
        "BBDAILY" to "BigBasket",
        "DOMINO" to "Domino's",
        "DOMINOS" to "Domino's",
        "PIZZAHUT" to "Pizza Hut",
        "MCDIND" to "McDonald's",
        "MCDONALDS" to "McDonald's",
        "INSTAM" to "Instamart",
        "INSTAMART" to "Instamart",

        // Ecommerce & Retail
        "AMAZON" to "Amazon",
        "AMZN" to "Amazon",
        "AMZNSM" to "Amazon",
        "AMZNPAY" to "Amazon",
        "FLPKRT" to "Flipkart",
        "FLIPKT" to "Flipkart",
        "FLIPKART" to "Flipkart",
        "MYNTRA" to "Myntra",
        "MYNTR" to "Myntra",
        "NYKAA" to "Nykaa",
        "NYKAAK" to "Nykaa",
        "AJIO" to "Ajio",
        "AJIOLIFE" to "Ajio",
        "MEESHO" to "Meesho",
        "MSHO" to "Meesho",
        "MAMAEARTH" to "Mamaearth",
        "MAMAER" to "Mamaearth",
        "TATA1MG" to "Tata 1mg",
        "1MG" to "Tata 1mg",
        "TATANEU" to "Tata Neu",
        "TATACLQ" to "Tata CLiQ",
        "BOAT" to "boAt",
        "BOATLK" to "boAt",
        "LENSKT" to "Lenskart",
        "LENSKART" to "Lenskart",

        // Travel & Mobility
        "UBER" to "Uber",
        "UBERIN" to "Uber",
        "OLA" to "Ola",
        "OLACAB" to "Ola",
        "RAPIDO" to "Rapido",
        "IRCTC" to "IRCTC",
        "IRCTCi" to "IRCTC",
        "MMT" to "MakeMyTrip",
        "MAKEMT" to "MakeMyTrip",
        "MAKEMYTRIP" to "MakeMyTrip",
        "MYTRIP" to "MakeMyTrip",
        "GOIBIB" to "Goibibo",
        "GOIBIBO" to "Goibibo",
        "INDIGO" to "IndiGo",
        "GOINDG" to "IndiGo",
        "AIRIND" to "Air India",
        "AIRINDIA" to "Air India",
        "REDBUS" to "redBus",
        "YATRA" to "Yatra",

        // Fintech & Payments
        "PAYTM" to "Paytm",
        "PYTM" to "Paytm",
        "PAYTMB" to "Paytm Bank",
        "PYTMBN" to "Paytm Bank",
        "GPAY" to "Google Pay",
        "GOOGLE" to "Google",
        "PHONEPE" to "PhonePe",
        "PHNPE" to "PhonePe",
        "CREDT" to "CRED",
        "CRED" to "CRED",
        "MOBIKWIK" to "MobiKwik",
        "MBKWIK" to "MobiKwik",
        "FREECH" to "Freecharge",
        "FREECHARGE" to "Freecharge",
        "SLICE" to "Slice",
        "SLICEC" to "Slice",
        "FIMONEY" to "Fi Money",
        "FIBANK" to "Fi Money",
        "JUPITR" to "Jupiter",
        "JUPITER" to "Jupiter",
        "GROWW" to "Groww",
        "GROW" to "Groww",
        "ZERODH" to "Zerodha",
        "ZERODHA" to "Zerodha",
        "ANGELO" to "Angel One",
        "ANGELONE" to "Angel One",
        "UPSTOX" to "Upstox",
        "NAVI" to "Navi",
        "NAVITE" to "Navi",
        "BAJAJ" to "Bajaj Finserv",
        "BAJAJF" to "Bajaj Finserv",
        "BAJFIN" to "Bajaj Finserv",

        // Telecom & Utilities
        "AIRTEL" to "Airtel",
        "AIRTL" to "Airtel",
        "AIRTELX" to "Airtel",
        "AIRTELB" to "Airtel",
        "AIRBNK" to "Airtel Payments Bank",
        "APBL" to "Airtel Payments Bank",
        "JIO" to "Jio",
        "JIOMART" to "JioMart",
        "JIOFIB" to "JioFiber",
        "JIOMOB" to "Jio",
        "JIOINF" to "Jio",
        "JIOBNK" to "Jio Payments Bank",
        "IDEA" to "Vi",
        "VODAFO" to "Vi",
        "VI" to "Vi",
        "VISMS" to "Vi",
        "BSNL" to "BSNL",
        "BSNLSMS" to "BSNL",
        "TATAPLAY" to "Tata Play",
        "TATASKY" to "Tata Play",

        // Government & Essential
        "AADHAR" to "Aadhaar",
        "AADHAAR" to "Aadhaar",
        "UIDAI" to "Aadhaar",
        "COWIN" to "CoWIN",
        "NHPSMS" to "CoWIN",
        "INCOMETAX" to "Income Tax Dept",
        "ITDPRC" to "Income Tax Dept",
        "VAHAN" to "Parivahan",
        "PARIVAHAN" to "Parivahan",
        "EPFOHO" to "EPFO",
        "EPFO" to "EPFO",

        // Entertainment
        "NETFLX" to "Netflix",
        "NETFLIX" to "Netflix",
        "PRIME" to "Prime Video",
        "AMZNPR" to "Prime Video",
        "HOTSTR" to "Disney+ Hotstar",
        "HOTSTAR" to "Disney+ Hotstar",
        "BOOKMY" to "BookMyShow",
        "BMS" to "BookMyShow",
        "BMSHOW" to "BookMyShow",
    )

    // Standard 2-letter operator & circle routing prefixes:
    // Followed by hyphen, space, or underscore (e.g. "VM-HDFCBK", "AX SWIGGY", "AD_AIRBNK", "BA-SBIINB")
    private val ROUTING_PREFIX_REGEX = Regex("""^[A-Za-z]{2}[-\s_]+([A-Za-z0-9]+.*)$""")

    // Trailing TRAI category suffix (e.g. "-S", "-P", "-T", "-G", "-D", "-B")
    private val TRAI_SUFFIX_REGEX = Regex("""-[SPTGDBspgtgdb]$""")

    // Trailing non-alphanumeric noise (hyphens, dots, spaces, underscores)
    private val TRAILING_NOISE_REGEX = Regex("""[-_\s.]+$""")

    /**
     * Strips standard 2-letter telco routing prefixes (and any trailing hyphens/spaces/suffixes),
     * returning the clean short code.
     */
    fun cleanShortCode(rawAddress: String?): String {
        if (rawAddress.isNullOrBlank()) return ""
        val trimmed = rawAddress.trim()

        // If it is a phone number (7+ digits, only phone characters), keep as is
        val digitCount = trimmed.count { it.isDigit() }
        if (digitCount >= 7 && trimmed.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }) {
            return trimmed
        }

        // 1. Try stripping routing prefix with separator (e.g. "VM-HDFCBK-S" -> "HDFCBK-S")
        var core = ROUTING_PREFIX_REGEX.matchEntire(trimmed)?.groupValues?.get(1) ?: trimmed

        // 2. If exactly 8 characters all letters (e.g. "VMHDFCBK"), check if stripping 2-letter prefix matches a brand
        if (core == trimmed && trimmed.length == 8 && trimmed.all { it.isLetter() }) {
            val potentialEntity = trimmed.substring(2).uppercase()
            if (MAPPINGS.containsKey(potentialEntity) || MAPPINGS.keys.any { potentialEntity.startsWith(it) }) {
                core = potentialEntity
            }
        }

        // 3. Strip trailing TRAI suffix (e.g. "-S", "-T", "-P")
        core = TRAI_SUFFIX_REGEX.replace(core, "")

        // 4. Strip trailing noise
        core = TRAILING_NOISE_REGEX.replace(core, "").trim()

        return if (core.isNotBlank()) core else trimmed
    }

    /**
     * Extracts a canonical brand/sender key for deduplication and thread merging.
     * Alphanumeric sender codes with routing prefixes (e.g. "VK-PhonePe", "VM-PhonePe", "AD-PhonePe")
     * map to the same uppercase brand key (e.g. "PHONEPE").
     * Phone numbers return their digits-only representation.
     */
    fun canonicalBrandKey(rawAddress: String?): String {
        if (rawAddress.isNullOrBlank()) return ""
        val cleaned = cleanShortCode(rawAddress)
        val digitCount = cleaned.count { it.isDigit() }
        if (digitCount >= 7 && cleaned.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }) {
            return cleaned.filter { it.isDigit() }
        }
        return cleaned.uppercase()
    }

    /**
     * Resolves a raw sender address/header into a friendly brand name.
     * When confident, returns the friendly brand name.
     * When not confident, returns the cleaned short code, not the raw prefixed one.
     */
    fun format(rawAddress: String?): String {
        if (rawAddress.isNullOrBlank()) return ""
        val trimmed = rawAddress.trim()

        // Phone numbers (+91..., 10-digit, etc.) return directly
        val digitCount = trimmed.count { it.isDigit() }
        if (digitCount >= 7 && trimmed.all { it.isDigit() || it == '+' || it == '-' || it == ' ' }) {
            return trimmed
        }

        val cleaned = cleanShortCode(trimmed)
        val upperCleaned = cleaned.uppercase()

        // 1. Exact match on cleaned code
        MAPPINGS[upperCleaned]?.let { return it }

        // 2. Exact match on raw trimmed
        MAPPINGS[trimmed.uppercase()]?.let { return it }

        // 3. Prefix match on cleaned code (e.g. "HDFCBK" starts with "HDFC")
        for ((key, name) in MAPPINGS) {
            if (upperCleaned == key || upperCleaned.startsWith(key) || upperCleaned.endsWith(key)) {
                return name
            }
        }

        // 4. Substring containment match for long distinctive keywords (>= 4 chars)
        for ((key, name) in MAPPINGS) {
            if (key.length >= 4 && upperCleaned.contains(key)) {
                return name
            }
        }

        // When not confident, show the cleaned short code, not the raw prefixed one!
        return cleaned
    }
}
