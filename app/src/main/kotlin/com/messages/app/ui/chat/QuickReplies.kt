package com.messages.app.ui.chat

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

    private val DEFAULTS = listOf(
        "On my way",
        "Can't talk now — call you later",
        "Yes",
        "No",
        "Thank you!",
    )

    val templates = MutableStateFlow<List<String>>(emptyList())

    private var loaded = false

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)

    fun load(ctx: Context): List<String> {
        if (!loaded) {
            loaded = true
            val raw = prefs(ctx).getString(KEY, null)
            templates.value = if (raw == null) DEFAULTS else decode(raw)
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

    private fun decode(raw: String): List<String> =
        runCatching { Json.decodeFromString(ListSerializer(String.serializer()), raw) }
            .getOrDefault(DEFAULTS)
}
