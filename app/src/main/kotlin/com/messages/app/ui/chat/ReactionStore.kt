package com.messages.app.ui.chat

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-chat message reaction store (Google Messages & Apple iMessage parity).
 * Stores emoji reactions (e.g. "❤️", "👍", "🔥", "😂") mapped to message IDs.
 */
object ReactionStore {
    val reactions = MutableStateFlow<Map<Long, List<String>>>(emptyMap())
    private var loaded = false

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("message_reactions", Context.MODE_PRIVATE)

    fun reactionsFor(ctx: Context, messageId: Long): List<String> {
        ensureLoaded(ctx)
        return reactions.value[messageId].orEmpty()
    }

    private fun ensureLoaded(ctx: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(ctx)
        val map = mutableMapOf<Long, List<String>>()
        p.all.forEach { (k, v) ->
            val id = k.toLongOrNull() ?: return@forEach
            val raw = v as? String ?: return@forEach
            if (raw.isNotBlank()) {
                map[id] = raw.split("|||")
            }
        }
        reactions.value = map
    }

    fun setReactions(ctx: Context, messageId: Long, items: List<String>) {
        ensureLoaded(ctx)
        prefs(ctx).edit().apply {
            if (items.isEmpty()) remove(messageId.toString())
            else putString(messageId.toString(), items.joinToString("|||"))
        }.apply()
        val m = reactions.value.toMutableMap()
        if (items.isEmpty()) m.remove(messageId) else m[messageId] = items
        reactions.value = m
    }

    fun addReaction(ctx: Context, messageId: Long, emoji: String) {
        val curr = reactionsFor(ctx, messageId).toMutableList()
        // If exact reaction exists, remove it (toggle); otherwise add
        val existingIndex = curr.indexOfFirst { it.startsWith(emoji) }
        if (existingIndex >= 0) {
            curr.removeAt(existingIndex)
        } else {
            curr.add(emoji)
        }
        setReactions(ctx, messageId, curr)
    }
}
