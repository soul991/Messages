package com.messages.app.ui.secret

import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.messages.core.MessageRepository
import com.messages.core.secret.SecretCrypto
import com.messages.core.secret.SecretSpace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * First-time secret-space setup: choose PIN / pattern / password → confirm →
 * disclaimer (must scroll to the end and tap "I understand"). Completing
 * setup also migrates any legacy biometric-locked conversations into the new
 * locked space — stated on the disclaimer step.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecretSetupScreen(
    onBack: () -> Unit,
    onComplete: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()

    var step by remember { mutableIntStateOf(0) } // 0 choose+enter · 1 confirm · 2 disclaimer
    var kind by remember { mutableStateOf(SecretCrypto.KIND_PIN) }
    var first by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var firstPattern by remember { mutableStateOf<List<Int>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    var patternClear by remember { mutableIntStateOf(0) }
    var working by remember { mutableStateOf(false) }
    var legacyCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        legacyCount = withContext(Dispatchers.IO) {
            MessageRepository.get(context).db.conversations().legacyLockedConversations().size
        }
    }

    fun credentialOf(text: String): CharArray = text.toCharArray()

    fun advanceFromEntry(credential: CharArray) {
        val setupError = SecretCrypto.setupError(kind, credential)
        if (setupError != null) {
            error = setupError
            return
        }
        error = null
        step = 1
    }

    fun finishSetup(credential: CharArray) {
        working = true
        scope.launch {
            withContext(Dispatchers.Default) {
                SecretSpace.setUp(context, kind, credential)
                MessageRepository.get(context).migrateLegacyLockedConversations()
            }
            SecretSession.unlock()
            onComplete()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Set up locked chats") },
                navigationIcon = {
                    IconButton(onClick = {
                        when (step) {
                            0 -> onBack()
                            else -> {
                                step = 0; confirm = ""; firstPattern = emptyList()
                                first = ""; error = null; patternClear++
                            }
                        }
                    }) {
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
        ) {
            when (step) {
                0 -> {
                    Spacer(Modifier.height(8.dp))
                    Icon(
                        Icons.Filled.Lock, contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        "Choose a secret code",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        "This code protects your locked chats. It works only here — " +
                            "it is separate from your fingerprint, the phone's lock, " +
                            "and the app lock.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        CREDENTIAL_KINDS.forEachIndexed { i, (k, label) ->
                            SegmentedButton(
                                selected = kind == k,
                                onClick = {
                                    kind = k; first = ""; error = null
                                    firstPattern = emptyList(); patternClear++
                                },
                                shape = SegmentedButtonDefaults.itemShape(i, CREDENTIAL_KINDS.size),
                            ) { Text(label) }
                        }
                    }
                    if (kind == SecretCrypto.KIND_PATTERN) {
                        PatternGrid(clearSignal = patternClear) { cells ->
                            firstPattern = cells
                            advanceFromEntry(SecretCrypto.patternToCredential(cells))
                        }
                    } else {
                        PinOrPasswordField(
                            kind = kind, value = first, onValueChange = { first = it },
                            label = if (kind == SecretCrypto.KIND_PIN) "Enter a PIN (4+ digits)"
                            else "Enter a password (4+ characters)",
                            isError = error != null,
                            onDone = { advanceFromEntry(credentialOf(first)) },
                        )
                        Button(
                            onClick = { advanceFromEntry(credentialOf(first)) },
                            enabled = first.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Next") }
                    }
                }

                1 -> {
                    Spacer(Modifier.height(8.dp))
                    Text("Confirm your secret code", style = MaterialTheme.typography.headlineSmall)
                    if (kind == SecretCrypto.KIND_PATTERN) {
                        PatternGrid(clearSignal = patternClear) { cells ->
                            if (cells == firstPattern) {
                                error = null; step = 2
                            } else {
                                error = "Patterns don't match — try again"
                                patternClear++
                            }
                        }
                    } else {
                        PinOrPasswordField(
                            kind = kind, value = confirm, onValueChange = { confirm = it },
                            label = "Re-enter to confirm",
                            isError = error != null,
                            onDone = {},
                        )
                        Button(
                            onClick = {
                                if (confirm == first) {
                                    error = null; step = 2
                                } else {
                                    error = "Codes don't match — try again"; confirm = ""
                                }
                            },
                            enabled = confirm.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Confirm") }
                    }
                }

                2 -> DisclaimerStep(
                    legacyCount = legacyCount,
                    working = working,
                    onUnderstood = {
                        val credential =
                            if (kind == SecretCrypto.KIND_PATTERN)
                                SecretCrypto.patternToCredential(firstPattern)
                            else credentialOf(first)
                        finishSetup(credential)
                    },
                )
            }
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/** Disclaimer paragraphs — text used VERBATIM per the product spec. */
private val DISCLAIMER_TITLE = "About locked chats."
private val DISCLAIMER_INTRO =
    "Locked chats are hidden inside this app and protected by the secret code you set — " +
        "not by your fingerprint or the phone's lock. Please understand:"
private val DISCLAIMER_POINTS = listOf(
    "(1) If you forget your secret code, there is NO way to recover these chats — " +
        "no reset, no backdoor. That's what makes it secure.",
    "(2) SMS messages are stored in your phone's shared message storage. If someone " +
        "makes another app the default SMS app, they could see these messages there. " +
        "Locked chats protect against casual snooping on THIS app — they cannot " +
        "change how Android stores SMS.",
    "(3) Backups include locked chats in encrypted form; restoring them on any " +
        "device requires this same secret code.",
    "(4) Notifications for locked chats will only say 'New message' — or can be " +
        "turned off entirely inside the locked folder's settings.",
)

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.DisclaimerStep(
    legacyCount: Int,
    working: Boolean,
    onUnderstood: () -> Unit,
) {
    val scroll = rememberScrollState()
    // "I understand" unlocks only once the user has scrolled to the end.
    val reachedEnd = !scroll.canScrollForward
    var everReachedEnd by remember { mutableStateOf(false) }
    if (reachedEnd) everReachedEnd = true

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.weight(1f).fillMaxWidth(),
    ) {
        Column(
            Modifier
                .verticalScroll(scroll)
                .padding(20.dp),
            verticalArrangement = SecretScreenSpacing,
        ) {
            Text(DISCLAIMER_TITLE, style = MaterialTheme.typography.titleLarge)
            Text(
                DISCLAIMER_INTRO,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DISCLAIMER_POINTS.forEach { point ->
                Text(
                    point,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (legacyCount > 0) {
                Text(
                    "Your existing locked chats will move here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
    Button(
        onClick = onUnderstood,
        enabled = everReachedEnd && !working,
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
    ) {
        if (working) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp,
                )
                Spacer(Modifier.width(10.dp))
                Text("Setting up…")
            }
        } else {
            Box { Text(if (everReachedEnd) "I understand" else "Scroll to continue") }
        }
    }
}
