package com.messages.app.ui.secret

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.messages.app.ui.settings.SettingsSectionDivider
import com.messages.app.ui.settings.SettingsSectionHeader
import com.messages.app.ui.settings.SettingsSwitchRow
import com.messages.core.secret.SecretCrypto
import com.messages.core.secret.SecretSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Settings INSIDE the locked space (only reachable behind the credential
 * gate): change the secret code (requires the current one), and choose
 * notification behavior — generic "New message" pings or full silence.
 * Unlocking individual chats lives on the list rows (long-press).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecretSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var notifyGeneric by remember {
        mutableStateOf(SecretSpace.notifyMode(context) == SecretSpace.NOTIFY_GENERIC)
    }
    var changing by remember { mutableStateOf(false) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Locked chats settings") },
                navigationIcon = {
                    IconButton(onClick = { if (changing) changing = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (changing) {
            ChangeCredentialFlow(
                modifier = Modifier.padding(padding),
                onDone = { message ->
                    changing = false
                    scope.launch { snackbar.showSnackbar(message) }
                },
            )
            return@Scaffold
        }
        Column(Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState())) {
            SettingsSectionHeader("Security")
            com.messages.app.ui.settings.SettingsNavRow(
                icon = Icons.Filled.Key,
                title = "Change secret code",
                subtitle = "Requires your current code. Backups re-key automatically.",
                onClick = { changing = true },
            )
            SettingsSectionDivider()
            SettingsSectionHeader("Notifications")
            SettingsSwitchRow(
                title = "Notify for locked chats",
                subtitle = "On: a generic \"New message\" with no sender or content. " +
                    "Off: locked chats never notify at all.",
                checked = notifyGeneric,
                onChange = { on ->
                    notifyGeneric = on
                    SecretSpace.setNotifyMode(
                        context,
                        if (on) SecretSpace.NOTIFY_GENERIC else SecretSpace.NOTIFY_OFF,
                    )
                },
            )
            SettingsSectionDivider()
            SettingsSectionHeader("About")
            Text(
                "Locked chats are protected by your secret code only — not your " +
                    "fingerprint or the phone's lock. If you forget the code there " +
                    "is no way to recover these chats. SMS content still lives in " +
                    "Android's shared message storage; locked chats hide it inside " +
                    "this app only.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

/** Verify current code → choose new kind → enter → confirm. */
@Composable
private fun ChangeCredentialFlow(
    modifier: Modifier = Modifier,
    onDone: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentKind = remember { SecretSpace.kind(context) }

    var step by remember { mutableIntStateOf(0) } // 0 verify current · 1 new · 2 confirm
    var current by remember { mutableStateOf("") }
    var currentPattern by remember { mutableStateOf<List<Int>>(emptyList()) }
    var newKind by remember { mutableStateOf(currentKind) }
    var new1 by remember { mutableStateOf("") }
    var newPattern by remember { mutableStateOf<List<Int>>(emptyList()) }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var patternClear by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf(false) }

    fun currentCredential(): CharArray =
        if (currentKind == SecretCrypto.KIND_PATTERN) SecretCrypto.patternToCredential(currentPattern)
        else current.toCharArray()

    fun newCredential(): CharArray =
        if (newKind == SecretCrypto.KIND_PATTERN) SecretCrypto.patternToCredential(newPattern)
        else new1.toCharArray()

    fun commitChange() {
        working = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                SecretSpace.changeCredential(context, currentCredential(), newKind, newCredential())
            }
            working = false
            when (result) {
                is SecretSpace.Attempt.Success -> onDone("Secret code changed")
                is SecretSpace.Attempt.Wrong -> {
                    step = 0; current = ""; currentPattern = emptyList(); patternClear++
                    error = "Current code was wrong"
                }
                is SecretSpace.Attempt.Cooldown ->
                    error = "Too many attempts. Try again in ${formatCooldown(result.remainingMs)}."
            }
        }
    }

    Column(
        modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = SecretScreenSpacing,
    ) {
        when (step) {
            0 -> {
                Text("Enter your current code", style = MaterialTheme.typography.titleLarge)
                if (currentKind == SecretCrypto.KIND_PATTERN) {
                    PatternGrid(clearSignal = patternClear) { cells ->
                        currentPattern = cells; error = null; step = 1
                    }
                } else {
                    PinOrPasswordField(
                        kind = currentKind, value = current, onValueChange = { current = it },
                        label = if (currentKind == SecretCrypto.KIND_PIN) "Current PIN" else "Current password",
                        isError = error != null,
                        onDone = { if (current.isNotEmpty()) { error = null; step = 1 } },
                    )
                    Button(
                        onClick = { error = null; step = 1 },
                        enabled = current.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Next") }
                }
            }
            1 -> {
                Text("Choose a new code", style = MaterialTheme.typography.titleLarge)
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    CREDENTIAL_KINDS.forEachIndexed { i, (k, label) ->
                        SegmentedButton(
                            selected = newKind == k,
                            onClick = { newKind = k; new1 = ""; newPattern = emptyList(); patternClear++ },
                            shape = SegmentedButtonDefaults.itemShape(i, CREDENTIAL_KINDS.size),
                        ) { Text(label) }
                    }
                }
                if (newKind == SecretCrypto.KIND_PATTERN) {
                    PatternGrid(clearSignal = patternClear) { cells ->
                        val err = SecretCrypto.setupError(
                            newKind, SecretCrypto.patternToCredential(cells),
                        )
                        if (err != null) { error = err } else {
                            newPattern = cells; error = null; step = 2; patternClear++
                        }
                    }
                } else {
                    PinOrPasswordField(
                        kind = newKind, value = new1, onValueChange = { new1 = it },
                        label = if (newKind == SecretCrypto.KIND_PIN) "New PIN (4+ digits)"
                        else "New password (4+ characters)",
                        isError = error != null,
                        onDone = {},
                    )
                    Button(
                        onClick = {
                            val err = SecretCrypto.setupError(newKind, new1.toCharArray())
                            if (err != null) error = err else { error = null; step = 2 }
                        },
                        enabled = new1.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Next") }
                }
            }
            2 -> {
                Text("Confirm the new code", style = MaterialTheme.typography.titleLarge)
                if (newKind == SecretCrypto.KIND_PATTERN) {
                    PatternGrid(enabled = !working, clearSignal = patternClear) { cells ->
                        if (cells == newPattern) commitChange()
                        else { error = "Patterns don't match"; patternClear++ }
                    }
                } else {
                    PinOrPasswordField(
                        kind = newKind, value = confirm, onValueChange = { confirm = it },
                        label = "Re-enter to confirm",
                        enabled = !working,
                        isError = error != null,
                        onDone = {},
                    )
                    Button(
                        onClick = {
                            if (confirm == new1) commitChange()
                            else { error = "Codes don't match"; confirm = "" }
                        },
                        enabled = confirm.isNotEmpty() && !working,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text(if (working) "Changing…" else "Change code") }
                }
            }
        }
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
