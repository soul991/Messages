package com.messages.app.drive

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.messages.core.MessageRepository
import com.messages.core.backup.BackupCrypto
import com.messages.core.backup.BackupManager
import com.messages.core.backup.Checkpoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * §8.3 Google Drive backup orchestration.
 *
 * Key model: when the user sets the backup password we mint ONE random data
 * key. It is (a) wrapped by the password and stored in prefs — this wrap is
 * embedded in every uploaded envelope, so on a new device the password alone
 * restores; (b) encrypted under an Android Keystore key locally, so the
 * scheduled worker can encrypt automatic backups without ever storing the
 * password itself.
 *
 * Checkpoint model: snapshots contain messages up to the most recent 6:00 AM
 * device-local checkpoint (Checkpoints.lastCheckpoint) — deterministic
 * content no matter when WorkManager actually runs; `lastCheckpointCovered`
 * ensures exactly one snapshot per window. Keep the last 2 snapshots.
 */
object DriveBackup {

    private const val PREFS = "drive_backup"
    private const val KEY_FREQUENCY = "frequency" // DAILY|WEEKLY|MONTHLY|MANUAL
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_INCLUDE_MEDIA = "include_media"
    private const val KEY_SPAM_MODE = "spam_mode" // ON|OFF|CUSTOM
    private const val KEY_SPAM_CUSTOM_IDS = "spam_custom_ids"
    private const val KEY_WRAPPED_BY_PASSWORD = "wrapped_by_password" // JSON of BackupCrypto.WrappedKey
    private const val KEY_DATA_KEY_LOCAL = "data_key_local" // keystore-encrypted data key
    private const val KEY_LAST_COVERED = "last_checkpoint_covered"
    private const val KEY_LAST_BACKUP_AT = "last_backup_at"
    private const val KEY_LAST_BACKUP_SIZE = "last_backup_size"
    private const val KEY_LAST_BACKUP_COUNT = "last_backup_count"
    private const val KEY_LAST_ERROR = "last_error"

    private const val KEYSTORE_ALIAS = "drive_backup_data_key"
    private const val WORK_PERIODIC = "drive_backup_periodic"
    private const val WORK_MANUAL = "drive_backup_manual"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- Google account ----

