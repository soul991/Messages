package com.messages.core.export

import com.messages.core.db.MessageEntity
import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationExporterTest {

    private fun msg(
        body: String,
        ts: Long,
        outgoing: Boolean = false,
        status: String = "NONE",
        mediaUri: String? = null,
    ) = MessageEntity(
        threadId = 1, address = "+911234567890", body = body,
        timestamp = ts, isOutgoing = outgoing, sendStatus = status,
        mediaUri = mediaUri,
    )

    @Test
    fun `formats header senders order and failure marker`() {
        val out = ConversationExporter.format(
            listOf(
                msg("later message", 2_000_000_000_000L, outgoing = true, status = "FAILED"),
                msg("hi there", 1_000_000_000_000L),
            ),
            conversationName = "Sam",
        )
        assertTrue(out.startsWith("Conversation with Sam"))
        assertTrue(out.contains("2 messages"))
        // Sorted by timestamp: incoming first despite input order.
        assertTrue(out.indexOf("Sam: hi there") < out.indexOf("Me: later message"))
        assertTrue(out.contains("later message [failed to send]"))
    }

    @Test
    fun `indents multiline bodies and placeholders media`() {
        val out = ConversationExporter.format(
            listOf(
                msg("line one\nline two", 1L),
                msg("", 2L, mediaUri = "/x/y.jpg"),
            ),
            conversationName = "A",
        )
        assertTrue(out.contains("line one\n    line two"))
        assertTrue(out.contains("[media message]"))
    }
}
