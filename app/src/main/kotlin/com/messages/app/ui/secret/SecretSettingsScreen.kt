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

/**
 * Change secret code: verify the CURRENT code first (immediately, with the
 * same rate limit as the prompt), then the user picks the type again —
 * PIN / pattern / password — through the exact first-time-setup component
 * ([CredentialCreationSteps]). Changing the type is a first-class path.
 */
@Composable
private fun ChangeCredentialFlow(
    modifier: Modifier = Modifier,
    onDone: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentKind = remember { SecretSpace.kind(context) }

    var current by remember { mutableStateOf("") }
    var verifiedCurrent by remember { mutableStateOf<CharArray?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var patternClear by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf(false) }

    fun verifyCurrent(credential: CharArray) {
        if (working) return
        working = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.Default) { SecretSpace.attempt(context, credential) }
            working = false
            when (result) {
                is SecretSpace.Attempt.Success -> verifiedCurrent = credential
                is SecretSpace.Attempt.Wrong -> {
                    current = ""; patternClear++
                    error = if (result.cooldownMs > 0) {
                        "Wrong code. Try again in ${formatCooldown(result.cooldownMs)}."
                    } else "Current code was wrong — try again"
                }
                is SecretSpace.Attempt.Cooldown ->
                    error = "Too many attempts. Try again in ${formatCooldown(result.remainingMs)}."
            }
        }
    }

    fun commitChange(newKind: String, new: CharArray) {
        val verified = verifiedCurrent ?: return
        working = true
        scope.launch {
            val result = withContext(Dispatchers.Default) {
                SecretSpace.changeCredential(context, verified, newKind, new)
            }
            working = false
            when (result) {
                is SecretSpace.Attempt.Success -> onDone("Secret code changed")
                // Re-verification can only fail if state changed underneath —
                // fall back to the verify step rather than guessing.
                is SecretSpace.Attempt.Wrong -> {
                    verifiedCurrent = null; current = ""; patternClear++
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
        if (verifiedCurrent == null) {
            Text("Enter your current code", style = MaterialTheme.typography.titleLarge)
            if (currentKind == SecretCrypto.KIND_PATTERN) {
                PatternGrid(enabled = !working, clearSignal = patternClear) { cells ->
                    verifyCurrent(SecretCrypto.patternToCredential(cells))
                }
            } else {
                PinOrPasswordField(
                    kind = currentKind, value = current, onValueChange = { current = it },
                    label = if (currentKind == SecretCrypto.KIND_PIN) "Current PIN" else "Current password",
                    enabled = !working,
                    isError = error != null,
                    onDone = { if (current.isNotEmpty()) verifyCurrent(current.toCharArray()) },
                )
                Button(
                    onClick = { verifyCurrent(current.toCharArray()) },
                    enabled = current.isNotEmpty() && !working,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Next") }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            // Current code verified — full type re-pick, exactly like setup.
            CredentialCreationSteps(
                heading = "Choose a new secret code",
                subtitle = "Pick any type — it doesn't have to match your current one.",
                working = working,
                onChosen = { newKind, new -> commitChange(newKind, new) },
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
