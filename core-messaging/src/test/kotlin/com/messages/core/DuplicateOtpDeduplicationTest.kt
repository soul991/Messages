package com.messages.core

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.messages.core.db.ConversationEntity
import com.messages.core.db.MessageEntity
import com.messages.core.db.Spaces
import com.messages.core.util.FriendlySenderNames
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DuplicateOtpDeduplicationTest {

    private lateinit var context: Context
    private lateinit var repo: MessageRepository

    @Before
    fun setUp() {
        MessageRepository.resetForTests()
        context = ApplicationProvider.getApplicationContext()
        repo = MessageRepository.get(context)
    }

    @Test
    fun canonicalBrandKey_normalizesCirclePrefixes() {
        assertEquals("PHONEPE", FriendlySenderNames.canonicalBrandKey("VK-PhonePe"))
        assertEquals("PHONEPE", FriendlySenderNames.canonicalBrandKey("VM-PhonePe"))
        assertEquals("PHONEPE", FriendlySenderNames.canonicalBrandKey("AD-PhonePe"))
        assertEquals("PHONEPE", FriendlySenderNames.canonicalBrandKey("PhonePe"))
        assertEquals("AIRTEL", FriendlySenderNames.canonicalBrandKey("AD-AIRTEL"))
        assertEquals("HDFCBK", FriendlySenderNames.canonicalBrandKey("AX-HDFCBK"))
        assertEquals("919876543210", FriendlySenderNames.canonicalBrandKey("+91 9876543210"))
    }

    @Test
    fun incomingOtp_thenTelephonySyncDelta_doesNotProduceDuplicateMessage() = runTest {
        val body = "Your PhonePe OTP is 123456. Do not share this with anyone."
        val timestamp = 10_000L

        // Store via incoming intake
        val intake = repo.onIncomingSms("VK-PhonePe", body, timestamp)
        assertTrue(intake.isNew)
        val initialMessage = intake.message
        assertNotNull(initialMessage)

        // Telephony sync delta arrives later with provider smsId = 777L
        val synced = repo.indexHistorical(
            smsId = 777L,
            threadId = 10L,
            address = "VK-PhonePe",
            body = body,
            timestamp = timestamp,
            isOutgoing = false,
            read = false,
            bumpUnread = true,
        )

        // Must be identified as duplicate and not re-inserted
        assertFalse(synced)

        // Verify only 1 message exists in DB with this body
        val matches = repo.db.messages().findPotentialDuplicates(body, timestamp)
        assertEquals(1, matches.size)
    }

    @Test
    fun intakeWithNullSmsId_thenTelephonySyncDelta_linksSmsIdAndDoesNotDuplicate() = runTest {
        val body = "Your PhonePe OTP is 987123. Valid for 10 minutes."
        val timestamp = 15_000L

        // Simulate message stored with smsId = null (e.g. app not default SMS app)
        val id = repo.db.messages().insert(
            MessageEntity(
                threadId = -100L,
                address = "VK-PhonePe",
                body = body,
                normalizedBody = body,
                timestamp = timestamp,
                isOutgoing = false,
                smsId = null,
            )
        )
        assertTrue(id > 0)

        // Telephony sync delta arrives later with provider smsId = 555L
        val synced = repo.indexHistorical(
            smsId = 555L,
            threadId = 12L,
            address = "VK-PhonePe",
            body = body,
            timestamp = timestamp,
            isOutgoing = false,
            read = false,
            bumpUnread = true,
        )

        assertFalse(synced)

        // Verify only 1 message exists and smsId was updated to 555L
        val matches = repo.db.messages().findPotentialDuplicates(body, timestamp)
        assertEquals(1, matches.size)
        val updated = repo.db.messages().byId(id)
        assertNotNull(updated)
        assertEquals(555L, updated?.smsId)
    }

    @Test
    fun messagesFromDifferentCirclePrefixes_mergeIntoSameConversationThread() = runTest {
        val body1 = "Your PhonePe OTP is 111222. Valid for 5 mins."
        val body2 = "Your transaction of Rs 500 at Merchant was successful."

        val intake1 = repo.onIncomingSms("VK-PhonePe", body1, 10_000L)
        assertTrue(intake1.isNew)

        val intake2 = repo.onIncomingSms("VM-PhonePe", body2, 20_000L)
        assertTrue(intake2.isNew)

        // Both messages must share the exact same threadId
        assertEquals(intake1.message.threadId, intake2.message.threadId)

        // Only 1 conversation row should exist on the Home list
        val conversations = repo.db.conversations().allInSpace(Spaces.NORMAL)
        val phonePeConvs = conversations.filter {
            FriendlySenderNames.canonicalBrandKey(it.address) == "PHONEPE"
        }
        assertEquals(1, phonePeConvs.size)
    }

    @Test
    fun duplicateOtpFromDifferentCirclePrefix_within2Seconds_isDeduplicated() = runTest {
        val body = "Your PhonePe OTP is 333444."
        val intake1 = repo.onIncomingSms("VK-PhonePe", body, 50_000L)
        assertTrue(intake1.isNew)

        // Second duplicate arrives via different circle prefix 500ms later
        val intake2 = repo.onIncomingSms("VM-PhonePe", body, 50_500L)
        assertFalse(intake2.isNew)
        assertEquals(intake1.message.id, intake2.message.id)

        // Only 1 message stored
        val potential = repo.db.messages().findPotentialDuplicates(body, 50_000L)
        assertEquals(1, potential.size)
    }

    @Test
    fun deduplicateExistingMessages_safelyCleansUpPreexistingDuplicatesAndMergesThreads() = runTest {
        val body = "Your PhonePe OTP is 777888."
        val thread1 = -101L
        val thread2 = -102L

        // Insert conversation 1 and message 1
        repo.db.conversations().upsert(
            ConversationEntity(
                threadId = thread1,
                address = "VK-PhonePe",
                lastMessage = body,
                lastTimestamp = 60_000L,
            )
        )
        val m1 = repo.db.messages().insert(
            MessageEntity(
                threadId = thread1,
                address = "VK-PhonePe",
                body = body,
                normalizedBody = body,
                timestamp = 60_000L,
                isOutgoing = false,
                smsId = null,
            )
        )

        // Insert conversation 2 and message 2 (duplicate with different thread and circle prefix)
        repo.db.conversations().upsert(
            ConversationEntity(
                threadId = thread2,
                address = "VM-PhonePe",
                lastMessage = body,
                lastTimestamp = 60_800L,
            )
        )
        val m2 = repo.db.messages().insert(
            MessageEntity(
                threadId = thread2,
                address = "VM-PhonePe",
                body = body,
                normalizedBody = body,
                timestamp = 60_800L,
                isOutgoing = false,
                smsId = 888L,
            )
        )

        assertTrue(m1 > 0)
        assertTrue(m2 > 0)

        // Run safe deduplication
        val deduped = repo.deduplicateExistingMessages()
        assertEquals(1, deduped)

        // Verify only 1 message remains and it has smsId 888L
        val remaining = repo.db.messages().allActiveIncoming().filter { it.body == body }
        assertEquals(1, remaining.size)
        assertEquals(888L, remaining.first().smsId)

        // Verify thread2 conversation was merged away
        val conversations = repo.db.conversations().allInSpace(Spaces.NORMAL).filter {
            FriendlySenderNames.canonicalBrandKey(it.address) == "PHONEPE"
        }
        assertEquals(1, conversations.size)
    }
}
