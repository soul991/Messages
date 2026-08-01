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
import com.messages.app.R
import com.messages.core.MessageRepository
import com.messages.core.backup.BackupCrypto
import com.messages.core.backup.BackupManager
import com.messages.core.backup.Checkpoints
import com.messages.core.backup.MasterKeyVault
import com.messages.core.backup.RestoreBudget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
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
 * Key model. Every snapshot gets a fresh random data key, wrapped under a
 * long-lived master key ("account-plain" in BackupCrypto's versioned
 * wrappedKeys[]). The payload is AES-256-GCM end-to-end in both modes below;
 * what differs is only who holds the master key. It is also cached locally
 * (Android-Keystore-encrypted) so scheduled backups skip the extra Drive read.
 * Legacy password-wrapped snapshots (BackupCrypto.requiresPassword) still
 * restore with their password.
 *
 *  - [KeyCustody.ACCOUNT] (default) — the master key lives in a key file in the
 *    same Drive appDataFolder as the snapshots. Signing in to the Google
 *    account IS the access control (WhatsApp's pre-2021 model): restore needs
 *    nothing the user has to keep, which is why it stays the default.
 *  - [KeyCustody.USER_HELD] (V2-5 / V2-46, opt-in) — the master key is sealed
 *    into a [MasterKeyVault] under a recovery code or password Drive never
 *    sees, and **the plain key file is deleted**. Account compromise no longer
 *    yields the plaintext, at the cost of a secret the user must not lose.
 *
 * Switching modes never changes the master key, so no existing snapshot is
 * invalidated by turning protection on, off, or rotating the secret.
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
    private const val KEY_CUSTODY = "key_custody" // ACCOUNT|USER_HELD — a cache of Drive's state
    private const val KEY_LAST_COVERED = "last_checkpoint_covered"
    private const val KEY_LAST_BACKUP_AT = "last_backup_at"
    private const val KEY_LAST_BACKUP_SIZE = "last_backup_size"
    private const val KEY_LAST_BACKUP_COUNT = "last_backup_count"
    private const val KEY_LAST_ERROR = "last_error"

    private const val KEYSTORE_ALIAS = "drive_backup_data_key"
    private const val WORK_PERIODIC = "drive_backup_periodic"
    private const val WORK_MANUAL = "drive_backup_manual"

    /** Master-key file kept alongside snapshots in appDataFolder (never pruned
     *  — the prune filter only touches `.mbk` snapshot files). Present in
     *  [KeyCustody.ACCOUNT] mode only; turning user-held protection on deletes
     *  it, which is the entire point of V2-5. */
    private const val KEY_FILE_NAME = "messages-backup-key.bin"

    /** The sealed master key of [KeyCustody.USER_HELD] mode. Its presence on
     *  Drive — not the local preference — is what defines the mode. */
    private const val VAULT_FILE_NAME = "messages-backup-key-vault.json"

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
     * Who holds the master key. Cached from Drive by [keyAccess] so the
     * settings screen can render without a network round trip; Drive's own
     * state is what actually decides, and every write path re-reads it.
     */
    enum class KeyCustody { ACCOUNT, USER_HELD }

    fun keyCustody(context: Context): KeyCustody =
        runCatching {
            KeyCustody.valueOf(prefs(context).getString(KEY_CUSTODY, KeyCustody.ACCOUNT.name)!!)
        }.getOrDefault(KeyCustody.ACCOUNT)

    /**
     * The master key every backup must be wrapped under. The Drive key file
     * is authoritative (a future restore on another device will read it);
     * if none exists yet, re-upload the local cache or mint a fresh key.
     * Blocking — call on Dispatchers.IO.
     */
    private fun ensureMasterKey(context: Context, client: DriveClient): ByteArray {
        // R-07: exact, fully paginated name query. The old code scanned only the
        // first 25 listed files, so once a user had more objects than that the
        // key became invisible and a second one was minted — splitting snapshots
        // across two keys.
        val keyFiles = client.findByName(KEY_FILE_NAME)
        val vaults = client.findByName(VAULT_FILE_NAME)
        if (vaults.isNotEmpty()) {
            // V2-5: user-held custody. A plain key file alongside a vault means
            // an enable was interrupted after the upload and before the delete —
            // the protection is not actually in force, and silently backing up
            // anyway would leave the user believing it is.
            require(keyFiles.isEmpty()) {
                context.getString(R.string.drive_error_mixed_custody)
            }
            prefs(context).edit().putString(KEY_CUSTODY, KeyCustody.USER_HELD.name).apply()
            // An unattended backup cannot prompt, so the Keystore-sealed local
            // copy is the only key available. It is written when protection is
            // turned on and when a recovery code is accepted, so the only way to
            // be here without one is a fresh device that has not been unlocked.
            return cachedMasterKey(context)
                ?: error(context.getString(R.string.drive_error_recovery_code_needed))
        }
        prefs(context).edit().putString(KEY_CUSTODY, KeyCustody.ACCOUNT.name).apply()
        // Two key files means an earlier concurrent first-backup already split
        // custody. Refuse rather than guessing: picking one silently makes the
        // snapshots wrapped under the other permanently unreadable. Nothing is
        // deleted — recovery needs every key.
        // V2-36. These three reach the user verbatim — the caller shows them as
        // "Backup failed: <message>" — so they are copy, not log text.
        require(keyFiles.size <= 1) {
            context.getString(R.string.drive_error_multiple_keys, keyFiles.size)
        }
        val remote = keyFiles.firstOrNull()
        if (remote != null) {
            val key = client.download(remote.id, maxBytes = MAX_KEY_FILE_BYTES)
            require(key.size == 32) { context.getString(R.string.drive_error_corrupt_key) }
            cacheMasterKey(context, key)
            return key
        }
        val key = cachedMasterKey(context) ?: BackupCrypto.newMasterKey()
        client.upload(KEY_FILE_NAME, key)
        // Re-list after the upload: if a concurrent run uploaded its own key at
        // the same moment, fail loudly here rather than writing snapshots under
        // a key that a later lookup won't treat as authoritative.
        val after = client.findByName(KEY_FILE_NAME)
        require(after.size <= 1) {
            context.getString(R.string.drive_error_duplicate_key)
        }
        cacheMasterKey(context, key)
        return key
    }

    /**
     * Every master key that could plausibly open a snapshot, newest Drive copy
     * first, local cache last.
     *
     * V2-22: this used to return the *first* well-formed candidate and cache it
     * on the spot, despite the comment claiming the decrypt attempt would pick
     * the working duplicate — it never saw a second one. Drive genuinely can
     * hold two key files after an interrupted or concurrent first backup, and
     * if the newest is the stale one, restore failed with a key sitting right
     * there that would have worked. Caching the untested key made it worse: the
     * wrong key then persisted locally as the "known" one.
     *
     * Nothing is cached here. `openWithAnyMasterKey` caches only the key that
     * actually authenticated a snapshot.
     */
    private fun masterKeyCandidatesForRestore(
        context: Context,
        client: DriveClient,
        secret: CharArray? = null,
    ): List<ByteArray> {
        val out = mutableListOf<ByteArray>()
        fun offer(key: ByteArray?) {
            if (key == null || key.size != 32) return
            if (out.none { it.contentEquals(key) }) out += key
        }
        // V2-5: a secret the user just typed goes first — on a device restoring
        // from someone else's backup it is the only key there is, and where a
        // stale local cache exists it is the more trustworthy of the two.
        if (secret != null) {
            for (vault in client.findByName(VAULT_FILE_NAME)) {
                offer(
                    runCatching {
                        MasterKeyVault.open(
                            client.download(vault.id, maxBytes = MasterKeyVault.MAX_VAULT_BYTES.toLong()),
                            secret,
                        )
                    }.getOrNull(),
                )
            }
        }
        for (remote in client.findByName(KEY_FILE_NAME)) {
            offer(runCatching { client.download(remote.id, maxBytes = MAX_KEY_FILE_BYTES) }.getOrNull())
        }
        offer(cachedMasterKey(context))
        return out
    }

    /**
     * Try each candidate against the blob and return the plaintext from the one
     * that authenticates. AES-GCM makes this safe to brute-force over a handful
     * of keys: a wrong key fails the tag check, it cannot yield wrong-but-
     * plausible plaintext.
     *
     * Duplicate key files on Drive are deliberately left in place. Deleting one
     * after a successful restore would be guessing which snapshots it still
     * guards — the same reason `masterKey()` refuses to choose during backup.
     */
    private fun openWithAnyMasterKey(
        context: Context,
        blob: ByteArray,
        candidates: List<ByteArray>,
        maxExpanded: Int,
    ): String {
        var lastFailure: Throwable? = null
        candidates.forEachIndexed { index, key ->
            val payload = runCatching { BackupCrypto.openWithMasterKey(blob, key, maxExpanded) }
                .onFailure { lastFailure = it }
                .getOrNull()
            if (payload != null) {
                if (index > 0) Log.i(TAG, "restore opened with key candidate #${index + 1}")
                // Only a key that opened a real snapshot earns the cache slot.
                cacheMasterKey(context, key)
                return payload
            }
        }
        throw lastFailure
            ?: IllegalStateException(context.getString(R.string.drive_error_key_file_missing))
    }

    // ---- User-held key custody (V2-5 / V2-46) ----

    /** Whether a restore on THIS device can reach the master key right now. */
    enum class KeyAccess {
        /** A key is available with no user input. */
        READY,

        /** Protection is on and this device has no cached copy — prompt. */
        NEEDS_USER_SECRET,

        /** Nothing to restore with: no key file, no vault, no cache. */
        MISSING,
    }

    /** What [KeyAccess] applies, plus which prompt the vault expects. */
    data class KeyState(
        val access: KeyAccess,
        val custody: KeyCustody,
        /** [MasterKeyVault.METHOD_RECOVERY_CODE] or `METHOD_PASSWORD`; null in account mode. */
        val method: String?,
    )

    /**
     * Read Drive's actual custody state. Also refreshes the cached
     * [keyCustody] preference, so the settings screen is correct on a device
     * that never enabled protection itself.
     */
    suspend fun keyState(context: Context): Result<KeyState> = withContext(Dispatchers.IO) {
        runCatching {
            val client = driveClient(context) ?: error(context.getString(R.string.drive_error_not_signed_in))
            val vaults = client.findByName(VAULT_FILE_NAME)
            val plain = client.findByName(KEY_FILE_NAME)
            val custody = if (vaults.isNotEmpty()) KeyCustody.USER_HELD else KeyCustody.ACCOUNT
            prefs(context).edit().putString(KEY_CUSTODY, custody.name).apply()
            val method = vaults.firstOrNull()?.let { vault ->
                runCatching {
                    MasterKeyVault.methodOf(
                        client.download(vault.id, maxBytes = MasterKeyVault.MAX_VAULT_BYTES.toLong()),
                    )
                }.getOrNull()
            }
            val access = when {
                // A cached key opens everything without asking, in either mode.
                cachedMasterKey(context) != null -> KeyAccess.READY
                plain.isNotEmpty() -> KeyAccess.READY
                vaults.isNotEmpty() -> KeyAccess.NEEDS_USER_SECRET
                else -> KeyAccess.MISSING
            }
            KeyState(access, custody, method)
        }.onFailure { e -> Log.w(TAG, "keyState failed", e) }
    }

    /**
     * Publish [sealed] as the one live vault, proving it opens before anything
     * irreversible happens.
     *
     * V2-46 asks for recovery to be verified before it is relied on, and the
     * verification here deliberately re-downloads rather than trusting the
     * bytes in hand: what matters is that the object *Drive stored* opens with
     * the secret the user was just shown. A vault that fails is deleted again,
     * because a vault Drive has but nothing can open would strand every future
     * backup behind [ensureMasterKey]'s mixed-custody guard.
     *
     * The previous vault is removed only afterwards — during rotation there is
     * therefore always at least one openable vault on Drive.
     */
    private fun publishVault(
        context: Context,
        client: DriveClient,
        sealed: ByteArray,
        secret: CharArray,
        masterKey: ByteArray,
    ) {
        val superseded = client.findByName(VAULT_FILE_NAME).map { it.id }
        val newId = client.upload(VAULT_FILE_NAME, sealed)
        runCatching {
            val stored = client.download(newId, maxBytes = MasterKeyVault.MAX_VAULT_BYTES.toLong())
            val proof = MasterKeyVault.open(stored, secret)
            require(proof.contentEquals(masterKey)) {
                context.getString(R.string.drive_error_vault_verify)
            }
        }.onFailure { e ->
            runCatching { client.delete(newId) }
            throw e
        }
        superseded.forEach { runCatching { client.delete(it) } }
    }

    /**
     * Turn user-held protection on: seal the existing master key under
     * [secret], verify it opens, then delete the plain key file.
     *
     * The deletion is the finding. Sealing a copy while the plaintext stays in
     * the same folder would close nothing at all, so it happens here and only
     * after the vault has been proven readable.
     */
    suspend fun enableUserHeldKey(
        context: Context,
        secret: CharArray,
        method: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        backupMutex.withLock {
            runCatching {
                val client = driveClient(context)
                    ?: error(context.getString(R.string.drive_error_not_signed_in))
                require(client.findByName(VAULT_FILE_NAME).isEmpty()) {
                    context.getString(R.string.drive_error_already_protected)
                }
                val masterKey = ensureMasterKey(context, client)
                publishVault(
                    context, client,
                    MasterKeyVault.seal(masterKey, secret, method, System.currentTimeMillis()),
                    secret, masterKey,
                )
                client.findByName(KEY_FILE_NAME).forEach { client.delete(it.id) }
                cacheMasterKey(context, masterKey)
                prefs(context).edit().putString(KEY_CUSTODY, KeyCustody.USER_HELD.name).apply()
            }.onFailure { e -> Log.w(TAG, "enableUserHeldKey failed", e) }
        }
    }

    /**
     * Rotate the secret without touching the master key, so no snapshot is
     * invalidated. [current] may be null when this device holds a cached copy
     * of the master key — the same reasoning as an unattended backup.
     */
    suspend fun changeUserHeldSecret(
        context: Context,
        current: CharArray?,
        next: CharArray,
        nextMethod: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        backupMutex.withLock {
            runCatching {
                val client = driveClient(context)
                    ?: error(context.getString(R.string.drive_error_not_signed_in))
                val masterKey = resolveUserHeldKey(context, client, current)
                publishVault(
                    context, client,
                    MasterKeyVault.seal(masterKey, next, nextMethod, System.currentTimeMillis()),
                    next, masterKey,
                )
                cacheMasterKey(context, masterKey)
            }.onFailure { e -> Log.w(TAG, "changeUserHeldSecret failed", e) }
        }
    }

    /**
     * Turn protection off: put the master key back in the clear, verify Drive
     * has it, and only then drop the vault. The order matters — a vault deleted
     * before the plain key lands would leave the key in the local Keystore
     * cache alone, one factory reset from unrecoverable.
     */
    suspend fun disableUserHeldKey(
        context: Context,
        secret: CharArray?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        backupMutex.withLock {
            runCatching {
                val client = driveClient(context)
                    ?: error(context.getString(R.string.drive_error_not_signed_in))
                val masterKey = resolveUserHeldKey(context, client, secret)
                val existingPlain = client.findByName(KEY_FILE_NAME)
                require(existingPlain.size <= 1) {
                    context.getString(R.string.drive_error_multiple_keys, existingPlain.size)
                }
                if (existingPlain.isEmpty()) {
                    val id = client.upload(KEY_FILE_NAME, masterKey)
                    val stored = client.download(id, maxBytes = MAX_KEY_FILE_BYTES)
                    require(stored.contentEquals(masterKey)) {
                        context.getString(R.string.drive_error_corrupt_key)
                    }
                }
                client.findByName(VAULT_FILE_NAME).forEach { client.delete(it.id) }
                cacheMasterKey(context, masterKey)
                prefs(context).edit().putString(KEY_CUSTODY, KeyCustody.ACCOUNT.name).apply()
            }.onFailure { e -> Log.w(TAG, "disableUserHeldKey failed", e) }
        }
    }

    /**
     * Accept a recovery code / password on a device that has no cached key —
     * the new-phone path. On success the master key lands in the Keystore
     * cache and everything else (restore, scheduled backups) proceeds as if
     * this device had always had it.
     */
    suspend fun unlockUserHeldKey(context: Context, secret: CharArray): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = driveClient(context)
                    ?: error(context.getString(R.string.drive_error_not_signed_in))
                cacheMasterKey(context, resolveUserHeldKey(context, client, secret))
            }.onFailure { e -> Log.w(TAG, "unlockUserHeldKey failed", e) }
        }

    /**
     * The master key in user-held mode: the typed [secret] first, the local
     * cache as the fallback for the device that turned protection on.
     * Blocking — call on Dispatchers.IO.
     */
    private fun resolveUserHeldKey(
        context: Context,
        client: DriveClient,
        secret: CharArray?,
    ): ByteArray {
        val vaults = client.findByName(VAULT_FILE_NAME)
        if (vaults.isEmpty()) {
            // Not protected — the ordinary key path already answers this.
            return ensureMasterKey(context, client)
        }
        if (secret != null) {
            var malformed: Throwable? = null
            for (vault in vaults) {
                val key = runCatching {
                    MasterKeyVault.open(
                        client.download(vault.id, maxBytes = MasterKeyVault.MAX_VAULT_BYTES.toLong()),
                        secret,
                    )
                }.onFailure { malformed = it }.getOrNull()
                if (key != null) return key
            }
            // Every vault refused the secret. A malformed vault is a different
            // problem from a mistyped code and must not be reported as one.
            malformed?.let { if (it !is MasterKeyVault.WrongSecretException) throw it }
            throw MasterKeyVault.WrongSecretException()
        }
        return cachedMasterKey(context)
            ?: error(context.getString(R.string.drive_error_recovery_code_needed))
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
    /**
     * R-07: periodic and manual backups use DIFFERENT unique-work names, so
     * WorkManager will happily run both at once. On a first backup that raced,
     * each run could mint its own master key. One process-wide mutex serializes
     * the whole key-resolution + upload critical section, whichever worker (or
     * direct call) gets there first.
     */
    private val backupMutex = kotlinx.coroutines.sync.Mutex()

    suspend fun backupNow(
        context: Context,
        manual: Boolean,
        onProgress: ((BackupProgress) -> Unit)? = null,
    ): Result<Status> =
        withContext(Dispatchers.IO) {
            backupMutex.withLock {
                backupNowLocked(context, manual, onProgress)
            }
        }

    private suspend fun backupNowLocked(
        context: Context,
        manual: Boolean,
        onProgress: ((BackupProgress) -> Unit)? = null,
    ): Result<Status> =
        withContext(Dispatchers.IO) {
            runCatching {
                val client = driveClient(context)
                    ?: error(context.getString(R.string.drive_error_not_signed_in))

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

    /**
     * Which restore-outcome sentence applies (§ restore idempotency UI states).
     *
     * V2-36. This used to return the English sentence itself. The part worth
     * testing is the *decision* — a re-restore that adds nothing must not read
     * as an empty backup — and that decision survives translation, so it is
     * what stays here. The words live in the resource table and are put
     * together by [restoreResultMessage].
     */
    enum class RestoreOutcome { NOTHING_NEW, EMPTY_BACKUP, LOCKED_ONLY, RESTORED }

    /** What to say, if anything, about locked chats in the same breath. */
    enum class LockedNote { NONE, PENDING, RESTORED }

    /** Pure, JVM-tested. See [RestoreOutcome]. */
    fun restoreOutcome(
        restored: Int,
        skipped: Int,
        lockedPending: Boolean = false,
        lockedRestored: Int = 0,
    ): Pair<RestoreOutcome, LockedNote> {
        val outcome = when {
            restored == 0 && skipped > 0 -> RestoreOutcome.NOTHING_NEW
            restored == 0 && (lockedPending || lockedRestored > 0) -> RestoreOutcome.LOCKED_ONLY
            restored == 0 -> RestoreOutcome.EMPTY_BACKUP
            else -> RestoreOutcome.RESTORED
        }
        // Secret space: the opaque state someone with mere account access
        // sees — locked chats exist, but only the secret code opens them.
        val note = when {
            lockedPending -> LockedNote.PENDING
            lockedRestored > 0 -> LockedNote.RESTORED
            else -> LockedNote.NONE
        }
        return outcome to note
    }

    /** Renders [restoreOutcome] for display. */
    fun restoreResultMessage(
        context: Context,
        restored: Int,
        skipped: Int,
        lockedPending: Boolean = false,
        lockedRestored: Int = 0,
    ): String {
        val (outcome, note) = restoreOutcome(restored, skipped, lockedPending, lockedRestored)
        val base = when (outcome) {
            RestoreOutcome.NOTHING_NEW -> context.getString(R.string.drive_restore_nothing_new)
            RestoreOutcome.LOCKED_ONLY -> context.getString(R.string.drive_restore_complete)
            RestoreOutcome.EMPTY_BACKUP -> context.getString(R.string.drive_restore_empty)
            RestoreOutcome.RESTORED ->
                context.resources.getQuantityString(
                    R.plurals.drive_restored_count, restored, restored,
                )
        }
        return when (note) {
            LockedNote.PENDING -> context.getString(R.string.drive_restore_locked_pending, base)
            // The locked-chat count is deliberately not broken out — whoever
            // ran the restore may not be the person the locked space is for.
            LockedNote.RESTORED -> context.getString(R.string.drive_restore_locked_done, base)
            LockedNote.NONE -> base
        }
    }

    /** Range-request size that comfortably covers the plaintext JSON header. */
    private const val HEADER_PROBE_BYTES = 8 * 1024

    /**
     * R-10: one bounded retry for an unusually large header (many wrapped keys).
     * Still far below a full snapshot download, and matches BackupCrypto's 64 KiB
     * header ceiling plus the magic/length prefix.
     */
    private const val HEADER_PROBE_RETRY_BYTES = 80 * 1024

    /** A master key file is exactly 32 bytes; cap the read accordingly (R-10). */
    private const val MAX_KEY_FILE_BYTES = 4096L

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
                val client = driveClient(context) ?: error(context.getString(R.string.drive_error_not_signed_in))
                snapshotFiles(client).mapNotNull { readSnapshot(client, it) }
            }.onFailure { e -> Log.w(TAG, "listSnapshots failed", e) }
        }

    /** The `.mbk` objects in appDataFolder, newest first (Drive orders the listing). */
    private fun snapshotFiles(client: DriveClient): List<DriveClient.RemoteFile> =
        client.list().filter { it.name.endsWith(".mbk") }

    /**
     * Read one snapshot's header with a bounded Range request, or null if it
     * cannot be read at all.
     *
     * Shared by the restore chooser and the health check (V2-52) so both are
     * bounded the same way and neither can drift into downloading payloads.
     */
    private fun readSnapshot(client: DriveClient, file: DriveClient.RemoteFile): RemoteSnapshot? =
        runCatching {
            val head = client.downloadPrefix(file.id, HEADER_PROBE_BYTES)
            RemoteSnapshot(file.id, file.name, file.size, BackupCrypto.readHeader(head))
        }.recoverCatching {
            // R-10: retry ONCE with a larger prefix — never with a full
            // download. The old fallback pulled the entire snapshot
            // (potentially hundreds of MB of media) just to render a chooser
            // row, which a server ignoring Range could trigger for every
            // listed file.
            val head = client.downloadPrefix(file.id, HEADER_PROBE_RETRY_BYTES)
            RemoteSnapshot(file.id, file.name, file.size, BackupCrypto.readHeader(head))
        }.onFailure {
            // An unreadable header means "unavailable", not "fetch everything
            // and hope".
            Log.w(TAG, "skipping snapshot with unreadable header: ${file.name}")
        }.getOrNull()

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
        /** V2-5: recovery code or password when the master key is user-held. */
        recoverySecret: CharArray? = null,
        onProgress: ((RestoreProgress) -> Unit)? = null,
    ): Result<BackupManager.ImportStats> = withContext(Dispatchers.IO) {
        // R-07: hold the same lock as backupNow. A backup running mid-restore
        // could otherwise re-resolve the master key or upload a snapshot while
        // rows are being imported.
        backupMutex.withLock {
        runCatching {
            val client = driveClient(context) ?: error(context.getString(R.string.drive_error_not_signed_in))
            onProgress?.invoke(RestoreProgress(RestoreStage.DOWNLOADING))
            // V2-12: the download ceiling was a fixed 512 MB — four times the
            // heap of the phone doing the downloading. Bound it by what this
            // device could actually go on to restore, so an over-large snapshot
            // is abandoned during transfer rather than after it.
            val budget = RestoreBudget.forDevice(context)
            val blob = client.download(fileId, maxBytes = budget.maxExpandedBytes.toLong()) { got, total ->
                onProgress?.invoke(RestoreProgress(RestoreStage.DOWNLOADING, got, total))
            }
            onProgress?.invoke(RestoreProgress(RestoreStage.DECRYPTING))
            val header = BackupCrypto.readHeader(blob)
            val payload = if (BackupCrypto.requiresPassword(header)) {
                requireNotNull(password) { context.getString(R.string.drive_error_password_required) }
                BackupCrypto.openWithPassword(blob, password, budget.maxExpandedBytes)
            } else {
                val candidates = masterKeyCandidatesForRestore(context, client, recoverySecret)
                if (candidates.isEmpty()) {
                    // V2-5: in user-held mode "no candidates" means the secret
                    // was wrong or absent, not that the key is gone — say so.
                    if (client.findByName(VAULT_FILE_NAME).isNotEmpty()) {
                        throw MasterKeyVault.WrongSecretException()
                    }
                    error(context.getString(R.string.drive_error_key_file_missing))
                }
                openWithAnyMasterKey(context, blob, candidates, budget.maxExpandedBytes)
            }
            onProgress?.invoke(RestoreProgress(RestoreStage.IMPORTING))
            BackupManager.import(context, payload).getOrThrow()
        }.onFailure { e -> Log.w(TAG, "restore failed", e) }
        }
    }

    // ---- Backup health (V2-52) ----

    private const val KEY_HEALTH_AT = "health_checked_at"
    private const val KEY_HEALTH_CODE = "health_code"
    private const val KEY_HEALTH_SNAPSHOT_AT = "health_snapshot_at"
    private const val KEY_HEALTH_SNAPSHOT_COUNT = "health_snapshot_count"
    private const val KEY_HEALTH_KEYS_OK = "health_keys_ok"
    private const val KEY_HEALTH_KEYS_TRIED = "health_keys_tried"

    /** At most one automatic check a day; "Check now" ignores this. */
    private const val HEALTH_MIN_INTERVAL_MS = 24 * 60 * 60 * 1000L

    /**
     * What a health check concluded. Ordered roughly by how much the user has
     * to do about it, and every value is actionable — "something went wrong"
     * is not a status, it is the absence of one.
     */
    enum class HealthCode {
        /** A snapshot exists and a key this device can reach unwraps it. */
        OK,

        /** Restorable, but the newest snapshot is older than the schedule promises. */
        STALE,

        /** No Google account connected. */
        NOT_SIGNED_IN,

        /** The account is connected but Drive wants the user to re-consent. */
        NEEDS_REAUTH,

        /** Signed in, reachable, nothing backed up yet. */
        NO_SNAPSHOTS,

        /** Snapshots exist but not one of them has a readable header. */
        UNREADABLE_SNAPSHOT,

        /** User-held custody and this device holds no key — restore needs the code. */
        NEEDS_USER_SECRET,

        /** Legacy password-wrapped snapshot: only the user's password can prove it opens. */
        NEEDS_PASSWORD,

        /** No key file, no vault, no local cache. The snapshot is unopenable. */
        NO_KEY,

        /** Keys were found and every one of them was refused by the snapshot. */
        KEY_MISMATCH,

        /** A vault and a plain key file coexist — [ensureMasterKey] will refuse to back up. */
        MIXED_CUSTODY,

        /** Two key files: snapshots are split across keys and backups will refuse. */
        MULTIPLE_KEYS,

        /** Drive could not be reached. Says nothing about the backup itself. */
        UNREACHABLE,
    }

    /**
     * The result of one check. Metadata only — deliberately.
     *
     * Nothing here is message content and nothing here is key material: the
     * counts say how many keys were *tried* and how many were accepted, never
     * which or what. A health screen that printed a message to prove the backup
     * works would put plaintext on a screen the user opened to be reassured.
     */
    data class Health(
        val code: HealthCode,
        val checkedAt: Long,
        /** Header time of the newest readable snapshot; 0 if there is none. */
        val snapshotAt: Long = 0L,
        val snapshotCount: Int = 0,
        val keysTried: Int = 0,
        val keysAccepted: Int = 0,
    ) {
        /** True when this device could restore right now with no further input. */
        val restorable: Boolean get() = code == HealthCode.OK || code == HealthCode.STALE
    }

    /** The last stored result, or null if the check has never run here. */
    fun lastHealth(context: Context): Health? {
        val p = prefs(context)
        val at = p.getLong(KEY_HEALTH_AT, 0L)
        if (at <= 0L) return null
        val code = runCatching { HealthCode.valueOf(p.getString(KEY_HEALTH_CODE, "")!!) }
            .getOrNull() ?: return null
        return Health(
            code = code,
            checkedAt = at,
            snapshotAt = p.getLong(KEY_HEALTH_SNAPSHOT_AT, 0L),
            snapshotCount = p.getInt(KEY_HEALTH_SNAPSHOT_COUNT, 0),
            keysTried = p.getInt(KEY_HEALTH_KEYS_TRIED, 0),
            keysAccepted = p.getInt(KEY_HEALTH_KEYS_OK, 0),
        )
    }

    /**
     * Answer "if I needed this backup today, would it work?" without restoring.
     *
     * ## What it actually proves, and what it does not
     *
     * The check reads the newest snapshot's *header* over a bounded Range
     * request and tries to unwrap its data key with every master key this
     * device can reach. That unwrap is an AES-GCM tag check, so a key that
     * succeeds is the right key — it cannot succeed by accident. Combined with
     * a header that parsed (`BackupCrypto.readHeader` validates version, nonce
     * lengths, wrap methods and PBKDF2 parameters) that covers every failure
     * this feature exists for: a lost key file, a stale local cache, split
     * custody, an account that silently lost its grant, a schedule that quietly
     * stopped running.
     *
     * It does NOT prove the payload bytes are intact, because proving that
     * means downloading and decrypting the whole snapshot — hundreds of
     * megabytes, possibly metered, on a timer. R-10 already refused a full
     * download to render a *chooser row*; doing it for a background health
     * check would be worse. The honest position is that the check verifies
     * reachability and key custody, and the UI says "checked", not "restore
     * tested". The remaining risk is Drive silently corrupting bytes it
     * checksums itself.
     *
     * Read-only throughout: nothing is uploaded, deleted or rewritten, and the
     * unwrapped data key is zeroed rather than cached — a health check must
     * never be able to make the thing it is checking worse.
     */
    suspend fun verifyBackupHealth(context: Context): Health = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val health = runCatching { checkHealth(context, now) }.getOrElse { e ->
            Log.w(TAG, "backup health check failed", e)
            val code = if (e is DriveClient.RecoverableAuthException) {
                HealthCode.NEEDS_REAUTH
            } else {
                HealthCode.UNREACHABLE
            }
            Health(code, now)
        }
        prefs(context).edit()
            .putLong(KEY_HEALTH_AT, health.checkedAt)
            .putString(KEY_HEALTH_CODE, health.code.name)
            .putLong(KEY_HEALTH_SNAPSHOT_AT, health.snapshotAt)
            .putInt(KEY_HEALTH_SNAPSHOT_COUNT, health.snapshotCount)
            .putInt(KEY_HEALTH_KEYS_TRIED, health.keysTried)
            .putInt(KEY_HEALTH_KEYS_OK, health.keysAccepted)
            .apply()
        health
    }

    /**
     * Run the check if a day has passed since the last one. Called from the
     * backup worker, which already runs on a schedule with the user's network
     * constraints attached — so an unhealthy backup is noticed even during the
     * long stretches when the worker itself has nothing to do.
     */
    internal suspend fun verifyHealthIfDue(context: Context) {
        val last = prefs(context).getLong(KEY_HEALTH_AT, 0L)
        val now = System.currentTimeMillis()
        // `now < last` guards a clock that moved backwards: without it a device
        // whose time jumped forward once would never check again.
        if (last != 0L && now - last in 0 until HEALTH_MIN_INTERVAL_MS) return
        runCatching { verifyBackupHealth(context) }
    }

    private fun checkHealth(context: Context, now: Long): Health {
        val client = driveClient(context) ?: return Health(HealthCode.NOT_SIGNED_IN, now)

        // Custody first: both of these break the NEXT backup, which is exactly
        // the kind of thing a health check exists to surface before the day the
        // user needs it. They are also why a snapshot list can look healthy
        // while nothing new is being written.
        val vaults = client.findByName(VAULT_FILE_NAME)
        val plainKeys = client.findByName(KEY_FILE_NAME)
        if (vaults.isNotEmpty() && plainKeys.isNotEmpty()) {
            return Health(HealthCode.MIXED_CUSTODY, now)
        }
        if (vaults.isEmpty() && plainKeys.size > 1) {
            return Health(HealthCode.MULTIPLE_KEYS, now)
        }

        val files = snapshotFiles(client)
        if (files.isEmpty()) return Health(HealthCode.NO_SNAPSHOTS, now)
        val newest = files.firstNotNullOfOrNull { readSnapshot(client, it) }
            ?: return Health(HealthCode.UNREADABLE_SNAPSHOT, now, snapshotCount = files.size)

        val base = Health(
            code = HealthCode.OK,
            checkedAt = now,
            snapshotAt = newest.header.createdAt,
            snapshotCount = files.size,
        )

        if (newest.needsPassword) {
            // Legacy envelope. Nothing this device holds can open it, and the
            // check must not prompt — say what the user would need instead.
            return base.copy(code = HealthCode.NEEDS_PASSWORD)
        }

        // No secret is passed: an unattended check cannot prompt, so it tests
        // exactly what an unattended restore would have available.
        val candidates = masterKeyCandidatesForRestore(context, client)
        if (candidates.isEmpty()) {
            val code = if (vaults.isNotEmpty()) HealthCode.NEEDS_USER_SECRET else HealthCode.NO_KEY
            return base.copy(code = code)
        }

        // Every candidate is tried, not just the first: two key files or a
        // stale local cache is precisely the situation V2-22 found, where a
        // working key sat one position behind a broken one.
        val accepted = candidates.count { key ->
            runCatching { BackupCrypto.unwrapWithMasterKey(newest.header, key) }
                // Zero it immediately. The check needed to know it unwrapped,
                // not what it unwrapped to.
                .onSuccess { it.fill(0) }
                .isSuccess
        }
        val tested = base.copy(keysTried = candidates.size, keysAccepted = accepted)
        return when {
            accepted == 0 -> tested.copy(code = HealthCode.KEY_MISMATCH)
            isStale(context, newest.header.createdAt, now) -> tested.copy(code = HealthCode.STALE)
            else -> tested
        }
    }

    /**
     * Whether the newest snapshot is older than the chosen schedule promises.
     *
     * One full period plus a grace window, so a single missed run — a phone off
     * overnight, a metered connection — is not reported as a broken backup.
     * MANUAL has nothing to be late for.
     */
    private fun isStale(context: Context, snapshotAt: Long, now: Long): Boolean {
        if (snapshotAt <= 0L) return false
        val day = 24 * 60 * 60 * 1000L
        val limit = when (frequency(context)) {
            Checkpoints.Frequency.DAILY -> 2 * day
            Checkpoints.Frequency.WEEKLY -> 10 * day
            Checkpoints.Frequency.MONTHLY -> 40 * day
            Checkpoints.Frequency.MANUAL -> return false
        }
        return now - snapshotAt > limit
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
        // V2-52: piggyback the daily health check on the periodic run rather
        // than adding a second worker. The periodic run fires every 6 hours and
        // usually no-ops (its checkpoint window is already covered), so this is
        // the one place that is already awake, already has the user's network
        // constraints applied, and already exists on every device with backup
        // turned on. Its own failures are swallowed — a health check must never
        // turn a successful backup into a retry.
        if (!manual) DriveBackup.verifyHealthIfDue(applicationContext)
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
