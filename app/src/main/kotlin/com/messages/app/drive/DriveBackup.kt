package com.messages.app.drive

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Log
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
 * Key model (WhatsApp-style, account-as-access-control): a random master key
 * lives in a key file in the same Drive appDataFolder as the snapshots —
 * whoever can sign in to the Google account can restore, no password needed.
 * Every snapshot gets a fresh random data key, wrapped under the master key
 * ("account-plain" in BackupCrypto's versioned wrappedKeys[]). The payload
 * stays AES-256-GCM end-to-end; only key custody changed. The master key is
 * also cached locally (Android-Keystore-encrypted) so scheduled backups skip
 * the extra Drive read when possible. Legacy password-wrapped snapshots
 * (detected via BackupCrypto.requiresPassword) still restore with their
 * password.
 *
 * Checkpoint model: snapshots contain messages up to the most recent 6:00 AM
 * device-local checkpoint (Checkpoints.lastCheckpoint) — deterministic
 * content no matter when WorkManager actually runs; `lastCheckpointCovered`
 * ensures exactly one snapshot per window. Keep the last 2 snapshots.
 */
object DriveBackup {

    private const val TAG = "DriveBackup"
    private const val PREFS = "drive_backup"
    private const val KEY_FREQUENCY = "frequency" // DAILY|WEEKLY|MONTHLY|MANUAL
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_INCLUDE_MEDIA = "include_media"
    private const val KEY_SPAM_MODE = "spam_mode" // ON|OFF|CUSTOM
    private const val KEY_SPAM_CUSTOM_IDS = "spam_custom_ids"
    private const val KEY_MASTER_KEY_LOCAL = "master_key_local" // keystore-encrypted Drive master key
    private const val KEY_LAST_COVERED = "last_checkpoint_covered"
    private const val KEY_LAST_BACKUP_AT = "last_backup_at"
    private const val KEY_LAST_BACKUP_SIZE = "last_backup_size"
    private const val KEY_LAST_BACKUP_COUNT = "last_backup_count"
    private const val KEY_LAST_ERROR = "last_error"

    private const val KEYSTORE_ALIAS = "drive_backup_data_key"
    private const val WORK_PERIODIC = "drive_backup_periodic"
    private const val WORK_MANUAL = "drive_backup_manual"

    /** Master-key file kept alongside snapshots in appDataFolder (never pruned
     *  — the prune filter only touches `.mbk` snapshot files). */
    private const val KEY_FILE_NAME = "messages-backup-key.bin"

    fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ---- Google account ----

    fun signInClient(context: Context): GoogleSignInClient =
        GoogleSignIn.getClient(
            context,
            // DEFAULT_SIGN_IN alone only guarantees a stable ID + basic
            // profile (name/photo) — email is null unless requested
            // explicitly, and without it GMS also can't resolve the
            // underlying system Account that DriveClient/GoogleAuthUtil
            // need, breaking backups even after a "successful" sign-in.
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestScopes(Scope(DriveClient.SCOPE))
                .build(),
        )

    fun signedInAccount(context: Context): GoogleSignInAccount? =
        GoogleSignIn.getLastSignedInAccount(context)
            ?.takeIf { GoogleSignIn.hasPermissions(it, Scope(DriveClient.SCOPE)) }

    /** account.email can be null on a silent re-auth even when fully signed
     *  in with the drive.appdata scope granted — account.account.name (the
     *  underlying system Account, same one used for GoogleAuthUtil.getToken)
     *  is the reliable fallback so the UI never gets stuck showing "not
     *  signed in" for a genuinely signed-in account. */
    fun signedInEmail(context: Context): String? =
        signedInAccount(context)?.let { it.email ?: it.account?.name }

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

    // ---- Master key (account-plain access model) ----

    private fun cachedMasterKey(context: Context): ByteArray? =
        prefs(context).getString(KEY_MASTER_KEY_LOCAL, null)?.let {
            runCatching { keystoreDecrypt(it) }.getOrNull()
        }?.takeIf { it.size == 32 }

    private fun cacheMasterKey(context: Context, key: ByteArray) {
        prefs(context).edit().putString(KEY_MASTER_KEY_LOCAL, keystoreEncrypt(key)).apply()
    }

    /**
     * The master key every backup must be wrapped under. The Drive key file
     * is authoritative (a future restore on another device will read it);
     * if none exists yet, re-upload the local cache or mint a fresh key.
     * Blocking — call on Dispatchers.IO.
     */
    private fun ensureMasterKey(context: Context, client: DriveClient): ByteArray {
        val remote = client.list().firstOrNull { it.name == KEY_FILE_NAME }
        if (remote != null) {
            val key = client.download(remote.id)
            require(key.size == 32) { "Corrupt backup key file in Google Drive" }
            cacheMasterKey(context, key)
            return key
        }
        val key = cachedMasterKey(context) ?: BackupCrypto.newMasterKey()
        client.upload(KEY_FILE_NAME, key)
        cacheMasterKey(context, key)
        return key
    }

    /** Master key for restore: Drive key file first, local cache as fallback. */
    private fun masterKeyForRestore(context: Context, client: DriveClient): ByteArray? {
        val remote = client.list().firstOrNull { it.name == KEY_FILE_NAME }
            ?: return cachedMasterKey(context)
        val key = client.download(remote.id)
        if (key.size != 32) return cachedMasterKey(context)
        cacheMasterKey(context, key)
        return key
    }

    // ---- Backup ----

    data class Status(
        val lastBackupAt: Long,
        val sizeBytes: Long,
        val messageCount: Int,
        val lastError: String?,
    )

    enum class BackupStage { PREPARING, ENCRYPTING, UPLOADING }

    /** Live progress for a manual backup-now run (§8.3 popup). [total] == 0
     *  means indeterminate — no meaningful denominator yet (e.g. mid-encrypt). */
    data class BackupProgress(val stage: BackupStage, val done: Long = 0, val total: Long = 0) {
        val fraction: Float? get() = if (total <= 0) null else (done.toFloat() / total).coerceIn(0f, 1f)
    }

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
    suspend fun backupNow(
        context: Context,
        manual: Boolean,
        onProgress: ((BackupProgress) -> Unit)? = null,
    ): Result<Status> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = driveClient(context)
                    ?: error("Not signed in to Google")

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
                    onMessageProgress = { done, total ->
                        onProgress?.invoke(BackupProgress(BackupStage.PREPARING, done.toLong(), total.toLong()))
                    },
                )
                onProgress?.invoke(BackupProgress(BackupStage.ENCRYPTING))
                val masterKey = ensureMasterKey(context, client)
                val dataKey = BackupCrypto.newDataKey()
                val count = MessageRepository.get(context).db.messages().allMessages()
                    .count { !it.trashed && it.sendStatus != "SCHEDULED" && it.timestamp <= checkpointAt }
                val blob = BackupCrypto.seal(
                    payloadJson = payload,
                    dataKey = dataKey,
                    wrappedKeys = listOf(BackupCrypto.wrapWithMasterKey(dataKey, masterKey)),
                    createdAt = System.currentTimeMillis(),
                    checkpointAt = checkpointAt,
                    deviceModel = android.os.Build.MODEL ?: "Android",
                    messageCount = count,
                )
                client.upload(
                    "messages-snapshot-$checkpointAt.mbk",
                    blob,
                    onProgress = { sent, total ->
                        onProgress?.invoke(BackupProgress(BackupStage.UPLOADING, sent, total))
                    },
                )

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
                Log.w(TAG, "backupNow failed", e)
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
    ) {
        /** Legacy password-wrapped snapshot → the restore UI must prompt. */
        val needsPassword: Boolean get() = BackupCrypto.requiresPassword(header)
    }

    /** Restore-outcome copy (§ restore idempotency UI states). Pure, JVM-tested. */
    fun restoreResultMessage(
        restored: Int,
        skipped: Int,
        lockedPending: Boolean = false,
        lockedRestored: Int = 0,
    ): String {
        val base = when {
            restored == 0 && skipped > 0 ->
                "Nothing to restore — all messages are already on this device"
            restored == 0 && (lockedPending || lockedRestored > 0) -> "Restore complete"
            restored == 0 -> "Backup contained no messages"
            restored == 1 -> "Restored 1 new message"
            else -> "Restored $restored new messages"
        }
        // Secret space: the opaque state someone with mere account access
        // sees — locked chats exist, but only the secret code opens them.
        return when {
            lockedPending ->
                "$base. Locked chats present — enter your secret code to unlock " +
                    "(press and hold the Messages title)."
            lockedRestored > 0 -> "$base. Locked chats restored to your locked space."
            else -> base
        }
    }

    /** Range-request size that comfortably covers the plaintext JSON header. */
    private const val HEADER_PROBE_BYTES = 8 * 1024

    /**
     * All available snapshots, newest first (§8.3 keeps the last 2 — the
     * chooser lets the user pick either). Headers are read via a small Range
     * request so a media-heavy snapshot isn't fully downloaded just to be
     * listed; unreadable/corrupt snapshots are skipped rather than failing
     * the whole listing.
     */
    suspend fun listSnapshots(context: Context): Result<List<RemoteSnapshot>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = driveClient(context) ?: error("Not signed in to Google")
                client.list()
                    .filter { it.name.endsWith(".mbk") }
                    .mapNotNull { file ->
                        runCatching {
                            val head = client.downloadPrefix(file.id, HEADER_PROBE_BYTES)
                            RemoteSnapshot(file.id, file.name, file.size, BackupCrypto.readHeader(head))
                        }.recoverCatching {
                            // Oversized header or a server that ignored Range —
                            // fall back to the full blob before giving up.
                            val blob = client.download(file.id)
                            RemoteSnapshot(file.id, file.name, file.size, BackupCrypto.readHeader(blob))
                        }.getOrNull()
                    }
            }.onFailure { e -> Log.w(TAG, "listSnapshots failed", e) }
        }

    /**
     * Download, decrypt and merge-import (§8.3: restore is additive — never
     * deletes or overwrites what's on the device). Account-plain snapshots
     * need no input; [password] is only required for legacy password-wrapped
     * snapshots (BackupCrypto.requiresPassword on the header).
     */
    enum class RestoreStage { DOWNLOADING, DECRYPTING, IMPORTING }

    /** Live progress for a restore run. [total] <= 0 means indeterminate. */
    data class RestoreProgress(val stage: RestoreStage, val done: Long = 0, val total: Long = 0) {
        val fraction: Float? get() = if (total <= 0) null else (done.toFloat() / total).coerceIn(0f, 1f)
    }

    suspend fun restore(
        context: Context,
        fileId: String,
        password: CharArray? = null,
        onProgress: ((RestoreProgress) -> Unit)? = null,
    ): Result<BackupManager.ImportStats> = withContext(Dispatchers.IO) {
        runCatching {
            val client = driveClient(context) ?: error("Not signed in to Google")
            onProgress?.invoke(RestoreProgress(RestoreStage.DOWNLOADING))
            val blob = client.download(fileId) { got, total ->
                onProgress?.invoke(RestoreProgress(RestoreStage.DOWNLOADING, got, total))
            }
            onProgress?.invoke(RestoreProgress(RestoreStage.DECRYPTING))
            val header = BackupCrypto.readHeader(blob)
            val payload = if (BackupCrypto.requiresPassword(header)) {
                requireNotNull(password) { "This backup needs its backup password" }
                BackupCrypto.openWithPassword(blob, password)
            } else {
                val masterKey = masterKeyForRestore(context, client)
                    ?: error("Backup key file is missing from Google Drive")
                BackupCrypto.openWithMasterKey(blob, masterKey)
            }
            onProgress?.invoke(RestoreProgress(RestoreStage.IMPORTING))
            BackupManager.import(context, payload).getOrThrow()
        }.onFailure { e -> Log.w(TAG, "restore failed", e) }
    }

    // ---- Scheduling ----

    /** App-start safety net + on-change rescheduling. */
    fun reschedule(context: Context) {
        val wm = WorkManager.getInstance(context)
        if (frequency(context) == Checkpoints.Frequency.MANUAL ||
            signedInAccount(context) == null
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
            runAttemptCount < 3 -> {
                Log.w(
                    "DriveBackup",
                    "worker attempt $runAttemptCount failed, retrying",
                    result.exceptionOrNull(),
                )
                Result.retry()
            }
            else -> {
                Log.w(
                    "DriveBackup",
                    "worker giving up after $runAttemptCount attempts",
                    result.exceptionOrNull(),
                )
                Result.failure()
            }
        }
    }
}
