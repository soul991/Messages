package com.messages.app.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.messages.app.notify.MessageNotifier
import com.messages.app.widget.WidgetUpdater
import com.messages.core.MessageRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * DEBUG-ONLY test harness. Exists only in the debug source set — never shipped.
 *
 * adb cannot broadcast the system-protected SMS_DELIVER, so this receiver lets a
 * device walkthrough drive the EXACT same classify → store → notify pipeline the
 * radio uses (repo.onIncomingSms + MessageNotifier.notifyFor), for verifying
 * notification actions (OTP copy, inline reply, fraud warning) on-device.
 *
 * Authorization (R-24): the manifest gates this on the signature-level
 * permission `com.messages.app.permission.DEBUG_HARNESS`, and [DebugAuth]
 * additionally requires this install's token. Run once without `--es token` and
 * read the expected value from logcat (`DebugAuth`).
 *
 * Usage:
 *   adb shell am broadcast -a com.messages.app.DEBUG_INJECT_SMS \
 *     -n com.messages.app/.debug.InjectSmsReceiver \
 *     --es token "<per-install token>" \
 *     --es address "AX-BANKXX" --es body "Your OTP is 483920 ..."
 */
class InjectSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION) return
        // R-24: signature permission (manifest) + per-install token. Without
        // this, any app on the device could fabricate inbox messages.
        if (!DebugAuth.isAuthorized(context, intent)) return
        val address = intent.getStringExtra("address") ?: return
        val body = intent.getStringExtra("body") ?: return
        val timestamp = System.currentTimeMillis()

        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repo = MessageRepository.get(context)
                val intake = repo.onIncomingSms(address, body, timestamp, null)
                val entity = intake.message
                val verdict = intake.verdict
                MessageNotifier(context).notifyFor(entity, verdict, repo.lookupContactName(address))
                WidgetUpdater.requestUpdate(context)
                android.util.Log.i(
                    "InjectSmsReceiver",
                    "injected from=$address cat=${verdict.category} " +
                        "dangerous=${verdict.dangerous} label=${verdict.protectedLabel} " +
                        "thread=${entity.threadId}",
                )
            } catch (t: Throwable) {
                android.util.Log.e("InjectSmsReceiver", "inject failed", t)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION = "com.messages.app.DEBUG_INJECT_SMS"
    }
}
