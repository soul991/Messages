package com.messages.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * V2-51 guard: the card stays a reading aid, and nothing routes around its gate.
 *
 * [com.messages.protection.CardExtractorTest] pins what may be extracted. This
 * pins the two properties that live in the app module and that a later, well-
 * meaning change is most likely to break:
 *
 *  1. **No action ever ends up on the card.** The moment a summary grows a
 *     "Pay", a dialler tap or a link, the app has put its own credibility
 *     behind a stranger's SMS. Enforced as an absence, because absence is
 *     exactly what is easy to lose by accident.
 *  2. **Every card goes through the eligibility gate.** A second call site that
 *     ran the extractor directly would happily summarise spam.
 *
 * Pure JVM, like the module's other guards — there is no Robolectric here.
 */
class SummaryCardWiringTest {

    private fun source(path: String) = File("src/main/kotlin/com/messages/app/$path").readText()

    private val cardUi = source("ui/chat/SummaryCard.kt")
    private val cards = source("ui/chat/MessageCards.kt")
    private val chat = source("ui/chat/ChatScreen.kt")
    private val settings = source("ui/settings/SettingsScreen.kt")
    private val strings = File("src/main/res/values/strings.xml").readText()

    @Test
    fun `the card carries no action out of the app`() {
        // Each of these is a different way to make a field tappable into the
        // outside world. None of them belongs on a summary of an SMS whose
        // sender is, by construction, not verified.
        for (forbidden in listOf(
            "Intent(", "startActivity", "LocalUriHandler", "uriHandler",
            "ACTION_VIEW", "ACTION_DIAL", "tel:", "http",
        )) {
            assertFalse(
                "the summary card must never contain \"$forbidden\"",
                cardUi.contains(forbidden),
            )
        }
        // Not even a copy button: a reference the user copies from a card they
        // did not read is a reference they did not check.
        assertFalse(cardUi.contains("ClipboardManager"))
        assertFalse(cardUi.contains("AnnotatedString(field.normalized"))
    }

    @Test
    fun `the extractor is only ever reached through the eligibility gate`() {
        val callers = File("src/main/kotlin/com/messages/app")
            .walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { it.readText().contains("CardExtractor.extract(") }
            .map { it.name }
            .toList()
        assertEquals(
            "only MessageCards may run the extractor — every other path would " +
                "skip the spam/fraud gate",
            listOf("MessageCards.kt"), callers,
        )
        assertTrue(cards.contains("if (!eligible(category, protectedLabel, dangerous, fraudWarning)) return null"))
        // An unknown category must fail closed rather than fall through.
        assertTrue(cards.contains(".getOrNull() ?: return false"))
    }

    @Test
    fun `the chat screen shows the card under the body and never instead of it`() {
        assertTrue(chat.contains("MessageCards.cardFor("))
        assertTrue(chat.contains("SummaryCard("))
        // The bubble renders the body; the card is added after it. If the card
        // ever moved above the bubble's own text the "view original" promise
        // would stop being satisfied by simply looking up.
        val bubble = chat.substringAfter("private fun MessageBubble(")
        assertTrue(
            "the card must come after the bubble body in the layout",
            bubble.indexOf("BubbleMetaRow(") < bubble.indexOf("MessageCards.cardFor("),
        )
    }

    @Test
    fun `a correction sticks`() {
        // Dismissals are persisted, not composition state: a card that returned
        // on the next scroll would read as the app overruling the user.
        assertTrue(cards.contains("getSharedPreferences(DISMISS_PREFS"))
        assertTrue(chat.contains("MessageCards.dismissField(context, msg.id, kind)"))
        assertTrue(chat.contains("MessageCards.dismissCard(context, msg.id)"))
        // …and it must be reversible, or "this is wrong" becomes a trap.
        assertTrue(chat.contains("MessageCards.restore(context, msg.id)"))
        assertTrue(strings.contains("""name="summary_action_restore""""))
    }

    @Test
    fun `every field explains itself`() {
        assertTrue(
            "the expanded row must show the extractor's own explanation",
            cardUi.contains("field.explanation"),
        )
        assertTrue(
            "and point at the words in the body it came from",
            cardUi.contains("stringResource(R.string.summary_in_message)") &&
                cardUi.contains("excerpt(body, field)"),
        )
        assertTrue(
            "a less certain amount must say so",
            cardUi.contains("CardExtractor.Confidence.MEDIUM"),
        )
    }

    @Test
    fun `the feature can be turned off from the card and from settings`() {
        assertTrue(chat.contains("MessageCards.setEnabled(context, false)"))
        assertTrue(settings.contains("com.messages.app.ui.chat.MessageCards.setEnabled(ctx, it)"))
        assertTrue(strings.contains("""name="settings_summary_cards""""))
        assertTrue(cards.contains("if (!enabled(context)) return null"))
    }

    @Test
    fun `the card names all of its strings`() {
        val names = Regex("""R\.string\.(summary_\w+|settings_summary_\w+)""")
            .findAll(cardUi + chat + settings)
            .map { it.groupValues[1] }.toSet()
        assertTrue("expected the card's copy to be externalised", names.size >= 12)
        for (name in names) {
            assertTrue("missing string $name", strings.contains("""name="$name""""))
        }
    }
}
