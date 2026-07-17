package com.messages.app.drive

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Google Drive REST client for the app-private `appDataFolder`
 * (§8.3). Uses the drive.appdata scope ONLY — the app cannot see the user's
 * real Drive files, and other apps cannot see our backups. Plain REST over
 * HttpURLConnection; the OAuth token comes from GMS for the signed-in
 * account (no client secret in the app — the Cloud Console Android OAuth
 * client is matched by package name + signing SHA-1, see
 * docs/DRIVE_BACKUP_SETUP.md).
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

    private fun token(): String =
        GoogleAuthUtil.getToken(context, account, "oauth2:$SCOPE")

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
    fun upload(name: String, bytes: ByteArray): String {
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
        )
        return JSONObject(response).getString("id")
    }

    fun download(fileId: String): ByteArray =
        requestBytes("GET", "https://www.googleapis.com/drive/v3/files/$fileId?alt=media")

    fun delete(fileId: String) {
        request("DELETE", "https://www.googleapis.com/drive/v3/files/$fileId")
    }

    private fun request(
        method: String,
        url: String,
        body: ByteArray? = null,
        contentType: String? = null,
    ): String = String(requestBytes(method, url, body, contentType), Charsets.UTF_8)

    private fun requestBytes(
        method: String,
        url: String,
        body: ByteArray? = null,
        contentType: String? = null,
    ): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.setRequestProperty("Authorization", "Bearer ${token()}")
            conn.connectTimeout = 30_000
            conn.readTimeout = 120_000
            if (body != null) {
                conn.doOutput = true
                if (contentType != null) conn.setRequestProperty("Content-Type", contentType)
                conn.outputStream.use { it.write(body) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.readBytes()?.toString(Charsets.UTF_8) ?: ""
                // An expired token must not poison the GMS cache.
                if (code == 401) {
                    runCatching { GoogleAuthUtil.clearToken(context, token()) }
                }
                throw DriveHttpException(code, err.take(400))
            }
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"
    }
}
