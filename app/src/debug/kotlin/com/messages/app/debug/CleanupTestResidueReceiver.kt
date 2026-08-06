package com.messages.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.messages.app.widget.WidgetUpdater
import com.messages.core.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * DEBUG-ONLY test harness. Exists only in the debug source set — never shipped.
 *
 * Permanently removes rows created by [InjectSmsReceiver] from BOTH stores
 * (Telephony provider + Room index). adb shell cannot delete provider rows
 * itself (non-default-app writes are silently ignored), so this runs the
 * deletion inside the app, which holds the default-SMS role.
 *
 * Guards: only INCOMING rows are accepted, and only rows whose timestamp has
 * millisecond residue — real radio messages carry the carrier's
 * second-granularity SMSC timestamp (…000), injected rows carry
 * System.currentTimeMillis(). A real message can therefore never be deleted
 * even if its provider id were passed by mistake. Emptied threads have their
 * conversation row removed.
 *
 * Authorization (R-24): signature-level permission
 * `com.messages.app.permission.DEBUG_HARNESS` plus this install's [DebugAuth]
 * token. Only the explicit ids passed in `smsIds` are ever touched.
 *
 * Usage — note this must go through `run-as`, not a bare `am broadcast`; see
 * [InjectSmsReceiver] for why plain adb can never reach these receivers, and
 * for how to obtain the token. Substitute the real applicationId (the debug
 * build carries an `applicationIdSuffix`, so it is `com.messages.app.debug`)
 * and spell the receiver class out in full — the `/.debug.Foo` shorthand
 * resolves against the applicationId and would yield `…debug.debug.Foo`:
 *   adb shell "run-as com.messages.app.debug am broadcast --user 0 \
 *     -a com.messages.app.DEBUG_CLEANUP_RESIDUE \
 *     -n com.messages.app.debug/com.messages.app.debug.CleanupTestResidueReceiver \
 *     --es token '<per-install token>' --es smsIds '1,2,3'"
 */
class CleanupTestResidueReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        // R-24: signature permission (manifest) + per-install token. Deletion is
        // irreversible, so an unauthenticated caller must never reach it.
        if (!DebugAuth.isAuthorized(context, intent)) return
        // Explicit ids only — there is no "delete everything" mode by design.
        val smsIds = intent.getStringExtra("smsIds")
            ?.split(',')?.mapNotNull { it.trim().toLongOrNull() }
            ?.takeIf { it.isNotEmpty() } ?: return

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = MessageRepository.get(context)
                val db = repo.db
                val touchedThreads = mutableSetOf<Long>()
                for (smsId in smsIds) {
                    val msg = db.messages().bySmsId(smsId)
                    if (msg == null) {
                        android.util.Log.w(TAG, "skip smsId=$smsId: not in index")
                        continue
                    }
                    if (msg.isOutgoing || msg.timestamp % 1000 == 0L) {
                        android.util.Log.w(
                            TAG,
                            "REFUSED smsId=$smsId (outgoing=${msg.isOutgoing} " +
                                "ts=${msg.timestamp}): not an injected row",
                        )
                        continue
                    }
                    val providerDeleted = context.contentResolver.delete(
                        Uri.parse("content://sms/$smsId"), null, null,
                    )
                    db.messages().userDelete(msg.id)
                    touchedThreads += msg.threadId
                    android.util.Log.i(
                        TAG,
                        "deleted smsId=$smsId roomId=${msg.id} thread=${msg.threadId} " +
                            "from=${msg.address} provider=$providerDeleted " +
                            "body='${msg.body.take(40)}'",
                    )
                }
                for (threadId in touchedThreads) {
                    val remaining = db.query(
                        androidx.sqlite.db.SimpleSQLiteQuery(
                            "SELECT COUNT(*) FROM messages WHERE threadId = ?",
                            arrayOf(threadId),
                        ),
                    ).use { c -> c.moveToFirst(); c.getLong(0) }
                    if (remaining == 0L) {
                        db.conversations().deleteByThreadId(threadId, com.messages.core.db.Spaces.NORMAL)
                        android.util.Log.i(TAG, "thread=$threadId emptied, conversation row removed")
                    } else {
                        android.util.Log.i(TAG, "thread=$threadId keeps $remaining real rows")
                    }
                }
                WidgetUpdater.requestUpdate(context)
                android.util.Log.i(TAG, "cleanup done: ${smsIds.size} ids requested")
            } catch (t: Throwable) {
                android.util.Log.e(TAG, "cleanup failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.messages.app.DEBUG_CLEANUP_RESIDUE"
        private const val TAG = "CleanupTestResidue"
    }
}
