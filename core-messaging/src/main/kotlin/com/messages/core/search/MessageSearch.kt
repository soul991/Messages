package com.messages.core.search

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.messages.core.MessageRepository
import com.messages.core.db.MessageDao
import com.messages.core.db.MessageEntity
import com.messages.protection.Normalizer

/**
 * §8.5 multi-keyword search over the FTS index.
 *
 * Semantics (straight from the PRD): every keyword is equal; a message matches
 * if it contains ANY keyword; results are ranked by how many keywords they
 * match, ties broken by recency. Implemented as one FTS query per keyword
 * merged in memory — identical results to an FTS `OR` query with match-count
 * scoring, without depending on the enhanced-query syntax flag of the device's
 * SQLite build. Prefix matching (`applicat` → "application") comes free via
 * the trailing `*`.
 */
class MessageSearch(private val dao: MessageDao) {

    data class Result(
        val message: MessageEntity,
        /** Which of the active keywords matched — powers ranking + highlighting. */
        val matchedKeywords: List<String>,
    )

    /** 3-character junk-fragment guard (§8.5.1) — applies to the live-typed token. */
    fun isQueryable(keyword: String): Boolean = keyword.trim().length >= MIN_QUERY_LENGTH

    /**
     * Committed chips are deliberate, not junk keystrokes, so they query from
     * 2 characters — a chip for "hi" must find "hi" (the §8.5.1 guard exists
     * to suppress intermediate typing, not explicit keywords).
     */
    suspend fun search(keywords: List<String>, limitPerKeyword: Int = 300): List<Result> {
        val active = keywords.map { it.trim() }.filter { it.length >= MIN_CHIP_LENGTH }.distinct()
        if (active.isEmpty()) return emptyList()

        val byId = LinkedHashMap<Long, Pair<MessageEntity, MutableList<String>>>()
        for (keyword in active) {
            val match = toMatchExpression(keyword) ?: continue
            val rows = runCatching { dao.searchFts(match, limitPerKeyword) }.getOrDefault(emptyList())
            for (row in rows) {
                byId.getOrPut(row.id) { row to mutableListOf() }.second += keyword
            }
        }
        return byId.values
            .map { (msg, matched) -> Result(msg, matched) }
            .sortedWith(
                compareByDescending<Result> { it.matchedKeywords.size }
                    .thenByDescending { it.message.timestamp }
            )
    }

    /**
     * Suggested chips (§8.5.2): frequent distinctive words from the current
     * result set, minus stop-words and the already-active keywords. A term
     * must appear in at least two distinct messages to be suggested.
     */
    fun suggestedChips(
        results: List<Result>,
        activeKeywords: List<String>,
        max: Int = 8,
    ): List<String> {
        val active = activeKeywords.map { it.lowercase().trim() }.filter { it.isNotEmpty() }
        val docFreq = HashMap<String, Int>()
        for (r in results.take(120)) {
            val text = r.message.normalizedBody.ifBlank { r.message.body.lowercase() }
            val seen = HashSet<String>()
            for (token in TOKEN.findAll(text)) {
                val t = token.value
                if (t.length < 3 || t.length > 24) continue
                if (t.all { it.isDigit() }) continue
                if (t in STOP_WORDS) continue
                if (active.any { a -> t == a || t.startsWith(a) || a.startsWith(t) }) continue
                if (seen.add(t)) docFreq[t] = (docFreq[t] ?: 0) + 1
            }
        }
        return docFreq.entries
            .filter { it.value >= 2 }
            .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
            .take(max)
            .map { it.key }
    }

    companion object {
        const val MIN_QUERY_LENGTH = 3
        const val MIN_CHIP_LENGTH = 2

        private val TOKEN = Regex("[a-z0-9]+")

        /**
         * Keyword → FTS MATCH expression. Single word → prefix query;
         * multi-word chip ("driving licence") → phrase query with a prefix on
         * the last token. Returns null for keywords with no indexable token.
         */
        fun toMatchExpression(keyword: String): String? {
            val tokens = TOKEN.findAll(keyword.lowercase()).map { it.value }.toList()
            if (tokens.isEmpty()) return null
            return if (tokens.size == 1) "${tokens[0]}*"
            else "\"${tokens.joinToString(" ")}*\""
        }

        // Search-facing stop-words (English + common SMS/Hinglish filler).
        private val STOP_WORDS = setOf(
            "the", "and", "for", "you", "your", "yours", "are", "was", "were", "has", "have",
            "had", "not", "with", "this", "that", "these", "those", "from", "will", "can",
            "our", "out", "all", "any", "get", "got", "now", "new", "one", "two", "per",
            "please", "pls", "dear", "hello", "him", "her", "his", "she", "they", "them",
            "who", "what", "when", "where", "why", "how", "which", "been", "being", "but",
            "off", "over", "under", "into", "onto", "than", "then", "there", "here", "its",
            "it's", "dont", "don't", "just", "only", "also", "more", "most", "some", "such",
            "very", "via", "use", "used", "using", "may", "might", "should", "would", "could",
            "about", "after", "before", "between", "each", "few", "other", "own", "same",
            "too", "upto", "avl", "till", "valid", "day", "days", "today", "call", "sms",
            "msg", "message", "send", "sent", "hai", "hain", "kar", "karo", "aap", "aapka",
            "liye", "nahi", "koi", "bhi", "yeh", "woh", "par", "mein", "se", "ka", "ki", "ko",
        )
    }
}

/**
 * One-time post-v6 pass: recompute [MessageEntity.normalizedBody] with the
 * engine's Stage-0 normalizer for rows the SQL migration could only
 * approximate. Runs once (unique work + prefs flag), touches only rows still
 * carrying the empty-string default.
 */
class FtsRenormalizeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val prefs = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean(FtsBackfill.KEY_DONE, false)) return Result.success()
        val dao = MessageRepository.get(ctx).db.messages()
        runCatching {
            dao.rowsNeedingNormalization().forEach { row ->
                val normalized = runCatching {
                    Normalizer.normalize(row.body).normalizedText
                }.getOrDefault(row.body.lowercase())
                dao.setNormalizedBody(row.id, normalized.ifBlank { row.body.lowercase() })
            }
        }.onFailure { return Result.retry() }
        prefs.edit().putBoolean(FtsBackfill.KEY_DONE, true).apply()
        return Result.success()
    }
}

object FtsBackfill {
    const val KEY_DONE = "fts_renormalize_done"
    private const val WORK_NAME = "fts_renormalize"

    fun ensureScheduled(context: Context) {
        val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_DONE, false)) return
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_NAME,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<FtsRenormalizeWorker>().build(),
        )
    }
}
