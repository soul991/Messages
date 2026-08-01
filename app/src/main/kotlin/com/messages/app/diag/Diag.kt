package com.messages.app.diag

import com.messages.app.BuildConfig

/**
 * Issue #13: release logs are not private.
 *
 * A device log can be read by diagnostics collectors, OEM tooling, support
 * bundles, an attached debugger, or anything with log access on a rooted
 * device. The Drive paths were logging the signed-in account's email and
 * display name, the full granted scope list, request URLs, and up to 400 bytes
 * of the server's error body — and, because `DriveHttpException.message`
 * carried that body, it was also persisted into SharedPreferences as the
 * "last error" and shown in snackbars.
 *
 * The rule this object encodes: in release builds log *shape*, not *content*.
 * Whether a value was present, how many there were, what class an error was —
 * all of that is enough to diagnose, and none of it identifies a user. Full
 * detail stays available in debug builds where it is actually useful.
 */
object Diag {

    /** Verbose, potentially identifying diagnostics are debug-only. */
    val verbose: Boolean = BuildConfig.DEBUG

    /**
     * `https://host/path` — query string and fragment dropped. Drive URLs carry
     * file ids, `q=` search filters and `fields=` selectors in the query, which
     * is exactly the part that identifies a user's data.
     */
    fun redactUrl(url: String): String = runCatching {
        val parsed = java.net.URI(url)
        val scheme = parsed.scheme ?: return@runCatching "(unparseable url)"
        val host = parsed.host ?: return@runCatching "$scheme://(no host)"
        val path = parsed.path.orEmpty().ifEmpty { "/" }
        val query = if (parsed.rawQuery.isNullOrEmpty()) "" else "?(redacted)"
        "$scheme://$host$path$query"
    }.getOrDefault("(unparseable url)")

    /** "present"/"absent" — never the address itself. */
    fun presence(value: String?): String = if (value.isNullOrBlank()) "absent" else "present"

    /**
     * An error's type without its message. Exception messages routinely embed
     * server responses, tokens, file ids and account names.
     */
    fun errorType(t: Throwable?): String = t?.javaClass?.simpleName ?: "none"

    /**
     * Detail that is safe to *include* only in a debug build. Returns an empty
     * string in release so it can be concatenated unconditionally.
     */
    inline fun debugOnly(block: () -> String): String = if (verbose) block() else ""
}
