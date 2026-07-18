package com.personal.detectivedialer.service

import android.app.NotificationManager
import android.content.Context
import android.telecom.Call
import android.telecom.CallScreeningService
import android.util.Log
import androidx.core.app.NotificationCompat
import com.personal.detectivedialer.DetectiveDialerApp
import com.personal.detectivedialer.R
import com.personal.detectivedialer.data.local.CallLogEntry
import com.personal.detectivedialer.data.prefs.SettingsRepository
import com.personal.detectivedialer.data.repository.CallRepository
import com.personal.detectivedialer.data.screening.ScreeningMatcher
import com.personal.detectivedialer.telecom.CallManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * The OS binds this service on every incoming call. We must decide, within a
 * short budget (~5s), whether to let the call ring or reject it silently.
 *
 * Decision order:
 *   1. In allowlist (contacts + saved numbers) → RING (ring normally).
 *   2. In blocklist → drop silently, no notification.
 *   3. Unknown → POST to the backend's /screen endpoint, which classifies the
 *      number with Gemini and replies with a `decision` label (ALLOW|REJECT|SPAM)
 *      and — authoritatively — a policy `action`:
 *        • RING      → let it ring through
 *        • VOICEMAIL → don't ring; reject so the carrier diverts to voicemail
 *        • REJECT    → drop silently, no ring and no voicemail
 *      We branch on `action`, not the label, then raise a notification with the
 *      AI's decision + reason.
 *
 * Anything that goes wrong — timeout, crash, backend down — fails open to
 * RING so a real call is never dropped by our own bug.
 */
@AndroidEntryPoint
class ScreeningService : CallScreeningService() {

    @Inject lateinit var repository: CallRepository
    @Inject lateinit var contacts: ContactsHelper
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var callManager: CallManager

