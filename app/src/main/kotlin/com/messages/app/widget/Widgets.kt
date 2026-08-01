package com.messages.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.messages.app.MainActivity
import com.messages.app.R
import com.messages.app.security.AppLock
import com.messages.core.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Home-screen widgets (§8.2): protection stats ("spam blocked this week") and
 * unread/recent chats. Classic RemoteViews — no extra dependencies. Providers
 * refresh on the system's 30-min cycle; [WidgetUpdater.requestUpdate] pushes
 * an immediate refresh whenever a message arrives or is read.
 */
class ProtectionStatsWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        WidgetUpdater.requestUpdate(context)
    }
}

class UnreadWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        WidgetUpdater.requestUpdate(context)
    }
}

object WidgetUpdater {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Fire-and-forget refresh of every placed widget. Cheap no-op when none. */
    fun requestUpdate(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            runCatching { update(appContext) }
        }
    }

    private suspend fun update(context: Context) {
        val mgr = AppWidgetManager.getInstance(context)
        val protectionIds = mgr.getAppWidgetIds(ComponentName(context, ProtectionStatsWidget::class.java))
        val unreadIds = mgr.getAppWidgetIds(ComponentName(context, UnreadWidget::class.java))
        if (protectionIds.isEmpty() && unreadIds.isEmpty()) return

        val repo = MessageRepository.get(context)
        if (protectionIds.isNotEmpty()) {
            val weekAgo = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
            val week = repo.db.messages().spamCountSince(weekAgo)
            val total = repo.db.messages().totalSilenced()
            val views = RemoteViews(context.packageName, R.layout.widget_protection).apply {
                setTextViewText(R.id.widget_count, "%,d".format(week))
                setTextViewText(
                    R.id.widget_subtitle,
                    context.getString(R.string.widget_protection_subtitle),
                )
                setTextViewText(
                    R.id.widget_total,
                    context.getString(R.string.widget_protection_total, "%,d".format(total)),
                )
                setOnClickPendingIntent(R.id.widget_root, openApp(context, dashboard = true))
            }
            mgr.updateAppWidget(protectionIds, views)
        }
        if (unreadIds.isNotEmpty()) {
            val count = repo.db.conversations().unreadInboxConversations()
            // R-03: a launcher widget is an UNAUTHENTICATED surface. When app
            // lock is on, or previews are hidden, senders and bodies must not
            // render there — the count alone is safe and still useful. The DAO
            // additionally excludes legacy locked rows and the LOCKED space.
            val privateSurface = AppLock.isEnabled(context) || AppLock.hidePreviews(context)
            val lines = if (privateSurface) {
                if (count == 0) "" else context.getString(R.string.widget_unread_locked)
            } else {
                repo.db.conversations().recentUnreadInbox(3).joinToString("\n") { conv ->
                    val name = conv.contactName ?: conv.address
                    "$name · ${conv.lastMessage}".let {
                        if (it.length > 40) it.take(39) + "…" else it
                    }
                }
            }
            val views = RemoteViews(context.packageName, R.layout.widget_unread).apply {
                setTextViewText(
                    R.id.widget_unread_count,
                    if (count == 0) {
                        context.getString(R.string.widget_unread_none)
                    } else {
                        // V2-36: a plural, not "$count unread" — Polish, Arabic
                        // and Russian all need more forms than an "s" can give.
                        context.resources.getQuantityString(
                            R.plurals.widget_unread_count, count, count,
                        )
                    },
                )
                setTextViewText(R.id.widget_unread_lines, lines)
                setOnClickPendingIntent(R.id.widget_root, openApp(context, dashboard = false))
            }
            mgr.updateAppWidget(unreadIds, views)
        }
    }

    private fun openApp(context: Context, dashboard: Boolean): PendingIntent =
        PendingIntent.getActivity(
            context, if (dashboard) 2001 else 2002,
            Intent(context, MainActivity::class.java).apply {
                if (dashboard) putExtra("dashboard", true)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
}
