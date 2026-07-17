package com.messages.app.ui.drivebackup

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.messages.app.drive.DriveBackup
import com.messages.core.backup.BackupManager
import com.messages.core.backup.Checkpoints
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DriveBackupViewModel(app: Application) : AndroidViewModel(app) {

    val accountEmail = MutableStateFlow(DriveBackup.signedInAccount(app)?.email)
    val encryptionConfigured = MutableStateFlow(DriveBackup.isEncryptionConfigured(app))
    val frequency = MutableStateFlow(DriveBackup.frequency(app))
    val wifiOnly = MutableStateFlow(DriveBackup.wifiOnly(app))
    val includeMedia = MutableStateFlow(DriveBackup.includeMedia(app))
    val spamMode = MutableStateFlow(DriveBackup.spamMode(app))
    val customSpamCount = MutableStateFlow(DriveBackup.customSpamIds(app).size)
    val status = MutableStateFlow(DriveBackup.status(app))
    val busy = MutableStateFlow<String?>(null)
    val snackbar = MutableStateFlow<String?>(null)
    val restoreCandidate = MutableStateFlow<DriveBackup.RemoteSnapshot?>(null)

    fun refresh() {
        val app = getApplication<Application>()
        accountEmail.value = DriveBackup.signedInAccount(app)?.email
        encryptionConfigured.value = DriveBackup.isEncryptionConfigured(app)
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

    fun setPassword(password: String) {
        DriveBackup.setPassword(getApplication(), password.toCharArray())
        snackbar.value = "Backup password set"
        refresh()
    }

    fun backupNow() = viewModelScope.launch {
        busy.value = "Backing up…"
        val result = DriveBackup.backupNow(getApplication(), manual = true)
        busy.value = null
        snackbar.value = result.fold(
            onSuccess = { "Backup complete — ${it.messageCount} messages" },
            onFailure = { "Backup failed: ${it.message}" },
        )
        refresh()
    }

    fun findLatestSnapshot() = viewModelScope.launch {
        busy.value = "Looking for backups…"
        val result = DriveBackup.latestSnapshot(getApplication())
        busy.value = null
        result.fold(
            onSuccess = { snap ->
                if (snap == null) snackbar.value = "No backups found in this Google account"
                else restoreCandidate.value = snap
            },
            onFailure = { snackbar.value = "Couldn't reach Drive: ${it.message}" },
        )
    }

    fun restore(fileId: String, password: String) = viewModelScope.launch {
        busy.value = "Restoring…"
        val result = DriveBackup.restore(getApplication(), fileId, password.toCharArray())
        busy.value = null
        restoreCandidate.value = null
        snackbar.value = result.fold(
            onSuccess = { "Restored ${it.messagesRestored} messages (${it.messagesSkipped} already present)" },
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
    val encryptionConfigured by vm.encryptionConfigured.collectAsState()
    val frequency by vm.frequency.collectAsState()
    val wifiOnly by vm.wifiOnly.collectAsState()
    val includeMedia by vm.includeMedia.collectAsState()
    val spamMode by vm.spamMode.collectAsState()
    val customSpamCount by vm.customSpamCount.collectAsState()
    val status by vm.status.collectAsState()
    val busy by vm.busy.collectAsState()
    val snackbar by vm.snackbar.collectAsState()
    val restoreCandidate by vm.restoreCandidate.collectAsState()

    var showPasswordDialog by remember { mutableStateOf(false) }
    var restorePassword by remember { mutableStateOf("") }
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    androidx.compose.runtime.LaunchedEffect(snackbar) {
        snackbar?.let { snackbarHostState.showSnackbar(it); vm.clearSnackbar() }
    }

    val signInLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        runCatching { GoogleSignIn.getSignedInAccountFromIntent(result.data).getResult(Exception::class.java) }
        vm.refresh()
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
                "Backups are encrypted on this phone before upload — Google can't " +
                    "read them. They live in this app's private Drive space and don't " +
                    "count against your Drive storage UI.",
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
                Button(onClick = {
                    signInLauncher.launch(DriveBackup.signInClient(context).signInIntent)
                }) { Text("Choose Google account") }
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

            // 2. Encryption
            SectionLabel("Encryption (required)")
            Text(
                if (encryptionConfigured) "Backup password is set."
                else "Set a backup password before the first backup. If you forget " +
                    "it, your backups CANNOT be recovered — by anyone.",
                style = MaterialTheme.typography.bodySmall,
                color = if (encryptionConfigured) MaterialTheme.colorScheme.outline
                else MaterialTheme.colorScheme.error,
            )
            TextButton(onClick = { showPasswordDialog = true }) {
                Text(if (encryptionConfigured) "Change password" else "Set backup password")
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))

            // 3. Schedule
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

            // 4. Spam backup mode (§8.3)
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

            // 5. Actions + status
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
                    enabled = email != null && encryptionConfigured && busy == null,
                ) { Text("Back up now") }
                Spacer(Modifier.width(12.dp))
                TextButton(
                    onClick = { vm.findLatestSnapshot() },
                    enabled = email != null && busy == null,
                ) { Text("Restore") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPasswordDialog) {
        PasswordDialog(
            onDismiss = { showPasswordDialog = false },
            onSet = { vm.setPassword(it); showPasswordDialog = false },
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
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = restorePassword,
                        onValueChange = { restorePassword = it },
                        label = { Text("Backup password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.restore(snap.fileId, restorePassword); restorePassword = "" },
                    enabled = restorePassword.isNotEmpty(),
                ) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { vm.restoreCandidate.value = null }) { Text("Cancel") }
            },
        )
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

@Composable
private fun PasswordDialog(
    onDismiss: () -> Unit,
    onSet: (String) -> Unit,
) {
    var pw by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup password") },
        text = {
            Column {
                Text(
                    "Encrypts every backup before upload. Minimum 6 characters. " +
                        "If you forget it, backups cannot be recovered — there is no reset.",
                    style = MaterialTheme.typography.bodySmall,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = pw, onValueChange = { pw = it },
                    label = { Text("Password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = confirm, onValueChange = { confirm = it },
                    label = { Text("Confirm password") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSet(pw) },
                enabled = pw.length >= 6 && pw == confirm,
            ) { Text("Set password") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
