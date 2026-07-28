package com.messages.app.ui.chat

import android.content.Context
import android.util.LruCache
import java.net.HttpURLConnection
import java.net.InetAddress
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
 *
 * R-20 (SSRF): the URL in a preview is chosen by whoever sent the message, so
 * every hop is treated as hostile:
 *
 * - [requirePublicHttps] rejects non-HTTPS, embedded credentials, non-443
 *   ports, and any host that resolves to a loopback/private/link-local/
 *   multicast/ULA address — closing off routers, cloud metadata endpoints and
 *   other LAN services.
 * - EVERY redirect target is re-validated, not just the first URL.
 * - The connection is pinned to the address that passed validation, so a second
 *   DNS lookup cannot swap in a private address afterwards (DNS rebinding).
 * - og:image URLs are validated with the SAME policy before Coil ever sees
 *   them; an image on a private host is dropped rather than fetched.
 */
object LinkPreview {

    private const val MAX_BYTES = 256 * 1024
    private const val TIMEOUT_MS = 5_000
    private const val MAX_REDIRECTS = 3

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

    /**
     * R-20: public-internet HTTPS only. Returns the validated URL paired with
     * the address it resolved to, so the caller can pin the connection to that
     * exact address instead of re-resolving.
     */
    internal fun requirePublicHttps(raw: String): Pair<URL, InetAddress>? {
        val url = runCatching { URL(raw) }.getOrNull() ?: return null
        if (!url.protocol.equals("https", ignoreCase = true)) return null
        if (url.userInfo != null) return null
        if (url.port != -1 && url.port != 443) return null
        val addresses = runCatching { InetAddress.getAllByName(url.host) }.getOrNull()
        if (addresses.isNullOrEmpty()) return null
        // ALL resolved addresses must be public: a host that returns one public
        // and one private address must not be reachable via the private one.
        if (addresses.any { !isPublicAddress(it) }) return null
        return url to addresses.first()
    }

    private fun isPublicAddress(address: InetAddress): Boolean {
        val bytes = address.address
        // IPv6 unique-local (fc00::/7) has no isSiteLocalAddress equivalent.
        val ipv6Ula = bytes.size == 16 && (bytes[0].toInt() and 0xfe) == 0xfc
        return !address.isAnyLocalAddress &&
            !address.isLoopbackAddress &&
            !address.isLinkLocalAddress &&
            !address.isSiteLocalAddress &&
            !address.isMulticastAddress &&
            !ipv6Ula
    }

    /**
     * Validate an og:image URL under the same policy as the page fetch. Returns
     * null when the image must not be loaded — ChatScreen drops the thumbnail
     * rather than handing an unvalidated URL to Coil.
     */
    fun safeImageUrlOrNull(raw: String?): String? {
        val candidate = raw ?: return null
        return requirePublicHttps(candidate)?.first?.toString()
    }

    private fun fetchOnce(rawUrl: String): LinkPreviewParser.Preview? {
        var current = rawUrl
        // Follow at most 3 redirects, https→https only, by hand so a redirect to
        // plain http — or to a private address — can never happen silently.
        repeat(MAX_REDIRECTS) {
            val (url, pinned) = requirePublicHttps(current) ?: return null
            val conn = openPinned(url, pinned) ?: return null
            try {
                conn.connectTimeout = TIMEOUT_MS
                conn.readTimeout = TIMEOUT_MS
                conn.instanceFollowRedirects = false
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) MessagesPreview/1.0")
                conn.setRequestProperty("Accept", "text/html")
                val code = conn.responseCode
                if (code in 300..399) {
                    val next = conn.getHeaderField("Location") ?: return null
                    val resolved = runCatching { URL(url, next).toString() }.getOrNull()
                        ?: return null
                    // Re-validated at the top of the next iteration.
                    current = resolved
                    return@repeat
                }
                if (code != 200) return null
                if (conn.contentType?.contains("text/html") != true) return null
                val html = conn.inputStream.use { input ->
                    input.readNBytesCompat(MAX_BYTES).toString(StandardCharsets.UTF_8)
                }
                val preview = LinkPreviewParser.parse(url.toString(), html) ?: return null
                // Drop an image that points anywhere non-public (R-20).
                return preview.copy(imageUrl = safeImageUrlOrNull(preview.imageUrl))
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    /**
     * Connect to the exact [pinned] address that passed validation, carrying the
     * original Host header and verifying the certificate against the real
     * hostname. Without this, HttpURLConnection performs its own DNS lookup and
     * a rebinding attack can point that second lookup at a private address.
     */
    private fun openPinned(url: URL, pinned: InetAddress): HttpURLConnection? {
        val hostAddress = pinned.hostAddress ?: return null
        // Bracket IPv6 literals so they parse as a URL authority.
        val literal = if (hostAddress.contains(':')) "[$hostAddress]" else hostAddress
        val byAddress = runCatching {
            URL(url.protocol, literal, url.port, url.file)
        }.getOrNull() ?: return null
        val conn = runCatching { byAddress.openConnection() as? HttpURLConnection }
            .getOrNull() ?: return null
        conn.setRequestProperty("Host", url.host)
        (conn as? javax.net.ssl.HttpsURLConnection)?.let { https ->
            val default = javax.net.ssl.HttpsURLConnection.getDefaultHostnameVerifier()
            https.hostnameVerifier = javax.net.ssl.HostnameVerifier { _, session ->
                default.verify(url.host, session)
            }
        }
        return conn
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
