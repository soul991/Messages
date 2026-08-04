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
import androidx.compose.material.icons.filled.Edit
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.messages.app.R
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.app.ui.common.AppDateFormat
import com.messages.app.ui.common.ContactAvatar
import com.messages.core.MessageRepository
import com.messages.core.db.ConversationEntity
import com.messages.core.db.Spaces
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.messages.core.secret.LockedContent
import com.messages.core.secret.LockedWriteBlockedException

class LockedSpaceViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = MessageRepository.get(app)

    /** Held as a property because `app` is a plain constructor parameter and so
     *  is out of scope inside member functions. */
    private val ctx = app.applicationContext

    val folder = MutableStateFlow("INBOX")

    private val cache = HashMap<String, StateFlow<List<ConversationEntity>?>>()

    fun conversationsFor(category: String): StateFlow<List<ConversationEntity>?> =
        cache.getOrPut(category) {
            repo.db.conversations().byCategory(category, Spaces.LOCKED)
                // V2-6: the preview snippet of a locked conversation is sealed.
                .map<List<ConversationEntity>, List<ConversationEntity>?> {
                    LockedContent.open(ctx, it)
                }
                .stateIn(viewModelScope, SharingStarted.Lazily, null)
        }

    /** Conversation-level, matching Home's unified unread predicate. */
    fun folderUnread(category: String) =
        repo.db.conversations().unreadConversationCount(category, Spaces.LOCKED)

    fun setFolder(f: String) { folder.value = f }

    /** "Unlock chat": whole thread moves back to the normal space. */
    fun unlockThread(threadId: Long, onDone: () -> Unit = {}) = viewModelScope.launch {
        try {
            repo.moveThreadToSpace(threadId, Spaces.LOCKED, Spaces.NORMAL)
        } catch (_: LockedWriteBlockedException) {
            // V2-6b: unlocking needs the content key to open the rows on the
            // way out. The banner above the list already says why.
            return@launch
        }
        onDone()
    }

    // ---- V2-6b: seal-health surface for the in-space warning banner ----

    /** Locked rows still held at the degraded pending grade. */
    val pendingCount: StateFlow<Int> =
        repo.db.messages().pendingSealCount(LockedContent.PENDING_MARKER)
            .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    /** Locked TOMBSTONES — rows whose only text copy is the provider row. */
    val tombstoneCount: StateFlow<Int> =
        repo.db.messages().lockedTombstoneCount()
            .stateIn(viewModelScope, SharingStarted.Lazily, 0)

    /** False while the Keystore content key cannot be obtained. */
    val sealHealthy = MutableStateFlow(true)

    /** The banner's Retry and the auto-repair on entering the space: run the
     *  backlog pass (upgrades pending rows, recovers tombstones), then re-read
     *  key health. Serialized against other runs by the repository's mutex. */
    fun retrySeal() = viewModelScope.launch {
        runCatching { repo.sealLockedBacklog() }
        sealHealthy.value = withContext(Dispatchers.IO) { LockedContent.available(ctx) }
    }

    init {
        retrySeal()
    }

    fun toggleMute(threadId: Long, muted: Boolean) = viewModelScope.launch {
        repo.db.conversations().setMuted(threadId, muted, Spaces.LOCKED)
    }
}

