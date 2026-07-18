package com.personal.detectivedialer.ui.person

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.detectivedialer.data.contacts.ContactNumber
import com.personal.detectivedialer.data.local.CallLogEntry
import com.personal.detectivedialer.ui.CategoryUi
import com.personal.detectivedialer.ui.components.InitialAvatar
import com.personal.detectivedialer.ui.components.VerdictChip
import com.personal.detectivedialer.ui.components.callTimeLabel
import com.personal.detectivedialer.ui.components.dayLabel
import com.personal.detectivedialer.ui.components.insertOrEditContactIntent
import com.personal.detectivedialer.ui.components.rememberContactPhotoBitmap
import com.personal.detectivedialer.ui.theme.Brand
import com.personal.detectivedialer.ui.theme.DialerColors

/**
 * Unified detail page for a person or a number. A saved contact shows every one of
 * their numbers as an actionable row and one merged history timeline; an unknown
 * number shows the single number with a Save action. Tapping a history entry opens
 * that individual call's AI verdict / transcript.
 */
@Composable
fun PersonDetailScreen(
    onBack: () -> Unit,
    onMessage: (String) -> Unit,
    onOpenCall: (String) -> Unit,
    viewModel: PersonDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var pendingCall by remember { mutableStateOf<String?>(null) }
    val callPermLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) pendingCall?.let { viewModel.placeCall(it) }; pendingCall = null }
    val call: (String) -> Unit = { number ->
        if (number.isNotBlank() && !viewModel.placeCall(number)) {
            pendingCall = number
            callPermLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    // Save-contact hand-off (unknown numbers only): re-resolve on return so the
    // header/actions switch to the saved-person layout live.
    val saveContactLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { viewModel.onContactSaved() }
    val onSave: (String) -> Unit = { number ->
        runCatching { saveContactLauncher.launch(insertOrEditContactIntent(number)) }
    }

    Column(Modifier.fillMaxSize()) {
        val s = state
        PersonHeader(
            name = s?.name ?: "…",
            photoUri = s?.photoUri,
            // Round actions live in the header only for an unknown number; a saved
            // person's actions live on the per-number rows below.
            singleNumber = if (s != null && !s.isSaved) s.numbers.firstOrNull()?.number else null,
            onBack = onBack,
            onCall = call,
            onMessage = onMessage,
            onSave = onSave,
        )

        if (s == null) {
            Box(Modifier.fillMaxSize(), Alignment.Center) {
                Text("Loading…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Column
        }

        LazyColumn(Modifier.fillMaxSize()) {
            if (s.isSaved) {
                item("numbers-header") { SectionLabel("Contact") }
                s.numbers.forEach { number ->
                    item("num-${number.number}") {
                        NumberRow(
                            number = number,
                            onCall = { call(number.number) },
                            onMessage = { onMessage(number.number) },
                        )
                    }
                }
            }

            item("history-header") { SectionLabel("Call history") }
            if (s.history.isEmpty()) {
                item("history-empty") {
                    Text(
                        "No calls with this ${if (s.isSaved) "contact" else "number"} yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            } else {
                // Full timeline, grouped by day but never collapsed — every call is shown.
                val byDay = s.history.groupBy { dayLabel(it.timestamp) }
                byDay.forEach { (day, dayCalls) ->
                    item("day-$day") { DayLabel(day) }
                    dayCalls.forEach { entry ->
                        item("call-${entry.id}") {
                            HistoryRow(
                                entry = entry,
                                numberNote = if (s.annotateNumbers) numberNote(entry, s.numbers) else null,
                                onClick = { onOpenCall(entry.id) },
                            )
                        }
                    }
                }
            }
            item("tail") { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun PersonHeader(
    name: String,
    photoUri: String?,
    singleNumber: String?,
    onBack: () -> Unit,
    onCall: (String) -> Unit,
    onMessage: (String) -> Unit,
    onSave: (String) -> Unit,
) {
    val photo = rememberContactPhotoBitmap(photoUri)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Brand.Blue, Brand.BlueDeep)))
            .statusBarsPadding(),
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (photo != null) {
                Image(
                    bitmap = photo,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(88.dp).clip(CircleShape),
                )
            } else {
                InitialAvatar(
                    name = name,
                    color = Color.White.copy(alpha = 0.22f),
                    size = 88.dp,
                    onColor = Color.White,
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                name,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            // Unknown number: show the number and the Call/Message/Save round actions.
            if (singleNumber != null) {
                if (singleNumber.isNotBlank() && singleNumber != name) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        singleNumber,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                }
                Spacer(Modifier.height(20.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    RoundAction(Icons.Default.Call, "Call") { onCall(singleNumber) }
                    RoundAction(Icons.AutoMirrored.Filled.Message, "Message") { onMessage(singleNumber) }
                    if (singleNumber.isNotBlank()) {
                        RoundAction(Icons.Default.PersonAdd, "Save") { onSave(singleNumber) }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, onClick: () -> Unit) {
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

/** A saved contact's number: label + number with inline message and call actions. */
@Composable
private fun NumberRow(number: ContactNumber, onCall: () -> Unit, onMessage: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(number.number, style = MaterialTheme.typography.bodyLarge)
            Text(
                number.label.ifBlank { "Phone" },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onMessage) {
            Icon(
                Icons.AutoMirrored.Filled.Message,
                contentDescription = "Message ${number.number}",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(4.dp))
        Box(
            modifier = Modifier.size(44.dp).background(Brand.SafeGreen, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            IconButton(onClick = onCall) {
                Icon(Icons.Filled.Call, contentDescription = "Call ${number.number}", tint = Color.White)
            }
        }
    }
}

/** One call in the merged timeline: type, time, duration, optional verdict + number note. */
@Composable
private fun HistoryRow(entry: CallLogEntry, numberNote: String?, onClick: () -> Unit) {
    val badge = CategoryUi.badgeFor(entry.category, entry.action)
    val severe = (badge?.severe == true) || entry.type == CallLogEntry.TYPE_MISSED
    val red = DialerColors.spam
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = typeIcon(entry, badge),
            contentDescription = null,
            tint = if (severe) red else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                buildString {
                    append(entry.summary.ifBlank { typeLabel(entry) })
                    append(" · ")
                    append(callTimeLabel(context, entry.timestamp))
                    if (entry.duration > 0 &&
                        (entry.type == CallLogEntry.TYPE_INCOMING || entry.type == CallLogEntry.TYPE_OUTGOING)
                    ) {
                        append(" · ")
                        append(durationLabel(entry.duration))
                    }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (severe) red.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (numberNote != null) {
                Text(
                    numberNote,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            VerdictChip(badge)
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun DayLabel(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
    )
}

/** Which of a multi-number person's numbers this call used: "Mobile · +91 98…". */
private fun numberNote(entry: CallLogEntry, numbers: List<ContactNumber>): String {
    val key = matchKey(entry.number)
    val match = numbers.firstOrNull { matchKey(it.number) == key }
    val label = match?.label?.takeIf { it.isNotBlank() }
    val shown = match?.number?.takeIf { it.isNotBlank() } ?: entry.number.ifBlank { "Unknown number" }
    return if (label != null) "$label · $shown" else shown
}

/** Digits only, reduced to the last 10 — mirrors ContactsRepository's number matching. */
private fun matchKey(raw: String): String {
    val digits = raw.filter { it.isDigit() }
    return if (digits.length >= 10) digits.takeLast(10) else digits
}

private fun typeLabel(entry: CallLogEntry): String = when (entry.type) {
    CallLogEntry.TYPE_INCOMING -> "Incoming"
    CallLogEntry.TYPE_OUTGOING -> "Outgoing"
    CallLogEntry.TYPE_MISSED -> "Missed"
    CallLogEntry.TYPE_REJECTED -> "Declined"
    else -> CategoryUi.from(entry.category).label
}

private fun typeIcon(entry: CallLogEntry, badge: CategoryUi?): ImageVector = when {
    badge?.severe == true -> Icons.Default.Block
    entry.type == CallLogEntry.TYPE_MISSED -> Icons.AutoMirrored.Filled.CallMissed
    entry.type == CallLogEntry.TYPE_OUTGOING -> Icons.AutoMirrored.Filled.CallMade
    entry.type == CallLogEntry.TYPE_REJECTED -> Icons.Default.Block
    else -> Icons.AutoMirrored.Filled.CallReceived
}

private fun durationLabel(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
