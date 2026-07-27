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

    class DriveHttpException(val code: Int, message: String) : Exception("HTTP $code: $message")

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

    /** List backup files in the app data folder, newest first. */
    fun list(): List<RemoteFile> {
        val url = "https://www.googleapis.com/drive/v3/files" +
            "?spaces=appDataFolder&orderBy=createdTime desc&pageSize=25" +
            "&fields=files(id,name,size,createdTime)"
        val json = JSONObject(request("GET", url))
        val files = json.getJSONArray("files")
        return (0 until files.length()).map { i ->
            val f = files.getJSONObject(i)
            RemoteFile(
                id = f.getString("id"),
                name = f.getString("name"),
                size = f.optString("size", "0").toLongOrNull() ?: 0L,
                createdTime = f.optString("createdTime"),
            )
        }
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

    fun download(
        fileId: String,
        onProgress: ((got: Long, total: Long) -> Unit)? = null,
    ): ByteArray = requestBytes(
        "GET", "https://www.googleapis.com/drive/v3/files/$fileId?alt=media",
        onReadProgress = onProgress,
    )

    /** First [maxBytes] of a file (Range request) — enough to read a backup
     *  header without pulling the whole snapshot down for the chooser. */
    fun downloadPrefix(fileId: String, maxBytes: Int): ByteArray = requestBytes(
        "GET", "https://www.googleapis.com/drive/v3/files/$fileId?alt=media",
        rangeHeader = "bytes=0-${maxBytes - 1}",
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
                Log.w(TAG, "Drive HTTP $method $url -> $code: ${err.take(400)}")
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
            val outBuf = ByteArrayOutputStream()
            conn.inputStream.use { ins ->
                val buf = ByteArray(UPLOAD_CHUNK_BYTES)
                var got = 0L
                while (true) {
                    val n = ins.read(buf)
                    if (n < 0) break
                    outBuf.write(buf, 0, n)
                    got += n
                    onReadProgress?.invoke(got, total)
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
    }
}
