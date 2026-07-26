package com.messages.app.ui.secret

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.app.ui.common.ContactAvatar
import com.messages.core.MessageRepository
import com.messages.core.db.ConversationEntity
import com.messages.core.db.Spaces
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LockedSpaceViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MessageRepository.get(app)

    val folder = MutableStateFlow("INBOX")

    private val cache = HashMap<String, StateFlow<List<ConversationEntity>?>>()

    fun conversationsFor(category: String): StateFlow<List<ConversationEntity>?> =
        cache.getOrPut(category) {
            repo.db.conversations().byCategory(category, Spaces.LOCKED)
                .map<List<ConversationEntity>, List<ConversationEntity>?> { it }
                .stateIn(viewModelScope, SharingStarted.Lazily, null)
        }

    fun folderUnread(category: String) = repo.db.messages().unreadCount(category, Spaces.LOCKED)

    fun setFolder(f: String) { folder.value = f }

    /** "Unlock chat": whole thread moves back to the normal space. */
    fun unlockThread(threadId: Long, onDone: () -> Unit = {}) = viewModelScope.launch {
        repo.moveThreadToSpace(threadId, Spaces.LOCKED, Spaces.NORMAL)
        onDone()
    }

    fun toggleMute(threadId: Long, muted: Boolean) = viewModelScope.launch {
        repo.db.conversations().setMuted(threadId, muted, Spaces.LOCKED)
    }
}

private val FOLDERS = listOf(
    "INBOX" to "Inbox",
    "TRANSACTIONS" to "Transactions",
    "PROMOTIONS" to "Promotions",
    "SPAM" to "Spam",
    "REVIEW" to "Review",
    "BLOCKED" to "Blocked",
)

/**
 * The secret locked space: its own conversation list mirroring Home's design
 * (avatar-first rows, folder chips with unread badges), fed exclusively by
 * LOCKED-space queries. Back re-locks immediately (MainActivity wiring);
 * FLAG_SECURE is always on while this screen is anywhere on the stack.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockedSpaceScreen(
    onBack: () -> Unit,
    onOpenThread: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    vm: LockedSpaceViewModel = viewModel(),
) {
    val context = LocalContext.current
    val folder by vm.folder.collectAsState()
    val conversations by vm.conversationsFor(folder).collectAsState()
    var sheetThread by remember { mutableStateOf<ConversationEntity?>(null) }
    val notifyOff = remember {
        com.messages.core.secret.SecretSpace.notifyMode(context) ==
            com.messages.core.secret.SecretSpace.NOTIFY_OFF
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Lock, contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Locked chats")
                        if (notifyOff) {
                            Spacer(Modifier.width(8.dp))
                            Icon(
                                Icons.Filled.NotificationsOff, contentDescription = "Notifications off",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Locked chats settings")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FOLDERS.forEach { (key, label) ->
                    val unread by vm.folderUnread(key).collectAsState(initial = 0)
                    FilterChip(
                        selected = folder == key,
                        onClick = { vm.setFolder(key) },
                        label = {
                            Text(if (unread > 0) "$label $unread" else label)
                        },
                    )
                }
            }

            val list = conversations
            if (list != null && list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.Lock, contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(40.dp),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            if (folder == "INBOX") "No locked chats yet" else "Nothing here",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (folder == "INBOX") {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Lock a chat from its ⋮ menu to move it here.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            } else if (list != null) {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(list, key = { it.id }) { conv ->
                        LockedConversationRow(
                            conv = conv,
                            onClick = { onOpenThread(conv.threadId) },
                            onLongClick = { sheetThread = conv },
                        )
                    }
                }
            }
        }
    }

    sheetThread?.let { conv ->
        ModalBottomSheet(onDismissRequest = { sheetThread = null }) {
            ListItem(
                headlineContent = { Text("Move back to normal chats") },
                supportingContent = {
                    Text("The whole chat returns to your normal list; new messages stop routing here.")
                },
                leadingContent = { Icon(Icons.Filled.LockOpen, contentDescription = null) },
                modifier = Modifier.clickable {
                    vm.unlockThread(conv.threadId)
                    sheetThread = null
                },
            )
            ListItem(
                headlineContent = { Text(if (conv.muted) "Unmute" else "Mute") },
                leadingContent = { Icon(Icons.Filled.NotificationsOff, contentDescription = null) },
                modifier = Modifier.clickable {
                    vm.toggleMute(conv.threadId, !conv.muted)
                    sheetThread = null
                },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun LockedConversationRow(
    conv: ConversationEntity,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val name = conv.contactName ?: conv.address
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ContactAvatar(name = name, category = conv.category, size = 54.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatLockedTime(conv.lastTimestamp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                conv.lastMessage.ifBlank { "No messages yet" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (conv.unreadCount > 0) {
            Spacer(Modifier.width(12.dp))
            Badge { Text(conv.unreadCount.toString()) }
        }
    }
}

private val lockedTimeFormat = SimpleDateFormat("d MMM", Locale.getDefault())
private val lockedClockFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

private fun formatLockedTime(ts: Long): String {
    if (ts <= 0) return ""
    val now = System.currentTimeMillis()
    val sameDay = now - ts < 24 * 3600_000L &&
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(now)) ==
        SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ts))
    return if (sameDay) lockedClockFormat.format(Date(ts)) else lockedTimeFormat.format(Date(ts))
}
