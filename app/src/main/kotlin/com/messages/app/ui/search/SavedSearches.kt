package com.messages.app.ui.search

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * §8.5.2 saved searches: the user's frequently used keyword-chip combos,
 * persisted locally and offered as one-tap chip-sets when the search bar is
 * empty. Recorded automatically whenever a search result is opened.
 */
object SavedSearches {

    private const val PREFS = "saved_searches"
    private const val KEY = "combos"
    private const val MAX_STORED = 20

    fun record(context: Context, terms: List<String>) {
        val clean = terms.map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        if (clean.isEmpty()) return
        val key = clean.sorted().joinToString("")
        val all = load(context).toMutableList()
        val existing = all.indexOfFirst { it.key == key }
        val now = System.currentTimeMillis()
        if (existing >= 0) {
            val e = all[existing]
            all[existing] = e.copy(count = e.count + 1, lastUsed = now)
        } else {
            all += Combo(clean, key, 1, now)
        }
        save(context, all.sortedWith(comparator).take(MAX_STORED))
    }

    /** Best combos first: frequency, then recency. */
    fun top(context: Context, n: Int = 6): List<List<String>> =
        load(context).sortedWith(comparator).take(n).map { it.terms }

    private val comparator =
        compareByDescending<Combo> { it.count }.thenByDescending { it.lastUsed }

    private data class Combo(
        val terms: List<String>,
        val key: String,
        val count: Int,
        val lastUsed: Long,
    )

    private fun load(context: Context): List<Combo> = runCatching {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, null) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val termsArr = o.getJSONArray("terms")
            val terms = (0 until termsArr.length()).map { termsArr.getString(it) }
            Combo(terms, terms.sorted().joinToString(""), o.getInt("count"), o.getLong("last"))
        }
    }.getOrDefault(emptyList())

    private fun save(context: Context, combos: List<Combo>) {
        val arr = JSONArray()
        combos.forEach { c ->
            arr.put(
                JSONObject()
                    .put("terms", JSONArray(c.terms))
                    .put("count", c.count)
                    .put("last", c.lastUsed)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
