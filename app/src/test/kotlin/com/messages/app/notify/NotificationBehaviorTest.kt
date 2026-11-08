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
    fun `sender grouping key isolates conversations by formatted sender using production code`() {
        val groupKeyAirtel = MessageNotifier.resolveGroupKey("AV-AIRTEL-S", null)
        val groupKeyPnb = MessageNotifier.resolveGroupKey("VA-PNBSMS-S", null)
        val groupKeyCustomContact = MessageNotifier.resolveGroupKey("+15551234567", "John Doe")

        assertEquals("com.messages.app.sender.Airtel", groupKeyAirtel)
        assertEquals("com.messages.app.sender.PNB", groupKeyPnb)
        assertEquals("com.messages.app.sender.John Doe", groupKeyCustomContact)
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
    fun `lock-screen privacy hides body and sets private visibility using production code`() {
        val originalBody = "Your OTP is 948123 for transaction of Rs 5000"

        // When hidden is true (e.g. app lock on or conversation locked)
        val hiddenText = MessageNotifier.resolveNotificationBody(
            body = originalBody,
            hidden = true,
            placeholder = "New message",
        )
        assertEquals("New message", hiddenText)
        assertFalse("Must not leak OTP or amount when hidden", hiddenText.contains("948123"))

        // When hidden is false
        val visibleText = MessageNotifier.resolveNotificationBody(
            body = originalBody,
            hidden = false,
            placeholder = "New message",
        )
        assertEquals(originalBody, visibleText)

        // Visibility flags on lock screen
        val privateVisibility = MessageNotifier.resolveNotificationVisibility(hidden = true, hidePreviewsSetting = false)
        val settingPrivateVisibility = MessageNotifier.resolveNotificationVisibility(hidden = false, hidePreviewsSetting = true)
        val publicVisibility = MessageNotifier.resolveNotificationVisibility(hidden = false, hidePreviewsSetting = false)

        assertEquals(androidx.core.app.NotificationCompat.VISIBILITY_PRIVATE, privateVisibility)
        assertEquals(androidx.core.app.NotificationCompat.VISIBILITY_PRIVATE, settingPrivateVisibility)
        assertEquals(androidx.core.app.NotificationCompat.VISIBILITY_PUBLIC, publicVisibility)
    }
}
