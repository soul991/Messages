package com.messages.app.update

import android.content.Context
import com.messages.app.BuildConfig
import com.messages.app.net.SafeHttp
import org.json.JSONObject

/**
 * Check-and-notify update support. **Check only** — this package never
 * downloads, installs, or requests `REQUEST_INSTALL_PACKAGES`. The single
 * outbound call is an unauthenticated GET of the GitHub Releases API; when a
 * newer release exists the user is handed a URL and the browser takes over.
 *
 * **What is sent.** A plain GET to [RELEASES_LATEST_URL] with `Accept` and
 * `User-Agent` headers, and nothing else. No message content, no sender, no
 * contact, no device identifier, no token, no query string. The request body
 * is empty because [SafeHttp.get] issues a GET. This is asserted by
 * `UpdateCheckTest`, and it is why the endpoint is a constant rather than
 * anything caller-supplied.
 *
 * **No token.** Unauthenticated API only, so GitHub rate-limits by IP (60/h).
 * A 403 is therefore an expected outcome, not an error worth surfacing as a
 * crash — see [UpdateResult.RateLimited].
 *
 * Transport is [SafeHttp], shared with link previews: HTTPS only, port 443
 * only, public-IP-only DNS, no cookies, no automatic redirects, and hard
 * timeouts including a whole-call ceiling.
 */
object UpdateCheck {

    const val RELEASES_LATEST_URL =
        "https://api.github.com/repos/soul991/Messages/releases/latest"

    /** Where a user is sent to read about, and download, a newer release. */
    const val RELEASES_PAGE_URL = "https://github.com/soul991/Messages/releases/latest"

    /** GitHub's JSON is small; anything larger is a wrong or hostile response. */
    private const val MAX_BYTES = 256 * 1024

    private const val PREFS = "update_check"
    private const val K_AUTO = "auto_check"
    private const val K_NOTIFY = "notify_updates"
    private const val K_LAST_SEEN = "last_notified_tag"

    /** The outcome of one check. Every failure mode is a value, never a throw. */
    sealed class UpdateResult {
        /** Installed version is current (or ahead of) the latest release. */
        data class UpToDate(val current: String) : UpdateResult()

        /** A newer release exists. [version] is the cleaned tag, e.g. "1.1". */
        data class Available(val version: String, val pageUrl: String) : UpdateResult()

        /** No network, DNS failure, timeout, or the host was unreachable. */
        object Offline : UpdateResult()

        /** GitHub returned 403 — the unauthenticated hourly quota is spent. */
        object RateLimited : UpdateResult()

        /** A 200 that was not the JSON we expect, or had no usable tag. */
        object Malformed : UpdateResult()
    }

    // ---- Settings ----------------------------------------------------------

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Weekly background check. Default ON — can be turned off in Settings. */
    fun autoCheckEnabled(context: Context): Boolean = prefs(context).getBoolean(K_AUTO, true)

