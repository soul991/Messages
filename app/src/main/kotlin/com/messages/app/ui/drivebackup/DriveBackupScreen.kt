package com.messages.app.ui.drivebackup

import android.app.Application
import android.content.Intent
import android.util.Log
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.messages.app.R
import com.messages.app.drive.BackupHealthCopy
import com.messages.app.drive.DriveBackup
import com.messages.app.drive.DriveClient
import com.messages.app.drive.DriveSignInError
import com.messages.app.ui.common.AppDateFormat
import com.messages.core.backup.BackupManager
import com.messages.core.backup.Checkpoints
import com.messages.core.backup.MasterKeyVault
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.NumberFormat

private const val TAG = "DriveBackup"

class DriveBackupViewModel(app: Application) : AndroidViewModel(app) {

    val accountEmail = MutableStateFlow(DriveBackup.signedInEmail(app))
    val frequency = MutableStateFlow(DriveBackup.frequency(app))
    val wifiOnly = MutableStateFlow(DriveBackup.wifiOnly(app))
    val includeMedia = MutableStateFlow(DriveBackup.includeMedia(app))
    val spamMode = MutableStateFlow(DriveBackup.spamMode(app))
    val customSpamCount = MutableStateFlow(DriveBackup.customSpamIds(app).size)
    val status = MutableStateFlow(DriveBackup.status(app))
    val busy = MutableStateFlow<String?>(null)
    /** Live progress for "Back up now" only — drives the WhatsApp-style popup. */
    val backupProgress = MutableStateFlow<DriveBackup.BackupProgress?>(null)
    val snackbar = MutableStateFlow<String?>(null)
    val restoreCandidate = MutableStateFlow<DriveBackup.RemoteSnapshot?>(null)
    /** ≥2 snapshots found → the chooser dialog lists them (last 2 are kept). */
    val snapshotChoices = MutableStateFlow<List<DriveBackup.RemoteSnapshot>?>(null)
    /** Live progress while a restore runs — drives the restore popup. */
    val restoreProgress = MutableStateFlow<DriveBackup.RestoreProgress?>(null)
    /** Non-null when GMS needs the user to re-consent; UI must launch this intent. */
    val recoverableAuthIntent = MutableStateFlow<Intent?>(null)
    /** V2-52: last stored health verdict, shown before any network call. */
    val health = MutableStateFlow(DriveBackup.lastHealth(app))
    val healthChecking = MutableStateFlow(false)
    private var pendingRetry: (() -> Unit)? = null

    /** Result of the sign-in picker, resolved directly from the intent — never
     *  re-derived via GoogleSignIn.getLastSignedInAccount(), which can race
     *  with the just-completed consent and silently look unsigned-in. */
    fun onSignInResult(account: GoogleSignInAccount?, error: Throwable?) {
        val granted = account != null && GoogleSignIn.hasPermissions(account, Scope(DriveClient.SCOPE))
        // account.email can be null on a silent re-auth (no fresh ID token is
        // returned, only the incremental scope grant) even though sign-in
        // fully succeeded — account.account.name (the underlying system
        // Account, same one DriveClient uses for GoogleAuthUtil.getToken) is
        // never null once granted, so it's the reliable display fallback.
        val displayEmail = account?.email ?: account?.account?.name
        // V2-13: the email, the system account name and the granted-scope list
        // are all identifying. Release logs get the shape of the result only;
        // the values stay in debug builds.
        Log.w(
            TAG,
            "onSignInResult: signedIn=${account != null} " +
                "email=${com.messages.app.diag.Diag.presence(account?.email)} " +
                "accountName=${com.messages.app.diag.Diag.presence(account?.account?.name)} " +
                "grantedScopeCount=${account?.grantedScopes?.size ?: 0} " +
                "hasDriveScope=$granted " +
                "error=${com.messages.app.diag.Diag.errorType(error)}" +
                com.messages.app.diag.Diag.debugOnly {
                    " | debug: email=${account?.email} name=${account?.account?.name} scopes=${account?.grantedScopes}"
                },
        )
        when {
            granted -> {
                accountEmail.value = displayEmail
                refresh()
            }
            error != null -> {
                Log.w(TAG, "sign-in failed", error)
                snackbar.value = DriveSignInError.describe(getApplication(), error)
            }
            else -> {
                Log.w(TAG, "signed in without granting the drive.appdata scope")
                snackbar.value = string(R.string.drive_scope_not_granted)
            }
        }
    }

