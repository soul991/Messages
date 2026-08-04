package com.messages.app.ui.chat

import android.content.Context
import com.messages.core.db.Spaces
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * In-chat pinned messages (Phase 4 item 6). Deliberately LOCAL-ONLY: prefs
 * ("pinned_messages", key = (space, threadId), value = message-id StringSet),
 * never backed up — pins are a this-device reading aid, and message ids are not
 * stable across restores anyway (same reasoning as the spam-backup picker).
 *
 * R-31: keys are namespaced by (space, threadId), not threadId alone. The same
 * thread ID can have BOTH normal-space and locked-space conversation rows, so a
 * threadId-only key lets one space see the other's pinned message IDs.
 */
object PinStore {

    /** (space, threadId) → pinned message ids, newest pin last. */
    val pins = MutableStateFlow<Map<Pair<String, Long>, List<Long>>>(emptyMap())

    private fun prefs(ctx: Context) =
        ctx.getSharedPreferences("pinned_messages", Context.MODE_PRIVATE)

    private var loaded = false

    private fun key(space: String, threadId: Long) = "$space:$threadId"

    private fun ensureLoaded(ctx: Context) {
        if (loaded) return
        loaded = true
        pins.value = prefs(ctx).all.mapNotNull { (k, v) ->
            val parts = k.split(":", limit = 2)
            if (parts.size != 2) return@mapNotNull null  // skip legacy bare threadId keys
            val space = parts[0]
            val threadId = parts[1].toLongOrNull() ?: return@mapNotNull null
            val ids = (v as? Set<*>)?.mapNotNull { (it as? String)?.toLongOrNull() }
                ?.sorted() ?: return@mapNotNull null
            if (ids.isEmpty()) null else (space to threadId) to ids
        }.toMap()
    }

    fun pinsFor(ctx: Context, threadId: Long, space: String = Spaces.NORMAL): List<Long> {
        ensureLoaded(ctx)
        return pins.value[space to threadId].orEmpty()
    }

    fun isPinned(ctx: Context, threadId: Long, messageId: Long, space: String = Spaces.NORMAL): Boolean =
        pinsFor(ctx, threadId, space).contains(messageId)

    fun setPinned(ctx: Context, threadId: Long, messageId: Long, pinned: Boolean, space: String = Spaces.NORMAL) {
        ensureLoaded(ctx)
        val current = pins.value[space to threadId].orEmpty()
        val updated = if (pinned) (current + messageId).distinct() else current - messageId
        prefs(ctx).edit().apply {
            if (updated.isEmpty()) remove(key(space, threadId))
            else putStringSet(key(space, threadId), updated.map(Long::toString).toSet())
        }.apply()
        pins.value =
            if (updated.isEmpty()) pins.value - (space to threadId)
            else pins.value + ((space to threadId) to updated)
    }
}
