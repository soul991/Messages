package com.messages.app.ui.secret

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.messages.core.backup.BackupManager
import com.messages.core.secret.SecretCrypto
import com.messages.core.secret.SecretSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Credential prompt for the secret locked space. Rate-limited: after 5
 * consecutive failures an escalating cooldown gates further attempts (the
 * countdown renders live). On the first successful entry after a fresh-
 * install restore, the pending locked envelope is decrypted and imported
 * before entering the space.
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
                    cooldownMs = result.cooldownMs
                    error = if (result.cooldownMs > 0) {
                        "Wrong code. Try again in ${formatCooldown(result.cooldownMs)}."
                    } else {
                        "Wrong code — try again"
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
        topBar = {
            TopAppBar(
                title = { Text("Locked chats") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = SecretScreenSpacing,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Icon(
                Icons.Filled.Lock, contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                if (restoring) "Locked chats present" else "Enter your secret code",
                style = MaterialTheme.typography.headlineSmall,
            )
            if (restoring) {
                Text(
                    "Your backup contains locked chats. Enter the secret code you " +
                        "set on your previous device to unlock them here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val coolingDown = cooldownMs > 0
            if (coolingDown) {
                Text(
                    "Too many attempts. Try again in ${formatCooldown(cooldownMs)}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            if (importing) {
                CircularProgressIndicator()
                Text(
                    "Unlocking your restored chats…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (kind == SecretCrypto.KIND_PATTERN) {
                PatternGrid(enabled = !coolingDown && !checking, clearSignal = patternClear) { cells ->
                    submit(SecretCrypto.patternToCredential(cells))
                }
            } else {
                PinOrPasswordField(
                    kind = kind, value = entry, onValueChange = { entry = it },
                    label = if (kind == SecretCrypto.KIND_PIN) "PIN" else "Password",
                    enabled = !coolingDown && !checking,
                    isError = error != null,
                    onDone = { if (entry.isNotEmpty()) submit(entry.toCharArray()) },
                )
                // While the (deliberately slow) PBKDF2 check runs, the button
                // and input just grey out — no label change, no spinner.
                Button(
                    onClick = { submit(entry.toCharArray()) },
                    enabled = entry.isNotEmpty() && !coolingDown && !checking,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Unlock") }
            }

            if (!coolingDown) {
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            // The only path past a forgotten code: destroy, never reveal.
            androidx.compose.material3.TextButton(
                onClick = { showResetDialog = true },
                enabled = !checking && !importing && !resetting,
            ) {
                Text("Reset", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (showResetDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { if (!resetting) showResetDialog = false },
            title = { Text("Reset locked chats?") },
            text = {
                Text(
                    "ALL messages in your locked folder will be permanently deleted. " +
                        "This cannot be undone and cannot be recovered. You'll set a " +
                        "new secret code and start with an empty locked folder.",
                )
            },
            confirmButton = {
                androidx.compose.material3.TextButton(
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
                        if (resetting) "Deleting…" else "Delete everything",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(
                    enabled = !resetting,
                    onClick = { showResetDialog = false },
                ) { Text("Cancel") }
            },
        )
    }
}

internal fun formatCooldown(ms: Long): String {
    val totalSec = (ms + 999) / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return when {
        min >= 60 -> "${min / 60}h ${min % 60}m"
        min > 0 -> "${min}m ${sec}s"
        else -> "${sec}s"
    }
}