private val FOLDERS = listOf(
    "INBOX" to R.string.category_inbox,
    "TRANSACTIONS" to R.string.category_transactions,
    "PROMOTIONS" to R.string.category_promotions,
    "SPAM" to R.string.category_spam,
    "REVIEW" to R.string.category_review,
    "BLOCKED" to R.string.home_folder_blocked,
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
    /** New-message FAB: same compose UX as Home, conversation lands LOCKED. */
    onCompose: () -> Unit = {},
    vm: LockedSpaceViewModel = viewModel(),
) {
    val context = LocalContext.current
    val folder by vm.folder.collectAsStateWithLifecycle()
    val conversations by vm.conversationsFor(folder).collectAsStateWithLifecycle()
    var sheetThread by remember { mutableStateOf<ConversationEntity?>(null) }
    val notifyOff = remember {
        com.messages.core.secret.SecretSpace.notifyMode(context) ==
            com.messages.core.secret.SecretSpace.NOTIFY_OFF
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            androidx.compose.foundation.layout.Column {
                TopAppBar(
                    title = {
                        if (notifyOff) {
                            Icon(
                                Icons.Filled.NotificationsOff, contentDescription = stringResource(R.string.secret_notifications_off),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    },
                    colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(R.string.action_back),
                            )
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenSettings) {
                            Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.secret_settings_title))
                        }
                    },
                )
                // Phase 7: slim persistent strip — the space's name + mark
                // with a hairline accent edge, so there's never any doubt
                // which side of the wall you're on.
                VaultHeaderStrip()
            }
        },
        floatingActionButton = {
            androidx.compose.material3.ExtendedFloatingActionButton(
                onClick = onCompose,
                icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                text = { Text(stringResource(R.string.secret_new_message)) },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            // V2-6b: seal-health warning — rendered ONLY here, behind the
            // credential. Nothing outside the space may reveal it exists, so
            // this banner (and the write-blocks it explains) is the whole
            // user-facing surface of a Keystore outage.
            val pending by vm.pendingCount.collectAsStateWithLifecycle()
            val tombstones by vm.tombstoneCount.collectAsStateWithLifecycle()
            val sealHealthy by vm.sealHealthy.collectAsStateWithLifecycle()
            if (!sealHealthy || pending > 0 || tombstones > 0) {
                androidx.compose.material3.Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = androidx.compose.material3.CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            stringResource(R.string.secret_seal_warning_title),
                            style = MaterialTheme.typography.titleSmall,
                        )
                        Text(
                            stringResource(R.string.secret_seal_warning_body),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                        androidx.compose.material3.TextButton(
                            onClick = { vm.retrySeal() },
                            modifier = Modifier.padding(top = 8.dp),
                        ) { Text(stringResource(R.string.secret_seal_warning_action)) }
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FOLDERS.forEach { (key, labelRes) ->
                    val unread by vm.folderUnread(key).collectAsStateWithLifecycle(initialValue = 0)
                    val label = stringResource(labelRes)
                    FilterChip(
                        selected = folder == key,
                        onClick = { vm.setFolder(key) },
                        label = {
                            Text(
                                if (unread > 0) {
                                    pluralStringResource(
                                        R.plurals.secret_folder_unread, unread, label, unread,
                                    )
                                } else {
                                    label
                                },
                            )
                        },
                    )
                }
            }

            val list = conversations
            if (list != null && list.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        VaultEmptyArt()
                        Spacer(Modifier.height(20.dp))
                        Text(
                            stringResource(
                                if (folder == "INBOX") R.string.secret_empty_inbox
                                else R.string.secret_empty_folder,
                            ),
                            style = MaterialTheme.typography.titleLarge,
                        )
                        if (folder == "INBOX") {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                stringResource(R.string.secret_empty_inbox_body),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(20.dp))
                            androidx.compose.material3.FilledTonalButton(onClick = onCompose) {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.secret_new_message))
                            }
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
        ModalBottomSheet(
            onDismissRequest = { sheetThread = null },
            // Sheets get their own window — secure it explicitly, like every
            // surface inside the space (activity FLAG_SECURE doesn't extend).
            properties = androidx.compose.material3.ModalBottomSheetDefaults.properties(
                securePolicy = androidx.compose.ui.window.SecureFlagPolicy.SecureOn,
            ),
        ) {
            ListItem(
                headlineContent = { Text(stringResource(R.string.secret_unlock_thread)) },
                supportingContent = {
                    Text(stringResource(R.string.secret_unlock_thread_body))
                },
                leadingContent = { Icon(Icons.Filled.LockOpen, contentDescription = null) },
                modifier = Modifier.clickable {
                    vm.unlockThread(conv.threadId)
                    sheetThread = null
                },
            )
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(
                            if (conv.muted) R.string.action_unmute else R.string.action_mute,
                        ),
                    )
                },
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
                conv.lastMessage.ifBlank { stringResource(R.string.secret_no_messages_yet) },
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

// V2-45: this was the file lint pointed at — two formatters built with
// `Locale.getDefault()` at class-init, so a language change with the process
// still alive left locked-space rows in the old language. The same-day check
// also allocated two SimpleDateFormats and formatted two dates *per row* just
// to compare calendar days; AppDateFormat compares day numbers instead.
private fun formatLockedTime(ts: Long): String = AppDateFormat.listRowStamp(ts)