    fun signInClient(context: Context): GoogleSignInClient =
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestScopes(Scope(DriveClient.SCOPE))
                .build(),
        )

    fun signedInAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)
            ?.takeIf { GoogleSignIn.hasPermissions(it, Scope(DriveClient.SCOPE)) }

    fun driveClient(context: Context): DriveClient? {
        val account = signedInAccount(context)?.account ?: return null
        return DriveClient(context, account)
    }

    // ---- Settings ----

    fun frequency(context: Context): Checkpoints.Frequency =
        runCatching {
            Checkpoints.Frequency.valueOf(prefs(context).getString(KEY_FREQUENCY, "MANUAL")!!)
        }.getOrDefault(Checkpoints.Frequency.MANUAL)

    fun setFrequency(context: Context, freq: Checkpoints.Frequency) {
        prefs(context).edit().putString(KEY_FREQUENCY, freq.name).apply()
        reschedule(context)
    }

    fun wifiOnly(context: Context): Boolean = prefs(context).getBoolean(KEY_WIFI_ONLY, true)

    fun setWifiOnly(context: Context, wifiOnly: Boolean) {
        prefs(context).edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply()
        reschedule(context)
    }

    fun includeMedia(context: Context): Boolean =
        prefs(context).getBoolean(KEY_INCLUDE_MEDIA, false)

    fun setIncludeMedia(context: Context, include: Boolean) {
        prefs(context).edit().putBoolean(KEY_INCLUDE_MEDIA, include).apply()
    }

    fun spamMode(context: Context): BackupManager.SpamMode =
        runCatching {
            BackupManager.SpamMode.valueOf(prefs(context).getString(KEY_SPAM_MODE, "ON")!!)
        }.getOrDefault(BackupManager.SpamMode.ON)

    fun setSpamMode(context: Context, mode: BackupManager.SpamMode) {
        prefs(context).edit().putString(KEY_SPAM_MODE, mode.name).apply()
    }

    fun customSpamIds(context: Context): Set<Long> =
        prefs(context).getStringSet(KEY_SPAM_CUSTOM_IDS, emptySet())!!
            .mapNotNull { it.toLongOrNull() }.toSet()

    fun setCustomSpamIds(context: Context, ids: Set<Long>) {
        prefs(context).edit()
            .putStringSet(KEY_SPAM_CUSTOM_IDS, ids.map { it.toString() }.toSet())
            .apply()
    }

    // ---- Encryption setup ----

    /** True once a backup password has been configured. */
    fun isEncryptionConfigured(context: Context): Boolean =
        prefs(context).contains(KEY_WRAPPED_BY_PASSWORD) &&
            prefs(context).contains(KEY_DATA_KEY_LOCAL)

    /**
     * Set (or change) the backup password. Mints a fresh data key — older
     * snapshots keep their old key and password.
     */
    fun setPassword(context: Context, password: CharArray) {
        val dataKey = BackupCrypto.newDataKey()
        val wrap = BackupCrypto.wrapWithPassword(dataKey, password)
        val wrapJson = kotlinx.serialization.json.Json.encodeToString(
            BackupCrypto.WrappedKey.serializer(), wrap,
        )
        prefs(context).edit()
            .putString(KEY_WRAPPED_BY_PASSWORD, wrapJson)
            .putString(KEY_DATA_KEY_LOCAL, keystoreEncrypt(dataKey))
            .remove(KEY_LAST_COVERED) // next run re-uploads under the new key
            .apply()
    }

    private fun storedWrap(context: Context): BackupCrypto.WrappedKey? =
        prefs(context).getString(KEY_WRAPPED_BY_PASSWORD, null)?.let {
            runCatching {
                kotlinx.serialization.json.Json.decodeFromString(
                    BackupCrypto.WrappedKey.serializer(), it,
                )
            }.getOrNull()
        }

    private fun localDataKey(context: Context): ByteArray? =
        prefs(context).getString(KEY_DATA_KEY_LOCAL, null)?.let {
            runCatching { keystoreDecrypt(it) }.getOrNull()
        }

    // ---- Backup ----

    data class Status(
        val lastBackupAt: Long,
        val sizeBytes: Long,
        val messageCount: Int,
        val lastError: String?,
    )

    fun status(context: Context): Status = prefs(context).let {
        Status(
            lastBackupAt = it.getLong(KEY_LAST_BACKUP_AT, 0L),
            sizeBytes = it.getLong(KEY_LAST_BACKUP_SIZE, 0L),
            messageCount = it.getInt(KEY_LAST_BACKUP_COUNT, 0),
            lastError = it.getString(KEY_LAST_ERROR, null),
        )
    }

    /**
     * Cut, encrypt and upload one snapshot. [manual] uses checkpoint = now;
     * scheduled runs use the frequency's last 6 AM checkpoint and skip when
     * that window is already covered.
     */
    suspend fun backupNow(context: Context, manual: Boolean): Result<Status> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = driveClient(context)
                    ?: error("Not signed in to Google")
                val wrap = storedWrap(context) ?: error("Backup password not set")
                val dataKey = localDataKey(context) ?: error("Backup key unavailable — set the password again")

                val freq = if (manual) Checkpoints.Frequency.MANUAL else frequency(context)
                val checkpointAt = Checkpoints.lastCheckpoint(System.currentTimeMillis(), freq)
                if (!manual && checkpointAt <= prefs(context).getLong(KEY_LAST_COVERED, 0L)) {
                    return@runCatching status(context) // window already covered
                }

                val payload = BackupManager.export(
                    context,
                    BackupManager.ExportOptions(
                        upTo = checkpointAt,
                        spamMode = spamMode(context),
                        customSpamIds = customSpamIds(context),
                        includeMedia = includeMedia(context),
                    ),
                )
                val count = MessageRepository.get(context).db.messages().allMessages()
                    .count { !it.trashed && it.sendStatus != "SCHEDULED" && it.timestamp <= checkpointAt }
                val blob = BackupCrypto.seal(
                    payloadJson = payload,
                    dataKey = dataKey,
                    wrappedKeys = listOf(wrap),
                    createdAt = System.currentTimeMillis(),
                    checkpointAt = checkpointAt,
                    deviceModel = android.os.Build.MODEL ?: "Android",
                    messageCount = count,
                )
                client.upload("messages-snapshot-$checkpointAt.mbk", blob)

                // Keep the last 2 snapshots (§8.3).
                client.list()
                    .filter { it.name.endsWith(".mbk") }
                    .drop(2)
                    .forEach { runCatching { client.delete(it.id) } }

                prefs(context).edit()
                    .putLong(KEY_LAST_COVERED, checkpointAt)
                    .putLong(KEY_LAST_BACKUP_AT, System.currentTimeMillis())
                    .putLong(KEY_LAST_BACKUP_SIZE, blob.size.toLong())
                    .putInt(KEY_LAST_BACKUP_COUNT, count)
                    .remove(KEY_LAST_ERROR)
                    .apply()
                status(context)
            }.onFailure { e ->
                prefs(context).edit()
                    .putString(KEY_LAST_ERROR, e.message ?: e.javaClass.simpleName)
                    .apply()
            }
        }

    // ---- Restore ----

    data class RemoteSnapshot(
        val fileId: String,
        val name: String,
        val sizeBytes: Long,
        val header: BackupCrypto.Header,
    )

    /** Newest available snapshot with its readable (plaintext) header. */
    suspend fun latestSnapshot(context: Context): Result<RemoteSnapshot?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = driveClient(context) ?: error("Not signed in to Google")
                val file = client.list().firstOrNull { it.name.endsWith(".mbk") }
                    ?: return@runCatching null
                // Header is at the front; a full download is fine at SMS sizes.
                val blob = client.download(file.id)
                RemoteSnapshot(file.id, file.name, blob.size.toLong(), BackupCrypto.readHeader(blob))
            }
        }

    /**
     * Download, decrypt with [password] and merge-import (§8.3: restore is
     * additive — never deletes or overwrites what's on the device).
     */
    suspend fun restore(
        context: Context,
        fileId: String,
        password: CharArray,
    ): Result<BackupManager.ImportStats> = withContext(Dispatchers.IO) {
        runCatching {
            val client = driveClient(context) ?: error("Not signed in to Google")
            val blob = client.download(fileId)
            val payload = BackupCrypto.openWithPassword(blob, password)
            BackupManager.import(context, payload).getOrThrow()
        }
    }

    // ---- Scheduling ----

    /** App-start safety net + on-change rescheduling. */
    fun reschedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (frequency(context) == Checkpoints.Frequency.MANUAL ||
            !isEncryptionConfigured(context) || signedInAccount(context) == null
        ) {
            wm.cancelUniqueWork(WORK_PERIODIC)
            return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly(context)) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .build()
        // Daily cadence regardless of frequency: the worker itself no-ops
        // until a new checkpoint window has passed.
        wm.enqueueUniquePeriodicWork(
            WORK_PERIODIC,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<DriveBackupWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build(),
        )
    }

    fun enqueueManualBackup(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_MANUAL,
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<DriveBackupWorker>()
                .setInputData(
                    androidx.work.Data.Builder().putBoolean("manual", true).build()
                )
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build(),
        )
    }

    // ---- Android Keystore wrap for the local data-key copy ----

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(KEYSTORE_ALIAS, null) as? SecretKey)?.let { return it }
        val kg = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        kg.init(
            KeyGenParameterSpec.Builder(
                KEYSTORE_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return kg.generateKey()
    }

    private fun keystoreEncrypt(plain: ByteArray): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, keystoreKey())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain)
        return java.util.Base64.getEncoder().encodeToString(iv) + ":" +
            java.util.Base64.getEncoder().encodeToString(ct)
    }

    private fun keystoreDecrypt(stored: String): ByteArray {
        val (ivB64, ctB64) = stored.split(":", limit = 2)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE, keystoreKey(),
            GCMParameterSpec(128, java.util.Base64.getDecoder().decode(ivB64)),
        )
        return cipher.doFinal(java.util.Base64.getDecoder().decode(ctB64))
    }
}

/** Runs scheduled + manual Drive backups (§8.3). */
class DriveBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val manual = inputData.getBoolean("manual", false)
        val result = DriveBackup.backupNow(applicationContext, manual = manual)
        return when {
            result.isSuccess -> Result.success()
            runAttemptCount < 3 -> Result.retry()
            else -> Result.failure()
        }
    }
}
