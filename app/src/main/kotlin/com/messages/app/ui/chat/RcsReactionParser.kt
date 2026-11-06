package com.messages.app.ui.chat

import com.messages.core.db.MessageEntity

/**
 * Parser for mirrored RCS / SMS tapback reactions.
 *
 * When cross-platform messaging (such as Google Messages or iMessage fallbacks)
 * sends a reaction over SMS/MMS without native protocol support, it arrives as
 * a synthetic text message quoting the target message, e.g.:
 * - `Liked “See you tomorrow at 8”`
 * - `Loved “Congratulations!”`
 * - `Reacted ❤️ to “Sounds good”`
 *
 * This parser detects these mirrored messages, resolves the intended emoji
 * and target quote snippet, and correlates reactions to their original message bubbles.
 */
object RcsReactionParser {

    data class TapbackReaction(
        val emoji: String,
        val quotedSnippet: String,
        val actionText: String,
    )

    private val REACTION_PREFIXES = mapOf(
        "Liked" to "👍",
        "Loved" to "❤️",
        "Disliked" to "👎",
        "Laughed at" to "😂",
        "Emphasized" to "‼️",
        "Questioned" to "❓",
    )

    // Matches `Liked “...”`, `Loved "..."`, `Reacted ❤️ to “...”`, `Reacted with ❤️ to “...”`, etc.
    // Handles unicode quotes: “ \u201C, ” \u201D, ‘ \u2018, ’ \u2019, " \u0022, ' \u0027
    private val TAPBACK_REGEX = Regex(
        """^(Liked|Loved|Disliked|Laughed\s+at|Emphasized|Questioned|Reacted\s+(?:with\s+)?(\S+)\s+to)\s+[“"'\u201C\u2018](.+?)[”"'\u201D\u2019]$""",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    )

    /**
     * Parse a message body. Returns a [TapbackReaction] if the body is a mirrored reaction,
     * or null if it is a regular message.
     */
    fun parse(body: String): TapbackReaction? {
        val trimmed = body.trim()
        if (trimmed.length < 5) return null
        val match = TAPBACK_REGEX.find(trimmed) ?: return null

        val action = match.groupValues.getOrNull(1)?.trim() ?: return null
        val quote = match.groupValues.getOrNull(3)?.trim() ?: return null
        if (quote.isBlank()) return null

        val customEmoji = match.groupValues.getOrNull(2)?.trim()
        val emoji = if (!customEmoji.isNullOrBlank()) {
            customEmoji
        } else {
            val key = REACTION_PREFIXES.keys.firstOrNull { it.equals(action, ignoreCase = true) }
            REACTION_PREFIXES[key] ?: "👍"
        }

        return TapbackReaction(
            emoji = emoji,
            quotedSnippet = quote,
            actionText = action,
        )
    }

    /**
     * Scans conversation messages and maps target message IDs to lists of reaction emojis.
     * Searches backwards from the reaction timestamp for the best matching message.
     */
    fun buildReactionsMap(messages: List<MessageEntity>): Map<Long, List<String>> {
        val reactionsMap = mutableMapOf<Long, MutableList<String>>()

        for (i in messages.indices) {
            val msg = messages[i]
            val reaction = parse(msg.body) ?: continue

            // Normalize snippet by stripping leading/trailing ellipsis, punctuation, and quotes
            val cleanSnippet = reaction.quotedSnippet.trim()
                .trim('.', '…', ' ', '"', '“', '”', '\'', '‘', '’')
                .lowercase()
            if (cleanSnippet.isBlank()) continue

            // Find the most recent earlier message matching the quoted snippet
            for (j in i - 1 downTo 0) {
                val candidate = messages[j]
                if (parse(candidate.body) != null) continue
                val candidateBody = candidate.body.trim().lowercase()
                if (candidateBody.isBlank()) continue

                if (candidateBody.contains(cleanSnippet) || (candidateBody.length >= 4 && cleanSnippet.contains(candidateBody))) {
                    val list = reactionsMap.getOrPut(candidate.id) { mutableListOf() }
                    if (!list.contains(reaction.emoji)) {
                        list.add(reaction.emoji)
                    }
                    break
                }
            }
        }

        return reactionsMap
    }

    /**
     * Set of mirrored tapback message IDs that were successfully correlated to an earlier
     * message and should be suppressed from the message bubble list to render as badges only.
     */
    fun findConsumedReactionIds(messages: List<MessageEntity>): Set<Long> {
        val consumed = mutableSetOf<Long>()
        for (i in messages.indices) {
            val msg = messages[i]
            val reaction = parse(msg.body) ?: continue

            val cleanSnippet = reaction.quotedSnippet.trim()
                .trim('.', '…', ' ', '"', '“', '”', '\'', '‘', '’')
                .lowercase()
            if (cleanSnippet.isBlank()) continue

            for (j in i - 1 downTo 0) {
                val candidate = messages[j]
                if (parse(candidate.body) != null) continue
                val candidateBody = candidate.body.trim().lowercase()
                if (candidateBody.isBlank()) continue

                if (candidateBody.contains(cleanSnippet) || (candidateBody.length >= 4 && cleanSnippet.contains(candidateBody))) {
                    consumed.add(msg.id)
                    break
                }
            }
        }
        return consumed
    }

    /**
     * Set of message IDs that are mirrored tapback reaction messages and should
     * render as reaction badges rather than raw plain text.
     */
    fun findReactionMessageIds(messages: List<MessageEntity>): Set<Long> {
        val result = mutableSetOf<Long>()
        for (msg in messages) {
            if (parse(msg.body) != null) {
                result.add(msg.id)
            }
        }
        return result
    }
}
