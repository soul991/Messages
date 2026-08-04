package com.messages.app.receiver

import android.content.BroadcastReceiver
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout

/**
 * V2-34: one supervised place for the work a broadcast receiver does after
 * `goAsync()`.
 *
 * Each receiver used to build its own `CoroutineScope` per broadcast. Three
 * things went wrong with that:
 *
 *  - **No supervision.** Two of the scopes had no [SupervisorJob] and no
 *    `catch`, so an exception from a repository call reached the default
 *    handler and took the process down — from a delivery-report callback.
 *  - **No time budget.** `goAsync()` extends the receiver's window; it does not
 *    remove it. Work that blocks on the provider, the network or a busy
 *    database can outlive the window, and the system then kills the process
 *    with the pending result still open — which reads, from the platform's
 *    side, as a receiver that never finished.
 *  - **`finish()` on one path only.** Anything thrown before the `finally`
 *    — or by the `catch` block itself — left the broadcast open.
 *
 * The scope here lives for the process, is supervised so one failure cannot
 * cancel the next broadcast's work, and always finishes the pending result
 * exactly once.
 *
 * This is deliberately NOT a durability mechanism: if the process dies, the
 * work is gone either way. Work that must survive belongs in WorkManager; what
 * this bounds is the window a receiver is allowed to occupy.
 */
internal object ReceiverWork {

    /**
     * The platform's own guidance is that a receiver finishes within ten
     * seconds; a little under that leaves room for the recovery path to run
     * and for `finish()` to be delivered.
     */
    const val DEFAULT_BUDGET_MILLIS = 8_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Run [block] with a time budget, then finish [pending] no matter how it
     * ended.
     *
     * [onFailure] is the "never lose the message" path — it runs for a thrown
     * failure AND for a timeout, under [NonCancellable] so a recovery write
     * cannot itself be interrupted partway.
     */
    fun launch(
        pending: BroadcastReceiver.PendingResult,
        tag: String,
        budgetMillis: Long = DEFAULT_BUDGET_MILLIS,
        onFailure: (suspend (Throwable) -> Unit)? = null,
        block: suspend CoroutineScope.() -> Unit,
    ) {
        scope.launch {
            guard(tag, budgetMillis, onFailure, finish = { pending.finish() }, block = block)
        }
    }

    /**
     * The policy itself, with the Android [BroadcastReceiver.PendingResult]
     * replaced by a plain [finish] callback so it can be tested on the JVM.
     */
    internal suspend fun guard(
        tag: String,
        budgetMillis: Long,
        onFailure: (suspend (Throwable) -> Unit)?,
        finish: () -> Unit,
        // android.util.Log is not available to a plain JVM test; the default is
        // the only thing production ever passes.
        log: (String, Throwable) -> Unit = { message, t -> Log.e(tag, message, t) },
        block: suspend CoroutineScope.() -> Unit,
    ) {
        try {
            withTimeout(budgetMillis) { block() }
        } catch (timeout: TimeoutCancellationException) {
            log("receiver work exceeded its ${budgetMillis}ms budget", timeout)
            recover(onFailure, timeout, log)
        } catch (cancelled: CancellationException) {
            // Not ours to swallow — but the pending result is still ours to
            // close, which the finally below does.
            throw cancelled
        } catch (t: Throwable) {
            log("receiver work failed", t)
            recover(onFailure, t, log)
        } finally {
            // A second finish() would throw; a missing one hangs the
            // broadcast. Neither may take the process with it.
            runCatching { finish() }
        }
    }

    private suspend fun recover(
        onFailure: (suspend (Throwable) -> Unit)?,
        cause: Throwable,
        log: (String, Throwable) -> Unit,
    ) {
        if (onFailure == null) return
        withContext(NonCancellable) {
            try {
                onFailure(cause)
            } catch (t: Throwable) {
                log("receiver recovery failed", t)
            }
        }
    }
}