    /** Called once the user completes a UserRecoverableAuthException recovery intent. */
    fun onAuthRecovered() {
        val retry = pendingRetry
        pendingRetry = null
        retry?.invoke()
    }

    fun clearRecoverableAuthIntent() { recoverableAuthIntent.value = null }

    /** Returns true (and stashes [retry]) if [e] means the UI must launch a recovery intent. */
    private fun tryRecoverable(e: Throwable, retry: () -> Unit): Boolean {
        if (e is DriveClient.RecoverableAuthException) {
            Log.w(TAG, "needs auth recovery, prompting user", e)
            pendingRetry = retry
            recoverableAuthIntent.value = e.intent
            return true
        }
        return false
    }

    fun refresh() {
        val app = getApplication<Application>()
        accountEmail.value = DriveBackup.signedInEmail(app)
        frequency.value = DriveBackup.frequency(app)
        wifiOnly.value = DriveBackup.wifiOnly(app)
        includeMedia.value = DriveBackup.includeMedia(app)
        spamMode.value = DriveBackup.spamMode(app)
        customSpamCount.value = DriveBackup.customSpamIds(app).size
        status.value = DriveBackup.status(app)
        health.value = DriveBackup.lastHealth(app)
        DriveBackup.reschedule(app)
    }

    /**
     * V2-52. Deliberately its own button and its own busy flag: it is the only
     * action on this screen that answers "would a restore work", and folding it
     * into [refresh] would put a Drive round trip behind every toggle.
     *
     * No `tryRecoverable` here. The check reports NEEDS_REAUTH as a verdict
     * instead — throwing a consent dialog at someone who pressed "check" would
     * make the check itself the thing that needs attention.
     */
    fun checkHealth(): Job = viewModelScope.launch {
        healthChecking.value = true
        health.value = DriveBackup.verifyBackupHealth(getApplication())
        healthChecking.value = false
    }

    fun setFrequency(f: Checkpoints.Frequency) {
        DriveBackup.setFrequency(getApplication(), f); refresh()
    }

    fun setWifiOnly(v: Boolean) {
        DriveBackup.setWifiOnly(getApplication(), v); refresh()
    }

    fun setIncludeMedia(v: Boolean) {
        DriveBackup.setIncludeMedia(getApplication(), v); refresh()
    }

    fun setSpamMode(m: BackupManager.SpamMode) {
        DriveBackup.setSpamMode(getApplication(), m); refresh()
    }

    fun backupNow(): Job = viewModelScope.launch {
        backupProgress.value = DriveBackup.BackupProgress(DriveBackup.BackupStage.PREPARING)
        val result = DriveBackup.backupNow(getApplication(), manual = true) { progress ->
            backupProgress.value = progress
        }
        backupProgress.value = null
        val failure = result.exceptionOrNull()
        if (failure != null && tryRecoverable(failure) { backupNow() }) return@launch
        snackbar.value = result.fold(
            onSuccess = { plural(R.plurals.drive_backup_complete, it.messageCount, it.messageCount) },
            onFailure = { string(R.string.drive_backup_failed, it.message.orEmpty()) },
        )
        refresh()
    }

