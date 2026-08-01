package com.messages.app.net

import okhttp3.CookieJar
import okhttp3.Dns
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayOutputStream
import java.net.InetAddress
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit

/**
 * The one networking layer allowed to fetch a URL that came out of a message.
 *
 * A link in an SMS is chosen by whoever sent it, so every hop is hostile input.
 * Two findings drove this design:
 *
 * **Issue #9 — SNI.** The previous implementation pinned DNS by building a URL
 * from the resolved *literal IP* and setting a `Host:` header. But `Host` is
 * sent inside the TLS session, after the handshake; SNI is sent during it, and
 * RFC 6066 forbids an IP literal there. So the server picked its certificate
 * with no SNI at all — which on any CDN or shared host means the wrong
 * certificate and a failed verification for perfectly valid sites. Worse, the
 * obvious "fix" for that symptom is to loosen hostname verification.
 *
 * The correct shape is a **DNS override**: connect by hostname, so SNI and
 * certificate verification both use the real hostname, but resolve through
 * [PublicOnlyDns] so the socket can only ever go to an address that passed
 * policy. That is what OkHttp's [Dns] hook is for; `HttpURLConnection` has no
 * equivalent, which is why this is not built on it.
 *
 * **Issue #8 — the image fetch.** Validating the page URL and then handing the
 * `og:image` *hostname* to Coil left a second, unvalidated resolution: the name
 * could resolve publicly for our check and privately for Coil's fetch (DNS
 * rebinding). Everything now goes through this client, and callers hand Coil
 * local bytes rather than a remote host.
 *
 * Also: HTTPS only, no cookies ever, no automatic redirects (each hop is
 * re-validated by hand), and hard timeouts including a whole-call ceiling.
 */
object SafeHttp {

    private const val CONNECT_TIMEOUT_S = 5L
    private const val READ_TIMEOUT_S = 5L

    /** Whole-call ceiling, so a slow-drip response cannot hold a coroutine open. */
    private const val CALL_TIMEOUT_S = 15L

    private const val MAX_REDIRECTS = 3

    /**
     * Resolution that refuses anything not on the public internet.
     *
     * If *any* address for a host fails policy the whole lookup fails: a name
     * that returns one public and one private address must not be reachable via
     * the private one, and we cannot control which address OkHttp tries.
     */
    internal object PublicOnlyDns : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            val resolved = Dns.SYSTEM.lookup(hostname)
            if (resolved.isEmpty()) throw UnknownHostException(hostname)
            if (resolved.any { !isPublicAddress(it) }) {
                // Deliberately the same failure as "no such host": the caller
                // learns nothing about the private topology behind the name.
                throw UnknownHostException(hostname)
            }
            return resolved
        }
    }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .dns(PublicOnlyDns)
            // Redirects are followed by hand so each hop is re-validated.
            .followRedirects(false)
            .followSslRedirects(false)
            .cookieJar(CookieJar.NO_COOKIES)
            .connectTimeout(CONNECT_TIMEOUT_S, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_S, TimeUnit.SECONDS)
            .build()
    }

    /**
     * Scheme/port/credential policy. Pure — no DNS happens here, because the
     * only resolution that matters is the one [PublicOnlyDns] performs at
     * connect time. Checking addresses here as well would just be a
     * time-of-check/time-of-use gap.
     */
    internal fun validateUrl(raw: String): HttpUrl? {
        val url = raw.toHttpUrlOrNull() ?: return null
        if (!url.isHttps) return null
        if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
        if (url.port != 443) return null
        return url
    }

    /**
     * Reject anything that is not a routable public unicast address.
     *
     * Beyond the obvious loopback/private/link-local set this also refuses
     * carrier-grade NAT and the reserved/benchmark ranges, which are reachable
     * on real networks and are exactly where an SSRF probe would aim.
     */
    internal fun isPublicAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress ||
            address.isLinkLocalAddress || address.isSiteLocalAddress ||
            address.isMulticastAddress
        ) return false
        val bytes = address.address
        if (bytes.size == 16) {
            // IPv6 unique-local (fc00::/7) has no isSiteLocalAddress equivalent.
            if ((bytes[0].toInt() and 0xfe) == 0xfc) return false
            return true
        }
        if (bytes.size != 4) return false
        val a = bytes[0].toInt() and 0xff
        val b = bytes[1].toInt() and 0xff
        return when {
            a == 0 -> false                                  // 0.0.0.0/8
            a == 100 && b in 64..127 -> false                // 100.64/10 CGNAT
            a == 192 && b == 0 -> false                      // 192.0.0/24 IETF
            a == 198 && (b == 18 || b == 19) -> false        // 198.18/15 benchmark
            a >= 240 -> false                                // 240/4 reserved + broadcast
            else -> true
        }
    }

    /** What a fetch produced: where it ended up, and the bytes. */
    data class Fetched(val url: HttpUrl, val contentType: String?, val bytes: ByteArray) {
        // Generated equals/hashCode on a ByteArray field compares by identity,
        // which is misleading. This type is a carrier, never a map key.
        override fun equals(other: Any?) = this === other
        override fun hashCode() = System.identityHashCode(this)
    }

    /**
     * GET [rawUrl], following at most [MAX_REDIRECTS] hops, re-validating every
     * one, and reading at most [maxBytes].
     *
     * [contentTypeOk] is checked before the body is read, so a response
     * announcing the wrong type costs nothing. Returns null on any failure —
     * previews are best-effort and a failure must never surface as an error.
     */
    fun get(
        rawUrl: String,
        accept: String,
        maxBytes: Int,
        contentTypeOk: (String?) -> Boolean,
    ): Fetched? {
        var current = rawUrl
        repeat(MAX_REDIRECTS) {
            val url = validateUrl(current) ?: return null
            val request = Request.Builder()
                .url(url)
                .header("Accept", accept)
                .header("User-Agent", "Mozilla/5.0 (Android) MessagesPreview/1.0")
                .build()
            val response = runCatching { client.newCall(request).execute() }.getOrNull()
                ?: return null
            response.use {
                if (response.isRedirect) {
                    val location = response.header("Location") ?: return null
                    // Resolved against the current URL, then re-validated at the
                    // top of the next iteration — including its DNS.
                    current = url.resolve(location)?.toString() ?: return null
                    return@repeat
                }
                if (response.code != 200) return null
                val contentType = response.header("Content-Type")
                if (!contentTypeOk(contentType)) return null
                val body = response.body ?: return null
                val bytes = runCatching { body.byteStream().readAtMost(maxBytes) }
                    .getOrNull() ?: return null
                return Fetched(url, contentType, bytes)
            }
        }
        return null
    }

    /**
     * Read at most [limit] bytes. The remote decides the length, so the ceiling
     * is enforced while reading rather than checked against a header we do not
     * control.
     */
    private fun java.io.InputStream.readAtMost(limit: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val chunk = ByteArray(8 * 1024)
        var total = 0
        while (total < limit) {
            val n = read(chunk, 0, minOf(chunk.size, limit - total))
            if (n < 0) break
            out.write(chunk, 0, n)
            total += n
        }
        return out.toByteArray()
    }
}
