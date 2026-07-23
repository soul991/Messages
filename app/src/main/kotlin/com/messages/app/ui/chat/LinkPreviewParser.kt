package com.messages.app.ui.chat

/**
 * The pure, JVM-testable half of link previews (Phase 4 item 9): URL
 * extraction and OG/title scraping. [LinkPreview] owns the network + cache.
 */
object LinkPreviewParser {

    data class Preview(
        val url: String,
        val title: String,
        val siteName: String?,
        val imageUrl: String?,
    )

    /** First https URL in the body, if any (http is never previewed). */
    fun firstUrl(body: String): String? =
        Regex("""https://[^\s<>"')\]]+""").find(body)?.value?.trimEnd('.', ',', ';')

    /** Deterministic OG/title scrape — no HTML parser dependency needed. */
    fun parse(url: String, html: String): Preview? {
        fun meta(property: String): String? {
            val re = Regex(
                """<meta[^>]+(?:property|name)\s*=\s*["']$property["'][^>]*>""",
                RegexOption.IGNORE_CASE,
            )
            val tag = re.find(html)?.value ?: return null
            return Regex("""content\s*=\s*["']([^"']*)["']""", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        }

        val title = meta("og:title")
            ?: Regex("""<title[^>]*>([^<]{1,300})</title>""", RegexOption.IGNORE_CASE)
                .find(html)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }
            ?: return null
        val image = meta("og:image")?.takeIf { it.startsWith("https://") }
        return Preview(
            url = url,
            title = decodeEntities(title),
            siteName = meta("og:site_name")?.let(::decodeEntities),
            imageUrl = image,
        )
    }

    private fun decodeEntities(s: String): String = s
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&nbsp;", " ")
}

/** Pure tracking-number detection shared by [SmartText]; JVM-testable. */
object TrackingNumbers {

    /** India Post consignment format (RX123456789IN) — deterministic, no context needed. */
    fun ranges(text: String): List<IntRange> =
        Regex("""\b[A-Z]{2}\d{9}IN\b""").findAll(text).map { it.range }.toList()
}
