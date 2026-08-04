package com.messages.app.ui.common

import android.content.Context
import com.messages.core.db.Spaces
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Per-conversation drafts (§8.1): composer text survives leaving the chat.
 *
 * R-04/R-31: values are namespaced by (space, threadId), not threadId alone. The
 * same thread ID can legitimately have BOTH a normal-space and a locked-space
 * conversation row, so a threadId-only key let one space read or overwrite the
 * other's composer text. Current policy still declines to persist locked-space
 * drafts at all (ChatScreen skips both the debounced write and the disposal
 * write) — the namespace exists so that policy is enforced by the key, not only
 * by every caller remembering to check.
 *
 * Legacy bare-numeric keys are purged on first init (see [migrateLegacyKeys]).
 */
object DraftStore {

    /** Normal-space drafts only — this feeds the normal Home conversation list. */
    val drafts = MutableStateFlow<Map<Long, String>>(emptyMap())

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("drafts", Context.MODE_PRIVATE)

    private fun key(space: String, threadId: Long) = "$space:$threadId"

    fun init(ctx: Context) {
        migrateLegacyKeys(ctx)
        val normalPrefix = "${Spaces.NORMAL}:"
        drafts.value = prefs(ctx).all
            .mapNotNull { (k, v) ->
                if (!k.startsWith(normalPrefix)) return@mapNotNull null
                val id = k.removePrefix(normalPrefix).toLongOrNull() ?: return@mapNotNull null
                val text = v as? String ?: return@mapNotNull null
                if (text.isBlank()) null else id to text
            }
            .toMap()
    }

    /**
     * One-time upgrade: bare numeric keys were written before drafts were
     * space-aware. Locked-space text that the pre-R-04 disposal bug may have
     * written under a bare key is indistinguishable from a genuine normal-space
     * draft, so legacy values are DROPPED rather than promoted to NORMAL. Losing
     * an unsent draft is a far smaller harm than surfacing secret-space text on
     * the normal Home row.
     */
    private fun migrateLegacyKeys(ctx: Context) {
        val legacy = prefs(ctx).all.keys.filter { it.toLongOrNull() != null }
        if (legacy.isEmpty()) return
        prefs(ctx).edit().apply { legacy.forEach { remove(it) } }.apply()
    }

    fun get(ctx: Context, threadId: Long, space: String = Spaces.NORMAL): String =
        prefs(ctx).getString(key(space, threadId), "") ?: ""

    fun save(ctx: Context, threadId: Long, text: String, space: String = Spaces.NORMAL) {
        if (text.isBlank()) {
            clear(ctx, threadId, space)
            return
        }
        prefs(ctx).edit().putString(key(space, threadId), text).apply()
        if (space == Spaces.NORMAL) drafts.value = drafts.value + (threadId to text)
    }

    fun clear(ctx: Context, threadId: Long, space: String = Spaces.NORMAL) {
        prefs(ctx).edit().remove(key(space, threadId)).apply()
        if (space == Spaces.NORMAL) drafts.value = drafts.value - threadId
    }
}
