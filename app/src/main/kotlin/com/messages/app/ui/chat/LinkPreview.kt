package com.messages.app.ui.chat

import android.content.Context
import android.net.Uri
import android.util.LruCache
import com.messages.app.net.SafeHttp
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opt-in link previews (Phase 4 item 9). Guarantees, in order of importance:
 *
 * - OFF by default (`link_previews` pref) — the app promises "no network for
 *   message content"; previews are the one user-opted exception.
 * - Callers gate WHO gets previews (Inbox only, never filtered folders, never
 *   Dangerous/fraud-flagged messages) — enforced in ChatScreen, restated here.
 * - No cookies are ever sent or stored, no redirects across scheme downgrades,
 *   HTTPS only.
 * - Timeout-safe: 5s connect / 5s read with a whole-call ceiling, HTML capped
 *   at 256 KB and images at 512 KB, failures cached as misses so a dead link is
 *   fetched once per process, not per recomposition.
 *
 * R-20 / V2-09 (SSRF, DNS rebinding, TLS): every hop goes through [SafeHttp],
 * which connects **by hostname** — so SNI and certificate verification use the
 * real host — while resolving through a DNS override that only ever yields
 * public addresses. The earlier approach (connect to a literal IP, carry a
 * `Host:` header) sent no usable SNI and broke every shared-certificate host.
 *
 * V2-08 (the image): an `og:image` URL used to be validated here and then handed
 * to Coil, which resolved the hostname a *second* time — the gap a rebinding
 * attack lives in. Now the bytes are fetched through the same validated client
 * and written to the app cache, and [LinkPreviewParser.Preview.imageUrl] is
 * rewritten to that local `file://` URI.
 *
 * **Invariant:** after [fetch] returns, `imageUrl` is either a local file URI or
 * null. It is never a remote URL, so no image loader ever performs a network
 * fetch on message-derived content.
 */
object LinkPreview {

    private const val MAX_HTML_BYTES = 256 * 1024
    private const val MAX_IMAGE_BYTES = 512 * 1024

    private const val IMAGE_DIR = "link_previews"
    private const val IMAGE_MAX_AGE_MS = 24L * 60 * 60 * 1000

    /** url → Result-ish: Preview, or null = known miss. */
    private val cache = LruCache<String, Optional>(64)

    private class Optional(val value: LinkPreviewParser.Preview?)

    /** Cached images are swept once per process; nothing here is hot enough to warrant more. */
    private val pruned = AtomicBoolean(false)

    fun enabled(context: Context): Boolean =
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .getBoolean("link_previews", false)

    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("settings", Context.MODE_PRIVATE)
            .edit().putBoolean("link_previews", enabled).apply()
    }

    /** First https URL in the body, if any (http is never previewed). */
    fun firstUrl(body: String): String? = LinkPreviewParser.firstUrl(body)

    suspend fun fetch(context: Context, url: String): LinkPreviewParser.Preview? =
        withContext(Dispatchers.IO) {
            cache.get(url)?.let { return@withContext it.value }
            val appContext = context.applicationContext
            val result = runCatching { fetchOnce(appContext, url) }.getOrNull()
            cache.put(url, Optional(result))
            result
        }

    private fun fetchOnce(context: Context, rawUrl: String): LinkPreviewParser.Preview? {
        val page = SafeHttp.get(
            rawUrl = rawUrl,
            accept = "text/html",
            maxBytes = MAX_HTML_BYTES,
            contentTypeOk = { it?.contains("text/html", ignoreCase = true) == true },
        ) ?: return null

        // Parse against the URL we actually landed on, so relative OG values and
        // the displayed link agree with the final hop rather than the first.
        val html = String(page.bytes, StandardCharsets.UTF_8)
        val preview = LinkPreviewParser.parse(page.url.toString(), html) ?: return null

        return preview.copy(imageUrl = localImageUriOrNull(context, preview.imageUrl))
    }

    /**
     * Fetch [remoteUrl] through the validated client and return a `file://` URI
     * for the cached bytes, or null if it cannot be fetched safely.
     *
     * A preview without a thumbnail is a fine outcome; handing out a remote URL
     * is not, so every failure path here returns null rather than falling back.
     */
    private fun localImageUriOrNull(context: Context, remoteUrl: String?): String? {
        val url = remoteUrl ?: return null
        val dir = File(context.cacheDir, IMAGE_DIR)
        if (pruned.compareAndSet(false, true)) pruneStaleImages(dir)

        val cached = File(dir, cacheName(url))
        if (cached.isFile && cached.length() > 0) return Uri.fromFile(cached).toString()

        val image = SafeHttp.get(
            rawUrl = url,
            accept = "image/*",
            maxBytes = MAX_IMAGE_BYTES,
            contentTypeOk = { it?.startsWith("image/", ignoreCase = true) == true },
        ) ?: return null
        if (image.bytes.isEmpty()) return null

        if (!dir.isDirectory && !dir.mkdirs()) return null
        // Write-then-rename: a torn file would otherwise be served from cache
        // forever, since presence is what the hit check tests.
        val tmp = File(dir, cacheName(url) + ".tmp")
        val ok = runCatching {
            tmp.writeBytes(image.bytes)
            tmp.renameTo(cached)
        }.getOrDefault(false)
        if (!ok) {
            tmp.delete()
            return null
        }
        return Uri.fromFile(cached).toString()
    }

    /** Content-addressed by URL: no attacker-controlled bytes reach the filename. */
    private fun cacheName(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(url.toByteArray(StandardCharsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) } + ".img"
    }

    private fun pruneStaleImages(dir: File) {
        val cutoff = System.currentTimeMillis() - IMAGE_MAX_AGE_MS
        runCatching {
            dir.listFiles()?.forEach { file ->
                if (file.isFile && file.lastModified() < cutoff) file.delete()
            }
        }
    }
}
