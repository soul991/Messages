package com.messages.core.contacts

/**
 * V2-28: one process-wide, bounded, invalidatable address → display-name cache.
 *
 * Every list that shows a name (search results, starred messages) used to hold
 * its own `HashMap` and call [com.messages.core.MessageRepository.displayNameFor]
 * — a `ContactsContract.PhoneLookup` query — per row, on whatever dispatcher the
 * flow happened to be collected on. Three problems, in order of severity:
 *
 *  - Two hundred provider round-trips, on the main thread, per keystroke-driven
 *    re-query. A slow or busy contacts provider turns that into visible typing
 *    lag.
 *  - Those per-screen maps never expired, so a renamed or deleted contact kept
 *    its old name until the screen's ViewModel died.
 *  - They also never bounded, so a long-lived screen over a large history could
 *    keep an entry for every address ever displayed.
 *
 * This cache is shared, so a name resolved for search is free for starred; it is
 * LRU-bounded; it caches misses too (a number with no contact is the common
 * case, and re-querying it every recomposition is exactly the cost being
 * removed); and it is invalidated wholesale by
 * [com.messages.core.contacts.ContactSync] whenever the contacts provider
 * changes.
 *
 * It deliberately holds no [android.content.Context] and performs no queries
 * itself — the caller supplies the resolver, which keeps this testable on the
 * JVM and keeps provider work on the caller's dispatcher.
 */
object ContactNameCache {

    /**
     * Roughly a few screens' worth of distinct correspondents. Small enough to
     * be irrelevant to the heap, large enough that scrolling a search result
     * set does not evict its own entries.
     */
    const val MAX_ENTRIES = 512

    /** A resolved answer, including "no contact" — misses are worth caching. */
    private class Entry(val name: String?)

    private val lru = object : LinkedHashMap<String, Entry>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>) =
            size > MAX_ENTRIES
    }

    /**
     * Bumped by [invalidate]. A resolution that started before an invalidation
     * must not write its now-stale answers back afterwards.
     */
    private var version = 0

    /** Drop everything: the contacts provider changed under us. */
    fun invalidate() = synchronized(lru) {
        lru.clear()
        version++
        Unit
    }

    /**
     * Names for [addresses], calling [resolve] only for the ones not already
     * known — once per distinct address, never under the lock.
     *
     * [resolve] is expected to be the provider lookup and to be called from a
     * background dispatcher; this function is as blocking as [resolve] is.
     */
    fun resolveAll(
        addresses: Collection<String>,
        resolve: (String) -> String?,
    ): Map<String, String?> {
        val wanted = addresses.filter { it.isNotBlank() }.distinct()
        val out = HashMap<String, String?>(wanted.size)
        val misses = ArrayList<String>()
        val startedAt: Int
        synchronized(lru) {
            startedAt = version
            for (address in wanted) {
                val hit = lru[address]
                if (hit != null) out[address] = hit.name else misses += address
            }
        }
        if (misses.isEmpty()) return out
        // Outside the lock: a provider query must never be held under it, or one
        // slow lookup blocks every other screen resolving a name.
        val resolved = LinkedHashMap<String, String?>(misses.size)
        for (address in misses) resolved[address] = resolve(address)
        synchronized(lru) {
            // Invalidated while we were querying: the answers may predate the
            // change that invalidated us, so they are used once and not stored.
            if (version == startedAt) {
                for ((address, name) in resolved) lru[address] = Entry(name)
            }
        }
        out.putAll(resolved)
        return out
    }

    /** Single-address convenience with the same caching contract. */
    fun resolve(address: String, resolve: (String) -> String?): String? =
        resolveAll(listOf(address), resolve)[address]

    /** Test/diagnostic only. */
    internal fun size(): Int = synchronized(lru) { lru.size }

    internal fun isCached(address: String): Boolean = synchronized(lru) { lru.containsKey(address) }
}
