package com.messages.app.notify

import com.messages.app.MessagesApp
import com.messages.app.R
import com.messages.protection.Category
import com.messages.protection.ProtectedLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Phase N5 Notification Verification Test Suite:
 * Asserts notification payload construction against design tokens, glyphs,
 * channels, action buttons, and conversation shortcut references.
 */
class NotificationBuilderOutputTest {

    private val brandAccentGreen = 0xFF30D158.toInt() // #30D158
    private val semanticErrorRed = 0xFFFF453A.toInt() // #FF453A

    @Test
    fun `personal SMS notification matches design tokens and specifications`() {
        val payload = MessageNotifier.buildPayloadSpec(
            address = "+15551234567",
            contactName = "Sarah Connor",
            body = "Hey! Let's meet at 5pm today.",
            category = Category.INBOX,
            threadId = 42L,
        )

        assertEquals("Personal channel ID", MessagesApp.CH_PERSONAL, payload.channelId)
        assertEquals("Small icon is ic_stat_message silhouette", R.drawable.ic_stat_message, payload.smallIconRes)
        assertEquals("Accent color is brand green (#30D158)", brandAccentGreen, payload.color)
        assertEquals("Title uses contact name", "Sarah Connor", payload.title)
        assertEquals("Body text preserved", "Hey! Let's meet at 5pm today.", payload.body)
        assertEquals("Group key by sender", "com.messages.app.sender.Sarah Connor", payload.groupKey)
        assertEquals("Shortcut ID set to thread ID", "thread_42", payload.shortcutId)
        assertTrue("Has mark as read action", payload.hasMarkAsReadAction)
        assertTrue("Two-way personal phone number is replyable", payload.isReplyable)
        assertFalse("Not a fraud warning", payload.isFraudWarning)
        assertNull("No OTP code", payload.copyOtpCode)
    }

    @Test
    fun `alphanumeric sender notification has friendly title and no reply action`() {
        val payload = MessageNotifier.buildPayloadSpec(
            address = "AD-HDFCBK",
            contactName = null,
            body = "INR 2,500.00 debited from A/c XX1234 on 09-NOV-26.",
            category = Category.TRANSACTIONS,
            threadId = 101L,
        )

        // Merged bank into personal
        assertEquals("Transactions route to Personal channel", MessagesApp.CH_PERSONAL, payload.channelId)
        assertEquals("Small icon is ic_stat_message", R.drawable.ic_stat_message, payload.smallIconRes)
        assertEquals("Accent color is brand green (#30D158)", brandAccentGreen, payload.color)
        assertTrue("Friendly sender name formatted in hero or title", payload.title.contains("HDFC Bank"))
        assertTrue("Extracted amount in hero title", payload.title.contains("INR 2,500.00"))
        assertEquals("Shortcut ID set", "thread_101", payload.shortcutId)
        assertTrue("Has mark as read action", payload.hasMarkAsReadAction)
        assertFalse("Alphanumeric sender cannot receive replies", payload.isReplyable)
        assertFalse("Not fraud", payload.isFraudWarning)
    }

    @Test
    fun `OTP notification exposes hero title and one-tap copy action code`() {
        val payload = MessageNotifier.buildPayloadSpec(
            address = "VK-GOOGLE",
            contactName = null,
            body = "G-849102 is your Google verification code.",
            category = Category.INBOX,
            protectedLabel = ProtectedLabel.OTP,
            threadId = 202L,
        )

        assertEquals("Personal channel ID", MessagesApp.CH_PERSONAL, payload.channelId)
        assertEquals("Small icon is ic_stat_message", R.drawable.ic_stat_message, payload.smallIconRes)
        assertEquals("Color is brand green (#30D158)", brandAccentGreen, payload.color)
        assertEquals("Extracted OTP code", "849102", payload.copyOtpCode)
        assertTrue("Hero title shows OTP code leading", payload.title.startsWith("849102 —"))
        assertTrue("Friendly sender name in title", payload.title.contains("Google"))
        assertNotNull("Shortcut ID set", payload.shortcutId)
        assertTrue("Has mark as read action", payload.hasMarkAsReadAction)
        assertFalse("Google alphanumeric is not replyable", payload.isReplyable)
    }

    @Test
    fun `critical fraud warning uses semantic red and dedicated fraud channel`() {
        val payload = MessageNotifier.buildPayloadSpec(
            address = "+19009998888",
            contactName = null,
            body = "URGENT: Your bank account is suspended. Click http://scam-bank.ru/login to verify.",
            category = Category.SPAM,
            dangerous = true,
            threadId = 999L,
        )

        assertEquals("Dedicated fraud warnings channel", MessagesApp.CH_FRAUD, payload.channelId)
        assertEquals("Dedicated fraud small icon silhouette", R.drawable.ic_notif_fraud, payload.smallIconRes)
        assertEquals("Semantic error red color token (#FF453A)", semanticErrorRed, payload.color)
        assertEquals("Title warns of fraud", "Potential fraud detected", payload.title)
        assertTrue("Fraud body identifies suspicious sender", payload.body.contains("Suspicious message from"))
        assertNull("Fraud warnings do not publish user-facing shortcuts", payload.shortcutId)
        assertFalse("No mark as read action on fraud warning", payload.hasMarkAsReadAction)
        assertFalse("Fraud warning is not replyable", payload.isReplyable)
        assertTrue("Flagged as fraud warning", payload.isFraudWarning)
    }

    @Test
    fun `locked conversation hides sender identity and suppresses shortcut`() {
        val payload = MessageNotifier.buildPayloadSpec(
            address = "+15559876543",
            contactName = "Secret Contact",
            body = "Classified intelligence brief",
            category = Category.INBOX,
            threadId = 777L,
            conversationLocked = true,
            hidden = true,
            appNamePlaceholder = "Messages",
            newMessagePlaceholder = "New message",
        )

        assertEquals("Messages", payload.title)
        assertEquals("New message", payload.body)
        assertNull("Shortcut MUST be suppressed for locked conversation", payload.shortcutId)
        assertFalse("Locked conversation is not replyable from notification shade", payload.isReplyable)
        assertNull("No OTP code leaked when hidden", payload.copyOtpCode)
    }
}
