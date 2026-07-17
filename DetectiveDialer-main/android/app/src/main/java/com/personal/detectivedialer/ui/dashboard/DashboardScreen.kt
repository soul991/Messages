package com.personal.detectivedialer.ui.dashboard

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.detectivedialer.data.local.CallLogEntry
import com.personal.detectivedialer.ui.CategoryUi
import com.personal.detectivedialer.ui.components.AddNumberDialog
import com.personal.detectivedialer.ui.components.DayHeader
import com.personal.detectivedialer.ui.components.EmptyState
import com.personal.detectivedialer.ui.components.InitialAvatar
import com.personal.detectivedialer.ui.components.VerdictChip
import com.personal.detectivedialer.ui.components.insertOrEditContactIntent
import com.personal.detectivedialer.ui.components.avatarColorFor
import com.personal.detectivedialer.ui.components.callTimeLabel
import com.personal.detectivedialer.ui.components.dayLabel
import com.personal.detectivedialer.ui.theme.Brand
import com.personal.detectivedialer.ui.theme.DialerColors

@Composable
fun DashboardScreen(
    onOpenNumber: (String) -> Unit,
    onOpenCall: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDialer: () -> Unit = {},
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showAdd by remember { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = { SearchTopBar(query, { query = it }, onOpenSettings) },
        floatingActionButton = {
            Column(horizontalAlignment = Alignment.End) {
                // Secondary: block a number (small).
                androidx.compose.material3.SmallFloatingActionButton(
                    onClick = { showAdd = true },
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ) {
                    Icon(Icons.Default.Block, contentDescription = "Block a number")
                }
                Spacer(Modifier.height(12.dp))
                // Primary: open the dialpad to place a call.
                FloatingActionButton(
                    onClick = onOpenDialer,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Default.Dialpad, contentDescription = "Open dialpad")
                }
            }
        },
    ) { padding ->
        val calls = remember(state.calls, query) {
            val q = query.trim()
            if (q.isEmpty()) state.calls
            else state.calls.filter {
                it.number.contains(q, ignoreCase = true) ||
                    it.callerName.contains(q, ignoreCase = true)
            }
        }

        // Save-contact hand-off: remember which number we're saving so we can
        // re-resolve its name when the system Contacts UI returns (any result —
        // the editor doesn't report whether the user saved, so we just refresh).
        var pendingSaveNumber by remember { mutableStateOf("") }
        val saveContactLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.StartActivityForResult(),
        ) {
            if (pendingSaveNumber.isNotBlank()) viewModel.onContactSaved(pendingSaveNumber)
            pendingSaveNumber = ""
        }
        val onSaveContact: (String) -> Unit = { number ->
            pendingSaveNumber = number
            runCatching { saveContactLauncher.launch(insertOrEditContactIntent(number)) }
        }

        // Direct-call from a row (9a): try immediately; if CALL_PHONE isn't granted,
        // request it and place the remembered number on grant.
        var pendingCall by remember { mutableStateOf<String?>(null) }
        val callPermLauncher = rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission(),
        ) { granted ->
            if (granted) pendingCall?.let { viewModel.placeCall(it) }
            pendingCall = null
        }
        val onCall: (String) -> Unit = { number ->
            if (number.isNotBlank() && !viewModel.placeCall(number)) {
                pendingCall = number
                callPermLauncher.launch(Manifest.permission.CALL_PHONE)
            }
        }

        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.calls.isEmpty() -> EmptyState(
                    icon = Icons.Outlined.Phone,
                    title = "No calls yet",
                    hint = "Your call history — incoming, outgoing, and missed — will appear here, " +
                        "with the AI's verdict on screened numbers.",
                )
                calls.isEmpty() -> EmptyState(
                    icon = Icons.Default.Search,
                    title = "No matches",
                    hint = "No calls match \"${query.trim()}\".",
                )
                else -> CallList(calls, onOpenNumber, onOpenCall, onSaveContact, onCall)
            }
        }
    }

    if (showAdd) {
        AddNumberDialog(
            title = "Block a number",
            onConfirm = { number, _ ->
                viewModel.block(number)
                showAdd = false
            },
            onDismiss = { showAdd = false },
        )
    }
}

