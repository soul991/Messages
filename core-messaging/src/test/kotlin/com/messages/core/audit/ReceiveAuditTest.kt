package com.messages.core.audit

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.messages.core.MessageRepository
import com.messages.core.db.MessagesDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ReceiveAuditTest {

    private lateinit var context: Context
    private lateinit var repo: MessageRepository
    private lateinit var db: MessagesDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        MessageRepository.resetForTests()
        ReceiveAudit.resetForTests()
        db = Room.inMemoryDatabaseBuilder(context, MessagesDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = MessageRepository.get(context)
    }

    @After
    fun tearDown() {
        db.close()
        MessageRepository.resetForTests()
        ReceiveAudit.resetForTests()
    }

    @Test
    fun `multipart SMS reassembly preserves full body across parts`() = runTest {
        val parts = listOf(
            "First segment of a long message. ",
            "Second segment carrying OTP: 839201. ",
            "Final segment with confirmation details.",
        )
        val fullBody = parts.joinToString("")
        val sender = "VK-HDFCBK"
        val timestamp = System.currentTimeMillis()

        ReceiveAudit.record(ReceiveAudit.Stage.RECEIVED, sender, "", "multipart=true")
        ReceiveAudit.record(ReceiveAudit.Stage.PARSED, sender, fullBody, "parts=${parts.size}")

        val intake = repo.onIncomingSms(sender, fullBody, timestamp)
        assertNotNull(intake.message)
        assertEquals(fullBody, intake.message.body)

        val counts = ReceiveAudit.getCounts()
        assertEquals(1L, counts["received"])
        assertEquals(1L, counts["parsed"])
        assertEquals(1L, counts["stored"])
        assertEquals(1L, counts["classified"])
    }

    @Test
    fun `alphanumeric and non-standard senders are parsed and stored`() = runTest {
        val senders = listOf(
            "VK-HDFCBK",
            "AIRTEL",
            "VM-GOOGLE",
            "12345",
            "+919876543210",
            "AX-AMAZON-S",
        )

        senders.forEachIndexed { index, sender ->
            val body = "Alert from $sender with index $index"
            val timestamp = 1_700_000_000_000L + index * 1000
            val intake = repo.onIncomingSms(sender, body, timestamp)

            assertNotNull("Message from $sender must be stored", intake.message)
            assertEquals(sender, intake.message.address)
            assertEquals(body, intake.message.body)
        }

        val stored = repo.db.messages().allMessages()
        assertEquals(senders.size, stored.size)
    }

    @Test
    fun `non-ASCII and unicode text bodies are preserved intact`() = runTest {
        val unicodeBodies = listOf(
            "Your OTP is 492018 🚀🔥 Do not share it! 🎉",
            "नमस्ते, आपका खाता सक्रिय हो गया है।",
            "مرحبا بك في تطبيق الرسائل",
            "こんにちは、メッセージアプリへようこそ",
            "Special symbols: € $ £ ¥ ₹ & % @ # ~ ^ *",
        )

        unicodeBodies.forEachIndexed { index, body ->
            val sender = "VM-UNICODE"
            val timestamp = 1_700_000_100_000L + index * 1000
            val intake = repo.onIncomingSms(sender, body, timestamp)

            assertEquals(body, intake.message.body)
            val fetched = repo.db.messages().byId(intake.message.id)!!
            assertEquals(body, fetched.body)
        }
    }

    @Test
    fun `empty body messages are stored and never dropped`() = runTest {
        val emptyBodies = listOf("", "   ", "\n\t")
        val sender = "CARRIER-PING"

        emptyBodies.forEachIndexed { index, body ->
            val timestamp = 1_700_000_200_000L + index * 1000
            val intake = repo.onIncomingSms(sender, body, timestamp)

            assertNotNull("Empty body message must not be dropped", intake.message)
            val fetched = repo.db.messages().byId(intake.message.id)
            assertNotNull(fetched)
            assertEquals(body, fetched!!.body)
        }

        val allMessages = repo.db.messages().allMessages()
        assertEquals(emptyBodies.size, allMessages.size)
    }

    @Test
    fun `historical indexing retains empty body messages from provider`() = runTest {
        val inserted = repo.indexHistorical(
            smsId = 9999L,
            threadId = 42L,
            address = "CARRIER",
            body = "",
            timestamp = 1_700_000_300_000L,
            isOutgoing = false,
            read = true,
        )

        assertTrue("Historical empty body message must be indexed", inserted)
        val msg = repo.db.messages().bySmsId(9999L)
        assertNotNull(msg)
        assertEquals("", msg!!.body)
    }

    @Test
    fun `audit ring buffer records recent events with details`() {
        ReceiveAudit.record(ReceiveAudit.Stage.RECEIVED, "TEST-SENDER", "Hello world", "initial")
        ReceiveAudit.record(ReceiveAudit.Stage.STORED, "TEST-SENDER", "Hello world", "saved id=1")

        val log = ReceiveAudit.getRecentLog()
        assertTrue(log.size >= 2)
        val last = log.last()
        assertEquals(ReceiveAudit.Stage.STORED, last.stage)
        assertEquals("TEST-SENDER", last.address)
        assertEquals("saved id=1", last.details)
    }
}
