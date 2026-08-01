package com.messages.app.drive

import android.accounts.Account
import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.GoogleAuthException
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.UserRecoverableAuthException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal Google Drive REST client for the app-private `appDataFolder`
 * (§8.3). Uses the drive.appdata scope ONLY — the app cannot see the user's
 * real Drive files, and other apps cannot see our backups. Plain REST over
 * HttpURLConnection; the OAuth token comes from GMS for the signed-in
 * account (no client secret in the app — the Cloud Console Android OAuth
 * client is matched by package name + signing SHA-1, see
 * docs/ops/DRIVE_BACKUP_SETUP.md).
 *
 * All methods are blocking — call from Dispatchers.IO.
 */
class DriveClient(private val context: Context, private val account: Account) {

    data class RemoteFile(
        val id: String,
        val name: String,
        val size: Long,
        val createdTime: String,
    )

    /**
     * V2-13: [detail] usually comes from the server's error body. That body is
     * echoed into the "last backup error" preference and into user-facing
     * snackbars via `message`, so in a release build the message is the status
     * code alone. The detail is kept as a field for debug logging.
     */
    class DriveHttpException(val code: Int, val detail: String) : Exception(
        if (com.messages.app.diag.Diag.verbose) "HTTP $code: $detail" else "HTTP $code",
    )

    /** Thrown when GMS needs the user to re-consent; [intent] must be launched to recover. */
    class RecoverableAuthException(val intent: Intent) :
        Exception("Google needs additional permission to continue")

    private fun token(): String =
        try {
            GoogleAuthUtil.getToken(context, account, "oauth2:$SCOPE")
        } catch (e: UserRecoverableAuthException) {
            val recoveryIntent = e.intent
            if (recoveryIntent != null) {
                Log.w(TAG, "token fetch needs user recovery", e)
                throw RecoverableAuthException(recoveryIntent)
            }
            Log.w(TAG, "token fetch needs user recovery but GMS gave no recovery intent", e)
            throw e
        } catch (e: GoogleAuthException) {
            Log.w(TAG, "token fetch failed (auth)", e)
            throw e
        } catch (e: IOException) {
            Log.w(TAG, "token fetch failed (network)", e)
            throw e
        }

    /**
     * List files in the app data folder, newest first.
     *
     * R-07: this used to request a single page of 25 without asking for
     * `nextPageToken`. Once a user accumulated more than 25 objects the master
     * key file could fall off the end of the list, the caller would conclude no
     * key existed, and a SECOND key would be minted — splitting snapshots across
     * two keys and breaking later restores. Every page is now walked, and
     * [query] lets callers ask for an exact filename instead of scanning.
     */
    fun list(query: String? = null): List<RemoteFile> {
        val out = mutableListOf<RemoteFile>()
        var pageToken: String? = null
        do {
            val params = buildList {
                add("spaces=appDataFolder")
                add("pageSize=$PAGE_SIZE")
                add("orderBy=createdTime desc")
                add("fields=nextPageToken,files(id,name,size,createdTime)")
                query?.let { add("q=" + URLEncoder.encode(it, "UTF-8")) }
                pageToken?.let { add("pageToken=" + URLEncoder.encode(it, "UTF-8")) }
            }.joinToString("&")
            val json = JSONObject(
                request("GET", "https://www.googleapis.com/drive/v3/files?$params")
            )
            val files = json.getJSONArray("files")
            for (i in 0 until files.length()) {
                val f = files.getJSONObject(i)
                out += RemoteFile(
                    id = f.getString("id"),
                    name = f.getString("name"),
                    size = f.optString("size", "0").toLongOrNull() ?: 0L,
                    createdTime = f.optString("createdTime"),
                )
            }
            pageToken = json.optString("nextPageToken").takeIf { it.isNotBlank() }
            // Defensive: a server that keeps handing back tokens must not spin
            // this loop forever.
        } while (pageToken != null && out.size < MAX_LISTED_FILES)
        return out
    }

    /** Exact-name lookup, correctly paginated and escaped (R-07). */
    fun findByName(name: String): List<RemoteFile> {
        val escaped = name.replace("\\", "\\\\").replace("'", "\\'")
        return list("name = '$escaped' and trashed = false")
    }

