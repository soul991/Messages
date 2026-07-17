package com.personal.detectivedialer.ui.detail

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.detectivedialer.data.local.CallLogEntry
import com.personal.detectivedialer.ui.CategoryUi
import com.personal.detectivedialer.ui.components.InitialAvatar
import com.personal.detectivedialer.ui.components.VerdictChip
import com.personal.detectivedialer.ui.components.insertOrEditContactIntent
import com.personal.detectivedialer.ui.theme.Brand

@Composable
fun CallDetailScreen(
    callId: String,
    onBack: () -> Unit,
    viewModel: CallDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(callId) { viewModel.load(callId) }
    val callFlow = remember(callId) { viewModel.call(callId) }
    val call by callFlow.collectAsStateWithLifecycle()

    val c = call
    if (c == null) {
        LoadingHeader(onBack)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        HeaderCard(c, onBack, viewModel)

        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            VerdictCard(c)
            if (c.recordingUrl.isNotBlank() && c.recordingUrl != "pending") {
                AudioPlayer(url = c.recordingUrl)
            }
            TranscriptCard(c)
        }
    }
}

/** Placeholder header while the call loads (keeps back reachable). */
@Composable
private fun LoadingHeader(onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Brand.Blue, Brand.BlueDeep)))
                .statusBarsPadding(),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Loading call…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Truecaller-style identity header: blue gradient for normal/allowed calls,
 * red gradient for spam/blocked — avatar, name, number, round actions.
 */
@Composable
private fun HeaderCard(c: CallLogEntry, onBack: () -> Unit, viewModel: CallDetailViewModel) {
    val cat = CategoryUi.from(c.category)
    val gradient = if (cat.severe) {
        Brush.verticalGradient(listOf(Brand.SpamRed, Brand.SpamRedDeep))
    } else {
        Brush.verticalGradient(listOf(Brand.Blue, Brand.BlueDeep))
    }
    val context = LocalContext.current
    val displayName = c.callerName.ifBlank { c.number.ifBlank { "Unknown" } }
    // Offer "Save" for an unknown number (no resolved name yet). Re-resolve on
    // return from the system Contacts UI so the header reflects a newly-saved name.
    val canSaveContact = c.callerName.isBlank() && c.number.isNotBlank()
    val saveContactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.onContactSaved(c.number) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(gradient)
            .statusBarsPadding(),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            InitialAvatar(
                name = displayName,
                color = Color.White.copy(alpha = 0.22f),
                size = 88.dp,
                onColor = Color.White,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                displayName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            if (c.callerName.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    c.number.ifBlank { "Unknown number" },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Text(
                    "Name verified by network (CNAP)",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                )
            }
            Spacer(Modifier.height(20.dp))
            // Tighten spacing when the optional Save action pushes us to 5 buttons.
            Row(horizontalArrangement = Arrangement.spacedBy(if (canSaveContact) 18.dp else 28.dp)) {
                RoundAction(Icons.Default.Call, "Call") {
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${c.number}")))
                }
                RoundAction(Icons.AutoMirrored.Filled.Message, "Message") {
                    context.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${c.number}")))
                }
                if (canSaveContact) {
                    RoundAction(Icons.Default.PersonAdd, "Save") {
                        runCatching { saveContactLauncher.launch(insertOrEditContactIntent(c.number)) }
                    }
                }
                RoundAction(Icons.Default.CheckCircle, "Allow") { viewModel.allow(c) }
                RoundAction(Icons.Default.Block, "Block") { viewModel.block(c) }
            }
        }
    }
}

@Composable
private fun RoundAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        FilledIconButton(
            onClick = onClick,
            shape = CircleShape,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color.White.copy(alpha = 0.18f),
                contentColor = Color.White,
            ),
            modifier = Modifier.size(52.dp),
        ) {
            Icon(icon, contentDescription = label)
        }
        Spacer(Modifier.height(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White)
    }
}

/** AI verdict: decision chip, confidence, and the reason/summary text. */
@Composable
private fun VerdictCard(c: CallLogEntry) {
    val cat = CategoryUi.from(c.category)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "AI verdict",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                VerdictChip(cat)
            }
            if (c.confidence > 0f) {
                Text(
                    "Confidence: ${(c.confidence * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                c.summary.ifBlank { "No reason recorded for this decision." },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun TranscriptCard(c: CallLogEntry) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Transcript", style = MaterialTheme.typography.titleSmall)
            if (c.transcript.isBlank()) {
                Text(
                    "No transcript available.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                c.transcript.lines().filter { it.isNotBlank() }.forEach { line ->
                    TranscriptBubble(line)
                }
            }
        }
    }
}

@Composable
private fun TranscriptBubble(line: String) {
    val isCaller = line.startsWith("caller", ignoreCase = true)
    val text = line.substringAfter(":", line).trim()
    val align = if (isCaller) Alignment.Start else Alignment.End
    Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = align) {
        Surface(
            color = if (isCaller) MaterialTheme.colorScheme.surfaceVariant
            else MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(12.dp),
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    if (isCaller) "Caller" else "Assistant",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(text, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
