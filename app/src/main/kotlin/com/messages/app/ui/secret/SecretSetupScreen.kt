package com.messages.app.ui.secret

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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

    // (kind, credential) once chosen+confirmed; null while still choosing.
    var chosen by remember { mutableStateOf<Pair<String, CharArray>?>(null) }
    var working by remember { mutableStateOf(false) }
    var legacyCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        legacyCount = withContext(Dispatchers.IO) {
            MessageRepository.get(context).db.conversations().legacyLockedConversations().size
        }
    }

    fun finishSetup(kind: String, credential: CharArray) {
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
                        if (chosen != null) chosen = null else onBack()
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
            if (chosen == null) {
                Spacer(Modifier.height(8.dp))
                VaultLockMark(size = 56.dp)
                // The one shared choose→enter→confirm flow (also used by the
                // in-space "change secret code" — fix: full type re-pick).
                CredentialCreationSteps(
                    subtitle = "This code protects your locked chats. It works only here — " +
                        "it is separate from your fingerprint, the phone's lock, " +
                        "and the app lock.",
                    onChosen = { kind, credential -> chosen = kind to credential },
                )
            } else {
                DisclaimerStep(
                    legacyCount = legacyCount,
                    working = working,
                    onUnderstood = {
                        chosen?.let { (kind, credential) -> finishSetup(kind, credential) }
                    },
                )
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

/** Short bold lead per verbatim point — structure added ABOVE the mandated
 *  text, never replacing a word of it (styling per design system). */
private val DISCLAIMER_LEADS = listOf("No recovery", "SMS storage", "Backups", "Notifications")

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

    Box(Modifier.weight(1f).fillMaxWidth()) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier
                    .verticalScroll(scroll)
                    .padding(20.dp),
                verticalArrangement = SecretScreenSpacing,
            ) {
                // Typographic hierarchy (Phase 7): display-size heading, lead
                // labels per point, verbatim body text underneath.
                Text(DISCLAIMER_TITLE, style = MaterialTheme.typography.headlineMedium)
                Text(
                    DISCLAIMER_INTRO,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                DISCLAIMER_POINTS.forEachIndexed { i, point ->
                    Column {
                        Text(
                            DISCLAIMER_LEADS.getOrElse(i) { "" },
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            point,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (legacyCount > 0) {
                    Text(
                        "Your existing locked chats will move here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(28.dp)) // room beneath the fade edge
            }
        }
        // Scroll gate affordance (Phase 7): fading bottom edge + a floating
        // "scroll to continue" chip; both disappear once the end is reached.
        androidx.compose.animation.AnimatedVisibility(
            visible = !everReachedEnd,
            enter = androidx.compose.animation.fadeIn(com.messages.designsystem.Motion.effectsDefault()),
            exit = androidx.compose.animation.fadeOut(com.messages.designsystem.Motion.effectsFast()),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            listOf(
                                androidx.compose.ui.graphics.Color.Transparent,
                                MaterialTheme.colorScheme.background,
                            )
                        )
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    Text(
                        "Scroll to continue ↓",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
    Button(
        onClick = onUnderstood,
        enabled = everReachedEnd && !working,
        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
            disabledContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
            disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.55f),
        ),
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
            Box { Text("I understand") }
        }
    }
}
