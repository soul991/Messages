package com.personal.detectivedialer.ui.lists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.Badge
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.detectivedialer.data.local.BlockedNumber
import com.personal.detectivedialer.data.local.SmsMessage
import com.personal.detectivedialer.ui.components.AddNumberDialog
import com.personal.detectivedialer.ui.components.EmptyState
import com.personal.detectivedialer.ui.components.InitialAvatar
import com.personal.detectivedialer.ui.components.relativeTime
import com.personal.detectivedialer.ui.messages.MessagesViewModel
import com.personal.detectivedialer.ui.theme.Brand
import com.personal.detectivedialer.ui.theme.DialerColors

/**
 * Blocked tab: everything the app kept away from you, in one place.
 * Calls segment = blocked numbers; Messages segment = the blocked SMS
 * folder (readable, restorable, never auto-deleted).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BlocklistScreen(
    onOpenSettings: () -> Unit,
    listsViewModel: ListsViewModel = hiltViewModel(),
    smsViewModel: MessagesViewModel = hiltViewModel(),
) {
    val blockedNumbers by listsViewModel.blocked.collectAsStateWithLifecycle()
    val blockedSms by smsViewModel.blocked.collectAsStateWithLifecycle()
    val unreadSms by smsViewModel.blockedBadge.collectAsStateWithLifecycle()
    var segment by rememberSaveable { mutableIntStateOf(0) }
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Blocked", fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Brand.Blue,
                    titleContentColor = Color.White,
                ),
            )
        },
        floatingActionButton = {
            if (segment == 0) {
                FloatingActionButton(
                    onClick = { showAdd = true },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Block a number")
                }
            }
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                SegmentedButton(
                    selected = segment == 0,
                    onClick = { segment = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                ) { Text("Calls") }
                SegmentedButton(
                    selected = segment == 1,
                    onClick = { segment = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                ) {
                    Text("Messages")
                    if (unreadSms > 0) {
                        Spacer(Modifier.width(6.dp))
                        Badge { Text("$unreadSms") }
                    }
                }
            }
            when (segment) {
                0 -> BlockedNumbersList(blockedNumbers, onUnblock = listsViewModel::unblock)
                1 -> BlockedSmsList(
                    blockedSms,
                    onRead = smsViewModel::markRead,
                    onRestore = smsViewModel::restore,
                    onNeverBlock = smsViewModel::neverBlock,
                    onDelete = smsViewModel::delete,
                )
            }
        }
    }

    if (showAdd) {
        AddNumberDialog(
            title = "Block a number",
            onConfirm = { number, _ -> listsViewModel.block(number); showAdd = false },
            onDismiss = { showAdd = false },
        )
    }
}

@Composable
private fun BlockedNumbersList(blocked: List<BlockedNumber>, onUnblock: (String) -> Unit) {
    if (blocked.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Block,
            title = "No blocked numbers",
            hint = "Numbers you block — or the AI auto-blocks — will show up here.",
        )
        return
    }
    val red = DialerColors.spam
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(blocked, key = { it.id }) { item ->
            var menuOpen by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                InitialAvatar(name = item.number, color = red)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        item.number,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = red,
                    )
                    val why = buildString {
                        append(if (item.source == "ai_learned") "Auto-blocked by AI" else "Blocked by you")
                        if (item.reason.isNotBlank()) append(" · ${item.reason}")
                    }
                    Text(
                        why,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Unblock") },
                            onClick = { menuOpen = false; onUnblock(item.number) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedSmsList(
    blocked: List<SmsMessage>,
    onRead: (Long) -> Unit,
    onRestore: (Long) -> Unit,
    onNeverBlock: (String) -> Unit,
    onDelete: (Long) -> Unit,
) {
    if (blocked.isEmpty()) {
        EmptyState(
            icon = Icons.Outlined.Sms,
            title = "No blocked messages",
            hint = "Filtered texts land here — always readable, never auto-deleted.",
        )
        return
    }
    val red = DialerColors.spam
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(blocked, key = { it.id }) { msg ->
            var menuOpen by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { if (!msg.read) onRead(msg.id) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.Top,
            ) {
                InitialAvatar(name = msg.sender, color = red)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            msg.sender,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (!msg.read) FontWeight.Bold else FontWeight.Medium,
                            color = red,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            relativeTime(msg.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Full body, always readable.
                    Text(msg.body, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "Why blocked: ${msg.blockReason.ifBlank { "unknown reason" }}",
                        style = MaterialTheme.typography.labelMedium,
                        color = red,
                    )
                }
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "More",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Restore to inbox") },
                            onClick = { menuOpen = false; onRestore(msg.id) },
                        )
                        DropdownMenuItem(
                            text = { Text("Never block this sender") },
                            onClick = { menuOpen = false; onNeverBlock(msg.sender) },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete", color = red) },
                            onClick = { menuOpen = false; onDelete(msg.id) },
                        )
                    }
                }
            }
        }
    }
}
