package com.messages.app.ui.search

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString

/**
 * §8.5.3 match highlighting (Google Messages behavior): every occurrence of
 * every active keyword gets an accent-colored span — in result snippets,
 * sender names, and chat bubbles.
 */
object SearchHighlight {

    /** All (start, end) ranges of any term in [text], case-insensitive, merged. */
    fun matchRanges(text: String, terms: List<String>): List<IntRange> {
        if (text.isEmpty()) return emptyList()
        val ranges = mutableListOf<IntRange>()
        for (term in terms) {
            val t = term.trim()
            if (t.isEmpty()) continue
            val re = Regex(Regex.escape(t), RegexOption.IGNORE_CASE)
            re.findAll(text).forEach { ranges += it.range }
        }
        if (ranges.isEmpty()) return emptyList()
        // Merge overlaps so stacked spans don't double-paint.
        ranges.sortBy { it.first }
        val merged = mutableListOf(ranges[0])
        for (r in ranges.drop(1)) {
            val last = merged.last()
            if (r.first <= last.last + 1) {
                if (r.last > last.last) merged[merged.lastIndex] = last.first..r.last
            } else merged += r
        }
        return merged
    }

    fun annotate(text: String, terms: List<String>, style: SpanStyle): AnnotatedString =
        buildAnnotatedString {
            append(text)
            matchRanges(text, terms).forEach { addStyle(style, it.first, it.last + 1) }
        }

    /**
     * Windowed snippet for result rows: prefer the line/sentence containing
     * the first match; ellipsize around the match when the line runs long.
     */
    fun snippet(text: String, terms: List<String>, maxLen: Int = 140): String {
        val flat = text.replace('\n', ' ').trim()
        val first = matchRanges(flat, terms).firstOrNull()
            ?: return flat.take(maxLen)
        // Sentence/line window around the match.
        val sentenceStart = flat.lastIndexOf('.', first.first).let { if (it == -1) 0 else it + 1 }
        val sentenceEnd = flat.indexOf('.', first.last).let { if (it == -1) flat.length else it + 1 }
        var start = sentenceStart
        var end = sentenceEnd
        if (end - start > maxLen) {
            // Sentence too long — center the window on the match, snap to word edges.
            start = (first.first - maxLen / 3).coerceAtLeast(0)
            if (start > 0) {
                val ws = flat.indexOf(' ', start)
                if (ws != -1 && ws < first.first) start = ws + 1
            }
            end = (start + maxLen).coerceAtMost(flat.length)
            if (end < flat.length) {
                val we = flat.lastIndexOf(' ', end)
                if (we > first.last) end = we
            }
        }
        val core = flat.substring(start, end).trim()
        return buildString {
            if (start > 0) append("… ")
            append(core)
            if (end < flat.length) append(" …")
        }
    }
}
