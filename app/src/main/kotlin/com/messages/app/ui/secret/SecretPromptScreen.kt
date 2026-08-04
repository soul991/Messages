package com.messages.app.ui.secret

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.messages.app.R
import com.messages.core.backup.BackupManager
import com.messages.core.secret.SecretCrypto
import com.messages.core.secret.SecretSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Vault unlock (Phase 7 redesign): AMOLED-black surface, accent as glow and
 * edge, the Compose-drawn lock mark, dot-indicator PIN entry with a wrong-
 * code shake, accent pattern grid, revealed-toggle password field. Behavior
 * is UNCHANGED from the security review: escalating cooldown, greyed (never
 * spinner/"Checking…") verification state, knowledge factor only, and Reset
 * — the space's single red element — destroying without revealing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecretPromptScreen(
    onBack: () -> Unit,
    onUnlocked: () -> Unit,
    /** Reset completed (everything locked destroyed) → launch fresh setup. */
    onReset: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val kind = remember { SecretSpace.kind(context) }
    val restoring = remember { SecretSpace.hasPendingRestore(context) && !SecretSpace.isSetUp(context) }

    var entry by remember { mutableStateOf("") }
    var patternClear by remember { mutableIntStateOf(0) }
    var errorSignal by remember { mutableIntStateOf(0) } // drives the PIN-dot shake
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var showResetDialog by remember { mutableStateOf(false) }
    var resetting by remember { mutableStateOf(false) }
    var cooldownMs by remember { mutableLongStateOf(SecretSpace.remainingCooldownMs(context)) }

    // Live cooldown countdown.
    LaunchedEffect(cooldownMs > 0) {
        while (cooldownMs > 0) {
            delay(1_000)
            cooldownMs = SecretSpace.remainingCooldownMs(context)
        }
    }

    fun submit(credential: CharArray) {
        if (checking || importing) return
        checking = true
        error = null
        scope.launch {
            val result = withContext(Dispatchers.Default) { SecretSpace.attempt(context, credential) }
            when (result) {
                is SecretSpace.Attempt.Success -> {
                    if (SecretSpace.hasPendingRestore(context)) {
                        importing = true
                        withContext(Dispatchers.IO) { BackupManager.completeLockedRestore(context) }
                        importing = false
                    }
                    SecretSession.unlock()
                    onUnlocked()
                }
                is SecretSpace.Attempt.Wrong -> {
                    entry = ""
                    patternClear++
                    errorSignal++
                    cooldownMs = result.cooldownMs
                    error = if (result.cooldownMs > 0) {
                        context.getString(
                            R.string.secret_wrong_code_cooldown,
                            formatCooldown(context, result.cooldownMs),
                        )
                    } else {
                        context.getString(R.string.secret_wrong_code)
                    }
                }
                is SecretSpace.Attempt.Cooldown -> {
                    cooldownMs = result.remainingMs
                    error = null
                }
            }
            checking = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(36.dp))
            VaultLockMark()
            Spacer(Modifier.height(28.dp))
            Text(
                stringResource(
                    if (restoring) R.string.secret_prompt_title_restoring
                    else R.string.secret_prompt_title,
                ),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(
                    when {
                        restoring -> R.string.secret_prompt_restoring_body
                        kind == SecretCrypto.KIND_PATTERN -> R.string.secret_prompt_draw_pattern
                        kind == SecretCrypto.KIND_PASSWORD -> R.string.secret_prompt_enter_password
                        else -> R.string.secret_prompt_enter_pin
                    },
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(32.dp))

            val coolingDown = cooldownMs > 0
            when {
                importing -> {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.secret_unlocking_restored),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                kind == SecretCrypto.KIND_PATTERN -> {
                    PatternGrid(enabled = !coolingDown && !checking, clearSignal = patternClear) { cells ->
                        submit(SecretCrypto.patternToCredential(cells))
                    }
                }
                kind == SecretCrypto.KIND_PASSWORD -> {
                    PinOrPasswordField(
                        kind = kind, value = entry, onValueChange = { entry = it },
                        label = stringResource(R.string.secret_password_label),
                        enabled = !coolingDown && !checking,
                        isError = false, // feedback is the message below, not a red field
                        onDone = { if (entry.isNotEmpty()) submit(entry.toCharArray()) },
                    )
                }
                else -> {
                    PinDotsEntry(
                        value = entry,
                        onValueChange = { entry = it },
                        enabled = !coolingDown && !checking,
                        errorSignal = errorSignal,
                        onDone = { if (entry.isNotEmpty()) submit(entry.toCharArray()) },
                    )
                }
            }

            if (!importing && kind != SecretCrypto.KIND_PATTERN) {
                Spacer(Modifier.height(24.dp))
                // Greyed verification state (deliberate): accent at low alpha,
                // no spinner, no label change — the PBKDF2 delay just reads as
                // a held button.
                Button(
                    onClick = { submit(entry.toCharArray()) },
                    enabled = entry.isNotEmpty() && !coolingDown && !checking,
                    colors = ButtonDefaults.buttonColors(
                        disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
                        disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.secret_unlock)) }
            }

            Spacer(Modifier.height(16.dp))
            // Wrong-code + cooldown feedback stays NEUTRAL — the shake carries
            // the emphasis; red is reserved for Reset, the single destructive
            // action in the space.
            //
            // V2-38: it is also a live region. The shake and the red-free
            // wording are both visual, so without this a screen-reader user
            // submits a wrong code and hears nothing at all — the screen simply
            // sits there. Assertive because it is blocking: there is no point
            // entering another code during a cooldown. Neither string contains
            // any part of the credential.
            val announce = Modifier.semantics { liveRegion = LiveRegionMode.Assertive }
            when {
                coolingDown -> Text(
                    stringResource(
                        R.string.secret_too_many_attempts,
                        formatCooldown(context, cooldownMs),
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = announce,
                )
                error != null -> Text(
                    error!!,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = announce,
                )
            }

            Spacer(Modifier.weight(1f))
            // The only path past a forgotten code: destroy, never reveal.
            TextButton(
                onClick = { showResetDialog = true },
                enabled = !checking && !importing && !resetting,
            ) {
                Text(stringResource(R.string.secret_reset), color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showResetDialog) {
        AlertDialog(
            onDismissRequest = { if (!resetting) showResetDialog = false },
            // Dialogs get their own window — the activity's FLAG_SECURE does
            // not cover them. Explicitly secure, like every surface in here.
            properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
            title = { Text(stringResource(R.string.secret_reset_confirm_title)) },
            text = {
                Text(stringResource(R.string.secret_reset_confirm_body))
            },
            confirmButton = {
                TextButton(
                    enabled = !resetting,
                    onClick = {
                        resetting = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                // Destruction without revelation: hard-delete
                                // every locked row (Room + Telephony provider +
                                // media, never via Trash), drop the LOCKED
                                // conversation rows (routing reverts to normal),
                                // then forget credential/KEK/rate-limit/pending.
                                com.messages.core.MessageRepository.get(context).wipeLockedSpace()
                                SecretSpace.clearAll(context)
                            }
                            androidx.core.app.NotificationManagerCompat.from(context)
                                .cancel(com.messages.app.notify.MessageNotifier.LOCKED_SPACE_ID)
                            SecretSession.lock()
                            resetting = false
                            showResetDialog = false
                            onReset()
                        }
                    },
                ) {
                    Text(
                        stringResource(
                            if (resetting) R.string.secret_deleting
                            else R.string.secret_delete_everything,
                        ),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !resetting,
                    onClick = { showResetDialog = false },
                ) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/**
 * V2-36. "5m 30s" is copy, not arithmetic: the unit letters differ by language
 * and some put them before the number. The rounding-up and the choice of which
 * two units to show stay here; only the letters moved to the resource table.
 */
internal fun formatCooldown(context: Context, ms: Long): String {
    val totalSec = (ms + 999) / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return when {
        min >= 60 -> context.getString(R.string.secret_cooldown_hm, min / 60, min % 60)
        min > 0 -> context.getString(R.string.secret_cooldown_ms, min, sec)
        else -> context.getString(R.string.secret_cooldown_s, sec)
    }
}