    private val exceptionHandler = CoroutineExceptionHandler { _, t ->
        Log.e(TAG, "Uncaught error in screening scope", t)
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    /** A screening outcome plus deferred bookkeeping to run after we've responded. */
    private class Verdict(
        val response: CallResponse,
        val bookkeeping: (suspend () -> Unit)? = null,
    )

    override fun onScreenCall(callDetails: Call.Details) {
        // Only screen incoming calls.
        if (callDetails.callDirection != Call.Details.DIRECTION_INCOMING) {
            respondToCall(callDetails, CallResponse.Builder().build())
            return
        }

        val number = extractNumber(callDetails)
        val callerId = callDetails.callerDisplayName?.takeIf { it.isNotBlank() }
        Log.d(TAG, "Screening incoming call")

        scope.launch {
            // The OS gives us ~5s; budget 3.5s for the decision and fail open
            // on timeout or any crash so respondToCall is always reached.
            val verdict = runCatching {
                withTimeoutOrNull(DECISION_BUDGET_MS) { decide(number, callerId) }
            }.onFailure { Log.e(TAG, "Screening decision failed", it) }
                .getOrNull()
                ?: Verdict(allow()).also { Log.w(TAG, "Screening timed out/failed → allowing") }

            runCatching { respondToCall(callDetails, verdict.response) }
                .onFailure { Log.e(TAG, "respondToCall failed", it) }

            // Room upsert + notification only after the OS has its answer.
            verdict.bookkeeping?.let { work ->
                runCatching { work() }.onFailure { Log.e(TAG, "Post-screen bookkeeping failed", it) }
            }
        }
    }

    private suspend fun decide(number: String, callerId: String?): Verdict {
        if (number.isBlank()) {
            // No caller ID — we can't classify it, so let it ring rather than
            // silently dropping a potentially real call.
            return Verdict(allow())
        }

        // 1. Allowlist or contacts → ring through.
        if (repository.isAllowed(number) || contacts.isKnownContact(number)) {
            Log.d(TAG, "Allowed (contact/allowlist)")
            return Verdict(allow())
        }

        // 2. Blocklist → silent reject.
        if (repository.isBlocked(number)) {
            Log.d(TAG, "Blocked (blocklist)")
            return Verdict(rejectSilently())
        }

        val timestamp = System.currentTimeMillis()

        // 3. Local TRAI prefix tier — instant, offline. 140-series = registered
        //    promotional caller (rejected like SPAM), 160-series = transactional
        //    (allowed). Rules refresh from GET /api/screening-rules.
        ScreeningMatcher.matchPrefix(number, repository.screeningRules())?.let { rule ->
            val action = ScreeningMatcher.actionFor(rule.decision, rule.action)
            Log.d(TAG, "Local prefix rule → ${rule.decision}/$action")
            return Verdict(responseForAction(action)) {
                cacheAndNotify(number, callerId, rule.decision, action, rule.label, timestamp)
            }
        }

        // 4. International policy: unknown non-domestic caller → silence/reject
        //    per the user's setting (contacts/allowlist already rang through above).
        if (!ScreeningMatcher.isDomestic(number)) {
            val policy = settings.settings.first().internationalPolicy.uppercase()
            val label = "International unknown number"
            when (policy) {
                "REJECT" -> {
                    Log.d(TAG, "International policy → reject")
                    return Verdict(rejectSilently()) {
                        cacheAndNotify(number, callerId, "SPAM", ScreeningMatcher.ACTION_REJECT, label, timestamp)
                    }
                }
                "SILENCE" -> {
                    Log.d(TAG, "International policy → silence")
                    // The call still rings (silenced), so the action is RING even
                    // though we flag it in history under the REJECT category.
                    return Verdict(silenceRing()) {
                        cacheAndNotify(number, callerId, "REJECT", ScreeningMatcher.ACTION_RING, label, timestamp)
                    }
                }
                // OFF → fall through to the backend like any other unknown number.
            }
        }

        // 5. Unknown → ask the backend to classify it.
        val result = repository.screen(number, callerId, timestamp)
        if (result == null) {
            // Backend unreachable / not configured — let it ring (fail open).
            Log.w(TAG, "Screening unavailable → allowing")
            return Verdict(allow())
        }

        val decision = result.decision.uppercase()
        // Branch on the backend's authoritative policy action, deriving a safe
        // default from the decision when a legacy backend omitted it.
        val action = ScreeningMatcher.actionFor(decision, result.action)
        Log.d(TAG, "Backend decision: $decision/$action")
        return Verdict(responseForAction(action)) {
            cacheAndNotify(number, callerId, decision, action, result.reason, timestamp, result.confidence)
        }
    }

    /**
     * Map a policy action to a telecom response:
     *   RING      → ring through
     *   VOICEMAIL → reject so the carrier diverts to voicemail (no custom greeting;
     *               the OS/network owns the voicemail path — see bug batch #4c)
     *   REJECT    → drop silently, no ring
     * Note: at the CallScreeningService layer VOICEMAIL and REJECT are both a
     * rejected call — whether a rejected call reaches voicemail is decided by the
     * carrier/SIM, not the dialer. The distinction is preserved for history + UX.
     */
    private fun responseForAction(action: String): CallResponse = when (action.uppercase()) {
        ScreeningMatcher.ACTION_RING -> allow()
        ScreeningMatcher.ACTION_VOICEMAIL -> rejectToVoicemail()
        ScreeningMatcher.ACTION_REJECT -> rejectSilently()
        else -> allow()
    }

    /** Cache the screened call locally and raise a notification with the decision. */
    private suspend fun cacheAndNotify(
        number: String,
        callerName: String?,
        decision: String,
        action: String,
        reason: String,
        timestamp: Long,
        confidence: Float = 0f,
    ) {
        // Hand the verdict to the in-call UI: if the call rings (RING / silenced),
        // our InCallService can show the AI's decision + reason live.
        callManager.rememberVerdict(number, decision, action, reason, confidence)
        val callId = "screen-$number-$timestamp"
        repository.upsertCall(
            CallLogEntry(
                id = callId,
                number = number,
                callerName = callerName.orEmpty(),
                timestamp = timestamp,
                category = decision,
                action = action,
                summary = reason,
            ),
        )
        // Lead with the network-verified CNAP name when the carrier gave us one.
        showNotification(callId, callerName?.takeIf { it.isNotBlank() } ?: number, decision, action, reason)
    }

    private fun showNotification(callId: String, number: String, decision: String, action: String, reason: String) {
        val icon = when (action.uppercase()) {
            ScreeningMatcher.ACTION_REJECT -> "🚫"
            ScreeningMatcher.ACTION_VOICEMAIL -> "📭"
            ScreeningMatcher.ACTION_RING -> "✅"
            else -> "📞"
        }
        val notification = NotificationCompat.Builder(this, DetectiveDialerApp.CHANNEL_SCREENED)
            .setSmallIcon(R.drawable.ic_shield)
            .setContentTitle("$icon $decision · $number")
            .setContentText(reason.ifBlank { "Screened by AI" })
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .build()

        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        runCatching { nm.notify(callId.hashCode(), notification) }
    }

    private fun allow(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

    private fun rejectSilently(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSilenceCall(true)
            .setSkipCallLog(false)
            .setSkipNotification(true)
            .build()

    /**
     * VOICEMAIL action: reject the call so the network diverts it to the carrier's
     * voicemail box (same mechanism stock Android uses — there is no public
     * "send to voicemail" InCallService API). Unlike [rejectSilently] we do NOT
     * skip the notification: the user should still see a diverted-to-voicemail
     * call in the shade / history.
     */
    private fun rejectToVoicemail(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(true)
            .setRejectCall(true)
            .setSilenceCall(true)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

    /** REJECT verdict: the call still comes through, but the ring is muted. */
    private fun silenceRing(): CallResponse =
        CallResponse.Builder()
            .setDisallowCall(false)
            .setRejectCall(false)
            .setSilenceCall(true)
            .setSkipCallLog(false)
            .setSkipNotification(false)
            .build()

    private fun extractNumber(details: Call.Details): String {
        val uri = details.handle ?: return ""
        // tel:+9198... → schemeSpecificPart
        return CallRepository.normalize(uri.schemeSpecificPart ?: "")
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ScreeningService"
        private const val DECISION_BUDGET_MS = 3_500L
    }
}
