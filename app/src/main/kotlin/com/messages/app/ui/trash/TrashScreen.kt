package com.messages.app.ui.trash

import android.app.Application
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.messages.core.MessageRepository
import com.messages.core.db.MessageEntity
import com.messages.core.trash.TrashRetention
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TrashViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MessageRepository.get(app)

    val trashed: StateFlow<List<MessageEntity>> =
        repo.db.messages().trashedMessages()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val lastAction = MutableStateFlow<String?>(null)

    fun restore(messageId: Long) = viewModelScope.launch {
        repo.restoreFromTrash(messageId)
        lastAction.value = "Message restored"
    }

    fun deleteForever(messageId: Long) = viewModelScope.launch {
        repo.deleteForever(messageId)
        lastAction.value = "Deleted forever"
    }

    fun emptyTrash() = viewModelScope.launch {
        trashed.value.forEach { repo.deleteForever(it.id) }
        lastAction.value = "Trash emptied"
    }

    fun clearLastAction() {
        lastAction.value = null
    }
}

/**
 * Trash folder (§6.4): user-deleted messages, browsable + restorable for 60
 * days; per-item Delete forever and a guarded Empty-trash action.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrashScreen(
    onBack: () -> Unit,
    vm: TrashViewModel = viewModel(),
) {
    val trashed by vm.trashed.collectAsState()
    val lastAction by vm.lastAction.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmForever by remember { mutableStateOf<MessageEntity?>(null) }
    var confirmEmpty by remember { mutableStateOf(false) }

    LaunchedEffect(lastAction) {
        lastAction?.let {
            snackbarHostState.showSnackbar(it)
            vm.clearLastAction()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("Trash") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (trashed.isNotEmpty()) {
                        TextButton(onClick = { confirmEmpty = true }) { Text("Empty trash") }
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Deleted messages are kept here for 60 days, then removed permanently.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (trashed.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Outlined.Delete, contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Trash is empty",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            } else {
                LazyColumn(Modifier.fillMaxSize()) {
                    items(trashed, key = { it.id }) { msg ->
                        TrashRow(
                            msg = msg,
                            onRestore = { vm.restore(msg.id) },
                            onDeleteForever = { confirmForever = msg },
                        )
                    }
                }
            }
        }
    }

    confirmForever?.let { msg ->
        AlertDialog(
            onDismissRequest = { confirmForever = null },
            title = { Text("Delete forever?") },
            text = { Text("This message will be permanently deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteForever(msg.id)
                    confirmForever = null
                }) { Text("Delete forever") }
            },
            dismissButton = {
                TextButton(onClick = { confirmForever = null }) { Text("Cancel") }
            },
        )
    }
    if (confirmEmpty) {
        AlertDialog(
            onDismissRequest = { confirmEmpty = false },
            title = { Text("Empty trash?") },
            text = { Text("All ${trashed.size} messages in Trash will be permanently deleted. This cannot be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.emptyTrash()
                    confirmEmpty = false
                }) { Text("Empty trash") }
            },
            dismissButton = {
                TextButton(onClick = { confirmEmpty = false }) { Text("Cancel") }
            },
        )
    }
}

private val TRASH_TIME_FMT = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.US)

@Composable
private fun TrashRow(
    msg: MessageEntity,
    onRestore: () -> Unit,
    onDeleteForever: () -> Unit,
) {
    val daysLeft = TrashRetention.purgeCountdownDays(msg.trashedAt)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    (if (msg.isOutgoing) "To " else "") + msg.address,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    TRASH_TIME_FMT.format(Date(msg.timestamp)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                msg.body.ifBlank { "(media message)" },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.outline,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (daysLeft > 0) "Deleted forever in $daysLeft days" else "Deleted forever soon",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
            )
        }
        IconButton(onClick = onRestore) {
            Icon(
                Icons.Filled.RestoreFromTrash, contentDescription = "Restore",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        IconButton(onClick = onDeleteForever) {
            Icon(
                Icons.Filled.DeleteForever, contentDescription = "Delete forever",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}
