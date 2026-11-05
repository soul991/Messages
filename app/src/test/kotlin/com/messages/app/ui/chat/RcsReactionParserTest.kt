package com.messages.app.ui.chat

import com.messages.core.db.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RcsReactionParserTest {

    @Test
    fun `parse standard mirrored reactions with smart quotes`() {
        val liked = RcsReactionParser.parse("Liked “See you tomorrow at 8”")
        assertNotNull(liked)
        assertEquals("👍", liked?.emoji)
        assertEquals("Liked", liked?.actionText)
        assertEquals("See you tomorrow at 8", liked?.quotedSnippet)

        val loved = RcsReactionParser.parse("Loved “Congratulations on the promotion!”")
        assertNotNull(loved)
        assertEquals("❤️", loved?.emoji)
        assertEquals("Loved", loved?.actionText)
        assertEquals("Congratulations on the promotion!", loved?.quotedSnippet)

        val disliked = RcsReactionParser.parse("Disliked “That sounds awful”")
        assertNotNull(disliked)
        assertEquals("👎", disliked?.emoji)
        assertEquals("Disliked", disliked?.actionText)

        val laughed = RcsReactionParser.parse("Laughed at “Hahaha that was hilarious”")
        assertNotNull(laughed)
        assertEquals("😂", laughed?.emoji)
        assertEquals("Laughed at", laughed?.actionText)

        val emphasized = RcsReactionParser.parse("Emphasized “Do not forget this”")
        assertNotNull(emphasized)
        assertEquals("‼️", emphasized?.emoji)

        val questioned = RcsReactionParser.parse("Questioned “Are you sure about that?”")
        assertNotNull(questioned)
        assertEquals("❓", questioned?.emoji)
    }

    @Test
    fun `parse standard mirrored reactions with standard ascii quotes`() {
        val liked = RcsReactionParser.parse("Liked \"See you soon\"")
        assertNotNull(liked)
        assertEquals("👍", liked?.emoji)
        assertEquals("See you soon", liked?.quotedSnippet)
    }

    @Test
    fun `parse custom emoji reaction`() {
        val custom = RcsReactionParser.parse("Reacted 🔥 to “That looks awesome”")
        assertNotNull(custom)
        assertEquals("🔥", custom?.emoji)
        assertEquals("Reacted 🔥 to", custom?.actionText)
        assertEquals("That looks awesome", custom?.quotedSnippet)
    }

    @Test
    fun `regular messages return null`() {
        assertNull(RcsReactionParser.parse("Hey how are you?"))
        assertNull(RcsReactionParser.parse("I liked the movie yesterday"))
        assertNull(RcsReactionParser.parse("Liked"))
    }

    @Test
    fun `buildReactionsMap correlates reactions backwards to matching earlier message`() {
        val m1 = MessageEntity(
            id = 100L,
            threadId = 1L,
            address = "+1234567890",
            body = "Dinner tonight at 7?",
            timestamp = 1000L,
            isOutgoing = false,
            category = "INBOX",
        )
        val m2 = MessageEntity(
            id = 101L,
            threadId = 1L,
            address = "+1234567890",
            body = "Sounds good",
            timestamp = 2000L,
            isOutgoing = true,
            category = "INBOX",
        )
        val m3 = MessageEntity(
            id = 102L,
            threadId = 1L,
            address = "+1234567890",
            body = "Liked “Sounds good”",
            timestamp = 3000L,
            isOutgoing = false,
            category = "INBOX",
        )
        val m4 = MessageEntity(
            id = 103L,
            threadId = 1L,
            address = "+1234567890",
            body = "Loved “Dinner tonight at 7?”",
            timestamp = 4000L,
            isOutgoing = true,
            category = "INBOX",
        )

        val map = RcsReactionParser.buildReactionsMap(listOf(m1, m2, m3, m4))

        assertEquals(listOf("👍"), map[101L])
        assertEquals(listOf("❤️"), map[100L])
        assertTrue(map[102L].isNullOrEmpty())
        assertTrue(map[103L].isNullOrEmpty())
    }

    @Test
    fun `correlates truncated quotes with ellipsis`() {
        val original = MessageEntity(
            id = 200L,
            threadId = 1L,
            address = "+1234567890",
            body = "Hey! Let's meet at the coffee shop downtown tomorrow at 5pm",
            timestamp = 1000L,
            isOutgoing = false,
            category = "INBOX",
        )
        val reaction = MessageEntity(
            id = 201L,
            threadId = 1L,
            address = "+1234567890",
            body = "Liked “Hey! Let's meet at the coffee shop...”",
            timestamp = 2000L,
            isOutgoing = true,
            category = "INBOX",
        )

        val map = RcsReactionParser.buildReactionsMap(listOf(original, reaction))
        assertEquals(listOf("👍"), map[200L])

        val consumed = RcsReactionParser.findConsumedReactionIds(listOf(original, reaction))
        assertTrue(consumed.contains(201L))
    }

    @Test
    fun `parse and correlate Reacted with emoji to syntax`() {
        val parsed = RcsReactionParser.parse("Reacted with 🎉 to “Congrats on the new job!”")
        assertNotNull(parsed)
        assertEquals("🎉", parsed?.emoji)
        assertEquals("Congrats on the new job!", parsed?.quotedSnippet)

        val original = MessageEntity(
            id = 300L,
            threadId = 1L,
            address = "+1234567890",
            body = "Congrats on the new job!",
            timestamp = 1000L,
            isOutgoing = false,
            category = "INBOX",
        )
        val reaction = MessageEntity(
            id = 301L,
            threadId = 1L,
            address = "+1234567890",
            body = "Reacted with 🎉 to “Congrats on the new job!”",
            timestamp = 2000L,
            isOutgoing = true,
            category = "INBOX",
        )

        val map = RcsReactionParser.buildReactionsMap(listOf(original, reaction))
        assertEquals(listOf("🎉"), map[300L])
    }
}
