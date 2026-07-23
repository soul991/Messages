package com.messages.app.ui.chat

import android.content.Context
import android.util.LruCache
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opt-in link previews (Phase 4 item 9). Guarantees, in order of importance:
 *
 * - OFF by default (`link_previews` pref) — the app promises "no network for
 *   message content"; previews are the one user-opted exception.
 * - Callers gate WHO gets previews (Inbox only, never filtered folders, never
 *   Dangerous/fraud-flagged messages) — enforced in ChatScreen, restated here.
 * - No cookies are ever sent or stored (no CookieHandler is installed
 *   app-wide; the request carries no Cookie header), no redirects across
 *   scheme downgrades, HTTPS only.
 * - Timeout-safe: 5s connect / 5s read, response capped at 256 KB, failures
 *   cached as misses so a dead link is fetched once per process, not per
 *   recomposition.
 */
object LinkPreview {

    private const val MAX_BYTES = 256 * 1024
    private const val TIMEOUT_MS = 5_000

    /** url → Result-ish: Preview, or null = known miss. */
    private val cache = LruCache<String, Optional>(64)

    private class Optional(val value: LinkPreviewParser.Preview?)

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("link_previews", false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putBoolean("link_previews", enabled).apply()
    }

    /** First https URL in the body, if any (http is never previewed). */
    fun firstUrl(body: String): String? = LinkPreviewParser.firstUrl(body)

    suspend fun fetch(url: String): LinkPreviewParser.Preview? = withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext it.value }
        val result = runCatching { fetchOnce(url) }.getOrNull()
        cache.put(url, Optional(result))
        result
    }

    private fun fetchOnce(rawUrl: String): LinkPreviewParser.Preview? {
        var url = rawUrl
        // Follow at most 3 redirects, https→https only, by hand so a
        // redirect to plain http can never happen silently.
        repeat(3) {
            val conn = (URL(url).openConnection() as? HttpURLConnection) ?: return null
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) MessagesPreview/1.0")
                conn.setRequestProperty("Accept", "text/html")
                val code = conn.responseCode
                if (code in 300..399) {
                    val next = conn.getHeaderField("Location") ?: return null
                    val resolved = URL(URL(url), next).toString()
                    if (!resolved.startsWith("https://")) return null
                    url = resolved
                    return@repeat
                }
                if (code != 200) return null
                if (conn.contentType?.contains("text/html") != true) return null
                val html = conn.inputStream.use { input ->
                    input.readNBytesCompat(MAX_BYTES).toString(StandardCharsets.UTF_8)
                }
                return LinkPreviewParser.parse(url, html)
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
        val buf = java.io.ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        var total = 0
        while (total < limit) {
            val n = read(chunk, 0, minOf(chunk.size, limit - total))
            if (n < 0) break
            buf.write(chunk, 0, n)
            total += n
        }
        return buf.toByteArray()
    }
}
