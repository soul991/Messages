package com.messages.app.update

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.messages.app.MessagesApp
import com.messages.app.R
import com.messages.app.notify.NotificationAvatarGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

/**
 * In-app APK download engine with Chrome-style notification progress.
 *
 * Downloads the APK from the GitHub release asset URL using a dedicated
 * OkHttpClient (separate from [com.messages.app.net.SafeHttp] which has a
 * max-bytes ceiling too small for APKs). Writes to `cacheDir/updates/`
 * which is registered in `file_paths.xml` for [FileProvider] access.
 *
 * Emits [DownloadState] through a coroutine [Flow] for the UI's progress
 * bar, and simultaneously updates a notification in the notification bar
 * (like Chrome's download notifications).
 */
object AppUpdateManager {

    /** States emitted during download. */
    sealed class DownloadState {
        object Idle : DownloadState()
        data class Downloading(val progress: Int, val bytesDownloaded: Long, val totalBytes: Long) : DownloadState()
        data class Complete(val apkFile: File) : DownloadState()
        data class Failed(val error: String) : DownloadState()
    }

    private const val NOTIFICATION_ID = -301
    private const val REQUEST_CODE_INSTALL = 9_311
    private const val REQUEST_CODE_CANCEL = 9_312

    /** Dedicated client for large downloads — generous timeouts, no SafeHttp restrictions. */
    private val downloadClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .callTimeout(10, TimeUnit.MINUTES)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    private fun updatesDir(context: Context): File =
        File(context.cacheDir, "updates").also { it.mkdirs() }

    /**
     * Download the APK from [url] and emit progress states.
     * Also posts a Chrome-style notification showing download progress.
     *
     * @param url Direct download URL for the APK asset.
     * @param version Version string for notification title (e.g. "1.2.2").
     * @param context Application context for notifications and file access.
     */
    fun download(
        context: Context,
        url: String,
        version: String,
    ): Flow<DownloadState> = flow {
        emit(DownloadState.Idle)

        val nm = NotificationManagerCompat.from(context)
        val hasNotifPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        val targetFile = File(updatesDir(context), "Messages-v$version.apk")
        // Clean up any partial download
        targetFile.delete()

        val appIcon = NotificationAvatarGenerator.getAppIconBitmap(context)
        val builder = NotificationCompat.Builder(context, MessagesApp.CH_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_notif_message)
            .setLargeIcon(appIcon)
            .setColor(0xFF10B981.toInt())
            .setContentTitle(context.getString(R.string.download_notif_title, version))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)

        try {
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/octet-stream")
                .build()

            val response = downloadClient.newCall(request).execute()

            if (!response.isSuccessful) {
                val errorMsg = "HTTP ${response.code}"
                response.close()
                // Show failure notification
                if (hasNotifPermission) {
                    builder
                        .setSmallIcon(R.drawable.ic_notif_message)
                        .setLargeIcon(appIcon)
                        .setColor(0xFFEF4444.toInt())
                        .setContentTitle(context.getString(R.string.download_notif_failed))
                        .setContentText(errorMsg)
                        .setOngoing(false)
                        .setAutoCancel(true)
                        .setProgress(0, 0, false)
                    nm.notify(NOTIFICATION_ID, builder.build())
                }
                emit(DownloadState.Failed(errorMsg))
                return@flow
            }

            val body = response.body ?: run {
                response.close()
                emit(DownloadState.Failed("Empty response"))
                return@flow
            }

            val totalBytes = body.contentLength()
            var downloadedBytes = 0L
            var lastNotifiedPercent = -1

            // Show initial notification
            if (hasNotifPermission) {
                builder.setProgress(100, 0, totalBytes <= 0)
                    .setContentText("0%")
                nm.notify(NOTIFICATION_ID, builder.build())
            }

            body.byteStream().use { input ->
                FileOutputStream(targetFile).use { output ->
                    val buffer = ByteArray(8 * 1024)
                    while (true) {
                        val bytesRead = input.read(buffer)
                        if (bytesRead < 0) break

                        output.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead

                        val percent = if (totalBytes > 0) {
                            ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            -1 // indeterminate
                        }

                        emit(DownloadState.Downloading(percent, downloadedBytes, totalBytes))

                        // Update notification every 2% to avoid excessive updates
                        if (hasNotifPermission && percent != lastNotifiedPercent &&
                            (percent < 0 || percent - lastNotifiedPercent >= 2 || percent == 100)
                        ) {
                            lastNotifiedPercent = percent
                            if (percent >= 0) {
                                builder.setProgress(100, percent, false)
                                    .setContentText("$percent%")
                            } else {
                                builder.setProgress(0, 0, true)
                                    .setContentText(formatBytes(downloadedBytes))
                            }
                            nm.notify(NOTIFICATION_ID, builder.build())
                        }

                        // Break immediately when all content-length bytes have been received
                        if (totalBytes > 0 && downloadedBytes >= totalBytes) break
                    }
                }
            }
            response.close()

            // Show "complete" notification with tap-to-install
            if (hasNotifPermission) {
                nm.cancel(NOTIFICATION_ID)
                val installIntent = createInstallPendingIntent(context, targetFile)
                val completeBuilder = NotificationCompat.Builder(context, MessagesApp.CH_DOWNLOAD)
                    .setSmallIcon(R.drawable.ic_notif_message)
                    .setLargeIcon(appIcon)
                    .setColor(0xFF10B981.toInt())
                    .setContentTitle(context.getString(R.string.download_notif_complete, version))
                    .setContentText(context.getString(R.string.download_notif_tap_install))
                    .setContentIntent(installIntent)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                nm.notify(NOTIFICATION_ID, completeBuilder.build())
            }

            emit(DownloadState.Complete(targetFile))

        } catch (e: Exception) {
            targetFile.delete()
            val errorMsg = e.message ?: "Unknown error"
            // Show failure notification
            if (hasNotifPermission) {
                builder
                    .setSmallIcon(R.drawable.ic_notif_message)
                    .setLargeIcon(appIcon)
                    .setColor(0xFFEF4444.toInt())
                    .setContentTitle(context.getString(R.string.download_notif_failed))
                    .setContentText(errorMsg)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .setProgress(0, 0, false)
                nm.notify(NOTIFICATION_ID, builder.build())
            }
            emit(DownloadState.Failed(errorMsg))
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Cancel the download notification (called when navigating away or cancelling).
     */
    fun cancelNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    /**
     * Install an APK file using the system package installer via FileProvider.
     */
    fun installApk(context: Context, apkFile: File) {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    private fun createInstallPendingIntent(context: Context, apkFile: File): PendingIntent {
        val authority = "${context.packageName}.fileprovider"
        val uri = FileProvider.getUriForFile(context, authority, apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE_INSTALL,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    // ---- Cache management --------------------------------------------------

    /** List all downloaded APK files in the updates cache. */
    fun listCachedApks(context: Context): List<File> =
        updatesDir(context).listFiles { f -> f.extension.equals("apk", ignoreCase = true) }
            ?.toList().orEmpty()

    /** Total size of cached APKs in bytes. */
    fun cachedSizeBytes(context: Context): Long =
        listCachedApks(context).sumOf { it.length() }

    /** Delete all cached APK downloads. */
    fun clearCache(context: Context): Int {
        val files = listCachedApks(context)
        files.forEach { it.delete() }
        return files.size
    }

    /** Format bytes to a human-readable string. */
    fun formatBytes(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "%.1f KB".format(bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024))
        else -> "%.2f GB".format(bytes / (1024.0 * 1024 * 1024))
    }
}