/** Blue pinned bar with a rounded search field and a settings shortcut. */
@Composable
private fun SearchTopBar(
    query: String,
    onQuery: (String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brand.Blue)
            .statusBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.94f),
                modifier = Modifier.weight(1f).height(44.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp),
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = Color(0xFF5C6670),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    BasicTextField(
                        value = query,
                        onValueChange = onQuery,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = Color(0xFF171C20),
                            fontSize = MaterialTheme.typography.bodyLarge.fontSize,
                        ),
                        cursorBrush = SolidColor(Brand.Blue),
                        modifier = Modifier.weight(1f),
                        decorationBox = { inner ->
                            if (query.isEmpty()) {
                                Text(
                                    "Search numbers & names",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color(0xFF8A939C),
                                )
                            }
                            inner()
                        },
                    )
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQuery("") }, modifier = Modifier.size(28.dp)) {
                            Icon(
                                Icons.Default.Close,
                                contentDescription = "Clear search",
                                tint = Color(0xFF5C6670),
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
            }
        }
    }
}

/**
 * A run of one or more STRICTLY CONSECUTIVE calls to/from the same number within a
 * single date section — no other caller's call falls between them (bug batch #6).
 * A single-call group renders as a normal row; a multi-call group collapses into
 * one row with a count badge and an expand chevron. This is a display-layer
 * projection only — the underlying call_log rows stay individual records.
 */
private data class CallGroup(
    /** Individual calls, newest-first (same order the repository emits). */
    val calls: List<CallLogEntry>,
) {
    /** Most recent call in the run — its resolved name/number represents the group. */
    val representative: CallLogEntry get() = calls.first()
    val count: Int get() = calls.size
    val isCollapsible: Boolean get() = calls.size > 1
    /** Stable list key: the newest call's id is unique and survives recomposition. */
    val key: String get() = "group-${representative.id}"
}

/**
 * Fold a date section's chronologically-ordered calls into consecutive same-number
 * runs. Adjacency in the input list IS the "strictly consecutive" rule: any call
 * from a different number between two same-number calls breaks the run, so
 * A→B→A yields three groups, never a merged A. Numbers are normalized so
 * formatting differences (+91, spaces) don't split an otherwise-consecutive run.
 *
 * A run also breaks across a calendar-day boundary: the "Previous" section lumps
 * many days under one header, and two calls from the same number on different days
 * are not one run even if they're adjacent in that section.
 */
private fun groupConsecutive(dayCalls: List<CallLogEntry>): List<CallGroup> {
    if (dayCalls.isEmpty()) return emptyList()
    val groups = mutableListOf<CallGroup>()
    var run = mutableListOf(dayCalls.first())
    var runKey = normalizeKey(dayCalls.first().number)
    var runDay = calendarDay(dayCalls.first().timestamp)
    for (call in dayCalls.drop(1)) {
        val key = normalizeKey(call.number)
        val day = calendarDay(call.timestamp)
        // Fold only same non-blank number AND same calendar day. A blank number
        // never folds — we can't prove two blanks are the same caller.
        if (key.isNotEmpty() && key == runKey && day == runDay) {
            run.add(call)
        } else {
            groups.add(CallGroup(run))
            run = mutableListOf(call)
            runKey = key
            runDay = day
        }
    }
    groups.add(CallGroup(run))
    return groups
}

/** Digits + a single leading '+', so formatting variants of one number compare equal. */
private fun normalizeKey(raw: String): String =
    raw.replace(Regex("[^\\d+]"), "").replace(Regex("(?!^)\\+"), "")

