package com.messages.app.ui.drivebackup

import android.app.Application
import android.content.Intent
import android.util.Log
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.messages.app.drive.DriveBackup
import com.messages.app.drive.DriveClient
import com.messages.app.drive.DriveSignInError
import com.messages.core.backup.BackupManager
import com.messages.core.backup.Checkpoints
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        Log.w(
            TAG,
            "onSignInResult: email=${account?.email} accountName=${account?.account?.name} " +
                "grantedScopes=${account?.grantedScopes} hasDriveScope=$granted error=$error",
        )
        when {
            granted -> {
                accountEmail.value = displayEmail
                refresh()
            }
            error != null -> {
                Log.w(TAG, "sign-in failed", error)
                snackbar.value = DriveSignInError.describe(error)
            }
            else -> {
                Log.w(TAG, "signed in without granting the drive.appdata scope")
                snackbar.value = "Drive backup access wasn't granted — please try again and allow access"
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
        DriveBackup.reschedule(app)
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
            onSuccess = { "Backup complete — ${it.messageCount} messages" },
            onFailure = { "Backup failed: ${it.message}" },
        )
        refresh()
    }

    fun findSnapshots(): Job = viewModelScope.launch {
        busy.value = "Looking for backups…"
        val result = DriveBackup.listSnapshots(getApplication())
        busy.value = null
        val failure = result.exceptionOrNull()
        if (failure != null && tryRecoverable(failure) { findSnapshots() }) return@launch
        result.fold(
            onSuccess = { snaps ->
                when {
                    snaps.isEmpty() -> snackbar.value = "No backups found in this Google account"
                    // One snapshot → straight to the confirm dialog; two (we
                    // keep the last 2) → let the user pick which to restore.
                    snaps.size == 1 -> restoreCandidate.value = snaps.first()
                    else -> snapshotChoices.value = snaps
                }
            },
            onFailure = { snackbar.value = "Couldn't reach Drive: ${it.message}" },
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
                    it.messagesRestored, it.messagesSkipped,
                    lockedPending = it.lockedPending, lockedRestored = it.lockedRestored,
                )
            },
            onFailure = {
                if (it is com.messages.core.backup.BackupCrypto.WrongPasswordException)
                    "Wrong backup password"
                else "Restore failed: ${it.message}"
            },
        )
        refresh()
    }

    fun clearSnackbar() { snackbar.value = null }
}

