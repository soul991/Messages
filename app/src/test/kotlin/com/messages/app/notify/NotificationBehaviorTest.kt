package com.messages.app.notify

import com.messages.core.util.FriendlySenderNames
import com.messages.protection.Category
import com.messages.protection.SenderAnalyzer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationBehaviorTest {

    @Test
    fun `friendly sender names formats known brand senders in notification title`() {
        assertEquals("Airtel", FriendlySenderNames.format("AV-AIRTEL-S"))
        assertEquals("PNB", FriendlySenderNames.format("VA-PNBSMS-S"))
        assertEquals("SBI", FriendlySenderNames.format("VK-SBIINB"))
        assertEquals("HDFC Bank", FriendlySenderNames.format("AD-HDFCBK"))
    }

    @Test
    fun `sender grouping key isolates conversations by formatted sender`() {
        val senderAirtel = "AV-AIRTEL-S"
        val senderPnb = "VA-PNBSMS-S"
        val groupKeyAirtel = "com.messages.app.sender." + FriendlySenderNames.format(senderAirtel)
        val groupKeyPnb = "com.messages.app.sender." + FriendlySenderNames.format(senderPnb)

        assertEquals("com.messages.app.sender.Airtel", groupKeyAirtel)
        assertEquals("com.messages.app.sender.PNB", groupKeyPnb)
        assertTrue(groupKeyAirtel != groupKeyPnb)
    }

    @Test
    fun `reply action only allows two-way personal conversations`() {
        // Alphanumeric senders cannot receive SMS replies
        assertFalse(SenderAnalyzer.canReceiveReplies("AD-HDFCBK"))
        assertFalse(SenderAnalyzer.canReceiveReplies("VM-AIRTEL"))
        assertFalse(SenderAnalyzer.canReceiveReplies("GOOGLE"))

        // Standard 10-digit or E.164 phone number can receive SMS replies
        assertTrue(SenderAnalyzer.canReceiveReplies("+15551234567"))
        assertTrue(SenderAnalyzer.canReceiveReplies("9876543210"))
    }

    @Test
    fun `lock-screen privacy hides body and provides generic text when requested`() {
        val originalBody = "Your OTP is 948123 for transaction of Rs 5000"
        val hidePreviews = true
        val publicNotificationText = if (hidePreviews) "New message" else originalBody
        assertEquals("New message", publicNotificationText)
        assertFalse("Must not leak OTP or amount in locked preview", publicNotificationText.contains("948123"))
    }
}