    /** Multipart upload into the app data folder; returns the new file id. */
    fun upload(
        name: String,
        bytes: ByteArray,
        onProgress: ((sent: Long, total: Long) -> Unit)? = null,
    ): String {
        val boundary = "messages-backup-${System.nanoTime()}"
        val metadata = JSONObject()
            .put("name", name)
            .put("parents", org.json.JSONArray().put("appDataFolder"))
            .toString()
        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\n".toByteArray())
            write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            write(metadata.toByteArray())
            write("\r\n--$boundary\r\n".toByteArray())
            write("Content-Type: application/octet-stream\r\n\r\n".toByteArray())
            write(bytes)
            write("\r\n--$boundary--\r\n".toByteArray())
        }.toByteArray()
        val response = request(
            "POST",
            "https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart&fields=id",
            body,
            "multipart/related; boundary=$boundary",
            onProgress,
        )
        return JSONObject(response).getString("id")
    }

    /**
     * Whole-file download, hard-capped (R-10). [maxBytes] defaults to the
     * largest envelope the crypto layer will accept, so a hostile or
     * misconfigured server cannot stream unbounded data into app heap.
     */
    fun download(
        fileId: String,
        maxBytes: Long = MAX_DOWNLOAD_BYTES,
        onProgress: ((got: Long, total: Long) -> Unit)? = null,
    ): ByteArray = requestBytes(
        "GET", "https://www.googleapis.com/drive/v3/files/$fileId?alt=media",
        onReadProgress = onProgress,
        maxBytes = maxBytes,
    )

    /**
     * First [maxBytes] of a file — enough to read a backup header without
     * pulling a whole snapshot down for the chooser.
     *
     * R-10: a server or proxy that ignores `Range` answers 200 with the ENTIRE
     * object, which silently turned a few-KB header probe into a full download.
     * The read is now capped at [maxBytes] regardless of status, so a non-206
     * response costs at most one buffer.
     */
    fun downloadPrefix(fileId: String, maxBytes: Int): ByteArray = requestBytes(
        "GET", "https://www.googleapis.com/drive/v3/files/$fileId?alt=media",
        rangeHeader = "bytes=0-${maxBytes - 1}",
        maxBytes = maxBytes.toLong(),
    )

    fun delete(fileId: String) {
        request("DELETE", "https://www.googleapis.com/drive/v3/files/$fileId")
    }

    private fun request(
        method: String,
        url: String,
        body: ByteArray? = null,
        contentType: String? = null,
        onProgress: ((sent: Long, total: Long) -> Unit)? = null,
    ): String = String(requestBytes(method, url, body, contentType, onProgress), Charsets.UTF_8)

    private fun requestBytes(
        method: String,
        url: String,
        body: ByteArray? = null,
        contentType: String? = null,
        onProgress: ((sent: Long, total: Long) -> Unit)? = null,
        rangeHeader: String? = null,
        onReadProgress: ((got: Long, total: Long) -> Unit)? = null,
        maxBytes: Long = MAX_DOWNLOAD_BYTES,
    ): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        val usedToken = token()
        try {
            conn.requestMethod = method
            conn.setRequestProperty("Authorization", "Bearer $usedToken")
            if (rangeHeader != null) conn.setRequestProperty("Range", rangeHeader)
            conn.connectTimeout = 30_000
            conn.readTimeout = 120_000
            if (body != null) {
                conn.doOutput = true
                if (contentType != null) conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { out ->
                    var sent = 0
                    while (sent < body.size) {
                        val chunkSize = minOf(UPLOAD_CHUNK_BYTES, body.size - sent)
                        out.write(body, sent, chunkSize)
                        sent += chunkSize
                        onProgress?.invoke(sent.toLong(), body.size.toLong())
                    }
                }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
                // V2-13: the URL's query carries file ids and search filters and
                // the body can carry account context — neither belongs in a
                // release log. Method, redacted URL and status code are enough
                // to diagnose; the body is debug-only.
                Log.w(
                    TAG,
                    "Drive HTTP $method ${com.messages.app.diag.Diag.redactUrl(url)} -> $code" +
                        com.messages.app.diag.Diag.debugOnly { ": ${err.take(400)}" },
                )
                // An expired token must not poison the GMS cache — clear the
                // exact token we just used, not a freshly re-fetched one.
                if (code == 401) {
                    runCatching { GoogleAuthUtil.clearToken(context, usedToken) }
                }
                throw DriveHttpException(code, err.take(400))
            }
            // Chunked read so restore can show live download progress.
            // contentLengthLong is -1 when the server doesn't say — callers
            // get total<=0 and should treat the progress as indeterminate.
            val total = conn.contentLengthLong
            // R-10: refuse before reading when the server announces more than
            // the caller is willing to hold.
            if (total > maxBytes) {
                throw DriveHttpException(code, "Response of $total bytes exceeds the $maxBytes cap")
            }
            val outBuf = ByteArrayOutputStream()
            conn.inputStream.use { ins ->
                val buf = ByteArray(UPLOAD_CHUNK_BYTES)
                var got = 0L
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    // Stop at the cap even when Content-Length lied or a proxy
                    // ignored our Range header and returned 200 + whole object.
                    val allowed = minOf(n.toLong(), maxBytes - got).toInt()
                    outBuf.write(buf, 0, allowed)
                    got += allowed
                    onReadProgress?.invoke(got, total)
                    if (got >= maxBytes) break
                }
            }
            return outBuf.toByteArray()
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
        private const val TAG = "DriveBackup"
        private const val UPLOAD_CHUNK_BYTES = 64 * 1024

        /** R-07: full page size, so key lookups need few round trips. */
        private const val PAGE_SIZE = 1000

        /** R-07: stop walking pages long before an unbounded server can OOM us. */
        private const val MAX_LISTED_FILES = 10_000

        /**
         * R-10: default read cap, matching the largest envelope BackupCrypto
         * will accept. Nothing legitimate in the appDataFolder is bigger.
         */
        const val MAX_DOWNLOAD_BYTES = 512L * 1024 * 1024
    }
}