    fun findSnapshots(): Job = viewModelScope.launch {
        busy.value = string(R.string.drive_looking_for_backups)
        val result = DriveBackup.listSnapshots(getApplication())
        busy.value = null
        val failure = result.exceptionOrNull()
        if (failure != null && tryRecoverable(failure) { findSnapshots() }) return@launch
        result.fold(
            onSuccess = { snaps ->
                when {
                    snaps.isEmpty() -> snackbar.value = string(R.string.drive_no_backups)
                    // One snapshot → straight to the confirm dialog; two (we
                    // keep the last 2) → let the user pick which to restore.
                    snaps.size == 1 -> restoreCandidate.value = snaps.first()
                    else -> snapshotChoices.value = snaps
                }
            },
            onFailure = { snackbar.value = string(R.string.drive_unreachable, it.message.orEmpty()) },
        )
    }

    fun restore(fileId: String, password: String?): Job = viewModelScope.launch {
        restoreProgress.value = DriveBackup.RestoreProgress(DriveBackup.RestoreStage.DOWNLOADING)
        val result = DriveBackup.restore(getApplication(), fileId, password?.toCharArray()) {
            restoreProgress.value = it
        }
        restoreProgress.value = null
        val failure = result.exceptionOrNull()
        if (failure != null && tryRecoverable(failure) { restore(fileId, password) }) return@launch
        restoreCandidate.value = null
        snackbar.value = result.fold(
            onSuccess = {
                DriveBackup.restoreResultMessage(
                    getApplication(), it.messagesRestored, it.messagesSkipped,
                    lockedPending = it.lockedPending, lockedRestored = it.lockedRestored,
                )
            },
            onFailure = {
                when (it) {
                    is com.messages.core.backup.BackupCrypto.WrongPasswordException ->
                        string(R.string.drive_wrong_password)
                    // V2-5: the master key is behind a vault this device has
                    // not opened. Say so and point at the unlock button rather
                    // than reporting a generic restore failure.
                    is MasterKeyVault.WrongSecretException ->
                        string(R.string.drive_restore_needs_recovery)
                    else -> string(R.string.drive_restore_failed, it.message.orEmpty())
                }
            },
        )
        refresh()
    }

    // ---- V2-5 / V2-46: user-held backup key ----

    /** Null until read from Drive; the section renders nothing until then. */
    val keyState = MutableStateFlow<DriveBackup.KeyState?>(null)
    val keyFlow = MutableStateFlow<KeyFlowState?>(null)

    /**
     * Deliberately not folded into [refresh]: reading custody costs two Drive
     * listings, and refresh runs on every toggle. This is called on screen
     * entry, after sign-in, and after each custody change instead.
     */
    fun loadKeyState(): Job = viewModelScope.launch {
        if (DriveBackup.signedInEmail(getApplication()) == null) {
            keyState.value = null
            return@launch
        }
        DriveBackup.keyState(getApplication())
            .onSuccess { keyState.value = it }
            .onFailure { Log.w(TAG, "could not read key custody", it) }
    }

    fun startProtect() {
        keyFlow.value = KeyFlowState(KeyFlowKind.ENABLE, KeyFlowStep.METHOD)
    }

    /**
     * Rotation and disabling both need the current secret — unless this device
     * already holds the key, in which case demanding it would be theatre: the
     * key is right there in the Keystore and every backup already uses it.
     */
    fun startChangeSecret() {
        val ready = keyState.value?.access == DriveBackup.KeyAccess.READY
        keyFlow.value = KeyFlowState(
            kind = KeyFlowKind.CHANGE,
            step = if (ready) KeyFlowStep.METHOD else KeyFlowStep.CURRENT,
            currentMethod = keyState.value?.method,
        )
    }

    fun startUnprotect() {
        val ready = keyState.value?.access == DriveBackup.KeyAccess.READY
        keyFlow.value = KeyFlowState(
            kind = KeyFlowKind.DISABLE,
            step = if (ready) KeyFlowStep.CONFIRM_DISABLE else KeyFlowStep.CURRENT,
            currentMethod = keyState.value?.method,
        )
    }

    fun startUnlock() {
        keyFlow.value = KeyFlowState(
            kind = KeyFlowKind.UNLOCK,
            step = KeyFlowStep.CURRENT,
            currentMethod = keyState.value?.method,
        )
    }

