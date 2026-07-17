package com.messages.app.shortcut

import android.content.Context
import android.content.Intent
import androidx.core.app.Person
import androidx.core.content.LocusIdCompat
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.messages.app.MainActivity
import com.messages.app.R

/**
 * Conversation shortcuts (§8.2, Android platform features): long-lived dynamic
 * shortcuts per conversation. They power launcher long-press shortcuts,
 * direct-share targets (share_targets.xml matches [CATEGORY_TEXT_SHARE]), and
 * are the anchor notification bubbles require (setShortcutId).
 *
 * Locked conversations never get a shortcut — their names must not surface in
 * share sheets or launchers.
 */
object ConversationShortcuts {

    const val CATEGORY_TEXT_SHARE = "com.messages.app.category.TEXT_SHARE_TARGET"

    fun idFor(threadId: Long) = "thread_$threadId"

    /** Push/update the shortcut for a conversation; returns its shortcut id. */
    fun push(context: Context, threadId: Long, displayName: String): String {
        val id = idFor(threadId)
        runCatching {
            val person = Person.Builder().setName(displayName).build()
            val intent = Intent(context, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                putExtra("threadId", threadId)
            }
            val shortcut = ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(displayName.ifBlank { "Conversation" })
                .setIcon(IconCompat.createWithResource(context, R.mipmap.ic_launcher))
                .setIntent(intent)
                .setLongLived(true)
                .setPerson(person)
                .setLocusId(LocusIdCompat(id))
                .setCategories(setOf(CATEGORY_TEXT_SHARE))
                .build()
            ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        }
        return id
    }

    /** Report usage so direct-share ranking learns the user's frequent chats. */
    fun reportUsed(context: Context, threadId: Long) {
        runCatching { ShortcutManagerCompat.reportShortcutUsed(context, idFor(threadId)) }
    }

    /** Remove a conversation's shortcut (e.g. when it gets locked or deleted). */
    fun remove(context: Context, threadId: Long) {
        runCatching {
            ShortcutManagerCompat.removeLongLivedShortcuts(context, listOf(idFor(threadId)))
        }
    }
}
