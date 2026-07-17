package com.messages.app.ui.common

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Per-conversation drafts (§8.1): composer text survives leaving the chat.
 * Prefs-backed ("drafts", key = threadId) with an in-memory map flow so the
 * conversation list can show a "Draft" preview reactively.
 */
object DraftStore {

    val drafts = MutableStateFlow<Map<Long, String>>(emptyMap())

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("drafts", Context.MODE_PRIVATE)

    fun init(ctx: Context) {
        drafts.value = prefs(ctx).all
            .mapNotNull { (k, v) ->
                val id = k.toLongOrNull() ?: return@mapNotNull null
                val text = v as? String ?: return@mapNotNull null
                if (text.isBlank()) null else id to text
            }
            .toMap()
    }

    fun get(ctx: Context, threadId: Long): String =
        prefs(ctx).getString(threadId.toString(), "") ?: ""

    fun save(ctx: Context, threadId: Long, text: String) {
        if (text.isBlank()) {
            clear(ctx, threadId)
            return
        }
        prefs(ctx).edit().putString(threadId.toString(), text).apply()
        drafts.value = drafts.value + (threadId to text)
    }

    fun clear(ctx: Context, threadId: Long) {
        prefs(ctx).edit().remove(threadId.toString()).apply()
        drafts.value = drafts.value - threadId
    }
}