    fun updateKeyFlow(next: KeyFlowState) { keyFlow.value = next }

    fun cancelKeyFlow() { keyFlow.value = null }

    /**
     * One step forward. Steps that only gather input advance locally; the ones
     * that commit hand off to [runKeyOperation], which is the only place any of
     * this touches Drive.
     */
    fun submitKeyFlow() {
        val flow = keyFlow.value ?: return
        when (flow.step) {
            KeyFlowStep.CURRENT -> when (flow.kind) {
                // UNLOCK ends here — the typed secret IS the whole operation.
                KeyFlowKind.UNLOCK -> runKeyOperation(flow) {
                    DriveBackup.unlockUserHeldKey(getApplication(), secretChars(flow.current, flow.currentMethod))
                        .map { string(R.string.drive_key_unlocked_toast) }
                }
                KeyFlowKind.DISABLE -> keyFlow.value = flow.copy(step = KeyFlowStep.CONFIRM_DISABLE)
                else -> keyFlow.value = flow.copy(step = KeyFlowStep.METHOD)
            }

            KeyFlowStep.METHOD -> keyFlow.value = when (flow.newMethod) {
                MasterKeyVault.METHOD_RECOVERY_CODE -> flow.copy(
                    step = KeyFlowStep.CODE,
                    generated = MasterKeyVault.formatForDisplay(MasterKeyVault.newRecoveryCode()),
                )
                MasterKeyVault.METHOD_PASSWORD -> flow.copy(step = KeyFlowStep.PASSWORD)
                else -> return
            }

            KeyFlowStep.CODE -> commitNewSecret(flow, flow.generated.orEmpty())

            KeyFlowStep.PASSWORD -> {
                if (flow.password != flow.passwordRepeat) {
                    keyFlow.value = flow.copy(error = string(R.string.drive_recovery_password_mismatch))
                    return
                }
                commitNewSecret(flow, flow.password)
            }

            KeyFlowStep.CONFIRM_DISABLE -> runKeyOperation(flow) {
                DriveBackup.disableUserHeldKey(
                    getApplication(),
                    flow.current.takeIf { it.isNotBlank() }
                        ?.let { secretChars(it, flow.currentMethod) },
                ).map { string(R.string.drive_key_unprotected_toast) }
            }
        }
    }

    private fun commitNewSecret(flow: KeyFlowState, secret: String) {
        val method = flow.newMethod ?: return
        val next = secretChars(secret, method)
        runKeyOperation(flow) {
            if (flow.kind == KeyFlowKind.ENABLE) {
                DriveBackup.enableUserHeldKey(getApplication(), next, method)
                    .map { string(R.string.drive_key_protected_toast) }
            } else {
                DriveBackup.changeUserHeldSecret(
                    getApplication(),
                    flow.current.takeIf { it.isNotBlank() }
                        ?.let { secretChars(it, flow.currentMethod) },
                    next, method,
                ).map { string(R.string.drive_key_changed_toast) }
            }
        }
    }

    /**
     * Recovery codes are normalized before they are used, so the same code
     * opens the vault whether it was typed with dashes, in lower case, or with
     * an O where a zero belongs. Passwords are taken exactly as entered —
     * "correcting" a password would silently change it.
     */
    private fun secretChars(raw: String, method: String?): CharArray =
        if (method == MasterKeyVault.METHOD_PASSWORD) {
            raw.toCharArray()
        } else {
            MasterKeyVault.normalizeRecoveryCode(raw.toCharArray())
        }

