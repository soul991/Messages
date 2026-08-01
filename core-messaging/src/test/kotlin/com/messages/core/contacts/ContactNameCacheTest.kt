package com.messages.core.contacts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * V2-28. Search decorated up to 200 rows by calling PhoneLookup once per row,
 * on the main thread, behind per-screen maps that never expired or bounded.
 * These pin the shared cache that replaced them: one query per distinct
 * address, misses cached too, LRU-bounded, and dropped wholesale when contacts
 * change.
 */
class ContactNameCacheTest {

    @Before
    fun reset() = ContactNameCache.invalidate()

    private class CountingResolver(private val names: Map<String, String?> = emptyMap()) :
        (String) -> String? {
        val calls = ArrayList<String>()
        override fun invoke(address: String): String? {
            calls += address
            return names[address]
        }
    }

    @Test
    fun `a repeated address is resolved once, not once per row`() {
        val resolver = CountingResolver(mapOf("+1" to "Mom"))
        // The shape of a search result page: many rows, few correspondents.
        val page = List(200) { if (it % 2 == 0) "+1" else "+2" }
        val names = ContactNameCache.resolveAll(page, resolver)
        assertEquals(2, resolver.calls.size)
        assertEquals("Mom", names["+1"])
        assertNull(names["+2"])
    }

    @Test
    fun `a second page costs nothing for addresses already seen`() {
        val first = CountingResolver(mapOf("+1" to "Mom"))
        ContactNameCache.resolveAll(listOf("+1", "+2"), first)
        val second = CountingResolver(mapOf("+1" to "Mom"))
        val names = ContactNameCache.resolveAll(listOf("+1", "+2"), second)
        assertTrue(second.calls.isEmpty())
        assertEquals("Mom", names["+1"])
    }

    @Test
    fun `a number with no contact is remembered as a miss`() {
        // The common case in a spam-heavy inbox. Re-querying it every
        // recomposition is exactly the cost being removed here.
        val resolver = CountingResolver()
        ContactNameCache.resolveAll(listOf("+9"), resolver)
        ContactNameCache.resolveAll(listOf("+9"), resolver)
        assertEquals(1, resolver.calls.size)
        assertTrue(ContactNameCache.isCached("+9"))
    }

    @Test
    fun `a contacts change drops every cached name`() {
        val before = CountingResolver(mapOf("+1" to "Mom"))
        assertEquals("Mom", ContactNameCache.resolveAll(listOf("+1"), before)["+1"])
        ContactNameCache.invalidate()
        val after = CountingResolver(mapOf("+1" to "Mum"))
        assertEquals("Mum", ContactNameCache.resolveAll(listOf("+1"), after)["+1"])
        assertEquals(listOf("+1"), after.calls)
    }

    @Test
    fun `an answer that predates an invalidation is used but not stored`() {
        // The rename landed while this resolution was in flight; the answer in
        // hand may be the old name, so it must not outlive this call.
        val racing = object : (String) -> String? {
            override fun invoke(address: String): String? {
                ContactNameCache.invalidate()
                return "Old name"
            }
        }
        assertEquals("Old name", ContactNameCache.resolveAll(listOf("+1"), racing)["+1"])
        assertTrue(!ContactNameCache.isCached("+1"))
    }

    @Test
    fun `the cache is bounded`() {
        val resolver = CountingResolver()
        ContactNameCache.resolveAll((1..ContactNameCache.MAX_ENTRIES * 2).map { "+$it" }, resolver)
        assertEquals(ContactNameCache.MAX_ENTRIES, ContactNameCache.size())
    }

    @Test
    fun `eviction is least-recently-used, not insertion order`() {
        val resolver = CountingResolver()
        ContactNameCache.resolveAll((1..ContactNameCache.MAX_ENTRIES).map { "+$it" }, resolver)
        // Touch the oldest entry, then overflow by one.
        ContactNameCache.resolveAll(listOf("+1"), resolver)
        ContactNameCache.resolveAll(listOf("+new"), resolver)
        assertTrue(ContactNameCache.isCached("+1"))
        assertTrue(!ContactNameCache.isCached("+2"))
    }

    @Test
    fun `blank addresses are never looked up`() {
        val resolver = CountingResolver()
        val names = ContactNameCache.resolveAll(listOf("", "   "), resolver)
        assertTrue(resolver.calls.isEmpty())
        assertTrue(names.isEmpty())
    }

    @Test
    fun `the single-address form shares the same cache`() {
        val resolver = CountingResolver(mapOf("+1" to "Mom"))
        assertEquals("Mom", ContactNameCache.resolve("+1", resolver))
        val second = CountingResolver()
        assertEquals("Mom", ContactNameCache.resolve("+1", second))
        assertTrue(second.calls.isEmpty())
    }
}