private val STAMP = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.US)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveBackupScreen(
    onBack: () -> Unit,
    onPickSpamMessages: () -> Unit,
    vm: DriveBackupViewModel = viewModel(),
) {
    val email by vm.accountEmail.collectAsState()
    val frequency by vm.frequency.collectAsState()
    val wifiOnly by vm.wifiOnly.collectAsState()
    val includeMedia by vm.includeMedia.collectAsState()
    val spamMode by vm.spamMode.collectAsState()
    val customSpamCount by vm.customSpamCount.collectAsState()
    val status by vm.status.collectAsState()
    val busy by vm.busy.collectAsState()
    val backupProgress by vm.backupProgress.collectAsState()
    val snackbar by vm.snackbar.collectAsState()
    val restoreCandidate by vm.restoreCandidate.collectAsState()
    val snapshotChoices by vm.snapshotChoices.collectAsState()
    val restoreProgress by vm.restoreProgress.collectAsState()
    val recoverableAuthIntent by vm.recoverableAuthIntent.collectAsState()

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
                title = { Text("Google Drive backup") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                "Backups are encrypted on this phone before upload and live in " +
                    "this app's private Drive space. Your Google account is the key: " +
                    "to restore on any phone, just sign in with the same account — " +
                    "no password to remember.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(vertical = 12.dp),
            )
            busy?.let {
                Text(it, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
            }

            // 1. Account
            SectionLabel("Google account")
            if (email == null) {
                Button(
                    onClick = {
                        signingIn = true
                        signInLauncher.launch(DriveBackup.signInClient(context).signInIntent)
                    },
                    enabled = !signingIn,
                ) { Text("Choose Google account") }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(email!!, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        DriveBackup.signInClient(context).signOut()
                        vm.refresh()
                    }) { Text("Sign out") }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // 2. Schedule
            SectionLabel("Frequency")
            Text(
                "Scheduled backups contain messages up to 6:00 AM of the checkpoint " +
                    "day, whenever they actually run.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
            Spacer(Modifier.height(6.dp))
            Row {
                listOf(
                    Checkpoints.Frequency.DAILY to "Daily",
                    Checkpoints.Frequency.WEEKLY to "Weekly",
                    Checkpoints.Frequency.MONTHLY to "Monthly",
                    Checkpoints.Frequency.MANUAL to "Manual",
                ).forEach { (f, label) ->
                    FilterChip(
                        selected = frequency == f,
                        onClick = { vm.setFrequency(f) },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            SwitchRow("Back up over Wi-Fi only", wifiOnly) { vm.setWifiOnly(it) }
            SwitchRow(
                "Include photos & videos (MMS)", includeMedia,
                subtitle = "Larger backups; message text is always included",
            ) { vm.setIncludeMedia(it) }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // 3. Spam backup mode (§8.3)
            SectionLabel("Back up the Spam folder")
            Row {
                listOf(
                    BackupManager.SpamMode.ON to "On",
                    BackupManager.SpamMode.OFF to "Off",
                    BackupManager.SpamMode.CUSTOM to "Custom",
                ).forEach { (m, label) ->
                    FilterChip(
                        selected = spamMode == m,
                        onClick = { vm.setSpamMode(m) },
                        label = { Text(label) },
                        modifier = Modifier.padding(end = 6.dp),
                    )
                }
            }
            if (spamMode == BackupManager.SpamMode.CUSTOM) {
                TextButton(onClick = onPickSpamMessages) {
                    Text("Choose spam messages… ($customSpamCount selected)")
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // 4. Actions + status
            SectionLabel("Backup")
            if (status.lastBackupAt > 0) {
                Text(
                    "Last backup: ${STAMP.format(Date(status.lastBackupAt))} · " +
                        "${status.messageCount} messages · ${status.sizeBytes / 1024} KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            status.lastError?.let {
                Text(
                    "Last error: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Row {
                Button(
                    onClick = { vm.backupNow() },
                    enabled = email != null && busy == null &&
                        backupProgress == null && restoreProgress == null,
                ) { Text("Back up now") }
                Spacer(Modifier.width(12.dp))
                TextButton(
                    onClick = { vm.findSnapshots() },
                    enabled = email != null && busy == null &&
                        backupProgress == null && restoreProgress == null,
                ) { Text("Restore") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    backupProgress?.let { BackupProgressDialog(it) }
    restoreProgress?.let { RestoreProgressDialog(it) }

    // Snapshot chooser (§8.3 keeps the last 2): pick which one to restore.
    snapshotChoices?.let { snaps ->
        AlertDialog(
            onDismissRequest = { vm.snapshotChoices.value = null },
            title = { Text("Choose a backup") },
            text = {
                Column {
                    Text(
                        "Two snapshots are kept. Newer is listed first.",
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
                            Text(
                                STAMP.format(Date(snap.header.createdAt)) +
                                    (if (index == 0) "  ·  Latest" else ""),
                                style = MaterialTheme.typography.bodyMedium,
                            )
                            Text(
                                "${snap.header.deviceModel.ifBlank { "Unknown device" }} · " +
                                    "${snap.header.messageCount} messages · ${snap.sizeBytes / 1024} KB" +
                                    (if (snap.needsPassword) " · needs password" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { vm.snapshotChoices.value = null }) { Text("Cancel") }
            },
        )
    }

    restoreCandidate?.let { snap ->
        AlertDialog(
            onDismissRequest = { vm.restoreCandidate.value = null },
            title = { Text("Restore backup?") },
            text = {
                Column {
                    Text(
                        "From ${snap.header.deviceModel.ifBlank { "another device" }} · " +
                            STAMP.format(Date(snap.header.createdAt))
                    )
                    Text("${snap.header.messageCount} messages · ${snap.sizeBytes / 1024} KB")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Messages are added, never overwritten; duplicates are skipped.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    if (snap.needsPassword) {
                        // Legacy snapshot made before the account-key model —
                        // it can only be opened with its original password.
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "This backup was made with a backup password.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = restorePassword,
                            onValueChange = { restorePassword = it },
                            label = { Text("Backup password") },
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
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { vm.restoreCandidate.value = null }) { Text("Cancel") }
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
                Text("Backing up", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                val fraction = progress.fraction
                val pct = fraction?.let { " ${(it * 100).toInt()}%" } ?: ""
                Text(
                    when (progress.stage) {
                        DriveBackup.BackupStage.PREPARING ->
                            if (progress.total > 0) {
                                "Preparing messages…$pct (${progress.done}/${progress.total})"
                            } else {
                                "Preparing messages…"
                            }
                        DriveBackup.BackupStage.ENCRYPTING -> "Encrypting…"
                        DriveBackup.BackupStage.UPLOADING ->
                            "Uploading…$pct (${progress.done / 1024} KB of ${progress.total / 1024} KB)"
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
                Text("Restoring", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                val fraction = progress.fraction
                val pct = fraction?.let { " ${(it * 100).toInt()}%" } ?: ""
                Text(
                    when (progress.stage) {
                        DriveBackup.RestoreStage.DOWNLOADING ->
                            if (progress.total > 0) {
                                "Downloading…$pct (${progress.done / 1024} KB of ${progress.total / 1024} KB)"
                            } else {
                                "Downloading…"
                            }
                        DriveBackup.RestoreStage.DECRYPTING -> "Decrypting…"
                        DriveBackup.RestoreStage.IMPORTING -> "Adding messages…"
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
private fun SectionLabel(text: String) {
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