    private fun runKeyOperation(flow: KeyFlowState, op: suspend () -> Result<String>): Job =
        viewModelScope.launch {
            keyFlow.value = flow.copy(busy = true, error = null)
            val result = op()
            result.fold(
                onSuccess = {
                    keyFlow.value = null
                    snackbar.value = it
                },
                onFailure = { e ->
                    // A wrong code is the user's to fix and belongs in the
                    // dialog; anything else closes it and reports plainly,
                    // because retyping will not help.
                    if (e is MasterKeyVault.WrongSecretException) {
                        keyFlow.value = flow.copy(
                            busy = false,
                            error = string(R.string.drive_wrong_recovery_code),
                        )
                    } else {
                        Log.w(TAG, "key custody operation failed", e)
                        keyFlow.value = null
                        snackbar.value = string(R.string.drive_key_failed, e.message.orEmpty())
                    }
                },
            )
            loadKeyState()
            refresh()
        }

    fun clearSnackbar() { snackbar.value = null }

    /**
     * V2-36. Every line this ViewModel puts in a snackbar is read by a user, so
     * it comes out of the resource table. A ViewModel has no composition to
     * call `stringResource` from — the Application it already holds resolves
     * them just as well.
     */
    private fun string(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    private fun plural(@PluralsRes id: Int, count: Int, vararg args: Any): String =
        getApplication<Application>().resources.getQuantityString(id, count, *args)
}

// V2-45: locale and zone read at render time, not at class-init.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveBackupScreen(
    onBack: () -> Unit,
    onPickSpamMessages: () -> Unit,
    vm: DriveBackupViewModel = viewModel(),
) {
    val email by vm.accountEmail.collectAsStateWithLifecycle()
    val frequency by vm.frequency.collectAsStateWithLifecycle()
    val wifiOnly by vm.wifiOnly.collectAsStateWithLifecycle()
    val includeMedia by vm.includeMedia.collectAsStateWithLifecycle()
    val spamMode by vm.spamMode.collectAsStateWithLifecycle()
    val customSpamCount by vm.customSpamCount.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val backupProgress by vm.backupProgress.collectAsStateWithLifecycle()
    val snackbar by vm.snackbar.collectAsStateWithLifecycle()
    val restoreCandidate by vm.restoreCandidate.collectAsStateWithLifecycle()
    val snapshotChoices by vm.snapshotChoices.collectAsStateWithLifecycle()
    val restoreProgress by vm.restoreProgress.collectAsStateWithLifecycle()
    val recoverableAuthIntent by vm.recoverableAuthIntent.collectAsStateWithLifecycle()
    val keyState by vm.keyState.collectAsStateWithLifecycle()
    val keyFlow by vm.keyFlow.collectAsStateWithLifecycle()
    val health by vm.health.collectAsStateWithLifecycle()
    val healthChecking by vm.healthChecking.collectAsStateWithLifecycle()

    // Custody lives on Drive, so it is read when the screen opens and again
    // whenever the signed-in account changes.
    androidx.compose.runtime.LaunchedEffect(email) { vm.loadKeyState() }

    var restorePassword by remember { mutableStateOf("") }
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    androidx.compose.runtime.LaunchedEffect(snackbar) {
        snackbar?.let { snackbarHostState.showSnackbar(it); vm.clearSnackbar() }
    }

    // Guards against a single tap producing many overlapping sign-in intent
    // launches (observed live: a tap can be delivered as a burst of duplicate
    // touch events, each launch racing GMS's own single-flight sign-in cache
    // — ApiException 12502 SIGN_IN_CURRENTLY_IN_PROGRESS — and racing each
    // other's result callbacks, which stomp the account state out of order).
    var signingIn by remember { mutableStateOf(false) }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        signingIn = false
        Log.w(TAG, "signInLauncher result: resultCode=${result.resultCode} hasData=${result.data != null}")
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            vm.onSignInResult(task.getResult(ApiException::class.java), null)
        } catch (e: ApiException) {
            vm.onSignInResult(null, e)
        } catch (e: Exception) {
            Log.w(TAG, "signInLauncher callback threw a non-ApiException", e)
            vm.onSignInResult(null, e)
        }
    }

    // A very common miss: GoogleAuthUtil.getToken can require a one-time user
    // consent screen (UserRecoverableAuthException) even after sign-in
    // succeeds — this launches it and retries whatever Drive call needed it.
    val recoverAuthLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { vm.onAuthRecovered() }

    androidx.compose.runtime.LaunchedEffect(recoverableAuthIntent) {
        recoverableAuthIntent?.let {
            recoverAuthLauncher.launch(it)
            vm.clearRecoverableAuthIntent()
        }
    }
    val context = androidx.compose.ui.platform.LocalContext.current

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drive_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                stringResource(R.string.drive_intro),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            busy?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
            }

            // 1. Account
            SectionLabel(stringResource(R.string.drive_section_account))
            if (email == null) {
                Button(
                    onClick = {
                        signingIn = true
                        signInLauncher.launch(DriveBackup.signInClient(context).signInIntent)
                    },
                    enabled = !signingIn,
                ) { Text(stringResource(R.string.drive_choose_account)) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(email!!, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        DriveBackup.signInClient(context).signOut()
                        vm.refresh()
                    }) { Text(stringResource(R.string.drive_sign_out)) }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // 2. Key custody (V2-5 / V2-46). Sits directly under the account
            // because it answers the question the account section raises: who,
            // besides this phone, can open these backups.
            if (email != null) {
                KeyProtectionSection(
                    state = keyState,
                    enabled = busy == null && backupProgress == null && restoreProgress == null,
                    onProtect = vm::startProtect,
                    onChange = vm::startChangeSecret,
                    onUnprotect = vm::startUnprotect,
                    onUnlock = vm::startUnlock,
                )
                HorizontalDivider(Modifier.padding(vertical = 12.dp))
            }

            // 3. Schedule
            SectionLabel(stringResource(R.string.drive_section_frequency))
            Text(
                stringResource(R.string.drive_frequency_note),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(6.dp))
            Row {
                listOf(
                    Checkpoints.Frequency.DAILY to R.string.drive_frequency_daily,
                    Checkpoints.Frequency.WEEKLY to R.string.drive_frequency_weekly,
                    Checkpoints.Frequency.MONTHLY to R.string.drive_frequency_monthly,
                    Checkpoints.Frequency.MANUAL to R.string.drive_frequency_manual,
                ).forEach { (f, labelRes) ->
                    FilterChip(
                        selected = frequency == f,
                        onClick = { vm.setFrequency(f) },
                        label = { Text(stringResource(labelRes)) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            SwitchRow(stringResource(R.string.drive_wifi_only), wifiOnly) { vm.setWifiOnly(it) }
            SwitchRow(
                stringResource(R.string.drive_include_media), includeMedia,
                subtitle = stringResource(R.string.drive_include_media_subtitle),
            ) { vm.setIncludeMedia(it) }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // 4. Spam backup mode (§8.3)
            SectionLabel(stringResource(R.string.drive_section_spam))
            Row {
                listOf(
                    BackupManager.SpamMode.ON to R.string.drive_spam_on,
                    BackupManager.SpamMode.OFF to R.string.drive_spam_off,
                    BackupManager.SpamMode.CUSTOM to R.string.drive_spam_custom,
                ).forEach { (m, labelRes) ->
                    FilterChip(
                        selected = spamMode == m,
                        onClick = { vm.setSpamMode(m) },
                        label = { Text(stringResource(labelRes)) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            if (spamMode == BackupManager.SpamMode.CUSTOM) {
                TextButton(onClick = onPickSpamMessages) {
                    Text(
                        pluralStringResource(
                            R.plurals.drive_choose_spam_messages,
                            customSpamCount, customSpamCount,
                        ),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // 5. Actions + status
            SectionLabel(stringResource(R.string.drive_section_backup))
            if (status.lastBackupAt > 0) {
                Text(
                    pluralStringResource(
                        R.plurals.drive_last_backup, status.messageCount,
                        AppDateFormat.dayMonthYearClock(status.lastBackupAt),
                        status.messageCount, status.sizeBytes / 1024,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            status.lastError?.let {
                Text(
                    stringResource(R.string.drive_last_error, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row {
                Button(
                    onClick = { vm.backupNow() },
                    enabled = email != null && busy == null &&
                        backupProgress == null && restoreProgress == null,
                ) { Text(stringResource(R.string.drive_back_up_now)) }
                Spacer(Modifier.width(12.dp))
                TextButton(
                    onClick = { vm.findSnapshots() },
                    enabled = email != null && busy == null &&
                        backupProgress == null && restoreProgress == null,
                ) { Text(stringResource(R.string.drive_restore)) }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // 6. Backup health (V2-52). "Last backup succeeded" is not the same
            // claim as "the backup can be restored" — the key can go missing
            // long after the upload that used it.
            SectionLabel(stringResource(R.string.drive_health_title))
            Text(
                when {
                    healthChecking -> stringResource(R.string.drive_health_checking)
                    health == null -> stringResource(R.string.drive_health_never)
                    else -> stringResource(BackupHealthCopy.message(health!!.code))
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (health != null && BackupHealthCopy.isAlarming(health!!.code)) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            health?.takeIf { it.checkedAt > 0 }?.let { h ->
                Text(
                    buildString {
                        append(
                            stringResource(
                                R.string.drive_health_checked_at,
                                AppDateFormat.dayMonthYearClock(h.checkedAt),
                            )
                        )
                        // Only when keys were actually tried: "0 of 0" would be
                        // noise on every verdict that never got that far.
                        if (h.keysTried > 0) {
                            append(" · ")
                            append(
                                pluralStringResource(
                                    R.plurals.drive_health_keys, h.keysTried,
                                    h.keysAccepted, h.keysTried,
                                )
                            )
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            TextButton(
                onClick = { vm.checkHealth() },
                enabled = email != null && !healthChecking && busy == null &&
                    backupProgress == null && restoreProgress == null,
            ) { Text(stringResource(R.string.drive_health_check_now)) }
            Spacer(Modifier.height(24.dp))
        }
    }

    backupProgress?.let { BackupProgressDialog(it) }
    restoreProgress?.let { RestoreProgressDialog(it) }

    keyFlow?.let { flow ->
        KeyProtectionDialog(
            flow = flow,
            onUpdate = vm::updateKeyFlow,
            onSubmit = vm::submitKeyFlow,
            onDismiss = vm::cancelKeyFlow,
        )
    }

    // Snapshot chooser (§8.3 keeps the last 2): pick which one to restore.
    snapshotChoices?.let { snaps ->
        AlertDialog(
            onDismissRequest = { vm.snapshotChoices.value = null },
            title = { Text(stringResource(R.string.drive_choose_backup)) },
            text = {
                Column {
                    Text(
                        stringResource(R.string.drive_snapshots_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Spacer(Modifier.height(8.dp))
                    snaps.forEachIndexed { index, snap ->
                        if (index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    vm.snapshotChoices.value = null
                                    vm.restoreCandidate.value = snap
                                }
                                .padding(vertical = 8.dp),
                        ) {
                            val created = AppDateFormat.dayMonthYearClock(snap.header.createdAt)
                            Text(
                                if (index == 0) {
                                    stringResource(R.string.drive_snapshot_latest, created)
                                } else {
                                    created
                                },
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            // V2-36. Built by concatenation before, which forces
                            // the translator into English word order; each part is
                            // now a resource that can be reordered.
                            val summary = pluralStringResource(
                                R.plurals.drive_snapshot_summary, snap.header.messageCount,
                                snap.header.deviceModel.ifBlank {
                                    stringResource(R.string.drive_unknown_device)
                                },
                                snap.header.messageCount, snap.sizeBytes / 1024,
                            )
                            Text(
                                if (snap.needsPassword) {
                                    stringResource(R.string.drive_snapshot_needs_password, summary)
                                } else {
                                    summary
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { vm.snapshotChoices.value = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    restoreCandidate?.let { snap ->
        AlertDialog(
            onDismissRequest = { vm.restoreCandidate.value = null },
            title = { Text(stringResource(R.string.drive_restore_confirm_title)) },
            text = {
                Column {
                    Text(
                        stringResource(
                            R.string.drive_restore_from,
                            snap.header.deviceModel.ifBlank {
                                stringResource(R.string.drive_another_device)
                            },
                            AppDateFormat.dayMonthYearClock(snap.header.createdAt),
                        )
                    )
                    Text(
                        pluralStringResource(
                            R.plurals.drive_message_count_size, snap.header.messageCount,
                            snap.header.messageCount, snap.sizeBytes / 1024,
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.drive_restore_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    if (snap.needsPassword) {
                        // Legacy snapshot made before the account-key model —
                        // it can only be opened with its original password.
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.drive_password_required),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = restorePassword,
                            onValueChange = { restorePassword = it },
                            label = { Text(stringResource(R.string.drive_password_label)) },
                            visualTransformation = PasswordVisualTransformation(),
                            singleLine = true,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.restore(snap.fileId, restorePassword.takeIf { snap.needsPassword })
                        restorePassword = ""
                    },
                    enabled = !snap.needsPassword || restorePassword.isNotEmpty(),
                ) { Text(stringResource(R.string.drive_restore)) }
            },
            dismissButton = {
                TextButton(onClick = { vm.restoreCandidate.value = null }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** WhatsApp-style non-dismissible progress popup shown while a manual backup runs. */
@Composable
private fun BackupProgressDialog(progress: DriveBackup.BackupProgress) {
    Dialog(onDismissRequest = {}) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.drive_backing_up), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                val fraction = progress.fraction
                // V2-36. A percentage is a number: NumberFormat places the sign
                // where the locale puts it and uses the locale's digits, which
                // "${(it * 100).toInt()}%" cannot do.
                val pct = fraction?.let { " " + NumberFormat.getPercentInstance().format(it) } ?: ""
                Text(
                    when (progress.stage) {
                        DriveBackup.BackupStage.PREPARING ->
                            if (progress.total > 0) {
                                stringResource(
                                    R.string.drive_preparing_progress,
                                    pct, progress.done, progress.total,
                                )
                            } else {
                                stringResource(R.string.drive_preparing)
                            }
                        DriveBackup.BackupStage.ENCRYPTING -> stringResource(R.string.drive_encrypting)
                        DriveBackup.BackupStage.UPLOADING ->
                            stringResource(
                                R.string.drive_uploading_progress,
                                pct, progress.done / 1024, progress.total / 1024,
                            )
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** Restore twin of [BackupProgressDialog]: download % → decrypt → import. */
@Composable
private fun RestoreProgressDialog(progress: DriveBackup.RestoreProgress) {
    Dialog(onDismissRequest = {}) {
        Surface(shape = MaterialTheme.shapes.large, tonalElevation = 6.dp) {
            Column(Modifier.padding(24.dp)) {
                Text(stringResource(R.string.drive_restoring), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                val fraction = progress.fraction
                // V2-36. A percentage is a number: NumberFormat places the sign
                // where the locale puts it and uses the locale's digits, which
                // "${(it * 100).toInt()}%" cannot do.
                val pct = fraction?.let { " " + NumberFormat.getPercentInstance().format(it) } ?: ""
                Text(
                    when (progress.stage) {
                        DriveBackup.RestoreStage.DOWNLOADING ->
                            if (progress.total > 0) {
                                stringResource(
                                    R.string.drive_downloading_progress,
                                    pct, progress.done / 1024, progress.total / 1024,
                                )
                            } else {
                                stringResource(R.string.drive_downloading)
                            }
                        DriveBackup.RestoreStage.DECRYPTING -> stringResource(R.string.drive_decrypting)
                        DriveBackup.RestoreStage.IMPORTING -> stringResource(R.string.drive_importing)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(16.dp))
                if (fraction != null) {
                    LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

@Composable
internal fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    subtitle: String? = null,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
