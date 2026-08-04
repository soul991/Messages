package com.messages.app.ui.chat

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.view.textclassifier.TextClassificationManager
import android.view.textclassifier.TextClassifier
import android.view.textclassifier.TextLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Smart text actions in bubbles (Phase 4 item 5): dates → calendar,
 * addresses → maps, numbers → dial, URLs → browser, plus a deterministic
 * India Post tracking-number pattern. Entity spans come from the platform
 * [TextClassifier] (on-device, API 28+; URL/phone regex fallback below 28).
 *
 * HARD RULE, enforced by [eligible] and re-checked at every call site:
 * never on Spam/Blocked messages and never on Dangerous/fraud-flagged
 * messages — a scam must not get tappable anything. (Group D item 20
 * separately disables links on Dangerous outright.)
 */
object SmartText {

    data class Span(val start: Int, val end: Int, val type: String)

    const val TYPE_TRACKING = "tracking"

    private const val MAX_LEN = 700
    private val cache = LruCache<Long, List<Span>>(256)

    /** Smart actions are for legitimate mail only. */
    fun eligible(category: String?, dangerous: Boolean, fraudWarning: Boolean): Boolean =
        !dangerous && !fraudWarning && category !in listOf("SPAM", "BLOCKED")

    /** Cache-only peek so already-detected bubbles compose without a coroutine round trip. */
    fun cached(messageId: Long): List<Span>? = cache.get(messageId)

    suspend fun spansFor(context: Context, messageId: Long, body: String): List<Span> {
        cache.get(messageId)?.let { return it }
        val spans = withContext(Dispatchers.Default) { detect(context, body) }
        cache.put(messageId, spans)
        return spans
    }

    private fun detect(context: Context, body: String): List<Span> {
        val text = if (body.length > MAX_LEN) body.substring(0, MAX_LEN) else body
        val platform: List<Span> = if (Build.VERSION.SDK_INT >= 28) {
            runCatching {
                val classifier = context.getSystemService(TextClassificationManager::class.java)
                    ?.textClassifier ?: return@runCatching emptyList()
                classifier.generateLinks(TextLinks.Request.Builder(text).build())
                    .links.mapNotNull { link ->
                        val type = (0 until link.entityCount)
                            .map { link.getEntity(it) }
                            .firstOrNull { it in SUPPORTED }
                            ?: return@mapNotNull null
                        Span(link.start, link.end, type)
                    }
            }.getOrDefault(emptyList())
        } else {
            fallbackDetect(text)
        }
        // Custom tracking-number spans win overlaps against platform spans
        // (the classifier tends to read "RX123456789IN" as a flight number).
        val tracking = trackingSpans(text)
        return (tracking + platform.filter { p ->
            tracking.none { t -> p.start < t.end && t.start < p.end }
        }).sortedBy { it.start }
    }

    /** India Post consignment format — deterministic, no context needed. */
    internal fun trackingSpans(text: String): List<Span> =
        TrackingNumbers.ranges(text)
            .map { Span(it.first, it.last + 1, TYPE_TRACKING) }

    /** Pre-API-28: URLs and phone numbers only. */
    private fun fallbackDetect(text: String): List<Span> {
        val urls = android.util.Patterns.WEB_URL.toRegex().findAll(text)
            .filter { it.value.contains("://") || it.value.startsWith("www.") }
            .map { Span(it.range.first, it.range.last + 1, TextClassifier.TYPE_URL) }
        val phones = Regex("""\+?\d[\d\s\-()]{7,14}\d""").findAll(text)
            .map { Span(it.range.first, it.range.last + 1, TextClassifier.TYPE_PHONE) }
        return (urls + phones).toList()
    }

    /**
     * Fires the action for a tapped span. URL/phone/email/tracking are direct
     * intents; date/address ask the classifier for its system action (which
     * carries the parsed time / geocoded location) and fire the first one.
     */
    fun performAction(context: Context, body: String, span: Span) {
        val value = body.substring(span.start, minOf(span.end, body.length))
        val direct: Intent? = when (span.type) {
            TextClassifier.TYPE_URL -> Intent(
                Intent.ACTION_VIEW,
                Uri.parse(if (value.contains("://")) value else "https://$value"),
            )
            TextClassifier.TYPE_PHONE -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:$value"))
            TextClassifier.TYPE_EMAIL -> Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$value"))
            TYPE_TRACKING -> Intent(Intent.ACTION_WEB_SEARCH)
                .putExtra(android.app.SearchManager.QUERY, "track $value")
            else -> null
        }
        if (direct != null) {
            runCatching {
                context.startActivity(direct.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        if (Build.VERSION.SDK_INT >= 28) {
            // Dates/addresses: the classifier's own action knows the parsed
            // datetime / location. Runs a quick classify on tap only.
            Thread {
                runCatching {
                    val classifier = context.getSystemService(TextClassificationManager::class.java)
                        ?.textClassifier ?: return@runCatching
                    val classification = classifier.classifyText(
                        android.view.textclassifier.TextClassification.Request.Builder(
                            body, span.start, minOf(span.end, body.length),
                        ).build()
                    )
                    classification.actions.firstOrNull()?.actionIntent?.send()
                }
            }.start()
        }
    }

    private val SUPPORTED = setOf(
        TextClassifier.TYPE_URL,
        TextClassifier.TYPE_PHONE,
        TextClassifier.TYPE_EMAIL,
        TextClassifier.TYPE_ADDRESS,
        TextClassifier.TYPE_DATE,
        TextClassifier.TYPE_DATE_TIME,
    )
}
