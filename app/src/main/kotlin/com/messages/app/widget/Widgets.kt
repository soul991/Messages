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
                setTextViewText(R.id.widget_subtitle, "spam blocked this week")
                setTextViewText(R.id.widget_total, "%,d silenced all-time".format(total))
                setOnClickPendingIntent(R.id.widget_root, openApp(context, dashboard = true))
            }
            mgr.updateAppWidget(protectionIds, views)
        }
        if (unreadIds.isNotEmpty()) {
            val count = repo.db.conversations().unreadInboxConversations()
            val recent = repo.db.conversations().recentUnreadInbox(3)
            val views = RemoteViews(context.packageName, R.layout.widget_unread).apply {
                setTextViewText(
                    R.id.widget_unread_count,
                    if (count == 0) "All caught up" else "$count unread",
                )
                setTextViewText(
                    R.id.widget_unread_lines,
                    recent.joinToString("\n") { conv ->
                        val name = conv.contactName ?: conv.address
                        "$name · ${conv.lastMessage}".let {
                            if (it.length > 40) it.take(39) + "…" else it
                        }
                    },
                )
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
