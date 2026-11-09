package com.messages.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FriendlySenderNamesTest {

    @Test
    fun testRealShortCodes_confidentBrandMappings() {
        // At least 20 real short codes tested across banks, services, and merchants
        val realShortCodes = listOf(
            "VM-HDFCBK" to "HDFC Bank",
            "AD-HDFCBK-T" to "HDFC Bank",
            "VM-HDFCCC" to "HDFC Bank",
            "AX-SWIGGY" to "Swiggy",
            "VK-SWIGGY-P" to "Swiggy",
            "VM-ZOMATO" to "Zomato",
            "BZ-ZOMATO-S" to "Zomato",
            "AD-AMAZON" to "Amazon",
            "BW-AMZN-T" to "Amazon",
            "BA-FLPKRT" to "Flipkart",
            "VK-FLIPKART" to "Flipkart",
            "AX-SBIINB" to "SBI",
            "VM-SBISMS" to "SBI",
            "AD-SBIYONO" to "SBI YONO",
            "BP-ICICIB" to "ICICI Bank",
            "JM-ICICIC-S" to "ICICI Bank",
            "VK-AXISBK" to "Axis Bank",
            "AD-KOTAKB" to "Kotak Bank",
            "VA-PNBSMS-S" to "PNB",
            "AV-AIRTEL-S" to "Airtel",
            "JD-JIOMOB" to "Jio",
            "VM-PAYTM" to "Paytm",
            "AX-CREDT" to "CRED",
            "BW-UBERIN" to "Uber",
            "VM-MAMAEARTH" to "Mamaearth",
            "AD-TATA1MG" to "Tata 1mg",
            "VK-BLINKIT" to "Blinkit",
            "AD-AIRBNK-S" to "Airtel Payments Bank",
        )

        for ((input, expected) in realShortCodes) {
            assertEquals("Failed for input: $input", expected, FriendlySenderNames.format(input))
        }
    }

    @Test
    fun testNonConfidentShortCodes_showsCleanedShortCodeNotRaw() {
        // When not confident, show the cleaned short code, not the raw prefixed one!
        assertEquals("MYRANDOM", FriendlySenderNames.format("VM-MYRANDOM"))
        assertEquals("UNKNOWNBIZ", FriendlySenderNames.format("AX-UNKNOWNBIZ-P"))
        assertEquals("LOCALSHOP", FriendlySenderNames.format("BA-LOCALSHOP"))
        assertEquals("CUSTOMSERVICE", FriendlySenderNames.format("BW-CUSTOMSERVICE-S"))
        assertEquals("XYZCORP", FriendlySenderNames.format("JD_XYZCORP"))
    }

    @Test
    fun testPhoneNumbers_keptRaw() {
        assertEquals("+919876543210", FriendlySenderNames.format("+919876543210"))
        assertEquals("9876543210", FriendlySenderNames.format("9876543210"))
        assertEquals("+14155552671", FriendlySenderNames.format("+14155552671"))
        assertEquals("", FriendlySenderNames.format(""))
        assertEquals(null, FriendlySenderNames.format(null).takeIf { it.isNotEmpty() })
    }
}
