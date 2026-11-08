package com.messages.core.util

import org.junit.Assert.assertEquals
import org.junit.Test

class FriendlySenderNamesTest {

    @Test
    fun testFriendlySenderNamesMapping() {
        assertEquals("Airtel", FriendlySenderNames.format("AV-AIRTEL-S"))
        assertEquals("PNB", FriendlySenderNames.format("VA-PNBSMS-S"))
        assertEquals("Airtel Payments Bank", FriendlySenderNames.format("AD-AIRBNK-S"))
        assertEquals("SBI", FriendlySenderNames.format("SBI"))
        assertEquals("SBI", FriendlySenderNames.format("SBI-SMS"))
        assertEquals("SBI YONO", FriendlySenderNames.format("SBIYONO"))
        assertEquals("Tata 1mg", FriendlySenderNames.format("TATA1MG"))
        assertEquals("Mamaearth", FriendlySenderNames.format("MAMAEARTH"))
        assertEquals("HDFC Bank", FriendlySenderNames.format("VM-HDFCBK-T"))
        assertEquals("Axis Bank", FriendlySenderNames.format("AX-AXISBK-S"))
        assertEquals("Amazon", FriendlySenderNames.format("AMAZON"))
        assertEquals("Swiggy", FriendlySenderNames.format("VK-SWIGGY-P"))
    }

    @Test
    fun testFallbackToRawAddress() {
        assertEquals("+919876543210", FriendlySenderNames.format("+919876543210"))
        assertEquals("UnknownSender", FriendlySenderNames.format("UnknownSender"))
        assertEquals("", FriendlySenderNames.format(""))
    }
}
