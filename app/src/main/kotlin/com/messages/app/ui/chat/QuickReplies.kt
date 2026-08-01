package com.messages.app.ui.chat

import com.messages.app.R
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/**
 * Quick-reply templates (Phase 4 item 8): a user-managed, ordered list of
 * canned messages. Prefs-backed as a JSON array ("settings" /
 * `quick_reply_templates`) so order survives — StringSet would shuffle it.
 * Seeded with a few starters on first use; an empty list the user created
 * stays empty (the seed only applies while the pref is absent).
 */
object QuickReplies {

    private const val KEY = "quick_reply_templates"

    /**
     * V2-36. The seeds are sentences the user sends to other people, so they
     * have to be in the user's language. They are read once, on the first
     * launch that has no saved list, and then persist as ordinary user data —
     * changing the phone's language later does not rewrite replies the user
     * has since edited, which is the behaviour you want here.
     */
    private val DEFAULT_RES = listOf(
        R.string.quick_reply_on_my_way,
        R.string.quick_reply_call_later,
        R.string.quick_reply_yes,
        R.string.quick_reply_no,
        R.string.quick_reply_thanks,
    )

    private fun defaults(ctx: Context): List<String> = DEFAULT_RES.map(ctx::getString)

    val templates = MutableStateFlow<List<String>>(emptyList())

    private var loaded = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(ctx: Context): List<String> {
        if (!loaded) {
            loaded = true
            val raw = prefs(ctx).getString(KEY, null)
            templates.value = if (raw == null) defaults(ctx) else decode(ctx, raw)
        }
        return templates.value
    }

    fun add(ctx: Context, text: String) {
        val t = text.trim()
        if (t.isEmpty()) return
        save(ctx, (load(ctx) + t).distinct())
    }

    fun remove(ctx: Context, text: String) = save(ctx, load(ctx) - text)

    private fun save(ctx: Context, list: List<String>) {
        prefs(ctx).edit().putString(KEY, encode(list)).apply()
        templates.value = list
    }

    private fun encode(list: List<String>): String =
        Json.encodeToString(ListSerializer(String.serializer()), list)

    private fun decode(ctx: Context, raw: String): List<String> =
        runCatching { Json.decodeFromString(ListSerializer(String.serializer()), raw) }
            .getOrDefault(defaults(ctx))
}