/** (year, day-of-year) identity for a timestamp — used to keep runs within one day. */
private fun calendarDay(timestamp: Long): Pair<Int, Int> {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
    return c.get(java.util.Calendar.YEAR) to c.get(java.util.Calendar.DAY_OF_YEAR)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallList(
    calls: List<CallLogEntry>,
    onOpenNumber: (String) -> Unit,
    onOpenCall: (String) -> Unit,
    onSaveContact: (String) -> Unit,
    onCall: (String) -> Unit,
) {
    // PRIMARY: date buckets (Today / Yesterday / Previous), newest-first order kept.
    // SECONDARY: collapse strictly-consecutive same-number runs into one summary row.
    // Tapping any row — grouped or single — opens that number's unified detail page
    // (full history lives there); a run's "N missed calls" summary stays for glance.
    val grouped = remember(calls) {
        calls.groupBy { dayLabel(it.timestamp) }
            .map { (day, dayCalls) -> day to groupConsecutive(dayCalls) }
    }

    // Open the number's detail page; unidentifiable (blank-number) calls have no
    // "person", so fall back to that single call's detail.
    val openCall: (CallLogEntry) -> Unit = { call ->
        if (call.number.isNotBlank()) onOpenNumber(call.number) else onOpenCall(call.id)
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        grouped.forEach { (day, groups) ->
            item(key = "header-$day") { DayHeader(day, Modifier.animateItemPlacement()) }
            groups.forEach { group ->
                if (!group.isCollapsible) {
                    val call = group.representative
                    item(key = group.key) {
                        CallRow(
                            call,
                            Modifier.animateItemPlacement(),
                            onClick = { openCall(call) },
                            onSaveContact = onSaveContact,
                            onCall = onCall,
                        )
                    }
                } else {
                    item(key = group.key) {
                        CollapsedGroupRow(
                            group = group,
                            modifier = Modifier.animateItemPlacement(),
                            onClick = { openCall(group.representative) },
                            onSaveContact = onSaveContact,
                            onCall = onCall,
                        )
                    }
                }
            }
        }
        item { Spacer(Modifier.height(80.dp)) } // clear the FAB
    }
}

@Composable
private fun CallRow(
    call: CallLogEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onSaveContact: (String) -> Unit = {},
    onCall: (String) -> Unit = {},
) {
    // Only badge non-default outcomes (spam / blocked / voicemail); a normal
    // allowed call gets no chip — matches how Google/Samsung dialers flag only
    // exceptions (bug batch #4a).
    val badge = CategoryUi.badgeFor(call.category, call.action)
    val missed = call.type == CallLogEntry.TYPE_MISSED
    val red = DialerColors.spam
    val severe = (badge?.severe == true) || missed
    val displayName = call.callerName.ifBlank { call.number.ifBlank { "Unknown" } }
    val context = LocalContext.current
    // "Save contact" is offered only for unknown numbers: a real number that hasn't
    // resolved to a saved-contact/CNAP name yet.
    val canSaveContact = call.callerName.isBlank() && call.number.isNotBlank()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialAvatar(
            name = displayName,
            color = if (severe) red else avatarColorFor(call.number.ifBlank { displayName }),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (severe) red else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    VerdictChip(badge)
                }
            }
            Text(
                buildString {
                    append(call.summary.ifBlank { typeLabel(call) })
                    append(" · ")
                    append(callTimeLabel(context, call.timestamp))
                    if (call.duration > 0 && call.type == CallLogEntry.TYPE_INCOMING ||
                        call.duration > 0 && call.type == CallLogEntry.TYPE_OUTGOING
                    ) {
                        append(" · ")
                        append(durationLabel(call.duration))
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (severe) red.copy(alpha = 0.85f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (canSaveContact) {
            IconButton(onClick = { onSaveContact(call.number) }) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = "Save ${call.number} as a contact",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = typeIcon(call, badge),
            contentDescription = null,
            tint = if (severe) red else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        // Direct-call button on the right edge (9a): dials this number immediately.
        if (call.number.isNotBlank()) {
            Spacer(Modifier.width(4.dp))
            CallActionButton(number = call.number, displayName = displayName, onCall = onCall)
        }
    }
}

/** Green round call icon used on the right edge of call-log rows (9a). */
@Composable
private fun CallActionButton(number: String, displayName: String, onCall: (String) -> Unit) {
    IconButton(onClick = { onCall(number) }) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .background(Brand.SafeGreen.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Call,
                contentDescription = "Call $displayName",
                tint = Brand.SafeGreen,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Summary row for a run of consecutive same-number calls: avatar, name/badge, and a
 * count summary ("3 missed calls") for at-a-glance info. Tapping opens the number's
 * unified detail page, where the full history (including this run) lives — no inline
 * expansion. Represented by the run's most recent call (bug batch #6).
 */
@Composable
private fun CollapsedGroupRow(
    group: CallGroup,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onSaveContact: (String) -> Unit = {},
    onCall: (String) -> Unit = {},
) {
    val call = group.representative
    val badge = CategoryUi.badgeFor(call.category, call.action)
    val red = DialerColors.spam
    // Red treatment only when the whole run is missed/severe, matching a single row.
    val allMissed = group.calls.all { it.type == CallLogEntry.TYPE_MISSED }
    val severe = (badge?.severe == true) || allMissed
    val displayName = call.callerName.ifBlank { call.number.ifBlank { "Unknown" } }
    val context = LocalContext.current
    val canSaveContact = call.callerName.isBlank() && call.number.isNotBlank()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        InitialAvatar(
            name = displayName,
            color = if (severe) red else avatarColorFor(call.number.ifBlank { displayName }),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (severe) red else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                // Count badge, e.g. "(3)", so the run size reads at a glance.
                Text(
                    "(${group.count})",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = if (severe) red else MaterialTheme.colorScheme.primary,
                )
                if (badge != null) {
                    Spacer(Modifier.width(8.dp))
                    VerdictChip(badge)
                }
            }
            Text(
                buildString {
                    append(groupSummary(group))
                    append(" · ")
                    append(callTimeLabel(context, call.timestamp))
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (severe) red.copy(alpha = 0.85f)
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (canSaveContact) {
            IconButton(onClick = { onSaveContact(call.number) }) {
                Icon(
                    Icons.Default.PersonAdd,
                    contentDescription = "Save ${call.number} as a contact",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        // Direct-call the group's most recent number (9a).
        if (call.number.isNotBlank()) {
            Spacer(Modifier.width(4.dp))
            CallActionButton(number = call.number, displayName = displayName, onCall = onCall)
        }
    }
}

/**
 * Count summary for a collapsed group, e.g. "3 missed calls" when the run is all
 * missed, otherwise a neutral "3 calls". Mirrors how stock dialers phrase a run.
 */
private fun groupSummary(group: CallGroup): String {
    val n = group.count
    return if (group.calls.all { it.type == CallLogEntry.TYPE_MISSED }) {
        "$n missed calls"
    } else {
        "$n calls"
    }
}

private fun typeLabel(call: CallLogEntry): String = when (call.type) {
    CallLogEntry.TYPE_INCOMING -> "Incoming"
    CallLogEntry.TYPE_OUTGOING -> "Outgoing"
    CallLogEntry.TYPE_MISSED -> "Missed"
    CallLogEntry.TYPE_REJECTED -> "Declined"
    else -> CategoryUi.from(call.category).label
}

private fun typeIcon(call: CallLogEntry, badge: CategoryUi?) = when {
    badge?.severe == true -> Icons.Default.Block
    call.type == CallLogEntry.TYPE_MISSED -> Icons.AutoMirrored.Filled.CallMissed
    call.type == CallLogEntry.TYPE_OUTGOING -> Icons.AutoMirrored.Filled.CallMade
    call.type == CallLogEntry.TYPE_REJECTED -> Icons.Default.Block
    else -> Icons.AutoMirrored.Filled.CallReceived
}

private fun durationLabel(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return if (m > 0) "${m}m ${s}s" else "${s}s"
}
