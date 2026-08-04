package com.messages.app.ui.chat

import android.content.Context
import android.util.LruCache
import com.messages.protection.CardExtractor
import org.json.JSONArray
import org.json.JSONObject

/**
 * V2-51: the app-side half of the deterministic summary card.
 *
 * [CardExtractor] decides *what* a message says; this decides whether to show
 * it, remembers when the user says it got something wrong, and keeps the work
 * off the recomposition path.
 *
 * ## Nothing is stored
 *
 * The finding asked for normalized fields to live "separately from raw bodies".
 * The strictest reading of that is the one taken here: they are not persisted
 * at all. Extraction is pure and fast enough to run per visible bubble, so a
 * second copy of every amount and account tail in the database would buy
 * nothing and cost a new place for that data to leak from — a table that the
 * export, the Drive backup and the FTS index would each need teaching to
 * exclude, and that a future one would forget. What is persisted is only what
 * cannot be recomputed: which cards the user has waved away.
 *
 * The in-memory [cache] is keyed by message id and dies with the process, the
 * same shape [SmartText] already uses for its spans.
 */
object MessageCards {

    private const val PREFS = "settings"
    private const val KEY_ENABLED = "summary_cards"
    private const val DISMISS_PREFS = "summary_card_dismissals"
    private const val KEY_DISMISSED = "dismissed"

    /**
     * How many dismissals are remembered. Old entries fall off the end: a card
     * hidden on a message from two years ago is not worth an unbounded prefs
     * file, and the message will almost certainly never be on screen again.
     */
    private const val MAX_DISMISSALS = 500

    private val cache = LruCache<Long, Holder>(256)

    private class Holder(val card: CardExtractor.Card?)

    /** On by default — an opt-in reading aid nobody finds is not a feature. */
    fun enabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ENABLED, true)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    /**
     * The card for a message, or null when there should not be one.
     *
     * The eligibility gate is checked here rather than left to the call site,
     * so a future caller cannot get a card for a spam message by forgetting a
     * condition. [CardExtractor.eligible] takes the engine's stored verdict —
     * this never re-classifies.
     */
    fun cardFor(
        context: Context,
        messageId: Long,
        body: String,
        category: String,
        protectedLabel: String,
        dangerous: Boolean,
        fraudWarning: Boolean,
    ): CardExtractor.Card? {
        if (!enabled(context)) return null
        if (!eligible(category, protectedLabel, dangerous, fraudWarning)) return null
        cache.get(messageId)?.let { return visible(context, messageId, it.card) }
        val card = CardExtractor.extract(body)
        cache.put(messageId, Holder(card))
        return visible(context, messageId, card)
    }

    /**
     * String-typed bridge to [CardExtractor.eligible]. The database stores the
     * verdict as text, and an unrecognised value must mean "no card" — a typo
     * or a category added later has to fail closed, not fall through to the
     * permissive branch.
     */
    internal fun eligible(
        category: String,
        protectedLabel: String,
        dangerous: Boolean,
        fraudWarning: Boolean,
    ): Boolean {
        val cat = runCatching { com.messages.protection.Category.valueOf(category) }
            .getOrNull() ?: return false
        val label = runCatching { com.messages.protection.ProtectedLabel.valueOf(protectedLabel) }
            .getOrNull() ?: com.messages.protection.ProtectedLabel.NONE
        return CardExtractor.eligible(cat, label, dangerous, fraudWarning)
    }

    /** Applies the user's corrections: drop dismissed fields, and the whole
     *  card once nothing is left. */
    private fun visible(
        context: Context,
        messageId: Long,
        card: CardExtractor.Card?,
    ): CardExtractor.Card? {
        if (card == null) return null
        val hidden = dismissedFor(context, messageId)
        if (hidden.contains(ALL)) return null
        if (hidden.isEmpty()) return card
        val kept = card.fields.filterNot { hidden.contains(it.kind.name) }
        return if (kept.isEmpty()) null else card.copy(fields = kept)
    }

    /** Marker for "the whole card", stored alongside field-kind names. */
    private const val ALL = "*"

    // ---- corrections ---------------------------------------------------

    /**
     * The correction control the finding asked for. A wrong field is the user's
     * to remove, and the removal has to survive a scroll — a card that comes
     * back after being dismissed reads as the app insisting it was right.
     */
    fun dismissField(context: Context, messageId: Long, kind: CardExtractor.FieldKind) {
        record(context, messageId, kind.name)
    }

    fun dismissCard(context: Context, messageId: Long) {
        record(context, messageId, ALL)
    }

    fun restore(context: Context, messageId: Long) {
        val all = load(context).toMutableList()
        all.removeAll { it.messageId == messageId }
        save(context, all)
    }

    fun dismissedFor(context: Context, messageId: Long): Set<String> =
        load(context).firstOrNull { it.messageId == messageId }?.kinds ?: emptySet()

    private fun record(context: Context, messageId: Long, kind: String) {
        val all = load(context).toMutableList()
        val at = all.indexOfFirst { it.messageId == messageId }
        if (at >= 0) {
            val entry = all.removeAt(at)
            all.add(entry.copy(kinds = entry.kinds + kind))
        } else {
            all.add(Dismissal(messageId, setOf(kind)))
        }
        // Newest last; the overflow is trimmed from the front.
        save(context, if (all.size > MAX_DISMISSALS) all.takeLast(MAX_DISMISSALS) else all)
    }

    private data class Dismissal(val messageId: Long, val kinds: Set<String>)

    private fun load(context: Context): List<Dismissal> = runCatching {
        val raw = context.getSharedPreferences(DISMISS_PREFS, Context.MODE_PRIVATE)
            .getString(KEY_DISMISSED, null) ?: return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val kinds = o.getJSONArray("kinds")
            Dismissal(
                o.getLong("id"),
                (0 until kinds.length()).map { kinds.getString(it) }.toSet(),
            )
        }
    }.getOrDefault(emptyList())

    private fun save(context: Context, all: List<Dismissal>) {
        val arr = JSONArray()
        for (d in all) {
            arr.put(
                JSONObject()
                    .put("id", d.messageId)
                    .put("kinds", JSONArray(d.kinds.toList()))
            )
        }
        context.getSharedPreferences(DISMISS_PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_DISMISSED, arr.toString()).apply()
    }

    /** Drops the in-memory extraction for a message whose card just changed. */
    fun invalidate(messageId: Long) {
        cache.remove(messageId)
    }
}
