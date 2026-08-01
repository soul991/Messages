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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.messages.app.R
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
    // Keyed on `changing` so returning from the change flow re-reads the flag
    // and the card goes away the moment the code is strengthened.
    val weakCredential = remember(changing) { SecretSpace.isCredentialWeak(context) }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.secret_settings_title)) },
                navigationIcon = {
                    IconButton(onClick = { if (changing) changing = false else onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
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
            // V2-7: the existing code is below today's floor. This is the whole
            // enforcement mechanism — a card the user can ignore forever. Read
            // once per composition of the screen, so it disappears as soon as
            // the change flow returns here.
            if (weakCredential) WeakCredentialCard(onChange = { changing = true })
            SettingsSectionHeader(stringResource(R.string.secret_section_security))
            com.messages.app.ui.settings.SettingsNavRow(
                icon = Icons.Filled.Key,
                title = stringResource(R.string.secret_change_code),
                subtitle = stringResource(R.string.secret_change_code_subtitle),
                onClick = { changing = true },
            )
            SettingsSectionDivider()
            SettingsSectionHeader(stringResource(R.string.settings_section_notifications))
            SettingsSwitchRow(
                title = stringResource(R.string.secret_notify_title),
                subtitle = stringResource(R.string.secret_notify_subtitle),
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
            SettingsSectionHeader(stringResource(R.string.secret_section_about))
            Text(
                stringResource(R.string.secret_about_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
        }
    }
}

/**
 * V2-7: shown when the stored credential is below the current setup floors.
 *
 * Deliberately a card and not a dialog, and deliberately dismissible by simply
 * not tapping it: NIST SP 800-63B-4 prohibits forcing rotation without evidence
 * of compromise, and a modal that reappears every visit would train people to
 * dismiss it without reading. The body says the code still works, because it
 * does, and because a warning that overstates its case gets ignored.
 */
@Composable
private fun WeakCredentialCard(onChange: () -> Unit) {
    androidx.compose.material3.Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.secret_weak_credential_title),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                stringResource(R.string.secret_weak_credential_body),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
            androidx.compose.material3.TextButton(
                onClick = onChange,
                modifier = Modifier.padding(top = 8.dp),
            ) { Text(stringResource(R.string.secret_weak_credential_action)) }
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
                        context.getString(
                            R.string.secret_wrong_code_cooldown,
                            formatCooldown(context, result.cooldownMs),
                        )
                    } else context.getString(R.string.secret_current_code_wrong_retry)
                }
                is SecretSpace.Attempt.Cooldown ->
                    error = context.getString(
                        R.string.secret_too_many_attempts,
                        formatCooldown(context, result.remainingMs),
                    )
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
                is SecretSpace.Attempt.Success ->
                    onDone(context.getString(R.string.secret_code_changed))
                // Re-verification can only fail if state changed underneath —
                // fall back to the verify step rather than guessing.
                is SecretSpace.Attempt.Wrong -> {
                    verifiedCurrent = null; current = ""; patternClear++
                    error = context.getString(R.string.secret_current_code_wrong)
                }
                is SecretSpace.Attempt.Cooldown ->
                    error = context.getString(
                        R.string.secret_too_many_attempts,
                        formatCooldown(context, result.remainingMs),
                    )
            }
        }
    }

    Column(
        modifier.fillMaxSize().padding(horizontal = 24.dp),
        verticalArrangement = SecretScreenSpacing,
    ) {
        if (verifiedCurrent == null) {
            Text(stringResource(R.string.secret_enter_current_code), style = MaterialTheme.typography.titleLarge)
            if (currentKind == SecretCrypto.KIND_PATTERN) {
                PatternGrid(enabled = !working, clearSignal = patternClear) { cells ->
                    verifyCurrent(SecretCrypto.patternToCredential(cells))
                }
            } else {
                PinOrPasswordField(
                    kind = currentKind, value = current, onValueChange = { current = it },
                    label = stringResource(
                        if (currentKind == SecretCrypto.KIND_PIN) R.string.secret_current_pin
                        else R.string.secret_current_password,
                    ),
                    enabled = !working,
                    isError = error != null,
                    onDone = { if (current.isNotEmpty()) verifyCurrent(current.toCharArray()) },
                )
                Button(
                    onClick = { verifyCurrent(current.toCharArray()) },
                    enabled = current.isNotEmpty() && !working,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.action_next)) }
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            // Current code verified — full type re-pick, exactly like setup.
            CredentialCreationSteps(
                heading = stringResource(R.string.secret_choose_new_code),
                subtitle = stringResource(R.string.secret_choose_new_code_subtitle),
                working = working,
                onChosen = { newKind, new -> commitChange(newKind, new) },
            )
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
