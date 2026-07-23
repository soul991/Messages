package com.messages.app.ui.chat

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-chat pinned messages (Phase 4 item 6). Deliberately LOCAL-ONLY: prefs
 * ("pinned_messages", key = threadId, value = message-id StringSet), never
 * backed up — pins are a this-device reading aid, and message ids are not
 * stable across restores anyway (same reasoning as the spam-backup picker).
 */
object PinStore {

    /** threadId → pinned message ids, newest pin last. */
    val pins = MutableStateFlow<Map<Long, List<Long>>>(emptyMap())

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("pinned_messages", Context.MODE_PRIVATE)

    private var loaded = false

    private fun ensureLoaded(ctx: Context) {
        if (loaded) return
        loaded = true
        pins.value = prefs(ctx).all.mapNotNull { (k, v) ->
            val threadId = k.toLongOrNull() ?: return@mapNotNull null
            val ids = (v as? Set<*>)?.mapNotNull { (it as? String)?.toLongOrNull() }
                ?.sorted() ?: return@mapNotNull null
            if (ids.isEmpty()) null else threadId to ids
        }.toMap()
    }

    fun pinsFor(ctx: Context, threadId: Long): List<Long> {
        ensureLoaded(ctx)
        return pins.value[threadId].orEmpty()
    }

    fun isPinned(ctx: Context, threadId: Long, messageId: Long): Boolean =
        pinsFor(ctx, threadId).contains(messageId)

    fun setPinned(ctx: Context, threadId: Long, messageId: Long, pinned: Boolean) {
        ensureLoaded(ctx)
        val current = pins.value[threadId].orEmpty()
        val updated = if (pinned) (current + messageId).distinct() else current - messageId
        prefs(ctx).edit().apply {
            if (updated.isEmpty()) remove(threadId.toString())
            else putStringSet(threadId.toString(), updated.map(Long::toString).toSet())
        }.apply()
        pins.value =
            if (updated.isEmpty()) pins.value - threadId
            else pins.value + (threadId to updated)
    }
}
