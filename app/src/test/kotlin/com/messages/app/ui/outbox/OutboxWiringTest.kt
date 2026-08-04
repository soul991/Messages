package com.messages.app.ui.outbox

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V2-48 guard: the outbox is reachable, and every path that sends goes through
 * a claim.
 *
 * [com.messages.core.send.SendRetryTest] pins the policy; this pins the wiring
 * the policy needs to matter — a retry nobody schedules and an outbox nobody
 * can open are both indistinguishable from not having built either. The app
 * module's tests are pure JVM (no Robolectric here), so the wiring is checked
 * by reading the source, the same technique the other app-module guards use.
 */
class OutboxWiringTest {

    private fun source(path: String) = File("src/main/kotlin/com/messages/app/$path").readText()

    private val outbox = source("ui/outbox/OutboxScreen.kt")
    private val receivers = source("receiver/OtherReceivers.kt")
    private val scheduling = source("schedule/Scheduling.kt")
    private val home = source("ui/home/HomeScreen.kt")
    private val main = File("src/main/kotlin/com/messages/app/MainActivity.kt").readText()
    private val strings = File("src/main/res/values/strings.xml").readText()

    @Test
    fun `a failed send arms a retry and schedules it`() {
        val handler = receivers.substringAfter("class SmsSentReceiver")
            .substringBefore("class SmsDeliveredReceiver")
        assertTrue(
            "the failure path must ask the repository whether a retry is allowed",
            handler.contains("armAutoRetry(messageId)"),
        )
        assertTrue(
            "and only schedule one when it says yes",
            handler.contains("Scheduler.scheduleRetry(context, messageId, delay)"),
        )
        // The decision must come from armAutoRetry, not from the receiver
        // reading the code itself — one place owns the allowlist.
        assertFalse(
            "the receiver must not decide retryability on its own",
            handler.contains("RETRYABLE_CODES"),
        )
        // A retry may only be armed for a settled failure.
        assertTrue(
            "the attempt must be settled before the retry decision",
            handler.indexOf("settleSendAttempt") < handler.indexOf("armAutoRetry"),
        )
    }

    @Test
    fun `every send path claims before it sends`() {
        val worker = scheduling.substringAfter("class SendRetryWorker")
            .substringBefore("class SnoozeWorker")
        assertTrue(
            "the retry worker must compare-and-set before sending",
            worker.indexOf("claimFailedForResend") < worker.indexOf("SmsRadio.send"),
        )
        assertTrue(
            "a lost claim means someone else took the message — do nothing",
            worker.contains("?: return Result.success()"),
        )
        val resend = outbox.substringAfter("fun resend(").substringBefore("fun cancelScheduled(")
        assertTrue(
            "the Resend button must go through the same claim",
            resend.indexOf("claimFailedForResend") < resend.indexOf("SmsRadio.send"),
        )
        assertTrue(
            "and must disarm the pending automatic retry first, or both would send",
            resend.indexOf("Scheduler.cancelRetry") < resend.indexOf("claimFailedForResend"),
        )
        val sendNow = outbox.substringAfter("fun sendNow(").substringBefore("/**")
        assertTrue(
            "Send now must go through the scheduled claim, not straight to the radio",
            sendNow.indexOf("promoteScheduledToSending") < sendNow.indexOf("SmsRadio.send"),
        )
    }

    @Test
    fun `a lost race is reported rather than swallowed`() {
        // Every claim in this screen can lose to a worker that fired a moment
        // earlier. Silence would read as "nothing happened"; the user has to be
        // told the message already went.
        for (fn in listOf("fun sendNow(", "fun resend(", "fun edit(", "fun changeSim(")) {
            val body = outbox.substringAfter(fn).take(900)
            assertTrue("$fn must report a lost race", body.contains("outbox_too_late"))
        }
        assertTrue(strings.contains("""name="outbox_too_late""""))
    }

    @Test
    fun `an in-flight message offers no edit and no cancel`() {
        val card = outbox.substringAfter("private fun OutboxCard(").substringBefore("@Composable\nprivate fun statusLine")
        assertTrue("edit must be gated on not being in flight", card.contains("if (!busy) {"))
        // Cancel is only offered for SCHEDULED — a message with the radio
        // cannot be recalled, and a button that quietly did nothing would lie.
        assertTrue(card.contains("if (scheduled) {"))
        assertTrue(
            "the reason must be stated, not left as a missing button",
            card.contains("R.string.outbox_in_flight"),
        )
        assertTrue(strings.contains("""name="outbox_in_flight""""))
    }

    @Test
    fun `the outbox is reachable and shows how much is waiting`() {
        assertTrue("Home must offer the entry", home.contains("onOpenOutbox()"))
        assertTrue(
            "the waiting count belongs in the label",
            home.contains("R.string.outbox_title_count, outboxWaiting"),
        )
        assertTrue("a route must exist", main.contains("""composable("outbox")"""))
        assertTrue(main.contains("com.messages.app.ui.outbox.OutboxScreen("))
    }

    @Test
    fun `the outbox screen names all of its strings`() {
        val names = Regex("""R\.string\.(outbox_\w+)""").findAll(outbox + home)
            .map { it.groupValues[1] }.toSet()
        assertTrue("the screen should reference its copy by name", names.size >= 15)
        for (name in names) {
            assertTrue("missing string $name", strings.contains("""name="$name""""))
        }
    }

    @Test
    fun `per-recipient results are shown from the attempt rows`() {
        assertTrue(
            "the detail must come from the recorded attempts, not be recomputed",
            outbox.contains("repo.sendAttemptsFor(message.id)"),
        )
        assertTrue(
            "a failed part must name the carrier's reason",
            outbox.contains("SendFailure.reasonFor(line.resultCode)"),
        )
        // "No delivery report" against a message that never asked for one would
        // read as a fault. Only mention delivery when it was requested.
        assertTrue(outbox.contains("!line.wantDelivery -> stringResource(R.string.outbox_attempt_sent)"))
    }
}
