package com.messages.core.search

import com.messages.core.db.MessageEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MessageSearchTest {

    @Test
    fun `single keyword becomes prefix query`() {
        assertEquals("applicat*", MessageSearch.toMatchExpression("applicat"))
        assertEquals("neet*", MessageSearch.toMatchExpression("NEET"))
    }

    @Test
    fun `multi-word keyword becomes phrase query with trailing prefix`() {
        assertEquals("\"driving licence*\"", MessageSearch.toMatchExpression("driving licence"))
    }

    @Test
    fun `punctuation and fts operators are stripped`() {
        assertEquals("otp*", MessageSearch.toMatchExpression("otp*\""))
        assertNull(MessageSearch.toMatchExpression("!!!"))
    }

    @Test
    fun `suggested chips come from result set minus stop words and active keywords`() {
        fun msg(id: Long, body: String) = MessageEntity(
            id = id, threadId = 1, address = "X", body = body,
            normalizedBody = body.lowercase(), timestamp = id, isOutgoing = false,
        )
        val search = MessageSearch(dao = FakeDaoUnused)
        val results = listOf(
            MessageSearch.Result(msg(1, "Your NEET application registration is confirmed"), listOf("application")),
            MessageSearch.Result(msg(2, "NEET registration window closes tomorrow"), listOf("application")),
            MessageSearch.Result(msg(3, "The registration for JEE application opened"), listOf("application")),
        )
        val chips = search.suggestedChips(results, activeKeywords = listOf("application"))
        assertTrue("registration should be suggested (3 docs)", "registration" in chips)
        assertTrue("neet should be suggested (2 docs)", "neet" in chips)
        assertTrue("active keyword must not be suggested", "application" !in chips)
        assertTrue("stop word must not be suggested", "your" !in chips)
        assertTrue("single-doc term must not be suggested", "jee" !in chips)
        // Frequency ranking: registration (3) before neet (2).
        assertTrue(chips.indexOf("registration") < chips.indexOf("neet"))
    }

    // suggestedChips never touches the DAO — a null-backed stand-in is safe here.
    private val FakeDaoUnused: com.messages.core.db.MessageDao
        get() = java.lang.reflect.Proxy.newProxyInstance(
            javaClass.classLoader,
            arrayOf(com.messages.core.db.MessageDao::class.java),
        ) { _, _, _ -> throw UnsupportedOperationException("not used") }
            as com.messages.core.db.MessageDao
}
