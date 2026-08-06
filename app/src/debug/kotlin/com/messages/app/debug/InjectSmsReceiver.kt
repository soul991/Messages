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
 * additionally requires this install's token.
 *
 * IMPORTANT — a bare `adb shell am broadcast` can NEVER reach this receiver:
 *
 *     W BroadcastQueue: Permission Denial: broadcasting Intent { … } from null
 *     (pid=…, uid=2000) to …/.InjectSmsReceiver requires
 *     com.messages.app.permission.DEBUG_HARNESS
 *
 * `android:permission` on a receiver constrains the SENDER. adb arrives as the
 * shell uid (2000), and the permission is `signature`-level with this app as
 * its sourcePackage — shell is signed with the platform key, so it can never
 * hold it. (Shell auto-holds only permissions declared in its OWN manifest; it
 * cannot declare an app-defined one.) The gate is therefore not bypassable from
 * adb by design, and must NOT be weakened to make it so.
 *
 * The way in is to become this app's own uid — which does hold the permission —
 * via `run-as`, available because a debug build is `debuggable`. `--user 0` is
 * needed as well: under run-as the default USER_CURRENT (-2) is rejected for
 * lack of INTERACT_ACROSS_USERS.
 *
 * The token is NOT printed to logcat (see [DebugAuth]); read it from the app's
 * prefs under the same uid. The file only exists after a first attempt, because
 * the token is generated lazily — so broadcast once (it will be refused), then:
 *   adb shell run-as com.messages.app.debug \
 *     cat /data/data/com.messages.app.debug/shared_prefs/debug_harness.xml
 *
 * Usage — substitute the real applicationId (the debug build carries an
 * `applicationIdSuffix`, so it is `com.messages.app.debug`) and spell the
 * receiver class out in full: the `/.debug.Foo` shorthand resolves against the
 * applicationId and would yield `…debug.debug.Foo`.
 *   adb shell "run-as com.messages.app.debug am broadcast --user 0 \
 *     -a com.messages.app.DEBUG_INJECT_SMS \
 *     -n com.messages.app.debug/com.messages.app.debug.InjectSmsReceiver \
 *     --es token '<per-install token>' \
 *     --es address 'AX-BANKXX' --es body 'Your OTP is 483920 ...'"
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