    fun setAutoCheckEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(K_AUTO, enabled).apply()
        UpdateCheckWorker.reschedule(context)
    }

    /** Whether a background check may post a notification. Default ON. */
    fun notifyEnabled(context: Context): Boolean = prefs(context).getBoolean(K_NOTIFY, true)

    fun setNotifyEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(K_NOTIFY, enabled).apply()
    }

    /**
     * Last tag the user was notified about, so a weekly worker announces any
     * given release exactly once instead of every week until they upgrade.
     */
    internal fun lastNotifiedTag(context: Context): String? =
        prefs(context).getString(K_LAST_SEEN, null)

    internal fun setLastNotifiedTag(context: Context, tag: String) {
        prefs(context).edit().putString(K_LAST_SEEN, tag).apply()
    }

    // ---- The check ---------------------------------------------------------

    /**
     * Perform one check. Blocking I/O — callers must be off the main thread
     * (the settings screen uses `Dispatchers.IO`, the worker is a
     * `CoroutineWorker`).
     */
    fun check(current: String = BuildConfig.VERSION_NAME): UpdateResult {
        val fetched = SafeHttp.get(
            rawUrl = RELEASES_LATEST_URL,
            accept = "application/vnd.github+json",
            maxBytes = MAX_BYTES,
            contentTypeOk = { it == null || it.contains("json", ignoreCase = true) },
        )
        // SafeHttp collapses every failure to null — offline, DNS, timeout,
        // a non-200 (403 included), or a body over the ceiling. Rate limiting
        // is distinguished below by a second, cheap signal rather than by
        // widening SafeHttp's contract for one caller.
            ?: return if (lastCallWasRateLimited()) UpdateResult.RateLimited
            else UpdateResult.Offline

        val body = String(fetched.bytes, Charsets.UTF_8)
        val tag = parseTag(body)
            ?: return UpdateResult.Malformed

        val downloadUrl = parseDownloadUrl(body)

        return if (isNewer(tag, current)) {
            UpdateResult.Available(tag, downloadUrl)
        } else {
            UpdateResult.UpToDate(current)
        }
    }

    /**
     * Pull direct browser download URL for an attached .apk asset from the release JSON.
     * Falls back to [RELEASES_PAGE_URL] if no .apk asset is present or parsing fails.
     */
    internal fun parseDownloadUrl(body: String): String {
        try {
            val json = JSONObject(body)
            val assets = json.optJSONArray("assets")
            if (assets != null) {
                for (i in 0 until assets.length()) {
                    val asset = assets.optJSONObject(i) ?: continue
                    val name = asset.optString("name", "")
                    if (name.endsWith(".apk", ignoreCase = true)) {
                        val downloadUrl = asset.optString("browser_download_url", "")
                        if (downloadUrl.isNotBlank()) {
                            return downloadUrl
                        }
                    }
                }
            }
            val htmlUrl = json.optString("html_url", "")
            if (htmlUrl.isNotBlank()) return htmlUrl
        } catch (_: Throwable) {
            // fall through
        }
        return RELEASES_PAGE_URL
    }

    /**
     * SafeHttp does not report status codes, and widening it for this one
     * caller would touch the link-preview path. A 403 and an outage are both
     * "no result" to the user; the copy differs only to be honest about why.
     * Left as a hook so a future SafeHttp that surfaces the code can fill it
     * in without changing this file's shape.
     *
     * ponytail: always false — 403 currently reads as Offline. Wire this up if
     * users report confusing "offline" copy while plainly online.
     */
    private fun lastCallWasRateLimited(): Boolean = false

    /**
     * Pull `tag_name` out of the release JSON and clean it.
     *
     * Returns null for anything unusable: not an object, no `tag_name`, or a
     * tag with no digits (`"latest"`, `""`). A tag that survives is safe to
     * show in the UI.
     */
    internal fun parseTag(body: String): String? {
        val raw = try {
            JSONObject(body).optString("tag_name", "")
        } catch (_: Throwable) {
            return null
        }
        val cleaned = raw.trim().removePrefix("v").removePrefix("V").trim()
        if (cleaned.isEmpty()) return null
        if (cleaned.none { it.isDigit() }) return null
        return cleaned
    }

    /**
     * True when [candidate] is a strictly higher version than [current].
     *
     * Dotted numeric compare, shorter side zero-padded so `1.1` beats `1.0.9`
     * and `1.0` equals `1.0.0`. A non-numeric segment (`1.0-rc1`) contributes
     * its leading digits and otherwise sorts low, so a pre-release never
     * outranks the release of the same number. Unparseable input returns
     * false: never nag on a tag we do not understand.
     */
    internal fun isNewer(candidate: String, current: String): Boolean {
        val a = segments(candidate) ?: return false
        val b = segments(current) ?: return false
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return false
    }

    private fun segments(version: String): List<Int>? {
        val parts = version.trim().removePrefix("v").removePrefix("V")
            .split('.')
            .map { part -> part.takeWhile { it.isDigit() }.toIntOrNull() ?: 0 }
        return parts.ifEmpty { null }
    }
}
